package AngioTool;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;

/** Pure filesystem/task planning shared by dry-run and execution. */
final class CliPlan
{
    static final LinkedHashMap<String, String> IMAGE_OUTPUTS = new LinkedHashMap<>();
    static final List<String> FAMILIES = Arrays.asList("overlay", "mask", "raw-skeleton", "pruned-skeleton", "json", "xlsx");
    static {
        IMAGE_OUTPUTS.put("result.png", "overlay");
        IMAGE_OUTPUTS.put("result vessel-mask.png", "mask");
        IMAGE_OUTPUTS.put("result raw-skeleton.png", "raw-skeleton");
        IMAGE_OUTPUTS.put("result pruned-skeleton.png", "pruned-skeleton");
        IMAGE_OUTPUTS.put("result overlay-data.json", "json");
        IMAGE_OUTPUTS.put("result normalized-data.json", "normalized-json");
    }

    static final class Root
    {
        final Path path;
        final boolean directory;
        final String namespace;
        Root(Path path)
        {
            this.path = path;
            directory = Files.isDirectory(path);
            String name = path.getFileName() == null ? "root" : path.getFileName().toString();
            namespace = name + "-" + digest(path.toString());
        }
        Path relative(Path input) { return directory ? path.relativize(input) : input.getFileName(); }
    }

    static final class Entry
    {
        final Path input;
        final Root root;
        final Path relative;
        final Path folder;
        Entry(Path input, Root root, Path output, boolean multiple)
        {
            this.input = input; this.root = root;
            relative = root.relative(input);
            folder = (multiple ? output.resolve("images").resolve(root.namespace) : output.resolve("images")).resolve(relative);
        }
    }

    final List<Entry> entries;
    final List<Root> roots;
    final Path output;
    final Set<String> exports;
    final List<String> imageWrites = new ArrayList<>();
    final List<String> imageDeletes = new ArrayList<>();
    final List<String> sharedWrites = new ArrayList<>();
    final List<String> sharedDeletes = new ArrayList<>();
    final boolean explicitExports;
    final boolean overwrite;
    final boolean failFast;
    final String configurationSource;
    final String sourceVersion;
    final List<String> overrides;

    CliPlan(Cli.Options options, AnalyzerParameters p) throws IOException
    {
        output = Cli.requiredPath(options, "--output").toFile().getCanonicalFile().toPath();
        overwrite = options.containsKey("--overwrite");
        failFast = options.containsKey("--fail-fast");
        explicitExports = options.containsKey("--exports");
        exports = new LinkedHashSet<>();
        if (explicitExports) {
            for (String family : options.get("--exports").split(",", -1)) {
                if (!FAMILIES.contains(family) || !exports.add(family))
                    throw new IllegalArgumentException("Invalid or repeated export family: " + family);
            }
        }
        else exports.addAll(FAMILIES);
        if (p.shouldNormalizeOutputData && exports.contains("json")) exports.add("normalized-json");
        for (Map.Entry<String, String> output : IMAGE_OUTPUTS.entrySet()) {
            if (exports.contains(output.getValue())) imageWrites.add(output.getKey());
            else if (!explicitExports) imageDeletes.add(output.getKey());
        }
        sharedWrites.add("run-report.json");
        if (exports.contains("xlsx")) {
            sharedWrites.add("results.xlsx");
            if (p.shouldNormalizeOutputData) sharedWrites.add("normalized-results.xlsx");
            else if (!explicitExports) sharedDeletes.add("normalized-results.xlsx");
        }
        configurationSource = options.configurationSource;
        sourceVersion = options.sourceVersion;
        overrides = new ArrayList<>();
        for (String option : options.keySet())
            if (CliConfig.OPTIONS.containsKey(option) || Arrays.asList("--mode", "--skeleton", "--scale", "--resize", "--prune", "--merge", "--normalize").contains(option))
                overrides.add(option);
        Collections.sort(overrides);
        TreeSet<Path> rootPaths = new TreeSet<>();
        for (String input : options.all("--input")) rootPaths.add(Paths.get(input).toRealPath());
        for (String file : options.all("--input-list")) {
            Path list = Paths.get(file).toAbsolutePath().normalize();
            List<String> lines = Files.readAllLines(list, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (i == 0 && line.startsWith("\ufeff")) line = line.substring(1);
                if (!line.trim().isEmpty()) rootPaths.add(list.getParent().resolve(line).normalize().toRealPath());
            }
        }
        if (rootPaths.isEmpty()) throw new IllegalArgumentException("Required: --input or --input-list");
        roots = new ArrayList<>();
        Set<String> namespaces = new HashSet<>();
        for (Path path : rootPaths) {
            if (!Files.isDirectory(path) && !Files.isRegularFile(path)) throw new IllegalArgumentException("Input is not a file or directory: " + path);
            if (path.startsWith(output)) throw new IllegalArgumentException("Output must not contain an input: " + path);
            Root root = new Root(path);
            if (!namespaces.add(root.namespace)) throw new IllegalArgumentException("Input namespace collision: " + path);
            roots.add(root);
        }
        Map<Path, Root> owners = new TreeMap<>();
        for (Root root : roots) {
            for (Path file : Cli.enumerate(root.path, output, options.containsKey("--recursive"))) {
                Path canonical = file.toRealPath();
                Root owner = owners.get(canonical);
                if (owner == null || moreSpecific(root, owner)) owners.put(canonical, root);
            }
        }
        List<Pattern> includes = patterns(options.all("--include"));
        List<Pattern> excludes = patterns(options.all("--exclude"));
        entries = new ArrayList<>();
        Set<Path> folders = new HashSet<>();
        for (Map.Entry<Path, Root> owner : owners.entrySet()) {
            String relative = owner.getValue().relative(owner.getKey()).toString().replace(File.separatorChar, '/');
            if ((!includes.isEmpty() && !matches(includes, relative)) || matches(excludes, relative)) continue;
            Entry entry = new Entry(owner.getKey(), owner.getValue(), output, roots.size() > 1);
            if (!folders.add(entry.folder)) throw new IllegalArgumentException("Output mapping collision: " + entry.folder);
            entries.add(entry);
        }
        if (entries.isEmpty()) throw new IllegalArgumentException("No supported images found after filtering");
        preflight();
    }

