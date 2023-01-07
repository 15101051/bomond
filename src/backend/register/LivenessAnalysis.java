package backend.register;

import backend.mc.MCBasicBlock;
import backend.mc.MCFunction;
import backend.mc.MCInstr;
import backend.mc.MCOperand;
import utils.IList;

import java.util.HashMap;
import java.util.HashSet;

public class LivenessAnalysis {

    public void analysis(MCFunction mcf) {
        for (IList.INode<MCBasicBlock, MCFunction> bNode : mcf.getList()) {
            MCBasicBlock mcBB = bNode.getValue();
            mcBB.liveUse.clear();
            mcBB.liveDef.clear();
            for (IList.INode<MCInstr, MCBasicBlock> iNode : mcBB.getList()) {
                MCInstr instr = iNode.getValue();
                instr.getUseReg().forEach(reg -> {
                    if (!mcBB.liveDef.contains(reg)) {
                        mcBB.liveUse.add(reg);
                    }
                });
                instr.getDefReg().forEach(reg -> {
                    if (!mcBB.liveUse.contains(reg)) {
                        mcBB.liveDef.add(reg);
                    }
                });
            }
            mcBB.liveIn.clear();
            mcBB.liveOut.clear();
            mcBB.liveIn.addAll(mcBB.liveUse);
        }
        boolean stable = false;
        while (!stable) {
            stable = true;
            for (IList.INode<MCBasicBlock, MCFunction> bNode = mcf.getList().getLast(); !bNode.isGuard(); bNode = bNode.getPrev()) {
                MCBasicBlock mcBB = bNode.getValue();
                HashSet<MCOperand> newLiveOut = new HashSet<>();
                for (MCBasicBlock succ : mcBB.getSucc()) {
                    for (MCOperand liveIn : succ.liveIn) {
                        if (mcBB.liveOut.add(liveIn)) {
                            newLiveOut.add(liveIn);
                        }
                    }
                }
                if (newLiveOut.size() > 0) {
                    stable = false;
                }
                if (!stable) {
                    for (MCOperand op : mcBB.liveOut) {
                        if (!mcBB.liveDef.contains(op)) {
                            mcBB.liveIn.add(op);
                        }
                    }
                }
            }
        }
    }
}
