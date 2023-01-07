package midend.analysis;

import midend.ir.BasicBlock;
import midend.ir.Function;
import midend.ir.Instruction;
import midend.ir.Module;
import utils.IList;

public class BBPredSuccAnalysis {
    public void run(Module m) {
        for (IList.INode<Function, Module> fNode : m.functionList) {
            if (!fNode.getValue().isBuiltin()) {
                run(fNode.getValue());
            }
        }
    }

    private void run(Function f) {
        for (IList.INode<BasicBlock, Function> bNode : f.getList()) {
            bNode.getValue().getPredecessors().clear();
            bNode.getValue().getSuccessors().clear();
        }
        for (IList.INode<BasicBlock, Function> bNode : f.getList()) {
            BasicBlock bb = bNode.getValue();
            Instruction instr = bb.getList().getLast().getValue();
            if (instr.tag != Instruction.InstrTag.Br) {
                continue;
            }
            if (instr.getOperandNum() == 1) {
                addEdge(bb, (BasicBlock) instr.getOperand(0));
            } else {
                addEdge(bb, (BasicBlock) instr.getOperand(1));
                addEdge(bb, (BasicBlock) instr.getOperand(2));
            }
        }
    }

    public void addEdge(BasicBlock pred, BasicBlock succ) {
        pred.getSuccessors().add(succ);
        succ.getPredecessors().add(pred);
    }
}
