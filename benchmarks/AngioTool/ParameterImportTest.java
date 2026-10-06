package AngioTool;

import Pixels.ArgbBuffer;
import Pixels.Rgb;
import Utils.ISliceRunner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Tests real export/import round trips and independent older/invalid JSON fixtures. */
public class ParameterImportTest
{
    public static void main(String[] args) throws Exception
    {
        Path root = args.length == 0 ? Files.createTempDirectory("angiotool-import-") :
            Files.createDirectories(Paths.get(args[0]));
        Analyzer.Data data = new Analyzer.Data(null, 0);
        try {
            AnalyzerParameters params = AnalyzerParameters.defaults();
            params.shouldUseBinaryMask = true;
            params.shouldNormalizeOutputData = true;
            params.shouldUseFastSkeletonizer = true;
            params.binaryRemoveSmallParticles = true;
            params.binarySmallParticleRadius = 2.5;
            params.binaryFillHoles = true;
            params.binaryHoleRadius = 1.5;
            params.shouldResizeImage = true;
            params.resizingFactor = 0.375;
            params.shouldApplyLinearScale = true;
            params.linearScalingFactor = 0.625;
            params.shouldRemapColors = true;
            params.saturationFactor = 0.75;
            params.hueTransformWeight = 0.5;
            params.brightnessTransformWeight = 1.25;
            params.targetRemapColor = new Rgb("3388CC");
            params.voidRemapColor = new Rgb("DD6622");
            params.brightnessLineSegments = new int[] {0, 5, 50, 70, 100, 95};
            params.shouldFillHoles = true;
            params.fillHolesValue = 13;
            params.shouldRemoveSmallParticles = true;
            params.removeSmallParticlesThreshold = 17;
            params.shouldFillBrightShapes = true;
            params.brightShapeThresholdFactor = 1.25;
            params.minBoxness = 0.125;
            params.minAreaLengthRatio = 8;
            params.sigmas = new double[] {2.5, 7, 12};
            params.thresholdLow = 31;
            params.thresholdHigh = 219;
            params.shouldComputeLacunarity = false;
            params.shouldComputeThickness = false;
            params.shouldPruneSkeleton = false;
            params.maxTerminalBranchLength = 4.5;
            params.maxJunctionMergeDistance = 2.5;
            params.outlineColor = new Rgb("123456");
            params.skeletonColor = new Rgb("ABCDEF");
            params.branchingPointsColor = new Rgb("456789");
            params.convexHullColor = new Rgb("987654");
            params.outlineSize = 1.5;
            params.skeletonSize = 3;
            params.branchingPointsSize = 5;
            params.convexHullSize = 2;
            params.shouldDrawConvexHull = false;
            params.shouldExpandOutputToGrayScale = false;
            int size = 32;
            int[] pixels = new int[size * size];
            for (int y = 8; y < 24; y++)
                for (int x = 6; x < 26; x++) pixels[x + y * size] = 0xffffffff;
            Analyzer.Stats stats = Analyzer.analyze(data, root.resolve("mask.png").toFile(),
                new ArgbBuffer(pixels, size, size), params, new ISliceRunner.Parallel(Analyzer.threadPool));
            OverlayDataExporter.export(root.resolve("roundtrip").toString(), params, data, stats, size, size, 0);
            AnalyzerParameterImporter.Result result = AnalyzerParameterImporter.load(root.resolve("roundtrip overlay-data.json").toFile());
            check(params.equals(result.parameters), "All exported parameters round trip, including dormant binary-mode settings");
            check(result.defaultedFields.isEmpty(), "Current export restores every field");
            check(AngioTool.VERSION.equals(result.programVersion), "Source version retained");
            reject(root.resolve("roundtrip normalized-data.json"));
            AnalyzerParameters legacy = load(root, "{\"analysisParameters\":{\"lowThreshold\":42,\"highThreshold\":230,\"vesselThickness\":[3,8],\"skeletonizer\":\"Fast (Zha84)\"},\"overlayParameters\":{\"outlineColor\":\"#12ab34\"}}" ).parameters;
            check(legacy.thresholdLow == 42 && legacy.thresholdHigh == 230 && legacy.shouldUseFastSkeletonizer,
                "Older field aliases and skeletonizer restored");
            check(!legacy.shouldPruneSkeleton && !legacy.shouldMergeNearbyJunctions, "Old snapshots do not enable pruning or merging");
            check(legacy.outlineColor.getRGB() == new Rgb("12ab34").getRGB(), "Older color syntax restored");
            AnalyzerParameters priority = load(root, "\ufeff{\"analysisParameters\":{\"lowThreshold\":10},\"uiParameters\":{\"analysis\":{\"minVesselIntensity\":41,\"resizeInputsPercent\":37.5}}}").parameters;
            check(priority.thresholdLow == 41 && priority.resizingFactor == 0.375, "UI snapshot takes priority; percentages and BOM supported");
            for (String invalid : new String[] {
                "{}", "{\"uiParameters\":null}", "{\"analysisParameters\":{\"lowThreshold\":\"42\"}}",
                "{\"analysisParameters\":{\"lowThreshold\":1.5}}", "{\"analysisParameters\":{\"lowThreshold\":256}}",
                "{\"analysisParameters\":{\"lowThreshold\":42,\"lowThreshold\":43}}",
                "{\"analysisParameters\":{\"lowThreshold\":42}} trailing",
                "{\"analysisParameters\":{\"vesselThickness\":[NaN]}}",
                "{\"analysisParameters\":{\"lowThreshold\":42},\"programVersion\":17}",
                "{\"analysisParameters\":{\"brightnessGraph\":[0,0,100]}}"
            }) {
                Path file = root.resolve("invalid.json");
                Files.write(file, invalid.getBytes(StandardCharsets.UTF_8));
                reject(file);
            }
            StringBuilder large = new StringBuilder("{\"analysisParameters\":{\"lowThreshold\":42},\"points\":[");
            for (int i = 0; i < 50000; i++) { if (i > 0) large.append(','); large.append(i); }
            large.append("]}");
            check(load(root, large.toString()).parameters.thresholdLow == 42, "Large unrelated point arrays do not exhaust retained parameter limit");
            System.out.println("ParameterImportTest: PASS");
        }
        finally {
            data.nullify();
            Analyzer.threadPool.shutdown();
        }
    }

    private static AnalyzerParameterImporter.Result load(Path root, String json) throws Exception
    {
        Path file = root.resolve("fixture.json");
        Files.write(file, json.getBytes(StandardCharsets.UTF_8));
        return AnalyzerParameterImporter.load(file.toFile());
    }

    private static void reject(Path file) throws Exception
    {
        try { AnalyzerParameterImporter.load(file.toFile()); }
        catch (IOException expected) { return; }
        throw new AssertionError("Invalid snapshot accepted: " + file.getFileName());
    }

    private static void check(boolean condition, String message)
    {
        if (!condition) throw new AssertionError(message);
    }
}
