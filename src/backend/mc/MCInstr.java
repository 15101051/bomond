package backend.mc;

import backend.mc.MCOperand.*;
import backend.register.RegisterManager;
import utils.IList;

import java.util.ArrayList;

public abstract class MCInstr {
    public final IList.INode<MCInstr, MCBasicBlock> node = new IList.INode<>(this);
    private final ArrayList<MCOperand.MCReg> useReg = new ArrayList<>();
    private final ArrayList<MCOperand.MCReg> defReg = new ArrayList<>();
    private static final MCPhyReg A0 = new MCPhyReg(RegisterManager.MCPhyRegTag.a0);
    private static final MCPhyReg V0 = new MCPhyReg(RegisterManager.MCPhyRegTag.v0);
    private final MCInstrTag tag;
    private final int id;
    private static int curId = 0;
    public String comment;

    public MCInstr(MCInstrTag tag, MCBasicBlock mcBB) {
        this.tag = tag;
        if (mcBB != null) {
            this.node.insertAtEnd(mcBB.getList());
        }
        this.id = curId++;
        MCModule.module.idToMCInstr.put(this.id, this);
    }

    public MCFunction getMCFunction() {
        return this.node.getParent().getHolder().getMCFunction();
    }

    public int getId() {
        return id;
    }

    public ArrayList<MCReg> getUseReg() {
        return useReg;
    }

    public ArrayList<MCReg> getDefReg() {
        return defReg;
    }

    public void addUse(MCReg op) {
        useReg.add(op);
    }

    public void addDef(MCReg op) {
        defReg.add(op);
    }

    public void replaceDefReg(MCReg oldOp, MCReg newOp) {
        defReg.remove(oldOp);
        addDef(newOp);
    }

    public void replaceUseReg(MCReg oldOp, MCReg newOp) {
        useReg.remove(oldOp);
        addUse(newOp);
    }

    public abstract void replaceOperandOfInstr(MCReg oldOp, MCReg newOp);

    public abstract void replaceUseOfInstr(MCReg oldOp, MCReg newOp);

    public MCInstrTag getTag() {
        return tag;
    }

    public static class MCLui extends MCInstr {
        private MCReg dst;
        private final MCImm value;

        MCLui(MCInstrTag tag, MCReg dst, MCImm value, MCBasicBlock mcBB) {
            super(tag, mcBB);
            assert tag.isLui();
            this.dst = dst;
            this.value = value;
            addDef(dst);
        }

        @Override
        public String toString() {
            return "\t" + this.getTag() + "\t\t" + dst + ", " + value + "\n";
        }

        public void setDst(MCReg dst) {
            replaceDefReg(this.dst, dst);
            this.dst = dst;
        }

        public MCReg getDst() {
            return dst;
        }

        public MCImm getValue() {
            return value;
        }

        @Override
        public void replaceOperandOfInstr(MCReg oldOp, MCReg newOp) {
            if (dst.equals(oldOp)) {
                setDst(newOp);
            }
        }

        @Override
        public void replaceUseOfInstr(MCReg oldOp, MCReg newOp) {}
    }

    public static class MCBinaryI extends MCInstr {
        private MCReg dst;
        private MCReg lhs;
        private final MCImm rhs;

        MCBinaryI(MCInstrTag tag, MCReg dst, MCReg lhs, MCImm rhs, MCBasicBlock mcBB) {
            super(tag, mcBB);
            assert tag.isBinaryI();
            this.dst = dst;
            this.lhs = lhs;
            this.rhs = rhs;
            addDef(dst);
            addUse(lhs);
        }

        public void setDst(MCReg dst) {
            replaceDefReg(this.dst, dst);
            this.dst = dst;
        }

        public void setLhs(MCReg lhs) {
            replaceUseReg(this.lhs, lhs);
            this.lhs = lhs;
        }

        public MCReg getLhs() {
            return lhs;
        }

        public MCImm getRhs() {
            return rhs;
        }

        public MCReg getDst() {
            return dst;
        }

        @Override
        public String toString() {
            return "\t" + this.getTag() + "\t" + dst + ", " + lhs + ", " + rhs + "\n";
        }

        @Override
        public void replaceOperandOfInstr(MCReg oldOp, MCReg newOp) {
            if (dst.equals(oldOp)) {
                setDst(newOp);
            }
            if (lhs.equals(oldOp)) {
                setLhs(newOp);
            }
        }

