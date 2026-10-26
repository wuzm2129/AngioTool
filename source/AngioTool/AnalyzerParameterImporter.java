package AngioTool;

import Pixels.Rgb;
import Utils.RefVector;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads parameter snapshots without retaining the potentially large point/segment arrays. */
public final class AnalyzerParameterImporter
{
    public static final class Result
    {
        public final AnalyzerParameters parameters;
        public final List<String> defaultedFields;
        public final String programVersion;

        Result(AnalyzerParameters parameters, List<String> defaultedFields, String programVersion)
        {
            this.parameters = parameters;
            this.defaultedFields = defaultedFields;
            this.programVersion = programVersion;
        }
    }

    private final AnalyzerParameters parameters = AnalyzerParameters.defaults();
    private final Set<String> restored = new HashSet<>();

    public static Result load(File file) throws IOException
    {
        Map<String, Object> root;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Files.newInputStream(file.toPath()), StandardCharsets.UTF_8))) {
            root = new JsonReader(reader).readConfiguration();
        }
        AnalyzerParameterImporter importer = new AnalyzerParameterImporter();
        return importer.restore(root);
    }

    private Result restore(Map<String, Object> root) throws IOException
    {
        // Historical files predate pruning/merging. Never enable these implicitly on import.
        parameters.shouldPruneSkeleton = false;
        parameters.shouldMergeNearbyJunctions = false;
        Map<String, Object> analysis = object(root, "analysisParameters");
        copy(analysis,
            "shouldUseBinaryMask:useBinaryMask", "binaryRemoveSmallParticles", "binarySmallParticleRadius",
            "binaryFillHoles", "binaryHoleRadius", "thresholdLow:lowThreshold", "thresholdHigh:highThreshold",
            "sigmas:vesselThickness", "removeSmallParticlesThreshold:smallParticles", "fillHolesValue:fillHoles",
            "brightShapeThresholdFactor:maxHoleLevel", "minBoxness", "minAreaLengthRatio",
            "maxSkelIterations:maxSkeletonSteps", "linearScalingFactor:scalingFactor",
            "shouldNormalizeOutputData:normalizeOutputData", "shouldRemapColors:transformedColors",
            "hueTransformWeight:hueTransformWeight", "brightnessTransformWeight:brightnessWeight",
            "targetRemapColor:targetColor", "voidRemapColor:offColor", "saturationFactor",
            "brightnessLineSegments:brightnessGraph");
        skeletonizer(analysis);
        Map<String, Object> overlay = object(root, "overlayParameters");
        copy(overlay,
            "shouldDrawOutline:drawOutline", "outlineColor", "outlineSize", "shouldDrawSkeleton:drawSkeleton",
            "skeletonColor", "skeletonSize", "shouldDrawBranchPoints:drawBranchPoints",
            "branchingPointsColor", "branchingPointsSize", "shouldDrawConvexHull:drawConvexHull",
            "convexHullColor", "convexHullSize", "shouldIsolateBrightestChannelInOutput:isolateBrightestChannelInOutput",
            "shouldExpandOutputToGrayScale:expandOutputToGrayScale");
        Map<String, Object> skeleton = object(root, "skeletonProcessing");
        copy(skeleton, "shouldPruneSkeleton:pruneTerminalBranches", "maxTerminalBranchLength:maxTerminalBranchLengthPx",
            "shouldMergeNearbyJunctions:mergeNearbyJunctions", "maxJunctionMergeDistance:maxJunctionMergeDistancePx");
        copy(analysis, "shouldMergeNearbyJunctions:mergeNearbyJunctions",
            "maxJunctionMergeDistance:maxJunctionMergeDistancePx");

        // UI values include enable switches and percentages; they take precedence over legacy aliases.
        Map<String, Object> ui = object(root, "uiParameters");
        Map<String, Object> uiAnalysis = object(ui, "analysis");
        copy(uiAnalysis,
            "shouldUseBinaryMask:useBinaryMask", "binaryRemoveSmallParticles", "binarySmallParticleRadius",
            "binaryFillHoles", "binaryHoleRadius", "shouldComputeLacunarity:computeLacunarity",
            "shouldComputeThickness:computeThickness", "shouldCapSkelIterations:capSkeletonSteps",
            "maxSkelIterations:maxSkeletonSteps", "shouldPruneSkeleton:pruneTerminalBranches",
            "maxTerminalBranchLength:maxTerminalBranchLengthPx", "shouldMergeNearbyJunctions:mergeNearbyJunctions",
            "maxJunctionMergeDistance:maxJunctionMergeDistancePx", "shouldResizeImage:resizeInputs",
            "resizingFactor:resizeInputsPercent:percent", "shouldApplyLinearScale:applyMeasurementScale",
            "linearScalingFactor:measurementScalePercent:percent", "shouldNormalizeOutputData:normalizeOutputData",
            "shouldFillHoles:fillHoles", "fillHolesValue:fillHolesPixels", "shouldRemoveSmallParticles:removeParticles",
            "removeSmallParticlesThreshold:removeParticlesPixels", "shouldFillBrightShapes:fillBrightShapes",
            "brightShapeThresholdFactor:maxHoleLevelPercent:percent", "shouldApplyMinBoxness:applyMinBoxness",
            "minBoxness:minBoxnessPercent:percent", "shouldApplyMinAreaLength:applyMinAreaLengthRatio",
            "minAreaLengthRatio", "sigmas:vesselDiameters", "thresholdLow:minVesselIntensity",
            "thresholdHigh:maxVesselIntensity");
        skeletonizer(uiAnalysis);
        copy(object(ui, "colorPreprocessing"), "shouldRemapColors:transformColors",
            "saturationFactor:saturationPercent:percent", "voidRemapColor:offColor", "targetRemapColor:targetColor",
            "hueTransformWeight:hueWeight", "brightnessTransformWeight:brightnessWeight",
            "brightnessLineSegments:brightnessGraph");
        Map<String, Object> display = object(ui, "overlayDisplay");
        copy(display, "shouldIsolateBrightestChannelInOutput:isolateBrightestChannelInOutput",
            "shouldExpandOutputToGrayScale:expandOutputToGrayScale");
        overlay(object(display, "outline"), "shouldDrawOutline", "outlineColor", "outlineSize");
        overlay(object(display, "skeleton"), "shouldDrawSkeleton", "skeletonColor", "skeletonSize");
        overlay(object(display, "branches"), "shouldDrawBranchPoints", "branchingPointsColor", "branchingPointsSize");
        overlay(object(display, "convexHull"), "shouldDrawConvexHull", "convexHullColor", "convexHullSize");
        if (restored.isEmpty())
            throw new IOException("This JSON contains no supported AngioTool parameters. Select an overlay-data.json file.");
        validateValues();
        List<String> missing = new ArrayList<>();
        for (Field field : AnalyzerParameters.class.getFields())
            if (!restored.contains(field.getName()))
                missing.add(field.getName());
        Object version = root.get("programVersion");
        if (version != null && !(version instanceof String))
            throw new IOException("programVersion must be a string.");
        return new Result(parameters, missing, version == null ? "Unknown (older export)" : (String)version);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Map<String, Object> parent, String name) throws IOException
    {
        if (parent == null || !parent.containsKey(name))
            return null;
        Object value = parent.get(name);
        if (!(value instanceof Map))
            throw new IOException(name + " must be a JSON object.");
        return (Map<String, Object>)value;
    }

    private void overlay(Map<String, Object> group, String enabled, String color, String size) throws IOException
    {
        copy(group, enabled + ":enabled", color + ":color", size + ":size");
    }

    private void skeletonizer(Map<String, Object> group) throws IOException
    {
        if (group == null || !group.containsKey("skeletonizer"))
            return;
        Object name = group.get("skeletonizer");
        if ("Fast".equals(name) || "Fast (Zha84)".equals(name))
            parameters.shouldUseFastSkeletonizer = true;
        else if ("Thorough".equals(name) || "Thorough (Lee94)".equals(name))
            parameters.shouldUseFastSkeletonizer = false;
        else
            throw new IOException("Unsupported skeletonizer in parameter file.");
        restored.add("shouldUseFastSkeletonizer");
    }

    private void copy(Map<String, Object> group, String... mappings) throws IOException
    {
        if (group == null)
            return;
        for (String mapping : mappings) {
            String[] parts = mapping.split(":");
            String key = parts.length > 1 ? parts[1] : parts[0];
            if (!group.containsKey(key))
                continue;
            try {
                Field field = AnalyzerParameters.class.getField(parts[0]);
                Object value = convert(group.get(key), field.getType(), key);
                if (parts.length > 2)
                    value = ((Double)value) / 100.0;
                field.set(parameters, value);
                restored.add(parts[0]);
            }
            catch (ReflectiveOperationException ex) {
                throw new IOException("Parameter mapping failed: " + parts[0], ex);
            }
        }
    }

    private static Object convert(Object value, Class<?> type, String name) throws IOException
    {
        if (type == boolean.class && value instanceof Boolean)
            return value;
        if (type == double.class && value instanceof Number) {
            double number = ((Number)value).doubleValue();
            if (Double.isFinite(number))
                return number;
        }
        if (type == int.class && value instanceof Number) {
            double number = ((Number)value).doubleValue();
            if (number == Math.rint(number) && number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE)
                return (int)number;
        }
        if (type == Rgb.class && value instanceof String && ((String)value).matches("#?[0-9a-fA-F]{6}"))
            return new Rgb((String)value);
        if (type.isArray() && value instanceof List) {
            List<?> values = (List<?>)value;
            if (values.size() > 4096)
                throw new IOException(name + " has too many values.");
            if (type == double[].class) {
                double[] array = new double[values.size()];
                for (int i = 0; i < array.length; i++)
                    array[i] = (Double)convert(values.get(i), double.class, name);
                return array;
            }
            if (type == int[].class) {
                int[] array = new int[values.size()];
                for (int i = 0; i < array.length; i++)
                    array[i] = (Integer)convert(values.get(i), int.class, name);
                return array;
            }
        }
        throw new IOException("Invalid value or type for parameter: " + name);
    }

    private void validateValues() throws IOException
    {
        for (double factor : new double[] {parameters.resizingFactor, parameters.linearScalingFactor,
                parameters.saturationFactor, parameters.brightShapeThresholdFactor, parameters.minBoxness})
            if (!Double.isFinite(factor * 100.0))
                throw new IOException("Parameter percentage is too large to display.");
        if (parameters.brightnessLineSegments.length % 2 != 0)
            throw new IOException("Brightness graph must contain complete coordinate pairs.");
        RefVector<String> errors = parameters.validate();
        if (errors.size > 0)
            throw new IOException(errors.buf[0]);
    }

    /** Strict streaming JSON reader; unknown top-level data is validated and discarded. */
    static final class JsonReader
    {
        private final BufferedReader reader;
        private int next;
        private long position;
        private int retainedValues;

        JsonReader(BufferedReader reader) throws IOException
        {
            this.reader = reader;
            advance();
            if (next == 0xfeff)
                advance();
        }

        Map<String, Object> readConfiguration() throws IOException
        {
            return readConfiguration(false);
        }

        Map<String, Object> readConfiguration(boolean retainTopKeys) throws IOException
        {
            whitespace();
            expect('{');
            Map<String, Object> root = new LinkedHashMap<>();
            Set<String> keys = new HashSet<>();
            whitespace();
            if (next != '}') {
                do {
                    String key = string();
                    if (!keys.add(key))
                        fail("Duplicate top-level key");
                    whitespace();
                    expect(':');
                    boolean keep = key.equals("uiParameters") || key.equals("analysisParameters") ||
                        key.equals("overlayParameters") || key.equals("skeletonProcessing") || key.equals("programVersion") ||
                        key.equals("schemaVersion") || key.equals("parameters");
                    Object value = value(1, keep);
                    if (keep || retainTopKeys)
                        root.put(key, value);
                    whitespace();
                    if (next != ',')
                        break;
                    advance();
                    whitespace();
                } while (true);
            }
            expect('}');
            whitespace();
            if (next != -1)
                fail("Unexpected trailing content");
            return root;
        }

        private Object value(int depth, boolean keep) throws IOException
        {
            if (depth > 64 || (keep && ++retainedValues > 20000))
                fail("JSON parameter structure is too large or deeply nested");
            whitespace();
            if (next == '"')
                return string();
            if (next == '{') {
                advance();
                Map<String, Object> values = keep ? new LinkedHashMap<String, Object>() : null;
                whitespace();
                if (next != '}') {
                    do {
                        String key = string();
                        if (keep && values.containsKey(key))
                            fail("Duplicate parameter key");
                        whitespace();
                        expect(':');
                        Object child = value(depth + 1, keep);
                        if (keep)
                            values.put(key, child);
                        whitespace();
                        if (next != ',')
                            break;
                        advance();
                        whitespace();
                    } while (true);
                }
                expect('}');
                return values;
            }
            if (next == '[') {
                advance();
                List<Object> values = keep ? new ArrayList<Object>() : null;
                whitespace();
                if (next != ']') {
                    do {
                        Object child = value(depth + 1, keep);
                        if (keep)
                            values.add(child);
                        whitespace();
                        if (next != ',')
                            break;
                        advance();
                    } while (true);
                }
                expect(']');
                return values;
            }
            if (next == 't') { literal("true"); return Boolean.TRUE; }
            if (next == 'f') { literal("false"); return Boolean.FALSE; }
            if (next == 'n') { literal("null"); return null; }
            StringBuilder number = new StringBuilder();
            if (next == '-') { number.append('-'); advance(); }
            if (next == '0') { number.append('0'); advance(); }
            else {
                if (next < '1' || next > '9')
                    fail("Expected a JSON value");
                digits(number);
            }
            if (next == '.') {
                number.append('.'); advance();
                digits(number);
            }
            if (next == 'e' || next == 'E') {
                number.append((char)next); advance();
                if (next == '+' || next == '-') { number.append((char)next); advance(); }
                digits(number);
            }
            try {
                double result = Double.parseDouble(number.toString());
                if (keep && !Double.isFinite(result))
                    fail("Non-finite parameter number");
                return keep ? result : null;
            }
            catch (NumberFormatException ex) {
                throw new IOException("Invalid JSON number at character " + position, ex);
            }
        }

        private void digits(StringBuilder number) throws IOException
        {
            if (next < '0' || next > '9')
                fail("Expected a digit");
            while (next >= '0' && next <= '9') {
                if (number.length() > 256)
                    fail("JSON number is too long");
                number.append((char)next);
                advance();
            }
        }

        private String string() throws IOException
        {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (next != '"') {
                if (next < 0x20)
                    fail("Unterminated string or unescaped control character");
                int character = next;
                advance();
                if (character == '\\') {
                    character = next;
                    advance();
                    switch (character) {
                        case '"': case '\\': case '/': break;
                        case 'b': character = '\b'; break;
                        case 'f': character = '\f'; break;
                        case 'n': character = '\n'; break;
                        case 'r': character = '\r'; break;
                        case 't': character = '\t'; break;
                        case 'u':
                            character = 0;
                            for (int i = 0; i < 4; i++) {
                                int digit = next >= '0' && next <= '9' ? next - '0' :
                                    next >= 'a' && next <= 'f' ? next - 'a' + 10 :
                                    next >= 'A' && next <= 'F' ? next - 'A' + 10 : -1;
                                if (digit < 0)
                                    fail("Invalid Unicode escape");
                                character = character * 16 + digit;
                                advance();
                            }
                            break;
                        default: fail("Invalid string escape");
                    }
                }
                if (result.length() >= 1048576)
                    fail("JSON string is too long");
                result.append((char)character);
            }
            advance();
            return result.toString();
        }

        private void literal(String word) throws IOException
        {
            for (int i = 0; i < word.length(); i++)
                expect(word.charAt(i));
        }

        private void whitespace() throws IOException
        {
            while (next == ' ' || next == '\t' || next == '\r' || next == '\n')
                advance();
        }

        private void expect(int character) throws IOException
        {
            if (next != character)
                fail("Unexpected JSON token");
            advance();
        }

        private void advance() throws IOException
        {
            if (++position > 134217728L)
                fail("JSON exceeds the 128 MiB import limit");
            next = reader.read();
        }

        private void fail(String message) throws IOException
        {
            throw new IOException(message + " at character " + position + ".");
        }
    }
}
