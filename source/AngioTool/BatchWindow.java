package AngioTool;

import Pixels.Rgb;
import Utils.*;
import Xlsx.*;
import java.awt.Color;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileFilter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import com.formdev.flatlaf.icons.FlatFileViewDirectoryIcon;
import com.formdev.flatlaf.icons.FlatFileChooserDetailsViewIcon;

public class BatchWindow extends JFrame implements BatchProcessing.IProgressToken
{
    static final int UNITS_GAP = 4;
    static final double BALANCED_FACTOR = 0.75;

    final AngioToolGui2 mainWindow;

    final JLabel labelData = new JLabel();
    final JLabel labelInputFolders = new JLabel();
    final JButton btnInputFolders = new JButton();
    final JTextField textInputFolders = new JTextField();
    final JLabel labelExcel = new JLabel();
    final JButton btnExcel = new JButton();
    final JTextField textExcel = new JTextField();
    final JCheckBox cbAutomaticExcelPath = new JCheckBox();
    final ButtonGroup groupSaveResults = new ButtonGroup();
    final JRadioButton rbNoOutput = new JRadioButton();
    final JRadioButton rbSameOutput = new JRadioButton();
    final JRadioButton rbSaveResultsTo = new JRadioButton();
    final JButton btnSaveResultsFolder = new JButton();
    final JTextField textSaveResultsFolder = new JTextField();
    final JLabel labelResultsImageFormat = new JLabel();
    final JComboBox<String> imageFormat = new JComboBox<>(
        new String[] {"png", "tif", "tiff", "jpg", "jpeg", "gif", "bmp", "ppm"});
    final JCheckBox cbWorkerCount = new JCheckBox();
    final JSpinner workerSpinner = new JSpinner(new SpinnerNumberModel(1, 1, Integer.MAX_VALUE, 1));
    final JTextField textWorkerCount = ((JSpinner.DefaultEditor)workerSpinner.getEditor()).getTextField();
    final JLabel labelWorkerCountHelp = new JLabel();

    final JSeparator sepProgress = new JSeparator();
    final JLabel labelProgress = new JLabel();
    final JLabel overallLabel = new JLabel();
    final JProgressBar overallProgress = new JProgressBar();
    final JLabel labelCurrentFile = new JLabel();
    final JButton analyzeBtn = new JButton();
    final JButton cancelBtn = new JButton();

    final String workerCountHelpString;
    int storedWorkerCount;

    String defaultPath;
    ArrayList<XlsxReader.SheetCells> originalSheets = new ArrayList<>();
    int nErrors = 0;

    public Future<Void> analysisTaskFuture = null;
    public final AtomicBoolean isClosed = new AtomicBoolean(false);

