package AngioTool;

import Algorithms.AnalyzeSkeleton2;
import Pixels.ArgbBuffer;
import Pixels.ImageFile;
import Utils.IntBufferPool;
import Utils.IntVector;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

public class OverlayDataExporter
{
    public static void export(
        String basePath,
        AnalyzerParameters params,
        Analyzer.Data data,
        int width,
        int height,
        int brightestChannel
    ) throws IOException
    {
        saveMaskImage(basePath + " vessel-mask.png", data.analysisImage.buf, width, height);
        saveOverlayJson(basePath + " overlay-data.json", params, data, width, height, brightestChannel);
    }

    private static void saveMaskImage(String absPath, byte[] analysisImage, int width, int height) throws IOException
    {
        int area = width * height;
        int[] pixels = IntBufferPool.acquireAsIs(area);
        ArgbBuffer maskImage = new ArgbBuffer(pixels, width, height);

        try {
            for (int i = 0; i < area; i++) {
                int v = (analysisImage[i] & 0xff) != 0 ? 255 : 0;
                pixels[i] = 0xff000000 | (v << 16) | (v << 8) | v;
            }

            ImageFile.saveImage(maskImage, "png", absPath);
        }
        finally {
            IntBufferPool.release(pixels);
        }
    }

    private static void saveOverlayJson(
        String absPath,
        AnalyzerParameters params,
        Analyzer.Data data,
        int width,
        int height,
        int brightestChannel
    ) throws IOException
    {
        Writer writer = new OutputStreamWriter(new FileOutputStream(new File(absPath)), "UTF-8");
        try {
            AnalyzeSkeleton2.Result skel = data.skelResult;

            writer.write("{\n");
            writeImageInfo(writer, width, height, brightestChannel);
            writer.write(",\n");
            writeParameters(writer, params);
            writer.write(",\n");
            writePointArray(writer, "convexHull", data.convexHull, 2, 2);
            writer.write(",\n");
            writePointArray(writer, "skeleton", skel.slabList, 3, 2);
            writer.write(",\n");
            writeIndexedPointArray(writer, "branchPoints", skel.isolatedJunctions, skel.junctionVoxels, 3, 2);
            writer.write(",\n");
            writePointArray(writer, "junctionVoxels", skel.junctionVoxels, 3, 2);
            writer.write(",\n");
            writeIndexedPointArray(writer, "removedJunctions", skel.removedJunctions, skel.junctionVoxels, 3, 2);
            writer.write("\n}\n");
        }
        finally {
            writer.close();
        }
    }

    private static void writeImageInfo(Writer writer, int width, int height, int brightestChannel) throws IOException
    {
        writer.write("  \"image\": {\n");
        writer.write("    \"width\": " + width + ",\n");
        writer.write("    \"height\": " + height + ",\n");
        writer.write("    \"brightestChannel\": \"" + channelName(brightestChannel) + "\"\n");
        writer.write("  }");
    }

    private static void writeParameters(Writer writer, AnalyzerParameters params) throws IOException
    {
        writer.write("  \"overlayParameters\": {\n");
        writer.write("    \"drawOutline\": " + params.shouldDrawOutline + ",\n");
        writer.write("    \"outlineColor\": \"" + params.outlineColor.toString() + "\",\n");
        writer.write("    \"outlineSize\": " + params.outlineSize + ",\n");
        writer.write("    \"drawSkeleton\": " + params.shouldDrawSkeleton + ",\n");
        writer.write("    \"skeletonColor\": \"" + params.skeletonColor.toString() + "\",\n");
        writer.write("    \"skeletonSize\": " + params.skeletonSize + ",\n");
        writer.write("    \"drawBranchPoints\": " + params.shouldDrawBranchPoints + ",\n");
        writer.write("    \"branchingPointsColor\": \"" + params.branchingPointsColor.toString() + "\",\n");
        writer.write("    \"branchingPointsSize\": " + params.branchingPointsSize + ",\n");
        writer.write("    \"drawConvexHull\": " + params.shouldDrawConvexHull + ",\n");
        writer.write("    \"convexHullColor\": \"" + params.convexHullColor.toString() + "\",\n");
        writer.write("    \"convexHullSize\": " + params.convexHullSize + ",\n");
        writer.write("    \"isolateBrightestChannelInOutput\": " + params.shouldIsolateBrightestChannelInOutput + ",\n");
        writer.write("    \"expandOutputToGrayScale\": " + params.shouldExpandOutputToGrayScale + "\n");
        writer.write("  }");
    }

    private static void writePointArray(Writer writer, String name, IntVector points, int pointSize, int indent) throws IOException
    {
        String pad = indent(indent);
        writer.write(pad + "\"" + name + "\": [");

        int count = points != null ? points.size / pointSize : 0;
        for (int i = 0; i < count; i++) {
            if (i > 0)
                writer.write(",");
            writer.write("\n" + pad + "  ");
            writePoint(writer, points.buf, i * pointSize, pointSize);
        }

        if (count > 0)
            writer.write("\n" + pad);
        writer.write("]");
    }

    private static void writeIndexedPointArray(
        Writer writer,
        String name,
        IntVector pointIndexes,
        IntVector pointPool,
        int pointSize,
        int indent
    ) throws IOException
    {
        String pad = indent(indent);
        writer.write(pad + "\"" + name + "\": [");

        int count = pointIndexes != null ? pointIndexes.size : 0;
        int written = 0;
        for (int i = 0; i < count; i++) {
            int off = pointIndexes.buf[i];
            if (pointPool == null || off < 0 || off + pointSize > pointPool.size)
                continue;

            if (written > 0)
                writer.write(",");
            writer.write("\n" + pad + "  ");
            writePoint(writer, pointPool.buf, off, pointSize);
            written++;
        }

        if (written > 0)
            writer.write("\n" + pad);
        writer.write("]");
    }

    private static void writePoint(Writer writer, int[] points, int off, int pointSize) throws IOException
    {
        writer.write("{");
        writer.write("\"x\": " + points[off] + ", \"y\": " + points[off + 1]);
        if (pointSize > 2)
            writer.write(", \"z\": " + points[off + 2]);
        writer.write("}");
    }

    private static String indent(int n)
    {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++)
            sb.append(' ');
        return sb.toString();
    }

    private static String channelName(int channel)
    {
        switch (channel) {
            case 0: return "red";
            case 1: return "green";
            case 2: return "blue";
            default: return "unknown";
        }
    }
}
