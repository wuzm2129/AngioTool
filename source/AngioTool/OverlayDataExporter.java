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
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

public class OverlayDataExporter
{
    public static void export(
        String basePath,
        AnalyzerParameters params,
        Analyzer.Data data,
        Analyzer.Stats stats,
        int width,
        int height,
        int brightestChannel
    ) throws IOException
    {
        saveMaskImage(basePath + " vessel-mask.png", data.analysisImage.buf, width, height);
        saveOverlayJson(basePath + " overlay-data.json", params, data, stats, width, height, brightestChannel);
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
        Analyzer.Stats stats,
        int width,
        int height,
        int brightestChannel
    ) throws IOException
    {
        Writer writer = new OutputStreamWriter(new FileOutputStream(new File(absPath)), "UTF-8");
        try {
            AnalyzeSkeleton2.Result skel = data.skelResult;

            writer.write("{\n");
            writeImageInfo(writer, stats, width, height, brightestChannel);
            writer.write(",\n");
            writeAnalysisParameters(writer, params);
            writer.write(",\n");
            writeParameters(writer, params);
            writer.write(",\n");
            writeMetrics(writer, params, data, stats);
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
            writer.write(",\n");
            writeSegmentArray(writer, "vesselSegments", skel, params, 2);
            writer.write("\n}\n");
        }
        finally {
            writer.close();
        }
    }

    private static void writeImageInfo(Writer writer, Analyzer.Stats stats, int width, int height, int brightestChannel) throws IOException
    {
        Date today = new Date();
        DateFormat dateFormatter = DateFormat.getDateInstance(2, Locale.US);
        DateFormat timeFormatter = DateFormat.getTimeInstance(2, Locale.US);

        writer.write("  \"image\": {\n");
        writer.write("    \"name\": ");
        writeJsonString(writer, stats != null ? stats.imageFileName : "null");
        writer.write(",\n");
        writer.write("    \"absolutePath\": ");
        writeJsonString(writer, stats != null ? stats.imageAbsolutePath : "null");
        writer.write(",\n");
        writer.write("    \"exportDate\": ");
        writeJsonString(writer, dateFormatter.format(today));
        writer.write(",\n");
        writer.write("    \"exportTime\": ");
        writeJsonString(writer, timeFormatter.format(today));
        writer.write(",\n");
        writer.write("    \"width\": " + width + ",\n");
        writer.write("    \"height\": " + height + ",\n");
        writer.write("    \"brightestChannel\": \"" + channelName(brightestChannel) + "\"\n");
        writer.write("  }");
    }

    private static void writeMetrics(Writer writer, AnalyzerParameters params, Analyzer.Data data, Analyzer.Stats stats) throws IOException
    {
        AnalyzeSkeleton2.Result skel = data.skelResult;
        double scale = params.shouldApplyLinearScale ? params.linearScalingFactor : 1.0;
        double areaScale = scale * scale;
        double totalLengthPixels = sum(skel.totalBranchLengths);
        double totalLength = totalLengthPixels * scale;
        int treeCount = skel.treeCount;

        writer.write("  \"metrics\": {\n");
        writer.write("    \"vesselPixelArea\": " + data.vesselPixelArea + ",\n");
        writer.write("    \"vesselArea\": " + (stats != null ? stats.vesselMMArea : (double)data.vesselPixelArea * areaScale) + ",\n");
        writer.write("    \"convexHullAreaPixels\": " + data.convexHullArea + ",\n");
        writer.write("    \"convexHullArea\": " + (stats != null ? stats.allantoisMMArea : data.convexHullArea * areaScale) + ",\n");
        writer.write("    \"explantArea\": " + (stats != null ? stats.allantoisMMArea : data.convexHullArea * areaScale) + ",\n");
        writer.write("    \"vesselsArea\": " + (stats != null ? stats.vesselMMArea : (double)data.vesselPixelArea * areaScale) + ",\n");
        writer.write("    \"vesselsPercentageArea\": " + (stats != null ? stats.vesselPercentageArea : ((double)data.vesselPixelArea * 100.0 / data.convexHullArea)) + ",\n");
        writer.write("    \"treeCount\": " + treeCount + ",\n");
        writer.write("    \"segmentCount\": " + skel.edgesLengths.size + ",\n");
        writer.write("    \"junctionCount\": " + skel.isolatedJunctions.size + ",\n");
        writer.write("    \"junctionDensity\": " + (stats != null ? stats.junctionsPerScaledArea : ((double)skel.isolatedJunctions.size / (data.convexHullArea * areaScale))) + ",\n");
        writer.write("    \"endPointCount\": " + (skel.endPoints.size / 3) + ",\n");
        writer.write("    \"totalLengthPixels\": " + totalLengthPixels + ",\n");
        writer.write("    \"totalLength\": " + (stats != null ? stats.totalLength : totalLength) + ",\n");
        writer.write("    \"totalVesselsLength\": " + (stats != null ? stats.totalLength : totalLength) + ",\n");
        writer.write("    \"averageVesselsLength\": " + (stats != null ? stats.averageBranchLength : (treeCount > 0 ? totalLength / (double)treeCount : 0.0)) + ",\n");
        writer.write("    \"averageSegmentLength\": " + (skel.edgesLengths.size > 0 ? totalLength / (double)skel.edgesLengths.size : 0.0) + ",\n");
        writer.write("    \"averageVesselDiameter\": " + (stats != null ? stats.averageVesselDiameter : data.averageVesselDiameter) + ",\n");
        writer.write("    \"medialELacunarity\": " + (stats != null ? stats.ELacunarityMedial : data.lacunarity.elMedial) + ",\n");
        writer.write("    \"meanELacunarity\": " + (stats != null ? stats.meanEl : data.lacunarity.elMean) + ",\n");
        writer.write("    \"eLacunarityGradient\": " + (stats != null ? stats.ELacunarityCurve : data.lacunarity.elCurve) + ",\n");
        writer.write("    \"medialFLacunarity\": " + (stats != null ? stats.FLacunarityMedial : data.lacunarity.flMedial) + ",\n");
        writer.write("    \"meanFLacunarity\": " + (stats != null ? stats.meanFl : data.lacunarity.flMean) + ",\n");
        writer.write("    \"fLacunarityGradient\": " + (stats != null ? stats.FLacunarityCurve : data.lacunarity.flCurve) + ",\n");
        writer.write("    \"averageSegmentDiameter\": " + computeAverageDiameter(skel, scale) + "\n");
        writer.write("  }");
    }

