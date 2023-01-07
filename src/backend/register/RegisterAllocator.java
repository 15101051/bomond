package backend.register;

import backend.mc.MCBasicBlock;
import backend.mc.MCFunction;
import backend.mc.MCInstr;
import backend.mc.MCOperand;
import backend.mc.MCOperand.*;
import backend.register.RegisterManager.*;
import utils.Config;
import utils.IList;

import java.util.*;

public abstract class RegisterAllocator {
    private final MCPhyReg SP = new MCPhyReg(RegisterManager.MCPhyRegTag.sp);
    private final MCPhyReg ZERO = new MCPhyReg(RegisterManager.MCPhyRegTag.zero);
    protected final MCFunction mcf;
    private final LivenessAnalysis livenessAnalysis = new LivenessAnalysis();
    private final InterfereGraph graph = new InterfereGraph();
    private final HashSet<MCVirtualReg> spilledNodes = new HashSet<>();
    protected final Stack<MCVirtualReg> selectedStack = new Stack<>();
    private final HashMap<MCVirtualReg, Integer> newRegLiveLength = new HashMap<>();

    protected RegisterAllocator(MCFunction mcf) {
        this.mcf = mcf;
    }

    protected void init() {
        livenessAnalysis.analysis(this.mcf);
        spilledNodes.clear();
        selectedStack.clear();
        graph.init();
        for (MCPhyRegTag tag : RegisterManager.getAllocatableRegisters()) {
            graph.setDegreeINF(new MCPhyReg(tag));
        }
    }

    public abstract void allocate();

    protected void buildGraph() {
        for (IList.INode<MCBasicBlock, MCFunction> bNode = mcf.getList().getLast(); !bNode.isGuard(); bNode = bNode.getPrev()) {
            MCBasicBlock mcBB = bNode.getValue();
            HashSet<MCOperand> liveSet = new HashSet<>(mcBB.liveOut);
            for (IList.INode<MCInstr, MCBasicBlock> iNode = mcBB.getList().getLast(); !iNode.isGuard(); iNode = iNode.getPrev()) {
                MCInstr mcInstr = iNode.getValue();
                dealDefUse(liveSet, mcInstr, mcBB);
            }
        }
    }

    private final HashMap<MCOperand, Integer> loopDepth = new HashMap<>();

    private void dealDefUse(HashSet<MCOperand> live, MCInstr instr, MCBasicBlock mcBB) {
        ArrayList<MCReg> useReg = instr.getUseReg();
        ArrayList<MCReg> defReg = instr.getDefReg();
        live.addAll(defReg);
        for (MCOperand def : defReg) {
            for (MCOperand l : live) {
                graph.addEdge(l, def);
            }
        }
        for (MCOperand def : defReg) {
            loopDepth.compute(def, (k, v) -> v == null ? 0 : v + mcBB.getLoopDepth());
            live.remove(def);
        }
        for (MCOperand use : useReg) {
            loopDepth.compute(use, (k, v) -> v == null ? 0 : v + mcBB.getLoopDepth());
            live.add(use);
        }
    }

    protected boolean haveSpillAndDeal() {
        boolean res = !spilledNodes.isEmpty();
        for (MCVirtualReg spilled : spilledNodes) {
            spillOneRegister(spilled);
        }
        return res;
    }

