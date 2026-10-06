package AngioTool;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

public final class StatisticsPanel extends JPanel
{
    final JLabel status = new JLabel("Analyzing...");
    final DefaultTableModel model = new DefaultTableModel(new String[] {"Measurement", "Value"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final MetricCard area = new MetricCard("Image & area");
    private final MetricCard network = new MetricCard("Network topology");
    private final MetricCard length = new MetricCard("Length & diameter");
    private final MetricCard skeleton = new MetricCard("Skeleton quality");
    private final MetricCard fractal = new MetricCard("Fractal dimension");
    private final MetricCard lacunarity = new MetricCard("Lacunarity", "Statistic", "E", "F");

    public StatisticsPanel()
    {
        super(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(6, 8, 6, 8));
        JLabel title = new JLabel("Measurements");
        title.setFont(title.getFont().deriveFont(Font.BOLD, UiTheme.fontSize(16f)));
        JPanel heading = new JPanel(new BorderLayout(0, 4));
        heading.add(title, BorderLayout.NORTH);
        status.setForeground(UiTheme.MUTED);
        status.setOpaque(true);
        status.setBackground(UiTheme.BACKGROUND);
        status.setBorder(new EmptyBorder(3, 8, 3, 8));
        heading.add(status, BorderLayout.SOUTH);
        add(heading, BorderLayout.NORTH);
        MetricGrid grid = new MetricGrid();
        JScrollPane scroll = new JScrollPane(grid);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(UiTheme.size(0, 24).height);
        add(scroll, BorderLayout.CENTER);
        setPreferredSize(UiTheme.size(350, 450));
        setMinimumSize(UiTheme.size(280, 100));
    }

    public void showStatus(String message, boolean error)
    {
        status.setText(message);
        status.setToolTipText(message);
        status.setForeground(error ? UiTheme.ERROR : UiTheme.MUTED);
        status.setBackground(error ? new Color(253, 237, 237) : UiTheme.BACKGROUND);
        model.setRowCount(0);
        for (MetricCard card : new MetricCard[] {area, network, length, skeleton, fractal, lacunarity})
            card.values.setRowCount(0);
    }

    private void metric(MetricCard card, String name, double value)
    {
        row(card, name, formatValue(value));
    }

    private void row(MetricCard card, String name, String value)
    {
        model.addRow(new Object[] {name, value});
        card.values.addRow(new Object[] {name, value});
    }

    static String formatValue(double value)
    {
        double magnitude = Math.abs(value);
        String pattern = magnitude > 0 && (magnitude < 0.001 || magnitude >= 1.0e9) ?
            "0.####E0" : "0.####";
        java.text.DecimalFormat format = new java.text.DecimalFormat(pattern,
            java.text.DecimalFormatSymbols.getInstance(java.util.Locale.ROOT));
        return format.format(value);
    }

    public void showResults(AnalyzerParameters params, Analyzer.Stats stats)
    {
        showStatus(stats.valid ? "Analysis complete" : "Invalid: " + stats.invalidReason, !stats.valid);
        if (stats.valid)
            status.setForeground(UiTheme.MUTED);
        String lengthUnit = params.shouldApplyLinearScale ? "scaled" : "px";
        String areaUnit = params.shouldApplyLinearScale ? "scaled area" : "px\u00b2";
        row(area, "Image size", stats.imageWidth + " x " + stats.imageHeight);
        metric(area, "Convex hull (" + areaUnit + ")", stats.allantoisMMArea);
        metric(area, "Vessel area (" + areaUnit + ")", stats.vesselMMArea);
        metric(area, "Vessel area (%)", stats.vesselPercentageArea);
        metric(network, "Junctions", stats.totalNJunctions);
        metric(network, "Junctions / area", stats.junctionsPerScaledArea);
        metric(length, "Total length (" + lengthUnit + ")", stats.totalLength);
        metric(length, "Mean tree length", stats.averageBranchLength);
        metric(length, "Mean segment length", stats.averageSegmentLength);
        metric(network, "Segments", stats.totalSegments);
        metric(network, "Endpoints", stats.totalNEndPoints);
        if (params.shouldComputeThickness)
            metric(length, "Median diameter (" + lengthUnit + ")", stats.averageVesselDiameter);
        row(skeleton, "Skeleton convergence", stats.skeletonConverged ? "Converged" : "Step limit reached");
        row(skeleton, "Pruning status", stats.pruningStatus);
        metric(skeleton, "Raw skeleton pixels", stats.rawSkeletonPixelCount);
        metric(skeleton, "Pruned pixels", stats.prunedSkeletonPixelCount);
        metric(skeleton, "Skeleton pixels", stats.skeletonPixelCount);
        metric(skeleton, "Isolated skeleton pixels", stats.isolatedSkeletonPixelCount);
        if (stats.fractalBoxCount != null && stats.fractalBoxCount.valid) {
            metric(fractal, "Fractal dimension", stats.fractalBoxCount.dimension);
            metric(fractal, "Fractal fit R\u00b2", stats.fractalBoxCount.rSquared);
        }
        else {
            String reason = stats.fractalBoxCount != null && stats.fractalBoxCount.invalidReason != null &&
                !stats.fractalBoxCount.invalidReason.isEmpty() ?
                stats.fractalBoxCount.invalidReason : "not-computed";
            row(fractal, "Fractal dimension", "N/A (" + reason + ")");
            row(fractal, "Fractal fit R\u00b2", "N/A (" + reason + ")");
        }
        if (params.shouldComputeLacunarity) {
            lacunarity("medial", stats.ELacunarityMedial, stats.FLacunarityMedial);
            lacunarity("mean", stats.meanEl, stats.meanFl);
            lacunarity("gradient", stats.ELacunarityCurve, stats.FLacunarityCurve);
        }
        lacunarity.setVisible(params.shouldComputeLacunarity);
        revalidate();
        repaint();
    }

    private void lacunarity(String name, double e, double f)
    {
        model.addRow(new Object[] {"E lacunarity: " + name, formatValue(e)});
        model.addRow(new Object[] {"F lacunarity: " + name, formatValue(f)});
        lacunarity.values.addRow(new Object[] {name, formatValue(e), formatValue(f)});
    }

    private static final class MetricCard extends JPanel
    {
        final DefaultTableModel values;
        final JTable table;
        final JLabel heading;
        final JPanel header;

        MetricCard(String title, String... columns)
        {
            super(new BorderLayout(0, 4));
            setOpaque(false);
            setBorder(new EmptyBorder(4, 6, 2, 6));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            heading = new JLabel(title);
            heading.setFont(heading.getFont().deriveFont(Font.BOLD));
            heading.setForeground(UiTheme.TEXT);
            if (columns.length == 0)
                columns = new String[] {"Measurement", "Value"};
            values = new DefaultTableModel(columns, 0) {
                @Override public boolean isCellEditable(int row, int column) { return false; }
            };
            table = new JTable(values) {
                @Override public String getToolTipText(java.awt.event.MouseEvent event)
                {
                    int row = rowAtPoint(event.getPoint());
                    int column = columnAtPoint(event.getPoint());
                    return row >= 0 && column >= 0 ? String.valueOf(getValueAt(row, column)) : null;
                }
            };
            table.setRowHeight(Math.max(UiTheme.size(0, 22).height,
                table.getFontMetrics(table.getFont()).getHeight() + UiTheme.size(0, 4).height));
            table.setShowGrid(false);
            table.setIntercellSpacing(new Dimension(0, 0));
            table.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
                @Override public Component getTableCellRendererComponent(JTable source, Object value,
                    boolean selected, boolean focused, int row, int column)
                {
                    super.getTableCellRendererComponent(source, value, selected, focused, row, column);
                    if (!selected)
                        setForeground(UiTheme.MUTED);
                    return this;
                }
            });
            table.getColumnModel().getColumn(0).setPreferredWidth(UiTheme.size(columns.length == 2 ? 170 : 140, 0).width);
            for (int i = 1; i < columns.length; i++) {
                DefaultTableCellRenderer value = new DefaultTableCellRenderer() {
                    @Override public Component getTableCellRendererComponent(JTable source, Object value,
                        boolean selected, boolean focused, int row, int column)
                    {
                        super.getTableCellRendererComponent(source, value, selected, focused, row, column);
                        setFont(source.getFont().deriveFont(Font.BOLD));
                        if (!selected)
                            setForeground(UiTheme.TEXT);
                        return this;
                    }
                };
                value.setHorizontalAlignment(SwingConstants.RIGHT);
                table.getColumnModel().getColumn(i).setCellRenderer(value);
                table.getColumnModel().getColumn(i).setPreferredWidth(UiTheme.size(88, 0).width);
            }
            header = new JPanel(new BorderLayout(0, 4));
            header.setOpaque(false);
            header.add(heading, BorderLayout.NORTH);
            if (columns.length > 2) {
                table.getTableHeader().setReorderingAllowed(false);
                table.getTableHeader().setBackground(UiTheme.BACKGROUND);
                table.getTableHeader().setForeground(UiTheme.MUTED);
                header.add(table.getTableHeader(), BorderLayout.SOUTH);
            }
            add(header, BorderLayout.NORTH);
            add(table, BorderLayout.CENTER);
        }

        @Override public Dimension getMaximumSize()
        {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

    }

    private final class MetricGrid extends UiTheme.Surface implements Scrollable
    {
        private final MetricCard[] cards = {area, length, network, skeleton, fractal, lacunarity};

        MetricGrid()
        {
            super(null);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(new EmptyBorder(2, 6, 2, 6));
            for (MetricCard card : cards) {
                add(card);
                add(Box.createVerticalStrut(1));
            }
            add(Box.createVerticalGlue());
        }

        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle visible, int axis, int direction) { return UiTheme.size(0, 24).height; }
        public int getScrollableBlockIncrement(Rectangle visible, int axis, int direction) { return Math.max(24, visible.height - 24); }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight()
        {
            return getParent() != null && getPreferredSize().height <= getParent().getHeight();
        }
    }
}
