package AngioTool;

import Pixels.ArgbBuffer;
import Pixels.ImageFile;
import Utils.ISliceRunner;
import Xlsx.SpreadsheetWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/** Standalone, headless orchestration. Never reads desktop preferences. */
public final class Cli
{
    private static final String[] IMAGE_FILES = {"result.png", "result vessel-mask.png",
        "result raw-skeleton.png", "result pruned-skeleton.png", "result overlay-data.json",
        "result normalized-data.json"};
    private static final Set<String> FLAGS = new HashSet<>(Arrays.asList(
        "--recursive", "--overwrite", "--fail-fast"));
    private static final Set<String> VALUES = new HashSet<>(Arrays.asList(
        "--input", "--output", "--parameters", "--mode", "--scale", "--resize",
        "--skeleton", "--prune", "--merge", "--normalize"));

    private Cli() {}

    public static int run(String[] args, PrintStream out, PrintStream err)
    {
        if (args.length == 1 && "--version".equals(args[0])) {
            out.println(AngioTool.VERSION);
            return 0;
        }
        if (args.length > 0 && "config".equals(args[0]))
            return config(args, out, err);
        if ((args.length == 1 && "--help".equals(args[0])) ||
                (args.length == 2 && "analyze".equals(args[0]) && "--help".equals(args[1]))) {
            out.println("Usage: java -jar AngioTool2.3.1.jar analyze --input FILE|DIR --output DIR [options]\n"
                + "  --parameters FILE   Load an overlay-data.json parameter snapshot\n"
                + "  --recursive         Scan subdirectories (never follow symbolic links)\n"
                + "  --overwrite         Replace generated files; never append a workbook\n"
                + "  --fail-fast         Stop after the first image failure\n"
                + "  --mode tubeness|binary    --skeleton zha84|lee94\n"
                + "  --scale FACTOR      Measurement multiplier; 1 disables scaling\n"
                + "  --resize FACTOR     Input resize multiplier; binary mode ignores resizing\n"
                + "  --prune PX|off      Terminal branch length in analysis-image pixels\n"
                + "  --merge PX|off      Junction merge distance in analysis-image pixels\n"
                + "  --normalize true|false\n"
                + "Outputs: images/<relative input name>/result*, results.xlsx, run-report.json.\n"
                + "Exit: 0 completed (possibly invalid metrics), 1 processing/output failure, 2 configuration error.\n"
                + "Config: config defaults|validate|resolve [--parameters FILE] [--output FILE]\n"
                + "All parameters: each name below accepts an explicit value. Arrays use commas.\n"
                + "No arguments launches the desktop interface.");
            for (CliConfig.Parameter p : CliConfig.PARAMETERS.values())
                out.println("  " + p.option + " VALUE  [" + p.unit + "; active: " + p.enabledBy + "]");
            return 0;
        }
        try {
            Map<String, String> options = parse(args);
            AnalyzerParameters params = parameters(options);
            Path input = requiredPath(options, "--input").toRealPath();
            Path output = requiredPath(options, "--output").toFile().getCanonicalFile().toPath();
            if (!Files.isRegularFile(input) && !Files.isDirectory(input))
                throw new IllegalArgumentException("Input must be a file or directory");
            if (input.equals(output) || input.startsWith(output))
                throw new IllegalArgumentException("Output must not contain the input");
            List<Path> files = enumerate(input, output, options.containsKey("--recursive"));
            if (files.isEmpty())
                throw new IllegalArgumentException("No supported images found");
            Path root = Files.isDirectory(input) ? input : input.getParent();
            preflight(output, files, root, options.containsKey("--overwrite"));
            return analyze(files, root, output, params, options.containsKey("--fail-fast"), out, err);
        }
        catch (IllegalArgumentException | IOException ex) {
            err.println("Configuration error: " + ex.getMessage());
            return 2;
        }
    }

