package backend.mc;

import backend.mc.MCOperand.*;
import backend.register.FullRegisterAllocator;
import backend.register.NaiveRegisterAllocator;
import backend.register.RegisterAllocator;
import backend.register.RegisterManager;
import midend.ir.BasicBlock;
import midend.ir.Function;
import midend.ir.Instruction;
import midend.ir.Value;
import utils.Config;
import utils.IList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class MCFunction {
    private final Function func;
    private final String name;
    private final IList<MCBasicBlock, MCFunction> list = new IList<>(this);
    private final HashMap<BasicBlock, MCLabel> BBLabelMap = new HashMap<>();
    private int stackSize = 0;
    private final boolean isBuiltin;
    private final HashMap<Value, MCReg> value2MCReg = new HashMap<>();
    private final HashMap<Instruction.GEP, Integer> gepOffsets = new HashMap<>();
    private final HashMap<String, MCVirtualReg> str2Reg = new HashMap<>();
    private final HashSet<MCPhyReg> regToSave = new HashSet<>();
    private final HashMap<MCReg, MCImm> allocaImm = new HashMap<>();
    private final HashMap<MCOperand, MCGlobalVariable> reg2Gv = new HashMap<>();
    private final MCInstrFactory mcInstrFactory = new MCInstrFactory(this);
    private final ArrayList<MCInstr.MCLw> lwsToGetArg = new ArrayList<>();
    private final MCPhyReg RA = new MCPhyReg(RegisterManager.MCPhyRegTag.ra);
    private final MCPhyReg SP = new MCPhyReg(RegisterManager.MCPhyRegTag.sp);

    public MCFunction(Function func) {
        this.func = func;
        this.name = func.getName();
        MCModule.module.functions.put(name, this);
        gepOffsets.clear();
        this.isBuiltin = func.isBuiltin();
        if (!this.isBuiltin) {
            workOnBB();
            allocateRegisters();
            addInOutMoveSp();
            expandCalls();
            ArrayList<MCOperand.MCPhyReg> regTempSave = this.getDefRegsExceptRaSp();
            regTempSave.remove(new MCOperand.MCPhyReg(RegisterManager.MCPhyRegTag.v0));
            for (MCInstr.MCLw lw : this.getLwsToGetArg()) {
                lw.setOffset(new MCOperand.MCImm(lw.getOffset().getImm() + this.getStackSize() + (regTempSave.size() + 1) * 4, false));
            }
        }
    }

    private void expand(MCInstr.MCCall call, ArrayList<MCPhyReg> regTempSave) {
        regTempSave.remove(new MCPhyReg(RegisterManager.MCPhyRegTag.v0));
        int saveSize = 4 * (call.getArgsCnt() + 1 + regTempSave.size());
        MCInstr.buildSwBefore(RA, new MCImm(-4 * (call.getArgsCnt() + 1)), SP, call);
        for (int i = 0; i < regTempSave.size(); ++i) {
            MCInstr.buildSwBefore(regTempSave.get(i), new MCImm(i * 4 - saveSize), SP, call);
        }
        MCInstr.buildBinaryIBefore(MCInstr.MCInstrTag.addiu, SP, SP, new MCImm(-saveSize), call);
        MCInstr.buildJBefore(MCInstr.MCInstrTag.jal, call.getCallee().getList().getEntry().getValue().getLabel(), call);
        MCInstr.buildBinaryIBefore(MCInstr.MCInstrTag.addiu, SP, SP, new MCImm(saveSize), call);
        for (int i = 0; i < regTempSave.size(); ++i) {
            MCInstr.buildLwBefore(regTempSave.get(i), new MCImm(i * 4 - saveSize), SP, call);
        }
        MCInstr.buildLwBefore(RA, new MCImm(-4 * (call.getArgsCnt() + 1)), SP, call);
        call.node.removeSelf();
    }

    private void expandCalls() {
        for (IList.INode<MCBasicBlock, MCFunction> bNode : this.getList()) {
            MCBasicBlock bb = bNode.getValue();
            for (IList.INode<MCInstr, MCBasicBlock> iNode : bb.getList()) {
                MCInstr instr = iNode.getValue();
                if (instr.getTag() == MCInstr.MCInstrTag.call) {
                    MCInstr.MCCall mcCall = (MCInstr.MCCall) instr;
                    ArrayList<MCPhyReg> protectRegs;
                    if (mcCall.getCallee().equals(this)) {
                        protectRegs = new ArrayList<>(this.getChildDefRegs());
                    } else {
                        protectRegs = new ArrayList<>(mcCall.getCallee().getDefRegsExceptRaSp());
                    }
                    expand(mcCall, protectRegs);
                }
            }
        }
    }

    private void addInOutMoveSp() {
        MCOperand.MCReg SP = new MCOperand.MCPhyReg(RegisterManager.MCPhyRegTag.sp);
        MCInstr.buildBinaryIAtEntry(MCInstr.MCInstrTag.addiu, SP, SP, new MCOperand.MCImm(-this.getStackSize(), false), this.getList().getEntry().getValue());
        for (IList.INode<MCBasicBlock, MCFunction> bNode : this.getList()) {
            MCBasicBlock bb = bNode.getValue();
            for (IList.INode<MCInstr, MCBasicBlock> iNode : bb.getList()) {
                MCInstr instr = iNode.getValue();
                if (instr.getTag() == MCInstr.MCInstrTag.jr) {
                    MCInstr.buildBinaryIBefore(MCInstr.MCInstrTag.addiu, SP, SP, new MCOperand.MCImm(this.getStackSize(), false), instr);
                }
            }
        }
    }

    private void workOnBB() {
        for (IList.INode<BasicBlock, Function> bNode : func.getList()) {
            if (bNode.isEntry()) {
                BBLabelMap.put(bNode.getValue(), new MCLabel(func.getName()));
            } else {
                BBLabelMap.put(bNode.getValue(), new MCLabel("BB_" + this.getName() + "_" + bNode.getValue().getName()));
            }
        }
        for (IList.INode<BasicBlock, Function> bNode : func.getList()) {
            BasicBlock bb = bNode.getValue();
            new MCBasicBlock(bb, BBLabelMap.get(bb), this, mcInstrFactory);
        }
        for (IList.INode<BasicBlock, Function> bNode : func.getList()) {
            BasicBlock bb = bNode.getValue();
            MCBasicBlock mcBB = BB2mcBB(bb);
            for (BasicBlock succ : bb.getSuccessors()) {
                mcBB.getSucc().add(BB2mcBB(succ));
                BB2mcBB(succ).getPred().add(mcBB);
            }
        }
    }

    private void allocateRegisters() {
        if (Config.registerAllocatorChoice == RegisterAllocator.RegisterAllocatorChoice.naive) {
            new NaiveRegisterAllocator(this).allocate();
        } else if (Config.registerAllocatorChoice == RegisterAllocator.RegisterAllocatorChoice.full) {
            new FullRegisterAllocator(this, RegisterManager.getAllocatableRegisters()).allocate();
        } else {
            throw new RuntimeException("unImplemented register allocator");
        }
    }

    private ArrayList<MCPhyReg> getChildDefRegs() {
        ArrayList<MCOperand.MCPhyReg> childDefRegs = new ArrayList<>();
        for (Function child : func.getCallees()) {
            MCFunction childMcf = MCModule.module.functions.get(child.getName());
            for (MCOperand.MCPhyReg pr : childMcf.getDefRegsExceptRaSp()) {
                if (!childDefRegs.contains(pr)) {
                    childDefRegs.add(pr);
                }
            }
        }
        if (func.isRecurrent()) {
            for (MCOperand.MCPhyReg pr : this.getDefRegsExceptRaSp()) {
                if (!childDefRegs.contains(pr)) {
                    childDefRegs.add(pr);
                }
            }
        }
        return childDefRegs;
    }

    public MCBasicBlock BB2mcBB(BasicBlock bb) {
        return MCModule.module.label2BB.get(this.BBLabelMap.get(bb));
    }

    public String getMIPS() {
        if (this.isBuiltin) {
            return "# " + getName() + "\n";
        }
        StringBuilder sb = new StringBuilder();
        for (IList.INode<MCBasicBlock, MCFunction> bNode : this.list) {
            sb.append(bNode.getValue());
        }
        return sb.toString();
    }

    public String getName() {
        return "func_" + name;
    }

    public int getStackSize() {
        return stackSize;
    }

    public boolean isBuiltin() {
        return this.isBuiltin;
    }

    public IList<MCBasicBlock, MCFunction> getList() {
        return list;
    }

    public HashMap<MCOperand, MCGlobalVariable> reg2Gv() {
        return reg2Gv;
    }

    public HashMap<MCReg, MCImm> getAllocaImm() {
        return allocaImm;
    }

    public HashMap<String, MCVirtualReg> str2Reg() {
        return str2Reg;
    }

    public void addStackSize(int add) {
        this.stackSize += add;
    }

    public ArrayList<MCPhyReg> getDefRegsExceptRaSp() {
        ArrayList<MCPhyReg> defRegs = new ArrayList<>();
        for (IList.INode<MCBasicBlock, MCFunction> bNode : list) {
            MCBasicBlock mcBB = bNode.getValue();
            for (IList.INode<MCInstr, MCBasicBlock> iNode : mcBB.getList()) {
                MCInstr instr = iNode.getValue();
                ArrayList<MCReg> defs = instr.getDefReg();
                for (MCReg def : defs) {
                    if (def instanceof MCPhyReg && !defRegs.contains(def)) {
                        if (!def.equals(RA) && !def.equals(SP)) {
                            defRegs.add((MCPhyReg) def);
                        }
                    }
                }
            }
        }
        return defRegs;
    }

    public HashSet<MCPhyReg> regToSave() {
        return regToSave;
    }

    public HashMap<Value, MCReg> getValue2MCReg() {
        return value2MCReg;
    }

    public Function getFunc() {
        return func;
    }

    public ArrayList<MCInstr.MCLw> getLwsToGetArg() {
        return lwsToGetArg;
    }

    public HashMap<BasicBlock, MCLabel> getBBLabelMap() {
        return BBLabelMap;
    }

    public HashMap<Instruction.GEP, Integer> getGepOffsets() {
        return gepOffsets;
    }
}
