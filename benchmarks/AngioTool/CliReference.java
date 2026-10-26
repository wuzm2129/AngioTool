package AngioTool;

import Pixels.ArgbBuffer;
import Pixels.ImageFile;
import Utils.ISliceRunner;
import java.io.File;

/** Independent direct analysis path, also compiled against the 2.2.3 baseline. */
public class CliReference
{
    public static void main(String[] args) throws Exception
    {
        AnalyzerParameters p = AnalyzerParameterImporter.load(new File(args[1])).parameters;
        ArgbBuffer image = ImageFile.acquireImageForAnalysis(args[0], p.effectiveResizeFactor());
        try {
            Analyzer.Data data = new Analyzer.Data(p.brightnessLineSegments, p.brightnessLineSegments.length / 2);
            Analyzer.Stats stats = Analyzer.analyze(data, new File(args[0]), image, p, new ISliceRunner.Series());
            OverlayDataExporter.export(args[2], p, data, stats, image.width, image.height, image.brightestChannel);
        }
        finally {
            ImageFile.releaseImage(image);
            Analyzer.threadPool.shutdownNow();
        }
    }
}