    public BatchWindow(AngioToolGui2 mainWindow, BatchParameters params)
    {
        super("Batch Analysis");
        this.mainWindow = mainWindow;
        this.defaultPath = params.defaultPath;

        textInputFolders.setText(joinInputPaths(params.inputImagePaths));
        cbAutomaticExcelPath.setText("Create spreadsheet in first input folder");
        cbAutomaticExcelPath.setSelected(params.shouldUseAutomaticExcelPath);
        cbAutomaticExcelPath.setToolTipText("Uses the first selected input folder; edit the generated workbook name below if needed.");
        cbAutomaticExcelPath.addActionListener(e -> toggleAutomaticExcelPath(true));
        if (params.shouldUseAutomaticExcelPath) {
            File firstInput = firstInputFolder();
            textExcel.setText(Misc.isValidPath(params.excelFilePath) ?
                new File(Misc.stripWrappingQuotes(params.excelFilePath)).getName() :
                BatchParameters.defaultExcelFileName(firstInput));
        }
        else {
            textExcel.setText(params.excelFilePath != null ? params.excelFilePath : "");
        }
        textSaveResultsFolder.setText(params.resultImagesPath != null ? params.resultImagesPath : "");

        this.setIconImage(AngioTool.ATIcon.getImage());

        labelData.setText("Data");
        Misc.setNewFontSizeOn(labelData, 20);

        labelInputFolders.setText("Select input folders:");

        btnInputFolders.setIcon(new FlatFileViewDirectoryIcon());
        UiTheme.tool(btnInputFolders, "Choose input folders");
        btnInputFolders.addActionListener((ActionEvent e) -> BatchWindow.this.selectInputFolders());
        //textInputFolders

        labelExcel.setText("Excel spreadsheet:");

        btnExcel.setIcon(new FlatFileChooserDetailsViewIcon());
        UiTheme.tool(btnExcel, "Choose spreadsheet");
        btnExcel.addActionListener((ActionEvent e) -> BatchWindow.this.selectExcelFile());
        //textExcel

        rbNoOutput.setText("Spreadsheet only");
        rbNoOutput.addActionListener((ActionEvent e) -> BatchWindow.this.toggleSaveResults());
        rbNoOutput.setSelected(!params.shouldSaveResultImages);

        rbSameOutput.setText("Create results folders next to inputs");
        rbSameOutput.addActionListener((ActionEvent e) -> BatchWindow.this.toggleSaveResults());
        rbSameOutput.setSelected(params.shouldSaveResultImages && !params.shouldSaveImagesToSpecificFolder);

        rbSaveResultsTo.setText("Save result images to:");
        rbSaveResultsTo.addActionListener((ActionEvent e) -> BatchWindow.this.toggleSaveResults());
        rbSaveResultsTo.setSelected(params.shouldSaveResultImages && params.shouldSaveImagesToSpecificFolder);

        groupSaveResults.add(rbNoOutput);
        groupSaveResults.add(rbSameOutput);
        groupSaveResults.add(rbSaveResultsTo);

        btnSaveResultsFolder.setIcon(new FlatFileViewDirectoryIcon());
        UiTheme.tool(btnSaveResultsFolder, "Choose output folder");
        btnSaveResultsFolder.addActionListener((ActionEvent e) -> BatchWindow.this.selectResultFolder());
        //textSaveResultsFolder

        toggleSaveResults();
        toggleAutomaticExcelPath(false);

        labelResultsImageFormat.setText("Result image format: ");
        imageFormat.setEditable(true);
        imageFormat.setSelectedItem(params.resultImageFormat);

        cbWorkerCount.setText("Override job count: ");
        cbWorkerCount.setSelected(params.shouldOverrideWorkerCount);
        cbWorkerCount.addActionListener((ActionEvent e) -> BatchWindow.this.toggleWorkerCount());

        int nProcessors = Runtime.getRuntime().availableProcessors();
        int defaultWorkerCount = getDefaultWorkerCount();
        this.workerCountHelpString =
            "<html>Recommended job count:<br>" + nProcessors + " for maximum throughput,<br>" +
             defaultWorkerCount + " (default) for balancing resources</html>";

        Misc.setNewFontStyleOn(labelWorkerCountHelp, Font.PLAIN);

        if (params.shouldOverrideWorkerCount) {
            workerSpinner.setValue(Math.max(1, params.workerCount));
            labelWorkerCountHelp.setText(workerCountHelpString);
        }
        else {
            workerSpinner.setValue(defaultWorkerCount);
        }

        storedWorkerCount = params.workerCount > 0 ? params.workerCount : defaultWorkerCount;

        //sepProgress

        labelProgress.setText("Progress");
        Misc.setNewFontSizeOn(labelProgress, 20);

        //overallLabel
        overallProgress.setValue(0);
        overallProgress.setStringPainted(true);

        Misc.setNewFontStyleOn(labelCurrentFile, Font.ITALIC);

        analyzeBtn.setText("Run analysis");
        UiTheme.primary(analyzeBtn);
        analyzeBtn.addActionListener((ActionEvent e) -> BatchWindow.this.startAnalysis());

        cancelBtn.setText("Cancel");
        cancelBtn.addActionListener((ActionEvent e) -> BatchWindow.this.cancel());
        cancelBtn.setEnabled(false);
    }

