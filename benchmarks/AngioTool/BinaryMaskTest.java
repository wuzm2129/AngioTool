package AngioTool;

import Pixels.ArgbBuffer;
import Utils.ISliceRunner;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;

public class BinaryMaskTest
{
    public static void main(String[] args) throws Exception
    {
        Path root = Files.createTempDirectory("angiotool-binary-");
        System.setProperty("user.home", root.toString());
        try {
            AnalyzerParameters p = AnalyzerParameters.defaults();
            check(!p.shouldUseBinaryMask, "Legacy mode remains default");
            AnalyzerParameters normal = AnalyzerParameters.defaults();
            p.shouldUseBinaryMask = true;
            check(!p.equals(normal) && !p.hasSameAnalysisSettings(normal), "Mode triggers reanalysis");
            p.shouldUseFastSkeletonizer = true;
            p.shouldCapSkelIterations = false;
            p.shouldResizeImage = true;
            p.resizingFactor = 0.5;
            p.shouldFillHoles = true;
            p.fillHolesValue = 100;
            p.shouldRemoveSmallParticles = true;
            p.removeSmallParticlesThreshold = 100;
            p.shouldRemapColors = true;
            p.sigmas = new double[0];
            check(p.validate().size == 0 && p.effectiveResizeFactor() == 1.0,
                "Ignored detection parameters do not block mask analysis");
            int w = 96;
            int[] pixels = new int[w * w];
            Arrays.fill(pixels, 0xff000000);
            // Solid block, genuine ring, isolated pixel and edge-touching foreground.
            for (int y = 8; y < 42; y++)
                for (int x = 8; x < 42; x++)
                    pixels[x + w * y] = 0xffffffff;
            for (int y = 50; y < 85; y++)
                for (int x = 50; x < 85; x++)
                    if (x < 55 || x >= 80 || y < 55 || y >= 80)
                        pixels[x + w * y] = 0xffffffff;
            pixels[70 + w * 10] = 0xffffffff;
            pixels[w * 20] = 0xffffffff;
            ArgbBuffer input = new ArgbBuffer(pixels, w, w);
            Analyzer.Data data = new Analyzer.Data(null, 0);
            ISliceRunner runner = new ISliceRunner.Parallel(Analyzer.threadPool);
            Analyzer.Stats stats = Analyzer.analyze(data, new File("binary.png"), input, p, runner);
            checkMask(data, pixels);
            check(data.analysisImage.buf[24 + w * 24] == (byte)255, "Solid center remains filled");
            check(data.analysisImage.buf[65 + w * 65] == 0, "Real ring hole remains empty");
            long expectedArea = Arrays.stream(pixels).filter(v -> (v & 0xffffff) != 0).count();
            check(data.vesselPixelArea == expectedArea, "Exact foreground area");
            OverlayDataExporter.export(root.resolve("binary").toString(), p, data, stats, w, w, 0);
            String json = new String(Files.readAllBytes(root.resolve("binary overlay-data.json")),
                StandardCharsets.UTF_8);
            check(json.contains("\"inputMode\": \"Binary mask\""), "Export identifies input mode");
            Path batchInput = Files.createDirectories(root.resolve("batch"));
            Files.copy(root.resolve("binary vessel-mask.png"), batchInput.resolve("sample.png"));
            BatchParameters batch = new BatchParameters(batchInput.toString(),
                new String[] {batchInput.toString()}, root.resolve("batch.xlsx").toString(),
                true, false, "", "", true, 1);
            BatchOutputPathTest.ProgressToken token = new BatchOutputPathTest.ProgressToken();
            BatchProcessing.doBatchAnalysis(p, batch, token, new java.util.ArrayList<Xlsx.XlsxReader.SheetCells>());
            check(token.finished && token.error == null, "Binary batch completes");
            java.awt.image.BufferedImage batchMask = javax.imageio.ImageIO.read(
                batchInput.resolve("results/ana_sample vessel-mask.png").toFile());
            check(batchMask.getWidth() == w && batchMask.getHeight() == w, "Batch ignores resize");
            for (int i = 0; i < pixels.length; i++)
                check((batchMask.getRGB(i % w, i / w) & 0xffffff) == (pixels[i] & 0xffffff),
                    "Batch mask preserved at " + i);
            check(Files.isRegularFile(root.resolve("batch.xlsx")), "Batch workbook exported");
            ATPreferences.savePreferences(p, "binary-test.txt");
            AnalyzerParameters loaded = AnalyzerParameters.defaults();
            ATPreferences.loadPreferences(loaded, AngioTool.class, "binary-test.txt");
            check(loaded.shouldUseBinaryMask, "Mode preference round trip");
            p.shouldUseFastSkeletonizer = false;
            Analyzer.analyze(data, new File("binary.png"), input, p, runner);
            checkMask(data, pixels);
            for (int i = 0; i < pixels.length; i++)
                if ((pixels[i] & 0xffffff) != 0) pixels[i] = 0xff010101;
            Analyzer.analyze(data, new File("binary01.png"), input, p, runner);
            checkMask(data, pixels);
            for (int invalid : new int[] {0xff808080, 0xffff0000}) {
                pixels[0] = invalid;
                try {
                    Analyzer.analyze(data, new File("invalid.png"), input, p, runner);
                    throw new AssertionError("Non-binary input must be rejected");
                }
                catch (IllegalArgumentException expected) {
                    check(expected.getMessage().contains("Non-binary pixel"), "Actionable validation message");
                }
            }
            Arrays.fill(pixels, 0xff000000);
            Analyzer.analyze(data, new File("empty.png"), input, p, runner);
            check(data.vesselPixelArea == 0, "Empty mask supported");
            testCleanup(runner);
            System.out.println("BinaryMaskTest: PASS; outputs: " + root);
        }
        finally {
            Analyzer.threadPool.shutdown();
        }
    }

