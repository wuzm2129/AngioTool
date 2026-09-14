package benchmarks.AngioTool;

import Algorithms.AnalyzeSkeleton2;
import Algorithms.Lacunarity2;
import Algorithms.Tubeness;
import Algorithms.Zha84;
import Utils.ISliceRunner;

public class AnalysisRegressionTest
{
    public static void main(String[] args)
    {
        testEmptyLacunarity();
        testSmallLacunarity();
        testNarrowTubeness();
        testStraightSkeleton();
        testBranchingSkeleton();
        System.out.println("AnalysisRegressionTest: PASS");
    }

    private static void testNarrowTubeness()
    {
        byte[] output = new byte[1];
        float[] image = new float[] {255.0f};
        ISliceRunner runner = new SingleThreadSliceRunner();
        try {
            Tubeness.computeTubenessImage(runner, 1, output, image, 1, 1, 0, new double[] {1.0}, 1);
        }
        catch (Exception ex) {
            throw new AssertionError("1x1 image must not fail in Tubeness", ex);
        }
    }

    private static void testEmptyLacunarity()
    {
        Lacunarity2.Statistics stats = new Lacunarity2.Statistics();
        Lacunarity2.computeLacunarity(stats, new byte[16 * 16], 16, 16, 10, 10, 5);
        check(stats.eLambda3.size == 0, "empty image must not produce lacunarity samples");
        check(stats.fLambda3.size == 0, "empty image must not produce F lacunarity samples");
    }

    private static void testSmallLacunarity()
    {
        byte[] image = new byte[8 * 8];
        image[4 + 8 * 4] = (byte)0xff;

        Lacunarity2.Statistics stats = new Lacunarity2.Statistics();
        Lacunarity2.computeLacunarity(stats, image, 8, 8, 10, 10, 5);
        check(stats.eLambda3.size == 0, "ROI smaller than minimum box must be rejected");
    }

    private static void testStraightSkeleton()
    {
        int width = 31;
        int height = 15;
        byte[] mask = new byte[width * height];
        for (int x = 3; x < width - 3; x++)
            mask[x + width * (height / 2)] = (byte)0xff;

        AnalyzeSkeleton2.Result result = analyzeFast(mask, width, height);
        check(result.treeCount == 1, "straight line must produce one tree");
        check(result.endPoints.size / 3 == 2, "straight line must produce two endpoints");
        check(result.isolatedJunctions.size == 0, "straight line must not produce junctions");
        check(result.edgesLengths.size == 1, "straight line must produce one segment");
    }

    private static void testBranchingSkeleton()
    {
        int width = 31;
        int height = 31;
        byte[] mask = new byte[width * height];
        int cx = width / 2;
        int cy = height / 2;

        for (int x = 4; x <= cx; x++)
            mask[x + width * cy] = (byte)0xff;
        for (int x = cx; x < width - 4; x++)
            mask[x + width * (cy - (x - cx) / 2)] = (byte)0xff;
        for (int x = cx; x < width - 4; x++)
            mask[x + width * (cy + (x - cx) / 2)] = (byte)0xff;

        AnalyzeSkeleton2.Result result = analyzeFast(mask, width, height);
        check(result.treeCount == 1, "branching shape must produce one tree");
        check(result.endPoints.size / 3 >= 3, "branching shape must retain branch endpoints");
        check(result.isolatedJunctions.size >= 1, "branching shape must produce a junction");
    }

    private static AnalyzeSkeleton2.Result analyzeFast(byte[] mask, int width, int height)
    {
        byte[] skeleton = new byte[mask.length];
        byte[] scratch = new byte[mask.length];
        Zha84.skeletonize(skeleton, scratch, mask, width, height, 0);

        AnalyzeSkeleton2.Result result = new AnalyzeSkeleton2.Result();
        AnalyzeSkeleton2.analyze(result, skeleton, width, height, 1);
        return result;
    }

    private static void check(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }

    private static class SingleThreadSliceRunner implements ISliceRunner
    {
        public void runSlices(
            Utils.ISliceCompute compute,
            int maxWorkers,
            int size,
            int inPlaceThreshold
        ) throws java.util.concurrent.ExecutionException
        {
            compute.initSlices(1);
            compute.computeSlice(0, 0, size);
            Utils.ISliceCompute.Result result = new Utils.ISliceCompute.Result();
            result.idx = 0;
            compute.finishSlice(result);
        }
    }
}
