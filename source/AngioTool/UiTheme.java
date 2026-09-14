package AngioTool;

import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.util.UIScale;
import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public final class UiTheme
{
    public static final Color BACKGROUND = new Color(245, 247, 248);
    public static final Color TEXT = new Color(35, 42, 47);
    public static final Color MUTED = new Color(99, 111, 119);
    public static final Color ACCENT = new Color(0, 116, 126);
    public static final Color BORDER = new Color(220, 226, 229);
    public static final Color ERROR = new Color(172, 49, 49);

    private UiTheme() {}

    public static void install()
    {
        FlatLightLaf.setup();
        // Logical fonts retain platform glyph fallback for localized labels and paths.
        UIManager.put("defaultFont", new Font(Font.DIALOG, Font.PLAIN, 13).deriveFont(UIScale.scale(13f)));
        UIManager.put("Panel.background", BACKGROUND);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Component.focusColor", ACCENT);
        UIManager.put("Component.accentColor", ACCENT);
        UIManager.put("TabbedPane.underlineColor", ACCENT);
        UIManager.put("Button.arc", 8);
        UIManager.put("Component.arc", 6);
        UIManager.put("TextComponent.arc", 6);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("ScrollBar.width", 10);
    }

    public static JPanel column()
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    public static JPanel row(Component... items)
    {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 0));
        for (Component item : items)
            panel.add(item);
        return panel;
    }

    public static JPanel field(String title, JComponent control)
    {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        JLabel label = new JLabel(title);
        label.setLabelFor(control);
        panel.add(label, BorderLayout.NORTH);
        panel.add(control, BorderLayout.CENTER);
        return panel;
    }

    public static JPanel section(String title, JComponent... rows)
    {
        JPanel panel = new JPanel() {
            @Override public Dimension getMaximumSize()
            {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(16, 18, 12, 18));
        JLabel heading = new JLabel(title);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, UIScale.scale(14f)));
        Dimension headingSize = heading.getPreferredSize();
        heading.setPreferredSize(new Dimension(headingSize.width + 12, headingSize.height + 2));
        heading.setMaximumSize(new Dimension(Integer.MAX_VALUE, headingSize.height + 2));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(heading);
        panel.add(Box.createVerticalStrut(12));
        for (JComponent row : rows) {
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
            panel.add(row);
            panel.add(Box.createVerticalStrut(8));
        }
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    public static JPanel disclosure(String title, JComponent... rows)
    {
        JPanel panel = new JPanel() {
            @Override public Dimension getMaximumSize()
            {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JToggleButton toggle = new JToggleButton(title);
        toggle.setIcon(new com.formdev.flatlaf.icons.FlatTreeCollapsedIcon());
        toggle.setBorderPainted(false);
        toggle.setContentAreaFilled(false);
        toggle.setMargin(new Insets(4, 0, 4, 0));
        toggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel content = column();
        content.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (JComponent row : rows) {
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
            content.add(row);
            content.add(Box.createVerticalStrut(8));
        }
        content.setVisible(false);
        toggle.addActionListener(e -> {
            content.setVisible(toggle.isSelected());
            toggle.setIcon(toggle.isSelected() ?
                new com.formdev.flatlaf.icons.FlatTreeExpandedIcon() :
                new com.formdev.flatlaf.icons.FlatTreeCollapsedIcon());
            panel.revalidate();
        });
        panel.add(toggle);
        panel.add(content);
        return panel;
    }

    public static JScrollPane scrollColumn(JComponent... sections)
    {
        JPanel body = new ScrollableColumn();
        for (JComponent section : sections)
            body.add(section);
        body.add(Box.createVerticalGlue());
        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        return scroll;
    }

    private static final class ScrollableColumn extends JPanel implements Scrollable
    {
        ScrollableColumn() { setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); }
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle visible, int axis, int direction) { return 20; }
        public int getScrollableBlockIncrement(Rectangle visible, int axis, int direction) { return visible.height - 20; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight()
        {
            return getParent() != null && getPreferredSize().height < getParent().getHeight();
        }
    }

    public static void primary(JButton button)
    {
        button.setBackground(ACCENT);
        button.setForeground(Color.WHITE);
        button.setFont(button.getFont().deriveFont(Font.BOLD));
    }

    public static void tool(JButton button, String tooltip)
    {
        button.setToolTipText(tooltip);
        button.getAccessibleContext().setAccessibleName(tooltip);
        button.setMargin(new Insets(2, 4, 2, 4));
        button.setPreferredSize(size(36, 32));
        button.setMinimumSize(size(36, 32));
    }

    public static Dimension size(int width, int height)
    {
        return UIScale.scale(new Dimension(width, height));
    }

    public static float fontSize(float size) { return UIScale.scale(size); }

    public static void place(Window window, Window owner, int width, int height)
    {
        GraphicsConfiguration config = owner != null ?
            owner.getGraphicsConfiguration() : window.getGraphicsConfiguration();
        Rectangle bounds = config.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(config);
        int availableWidth = bounds.width - insets.left - insets.right;
        int availableHeight = bounds.height - insets.top - insets.bottom;
        window.setSize(Math.min(UIScale.scale(width), availableWidth),
            Math.min(UIScale.scale(height), availableHeight));
        window.setLocationRelativeTo(owner);
        window.setLocation(
            Math.max(bounds.x + insets.left, Math.min(window.getX(),
                bounds.x + bounds.width - insets.right - window.getWidth())),
            Math.max(bounds.y + insets.top, Math.min(window.getY(),
                bounds.y + bounds.height - insets.bottom - window.getHeight()))
        );
    }
}
