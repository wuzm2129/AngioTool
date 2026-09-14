package ij.plugin.filter;

import Algorithms.FractalBoxCount;
import ij.IJ;
import ij.measure.CurveFitter;
import ij.process.ByteProcessor;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Optional external reference check; ij.jar is a test-only dependency.
 * Runs ImageJ's actual box counter at the same scales and four padded origins.
 * Deliberately bypasses its default margin crop and interactive plot dialog.
 */
public class FractalReferenceTest
{
    public static void main(String[] args) throws Exception
    {
        System.out.println("ImageJ reference version: " + IJ.getVersion());
        for (String path : args) {
            BufferedImage image = ImageIO.read(new File(path));
            int width = image.getWidth();
            int height = image.getHeight();
            byte[] mask = new byte[width * height];
            for (int y = 0; y < height; y++)
                for (int x = 0; x < width; x++)
                    mask[x + width * y] = (image.getRGB(x, y) & 0xffffff) != 0 ? (byte)255 : 0;
            FractalBoxCount.Result actual = FractalBoxCount.compute(mask, width, height);
            double[] xs = new double[actual.boxSizes.length];
            double[] ys = new double[actual.boxSizes.length];
            for (int i = 0; i < actual.boxSizes.length; i++) {
                int size = actual.boxSizes[i];
                int reference = Integer.MAX_VALUE;
                for (int padY : new int[] {0, size / 2}) {
                    for (int padX : new int[] {0, size / 2}) {
                        // Adding half a box of background exactly reproduces the
                        // half-box origin, including its clipped leading border box.
                        int paddedWidth = width + padX;
                        int paddedHeight = height + padY;
                        byte[] padded = new byte[paddedWidth * paddedHeight];
                        for (int y = 0; y < height; y++)
                            System.arraycopy(mask, y * width, padded, (y + padY) * paddedWidth + padX, width);
                        FractalBoxCounter counter = new FractalBoxCounter();
                        counter.foreground = 255;
                        counter.roi = new Rectangle(0, 0, paddedWidth, paddedHeight);
                        counter.counts = new int[size * size + 1];
                        int count = counter.count(size, new ByteProcessor(paddedWidth, paddedHeight, padded, null));
                        reference = Math.min(reference, count);
                    }
                }
                if (actual.boxCounts[i] != reference)
                    throw new AssertionError(path + " scale=" + size + ": " + actual.boxCounts[i] + " != " + reference);
                xs[i] = Math.log(size);
                ys[i] = Math.log(reference);
            }
            if (actual.valid) {
                CurveFitter fit = new CurveFitter(xs, ys);
                fit.doFit(CurveFitter.STRAIGHT_LINE);
                if (Math.abs(actual.dimension + fit.getParams()[1]) > 1e-10 ||
                    Math.abs(actual.rSquared - fit.getRSquared()) > 1e-10)
                    throw new AssertionError("ImageJ regression mismatch: " + path);
            }
            System.out.println(new File(path).getName() + ": counts and fit agree; D=" + actual.dimension);
        }
        System.out.println("ImageJ fractal reference check: PASS");
    }
}
