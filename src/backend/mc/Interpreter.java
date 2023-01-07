package backend.mc;

import backend.mc.MCOperand.*;
import backend.mc.MCInstr.*;
import backend.register.RegisterManager;
import utils.Config;
import utils.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;

public class Interpreter {
    @SuppressWarnings("FieldCanBeLocal")
    private final boolean showMemAtLast = false;
    @SuppressWarnings({"FieldCanBeLocal"})
    private final boolean oneStep = false;
    @SuppressWarnings({"FieldCanBeLocal"})
    private final String dumpInFunction = "";
    private char last_char;
    private boolean have_last_char = false;

    private static class Datas {
        private final boolean allowVirtualReg;
        private final HashMap<RegisterManager.MCPhyRegTag, Integer> reg = new HashMap<>();
        private final HashMap<Integer, Byte> mem = new HashMap<>();
        private final HashMap<MCVirtualReg, Integer> vrVal = new HashMap<>();
        private int hi = 0;
        private int lo = 0;

        public Datas(boolean allowVirtualReg) {
            for (RegisterManager.MCPhyRegTag tag : RegisterManager.MCPhyRegTag.values()) {
                reg.put(tag, 0);
            }
            reg.put(RegisterManager.MCPhyRegTag.gp, 0x10008000);
            reg.put(RegisterManager.MCPhyRegTag.sp, 0x7fffeffc);
            this.allowVirtualReg = allowVirtualReg;
        }

        int getRegVal(MCReg r) {
            if (r instanceof MCPhyReg) {
                return reg.get(((MCPhyReg) r).getTag());
            } else {
                if (allowVirtualReg) {
                    return vrVal.getOrDefault((MCVirtualReg) r, 0);
                } else {
                    System.out.println(r);
                    throw new RuntimeException("Virtual Reg Is Not Allowed In This Interpreter!!!");
                }
            }
        }

        void setRegVal(MCReg r, int val) {
            if (r instanceof MCPhyReg) {
                reg.put(((MCPhyReg) r).getTag(), val);
            } else {
                if (allowVirtualReg) {
                    vrVal.put((MCVirtualReg) r, val);
                } else {
                    throw new RuntimeException("Virtual Reg Is Not Allowed In This Interpreter!!!");
                }
            }
        }

        void setRegVal(RegisterManager.MCPhyRegTag r, int val) {
            reg.put(r, val);
        }

        int getRegVal(RegisterManager.MCPhyRegTag r) {
            return reg.get(r);
        }

        int getMemInt(int pos) {
            assert pos % 4 == 0;
            int v3 = ((int) getMemByte(pos + 3)) & 0xFF;
            int v2 = ((int) getMemByte(pos + 2)) & 0xFF;
            int v1 = ((int) getMemByte(pos + 1)) & 0xFF;
            int v0 = ((int) getMemByte(pos)) & 0xFF;
            return (v3 << 24) + (v2 << 16) + (v1 << 8) + v0;
        }

        void setMemInt(int pos, int val) {
            assert pos % 4 == 0;
            byte v3 = (byte) (val >>> 24);
            byte v2 = (byte) (val >>> 16);
            byte v1 = (byte) (val >>> 8);
            byte v0 = (byte) val;
            setMemByte(pos + 3, v3);
            setMemByte(pos + 2, v2);
            setMemByte(pos + 1, v1);
            setMemByte(pos, v0);
        }

        byte getMemByte(int pos) {
            return mem.getOrDefault(pos, (byte) 0);
        }

        void setMemByte(int pos, byte val) {
            mem.put(pos, val);
        }

        public static String toHexString(int val) {
            StringBuilder str = new StringBuilder(Integer.toHexString(val));
            while (str.length() < 8) {
                str.insert(0, "0");
            }
            return str.toString();
        }

        public static char toChar(byte b) {
            if ((40 <= b && b <= 126) || b == 32 || b == 33) {
                return (char) b;
            } else if (b == '\n') {
                return '\\';
            } else {
                return '#';
            }
        }

        public static String toCharString(int val) {
            byte v1 = (byte) (val >> 24);
            byte v2 = (byte) (val >> 16);
            byte v3 = (byte) (val >> 8);
            byte v4 = (byte) val;
            return String.valueOf(toChar(v4)) +
                    toChar(v3) +
                    toChar(v2) +
                    toChar(v1);
        }