    private static void writeAnalysisParameters(Writer writer, AnalyzerParameters params) throws IOException
    {
        writer.write("  \"analysisParameters\": {\n");
        writer.write("    \"lowThreshold\": " + params.thresholdLow + ",\n");
        writer.write("    \"highThreshold\": " + params.thresholdHigh + ",\n");
        writer.write("    \"vesselThickness\": ");
        writeDoubleArray(writer, params.sigmas);
        writer.write(",\n");
        writer.write("    \"smallParticles\": " + params.removeSmallParticlesThreshold + ",\n");
        writer.write("    \"fillHoles\": " + params.fillHolesValue + ",\n");
        writer.write("    \"maxHoleLevel\": " + params.brightShapeThresholdFactor + ",\n");
        writer.write("    \"minBoxness\": " + params.minBoxness + ",\n");
        writer.write("    \"minAreaLengthRatio\": " + params.minAreaLengthRatio + ",\n");
        writer.write("    \"skeletonizer\": \"" + (params.shouldUseFastSkeletonizer ? "Fast" : "Thorough") + "\",\n");
        writer.write("    \"maxSkeletonSteps\": " + params.maxSkelIterations + ",\n");
        writer.write("    \"scalingFactor\": " + params.linearScalingFactor + ",\n");
        writer.write("    \"transformedColors\": " + params.shouldRemapColors + ",\n");
        writer.write("    \"hueTransformWeight\": " + params.hueTransformWeight + ",\n");
        writer.write("    \"brightnessWeight\": " + params.brightnessTransformWeight + ",\n");
        writer.write("    \"targetColor\": \"" + params.targetRemapColor.toString() + "\",\n");
        writer.write("    \"offColor\": \"" + params.voidRemapColor.toString() + "\",\n");
        writer.write("    \"saturationFactor\": " + params.saturationFactor + ",\n");
        writer.write("    \"brightnessGraph\": ");
        writeIntArray(writer, params.brightnessLineSegments);
        writer.write("\n");
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

    private static void writeSegmentArray(
        Writer writer,
        String name,
        AnalyzeSkeleton2.Result skel,
        AnalyzerParameters params,
        int indent
    ) throws IOException
    {
        String pad = indent(indent);
        writer.write(pad + "\"" + name + "\": [");

        int count = skel.edgesLengths != null ? skel.edgesLengths.size : 0;
        double scale = params.shouldApplyLinearScale ? params.linearScalingFactor : 1.0;
        boolean thicknessComputed = params.shouldComputeThickness && skel.edgesDiameters != null && skel.edgesDiameters.size >= count;
        for (int i = 0; i < count; i++) {
            if (i > 0)
                writer.write(",");

            double lengthPixels = skel.edgesLengths.buf[i];
            double diameterPixels = thicknessComputed ? skel.edgesDiameters.buf[i] : 0.0;
            double angleDegrees = computeSegmentAngleDegrees(skel, i);

            writer.write("\n" + pad + "  {");
            writer.write("\"id\": " + i);
            writer.write(", \"tree\": " + readIntOrZero(skel.edgesTrees, i));
            writer.write(", \"lengthPixels\": " + lengthPixels);
            writer.write(", \"length\": " + (lengthPixels * scale));
            writer.write(", \"angleDegrees\": ");
            writer.write(Double.isNaN(angleDegrees) ? "null" : Double.toString(angleDegrees));
            writer.write(", \"diameterPixels\": ");
            writer.write(thicknessComputed ? Double.toString(diameterPixels) : "null");
            writer.write(", \"diameter\": ");
            writer.write(thicknessComputed ? Double.toString(diameterPixels * scale) : "null");
            writer.write("}");
        }

        if (count > 0)
            writer.write("\n" + pad);
        writer.write("]");
    }

    private static double sum(Utils.DoubleVector values)
    {
        double result = 0.0;
        if (values != null) {
            for (int i = 0; i < values.size; i++)
                result += values.buf[i];
        }
        return result;
    }

    private static double computeAverageDiameter(AnalyzeSkeleton2.Result skel, double scale)
    {
        if (skel.edgesDiameters == null || skel.edgesDiameters.size == 0)
            return 0.0;

        double total = 0.0;
        for (int i = 0; i < skel.edgesDiameters.size; i++)
            total += skel.edgesDiameters.buf[i];
        return (total / (double)skel.edgesDiameters.size) * scale;
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

    private static void writeDoubleArray(Writer writer, double[] values) throws IOException
    {
        writer.write("[");
        if (values != null) {
            for (int i = 0; i < values.length; i++) {
                if (i > 0)
                    writer.write(", ");
                writer.write(Double.toString(values[i]));
            }
        }
        writer.write("]");
    }

    private static void writeIntArray(Writer writer, int[] values) throws IOException
    {
        writer.write("[");
        if (values != null) {
            for (int i = 0; i < values.length; i++) {
                if (i > 0)
                    writer.write(", ");
                writer.write(Integer.toString(values[i]));
            }
        }
        writer.write("]");
    }

    private static void writeJsonString(Writer writer, String value) throws IOException
    {
        writer.write("\"");
        if (value != null) {
            for (int i = 0; i < value.length(); i++) {
                char ch = value.charAt(i);
                switch (ch) {
                    case '\\': writer.write("\\\\"); break;
                    case '"': writer.write("\\\""); break;
                    case '\b': writer.write("\\b"); break;
                    case '\f': writer.write("\\f"); break;
                    case '\n': writer.write("\\n"); break;
                    case '\r': writer.write("\\r"); break;
                    case '\t': writer.write("\\t"); break;
                    default:
                        if (ch < 0x20) {
                            writer.write("\\u");
                            String hex = Integer.toHexString(ch);
                            for (int j = hex.length(); j < 4; j++)
                                writer.write("0");
                            writer.write(hex);
                        }
                        else {
                            writer.write(ch);
                        }
                        break;
                }
            }
        }
        writer.write("\"");
    }

    private static int readIntOrZero(IntVector values, int index)
    {
        return values != null && index >= 0 && index < values.size ? values.buf[index] : 0;
    }

    private static double computeSegmentAngleDegrees(AnalyzeSkeleton2.Result skel, int edgeIndex)
    {
        if (skel.edgesPoints == null || skel.slabList == null || edgeIndex < 0 || edgeIndex * 2 + 1 >= skel.edgesPoints.size)
            return Double.NaN;

        int x1 = segmentEndpointCoordinate(skel, edgeIndex, true, 0);
        int y1 = segmentEndpointCoordinate(skel, edgeIndex, true, 1);
        int x2 = segmentEndpointCoordinate(skel, edgeIndex, false, 0);
        int y2 = segmentEndpointCoordinate(skel, edgeIndex, false, 1);
        if (x1 < 0 || y1 < 0 || x2 < 0 || y2 < 0)
            return Double.NaN;

        double dx = (double)(x2 - x1);
        double dy = (double)(y2 - y1);
        if (dx == 0.0 && dy == 0.0)
            return Double.NaN;

        double angle = Math.toDegrees(Math.atan2(dy, dx));
        return angle < 0.0 ? angle + 360.0 : angle;
    }

    private static int segmentEndpointCoordinate(AnalyzeSkeleton2.Result skel, int edgeIndex, boolean start, int coord)
    {
        if (skel.edgesVerts == null || skel.edgesPoints == null || edgeIndex * 3 + 2 >= skel.edgesVerts.size)
            return -1;

        int modeSides = skel.edgesVerts.buf[edgeIndex * 3];
        int mode = start ? (modeSides >>> 2) : (modeSides & 3);
        int rawIndex = skel.edgesVerts.buf[edgeIndex * 3 + (start ? 1 : 2)];

        if (mode == AnalyzeSkeleton2.END_POINT) {
            int off = rawIndex;
            if (off >= 0 && off + coord < skel.endPoints.size)
                return skel.endPoints.buf[off + coord];
        }

        if (mode == AnalyzeSkeleton2.JUNCTION) {
            int off = rawIndex * 3;
            if (off >= 0 && off + coord < skel.singleJunctions.size)
                return skel.singleJunctions.buf[off + coord];
        }

        int slabStart = skel.edgesPoints.buf[edgeIndex * 2];
        int nSlabValues = skel.edgesPoints.buf[edgeIndex * 2 + 1];
        if (nSlabValues <= 0)
            return -1;

        int off = start ? slabStart : slabStart + nSlabValues - 3;
        if (off < 0 || off + coord >= skel.slabList.size)
            return -1;
        return skel.slabList.buf[off + coord];
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