        @Override
        public void replaceUseOfInstr(MCReg oldOp, MCReg newOp) {
            if (lhs.equals(oldOp)) {
                setLhs(newOp);
            }
        }
    }

    public static class MCBinaryR extends MCInstr {
        private MCReg dst;
        private MCReg lhs;
        private MCReg rhs;

        MCBinaryR(MCInstrTag tag, MCReg dst, MCReg lhs, MCReg rhs, MCBasicBlock mcBB) {
            super(tag, mcBB);
            assert tag.isBinaryR();
            this.dst = dst;
            this.lhs = lhs;
            this.rhs = rhs;
            addDef(dst);
            addUse(lhs);
            addUse(rhs);
        }

        public void setDst(MCReg dst) {
            replaceDefReg(this.dst, dst);
            this.dst = dst;
        }

        public void setLhs(MCReg lhs) {
            replaceUseReg(this.lhs, lhs);
            this.lhs = lhs;
        }

        public void setRhs(MCReg rhs) {
            replaceUseReg(this.rhs, rhs);
            this.rhs = rhs;
        }

        public MCReg getLhs() {
            return lhs;
        }

        public MCReg getRhs() {
            return rhs;
        }

        public MCReg getDst() {
            return dst;
        }

        @Override
        public String toString() {
            return "\t" + this.getTag() + "\t" + dst + ", " + lhs + ", " + rhs + "\n";
        }

        @Override
        public void replaceOperandOfInstr(MCReg oldOp, MCReg newOp) {
            if (dst.equals(oldOp)) {
                setDst(newOp);
            }
            if (lhs.equals(oldOp)) {
                setLhs(newOp);
            }
            if (rhs.equals(oldOp)) {
                setRhs(newOp);
            }
        }

        @Override
        public void replaceUseOfInstr(MCReg oldOp, MCReg newOp) {
            if (lhs.equals(oldOp)) {
                setLhs(newOp);
            }
            if (rhs.equals(oldOp)) {
                setRhs(newOp);
            }
        }
    }

    public static class MCMove extends MCInstr {
        private MCReg dst;
        private MCReg src;

        MCMove(MCInstrTag tag, MCReg dst, MCReg src, MCBasicBlock mcBB) {
            super(tag, mcBB);
            assert tag.isMove();
            this.dst = dst;
            this.src = src;
            addDef(dst);
            addUse(src);
        }

        public MCReg getDst() {
            return dst;
        }

        public MCReg getSrc() {
            return src;
        }

        public void setDst(MCReg dst) {
            replaceDefReg(this.dst, dst);
            this.dst = dst;
        }

        public void setSrc(MCReg src) {
            replaceUseReg(this.src, src);
            this.src = src;
        }

        @Override
        public String toString() {
            return "\tmovz\t" + dst + ", " + src + ", $zero\n";
        }

        @Override
        public void replaceOperandOfInstr(MCReg oldOp, MCReg newOp) {
            if (this.dst.equals(oldOp)) {
                this.setDst(newOp);
            }
            if (this.src.equals(oldOp)) {
                this.setSrc(newOp);
            }
        }

        @Override
        public void replaceUseOfInstr(MCReg oldOp, MCReg newOp) {
            if (this.src.equals(oldOp)) {
                this.setSrc(newOp);
            }
        }
    }

    public static class MCAbs extends MCInstr {
        private MCReg dst;
        private MCReg src;

        MCAbs(MCInstrTag tag, MCReg dst, MCReg src, MCBasicBlock mcBB) {
            super(tag, mcBB);
            assert tag.isAbs();
            this.dst = dst;
            this.src = src;
            addDef(dst);
            addUse(src);
        }

        public MCReg getDst() {
            return dst;
        }

        public MCReg getSrc() {
            return src;
        }

        public void setDst(MCReg dst) {
            replaceDefReg(this.dst, dst);
            this.dst = dst;
        }

        public void setSrc(MCReg src) {
            replaceUseReg(this.src, src);
            this.src = src;
        }

        @Override
        public String toString() {
            return "\tabs\t" + dst + ", " + src + "\n";
        }

