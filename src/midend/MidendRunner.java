package midend;

import midend.analysis.LoopInfoAnalysis;
import midend.ir.IRValidator;
import midend.ir.Module;
import midend.analysis.BBPredSuccAnalysis;
import midend.analysis.DominanceAnalysis;
import midend.analysis.InterproceduralAnalysis;
import midend.pass.*;
import utils.Config;
import utils.Logger;

public class MidendRunner {
    public void run(Module m) {
        new BBPredSuccAnalysis().run(m);
        if (!Config.submit) Logger.logLLVM("visitor");
        new DominanceAnalysis().run(m);
        new InterproceduralAnalysis().run(m);
        new Mem2Reg().run(m);
        if (!Config.submit) Logger.logLLVM("mem2reg");
        assert Config.submit || new IRValidator().run(m);
        new FunctionInline().run(m);
        new BranchOptimization().run(m);
        if (!Config.submit) Logger.logLLVM("function_inline");
        assert Config.submit || new IRValidator().run(m);
        GVN_GCM();
        new LoadStoreInBlock().run(m);
        new InstructionCombination().run(m);
        if (!Config.submit) Logger.logLLVM("load_store_in_block");
        GVN_GCM();
        new DeadCodeElimination().run(m);
        if (!Config.submit) Logger.logLLVM("dce");
        assert Config.submit || new IRValidator().run(m);
        new StrengthReduction().run(m);
        if (!Config.submit) Logger.logLLVM("strength_reduction");
        assert Config.submit || new IRValidator().run(m);
        new PhiElimination().run(m);
        if (!Config.submit) Logger.logLLVM("phi_elimination");
    }

    static int gvn_gcm_count = 0;

    private void GVN_GCM() {
        // System.out.println("gvn gcm");
        new LoopInfoAnalysis().run(Module.module);
        new DominanceAnalysis().run(Module.module);
        new GVN().run(Module.module);
        new GCM().run(Module.module);
        new BranchOptimization().run(Module.module);
        new InterproceduralAnalysis().run(Module.module);
        assert Config.submit || new IRValidator().run(Module.module);
        if (!Config.submit) Logger.logLLVM("gvn_gcm" + ++gvn_gcm_count);
        // System.out.println("gvn gcm done");
    }
}
