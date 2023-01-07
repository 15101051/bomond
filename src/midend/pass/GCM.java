package midend.pass;

import midend.analysis.LoopInfoAnalysis;
import midend.ir.*;
import midend.ir.Instruction.*;
import utils.IList;
import java.util.ArrayList;
import java.util.Collections;

public class GCM {
    public void run(Module m) {
        for (IList.INode<Function, Module> fNode : m.functionList) {
            Function f = fNode.getValue();
            if (f.isBuiltin()) {
                continue;
            }
            runGCMOnFunction(f);
        }
    }

    private void runGCMOnFunction(Function f) {
        ArrayList<Instruction> instructions = new ArrayList<>();
        for (IList.INode<BasicBlock, Function> bNode : f.getList()) {
            for (IList.INode<Instruction, BasicBlock> iNode : bNode.getValue().getList()) {
                instructions.add(iNode.getValue());
            }
        }
        for (Instruction instr : instructions) {
            scheduleEarly(instr);
        }
        Collections.reverse(instructions);
        for (Instruction instr : instructions) {
            scheduleLate(instr);
        }
    }

    private void scheduleEarly(Instruction instr) {
        int maxDomLevel = 0;
        switch (instr.tag) {
            case Add: case Sub: case Srem: case Mul: case Sdiv: case Shl: case Eq: case Ne: case Sge: case Sgt: case Sle: case Slt: case And: case Or: case Xor: case MulH: case Lshr: case Ashr:
                Value lhs = instr.getOperand(0);
                Value rhs = instr.getOperand(1);
                if (lhs instanceof Instruction) {
                    maxDomLevel = Math.max(maxDomLevel, ((Instruction) lhs).getParent().getDominanceLevel());
                }
                if (rhs instanceof Instruction) {
                    maxDomLevel = Math.max(maxDomLevel, ((Instruction) rhs).getParent().getDominanceLevel());
                }
                break;
            case Br: case Ret: case Store: case Load: case Alloca: case Phi: case Call:
                return;
            case Zext: case Move:
                Value value0 = instr.getOperand(0);
                if (value0 instanceof Instruction) {
                    maxDomLevel = Math.max(maxDomLevel, ((Instruction) value0).getParent().getDominanceLevel());
                }
                break;
            case GetElementPtr:
                for (int i = 0; i < instr.getOperandNum(); ++i) {
                    Value value = instr.getOperand(i);
                    if (value instanceof Instruction) {
                        maxDomLevel = Math.max(maxDomLevel, ((Instruction) value).getParent().getDominanceLevel());
                    }
                }
                break;
        }
        if (maxDomLevel == instr.getParent().getDominanceLevel()) {
            return;
        }
        BasicBlock cur = instr.getParent();
        while (cur.getDominanceLevel() > maxDomLevel) {
            cur = cur.getIdominator();
        }
        instr.node.removeSelf();
        instr.node.insertBefore(cur.getList().getLast());
    }

    private void scheduleLate(Instruction instr) {
        switch (instr.tag) {
            case Br: case Ret: case Store: case Load: case Alloca: case Phi: case Call:
                return;
        }
        if (instr.getUses().isEmpty()) {
            return;
        }
        BasicBlock lca = null;
        for (Use use : instr.getUses()) {
            Instruction userInst = use.getUser();
            BasicBlock userBB = userInst.getParent();
            if (userInst.tag == InstrTag.Phi) {
                userBB = userBB.getPredecessors().get(use.getOperandRank());
            }
            lca = LCA(lca, userBB);
        }
        BasicBlock finalBB = lca;
        BasicBlock cur = lca;
        LoopInfoAnalysis.LoopInfo loopInfo = instr.getParent().getParent().getLoopInfo();
        while (cur != instr.getParent()) {// System.out.println(cur.getLLVM() + " " + loopInfo.BBLoopDepth(cur));
            if (loopInfo.BBLoopDepth(cur) < loopInfo.BBLoopDepth(finalBB)) {
                finalBB = cur;
            }
            cur = cur.getIdominator();
        }
        if (finalBB == lca) {
            for (IList.INode<Instruction, BasicBlock> iNode : finalBB.getList()) {
                if (iNode.getValue().tag != InstrTag.Phi && iNode.getValue().containOperand(instr)) {
                    instr.node.removeSelf();
                    instr.node.insertBefore(iNode);
                    return;
                }
            }
        }
        instr.node.removeSelf();
        instr.node.insertBefore(finalBB.getList().getLast());
    }

    private BasicBlock LCA(BasicBlock a, BasicBlock b) {
        if (a == null) return b;
        if (b == null) return a;
        while (a.getDominanceLevel() > b.getDominanceLevel()) {
            a = a.getIdominator();
        }
        while (b.getDominanceLevel() > a.getDominanceLevel()) {
            b = b.getIdominator();
        }
        while (a != b) {
            a = a.getIdominator();
            b = b.getIdominator();
        }
        return a;
    }
}
