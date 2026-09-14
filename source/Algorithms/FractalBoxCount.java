package Algorithms;

import java.util.BitSet;

/**
 * Estimates the finite-scale box-counting dimension of a binary vessel mask.
 * Foreground is every nonzero byte and the whole processed image defines the
 * measurement domain.  At each power-of-two box size, this implementation
 * evaluates grids whose origins are (0,0), (s/2,0), (0,s/2), and (s/2,s/2).
 * A grid includes its partial border boxes: grid cells intersecting the image
 * at either edge are clipped to the image before occupancy is assessed.  The
 * smallest occupied-box count across those four origins is retained.  The
 * reported dimension is the ordinary least-squares slope of log(N) against
 * log(1/s) across every configured scale; it is therefore a finite-scale
 * estimator rather than a claim of an exact mathematical dimension.  A mask
 * may have spare capacity, as pooled image buffers do; only its first
 * {@code width * height} bytes are part of the image.
 */
public final class FractalBoxCount
{
    public static final String METHOD = "box-counting";
    public static final String ANALYSIS_OBJECT = "final-vessel-mask";
    public static final String GRID_POLICY = "minimum-count-of-four-half-box-offsets";
    public static final String SCALE_POLICY = "powers-of-two-from-2-to-quarter-shorter-image-side";

    private FractalBoxCount()
    {
    }

    public static final class Result
    {
        public boolean valid;
        public String invalidReason;
        public double dimension = Double.NaN;
        public double rSquared = Double.NaN;
        public int[] boxSizes = new int[0];
        public long[] boxCounts = new long[0];
        public int imageWidth;
        public int imageHeight;
    }

    public static Result compute(byte[] mask, int width, int height)
    {
        Result result = new Result();
        result.imageWidth = width;
        result.imageHeight = height;

        if (mask == null) {
            invalidate(result, "mask is null");
            return result;
        }
        if (width <= 0 || height <= 0) {
            invalidate(result, "image dimensions must be positive");
            return result;
        }

        long pixelCount = (long)width * (long)height;
        if (pixelCount > Integer.MAX_VALUE || mask.length < pixelCount) {
            invalidate(result, "mask capacity does not cover image dimensions");
            return result;
        }

        int[] sizes = configuredSizes(Math.min(width, height));
        result.boxSizes = sizes;
        result.boxCounts = new long[sizes.length];

        boolean hasForeground = false;
        for (int i = 0; i < (int)pixelCount; i++) {
            if (mask[i] != 0) {
                hasForeground = true;
                break;
            }
        }

        if (!hasForeground) {
            invalidate(result, "mask contains no foreground pixels");
            return result;
        }

        try {
            for (int i = 0; i < sizes.length; i++)
                result.boxCounts[i] = minimumOccupiedBoxCount(mask, width, height, sizes[i]);
        }
        catch (IllegalArgumentException exception) {
            invalidate(result, exception.getMessage());
            return result;
        }

        if (sizes.length < 4) {
            invalidate(result, "at least four box sizes are required");
            return result;
        }

        long firstCount = result.boxCounts[0];
        boolean countsVary = false;
        for (int i = 1; i < result.boxCounts.length; i++) {
            if (result.boxCounts[i] != firstCount) {
                countsVary = true;
                break;
            }
        }
        if (!countsVary) {
            invalidate(result, "occupied box counts are constant across scales");
            return result;
        }

        fit(result);
        return result;
    }

    private static int[] configuredSizes(int shorterSide)
    {
        int maximumSize = shorterSide / 4;
        int count = 0;
        for (int size = 2; size <= maximumSize; size *= 2) {
            count++;
            if (size > Integer.MAX_VALUE / 2)
                break;
        }

        int[] sizes = new int[count];
        int size = 2;
        for (int i = 0; i < sizes.length; i++) {
            sizes[i] = size;
            size *= 2;
        }
        return sizes;
    }