        @SuppressWarnings({"unused"})
        void dumpMem() {
            ArrayList<Integer> visited = new ArrayList<>();
            for (Integer pos : mem.keySet()) {
                if (!visited.contains(pos - pos % 4)) {
                    visited.add(pos - pos % 4);
                }
            }
            Collections.sort(visited);
            System.out.println("dump memory -------------------------------------");
            for (Integer pos : visited) {
                System.out.println(toHexString(pos) + " : " + toHexString(getMemInt(pos)) + " " + toCharString(getMemInt(pos)));
            }
            System.out.println("-------------------------------------------------");
        }

        @SuppressWarnings({"unused"})
        void dumpData() {
            ArrayList<Integer> visited = new ArrayList<>();
            for (Integer pos : mem.keySet()) {
                if (!visited.contains(pos - pos % 4) && pos < 0x30000000) {
                    visited.add(pos - pos % 4);
                }
            }
            Collections.sort(visited);
            System.out.println("dump memory -------------------------------------");
            for (Integer pos : visited) {
                System.out.println(toHexString(pos) + " : " + toHexString(getMemInt(pos)) + " " + toCharString(getMemInt(pos)));
            }
            System.out.println("-------------------------------------------------");
        }

        @SuppressWarnings({"unused"})
        void dumpReg() {
            System.out.println("dump register -----------------------------------");
            RegisterManager.MCPhyRegTag[] tags = RegisterManager.MCPhyRegTag.values();
            for (int i = 0; i < 32; i += 4) {
                for (int j = i; j < i + 4; ++j) {
                    System.out.printf(tags[j] + ": %08x ", reg.get(tags[j]));
                }
                System.out.println();
            }
            System.out.println("-------------------------------------------------");
        }

        public int getHi() {
            return hi;
        }

        public int getLo() {
            return lo;
        }

        public void setHi(int hi) {
            this.hi = hi;
        }

        public void setLo(int lo) {
            this.lo = lo;
        }
    }

    private static MCInstr startFromBB(MCBasicBlock bb) {
        while (!bb.node.isGuard() && bb.getList().isEmpty()) {
            bb = bb.node.getNext().getValue();
        }
        if (bb.node.isGuard()) {
            return null;
        } else {
            return bb.getList().getEntry().getValue();
        }
    }

    private static MCInstr directNext(MCInstr instr) {
        if (!instr.node.getNext().isGuard()) {
            return instr.node.getNext().getValue();
        } else {
            MCBasicBlock thisBB = instr.node.getParent().getHolder();
            if (thisBB.node.getNext().isGuard()) {
                return null;
            } else {
                return startFromBB(thisBB.node.getNext().getValue());
            }
        }
    }

    private static MCInstr labelNext(MCLabel label) {
        return startFromBB(MCModule.module.label2BB.get(label));
    }

    private static boolean condBranchE(int lhs, int rhs, MCInstrTag tag) {
        switch (tag) {
            case beq: return lhs == rhs;
            case bne: return lhs != rhs;
        }
        throw new RuntimeException("condBranchE : " + tag);
    }

    private static boolean condBranchZ(int val, MCInstrTag tag) {
        switch (tag) {
            case blez: return val <= 0;
            case bltz: return val < 0;
            case bgez: return val >= 0;
            case bgtz: return val > 0;
        }
        throw new RuntimeException("condBranchZ : " + tag);
    }