    protected void assignColors() {
        HashMap<MCOperand, MCPhyRegTag> color = new HashMap<>();
        while (!selectedStack.isEmpty()) {
            MCVirtualReg vr = selectedStack.pop();
            MCPhyRegTag tag = assignColor(vr, color);
            if (tag == null) {
                spilledNodes.add(vr);
            } else {
                color.put(vr, tag);
            }
        }
        if (!spilledNodes.isEmpty()) {
            return;
        }
        for (IList.INode<MCBasicBlock, MCFunction> bNode : mcf.getList()) {
            MCBasicBlock mcBB = bNode.getValue();
            for (IList.INode<MCInstr, MCBasicBlock> iNode : mcBB.getList()) {
                MCInstr instr = iNode.getValue();
                ArrayList<MCReg> defs = new ArrayList<>(instr.getDefReg());
                ArrayList<MCReg> uses = new ArrayList<>(instr.getUseReg());
                for (MCReg use : uses) {
                    if (color.containsKey(use)) {
                        instr.replaceOperandOfInstr(use, new MCPhyReg(color.get(use)));
                    }
                }
                for (MCReg def : defs) {
                    if (color.containsKey(def)) {
                        instr.replaceOperandOfInstr(def, new MCPhyReg(color.get(def)));
                    }
                }
            }
        }
        ArrayList<MCPhyReg> defRegs = mcf.getDefRegsExceptRaSp();
        for (MCPhyReg pr : defRegs) {
            if (RegisterManager.getAllocatableRegisters().contains(pr.getTag())) {
                mcf.regToSave().add(pr);
            }
        }
    }

    protected MCPhyRegTag assignColor(MCVirtualReg vr, HashMap<MCOperand, MCPhyRegTag> color) {
        HashSet<MCPhyRegTag> okColors;
        okColors = RegisterManager.getAllocatableRegisters();
        for (MCOperand adj : graph.getAllAdjacent(vr)) {
            if (adj instanceof MCPhyReg) {
                okColors.remove(((MCPhyReg) adj).getTag());
            } else if (adj instanceof MCVirtualReg && color.containsKey(adj)) {
                okColors.remove(color.get(adj));
            }
            if (okColors.isEmpty()) {
                return null;
            }
        }
        return okColors.iterator().next();
    }

    private void spillOneToStore(MCVirtualReg spillReg, MCInstr firstUse, MCInstr lastDef, MCBasicBlock mcBB, boolean storeInStack, int offset) {
        if (spillReg == null) {
            return;
        }
        if (storeInStack) {
            if (firstUse != null) {
                buildLwBefore(spillReg, offset, SP, firstUse);
            }
            if (lastDef != null) {
                buildSwAfter(spillReg, offset, SP, lastDef);
            }
        } else {
            MCReg real = new2old.get(spillReg);
            if (mcf.reg2Gv().containsKey(real)) {
                if (firstUse != null) {
                    MCInstr.buildLwBefore(spillReg, new MCImm(mcf.reg2Gv().get(real).getPos()), ZERO, firstUse);
                }
            } else if (mcf.getAllocaImm().containsKey(real)) {
                if (firstUse != null) {
                    MCInstr.buildBinaryIBefore(MCInstr.MCInstrTag.addiu, spillReg, SP, mcf.getAllocaImm().get(real), firstUse);
                }
            } else {
                throw new RuntimeException("spill? " + real);
            }
        }
        int firstPos = -1;
        int lastPos = -1;
        int pos = 0;
        for (IList.INode<MCInstr, MCBasicBlock> iNode : mcBB.getList()) {
            MCInstr instr = iNode.getValue();
            ++pos;
            if (instr.getDefReg().contains(spillReg) || instr.getUseReg().contains(spillReg)) {
                if (firstPos == -1) {
                    firstPos = pos;
                }
                lastPos = pos;
            }
        }
        if (firstPos == -1) {
            throw new RuntimeException("calc reg live?");
        } else {
            newRegLiveLength.put(spillReg, lastPos - firstPos + 1);
        }
    }

