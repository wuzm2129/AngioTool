package AngioTool;

import Algorithms.FractalBoxCount;
import java.util.Arrays;
import java.util.Locale;

public class FractalBoxCountTest
{
    public static void main(String[] args)
    {
        testFullSquareAndLine();
        FractalBoxCount.Result carpet = testSierpinskiCarpet();
        testInvalidMasks();
        testRectangularEdgesAndOracle();
        if (args.length == 1 && "--benchmark".equals(args[0]))
            runBenchmark();
        else if (args.length != 0)
            throw new IllegalArgumentException("Expected no arguments or --benchmark");
        System.out.println(String.format(Locale.ROOT,
            "FractalBoxCountTest: PASS; Sierpinski D=%.6f R2=%.6f error=%.6f",
            carpet.dimension, carpet.rSquared, Math.abs(carpet.dimension - Math.log(8.0) / Math.log(3.0))));
    }

    private static void testFullSquareAndLine()
    {
        int size = 256;
        byte[] square = new byte[size * size];
        Arrays.fill(square, (byte)1);
        FractalBoxCount.Result full = FractalBoxCount.compute(square, size, size);
        check(full.valid, "Full square is valid");
        checkNear(full.dimension, 2.0, 1e-12, "Full square dimension");
        checkNear(full.rSquared, 1.0, 1e-12, "Full square regression");
        check("box-counting".equals(FractalBoxCount.METHOD), "Method metadata");

        byte[] line = new byte[size * size];
        for (int x = 0; x < size; x++)
            line[x + size * (size / 2)] = 1;
        FractalBoxCount.Result lineResult = FractalBoxCount.compute(line, size, size);
        check(lineResult.valid, "Line is valid");
        checkNear(lineResult.dimension, 1.0, 1e-12, "Line dimension");

        byte[] boundary = new byte[64 * 64];
        Arrays.fill(boundary, (byte)1);
        check(FractalBoxCount.compute(boundary, 64, 64).valid, "64-pixel side supplies four scales");
        byte[] belowBoundary = new byte[63 * 63];
        Arrays.fill(belowBoundary, (byte)1);
        FractalBoxCount.Result belowBoundaryResult = FractalBoxCount.compute(belowBoundary, 63, 63);
        check(!belowBoundaryResult.valid && belowBoundaryResult.invalidReason.contains("four box sizes"),
            "63-pixel side has too few scales");
    }

