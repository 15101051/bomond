package backend.register;

import backend.mc.MCBasicBlock;
import backend.mc.MCFunction;
import backend.mc.MCInstr;
import backend.mc.MCOperand;
import backend.register.RegisterManager.*;
import backend.mc.MCOperand.*;
import backend.mc.MCInstr.*;
import utils.Config;
import utils.IList;
import utils.Logger;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class FullRegisterAllocator {
    private final MCFunction mcf;
    private final HashSet<MCPhyRegTag> allocatableRegs;
    private final int ALLOCATABLE_REG_COUNT;
    private final HashMap<MCVirtualReg, MCVirtualReg> new2old = new HashMap<>();
    private final LivenessAnalysis liveness = new LivenessAnalysis();
    private final HashMap<MCVirtualReg, Integer> newRegLiveLength = new HashMap<>();
    private final HashSet<MCVirtualReg> simplifyWorkList = new HashSet<>();
    private final HashSet<MCMove> coalesceWorkList = new HashSet<>();
    private final HashSet<MCVirtualReg> freezeWorkList = new HashSet<>();
    protected final HashSet<MCVirtualReg> spilledNodes = new HashSet<>();
    protected final HashSet<MCVirtualReg> spillWorklist = new HashSet<>();
    protected final Stack<MCVirtualReg> selectedStack = new Stack<>();
    private final HashSet<MCVirtualReg> coalescedNodes = new HashSet<>();
    private final HashMap<MCOperand, MCOperand> alias = new HashMap<>();
    private final InterfereGraph graph = new InterfereGraph();
    private final HashMap<MCOperand, HashSet<MCInstr.MCMove>> moveList = new HashMap<>();
    private final HashMap<MCOperand, Integer> loopDepth = new HashMap<>();
    private final HashSet<MCMove> activeMoves = new HashSet<>();
    private static final int INF = 0x3f3f3f3f;
    private final MCPhyReg SP = new MCPhyReg(MCPhyRegTag.sp);
    private final MCPhyReg ZERO = new MCPhyReg(MCPhyRegTag.zero);
    private final HashMap<MCVirtualReg, MCPhyRegTag> color = new HashMap<>();

    public FullRegisterAllocator(MCFunction mcf, HashSet<MCPhyRegTag> allocatableRegs) {
        this.mcf = mcf;
        this.allocatableRegs = allocatableRegs;
        this.ALLOCATABLE_REG_COUNT = allocatableRegs.size();
    }

    protected void init() {
        simplifyWorkList.clear();
        coalesceWorkList.clear();
        freezeWorkList.clear();
        spillWorklist.clear();
        moveList.clear();
        for (MCVirtualReg vr : mcf.str2Reg().values()) {
            moveList.put(vr, new HashSet<>());
        }
        spilledNodes.clear();
        coalescedNodes.clear();
        alias.clear();
        activeMoves.clear();
        selectedStack.clear();
        graph.init();
        for (MCPhyRegTag tag : MCPhyRegTag.values()) {
            graph.setDegreeINF(new MCPhyReg(tag));
        }
    }

    public void allocate() {
        new2old.clear();
        newRegLiveLength.clear();
        while (true) {
            // TODO: 2022/11/6 if-if or if-elseif
            init();
            liveness.analysis(mcf);
            build();
            makeWorklist();
            do {
                if (!simplifyWorkList.isEmpty()) {
                    simplify();
                } else if (!coalesceWorkList.isEmpty()) {
                    coalesce();
                } else if (!freezeWorkList.isEmpty()) {
                    freeze();
                } else if (!spillWorklist.isEmpty()) {
                    spill();
                }
            } while (!(simplifyWorkList.isEmpty() &&
                    coalesceWorkList.isEmpty() &&
                    freezeWorkList.isEmpty() &&
                    spillWorklist.isEmpty()));
            assignColors();
            if (spilledNodes.isEmpty()) {
                break;
            }
            spillRegisters();
        }
        rewriteProgram();
    }

    private void build() {
        // TODO: 2022/11/6 正着快还是反着快？？？
        for (IList.INode<MCBasicBlock, MCFunction> bNode = mcf.getList().getLast(); !bNode.isGuard(); bNode = bNode.getPrev()) {
            MCBasicBlock mcBB = bNode.getValue();
            HashSet<MCOperand> live = new HashSet<>(mcBB.liveOut);
            for (IList.INode<MCInstr, MCBasicBlock> iNode = mcBB.getList().getLast(); !iNode.isGuard(); iNode = iNode.getPrev()) {
                MCInstr instr = iNode.getValue();
                if (instr instanceof MCMove && !((MCMove) instr).getSrc().equals(ZERO)) {
                    // TODO: 2022/11/7 move from $zero
                    MCMove move = (MCMove) instr;
                    live.remove(move.getSrc());
                    moveList.computeIfAbsent(move.getSrc(), k -> new HashSet<>()).add(move);
                    moveList.computeIfAbsent(move.getDst(), k -> new HashSet<>()).add(move);
                    coalesceWorkList.add(move);
                }
                live.addAll(instr.getDefReg());
                for (MCOperand d : instr.getDefReg()) {
                    for (MCOperand l : live) {
                        graph.addEdge(l, d);
                    }
                }
                // TODO: 2022/11/6 choose spill Scheme!!!
                for (MCOperand def : instr.getDefReg()) {
                    loopDepth.compute(def, (k, v) -> v == null ? 0 : v + mcBB.getLoopDepth());
                    live.remove(def);
                }
                for (MCOperand use : instr.getUseReg()) {
                    loopDepth.compute(use, (k, v) -> v == null ? 0 : v + mcBB.getLoopDepth());
                    live.add(use);
                }
            }
        }
    }

    private void makeWorklist() {
        for (MCVirtualReg vr : mcf.str2Reg().values()) {
            if (graph.getDegree(vr) >= ALLOCATABLE_REG_COUNT) {
                spillWorklist.add(vr);
            } else if (moveRelated(vr)) {
                freezeWorkList.add(vr);
            } else {
                simplifyWorkList.add(vr);
            }
        }
    }

    private Set<MCOperand> adjacent(MCVirtualReg vr) {
        Predicate<MCOperand> ok = (t) -> !(t instanceof MCVirtualReg && (selectedStack.contains(t) || coalescedNodes.contains(t)));
        return graph.getAllAdjacent(vr).stream().filter(ok).collect(Collectors.toSet());
    }

    private Set<MCMove> nodeMoves(MCOperand n) {
        Predicate<MCMove> ok = (t) -> (activeMoves.contains(t) || coalesceWorkList.contains(t));
        return moveList.getOrDefault(n, new HashSet<>()).stream().filter(ok).collect(Collectors.toSet());
    }

    private boolean moveRelated(MCOperand n) {
        return !nodeMoves(n).isEmpty();
    }

    private void simplify() {
        MCVirtualReg n = simplifyWorkList.iterator().next();
        simplifyWorkList.remove(n);
        selectedStack.push(n);
        for (MCOperand m : adjacent(n)) {
            decrementDegree(m);
        }
    }

    private void decrementDegree(MCOperand m) {
        int d = graph.getDegree(m);
        graph.decrementDegree(m);
        if (d == ALLOCATABLE_REG_COUNT) {
            MCVirtualReg vr = (MCVirtualReg) m;
            enableMoves(new HashSet<MCOperand>() {{ add(vr); addAll(adjacent(vr)); }});
            spillWorklist.remove(vr);
            if (moveRelated(vr)) {
                freezeWorkList.add(vr);
            } else {
                simplifyWorkList.add(vr);
            }
        }
    }

    private void enableMoves(Set<MCOperand> nodes) {
        for (MCOperand n : nodes) {
            for (MCMove m : nodeMoves(n)) {
                if (activeMoves.contains(m)) {
                    activeMoves.remove(m);
                    coalesceWorkList.add(m);
                }
            }
        }
    }

    private void coalesce() {
        MCMove move = coalesceWorkList.iterator().next();
        MCOperand x = getAlias(move.getDst());
        MCOperand y = getAlias(move.getSrc());
        MCOperand u, v;
        if (y instanceof MCPhyReg) {
            u = y;
            v = x;
        } else {
            u = x;
            v = y;
        }
        coalesceWorkList.remove(move);
        if (u.equals(v)) {
            Logger.logCoalescedMove(move);
            addWorkList(u);
        } else if (v instanceof MCPhyReg || graph.isLinked(u, v)) {
            Logger.logConstrainedMove(move);
            addWorkList(u);
            addWorkList(v);
        } else if ((u instanceof MCPhyReg && adjacent((MCVirtualReg) v).stream().allMatch(t -> OK(t, (MCPhyReg) u))) ||
                (u instanceof MCVirtualReg && conservative(new HashSet<MCOperand>() {{ addAll(adjacent((MCVirtualReg) u)); addAll(adjacent((MCVirtualReg) v)); }}))) {
            Logger.logCoalescedMove(move);
            combine(u, (MCVirtualReg) v);
            addWorkList(u);
        } else {
            activeMoves.add(move);
        }
    }

    private void addWorkList(MCOperand u) {
        if (u instanceof MCVirtualReg) {
            MCVirtualReg vr = (MCVirtualReg) u;
            if (!moveRelated(u) && graph.getDegree(u) < ALLOCATABLE_REG_COUNT) {
                freezeWorkList.remove(vr);
                simplifyWorkList.add(vr);
            }
        }
    }

    private boolean OK(MCOperand t, MCPhyReg r) {
        return graph.getDegree(t) < ALLOCATABLE_REG_COUNT || t instanceof MCPhyReg || graph.isLinked(t, r);
    }

    private boolean conservative(Set<MCOperand> s) {
        return s.stream().filter(n -> graph.getDegree(n) >= ALLOCATABLE_REG_COUNT).count() < ALLOCATABLE_REG_COUNT;
    }

    private MCOperand getAlias(MCOperand n) {
        if (n instanceof MCVirtualReg && coalescedNodes.contains(n)) {
            return getAlias(alias.get(n));
        } else {
            return n;
        }
    }

    private void combine(MCOperand u, MCVirtualReg v) {
        if (freezeWorkList.contains(v)) {
            freezeWorkList.remove(v);
        } else {
            spillWorklist.remove(v);
        }
        coalescedNodes.add(v);
        alias.put(v, u);
        moveList.computeIfAbsent(u, k -> new HashSet<>()).addAll(moveList.get(v));
        enableMoves(Collections.singleton(v));
        for (MCOperand t : adjacent(v)) {
            graph.addEdge(t, u);
            decrementDegree(t);
        }
        if (u instanceof MCVirtualReg && graph.getDegree(u) >= ALLOCATABLE_REG_COUNT && freezeWorkList.contains(u)) {
            freezeWorkList.remove((MCVirtualReg) u);
            spillWorklist.add((MCVirtualReg) u);
        }
    }

    private void freeze() {
        MCVirtualReg u = freezeWorkList.iterator().next();
        freezeWorkList.remove(u);
        simplifyWorkList.add(u);
        freezeMove(u);
    }

    private void freezeMove(MCVirtualReg u) {
        // TODO: 2022/11/6 写法不一样？
        for (MCMove m : nodeMoves(u)) {
            MCOperand x = m.getDst(), y = m.getSrc();
            MCOperand v;
            if (getAlias(y).equals(getAlias(u))) {
                v = getAlias(x);
            } else {
                v = getAlias(y);
            }
            activeMoves.remove(m);
            Logger.logFrozenMoves(m);
            if (nodeMoves(v).isEmpty() && graph.getDegree(v) < ALLOCATABLE_REG_COUNT) {
                MCVirtualReg vr = (MCVirtualReg) v;
                freezeWorkList.remove(vr);
                simplifyWorkList.add(vr);
            }
        }
    }

    private MCVirtualReg chooseToSpill() {
        Optional<MCVirtualReg> m = spillWorklist.stream().max((a, b) -> {
            double value1 = ((double) graph.getDegree(a)) / Math.pow(1.4, loopDepth.getOrDefault(a, 0));
            double value2 = ((double) graph.getDegree(b)) / Math.pow(1.4, loopDepth.getOrDefault(b, 0));
            if (newRegLiveLength.getOrDefault(a, INF) < 7) {
                value1 = 0;
            }
            if (newRegLiveLength.getOrDefault(b, INF) < 7) {
                value2 = 0;
            }
            return Double.compare(value1, value2);
        });
        assert m.isPresent();
        return m.get();
    }

    private void spill() {
        MCVirtualReg m = chooseToSpill();
        spillWorklist.remove(m);
        simplifyWorkList.add(m);
        freezeMove(m);
    }

    private void assignColors() {
        color.clear();
        while (!selectedStack.isEmpty()) {
            MCVirtualReg n = selectedStack.pop();
            HashSet<MCPhyRegTag> okColors = new HashSet<>(allocatableRegs);
            for (MCOperand w : graph.getAllAdjacent(n)) {
                MCOperand aliasW = getAlias(w);
                MCPhyRegTag removedTag = null;
                if (aliasW instanceof MCPhyReg) {
                    removedTag = ((MCPhyReg) aliasW).getTag();
                } else if (color.containsKey((MCVirtualReg) aliasW)) {
                    removedTag = color.get((MCVirtualReg) aliasW);
                }
                if (removedTag != null) {
                    okColors.remove(removedTag);
                }
            }
            if (okColors.isEmpty()) {
                spilledNodes.add(n);
            } else {
                MCPhyRegTag assignTag = okColors.iterator().next();
                color.put(n, assignTag);
            }
        }
        for (MCVirtualReg n : coalescedNodes) {
            MCOperand aliasN = getAlias(n);
            if (aliasN instanceof MCPhyReg) {
                color.put(n, ((MCPhyReg) aliasN).getTag());
            } else {
                color.put(n, color.get((MCVirtualReg) aliasN));
            }
        }
    }

    private void spillRegisters() {
        for (MCVirtualReg spilled : spilledNodes) {
            spillOneRegister(spilled);
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
            MCOperand real = new2old.get(spillReg);
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

    private void rewriteProgram() {
        for (IList.INode<MCBasicBlock, MCFunction> bNode : mcf.getList()) {
            MCBasicBlock mcBB = bNode.getValue();
            for (IList.INode<MCInstr, MCBasicBlock> iNode : mcBB.getList()) {
                MCInstr instr = iNode.getValue();
                ArrayList<MCReg> defs = new ArrayList<>(instr.getDefReg());
                ArrayList<MCReg> uses = new ArrayList<>(instr.getUseReg());
                for (MCReg use : uses) {
                    if (use instanceof MCVirtualReg && color.containsKey((MCVirtualReg) use)) {
                        instr.replaceOperandOfInstr(use, new MCPhyReg(color.get((MCVirtualReg) use)));
                    }
                }
                for (MCReg def : defs) {
                    if (def instanceof MCVirtualReg && color.containsKey((MCVirtualReg) def)) {
                        instr.replaceOperandOfInstr(def, new MCPhyReg(color.get((MCVirtualReg) def)));
                    }
                }
            }
        }
        ArrayList<MCPhyReg> defRegs = mcf.getDefRegsExceptRaSp();
        for (MCPhyReg pr : defRegs) {
            if (allocatableRegs.contains(pr.getTag())) {
                mcf.regToSave().add(pr);
            }
        }
    }
}
