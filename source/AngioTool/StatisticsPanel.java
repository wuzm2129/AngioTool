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
    final JTable table = new JTable(model) {
        @Override public String getToolTipText(java.awt.event.MouseEvent event)
        {
            int row = rowAtPoint(event.getPoint());
            int column = columnAtPoint(event.getPoint());
            return row >= 0 && column >= 0 ? String.valueOf(getValueAt(row, column)) : null;
        }
    };

    public StatisticsPanel()
    {
        super(new BorderLayout(0, 12));
        setBorder(new EmptyBorder(16, 14, 12, 14));
        JLabel title = new JLabel("Measurements");
        title.setFont(title.getFont().deriveFont(Font.BOLD, UiTheme.fontSize(16f)));
        JPanel heading = new JPanel(new BorderLayout(0, 8));
        heading.add(title, BorderLayout.NORTH);
        status.setForeground(UiTheme.MUTED);
        heading.add(status, BorderLayout.SOUTH);
        add(heading, BorderLayout.NORTH);
        table.setRowHeight(UiTheme.size(0, 30).height);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(UiTheme.size(205, 0).width);
        table.getColumnModel().getColumn(1).setPreferredWidth(UiTheme.size(110, 0).width);
        DefaultTableCellRenderer values = new DefaultTableCellRenderer();
        values.setHorizontalAlignment(SwingConstants.RIGHT);
        table.getColumnModel().getColumn(1).setCellRenderer(values);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);
        setPreferredSize(UiTheme.size(350, 450));
        setMinimumSize(UiTheme.size(280, 100));
    }

    public void showStatus(String message, boolean error)
    {
        status.setText(message);
        status.setToolTipText(message);
        status.setForeground(error ? UiTheme.ERROR : UiTheme.MUTED);
        model.setRowCount(0);
    }

    private void metric(String name, double value)
    {
        model.addRow(new Object[] {name, formatValue(value)});
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
        String lengthUnit = params.shouldApplyLinearScale ? "scaled" : "px";
        String areaUnit = params.shouldApplyLinearScale ? "scaled area" : "px\u00b2";
        model.addRow(new Object[] {"Image size", stats.imageWidth + " x " + stats.imageHeight});
        metric("Convex hull (" + areaUnit + ")", stats.allantoisMMArea);
        metric("Vessel area (" + areaUnit + ")", stats.vesselMMArea);
        metric("Vessel area (%)", stats.vesselPercentageArea);
        metric("Junctions", stats.totalNJunctions);
        metric("Junctions / area", stats.junctionsPerScaledArea);
        metric("Total length (" + lengthUnit + ")", stats.totalLength);
        metric("Mean tree length", stats.averageBranchLength);
        metric("Mean segment length", stats.averageSegmentLength);
        metric("Segments", stats.totalSegments);
        metric("Endpoints", stats.totalNEndPoints);
        if (params.shouldComputeThickness)
            metric("Median diameter (" + lengthUnit + ")", stats.averageVesselDiameter);
        model.addRow(new Object[] {"Skeleton convergence", stats.skeletonConverged ? "Converged" : "Step limit reached"});
        model.addRow(new Object[] {"Pruning status", stats.pruningStatus});
        metric("Raw skeleton pixels", stats.rawSkeletonPixelCount);
        metric("Pruned pixels", stats.prunedSkeletonPixelCount);
        metric("Skeleton pixels", stats.skeletonPixelCount);
        metric("Isolated skeleton pixels", stats.isolatedSkeletonPixelCount);
        if (stats.fractalBoxCount != null && stats.fractalBoxCount.valid) {
            metric("Fractal dimension", stats.fractalBoxCount.dimension);
            metric("Fractal fit R\u00b2", stats.fractalBoxCount.rSquared);
        }
        else {
            String reason = stats.fractalBoxCount != null && stats.fractalBoxCount.invalidReason != null &&
                !stats.fractalBoxCount.invalidReason.isEmpty() ?
                stats.fractalBoxCount.invalidReason : "not-computed";
            model.addRow(new Object[] {"Fractal dimension", "N/A (" + reason + ")"});
            model.addRow(new Object[] {"Fractal fit R\u00b2", "N/A (" + reason + ")"});
        }
        if (params.shouldComputeLacunarity) {
            metric("E lacunarity: medial", stats.ELacunarityMedial);
            metric("E lacunarity: mean", stats.meanEl);
            metric("E lacunarity: gradient", stats.ELacunarityCurve);
            metric("F lacunarity: medial", stats.FLacunarityMedial);
            metric("F lacunarity: mean", stats.meanFl);
            metric("F lacunarity: gradient", stats.FLacunarityCurve);
        }
    }
}
