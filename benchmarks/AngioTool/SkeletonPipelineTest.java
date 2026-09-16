package AngioTool;

import Pixels.ArgbBuffer;
import Utils.ISliceRunner;
import Xlsx.SpreadsheetWriter;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import javax.imageio.ImageIO;

/** Exercises both skeletonizers through the real binary-mask pipeline and its exports. */
public class SkeletonPipelineTest
{
    private static final int SIZE = 96;

    public static void main(String[] args) throws Exception
    {
        Path root = args.length == 0 ? Files.createTempDirectory("angiotool-skeleton-") :
            Files.createDirectories(Paths.get(args[0]).toAbsolutePath());
        ISliceRunner runner = new ISliceRunner.Parallel(Analyzer.threadPool);
        try {
            for (boolean fast : new boolean[] {true, false}) {
                String method = fast ? "Zha84" : "Lee94";
                AnalyzerParameters p = AnalyzerParameters.defaults();
                p.shouldUseBinaryMask = true;
                p.shouldUseFastSkeletonizer = fast;
                p.shouldComputeThickness = false;
                p.shouldComputeLacunarity = false;
                p.shouldMergeNearbyJunctions = false;
                p.shouldNormalizeOutputData = true;
                Analyzer.Data data = new Analyzer.Data(null, 0);
                ArgbBuffer fork = fork();
                int[] original = fork.pixels.clone();

                p.shouldPruneSkeleton = false;
                Analyzer.Stats baseline = analyze(data, fork, p, runner);
                check(baseline.skeletonConverged, method + " unlimited thinning converges");
                check("disabled".equals(baseline.pruningStatus), "Disabled pruning status");
                check(baseline.totalNEndPoints == 3 && baseline.totalNJunctions == 1,
                    method + " hand-drawn Y has three endpoints and one junction");
                byte[] raw = data.rawSkeleton.clone();
                byte[] mask = Arrays.copyOf(data.analysisImage.buf, SIZE * SIZE);
                exportAndCheck(root.resolve(method + "-off"), p, data, baseline);

                p.shouldPruneSkeleton = true;
                for (int threshold : new int[] {2, 3, 5}) {
                    p.maxTerminalBranchLength = threshold;
                    Analyzer.Stats stats = analyze(data, fork, p, runner);
                    int expectedRemoved = threshold >= 3 ? 3 : 0;
                    check(stats.skeletonConverged && "applied".equals(stats.pruningStatus),
                        method + " pruning applied after convergence");
                    check(stats.prunedSkeletonPixelCount == expectedRemoved,
                        method + " threshold " + threshold + ": expected " + expectedRemoved +
                        " removed, got " + stats.prunedSkeletonPixelCount);
                    check(stats.totalNEndPoints == (expectedRemoved > 0 ? 2 : 3), "Endpoint count after pruning");
                    check(stats.totalNJunctions == (expectedRemoved > 0 ? 0 : 1), "Junction count after pruning");
                    check(Arrays.equals(raw, data.rawSkeleton), "Raw skeleton snapshot preserved");
                    check(Arrays.equals(mask, Arrays.copyOf(data.analysisImage.buf, SIZE * SIZE)),
                        "Pruning must not change vessel mask");
                    check(Arrays.equals(baseline.fractalBoxCount.boxCounts, stats.fractalBoxCount.boxCounts),
                        "Pruning must not change mask box counts");
                    check(baseline.vesselMMArea == stats.vesselMMArea &&
                        baseline.allantoisMMArea == stats.allantoisMMArea, "Mask areas unchanged");
                    near(baseline.totalLength - expectedRemoved, stats.totalLength, "Length removed from Y");
                    exportAndCheck(root.resolve(method + "-" + threshold), p, data, stats);
                }
                check(Arrays.equals(original, fork.pixels), "Input pixels unchanged");
                byte[] pruned = data.processedSkeleton.clone();
                p.shouldApplyLinearScale = true;
                p.linearScalingFactor = 4;
                Analyzer.Stats scaled = analyze(data, fork, p, runner);
                check(Arrays.equals(pruned, data.processedSkeleton), "Measurement scale must not alter px pruning");
                near((baseline.totalLength - 3) * 4, scaled.totalLength, "Scaled length");
                p.shouldApplyLinearScale = false;

                // A thick bar cannot finish thinning in one iteration. Reuse Data to expose stale state.
                ArgbBuffer bar = empty();
                for (int y = 32; y < 64; y++)
                    for (int x = 16; x < 80; x++) bar.pixels[x + SIZE * y] = 0xffffffff;
                p.shouldCapSkelIterations = true;
                p.maxSkelIterations = 1;
                Analyzer.Stats capped = analyze(data, bar, p, runner);
                check(!capped.skeletonConverged, method + " thick bar must report iteration limit");
                check("skipped-not-converged".equals(capped.pruningStatus), "Skip unsafe pruning");
                check(capped.prunedSkeletonPixelCount == 0 && Arrays.equals(data.rawSkeleton, data.processedSkeleton),
                    "Unconverged skeleton is not pruned");
                exportAndCheck(root.resolve(method + "-capped"), p, data, capped);
                p.shouldCapSkelIterations = false;
                Analyzer.Stats complete = analyze(data, bar, p, runner);
                check(complete.skeletonConverged, method + " bar converges without iteration cap");
                check(complete.rawSkeletonPixelCount < capped.rawSkeletonPixelCount,
                    "Converged bar is thinner than capped intermediate");
                Analyzer.Stats empty = analyze(data, empty(), p, runner);
                check(empty.skeletonConverged && empty.rawSkeletonPixelCount == 0 &&
                    empty.prunedSkeletonPixelCount == 0 && !empty.valid, "Empty analysis clears previous state");
                exportAndCheck(root.resolve(method + "-empty"), p, data, empty);
            }
            System.out.println("SkeletonPipelineTest: PASS; artifacts: " + root);
        }
        finally {
            Analyzer.threadPool.shutdown();
        }
    }