    private static long minimumOccupiedBoxCount(byte[] mask, int width, int height, int size)
    {
        long minimum = Long.MAX_VALUE;
        int halfSize = size / 2;
        int[] offsets = {0, halfSize};
        int largestGrid = 0;
        for (int yIndex = 0; yIndex < offsets.length; yIndex++) {
            for (int xIndex = 0; xIndex < offsets.length; xIndex++) {
                int gridCells = gridCellCount(width, height, size, offsets[xIndex], offsets[yIndex]);
                if (gridCells > largestGrid)
                    largestGrid = gridCells;
            }
        }
        BitSet occupied = new BitSet(largestGrid);
        for (int yIndex = 0; yIndex < offsets.length; yIndex++) {
            for (int xIndex = 0; xIndex < offsets.length; xIndex++) {
                occupied.clear();
                long count = occupiedBoxCount(mask, width, height, size, offsets[xIndex], offsets[yIndex], occupied);
                if (count < minimum)
                    minimum = count;
            }
        }
        return minimum;
    }

    private static int gridCellCount(int width, int height, int size, int offsetX, int offsetY)
    {
        int firstColumn = floorDiv(-offsetX, size);
        int lastColumn = floorDiv(width - 1 - offsetX, size);
        int firstRow = floorDiv(-offsetY, size);
        int lastRow = floorDiv(height - 1 - offsetY, size);
        long columns = (long)lastColumn - (long)firstColumn + 1L;
        long rows = (long)lastRow - (long)firstRow + 1L;
        long gridCells = columns * rows;
        if (gridCells <= 0L || gridCells > Integer.MAX_VALUE)
            throw new IllegalArgumentException("box grid is too large to index safely");
        return (int)gridCells;
    }

    private static long occupiedBoxCount(byte[] mask, int width, int height, int size, int offsetX, int offsetY, BitSet occupied)
    {
        int firstColumn = floorDiv(-offsetX, size);
        int firstRow = floorDiv(-offsetY, size);
        int lastColumn = floorDiv(width - 1 - offsetX, size);
        long columns = (long)lastColumn - (long)firstColumn + 1L;
        for (int y = 0, pixel = 0; y < height; y++) {
            int row = floorDiv(y - offsetY, size) - firstRow;
            long rowStart = (long)row * columns;
            for (int x = 0; x < width; x++, pixel++) {
                if (mask[pixel] != 0) {
                    int column = floorDiv(x - offsetX, size) - firstColumn;
                    occupied.set((int)(rowStart + column));
                }
            }
        }
        return occupied.cardinality();
    }

    private static int floorDiv(int value, int divisor)
    {
        if (value >= 0)
            return value / divisor;
        return -(((-value) + divisor - 1) / divisor);
    }

    private static void fit(Result result)
    {
        int n = result.boxSizes.length;
        double meanX = 0.0;
        double meanY = 0.0;
        for (int i = 0; i < n; i++) {
            meanX += -Math.log(result.boxSizes[i]);
            meanY += Math.log(result.boxCounts[i]);
        }
        meanX /= n;
        meanY /= n;

        double sumXX = 0.0;
        double sumYY = 0.0;
        double sumXY = 0.0;
        for (int i = 0; i < n; i++) {
            double dx = -Math.log(result.boxSizes[i]) - meanX;
            double dy = Math.log(result.boxCounts[i]) - meanY;
            sumXX += dx * dx;
            sumYY += dy * dy;
            sumXY += dx * dy;
        }
        if (!(sumXX > 0.0) || !(sumYY > 0.0)) {
            invalidate(result, "regression has zero variation");
            return;
        }

        double dimension = sumXY / sumXX;
        double rSquared = (sumXY * sumXY) / (sumXX * sumYY);
        if (rSquared > 1.0 && rSquared < 1.0 + 1e-12)
            rSquared = 1.0;
        else if (rSquared < 0.0 && rSquared > -1e-12)
            rSquared = 0.0;
        if (!isFinite(dimension) || !isFinite(rSquared)) {
            invalidate(result, "regression produced a non-finite result");
            return;
        }

        result.dimension = dimension;
        result.rSquared = rSquared;
        result.valid = true;
        result.invalidReason = null;
    }

    private static boolean isFinite(double value)
    {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static void invalidate(Result result, String reason)
    {
        result.valid = false;
        result.invalidReason = reason;
        result.dimension = Double.NaN;
        result.rSquared = Double.NaN;
    }
}