        @Override
        public void replaceOperandOfInstr(MCReg oldOp, MCReg newOp) {
            if (this.dst.equals(oldOp)) {
                this.setDst(newOp);
            }
            if (this.src.equals(oldOp)) {
                this.setSrc(newOp);
            }
        }

        @Override
        public void replaceUseOfInstr(MCReg oldOp, MCReg newOp) {
            if (this.src.equals(oldOp)) {
                this.setSrc(newOp);
            }
        }
    }

    public static class MCSyscall extends MCInstr {
        enum SyscallType {
            PrintInt, PrintStr, ReadInt, ReadChar, PrintChar, Exit
        }

        MCSyscall(MCInstrTag tag, SyscallType type, MCBasicBlock mcBB) {
            super(tag, mcBB);
            assert tag.isSyscall();
            switch (type) {
                case PrintInt: case PrintStr: case PrintChar: case Exit:
                    addUse(A0);
                    addUse(V0);
                    break;
                case ReadInt: case ReadChar:
                    addUse(V0);
                    break;
            }
        }

        @Override
        public String toString() {
            return "\tsyscall\n";
        }

        @Override
        public void replaceOperandOfInstr(MCReg oldOp, MCReg newOp) {}

        @Override
        public void replaceUseOfInstr(MCReg oldOp, MCReg newOp) {}
    }

    public static class MCCall extends MCInstr {
        private final MCFunction callee;
        private final int argsCnt;

        MCCall(MCInstrTag tag, MCFunction callee, int argsCnt, MCBasicBlock mcBB) {
            super(tag, mcBB);
            assert tag.isCall();
            this.callee = callee;
            this.argsCnt = argsCnt;
            addDef(new MCPhyReg(RegisterManager.MCPhyRegTag.v0));
        }

        public MCFunction getCallee() {
            return callee;
        }

        public int getArgsCnt() {
            return argsCnt;
        }

        @Override
        public String toString() {
            return "\tcall\t" + callee.getName() + ", " + argsCnt + "\n";
        }

        @Override
        public void replaceOperandOfInstr(MCReg oldOp, MCReg newOp) {}

        @Override
        public void replaceUseOfInstr(MCReg oldOp, MCReg newOp) {}
    }

    public static class MCJ extends MCInstr {
        private MCLabel target;

        MCJ(MCInstrTag tag, MCLabel target, MCBasicBlock mcBB) {
            super(tag, mcBB);
            assert tag.isJ();
            this.target = target;
        }

        public void setTarget(MCLabel target) {
            this.target = target;
        }

        public MCLabel getTarget() {
            return target;
        }

        @Override
        public String toString() {
            return "\t" + this.getTag() + "\t\t" + target + "\n";
        }

        @Override
        public void replaceOperandOfInstr(MCReg oldOp, MCReg newOp) {}

        @Override
        public void replaceUseOfInstr(MCReg oldOp, MCReg newOp) {}
    }

    public static class MCJr extends MCInstr {
        private final MCReg target;

        MCJr(MCInstrTag tag, MCReg target, boolean haveReturn, MCBasicBlock mcBB) {
            super(tag, mcBB);
            assert tag.isJr();
            this.target = target;
            addUse(target);
            if (haveReturn) {
                addUse(V0);
            }
        }

        public MCReg getTarget() {
            return target;
        }

        @Override
        public String toString() {
            return "\t" + this.getTag() + "\t\t" + target + "\n";
        }

        @Override
        public void replaceOperandOfInstr(MCReg oldOp, MCReg newOp) {}

        @Override
        public void replaceUseOfInstr(MCReg oldOp, MCReg newOp) {}
    }

    public static class MCLw extends MCInstr {
        private MCReg dst;
        private MCImm offset;
        private MCReg base;

        MCLw(MCInstrTag tag, MCReg dst, MCImm offset, MCReg base, MCBasicBlock mcBB) {
            super(tag, mcBB);
            assert tag.isLw();
            this.dst = dst;
            this.offset = offset;
            this.base = base;
            addDef(dst);
            addUse(base);
        }

        @Override
        public String toString() {
            return "\t" + this.getTag() + "\t\t" + dst + ", " + offset + "(" + base + ")\n";
        }

        public void setDst(MCReg dst) {
            replaceDefReg(this.dst, dst);
            this.dst = dst;
        }

        public void setBase(MCReg base) {
            replaceUseReg(this.base, base);
            this.base = base;
        }