    private static FractalBoxCount.Result testSierpinskiCarpet()
    {
        int side = 243;
        byte[] carpet = new byte[side * side];
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                int tx = x;
                int ty = y;
                boolean foreground = true;
                while (tx > 0 || ty > 0) {
                    if (tx % 3 == 1 && ty % 3 == 1) {
                        foreground = false;
                        break;
                    }
                    tx /= 3;
                    ty /= 3;
                }
                if (foreground)
                    carpet[x + y * side] = 1;
            }
        }
        FractalBoxCount.Result result = FractalBoxCount.compute(carpet, side, side);
        double expected = Math.log(8.0) / Math.log(3.0);
        check(result.valid, "Sierpinski carpet is valid");
        checkNear(result.dimension, expected, 0.1, "Finite-resolution carpet dimension");
        return result;
    }

    private static void testInvalidMasks()
    {
        byte[] empty = new byte[128 * 128];
        FractalBoxCount.Result emptyResult = FractalBoxCount.compute(empty, 128, 128);
        check(!emptyResult.valid && emptyResult.invalidReason.contains("no foreground"), "Empty mask rejected");
        check(emptyResult.boxCounts.length == emptyResult.boxSizes.length, "Empty result retains scales");
        for (long count : emptyResult.boxCounts)
            check(count == 0L, "Empty mask count remains zero");

        FractalBoxCount.Result small = FractalBoxCount.compute(new byte[31 * 31], 31, 31);
        check(!small.valid && small.invalidReason.contains("no foreground"), "Empty small image rejected");
        check(small.boxCounts[0] == 0L, "Empty small image is rejected for emptiness first");

        byte[] smallForeground = new byte[31 * 31];
        Arrays.fill(smallForeground, (byte)1);
        FractalBoxCount.Result smallForegroundResult = FractalBoxCount.compute(smallForeground, 31, 31);
        check(!smallForegroundResult.valid && smallForegroundResult.invalidReason.contains("four box sizes"),
            "Nonempty small image has too few scales");
        check(smallForegroundResult.boxCounts[0] > 0L, "Small image retains sampled scale counts");

        byte[] point = new byte[128 * 128];
        point[64 + 64 * 128] = 1;
        FractalBoxCount.Result pointResult = FractalBoxCount.compute(point, 128, 128);
        check(!pointResult.valid && pointResult.invalidReason.contains("constant"), "Single point rejected");
        check(Double.isNaN(pointResult.dimension), "Invalid result never fakes a dimension");
    }

    private static void testRectangularEdgesAndOracle()
    {
        int width = 131;
        int height = 101;
        byte[] mask = new byte[width * height];
        long state = 0x1234abcdL;
        for (int i = 0; i < mask.length; i++) {
            state = (state * 1664525L + 1013904223L) & 0xffffffffL;
            if ((state >>> 28) < 3)
                mask[i] = 1;
        }
        for (int x = 0; x < width; x++) {
            mask[x] = 1;
            mask[x + width * (height - 1)] = 1;
        }
        for (int y = 0; y < height; y++) {
            mask[y * width] = 1;
            mask[width - 1 + y * width] = 1;
        }
        byte[] original = mask.clone();
        FractalBoxCount.Result result = FractalBoxCount.compute(mask, width, height);
        check(Arrays.equals(mask, original), "Compute does not mutate input");
        check(result.boxSizes.length == 4, "Non-power rectangle supplies four scales");
        for (int i = 0; i < result.boxSizes.length; i++)
            check(result.boxCounts[i] == oracleMinimum(mask, width, height, result.boxSizes[i]),
                "Independent grid oracle at size " + result.boxSizes[i]);

        int logicalWidth = 64;
        int logicalHeight = 64;
        byte[] exact = new byte[logicalWidth * logicalHeight];
        for (int y = 0; y < logicalHeight; y++)
            exact[y * logicalWidth + logicalWidth / 2] = 1;
        byte[] padded = new byte[128 * 128];
        Arrays.fill(padded, (byte)1);
        System.arraycopy(exact, 0, padded, 0, exact.length);
        FractalBoxCount.Result exactResult = FractalBoxCount.compute(exact, logicalWidth, logicalHeight);
        FractalBoxCount.Result paddedResult = FractalBoxCount.compute(padded, logicalWidth, logicalHeight);
        check(Arrays.equals(exactResult.boxCounts, paddedResult.boxCounts),
            "Trailing pooled-buffer capacity is ignored");
        checkNear(paddedResult.dimension, exactResult.dimension, 0.0, "Padded buffer dimension");

        FractalBoxCount.Result malformed = FractalBoxCount.compute(new byte[63], 8, 8);
        check(!malformed.valid && malformed.invalidReason.contains("capacity"), "Insufficient buffer is rejected");
        FractalBoxCount.Result nullMask = FractalBoxCount.compute(null, 8, 8);
        check(!nullMask.valid && nullMask.invalidReason.contains("null"), "Null mask is rejected");
        FractalBoxCount.Result nonPositive = FractalBoxCount.compute(new byte[1], 0, 1);
        check(!nonPositive.valid && nonPositive.invalidReason.contains("positive"), "Nonpositive dimensions are rejected");
        FractalBoxCount.Result overflow = FractalBoxCount.compute(new byte[1], Integer.MAX_VALUE, 2);
        check(!overflow.valid && overflow.invalidReason.contains("capacity"), "Dimension overflow is rejected");
    }

    private static long oracleMinimum(byte[] mask, int width, int height, int size)
    {
        long minimum = Long.MAX_VALUE;
        int[] offsets = {0, size / 2};
        for (int offsetY : offsets) {
            for (int offsetX : offsets) {
                long count = 0;
                int firstColumn = floorDiv(-offsetX, size);
                int lastColumn = floorDiv(width - 1 - offsetX, size);
                int firstRow = floorDiv(-offsetY, size);
                int lastRow = floorDiv(height - 1 - offsetY, size);
                for (int row = firstRow; row <= lastRow; row++) {
                    int y0 = Math.max(0, offsetY + row * size);
                    int y1 = Math.min(height, offsetY + (row + 1) * size);
                    for (int column = firstColumn; column <= lastColumn; column++) {
                        int x0 = Math.max(0, offsetX + column * size);
                        int x1 = Math.min(width, offsetX + (column + 1) * size);
                        if (containsForeground(mask, width, x0, x1, y0, y1))
                            count++;
                    }
                }
                minimum = Math.min(minimum, count);
            }
        }
        return minimum;
    }

    private static boolean containsForeground(byte[] mask, int width, int x0, int x1, int y0, int y1)
    {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                if (mask[x + y * width] != 0)
                    return true;
            }
        }
        return false;
    }

    private static int floorDiv(int value, int divisor)
    {
        return value >= 0 ? value / divisor : -(((-value) + divisor - 1) / divisor);
    }

    private static void runBenchmark()
    {
        for (int side : new int[] {1024, 2048}) {
            byte[] sparse = branchingMask(side);
            byte[] solid = new byte[side * side];
            Arrays.fill(solid, (byte)1);
            benchmark("sparse-branching", sparse, side);
            benchmark("solid", solid, side);
        }
    }

    private static byte[] branchingMask(int side)
    {
        byte[] mask = new byte[side * side];
        int center = side / 2;
        for (int x = 0; x < side; x++)
            drawPoint(mask, side, x, center);
        for (int x = side / 8; x < side; x += side / 8) {
            int arm = Math.min(side / 3, x / 2);
            for (int step = 0; step <= arm; step++) {
                drawPoint(mask, side, x + step / 3, center - step);
                drawPoint(mask, side, x + step / 3, center + step);
            }
        }
        return mask;
    }

    private static void drawPoint(byte[] mask, int side, int x, int y)
    {
        if (x >= 0 && x < side && y >= 0 && y < side)
            mask[x + y * side] = 1;
    }

    private static void benchmark(String name, byte[] mask, int side)
    {
        FractalBoxCount.Result warmup = FractalBoxCount.compute(mask, side, side);
        check(warmup.valid, "Benchmark input valid: " + name);
        long[] timings = new long[3];
        for (int i = 0; i < timings.length; i++) {
            long started = System.nanoTime();
            FractalBoxCount.Result result = FractalBoxCount.compute(mask, side, side);
            timings[i] = System.nanoTime() - started;
            check(result.valid, "Benchmark result valid: " + name);
        }
        Arrays.sort(timings);
        System.out.println(String.format(Locale.ROOT, "Benchmark %dx%d %s median=%.2f ms",
            side, side, name, timings[1] / 1000000.0));
    }

    private static void checkNear(double actual, double expected, double tolerance, String label)
    {
        check(Math.abs(actual - expected) <= tolerance, label + ": " + actual + " expected " + expected);
    }

    private static void check(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }
}
