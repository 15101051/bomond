package midend.ir;

import midend.pass.Pass;
import utils.IList;

import java.util.HashMap;
import java.util.HashSet;

public class IRValidator implements Pass {
    void validateBB(BasicBlock basicBlock, Function function) {
        assert basicBlock.node.getParent().getHolder().equals(function) : "block does not belong to function.";
        for (IList.INode<Instruction, BasicBlock> iNode : basicBlock.getList()) {
            Instruction instruction = iNode.getValue();
            if (instruction instanceof Instruction.Call) {
                assert ((Instruction.Call) instruction).getFunc() != null : "call instruction has no function.";
                if (function.equals(((Instruction.Call) instruction).getFunc())) {
                    continue;
                }
                if (((Instruction.Call) instruction).getFunc().isBuiltin()) {
                    continue;
                }
                realCaller.get(((Instruction.Call) instruction).getFunc()).add(function);
                realCallee.get(function).add(((Instruction.Call) instruction).getFunc());
            }
            assert instruction.node.getParent().getHolder().equals(basicBlock) : "instruction does not belong to block.";
            for (Use use : instruction.getUses()) {
                assert use.getUser().getParent().getParent().equals(function) : "use does not belong to function.";
            }
            for (int i = 0; i < instruction.getOperandNum(); ++i) {
                Value operand = instruction.getOperand(i);
                assert !(operand instanceof Instruction) || ((Instruction) operand).getParent().getParent().equals(function) : "operand does not belong to function.";
            }
        }
    }

    private final HashMap<Function, HashSet<Function>> realCaller = new HashMap<>();
    private final HashMap<Function, HashSet<Function>> realCallee = new HashMap<>();

    @Override
    public boolean run(Module m) {
        for (IList.INode<Function, Module> fNode : m.functionList) {
            realCaller.put(fNode.getValue(), new HashSet<>());
            realCallee.put(fNode.getValue(), new HashSet<>());
        }
        for (IList.INode<Function, Module> fNode : m.functionList) {
            Function function = fNode.getValue();
            for (IList.INode<BasicBlock, Function> bNode : function.getList()) {
                BasicBlock basicBlock = bNode.getValue();
                validateBB(basicBlock, function);
            }
            for (Function callee : function.getCallees()) {
                assert callee.getParent().equals(m) : "callee does not belong to module.";
            }
            for (Function caller : function.getCallers()) {
                assert caller.getParent().equals(m) : "caller does not belong to module.";
            }
        }
        for (IList.INode<Function, Module> fNode : m.functionList) {
            Function function = fNode.getValue();
            if (function.isBuiltin()) {
                assert function.getCallers().isEmpty() : "builtin function has caller.";
                assert function.getCallees().isEmpty() : "builtin function has callee.";
                continue;
            }
            for (Function callee : function.getCallees()) {
                assert realCallee.get(function).contains(callee) : "callee not found." + function + " " + function.getCallees() + " " + realCallee.get(function);
            }
            for (Function caller : function.getCallers()) {
                assert realCaller.get(function).contains(caller) : "caller not found." + function + " " + function.getCallers() + " " + realCaller.get(function);
            }
            for (Function callee : realCallee.get(function)) {
                assert function.getCallees().contains(callee) : "callee not found." + function + " " + function.getCallees() + " " + realCallee.get(function);
            }
            for (Function caller : realCaller.get(function)) {
                assert function.getCallers().contains(caller) : "caller not found." + function + " " + function.getCallers() + " " + realCaller.get(function);
            }
        }
        return true;
    }
}