        public void setOffset(MCImm offset) {
            this.offset = offset;
        }

        public MCImm getOffset() {
            return offset;
        }

        public MCReg getBase() {
            return base;
        }

        public MCReg getDst() {
            return dst;
        }

        @Override
        public void replaceOperandOfInstr(MCReg oldOp, MCReg newOp) {
            if (dst.equals(oldOp)) {
                setDst(newOp);
            }
            if (base.equals(oldOp)) {
                setBase(newOp);
            }
        }

        @Override
        public void replaceUseOfInstr(MCReg oldOp, MCReg newOp) {
            if (base.equals(oldOp)) {
                setBase(newOp);
            }
        }
    }

    public static class MCSw extends MCInstr {
        private MCReg data;
        private final MCImm offset;
        private MCReg base;

        MCSw(MCInstrTag tag, MCReg data, MCImm offset, MCReg base, MCBasicBlock mcBB) {
            super(tag, mcBB);
            assert tag.isSw();
            this.data = data;
            this.offset = offset;
            this.base = base;
            addUse(data);
            addUse(base);
        }

        @Override
        public String toString() {
            return "\t" + this.getTag() + "\t\t" + data + ", " + offset + "(" + base + ")\n";
        }

        public void setData(MCReg data) {
            replaceUseReg(this.data, data);
            this.data = data;
        }

        public void setBase(MCReg base) {
            replaceUseReg(this.base, base);
            this.base = base;
        }

        public MCReg getData() {
            return data;
        }

        public MCImm getOffset() {
            return offset;
        }

        public MCReg getBase() {
            return base;
        }

        @Override
        public void replaceOperandOfInstr(MCReg oldOp, MCReg newOp) {
            if (data.equals(oldOp)) {
                setData(newOp);
            }
            if (base.equals(oldOp)) {
                setBase(newOp);
            }
        }

        @Override
        public void replaceUseOfInstr(MCReg oldOp, MCReg newOp) {
            if (data.equals(oldOp)) {
                setData(newOp);
            }
            if (base.equals(oldOp)) {
                setBase(newOp);
            }
        }
    }

    public static class MCMf extends MCInstr {
        private MCReg dst;

        MCMf(MCInstrTag tag, MCReg dst, MCBasicBlock mcBB) {
            super(tag, mcBB);
            assert tag.isMf();
            this.dst = dst;
            addDef(dst);
        }

        public MCReg getDst() {
            return dst;
        }

        @Override
        public String toString() {
            return "\t" + this.getTag() + "\t" + dst + "\n";
        }

        public void setDst(MCReg dst) {
            replaceDefReg(this.dst, dst);
            this.dst = dst;
        }

        @Override
        public void replaceOperandOfInstr(MCReg oldOp, MCReg newOp) {
            if (dst.equals(oldOp)) {
                setDst(newOp);
            }
        }

        @Override
        public void replaceUseOfInstr(MCReg oldOp, MCReg newOp) {}
    }

    public static class MCDivMul extends MCInstr {
        private MCReg lhs;
        private MCReg rhs;

        MCDivMul(MCInstrTag tag, MCReg lhs, MCReg rhs, MCBasicBlock mcBB) {
            super(tag, mcBB);
            assert tag.isDivMul();
            this.lhs = lhs;
            this.rhs = rhs;
            addUse(lhs);
            addUse(rhs);
        }

        public void setLhs(MCReg lhs) {
            replaceUseReg(this.lhs, lhs);
            this.lhs = lhs;
        }

        public void setRhs(MCReg rhs) {
            replaceUseReg(this.rhs, rhs);
            this.rhs = rhs;
        }

        public MCReg getLhs() {
            return lhs;
        }

        public MCReg getRhs() {
            return rhs;
        }

        @Override
        public String toString() {
            return "\t" + this.getTag() + "\t" + lhs + ", " + rhs + "\n";
        }

        @Override
        public void replaceOperandOfInstr(MCReg oldOp, MCReg newOp) {
            if (lhs.equals(oldOp)) {
                setLhs(newOp);
            }
            if (rhs.equals(oldOp)) {
                setRhs(newOp);
            }
        }

        @Override
        public void replaceUseOfInstr(MCReg oldOp, MCReg newOp) {
            if (lhs.equals(oldOp)) {
                setLhs(newOp);
            }
            if (rhs.equals(oldOp)) {
                setRhs(newOp);
            }
        }
    }

