package AngioTool;

import Utils.BidirectionalMap;
import Xlsx.SpreadsheetWriter;
import Xlsx.XlsxReader;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;

public class BatchOutputPathTest
{
    static class ProgressToken implements BatchProcessing.IProgressToken
    {
        Throwable error;
        boolean finished;

        public boolean isClosed() { return false; }
        public void notifyNoImages() {}
        public void onEnumerationStart() {}
        public void onBatchStatsKnown(int nImages) {}
        public void notifyImageWasInvalid() {}
        public void onImageDone(String absPath, Throwable error) { this.error = error; }
        public void onFinished(SpreadsheetWriter sw) { finished = true; }
    }

    public static void main(String[] args) throws Exception
    {
        Path root = Files.createTempDirectory("angiotool-batch-output-");
        try {
            File sourceWithExtension = Files.createFile(root.resolve("sample.tif")).toFile();
            File sourceWithoutExtension = Files.createFile(root.resolve("sample-no-extension")).toFile();

            File resultWithExtension = ImagingWindow.getDefaultResultImageFile(sourceWithExtension);
            File resultWithoutExtension = ImagingWindow.getDefaultResultImageFile(sourceWithoutExtension);

            assertPath(resultWithExtension, root.resolve("results").resolve("ana_sample.tif"));
            assertPath(resultWithoutExtension, root.resolve("results").resolve("ana_sample-no-extension.png"));

            ImagingWindow.ensureResultFolderExists(resultWithExtension);
            Files.createFile(root.resolve("results").resolve("ana_old-result.png"));
            Files.createFile(root.resolve("notes.txt"));

            ArrayList<File> images = new ArrayList<>();
            BidirectionalMap<String, String> outputPathMap = new BidirectionalMap<>();
            BatchProcessing.enumerateImageFilesRecursively(images, root.toFile(), outputPathMap, new ProgressToken());

            assertContains(images, sourceWithExtension);
            assertContains(images, sourceWithoutExtension);
            assertNotContains(images, root.resolve("results").resolve("ana_old-result.png").toFile());
            assertNotContains(images, root.resolve("notes.txt").toFile());

            BatchParameters automatic = new BatchParameters(
                root.toString(), new String[] {root.toString()}, "", true,
                false, false, "", "png", false, 1);
            assertPath(new File(automatic.resolveExcelFilePath()),
                root.resolve("result_" + root.getFileName() + ".xlsx"));
            automatic.excelFilePath = "custom-name";
            assertPath(new File(automatic.resolveExcelFilePath()), root.resolve("custom-name.xlsx"));

            if (args.length > 0)
                runBatchSmokeTest(root.resolve("smoke"), new File(args[0]));

            System.out.println("Batch output path checks passed");
        }
        finally {
            Analyzer.threadPool.shutdownNow();
            ImagingWindow.threadPool.shutdownNow();
            Files.walk(root)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try { Files.delete(path); }
                    catch (Exception ignored) {}
                });
        }
    }

    static void runBatchSmokeTest(Path root, File referenceImage) throws Exception
    {
        Files.createDirectories(root);
        File input = root.resolve("sample.png").toFile();
        Files.copy(referenceImage.toPath(), input.toPath(), StandardCopyOption.REPLACE_EXISTING);

        File spreadsheet = root.resolve("batch-results.xlsx").toFile();
        BatchParameters batchParams = new BatchParameters(
            root.toString(),
            new String[] { root.toString() },
            spreadsheet.getAbsolutePath(),
            true,
            false,
            "",
            "",
            true,
            1
        );

        if (batchParams.validate().size > 0)
            throw new AssertionError("results-folder mode unexpectedly required an output format");

        ProgressToken token = new ProgressToken();
        BatchProcessing.doBatchAnalysis(
            AnalyzerParameters.defaults(),
            batchParams,
            token,
            new ArrayList<XlsxReader.SheetCells>()
        );

        if (token.error != null)
            throw new AssertionError("Batch analysis failed", token.error);
        if (!token.finished)
            throw new AssertionError("Batch analysis did not finish");

        assertFile(root.resolve("results").resolve("ana_sample.png"));
        assertFile(root.resolve("results").resolve("ana_sample vessel-mask.png"));
        assertFile(root.resolve("results").resolve("ana_sample overlay-data.json"));
        assertFile(spreadsheet.toPath());
    }

    static void assertPath(File actual, Path expected)
    {
        if (actual == null || !actual.toPath().toAbsolutePath().normalize().equals(expected.toAbsolutePath().normalize()))
            throw new AssertionError("Expected " + expected + ", got " + actual);
    }

    static void assertContains(ArrayList<File> files, File expected)
    {
        if (!files.contains(expected))
            throw new AssertionError("Expected input was not enumerated: " + expected);
    }

    static void assertNotContains(ArrayList<File> files, File unexpected)
    {
        if (files.contains(unexpected))
            throw new AssertionError("Generated or excluded file was enumerated: " + unexpected);
    }

    static void assertFile(Path path)
    {
        if (!Files.isRegularFile(path))
            throw new AssertionError("Expected output was not created: " + path);
    }
}
