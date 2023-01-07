package midend.ir;

import midend.ir.Constant.ConstantInt;
import midend.ir.Type.*;
import utils.IList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

public abstract class Instruction extends Value{
    private final ArrayList<Value> operands;
    private int operandNum;
    private final ArrayList<Use> uses = new ArrayList<>();
    public InstrTag tag;
    public boolean needName = true;
    public final IList.INode<Instruction, BasicBlock> node;

    public Instruction(InstrTag tag, Type type, int operandNum, BasicBlock parent) {
        super(type, "");
        this.tag = tag;
        this.operandNum = operandNum;
        this.operands = new ArrayList<>(Collections.nCopies(operandNum, null));
        for (int i = 0; i < operandNum; ++i) {
            this.uses.add(new Use(this, i));
        }
        this.node = new IList.INode<>(this);
        if (parent != null) {
            this.node.insertAtEnd(parent.getList());
        }
    }

    public InstrTag getTag() {
        return tag;
    }

    public Value getOperand(int index) {
        if (index < 0 || index >= this.operandNum) {
            throw new RuntimeException("getOperand index out of bounds");
        }
        return this.operands.get(index);
    }

    public boolean containOperand(Value operand) {
        return this.operands.contains(operand);
    }

    public void setOperand(int index, Value operand) {
        Value originOperand = this.getOperand(index);
        this.operands.set(index, operand);
        Use use = this.uses.get(index);
        if (originOperand != null) {
            originOperand.removeUse(use);
        }
        if (operand != null) {
            operand.addUse(use);
        }
    }

    public void addOperand(Value operand) {
        int index = this.operandNum++;
        this.operands.add(operand);
        Use use = new Use(this, index);
        this.uses.add(use);
        if (operand != null) {
            operand.addUse(use);
        }
    }

    public void removeAllOperands() {
        for (int i = 0; i < this.operandNum; ++i) {
            this.setOperand(i, null);
        }
        this.operands.clear();
        this.uses.clear();
        this.operandNum = 0;
    }

    public void removeOperand(int index) {
        ArrayList<Value> tmp = new ArrayList<>(this.operands);
        removeAllOperands();
        for (int i = 0; i < tmp.size(); ++i) {
            if (i != index) {
                this.addOperand(tmp.get(i));
            }
        }
    }

    public void removeSelf() {
        this.removeAllOperands();
        this.replaceSelfWith(null);
        this.node.removeSelf();
    }

    public void removeSelf(Value replace) {
        this.removeAllOperands();
        this.replaceSelfWith(replace);
        this.node.removeSelf();
    }

    public int getOperandNum() {
        return operandNum;
    }

    public BasicBlock getParent() {
        return this.node.getParent().getHolder();
    }

    public enum InstrTag {
        Add, Sub, Srem, Mul, MulH, Sdiv, Shl, Lshr, Ashr, And, Or, Xor,
        Eq, Ne, Sgt, Sge, Slt, Sle,
        Br, Ret, // Terminator
        Call, Alloca, Load, Store, GetElementPtr, Zext, Phi, Move, Abs;

        public Type getResultType() {
            if (this.isIntegerArithmetic()) {
                return IntegerType.getI32();
            } else if (this.isIcmp()) {
                return IntegerType.getI1();
            } else {
                throw new RuntimeException("wrong binary type");
            }
        }

        public Type getOperandType() {
            if (this.isIntegerArithmetic() || this.isIcmp()) {
                return IntegerType.getI32();
            } else {
                throw new RuntimeException("wrong binary type");
            }
        }

        @Override
        public String toString() {
            String operation;
            if (this.isIntegerArithmetic()) {
                operation = this.name().toLowerCase(Locale.ROOT);
            } else if (this.isIcmp()) {
                operation = "icmp " + this.name().toLowerCase(Locale.ROOT);
            } else {
                throw new RuntimeException("Unsupported InstrTag toString");
            }
            return operation + " " + this.getOperandType();
        }

        public boolean isIntegerArithmetic() {
            return this.ordinal() <= Xor.ordinal();
        }

        public boolean isIcmp() {
            return Eq.ordinal() <= this.ordinal() && this.ordinal() <= Sle.ordinal();
        }

        public boolean isTerminator() {
            return this == Br || this == Ret;
        }

        public boolean isBinary() {
            return isIntegerArithmetic() || isIcmp();
        }

        boolean isCommutative() {
            switch (this) {
                case Add: case Mul: case MulH: case Eq: case Ne: case And: case Or: case Xor: {
                    return true;
                }
                case Sub: case Srem: case Sdiv: case Shl: case Lshr: case Ashr: case Sgt: case Sge: case Slt: case Sle: {
                    return false;
                }
                default: {
                    throw new RuntimeException("check commutative not Binary : " + this);
                }
            }
        }

