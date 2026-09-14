package AngioTool;

import IJPlugin.ATPlugin;
import Utils.*;
import Pixels.*;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.event.*;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.io.File;
import java.io.InputStream;
import java.util.LinkedList;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.GridBagLayout;
import com.formdev.flatlaf.icons.FlatFileViewDirectoryIcon;
import com.formdev.flatlaf.icons.FlatFileChooserDetailsViewIcon;
import com.formdev.flatlaf.icons.FlatHelpButtonIcon;
import com.formdev.flatlaf.icons.FlatTabbedPaneCloseIcon;

public class AngioToolGui2 extends JFrame implements ColorElement.Listener, ActionListener, FocusListener, KeyListener
{
    final ATPlugin.PlugContext pluginCtx;

    final JButton btnLoadImageOrSubmit = new JButton();
    final JButton btnStartBatch = new JButton();
    final JButton btnHelp = new JButton();

    final JLabel labelAnalysis = new JLabel();
    final JCheckBox cbComputeLacunarity = new JCheckBox();
    final JCheckBox cbComputeThickness = new JCheckBox();
    final JCheckBox cbNormalizeOutputData = new JCheckBox();
    final JCheckBox cbUseBinaryMask = new JCheckBox("Use binary mask directly");
    final JLabel labelSkeletonizer = new JLabel();
    final ButtonGroup groupSkeletonizer = new ButtonGroup();
    final JRadioButton rbSkelFast = new JRadioButton();
    final JRadioButton rbSkelThorough = new JRadioButton();
    final NumberEntry elemMaxSkelIterations;
    final NumberEntry elemPruneSkeleton;
    final NumberEntry elemMergeJunctions;
    final NumberEntry elemResizeInputs;
    final NumberEntry elemLinearScaleFactor;
    final NumberEntry elemRemoveParticles;
    final NumberEntry elemFillHoles;
    final NumberEntry elemBinaryFillHoles;
    final NumberEntry elemBinaryRemoveParticles;
    final JPanel cleanupInputs = new JPanel(new CardLayout());
    final NumberEntry elemMaxHoleLevelPercent;
    final NumberEntry elemMinBoxnessPercent;
    final NumberEntry elemMinAreaLengthRatio;

    final JCheckBox cbTransformColors = new JCheckBox();
    final JLabel labelSaturationFactor = new JLabel();
    final JLabel labelVoidColor = new JLabel();
    final JLabel labelTargetColor = new JLabel();
    final JLabel labelHueBrightness = new JLabel();
    final JLabel labelBrightnessGraph = new JLabel();
    final JLabel labelSatPercent = new JLabel();
    final JLabel labelHbRatio = new JLabel();
    final JTextField textSaturationFactor = new JTextField();
    final ColorElement elemVoidColor;
    final ColorElement elemTargetColor;
    final JTextField textHueWeight = new JTextField();
    final JTextField textBrightnessWeight = new JTextField();
    final JTextField textBrightnessSegments = new JTextField();

    final JLabel labelSigmas = new JLabel();
    final JTextField textSigmas = new JTextField();
    final JLabel labelIntensity = new JLabel();
    final JTextField textMinIntensity = new JTextField();
    final JTextField textMaxIntensity = new JTextField();

    final JLabel labelOverlay = new JLabel();
    final SizeColorEntry elemOutline;
    final SizeColorEntry elemBranches;
    final SizeColorEntry elemSkeleton;
    final SizeColorEntry elemConvexHull;
    final ButtonGroup groupImageRecolor = new ButtonGroup();
    final JRadioButton rbImageOriginal = new JRadioButton();
    final JRadioButton rbImageIsolated = new JRadioButton();
    final JRadioButton rbImageGray = new JRadioButton();
    final JButton btnReset = new JButton();
    final JLabel labelMemory = new JLabel();

    AnalyzerParameters latestAnalyzerParams = null;
    String defaultPath;

    public final LinkedList<ImagingWindow> imagingWindows = new LinkedList<>();
    byte[] helpHtmlData;
    final JLabel labelStatus = new JLabel("Ready");
    final JTabbedPane parameterTabs = new JTabbedPane();
    final JTabbedPane imageTabs = new JTabbedPane();
    final CardLayout imageCards = new CardLayout();
    final JPanel imageArea = new JPanel(imageCards);
    final JSplitPane mainWorkspace = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    boolean updatingUi;

