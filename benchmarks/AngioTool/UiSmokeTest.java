package AngioTool;

import Pixels.ArgbBuffer;
import Pixels.ImageFile;
import Pixels.Rgb;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.*;

public class UiSmokeTest
{
    static AngioToolGui2 main;
    static ImagingWindow image;
    static ImagingWindow second;
    static BatchWindow batch;
    static Path outputs;

    static void clickImageTab(ImagingWindow target, boolean title)
    {
        Container header = (Container)main.imageTabs.getTabComponentAt(main.imageTabs.indexOfComponent(target));
        Component component = title ? header.getComponent(0) : header;
        dispatchClick(component, title ? component.getWidth() / 2 : 1, component.getHeight() / 2);
    }

    static void dispatchClick(Component component, int x, int y)
    {
        long now = System.currentTimeMillis();
        component.dispatchEvent(new java.awt.event.MouseEvent(component,
            java.awt.event.MouseEvent.MOUSE_PRESSED, now,
            java.awt.event.InputEvent.BUTTON1_DOWN_MASK, x, y, 1, false,
            java.awt.event.MouseEvent.BUTTON1));
        component.dispatchEvent(new java.awt.event.MouseEvent(component,
            java.awt.event.MouseEvent.MOUSE_RELEASED, now, 0, x, y, 1, false,
            java.awt.event.MouseEvent.BUTTON1));
        component.dispatchEvent(new java.awt.event.MouseEvent(component,
            java.awt.event.MouseEvent.MOUSE_CLICKED, now, 0, x, y, 1, false,
            java.awt.event.MouseEvent.BUTTON1));
    }

