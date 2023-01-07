package midend.analysis;

import midend.ir.*;
import midend.pass.GVN;
import utils.IList;

import java.util.*;

public class InterproceduralAnalysis {
    HashMap<GlobalVariable, Boolean> GVmap = new HashMap<>();
    private final HashSet<Function> visited = new HashSet<>();

    public void run(Module m) {
        GVmap.clear();
        for (GlobalVariable gv : m.globalList) {
            GVmap.put(gv, false);
        }
        for (IList.INode<Function, Module> fNode : m.functionList) {
            Function func = fNode.getValue();
            func.getCallees().clear();
            func.getCallers().clear();
            func.setHasSideEffect(func.isBuiltin());
            func.setUseGlobalVariable(func.isBuiltin());
            func.getLoadGVSet().clear();
            func.getStoreGVSet().clear();
        }
        for (IList.INode<Function, Module> fNode : m.functionList) {
            Function f = fNode.getValue();
            if (f.isBuiltin()) {
                continue;
            }
            analyze(f);
        }
        for (GlobalVariable gv: m.globalList) {
            if (!gv.isConstant() && !GVmap.get(gv)) {
                Type type = ((Type.PointerType) gv.getType()).getPointTo();
                if (type instanceof Type.IntegerType) {
                    gv.setConstant(true);
                }
            }
        }
        for (IList.INode<Function, Module> fNode : m.functionList) {
            Function func = fNode.getValue();
            if (func.getName().equals("main")) {
                dfs(func);
                break;
            }
        }
    }

    private void analyze(Function f) {
        for (IList.INode<BasicBlock, Function> bNode : f.getList()) {
            for (IList.INode<Instruction, BasicBlock> iNode : bNode.getValue().getList()) {
                Instruction instr = iNode.getValue();
                for (int i = 0; i < instr.getOperandNum(); ++i) {
                    Value operand = instr.getOperand(i);
                    if (operand instanceof GlobalVariable) {
                        f.setUseGlobalVariable(true);
                    }
                }
                if (instr.getTag() == Instruction.InstrTag.Call) {
                    Instruction.Call call = (Instruction.Call) instr;
                    if (call.getFunc().equals(f)) {
                        f.setRecurrent(true);
                    }
                    if (!call.getFunc().isBuiltin() && !call.getFunc().equals(f)) {
                        if (!f.getCallees().contains(call.getFunc())) {
                            f.getCallees().add(call.getFunc());
                        }
                        if (!call.getFunc().getCallers().contains(f)) {
                            call.getFunc().getCallers().add(f);
                        }
                    }
                    if (call.getFunc().isBuiltin()) {
                        f.setUseGetPrint(true);
                    }
                } else if (instr.getTag() == Instruction.InstrTag.Load) {
                    Value pointer = ((Instruction.Load) instr).getPointer();
                    if (pointer instanceof GlobalVariable) {
                        f.getLoadGVSet().add((GlobalVariable) pointer);
                    }
                } else if (instr.getTag() == Instruction.InstrTag.Store) {
                    Value pointer = ((Instruction.Store) instr).getPointer();
                    if (pointer instanceof GlobalVariable) {
                        GVmap.put((GlobalVariable) pointer, true);
                        f.getStoreGVSet().add((GlobalVariable) pointer);
                    }
                    if (pointer instanceof Instruction.Alloca) {
                        continue;
                    }
                    pointer = ArrayAnalysis.getArray(pointer);
                    if (pointer instanceof GlobalVariable || pointer instanceof Function.Param) {
                        f.setHasSideEffect(true);
                    }
                    if (pointer instanceof GlobalVariable) {
                        GVmap.put((GlobalVariable) pointer, true);
                    }
                }
            }
        }
    }

    public static ArrayList<Function> reversedCallOrder(Module m) {
        ArrayList<Function> res = new ArrayList<>();
        HashMap<Function, Integer> ind = new HashMap<>();
        Queue<Function> q = new LinkedList<>();
        for (IList.INode<Function, Module> fNode : m.functionList) {
            Function f = fNode.getValue();
            if (f.isBuiltin()) {
                continue;
            }
            ind.put(f, f.getCallees().size());
            if (f.getCallees().isEmpty()) {
                q.add(f);
            }
        }
        while (!q.isEmpty()) {
            Function f = q.poll();
            res.add(f);
            for (Function caller : f.getCallers()) {
                ind.put(caller, ind.get(caller) - 1);
                if (ind.get(caller) == 0) {
                    q.add(caller);
                }
            }
        }
        return res;
    }

    public void dfs(Function func) {
        if (visited.contains(func)) {
            return;
        }
        visited.add(func);
        for (Function callee : func.getCallees()) {
            dfs(callee);
            func.getStoreGVSet().addAll(callee.getStoreGVSet());
            func.getLoadGVSet().addAll(callee.getLoadGVSet());
            if (callee.hasSideEffect()) {
                func.setHasSideEffect(true);
            }
            if (callee.useGlobalVariable()) {
                func.setUseGlobalVariable(true);
            }
        }
        if (func.useGetPrint()) {
            func.setHasSideEffect(true);
        }
    }
}