    public static class MCBranchE extends MCInstr {
        private MCReg lhs;
        private MCReg rhs;
        private MCLabel target;

        MCBranchE(MCInstrTag tag, MCReg lhs, MCReg rhs, MCLabel target, MCBasicBlock mcBB) {
            super(tag, mcBB);
            assert tag.isBranchE();
            this.lhs = lhs;
            this.rhs = rhs;
            this.target = target;
            addUse(lhs);
            addUse(rhs);
        }

        public void setLhs(MCReg lhs) {
            replaceUseReg(this.lhs, lhs);
            this.lhs = lhs;
        }

        public void setRhs(MCReg rhs) {
            replaceUseReg(this.rhs, rhs);
            this.rhs = rhs;
        }

        public void setTarget(MCLabel target) {
            this.target = target;
        }

        public MCReg getLhs() {
            return lhs;
        }

        public MCReg getRhs() {
            return rhs;
        }

        public MCLabel getTarget() {
            return target;
        }

        @Override
        public String toString() {
            return "\t" + this.getTag() + "\t\t" + lhs + ", " + rhs + ", " + target + "\n";
        }

        @Override
        public void replaceOperandOfInstr(MCReg oldOp, MCReg newOp) {
            if (lhs.equals(oldOp)) {
                setLhs(newOp);
            }
            if (rhs.equals(oldOp)) {
                setRhs(newOp);
            }
        }

        @Override
        public void replaceUseOfInstr(MCReg oldOp, MCReg newOp) {
            if (lhs.equals(oldOp)) {
                setLhs(newOp);
            }
            if (rhs.equals(oldOp)) {
                setRhs(newOp);
            }
        }
    }

    public static class MCBranchZ extends MCInstr {
        private MCReg val;
        private MCLabel target;

        MCBranchZ(MCInstrTag tag, MCReg val, MCLabel target, MCBasicBlock mcBB) {
            super(tag, mcBB);
            assert tag.isBranchZ();
            this.val = val;
            this.target = target;
            addUse(val);
        }

        public void setVal(MCReg val) {
            replaceUseReg(this.val, val);
            this.val = val;
        }

        public void setTarget(MCLabel target) {
            this.target = target;
        }

        public MCReg getVal() {
            return val;
        }

        public MCLabel getTarget() {
            return target;
        }

        @Override
        public String toString() {
            return "\t" + this.getTag() + "\t" + val + ", " + target + "\n";
        }

        @Override
        public void replaceOperandOfInstr(MCReg oldOp, MCReg newOp) {
            if (val.equals(oldOp)) {
                setVal(newOp);
            }
        }

        @Override
        public void replaceUseOfInstr(MCReg oldOp, MCReg newOp) {
            if (val.equals(oldOp)) {
                setVal(newOp);
            }
        }
    }

    public static class MCGetarray extends MCInstr {
        private MCReg n;
        private MCReg base;

        MCGetarray(MCReg n, MCReg base, MCBasicBlock mcBB) {
            super(MCInstrTag.getarray, mcBB);
            this.n = n;
            this.base = base;
            addDef(n);
            addUse(base);
        }

        public void setN(MCReg n) {
            replaceDefReg(this.n, n);
            this.n = n;
        }

        public void setBase(MCReg base) {
            replaceUseReg(this.n, n);
            this.base = base;
        }

        public MCReg getN() {
            return n;
        }

        public MCReg getBase() {
            return base;
        }

        @Override
        public String toString() {
            return "\t" + this.getTag() + "\t" + n + ", " + base + "\n";
        }

        @Override
        public void replaceOperandOfInstr(MCReg oldOp, MCReg newOp) {
            if (n.equals(oldOp)) {
                setN(newOp);
            }
            if (base.equals(oldOp)) {
                setBase(newOp);
            }
        }

        @Override
        public void replaceUseOfInstr(MCReg oldOp, MCReg newOp) {
            if (base.equals(oldOp)) {
                setBase(newOp);
            }
        }
    }

    public static class MCPutarray extends MCInstr {
        private MCReg n;
        private MCReg base;

        MCPutarray(MCReg n, MCReg base, MCBasicBlock mcBB) {
            super(MCInstrTag.putarray, mcBB);
            this.n = n;
            this.base = base;
            addUse(n);
            addUse(base);
        }