    public static void main(String[] args) throws Exception
    {
        Path isolatedHome = Files.createTempDirectory("angiotool-ui-test-");
        System.setProperty("user.home", isolatedHome.toString());
        outputs = Paths.get(args.length > 0 ? args[0] : "screenshots/ui-2.1");
        Files.createDirectories(outputs);
        try {
            File sample = new File("source/deprecated/images/ImageExample.png");
            SwingUtilities.invokeAndWait(() -> {
                main = AngioTool.initializeGui(null);
                check(main.parameterTabs.getTabCount() == 3, "Parameter tabs");
                check(StatisticsPanel.formatValue(0.00016833).equals("1.6833E-4"),
                    "Small values retain exponent");
                check(main.buildAnalyzerParamsFromUi().equals(AnalyzerParameters.defaults()), "Default parameter mapping");
                main.elemRemoveParticles.cb.doClick();
                check(main.elemRemoveParticles.spinner.isEnabled(), "Nested checkbox binding");
                main.elemRemoveParticles.spinner.setValue(22.0);
                check(main.buildAnalyzerParamsFromUi().removeSmallParticlesThreshold == 22, "Spinner mapping");
                main.textMinIntensity.setText("invalid");
                check(main.validatedParams() == null, "Invalid text must be rejected");
                main.textMinIntensity.setText("15");
                main.textSigmas.setText("5, 12");
                main.cbUseBinaryMask.doClick();
                check(main.buildAnalyzerParamsFromUi().shouldUseBinaryMask, "Binary mask mode binding");
                check(main.buildAnalyzerParamsFromUi().sigmas[0] == 5,
                    "Binary mode retains dormant detection settings for persistence");
                check(!main.textSigmas.isEnabled() && !main.elemFillHoles.cb.isEnabled()
                    && !main.elemResizeInputs.cb.isEnabled(), "Binary mode disables segmentation controls");
                check(main.elemBinaryFillHoles.cb.isEnabled() && main.elemBinaryRemoveParticles.cb.isEnabled(),
                    "Binary cleanup is available");
                check(!main.elemBinaryFillHoles.cb.isSelected() &&
                    !main.elemBinaryRemoveParticles.cb.isSelected(), "Binary cleanup defaults off");
                main.elemBinaryRemoveParticles.cb.doClick();
                main.elemBinaryRemoveParticles.spinner.setValue(2.0);
                check(main.buildAnalyzerParamsFromUi().binarySmallParticleRadius == 2,
                    "Binary cleanup value binding");
                main.textMinIntensity.setText("invalid");
                check(main.validatedParams() != null, "Binary mode ignores inactive threshold");
                main.textMinIntensity.setText("15");
                main.cbUseBinaryMask.doClick();
                check(main.textSigmas.isEnabled() && main.textSigmas.getText().equals("5, 12"),
                    "Detection mode restores controls and scales");
                check(main.elemRemoveParticles.cb.isSelected() && main.elemRemoveParticles.getValue() == 22,
                    "Normal cleanup setting preserved");
                main.cbUseBinaryMask.doClick();
                check(main.elemBinaryRemoveParticles.cb.isSelected() &&
                    main.elemBinaryRemoveParticles.getValue() == 2, "Binary cleanup setting preserved");
                main.setAnalysisUi(AnalyzerParameters.defaults());
                main.setSize(UiTheme.size(1480, 900));
            });
            flush();
            SwingUtilities.invokeAndWait(() -> {
                snapshot(main, "parameters.png");
                for (int tab = 1; tab < 3; tab++) {
                    main.parameterTabs.setSelectedIndex(tab);
                    main.validate();
                    snapshot(main, tab == 1 ? "color.png" : "overlay.png");
                }
                main.parameterTabs.setSelectedIndex(0);
                main.setSize(UiTheme.size(1100, 640));
                main.validate();
                check(main.textMaxIntensity.getWidth() > 70, "Small-window threshold width");
                snapshot(main, "parameters-small.png");
                main.setSize(UiTheme.size(1480, 900));
                AbstractButton advanced = findButton(main, "Advanced cleanup");
                check(advanced != null, "Advanced cleanup disclosure");
                advanced.doClick();
                main.validate();
                check(main.elemMinBoxnessPercent.spinner.isShowing(), "Expanded cleanup is visible");
                snapshot(main, "parameters-advanced.png");
                advanced.doClick();
                main.validate();
                batch = new BatchWindow(main, BatchParameters.defaults());
                batch.showDialog();
                check(batch.cbAutomaticExcelPath.isSelected() && !batch.btnExcel.isEnabled(),
                    "Automatic batch workbook defaults on");
                batch.textInputFolders.setText(new File(isolatedHome.toFile(), "input-leaf").getAbsolutePath());
                batch.toggleAutomaticExcelPath(true);
                check(batch.textExcel.getText().equals("result_input-leaf.xlsx"),
                    "Automatic batch workbook name follows input folder");
                snapshot(batch, "batch.png");
                batch.rbSaveResultsTo.doClick();
                check(batch.textSaveResultsFolder.isEnabled(), "Batch output binding");
                batch.rbSameOutput.doClick();
                check(!batch.textSaveResultsFolder.isEnabled(), "Batch results-folder binding");
                batch.cbWorkerCount.doClick();
                batch.workerSpinner.setValue(2);
                check(batch.buildBatchParamsFromUi().workerCount == 2, "Batch worker spinner mapping");
                batch.dispose();
                try {
                    ArgbBuffer source = ImageFile.acquireImageForAnalysis(sample.getAbsolutePath(), 1.0);
                    image = new ImagingWindow(main, source, 1.0, sample, isolatedHome.toString());
                    image.showInWorkspace();
                    check(SwingUtilities.getWindowAncestor(image) == main, "Image embedded in main window");
                    check(main.imageTabs.getTabCount() == 1, "First image tab");
                }
                catch (Exception ex) { throw new RuntimeException(ex); }
            });
            awaitAnalysis();
            SwingUtilities.invokeAndWait(() -> {
                try {
                    second = new ImagingWindow(main,
                        ImageFile.acquireImageForAnalysis(sample.getAbsolutePath(), 1.0),
                        1.0, sample, isolatedHome.toString()).showInWorkspace();
                }
                catch (Exception ex) { throw new RuntimeException(ex); }
            });
            awaitAnalysis(second);
            testParameterActions(isolatedHome);
            SwingUtilities.invokeAndWait(() -> {
                check(main.imageTabs.getTabCount() == 2, "Second image opens as tab");
                check(SwingUtilities.getWindowAncestor(second) == main, "Second image embedded");
                int visibleFrames = 0;
                for (Frame frame : Frame.getFrames())
                    if (frame.isVisible()) visibleFrames++;
                check(visibleFrames == 1, "Only one application frame after opening multiple images");
                clickImageTab(image, true);
                check(main.imageTabs.getSelectedComponent() == image, "Click first image title selects it");
                check(image.statistics.getParent() == main.measurementsArea, "First image measurements selected");
                image.btnActualSize.doClick();
                clickImageTab(second, true);
                check(main.imageTabs.getSelectedComponent() == second, "Click second image title selects it");
                check(second.statistics.getParent() == main.measurementsArea, "Second image measurements selected");
                check(!second.imageUi.actualSize, "Tabs have independent view state");
                clickImageTab(image, false);
                check(main.imageTabs.getSelectedComponent() == image, "Click tab padding selects it");
                check(image.imageUi.actualSize, "Switching tabs preserves view state");
                image.viewOriginal.doClick();
                clickImageTab(second, true);
                check(second.imageUi.viewMode == 2, "Second tab retains independent overlay view");
                clickImageTab(image, true);
                check(image.imageUi.viewMode == 0, "First tab retains original view");
                image.viewOverlay.doClick();
                image.btnFit.doClick();
                snapshot(main, "multiple-images.png");
                Component tab = main.imageTabs.getTabComponentAt(main.imageTabs.indexOfComponent(second));
                dispatchClick(((Container)tab).getComponent(1), 12, 12);
                check(second.closed && main.imageTabs.getTabCount() == 1, "Close only selected image tab");
                check(!image.closed, "Other image remains open");
                check(main.imageTabs.getSelectedComponent() == image, "Closing background tab preserves selection");
            });
            SwingUtilities.invokeAndWait(() -> {
                check(image.imageUi.currentStats != null, "Analysis completed");
                testViews(image, isolatedHome);
                check(image.statistics.model.getRowCount() >= 14, "Statistics sidebar populated");
                Analyzer.Stats originalStats = image.imageUi.currentStats;
                byte[] originalMask = java.util.Arrays.copyOf(image.analyzerData.analysisImage.buf,
                    image.imageUi.imgWidth * image.imageUi.imgHeight);
                AnalyzerParameters style = main.buildAnalyzerParamsFromUi();
                style.outlineColor = new Rgb("00CC88");
                image.updateImage(style);
                check(!image.analysisRunning, "Overlay change must not dispatch analysis");
                check(image.imageUi.currentStats == originalStats, "Overlay must preserve metrics");
                check(java.util.Arrays.equals(originalMask, java.util.Arrays.copyOf(
                    image.analyzerData.analysisImage.buf, originalMask.length)), "Overlay must preserve mask");
                image.btnActualSize.doClick();
                check(image.imageUi.actualSize, "Actual size control");
                image.btnFit.doClick();
                check(!image.imageUi.actualSize && image.imageUi.zoomLevels == 0, "Fit control");
                image.cbShowStats.doClick();
                check(!main.measurementsArea.isVisible() && !image.statistics.isShowing(), "Hide measurements column");
                image.cbShowStats.doClick();
                check(main.measurementsArea.isShowing() && image.statistics.getParent() == main.measurementsArea,
                    "Show measurements in the independent column");
                image.validate();
                main.validate();
                check(main.measurementsArea.getWidth() >= UiTheme.size(280, 0).width,
                    "Measurements retain their minimum width at the current DPI");
                snapshot(main, "analysis.png");
                main.setSize(UiTheme.size(1100, 640));
                main.validate();
                image.initializeDivider();
                check(main.measurementsArea.getWidth() >= UiTheme.size(280, 0).width,
                    "Small windows preserve the measurements width");
                checkControlBounds(image.viewRawSkeleton, "Small-window raw skeleton button");
                checkControlBounds(image.cbShowStats, "Small-window measurements switch");
                snapshot(main, "analysis-small.png");
                main.setSize(UiTheme.size(1480, 900));
                main.validate();
                image.initializeDivider();
                File result = isolatedHome.resolve("result.png").toFile();
                image.textSaveImage.setText(result.getAbsolutePath());
                image.saveResultImage();
                check(result.isFile(), "Overlay exported");
                check(isolatedHome.resolve("result vessel-mask.png").toFile().isFile(), "Mask exported");
                check(isolatedHome.resolve("result overlay-data.json").toFile().isFile(), "JSON exported");
                image.textSaveSpreadsheet.setText(isolatedHome.resolve("result.xlsx").toString());
                image.saveResultSpreadsheet();
                check(isolatedHome.resolve("result.xlsx").toFile().isFile(), "XLSX exported");
                AnalyzerParameters changed = main.buildAnalyzerParamsFromUi();
                changed.thresholdLow = 20;
                image.updateImage(changed);
                changed = main.buildAnalyzerParamsFromUi();
                changed.thresholdLow = 25;
                image.updateImage(changed);
            });
            awaitAnalysis();
            SwingUtilities.invokeAndWait(() -> {
                check(image.imageUi.currentParams.thresholdLow == 25, "Latest pending parameters applied");
                AnalyzerParameters changed = main.buildAnalyzerParamsFromUi();
                changed.thresholdLow = 30;
                image.updateImage(changed);
                main.closeImagingWindow(image);
                check(image.closed, "Close during analysis");
                check(main.imageTabs.getTabCount() == 0, "Empty workspace after last tab closes");
            });
            awaitAnalysis();
            SwingUtilities.invokeAndWait(() ->
                check(image.imageUi.source == null, "Resources released after worker completion"));
            testBinaryWorkspace(isolatedHome);
            System.out.println("UiSmokeTest: PASS; isolated output: " + isolatedHome);
        }
        finally {
            SwingUtilities.invokeAndWait(() -> {
                if (image != null && !image.closed) image.closeSafely();
                if (batch != null) batch.dispose();
                if (main != null) main.dispose();
            });
            ImagingWindow.threadPool.shutdown();
            Analyzer.threadPool.shutdown();
        }
    }

