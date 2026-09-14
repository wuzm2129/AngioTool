package AngioTool;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import Xlsx.SpreadsheetWriter;
import Xlsx.XlsxReader;

public class NormalizedMetricsTest
{
    public static void main(String[] args) throws Exception
    {
        AnalyzerParameters params = AnalyzerParameters.defaults();
        params.shouldApplyLinearScale = true;
        params.linearScalingFactor = 2.0;

        Analyzer.Stats stats = makeStats(400, 100, 80000.0, 40000.0, 10.0, 2000.0);
        NormalizedMetrics metrics = NormalizedMetrics.from(params, stats);
        assertNear(0.5, metrics.convexHullAreaFractionOfImage);
        assertNear(0.25, metrics.vesselAreaFractionOfImage);
        assertNear(20.0, metrics.junctionDensityTimesImageArea);
        assertNear(5.0, metrics.totalLengthOverSqrtImageArea);
        assertNear(0.5, metrics.prunedSkeletonPixelsOverSqrtImageArea);
        assertNear(1.5, metrics.rawSkeletonPixelsOverSqrtImageArea);

        AnalyzerParameters doubledParams = AnalyzerParameters.defaults();
        Analyzer.Stats doubled = makeStats(800, 200, 80000.0, 40000.0, 10.0, 2000.0);
        NormalizedMetrics doubledMetrics = NormalizedMetrics.from(doubledParams, doubled);
        assertNear(metrics.convexHullAreaFractionOfImage, doubledMetrics.convexHullAreaFractionOfImage);
        assertNear(metrics.vesselAreaFractionOfImage, doubledMetrics.vesselAreaFractionOfImage);
        assertNear(metrics.totalLengthOverSqrtImageArea, doubledMetrics.totalLengthOverSqrtImageArea);

        Analyzer.Stats invalid = makeStats(10, 10, 0.0, 0.0, 0.0, 0.0);
        NormalizedMetrics invalidMetrics = NormalizedMetrics.from(doubledParams, invalid);
        if (invalidMetrics.junctionDensityTimesImageArea != null ||
                invalidMetrics.vesselsPercentageAreaOfHull != null)
            throw new AssertionError("Degenerate hull ratios must be undefined");

        if (!"results_nor.xlsx".equals(BatchProcessing.normalizedWorkbookName("results.xlsx")))
            throw new AssertionError("Unexpected normalized workbook name");
        if (!"RESULTS_nor.XLSX".equals(BatchProcessing.normalizedWorkbookName("RESULTS.XLSX")))
            throw new AssertionError("XLSX suffix handling must be case-insensitive");

        File json = File.createTempFile("angiotool-normalized-", ".json");
        try {
            Analyzer.Data data = new Analyzer.Data(null, 0);
            OverlayDataExporter.saveNormalizedJson(
                json.getAbsolutePath(), params, data, stats, stats.imageWidth, stats.imageHeight, 1);
            String text = new String(Files.readAllBytes(json.toPath()), StandardCharsets.UTF_8);
            assertContains(text, "\"ruleVersion\": \"image-relative-v1\"");
            assertContains(text, "\"vesselAreaFractionOfImage\": 0.25");
            assertContains(text, "\"junctionDensityTimesImageArea\": 20.0");
        }
        finally {
            json.delete();
        }

        File workbookFolder = Files.createTempDirectory("angiotool-normalized-xlsx-").toFile();
        File workbook = new File(workbookFolder, "results_nor.xlsx");
        try {
            SpreadsheetWriter sw = BatchProcessing.createNormalizedBatchWriterWithNewSheet(
                new ArrayList<XlsxReader.SheetCells>(), workbookFolder, workbook.getName(), params);
            BatchProcessing.writeNormalizedBatchResultToSheet(sw, params, stats);
            sw.save();
            if (!workbook.isFile() || XlsxReader.loadXlsxFromFile(workbook.getAbsolutePath()).isEmpty())
                throw new AssertionError("Normalized workbook was not created or readable");
        }
        finally {
            workbook.delete();
            workbookFolder.delete();
        }

        System.out.println("NormalizedMetricsTest: PASS");
    }

    private static Analyzer.Stats makeStats(
        int width,
        int height,
        double hullArea,
        double vesselArea,
        double junctionCount,
        double totalLength
    ) {
        Analyzer.Stats stats = new Analyzer.Stats();
        stats.imageFileName = "test.png";
        stats.imageAbsolutePath = "test.png";
        stats.imageWidth = width;
        stats.imageHeight = height;
        stats.valid = hullArea > 0.0;
        stats.invalidReason = stats.valid ? "" : "degenerate-mask-area";
        stats.allantoisMMArea = hullArea;
        stats.vesselMMArea = vesselArea;
        stats.vesselPercentageArea = hullArea > 0.0 ? vesselArea * 100.0 / hullArea : 0.0;
        stats.totalNJunctions = junctionCount;
        stats.junctionsPerScaledArea = hullArea > 0.0 ? junctionCount / hullArea : 0.0;
        stats.totalLength = totalLength;
        stats.averageBranchLength = totalLength / 2.0;
        stats.averageSegmentLength = totalLength / 4.0;
        stats.totalSegments = 4;
        stats.totalNEndPoints = 3;
        stats.averageVesselDiameter = 40.0;
        stats.prunedSkeletonPixelCount = width / 4;
        stats.rawSkeletonPixelCount = 3 * width / 4;
        stats.pruningStatus = "applied";
        return stats;
    }

    private static void assertNear(double expected, Double actual)
    {
        if (actual == null || Math.abs(expected - actual.doubleValue()) > 1.0e-12)
            throw new AssertionError("Expected " + expected + " but got " + actual);
    }

    private static void assertContains(String text, String expected)
    {
        if (!text.contains(expected))
            throw new AssertionError("Missing JSON content: " + expected);
    }
}
