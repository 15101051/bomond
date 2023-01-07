package midend.pass;

import midend.ir.*;
import utils.IList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;

public class BranchOptimization implements Pass {
    public boolean run(Module m) {
        boolean changed = false;
        for (IList.INode<Function, Module> funcNode : m.functionList) {
            Function func = funcNode.getValue();
            if (!funcNode.getValue().isBuiltin()) {
                changed |= run(func);
            }
        }
        return changed;
    }

    private boolean run(Function func) {
        boolean changed;
        boolean res = false;
        while (true) {
            changed = removeUnreachableBasicBlock(func);
            changed |= removeUselessPhi(func);
            changed |= singleUnconditionalBr(func);
            changed |= mergeUnconditionalBr(func);
            changed |= mergeSameBr(func);
            if (!changed) {
                break;
            }
            res = true;
        }
        return res;
    }

    private boolean removeUselessPhi(Function func) {
        boolean changed = false;
        for (IList.INode<BasicBlock, Function> bNode : func.getList()) {
            BasicBlock bb = bNode.getValue();
            for (IList.INode<Instruction, BasicBlock> iNode : bb.getList()) {
                Instruction instr = iNode.getValue();
                if (!(instr instanceof Instruction.Phi)) {
                    break;
                }
                if (bb.getPredecessors().size() == 1) {
                    assert instr.getOperandNum() == 1;
                    instr.removeSelf(instr.getOperand(0));
                    changed = true;
                } else if (bb.getPredecessors().size() == 0) {
                    assert instr.getOperandNum() == 0;
                    iNode.removeSelf();
                    changed = true;
                } else {
                    boolean allSame = true;
                    for (int i = 1; i < instr.getOperandNum(); ++i) {
                        if (!instr.getOperand(i).equals(instr.getOperand(0))) {
                            allSame = false;
                        }
                    }
                    if (allSame) {
                        instr.removeSelf(instr.getOperand(0));
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    private boolean singleUnconditionalBr(Function f) {
        boolean changed = false;
        for (IList.INode<BasicBlock, Function> bNode : f.getList()) {
            BasicBlock bb = bNode.getValue();
            if (bb.getList().getNodeNum() != 1 || bb.equals(f.getList().getEntry().getValue())) {
                continue;
            }
            Instruction instr = bb.getList().getEntry().getValue();
            if (!(instr instanceof Instruction.Br) || instr.getOperandNum() != 1) {
                continue;
            }
            boolean canRemove = true;
            BasicBlock succ = (BasicBlock) instr.getOperand(0);
            if (succ.getList().getEntry().getValue() instanceof Instruction.Phi) {
                for (BasicBlock pred : bb.getPredecessors()) {
                    if (succ.getPredecessors().contains(pred)) {
                        canRemove = false;
                        break;
                    }
                }
            }
            if (!canRemove) {
                continue;
            }
            int index = succ.getPredecessors().indexOf(bb);
            succ.getPredecessors().remove(index);
            for (BasicBlock pred : bb.getPredecessors()) {
                Instruction.Br br = (Instruction.Br) pred.getList().getLast().getValue();
                if (br.getOperandNum() == 1) {
                    br.setOperand(0, succ);
                } else {
                    if (br.getOperand(1) == bb) {
                        br.setOperand(1, succ);
                    } else {
                        br.setOperand(2, succ);
                    }
                }
                pred.getSuccessors().remove(bb);
                pred.getSuccessors().add(succ);
                succ.getPredecessors().add(pred);
            }
            for (IList.INode<Instruction, BasicBlock> iNode : succ.getList()) {
                if (iNode.getValue() instanceof Instruction.Phi) {
                    Instruction.Phi phi = (Instruction.Phi) iNode.getValue();
                    Value val = phi.getOperand(index);
                    phi.removeOperand(index);
                    for (BasicBlock ignored : bb.getPredecessors()) {
                        phi.addOperand(val);
                    }
                } else {
                    break;
                }
            }
            bb.removeSelf();
        }
        return changed;
    }

    private boolean mergeUnconditionalBr(Function f) {
        boolean changed = false;

        return changed;
    }

    private boolean mergeSameBr(Function f) {
        boolean changed = false;
        for (IList.INode<BasicBlock, Function> bNode : f.getList()) {
            BasicBlock bb = bNode.getValue();
            if (!(bb.getList().getLast().getValue() instanceof Instruction.Br)) {
                continue;
            }
            Instruction.Br br = (Instruction.Br) bb.getList().getLast().getValue();
            if (br.getOperandNum() == 1) {
                continue;
            }
            if (br.getOperand(1).equals(br.getOperand(2))) {
                br.removeOperand(2);
                br.removeOperand(0);
                changed = true;
            }
        }
        return changed;
    }

    private boolean removeUnreachableBasicBlock(Function f) {
        HashSet<BasicBlock> blockVisited = new HashSet<>();
        BasicBlock start = f.getList().getEntry().getValue();
        Queue<BasicBlock> q = new LinkedList<>();
        q.add(start);
        blockVisited.add(start);
        while (!q.isEmpty()) {
            BasicBlock bb = q.poll();
            if (!(bb.getList().getLast().getValue() instanceof Instruction.Br)) {
                continue;
            }
            Instruction.Br br = (Instruction.Br) bb.getList().getLast().getValue();
            if (br.getOperandNum() == 1) {
                BasicBlock succ = (BasicBlock) br.getOperand(0);
                if (!blockVisited.contains(succ)) {
                    blockVisited.add(succ);
                    q.add(succ);
                }
            } else {
                BasicBlock succ1 = (BasicBlock) br.getOperand(1);
                BasicBlock succ2 = (BasicBlock) br.getOperand(2);
                if (!blockVisited.contains(succ1)) {
                    blockVisited.add(succ1);
                    q.add(succ1);
                }
                if (!blockVisited.contains(succ2)) {
                    blockVisited.add(succ2);
                    q.add(succ2);
                }
            }
        }
        ArrayList<BasicBlock> removedBBs = new ArrayList<>();
        for (IList.INode<BasicBlock, Function> bNode : f.getList()) {
            BasicBlock bb = bNode.getValue();
            if (!blockVisited.contains(bb)) {
                removedBBs.add(bb);
            }
        }
        boolean changed = !removedBBs.isEmpty();
        for (BasicBlock bb : removedBBs) {
            ArrayList<BasicBlock> succs = new ArrayList<>(bb.getSuccessors());
            for (BasicBlock succ : succs) {
                removeBrFlow(bb, succ);
            }
            ArrayList<Instruction> removedInstructions = new ArrayList<>();
            for (IList.INode<Instruction, BasicBlock> iNode : bb.getList()) {
                removedInstructions.add(iNode.getValue());
            }
            for (Instruction instr : removedInstructions) {
                instr.removeSelf();
            }
            bb.removeSelf();
        }
        return changed;
    }

    private void removeBrFlow(BasicBlock from, BasicBlock to) {
        int pos = to.getPredecessors().indexOf(from);
        for (IList.INode<Instruction, BasicBlock> iNode : to.getList()) {
            Instruction instr = iNode.getValue();
            if (instr instanceof Instruction.Phi) {
                instr.removeOperand(pos);
            } else {
                break;
            }
        }
        to.getPredecessors().remove(pos);
        from.getSuccessors().remove(to);
    }
}
