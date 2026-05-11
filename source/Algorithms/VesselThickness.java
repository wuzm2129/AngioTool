package Algorithms;

import Utils.*;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

public class VesselThickness
{
    public static double computeMedianVesselThickness(
        ISliceRunner runner,
        int maxWorkers,
        int[] points,
        int arraySize,
        int pointSize,
        int[] scratch,
        byte[] input,
        int width,
        int height
    ) throws ExecutionException
    {
        return computeMedianVesselThickness(runner, maxWorkers, points, arraySize, pointSize, scratch, input, width, height, null);
    }

    public static double computeMedianVesselThickness(
        ISliceRunner runner,
        int maxWorkers,
        int[] points,
        int arraySize,
        int pointSize,
        int[] scratch,
        byte[] input,
        int width,
        int height,
        AnalyzeSkeleton2.Result skelResult
    ) throws ExecutionException
    {
        final int nPoints = arraySize / pointSize;
        if (skelResult != null)
            skelResult.edgesDiameters.size = 0;

        if (nPoints <= 0) {
            if (skelResult != null) {
                int nEdges = skelResult.edgesLengths.size;
                skelResult.edgesDiameters.resize(nEdges);
                if (nEdges > 0)
                    Arrays.fill(skelResult.edgesDiameters.buf, 0, nEdges, 0.0);
            }
            return 0.0;
        }

        final int thresh = 200;

        runner.runSlices(new Step1(scratch, input, width, height, thresh), maxWorkers, height, (height / 4) + 1);

        double[] vesselThickness = DoubleBufferPool.acquireAsIs(nPoints);
        double[] sortedThickness = DoubleBufferPool.acquireAsIs(nPoints);

        final int maxDimension = Math.max(width, height);
        final int maxResult = 3 * (maxDimension + 1) * (maxDimension + 1);

        for(int i = 0; i < nPoints; i++) {
            int x  = points[i*pointSize];
            int y1 = points[i*pointSize + 1];

            if ((input[x + width * y1] & 255) < thresh) {
                vesselThickness[i] = 0.0;
                continue;
            }

            int min = maxResult;

            // the X and Y of 'scratch' were flipped in step 1
            for (int y2 = 0; y2 < height; ++y2)
                min = Math.min(scratch[y2 + height * x] + (y1-y2)*(y1-y2), min);

            double result = Math.sqrt(min);
            vesselThickness[i] = result;
        }

        if (skelResult != null)
            computeEdgeDiameters(skelResult, vesselThickness, pointSize);

        System.arraycopy(vesselThickness, 0, sortedThickness, 0, nPoints);
        Arrays.sort(sortedThickness, 0, nPoints);
        int middle = nPoints / 2;

        double thickness = nPoints % 2 == 1 ?
            sortedThickness[middle] :
            (sortedThickness[middle - 1] + sortedThickness[middle]) / 2.0;

        DoubleBufferPool.release(sortedThickness);
        DoubleBufferPool.release(vesselThickness);

        return thickness * 2.0;
    }

    private static void computeEdgeDiameters(AnalyzeSkeleton2.Result skelResult, double[] pointThickness, int pointSize)
    {
        int nEdges = skelResult.edgesLengths.size;
        skelResult.edgesDiameters.resize(nEdges);

        int maxSlabs = 0;
        for (int i = 0; i < nEdges; i++) {
            int nSlabs = skelResult.edgesPoints.buf[i * 2 + 1] / pointSize;
            if (nSlabs > maxSlabs)
                maxSlabs = nSlabs;
        }

        double[] edgeThickness = maxSlabs > 0 ? DoubleBufferPool.acquireAsIs(maxSlabs) : null;
        try {
            for (int i = 0; i < nEdges; i++) {
                int slabStart = skelResult.edgesPoints.buf[i * 2] / pointSize;
                int nSlabs = skelResult.edgesPoints.buf[i * 2 + 1] / pointSize;
                if (nSlabs <= 0) {
                    skelResult.edgesDiameters.buf[i] = 0.0;
                    continue;
                }

                for (int j = 0; j < nSlabs; j++)
                    edgeThickness[j] = pointThickness[slabStart + j];

                Arrays.sort(edgeThickness, 0, nSlabs);
                int middle = nSlabs / 2;
                double thickness = nSlabs % 2 == 1 ?
                    edgeThickness[middle] :
                    (edgeThickness[middle - 1] + edgeThickness[middle]) / 2.0;

                skelResult.edgesDiameters.buf[i] = thickness * 2.0;
            }
        }
        finally {
            if (edgeThickness != null)
                DoubleBufferPool.release(edgeThickness);
        }
    }

    static class Step1 implements ISliceCompute
    {
        final int[] output;
        final byte[] input;
        final int width;
        final int height;
        final int thresh;

        public Step1(int[] output, byte[] input, int width, int height, int thresh)
        {
            this.output = output;
            this.input = input;
            this.width = width;
            this.height = height;
            this.thresh = thresh;
        }

        @Override
        public void initSlices(int nSlices) {}

        @Override
        public Object computeSlice(int sliceIdx, int start, int length)
        {
            final int maxDimension = Math.max(width, height);
            final int maxResult = 3 * (maxDimension + 1) * (maxDimension + 1);

            for (int j = start; j < start + length; j++) {
                for (int i = 0; i < width; ++i) {
                    int min = maxResult;

                    for (int x = i; x < width; ++x) {
                        if ((input[x + width * j] & 255) < thresh) {
                            min = (i-x)*(i-x);
                            break;
                        }
                    }

                    for(int x = i - 1; x >= 0; --x) {
                        if ((input[x + width * j] & 255) < thresh) {
                            min = Math.min((i-x)*(i-x), min);
                            break;
                        }
                    }

                    // swap X and Y to improve memory locality in the next step
                    output[j + height * i] = min;
                }
            }

            return null;
        }

        @Override
        public void finishSlice(ISliceCompute.Result res) {}
    }
}
