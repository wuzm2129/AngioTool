package AngioTool;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Ensures the stable schema inventory cannot omit newly added analysis fields. */
public class CliConfigTest
{
    public static void main(String[] args) throws Exception
    {
        int count = 0;
        for (Field field : AnalyzerParameters.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            if (!CliConfig.PARAMETERS.containsKey(field.getName())) throw new AssertionError("Missing schema field " + field.getName());
            count++;
        }
        if (count != CliConfig.PARAMETERS.size()) throw new AssertionError("Schema inventory mismatch");
        Path file = Files.createTempFile("angiotool-config-", ".json");
        try {
            AnalyzerParameters p = AnalyzerParameters.defaults();
            Files.write(file, CliConfig.json(p).getBytes(StandardCharsets.UTF_8));
            if (!p.equals(CliConfig.load(file).parameters)) throw new AssertionError("All-field round trip");
            if (!CliConfig.errors(p).isEmpty()) throw new AssertionError("Defaults invalid");
            for (CliConfig.Parameter param : CliConfig.PARAMETERS.values()) {
                Object value = param.get(p);
                if (value instanceof Boolean) param.set(p, !(Boolean)value);
                else if (value instanceof Double) param.set(p, (Double)value + 0.5);
                else if (value instanceof Integer) param.set(p, (Integer)value + 1);
                else if (value instanceof Pixels.Rgb) param.set(p, new Pixels.Rgb("#123456"));
                else if (value instanceof double[]) param.set(p, new double[] {3, 5, 7});
                else if (value instanceof int[]) param.set(p, new int[] {0, 10, 50, 60, 100, 100});
            }
            Files.write(file, CliConfig.json(p).getBytes(StandardCharsets.UTF_8));
            if (!p.equals(CliConfig.load(file).parameters)) throw new AssertionError("Nondefault all-field round trip");
            p = AnalyzerParameters.defaults();
            p.shouldUseBinaryMask = true;
            p.thresholdLow = -42;
            p.sigmas = new double[] {-1};
            Files.write(file, CliConfig.json(p).getBytes(StandardCharsets.UTF_8));
            if (!p.equals(CliConfig.load(file).parameters) || !CliConfig.errors(p).isEmpty())
                throw new AssertionError("Dormant parameters lost or wrongly activated");
            System.out.println("PASS stable schema inventory and all-field/dormant round trips: " + count);
        }
        finally { Files.delete(file); }
    }
}