    public void showDialog()
    {
        JPanel dialogPanel = buildContent();

        this.getContentPane().add(dialogPanel);
        this.pack();

        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent evt) {
                try {
                    ATPreferences.savePreferences(buildBatchParamsFromUi(), AngioTool.BATCH_TXT);
                }
                catch (IllegalArgumentException ignored) {}
                cancel();
            }
        });

        this.setMinimumSize(UiTheme.size(580, 500));
        UiTheme.place(this, mainWindow, 680, 760);
        this.setVisible(true);
    }

    private JPanel buildContent()
    {
        JPanel root = new JPanel(new BorderLayout());
        JLabel title = new JLabel("Batch analysis");
        title.setFont(title.getFont().deriveFont(Font.BOLD, UiTheme.fontSize(22f)));
        title.setBorder(new EmptyBorder(18, 18, 2, 18));
        root.add(title, BorderLayout.NORTH);
        textInputFolders.putClientProperty("JTextField.placeholderText", "Input folders");
        textExcel.putClientProperty("JTextField.placeholderText", "Results workbook (.xlsx)");
        textSaveResultsFolder.putClientProperty("JTextField.placeholderText", "Output folder");
        imageFormat.setPreferredSize(UiTheme.size(110, 30));
        workerSpinner.setPreferredSize(UiTheme.size(110, 30));
        workerSpinner.setEnabled(cbWorkerCount.isSelected());
        labelWorkerCountHelp.setText("");
        cbWorkerCount.setToolTipText(workerCountHelpString);
        root.add(UiTheme.scrollColumn(
            UiTheme.section("Inputs",
                UiTheme.field("Image folders", pathRow(textInputFolders, btnInputFolders))),
            UiTheme.section("Outputs",
                cbAutomaticExcelPath,
                UiTheme.field("Spreadsheet", pathRow(textExcel, btnExcel)),
                rbNoOutput, rbSameOutput, rbSaveResultsTo,
                pathRow(textSaveResultsFolder, btnSaveResultsFolder),
                UiTheme.row(labelResultsImageFormat, imageFormat)),
            UiTheme.section("Execution", UiTheme.row(cbWorkerCount, workerSpinner))
        ), BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(0, 10));
        footer.setBorder(new EmptyBorder(14, 18, 18, 18));
        JPanel progress = new JPanel(new BorderLayout(0, 6));
        overallLabel.setText("Ready");
        progress.add(overallLabel, BorderLayout.NORTH);
        progress.add(overallProgress, BorderLayout.CENTER);
        labelCurrentFile.setPreferredSize(new Dimension(200, 22));
        progress.add(labelCurrentFile, BorderLayout.SOUTH);
        footer.add(progress, BorderLayout.CENTER);
        JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        actions.add(cancelBtn);
        actions.add(analyzeBtn);
        footer.add(actions, BorderLayout.SOUTH);
        root.add(footer, BorderLayout.SOUTH);
        return root;
    }

    private JPanel pathRow(JTextField path, JButton button)
    {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.add(path, BorderLayout.CENTER);
        row.add(button, BorderLayout.EAST);
        return row;
    }

    void toggleSaveResults() {
        boolean enabled = rbSaveResultsTo.isSelected();
        btnSaveResultsFolder.setEnabled(enabled);
        textSaveResultsFolder.setEnabled(enabled);
        labelResultsImageFormat.setEnabled(enabled);
        imageFormat.setEnabled(enabled);
    }

    void toggleWorkerCount() {
        boolean enabled = cbWorkerCount.isSelected();
        workerSpinner.setEnabled(enabled);
        if (enabled) {
            int workerCount = storedWorkerCount > 0 ? storedWorkerCount : getDefaultWorkerCount();
            workerSpinner.setValue(workerCount);
        }
        else {
            try {
                storedWorkerCount = Integer.parseInt(textWorkerCount.getText());
            }
            catch (Exception ignored) {
                storedWorkerCount = 0;
            }
            workerSpinner.setValue(getDefaultWorkerCount());
        }
    }

    void toggleAutomaticExcelPath(boolean updatePath)
    {
        boolean automatic = cbAutomaticExcelPath.isSelected();
        btnExcel.setEnabled(!automatic);
        if (automatic) {
            originalSheets = new ArrayList<>();
            if (updatePath || !Misc.isValidPath(textExcel.getText()))
                textExcel.setText(BatchParameters.defaultExcelFileName(firstInputFolder()));
        }
        else if (updatePath) {
            File inputFolder = firstInputFolder();
            File entered = new File(Misc.stripWrappingQuotes(textExcel.getText()));
            if (inputFolder != null && entered.getParentFile() == null)
                textExcel.setText(new File(inputFolder, entered.getName()).getAbsolutePath());
        }
    }

    void selectInputFolders() {
        JFileChooser fc = Misc.createFileChooser();
        fc.setDialogTitle("Select Folders");
        fc.setDialogType(JFileChooser.OPEN_DIALOG);
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setMultiSelectionEnabled(true);
        fc.setCurrentDirectory(new File(defaultPath));

        if (fc.showOpenDialog(this) != 0)
            return;

        File[] folderList = fc.getSelectedFiles();
        if (folderList == null || folderList.length == 0)
            return;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < folderList.length; i++) {
            if (i > 0)
                sb.append(";");
            sb.append(folderList[i].getAbsolutePath().replace(";", "" + File.separatorChar + ";"));
        }

        textInputFolders.setText(sb.toString());
        if (cbAutomaticExcelPath.isSelected()) {
            originalSheets = new ArrayList<>();
            textExcel.setText(BatchParameters.defaultExcelFileName(folderList[0]));
        }
    }

    void selectExcelFile() {
        String[] outStrings = new String[2];
        ArrayList<XlsxReader.SheetCells> sheets = Misc.openSpreadsheetForAppending(outStrings, null, defaultPath, this);
        if (sheets != null) {
            cbAutomaticExcelPath.setSelected(false);
            toggleAutomaticExcelPath(false);
            originalSheets = sheets;
            defaultPath = outStrings[1];
            textExcel.setText(outStrings[0]);
        }
    }

    void selectResultFolder() {
        JFileChooser fc = Misc.createFileChooser();
        fc.setDialogTitle("Select Folders");
        fc.setDialogType(JFileChooser.OPEN_DIALOG);
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setCurrentDirectory(new File(defaultPath));

        if (fc.showOpenDialog(this) == 0) {
            File file = fc.getSelectedFile();
            if (file != null)
                textSaveResultsFolder.setText(file.getAbsolutePath());
        }
    }

    public BatchParameters buildBatchParamsFromUi()
    {
        boolean shouldSaveImages;
        boolean shouldUseSpecificOutputFolder;
        ButtonModel saveImageType = groupSaveResults.getSelection();
        if (saveImageType == rbSaveResultsTo.getModel()) {
            shouldSaveImages = true;
            shouldUseSpecificOutputFolder = true;
        }
        else if (saveImageType == rbNoOutput.getModel()) {
            shouldSaveImages = false;
            shouldUseSpecificOutputFolder = false;
        }
        else {
            shouldSaveImages = true;
            shouldUseSpecificOutputFolder = false;
        }

        boolean shouldOverrideWorkerCount = cbWorkerCount.isSelected();
        int workerCount = shouldOverrideWorkerCount ?
            Integer.parseInt(textWorkerCount.getText()) :
            getDefaultWorkerCount();

        return new BatchParameters(
            defaultPath,
            Misc.splitPaths(textInputFolders.getText(), ';', File.separatorChar),
            Misc.stripWrappingQuotes(textExcel.getText()),
            cbAutomaticExcelPath.isSelected(),
            shouldSaveImages,
            shouldUseSpecificOutputFolder,
            Misc.stripWrappingQuotes(textSaveResultsFolder.getText()),
            imageFormat.getEditor().getItem().toString(),
            shouldOverrideWorkerCount,
            workerCount
        );
    }

    File firstInputFolder()
    {
        String[] paths = Misc.splitPaths(textInputFolders.getText(), ';', File.separatorChar);
        return paths != null && paths.length > 0 && Misc.isValidPath(paths[0]) ?
            new File(Misc.stripWrappingQuotes(paths[0])).getAbsoluteFile() : null;
    }

    static String joinInputPaths(String[] paths)
    {
        if (paths == null || paths.length == 0)
            return "";
        StringBuilder sb = new StringBuilder();
        for (String path : paths) {
            if (path == null)
                continue;
            if (sb.length() > 0)
                sb.append(';');
            sb.append(path.replace(";", "" + File.separatorChar + ";"));
        }
        return sb.toString();
    }

    public void startAnalysis()
    {
        if (analysisTaskFuture != null && !analysisTaskFuture.isDone()) {
            Misc.showDialogBox(
                "Analysis Still in Progress",
                "A batch analysis is still running. Either cancel it or wait for it to complete."
            );
            return;
        }

        nErrors = 0;

        AnalyzerParameters params;
        try {
            params = mainWindow.buildAnalyzerParamsFromUi();
        }
        catch (Throwable t) {
            Misc.showDialogBox("Parsing Error", "Invalid data in the analysis form (" + t.getClass().getSimpleName() + ")");
            return;
        }

        RefVector<String> errors = params.validate();

        BatchParameters batchParams;
        try {
            batchParams = buildBatchParamsFromUi();
        }
        catch (Throwable t) {
            Misc.showDialogBox("Parsing Error", "Invalid data in the batch form (" + t.getClass().getSimpleName() + ")");
            return;
        }

        RefVector<String> batchErrors = batchParams.validate();
        errors.extend(batchErrors);

        if (errors.size > 0) {
            int nErrors = errors.size;
            String header = nErrors > 1 ? ("There were " + nErrors + " errors:\n") : "";
            Misc.showDialogBox(
                "Validation Error" + (nErrors > 1 ? "s" : ""),
                header + errors.makeJoinedString("\n")
            );
            return;
        }

        cancelBtn.setEnabled(true);
        analyzeBtn.setEnabled(false);

        ATPreferences.savePreferences(params, AngioTool.PREFS_TXT);
        ATPreferences.savePreferences(batchParams, AngioTool.BATCH_TXT);

        analysisTaskFuture = (Future<Void>)Analyzer.threadPool.submit(() -> {
            try {
                BatchProcessing.doBatchAnalysis(params, batchParams, BatchWindow.this, originalSheets);
            }
            finally {
                SwingUtilities.invokeLater(() -> {
                    if (!isClosed.get()) {
                        analyzeBtn.setEnabled(true);
                        cancelBtn.setEnabled(false);
                        if (overallLabel.getText().equals("Finding every image to be analyzed..."))
                            overallLabel.setText("Stopped before analysis");
                    }
                });
            }
        });
    }

    void updateWindowSize() {
        Dimension preferred = this.getPreferredSize();
        Dimension curSize = this.getSize();

        if (preferred.height > curSize.height)
            this.setSize(new Dimension(curSize.width, preferred.height));
    }

    @Override
    public boolean isClosed()
    {
        return this.isClosed.get();
    }

    @Override
    public void notifyNoImages()
    {
        SwingUtilities.invokeLater(() -> {
            if (isClosed.get())
                return;

            overallLabel.setText("No images were found!");
            cancelBtn.setEnabled(false);
            analyzeBtn.setEnabled(true);

            updateWindowSize();
        });
    }

    @Override
    public void onEnumerationStart()
    {
        SwingUtilities.invokeLater(() -> {
            if (isClosed.get())
                return;

            overallLabel.setText("Finding every image to be analyzed...");

            updateWindowSize();
        });
    }

    @Override
    public void onBatchStatsKnown(int nImages)
    {
        SwingUtilities.invokeLater(() -> {
            if (isClosed.get())
                return;

            overallLabel.setText("Analyzing images...");
            overallProgress.setValue(0);
            overallProgress.setMaximum(nImages);

            updateWindowSize();
        });
    }

    @Override
    public void notifyImageWasInvalid()
    {
        SwingUtilities.invokeLater(() -> {
            if (isClosed.get())
                return;

            int nImages = overallProgress.getMaximum();
            overallProgress.setMaximum(nImages > 1 ? (nImages-1) : 1);
        });
    }

    @Override
    public void onImageDone(String path, Throwable error)
    {
        SwingUtilities.invokeLater(() -> {
            if (isClosed.get())
                return;

            if (error != null)
                nErrors++;

            int current = overallProgress.getValue() + 1;
            String status = "" + current + "/" + overallProgress.getMaximum();
            if (nErrors > 0)
                status += ", " + nErrors + (nErrors == 1 ? " error." : " errors.");

            overallLabel.setText(status);
            overallProgress.setValue(overallProgress.getValue() + 1);
            labelCurrentFile.setText(path);
            labelCurrentFile.setToolTipText(path);

            updateWindowSize();
        });
    }

    @Override
    public void onFinished(SpreadsheetWriter sw)
    {
        SwingUtilities.invokeLater(() -> {
            if (isClosed.get())
                return;

            if (sw.currentSheetIdx >= 0 && sw.currentSheetIdx < sw.sheets.size)
                originalSheets.add(new XlsxReader.SheetCells(sw.sheets.buf[sw.currentSheetIdx].valueRows));

            int nImages = overallProgress.getMaximum();
            String status = "" + nImages + "/" + nImages;
            if (nErrors > 0) {
                status += " Finished with " + nErrors + " error";
                if (nErrors != 1)
                    status += "s";
            }
            else {
                status += " Done!";
            }

            overallLabel.setText(status);
            cancelBtn.setEnabled(false);
            analyzeBtn.setEnabled(true);

            updateWindowSize();

            final File xlsxFile = new File(sw.parentFolder, sw.fileName);
            Analyzer.threadPool.submit(() -> {
                try { Desktop.getDesktop().open(xlsxFile); }
                catch (IOException ex) { ex.printStackTrace(); }
            });
        });
    }

    public void cancel()
    {
        SwingUtilities.invokeLater(() -> {
            if (!isClosed.getAndSet(true))
                this.dispose();
        });
    }

    public static int getDefaultWorkerCount()
    {
        return Math.max(1, (int)(Runtime.getRuntime().availableProcessors() * BALANCED_FACTOR));
    }
}
