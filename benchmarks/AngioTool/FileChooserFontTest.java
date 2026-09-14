package AngioTool;

import Utils.Misc;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.*;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.swing.*;

public class FileChooserFontTest
{
    static final String CJK = "\u4e2d\u6587\u6587\u4ef6\u5939\u540d\u79f0\u6253\u5f00\u53d6\u6d88\u684c\u9762\u56fe\u50cf";
    static JFrame frame;
    static JFileChooser chooser;
    static Path output;

    public static void main(String[] args) throws Exception
    {
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE);
        JComponent.setDefaultLocale(Locale.SIMPLIFIED_CHINESE);
        Path home = Files.createTempDirectory("angiotool-font-test-");
        System.setProperty("user.home", home.toString());
        Path images = Files.createDirectories(home.resolve("\u4e2d\u6587\u56fe\u50cf"));
        Files.createDirectories(images.resolve("\u8840\u7ba1\u5206\u6790"));
        File sample = images.resolve("\u6837\u672c_001.png").toFile();
        ImageIO.write(new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB), "png", sample);
        output = Paths.get(args.length > 0 ? args[0] : "screenshots/file-chooser-font");
        Files.createDirectories(output);
        System.out.println("Segoe UI missing glyph index: " + new Font("Segoe UI", Font.PLAIN, 13).canDisplayUpTo(CJK));
        System.out.println("Dialog missing glyph index: " + new Font(Font.DIALOG, Font.PLAIN, 13).canDisplayUpTo(CJK));
        try {
            SwingUtilities.invokeAndWait(() -> {
                UiTheme.install();
                frame = new JFrame("File chooser CJK regression");
                chooser = Misc.createFileChooser();
                chooser.setCurrentDirectory(images.toFile());
                chooser.setSelectedFile(sample);
                frame.add(chooser);
                frame.pack();
                UiTheme.place(frame, null, 900, 620);
                frame.setVisible(true);
            });
            // FilePane populates its directory model asynchronously.
            Thread.sleep(800);
            SwingUtilities.invokeAndWait(() -> {
                snapshot("open-image.png");
                checkFont(UIManager.getFont("defaultFont"), "Theme default");
                checkComponents(chooser);
                check(chooser.getSelectedFile().equals(sample), "Chinese image selection");
                chooser.approveSelection();
                check(chooser.getSelectedFile().equals(sample), "Chinese image approval");
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                chooser.setMultiSelectionEnabled(true);
                chooser.setSelectedFiles(new File[] {images.resolve("\u8840\u7ba1\u5206\u6790").toFile()});
                frame.validate();
                snapshot("select-folders.png");
                checkComponents(chooser);
                chooser.approveSelection();
                check(chooser.getSelectedFiles().length == 1, "Folder selection retained");
                check(chooser.getSelectedFiles()[0].getName().equals("\u8840\u7ba1\u5206\u6790"),
                    "Chinese folder approval");
                chooser.setMultiSelectionEnabled(false);
                chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                chooser.setDialogType(JFileChooser.SAVE_DIALOG);
                File destination = images.resolve("\u7edf\u8ba1\u7ed3\u679c.xlsx").toFile();
                chooser.setSelectedFile(destination);
                frame.validate();
                snapshot("save-spreadsheet.png");
                checkComponents(chooser);
                chooser.approveSelection();
                check(chooser.getSelectedFile().equals(destination), "Chinese save path retained");
            });
            System.out.println("FileChooserFontTest: PASS");
        }
        finally {
            SwingUtilities.invokeAndWait(() -> { if (frame != null) frame.dispose(); });
        }
    }

    static void checkFont(Font font, String name)
    {
        check(font != null && font.canDisplayUpTo(CJK) == -1,
            name + " must render Chinese: " + font);
        check(font.deriveFont(Font.BOLD).canDisplayUpTo(CJK) == -1, name + " bold Chinese");
        check(font.deriveFont(Font.ITALIC).canDisplayUpTo(CJK) == -1, name + " italic Chinese");
    }

    static void checkComponents(Component component)
    {
        if (component instanceof JLabel || component instanceof AbstractButton ||
                component instanceof javax.swing.text.JTextComponent ||
                component instanceof JList || component instanceof JComboBox ||
                component instanceof JTable)
            checkFont(component.getFont(), component.getClass().getSimpleName());
        if (component instanceof Container)
            for (Component child : ((Container)component).getComponents())
                checkComponents(child);
    }

    static void snapshot(String name)
    {
        frame.validate();
        BufferedImage bitmap = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = bitmap.createGraphics();
        frame.paintAll(graphics);
        graphics.dispose();
        try { ImageIO.write(bitmap, "png", output.resolve(name).toFile()); }
        catch (Exception ex) { throw new RuntimeException(ex); }
    }

    static void check(boolean condition, String message)
    {
        if (!condition) throw new AssertionError(message);
    }
}
