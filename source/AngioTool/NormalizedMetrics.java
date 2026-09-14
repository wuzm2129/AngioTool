package AngioTool;

public class NormalizedMetrics
{
    public static final String RULE_VERSION = "image-relative-v1";
    public static final String AREA_RULE = "value / analysisImageArea";
    public static final String LENGTH_RULE = "value / sqrt(analysisImageArea)";
    public static final String COUNT_RULE = "topological counts unchanged";

    public int analysisWidth;
    public int analysisHeight;
    public double analysisImageAreaPixels;
    public double lengthBasisPixels;
    public double measurementScale;
    public double areaBasisInMeasurementUnits;
    public double lengthBasisInMeasurementUnits;

    public Double convexHullAreaFractionOfImage;
    public Double vesselAreaFractionOfImage;
    public Double vesselsPercentageAreaOfHull;
    public double junctionCount;
    public Double junctionDensityTimesImageArea;
    public int endPointCount;
    public int segmentCount;
    public Double totalLengthOverSqrtImageArea;
    public Double averageVesselsLengthOverSqrtImageArea;
    public Double averageSegmentLengthOverSqrtImageArea;
    public Double averageVesselDiameterOverSqrtImageArea;
    public Double skeletonPixelsOverSqrtImageArea;
    public Double prunedSkeletonPixelsOverSqrtImageArea;
    public Double rawSkeletonPixelsOverSqrtImageArea;

    public static NormalizedMetrics from(AnalyzerParameters params, Analyzer.Stats stats)
    {
        NormalizedMetrics result = new NormalizedMetrics();
        result.analysisWidth = stats.imageWidth;
        result.analysisHeight = stats.imageHeight;
        result.analysisImageAreaPixels = (double)stats.imageWidth * (double)stats.imageHeight;
        result.lengthBasisPixels = Math.sqrt(result.analysisImageAreaPixels);
        result.measurementScale = params.shouldApplyLinearScale ? params.linearScalingFactor : 1.0;
        result.areaBasisInMeasurementUnits = result.analysisImageAreaPixels *
            result.measurementScale * result.measurementScale;
        result.lengthBasisInMeasurementUnits = result.lengthBasisPixels * result.measurementScale;

        result.convexHullAreaFractionOfImage = divideOrNull(
            stats.allantoisMMArea, result.areaBasisInMeasurementUnits);
        result.vesselAreaFractionOfImage = divideOrNull(
            stats.vesselMMArea, result.areaBasisInMeasurementUnits);
        result.vesselsPercentageAreaOfHull = stats.allantoisMMArea > 0.0 ?
            finiteOrNull(stats.vesselPercentageArea) : null;
        result.junctionCount = stats.totalNJunctions;
        result.junctionDensityTimesImageArea = stats.allantoisMMArea > 0.0 ?
            finiteOrNull(stats.junctionsPerScaledArea * result.areaBasisInMeasurementUnits) : null;
        result.endPointCount = stats.totalNEndPoints;
        result.segmentCount = stats.totalSegments;
        result.totalLengthOverSqrtImageArea = divideOrNull(
            stats.totalLength, result.lengthBasisInMeasurementUnits);
        result.averageVesselsLengthOverSqrtImageArea = divideOrNull(
            stats.averageBranchLength, result.lengthBasisInMeasurementUnits);
        result.averageSegmentLengthOverSqrtImageArea = divideOrNull(
            stats.averageSegmentLength, result.lengthBasisInMeasurementUnits);
        result.averageVesselDiameterOverSqrtImageArea = divideOrNull(
            stats.averageVesselDiameter, result.lengthBasisInMeasurementUnits);
        result.skeletonPixelsOverSqrtImageArea = divideOrNull(
            stats.skeletonPixelCount, result.lengthBasisPixels);
        result.prunedSkeletonPixelsOverSqrtImageArea = divideOrNull(
            stats.prunedSkeletonPixelCount, result.lengthBasisPixels);
        result.rawSkeletonPixelsOverSqrtImageArea = divideOrNull(
            stats.rawSkeletonPixelCount, result.lengthBasisPixels);
        return result;
    }

    private static Double divideOrNull(double numerator, double denominator)
    {
        return denominator > 0.0 && Double.isFinite(numerator) && Double.isFinite(denominator) ?
            Double.valueOf(numerator / denominator) : null;
    }

    private static Double finiteOrNull(double value)
    {
        return Double.isFinite(value) ? Double.valueOf(value) : null;
    }
}