    static void awaitAnalysis() throws Exception { awaitAnalysis(image); }

    static void testViews(ImagingWindow window, Path home)
    {
        Analyzer.Stats stats = window.imageUi.currentStats;
        int[] overlay = window.imageUi.getDrawingBuffer().clone();
        byte[] mask = java.util.Arrays.copyOf(window.analyzerData.analysisImage.buf,
            window.imageUi.imgWidth * window.imageUi.imgHeight);
        window.viewOriginal.doClick();
        check(window.imageUi.viewMode == 0, "Original view selected");
        int[] shown = ((java.awt.image.DataBufferInt)window.imageUi.getDisplayedImage()
            .getRaster().getDataBuffer()).getData();
        for (int i = 0; i < shown.length; i++)
            check((shown[i] & 0xffffff) == (window.imageUi.source.pixels[i] & 0xffffff),
                "Original view pixels");
        snapshot(main, "view-original.png");
        window.viewMask.doClick();
        check(window.imageUi.viewMode == 1, "Mask view selected");
        shown = ((java.awt.image.DataBufferInt)window.imageUi.getDisplayedImage()
            .getRaster().getDataBuffer()).getData();
        for (int i = 0; i < shown.length; i++)
            check((shown[i] & 0xffffff) == (mask[i] == 0 ? 0 : 0xffffff), "Final mask view pixels");
        snapshot(main, "view-mask.png");
        check(stats == window.imageUi.currentStats && !window.analysisRunning, "View switching does not reanalyze");
        check(java.util.Arrays.equals(overlay, window.imageUi.getDrawingBuffer()), "Export buffer remains overlay");
        File output = home.resolve("view-export.png").toFile();
        window.textSaveImage.setText(output.getAbsolutePath());
        window.saveResultImage();
        try {
            BufferedImage saved = ImageIO.read(output);
            for (int i = 0; i < overlay.length; i++)
                check((saved.getRGB(i % saved.getWidth(), i / saved.getWidth()) & 0xffffff) ==
                    (overlay[i] & 0xffffff), "Export from mask view stays overlay");
        }
        catch (Exception ex) { throw new RuntimeException(ex); }
        window.viewOverlay.doClick();
        check(window.imageUi.getDisplayedImage() == window.imageUi.drawingImage, "Overlay view restored");
    }


