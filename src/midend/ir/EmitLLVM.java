package midend.ir;

import utils.IList;

public class EmitLLVM {
    private int counter = 0;
    String outPath ;
    public EmitLLVM(String outPath) {
        this.outPath = outPath;
    }

    private String getCounter() {
        return String.valueOf(counter++);
    }

    private void nameVariable(Module m, StringBuilder sb) {
        for (GlobalVariable gv : m.globalList) {
            if (!gv.getName().startsWith("@")) {
                gv.setName("@" + gv.getName());
            }
        }
        for (IList.INode<Function, Module> f : m.functionList) {
            counter = 0;
            Function func = f.getValue();
            if (!func.isBuiltin()) {
                for (Function.Param arg : func.getParamList()) {
                    arg.setName("%" + getCounter());
                }
                for (IList.INode<BasicBlock, Function> bbInode : func.getList()) {
                    bbInode.getValue().setName(getCounter());
                    for (IList.INode<Instruction, BasicBlock> instNode : bbInode.getValue().getList()) {
                        if (instNode.getValue().needName) {
                            instNode.getValue().setName("%" + getCounter());
                        }
                    }
                }
            } else {
                if (func.getName().equals("printf")) {
                    sb.append("declare void @printf(i8*, ...)\n");
                } else {
                    sb.append("declare ").append(func).append("\n");
                }
            }
        }
    }

    public void run(Module m) {
        StringBuilder sb = new StringBuilder();
        nameVariable(m, sb);
        for (GlobalVariable gv : m.globalList) {
            sb.append(gv).append("\n");
        }
        for (IList.INode<Function, Module> f : m.functionList) {
            Function func = f.getValue();
            if (!func.isBuiltin()) {
                sb.append("\ndefine dso_local ").append(func).append("{").append("\n");
                for (IList.INode<BasicBlock, Function> bbInode : func.getList()) {
                    BasicBlock bb = bbInode.getValue();
                    if (!func.getList().getEntry().equals(bbInode)) {
                        sb.append(bb).append(":").append("\t\t\t;idom:").append(bb.getIdominator()).append(";");
                        sb.append("\t\t\tpreds:");
                        for (BasicBlock pred : bb.getPredecessors()) {
                            sb.append(pred).append(",");
                        }
                        sb.append("\t\t\tsuccs:");
                        for (BasicBlock succ : bb.getSuccessors()) {
                            sb.append(succ).append(",");
                        }
                        sb.append(";\n");
                    }
                    for (IList.INode<Instruction, BasicBlock> instNode : bb.getList()) {
                        sb.append(instNode.getValue().getLLVM()).append("\n");
                    }
                }
                sb.append("}\n");
            }
        }
        try {
            java.io.FileWriter fw = new java.io.FileWriter(outPath);
            fw.write(sb.toString());
            fw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}