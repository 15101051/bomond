package backend;

import backend.mc.*;
import backend.pass.Peephole;
import backend.register.RegisterManager;
import midend.ir.*;
import midend.analysis.InterproceduralAnalysis;

import java.util.ArrayList;

public class CodeGen {
    private final MCModule lirm = MCModule.module;
    private final MCOperand.MCPhyReg GP = new MCOperand.MCPhyReg(RegisterManager.MCPhyRegTag.gp);

    public void codeGen() {
        int startPos = 0x10010000;
        MCOperand.MCGlobalData.allocate(lirm, startPos);
        ArrayList<Function> order = InterproceduralAnalysis.reversedCallOrder(Module.module);
        for (Function f : order) {
            new MCFunction(f);
        }
        new Peephole().run();
        if (!lirm.mcGlobalDatas.isEmpty()) {
            for (MCFunction lirf : lirm.functions.values()) {
                if (lirf.getFunc().getName().equals("main")) {
                    MCInstr.buildLuiAtEntry(GP, new MCOperand.MCImm(0x1001), lirf.getList().getEntry().getValue());
                    break;
                }
            }
        }
    }

    public String getMIPS() {
        StringBuilder sb = new StringBuilder();
        sb.append(".data\n");
        for (MCOperand.MCGlobalData gd : lirm.mcGlobalDatas) {
            sb.append(gd.getMIPS());
        }
        sb.append(".text\n");
        for (MCFunction lirf : lirm.functions.values()) {
            if (lirf.getFunc().getName().equals("main")) {
                sb.append(lirf.getMIPS());
            }
        }
        for (MCFunction lirf : lirm.functions.values()) {
            if (!lirf.getFunc().getName().equals("main")) {
                sb.append(lirf.getMIPS());
            }
        }
        return sb.toString();
    }
}
