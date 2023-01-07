package midend.pass;

import midend.ir.*;
import midend.ir.Instruction.*;
import utils.IList;

import java.util.HashMap;

public class LoadStoreInBlock {
    public void run(Module module) {
        for (IList.INode<Function, Module> fNode : module.functionList) {
            if (!fNode.getValue().isBuiltin()) {
                run(fNode.getValue());
            }
        }
    }

    private void run(Function function) {
        for (IList.INode<BasicBlock, Function> bNode : function.getList()) {
            run(bNode.getValue());
        }
    }

    public static void removeBrFlow(BasicBlock fr, BasicBlock to) {
        int pos = to.getPredecessors().indexOf(fr);
        for (IList.INode<Instruction, BasicBlock> iNode : to.getList()) {
            if (iNode.getValue() instanceof Instruction.Phi) {
                iNode.getValue().removeOperand(pos);
            }
        }
        to.getPredecessors().remove(pos);
        fr.getSuccessors().remove(to);
    }

    private void replaceInstruction(Instruction origin, Value value) {
        // 这里只换了Use，如果需要insert到List里要单独加
        if (origin == value) {
            return;
        }// System.out.println("replace : " + origin + " - " + value);
        if (origin.tag == Instruction.InstrTag.Br) {
            assert origin.getOperandNum() == 3;
            assert value instanceof Br;
            assert ((Br) value).getOperandNum() == 1;
            BasicBlock nxt = (BasicBlock) ((Br) value).getOperand(0);
            if (nxt.equals(origin.getOperand(1))) {
                removeBrFlow(origin.getParent(), (BasicBlock) origin.getOperand(2));
            } else {
                removeBrFlow(origin.getParent(), (BasicBlock) origin.getOperand(1));
            }
        }
        origin.removeSelf(value);
    }

    private void run(BasicBlock bb) {
        HashMap<Value, Value> loadedValues = new HashMap<>(); // ptr -> value
        for (IList.INode<Instruction, BasicBlock> iNode : bb.getList()) {
            Instruction instruction = iNode.getValue();
            if (instruction.tag == InstrTag.Load) {
                if (loadedValues.containsKey(((Load) instruction).getPointer())) {
                    replaceInstruction(instruction, loadedValues.get(((Load) instruction).getPointer()));
                } else {
                    loadedValues.put(((Load) instruction).getPointer(), instruction);
                }
            } else if (instruction.tag == InstrTag.Store) {
                loadedValues.clear();
                // System.out.println("Store : " + ((MemInst.Store) instruction).getPointer() + " - " + ((MemInst.Store) instruction).getValue());
                loadedValues.put(((Store) instruction).getPointer(), ((Store) instruction).getValue());
            } else if (instruction.tag == InstrTag.Call) {
                loadedValues.clear();
            }
        }
    }
}
