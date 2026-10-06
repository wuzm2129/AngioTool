package AngioTool;

import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.util.UIScale;
import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public final class UiTheme
{
    public static final Color BACKGROUND = new Color(245, 246, 247);
    public static final Color TEXT = new Color(43, 49, 56);
    public static final Color MUTED = new Color(107, 116, 125);
    public static final Color ACCENT = new Color(70, 99, 121);
    public static final Color ACCENT_SOFT = new Color(232, 238, 242);
    public static final Color BORDER = new Color(223, 228, 232);
    public static final Color CANVAS = new Color(236, 239, 242);
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
        UIManager.put("Component.borderColor", BORDER);
        UIManager.put("Component.focusedBorderColor", ACCENT);
        UIManager.put("Button.background", Color.WHITE);
        UIManager.put("Button.hoverBackground", BACKGROUND);
        UIManager.put("Button.focusedBackground", ACCENT_SOFT);
        UIManager.put("ToggleButton.background", Color.WHITE);
        UIManager.put("ToggleButton.selectedBackground", ACCENT_SOFT);
        UIManager.put("ToggleButton.selectedForeground", TEXT);
        UIManager.put("ToggleButton.hoverBackground", BACKGROUND);
        UIManager.put("CheckBox.icon.checkmarkColor", TEXT);
        UIManager.put("CheckBox.icon.selectedBorderColor", MUTED);
        UIManager.put("CheckBox.icon.focusedBorderColor", ACCENT);
        UIManager.put("CheckBox.icon.hoverBorderColor", ACCENT);
        UIManager.put("CheckBox.icon.focusedBackground", ACCENT_SOFT);
        UIManager.put("CheckBox.icon.background", Color.WHITE);
        UIManager.put("CheckBox.icon.selectedBackground", Color.WHITE);
        UIManager.put("TabbedPane.underlineColor", ACCENT);
        UIManager.put("TabbedPane.selectedBackground", Color.WHITE);
        UIManager.put("TabbedPane.selectedForeground", TEXT);
        UIManager.put("TabbedPane.hoverColor", ACCENT_SOFT);
        UIManager.put("Table.selectionBackground", ACCENT_SOFT);
        UIManager.put("Table.selectionForeground", TEXT);
        UIManager.put("Button.arc", 8);
        UIManager.put("Component.arc", 8);
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
        panel.setOpaque(false);
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
        return section(title, false, rows);
    }

    public static JPanel compactSection(String title, JComponent... rows)
    {
        return section(title, true, rows);
    }

    public static JPanel inlineField(String title, JComponent control)
    {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        JLabel label = new JLabel(title);
        label.setLabelFor(control);
        panel.add(label, BorderLayout.WEST);
        panel.add(control, BorderLayout.CENTER);
        return panel;
    }

    private static JPanel section(String title, boolean compact, JComponent... rows)
    {
        JPanel panel = new Surface(null, compact) {
            @Override public Dimension getMaximumSize()
            {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(compact ? new EmptyBorder(8, 12, 6, 12) : new EmptyBorder(16, 18, 12, 18));
        JLabel heading = new JLabel(title);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, UIScale.scale(14f)));
        if (compact)
            heading.setForeground(TEXT);
        Dimension headingSize = heading.getPreferredSize();
        heading.setPreferredSize(new Dimension(headingSize.width + 12, headingSize.height + 2));
        heading.setMaximumSize(new Dimension(Integer.MAX_VALUE, headingSize.height + 2));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(heading);
        panel.add(Box.createVerticalStrut(compact ? 6 : 12));
        for (JComponent row : rows) {
            if (compact)
                transparentPanels(row);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
            panel.add(row);
            panel.add(Box.createVerticalStrut(compact ? 3 : 8));
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
        toggle.putClientProperty("AngioTool.presentationOnly", Boolean.TRUE);
        toggle.setIcon(new com.formdev.flatlaf.icons.FlatTreeCollapsedIcon());
        toggle.setBorderPainted(false);
        toggle.setContentAreaFilled(false);
        toggle.setMargin(new Insets(2, 0, 2, 0));
        toggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel content = column();
        content.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (JComponent row : rows) {
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
            content.add(row);
            content.add(Box.createVerticalStrut(4));
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
        return scrollColumn(false, sections);
    }

    public static JScrollPane compactScrollColumn(JComponent... sections)
    {
        return scrollColumn(true, sections);
    }

    private static JScrollPane scrollColumn(boolean compact, JComponent... sections)
    {
        JPanel body = new ScrollableColumn();
        if (compact)
            body.setBorder(new EmptyBorder(6, 8, 6, 8));
        for (JComponent section : sections) {
            body.add(section);
            if (compact)
                body.add(Box.createVerticalStrut(6));
        }
        body.add(Box.createVerticalGlue());
        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        return scroll;
    }

    private static void transparentPanels(Component component)
    {
        if (component instanceof JPanel && !(component instanceof RoundedPanel))
            ((JPanel)component).setOpaque(false);
        if (component instanceof Container)
            for (Component child : ((Container)component).getComponents())
                transparentPanels(child);
    }

    public static class Surface extends JPanel
    {
        private final boolean decorated;

        public Surface(LayoutManager layout) { this(layout, true); }

        private Surface(LayoutManager layout, boolean decorated)
        {
            super(layout);
            this.decorated = decorated;
            setOpaque(!decorated);
        }

        @Override protected void paintComponent(Graphics graphics)
        {
            super.paintComponent(graphics);
            if (!decorated)
                return;
            Graphics2D g = (Graphics2D)graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            int arc = UiTheme.size(12, 0).width;
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g.setColor(BORDER);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g.dispose();
        }
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
        java.util.Map<String, Object> style = new java.util.HashMap<>();
        style.put("hoverBackground", new Color(60, 86, 106));
        style.put("pressedBackground", new Color(51, 73, 91));
        style.put("focusedBackground", ACCENT);
        button.putClientProperty("FlatLaf.style", style);
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
