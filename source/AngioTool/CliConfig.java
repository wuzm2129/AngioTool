package AngioTool;

import Algorithms.PreprocessColor;
import Pixels.Rgb;
import Utils.Misc;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Versioned CLI parameter contract. Names below are stable schema-1 names. */
final class CliConfig
{
    static final class Parameter
    {
        final Field field;
        final String name;
        final String option;
        final String unit;
        final String enabledBy;

        Parameter(String name, String unit, String enabledBy)
        {
            this.name = name;
            this.unit = unit;
            this.enabledBy = enabledBy;
            String optionName = name.startsWith("should") ?
                Character.toLowerCase(name.charAt(6)) + name.substring(7) : name;
            this.option = "--" + optionName.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
            try { field = AnalyzerParameters.class.getField(name); }
            catch (NoSuchFieldException ex) { throw new ExceptionInInitializerError(ex); }
        }

        Object get(AnalyzerParameters p)
        {
            try { return field.get(p); }
            catch (IllegalAccessException ex) { throw new IllegalStateException(ex); }
        }

        void set(AnalyzerParameters p, Object value)
        {
            try { field.set(p, value); }
            catch (IllegalAccessException ex) { throw new IllegalStateException(ex); }
        }
    }

    static final LinkedHashMap<String, Parameter> PARAMETERS = new LinkedHashMap<>();
    static final Map<String, Parameter> OPTIONS = new LinkedHashMap<>();
    static {
        // This explicit inventory defines schema 1; adding a Java field cannot silently extend it.
        add("shouldUseBinaryMask", "boolean", "always");
        add("binaryRemoveSmallParticles", "boolean", "binary");
        add("binarySmallParticleRadius", "analysis-image radius px", "binaryRemoveSmallParticles");
        add("binaryFillHoles", "boolean", "binary");
        add("binaryHoleRadius", "analysis-image radius px", "binaryFillHoles");
        add("shouldResizeImage", "boolean", "tubeness");
        add("resizingFactor", "multiplier", "shouldResizeImage");
        add("shouldRemapColors", "boolean", "tubeness");
        add("hueTransformWeight", "weight", "shouldRemapColors");
        add("brightnessTransformWeight", "weight", "shouldRemapColors");
        add("targetRemapColor", "#RRGGBB", "shouldRemapColors");
        add("voidRemapColor", "#RRGGBB", "shouldRemapColors");
        add("saturationFactor", "fraction [0,1]", "shouldRemapColors");
        add("brightnessLineSegments", "x,y pairs in [0,100]", "shouldRemapColors");
        add("shouldFillBrightShapes", "boolean", "tubeness");
        add("brightShapeThresholdFactor", "multiplier", "shouldFillBrightShapes");
        add("shouldApplyMinBoxness", "boolean", "tubeness");
        add("minBoxness", "fraction [0,1]", "shouldApplyMinBoxness");
        add("shouldApplyMinAreaLength", "boolean", "tubeness");
        add("minAreaLengthRatio", "ratio", "shouldApplyMinAreaLength");
        add("shouldRemoveSmallParticles", "boolean", "tubeness");
        add("removeSmallParticlesThreshold", "analysis-image radius px", "shouldRemoveSmallParticles");
        add("shouldFillHoles", "boolean", "tubeness");
        add("fillHolesValue", "analysis-image radius px", "shouldFillHoles");
        add("sigmas", "analysis-image px list", "tubeness");
        add("thresholdHigh", "intensity [0,255]", "tubeness");
        add("thresholdLow", "intensity [0,255]", "tubeness");
        add("shouldUseFastSkeletonizer", "boolean: true=Zha84, false=Lee94", "always");
        add("shouldCapSkelIterations", "boolean", "always");
        add("maxSkelIterations", "iterations", "shouldCapSkelIterations");
        add("shouldPruneSkeleton", "boolean", "always");
        add("maxTerminalBranchLength", "analysis-image px", "shouldPruneSkeleton");
        add("shouldMergeNearbyJunctions", "boolean", "always");
        add("maxJunctionMergeDistance", "analysis-image px", "shouldMergeNearbyJunctions");
        add("shouldApplyLinearScale", "boolean", "always");
        add("linearScalingFactor", "multiplier", "shouldApplyLinearScale");
        add("shouldNormalizeOutputData", "boolean", "always");
        add("shouldDrawOutline", "boolean", "always");
        add("outlineColor", "#RRGGBB", "shouldDrawOutline");
        add("outlineSize", "analysis-image px", "shouldDrawOutline");
        add("shouldDrawSkeleton", "boolean", "always");
        add("skeletonColor", "#RRGGBB", "shouldDrawSkeleton");
        add("skeletonSize", "analysis-image px", "shouldDrawSkeleton");
        add("shouldDrawBranchPoints", "boolean", "always");
        add("branchingPointsColor", "#RRGGBB", "shouldDrawBranchPoints");
        add("branchingPointsSize", "analysis-image px", "shouldDrawBranchPoints");
        add("shouldDrawConvexHull", "boolean", "always");
        add("convexHullColor", "#RRGGBB", "shouldDrawConvexHull");
        add("convexHullSize", "analysis-image px", "shouldDrawConvexHull");
        add("shouldIsolateBrightestChannelInOutput", "boolean", "always");
        add("shouldExpandOutputToGrayScale", "boolean", "shouldIsolateBrightestChannelInOutput");
        add("shouldComputeLacunarity", "boolean", "always");
        add("shouldComputeThickness", "boolean", "always");
    }

