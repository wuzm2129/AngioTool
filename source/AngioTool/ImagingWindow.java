package AngioTool;

import Algorithms.PreprocessColor;
import Pixels.ArgbBuffer;
import Pixels.ImageFile;
import Utils.FloatBufferPool;
import Utils.ISliceRunner;
import Utils.Misc;
import Xlsx.*;
import java.awt.Color;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.LinkedBlockingQueue;
import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.border.EmptyBorder;
import com.formdev.flatlaf.icons.FlatFileViewFloppyDriveIcon;
import com.formdev.flatlaf.icons.FlatFileChooserDetailsViewIcon;
import com.formdev.flatlaf.icons.FlatWindowMaximizeIcon;

public class ImagingWindow extends JPanel implements ActionListener, KeyListener
{
    public static class ImagingDisplay extends JPanel implements MouseMotionListener, MouseWheelListener
    {
        public static final double ZOOM_BASE = 1.2;

        boolean waiting = false;
        AnalyzerParameters pendingParams = null;

        ArgbBuffer source;
        float[] luminanceTable;
        int imgWidth;
        int imgHeight;

        BufferedImage drawingImage;
        BufferedImage previewImage;
        byte[] maskSnapshot;
        byte[] rawSkeletonSnapshot;
        int viewMode = 2;
        Color backgroundColor;
        Color[] wheelColors;
        int loadTicks;
        Rectangle areaRect;

        AnalyzerParameters currentParams;
        Analyzer.Stats currentStats;

        int zoomLevels;
        boolean wasDragging;
        int panStartX;
        int panStartY;
        int panX;
        int panY;
        double imageOriginX;
        double imageOriginY;
        boolean actualSize;

