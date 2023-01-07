package utils;

import midend.ir.BasicBlock;
import midend.ir.Function;
import midend.ir.Instruction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;

public class BasicBlockToposort {
    public static ArrayList<BasicBlock> basicBlockToposort(Function f) {
        HashSet<BasicBlock> blockVisited = new HashSet<>();
        ArrayList<BasicBlock> res = new ArrayList<>();
        BasicBlock start = f.getList().getEntry().getValue();
        Queue<BasicBlock> q = new LinkedList<>();
        q.add(start);
        blockVisited.add(start);
        while (!q.isEmpty()) {
            BasicBlock bb = q.poll();
            res.add(bb);
            for (BasicBlock successor : bb.getSuccessors()) {
                if (blockVisited.contains(successor)) {
                    continue;
                }
                boolean ok = true;
                for (IList.INode<Instruction, BasicBlock> iter : successor.getList()) {
                    Instruction instr = iter.getValue();
                    if (instr.tag == Instruction.InstrTag.Phi) {
                        continue;
                    }
                    for (int i = 0; i < instr.getOperandNum(); ++i) {
                        if (instr.getOperand(i) instanceof Instruction) {
                            if (!blockVisited.contains(((Instruction) instr.getOperand(i)).getParent()) &&
                                    !((Instruction) instr.getOperand(i)).getParent().equals(successor)) {
                                ok = false;
                                break;
                            }
                        }
                    }
                    if (!ok) {
                        break;
                    }
                }
                if (ok) {
                    blockVisited.add(successor);
                    q.add(successor);
                }
            }
        }
        return res;
    }
}