    public AngioToolGui2(Object source, AnalyzerParameters analyzerParams, String defaultPath)
    {
        super(AngioTool.TITLE + (source != null ? " - ImageJ Plugin" : ""));
        this.pluginCtx = (ATPlugin.PlugContext)source;
        boolean isPlugin = source != null;
        this.defaultPath = defaultPath;

        this.setIconImage(AngioTool.ATIcon.getImage());

        if (isPlugin) {
            initButton(btnLoadImageOrSubmit, AngioTool.ATSubmit, "Submit");
        }
        else {
            initButton(btnLoadImageOrSubmit, AngioTool.ATFolder, "View");
            initButton(btnStartBatch, AngioTool.ATBatch, "Batch");
        }

        initButton(btnHelp, AngioTool.ATHelp, "Help");

        labelAnalysis.setText("Analysis");
        Misc.setNewFontSizeOn(labelAnalysis, 20);

        labelSkeletonizer.setText("Skeletonizer:");

        rbSkelFast.setText("Fast (Zha84)");
        rbSkelFast.setSelected(analyzerParams.shouldUseFastSkeletonizer);

        rbSkelThorough.setText("Thorough (Lee94)");
        rbSkelThorough.setSelected(!analyzerParams.shouldUseFastSkeletonizer);

        groupSkeletonizer.add(rbSkelFast);
        groupSkeletonizer.add(rbSkelThorough);

        cbComputeLacunarity.setText("Lacunarity");
        cbComputeLacunarity.setSelected(analyzerParams.shouldComputeLacunarity);

        cbComputeThickness.setText("Thickness");
        cbComputeThickness.setSelected(analyzerParams.shouldComputeThickness);

        cbNormalizeOutputData.setText("Export normalized data");
        cbNormalizeOutputData.setSelected(analyzerParams.shouldNormalizeOutputData);
        cbNormalizeOutputData.setToolTipText("Also export image-relative metrics using image area and its square root as normalization bases.");

        elemMaxSkelIterations = new NumberEntry("Max Skeleton Steps:", analyzerParams.shouldCapSkelIterations, analyzerParams.maxSkelIterations, "");
        elemPruneSkeleton = new NumberEntry("Prune terminal branches:", analyzerParams.shouldPruneSkeleton, analyzerParams.maxTerminalBranchLength, "px");
        elemPruneSkeleton.cb.setToolTipText("Remove endpoint-to-junction branches at or below this path length; preserve border endpoints and loops.");
        elemMergeJunctions = new NumberEntry("Merge nearby junctions:", analyzerParams.shouldMergeNearbyJunctions, analyzerParams.maxJunctionMergeDistance, "px");
        elemMergeJunctions.cb.setToolTipText("Merge junction regions only when directly connected by a skeleton segment at or below this path length.");
        elemMaxSkelIterations.cb.setToolTipText("Uncheck to thin until convergence. Pruning is skipped if the step limit is reached without convergence.");
        elemResizeInputs = new NumberEntry("Resize Inputs to:", analyzerParams.shouldResizeImage, 100.0 * analyzerParams.resizingFactor, "%");
        elemLinearScaleFactor = new NumberEntry("Measurement Scale:", analyzerParams.shouldApplyLinearScale, 100.0 * analyzerParams.linearScalingFactor, "%");
        elemRemoveParticles = new NumberEntry("Remove Particles:", analyzerParams.shouldRemoveSmallParticles, analyzerParams.removeSmallParticlesThreshold, "px");
        elemFillHoles = new NumberEntry("Fill Holes:", analyzerParams.shouldFillHoles, analyzerParams.fillHolesValue, "px");
        elemBinaryFillHoles = new NumberEntry("Fill Holes:", analyzerParams.binaryFillHoles, analyzerParams.binaryHoleRadius, "px");
        elemBinaryRemoveParticles = new NumberEntry("Remove Particles:", analyzerParams.binaryRemoveSmallParticles, analyzerParams.binarySmallParticleRadius, "px");
        elemBinaryFillHoles.spinner.setToolTipText("Enclosed holes only; maximum area = pi x radius squared");
        elemBinaryRemoveParticles.spinner.setToolTipText("Maximum connected foreground area = pi x radius squared");
        elemMaxHoleLevelPercent = new NumberEntry("Max Hole Level:", analyzerParams.shouldFillBrightShapes, 100.0 * analyzerParams.brightShapeThresholdFactor, "%");
        elemMinBoxnessPercent = new NumberEntry("Min Boxness:", analyzerParams.shouldApplyMinBoxness, 100.0 * analyzerParams.minBoxness, "%");
        elemMinAreaLengthRatio = new NumberEntry("Min Length : Area:", analyzerParams.shouldApplyMinAreaLength, analyzerParams.minAreaLengthRatio, "1 :");

        labelSigmas.setText("Vessel Diameters list");

        textSigmas.setText(Misc.formatDoubleArray(analyzerParams.sigmas, "12"));
        textSigmas.setToolTipText("List of Sigmas (numbers)");

        labelIntensity.setText("Vessel Intensity range");

        textMinIntensity.setText("" + analyzerParams.thresholdLow);
        textMaxIntensity.setText("" + analyzerParams.thresholdHigh);

        labelOverlay.setText("Overlay");
        Misc.setNewFontSizeOn(labelOverlay, 20);

        elemOutline = new SizeColorEntry("Outline:", analyzerParams.shouldDrawOutline, analyzerParams.outlineSize, analyzerParams.outlineColor);
        elemBranches = new SizeColorEntry("Branches:", analyzerParams.shouldDrawBranchPoints, analyzerParams.branchingPointsSize, analyzerParams.branchingPointsColor);
        elemSkeleton = new SizeColorEntry("Skeleton:", analyzerParams.shouldDrawSkeleton, analyzerParams.skeletonSize, analyzerParams.skeletonColor);
        elemConvexHull = new SizeColorEntry("Convex Hull:", analyzerParams.shouldDrawConvexHull, analyzerParams.convexHullSize, analyzerParams.convexHullColor);

        elemOutline.setColorChangeListener(this);
        elemBranches.setColorChangeListener(this);
        elemSkeleton.setColorChangeListener(this);
        elemConvexHull.setColorChangeListener(this);

        cbTransformColors.setText("Transform Colors");
        cbTransformColors.setSelected(analyzerParams.shouldRemapColors);

        labelSaturationFactor.setText("Saturation");
        labelVoidColor.setText("Off");
        labelTargetColor.setText("Target");
        labelHueBrightness.setText("Hue : Brightness");
        labelBrightnessGraph.setText("Brightness Graph");
        labelSatPercent.setText("%");
        labelHbRatio.setText(":");

        textSaturationFactor.setText(Misc.formatDouble(100.0 * analyzerParams.saturationFactor));
        elemVoidColor = new ColorElement("Off Color", analyzerParams.voidRemapColor);
        elemTargetColor = new ColorElement("Target Color", analyzerParams.targetRemapColor);

        elemVoidColor.setColorChangeListener(this);
        elemTargetColor.setColorChangeListener(this);

        textHueWeight.setText(Misc.formatDouble(analyzerParams.hueTransformWeight));
        textBrightnessWeight.setText(Misc.formatDouble(analyzerParams.brightnessTransformWeight));

        textBrightnessSegments.setText(Misc.formatIntVecTwoPointArray(analyzerParams.brightnessLineSegments));

        textSaturationFactor.setEnabled(analyzerParams.shouldRemapColors);
        textHueWeight.setEnabled(analyzerParams.shouldRemapColors);
        textBrightnessWeight.setEnabled(analyzerParams.shouldRemapColors);
        textBrightnessSegments.setEnabled(analyzerParams.shouldRemapColors);

        rbImageOriginal.setText("Keep Original Colors");
        rbImageOriginal.setSelected(!analyzerParams.shouldIsolateBrightestChannelInOutput);

        rbImageIsolated.setText("Isolate Brightest Channel");
        rbImageIsolated.setSelected(analyzerParams.shouldIsolateBrightestChannelInOutput && !analyzerParams.shouldExpandOutputToGrayScale);

        rbImageGray.setText("Convert to Grayscale");
        rbImageGray.setSelected(analyzerParams.shouldIsolateBrightestChannelInOutput && analyzerParams.shouldExpandOutputToGrayScale);

        groupImageRecolor.add(rbImageOriginal);
        groupImageRecolor.add(rbImageIsolated);
        groupImageRecolor.add(rbImageGray);

        btnReset.setText("Reset To Defaults");

        updateMemoryMonitor();

        cbUseBinaryMask.setSelected(analyzerParams.shouldUseBinaryMask);
        cbUseBinaryMask.setToolTipText("White foreground, black background (0/255 or 0/1); optional mask cleanup, no enhancement or resizing");
        JPanel dialogPanel = buildContent();
        bindParameterEvents(dialogPanel);
        updateInputModeControls();

        Container container = getContentPane();
        container.add(dialogPanel);
        this.pack();

        if (pluginCtx == null)
            this.setDefaultCloseOperation(0);

        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent evt) {
                try {
                    AnalyzerParameters params = buildAnalyzerParamsFromUi();
                    if (params.validate().size == 0)
                        ATPreferences.savePreferences(params, AngioTool.PREFS_TXT);
                }
                catch (IllegalArgumentException ignored) {}
                if (pluginCtx != null) {
                    for (ImagingWindow iw : new LinkedList<>(imagingWindows))
                        closeImagingWindow(iw);
                    dispose();
                }
                else {
                    AngioToolGui2.this.setVisible(false);
                    System.exit(0);
                }
            }
        });

        this.setMinimumSize(UiTheme.size(1040, 580));
        UiTheme.place(this, null, 1480, 900);
        mainWorkspace.setDividerLocation(UiTheme.size(400, 0).width);

        if (pluginCtx != null) {
            EventQueue.invokeLater(() -> {
                ArgbBuffer image = pluginCtx.makeArgbCopy();
                new ImagingWindow(this, image, 1.0, pluginCtx.imageFile, defaultPath).showInWorkspace();
            });
        }

        this.setVisible(true);
    }

    void setAnalysisUi(AnalyzerParameters analyzerParams)
    {
        updatingUi = true;
        cbUseBinaryMask.setSelected(analyzerParams.shouldUseBinaryMask);
        rbSkelFast.setSelected(analyzerParams.shouldUseFastSkeletonizer);
        rbSkelThorough.setSelected(!analyzerParams.shouldUseFastSkeletonizer);
        cbComputeLacunarity.setSelected(analyzerParams.shouldComputeLacunarity);
        cbComputeThickness.setSelected(analyzerParams.shouldComputeThickness);
        cbNormalizeOutputData.setSelected(analyzerParams.shouldNormalizeOutputData);

        elemMaxSkelIterations.update(analyzerParams.shouldCapSkelIterations, analyzerParams.maxSkelIterations);
        elemPruneSkeleton.update(analyzerParams.shouldPruneSkeleton, analyzerParams.maxTerminalBranchLength);
        elemMergeJunctions.update(analyzerParams.shouldMergeNearbyJunctions, analyzerParams.maxJunctionMergeDistance);
        elemResizeInputs.update(analyzerParams.shouldResizeImage, 100.0 * analyzerParams.resizingFactor);
        elemLinearScaleFactor.update(analyzerParams.shouldApplyLinearScale, 100.0 * analyzerParams.linearScalingFactor);
        elemRemoveParticles.update(analyzerParams.shouldRemoveSmallParticles, analyzerParams.removeSmallParticlesThreshold);
        elemFillHoles.update(analyzerParams.shouldFillHoles, analyzerParams.fillHolesValue);
        elemBinaryFillHoles.update(analyzerParams.binaryFillHoles, analyzerParams.binaryHoleRadius);
        elemBinaryRemoveParticles.update(analyzerParams.binaryRemoveSmallParticles, analyzerParams.binarySmallParticleRadius);

        textSigmas.setText(Misc.formatDoubleArray(analyzerParams.sigmas, "12"));
        textMinIntensity.setText("" + analyzerParams.thresholdLow);
        textMaxIntensity.setText("" + analyzerParams.thresholdHigh);

        elemOutline.update(analyzerParams.shouldDrawOutline, analyzerParams.outlineSize, analyzerParams.outlineColor);
        elemBranches.update(analyzerParams.shouldDrawBranchPoints, analyzerParams.branchingPointsSize, analyzerParams.branchingPointsColor);
        elemSkeleton.update(analyzerParams.shouldDrawSkeleton, analyzerParams.skeletonSize, analyzerParams.skeletonColor);
        elemConvexHull.update(analyzerParams.shouldDrawConvexHull, analyzerParams.convexHullSize, analyzerParams.convexHullColor);

        elemMaxHoleLevelPercent.update(analyzerParams.shouldFillBrightShapes, 100.0 * analyzerParams.brightShapeThresholdFactor);
        elemMinBoxnessPercent.update(analyzerParams.shouldApplyMinBoxness, 100.0 * analyzerParams.minBoxness);
        elemMinAreaLengthRatio.update(analyzerParams.shouldApplyMinAreaLength, analyzerParams.minAreaLengthRatio);

        textSaturationFactor.setText(Misc.formatDouble(100.0 * analyzerParams.saturationFactor));
        elemVoidColor.update(analyzerParams.voidRemapColor);
        elemTargetColor.update(analyzerParams.targetRemapColor);
        textHueWeight.setText(Misc.formatDouble(analyzerParams.hueTransformWeight));
        textBrightnessWeight.setText(Misc.formatDouble(analyzerParams.brightnessTransformWeight));
        textBrightnessSegments.setText(Misc.formatIntVecTwoPointArray(analyzerParams.brightnessLineSegments));

        cbTransformColors.setSelected(analyzerParams.shouldRemapColors);
        textSaturationFactor.setEnabled(analyzerParams.shouldRemapColors);
        textHueWeight.setEnabled(analyzerParams.shouldRemapColors);
        textBrightnessWeight.setEnabled(analyzerParams.shouldRemapColors);
        textBrightnessSegments.setEnabled(analyzerParams.shouldRemapColors);

        rbImageOriginal.setSelected(!analyzerParams.shouldIsolateBrightestChannelInOutput);
        rbImageIsolated.setSelected(analyzerParams.shouldIsolateBrightestChannelInOutput && !analyzerParams.shouldExpandOutputToGrayScale);
        rbImageGray.setSelected(analyzerParams.shouldIsolateBrightestChannelInOutput && analyzerParams.shouldExpandOutputToGrayScale);

        updatingUi = false;
        updateInputModeControls();
        toggleTransformColors();
    }

    private void initButton(JButton button, ImageIcon icon, String text)
    {
        button.setIcon(AngioTool.createResizedIcon(icon, 18, 18));
        button.setHorizontalTextPosition(SwingConstants.RIGHT);
        button.setText(text);
    }

    private JPanel buildContent()
    {
        JPanel root = new JPanel(new BorderLayout());
        JPanel header = new JPanel(new BorderLayout(18, 0));
        header.setBorder(new EmptyBorder(12, 18, 12, 18));
        JLabel title = new JLabel(AngioTool.TITLE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, UiTheme.fontSize(22f)));
        header.add(title, BorderLayout.WEST);
        btnLoadImageOrSubmit.setText(pluginCtx == null ? "Open image" : "Submit to ImageJ");
        btnLoadImageOrSubmit.setIcon(new FlatFileViewDirectoryIcon());
        btnStartBatch.setText("Batch");
        btnStartBatch.setIcon(new FlatFileChooserDetailsViewIcon());
        btnHelp.setText("");
        btnHelp.setIcon(new FlatHelpButtonIcon());
        UiTheme.tool(btnHelp, "Help");
        UiTheme.primary(btnLoadImageOrSubmit);
        header.add(pluginCtx == null ?
            UiTheme.row(btnLoadImageOrSubmit, btnStartBatch, btnHelp) :
            UiTheme.row(btnLoadImageOrSubmit, btnHelp), BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        JPanel thresholds = new JPanel(new GridLayout(1, 2, 12, 0));
        thresholds.add(UiTheme.field("Minimum intensity", textMinIntensity));
        thresholds.add(UiTheme.field("Maximum intensity", textMaxIntensity));
        textMinIntensity.setToolTipText("Inclusive lower threshold, 0 to 255");
        textMaxIntensity.setToolTipText("Inclusive upper threshold, 0 to 255");
        textSigmas.setToolTipText("Gaussian / Hessian sigma values, separated by commas");
        JPanel normalCleanup = new JPanel(new GridLayout(2, 1, 0, 8));
        normalCleanup.add(elemFillHoles.buildRow());
        normalCleanup.add(elemRemoveParticles.buildRow());
        JPanel binaryCleanup = new JPanel(new GridLayout(2, 1, 0, 8));
        binaryCleanup.add(elemBinaryFillHoles.buildRow());
        binaryCleanup.add(elemBinaryRemoveParticles.buildRow());
        cleanupInputs.add(normalCleanup, "detection");
        cleanupInputs.add(binaryCleanup, "binary");
        parameterTabs.addTab("Analysis", UiTheme.scrollColumn(
            UiTheme.section("Vessel detection",
                cbUseBinaryMask,
                UiTheme.field("Enhancement scales (sigma)", textSigmas), thresholds),
            UiTheme.section("Mask cleanup", cleanupInputs, UiTheme.disclosure("Advanced cleanup",
                    elemMaxHoleLevelPercent.buildRow(), elemMinBoxnessPercent.buildRow(),
                    elemMinAreaLengthRatio.buildRow())),
            UiTheme.section("Skeleton & measurements",
                UiTheme.row(rbSkelFast, rbSkelThorough),
                elemMaxSkelIterations.buildRow(),
                elemPruneSkeleton.buildRow(),
                elemMergeJunctions.buildRow(),
                UiTheme.row(cbComputeThickness, cbComputeLacunarity),
                cbNormalizeOutputData,
                elemLinearScaleFactor.buildRow()),
            UiTheme.section("Input size", elemResizeInputs.buildRow())
        ));

        JPanel colors = new JPanel(new GridLayout(1, 2, 12, 0));
        colors.add(UiTheme.field("Target color", UiTheme.row(elemTargetColor.panel)));
        colors.add(UiTheme.field("Excluded color", UiTheme.row(elemVoidColor.panel)));
        JPanel weights = new JPanel(new GridLayout(1, 2, 12, 0));
        weights.add(UiTheme.field("Hue weight", textHueWeight));
        weights.add(UiTheme.field("Brightness weight", textBrightnessWeight));
        parameterTabs.addTab("Color", UiTheme.scrollColumn(
            UiTheme.section("Color preprocessing", cbTransformColors, colors,
                UiTheme.field("Saturation (%)", textSaturationFactor), weights,
                UiTheme.field("Brightness curve", textBrightnessSegments))
        ));
        elemTargetColor.panel.setEnabled(cbTransformColors.isSelected());
        elemVoidColor.panel.setEnabled(cbTransformColors.isSelected());
        parameterTabs.addTab("Overlay", UiTheme.scrollColumn(
            UiTheme.section("Visible layers", elemOutline.buildRow(),
                elemSkeleton.buildRow(), elemBranches.buildRow(), elemConvexHull.buildRow()),
            UiTheme.section("Image background", rbImageOriginal, rbImageIsolated, rbImageGray)
        ));
        JPanel parameterPanel = new JPanel(new BorderLayout());
        parameterPanel.add(parameterTabs, BorderLayout.CENTER);
        parameterPanel.setMinimumSize(UiTheme.size(370, 200));
        parameterPanel.setPreferredSize(UiTheme.size(400, 700));

        JPanel footer = new JPanel(new BorderLayout(12, 10));
        footer.setBorder(new EmptyBorder(12, 18, 14, 18));
        btnReset.setText("Reset defaults");
        footer.add(btnReset, BorderLayout.WEST);
        labelMemory.setForeground(UiTheme.MUTED);
        labelMemory.setHorizontalAlignment(SwingConstants.RIGHT);
        footer.add(labelMemory, BorderLayout.CENTER);
        labelStatus.setForeground(UiTheme.MUTED);
        parameterPanel.add(footer, BorderLayout.SOUTH);
        labelStatus.setBorder(new EmptyBorder(7, 18, 9, 18));
        root.add(labelStatus, BorderLayout.SOUTH);

        JPanel empty = new JPanel(new GridBagLayout());
        empty.setBackground(new Color(29, 32, 35));
        JLabel emptyLabel = new JLabel("No image open");
        emptyLabel.setForeground(new Color(175, 186, 193));
        emptyLabel.setFont(emptyLabel.getFont().deriveFont(UiTheme.fontSize(18f)));
        empty.add(emptyLabel);
        imageTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        imageTabs.addChangeListener(e -> {
            Component selected = imageTabs.getSelectedComponent();
            if (selected instanceof ImagingWindow)
                ((ImagingWindow)selected).requestFocusInWindow();
        });
        imageArea.add(empty, "empty");
        imageArea.add(imageTabs, "images");
        imageCards.show(imageArea, "empty");
        imageArea.setMinimumSize(UiTheme.size(600, 200));
        mainWorkspace.setLeftComponent(parameterPanel);
        mainWorkspace.setRightComponent(imageArea);
        mainWorkspace.setResizeWeight(0);
        mainWorkspace.setContinuousLayout(true);
        mainWorkspace.setBorder(BorderFactory.createEmptyBorder());
        root.add(mainWorkspace, BorderLayout.CENTER);
        return root;
    }

    void attachImage(ImagingWindow image)
    {
        if (imagingWindows.contains(image))
            return;
        imagingWindows.add(image);
        String name = image.inputFile != null ? image.inputFile.getName() : "ImageJ image";
        imageTabs.addTab(name, image);
        int index = imageTabs.indexOfComponent(image);
        JPanel tab = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
        tab.setOpaque(false);
        JLabel label = new JLabel(name);
        label.setPreferredSize(UiTheme.size(150, 24));
        label.setToolTipText(image.inputFile != null ? image.inputFile.getAbsolutePath() : name);
        // Custom tab headers receive mouse events instead of the tabbed pane.
        java.awt.event.MouseAdapter selectTab = new java.awt.event.MouseAdapter()
        {
            @Override
            public void mousePressed(java.awt.event.MouseEvent event)
            {
                if (SwingUtilities.isLeftMouseButton(event) && imageTabs.indexOfComponent(image) >= 0)
                    imageTabs.setSelectedComponent(image);
            }
        };
        tab.addMouseListener(selectTab);
        label.addMouseListener(selectTab);
        JButton close = new JButton(new FlatTabbedPaneCloseIcon());
        close.setPreferredSize(UiTheme.size(24, 24));
        close.setMargin(new java.awt.Insets(0, 0, 0, 0));
        close.setBorderPainted(false);
        close.setContentAreaFilled(false);
        close.setToolTipText("Close " + name);
        close.getAccessibleContext().setAccessibleName("Close " + name);
        close.addActionListener(e -> closeImagingWindow(image));
        tab.add(label);
        tab.add(close);
        imageTabs.setTabComponentAt(index, tab);
        imageTabs.setToolTipTextAt(index, label.getToolTipText());
        imageCards.show(imageArea, "images");
        imageTabs.setSelectedComponent(image);
        imageArea.revalidate();
        validate();
        image.initializeDivider();
        updateMemoryMonitor();
        labelStatus.setText(imagingWindows.size() + " image(s) | Shared analysis parameters");
    }

    private void bindParameterEvents(Component component)
    {
        if (component instanceof JSpinner) {
            ((JSpinner)component).addChangeListener(e ->
                EventQueue.invokeLater(this::maybeUpdateImagingWindows));
            JFormattedTextField editor = ((JSpinner.DefaultEditor)((JSpinner)component)
                .getEditor()).getTextField();
            editor.addFocusListener(this);
            editor.addKeyListener(this);
            return;
        }
        if (component instanceof AbstractButton)
            ((AbstractButton)component).addActionListener(this);
        if (component instanceof JTextField) {
            component.addFocusListener(this);
            component.addKeyListener(this);
        }
        if (component instanceof Container)
            for (Component child : ((Container)component).getComponents())
                bindParameterEvents(child);
    }

    @Override
    public void onColorChanged(ColorElement colorElem)
    {
        maybeUpdateImagingWindows();
    }

    @Override
    public void actionPerformed(ActionEvent evt)
    {
        Object source = evt.getSource();
        if (pluginCtx != null && source == btnLoadImageOrSubmit)
            submitOverlayedImage();
        else if (source == btnLoadImageOrSubmit)
            openImageThenImagingWindow();
        else if (source == btnStartBatch)
            openBatchWindow();
        else if (source == btnHelp)
            openHelpWindow();
        else if (source == btnReset)
        {
            if (JOptionPane.showConfirmDialog(this, "Reset all analysis and overlay parameters?",
                    "Reset defaults", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION)
                setAnalysisUi(AnalyzerParameters.defaults());
        }
        else if (source == cbUseBinaryMask) {
            updateInputModeControls();
            maybeUpdateImagingWindows();
        }
        else if (source == cbTransformColors)
            toggleTransformColors();
        else
            maybeUpdateImagingWindows();
    }

    @Override
    public void focusGained(FocusEvent evt)
    {
        // ...
    }

    @Override
    public void focusLost(FocusEvent evt)
    {
        maybeUpdateImagingWindows();
    }

    @Override
    public void keyPressed(KeyEvent evt)
    {
        if (evt.getKeyCode() == KeyEvent.VK_ENTER)
            maybeUpdateImagingWindows();
    }

    @Override public void keyReleased(KeyEvent evt) {}
    @Override public void keyTyped(KeyEvent evt) {}

    void submitOverlayedImage()
    {
        if (imagingWindows.isEmpty() || imagingWindows.getFirst().imageUi.waiting ||
                imagingWindows.getFirst().imageUi.currentStats == null) {
            labelStatus.setText("Wait for an analysis result before submitting.");
            return;
        }
        ImagingWindow imageWindow = imagingWindows.get(0);
        Raster raster = imageWindow.getImageRaster();

        int width = raster.getWidth();
        int height = raster.getHeight();
        int[] pixels = ((DataBufferInt)raster.getDataBuffer()).getData();
        pluginCtx.setNewImage(pixels, width, height);

        Misc.sendWindowCloseEvent(this);
    }

    void openImageThenImagingWindow()
    {
        if (validatedParams() == null)
            return;
        File imageFile = openImageFile();
        if (imageFile == null)
            return;

        double resizeFactor = buildAnalyzerParamsFromUi().effectiveResizeFactor();

        ArgbBuffer image = null;
        Exception error = null;
        try {
            image = ImageFile.acquireImageForAnalysis(imageFile.getAbsolutePath(), resizeFactor);
        }
        catch (Exception ex) {
            error = ex;
        }

        if (image == null) {
            Misc.showDialogBox(
                "Failed to analyze image",
                error != null ?
                    Misc.buildDialogMessageFromException(error) :
                    "Image file could not be read"
            );
            return;
        }

        new ImagingWindow(this, image, resizeFactor, imageFile, defaultPath).showInWorkspace();

        updateMemoryMonitor();
    }

    void openBatchWindow()
    {
        BatchParameters batchParams = BatchParameters.defaults();
        try {
            ATPreferences.loadPreferences(batchParams, AngioTool.class, AngioTool.BATCH_TXT);
        }
        catch (Exception ignored) {}

        new BatchWindow(this, batchParams).showDialog();
        updateMemoryMonitor();
    }

    void openHelpWindow()
    {
        if (helpHtmlData == null) {
            String boxnessUrl = getClass().getResource("/images/boxness-formula.png").toString();
            try (InputStream in = getClass().getResourceAsStream("/manual.html")) {
                ByteVectorOutputStream vec = Misc.readFullyAsVector(in);
                this.helpHtmlData = vec.toString().replace("/images/boxness-formula.png", boxnessUrl).getBytes();
            }
            catch (Exception ex) {
                Misc.showExceptionInDialogBox(ex);
                return;
            }
        }

        new HelpWindow(this, this.helpHtmlData).showDialog();
        updateMemoryMonitor();
    }

    File openImageFile()
    {
        JFileChooser fc = Misc.createFileChooser();
        fc.setDialogTitle("Open Image to View/Analyze");
        fc.setDialogType(JFileChooser.OPEN_DIALOG);
        fc.setCurrentDirectory(new File(defaultPath));
        Misc.addImageFileFilters(fc);

        if (fc.showOpenDialog(this) != 0)
            return null;

        File selectedFile = fc.getSelectedFile();
        rememberViewDirectory(selectedFile);
        return selectedFile;
    }

    void rememberViewDirectory(File imageFile)
    {
        if (imageFile == null)
            return;

        File directory = imageFile.getParentFile();
        if (directory == null)
            return;

        defaultPath = directory.getAbsolutePath();
        ATPreferences.savePreferences(new ViewPreferences(defaultPath), AngioTool.VIEW_PREFS_TXT);
    }

    void toggleTransformColors()
    {
        boolean enabled = cbTransformColors.isSelected() && !cbUseBinaryMask.isSelected();
        textSaturationFactor.setEnabled(enabled);
        textHueWeight.setEnabled(enabled);
        textBrightnessWeight.setEnabled(enabled);
        textBrightnessSegments.setEnabled(enabled);
        elemVoidColor.panel.setEnabled(enabled);
        elemTargetColor.panel.setEnabled(enabled);

        maybeUpdateImagingWindows();
    }

    private void updateInputModeControls()
    {
        boolean detection = !cbUseBinaryMask.isSelected();
        ((CardLayout)cleanupInputs.getLayout()).show(cleanupInputs, detection ? "detection" : "binary");
        for (NumberEntry entry : new NumberEntry[] {elemBinaryFillHoles, elemBinaryRemoveParticles}) {
            entry.cb.setEnabled(!detection);
            entry.spinner.setEnabled(!detection && entry.cb.isSelected());
        }
        textSigmas.setEnabled(detection);
        textMinIntensity.setEnabled(detection);
        textMaxIntensity.setEnabled(detection);
        cbTransformColors.setEnabled(detection);
        for (NumberEntry entry : new NumberEntry[] {elemResizeInputs, elemFillHoles,
                elemRemoveParticles, elemMaxHoleLevelPercent, elemMinBoxnessPercent,
                elemMinAreaLengthRatio}) {
            entry.cb.setEnabled(detection);
            entry.spinner.setEnabled(detection && entry.cb.isSelected());
        }
        boolean color = detection && cbTransformColors.isSelected();
        for (JTextField field : new JTextField[] {textSaturationFactor, textHueWeight,
                textBrightnessWeight, textBrightnessSegments})
            field.setEnabled(color);
        elemVoidColor.panel.setEnabled(color);
        elemTargetColor.panel.setEnabled(color);
    }

    void maybeUpdateImagingWindows()
    {
        if (updatingUi)
            return;
        AnalyzerParameters params = validatedParams();
        if (params == null)
            return;

        if (latestAnalyzerParams != null && params.equals(latestAnalyzerParams)) {
            updateMemoryMonitor();
            return;
        }

        latestAnalyzerParams = params;

        for (ImagingWindow iw : imagingWindows)
            iw.updateImage(params);

        updateMemoryMonitor();
    }

    public void closeImagingWindow(ImagingWindow window)
    {
        window.closeSafely();
        imagingWindows.remove(window);
        imageTabs.remove(window);
        if (imageTabs.getTabCount() == 0)
            imageCards.show(imageArea, "empty");
        imageArea.revalidate();
        imageArea.repaint();
        updateMemoryMonitor();
        labelStatus.setText(imagingWindows.isEmpty() ? "Ready" :
            imagingWindows.size() + " image(s) | Shared analysis parameters");
    }

    AnalyzerParameters validatedParams()
    {
        try {
            AnalyzerParameters params = buildAnalyzerParamsFromUi();
            RefVector<String> errors = params.validate();
            if (errors.size > 0)
                throw new IllegalArgumentException(errors.buf[0]);
            labelStatus.setForeground(UiTheme.MUTED);
            labelStatus.setText(imagingWindows.isEmpty() ? "Ready" :
                imagingWindows.size() + " image(s) | Shared analysis parameters");
            labelStatus.setToolTipText(null);
            return params;
        }
        catch (IllegalArgumentException ex) {
            labelStatus.setForeground(UiTheme.ERROR);
            labelStatus.setText(ex.getMessage());
            labelStatus.setToolTipText(ex.getMessage());
            return null;
        }
    }

    static double number(JTextField field, String name)
    {
        try {
            double value = Double.parseDouble(field.getText().trim());
            if (!Double.isFinite(value))
                throw new NumberFormatException();
            return value;
        }
        catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + ": enter a valid number.");
        }
    }

    static int intensity(JTextField field, String name)
    {
        double value = number(field, name);
        if (value != Math.rint(value) || value < 0 || value > 255)
            throw new IllegalArgumentException(name + ": enter an integer from 0 to 255.");
        return (int)value;
    }

    private double detectionNumber(JTextField field, String name, double fallback)
    {
        try {
            return number(field, name);
        }
        catch (IllegalArgumentException ex) {
            if (!cbUseBinaryMask.isSelected())
                throw ex;
            return fallback;
        }
    }

    private double detectionNumber(NumberEntry entry)
    {
        return detectionNumber(entry.tf, entry.name, entry.originalValue);
    }

    private int detectionIntensity(JTextField field, String name, int fallback)
    {
        try {
            return intensity(field, name);
        }
        catch (IllegalArgumentException ex) {
            if (!cbUseBinaryMask.isSelected())
                throw ex;
            return fallback;
        }
    }

    public AnalyzerParameters buildAnalyzerParamsFromUi()
    {
        ButtonModel skelType = groupSkeletonizer.getSelection();
        boolean shouldUseFastSkel = skelType == rbSkelFast.getModel();

        ButtonModel recolorType = groupImageRecolor.getSelection();
        boolean shouldIsolateChannel;
        boolean shouldExpandToGrayScale;
        if (recolorType == rbImageOriginal.getModel()) {
            shouldIsolateChannel = false;
            shouldExpandToGrayScale = false;
        }
        else if (recolorType == rbImageGray.getModel()) {
            shouldIsolateChannel = true;
            shouldExpandToGrayScale = true;
        }
        else {
            shouldIsolateChannel = true;
            shouldExpandToGrayScale = false;
        }

        boolean binary = cbUseBinaryMask.isSelected();
        AnalyzerParameters params = new AnalyzerParameters(
            elemResizeInputs.cb.isSelected(),
            detectionNumber(elemResizeInputs) / 100.0,
            cbTransformColors.isSelected(),
            detectionNumber(textHueWeight, "Hue weight", 1.0),
            detectionNumber(textBrightnessWeight, "Brightness weight", 1.0),
            elemTargetColor.color,
            elemVoidColor.color,
            detectionNumber(textSaturationFactor, "Saturation", 100.0) / 100.0,
            Misc.getSomeInts(textBrightnessSegments.getText()),
            elemMaxHoleLevelPercent.cb.isSelected(),
            detectionNumber(elemMaxHoleLevelPercent) / 100.0,
            elemMinBoxnessPercent.cb.isSelected(),
            detectionNumber(elemMinBoxnessPercent) / 100.0,
            elemMinAreaLengthRatio.cb.isSelected(),
            detectionNumber(elemMinAreaLengthRatio),
            elemRemoveParticles.cb.isSelected(),
            detectionNumber(elemRemoveParticles),
            elemFillHoles.cb.isSelected(),
            detectionNumber(elemFillHoles),
            Misc.getSomeDoubles(textSigmas.getText()),
            detectionIntensity(textMaxIntensity, "Maximum intensity", 255),
            detectionIntensity(textMinIntensity, "Minimum intensity", 15),
            shouldUseFastSkel,
            elemMaxSkelIterations.cb.isSelected(),
            (int)elemMaxSkelIterations.getValue(),
            elemLinearScaleFactor.cb.isSelected(),
            elemLinearScaleFactor.getValue() / 100.0,
            elemOutline.cb.isSelected(),
            elemOutline.getColor(),
            elemOutline.getValue(),
            elemSkeleton.cb.isSelected(),
            elemSkeleton.getColor(),
            elemSkeleton.getValue(),
            elemBranches.cb.isSelected(),
            elemBranches.getColor(),
            elemBranches.getValue(),
            elemConvexHull.cb.isSelected(),
            elemConvexHull.getColor(),
            elemConvexHull.getValue(),
            shouldIsolateChannel,
            shouldExpandToGrayScale,
            cbComputeLacunarity.isSelected(),
            cbComputeThickness.isSelected()
        );
        params.shouldPruneSkeleton = elemPruneSkeleton.cb.isSelected();
        params.maxTerminalBranchLength = elemPruneSkeleton.getValue();
        params.shouldMergeNearbyJunctions = elemMergeJunctions.cb.isSelected();
        params.maxJunctionMergeDistance = elemMergeJunctions.getValue();
        params.shouldNormalizeOutputData = cbNormalizeOutputData.isSelected();
        params.shouldUseBinaryMask = binary;
        params.binaryFillHoles = elemBinaryFillHoles.cb.isSelected();
        params.binaryRemoveSmallParticles = elemBinaryRemoveParticles.cb.isSelected();
        params.binaryHoleRadius = binary ? elemBinaryFillHoles.getValue() :
            ((Number)elemBinaryFillHoles.spinner.getValue()).doubleValue();
        params.binarySmallParticleRadius = binary ? elemBinaryRemoveParticles.getValue() :
            ((Number)elemBinaryRemoveParticles.spinner.getValue()).doubleValue();
        return params;
    }

    public void updateMemoryMonitor()
    {
        Runtime rt = Runtime.getRuntime();
        long maxMB  = (rt.maxMemory() + (1L << 19)) >> 20L;
        long usedMB = (rt.totalMemory() - rt.freeMemory() + (1L << 19)) >> 20L;

        labelMemory.setText(usedMB + " / " + maxMB + " MB");
    }
}
