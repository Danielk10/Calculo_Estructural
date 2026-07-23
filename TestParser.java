import com.diamon.civil.engine.DatParser;
import java.io.File;

public class TestParser {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Provide .dat file");
            return;
        }
        DatParser parser = new DatParser();
        DatParser.ParseResult result = parser.parse(new File(args[0]));
        System.out.println("Parsed displacements: " + result.displacements.size());
        for (DatParser.NodeDisplacement nd : result.displacements) {
            System.out.println("Node " + nd.nodeId + ": ux=" + nd.ux + " uy=" + nd.uy + " uz=" + nd.uz);
        }
        System.out.println("Max Disp: " + result.maxDisp);
    }
}
