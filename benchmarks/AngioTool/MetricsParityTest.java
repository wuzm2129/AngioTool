package AngioTool;

import Pixels.ArgbBuffer;
import Pixels.ImageFile;
import Utils.ISliceRunner;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Arrays;

public class MetricsParityTest
{
    public static void main(String[] args) throws Exception
    {
        ArgbBuffer image = ImageFile.acquireImageForAnalysis(args[0], 1.0);
        try {
            Analyzer.Data data = new Analyzer.Data(null, 0);
            Analyzer.Stats stats = Analyzer.analyze(data, new File(args[0]), image,
                AnalyzerParameters.defaults(), new ISliceRunner.Parallel(Analyzer.threadPool));
            for (Field field : Analyzer.Stats.class.getFields())
                System.out.println(field.getName() + "=" + field.get(stats));
            System.out.println("mask=" + Arrays.hashCode(Arrays.copyOf(data.analysisImage.buf,
                image.width * image.height)));
        }
        finally {
            ImageFile.releaseImage(image);
            Analyzer.threadPool.shutdown();
        }
    }
}
