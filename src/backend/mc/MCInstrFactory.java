package backend.mc;

import backend.register.RegisterManager.*;
import utils.Config;
import midend.ir.*;
import midend.ir.Instruction.*;
import backend.mc.MCOperand.*;
import utils.Pair;

import java.util.ArrayList;

public class MCInstrFactory {
    private final MCModule m;
    private MCBasicBlock mcBB;
    private final MCPhyReg ZERO = new MCPhyReg(MCPhyRegTag.zero);
    private final MCPhyReg SP = new MCPhyReg(MCPhyRegTag.sp);
    private final MCPhyReg A0 = new MCPhyReg(MCPhyRegTag.a0);
    private final MCPhyReg A1 = new MCPhyReg(MCPhyRegTag.a1);
    private final MCPhyReg A2 = new MCPhyReg(MCPhyRegTag.a2);
    private final MCPhyReg A3 = new MCPhyReg(MCPhyRegTag.a3);
    private final MCPhyReg V0 = new MCPhyReg(MCPhyRegTag.v0);
    private final MCPhyReg RA = new MCPhyReg(MCPhyRegTag.ra);
    private final MCPhyReg GP = new MCPhyReg(MCPhyRegTag.gp);
    private final MCFunction mcf;

    public MCInstrFactory(MCFunction mcf) {
        this.mcf = mcf;
        this.m = MCModule.module;
    }

    public void setMcBB(MCBasicBlock mcBB) {
        this.mcBB = mcBB;
    }