    private static Analyzer.Stats analyze(Analyzer.Data data, ArgbBuffer image,
        AnalyzerParameters p, ISliceRunner runner) throws Exception
    {
        return Analyzer.analyze(data, new File("synthetic.png"), image, p, runner);
    }

    private static ArgbBuffer empty()
    {
        int[] pixels = new int[SIZE * SIZE];
        Arrays.fill(pixels, 0xff000000);
        return new ArgbBuffer(pixels, SIZE, SIZE);
    }

    private static ArgbBuffer fork()
    {
        ArgbBuffer image = empty();
        image.pixels[48 + SIZE * 48] = 0xffffffff;
        for (int i = 1; i <= 20; i++) {
            image.pixels[48 - i + SIZE * (48 - i)] = 0xffffffff;
            image.pixels[48 + i + SIZE * (48 - i)] = 0xffffffff;
        }
        for (int y = 49; y <= 51; y++) image.pixels[48 + SIZE * y] = 0xffffffff;
        return image;
    }

    private static void exportAndCheck(Path base, AnalyzerParameters p,
        Analyzer.Data data, Analyzer.Stats stats) throws Exception
    {
        int rawCount = count(data.rawSkeleton);
        int processedCount = count(data.processedSkeleton);
        check(stats.rawSkeletonPixelCount == rawCount && stats.skeletonPixelCount == processedCount &&
            rawCount - processedCount == stats.prunedSkeletonPixelCount, "Pixel count accounting");
        OverlayDataExporter.export(base.toString(), p, data, stats, SIZE, SIZE, 0);
        for (String suffix : new String[] {" overlay-data.json", " normalized-data.json"}) {
            String json = new String(Files.readAllBytes(Paths.get(base + suffix)), StandardCharsets.UTF_8);
            check(json.contains("\"skeletonConverged\": " + stats.skeletonConverged), "JSON convergence status");
            check(json.contains("\"pruningStatus\": \"" + stats.pruningStatus + "\""), "JSON pruning status");
        }
        for (boolean raw : new boolean[] {true, false}) {
            BufferedImage png = ImageIO.read(new File(base + (raw ? " raw-skeleton.png" : " pruned-skeleton.png")));
            byte[] expected = raw ? data.rawSkeleton : data.processedSkeleton;
            check(png.getWidth() == SIZE && png.getHeight() == SIZE, "PNG dimensions");
            for (int i = 0; i < expected.length; i++)
                check(((png.getRGB(i % SIZE, i / SIZE) & 0xffffff) != 0) == ((expected[i] & 1) != 0),
                    "Exported skeleton PNG pixel " + i);
        }
        SpreadsheetWriter sheet = Analyzer.createWriterWithNewSheet(new ArrayList<Xlsx.XlsxReader.SheetCells>(),
            base.getParent().toFile(), base.getFileName() + ".xlsx");
        Analyzer.writeResultToSheet(sheet, p, stats);
        Object[] headers = sheet.sheets.buf[0].valueRows.buf[0];
        Object[] row = sheet.sheets.buf[0].valueRows.buf[1];
        check(Boolean.valueOf(stats.skeletonConverged).equals(row[Arrays.asList(headers).indexOf("Skeleton Converged")]),
            "XLSX convergence status");
        check(stats.pruningStatus.equals(row[Arrays.asList(headers).indexOf("Pruning Status")]), "XLSX pruning status");
        check(((Number)row[Arrays.asList(headers).indexOf("Pruned Skeleton Pixels")]).intValue() == stats.prunedSkeletonPixelCount,
            "XLSX removed pixel count");
        sheet.save();
    }

    private static int count(byte[] pixels)
    {
        int count = 0;
        for (byte value : pixels) if ((value & 1) != 0) count++;
        return count;
    }

    private static void near(double expected, double actual, String label)
    {
        check(Double.isFinite(actual) && Math.abs(expected - actual) < 1.0e-6,
            label + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message)
    {
        if (!condition) throw new AssertionError(message);
    }
}
