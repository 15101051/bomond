package midend.ir;

import midend.analysis.LoopInfoAnalysis;
import utils.IList;

import java.util.ArrayList;

public class Function extends Value {
    public final IList.INode<Function, Module> node = new IList.INode<>(this);
    private final IList<BasicBlock, Function> list = new IList<>(this);
    private final ArrayList<Function> callees = new ArrayList<>();
    private final ArrayList<Function> callers = new ArrayList<>();
    private final ArrayList<Param> paramList;
    private final boolean isBuiltin;
    private final LoopInfoAnalysis.LoopInfo loopInfo = new LoopInfoAnalysis.LoopInfo();
    private boolean isRecurrent = false;
    private boolean hasSideEffect = true;
    private boolean useGlobalVariable = false;
    private final ArrayList<GlobalVariable> loadGVSet = new ArrayList<>();
    private final ArrayList<GlobalVariable> storeGVSet = new ArrayList<>();
    private boolean useGetPrint = false;

    public Function(String name, Type type, ArrayList<Param> params, boolean isBuiltin, Module module) {
        super(type, name);
        this.isBuiltin = isBuiltin;
        if (module != null) {
            this.node.insertAtEnd(module.functionList);
        }
        if (params != null) {
            this.paramList = new ArrayList<>(params);
        } else {
            this.paramList = new ArrayList<>();
            Type.FunctionType functionType = (Type.FunctionType) this.getType();
            for (int i = 0; i < functionType.getParamLength(); ++i) {
                paramList.add(new Param(functionType.getParamType(i), ""));
            }
        }
    }

    public Module getParent() {
        return node.getParent().getHolder();
    }

    public ArrayList<Param> getParamList() {
        return paramList;
    }

    public IList<BasicBlock, Function> getList() {
        return list;
    }

    public static class Param extends Value {
        public Param(Type type, String name) {
            super(type, name);
        }
    }

    public boolean isBuiltin() {
        return isBuiltin;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(((Type.FunctionType) this.getType()).getReturnType())
                .append(" @").append(this.getName()).append("(");
        for (int i = 0; i < paramList.size(); ++i) {
            sb.append(paramList.get(i).toString());
            if (i != paramList.size() - 1) {
                sb.append(",");
            }
        }
        sb.append(")");
        return sb.toString();
    }

    public LoopInfoAnalysis.LoopInfo getLoopInfo() {
        return loopInfo;
    }

    public ArrayList<Function> getCallees() {
        return callees;
    }

    public ArrayList<Function> getCallers() {
        return callers;
    }

    public void setRecurrent(boolean recurrent) {
        isRecurrent = recurrent;
    }

    public boolean isRecurrent() {
        return isRecurrent;
    }

    public void setHasSideEffect(boolean hasSideEffect) {
        this.hasSideEffect = hasSideEffect;
    }

    public void setUseGlobalVariable(boolean useGlobalVariable) {
        this.useGlobalVariable = useGlobalVariable;
    }

    public ArrayList<GlobalVariable> getLoadGVSet() {
        return loadGVSet;
    }

    public ArrayList<GlobalVariable> getStoreGVSet() {
        return storeGVSet;
    }

    public boolean hasSideEffect() {
        return hasSideEffect;
    }

    public boolean useGlobalVariable() {
        return useGlobalVariable;
    }

    public void setUseGetPrint(boolean useGetPrint) {
        this.useGetPrint = useGetPrint;
    }

    public boolean useGetPrint() {
        return useGetPrint;
    }

    public boolean isPure() {
        if (((Type.FunctionType) this.getType()).getParamLength() != 1) {
            return false;
        }
        if (!((Type.FunctionType) this.getType()).getParamType(0).isI32Type()) {
            return false;
        }
        if (this.isBuiltin()) {
            return false;
        }
        for (IList.INode<BasicBlock, Function> bNode : this.getList()) {
            BasicBlock block = bNode.getValue();
            for (IList.INode<Instruction, BasicBlock> iNode : block.getList()) {
                Instruction inst = iNode.getValue();
                if (inst instanceof Instruction.Call) {
                    if (!((Instruction.Call) inst).getFunc().equals(this)) {
                        return false;
                    }
                }
                for (int i = 0; i < inst.getOperandNum(); ++i) {
                    if (inst.getOperand(i) instanceof GlobalVariable) {
                        return false;
                    }
                }
                if (inst instanceof Instruction.Alloca) {
                    return false;
                }
            }
        }
        return true;
    }
}