    static void testParameterActions(Path home) throws Exception
    {
        File snapshot = home.resolve("older-parameters.json").toFile();
        Files.write(snapshot.toPath(), ("{\"programVersion\":\"older version\",\"analysisParameters\":" +
            "{\"lowThreshold\":41,\"highThreshold\":240,\"vesselThickness\":[5,12]}}")
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        SwingUtilities.invokeAndWait(() -> main.cbAutoUpdate.doClick());
        loadSnapshot(snapshot, false);
        SwingUtilities.invokeAndWait(() -> {
            check(!main.cbAutoUpdate.isSelected(), "Import keeps manual update selected");
            check(main.buildAnalyzerParamsFromUi().thresholdLow == 41, "Import fills controls");
            check(image.imageUi.currentParams.thresholdLow == 15 && second.imageUi.currentParams.thresholdLow == 15,
                "Import leaves both existing analyses unchanged");
            image.textSaveImage.setText(home.resolve("pending-export.png").toString());
            image.saveResultImage();
        });
        AnalyzerParameters exported = AnalyzerParameterImporter.load(home.resolve("pending-export overlay-data.json").toFile()).parameters;
        check(exported.thresholdLow == 15, "Export records applied parameters rather than pending controls");
        SwingUtilities.invokeAndWait(() -> main.btnApplyParameters.doClick());
        awaitAnalysis(image);
        awaitAnalysis(second);
        SwingUtilities.invokeAndWait(() -> {
            check(image.imageUi.currentParams.thresholdLow == 41 && second.imageUi.currentParams.thresholdLow == 41,
                "Manual apply updates both images");
            main.btnReset.doClick();
            check(main.buildAnalyzerParamsFromUi().equals(AnalyzerParameters.defaults()), "Reset restores defaults without a dialog");
            check(!main.cbAutoUpdate.isSelected() && image.imageUi.currentParams.thresholdLow == 41,
                "Reset in manual mode preserves applied result and switch");
            main.cbAutoUpdate.doClick();
        });
        awaitAnalysis(image);
        awaitAnalysis(second);
        loadSnapshot(snapshot, false);
        SwingUtilities.invokeAndWait(() -> {
            check(main.cbAutoUpdate.isSelected(), "Import keeps automatic update selected");
            check(image.imageUi.currentParams.thresholdLow == 15 && second.imageUi.currentParams.thresholdLow == 15,
                "Import also defers analysis when automatic update is selected");
            check(main.labelStatus.getToolTipText().contains("older version"), "Source version in tooltip");
            main.btnApplyParameters.doClick();
        });
        awaitAnalysis(image);
        awaitAnalysis(second);
        File invalid = home.resolve("invalid-parameters.json").toFile();
        Files.write(invalid.toPath(), "{\"uiParameters\":null}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        loadSnapshot(invalid, true);
        SwingUtilities.invokeAndWait(() -> {
            check(main.buildAnalyzerParamsFromUi().thresholdLow == 41, "Failed import leaves controls unchanged");
            check(image.imageUi.currentParams.thresholdLow == 41 && main.cbAutoUpdate.isSelected(),
                "Failed import leaves analysis and automatic update unchanged");
            main.btnReset.doClick();
        });
        awaitAnalysis(image);
        awaitAnalysis(second);
        SwingUtilities.invokeAndWait(() -> check(image.imageUi.currentParams.equals(AnalyzerParameters.defaults()) &&
            second.imageUi.currentParams.equals(AnalyzerParameters.defaults()), "Automatic reset applies to both images"));
    }

    static void loadSnapshot(File file, boolean expectedError) throws Exception
    {
        final boolean[] selected = {false};
        final boolean[] sawError = {false};
        final boolean[] completed = {false};
        SwingUtilities.invokeLater(() -> main.btnLoadParameters.doClick());
        long deadline = System.currentTimeMillis() + 30000;
        while (!completed[0] && System.currentTimeMillis() < deadline) {
            SwingUtilities.invokeAndWait(() -> {
                for (Window window : Window.getWindows()) {
                    if (!(window instanceof JDialog) || !window.isShowing()) continue;
                    JDialog dialog = (JDialog)window;
                    JFileChooser chooser = findChooser(dialog);
                    if (chooser != null && !selected[0]) {
                        selected[0] = true;
                        chooser.setSelectedFile(file);
                        chooser.approveSelection();
                    }
                    else if (chooser == null) {
                        sawError[0] = "Could not load parameters".equals(dialog.getTitle());
                        dialog.dispose();
                        check(expectedError && sawError[0], "Only failed imports show a message dialog");
                    }
                }
                completed[0] = selected[0] && !main.loadingParameters;
            });
            if (!completed[0]) Thread.sleep(50);
        }
        check(completed[0], "Parameter import timed out");
        check(sawError[0] == expectedError, "Import error dialog expectation");
        SwingUtilities.invokeAndWait(() -> check(main.btnLoadParameters.isEnabled() && main.btnApplyParameters.isEnabled()
            && main.btnReset.isEnabled() && main.cbAutoUpdate.isEnabled(), "Import restores editing controls"));
    }

    static JFileChooser findChooser(Container parent)
    {
        if (parent instanceof JFileChooser) return (JFileChooser)parent;
        for (Component child : parent.getComponents()) {
            if (child instanceof Container) {
                JFileChooser chooser = findChooser((Container)child);
                if (chooser != null) return chooser;
            }
        }
        return null;
    }

    static void testBinaryWorkspace(Path home) throws Exception
    {
        BufferedImage mask = new BufferedImage(96, 96, BufferedImage.TYPE_INT_RGB);
        for (int y = 12; y < 84; y++)
            for (int x = 12; x < 84; x++)
                mask.setRGB(x, y, 0xffffff);
        mask.setRGB(2, 2, 0xffffff);
        mask.setRGB(25, 25, 0);
        File input = home.resolve("solid-mask.png").toFile();
        ImageIO.write(mask, "png", input);
        SwingUtilities.invokeAndWait(() -> {
            AnalyzerParameters p = AnalyzerParameters.defaults();
            p.shouldResizeImage = true;
            p.resizingFactor = 0.5;
            p.shouldCapSkelIterations = false;
            main.setAnalysisUi(p);
            try {
                image = new ImagingWindow(main, ImageFile.acquireImageForAnalysis(input.getAbsolutePath(), 0.5),
                    0.5, input, home.toString()).showInWorkspace();
            }
            catch (Exception ex) { throw new RuntimeException(ex); }
        });
        awaitAnalysis();
        SwingUtilities.invokeAndWait(() -> {
            check(image.imageUi.source.width == 48, "Detection mode resize");
            main.cbUseBinaryMask.doClick();
        });
        awaitAnalysis();
        SwingUtilities.invokeAndWait(() -> {
            check(image.imageUi.currentStats != null, "Binary analysis finishes in workspace");
            check(image.imageUi.source.width == 96, "Mode switch reloads original resolution");
            check(image.analyzerData.vesselPixelArea == 72 * 72, "Workspace preserves solid area");
            snapshot(main, "binary-mask.png");
            image.viewMask.doClick();
            main.elemBinaryFillHoles.cb.doClick();
        });
        awaitAnalysis();
        SwingUtilities.invokeAndWait(() -> {
            check(image.analyzerData.vesselPixelArea == 72 * 72 + 1, "Binary fill reanalyzes");
            check((image.imageUi.getDisplayedImage().getRGB(25, 25) & 0xffffff) == 0xffffff,
                "Selected mask preview refreshes after cleanup");
            main.elemBinaryRemoveParticles.spinner.setValue(1.0);
            main.elemBinaryRemoveParticles.cb.doClick();
        });
        awaitAnalysis();
        SwingUtilities.invokeAndWait(() -> {
            check(image.analyzerData.vesselPixelArea == 72 * 72, "Binary removal reanalyzes");
            check((image.imageUi.getDisplayedImage().getRGB(2, 2) & 0xffffff) == 0,
                "Mask preview reflects particle removal");
            snapshot(main, "binary-cleanup-mask.png");
            main.cbUseBinaryMask.doClick();
        });
        awaitAnalysis();
        SwingUtilities.invokeAndWait(() -> {
            check(image.imageUi.source.width == 48, "Switch back restores resize");
            main.closeImagingWindow(image);
        });
    }

    static void awaitAnalysis(ImagingWindow target) throws Exception
    {
        long deadline = System.currentTimeMillis() + 60000;
        while (System.currentTimeMillis() < deadline) {
            AtomicReference<Boolean> busy = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> busy.set(target.analysisRunning || target.imageUi.waiting && !target.closed));
            if (!busy.get()) return;
            Thread.sleep(50);
        }
        throw new AssertionError("Analysis timed out");
    }

    static void flush() throws Exception { SwingUtilities.invokeAndWait(() -> {}); }

    static void snapshot(JFrame frame, String name)
    {
        frame.validate();
        BufferedImage bitmap = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = bitmap.createGraphics();
        frame.paintAll(graphics);
        graphics.dispose();
        try { ImageIO.write(bitmap, "png", outputs.resolve(name).toFile()); }
        catch (Exception ex) { throw new RuntimeException(ex); }
    }

    static void checkControlBounds(Component control, String message)
    {
        Component child = control;
        for (Container parent = child.getParent(); parent != null && parent != image; parent = child.getParent()) {
            check(child.getX() >= 0 && child.getY() >= 0 && child.getX() + child.getWidth() <= parent.getWidth()
                && child.getY() + child.getHeight() <= parent.getHeight(), message + " stays within its ancestors");
            child = parent;
        }
    }

    static void check(boolean condition, String message)
    {
        if (!condition) throw new AssertionError(message);
    }

    static AbstractButton findButton(Container parent, String text)
    {
        for (Component child : parent.getComponents()) {
            if (child instanceof AbstractButton && text.equals(((AbstractButton)child).getText()))
                return (AbstractButton)child;
            if (child instanceof Container) {
                AbstractButton found = findButton((Container)child, text);
                if (found != null) return found;
            }
        }
        return null;
    }
}
