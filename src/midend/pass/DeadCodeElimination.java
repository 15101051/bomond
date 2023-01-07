package midend.pass;

import midend.ir.*;
import utils.IList;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;

public class DeadCodeElimination {
    HashSet<Instruction> usefulInstrs = new HashSet<>();
    Module m = Module.module;
    boolean changed;
    public boolean run(Module m) {
        changed = false;
        removeDeadFunction();
        for (IList.INode<Function, Module> fNode : m.functionList) {
            Function func = fNode.getValue();
            if (!func.isBuiltin()) {
                run(func);
            }
        }
        return changed;
    }

    public void run(Function func) {
        removeDeadInstruction(func);
    }

    public void removeDeadInstruction(Function func) {
        usefulInstrs.clear();
        for (IList.INode<BasicBlock, Function> bbNode : func.getList()) {
            for (IList.INode<Instruction, BasicBlock> instNode : bbNode.getValue().getList()) {
                Instruction inst = instNode.getValue();
                if (isUseful(inst)) {
                    markInst(inst);
                }
            }
        }
        for (IList.INode<BasicBlock, Function> bbNode : func.getList()) {
            BasicBlock bb = bbNode.getValue();
            for (IList.INode<Instruction, BasicBlock> instNode = bb.getList().getEntry(); instNode.getValue() != null;) {
                Instruction inst = instNode.getValue();
                IList.INode<Instruction, BasicBlock> nxt = instNode.getNext();
                if (!usefulInstrs.contains(inst)) {
                    inst.removeSelf();
                    changed = true;
                }
                instNode = nxt;
            }
        }
    }

    public boolean isUseful(Instruction inst) {
        switch (inst.getTag()) {
            case Br: case Ret: case Store: {
                return true;
            }
            case Call: {
                return ((Instruction.Call) inst).getFunc().hasSideEffect();
            }
            default: {
                return false;
            }
        }
    }

    public void markInst(Instruction inst) {
        Queue<Instruction> q = new LinkedList<>();
        q.offer(inst);
        while (!q.isEmpty()) {
            inst = q.poll();
            if (usefulInstrs.contains(inst)) {
                continue;
            }
            usefulInstrs.add(inst);
            for (int i = 0; i < inst.getOperandNum(); ++i) {
                if (inst.getOperand(i) instanceof Instruction) {
                    q.offer((Instruction) inst.getOperand(i));
                }
            }
        }
    }

    public void removeDeadFunction() {
        Queue<Function> q = new LinkedList<>();
        for (IList.INode<Function, Module> fNode : m.functionList) {
            Function func = fNode.getValue();
            if (!func.isBuiltin() && func.getCallers().isEmpty() && !func.getName().equals("main")) {
                q.offer(func);
            }
        }
        while (!q.isEmpty()) {
            Function func = q.poll();
            for (GlobalVariable gv : m.globalList) {
                gv.getUses().removeIf(
                        use -> use.getUser().getParent().getParent().equals(func)
                );
            }
            func.node.removeSelf();
            changed = true;
            for (Function callee : func.getCallees()) {
                callee.getCallers().remove(func);
                if (callee.getCallers().isEmpty()) {
                    q.offer(callee);
                }
            }
        }
    }
}