    private static void add(String name, String unit, String enabledBy)
    {
        Parameter p = new Parameter(name, unit, enabledBy);
        PARAMETERS.put(name, p);
        if (OPTIONS.put(p.option, p) != null) throw new ExceptionInInitializerError("Duplicate CLI parameter");
    }

    static final class Loaded
    {
        final AnalyzerParameters parameters;
        final String source;
        final String sourceVersion;
        Loaded(AnalyzerParameters p, String source, String version)
        {
            parameters = p; this.source = source; sourceVersion = version;
        }
    }

    static Loaded load(Path path) throws IOException
    {
        Map<String, Object> root;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            root = new AnalyzerParameterImporter.JsonReader(reader).readConfiguration(true);
        }
        if (!root.containsKey("schemaVersion")) {
            if (root.containsKey("parameters")) throw new IOException("schemaVersion is required for independent configuration");
            AnalyzerParameterImporter.Result old = AnalyzerParameterImporter.load(path.toFile());
            return new Loaded(old.parameters, path.toString(), old.programVersion);
        }
        List<String> errors = new ArrayList<>();
        for (String key : root.keySet())
            if (!Arrays.asList("schemaVersion", "parameters", "programVersion").contains(key))
                errors.add(key + ": unknown configuration field");
        if (!(root.get("schemaVersion") instanceof Number) || ((Number)root.get("schemaVersion")).doubleValue() != 1)
            errors.add("schemaVersion: unsupported version (expected 1)");
        if (root.containsKey("programVersion") && !(root.get("programVersion") instanceof String))
            errors.add("programVersion: expected string");
        AnalyzerParameters p = AnalyzerParameters.defaults();
        Object values = root.get("parameters");
        if (!(values instanceof Map)) errors.add("parameters: expected object");
        else {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>)values).entrySet()) {
                String name = entry.getKey().toString();
                Parameter param = PARAMETERS.get(name);
                if (param == null) errors.add("parameters." + name + ": unknown field");
                else {
                    try { param.set(p, convert(entry.getValue(), param.field.getType(), "parameters." + name)); }
                    catch (IllegalArgumentException ex) { errors.add(ex.getMessage()); }
                }
            }
        }
        if (!errors.isEmpty()) throw new IOException(String.join("\n", errors));
        return new Loaded(p, path.toString(), (String)root.get("programVersion"));
    }

    private static Object convert(Object value, Class<?> type, String path)
    {
        if (type == boolean.class && value instanceof Boolean) return value;
        if ((type == double.class || type == int.class) && value instanceof Number) {
            double number = ((Number)value).doubleValue();
            if (Double.isFinite(number)) {
                if (type == double.class) return number;
                if (number == Math.rint(number) && number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE)
                    return (int)number;
            }
        }
        if (type == Rgb.class && value instanceof String && ((String)value).matches("#?[a-fA-F0-9]{6}"))
            return new Rgb((String)value);
        if (type.isArray() && value instanceof List && ((List<?>)value).size() <= 4096) {
            List<?> list = (List<?>)value;
            if (type == double[].class) {
                double[] array = new double[list.size()];
                for (int i = 0; i < array.length; i++) array[i] = (Double)convert(list.get(i), double.class, path + "[" + i + "]");
                return array;
            }
            int[] array = new int[list.size()];
            for (int i = 0; i < array.length; i++) array[i] = (Integer)convert(list.get(i), int.class, path + "[" + i + "]");
            return array;
        }
        throw new IllegalArgumentException(path + ": invalid type or representation");
    }

    static Object parseValue(Parameter p, String text)
    {
        Class<?> type = p.field.getType();
        Object value = text;
        try {
            if (type == boolean.class) {
                if (!text.equals("true") && !text.equals("false")) throw new IllegalArgumentException();
                value = Boolean.valueOf(text);
            }
            else if (type == double.class || type == int.class) value = Double.valueOf(text);
            else if (type.isArray()) {
                List<Double> list = new ArrayList<>();
                if (!text.isEmpty()) for (String token : text.split(",", -1)) list.add(Double.valueOf(token.trim()));
                value = list;
            }
            return convert(value, type, p.option);
        }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException(p.option + ": invalid value " + text); }
    }

    static void apply(AnalyzerParameters p, Map<String, String> options)
    {
        Map<String, Object> assignments = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : options.entrySet()) {
            Parameter full = OPTIONS.get(entry.getKey());
            String value = entry.getValue();
            if (full != null) { assign(assignments, full.name, parseValue(full, value)); continue; }
            switch (entry.getKey()) {
                case "--mode":
                    if (!value.equals("binary") && !value.equals("tubeness")) throw new IllegalArgumentException("Mode must be binary or tubeness");
                    assign(assignments, "shouldUseBinaryMask", value.equals("binary")); break;
                case "--skeleton":
                    if (!value.equals("zha84") && !value.equals("lee94")) throw new IllegalArgumentException("Skeleton must be zha84 or lee94");
                    assign(assignments, "shouldUseFastSkeletonizer", value.equals("zha84")); break;
                case "--scale": case "--resize":
                    String field = entry.getKey().equals("--scale") ? "linearScalingFactor" : "resizingFactor";
                    double factor = (Double)parseValue(PARAMETERS.get(field), value);
                    if (factor <= 0) throw new IllegalArgumentException(entry.getKey() + ": must be positive");
                    assign(assignments, field, factor);
                    assign(assignments, field.equals("resizingFactor") ? "shouldResizeImage" : "shouldApplyLinearScale", factor != 1); break;
                case "--prune": case "--merge":
                    boolean prune = entry.getKey().equals("--prune");
                    assign(assignments, prune ? "shouldPruneSkeleton" : "shouldMergeNearbyJunctions", !value.equals("off"));
                    if (!value.equals("off")) {
                        String name = prune ? "maxTerminalBranchLength" : "maxJunctionMergeDistance";
                        double length = (Double)parseValue(PARAMETERS.get(name), value);
                        if (length < 0) throw new IllegalArgumentException(entry.getKey() + ": must be nonnegative");
                        assign(assignments, name, length);
                    }
                    break;
                case "--normalize": assign(assignments, "shouldNormalizeOutputData", parseValue(PARAMETERS.get("shouldNormalizeOutputData"), value)); break;
                default: break;
            }
        }
        for (Map.Entry<String, Object> entry : assignments.entrySet()) PARAMETERS.get(entry.getKey()).set(p, entry.getValue());
    }

    private static void assign(Map<String, Object> assignments, String name, Object value)
    {
        if (assignments.containsKey(name)) throw new IllegalArgumentException("Conflicting assignments to parameters." + name);
        assignments.put(name, value);
    }

    static boolean active(AnalyzerParameters p, Parameter param)
    {
        if (param.enabledBy.equals("always")) return true;
        if (param.enabledBy.equals("binary")) return p.shouldUseBinaryMask;
        if (param.enabledBy.equals("tubeness")) return !p.shouldUseBinaryMask;
        Parameter parent = PARAMETERS.get(param.enabledBy);
        return active(p, parent) && Boolean.TRUE.equals(parent.get(p));
    }

    static List<String> errors(AnalyzerParameters p)
    {
        List<String> errors = new ArrayList<>();
        for (Parameter param : PARAMETERS.values()) {
            Object value = param.get(p);
            String path = "parameters." + param.name;
            if (value instanceof Double && !Double.isFinite((Double)value)) errors.add(path + ": must be finite");
            if (value instanceof double[]) {
                double[] array = (double[])value;
                for (int i = 0; i < array.length; i++)
                    if (!Double.isFinite(array[i]) || (active(p, param) && array[i] <= 0))
                        errors.add(path + "[" + i + "]: must be finite" + (active(p, param) ? " and positive" : ""));
                if (active(p, param) && array.length == 0) errors.add(path + ": at least one scale required");
            }
            if (!active(p, param)) continue;
            if (value instanceof Double) {
                double n = (Double)value;
                if ((param.unit.equals("multiplier") || param.unit.contains("radius") ||
                        Arrays.asList("outlineSize", "skeletonSize", "branchingPointsSize", "convexHullSize").contains(param.name)) && n <= 0)
                    errors.add(path + ": must be positive");
                if ((param.name.equals("maxTerminalBranchLength") || param.name.equals("maxJunctionMergeDistance") ||
                        param.name.equals("minAreaLengthRatio")) && n < 0) errors.add(path + ": must be nonnegative");
                if (param.unit.equals("fraction [0,1]") && (n < 0 || n > 1)) errors.add(path + ": must be in [0,1]");
            }
            if ((param.name.equals("thresholdLow") || param.name.equals("thresholdHigh")) && ((Integer)value < 0 || (Integer)value > 255))
                errors.add(path + ": must be in [0,255]");
            if (param.name.equals("maxSkelIterations") && (Integer)value <= 0) errors.add(path + ": must be positive");
        }
        for (String name : Arrays.asList("resizingFactor", "linearScalingFactor", "saturationFactor", "brightShapeThresholdFactor", "minBoxness"))
            if (!Double.isFinite((Double)PARAMETERS.get(name).get(p) * 100)) errors.add("parameters." + name + ": percentage representation overflows");
        if (p.brightnessLineSegments.length % 2 != 0) errors.add("parameters.brightnessLineSegments: expected x,y pairs");
        for (int n : p.brightnessLineSegments)
            if (n < 0 || n > 100) {
                errors.add("parameters.brightnessLineSegments: coordinates must be in [0,100]");
                break;
            }
        if (!p.shouldUseBinaryMask && p.thresholdLow > p.thresholdHigh) errors.add("parameters.thresholdLow: exceeds thresholdHigh");
        if (!p.shouldUseBinaryMask && p.shouldRemapColors) {
            if (p.hueTransformWeight < 0 || p.brightnessTransformWeight < 0 || p.hueTransformWeight + p.brightnessTransformWeight <= 0)
                errors.add("parameters.hueTransformWeight: color weights must be nonnegative with positive sum");
            if (Misc.isGrayscale(p.targetRemapColor.getRGB())) errors.add("parameters.targetRemapColor: must have hue");
            if (Misc.isGrayscale(p.voidRemapColor.getRGB())) errors.add("parameters.voidRemapColor: must have hue");
            float distance = Math.abs(Misc.getHue(p.targetRemapColor.getRGB()) - Misc.getHue(p.voidRemapColor.getRGB()));
            if (Math.min(distance, 6 - distance) < 0.05f) errors.add("parameters.voidRemapColor: too close to target hue");
            if (!PreprocessColor.computeBrightnessTable(null, p.brightnessLineSegments, p.brightnessLineSegments.length / 2))
                errors.add("parameters.brightnessLineSegments: invalid brightness curve");
        }
        return errors;
    }

    static List<String> inactive(AnalyzerParameters p)
    {
        List<String> names = new ArrayList<>();
        for (Parameter param : PARAMETERS.values()) if (!active(p, param)) names.add(param.name);
        return names;
    }

    static String json(AnalyzerParameters p) throws IOException
    {
        StringWriter writer = new StringWriter();
        writer.write("{\"schemaVersion\":1,\"programVersion\":");
        OverlayDataExporter.writeJsonString(writer, AngioTool.VERSION);
        writer.write(",\"parameters\":{");
        boolean first = true;
        for (Parameter param : PARAMETERS.values()) {
            if (!first) writer.write(',');
            first = false;
            OverlayDataExporter.writeJsonString(writer, param.name);
            writer.write(':');
            Object value = param.get(p);
            if (value instanceof Rgb) OverlayDataExporter.writeJsonString(writer, value.toString());
            else if (value instanceof int[] || value instanceof double[]) {
                writer.write('[');
                int length = java.lang.reflect.Array.getLength(value);
                for (int i = 0; i < length; i++) {
                    if (i != 0) writer.write(',');
                    writer.write(java.lang.reflect.Array.get(value, i).toString());
                }
                writer.write(']');
            }
            else writer.write(value.toString());
        }
        writer.write("}}\n");
        return writer.toString();
    }

    static void save(Path file, String text, boolean overwrite) throws IOException
    {
        Cli.checkTarget(file, overwrite);
        Path parent = file.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".cli-config-", ".json");
        try {
            Files.write(temporary, text.getBytes(StandardCharsets.UTF_8));
            if (overwrite) Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            else Files.move(temporary, file);
        }
        finally { Files.deleteIfExists(temporary); }
    }
}
