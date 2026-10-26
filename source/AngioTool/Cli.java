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
    private static final Set<String> FLAGS = new HashSet<>(Arrays.asList(
        "--recursive", "--overwrite", "--fail-fast", "--dry-run"));
    private static final Set<String> VALUES = new HashSet<>(Arrays.asList(
        "--input", "--output", "--parameters", "--mode", "--scale", "--resize",
        "--skeleton", "--prune", "--merge", "--normalize", "--exports", "--input-list", "--include", "--exclude"));

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
            out.println("Usage: java -jar AngioTool2.3.2.jar analyze --input FILE|DIR --output DIR [options]\n"
                + "  --parameters FILE   Load schema-1 JSON or an overlay-data.json snapshot\n"
                + "  --input FILE|DIR    Repeat for multiple roots; one shared configuration\n"
                + "  --input-list FILE   UTF-8 paths, relative to list directory (repeatable)\n"
                + "  --include GLOB      Repeatable; relative to owning root; **/*.png includes root files\n"
                + "  --exclude GLOB      Repeatable; exclusion takes priority\n"
                + "  --exports LIST      overlay,mask,raw-skeleton,pruned-skeleton,json,xlsx\n"
                + "  --dry-run           Print JSON plan without decoding or writing\n"
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
            Options options = parse(args);
            AnalyzerParameters params = parameters(options);
            CliPlan plan = new CliPlan(options, params);
            if (options.containsKey("--dry-run")) {
                out.println(plan.json(params));
                return 0;
            }
            return analyze(plan, params, out, err);
        }
        catch (IllegalArgumentException | IOException ex) {
            err.println("Configuration error: " + ex.getMessage());
            return 2;
        }
    }

    static final class Options extends LinkedHashMap<String, String>
    {
        final Map<String, List<String>> lists = new LinkedHashMap<>();
        String configurationSource = "defaults";
        String sourceVersion;
        List<String> all(String key)
        {
            List<String> values = lists.get(key);
            return values == null ? Collections.<String>emptyList() : values;
        }
    }

    private static Options parse(String[] args)
    {
        if (args.length == 0 || !"analyze".equals(args[0]))
            throw new IllegalArgumentException("Expected analyze; use --help");
        Options options = new Options();
        for (int i = 1; i < args.length; i++) {
            String key = args[i];
            if (!FLAGS.contains(key) && !VALUES.contains(key) && !CliConfig.OPTIONS.containsKey(key))
                throw new IllegalArgumentException("Unknown option: " + key);
            boolean repeated = Arrays.asList("--input", "--input-list", "--include", "--exclude").contains(key);
            if (options.containsKey(key) && !repeated)
                throw new IllegalArgumentException("Repeated option: " + key);
            String value = "true";
            if (VALUES.contains(key) || CliConfig.OPTIONS.containsKey(key)) {
                if (++i >= args.length || args[i].startsWith("--"))
                    throw new IllegalArgumentException("Missing value for " + key);
                value = args[i];
            }
            options.put(key, value);
            if (repeated) {
                if (!options.lists.containsKey(key)) options.lists.put(key, new ArrayList<String>());
                options.lists.get(key).add(value);
            }
        }
        return options;
    }

    static Path requiredPath(Map<String, String> options, String key)
    {
        if (!options.containsKey(key))
            throw new IllegalArgumentException("Required: " + key);
        return Paths.get(options.get(key)).toAbsolutePath().normalize();
    }

    private static AnalyzerParameters parameters(Map<String, String> options) throws IOException
    {
        AnalyzerParameters p = AnalyzerParameters.defaults();
        if (options.containsKey("--parameters")) {
            Path source = requiredPath(options, "--parameters").toRealPath();
            CliConfig.Loaded loaded = CliConfig.load(source);
            p = loaded.parameters;
            if (options instanceof Options) {
                ((Options)options).configurationSource = source.toString();
                ((Options)options).sourceVersion = loaded.sourceVersion;
            }
        }
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
                boolean setting = Arrays.asList("--mode", "--skeleton", "--scale", "--resize", "--prune", "--merge", "--normalize").contains(key)
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

    static List<Path> enumerate(Path input, final Path output, boolean recursive) throws IOException
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

    private static int analyze(CliPlan plan, AnalyzerParameters p, PrintStream out, PrintStream err)
    {
        long started = System.nanoTime();
        List<String> records = new ArrayList<>();
        List<String> sharedWritten = new ArrayList<>();
        int succeeded = 0, failed = 0;
        boolean stopped = false;
        try {
            plan.preflight();
            Files.createDirectories(plan.output);
            SpreadsheetWriter sheet = plan.exports.contains("xlsx") ?
                Analyzer.createWriterWithNewSheet(null, plan.output.toFile(), "results.xlsx") : null;
            if (sheet != null) sheet.shouldSaveAfterEveryRow = false;
            SpreadsheetWriter normalized = sheet != null && p.shouldNormalizeOutputData ?
                BatchProcessing.createNormalizedBatchWriterWithNewSheet(null, plan.output.toFile(), "normalized-results.xlsx", p) : null;
            if (normalized != null) normalized.shouldSaveAfterEveryRow = false;
            for (CliPlan.Entry entry : plan.entries) {
                if (stopped) {
                    records.add(record(plan, entry, "not-run", false, "Stopped after previous failure", 0, Collections.<String>emptyList()));
                    continue;
                }
                long imageStarted = System.nanoTime();
                ArgbBuffer image = null;
                Analyzer.Data data = new Analyzer.Data(p.brightnessLineSegments, p.brightnessLineSegments.length / 2);
                Path staging = null;
                Analyzer.Stats stats = null;
                Exception failure = null;
                List<String> written = new ArrayList<>();
                err.println("Analyzing " + entry.input);
                try {
                    plan.checkEntry(entry);
                    image = ImageFile.acquireImageForAnalysis(entry.input.toString(), p.effectiveResizeFactor());
                    if (image == null) throw new IOException("Unsupported or unreadable image");
                    stats = Analyzer.analyze(data, entry.input.toFile(), image, p, new ISliceRunner.Series());
                    staging = Files.createTempDirectory(plan.output, ".cli-image-");
                    String base = staging.resolve("result").toString();
                    OverlayDataExporter.exportSelected(base, p, data, stats, image.width, image.height, image.brightestChannel, plan.exports);
                    if (plan.exports.contains("overlay")) {
                        Analyzer.drawOverlay(p, data.convexHull, data.skelResult, data.analysisImage.buf,
                            image.pixels, image.pixels, null, image.width, image.height, image.brightestChannel);
                        ImageFile.saveImage(image, "png", base + ".png");
                    }
                    for (String name : plan.imageWrites) {
                        Path generated = staging.resolve(name);
                        Path destination = entry.folder.resolve(name);
                        if (Files.exists(generated)) {
                            Cli.checkTarget(destination, plan.overwrite);
                            Files.createDirectories(entry.folder);
                            if (plan.overwrite) Files.move(generated, destination, StandardCopyOption.REPLACE_EXISTING);
                            else Files.move(generated, destination);
                            written.add(destination.toString());
                        }
                        else if (!plan.explicitExports) {
                            Cli.checkTarget(destination, plan.overwrite);
                            Files.deleteIfExists(destination);
                        }
                    }
                    for (String name : plan.imageDeletes) {
                        Path destination = entry.folder.resolve(name);
                        Cli.checkTarget(destination, plan.overwrite);
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
                        for (String name : CliPlan.IMAGE_OUTPUTS.keySet()) Files.deleteIfExists(staging.resolve(name));
                        Files.delete(staging);
                    }
                }
                if (failure == null) {
                    succeeded++;
                    records.add(record(plan, entry, "success", stats.valid, stats.invalidReason, elapsed(imageStarted), written));
                    // Shared output failures abort the task rather than pretending to be bad images.
                    if (sheet != null) Analyzer.writeResultToSheet(sheet, p, stats);
                    if (normalized != null) BatchProcessing.writeNormalizedBatchResultToSheet(normalized, p, stats);
                }
                else {
                    failed++;
                    records.add(record(plan, entry, "failed", false, failure.toString(), elapsed(imageStarted), written));
                    err.println("Failed: " + failure);
                    stopped = plan.failFast;
                }
            }
            // A shared file was already created for headers; recheck its parent/link safety.
            if (sheet != null) {
                checkTarget(plan.output.resolve("results.xlsx"), true);
                sheet.save();
                sharedWritten.add(plan.output.resolve("results.xlsx").toString());
            }
            if (normalized != null) {
                checkTarget(plan.output.resolve("normalized-results.xlsx"), true);
                normalized.save();
                sharedWritten.add(plan.output.resolve("normalized-results.xlsx").toString());
            }
            for (String name : plan.sharedDeletes) {
                checkTarget(plan.output.resolve(name), plan.overwrite);
                Files.deleteIfExists(plan.output.resolve(name));
            }
            int code = failed == 0 ? 0 : 1;
            finishReport(plan, records, sharedWritten, succeeded, failed, code, elapsed(started), null, p, out);
            return code;
        }
        catch (Exception ex) {
            err.println("Output/task failure: " + ex);
            try {
                for (int i = records.size(); i < plan.entries.size(); i++)
                    records.add(record(plan, plan.entries.get(i), "not-run", false, "Task aborted", 0, Collections.<String>emptyList()));
                finishReport(plan, records, sharedWritten, succeeded, failed, 1, elapsed(started), ex.toString(), p, out);
            }
            catch (IOException reportError) { err.println("Cannot save report: " + reportError); }
            return 1;
        }
        finally { Analyzer.threadPool.shutdownNow(); }
    }

    private static void finishReport(CliPlan plan, List<String> records, List<String> sharedWritten,
        int succeeded, int failed, int code, long millis, String error, AnalyzerParameters p, PrintStream out) throws IOException
    {
        checkTarget(plan.output.resolve("run-report.json"), plan.overwrite);
        List<String> outputs = new ArrayList<>(sharedWritten);
        outputs.add(plan.output.resolve("run-report.json").toString());
        String text = report(plan, records, outputs, succeeded, failed, code, millis, error, p);
        saveReport(plan.output, text, plan.overwrite);
        out.println(text);
    }

    private static void saveReport(Path output, String report, boolean overwrite) throws IOException
    {
        Path temporary = Files.createTempFile(output, ".cli-report-", ".json");
        try {
            Files.write(temporary, report.getBytes(StandardCharsets.UTF_8));
            if (overwrite) Files.move(temporary, output.resolve("run-report.json"), StandardCopyOption.REPLACE_EXISTING);
            else Files.move(temporary, output.resolve("run-report.json"));
        }
        finally { Files.deleteIfExists(temporary); }
    }

    private static long elapsed(long started)
    {
        return (System.nanoTime() - started) / 1000000;
    }

    static String quote(String value) throws IOException
    {
        StringWriter writer = new StringWriter();
        OverlayDataExporter.writeJsonString(writer, value);
        return writer.toString();
    }

    static String strings(Collection<String> values) throws IOException
    {
        List<String> quoted = new ArrayList<>();
        for (String value : values) quoted.add(quote(value));
        return "[" + String.join(",", quoted) + "]";
    }

    private static String record(CliPlan plan, CliPlan.Entry entry, String status, boolean valid, String reason,
        long millis, Collection<String> written) throws IOException
    {
        return "{\"input\":" + quote(entry.input.toString()) + ",\"status\":" + quote(status)
            + ",\"metricsValid\":" + valid + ",\"reason\":" + quote(reason) + ",\"elapsedMs\":" + millis
            + ",\"outputDirectory\":" + quote(entry.folder.toString()) + ",\"relativeOutputDirectory\":"
            + quote(plan.output.relativize(entry.folder).toString()) + ",\"outputs\":" + strings(written)
            + ",\"notUpdated\":" + strings(plan.notUpdated(entry, written)) + "}";
    }

    private static String report(CliPlan plan, List<String> records, List<String> sharedWritten,
        int succeeded, int failed, int code, long millis, String error, AnalyzerParameters parameters) throws IOException
    {
        StringWriter settings = new StringWriter();
        OverlayDataExporter.writeUiParameters(settings, parameters);
        List<String> retained = new ArrayList<>();
        for (String name : Arrays.asList("results.xlsx", "normalized-results.xlsx")) {
            Path path = plan.output.resolve(name);
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !sharedWritten.contains(path.toString())) retained.add(path.toString());
        }
        return "{\"configuration\":" + CliConfig.json(parameters).trim() + "," + settings.toString()
            + ",\"configurationSource\":" + plan.provenance() + ",\"outputs\":" + strings(sharedWritten)
            + ",\"notUpdated\":" + strings(retained) + ",\"skeletonProcessing\":{\"pruneTerminalBranches\":"
            + parameters.shouldPruneSkeleton + ",\"maxTerminalBranchLengthPx\":" + parameters.maxTerminalBranchLength
            + "},\"programVersion\":" + quote(AngioTool.VERSION) + ",\"exitCode\":" + code
            + ",\"succeeded\":" + succeeded + ",\"failed\":" + failed + ",\"notRun\":" + (plan.entries.size() - succeeded - failed)
            + ",\"elapsedMs\":" + millis + ",\"taskError\":" + quote(error)
            + ",\"images\":[" + String.join(",", records) + "]}";
    }
}
