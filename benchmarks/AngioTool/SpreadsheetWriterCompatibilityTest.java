package AngioTool;

import Xlsx.SpreadsheetWriter;
import Xlsx.XlsxReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class SpreadsheetWriterCompatibilityTest
{
    public static void main(String[] args) throws Exception
    {
        Path folder = args.length > 0 ? Files.createDirectories(Paths.get(args[0]).toAbsolutePath()) :
            Files.createTempDirectory("angiotool-xlsx-兼容性-");
        File output = folder.resolve("中文结果.xlsx").toFile();
        SpreadsheetWriter writer = new SpreadsheetWriter(output);
        writer.shouldSaveAfterEveryRow = false;
        writer.writeRow("名称", "数值");
        writer.writeRow("图像<&>\"路径\"", Double.NaN);
        writer.save();

        ArrayList<XlsxReader.SheetCells> sheets = XlsxReader.loadXlsxFromFile(output.getAbsolutePath());
        if (sheets == null || sheets.isEmpty())
            throw new AssertionError("Generated workbook could not be read");

        ZipFile zip = new ZipFile(output);
        try {
            String sharedStrings = readUtf8(zip, "xl/sharedStrings.xml");
            String worksheet = readUtf8(zip, "xl/worksheets/sheet1.xml");
            if (!sharedStrings.contains("名称") || !sharedStrings.contains("图像&lt;&amp;&gt;&quot;路径&quot;"))
                throw new AssertionError("UTF-8 or XML escaping was not preserved");
            if (!worksheet.contains("<dimension ref=\"A1:B2\"/>"))
                throw new AssertionError("Worksheet dimension does not match actual rows");
            if (worksheet.contains("NaN") || worksheet.contains("Infinity"))
                throw new AssertionError("Non-finite values must be blank cells");
        }
        finally {
            zip.close();
        }
        System.out.println("SpreadsheetWriterCompatibilityTest: PASS; artifact: " + output);
    }

    private static String readUtf8(ZipFile zip, String name) throws Exception
    {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null)
            throw new AssertionError("Missing workbook entry: " + name);
        InputStream input = zip.getInputStream(entry);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0)
                output.write(buffer, 0, read);
        }
        finally {
            input.close();
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
}
