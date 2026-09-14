package AngioTool;

import Algorithms.FractalBoxCount;
import Pixels.ArgbBuffer;
import Pixels.ImageFile;
import Utils.ISliceRunner;
import Xlsx.SpreadsheetWriter;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/** Exercises the public analysis, presentation and export paths, not just the estimator. */
public class FractalIntegrationTest
{
    public static void main(String[] args) throws Exception
    {
        Path root = args.length == 0 ? Files.createTempDirectory("angiotool-fractal-") :
            Files.createDirectories(Paths.get(args[0]).toAbsolutePath());
        AnalyzerParameters p = AnalyzerParameters.defaults();
        p.shouldUseBinaryMask = true;
        p.shouldUseFastSkeletonizer = true;
        p.shouldCapSkelIterations = false;
        p.shouldComputeThickness = false;
        p.shouldComputeLacunarity = false;
        Analyzer.Data data = new Analyzer.Data(null, 0);
        ISliceRunner runner = new ISliceRunner.Parallel(Analyzer.threadPool);
        int width = 128;
        int[] pixels = new int[width * width];
        Arrays.fill(pixels, 0xff000000);
        for (int y = 32; y < 96; y++)
            for (int x = 32; x < 96; x++)
                pixels[x + width * y] = 0xffffffff;
        ArgbBuffer image = new ArgbBuffer(pixels, width, width);
        try {
            Analyzer.Stats square = Analyzer.analyze(data, new File("square.png"), image, p, runner);
            check(square.fractalBoxCount.valid, "Square is available without lacunarity/thickness");
            near(square.fractalBoxCount.dimension, 2.0, "Filled square dimension");
            near(square.fractalBoxCount.rSquared, 1.0, "Filled square fit");
            p.shouldNormalizeOutputData = true;
            OverlayDataExporter.export(root.resolve("square").toString(), p, data, square, width, width, 0);
            OverlayDataExporter.export(root.resolve("fallback").toString(), p, data, null, width, width, 0);
            SpreadsheetWriter sheet = Analyzer.createWriterWithNewSheet(new ArrayList<Xlsx.XlsxReader.SheetCells>(),
                root.toFile(), "single.xlsx");
            Analyzer.writeResultToSheet(sheet, p, square);
            Object[] header = sheet.sheets.buf[0].valueRows.buf[0];
            int dimensionColumn = Arrays.asList(header).indexOf("Fractal Dimension (Box-counting)");
            check(dimensionColumn == 45, "New columns append after all 45 legacy single-image columns");
            near(((Number)sheet.sheets.buf[0].valueRows.buf[1][dimensionColumn]).doubleValue(), 2.0,
                "Single-image XLSX numeric value");

            p.shouldApplyLinearScale = true;
            p.linearScalingFactor = 3.0;
            Analyzer.Stats scaled = Analyzer.analyze(data, null, image, p, runner);
            near(scaled.fractalBoxCount.dimension, square.fractalBoxCount.dimension, "Dimension has no units");
            near(scaled.fractalBoxCount.rSquared, square.fractalBoxCount.rSquared, "Fit has no units");
            check(Arrays.equals(scaled.fractalBoxCount.boxCounts, square.fractalBoxCount.boxCounts),
                "Unit conversion leaves box counts unchanged");
            p.shouldApplyLinearScale = false;

            pixels[60 + width * 60] = 0xff000000;
            p.binaryFillHoles = true;
            p.binaryHoleRadius = 2.0;
            Analyzer.Stats cleaned = Analyzer.analyze(data, null, image, p, runner);
            check(data.analysisImage.buf[60 + width * 60] != 0, "Cleanup fills the test hole");
            compareFinalMask(data, cleaned, width, width);

            Arrays.fill(pixels, 0xff000000);
            Analyzer.Stats empty = Analyzer.analyze(data, new File("empty.png"), image, p, runner);
            check(!empty.fractalBoxCount.valid && Double.isNaN(empty.fractalBoxCount.dimension),
                "Reused Data clears the previous valid dimension on empty input");
            check(empty.fractalBoxCount != square.fractalBoxCount && square.fractalBoxCount.valid,
                "Earlier Stats retain their own result snapshot");
            Analyzer.writeResultToSheet(sheet, p, empty);
            Object emptyValue = sheet.sheets.buf[0].valueRows.buf[2][dimensionColumn];
            check(emptyValue == null || "".equals(emptyValue), "Invalid dimension is blank in XLSX");
            OverlayDataExporter.export(root.resolve("empty").toString(), p, data, empty, width, width, 0);
            OverlayDataExporter.export(root.resolve("empty-fallback").toString(), p, data, null, width, width, 0);
            for (String name : new String[] {"square", "fallback", "empty", "empty-fallback"}) {
                String json = new String(Files.readAllBytes(root.resolve(name + " overlay-data.json")),
                    StandardCharsets.UTF_8);
                check(json.contains("\"fractalBoxCount\""), "JSON exposes fractal metadata");
                check(!json.contains("NaN") && !json.contains("Infinity"), "JSON contains only finite numbers/null");
            }
            String normalizedJson = new String(Files.readAllBytes(root.resolve("square normalized-data.json")),
                StandardCharsets.UTF_8);
            check(normalizedJson.contains("\"ruleVersion\": \"image-relative-v1\""),
                "Single-image normalized JSON produced");
            check(!normalizedJson.contains("NaN") && !normalizedJson.contains("Infinity"),
                "Normalized JSON contains only finite numbers/null");

            // A line has degenerate hull area, but its fractal result remains meaningful.
            for (int x = 0; x < width; x++) pixels[x + width * 64] = 0xffffffff;
            Analyzer.Stats line = Analyzer.analyze(data, null, image, p, runner);
            check(!line.valid && line.fractalBoxCount.valid, "Fractal validity is independent of hull area");
            near(line.fractalBoxCount.dimension, 1.0, "Line dimension");
            SwingUtilities.invokeAndWait(() -> {
                UiTheme.install();
                StatisticsPanel panel = new StatisticsPanel();
                panel.showResults(p, line);
                check("1".equals(findMetric(panel, "Fractal dimension")), "Sidebar shows valid D for line");
                panel.showResults(p, empty);
                check(findMetric(panel, "Fractal dimension").contains("N/A"), "Sidebar marks unavailable value");
                panel.showResults(p, square);
                panel.setSize(450, 900);
                layout(panel);
                BufferedImage capture = new BufferedImage(450, 900, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = capture.createGraphics();
                panel.printAll(graphics);
                graphics.dispose();
                try { ImageIO.write(capture, "png", root.resolve("fractal-measurements.png").toFile()); }
                catch (Exception e) { throw new RuntimeException(e); }
            });

            data.nullify();
            data.restart();
            Analyzer.Stats restarted = Analyzer.analyze(data, null, image, p, runner);
            near(restarted.fractalBoxCount.dimension, 1.0, "Data lifecycle restarts cleanly");
            ArgbBuffer normal = ImageFile.acquireImageForAnalysis("source/deprecated/images/ImageExample.png", 1.0);
            try {
                Analyzer.Stats normalStats = Analyzer.analyze(data, null, normal, AnalyzerParameters.defaults(), runner);
                check(normalStats.fractalBoxCount.valid, "Ordinary Tubeness mode produces fractal result");
                compareFinalMask(data, normalStats, normal.width, normal.height);
                OverlayDataExporter.export(root.resolve("normal").toString(), AnalyzerParameters.defaults(),
                    data, normalStats, normal.width, normal.height, 0);
            }
            finally { ImageFile.releaseImage(normal); }

            Path batchInput = Files.createDirectories(root.resolve("batch-input"));
            Files.copy(root.resolve("square vessel-mask.png"), batchInput.resolve("square.png"),
                StandardCopyOption.REPLACE_EXISTING);
            Files.copy(root.resolve("empty vessel-mask.png"), batchInput.resolve("empty.png"),
                StandardCopyOption.REPLACE_EXISTING);
            BatchParameters batch = new BatchParameters(batchInput.toString(), new String[] {batchInput.toString()},
                "", true, true, false, "", "", true, 1);
            BatchOutputPathTest.ProgressToken token = new BatchOutputPathTest.ProgressToken();
            BatchProcessing.doBatchAnalysis(p, batch, token, new ArrayList<Xlsx.XlsxReader.SheetCells>());
            check(token.finished && token.error == null, "Batch completes with valid and empty images");
            check(Files.isRegularFile(batchInput.resolve("result_batch-input.xlsx")), "Automatic batch XLSX produced");
            check(Files.isRegularFile(batchInput.resolve("result_batch-input_nor.xlsx")), "Normalized batch XLSX produced");
            System.out.println("FractalIntegrationTest: PASS; artifacts: " + root);
        }
        finally {
            Analyzer.threadPool.shutdownNow();
            ImagingWindow.threadPool.shutdownNow();
        }
    }

    private static void compareFinalMask(Analyzer.Data data, Analyzer.Stats stats, int width, int height)
    {
        FractalBoxCount.Result expected = FractalBoxCount.compute(data.analysisImage.buf, width, height);
        check(Arrays.equals(expected.boxCounts, stats.fractalBoxCount.boxCounts),
            "Estimator analyzes the actual final mask");
        near(expected.dimension, stats.fractalBoxCount.dimension, "Final-mask dimension matches");
    }

    private static String findMetric(StatisticsPanel panel, String name)
    {
        for (int row = 0; row < panel.model.getRowCount(); row++)
            if (name.equals(panel.model.getValueAt(row, 0)))
                return String.valueOf(panel.model.getValueAt(row, 1));
        throw new AssertionError("Missing metric: " + name);
    }

    private static void layout(Container container)
    {
        container.doLayout();
        for (Component child : container.getComponents())
            if (child instanceof Container) layout((Container)child);
    }

    private static void near(double actual, double expected, String message)
    {
        check(Double.isFinite(actual) && Math.abs(actual - expected) < 1e-9,
            message + ": " + actual + " vs " + expected);
    }

    private static void check(boolean condition, String message)
    {
        if (!condition) throw new AssertionError(message);
    }
}
