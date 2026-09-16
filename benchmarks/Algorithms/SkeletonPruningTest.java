package Algorithms;

import java.util.Arrays;

/** Hand-drawn pixel fixtures with explicit expected removals, independent of skeletonization. */
public class SkeletonPruningTest
{
    private static final int SIZE = 13;

    public static void main(String[] args)
    {
        byte[] branch = fork(4, 3);
        for (double threshold : new double[] {0, 2, 3, 5}) {
            byte[] expected = branch.clone();
            int removed = threshold >= 3 ? 3 : 0;
            if (removed > 0)
                for (int y = 7; y <= 9; y++) expected[6 + SIZE * y] = 0;
            assertPruned(branch, threshold, expected, removed, "threshold " + threshold);
            // A rotated/reflected fixture must retain the same physical branches.
            for (int turn = 0; turn < 4; turn++) {
                assertPruned(transform(branch, turn, false), threshold,
                    transform(expected, turn, false), removed, "rotation " + turn);
                assertPruned(transform(branch, turn, true), threshold,
                    transform(expected, turn, true), removed, "reflection " + turn);
            }
        }

        byte[] diagonal = new byte[SIZE * SIZE];
        for (int x = 2; x <= 6; x++) diagonal[x + SIZE * 6] = 1;
        for (int i = 1; i <= 4; i++) diagonal[6 + i + SIZE * (6 + i)] = 1;
        diagonal[7 + SIZE * 5] = diagonal[8 + SIZE * 4] = 1;
        byte[] diagonalExpected = diagonal.clone();
        diagonalExpected[7 + SIZE * 5] = diagonalExpected[8 + SIZE * 4] = 0;
        assertPruned(diagonal, 2.82, diagonal, 0, "diagonal path exceeds 2.82 px");
        assertPruned(diagonal, 2 * Math.sqrt(2), diagonalExpected, 2, "diagonal threshold inclusive");

        byte[] line = new byte[SIZE * SIZE];
        for (int x = 3; x <= 9; x++) line[x + SIZE * 6] = 1;
        line[2 + SIZE * 2] = 1;
        assertPruned(line, 100, line, 0, "standalone vessel and isolated pixel");
        byte[] loop = new byte[SIZE * SIZE];
        for (int i = 0; i <= 3; i++) {
            loop[6 + i + SIZE * (3 + i)] = 1;
            loop[9 - i + SIZE * (6 + i)] = 1;
            loop[6 - i + SIZE * (9 - i)] = 1;
            loop[3 + i + SIZE * (6 - i)] = 1;
        }
        assertPruned(loop, 100, loop, 0, "closed loop");
        byte[] border = fork(6, 6);
        assertPruned(border, 100, border, 0, "all three endpoints touch border");
        assertPruned(new byte[SIZE * SIZE], 3, new byte[SIZE * SIZE], 0, "empty skeleton");

        // Both short arms are selected from the original graph; the long stem survives.
        byte[] twoArms = fork(2, 5);
        byte[] stem = new byte[SIZE * SIZE];
        for (int y = 6; y <= 11; y++) stem[6 + SIZE * y] = 1;
        assertPruned(twoArms, 3, stem, 4, "simultaneous removal of two short arms");

        for (double invalid : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            byte[] input = branch.clone();
            try {
                SkeletonPruning.prune(input, SIZE, SIZE, invalid);
                throw new AssertionError("Invalid threshold accepted: " + invalid);
            }
            catch (IllegalArgumentException expected) {
                check(Arrays.equals(branch, input), "Rejected input must not be mutated");
            }
        }
        System.out.println("SkeletonPruningTest: PASS");
    }

    private static byte[] fork(int armLength, int stemLength)
    {
        byte[] image = new byte[SIZE * SIZE];
        image[6 + SIZE * 6] = 1;
        for (int i = 1; i <= armLength; i++) {
            image[6 - i + SIZE * (6 - i)] = 1;
            image[6 + i + SIZE * (6 - i)] = 1;
        }
        for (int i = 1; i <= stemLength; i++) image[6 + SIZE * (6 + i)] = 1;
        return image;
    }

    private static byte[] transform(byte[] source, int turns, boolean reflect)
    {
        byte[] result = new byte[source.length];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int xx = reflect ? SIZE - 1 - x : x;
                int yy = y;
                for (int i = 0; i < turns; i++) {
                    int nextX = SIZE - 1 - yy;
                    yy = xx;
                    xx = nextX;
                }
                result[xx + SIZE * yy] = source[x + SIZE * y];
            }
        }
        return result;
    }

    private static void assertPruned(byte[] input, double threshold, byte[] expected, int count, String label)
    {
        byte[] actual = input.clone();
        int removed = SkeletonPruning.prune(actual, SIZE, SIZE, threshold);
        check(removed == count, label + ": expected " + count + " removed pixels, got " + removed);
        check(Arrays.equals(actual, expected), label + ": unexpected surviving pixels");
    }

    private static void check(boolean condition, String message)
    {
        if (!condition) throw new AssertionError(message);
    }
}
