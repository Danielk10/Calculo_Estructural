import com.diamon.civil.engine.NativeFeaCore;

public class TestInp {
    public static void main(String[] args) {
        System.loadLibrary("feacore");
        NativeFeaCore core = new NativeFeaCore();
        long ptr = core.createModel();
        String json = "{\"nodes\":[{\"id\":1,\"x\":0,\"y\":0,\"z\":0},{\"id\":2,\"x\":5,\"y\":0,\"z\":0}],\"elements\":[{\"id\":1,\"type\":\"B31\",\"elset\":\"Eall\",\"nodes\":[1,2]}],\"materials\":[{\"name\":\"Steel\",\"youngModulus\":210000,\"poissonRatio\":0.3,\"density\":7850}],\"sections\":[{\"elset\":\"Eall\",\"type\":\"BEAM\",\"material\":\"Steel\",\"params\":[200,200]}],\"constraints\":[{\"nodeId\":1,\"dofs\":[1,2,3,4,5,6],\"value\":0}],\"loads\":[{\"nodeId\":2,\"fx\":100,\"fy\":0,\"fz\":0}]}";
        core.modelFromJson(ptr, json);
        System.out.println(core.modelToInp(ptr));
    }
}
