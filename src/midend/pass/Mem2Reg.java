package midend.pass;

import midend.ir.*;
import midend.ir.Instruction.*;
import utils.IList;

import java.util.*;

public class Mem2Reg implements Pass {
    @Override
    public boolean run(Module m) {
        for (IList.INode<Function, Module> fNode : m.functionList) {
            Function f = fNode.getValue();
            if (!f.isBuiltin()) {
                run(f);
            }
        }
        return true;
    }

    private final HashMap<Phi, Alloca> phi2Alloca = new HashMap<>();
    private final HashMap<Alloca, ArrayList<BasicBlock>> defs = new HashMap<>();

    public void run(Function f) {
        phi2Alloca.clear();
        defs.clear();
        insertPhi(f);
        renameVariable(f);
    }

    private void insertPhi(Function f) {
        for (IList.INode<BasicBlock, Function> bNode : f.getList()) {
            BasicBlock bb = bNode.getValue();
            for (IList.INode<Instruction, BasicBlock> iNode : bb.getList()) {
                Instruction instr = iNode.getValue();
                if (instr.getTag() == InstrTag.Alloca) {
                    Alloca alloca = (Alloca) instr;
                    defs.put(alloca, new ArrayList<>());
                } else if (instr.getTag() == InstrTag.Store) {
                    Store store = (Store) instr;
                    if (store.getPointer() instanceof Alloca && defs.containsKey((Alloca) store.getPointer())) {
                        defs.get((Alloca) store.getPointer()).add(bb);
                    }
                }
            }
        }
        for (Alloca alloca : defs.keySet()) {
            HashSet<BasicBlock> F = new HashSet<>();
            Queue<BasicBlock> W = new LinkedList<>(defs.get(alloca));
            while (!W.isEmpty()) {
                BasicBlock X = W.remove();
                for (BasicBlock Y : X.getDominanceFrontier()) {
                    if (!F.contains(Y)) {
                        F.add(Y);
                        Phi phi = new Phi(alloca.getAllocated(), Y.getPredecessors().size(), null);
                        phi.node.insertAtEntry(Y.getList());
                        phi2Alloca.put(phi, alloca);
                        if (!defs.get(alloca).contains(Y)) {
                            W.add(Y);
                        }
                    }
                }
            }
        }
    }

    private void renameVariable(Function f) {
        HashMap<Alloca, Value> renameValues = new HashMap<>();
        for (Alloca alloca : defs.keySet()) {
            if (alloca.getAllocated() == Type.IntegerType.getI32()) {
                renameValues.put(alloca, new Constant.ConstantInt(0));
            }
        }
        dfsBB(f.getList().getEntry().getValue(), renameValues);
    }

    private void dfsBB(BasicBlock bb, HashMap<Alloca, Value> renameValues) {
        HashMap<Alloca, Value> newRenameValues = new HashMap<>(renameValues);
        ArrayList<Instruction> instructions = new ArrayList<>();
        for (IList.INode<Instruction, BasicBlock> iNode : bb.getList()) {
            instructions.add(iNode.getValue());
        }
        for (Instruction instr : instructions) {
            switch (instr.getTag()) {
                case Alloca: {
                    if (newRenameValues.containsKey((Alloca) instr)) {
                        instr.node.removeSelf();
                    }
                    break;
                }
                case Load: {
                    Load load = (Load) instr;
                    if (load.getPointer() instanceof Alloca) {
                        Alloca alloca = (Alloca) load.getPointer();
                        if (newRenameValues.containsKey(alloca)){
                            load.removeSelf(newRenameValues.get(alloca));
                        }
                    }
                    break;
                }
                case Store: {
                    Store store = (Store) instr;
                    if (store.getPointer() instanceof Alloca) {
                        Alloca alloca = (Alloca) store.getPointer();
                        if (newRenameValues.containsKey(alloca)) {
                            newRenameValues.put(alloca, ((Store) instr).getValue());
                            store.removeSelf();
                        }
                    }
                    break;
                }
                case Phi: {
                    Phi phi = (Phi) instr;
                    Alloca alloca = phi2Alloca.get(phi);
                    newRenameValues.put(alloca, phi);
                    break;
                }
            }
        }
        for (BasicBlock succ : bb.getSuccessors()) {
            for (IList.INode<Instruction, BasicBlock> iNode : succ.getList()) {
                Instruction instr = iNode.getValue();
                if (instr.getTag() == InstrTag.Phi) {
                    Phi phi = (Phi) instr;
                    int index = succ.getPredecessors().indexOf(bb);
                    Value value = newRenameValues.get(phi2Alloca.get(phi));
                    phi.setOperand(index, value);
                }
            }
        }
        for (BasicBlock dom : bb.getIdominateds()) {
            dfsBB(dom, newRenameValues);
        }
    }
}
