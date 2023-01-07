package backend.mc;

import midend.ir.BasicBlock;
import midend.ir.Function;
import midend.ir.Instruction;
import utils.IList;

import java.util.ArrayList;
import java.util.HashSet;

public class MCBasicBlock {
    private final IList<MCInstr, MCBasicBlock> list = new IList<>(this);
    public final IList.INode<MCBasicBlock, MCFunction> node = new IList.INode<>(this);
    private final MCFunction mcf;
    private final BasicBlock irBB;
    public final HashSet<MCOperand> liveUse = new HashSet<>();
    public final HashSet<MCOperand> liveDef = new HashSet<>();
    public final HashSet<MCOperand> liveIn = new HashSet<>();
    public final HashSet<MCOperand> liveOut = new HashSet<>();
    private final ArrayList<MCBasicBlock> succ = new ArrayList<>();
    private final ArrayList<MCBasicBlock> pred = new ArrayList<>();
    private final MCOperand.MCLabel label;

    public MCBasicBlock(BasicBlock irBB, MCOperand.MCLabel label, MCFunction mcf, MCInstrFactory instrFactory) {
        this.node.insertAtEnd(mcf.getList());
        this.mcf = mcf;
        this.label = label;
        this.irBB = irBB;
        MCModule.module.label2BB.put(label, this);
        if (irBB != null) {
            instrFactory.setMcBB(this);
            Function func = mcf.getFunc();
            if (irBB.node.equals(func.getList().getEntry())) {
                for (Function.Param param : func.getParamList()) {
                    instrFactory.irOp2mcR(param);
                }
            }
            for (IList.INode<Instruction, BasicBlock> iNode : irBB.getList()) {
                instrFactory.buildInstr(iNode.getValue());
            }
        }
    }

    public MCFunction getMCFunction() {
        return this.mcf;
    }

    public IList<MCInstr, MCBasicBlock> getList() {
        return list;
    }

    public int getLoopDepth() {
        return this.mcf.getFunc().getLoopInfo().BBLoopDepth(this.irBB);
    }

    public ArrayList<MCBasicBlock> getSucc() {
        return succ;
    }

    public ArrayList<MCBasicBlock> getPred() {
        return pred;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.label).append(":\n");
        for (IList.INode<MCInstr, MCBasicBlock> iNode : list) {
            sb.append(iNode.getValue());
        }
        return sb.toString();
    }

    public MCOperand.MCLabel getLabel() {
        return this.label;
    }

    public void replaceSucc(MCBasicBlock oldBB, MCBasicBlock newBB) {
        for (IList.INode<MCInstr, MCBasicBlock> iNode : this.getList()) {
            MCInstr instr = iNode.getValue();
            if (instr.getTag().isBranchE()) {
                MCInstr.MCBranchE br = (MCInstr.MCBranchE) instr;
                if (br.getTarget().equals(oldBB.getLabel())) {
                    br.setTarget(newBB.getLabel());
                }
            } else if (instr.getTag().isBranchZ()) {
                MCInstr.MCBranchZ br = (MCInstr.MCBranchZ) instr;
                if (br.getTarget().equals(oldBB.getLabel())) {
                    br.setTarget(newBB.getLabel());
                }
            } else if (instr.getTag().isJ()) {
                MCInstr.MCJ j = (MCInstr.MCJ) instr;
                if (j.getTarget().equals(oldBB.getLabel())) {
                    j.setTarget(newBB.getLabel());
                }
            }
        }
    }
}