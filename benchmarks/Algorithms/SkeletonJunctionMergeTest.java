package Algorithms;

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
        AnalyzeSkeleton2.Result separate = analyze(image, rows[0].length(), rows.length, 0.0);
        AnalyzeSkeleton2.Result merged = analyze(image, rows[0].length(), rows.length, 6.0);
        check(separate.isolatedJunctions.size == 2,
            "Expected two separate junction regions, got " + separate.isolatedJunctions.size);
        check(merged.isolatedJunctions.size == 1,
            "Expected one merged junction, got " + merged.isolatedJunctions.size);
        check(merged.mergedJunctionRegionCount == 1,
            "Expected one absorbed junction region, got " + merged.mergedJunctionRegionCount);

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

        System.out.println("Skeleton junction merge tests passed");
    }
}