        static boolean isInverse(InstrTag tag1, InstrTag tag2) {
            if (!tag1.isBinary() || !tag2.isBinary()) {
                return false;
            }
            return tag1 == tag2 && tag1.isCommutative() ||
                    (tag1 == Sgt && tag2 == Slt) ||
                    (tag1 == Sge && tag2 == Sle) ||
                    (tag1 == Slt && tag2 == Sgt) ||
                    (tag1 == Sle && tag2 == Sge);
        }
    }

    abstract public String getLLVM();

    public boolean essentiallyEquals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Instruction that = (Instruction) o;
        if (this.tag != that.tag || this.getOperandNum() != that.getOperandNum()) {
            return false;
        }
        for (int i = 0; i < this.getOperandNum(); ++i) {
            if (this.getOperand(i) instanceof Constant) {
                if (!this.getOperand(i).equals(that.getOperand(i))) {
                    return false;
                }
            } else {
                if (this.getOperand(i) != that.getOperand(i)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static class BinaryInst extends Instruction {
        public BinaryInst(InstrTag tag, Value V1, Value V2, BasicBlock parent) {
            super(tag, tag.getResultType(), 2, parent);
            this.setOperand(0, V1);
            this.setOperand(1, V2);
        }

        public BinaryInst(InstrTag tag, Value V1, Value V2, Instruction prev) {
            super(tag, tag.getResultType(), 2, null);
            this.setOperand(0, V1);
            this.setOperand(1, V2);
            this.node.insertAfter(prev.node);
        }

        @Override
        public String getLLVM() {
            return "\t" + this.getName() + " = " + this.tag.toString() + " " + lhs().getName() + " , " + rhs().getName();
        }

        public Value lhs() {
            return this.getOperand(0);
        }

        public Value rhs() {
            return this.getOperand(1);
        }

        public static Constant calcBinary(InstrTag tag, Constant lhs, Constant rhs) {
            if (lhs.getType() != rhs.getType()) {
                throw new RuntimeException("different Type in binary");
            }
            Type operandType = lhs.getType();
            if (operandType.isI1Type()) {
                throw new RuntimeException("binary calc I1?");
            }
            if (tag.isIntegerArithmetic()) {
                return new ConstantInt(calcBinaryInt(tag, ((ConstantInt) lhs).getValue(), ((ConstantInt) rhs).getValue()));
            }
            if (tag.isIcmp()) {
                return new ConstantInt(calcIcmp(tag, ((ConstantInt) lhs).getValue(), ((ConstantInt) rhs).getValue()));
            }
            throw new RuntimeException("not binary to calc");
        }

        public static int calcBinaryInt(InstrTag tag, int lhs, int rhs) {
            switch (tag) {
                case Add: return lhs + rhs;
                case Sub: return lhs - rhs;
                case Mul: return lhs * rhs;
                case MulH: return (int) (((long) lhs * (long) rhs) >> 32);
                case Sdiv: return lhs / rhs;
                case Srem: return lhs % rhs;
                case Shl: return lhs << rhs;
                case Lshr: return lhs >>> rhs;
                case Ashr: return lhs >> rhs;
                case Eq: return lhs == rhs ? 1 : 0;
                case Ne: return lhs != rhs ? 1 : 0;
                case Sgt: return lhs > rhs ? 1 : 0;
                case Sge: return lhs >= rhs ? 1 : 0;
                case Slt: return lhs < rhs ? 1 : 0;
                case Sle: return lhs <= rhs ? 1 : 0;
                case And: return lhs & rhs;
                case Or: return lhs | rhs;
                case Xor: return lhs ^ rhs;
                default: throw new RuntimeException("Not Binary Int Inst " + tag);
            }
        }

        public static boolean calcIcmp(InstrTag tag, int i1, int i2) {
            switch (tag) {
                case Eq: return i1 == i2;
                case Ne: return i1 != i2;
                case Sle: return i1 <= i2;
                case Slt: return i1 < i2;
                case Sge: return i1 >= i2;
                case Sgt: return i1 > i2;
                default: throw new RuntimeException("Not Icmp");
            }
        }

        public static boolean checkSame(BinaryInst b1, BinaryInst b2) {
            return (b1.tag == b2.tag && b1.lhs().equals(b2.lhs()) && b1.rhs().equals(b2.rhs())) ||
                    (b1.tag == b2.tag && b1.tag.isCommutative() && b1.lhs().equals(b2.rhs()) && b1.rhs().equals(b2.lhs())) ||
                    (InstrTag.isInverse(b1.tag, b2.tag) && b1.lhs().equals(b2.rhs()) && b1.rhs().equals(b2.lhs()));
        }
    }

    public static class Zext extends Instruction {
        private final Type dstType;

        public Zext(Value v, Type dstType, BasicBlock parent) {
            super(InstrTag.Zext, dstType, 1, parent);
            this.dstType = dstType;
            this.setOperand(0, v);
        }

        public Type getDstType() {
            return dstType;
        }

        @Override
        public String getLLVM() {
            Value op = getOperand(0);
            return "\t" + this.getName() + " = zext " + op.getType() + " " +
                    op.getName() +
                    " to " + dstType;
        }
    }

    public static class Move extends Instruction {
        private Value dst;
        private final Value src;

        public Move(Value dst, Value src, BasicBlock parent) {
            super(InstrTag.Move, src.getType(), 1, parent);
            this.setOperand(0, src);
            this.src = src;
            this.dst = dst;
            needName = false;
        }

        public Move(Value src, BasicBlock parent) {
            super(InstrTag.Move, src.getType(), 1, parent);
            this.setOperand(0, src);
            this.src = src;
            this.dst = null;
            needName = true;
        }

        public Value getSrc() {
            return src;
        }

        public Value getDst() {
            return dst;
        }

        public void setDst(Value dst) {
            this.dst = dst;
        }

        @Override
        public String getLLVM() {
            if (dst == null) {
                return "\t" + this.getName() + " = " + src.getType() + " " + src.getName();
            } else {
                return "\t" + dst.getName() + " = " + src.getType() + " " + src.getName();
            }
        }
    }

    public static class Call extends Instruction {
        public Call(Function func, ArrayList<Value> args, BasicBlock parent) {
            super(InstrTag.Call, ((FunctionType) func.getType()).getReturnType(), args.size() + 1, parent);
            if (this.getType().isVoidType()) {
                needName = false;
            }
            setOperand(0, func);
            for (int i = 0; i < args.size(); ++i) {
                setOperand(i + 1, args.get(i));
            }
        }

        public Function getFunc() {
            return (Function) this.getOperand(0);
        }

        public Value getArg(int index) {
            return this.getOperand(index + 1);
        }

        @Override
        public String getLLVM() {
            StringBuilder sb = new StringBuilder();
            sb.append("\t");
            if (((FunctionType) this.getOperand(0).getType()).getReturnType().isVoidType())
                sb.append("call ");
            else
                sb.append(this.getName()).append(" = call ");
            sb.append(this.getType()).append(" @").append(this.getOperand(0).getName()).append("(");
            for (int i = 1; i < this.getOperandNum(); i++) {
                sb.append(this.getOperand(i).getType()).append(" ").append(this.getOperand(i).getName());
                if (i != this.getOperandNum() - 1)
                    sb.append(",");
            }
            sb.append(")");
            return sb.toString();
        }
    }

    public static class Br extends Instruction {
        private final boolean isJump;

        public Br(Value condition, BasicBlock trueBB, BasicBlock falseBB, BasicBlock parent) {
            super(InstrTag.Br, LabelType.getType(), 3, parent);
            this.setOperand(0, condition);
            this.setOperand(1, trueBB);
            this.setOperand(2, falseBB);
            this.isJump = false;
            needName = false;
        }

        public Br(BasicBlock nxtBB, BasicBlock parent) {
            super(InstrTag.Br, LabelType.getType(), 1, parent);
            this.setOperand(0, nxtBB);
            this.isJump = true;
            needName = false;
        }

        public Value condition() {
            return this.getOperand(0);
        }

        public BasicBlock trueBB() {
            return (BasicBlock) this.getOperand(1);
        }

        public BasicBlock falseBB() {
            return (BasicBlock) this.getOperand(2);
        }

        public BasicBlock nxtBB() {
            return (BasicBlock) this.getOperand(0);
        }

        public void replaceBB(BasicBlock oldBB, BasicBlock newBB) {
            if (isJump) {
                if (this.getOperand(0).equals(oldBB)) {
                    this.setOperand(0, newBB);
                }
            } else {
                if (this.getOperand(1).equals(oldBB)) {
                    this.setOperand(1, newBB);
                }
                if (this.getOperand(2).equals(oldBB)) {
                    this.setOperand(2, newBB);
                }
            }
        }

        @Override
        public String getLLVM() {
            if (this.getOperandNum() == 1) {
                return "\tbr " + nxtBB().getLLVM();
            } else {
                return "\tbr " + condition() + ", " + trueBB().getLLVM() + ", " + falseBB().getLLVM();
            }
        }
    }

    public static class Ret extends Instruction {
        public Ret(Value value, BasicBlock parent) {
            super(InstrTag.Ret, VoidType.getType(), 1, parent);
            this.setOperand(0, value);
            needName = false;
        }

        public Value getReturn() {
            return this.getOperand(0);
        }

        @Override
        public String getLLVM() {
            if (this.getOperand(0) != null) {
                return "\tret " + getReturn();
            } else {
                return "\tret void";
            }
        }
    }

    public static class Alloca extends Instruction {
        public Alloca(Type allocated, BasicBlock parent) {
            super(InstrTag.Alloca, new PointerType(allocated), 0, parent);
        }

        public Type getAllocated() {
            return ((PointerType) this.getType()).getPointTo();
        }

        @Override
        public String getLLVM() {
            return "\t" + this.getName() + " = alloca " + this.getAllocated();
        }
    }

    public static class Load extends Instruction {
        public Load(Value pointer, BasicBlock parent) {
            super(InstrTag.Load, ((PointerType) pointer.getType()).getPointTo(), 1, parent);
            this.setOperand(0, pointer);
        }

        public Value getPointer() {
            return this.getOperand(0);
        }

        @Override
        public String getLLVM() {
            return "\t" + this.getName() + " = load " + this.getType() + ", " + getPointer().getType() + " " + getPointer().getName();
        }
    }

    public static class Store extends Instruction {
        public Store(Value value, Value pointer, BasicBlock parent) {
            super(InstrTag.Store, VoidType.getType(), 2, parent);
            this.setOperand(0, value);
            this.setOperand(1, pointer);
            needName = false;
        }

        public Value getValue() {
            return this.getOperand(0);
        }

        public Value getPointer() {
            return this.getOperand(1);
        }

        @Override
        public String getLLVM() {
            return "\tstore " + getValue() + ", " + getPointer().getType() + " " + getPointer().getName();
        }
    }

    public static class GEP extends Instruction {
        private static Type getElementContainedType(Value ptr, int len) {
            assert ptr.getType() instanceof PointerType;
            Type type = ((PointerType) ptr.getType()).getPointTo();
            if (type.isArrayType()) {
                for (int i = 1; i < len; ++i) {
                    type = ((ArrayType) type).getChildType();
                }
            }
            return type;
        }

        public GEP(Value ptr, ArrayList<Value> indices, BasicBlock parent) {
            super(InstrTag.GetElementPtr, new PointerType(getElementContainedType(ptr, indices.size())), indices.size() + 1, parent);
            setOperand(0, ptr);
            for (int i = 0; i < indices.size(); ++i) {
                setOperand(i + 1, indices.get(i));
            }
        }

        public Value getPointer() {
            return this.getOperand(0);
        }

        @Override
        public String getLLVM() {
            StringBuilder sb = new StringBuilder();
            sb.append("\t").append(this.getName())
                    .append(" = getelementptr ")
                    .append(((PointerType) getOperand(0).getType()).getPointTo())
                    .append(", ")
                    .append(getPointer().getType())
                    .append(" ")
                    .append(getPointer().getName());
            for (int i = 1; i < getOperandNum(); ++i) {
                sb.append(", ").append(getOperand(i));
            }
            return sb.toString();
        }

        public static boolean checkSame(GEP i1, GEP i2) {
            if (i1.getOperandNum() != i2.getOperandNum()) {
                return false;
            }
            for (int i = 0; i < i1.getOperandNum(); ++i) {
                if (!i1.getOperand(i).equals(i2.getOperand(i))) {
                    return false;
                }
            }
            return true;
        }
    }

    public static class Phi extends Instruction {
        public Phi(Type type, int inCnt, BasicBlock parent) {
            super(InstrTag.Phi, type, inCnt, parent);
        }

        @Override
        public String getLLVM() {
            StringBuilder sb = new StringBuilder();
            sb.append("\t").append(this.getName()).append(" = phi ").append(this.getType());
            BasicBlock parent = this.node.getParent().getHolder().node.getValue();
            for (int i = 0; i < this.getOperandNum(); ++i) {
                sb.append(" [ ").append(this.getOperand(i).getName()).append(", %").append(parent.getPredecessors().get(i).getName()).append(" ]");
                if (i != this.getOperandNum() - 1) {
                    sb.append(",");
                }
            }
            return sb.toString();
        }
    }

    public static class Abs extends Instruction {
        private final Value src;

        public Abs(Value src, BasicBlock parent) {
            super(InstrTag.Abs, src.getType(), 1, parent);
            this.setOperand(0, src);
            this.src = src;
            needName = true;
        }

        public Value getSrc() {
            return src;
        }

        @Override
        public String getLLVM() {
            return "\t" + this.getName() + " = abs " + src.getType() + " " + src.getName();
        }
    }
}
