package midend.pass;

import midend.ir.*;
import utils.IList;
import utils.ValueCopy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public class FunctionInline {
    public void run(Module m) {
        ArrayList<Function> inlineFunctions = new ArrayList<>();
        for (IList.INode<Function, Module> fNode : m.functionList) {
            Function func = fNode.getValue();
            if (func.getName().equals("main") || func.isBuiltin()) {
                continue;
            }
            if (!func.isRecurrent()) {
                inlineFunctions.add(func);
            }
        }
        for (Function func : inlineFunctions) {
            inlineFunction(func);
        }
        new BranchOptimization().run(m);
    }

    private void inlineFunction(Function f) {
        ArrayList<Instruction.Call> calls = new ArrayList<>();
        for (Use use : f.getUses()) {
            calls.add((Instruction.Call) use.getUser());
        }
        for (Instruction.Call call : calls) {
            inlineCall(call);
        }
        for (Function caller : f.getCallers()) {
            caller.getCallees().removeIf(callee -> callee.equals(f));
            caller.getCallees().addAll(f.getCallees());
        }
        for (Function callee : f.getCallees()) {
            callee.getCallers().removeIf(caller -> caller.equals(f));
            callee.getCallers().addAll(f.getCallers());
        }
        f.node.removeSelf();
    }

    private void inlineCall(Instruction.Call call) {
        Function f = call.getFunc();
        BasicBlock curBB = call.node.getParent().getHolder();
        BasicBlock nxtBB = new BasicBlock("function_inline_next__" + f.getName() + (++Module.module.basicBlockIdx), null);
        nxtBB.node.insertAfter(curBB.node);
        Function funcCopy = new ValueCopy(new HashMap<>()).copyFunction(f);
        Instruction.Br entryBr = new Instruction.Br(funcCopy.getList().getEntry().getValue(), null);
        ArrayList<Instruction> afterCallInstructions = new ArrayList<>();
        for (IList.INode<Instruction, BasicBlock> instrINode = call.node.getNext(); !instrINode.isGuard(); instrINode = instrINode.getNext()) {
            afterCallInstructions.add(instrINode.getValue());
        }
        for (Instruction instr : afterCallInstructions) {
            instr.node.removeSelf();
            instr.node.insertAtEnd(nxtBB.getList());
        }
        entryBr.node.insertAtEnd(curBB.getList());
        Type.FunctionType functionType = ((Type.FunctionType) funcCopy.getType());
        Type retType = functionType.getReturnType();
        if (retType.isVoidType()) {
            ArrayList<Instruction.Ret> retInstrs = new ArrayList<>();
            for (IList.INode<BasicBlock, Function> bbINode : funcCopy.getList()) {
                for (IList.INode<Instruction, BasicBlock> instrINode : bbINode.getValue().getList()) {
                    if (instrINode.getValue() instanceof Instruction.Ret) {
                        retInstrs.add((Instruction.Ret) instrINode.getValue());
                    }
                }
            }
            for (Instruction.Ret retInstr : retInstrs) {
                BasicBlock retBB = retInstr.node.getParent().getHolder();
                nxtBB.getPredecessors().add(retBB);
                retBB.getSuccessors().add(nxtBB);
                Instruction.Br br = new Instruction.Br(nxtBB, null);
                br.node.insertBefore(retInstr.node);
                retInstr.node.removeSelf();
            }
        } else if (retType.isI32Type()) {
            ArrayList<Instruction.Ret> retInstrs = new ArrayList<>();
            for (IList.INode<BasicBlock, Function> bbINode : funcCopy.getList()) {
                for (IList.INode<Instruction, BasicBlock> instrINode : bbINode.getValue().getList()) {
                    if (instrINode.getValue() instanceof Instruction.Ret) {
                        retInstrs.add((Instruction.Ret) instrINode.getValue());
                    }
                }
            }
            Instruction.Phi phi = new Instruction.Phi(retType, retInstrs.size(), null);
            for (Instruction.Ret retInstr : retInstrs) {
                BasicBlock retBB = retInstr.node.getParent().getHolder();
                nxtBB.getPredecessors().add(retBB);
                retBB.getSuccessors().add(nxtBB);
            }
            phi.node.insertAtEntry(nxtBB.getList());
            for (int i = 0; i < nxtBB.getPredecessors().size(); ++i) {
                BasicBlock retBB = nxtBB.getPredecessors().get(i);
                Instruction.Ret retInstr = (Instruction.Ret) retBB.getList().getLast().getValue();
                new Instruction.Br(nxtBB, retBB);
                phi.setOperand(i, retInstr.getOperand(0));
                retInstr.removeSelf();
            }
            call.replaceSelfWith(phi);
        }
        ArrayList<Function.Param> params = funcCopy.getParamList();
        for (int i = 0; i < params.size(); ++i) {
            Function.Param param = params.get(i);
            Value callerValue = call.getOperand(i + 1);
            param.replaceSelfWith(callerValue);
        }
        nxtBB.getSuccessors().addAll(curBB.getSuccessors());
        for (BasicBlock bb : nxtBB.getSuccessors()) {
            for (int i = 0; i < bb.getPredecessors().size(); ++i) {
                if (bb.getPredecessors().get(i).equals(curBB)) {
                    bb.getPredecessors().set(i, nxtBB);
                }
            }
        }
        curBB.getSuccessors().clear();
        curBB.getSuccessors().add(funcCopy.getList().getEntry().getValue());
        funcCopy.getList().getEntry().getValue().getPredecessors().add(curBB);
        ArrayList<BasicBlock> bbs = new ArrayList<>();
        for (IList.INode<BasicBlock, Function> bbINode : funcCopy.getList()) {
            bbs.add(bbINode.getValue());
        }
        funcCopy.node.removeSelf();
        Collections.reverse(bbs);
        for (BasicBlock bb : bbs) {
            bb.node.removeSelf();
            bb.node.insertAfter(curBB.node);
        }
        call.removeSelf();
    }
}