    public static char getchar() {
        try {
            return (char) System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }

    private int getNextInt() {
        int res = 0;
        int f = 1;
        if (!have_last_char) {
            last_char = getchar();
            have_last_char = true;
        }
        char c = last_char;
        while (Character.isWhitespace(c)) {
            c = getchar();
        }
        if (c == '-') {
            f = -1;
            c = getchar();
        }
        while (Character.isDigit(c)) {
            res = res * 10 + c - '0';
            c = getchar();
        }
        last_char = c;
        return res * f;
    }

    private int getNextChar() {
        if (!have_last_char) {
            last_char = getchar();
            have_last_char = true;
        }
        char c = last_char;
        last_char = getchar();
        return c;
    }

    private void toNextLine() {
        if (!have_last_char) {
            last_char = getchar();
            have_last_char = true;
        }
        char c = last_char;
        while (c != '\n') {
            c = getchar();
        }
        last_char = c;
    }

    public void interpret(MCModule m, boolean allowVirtualRegs) {
        int divCount = 0, multCount = 0, jumpBranchCount = 0, memCount = 0, otherCount = 0;
        MCFunction mcf = m.functions.get("main");
        MCBasicBlock mcBB = mcf.getList().getEntry().getValue();
        MCInstr instr = startFromBB(mcBB);
        Datas datas = new Datas(allowVirtualRegs);
        int dotDataPos = 0x10010000;
        int returnValue = 0;
        for (MCGlobalData gd : m.mcGlobalDatas) {
            if (gd instanceof MCGlobalVariable) {
                MCGlobalVariable mcGV = (MCGlobalVariable) gd;
                while (dotDataPos % 4 != 0) {
                    ++dotDataPos;
                }
                for (MCGlobalVariable.Seg s : mcGV.getSegs().getSegs()) {
                    if (s.getType() == MCGlobalVariable.Seg.SegType.word) {
                        datas.setMemInt(dotDataPos, s.getVal());
                        dotDataPos += 4;
                    } else {
                        dotDataPos += s.getVal();
                    }
                }
            } else {
                String mcString = ((MCString) gd).getContent();
                for (int i = 0; i < mcString.length(); ++i) {
                    char c = mcString.charAt(i);
                    if (c == '\\') {
                        datas.setMemByte(dotDataPos, (byte) '\n');
                        ++i;
                    } else {
                        datas.setMemByte(dotDataPos, (byte) c);
                    }
                    ++dotDataPos;
                }
                datas.setMemByte(dotDataPos, (byte) 0);
                ++dotDataPos;
            }
        }
        while (instr != null) {
            if (oneStep) {
                System.out.println(instr);
            }
            if (dumpInFunction.equals(instr.getMCFunction().getName().substring(5))) {
                System.out.println(instr);
            }
            if (instr instanceof MCLui) {
                MCLui lui = (MCLui) instr;
                datas.setRegVal(lui.getDst(), lui.getValue().getImm() << 16);
                instr = directNext(instr);
                ++otherCount;
            } else if (instr instanceof MCBinaryI) {
                MCBinaryI i = (MCBinaryI) instr;
                int lhs = datas.getRegVal(i.getLhs());
                int rhs = i.getRhs().getImm();
                if (i.getTag() == MCInstrTag.addiu) {
                    datas.setRegVal(i.getDst(), lhs + rhs);
                    ++otherCount;
                } else if (i.getTag() == MCInstrTag.sll) {
                    datas.setRegVal(i.getDst(), lhs << rhs);
                    ++otherCount;
                } else if (i.getTag() == MCInstrTag.sra) {
                    datas.setRegVal(i.getDst(), lhs >> rhs);
                    ++otherCount;
                } else if (i.getTag() == MCInstrTag.srl) {
                    datas.setRegVal(i.getDst(), lhs >>> rhs);
                    ++otherCount;
                } else if (i.getTag() == MCInstrTag.slti) {
                    datas.setRegVal(i.getDst(), lhs < rhs ? 1 : 0);
                    ++otherCount;
                } else if (i.getTag() == MCInstrTag.sltiu) {
                    datas.setRegVal(i.getDst(), Integer.compareUnsigned(lhs, rhs) < 0 ? 1 : 0);
                    ++otherCount;
                } else if (i.getTag() == MCInstrTag.xori) {
                    datas.setRegVal(i.getDst(), lhs ^ rhs);
                    ++otherCount;
                } else if (i.getTag() == MCInstrTag.andi) {
                    datas.setRegVal(i.getDst(), lhs & rhs);
                    ++otherCount;
                } else if (i.getTag() == MCInstrTag.ori) {
                    datas.setRegVal(i.getDst(), lhs | rhs);
                    ++otherCount;
                } else {
                    throw new RuntimeException("interpret : " + i.getTag());
                }
                instr = directNext(instr);
            } else if (instr instanceof MCBinaryR) {
                MCBinaryR r = (MCBinaryR) instr;
                int lhs = datas.getRegVal(r.getLhs());
                int rhs = datas.getRegVal(r.getRhs());
                int res;
                if (r.getTag() == MCInstrTag.addu) {
                    res = lhs + rhs;
                    ++otherCount;
                } else if (r.getTag() == MCInstrTag.subu) {
                    res = lhs - rhs;
                    ++otherCount;
                } else if (r.getTag() == MCInstrTag.slt) {
                    res = (lhs < rhs ? 1 : 0);
                    ++otherCount;
                } else if (r.getTag() == MCInstrTag.sltu) {
                    res = Integer.compareUnsigned(lhs, rhs) < 0 ? 1 : 0;
                    ++otherCount;
                } else if (r.getTag() == MCInstrTag.mul) {
                    res = lhs * rhs;
                    datas.setHi((int) ((((long) lhs) * ((long) rhs)) >> 32));
                    datas.setLo((int) (((long) lhs) * ((long) rhs)));
                    ++multCount;
                } else if (r.getTag() == MCInstrTag.xor) {
                    res = lhs ^ rhs;
                    ++otherCount;
                } else if (r.getTag() == MCInstrTag.and) {
                    res = lhs & rhs;
                    ++otherCount;
                } else if (r.getTag() == MCInstrTag.or) {
                    res = lhs | rhs;
                    ++otherCount;
                } else {
                    throw new RuntimeException("wrong binary r tag " + r.getTag() + "!!!");
                }
                datas.setRegVal(r.getDst(), res);
                instr = directNext(instr);
            } else if (instr instanceof MCMove) {
                int val = datas.getRegVal(((MCMove) instr).getSrc());
                datas.setRegVal(((MCMove) instr).getDst(), val);
                instr = directNext(instr);
                ++otherCount;
            } else if (instr instanceof MCAbs) {
                int val = datas.getRegVal(((MCAbs) instr).getSrc());
                datas.setRegVal(((MCAbs) instr).getDst(), Math.abs(val));
                instr = directNext(instr);
                ++otherCount;
            } else if (instr instanceof MCSyscall) {
                int v0 = datas.getRegVal(RegisterManager.MCPhyRegTag.v0);
                if (v0 == 1) {
                    int a0 = datas.getRegVal(RegisterManager.MCPhyRegTag.a0);
                    System.out.print(a0);
                } else if (v0 == 4) {
                    int st = datas.getRegVal(RegisterManager.MCPhyRegTag.a0);
                    byte curByte;
                    while ((curByte = datas.getMemByte(st)) != 0) {
                        System.out.print((char) curByte);
                        ++st;
                    }
                } else if (v0 == 5) {
                    int val = getNextInt();
                    datas.setRegVal(RegisterManager.MCPhyRegTag.v0, val);
                } else if (v0 == 10) {
                    break;
                } else if (v0 == 17) {
                    returnValue = datas.getRegVal(RegisterManager.MCPhyRegTag.a0);
                    break;
                } else if (v0 == 12) {
                    int v = getNextChar();
                    datas.setRegVal(RegisterManager.MCPhyRegTag.v0, v);
                } else if (v0 == 11) {
                    int v = datas.getRegVal(RegisterManager.MCPhyRegTag.a0);
                    System.out.print((char) v);
                }
                instr = directNext(instr);
                ++otherCount;
            } else if (instr instanceof MCJ) {
                MCJ j = (MCJ) instr;
                if (instr.getTag() != MCInstrTag.j) {
                    MCInstr next = directNext(instr);
                    if (next != null) {
                        datas.setRegVal(RegisterManager.MCPhyRegTag.ra, next.getId());
                    } else {
                        datas.setRegVal(RegisterManager.MCPhyRegTag.ra, -1);
                    }
                }
                instr = labelNext(j.getTarget());
                ++jumpBranchCount;
            } else if (instr instanceof MCJr) {
                MCJr jr = (MCJr) instr;
                MCReg target = jr.getTarget();
                int ra = datas.getRegVal(target);
                if (ra == -1) {
                    instr = null;
                } else {
                    instr = m.idToMCInstr.get(ra);
                }
                ++jumpBranchCount;
            } else if (instr instanceof MCLw) {
                System.out.println(instr + " " + instr.node.getParent().getHolder());
                MCLw lw = (MCLw) instr;
                int offset = lw.getOffset().getImm();
                int base = datas.getRegVal(lw.getBase());
                int val = datas.getMemInt(base + offset);
                datas.setRegVal(lw.getDst(), val);
                instr = directNext(instr);
                ++memCount;
            } else if (instr instanceof MCSw) {
                MCSw sw = (MCSw) instr;
                int data = datas.getRegVal(sw.getData());
                int offset = sw.getOffset().getImm();
                int base = datas.getRegVal(sw.getBase());
                datas.setMemInt(base + offset, data);
                instr = directNext(instr);
                ++memCount;
            } else if (instr instanceof MCMf) {
                MCMf mf = (MCMf) instr;
                if (mf.getTag() == MCInstrTag.mflo) {
                    datas.setRegVal(mf.getDst(), datas.getLo());
                } else {
                    datas.setRegVal(mf.getDst(), datas.getHi());
                }
                instr = directNext(instr);
                ++otherCount;
            } else if (instr instanceof MCDivMul) {
                MCDivMul dm = (MCDivMul) instr;
                int lhs = datas.getRegVal(dm.getLhs());
                int rhs = datas.getRegVal(dm.getRhs());
                if (dm.getTag() == MCInstrTag.div) {
                    datas.setLo(lhs / rhs);
                    datas.setHi(lhs % rhs);
                    ++divCount;
                } else if (dm.getTag() == MCInstrTag.mult) {
                    datas.setHi((int) ((((long) lhs) * ((long) rhs)) >> 32));
                    datas.setLo((int) (((long) lhs) * ((long) rhs)));
                    ++multCount;
                } else {
                    datas.setHi(datas.getHi() + (int) ((((long) lhs) * ((long) rhs)) >> 32));
                    datas.setLo(datas.getLo() + (int) (((long) lhs) * ((long) rhs)));
                    ++multCount;
                }
                instr = directNext(instr);
            } else if (instr instanceof MCBranchE) {
                MCBranchE be = (MCBranchE) instr;
                int lhs = datas.getRegVal(be.getLhs());
                int rhs = datas.getRegVal(be.getRhs());
                if (condBranchE(lhs, rhs, be.getTag())) {
                    instr = labelNext(be.getTarget());
                } else {
                    instr = directNext(be);
                }
                ++jumpBranchCount;
            } else if (instr instanceof MCBranchZ) {
                MCBranchZ bz = (MCBranchZ) instr;
                int val = datas.getRegVal(bz.getVal());
                if (condBranchZ(val, bz.getTag())) {
                    instr = labelNext(bz.getTarget());
                } else {
                    instr = directNext(bz);
                }
                ++jumpBranchCount;
            } else if (instr instanceof MCGetarray) {
                MCGetarray mcGa = (MCGetarray) instr;
                int n = getNextInt();
                int base = datas.getRegVal(mcGa.getBase());
                for (int i = 0; i < n; ++i) {
                    int x = getNextInt();
                    datas.setMemInt(base + i * 4, x);
                }
                datas.setRegVal(mcGa.getN(), n);
                instr = directNext(instr);
            } else if (instr instanceof MCPutarray) {
                MCPutarray mcPa = (MCPutarray) instr;
                int n = datas.getRegVal(mcPa.getN());
                System.out.printf("%d:", n);
                int base = datas.getRegVal(mcPa.getBase());
                for (int i = 0; i < n; ++i) {
                    System.out.printf(" %d", datas.getMemInt(base + i * 4));
                }
                System.out.println();
                instr = directNext(instr);
            }
            if (oneStep) {
                datas.dumpReg();
                datas.dumpMem();
            }
            if (oneStep) {
                toNextLine();
            }
            if (instr != null && dumpInFunction.equals(instr.getMCFunction().getName().substring(5))) {
                datas.dumpReg();
                // datas.dumpMem();
            }
            if (instr == null) {
                throw new RuntimeException("program ended abnormally!!!");
            }
        }
        if (showMemAtLast) {
            datas.dumpMem();
        }
        Logger.printPerformanceResult(Config.statisticFileName, divCount, multCount, jumpBranchCount, memCount, otherCount);
        System.exit(returnValue);
    }
}
