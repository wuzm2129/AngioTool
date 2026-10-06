package AngioTool;

import Utils.Misc;
import java.awt.event.*;
import javax.swing.*;

public class NumberEntry
{
    public JCheckBox cb;
    public JLabel units;
    public JTextField tf;
    public final JSpinner spinner;

    public String name;
    public final double originalValue;

    public NumberEntry(String name, boolean enabled, double value, String unitsStr)
    {
        this.name = name;
        this.originalValue = value;

        cb = new JCheckBox();
        cb.addActionListener((ActionEvent e) -> toggleCheckbox());
        cb.setText(name);

        units = new JLabel(unitsStr, SwingConstants.RIGHT);

        spinner = new JSpinner(new SpinnerNumberModel(value, null, null, 1.0));
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinner, "0.########");
        editor.getFormat().setDecimalFormatSymbols(
            java.text.DecimalFormatSymbols.getInstance(java.util.Locale.ROOT));
        editor.getFormat().setMaximumFractionDigits(340);
        spinner.setEditor(editor);
        tf = editor.getTextField();
        spinner.setEnabled(enabled);
        tf.getAccessibleContext().setAccessibleName(name);
        spinner.getAccessibleContext().setAccessibleName(name);

        cb.setSelected(enabled);
    }

    public void update(boolean enabled, double value)
    {
        spinner.setValue(value);
        spinner.setEnabled(enabled);
        cb.setSelected(enabled);
    }

    public double getValue()
    {
        String str = tf.getText();
        try {
            double value = Double.parseDouble(str);
            if (Double.isNaN(value) || Double.isInfinite(value))
                throw new NumberFormatException();
            return value;
        }
        catch (Exception ex) {
            throw new IllegalArgumentException(name + " Enter a valid number.");
        }
    }

    public GroupLayout.Group addToSeqGroup(GroupLayout.Group seqGroup)
    {
        return seqGroup
            .addComponent(cb)
            .addGap(0, 0, Short.MAX_VALUE)
            .addComponent(units)
            .addGap(4)
            .addComponent(tf, 64, 64, 64);
    }

    public GroupLayout.Group addToParaGroup(GroupLayout.Group paraGroup)
    {
        final int MIN_TEXT_HEIGHT = 18;
        final int TEXT_HEIGHT = 24;
        return paraGroup
            .addComponent(cb, MIN_TEXT_HEIGHT, TEXT_HEIGHT, TEXT_HEIGHT)
            .addComponent(units)
            .addComponent(tf, MIN_TEXT_HEIGHT, TEXT_HEIGHT, TEXT_HEIGHT);
    }

    public void toggleCheckbox()
    {
        boolean enabled = cb.isSelected();
        spinner.setEnabled(enabled);
    }

    public JPanel buildRow()
    {
        JPanel panel = new JPanel(new java.awt.BorderLayout(10, 0));
        JPanel input = new JPanel(new java.awt.BorderLayout(6, 0));
        spinner.setPreferredSize(UiTheme.size(88, 28));
        units.setPreferredSize(new java.awt.Dimension(Math.max(UiTheme.size(16, 0).width,
            units.getPreferredSize().width), UiTheme.size(0, 24).height));
        units.setForeground(UiTheme.MUTED);
        input.setPreferredSize(UiTheme.size(110, 28));
        input.add(spinner, java.awt.BorderLayout.CENTER);
        input.add(units, "1 :".equals(units.getText()) ?
            java.awt.BorderLayout.WEST : java.awt.BorderLayout.EAST);
        panel.add(cb, java.awt.BorderLayout.CENTER);
        panel.add(input, java.awt.BorderLayout.EAST);
        return panel;
    }
}