        public void setN(MCReg n) {
            replaceUseReg(this.n, n);
            this.n = n;
        }

        public void setBase(MCReg base) {
            replaceUseReg(this.base, base);
            this.base = base;
        }

        public MCReg getN() {
            return n;
        }

        public MCReg getBase() {
            return base;
        }

        @Override
        public String toString() {
            return "\t" + this.getTag() + "\t" + n + ", " + base + "\n";
        }

        @Override
        public void replaceOperandOfInstr(MCReg oldOp, MCReg newOp) {
            if (n.equals(oldOp)) {
                setN(newOp);
            }
            if (base.equals(oldOp)) {
                setBase(newOp);
            }
        }

        @Override
        public void replaceUseOfInstr(MCReg oldOp, MCReg newOp) {
            if (n.equals(oldOp)) {
                setN(newOp);
            }
            if (base.equals(oldOp)) {
                setBase(newOp);
            }
        }
    }

    public enum MCInstrTag {
        // 只有 Ret 不是 basic Instruction
        addiu, sll, sra, slti, sltiu, xori, srl, andi, ori,
        addu, subu, slt, sltu, mul, xor, and, or,
        beq, bne,
        bgez, bgtz, blez, bltz,
        div, madd, mult,
        call,
        j, jal,
        jr,
        lw, sw,
        lui,
        mfhi, mflo,
        syscall,
        move, abs,
        getarray, putarray;

        boolean isBinaryI() {
            return this == addiu || this == sll || this == sra || this == slti || this == sltiu || this == xori || this == srl || this == andi || this == ori;
        }

        boolean isBinaryR() {
            return this == addu || this == subu || this == slt || this == sltu || this == mul || this == xor || this == and || this == or;
        }

        boolean isBranchE() {
            return this == beq || this == bne;
        }

        boolean isBranchZ() {
            return this == bgez || this == bgtz || this == blez || this == bltz;
        }

        boolean isDivMul() {
            return this == div || this == madd || this == mult;
        }

        boolean isCall() {
            return this == call;
        }

        boolean isJ() {
            return this == j || this == jal;
        }

        boolean isJr() {
            return this == jr;
        }

        boolean isLw() {
            return this == lw;
        }

        boolean isSw() {
            return this == sw;
        }

        boolean isLui() {
            return this == lui;
        }

        boolean isMf() {
            return this == mfhi || this == mflo;
        }

        boolean isSyscall() {
            return this == syscall;
        }

        boolean isMove() {
            return this == move;
        }

        boolean isAbs() {
            return this == abs;
        }
    }

    public static void buildLuiAtEnd(MCReg dst, MCImm val, MCBasicBlock mcBB) {
        new MCLui(MCInstrTag.lui, dst, val, mcBB);
    }

    public static void buildBinaryIAtEnd(MCInstrTag tag, MCReg dst, MCReg lhs, MCImm rhs, MCBasicBlock mcBB) {
        new MCBinaryI(tag, dst, lhs, rhs, mcBB);
    }

    public static void buildBinaryIAtEntry(MCInstrTag tag, MCReg dst, MCReg lhs, MCImm rhs, MCBasicBlock mcBB) {
        MCBinaryI mcBinaryI = new MCBinaryI(tag, dst, lhs, rhs, null);
        mcBinaryI.node.insertAtEntry(mcBB.getList());
    }

    public static void buildBinaryRAtEnd(MCInstrTag tag, MCReg dst, MCReg lhs, MCReg rhs, MCBasicBlock mcBB) {
        new MCBinaryR(tag, dst, lhs, rhs, mcBB);
    }

    public static void buildMoveAtEnd(MCReg dst, MCReg src, MCBasicBlock mcBB) {
        new MCMove(MCInstrTag.move, dst, src, mcBB);
    }

    public static void buildMoveAfter(MCReg dst, MCReg src, MCInstr prev) {
        MCMove mcMove = new MCMove(MCInstrTag.move, dst, src, null);
        mcMove.node.insertAfter(prev.node);
    }

    public static void buildLwAtEnd(MCReg dst, MCImm offset, MCReg base, MCBasicBlock mcBB) {
        new MCLw(MCInstrTag.lw, dst, offset, base, mcBB);
    }