    protected void spillOneRegister(MCVirtualReg vr) {
        boolean storeInStack = !(mcf.reg2Gv().containsKey(vr) || mcf.getAllocaImm().containsKey(vr));
        if (storeInStack) {
            mcf.addStackSize(4);
        }
        for (IList.INode<MCBasicBlock, MCFunction> bNode : mcf.getList()) {
            MCBasicBlock mcBB = bNode.getValue();
            int offset = mcf.getStackSize() - 4;
            MCVirtualReg spillReg = null;
            MCInstr firstUse = null;
            MCInstr lastDef = null;
            SpillScheme spillScheme;
            if (Config.spillChoice == SpillScheme.SpillSchemeChoice.CountInstr) {
                spillScheme = new SpillScheme.CountInstrSpillScheme();
            } else {
                throw new RuntimeException("unimplemented spill scheme");
            }
            for (IList.INode<MCInstr, MCBasicBlock> iNode : mcBB.getList()) {
                MCInstr instr = iNode.getValue();
                HashSet<MCReg> defs = new HashSet<>(instr.getDefReg());
                HashSet<MCReg> uses = new HashSet<>(instr.getUseReg());
                for (MCReg use : uses) {
                    if (use.equals(vr)) {
                        if (spillReg == null) {
                            spillReg = cloneVReg(vr);
                        }
                        instr.replaceOperandOfInstr(use, spillReg);
                        if (firstUse == null && lastDef == null) {
                            firstUse = instr;
                        }
                    }
                }
                for (MCReg def : defs) {
                    if (def.equals(vr)) {
                        if (spillReg == null) {
                            spillReg = cloneVReg(vr);
                        }
                        instr.replaceOperandOfInstr(def, spillReg);
                        lastDef = instr;
                    }
                }
                if (spillScheme.checkToSpill()) {
                    spillOneToStore(spillReg, firstUse, lastDef, mcBB, storeInStack, offset);
                    spillScheme.reset();
                    spillReg = null;
                    firstUse = null;
                    lastDef = null;
                }
                spillScheme.look(instr);
            }
            spillOneToStore(spillReg, firstUse, lastDef, mcBB, storeInStack, offset);
        }
    }

    private void buildLwBefore(MCReg dst, int offset, MCReg base, MCInstr instr) {
        if (MCImm.canEncodeImm(offset)) {
            MCInstr.buildLwBefore(dst, new MCImm(offset), base, instr);
        } else {
            MCVirtualReg tmp = cloneVReg(null);
            newRegLiveLength.put(tmp, 2);
            MCInstr.buildLuiBefore(tmp, MCImm.HI(offset), instr);
            if (base.equals(ZERO)) {
                MCInstr.buildLwBefore(dst, MCImm.LO(offset), tmp, instr);
            } else {
                MCInstr.buildBinaryRBefore(MCInstr.MCInstrTag.addu, tmp, tmp, base, instr);
                MCInstr.buildLwBefore(dst, MCImm.LO(offset), tmp, instr);
            }
        }
    }

    private void buildSwAfter(MCReg val, int offset, MCReg base, MCInstr instr) {
        if (MCImm.canEncodeImm(offset)) {
            MCInstr.buildSwAfter(val, new MCImm(offset), base, instr);
        } else {
            MCVirtualReg tmp = cloneVReg(null);
            newRegLiveLength.put(tmp, 2);
            MCInstr.buildLuiBefore(tmp, MCImm.HI(offset), instr);
            if (base.equals(ZERO)) {
                MCInstr.buildLwBefore(val, MCImm.LO(offset), tmp, instr);
            } else {
                MCInstr.buildBinaryRBefore(MCInstr.MCInstrTag.addu, tmp, tmp, base, instr);
                MCInstr.buildSwAfter(val, MCImm.LO(offset), tmp, instr);
            }
        }
    }

    private final HashMap<MCVirtualReg, MCVirtualReg> new2old = new HashMap<>();

    private MCVirtualReg cloneVReg(MCVirtualReg old) {
        MCVirtualReg newReg = new MCVirtualReg();
        mcf.str2Reg().put(newReg.getName(), newReg);
        if (old != null) {
            while (new2old.containsKey(old)) {
                old = new2old.get(old);
            }
            new2old.put(newReg, old);
        }
        return newReg;
    }

    public enum RegisterAllocatorChoice {
        naive, full,
    }
}