    public void buildInstr(Instruction instr) {
        switch (instr.tag) {
            case Call: {
                Call call = (Call) instr;
                Function f = call.getFunc();
                switch (f.getName()) {
                    case "printf":
                        String origin = ((Constant.ConstantString) ((GlobalVariable) call.getOperand(1)).getInit()).getString();
                        String content = origin.substring(1, origin.length() - 1);
                        StringBuilder sb = new StringBuilder();
                        int cur = 2;
                        for (int i = 0; i < content.length(); ++i) {
                            char c = content.charAt(i);
                            if (c == '%') {
                                String str = sb.toString();
                                sb = new StringBuilder();
                                if (!str.equals("")) {
                                    buildSaveCIIn(A0, MCModule.module.strings.get(str).getPos());
                                    buildSaveCIIn(V0, 4);
                                    MCInstr.buildSyscallAtEnd(mcBB, MCInstr.MCSyscall.SyscallType.PrintStr);
                                }
                                ++i;
                                if (call.getOperand(cur) instanceof Constant.ConstantInt) {
                                    buildSaveCIIn(A0, ((Constant.ConstantInt) call.getOperand(cur)).getValue());
                                } else {
                                    MCInstr.buildMoveAtEnd(A0, irOp2mcR(call.getOperand(cur)), mcBB);
                                }
                                ++cur;
                                buildSaveCIIn(V0, 1);
                                MCInstr.buildSyscallAtEnd(mcBB, MCInstr.MCSyscall.SyscallType.PrintInt);
                            } else {
                                sb.append(c);
                            }
                        }
                        String str = sb.toString();
                        if (!str.equals("")) {
                            buildSaveCIIn(A0, MCModule.module.strings.get(str).getPos());
                            buildSaveCIIn(V0, 4);
                            MCInstr.buildSyscallAtEnd(mcBB, MCInstr.MCSyscall.SyscallType.PrintStr);
                        }
                        break;
                    case "getint": {
                        MCReg dst = irOp2mcR(call);
                        buildSaveCIIn(V0, 5);
                        MCInstr.buildSyscallAtEnd(mcBB, MCInstr.MCSyscall.SyscallType.ReadInt);
                        MCInstr.buildMoveAtEnd(dst, V0, mcBB);
                        break;
                    }
                    case "putint": {
                        Value pos = call.getOperand(1);
                        if (pos instanceof Constant.ConstantInt) {
                            buildSaveCIIn(A0, ((Constant.ConstantInt) pos).getValue());
                        } else {
                            MCInstr.buildMoveAtEnd(A0, irOp2mcR(pos), mcBB);
                        }
                        buildSaveCIIn(V0, 1);
                        MCInstr.buildSyscallAtEnd(mcBB, MCInstr.MCSyscall.SyscallType.PrintInt);
                        break;
                    }
                    case "getch": {
                        MCReg dst = irOp2mcR(call);
                        buildSaveCIIn(V0, 12);
                        MCInstr.buildSyscallAtEnd(mcBB, MCInstr.MCSyscall.SyscallType.ReadChar);
                        MCInstr.buildMoveAtEnd(dst, V0, mcBB);
                        break;
                    }
                    case "putch": {
                        Value pos = call.getOperand(1);
                        if (pos instanceof Constant.ConstantInt) {
                            buildSaveCIIn(A0, ((Constant.ConstantInt) pos).getValue());
                        } else {
                            MCInstr.buildMoveAtEnd(A0, irOp2mcR(pos), mcBB);
                        }
                        buildSaveCIIn(V0, 11);
                        MCInstr.buildSyscallAtEnd(mcBB, MCInstr.MCSyscall.SyscallType.PrintChar);
                        break;
                    }
                    case "getarray": {
                        MCReg n = irOp2mcR(call);
                        MCReg base = irOp2mcR(call.getOperand(1));
                        if (call.getOperand(1) instanceof GEP) {
                            buildAddCI(base, base, mcf.getGepOffsets().get((GEP) call.getOperand(1)));
                        }
                        MCInstr.buildGetarrayAtEnd(n, base, mcBB);
                        break;
                    }
                    case "putarray": {
                        MCReg n = irOp2mcR(call.getOperand(1));
                        MCReg base = irOp2mcR(call.getOperand(2));
                        if (call.getOperand(2) instanceof GEP) {
                            buildAddCI(base, base, mcf.getGepOffsets().get((GEP) call.getOperand(2)));
                        }
                        MCInstr.buildPutarrayAtEnd(n, base, mcBB);
                        break;
                    }
                    default:
                        ArrayList<MCReg> argsRegs = new ArrayList<>();
                        for (int i = 1; i < call.getOperandNum(); ++i) {
                            MCReg arg = irOp2mcR(call.getOperand(i));
                            if (call.getOperand(i) instanceof GEP) {
                                buildAddCI(arg, arg, mcf.getGepOffsets().get((GEP) call.getOperand(i)));
                            }
                            argsRegs.add(arg);
                        }
                        MCFunction callee = MCModule.module.functions.get(call.getFunc().getName());
                        int argsCnt = argsRegs.size();
                        for (int i = 0; i < argsCnt; ++i) {
                            MCReg val = argsRegs.get(i);
                            if (i == 0) {
                                MCInstr.buildMoveAtEnd(A0, val, mcBB);
                            } else if (i == 1) {
                                MCInstr.buildMoveAtEnd(A1, val, mcBB);
                            } else if (i == 2) {
                                MCInstr.buildMoveAtEnd(A2, val, mcBB);
                            } else if (i == 3) {
                                MCInstr.buildMoveAtEnd(A3, val, mcBB);
                            } else {
                                MCInstr.buildSwAtEnd(val, new MCImm(4 * (i - argsCnt)), SP, mcBB);
                            }
                        }
                        MCInstr.buildCallAtEnd(callee, argsCnt, mcBB);
                        if (!((Type.FunctionType) f.getType()).getReturnType().isVoidType()) {
                            MCReg dst = irOp2mcR(call);
                            MCInstr.buildMoveAtEnd(dst, V0, mcBB);
                        }
                        break;
                }
                break;
            }
            case Load: {
                Load load = (Load) instr;
                MCReg dst = irOp2mcR(load);
                Value pointer = load.getPointer();
                if (pointer instanceof GlobalVariable) {
                    int pos = MCModule.module.lirGVs.get((GlobalVariable) pointer).getPos();
                    if (MCImm.HI(pos).getImm() != 0) {
                        if (MCImm.HI(pos).getImm() == 0x1001) {
                            MCInstr.buildLwAtEnd(dst, MCImm.LO(pos), GP, mcBB);
                        } else {
                            MCInstr.buildLuiAtEnd(dst, MCImm.HI(pos), mcBB);
                            MCInstr.buildLwAtEnd(dst, MCImm.LO(pos), dst, mcBB);
                        }
                    } else {
                        MCInstr.buildLwAtEnd(dst, MCImm.LO(pos), ZERO, mcBB);
                    }
                } else if (pointer instanceof GEP) {
                    GEP gep = (GEP) pointer;
                    MCReg base = irOp2mcR(gep);
                    int offset = mcf.getGepOffsets().get(gep);
                    buildAddCI(dst, base, offset - MCImm.LO(offset).getImm());
                    MCInstr.buildLwAtEnd(dst, MCImm.LO(offset), dst, mcBB);
                } else {
                    MCReg base = irOp2mcR(pointer);
                    MCInstr.buildLwAtEnd(dst, new MCImm(0), base, mcBB);
                }
                break;
            }
            case Store: {
                Store store = (Store) instr;
                MCReg val = irOp2mcR(store.getValue());
                Value pointer = store.getPointer();
                if (pointer instanceof GlobalVariable) {
                    int pos = MCModule.module.lirGVs.get((GlobalVariable) pointer).getPos();
                    if (MCImm.HI(pos).getImm() != 0) {
                        if (MCImm.HI(pos).getImm() == 0x1001) {
                            MCInstr.buildSwAtEnd(val, MCImm.LO(pos), GP, mcBB);
                        } else {
                            MCReg tmp = getNewVReg(null);
                            MCInstr.buildLuiAtEnd(tmp, MCImm.HI(pos), mcBB);
                            MCInstr.buildSwAtEnd(val, MCImm.LO(pos), tmp, mcBB);
                        }
                    } else {
                        MCInstr.buildSwAtEnd(val, MCImm.LO(pos), ZERO, mcBB);
                    }
                } else if (pointer instanceof GEP) {
                    GEP gep = (GEP) pointer;
                    MCReg base = irOp2mcR(gep);
                    MCReg tmp = getNewVReg(null);
                    int offset = mcf.getGepOffsets().get(gep);
                    buildAddCI(tmp, base, offset - MCImm.LO(offset).getImm());
                    MCInstr.buildSwAtEnd(val, MCImm.LO(offset), tmp, mcBB);
                } else {
                    MCReg base = irOp2mcR(pointer);
                    MCInstr.buildSwAtEnd(val, new MCImm(0), base, mcBB);
                }
                break;
            }
            case Zext: {
                MCReg dst = irOp2mcR(instr);
                MCInstr.buildMoveAtEnd(dst, irOp2mcR(instr.getOperand(0)), mcBB);
                break;
            }
            case Alloca: {
                Alloca alloca = (Alloca) instr;
                int size = alloca.getAllocated().needBytes();
                MCReg dst = irOp2mcR(alloca);
                int pos = mcf.getStackSize();
                mcf.addStackSize(size);
                if (MCImm.canEncodeImm(pos)) {
                    MCImm offset = new MCImm(pos);
                    MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.addiu, dst, SP, offset, mcBB);
                    mcf.getAllocaImm().put(dst, offset);
                } else {
                    MCReg tmp = getNewVReg(null);
                    buildSaveCIIn(tmp, pos);
                    MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.addu, dst, SP, tmp, mcBB);
                }
                break;
            }
            case Xor: {
                Value lhs = ((BinaryInst) instr).lhs();
                Value rhs = ((BinaryInst) instr).rhs();
                if (lhs instanceof Constant.ConstantInt) {
                    Value tmp = lhs; lhs = rhs; rhs = tmp;
                }
                MCReg dstR = irOp2mcR(instr);
                MCReg lhsR = irOp2mcR(lhs);
                if (rhs instanceof Constant.ConstantInt) {
                    int rhsValue = ((Constant.ConstantInt) rhs).getValue();
                    buildBitCI(dstR, lhsR, rhsValue, MCInstr.MCInstrTag.xor, MCInstr.MCInstrTag.xori);
                } else {
                    MCReg rhsR = irOp2mcR(rhs);
                    MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.xor, dstR, lhsR, rhsR, mcBB);
                }
                break;
            }
            case And: {
                Value lhs = ((BinaryInst) instr).lhs();
                Value rhs = ((BinaryInst) instr).rhs();
                if (lhs instanceof Constant.ConstantInt) {
                    Value tmp = lhs; lhs = rhs; rhs = tmp;
                }
                MCReg dstR = irOp2mcR(instr);
                MCReg lhsR = irOp2mcR(lhs);
                if (rhs instanceof Constant.ConstantInt) {
                    int rhsValue = ((Constant.ConstantInt) rhs).getValue();
                    buildBitCI(dstR, lhsR, rhsValue, MCInstr.MCInstrTag.and, MCInstr.MCInstrTag.andi);
                } else {
                    MCReg rhsR = irOp2mcR(rhs);
                    MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.and, dstR, lhsR, rhsR, mcBB);
                }
                break;
            }
            case Or: {
                Value lhs = ((BinaryInst) instr).lhs();
                Value rhs = ((BinaryInst) instr).rhs();
                if (lhs instanceof Constant.ConstantInt) {
                    Value tmp = lhs; lhs = rhs; rhs = tmp;
                }
                MCReg dstR = irOp2mcR(instr);
                MCReg lhsR = irOp2mcR(lhs);
                if (rhs instanceof Constant.ConstantInt) {
                    int rhsValue = ((Constant.ConstantInt) rhs).getValue();
                    buildBitCI(dstR, lhsR, rhsValue, MCInstr.MCInstrTag.or, MCInstr.MCInstrTag.ori);
                } else {
                    MCReg rhsR = irOp2mcR(rhs);
                    MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.or, dstR, lhsR, rhsR, mcBB);
                }
                break;
            }
            case Add: {
                Value lhs = ((BinaryInst) instr).lhs();
                Value rhs = ((BinaryInst) instr).rhs();
                if (lhs instanceof Constant.ConstantInt) {
                    Value tmp = lhs; lhs = rhs; rhs = tmp;
                }
                MCReg dstR = irOp2mcR(instr);
                MCReg lhsR = irOp2mcR(lhs);
                if (rhs instanceof Constant.ConstantInt) {
                    int rhsValue = ((Constant.ConstantInt) rhs).getValue();
                    buildAddCI(dstR, lhsR, rhsValue);
                } else {
                    MCReg rhsR = irOp2mcR(rhs);
                    MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.addu, dstR, lhsR, rhsR, mcBB);
                }
                break;
            }
            case Sub: {
                MCReg lhsR = irOp2mcR(((BinaryInst) instr).lhs());
                MCReg dstR = irOp2mcR(instr);
                Value rhs = ((BinaryInst) instr).rhs();
                if (rhs instanceof Constant.ConstantInt) {
                    int rhsValue = ((Constant.ConstantInt) rhs).getValue();
                    buildAddCI(dstR, lhsR, -rhsValue);
                } else {
                    MCReg rhsR = irOp2mcR(rhs);
                    MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.subu, dstR, lhsR, rhsR, mcBB);
                }
                break;
            }
            case Mul: {
                Value lhs = ((BinaryInst) instr).lhs();
                Value rhs = ((BinaryInst) instr).rhs();
                MCReg lhsR = irOp2mcR(lhs);
                MCReg rhsR = irOp2mcR(rhs);
                MCReg dstR = irOp2mcR(instr);
                MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.mul, dstR, lhsR, rhsR, mcBB);
                break;
            }
            case Sdiv: {
                Value lhs = ((BinaryInst) instr).lhs();
                Value rhs = ((BinaryInst) instr).rhs();
                MCReg lhsR = irOp2mcR(lhs);
                MCReg rhsR = irOp2mcR(rhs);
                MCReg dstR = irOp2mcR(instr);
                MCInstr.buildDivMulAtEnd(MCInstr.MCInstrTag.div, lhsR, rhsR, mcBB);
                MCInstr.buildMfAtEnd(MCInstr.MCInstrTag.mflo, dstR, mcBB);
                break;
            }
            case Srem: {
                Value lhs = ((BinaryInst) instr).lhs();
                Value rhs = ((BinaryInst) instr).rhs();
                MCReg lhsR = irOp2mcR(lhs);
                MCReg rhsR = irOp2mcR(rhs);
                MCReg dstR = irOp2mcR(instr);
                MCInstr.buildDivMulAtEnd(MCInstr.MCInstrTag.div, lhsR, rhsR, mcBB);
                MCInstr.buildMfAtEnd(MCInstr.MCInstrTag.mfhi, dstR, mcBB);
                break;
            }
            case Shl: {
                Value lhs = ((BinaryInst) instr).lhs();
                Value rhs = ((BinaryInst) instr).rhs();
                MCReg lhsR = irOp2mcR(lhs);
                MCReg dstR = irOp2mcR(instr);
                assert rhs instanceof Constant.ConstantInt;
                int rhsValue = ((Constant.ConstantInt) rhs).getValue();
                MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.sll, dstR, lhsR, new MCImm(rhsValue), mcBB);
                break;
            }
            case Ashr: {
                Value lhs = ((BinaryInst) instr).lhs();
                Value rhs = ((BinaryInst) instr).rhs();
                MCReg lhsR = irOp2mcR(lhs);
                MCReg dstR = irOp2mcR(instr);
                assert rhs instanceof Constant.ConstantInt;
                int rhsValue = ((Constant.ConstantInt) rhs).getValue();
                MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.sra, dstR, lhsR, new MCImm(rhsValue), mcBB);
                break;
            }
            case Lshr: {
                Value lhs = ((BinaryInst) instr).lhs();
                Value rhs = ((BinaryInst) instr).rhs();
                MCReg lhsR = irOp2mcR(lhs);
                MCReg dstR = irOp2mcR(instr);
                assert rhs instanceof Constant.ConstantInt;
                int rhsValue = ((Constant.ConstantInt) rhs).getValue();
                MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.srl, dstR, lhsR, new MCImm(rhsValue), mcBB);
                break;
            }
            case MulH: {
                Value lhs = ((BinaryInst) instr).lhs();
                Value rhs = ((BinaryInst) instr).rhs();
                MCReg lhsR = irOp2mcR(lhs);
                MCReg rhsR = irOp2mcR(rhs);
                MCReg dstR = irOp2mcR(instr);
                MCInstr.buildDivMulAtEnd(MCInstr.MCInstrTag.mult, lhsR, rhsR, mcBB);
                MCInstr.buildMfAtEnd(MCInstr.MCInstrTag.mfhi, dstR, mcBB);
                break;
            }
            case Ret: {
                if (mcf.getName().equals("func_main")) {
                    if (Config.mainReturnVal) {
                        if (instr.getOperand(0) != null) {
                            if (instr.getOperand(0) instanceof Constant.ConstantInt) {
                                buildSaveCIIn(A0, ((Constant.ConstantInt) instr.getOperand(0)).getValue());
                            } else {
                                MCInstr.buildMoveAtEnd(A0, irOp2mcR(instr.getOperand(0)), mcBB);
                            }
                        }
                        buildSaveCIIn(V0, 17);
                    } else {
                        buildSaveCIIn(V0, 10);
                    }
                    MCInstr.buildSyscallAtEnd(mcBB, MCInstr.MCSyscall.SyscallType.Exit);
                } else {
                    if (instr.getOperand(0) != null) {
                        if (instr.getOperand(0) instanceof Constant.ConstantInt) {
                            buildSaveCIIn(V0, ((Constant.ConstantInt) instr.getOperand(0)).getValue());
                        } else {
                            MCInstr.buildMoveAtEnd(V0, irOp2mcR(instr.getOperand(0)), mcBB);
                        }
                        MCInstr.buildJrAtEnd(MCInstr.MCInstrTag.jr, RA, true, mcBB);
                    } else {
                        MCInstr.buildJrAtEnd(MCInstr.MCInstrTag.jr, RA, false, mcBB);
                    }
                }
                break;
            }
            case GetElementPtr: {
                GEP gep = (GEP) instr;
                ArrayList<Integer> dims;
                Type thisInstrType = ((Type.PointerType) gep.getPointer().getType()).getPointTo();
                if (thisInstrType.isArrayType()) {
                    dims = new ArrayList<>(((Type.ArrayType) thisInstrType).getDims());
                } else {
                    dims = new ArrayList<>();
                }
                for (int i = dims.size() - 2; i >= 0; --i) {
                    dims.set(i, dims.get(i) * dims.get(i + 1));
                }
                dims.add(1);
                ArrayList<Pair<MCReg, MCReg>> addMuls = new ArrayList<>();
                ArrayList<Pair<MCReg, Integer>> slls = new ArrayList<>();
                int offsetNum = 0;
                if (gep.getPointer() instanceof GEP) {
                    offsetNum = mcf.getGepOffsets().get((GEP) gep.getPointer());
                }
                for (int i = 1; i < gep.getOperandNum(); ++i) {
                    Value op = gep.getOperand(i);
                    if (op instanceof Constant.ConstantInt) {
                        offsetNum += ((Constant.ConstantInt) op).getValue() * dims.get(i - 1) * 4;
                    } else if (MCImm.one_bit(dims.get(i - 1) * 4) != -1) {
                        slls.add(new Pair<>(irOp2mcR(op), MCImm.one_bit(dims.get(i - 1) * 4)));
                    } else {
                        addMuls.add(new Pair<>(irOp2mcR(op), irOp2mcR(new Constant.ConstantInt(dims.get(i - 1) * 4))));
                    }
                }
                MCReg dst = irOp2mcR(instr);
                if (gep.getPointer() instanceof GlobalVariable) {
                    offsetNum += m.lirGVs.get((GlobalVariable) gep.getPointer()).getPos();
                    if (!addMuls.isEmpty()) {
                        MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.mul, dst, addMuls.get(0).getFir(), addMuls.get(0).getSec(), mcBB);
                        if (addMuls.size() > 1) {
                            for (int i = 1; i < addMuls.size(); ++i) {
                                MCInstr.buildDivMulAtEnd(MCInstr.MCInstrTag.madd, addMuls.get(i).getFir(), addMuls.get(i).getSec(), mcBB);
                            }
                            MCInstr.buildMfAtEnd(MCInstr.MCInstrTag.mflo, dst, mcBB);
                        }
                        if (gep.containsUser(InstrTag.Call)) {
                            buildAddCI(dst, dst, offsetNum);
                            offsetNum = 0;
                        }
                        mcf.getGepOffsets().put(gep, offsetNum);
                        for (Pair<MCReg, Integer> sll : slls) {
                            MCReg tmp = getNewVReg(null);
                            MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.sll, tmp, sll.getFir(), new MCImm(sll.getSec()), mcBB);
                            MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.addu, dst, dst, tmp, mcBB);
                        }
                    } else if (!slls.isEmpty()) {
                        MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.sll, dst, slls.get(0).getFir(), new MCImm(slls.get(0).getSec()), mcBB);
                        for (int i = 1; i < slls.size(); ++i) {
                            MCReg tmp = getNewVReg(null);
                            MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.sll, tmp, slls.get(i).getFir(), new MCImm(slls.get(i).getSec()), mcBB);
                            MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.addu, dst, dst, tmp, mcBB);
                        }
                        if (gep.containsUser(InstrTag.Call)) {
                            buildAddCI(dst, dst, offsetNum);
                            offsetNum = 0;
                        }
                        mcf.getGepOffsets().put(gep, offsetNum);
                    } else {
                        if (gep.containsUser(InstrTag.Call)) {
                            buildSaveCIIn(dst, offsetNum);
                            offsetNum = 0;
                        } else {
                            mcf.getValue2MCReg().put(gep, ZERO);
                        }
                        mcf.getGepOffsets().put(gep, offsetNum);
                    }
                } else {
                    MCReg base = irOp2mcR(gep.getPointer());
                    if (!addMuls.isEmpty()) {
                        MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.mul, dst, addMuls.get(0).getFir(), addMuls.get(0).getSec(), mcBB);
                        if (addMuls.size() > 1) {
                            for (int i = 1; i < addMuls.size(); ++i) {
                                MCInstr.buildDivMulAtEnd(MCInstr.MCInstrTag.madd, addMuls.get(0).getFir(), addMuls.get(1).getSec(), mcBB);
                            }
                            MCInstr.buildMfAtEnd(MCInstr.MCInstrTag.mflo, dst, mcBB);
                        }
                        MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.addu, dst, dst, base, mcBB);
                        if (gep.containsUser(InstrTag.Call)) {
                            buildAddCI(dst, dst, offsetNum);
                            offsetNum = 0;
                        }
                        mcf.getGepOffsets().put(gep, offsetNum);
                        for (Pair<MCReg, Integer> sll : slls) {
                            MCReg tmp = getNewVReg(null);
                            MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.sll, tmp, sll.getFir(), new MCImm(sll.getSec()), mcBB);
                            MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.addu, dst, dst, tmp, mcBB);
                        }
                    } else if (!slls.isEmpty()) {
                        MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.sll, dst, slls.get(0).getFir(), new MCImm(slls.get(0).getSec()), mcBB);
                        for (int i = 1; i < slls.size(); ++i) {
                            MCReg tmp = getNewVReg(null);
                            MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.sll, tmp, slls.get(i).getFir(), new MCImm(slls.get(i).getSec()), mcBB);
                            MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.addu, dst, dst, tmp, mcBB);
                        }
                        if (gep.containsUser(InstrTag.Call)) {
                            buildAddCI(dst, dst, offsetNum);
                            offsetNum = 0;
                        }
                        mcf.getGepOffsets().put(gep, offsetNum);
                        MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.addu, dst, dst, base, mcBB);
                    } else {
                        if (gep.containsUser(InstrTag.Call)) {
                            buildAddCI(dst, base, offsetNum);
                            offsetNum = 0;
                        } else {
                            MCInstr.buildMoveAtEnd(dst, base, mcBB);
                        }
                        mcf.getGepOffsets().put(gep, offsetNum);
                    }
                }
                break;
            }
            case Eq: {
                if (instr.allUsersAre(InstrTag.Br)) {
                    break;
                }
                Value lhs = ((BinaryInst) instr).lhs();
                Value rhs = ((BinaryInst) instr).rhs();
                if (lhs instanceof Constant.ConstantInt) {
                    Value tmp = lhs; lhs = rhs; rhs = tmp;
                }
                MCReg lhsR = irOp2mcR(lhs);
                MCReg dstR = irOp2mcR(instr);
                if (rhs instanceof Constant.ConstantInt && MCImm.canEncodeImm(((Constant.ConstantInt) rhs).getValue())) {
                    MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.xori, dstR, lhsR, new MCImm(((Constant.ConstantInt) rhs).getValue()), mcBB);
                } else {
                    MCReg rhsR = irOp2mcR(rhs);
                    MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.xor, dstR, lhsR, rhsR, mcBB);
                }
                MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.sltiu, dstR, dstR, new MCImm(1), mcBB);
                break;
            }
            case Ne: {
                if (instr.allUsersAre(InstrTag.Br)) {
                    break;
                }
                Value lhs = ((BinaryInst) instr).lhs();
                Value rhs = ((BinaryInst) instr).rhs();
                if (lhs instanceof Constant.ConstantInt) {
                    Value tmp = lhs; lhs = rhs; rhs = tmp;
                }
                MCReg lhsR = irOp2mcR(lhs);
                MCReg dstR = irOp2mcR(instr);
                if (rhs instanceof Constant.ConstantInt && MCImm.canEncodeImm(((Constant.ConstantInt) rhs).getValue())) {
                    MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.xori, dstR, lhsR, new MCImm(((Constant.ConstantInt) rhs).getValue()), mcBB);
                } else {
                    MCReg rhsR = irOp2mcR(rhs);
                    MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.xor, dstR, lhsR, rhsR, mcBB);
                }
                MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.sltu, dstR, ZERO, dstR, mcBB);
                break;
            }
            case Slt: {
                Value lhs = ((BinaryInst) instr).lhs();
                Value rhs = ((BinaryInst) instr).rhs();
                if (instr.allUsersAre(InstrTag.Br)) {
                    if (lhs instanceof Constant.ConstantInt && ((Constant.ConstantInt) lhs).isZero()) {
                        break;
                    }
                    if (rhs instanceof Constant.ConstantInt && ((Constant.ConstantInt) rhs).isZero()) {
                        break;
                    }
                    MCReg dstR = irOp2mcR(instr);
                    if (rhs instanceof Constant.ConstantInt && MCImm.canEncodeImm(-((Constant.ConstantInt) rhs).getValue())) {
                        MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.addiu, dstR, irOp2mcR(lhs), new MCImm(-((Constant.ConstantInt) rhs).getValue()), mcBB);
                    } else {
                        MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.subu, dstR, irOp2mcR(lhs), irOp2mcR(rhs), mcBB);
                    }
                    break;
                }
                MCReg lhsR = irOp2mcR(lhs);
                MCReg dstR = irOp2mcR(instr);
                if (rhs instanceof Constant.ConstantInt && MCImm.canEncodeImm(((Constant.ConstantInt) rhs).getValue())) {
                    MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.slti, dstR, lhsR, new MCImm(((Constant.ConstantInt) rhs).getValue()), mcBB);
                } else {
                    MCReg rhsR = irOp2mcR(rhs);
                    MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.slt, dstR, lhsR, rhsR, mcBB);
                }
                break;
            }
            case Sle: {
                Value lhs = ((BinaryInst) instr).lhs();
                Value rhs = ((BinaryInst) instr).rhs();
                if (instr.allUsersAre(InstrTag.Br)) {
                    if (lhs instanceof Constant.ConstantInt && ((Constant.ConstantInt) lhs).isZero()) {
                        break;
                    }
                    if (rhs instanceof Constant.ConstantInt && ((Constant.ConstantInt) rhs).isZero()) {
                        break;
                    }
                    MCReg dstR = irOp2mcR(instr);
                    if (rhs instanceof Constant.ConstantInt && MCImm.canEncodeImm(-((Constant.ConstantInt) rhs).getValue())) {
                        MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.addiu, dstR, irOp2mcR(lhs), new MCImm(-((Constant.ConstantInt) rhs).getValue()), mcBB);
                    } else {
                        MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.subu, dstR, irOp2mcR(lhs), irOp2mcR(rhs), mcBB);
                    }
                    break;
                }
                MCReg rhsR = irOp2mcR(rhs);
                MCReg dstR = irOp2mcR(instr);
                if (lhs instanceof Constant.ConstantInt && MCImm.canEncodeImm(((Constant.ConstantInt) lhs).getValue())) {
                    MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.slti, dstR, rhsR, new MCImm(((Constant.ConstantInt) lhs).getValue()), mcBB);
                } else {
                    MCReg lhsR = irOp2mcR(lhs);
                    MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.slt, dstR, rhsR, lhsR, mcBB);
                }
                MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.xori, dstR, dstR, new MCImm(1), mcBB);
                break;
            }
            case Sgt: {
                Value lhs = ((BinaryInst) instr).lhs();
                Value rhs = ((BinaryInst) instr).rhs();
                if (instr.allUsersAre(InstrTag.Br)) {
                    if (lhs instanceof Constant.ConstantInt && ((Constant.ConstantInt) lhs).isZero()) {
                        break;
                    }
                    if (rhs instanceof Constant.ConstantInt && ((Constant.ConstantInt) rhs).isZero()) {
                        break;
                    }
                    MCReg dstR = irOp2mcR(instr);
                    if (rhs instanceof Constant.ConstantInt && MCImm.canEncodeImm(-((Constant.ConstantInt) rhs).getValue())) {
                        MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.addiu, dstR, irOp2mcR(lhs), new MCImm(-((Constant.ConstantInt) rhs).getValue()), mcBB);
                    } else {
                        MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.subu, dstR, irOp2mcR(lhs), irOp2mcR(rhs), mcBB);
                    }
                    break;
                }
                MCReg rhsR = irOp2mcR(rhs);
                MCReg dstR = irOp2mcR(instr);
                if (lhs instanceof Constant.ConstantInt && MCImm.canEncodeImm(((Constant.ConstantInt) lhs).getValue())) {
                    MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.slti, dstR, rhsR, new MCImm(((Constant.ConstantInt) lhs).getValue()), mcBB);
                } else {
                    MCReg lhsR = irOp2mcR(lhs);
                    MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.slt, dstR, rhsR, lhsR, mcBB);
                }
                break;
            }
            case Sge: {
                Value lhs = ((BinaryInst) instr).lhs();
                Value rhs = ((BinaryInst) instr).rhs();
                if (instr.allUsersAre(InstrTag.Br)) {
                    if (lhs instanceof Constant.ConstantInt && ((Constant.ConstantInt) lhs).isZero()) {
                        break;
                    }
                    if (rhs instanceof Constant.ConstantInt && ((Constant.ConstantInt) rhs).isZero()) {
                        break;
                    }
                    MCReg dstR = irOp2mcR(instr);
                    if (rhs instanceof Constant.ConstantInt && MCImm.canEncodeImm(-((Constant.ConstantInt) rhs).getValue())) {
                        MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.addiu, dstR, irOp2mcR(lhs), new MCImm(-((Constant.ConstantInt) rhs).getValue()), mcBB);
                    } else {
                        MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.subu, dstR, irOp2mcR(lhs), irOp2mcR(rhs), mcBB);
                    }
                    break;
                }
                MCReg lhsR = irOp2mcR(lhs);
                MCReg dstR = irOp2mcR(instr);
                if (rhs instanceof Constant.ConstantInt && MCImm.canEncodeImm(((Constant.ConstantInt) rhs).getValue())) {
                    MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.slti, dstR, lhsR, new MCImm(((Constant.ConstantInt) rhs).getValue()), mcBB);
                } else {
                    MCReg rhsR = irOp2mcR(rhs);
                    MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.slt, dstR, lhsR, rhsR, mcBB);
                }
                MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.xori, dstR, dstR, new MCImm(1), mcBB);
                break;
            }
            case Br: {
                Br br = (Br) instr;
                if (br.getOperandNum() == 1) {
                    MCInstr.buildJAtEnd(MCInstr.MCInstrTag.j, mcf.getBBLabelMap().get((BasicBlock) br.getOperand(0)), mcBB);
                } else {
                    Value cond = br.getOperand(0);
                    MCLabel trueBB = mcf.getBBLabelMap().get((BasicBlock) br.getOperand(1));
                    MCLabel falseBB = mcf.getBBLabelMap().get((BasicBlock) br.getOperand(2));
                    if (cond instanceof Constant.ConstantInt) {
                        // TODO: 2022/10/2 不应存在条件为Constant的跳转！
                        if (((Constant.ConstantInt) cond).isZero()) {
                            MCInstr.buildJAtEnd(MCInstr.MCInstrTag.j, trueBB, mcBB);
                        } else {
                            MCInstr.buildJAtEnd(MCInstr.MCInstrTag.j, falseBB, mcBB);
                        }
                    } else if (cond instanceof BinaryInst && cond.allUsersAre(InstrTag.Br)) {
                        BinaryInst bi = (BinaryInst) cond;
                        if (bi.getTag() == InstrTag.Eq) {
                            MCReg lhsR = irOp2mcR(bi.lhs());
                            MCReg rhsR = irOp2mcR(bi.rhs());
                            MCInstr.buildBranchEAtEnd(MCInstr.MCInstrTag.beq, lhsR, rhsR, trueBB, mcBB);
                        } else if (bi.getTag() == InstrTag.Ne) {
                            MCReg lhsR = irOp2mcR(bi.lhs());
                            MCReg rhsR = irOp2mcR(bi.rhs());
                            MCInstr.buildBranchEAtEnd(MCInstr.MCInstrTag.bne, lhsR, rhsR, trueBB, mcBB);
                        } else if (bi.getTag() == InstrTag.Sle ||
                                bi.getTag() == InstrTag.Slt ||
                                bi.getTag() == InstrTag.Sge ||
                                bi.getTag() == InstrTag.Sgt) {
                            if (bi.lhs() instanceof Constant.ConstantInt && ((Constant.ConstantInt) bi.lhs()).isZero()) {
                                MCInstr.buildBranchZAtEnd(cmp2BCondInv(bi.getTag()), irOp2mcR(bi.rhs()), trueBB, mcBB);
                            } else if (bi.rhs() instanceof Constant.ConstantInt && ((Constant.ConstantInt) bi.rhs()).isZero()) {
                                MCInstr.buildBranchZAtEnd(cmp2BCond(bi.getTag()), irOp2mcR(bi.lhs()), trueBB, mcBB);
                            } else {
                                assert mcf.getValue2MCReg().containsKey(bi);
                                MCReg dstR = irOp2mcR(bi);
                                MCInstr.buildBranchZAtEnd(cmp2BCond(bi.getTag()), dstR, trueBB, mcBB);
                            }
                        } else {
                            MCReg condR = irOp2mcR(cond);
                            MCInstr.buildBranchEAtEnd(MCInstr.MCInstrTag.bne, condR, ZERO, trueBB, mcBB);
                        }
                        MCInstr.buildJAtEnd(MCInstr.MCInstrTag.j, falseBB, mcBB);
                    } else {
                        MCReg condR = irOp2mcR(cond);
                        MCInstr.buildBranchEAtEnd(MCInstr.MCInstrTag.bne, condR, ZERO, trueBB, mcBB);
                        MCInstr.buildJAtEnd(MCInstr.MCInstrTag.j, falseBB, mcBB);
                    }
                }
                break;
            }
            case Move: {
                Move move = (Move) instr;
                MCReg dst;
                if (move.getDst() == null) {
                    dst = irOp2mcR(move);
                } else {
                    dst = irOp2mcR(move.getDst());
                }
                if (move.getSrc() instanceof Constant.ConstantInt) {
                    buildSaveCIIn(dst, ((Constant.ConstantInt) move.getSrc()).getValue());
                } else {
                    MCReg src = irOp2mcR(move.getSrc());
                    MCInstr.buildMoveAtEnd(dst, src, mcBB);
                }
                break;
            }
            case Abs: {
                Abs abs = (Abs) instr;
                MCReg dst = irOp2mcR(abs);
                if (abs.getSrc() instanceof Constant.ConstantInt) {
                    buildSaveCIIn(dst, Math.abs(((Constant.ConstantInt) abs.getSrc()).getValue()));
                } else {
                    MCReg src = irOp2mcR(abs.getSrc());
                    MCInstr.buildAbsAtEnd(dst, src, mcBB);
                }
            }
        }
    }

    public MCVirtualReg getNewVReg(Value value) {
        MCVirtualReg vr;
        if (value == null) {
            vr = new MCVirtualReg();
        } else {
            vr = new MCVirtualReg(value.getName());
            mcf.getValue2MCReg().put(value, vr);
        }
        mcf.str2Reg().put(vr.getName(), vr);
        return vr;
    }

    private void buildSaveCIIn(MCReg reg, int ci) {
        if (ci == 0) {
            MCInstr.buildMoveAtEnd(reg, ZERO, mcBB);
            return;
        }
        if (MCImm.canEncodeImm(ci)) {
            MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.addiu, reg, ZERO, new MCImm(ci), mcBB);
            return;
        }
        if (MCImm.HI(ci).getImm() == 0x1001) {
            MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.addiu, reg, GP, MCImm.LO(ci), mcBB);
            return;
        }
        MCInstr.buildLuiAtEnd(reg, MCImm.HI(ci), mcBB);
        if (MCImm.LO(ci).getImm() != 0) {
            MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.addiu, reg, reg, MCImm.LO(ci), mcBB);
        }
    }

    private void buildSw(MCReg val, int offset, MCReg base) {
        if (MCImm.canEncodeImm(offset)) {
            MCInstr.buildSwAtEnd(val, new MCImm(offset), base, mcBB);
            return;
        }
        MCReg tmp = getNewVReg(null);
        MCInstr.buildLuiAtEnd(tmp, MCImm.HI(offset), mcBB);
        if (base.equals(ZERO)) {
            MCInstr.buildSwAtEnd(val, MCImm.LO(offset), tmp, mcBB);
        } else {
            MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.addu, base, base, tmp, mcBB);
            MCInstr.buildSwAtEnd(val, MCImm.LO(offset), base, mcBB);
        }
    }

    private void buildLw(MCReg val, int offset, MCReg base) {
        if (MCImm.canEncodeImm(offset)) {
            MCInstr.buildLwAtEnd(val, new MCImm(offset), base, mcBB);
            return;
        }
        MCReg tmp = getNewVReg(null);
        MCInstr.buildLuiAtEnd(tmp, MCImm.HI(offset), mcBB);
        if (base.equals(ZERO)) {
            MCInstr.buildLwAtEnd(val, MCImm.LO(offset), tmp, mcBB);
        } else {
            MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.addu, base, base, tmp, mcBB);
            MCInstr.buildLwAtEnd(val, MCImm.LO(offset), base, mcBB);
        }
    }

    public MCReg irOp2mcR(Value op) {
        if (!(op instanceof Constant) && mcf.getValue2MCReg().containsKey(op)) {
            return mcf.getValue2MCReg().get(op);
        }
        if (op instanceof Constant) {
            Constant.ConstantInt ci = (Constant.ConstantInt) op;
            if (ci.getValue() == 0) {
                mcf.getValue2MCReg().put(ci, ZERO);
                return ZERO;
            }
            MCVirtualReg res = getNewVReg(ci);
            buildSaveCIIn(res, ci.getValue());
            mcf.getValue2MCReg().put(ci, res);
            return res;
        } else if (op instanceof Instruction) {
            MCVirtualReg vr = getNewVReg(op);
            mcf.getValue2MCReg().put(op, vr);
            return vr;
        } else if (op instanceof Function.Param) {
            Function.Param param = (Function.Param) op;
            MCVirtualReg vr = getNewVReg(op);
            mcf.getValue2MCReg().put(op, vr);
            int index = mcf.getFunc().getParamList().indexOf(param);
            if (index == 0) {
                MCInstr.buildMoveAtEnd(vr, A0, mcBB);
            } else if (index == 1) {
                MCInstr.buildMoveAtEnd(vr, A1, mcBB);
            } else if (index == 2) {
                MCInstr.buildMoveAtEnd(vr, A2, mcBB);
            } else if (index == 3) {
                MCInstr.buildMoveAtEnd(vr, A3, mcBB);
            } else {
                MCInstr.buildLwAtEnd(vr, new MCImm(4 * index), SP, mcBB);
                this.mcf.getLwsToGetArg().add((MCInstr.MCLw) mcBB.getList().getLast().getValue());
            }
            return vr;
        } else {
            throw new RuntimeException("irOp2mcR GlobalVariable!");
        }
    }

    public void buildAddCI(MCReg dst, MCReg lhs, int rhs) {
        if (rhs == 0) {
            if (!dst.equals(lhs)) {
                MCInstr.buildMoveAtEnd(dst, lhs, mcBB);
            }
        } else if (MCImm.canEncodeImm(rhs)) {
            MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.addiu, dst, lhs, new MCImm(rhs), mcBB);
        } else if (MCImm.canEncodeImm(-rhs)) {
            MCReg rhsReg = irOp2mcR(new Constant.ConstantInt(-rhs));
            MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.subu, dst, lhs, rhsReg, mcBB);
        } else if (MCImm.HI(rhs).getImm() == 0x1001) {
            MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.addu, dst, lhs, GP, mcBB);
            MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.addiu, dst, dst, MCImm.LO(rhs), mcBB);
        } else {
            MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.addu, dst, lhs, irOp2mcR(new Constant.ConstantInt(rhs)), mcBB);
        }
    }

    private void buildBitCI(MCReg dst, MCReg lhs, int rhs, MCInstr.MCInstrTag tag, MCInstr.MCInstrTag tagi) {
        if (rhs == 0) {
            if (tag == MCInstr.MCInstrTag.xor || tag == MCInstr.MCInstrTag.or) {
                if (!dst.equals(lhs)) {
                    MCInstr.buildMoveAtEnd(dst, lhs, mcBB);
                }
            } else {
                MCInstr.buildMoveAtEnd(dst, ZERO, mcBB);
            }
        } else if (MCImm.canEncodeImm(rhs)) {
            MCInstr.buildBinaryIAtEnd(tagi, dst, lhs, new MCImm(rhs), mcBB);
        } else {
            MCInstr.buildBinaryRAtEnd(tag, dst, lhs, irOp2mcR(new Constant.ConstantInt(rhs)), mcBB);
        }
    }

    private MCInstr.MCInstrTag cmp2BCond(InstrTag tag) {
        switch (tag) {
            case Sle: return MCInstr.MCInstrTag.blez;
            case Slt: return MCInstr.MCInstrTag.bltz;
            case Sge: return MCInstr.MCInstrTag.bgez;
            case Sgt: return MCInstr.MCInstrTag.bgtz;
            default: {
                throw new RuntimeException("not legal branch cond!");
            }
        }
    }

    private MCInstr.MCInstrTag cmp2BCondInv(InstrTag tag) {
        switch (tag) {
            case Sle: return MCInstr.MCInstrTag.bgez;
            case Slt: return MCInstr.MCInstrTag.bgtz;
            case Sge: return MCInstr.MCInstrTag.blez;
            case Sgt: return MCInstr.MCInstrTag.bltz;
            default: {
                throw new RuntimeException("not legal branch cond!");
            }
        }
    }
}
