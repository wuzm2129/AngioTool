package Algorithms;

import java.util.Arrays;

public class SkeletonJunctionMergeTest
{
    private static byte[] skeleton(String... rows)
    {
        int width = rows[0].length();
        byte[] image = new byte[width * rows.length];
        for (int y = 0; y < rows.length; y++) {
            if (rows[y].length() != width)
                throw new IllegalArgumentException("Rows must have equal width");
            for (int x = 0; x < width; x++)
                image[x + width * y] = rows[y].charAt(x) == '#' ? (byte)1 : 0;
        }
        return image;
    }

    private static AnalyzeSkeleton2.Result analyze(byte[] image, int width, int height, double distance)
    {
        AnalyzeSkeleton2.Result result = new AnalyzeSkeleton2.Result();
        AnalyzeSkeleton2.analyze(result, image, width, height, 1, distance);
        return result;
    }

    private static void check(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }

    public static void main(String[] args)
    {
        String[] rows = {
            ".............",
            "...#.........",
            "...#.....#...",
            ".###########.",
            ".........#...",
            ".........#...",
            "............."
        };
        byte[] image = skeleton(rows);
        byte[] original = image.clone();
        AnalyzeSkeleton2.Result separate = analyze(image, rows[0].length(), rows.length, 0.0);
        // Junction regions end at x=4 and start at x=8 on row 3: connecting path is 4 px.
        AnalyzeSkeleton2.Result belowThreshold = analyze(image, rows[0].length(), rows.length, 3.99);
        AnalyzeSkeleton2.Result merged = analyze(image, rows[0].length(), rows.length, 4.0);
        check(separate.isolatedJunctions.size == 2,
            "Expected two separate junction regions, got " + separate.isolatedJunctions.size);
        check(merged.isolatedJunctions.size == 1,
            "Expected one merged junction, got " + merged.isolatedJunctions.size);
        check(merged.mergedJunctionRegionCount == 1,
            "Expected one absorbed junction region, got " + merged.mergedJunctionRegionCount);
        check(belowThreshold.isolatedJunctions.size == 2, "Paths longer than threshold must not merge");
        check(Arrays.equals(original, image), "Merging must not mutate skeleton pixels");
        check(merged.treeCount == separate.treeCount && merged.endPoints.size == separate.endPoints.size &&
            merged.edgesLengths.size == separate.edgesLengths.size, "Merging preserves underlying graph counts");
        for (int i = 0; i < separate.edgesLengths.size; i++)
            check(merged.edgesLengths.buf[i] == separate.edgesLengths.buf[i], "Merging preserves graph lengths");

        String[] disconnectedRows = {
            "...#.....#...",
            "...#.....#...",
            ".#####.#####.",
            "...#.....#...",
            "...#.....#..."
        };
        byte[] disconnected = skeleton(disconnectedRows);
        AnalyzeSkeleton2.Result nearbyButDisconnected = analyze(
            disconnected, disconnectedRows[0].length(), disconnectedRows.length, 20.0);
        check(nearbyButDisconnected.isolatedJunctions.size == 2,
            "Disconnected junctions must not merge");

        String[] cornerRows = {
            ".........",
            "......#..",
            "......#..",
            "......#..",
            "..#####..",
            "........."
        };
        for (int turn = 0; turn < 4; turn++) {
            for (double distance : new double[] {0, 3}) {
                AnalyzeSkeleton2.Result corner = analyze(skeleton(cornerRows), cornerRows[0].length(), cornerRows.length, distance);
                check(corner.isolatedJunctions.size == 0, "A raster corner with two external paths is not a junction");
                check(corner.endPoints.size / 3 == 2, "Corner retains its endpoints");
            }
            cornerRows = rotate(cornerRows);
        }
        String[] loopRows = {
            ".........",
            ".........",
            "..#####..",
            "..#...#..",
            "..#...#..",
            "..#...#..",
            "..#####..",
            ".........",
            "........."
        };
        AnalyzeSkeleton2.Result loop = analyze(skeleton(loopRows), 9, 9, 0);
        check(loop.isolatedJunctions.size == 0 && loop.endPoints.size == 0,
            "A square loop has no true junctions or endpoints");
        // A real returning path contributes two contacts, plus the stem's third contact.
        String[] loopWithStem = {
            "....#....",
            "....#....",
            "..#####..",
            "..#...#..",
            "..#...#..",
            "..#...#..",
            "..#####..",
            ".........",
            "........."
        };
        AnalyzeSkeleton2.Result attachedLoop = analyze(skeleton(loopWithStem), 9, 9, 0);
        check(attachedLoop.isolatedJunctions.size == 1 && attachedLoop.endPoints.size / 3 == 1,
            "A loop with a stem retains its true three-way junction");
        AnalyzeSkeleton2.Result empty = analyze(new byte[81], 9, 9, 3);
        check(empty.isolatedJunctions.size == 0 && empty.mergedJunctionRegionCount == 0, "Empty graph");
        for (double invalid : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            try {
                analyze(image, rows[0].length(), rows.length, invalid);
                throw new AssertionError("Invalid merge threshold accepted: " + invalid);
            }
            catch (IllegalArgumentException expected) {
                check(Arrays.equals(original, image), "Rejected threshold preserves input");
            }
        }
        System.out.println("SkeletonJunctionMergeTest: PASS");
    }

    private static String[] rotate(String[] rows)
    {
        String[] result = new String[rows[0].length()];
        for (int y = 0; y < result.length; y++) {
            StringBuilder row = new StringBuilder();
            for (int x = rows.length - 1; x >= 0; x--) row.append(rows[x].charAt(y));
            result[y] = row.toString();
        }
        return result;
    }
}