    private static Map<String, String> parse(String[] args)
    {
        if (args.length == 0 || !"analyze".equals(args[0]))
            throw new IllegalArgumentException("Expected analyze; use --help");
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 1; i < args.length; i++) {
            String key = args[i];
            if (!FLAGS.contains(key) && !VALUES.contains(key) && !CliConfig.OPTIONS.containsKey(key))
                throw new IllegalArgumentException("Unknown option: " + key);
            if (options.containsKey(key))
                throw new IllegalArgumentException("Repeated option: " + key);
            String value = "true";
            if (VALUES.contains(key) || CliConfig.OPTIONS.containsKey(key)) {
                if (++i >= args.length || args[i].startsWith("--"))
                    throw new IllegalArgumentException("Missing value for " + key);
                value = args[i];
            }
            options.put(key, value);
        }
        return options;
    }

    private static Path requiredPath(Map<String, String> options, String key)
    {
        if (!options.containsKey(key))
            throw new IllegalArgumentException("Required: " + key);
        return Paths.get(options.get(key)).toAbsolutePath().normalize();
    }

    private static AnalyzerParameters parameters(Map<String, String> options) throws IOException
    {
        AnalyzerParameters p = options.containsKey("--parameters") ?
            CliConfig.load(requiredPath(options, "--parameters")).parameters : AnalyzerParameters.defaults();
        CliConfig.apply(p, options);
        List<String> errors = CliConfig.errors(p);
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("\n", errors));
        return p;
    }

    private static int config(String[] args, PrintStream out, PrintStream err)
    {
        if (args.length == 2 && args[1].equals("--help")) {
            out.println("config defaults [--output FILE] [--overwrite]\n"
                + "config validate --parameters FILE\n"
                + "config resolve [--parameters FILE] [parameter options] [--output FILE] [--overwrite]\n"
                + "defaults/resolve output reloadable schema-1 JSON; validate outputs {valid,errors}.");
            return 0;
        }
        try {
            if (args.length < 2 || !Arrays.asList("defaults", "validate", "resolve").contains(args[1]))
                throw new IllegalArgumentException("Expected config defaults, validate or resolve");
            String action = args[1];
            String[] parseArgs = new String[args.length - 1];
            parseArgs[0] = "analyze";
            System.arraycopy(args, 2, parseArgs, 1, args.length - 2);
            Map<String, String> options = parse(parseArgs);
            for (String key : options.keySet()) {
                boolean setting = VALUES.contains(key) && !Arrays.asList("--input", "--output", "--parameters").contains(key)
                    || CliConfig.OPTIONS.containsKey(key);
                boolean allowed = action.equals("validate") ? key.equals("--parameters") :
                    key.equals("--output") || key.equals("--overwrite") ||
                    (action.equals("resolve") && (setting || key.equals("--parameters")));
                if (!allowed) throw new IllegalArgumentException("Not supported by config " + action + ": " + key);
            }
            AnalyzerParameters p;
            if (action.equals("validate")) {
                p = CliConfig.load(requiredPath(options, "--parameters")).parameters;
                List<String> errors = CliConfig.errors(p);
                out.println(validationJson(errors));
                err.println("Inactive parameters (preserved): " + String.join(", ", CliConfig.inactive(p)));
                return errors.isEmpty() ? 0 : 2;
            }
            p = action.equals("defaults") ? AnalyzerParameters.defaults() : parameters(options);
            String text = CliConfig.json(p);
            if (options.containsKey("--output")) CliConfig.save(requiredPath(options, "--output"), text, options.containsKey("--overwrite"));
            else out.print(text);
            err.println("Inactive parameters (preserved): " + String.join(", ", CliConfig.inactive(p)));
            return 0;
        }
        catch (IllegalArgumentException | IOException ex) {
            if (args.length > 1 && args[1].equals("validate")) {
                try { out.println(validationJson(Arrays.asList(ex.getMessage().split("\n")))); }
                catch (IOException ignored) {}
            }
            err.println("Configuration error: " + ex.getMessage());
            return 2;
        }
    }

    private static String validationJson(List<String> errors) throws IOException
    {
        List<String> quoted = new ArrayList<>();
        for (String error : errors) quoted.add(quote(error));
        return "{\"valid\":" + errors.isEmpty() + ",\"errors\":[" + String.join(",", quoted) + "]}";
    }

    private static boolean supported(Path path)
    {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.matches(".*\\.(png|jpg|jpeg|bmp|gif|tif|tiff|ppm|pgm|pbm)$");
    }

    private static List<Path> enumerate(Path input, final Path output, boolean recursive) throws IOException
    {
        final List<Path> files = new ArrayList<>();
        if (Files.isRegularFile(input)) {
            files.add(input);
            return files;
        }
        Files.walkFileTree(input, EnumSet.noneOf(FileVisitOption.class), recursive ? Integer.MAX_VALUE : 1,
            new SimpleFileVisitor<Path>() {
                @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                {
                    return dir.equals(output) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                {
                    if (attrs.isRegularFile() && supported(file)) files.add(file);
                    return FileVisitResult.CONTINUE;
                }
            });
        Collections.sort(files);
        return files;
    }

    private static Path imageFolder(Path output, Path root, Path input)
    {
        return output.resolve("images").resolve(root.relativize(input));
    }

    static void checkTarget(Path path, boolean overwrite) throws IOException
    {
        for (Path parent = path; parent != null; parent = parent.getParent()) {
            if (Files.isSymbolicLink(parent))
                throw new IllegalArgumentException("Symbolic output paths are not supported: " + parent);
            if (!parent.equals(path) && Files.exists(parent) && !Files.isDirectory(parent))
                throw new IllegalArgumentException("Output parent is not a directory: " + parent);
        }
        if (Files.exists(path) && (!overwrite || !Files.isRegularFile(path)))
            throw new IllegalArgumentException("Output conflict: " + path + " (use --overwrite for generated files)");
    }

    private static void preflight(Path output, List<Path> files, Path root, boolean overwrite) throws IOException
    {
        checkTarget(output.resolve("run-report.json"), overwrite);
        checkTarget(output.resolve("results.xlsx"), overwrite);
        checkTarget(output.resolve("normalized-results.xlsx"), overwrite);
        for (Path file : files)
            for (String name : IMAGE_FILES)
                checkTarget(imageFolder(output, root, file).resolve(name), overwrite);
    }

    private static int analyze(List<Path> files, Path root, Path output, AnalyzerParameters p,
        boolean failFast, PrintStream out, PrintStream err)
    {
        long started = System.nanoTime();
        List<String> records = new ArrayList<>();
        int succeeded = 0, failed = 0;
        boolean stopped = false;
        try {
            Files.createDirectories(output);
            SpreadsheetWriter sheet = Analyzer.createWriterWithNewSheet(null, output.toFile(), "results.xlsx");
            sheet.shouldSaveAfterEveryRow = false;
            SpreadsheetWriter normalized = p.shouldNormalizeOutputData ?
                BatchProcessing.createNormalizedBatchWriterWithNewSheet(null, output.toFile(), "normalized-results.xlsx", p) : null;
            if (normalized != null) normalized.shouldSaveAfterEveryRow = false;
            for (Path file : files) {
                if (stopped) {
                    records.add(record(file, "not-run", false, "Stopped after previous failure", 0));
                    continue;
                }
                long imageStarted = System.nanoTime();
                ArgbBuffer image = null;
                Analyzer.Data data = new Analyzer.Data(p.brightnessLineSegments, p.brightnessLineSegments.length / 2);
                Path staging = null;
                Analyzer.Stats stats = null;
                Exception failure = null;
                err.println("Analyzing " + file);
                try {
                    image = ImageFile.acquireImageForAnalysis(file.toString(), p.effectiveResizeFactor());
                    if (image == null) throw new IOException("Unsupported or unreadable image");
                    stats = Analyzer.analyze(data, file.toFile(), image, p, new ISliceRunner.Series());
                    Path folder = imageFolder(output, root, file);
                    Files.createDirectories(folder);
                    staging = Files.createTempDirectory(output, ".cli-image-");
                    String base = staging.resolve("result").toString();
                    OverlayDataExporter.export(base, p, data, stats, image.width, image.height, image.brightestChannel);
                    Analyzer.drawOverlay(p, data.convexHull, data.skelResult, data.analysisImage.buf,
                        image.pixels, image.pixels, null, image.width, image.height, image.brightestChannel);
                    ImageFile.saveImage(image, "png", base + ".png");
                    for (String name : IMAGE_FILES) {
                        Path generated = staging.resolve(name);
                        Path destination = folder.resolve(name);
                        if (Files.exists(generated))
                            Files.move(generated, destination, StandardCopyOption.REPLACE_EXISTING);
                        else
                            Files.deleteIfExists(destination);
                    }
                }
                catch (Exception ex) {
                    if (ex.getCause() instanceof Error) throw (Error)ex.getCause();
                    failure = ex;
                }
                finally {
                    if (image != null) ImageFile.releaseImage(image);
                    data.nullify();
                    if (staging != null) {
                        for (String name : IMAGE_FILES) Files.deleteIfExists(staging.resolve(name));
                        Files.delete(staging);
                    }
                }
                if (failure == null) {
                    // A shared workbook failure is fatal, rather than a recoverable image failure.
                    succeeded++;
                    records.add(record(file, "success", stats.valid, stats.invalidReason, elapsed(imageStarted)));
                    Analyzer.writeResultToSheet(sheet, p, stats);
                    if (normalized != null) BatchProcessing.writeNormalizedBatchResultToSheet(normalized, p, stats);
                }
                else {
                    failed++;
                    records.add(record(file, "failed", false, failure.toString(), elapsed(imageStarted)));
                    err.println("Failed: " + failure);
                    stopped = failFast;
                }
            }
            sheet.save();
            if (normalized != null) normalized.save();
            else Files.deleteIfExists(output.resolve("normalized-results.xlsx"));
            int code = failed == 0 ? 0 : 1;
            String report = report(records, succeeded, failed, files.size(), code, elapsed(started), null, p);
            saveReport(output, report);
            out.println(report);
            return code;
        }
        catch (Exception ex) {
            err.println("Output/task failure: " + ex);
            try {
                for (int i = records.size(); i < files.size(); i++)
                    records.add(record(files.get(i), "not-run", false, "Task aborted", 0));
                String report = report(records, succeeded, failed, files.size(), 1, elapsed(started), ex.toString(), p);
                saveReport(output, report);
                out.println(report);
            }
            catch (IOException reportError) { err.println("Cannot save report: " + reportError); }
            return 1;
        }
        finally { Analyzer.threadPool.shutdownNow(); }
    }

    private static void saveReport(Path output, String report) throws IOException
    {
        Path temporary = Files.createTempFile(output, ".cli-report-", ".json");
        try {
            Files.write(temporary, report.getBytes(StandardCharsets.UTF_8));
            Files.move(temporary, output.resolve("run-report.json"), StandardCopyOption.REPLACE_EXISTING);
        }
        finally { Files.deleteIfExists(temporary); }
    }

    private static long elapsed(long started)
    {
        return (System.nanoTime() - started) / 1000000;
    }

    private static String quote(String value) throws IOException
    {
        StringWriter writer = new StringWriter();
        OverlayDataExporter.writeJsonString(writer, value);
        return writer.toString();
    }

    private static String record(Path file, String status, boolean valid, String reason, long millis) throws IOException
    {
        return "{\"input\":" + quote(file.toString()) + ",\"status\":" + quote(status)
            + ",\"metricsValid\":" + valid + ",\"reason\":" + quote(reason) + ",\"elapsedMs\":" + millis + "}";
    }

    private static String report(List<String> records, int succeeded, int failed, int total,
        int code, long millis, String error, AnalyzerParameters parameters) throws IOException
    {
        StringWriter settings = new StringWriter();
        OverlayDataExporter.writeUiParameters(settings, parameters);
        return "{\"configuration\":" + CliConfig.json(parameters).trim() + "," + settings.toString() + ",\"skeletonProcessing\":{\"pruneTerminalBranches\":"
            + parameters.shouldPruneSkeleton + ",\"maxTerminalBranchLengthPx\":" + parameters.maxTerminalBranchLength
            + "},\"programVersion\":" + quote(AngioTool.VERSION) + ",\"exitCode\":" + code
            + ",\"succeeded\":" + succeeded + ",\"failed\":" + failed + ",\"notRun\":" + (total - succeeded - failed)
            + ",\"elapsedMs\":" + millis + ",\"taskError\":" + quote(error)
            + ",\"images\":[" + String.join(",", records) + "]}";
    }
}
