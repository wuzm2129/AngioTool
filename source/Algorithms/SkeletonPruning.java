package Algorithms;

import Utils.IntVector;

/** Conservative, single-pass removal of short endpoint-to-junction paths in 2D. */
public final class SkeletonPruning
{
    public static int prune(byte[] skeleton, int width, int height, double maxLength)
    {
        if (!Double.isFinite(maxLength) || maxLength < 0)
            throw new IllegalArgumentException("Pruning length must be finite and nonnegative");
        if (maxLength == 0)
            return 0;
        int area = width * height;
        byte[] degree = new byte[area];
        boolean[] remove = new boolean[area];
        for (int i = 0; i < area; i++) {
            if ((skeleton[i] & 1) != 0)
                degree[i] = (byte)Integer.bitCount(AnalyzeSkeleton2.getBooleanNeighborBits(
                    skeleton, width, height, 1, i % width, i / width, 0));
        }
        IntVector path = new IntVector();
        for (int start = 0; start < area; start++) {
            if (degree[start] != 1)
                continue;
            path.size = 0;
            int previous = -1;
            int current = start;
            double length = 0;
            boolean touchesBorder = false;
            while (true) {
                int x = current % width;
                int y = current / width;
                touchesBorder |= x == 0 || y == 0 || x == width - 1 || y == height - 1;
                if (degree[current] > 2) {
                    if (!touchesBorder && length <= maxLength) {
                        for (int j = 0; j < path.size; j++)
                            remove[path.buf[j]] = true;
                    }
                    break;
                }
                // Never erase standalone endpoint-to-endpoint vessels or isolated pixels.
                if (current != start && degree[current] != 2)
                    break;
                path.add(current);
                int next = -1;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx;
                        int yy = y + dy;
                        if ((dx == 0 && dy == 0) || xx < 0 || xx >= width || yy < 0 || yy >= height)
                            continue;
                        int p = xx + width * yy;
                        if (p != previous && (skeleton[p] & 1) != 0)
                            next = p;
                    }
                }
                if (next < 0)
                    break;
                length += Math.hypot(next % width - x, next / width - y);
                if (length > maxLength)
                    break;
                previous = current;
                current = next;
            }
        }
        // Simultaneous deletion prevents scan order and cascading pruning from changing the threshold.
        int removed = 0;
        for (int i = 0; i < area; i++) {
            if (remove[i]) {
                skeleton[i] = 0;
                removed++;
            }
        }
        return removed;
    }
}
