package AngioTool;

import Pixels.*;
import Utils.*;
import Xlsx.*;
import java.io.IOException;
import java.io.File;
import java.util.Date;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class BatchProcessing
{
    public static final long NANOS_PER_SEC = 1000000000L;

    static final HashSet<String> suffixesToSkip = Misc.makeHashSetFromStringArray(new String[] {"raw-skeleton", "pruned-skeleton",
        "result",
        "tubeness",
        "thresholded",
        "filtered",
        "overlay",
        "vessel-mask",
        "overlay-data"
    });

    public interface IProgressToken
    {
        boolean isClosed();
        void notifyNoImages();
        void onEnumerationStart();
        void onBatchStatsKnown(int nImages);
        void notifyImageWasInvalid();
        void onImageDone(String absPath, Throwable error);
        void onFinished(SpreadsheetWriter sw);
    }

    static class AnalysisInput
    {
        File file;
        String baseOutputPath;

        AnalysisInput(File file, String baseOutputPath)
        {
            this.file = file;
            this.baseOutputPath = baseOutputPath;
        }
    }

    static class AnalysisResult
    {
        File file;
        Analyzer.Stats stats;
        Throwable error;
        boolean exiting;

        AnalysisResult(File file, Analyzer.Stats stats, Throwable error)
        {
            this.file = file;
            this.stats = stats;
            this.error = error;
        }

        static AnalysisResult makeExitToken()
        {
            AnalysisResult token = new AnalysisResult(null, null, null);
            token.exiting = true;
            return token;
        }
    }

    static class AnalysisWorkers
    {
        class Worker implements Runnable
        {
            @Override
            public void run()
            {
                try {
                    runImpl();
                }
                catch (Throwable t) {
                    t.printStackTrace();
                }
                finally {
                    outQueue.add(AnalysisResult.makeExitToken());
                }
            }

            void runImpl()
            {
                ISliceRunner sliceRunner = new ISliceRunner.Series();
                Analyzer.Data data = params.shouldUseBinaryMask ? new Analyzer.Data(null, 0) :
                    new Analyzer.Data(params.brightnessLineSegments, params.brightnessLineSegments.length / 2);

                double imageResizeFactor = params.effectiveResizeFactor();

                AnalysisInput input;
                while ((input = inQueue.poll()) != null) {
                    if (cancellationToken.get())
                        break;

                    ArgbBuffer inputImage = null;
                    try {
                        inputImage = ImageFile.acquireImageForAnalysis(
                            input.file.getAbsolutePath(),
                            imageResizeFactor
                        );
                    }
                    catch (Throwable ex) {
                        ex.printStackTrace();
                    }

                    if (inputImage == null) {
                        outQueue.add(new AnalysisResult(input.file, null, null));
                        continue;
                    }

                    Analyzer.Stats result = null;
                    Throwable error = null;
                    boolean analyzeSucceeded = false;
                    try {
                        result = Analyzer.analyze(data, input.file, inputImage, params, sliceRunner);
                        analyzeSucceeded = true;
                        if (batchParams.shouldSaveResultImages)
                            saveResultImage(
                                params,
                                batchParams,
                                data,
                                result,
                                inputImage,
                                input.file,
                                input.baseOutputPath,
                                imageFormat
                            );
                    }
                    catch (Throwable ex) {
                        cancellationToken.set(true);
                        ex.printStackTrace();
                        error = ex;
                    }
                    finally {
                        ImageFile.releaseImage(inputImage);
                    }

                    outQueue.add(new AnalysisResult(input.file, result, error));

                    if (error != null)
                        break;
                }

                data.nullify();
            }
        }

        final ConcurrentLinkedQueue<AnalysisInput> inQueue;
        final LinkedBlockingQueue<AnalysisResult> outQueue;
        final AnalyzerParameters params;
        final BatchParameters batchParams;
        final IProgressToken uiToken;
        final AtomicBoolean cancellationToken;
        final String imageFormat;

        RefVector<Worker> workers = new RefVector<>(Worker.class);

        AnalysisWorkers(AnalysisInput[] inputs, String format, AnalyzerParameters params, BatchParameters batchParams, IProgressToken uiToken)
        {
            this.inQueue = new ConcurrentLinkedQueue<>();
            this.outQueue = new LinkedBlockingQueue<>();
            this.imageFormat = format;
            this.params = params;
            this.batchParams = batchParams;
            this.uiToken = uiToken;
            this.cancellationToken = new AtomicBoolean(false);

            for (int i = 0; i < inputs.length; i++)
                inQueue.add(inputs[i]);
        }

        Worker addWorker()
        {
            Worker w = new Worker();
            workers.add(w);
            return w;
        }
    }

    public static void doBatchAnalysis(
        AnalyzerParameters params,
        BatchParameters batchParams,
        IProgressToken uiToken,
        ArrayList<XlsxReader.SheetCells> originalSheets
    ) {
        if (batchParams.workerCount <= 0)
            return;

        String imageFormat = null;
        if (batchParams.shouldSaveResultImages && batchParams.shouldSaveImagesToSpecificFolder) {
            try {
                imageFormat = resolveImageFormat(batchParams.resultImageFormat);
            }
            catch (Exception ex) {
                Misc.showExceptionInDialogBox(ex);
                return;
            }
        }

        uiToken.onEnumerationStart();

        ArrayList<File> inputs = new ArrayList<>();
        BidirectionalMap<String, String> outputPathMap = new BidirectionalMap<>();
        for (String path : batchParams.inputImagePaths) {
            enumerateImageFilesRecursively(inputs, new File(Misc.stripWrappingQuotes(path)), outputPathMap, uiToken);
            if (uiToken.isClosed())
                return;
        }

        if (inputs.isEmpty()) {
            uiToken.notifyNoImages();
            return;
        }

        File excelPath = new File(batchParams.resolveExcelFilePath());
        SpreadsheetWriter writer;
        SpreadsheetWriter normalizedWriter = null;
        try {
            ArrayList<XlsxReader.SheetCells> workbookSheets = originalSheets;
            if (batchParams.shouldUseAutomaticExcelPath && excelPath.isFile() && workbookSheets.isEmpty())
                workbookSheets = XlsxReader.loadXlsxFromFile(excelPath.getAbsolutePath());
            writer = createBatchWriterWithNewSheet(workbookSheets, excelPath.getParentFile(), excelPath.getName(), params);
            if (params.shouldNormalizeOutputData) {
                String normalizedName = normalizedWorkbookName(excelPath.getName());
                File normalizedFile = new File(excelPath.getParentFile(), normalizedName);
                ArrayList<XlsxReader.SheetCells> normalizedSheets = normalizedFile.isFile() ?
                    XlsxReader.loadXlsxFromFile(normalizedFile.getAbsolutePath()) :
                    new ArrayList<>();
                normalizedWriter = createNormalizedBatchWriterWithNewSheet(
                    normalizedSheets, excelPath.getParentFile(), normalizedName, params);
            }
        }
        catch (IOException ex) {
            Misc.showExceptionInDialogBox(ex);
            return;
        }

        final int nImages = inputs.size();
        uiToken.onBatchStatsKnown(nImages);

        AnalysisInput[] analysisInputs = new AnalysisInput[nImages];

        if (batchParams.shouldSaveImagesToSpecificFolder) {
            for (int i = 0; i < nImages; i++) {
                File f = inputs.get(i);
                analysisInputs[i] = new AnalysisInput(f, resolveOutputPath(batchParams.resultImagesPath, outputPathMap, f));
            }
        }
        else {
            for (int i = 0; i < nImages; i++) {
                File f = inputs.get(i);
                analysisInputs[i] = new AnalysisInput(f, f.getAbsolutePath());
            }
        }

        AnalysisWorkers workers = new AnalysisWorkers(
            analysisInputs,
            imageFormat,
            params,
            batchParams,
            uiToken
        );

        for (int i = 0; i < batchParams.workerCount; i++)
            Analyzer.threadPool.submit(workers.addWorker());

        writer.shouldSaveAfterEveryRow = false;
        if (normalizedWriter != null)
            normalizedWriter.shouldSaveAfterEveryRow = false;

        long lastSpreadsheetTime = 0L;
        boolean justSaved = false;
        boolean wasInputQueueEmpty = false;
        int nResults = 0;
        int nWorkersAlive = batchParams.workerCount;

        while (nResults < nImages && nWorkersAlive > 0 && !workers.cancellationToken.get()) {
            if (uiToken.isClosed()) {
                workers.cancellationToken.set(true);
                break;
            }

            AnalysisResult result = null;
            try {
                result = workers.outQueue.take();
            }
            catch (InterruptedException ex) {
                workers.cancellationToken.set(true);
                break;
            }

            if (result.exiting) {
                nWorkersAlive--;
                continue;
            }

            nResults++;

            // if an error is detected here, then 'cancellationToken' has already been set
            if (result.error != null) {
                writer.shouldSaveAfterEveryRow = true;

                try { Analyzer.writeError(writer, result.error, result.file); }
                catch (Exception ignored) {}
                if (normalizedWriter != null) {
                    normalizedWriter.shouldSaveAfterEveryRow = true;
                    try { Analyzer.writeError(normalizedWriter, result.error, result.file); }
                    catch (Exception ignored) {}
                }

                uiToken.onImageDone(result.file.getAbsolutePath(), result.error);
                Misc.showExceptionInDialogBox(result.error);
                break;
            }
            else if (result.stats == null) {
                uiToken.notifyImageWasInvalid();
                continue;
            }

            justSaved = false;

            long resultTime = System.nanoTime();
            if (lastSpreadsheetTime == 0L || resultTime - lastSpreadsheetTime < 5 * NANOS_PER_SEC) {
                writer.shouldSaveAfterEveryRow = true;
                if (normalizedWriter != null)
                    normalizedWriter.shouldSaveAfterEveryRow = true;
                lastSpreadsheetTime = resultTime;
                justSaved = true;
            }

            try {
                writeBatchResultToSheet(writer, result.stats);
                if (normalizedWriter != null)
                    writeNormalizedBatchResultToSheet(normalizedWriter, params, result.stats);
            }
            catch (IOException ex) {
                workers.cancellationToken.set(true);
                uiToken.onImageDone(result.file.getAbsolutePath(), ex);
                Misc.showExceptionInDialogBox(ex);
                break;
            }

            writer.shouldSaveAfterEveryRow = false;
            if (normalizedWriter != null)
                normalizedWriter.shouldSaveAfterEveryRow = false;
            uiToken.onImageDone(result.file.getAbsolutePath(), null);
        }

        if (lastSpreadsheetTime == 0L) {
            uiToken.notifyNoImages();
            return;
        }

        if (!justSaved) {
            try {
                writer.save();
                if (normalizedWriter != null)
                    normalizedWriter.save();
            }
            catch (IOException ex) {
                Misc.showExceptionInDialogBox(ex);
            }
        }

        writer.shouldSaveAfterEveryRow = true;
        if (normalizedWriter != null)
            normalizedWriter.shouldSaveAfterEveryRow = true;
        uiToken.onFinished(writer);
    }

    static void saveResultImage(
        AnalyzerParameters params,
        BatchParameters batchParams,
        Analyzer.Data data,
        Analyzer.Stats stats,
        ArgbBuffer inputImage,
        File sourceFile,
        String basePath,
        String format
    ) throws IOException
    {
        String resultImagePath;
        String exportBasePath;
        if (batchParams.shouldSaveImagesToSpecificFolder) {
            resultImagePath = basePath + " result." + format;
            exportBasePath = basePath;
        }
        else {
            File resultImageFile = ImagingWindow.getDefaultResultImageFile(sourceFile);
            if (resultImageFile == null)
                throw new IOException("Result path could not be determined for: " + sourceFile);

            ImagingWindow.ensureResultFolderExists(resultImageFile);
            resultImagePath = resultImageFile.getAbsolutePath();

            int extensionOffset = resultImagePath.lastIndexOf('.');
            format = resultImagePath.substring(extensionOffset + 1).toLowerCase();
            exportBasePath = resultImagePath.substring(0, extensionOffset);
        }

        Analyzer.drawOverlay(
            params,
            data.convexHull,
            data.skelResult,
            data.analysisImage.buf,
            inputImage.pixels,
            inputImage.pixels,
            null,
            inputImage.width,
            inputImage.height,
            inputImage.brightestChannel
        );

        ImageFile.saveImage(inputImage, format, resultImagePath);
        OverlayDataExporter.export(
            exportBasePath,
            params,
            data,
            stats,
            inputImage.width,
            inputImage.height,
            inputImage.brightestChannel
        );
    }

    static String resolveOutputPath(
        String resultImagesPath,
        BidirectionalMap<String, String> outputPathMap,
        File inFile
    ) {
        resultImagesPath = Misc.stripWrappingQuotes(resultImagesPath);
        String outFolder = outputPathMap.getBack(inFile.getParent());
        File outFolderPath = new File(resultImagesPath, outFolder);
        boolean shouldCreateFolder = false;

        if (outFolderPath.exists()) {
            if (outFolderPath.isFile()) {
                outFolderPath.delete();
                shouldCreateFolder = true;
            }
        }
        else {
            shouldCreateFolder = true;
        }

        if (shouldCreateFolder)
            outFolderPath.mkdirs();

        return new File(outFolderPath, inFile.getName()).getAbsolutePath();
    }

    public static String resolveImageFormat(String str) throws Exception
    {
        if (str == null || str.length() == 0)
            throw new Exception("File extension was null");

        byte[] bytes = str.getBytes();
        int start = 0;
        for (int i = bytes.length - 1; i >= 0; i--) {
            byte c = bytes[i];
            c = (byte)(c >= 'A' && c <= 'Z' ? c + 0x20 : c);
            if (c == '.') {
                start = i + 1;
                break;
            }
            if (c >= 0x7f)
                throw new Exception("File extension contains non-ASCII characters");
            bytes[i] = c;
        }

        if (start >= bytes.length)
            throw new Exception("Invalid file extension: " + str);

        return new String(bytes, start, bytes.length - start);
    }

    static void enumerateImageFilesRecursively(
        ArrayList<File> images,
        File currentFolder,
        BidirectionalMap<String, String> outputPathMap,
        IProgressToken uiToken
    ) {
        String curAbsPath = currentFolder.getAbsolutePath();
        String outFolder = currentFolder.getName();
        outFolder = outFolder == null || outFolder.length() == 0 ? "root" : outFolder;

        boolean addedParent = false;
        int retries = 0;
        String retryStr = "";

        while (true) {
            int status = outputPathMap.maybeAdd(curAbsPath, outFolder + retryStr);
            if (status == BidirectionalMap.SUCCESS || (status & BidirectionalMap.FAIL_FRONT) != 0)
                break;

            if (!addedParent) {
                addedParent = true;
                File parent = currentFolder.getParentFile();
                if (parent != null) {
                    String pName = parent.getName();
                    if (pName != null && pName.length() > 0) {
                        outFolder += "_" + pName;
                        continue;
                    }
                }
            }

            retries++;
            retryStr = "_" + (retries + 1);
        }

        File[] list = currentFolder.listFiles();
        for (File f : list) {
            if (f.isDirectory()) {
                if ("results".equalsIgnoreCase(f.getName()))
                    continue;
                enumerateImageFilesRecursively(images, f, outputPathMap, uiToken);
                if (uiToken.isClosed())
                    return;
            }
            else if (f.isFile()) {
                String name = f.getName().toLowerCase();
                int extIdx = name.lastIndexOf('.');
                if (extIdx > 0) {
                    int spaceIdx = name.lastIndexOf(' ');
                    if (spaceIdx > 0 && spaceIdx < extIdx-1) {
                        String lastWordInFileName = name.substring(spaceIdx + 1, extIdx);
                        if (suffixesToSkip.contains(lastWordInFileName))
                            continue;
                    }

                    String ext = name.substring(extIdx).toLowerCase();
                    if (ext.equals(".txt") || ext.equals(".zip") || ext.equals(".xls") || ext.equals(".xlsx"))
                        continue;
                }

                images.add(f);
            }
        }
    }

    public static SpreadsheetWriter createBatchWriterWithNewSheet(
        ArrayList<XlsxReader.SheetCells> originalSheets,
        File folder,
        String sheetName,
        AnalyzerParameters params
    ) throws IOException
    {
        SpreadsheetWriter writer = new SpreadsheetWriter(folder, sheetName);
        writer.addSheets(originalSheets);

        writer.writeRow(
            "Settings"
        );
        writer.writeRow(
            "Low Threshold",
            "High Threshold",
            "Vessel Thickness",
            "Small Particles",
            "Fill Holes",
            "Max Hole Level",
            "Min Boxness",
            "Min Area Length Ratio",
            "Skeletonizer",
            "Max Skeleton Steps",
            "Scaling Factor",
            "Transformed Colors",
            "Hue Transform Weight",
            "Brightness Weight",
            "Target Color",
            "Off Color",
            "Saturation Factor",
            "Brightness Graph",
            "Input Mode",
            "Binary Remove Particles",
            "Binary Particle Radius",
            "Binary Fill Holes",
            "Binary Hole Radius",
            "Prune Terminal Branches",
            "Max Terminal Branch Length (px)",
            "Merge Nearby Junctions",
            "Max Junction Merge Distance (px)",
            "Cap Skeleton Steps",
            "Junction Policy",
            "Normalize Output Data"
        );
        writer.writeRow(
            params.thresholdLow,
            params.thresholdHigh,
            Misc.formatDoubleArray(params.sigmas, ""),
            params.removeSmallParticlesThreshold,
            params.fillHolesValue,
            params.brightShapeThresholdFactor,
            params.minBoxness,
            params.minAreaLengthRatio,
            params.shouldUseFastSkeletonizer ? "Fast" : "Thorough",
            params.maxSkelIterations,
            params.linearScalingFactor,
            params.shouldRemapColors ? "Yes" : "No",
            params.hueTransformWeight,
            params.brightnessTransformWeight,
            params.targetRemapColor.toString(),
            params.voidRemapColor.toString(),
            params.saturationFactor,
            Misc.formatIntVecTwoPointArray(params.brightnessLineSegments),
            params.inputMode(),
            params.binaryRemoveSmallParticles,
            params.binarySmallParticleRadius,
            params.binaryFillHoles,
            params.binaryHoleRadius,
            params.shouldPruneSkeleton,
            params.maxTerminalBranchLength,
            params.shouldMergeNearbyJunctions,
            params.maxJunctionMergeDistance,
            params.shouldCapSkelIterations,
            "connected-region-plus-short-direct-skeleton-links",
            params.shouldNormalizeOutputData
        );
        writer.writeRow(
            "Results"
        );
        writer.writeRow(
            "Image Name",
            "Date",
            "Time",
            "Image Location",
            "Width",
            "Height",
            "Explant Area",
            "Vessels Area",
            "Vessels Percentage Area",
            "Total Number of Junctions",
            "Junctions Density",
            "Total Vessels Length",
            "Average Vessels Length",
            "Average Segment Length",
            "Total Number of End Points",
            "Average Vessel Diameter",
            "Medial E Lacunarity",
            "Mean E Lacunarity",
            "E Lacunarity Gradient",
            "Medial F Lacunarity",
            "Mean F Lacunarity",
            "F Lacunarity Gradient",
            "Fractal Dimension (Box-counting)",
            "Fractal Fit R squared",
            "Fractal Valid",
            "Fractal Invalid Reason",
            "Fractal Box Sizes (px)",
            "Fractal Box Counts",
            "Skeleton Converged",
            "Pruning Status",
            "Pruned Skeleton Pixels",
            "Raw Skeleton Pixels"
        );

        return writer;
    }

    public static String normalizedWorkbookName(String fileName)
    {
        int dot = fileName.lastIndexOf('.');
        if (dot > 0 && fileName.substring(dot).equalsIgnoreCase(".xlsx"))
            return fileName.substring(0, dot) + "_nor" + fileName.substring(dot);
        return fileName + "_nor.xlsx";
    }

    public static SpreadsheetWriter createNormalizedBatchWriterWithNewSheet(
        ArrayList<XlsxReader.SheetCells> originalSheets,
        File folder,
        String sheetName,
        AnalyzerParameters params
    ) throws IOException
    {
        SpreadsheetWriter writer = new SpreadsheetWriter(folder, sheetName);
        writer.addSheets(originalSheets);
        writer.writeRow("Normalization Settings");
        writer.writeRow(
            "Rule Version",
            "Basis Coordinate System",
            "Area Rule",
            "Length Rule",
            "Count Rule",
            "Configured Input Resize Factor",
            "Measurement Scale"
        );
        writer.writeRow(
            NormalizedMetrics.RULE_VERSION,
            "analysis-image",
            NormalizedMetrics.AREA_RULE,
            NormalizedMetrics.LENGTH_RULE,
            NormalizedMetrics.COUNT_RULE,
            params.effectiveResizeFactor(),
            params.shouldApplyLinearScale ? params.linearScalingFactor : 1.0
        );
        writer.writeRow("Normalized Results");
        writer.writeRow(
            "Image Name",
            "Date",
            "Time",
            "Image Location",
            "Analysis Width",
            "Analysis Height",
            "Analysis Image Area (px^2)",
            "Length Basis sqrt(Image Area) (px)",
            "Convex Hull Area / Image Area",
            "Vessel Area / Image Area",
            "Vessels Percentage Area of Hull",
            "Total Number of Junctions",
            "Junction Density x Image Area",
            "Total Vessels Length / sqrt(Image Area)",
            "Average Vessels Length / sqrt(Image Area)",
            "Average Segment Length / sqrt(Image Area)",
            "Total Number of End Points",
            "Total Number of Segments",
            "Average Vessel Diameter / sqrt(Image Area)",
            "Medial E Lacunarity",
            "Mean E Lacunarity",
            "E Lacunarity Gradient",
            "Medial F Lacunarity",
            "Mean F Lacunarity",
            "F Lacunarity Gradient",
            "Fractal Dimension (Box-counting)",
            "Fractal Fit R squared",
            "Fractal Valid",
            "Fractal Invalid Reason",
            "Fractal Box Sizes (raw px)",
            "Fractal Box Counts",
            "Skeleton Converged",
            "Pruning Status",
            "Skeleton Pixels / sqrt(Image Area)",
            "Pruned Skeleton Pixels / sqrt(Image Area)",
            "Raw Skeleton Pixels / sqrt(Image Area)",
            "Isolated Skeleton Pixel Count",
            "Merged Junction Regions"
        );
        return writer;
    }

    public static void writeNormalizedBatchResultToSheet(
        SpreadsheetWriter sw,
        AnalyzerParameters params,
        Analyzer.Stats stats
    ) throws IOException
    {
        Date today = new Date();
        NormalizedMetrics metrics = NormalizedMetrics.from(params, stats);
        sw.writeRow(
            stats.imageFileName,
            sw.dateFormatter.format(today),
            sw.timeFormatter.format(today),
            stats.imageAbsolutePath,
            metrics.analysisWidth,
            metrics.analysisHeight,
            metrics.analysisImageAreaPixels,
            metrics.lengthBasisPixels,
            metrics.convexHullAreaFractionOfImage,
            metrics.vesselAreaFractionOfImage,
            metrics.vesselsPercentageAreaOfHull,
            metrics.junctionCount,
            metrics.junctionDensityTimesImageArea,
            metrics.totalLengthOverSqrtImageArea,
            metrics.averageVesselsLengthOverSqrtImageArea,
            metrics.averageSegmentLengthOverSqrtImageArea,
            metrics.endPointCount,
            metrics.segmentCount,
            metrics.averageVesselDiameterOverSqrtImageArea,
            stats.ELacunarityMedial,
            stats.meanEl,
            stats.ELacunarityCurve,
            stats.FLacunarityMedial,
            stats.meanFl,
            stats.FLacunarityCurve,
            Analyzer.fractalDimensionValue(stats.fractalBoxCount),
            Analyzer.fractalFitValue(stats.fractalBoxCount),
            stats.fractalBoxCount != null && stats.fractalBoxCount.valid,
            Analyzer.fractalInvalidReason(stats.fractalBoxCount),
            stats.fractalBoxCount != null && stats.fractalBoxCount.boxSizes != null ?
                java.util.Arrays.toString(stats.fractalBoxCount.boxSizes) : "[]",
            stats.fractalBoxCount != null && stats.fractalBoxCount.boxCounts != null ?
                java.util.Arrays.toString(stats.fractalBoxCount.boxCounts) : "[]",
            stats.skeletonConverged,
            stats.pruningStatus,
            metrics.skeletonPixelsOverSqrtImageArea,
            metrics.prunedSkeletonPixelsOverSqrtImageArea,
            metrics.rawSkeletonPixelsOverSqrtImageArea,
            stats.isolatedSkeletonPixelCount,
            stats.mergedJunctionRegionCount
        );
    }

    public static void writeBatchResultToSheet(SpreadsheetWriter sw, Analyzer.Stats stats) throws IOException
    {
        Date today = new Date();
        String dateOut = sw.dateFormatter.format(today);
        String timeOut = sw.timeFormatter.format(today);

        sw.writeRow(
            stats.imageFileName,
            dateOut,
            timeOut,
            stats.imageAbsolutePath,
            stats.imageWidth,
            stats.imageHeight,
            stats.allantoisMMArea,
            stats.vesselMMArea,
            stats.vesselPercentageArea,
            stats.totalNJunctions,
            stats.junctionsPerScaledArea,
            stats.totalLength,
            stats.averageBranchLength,
            stats.averageSegmentLength,
            stats.totalNEndPoints,
            stats.averageVesselDiameter,
            stats.ELacunarityMedial,
            stats.meanEl,
            stats.ELacunarityCurve,
            stats.FLacunarityMedial,
            stats.meanFl,
            stats.FLacunarityCurve,
            Analyzer.fractalDimensionValue(stats.fractalBoxCount),
            Analyzer.fractalFitValue(stats.fractalBoxCount),
            stats.fractalBoxCount != null && stats.fractalBoxCount.valid,
            Analyzer.fractalInvalidReason(stats.fractalBoxCount),
            stats.fractalBoxCount != null && stats.fractalBoxCount.boxSizes != null ?
                java.util.Arrays.toString(stats.fractalBoxCount.boxSizes) : "[]",
            stats.fractalBoxCount != null && stats.fractalBoxCount.boxCounts != null ?
                java.util.Arrays.toString(stats.fractalBoxCount.boxCounts) : "[]",
            stats.skeletonConverged,
            stats.pruningStatus,
            stats.prunedSkeletonPixelCount,
            stats.rawSkeletonPixelCount
        );
    }

}