    private static boolean moreSpecific(Root candidate, Root current)
    {
        if (candidate.directory != current.directory) return !candidate.directory;
        if (candidate.path.getNameCount() != current.path.getNameCount())
            return candidate.path.getNameCount() > current.path.getNameCount();
        return candidate.path.compareTo(current.path) < 0;
    }

    private static String digest(String value)
    {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < 6; i++) text.append(String.format(Locale.ROOT, "%02x", bytes[i] & 255));
            return text.toString();
        }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    private static List<Pattern> patterns(List<String> globs)
    {
        List<Pattern> patterns = new ArrayList<>();
        for (String glob : globs) {
            if (glob.isEmpty()) throw new IllegalArgumentException("Empty include/exclude pattern");
            StringBuilder regex = new StringBuilder("^");
            for (int i = 0; i < glob.length(); i++) {
                char ch = glob.charAt(i);
                if (ch == '*') {
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                        i++;
                        if (i + 1 < glob.length() && glob.charAt(i + 1) == '/') { i++; regex.append("(?:.*/)?"); }
                        else regex.append(".*");
                    }
                    else regex.append("[^/]*");
                }
                else if (ch == '?') regex.append("[^/]");
                else regex.append(Pattern.quote(String.valueOf(ch)));
            }
            patterns.add(Pattern.compile(regex.append('$').toString()));
        }
        return patterns;
    }

    private static boolean matches(List<Pattern> patterns, String relative)
    {
        for (Pattern pattern : patterns) if (pattern.matcher(relative).matches()) return true;
        return false;
    }

    void preflight() throws IOException
    {
        for (String name : sharedWrites) Cli.checkTarget(output.resolve(name), overwrite);
        for (String name : sharedDeletes) Cli.checkTarget(output.resolve(name), overwrite);
        for (Entry entry : entries) checkEntry(entry);
    }

    void checkEntry(Entry entry) throws IOException
    {
        for (String name : imageWrites) Cli.checkTarget(entry.folder.resolve(name), overwrite);
        for (String name : imageDeletes) Cli.checkTarget(entry.folder.resolve(name), overwrite);
    }

    List<String> notUpdated(Entry entry, Collection<String> written)
    {
        List<String> retained = new ArrayList<>();
        for (String name : IMAGE_OUTPUTS.keySet()) {
            Path path = entry.folder.resolve(name);
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !written.contains(path.toString())) retained.add(path.toString());
        }
        return retained;
    }

    String provenance() throws IOException
    {
        return "{\"source\":" + Cli.quote(configurationSource) + ",\"sourceVersion\":" + Cli.quote(sourceVersion)
            + ",\"cliOverrides\":" + Cli.strings(overrides) + "}";
    }

    String json(AnalyzerParameters p) throws IOException
    {
        List<String> tasks = new ArrayList<>();
        for (Entry entry : entries) {
            List<String> writes = new ArrayList<>();
            for (String name : imageWrites) writes.add("{\"path\":" + Cli.quote(entry.folder.resolve(name).toString())
                + ",\"conditional\":" + name.contains("skeleton.png") + "}");
            List<String> deletes = new ArrayList<>();
            for (String name : imageDeletes) deletes.add(entry.folder.resolve(name).toString());
            tasks.add("{\"input\":" + Cli.quote(entry.input.toString()) + ",\"root\":" + Cli.quote(entry.root.path.toString())
                + ",\"relativeInput\":" + Cli.quote(entry.relative.toString().replace(File.separatorChar, '/'))
                + ",\"outputDirectory\":" + Cli.quote(entry.folder.toString()) + ",\"writes\":[" + String.join(",", writes)
                + "],\"mayDelete\":" + Cli.strings(deletes) + ",\"preserve\":" + Cli.strings(preserved(entry)) + "}");
        }
        List<String> shared = new ArrayList<>(), deletes = new ArrayList<>();
        for (String name : sharedWrites) shared.add(output.resolve(name).toString());
        for (String name : sharedDeletes) deletes.add(output.resolve(name).toString());
        return "{\"dryRun\":true,\"configuration\":" + CliConfig.json(p).trim() + ",\"configurationSource\":" + provenance()
            + ",\"output\":" + Cli.quote(output.toString()) + ",\"sharedWrites\":" + Cli.strings(shared)
            + ",\"sharedMayDelete\":" + Cli.strings(deletes) + ",\"tasks\":[" + String.join(",", tasks)
            + "],\"limits\":\"No decoding, analysis or writes; permissions, disk capacity and later filesystem changes are not guaranteed. Skeleton artifacts are conditional. Unlisted user files are preserved.\"}";
    }

    private List<String> preserved(Entry entry)
    {
        List<String> preserved = new ArrayList<>();
        for (String name : IMAGE_OUTPUTS.keySet())
            if (!imageWrites.contains(name) && !imageDeletes.contains(name)) preserved.add(entry.folder.resolve(name).toString());
        return preserved;
    }
}