    private static void checkMask(Analyzer.Data data, int[] pixels)
    {
        for (int i = 0; i < pixels.length; i++)
            check((data.analysisImage.buf[i] & 255) == ((pixels[i] & 0xffffff) == 0 ? 0 : 255),
                "Input mask preserved at " + i);
    }

    private static void testCleanup(ISliceRunner runner) throws Exception
    {
        AnalyzerParameters p = AnalyzerParameters.defaults();
        p.shouldUseBinaryMask = true;
        p.shouldUseFastSkeletonizer = true;
        p.shouldCapSkelIterations = false;
        p.shouldComputeLacunarity = false;
        p.shouldComputeThickness = false;
        p.binarySmallParticleRadius = 1;
        p.binaryHoleRadius = 2;
        int width = 40;
        int[] pixels = new int[width * width];
        Arrays.fill(pixels, 0xff000000);
        for (int y = 8; y < 32; y++)
            for (int x = 8; x < 32; x++)
                pixels[x + width * y] = 0xffffffff;
        pixels[2 + 2 * width] = 0xffffffff;
        for (int y = 16; y < 18; y++)
            for (int x = 16; x < 18; x++)
                pixels[x + width * y] = 0xff000000;
        ArgbBuffer input = new ArgbBuffer(pixels, width, width);
        Analyzer.Data data = new Analyzer.Data(null, 0);
        File file = new File("cleanup.png");
        Analyzer.analyze(data, file, input, p, runner);
        checkMask(data, pixels);
        p.binaryFillHoles = true;
        Analyzer.analyze(data, file, input, p, runner);
        check(data.analysisImage.buf[16 + 16 * width] == (byte)255, "Small enclosed hole filled");
        check(data.analysisImage.buf[2 + 2 * width] == (byte)255, "Fill alone retains particles");
        p.binaryFillHoles = false;
        p.binaryRemoveSmallParticles = true;
        Analyzer.analyze(data, file, input, p, runner);
        check(data.analysisImage.buf[2 + 2 * width] == 0, "Small particle removed");
        check(data.analysisImage.buf[16 + 16 * width] == 0, "Removal alone retains holes");
        p.binaryFillHoles = true;
        Analyzer.analyze(data, file, input, p, runner);
        check(data.analysisImage.buf[2 + 2 * width] == 0, "Filling does not restore deleted particle");
        check(data.analysisImage.buf[16 + 16 * width] == (byte)255, "Combined cleanup fills hole");
        p.binaryHoleRadius = 1000;
        Analyzer.analyze(data, file, input, p, runner);
        check(data.analysisImage.buf[0] == 0, "Large threshold never fills exterior background");
        Arrays.fill(pixels, 0xffffffff);
        pixels[0] = 0xff000000;
        Analyzer.analyze(data, file, input, p, runner);
        check(data.analysisImage.buf[0] == 0, "Single border-connected background pixel retained");
        ATPreferences.savePreferences(p, "cleanup-test.txt");
        AnalyzerParameters loaded = AnalyzerParameters.defaults();
        ATPreferences.loadPreferences(loaded, AngioTool.class, "cleanup-test.txt");
        check(loaded.binaryFillHoles && loaded.binaryRemoveSmallParticles &&
            !loaded.shouldFillHoles && !loaded.shouldRemoveSmallParticles,
            "Mode-specific cleanup preferences are independent");
        p.binaryHoleRadius = Double.NaN;
        check(p.validate().size > 0, "Invalid binary cleanup radius rejected");
    }

    private static void check(boolean condition, String message)
    {
        if (!condition) throw new AssertionError(message);
    }
}
