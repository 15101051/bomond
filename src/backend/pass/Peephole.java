package backend.pass;

import backend.mc.*;
import backend.mc.MCOperand.*;
import backend.mc.MCInstr.*;
import backend.register.LivenessAnalysis;
import backend.register.RegisterManager;
import utils.IList;
import utils.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class Peephole {
    private final MCModule m = MCModule.module;
    private final MCOperand.MCPhyReg ZERO = new MCOperand.MCPhyReg(RegisterManager.MCPhyRegTag.zero);
    private final MCOperand.MCPhyReg SP = new MCOperand.MCPhyReg(RegisterManager.MCPhyRegTag.sp);

    public void run() {
        for (MCFunction mcf : m.functions.values()) {
            if (mcf.isBuiltin()) {
                continue;
            }
            boolean done = false;
            while (!done) {
                done = simplePeephole(mcf);
                done &= dataFlowPeephole(mcf);
            }
        }
    }

    public boolean simplePeephole(MCFunction mcf) {
        boolean done = true;
        for (IList.INode<MCBasicBlock, MCFunction> bNode : mcf.getList()) {
            MCBasicBlock mcBB = bNode.getValue();
            done &= simplePeepholeForBlock(mcBB);
        }
        return done;
    }

    public boolean simplePeepholeForBlock(MCBasicBlock mcBB) {
        boolean done = true;
        MCBasicBlock nextBB = mcBB.node.getNext().getValue();
        ArrayList<MCInstr> instrs = new ArrayList<>();
        for (IList.INode<MCInstr, MCBasicBlock> iNode : mcBB.getList()) {
            instrs.add(iNode.getValue());
        }
        for (int i = 0; i < instrs.size(); ++i) {
            MCInstr instr = instrs.get(i);
            MCInstr prev = instr.node.getPrev().getValue();
            MCInstr next = instr.node.getNext().getValue();
            if (instr instanceof MCMove) {
                MCMove move = (MCMove) instr;
                // move a, a => remove
                if (move.getDst().equals(move.getSrc())) {
                    move.node.removeSelf();
                    done = false;
                    continue;
                }
                if (next != null &&
                        next.getDefReg().size() == 1 &&
                        next.getDefReg().contains(move.getDst()) &&
                        !next.getUseReg().contains(move.getDst())) {
                    move.node.removeSelf();
                    done = false;
                    continue;
                }
                // move b, a
                // move a, b => remove
                if (next instanceof MCMove) {
                    MCMove nextMove = (MCMove) next;
                    if (nextMove.getDst().equals(move.getSrc()) &&
                            nextMove.getSrc().equals(move.getDst())) {
                        nextMove.node.removeSelf();
                        done = false;
                        ++i;
                    }
                }
            } else if (nextBB != null && instr instanceof MCJ) {
                MCJ mcj = (MCJ) instr;
                if (mcj.getTarget().equals(nextBB.getLabel())) {
                    done = false;
                    mcj.node.removeSelf();
                }
            } else if (instr instanceof MCBranchE) {
                MCBranchE branchE = (MCBranchE) instr;
                if (branchE.getTag() == MCInstrTag.bne && branchE.getLhs().equals(branchE.getRhs())) {
                    done = false;
                    branchE.node.removeSelf();
                }
            } else if (instr instanceof MCBranchZ) {
                MCBranchZ branchZ = (MCBranchZ) instr;
                if ((branchZ.getTag() == MCInstrTag.bgtz || branchZ.getTag() == MCInstrTag.bltz) && branchZ.getVal().equals(ZERO)) {
                    done = false;
                    branchZ.node.removeSelf();
                }
            } else if (instr instanceof MCLw) {
                MCLw lw = (MCLw) instr;
                if (prev instanceof MCLw) {
                    MCLw lwPrev = (MCLw) prev;
                    if (lwPrev.getOffset().equals(lw.getOffset()) &&
                        lwPrev.getBase().equals(lw.getBase()) && !lwPrev.getDst().equals(lw.getBase())) {
                        buildMoveAfter(lw.getDst(), lwPrev.getDst(), lwPrev);
                        done = false;
                        lw.node.removeSelf();
                    }
                } else if (prev instanceof MCSw) {
                    MCSw swPrev = (MCSw) prev;
                    if (swPrev.getOffset().equals(lw.getOffset()) &&
                            swPrev.getBase().equals(lw.getBase())) {
                        buildMoveAfter(lw.getDst(), swPrev.getData(), swPrev);
                        done = false;
                        lw.node.removeSelf();
                    }
                }
            } else if (instr instanceof MCBinaryI) {
                MCBinaryI bi = (MCBinaryI) instr;
                if (bi.getTag() == MCInstrTag.xori || bi.getTag() == MCInstrTag.addiu) {
                    if (bi.getRhs().getImm() == 0) {
                        buildMoveAfter(bi.getDst(), bi.getLhs(), bi);
                        bi.node.removeSelf();
                        done = false;
                    }
                }
            } else if (instr instanceof MCBinaryR) {
                MCBinaryR br = (MCBinaryR) instr;
                if (br.getTag() == MCInstrTag.addu || br.getTag() == MCInstrTag.subu) {
                    if (br.getRhs().equals(ZERO)) {
                        buildMoveAfter(br.getDst(), br.getLhs(), br);
                        br.node.removeSelf();
                        done = false;
                    } else if (br.getTag() == MCInstrTag.addu && br.getLhs().equals(ZERO)) {
                        buildMoveAfter(br.getDst(), br.getRhs(), br);
                        br.node.removeSelf();
                        done = false;
                    }
                }
            }
        }
        return done;
    }

    public void buildMoveAfter(MCReg dst, MCReg src, MCInstr prev) {
        if (dst.equals(src)) {
            return;
        }
        MCInstr.buildMoveAfter(dst, src, prev);
    }

    private Pair<HashMap<MCOperand, MCInstr>, HashMap<MCInstr, MCInstr>> readWriteAnalyze(MCBasicBlock mcBB) {
        HashMap<MCOperand, MCInstr> lastWriter = new HashMap<>();
        HashMap<MCInstr, MCInstr> writer2Reader = new HashMap<>();
        for (IList.INode<MCInstr, MCBasicBlock> iNode : mcBB.getList()) {
            MCInstr instr = iNode.getValue();
            for (MCOperand use : instr.getUseReg()) {
                if (lastWriter.containsKey(use)) {
                    writer2Reader.put(lastWriter.get(use), instr);
                }
            }
            for (MCOperand def : instr.getDefReg()) {
                lastWriter.put(def, instr);
            }
            if (instr instanceof MCJr ||
                    instr instanceof MCJ ||
                    instr instanceof MCBranchZ ||
                    instr instanceof MCBranchE ||
                    instr instanceof MCSw ||
                    instr instanceof MCCall ||
                    instr instanceof MCGetarray ||
                    instr instanceof MCSyscall ||
                    instr instanceof MCDivMul ||
                    instr instanceof MCPutarray ||
                    instr.getDefReg().stream().anyMatch(def -> def.equals(SP))
            ) {
                writer2Reader.put(instr, instr); // 这些指令没有reader，但也不能删
            }
        }
        return new Pair<>(lastWriter, writer2Reader);
    }

    public boolean dataFlowPeephole(MCFunction mcf) {
        LivenessAnalysis livenessAnalysis = new LivenessAnalysis();
        livenessAnalysis.analysis(mcf);
        boolean changed = false;
        ArrayList<MCInstr> removedInstrs = new ArrayList<>();
        for (IList.INode<MCBasicBlock, MCFunction> bNode : mcf.getList()) {
            MCBasicBlock mcBB = bNode.getValue();
            Pair<HashMap<MCOperand, MCInstr>, HashMap<MCInstr, MCInstr>> readWriterInfo = readWriteAnalyze(mcBB);
            HashMap<MCOperand, MCInstr> lastWriter = readWriterInfo.getFir();
            HashMap<MCInstr, MCInstr> writer2Reader = readWriterInfo.getSec();
            HashSet<MCOperand> liveOut = mcBB.liveOut;
            for (IList.INode<MCInstr, MCBasicBlock> iNode : mcBB.getList()) {
                MCInstr instr = iNode.getValue();
                boolean isLastWriter = instr.getDefReg().stream().allMatch(def -> lastWriter.get(def).equals(instr));
                boolean inLiveOut = instr.getDefReg().stream().anyMatch(liveOut::contains);
                if (!(isLastWriter && inLiveOut)) {
                    MCInstr lastReader = writer2Reader.get(instr);
                    if (lastReader == null) {
                        removedInstrs.add(instr);
                        changed = true;
                        continue;
                    }
                    if (instr instanceof MCMove &&
                            !instr.node.getNext().isGuard() &&
                            instr.node.getNext().getValue().equals(lastReader)
                    ) {
                        MCInstr nextInstr = instr.node.getNext().getValue();
                        if (nextInstr.getTag() == MCInstrTag.jr || nextInstr.getTag() == MCInstrTag.jal) {
                            continue;
                        }
                        MCReg src = ((MCMove) instr).getSrc();
                        MCReg dst = ((MCMove) instr).getDst();
                        nextInstr.replaceUseOfInstr(dst, src);
                        removedInstrs.add(instr);
                        changed = true;
                    }
                }
            }
        }
        for (MCInstr instr : removedInstrs) {
            instr.node.removeSelf();
        }
        return !changed;
    }
}