    public static void buildSwAtEnd(MCReg val, MCImm offset, MCReg base, MCBasicBlock mcBB) {
        new MCSw(MCInstrTag.sw, val, offset, base, mcBB);
    }

    public static void buildSyscallAtEnd(MCBasicBlock mcBB, MCSyscall.SyscallType type) {
        new MCSyscall(MCInstrTag.syscall, type, mcBB);
    }

    public static void buildJAtEnd(MCInstrTag tag, MCLabel target, MCBasicBlock mcBB) {
        new MCJ(tag, target, mcBB);
    }

    public static void buildJBefore(MCInstrTag tag, MCLabel target, MCInstr next) {
        MCJ mcj = new MCJ(tag, target, null);
        mcj.node.insertBefore(next.node);
    }

    public static void buildMfAtEnd(MCInstrTag tag, MCReg dst, MCBasicBlock mcBB) {
        new MCMf(tag, dst, mcBB);
    }

    public static void buildDivMulAtEnd(MCInstrTag tag, MCReg lhs, MCReg rhs, MCBasicBlock mcBB) {
        new MCDivMul(tag, lhs, rhs, mcBB);
    }

    public static void buildJrAtEnd(MCInstrTag tag, MCReg dst, boolean haveReturn, MCBasicBlock mcBB) {
        new MCJr(tag, dst, haveReturn, mcBB);
    }

    public static void buildLwBefore(MCReg dst, MCImm offset, MCReg base, MCInstr next) {
        MCLw lw = new MCLw(MCInstrTag.lw, dst, offset, base, null);
        lw.node.insertBefore(next.node);
    }

    public static void buildSwBefore(MCReg val, MCImm offset, MCReg base, MCInstr next) {
        MCSw sw = new MCSw(MCInstrTag.sw, val, offset, base, null);
        sw.node.insertBefore(next.node);
    }

    public static void buildSwAfter(MCReg val, MCImm offset, MCReg base, MCInstr prev) {
        MCSw sw = new MCSw(MCInstrTag.sw, val, offset, base, null);
        sw.node.insertAfter(prev.node);
    }

    public static void buildLuiBefore(MCReg dst, MCImm imm, MCInstr next) {
        MCLui lui = new MCLui(MCInstrTag.lui, dst, imm, null);
        lui.node.insertBefore(next.node);
    }

    public static void buildBinaryRBefore(MCInstrTag tag, MCReg dst, MCReg lhs, MCReg rhs, MCInstr next) {
        MCBinaryR binaryR = new MCBinaryR(tag, dst, lhs, rhs, null);
        binaryR.node.insertBefore(next.node);
    }

    public static void buildBinaryIBefore(MCInstrTag tag, MCReg dst, MCReg lhs, MCImm rhs, MCInstr next) {
        MCBinaryI binaryI = new MCBinaryI(tag, dst, lhs, rhs, null);
        binaryI.node.insertBefore(next.node);
    }

    public static void buildCallAtEnd(MCFunction mcf, int argsCnt, MCBasicBlock mcBB) {
        new MCCall(MCInstrTag.call, mcf, argsCnt, mcBB);
    }

    public static void buildBranchEAtEnd(MCInstrTag tag, MCReg val1, MCReg val2, MCLabel target, MCBasicBlock mcBB) {
        new MCBranchE(tag, val1, val2, target, mcBB);
    }

    public static void buildBranchZAtEnd(MCInstrTag tag, MCReg val, MCLabel target, MCBasicBlock mcBB) {
        new MCBranchZ(tag, val, target, mcBB);
    }

    public static void buildGetarrayAtEnd(MCReg n, MCReg base, MCBasicBlock mcBB) {
        new MCGetarray(n, base, mcBB);
    }

    public static void buildPutarrayAtEnd(MCReg n, MCReg base, MCBasicBlock mcBB) {
        new MCPutarray(n, base, mcBB);
    }

    public static void buildLuiAtEntry(MCReg dst, MCImm val, MCBasicBlock mcBB) {
        MCLui lui = new MCLui(MCInstrTag.lui, dst, val, null);
        lui.node.insertAtEntry(mcBB.getList());
    }

    public static void buildAbsAtEnd(MCReg dst, MCReg src, MCBasicBlock mcBB) {
        new MCAbs(MCInstrTag.abs, dst, src, mcBB);
    }
}
