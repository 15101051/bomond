package midend.pass;

import midend.ir.*;
import midend.ir.Instruction.*;
import utils.IList;
import utils.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;

public class PhiElimination implements Pass {
    @Override
    public boolean run(Module m) {
        for (IList.INode<Function, Module> fNode : m.functionList) {
            Function func = fNode.getValue();
            if (!func.isBuiltin()) {
                run(func);
            }
        }
        return true;
    }

    public void run(Function f) {
        HashMap<Pair<BasicBlock, BasicBlock>, ArrayList<Move>> phiMoves = new HashMap<>();
        for (IList.INode<BasicBlock, Function> bNode : f.getList()) {
            BasicBlock bb = bNode.getValue();
            if (bb.getPredecessors().size() <= 1) {
                assert bb.getList().getEntry().getValue().getTag() != Instruction.InstrTag.Phi;
                continue;
            }
            ArrayList<Instruction.Phi> phis = new ArrayList<>();
            for (IList.INode<Instruction, BasicBlock> iNode : bb.getList()) {
                if (iNode.getValue() instanceof Instruction.Phi) {
                    phis.add((Instruction.Phi) iNode.getValue());
                }
            }
            if (phis.isEmpty()) {
                continue;
            }
            for (int i = 0; i < bb.getPredecessors().size(); ++i) {
                HashMap<Value, Value> phiDst2Src = new HashMap<>();
                BasicBlock predBB = bb.getPredecessors().get(i);
                for (Instruction.Phi phi : phis) {
                    phiDst2Src.put(phi, phi.getOperand(i));
                }
                ArrayList<Move> sequentialMoves = new ArrayList<>();
                while (!phiDst2Src.isEmpty()) {
                    Value cur = phiDst2Src.keySet().iterator().next();
                    Stack<Value> stack = new Stack<>();
                    while (phiDst2Src.containsKey(cur) && !stack.contains(cur)) {
                        stack.push(cur);
                        cur = phiDst2Src.get(cur);
                    }
                    if (!phiDst2Src.containsKey(cur)) {
                        while (!stack.isEmpty()) {
                            Value dst = stack.pop();
                            Move move = new Move(dst, cur, null);
                            sequentialMoves.add(0, move);
                            cur = dst;
                            phiDst2Src.remove(dst);
                        }
                    } else {
                        if (stack.size() != 1) {
                            Move start = new Move(cur, null);
                            sequentialMoves.add(start);
                            for (int si = 0; si < stack.size() - 1; ++si) {
                                Value o = stack.get(si);
                                sequentialMoves.add(new Move(o, phiDst2Src.get(o), null));
                                phiDst2Src.remove(o);
                            }
                            sequentialMoves.add(new Move(stack.get(stack.size() - 1), start, null));
                            phiDst2Src.remove(stack.get(stack.size() - 1));
                        } else {
                            phiDst2Src.remove(stack.get(0));
                        }
                    }
                }
                phiMoves.put(new Pair<>(predBB, bb), sequentialMoves);
            }
        }
        for (Pair<BasicBlock, BasicBlock> pair : phiMoves.keySet()) {
            BasicBlock pred = pair.getFir();
            BasicBlock mcBB = pair.getSec();
            ArrayList<Move> moves = phiMoves.get(pair);
            if (pred.getSuccessors().size() == 1) {
                Instruction last = pred.getList().getLast().getValue();
                assert last instanceof Br;
                for (Move move : moves) {
                    move.node.insertBefore(last.node);
                }
            } else {
                BasicBlock splitBB = new BasicBlock("BB_split__" + pred.getName() + "__" + mcBB.getName(), f);
                splitBB.node.removeSelf();
                splitBB.node.insertAfter(pred.node);
                pred.getSuccessors().remove(mcBB);
                pred.getSuccessors().add(splitBB);
                mcBB.getPredecessors().remove(pred);
                mcBB.getPredecessors().add(splitBB);
                splitBB.getPredecessors().add(pred);
                splitBB.getSuccessors().add(mcBB);
                Br br = (Br) pred.getList().getLast().getValue();
                br.replaceBB(mcBB, splitBB);
                for (Move move : moves) {
                    move.node.insertAtEnd(splitBB.getList());
                }
                new Br(mcBB, splitBB);
            }
        }
    }
}