        final Timer loadingTimer = new Timer(100, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                if (!waiting) {
                    loadingTimer.stop();
                }
                else {
                    loadTicks++;
                    repaint();
                }
            }
        });

        public ImagingDisplay(ArgbBuffer source)
        {
            this.source = source;
            this.imgWidth = source.width;
            this.imgHeight = source.height;
            this.luminanceTable = new float[255];
            this.drawingImage = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_RGB);
            this.backgroundColor = UiTheme.CANVAS;
            this.wheelColors = new Color[8];
            this.loadTicks = 0;
            this.areaRect = new Rectangle();

            this.currentParams = null;
            this.currentStats = null;

            this.zoomLevels = 0;
            this.wasDragging = false;
            this.panStartX = 0;
            this.panStartY = 0;
            this.panX = 0;
            this.panY = 0;
            this.imageOriginX = 0.5 * imgWidth;
            this.imageOriginY = 0.5 * imgHeight;

            final int dull = 64;
            final int factor = (256 - dull) / (wheelColors.length - 1);
            for (int i = 0; i < wheelColors.length; i++) {
                int lum = dull + (wheelColors.length - i - 1) * factor;
                wheelColors[i] = new Color(lum, lum, lum);
            }

            this.addMouseMotionListener(this);
            this.addMouseWheelListener(this);
        }

        public void setNewImage(ArgbBuffer newImage)
        {
            if (source != null)
                ImageFile.releaseImage(source);

            source = newImage;
            imgWidth = source.width;
            imgHeight = source.height;
            if (drawingImage != null)
                drawingImage.flush();
            drawingImage = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_RGB);
            maskSnapshot = null;
            rawSkeletonSnapshot = null;
            refreshPreview();
            imageOriginX = 0.5 * imgWidth;
            imageOriginY = 0.5 * imgHeight;
        }

        public static double getZoomFactor(int zoomIndex)
        {
            double rounding = (1 << (Math.min(Math.max(-zoomIndex + 2, 2), 16)));
            return Math.floor(Math.pow(ZOOM_BASE, zoomIndex) * rounding + 0.5) / rounding;
        }

        @Override
        public Dimension getPreferredSize()
        {
            return new Dimension(imgWidth, imgHeight);
        }

        @Override
        public void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            areaRect.setBounds(0, 0, getWidth(), getHeight());
            if (drawingImage == null)
                return;

            if (areaRect.width <= 0 || areaRect.height <= 0 || imgWidth <= 0 || imgHeight <= 0) {
                super.paintComponent(g);
                return;
            }

            double fitScale = Math.min((double)getWidth() / imgWidth, (double)getHeight() / imgHeight);
            double scale = actualSize ? 1.0 : fitScale * getZoomFactor(zoomLevels);
            imageOriginX += (panStartX - panX) / scale;
            imageOriginY += (panStartY - panY) / scale;
            panStartX = panX;
            panStartY = panY;
            int x = (int)Math.round(getWidth() * 0.5 - imageOriginX * scale);
            int y = (int)Math.round(getHeight() * 0.5 - imageOriginY * scale);
            g.setColor(backgroundColor);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.drawImage(getDisplayedImage(), x, y, (int)Math.round(imgWidth * scale),
                (int)Math.round(imgHeight * scale), null);

            if (waiting) {
                int centerX = areaRect.width / 2;
                int centerY = areaRect.height / 2;

                for (int i = 0; i < wheelColors.length; i++) {
                    double angle = 2.0 * Math.PI * i / wheelColors.length;
                    int dotX = (int)(20.0 * Math.cos(angle) + 0.5);
                    int dotY = (int)(20.0 * Math.sin(angle) + 0.5);

                    g.setColor(wheelColors[(loadTicks + i) % wheelColors.length]);
                    g.fillOval(centerX + dotX - 6, centerY + dotY - 6, 13, 13);
                }
            }
        }

        @Override
        public void mouseDragged(MouseEvent evt)
        {
            if (waiting)
                return;

            int x = evt.getX();
            int y = evt.getY();
            if (x != this.panX || y != this.panY) {
                if (!wasDragging) {
                    this.panStartX = x;
                    this.panStartY = y;
                }
                this.panX = x;
                this.panY = y;
                this.wasDragging = true;
                this.repaint();
            }
        }

        @Override
        public void mouseMoved(MouseEvent evt)
        {
            this.wasDragging = false;
        }

        @Override
        public void mouseWheelMoved(MouseWheelEvent evt)
        {
            if (waiting)
                return;

            int clicks = evt.getWheelRotation();
            if (clicks != 0)
                zoomBy(-clicks, evt);
        }

        public void zoomBy(int levels, MouseWheelEvent evt)
        {
            if (actualSize) {
                double fit = Math.min((double)getWidth() / imgWidth, (double)getHeight() / imgHeight);
                this.zoomLevels = (int)Math.round(Math.log(1.0 / fit) / Math.log(ZOOM_BASE));
                actualSize = false;
            }
            int prevZoom = this.zoomLevels;
            this.zoomLevels = Math.min(Math.max(this.zoomLevels + levels, -16), 32);

            if (evt != null) {
                int x1 = evt.getX();
                int y1 = evt.getY();
                int halfW = this.areaRect.width / 2;
                int halfH = this.areaRect.height / 2;
                int dx = x1 - halfW;
                int dy = y1 - halfH;
                double dz = getZoomFactor(this.zoomLevels) / getZoomFactor(prevZoom);
                int x2 = halfW + (int)(dx * dz + 0.5);
                int y2 = halfH + (int)(dy * dz + 0.5);

                this.panStartX = x2;
                this.panStartY = y2;
                this.panX = x1;
                this.panY = y1;
            }

            this.repaint();
        }

        public void panBy(int xUnits, int yUnits)
        {
            int size = Math.max(this.areaRect.width, this.areaRect.height) / 8;
            this.panX = this.panStartX - xUnits * size;
            this.panY = this.panStartY - yUnits * size;
            this.repaint();
        }

        public void resetView(boolean oneToOne)
        {
            actualSize = oneToOne;
            zoomLevels = 0;
            panX = panY = panStartX = panStartY = 0;
            imageOriginX = imgWidth * 0.5;
            imageOriginY = imgHeight * 0.5;
            repaint();
        }

        public int[] getDrawingBuffer()
        {
            return ((DataBufferInt)drawingImage.getRaster().getDataBuffer()).getData();
        }

        BufferedImage getDisplayedImage()
        {
            return viewMode == 2 || previewImage == null ? drawingImage : previewImage;
        }

        void setViewMode(int mode)
        {
            viewMode = mode;
            refreshPreview();
            repaint();
        }

        private void refreshPreview()
        {
            if (viewMode == 2 || source == null) {
                if (previewImage != null) previewImage.flush();
                previewImage = null;
                return;
            }
            if (previewImage == null || previewImage.getWidth() != imgWidth ||
                    previewImage.getHeight() != imgHeight) {
                if (previewImage != null) previewImage.flush();
                previewImage = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_RGB);
            }
            int[] pixels = ((DataBufferInt)previewImage.getRaster().getDataBuffer()).getData();
            if (viewMode == 3 && rawSkeletonSnapshot != null) {
                for (int i = 0; i < imgWidth * imgHeight; i++)
                    pixels[i] = rawSkeletonSnapshot[i] == 0 ? 0 : 0xffffff;
            }
            else if (viewMode == 1 && maskSnapshot != null) {
                for (int i = 0; i < imgWidth * imgHeight; i++)
                    pixels[i] = maskSnapshot[i] == 0 ? 0 : 0xffffff;
            }
            else
                System.arraycopy(source.pixels, 0, pixels, 0, imgWidth * imgHeight);
        }

        public void notifyImageProcessing(boolean shouldCopyFromSource)
        {
            this.waiting = true;
            this.currentParams = null;
            this.currentStats = null;
            maskSnapshot = null;
            rawSkeletonSnapshot = null;
            refreshPreview();
            if (shouldCopyFromSource)
                System.arraycopy(source.pixels, 0, getDrawingBuffer(), 0, imgWidth * imgHeight);

            loadingTimer.start();
            this.repaint();
        }

        public AnalyzerParameters onImageFinished(AnalyzerParameters params, Analyzer.Data data, Analyzer.Stats stats)
        {
            this.currentParams = params;
            this.currentStats = stats;

            int[] outPixels = getDrawingBuffer();

            if (stats == null) {
                maskSnapshot = null;
            rawSkeletonSnapshot = null;
                refreshPreview();
                System.arraycopy(source.pixels, 0, outPixels, 0, imgWidth * imgHeight);
                this.waiting = false;
                this.repaint();
                return null;
            }

            float[] preprocessedImage = null;
            if (!params.shouldUseBinaryMask && params.shouldRemapColors && params.shouldExpandOutputToGrayScale) {
                preprocessedImage = FloatBufferPool.acquireAsIs(imgWidth * imgHeight);
                PreprocessColor.transformToMonoFloatArray(
                    preprocessedImage,
                    source.pixels,
                    imgWidth,
                    imgHeight,
                    (float)params.hueTransformWeight,
                    (float)params.brightnessTransformWeight,
                    params.targetRemapColor.getRGB(),
                    params.voidRemapColor.getRGB(),
                    (float)params.saturationFactor,
                    luminanceTable
                );
            }

            Analyzer.drawOverlay(
                params,
                data.convexHull,
                data.skelResult,
                data.analysisImage.buf,
                outPixels,
                source.pixels,
                preprocessedImage,
                imgWidth,
                imgHeight,
                source.brightestChannel
            );

            if (preprocessedImage != null)
                FloatBufferPool.release(preprocessedImage);

            // Keep an immutable display copy while the next analysis reuses its working mask.
            maskSnapshot = java.util.Arrays.copyOf(data.analysisImage.buf, imgWidth * imgHeight);
            rawSkeletonSnapshot = java.util.Arrays.copyOf(data.rawSkeleton, imgWidth * imgHeight);
            refreshPreview();

            if (pendingParams != null) {
                notifyImageProcessing(false);

                AnalyzerParameters temp = pendingParams;
                pendingParams = null;
                return temp;
            }

            this.waiting = false;
            this.repaint();

            return null;
        }
    }

    public static final int MAX_WORKERS = 4;

    public static final ThreadPoolExecutor threadPool = new ThreadPoolExecutor(
        /* corePoolSize */ MAX_WORKERS,
        /* maximumPoolSize */ MAX_WORKERS,
        /* keepAliveTime */ 30,
        /* unit */ TimeUnit.SECONDS,
        /* workQueue */ new LinkedBlockingQueue<>()
    );

    AngioToolGui2 parentFrame;
    File inputFile;
    String defaultPath;
    double prevImageScale;
    boolean everSaved;
    boolean closed;
    boolean analysisRunning;
    private AnalyzerParameters activeAnalysisParams;

    ImagingDisplay imageUi;
    final StatisticsPanel statistics = new StatisticsPanel();
    final JPanel workspace = new JPanel(new BorderLayout());
    final JLabel analysisStatus = new JLabel("Analyzing...");
    final JButton btnFit = new JButton(new FlatWindowMaximizeIcon());
    final JButton btnActualSize = new JButton("1:1");
    final JToggleButton viewOriginal = new JToggleButton("Original");
    final JToggleButton viewMask = new JToggleButton("Final mask");
    final JToggleButton viewRawSkeleton = new JToggleButton("Raw skeleton");
    final JToggleButton viewOverlay = new JToggleButton("Skeleton overlay", true);

    JLabel labelZoom = new JLabel();
    JButton btnZoomIn = new JButton();
    JButton btnZoomOut = new JButton();
    JLabel labelPan = new JLabel();
    JButton btnPanLeft = new JButton();
    JButton btnPanUp = new JButton();
    JButton btnPanRight = new JButton();
    JButton btnPanDown = new JButton();
    JCheckBox cbShowStats = new JCheckBox();

    JLabel labelSaveImage = new JLabel();
    JLabel labelImageWasSaved = new JLabel();
    JButton btnSaveImage = new JButton();
    JTextField textSaveImage = new JTextField();

    JLabel labelSaveSpreadsheet = new JLabel();
    JLabel labelSpreadsheetWasSaved = new JLabel();
    JButton btnSaveSpreadsheet = new JButton();
    JTextField textSaveSpreadsheet = new JTextField();

    final Analyzer.Data analyzerData = new Analyzer.Data(null, 0);
    final ISliceRunner sliceRunner = new ISliceRunner.Parallel(Analyzer.threadPool);

    public ImagingWindow(AngioToolGui2 uiFrame, ArgbBuffer image, double imageScale, File sourceFile, String defaultPath)
    {
        super(new BorderLayout());
        this.parentFrame = uiFrame;
        this.imageUi = new ImagingDisplay(image);
        this.prevImageScale = imageScale;
        this.inputFile = sourceFile;
        this.defaultPath = defaultPath;
        this.everSaved = false;
        ButtonGroup views = new ButtonGroup();
        JToggleButton[] viewButtons = {viewOriginal, viewMask, viewOverlay, viewRawSkeleton};
        for (int i = 0; i < viewButtons.length; i++) {
            final int mode = i;
            JToggleButton button = viewButtons[i];
            button.setPreferredSize(UiTheme.size(130, 30));
            button.addActionListener(e -> imageUi.setViewMode(mode));
            views.add(button);
        }
        viewOriginal.setToolTipText("Input pixels at the current analysis resolution");
        viewRawSkeleton.setToolTipText("Skeleton before terminal branch pruning, at one pixel width");
        viewMask.setToolTipText("Final binary foreground used for measurements");
        viewOverlay.setToolTipText("Analysis overlay; image exports always use this view");

        this.labelZoom.setText("Zoom:");
        this.btnZoomIn.setIcon(AngioTool.ATPlus);
        this.btnZoomOut.setIcon(AngioTool.ATMinus);

        this.labelPan.setText("Move:");
        this.btnPanLeft.setIcon(AngioTool.ATLeft);
        this.btnPanUp.setIcon(AngioTool.ATUp);
        this.btnPanDown.setIcon(AngioTool.ATDown);
        this.btnPanRight.setIcon(AngioTool.ATRight);

        this.cbShowStats.setText("Measurements");
        this.cbShowStats.setSelected(true);

        this.labelSaveImage.setText("Save result image");
        Misc.setNewFontStyleOn(this.labelImageWasSaved, Font.ITALIC);
        this.labelImageWasSaved.setHorizontalAlignment(SwingConstants.TRAILING);
        this.btnSaveImage.setIcon(new FlatFileViewFloppyDriveIcon());
        this.btnSaveImage.setText("Save image");
        this.textSaveImage.addKeyListener(this);

        this.labelSaveSpreadsheet.setText("Save stats to spreadsheet");
        Misc.setNewFontStyleOn(this.labelSpreadsheetWasSaved, Font.ITALIC);
        this.labelSpreadsheetWasSaved.setHorizontalAlignment(SwingConstants.TRAILING);
        this.btnSaveSpreadsheet.setIcon(new FlatFileChooserDetailsViewIcon());
        this.btnSaveSpreadsheet.setText("Save XLSX");
        this.textSaveSpreadsheet.addKeyListener(this);
    }

    public ImagingWindow showInWorkspace()
    {
        JPanel dialogPanel = buildContent();
        for (AbstractButton button : new AbstractButton[] {btnZoomIn, btnZoomOut,
                btnPanLeft, btnPanRight, btnPanUp, btnPanDown, cbShowStats,
                btnSaveImage, btnSaveSpreadsheet, btnFit, btnActualSize})
            button.addActionListener(this);

        add(dialogPanel, BorderLayout.CENTER);
        parentFrame.attachImage(this);
        imageUi.notifyImageProcessing(true);
        setUiInteractivity(false);
        dispatchAnalysisTask(parentFrame.buildAnalyzerParamsFromUi());

        return this;
    }

    void initializeDivider()
    {
        parentFrame.initializeMeasurementsDivider();
    }

    public Raster getImageRaster()
    {
        return imageUi.drawingImage.getRaster();
    }

    public void release()
    {
        ImageFile.releaseImage(imageUi.source);
        imageUi.source = null;
        analyzerData.nullify();
        imageUi.maskSnapshot = null;
        imageUi.rawSkeletonSnapshot = null;
        if (imageUi.previewImage != null) {
            imageUi.previewImage.flush();
            imageUi.previewImage = null;
        }

        if (imageUi.drawingImage != null) {
            imageUi.drawingImage.flush();
            imageUi.drawingImage = null;
        }
    }

    public void closeSafely()
    {
        closed = true;
        imageUi.loadingTimer.stop();
        imageUi.pendingParams = null;
        if (!analysisRunning)
            release();
        setVisible(false);
    }

    boolean needsParameterUpdate(AnalyzerParameters params)
    {
        if (closed)
            return false;
        if (imageUi.waiting)
            return !params.equals(imageUi.pendingParams != null ? imageUi.pendingParams : activeAnalysisParams);
        return imageUi.currentStats == null || !params.equals(imageUi.currentParams);
    }

    public void updateImage(AnalyzerParameters params)
    {
        if (closed)
            return;
        if (imageUi.waiting) {
            imageUi.pendingParams = params.equals(activeAnalysisParams) ? null : params;
            return;
        }

        if (imageUi.currentStats != null &&
                params.hasSameAnalysisSettings(imageUi.currentParams)) {
            imageUi.onImageFinished(params, analyzerData, imageUi.currentStats);
            statistics.showResults(params, imageUi.currentStats);
            labelImageWasSaved.setText("");
            labelSpreadsheetWasSaved.setText("");
            return;
        }
        setUiInteractivity(false);

        double newResizeFactor = params.effectiveResizeFactor();
        if (newResizeFactor == prevImageScale)
            imageUi.notifyImageProcessing(false);

        dispatchAnalysisTask(params);
    }

    private JPanel buildContent()
    {
        JPanel root = new JPanel(new BorderLayout());
        JPanel tools = new JPanel(new BorderLayout(12, 0));
        tools.setBorder(new EmptyBorder(10, 12, 10, 12));
        UiTheme.tool(btnZoomIn, "Zoom in");
        UiTheme.tool(btnZoomOut, "Zoom out");
        UiTheme.tool(btnFit, "Fit image to window");
        UiTheme.tool(btnActualSize, "Actual size");
        btnActualSize.setPreferredSize(UiTheme.size(48, 32));
        UiTheme.tool(btnPanLeft, "Pan left");
        UiTheme.tool(btnPanRight, "Pan right");
        UiTheme.tool(btnPanUp, "Pan up");
        UiTheme.tool(btnPanDown, "Pan down");
        tools.add(UiTheme.row(btnZoomOut, btnZoomIn, btnFit, btnActualSize,
            btnPanLeft, btnPanUp, btnPanDown, btnPanRight), BorderLayout.WEST);
        tools.add(cbShowStats, BorderLayout.EAST);
        JPanel imageHeader = new JPanel(new BorderLayout());
        imageHeader.add(tools, BorderLayout.NORTH);
        JPanel views = UiTheme.row(viewOriginal, viewMask, viewOverlay, viewRawSkeleton);
        views.setBorder(new EmptyBorder(0, 12, 8, 12));
        imageHeader.add(views, BorderLayout.SOUTH);
        root.add(imageHeader, BorderLayout.NORTH);
        imageUi.setMinimumSize(new Dimension(200, 160));
        workspace.add(imageUi, BorderLayout.CENTER);
        workspace.setBorder(BorderFactory.createEmptyBorder());
        root.add(workspace, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(0, 10));
        footer.setBorder(new EmptyBorder(10, 16, 14, 16));
        analysisStatus.setForeground(UiTheme.MUTED);
        footer.add(analysisStatus, BorderLayout.NORTH);
        JPanel exports = new JPanel(new GridLayout(1, 2, 16, 0));
        exports.add(exportField(textSaveImage, btnSaveImage, labelImageWasSaved));
        exports.add(exportField(textSaveSpreadsheet, btnSaveSpreadsheet, labelSpreadsheetWasSaved));
        textSaveImage.setToolTipText("Result image path; leave empty for the results folder");
        textSaveImage.putClientProperty("JTextField.placeholderText", "Default results folder");
        textSaveSpreadsheet.setToolTipText("XLSX path; leave empty to choose a workbook");
        textSaveSpreadsheet.putClientProperty("JTextField.placeholderText", "Choose a workbook...");
        footer.add(exports, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);
        return root;
    }

    private JPanel exportField(JTextField path, JButton button, JLabel status)
    {
        JPanel panel = new JPanel(new BorderLayout(8, 4));
        panel.add(path, BorderLayout.CENTER);
        panel.add(button, BorderLayout.EAST);
        status.setPreferredSize(new Dimension(100, 18));
        panel.add(status, BorderLayout.SOUTH);
        return panel;
    }

    void dispatchAnalysisTask(AnalyzerParameters params)
    {
        if (closed)
            return;
        activeAnalysisParams = params;
        statistics.showStatus("Analyzing...", false);
        analysisStatus.setText("Analyzing...");
        analysisStatus.setForeground(UiTheme.MUTED);
        double newResizeFactor = params.effectiveResizeFactor();
        boolean needsReload = newResizeFactor != prevImageScale;

        if (needsReload) {
            ArgbBuffer newImage = null;
            try {
                if (inputFile == null) {
                    ArgbBuffer temp = parentFrame.pluginCtx.makeArgbCopy();
                    newImage = ImageFile.acquireImageForAnalysisFromBuffer(temp, newResizeFactor);
                }
                else {
                    newImage = ImageFile.acquireImageForAnalysis(inputFile.getAbsolutePath(), newResizeFactor);
                }

                if (newImage == null)
                    throw new IOException("Failed to load image for analysis");

                imageUi.setNewImage(newImage);
            }
            catch (Exception ex) {
                if (newImage != null)
                    ImageFile.releaseImage(newImage);

                Misc.showExceptionInDialogBox(ex);
                imageUi.waiting = false;
                imageUi.pendingParams = null;
                imageUi.currentStats = null;
                imageUi.loadingTimer.stop();
                statistics.showStatus("Image could not be loaded", true);
                analysisStatus.setText("Image could not be loaded");
                setUiInteractivity(true);
                btnSaveImage.setEnabled(false);
                btnSaveSpreadsheet.setEnabled(false);
                return;
            }
            imageUi.notifyImageProcessing(true);
        }

        prevImageScale = newResizeFactor;

        if (!params.shouldUseBinaryMask && params.shouldRemapColors)
            PreprocessColor.computeBrightnessTable(
                imageUi.luminanceTable,
                params.brightnessLineSegments,
                params.brightnessLineSegments.length / 2
            );

        analysisRunning = true;
        ImagingWindow.threadPool.submit(() -> {
            try {
                analyzerData.restart();
                if (!params.shouldUseBinaryMask && params.shouldRemapColors)
                    analyzerData.updateBrightnessTable(params.brightnessLineSegments, params.brightnessLineSegments.length / 2);

                Analyzer.Stats stats = Analyzer.analyze(
                    analyzerData,
                    inputFile,
                    imageUi.source,
                    params,
                    sliceRunner
                );

                SwingUtilities.invokeLater(() -> {
                    analysisRunning = false;
                    if (closed) {
                        release();
                        return;
                    }
                    AnalyzerParameters nextParams = imageUi.onImageFinished(params, analyzerData, stats);
                    if (nextParams != null) {
                        dispatchAnalysisTask(nextParams);
                    }
                    else {
                        statistics.showResults(params, stats);
                        analysisStatus.setText(stats.valid ? "Analysis complete" :
                            "Invalid analysis: " + stats.invalidReason);
                        analysisStatus.setForeground(stats.valid ? UiTheme.MUTED : UiTheme.ERROR);
                        setUiInteractivity(true);
                    }
                });
            }
            catch (Throwable t) {
                final Throwable error = t;
                SwingUtilities.invokeLater(() -> {
                    analysisRunning = false;
                    if (closed) {
                        release();
                        return;
                    }
                    imageUi.onImageFinished(params, analyzerData, null);
                    AnalyzerParameters pending = imageUi.pendingParams;
                    imageUi.pendingParams = null;
                    if (pending != null) {
                        imageUi.notifyImageProcessing(true);
                        dispatchAnalysisTask(pending);
                        return;
                    }
                    statistics.showStatus("Analysis failed", true);
                    analysisStatus.setText("Analysis failed");
                    analysisStatus.setForeground(UiTheme.ERROR);
                    setUiInteractivity(true);
                    btnSaveImage.setEnabled(false);
                    btnSaveSpreadsheet.setEnabled(false);
                    Misc.showExceptionInDialogBox(error);
                });
            }
        });
    }

    void setUiInteractivity(boolean enabled)
    {
        if (enabled && imageUi.currentStats == null) {
            viewOriginal.setSelected(true);
            imageUi.setViewMode(0);
        }
        viewOriginal.setEnabled(enabled);
        viewMask.setEnabled(enabled && imageUi.currentStats != null);
        viewOverlay.setEnabled(enabled && imageUi.currentStats != null);
        viewRawSkeleton.setEnabled(enabled && imageUi.currentStats != null);
        btnZoomIn.setEnabled(enabled);
        btnZoomOut.setEnabled(enabled);
        btnFit.setEnabled(enabled);
        btnActualSize.setEnabled(enabled);
        btnPanLeft.setEnabled(enabled);
        btnPanUp.setEnabled(enabled);
        btnPanRight.setEnabled(enabled);
        btnPanDown.setEnabled(enabled);
        cbShowStats.setEnabled(enabled);
        btnSaveImage.setEnabled(enabled);
        textSaveImage.setEnabled(enabled);
        btnSaveSpreadsheet.setEnabled(enabled);
        textSaveSpreadsheet.setEnabled(enabled);

        labelImageWasSaved.setText("");
        labelSpreadsheetWasSaved.setText("");
    }

    void saveResultImage()
    {
        String filePath = Misc.stripWrappingQuotes(textSaveImage.getText());
        int extIdx = -1;
        while (true) {
            if (filePath == null || filePath.length() == 0) {
                File defaultResultFile = getDefaultResultImageFile(inputFile);
                if (defaultResultFile != null) {
                    try {
                        ensureResultFolderExists(defaultResultFile);
                    }
                    catch (Throwable ex) {
                        Misc.showExceptionInDialogBox(ex);
                        return;
                    }
                    filePath = defaultResultFile.getAbsolutePath();
                }
                else {
                    JFileChooser fc = Misc.createFileChooser();
                    fc.setDialogTitle("Save Result Image");
                    fc.setDialogType(JFileChooser.SAVE_DIALOG);
                    fc.setCurrentDirectory(new File(defaultPath));
                    Misc.addImageFileFilters(fc);

                    if (fc.showSaveDialog(this) != 0)
                        return;

                    filePath = fc.getSelectedFile().getAbsolutePath();

                    FileFilter filter = fc.getFileFilter();
                    if (filter instanceof SimpleFileFilter)
                        filePath = ((SimpleFileFilter)filter).tailorFileName(filePath);
                }
            }

            extIdx = filePath.lastIndexOf('.');
            if (extIdx <= 0 || extIdx < Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\')))
                Misc.showDialogBox("Save Result Image", "No image format was specified.\nPlease select an image format, eg. jpg");
            else
                break;

            filePath = null;
        };

        String format = filePath.substring(extIdx + 1).toLowerCase();

        try {
            DataBufferInt imageBuffer = (DataBufferInt)getImageRaster().getDataBuffer();
            ImageFile.saveImage(imageUi.drawingImage, imageBuffer, format, filePath);
            if (imageUi.currentParams != null && imageUi.currentStats != null) {
                OverlayDataExporter.export(
                    filePath.substring(0, extIdx),
                    imageUi.currentParams,
                    analyzerData,
                    imageUi.currentStats,
                    imageUi.imgWidth,
                    imageUi.imgHeight,
                    imageUi.source.brightestChannel
                );
            }
            textSaveImage.setText(filePath);
            labelImageWasSaved.setText("saved");
        }
        catch (Throwable ex) {
            Misc.showExceptionInDialogBox(ex);
        }
    }

    static File getDefaultResultImageFile(File sourceFile)
    {
        if (sourceFile == null)
            return null;

        File sourceFolder = sourceFile.getParentFile();
        if (sourceFolder == null)
            return null;

        String outputName = sourceFile.getName();
        int extensionOffset = outputName.lastIndexOf('.');
        if (extensionOffset <= 0 || extensionOffset == outputName.length() - 1)
            outputName += ".png";

        return new File(new File(sourceFolder, "results"), "ana_" + outputName);
    }

    static void ensureResultFolderExists(File resultFile) throws IOException
    {
        File resultsFolder = resultFile != null ? resultFile.getParentFile() : null;
        if (resultsFolder == null)
            throw new IOException("Result folder could not be determined");
        if (resultsFolder.isDirectory())
            return;
        if (resultsFolder.exists())
            throw new IOException("Result path is not a folder: " + resultsFolder.getAbsolutePath());
        if (!resultsFolder.mkdirs() && !resultsFolder.isDirectory())
            throw new IOException("Could not create result folder: " + resultsFolder.getAbsolutePath());
    }

    void saveResultSpreadsheet()
    {
        if (imageUi.currentParams == null || imageUi.currentStats == null)
            return;

        String existingXlsxFile = textSaveSpreadsheet.getText();
        String[] outStrings = new String[2];

        ArrayList<XlsxReader.SheetCells> sheets = Misc.openSpreadsheetForAppending(
            outStrings,
            existingXlsxFile,
            defaultPath,
            parentFrame
        );
        if (sheets == null)
            return;

        defaultPath = outStrings[1];

        int fileNameOffset = Misc.getFileNameOffset(outStrings[0]);
        File folder = new File(outStrings[0].substring(0, fileNameOffset));
        String sheetName = outStrings[0].substring(fileNameOffset);

        try {
            SpreadsheetWriter sw;
            if (everSaved) {
                sw = new SpreadsheetWriter(folder, sheetName);
                sw.addSheets(sheets);
                sw.currentSheetIdx = Math.max(sw.currentSheetIdx - 1, 0);
            }
            else {
                sw = Analyzer.createWriterWithNewSheet(sheets, folder, sheetName);
            }

            Analyzer.writeResultToSheet(sw, imageUi.currentParams, imageUi.currentStats);
            everSaved = true;

            textSaveSpreadsheet.setText(outStrings[0]);
            labelSpreadsheetWasSaved.setText("saved");
        }
        catch (IOException ex) {
            Misc.showExceptionInDialogBox(ex);
        }
    }

    @Override
    public void actionPerformed(ActionEvent evt)
    {
        Object source = evt.getSource();
        if (source == btnZoomIn)
            imageUi.zoomBy(2, null);
        else if (source == btnZoomOut)
            imageUi.zoomBy(-2, null);
        else if (source == btnPanLeft)
            imageUi.panBy(-1, 0);
        else if (source == btnPanUp)
            imageUi.panBy(0, -1);
        else if (source == btnPanDown)
            imageUi.panBy(0, 1);
        else if (source == btnPanRight)
            imageUi.panBy(1, 0);
        else if (source == cbShowStats)
        {
            parentFrame.showSelectedMeasurements();
        }
        else if (source == btnFit)
            imageUi.resetView(false);
        else if (source == btnActualSize)
            imageUi.resetView(true);
        else if (source == btnSaveImage)
            saveResultImage();
        else if (source == btnSaveSpreadsheet)
            saveResultSpreadsheet();
    }

    @Override public void keyPressed(KeyEvent evt) {}
    @Override public void keyReleased(KeyEvent evt) {}

    @Override
    public void keyTyped(KeyEvent evt)
    {
        Object source = evt.getSource();
        if (source == textSaveImage)
            labelImageWasSaved.setText("");
        else if (source == textSaveSpreadsheet)
            labelSpreadsheetWasSaved.setText("");
    }
}
