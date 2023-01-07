package midend.pass;

import midend.analysis.ArrayAnalysis;
import midend.ir.*;
import midend.ir.Constant.*;
import midend.ir.Type.*;
import midend.ir.Instruction.*;
import utils.IList;
import utils.Pair;

import java.util.*;

public class GVN {
    /*
    1、删去只有br的块
    2、对一些确定的binary操作优化
    3、对相同值合并
     */
    private Module m;

    public void run(Module module) {
        this.m = module;
        runGVN();
    }

    private void runGVN() {
        for (IList.INode<Function, Module> fNode : m.functionList) {
            if (!fNode.getValue().isBuiltin()) {
                runGVNonFunction(fNode.getValue());
                //unreachableBasicBlockRemove(fNode.getValue());
            }
        }
        ArrayList<GlobalVariable> removedGVs = new ArrayList<>();
        for (GlobalVariable GV : m.globalList) {
            boolean hasUse = false;
            for (Use use : GV.getUses()) {
                if (use.getUser() instanceof Load || use.getUser() instanceof Instruction.GEP || use.getUser() instanceof Instruction.Call) {
                    hasUse = true;
                    break;
                }
            }
            if (!hasUse) {
                removedGVs.add(GV);
            }
        }
        for (GlobalVariable GV : removedGVs) {
            ArrayList<Use> uses = new ArrayList<>(GV.getUses());
            for (Use use : uses) {
                use.getUser().removeSelf();
            }
            m.globalList.remove(GV);
        }
        for (IList.INode<Function, Module> fNode : m.functionList) {
            if (!fNode.getValue().isBuiltin() && fNode.getValue().getUses().isEmpty() && !fNode.getValue().getName().equals("main")) {
                fNode.getValue().node.removeSelf();
            }
        }
    }

    public static void removeBrFlow(BasicBlock fr, BasicBlock to) {
        int pos = to.getPredecessors().indexOf(fr);
        for (IList.INode<Instruction, BasicBlock> iNode : to.getList()) {
            if (iNode.getValue() instanceof Instruction.Phi) {
                iNode.getValue().removeOperand(pos);
            }
        }
        to.getPredecessors().remove(pos);
        fr.getSuccessors().remove(to);
    }

    private void runGVNonFunction(Function f) {
        valueTable.clear();
        for (IList.INode<BasicBlock, Function> bNode : f.getList()) {
            runGVNonBasicBlock(bNode.getValue());
        }
    }

    private void runGVNonBasicBlock(BasicBlock bb) {
        for (IList.INode<Instruction, BasicBlock> iNode : bb.getList()) {
            runGVNonInstruction(iNode.getValue());
        }
    }

    private final ArrayList<Pair<Value, Value>> valueTable = new ArrayList<>();

    private void replaceInstruction(Instruction origin, Value value) {
        // 这里只换了Use，如果需要insert到List里要单独加
        if (origin == value) {
            return;
        }// System.out.println("replace : " + origin + " - " + value);
        if (origin.tag == Instruction.InstrTag.Br) {
            assert origin.getOperandNum() == 3;
            assert value instanceof Br;
            assert ((Br) value).getOperandNum() == 1;
            BasicBlock nxt = (BasicBlock) ((Br) value).getOperand(0);
            if (nxt.equals(origin.getOperand(1))) {
                removeBrFlow(origin.getParent(), (BasicBlock) origin.getOperand(2));
            } else {
                removeBrFlow(origin.getParent(), (BasicBlock) origin.getOperand(1));
            }
        }
        valueTable.removeIf(v -> v.getFir() == origin);
        origin.removeSelf(value);
    }

    private Value findBinaryInstr(BinaryInst binaryInst) {
        for (Pair<Value, Value> iter : valueTable) {
            Value key = iter.getFir();
            Value value = iter.getSec();
            if (key.equals(binaryInst)) {
                return value;
            }
            if (key instanceof BinaryInst) {
                if (BinaryInst.checkSame(binaryInst, (BinaryInst) key)) {
                    return value;
                }
            }
        }
        valueTable.add(new Pair<>(binaryInst, binaryInst));
        return binaryInst;
    }

    private Value findGEPInstr(Instruction.GEP gep) {
        for (Pair<Value, Value> iter : valueTable) {
            Value key = iter.getFir();
            Value value = iter.getSec();
            if (key.equals(gep)) {
                return value;
            }
            if (key instanceof Instruction.GEP) {
                if (Instruction.GEP.checkSame(gep, (Instruction.GEP) key)) {
                    return value;
                }
            }
        }
        valueTable.add(new Pair<>(gep, gep));
        return gep;
    }

    private Value findSimplifiedInstruction(Instruction instruction) {
        for (Pair<Value, Value> pair : valueTable) {
            if (pair.getFir() == instruction) {
                return pair.getSec();
            }
        }
        Value res;
        if (instruction.tag.isBinary()) {
            res = findBinaryInstr((BinaryInst) instruction);
        } else if (instruction.tag == Instruction.InstrTag.GetElementPtr) {
            res = findGEPInstr((Instruction.GEP) instruction);
        } else {
            res = instruction;
        }
        return res;
    }

    private void runGVNonInstruction(Instruction instr) {
        Value v = simplifyInstruction(instr);
        if (!(v instanceof Instruction)) {
            if (!(v instanceof Constant || v instanceof Function.Param)) {
                throw new RuntimeException(v.toString());
            }
            replaceInstruction(instr, v);
            return;
        }
        Instruction newInstr = (Instruction) v;
        Value value = findSimplifiedInstruction(newInstr);
        replaceInstruction(instr, value);
    }

    public static Value simplifyInstruction(Instruction instruction) {
        if (instruction.tag.isBinary()) {
            Value lhs = instruction.getOperand(0);
            Value rhs = instruction.getOperand(1);
            if (lhs instanceof Constant && rhs instanceof Constant) {
                return BinaryInst.calcBinary(instruction.getTag(), (Constant) lhs, (Constant) rhs);
            }
        }
        switch (instruction.tag) {
            case Add: {
                return simplifyAdd(instruction);
            }
            case Abs: {
                return simplifyAbs((Abs) instruction);
            }
            case Sub: {
                return simplifySub(instruction);
            }
            case Srem: {
                return simplifyMod(instruction);
            }
            case Mul: {
                return simplifyMul(instruction);
            }
            case Sdiv: {
                return simplifyDiv(instruction);
            }
            case Eq: case Sle: case Sge: {
                return simplifyEqIsTrue(instruction);
            }
            case Ne: case Slt: case Sgt: {
                return simplifyEqIsFalse(instruction);
            }
            case Shl: {
                return simplifySh(instruction);
            }
            case Br: {
                return simplifyBr(instruction);
            }
            case Call: {
                return simplifyCall((Call) instruction);
            }
            case Alloca: case Store: case Phi: case Ret: case GetElementPtr: case Zext: case Move: {
                return instruction;
            }
            case Load: {
                return simplifyLoad((Load) instruction);
            }
            case And: {
                return simplifyAnd( instruction);
            }
            case Or: {
                return simplifyOr(instruction);
            }
            case Xor: {
                return simplifyXor(instruction);
            }
            default: {
                throw new RuntimeException("Unknown instruction tag: " + instruction.tag);
            }
        }
    }

    public static Value simplifyBr(Instruction instr) {
        Br br = (Br) instr;
        if (br.getOperandNum() == 3 && br.getOperand(0) instanceof ConstantInt) {
            if (((ConstantInt) br.getOperand(0)).getValue() == 1) {
                Br newBr = new Br((BasicBlock) br.getOperand(1), null);
                newBr.node.insertAfter(br.node);
                return newBr;
            } else {
                Br newBr = new Br((BasicBlock) br.getOperand(2), null);
                newBr.node.insertAfter(br.node);
                return newBr;
            }
        }
        return instr;
    }

    public static Value simplifyAdd(Instruction instr) {
        Value lhs = instr.getOperand(0);
        Value rhs = instr.getOperand(1);
        if (lhs instanceof ConstantInt && ((ConstantInt) lhs).getValue() == 0) {
            return rhs;
        }
        if (rhs instanceof ConstantInt && ((ConstantInt) rhs).getValue() == 0) {
            return lhs;
        }
        if (rhs instanceof ConstantInt &&
                lhs instanceof BinaryInst &&
                ((BinaryInst) lhs).rhs() instanceof ConstantInt &&
                ((BinaryInst) lhs).tag == InstrTag.Add) {
            Value newLhs = ((BinaryInst) lhs).lhs();
            Value newRhs = new ConstantInt(IntegerType.getI32(), ((ConstantInt) rhs).getValue() + ((ConstantInt) ((BinaryInst) lhs).rhs()).getValue());
            instr.setOperand(0, newLhs);
            instr.setOperand(1, newRhs);
            return instr;
        }
        if (lhs instanceof Instruction &&
                ((Instruction) lhs).getTag() == InstrTag.Sub &&
                ((Instruction) lhs).getOperand(1).equals(rhs)) {
            return ((Instruction) lhs).getOperand(0);
        }
        if (rhs instanceof Instruction &&
                ((Instruction) rhs).getTag() == InstrTag.Sub &&
                ((Instruction) rhs).getOperand(1).equals(lhs)) {
            return ((Instruction) rhs).getOperand(0);
        }
        return instr;
    }

    public static Value simplifySub(Instruction instr) {
        Value lhs = instr.getOperand(0);
        Value rhs = instr.getOperand(1);
        if (lhs.equals(rhs)) {
            return new ConstantInt(IntegerType.getI32(), 0);
        }
        if (rhs instanceof ConstantInt && ((ConstantInt) rhs).getValue() == 0) {
            return lhs;
        }
        if (lhs instanceof Instruction &&
                ((Instruction) lhs).getTag() == InstrTag.Add &&
                ((Instruction) lhs).getOperand(1).equals(rhs)) {
            return ((Instruction) lhs).getOperand(0);
        }
        if (lhs instanceof Instruction &&
                ((Instruction) lhs).getTag() == InstrTag.Add &&
                ((Instruction) lhs).getOperand(0).equals(rhs)) {
            return ((Instruction) lhs).getOperand(1);
        }
        if (rhs instanceof Instruction &&
                ((Instruction) rhs).getTag() == InstrTag.Sub &&
                ((Instruction) rhs).getOperand(0).equals(lhs)) {
            return ((Instruction) rhs).getOperand(1);
        }
        return instr;
    }

    public static Value simplifyMod(Instruction instr) {
        Value lhs = instr.getOperand(0);
        Value rhs = instr.getOperand(1);
        if (lhs.equals(rhs)) {
            return ConstantInt.getConst0();
        }
        if (lhs instanceof ConstantInt && ((ConstantInt) lhs).getValue() == 0) {
            return ConstantInt.getConst0();
        }
        if (rhs instanceof ConstantInt && ((ConstantInt) rhs).getValue() == 1) {
            return ConstantInt.getConst0();
        }
        if (rhs instanceof ConstantInt && ((ConstantInt) rhs).getValue() == -1) {
            return ConstantInt.getConst0();
        }
        return instr;
    }

    public static Value simplifyMul(Instruction instr) {
        Value lhs = instr.getOperand(0);
        Value rhs = instr.getOperand(1);
        if (lhs instanceof ConstantInt && ((ConstantInt) lhs).getValue() == 0) {
            return ConstantInt.getConst0();
        }
        if (rhs instanceof ConstantInt && ((ConstantInt) rhs).getValue() == 0) {
            return ConstantInt.getConst0();
        }
        if (lhs instanceof ConstantInt && ((ConstantInt) lhs).getValue() == 1) {
            return rhs;
        }
        if (rhs instanceof ConstantInt && ((ConstantInt) rhs).getValue() == 1) {
            return lhs;
        }
        return instr;
    }

    public static Value simplifyDiv(Instruction instr) {
        Value lhs = instr.getOperand(0);
        Value rhs = instr.getOperand(1);
        if (lhs.equals(rhs)) {
            return new ConstantInt(IntegerType.getI32(), 1);
        }
        if (rhs instanceof ConstantInt && ((ConstantInt) rhs).getValue() == 1) {
            return lhs;
        }
        return instr;
    }

    public static Value simplifyEqIsTrue(Instruction instr) {
        Value lhs = instr.getOperand(0);
        Value rhs = instr.getOperand(1);
        if (lhs.equals(rhs)) {
            return new ConstantInt(IntegerType.getI1(), 1);
        }
        return instr;
    }

    public static Value simplifyEqIsFalse(Instruction instr) {
        Value lhs = instr.getOperand(0);
        Value rhs = instr.getOperand(1);
        if (lhs.equals(rhs)) {
            return new ConstantInt(IntegerType.getI1(), 0);
        }
        return instr;
    }

    public static Value simplifySh(Instruction instr) {
        Value lhs = instr.getOperand(0);
        Value rhs = instr.getOperand(1);
        if (rhs instanceof ConstantInt && ((ConstantInt) rhs).getValue() == 0) {
            return lhs;
        }
        return instr;
    }

    public static Value simplifyLoad(Load load) {
        Value pointer = ArrayAnalysis.getArray(load.getPointer());
        if (pointer instanceof GlobalVariable) {
            GlobalVariable gv = (GlobalVariable) pointer;
            if (gv.isConstant() && gv.getInit() == null) {
                return new ConstantInt(0);
            }
        }
        if (load.getPointer() instanceof GlobalVariable) {
            if (((GlobalVariable) load.getPointer()).isConstant()) {
                GlobalVariable GV = ((GlobalVariable) load.getPointer());
                Type GVStoredType = ((PointerType) GV.getType()).getPointTo();
                if (GVStoredType.isI32Type()) {
                    return GV.getInit();
                }
            }
        }
        return load;
    }

    public static Value simplifyCall(Call call) {
        if (!call.getFunc().isPure() || !(call.getOperand(1) instanceof ConstantInt)) {
            return call;
        }
        int res = new Simulate().simulate(call.getFunc(), ((ConstantInt) call.getOperand(1)).getValue());
        return new ConstantInt(IntegerType.getI32(), res);
    }

    public static Value simplifyAbs(Abs abs) {
        Value operand = abs.getOperand(0);
        if (operand instanceof ConstantInt) {
            int value = ((ConstantInt) operand).getValue();
            if (value < 0) {
                return new ConstantInt(IntegerType.getI32(), -value);
            }
            return operand;
        }
        return abs;
    }

    public static Value simplifyAnd(Instruction instr) {
        Value lhs = instr.getOperand(0);
        Value rhs = instr.getOperand(1);
        if (lhs instanceof ConstantInt && ((ConstantInt) lhs).getValue() == 0) {
            return new ConstantInt(0);
        }
        if (rhs instanceof ConstantInt && ((ConstantInt) rhs).getValue() == 0) {
            return new ConstantInt(0);
        }
        if (lhs instanceof ConstantInt && ((ConstantInt) lhs).getValue() == -1) {
            return rhs;
        }
        if (rhs instanceof ConstantInt && ((ConstantInt) rhs).getValue() == -1) {
            return lhs;
        }
        return instr;
    }

    public static Value simplifyOr(Instruction instr) {
        Value lhs = instr.getOperand(0);
        Value rhs = instr.getOperand(1);
        if (lhs instanceof ConstantInt && ((ConstantInt) lhs).getValue() == 0) {
            return rhs;
        }
        if (rhs instanceof ConstantInt && ((ConstantInt) rhs).getValue() == 0) {
            return lhs;
        }
        if (lhs instanceof ConstantInt && ((ConstantInt) lhs).getValue() == -1) {
            return new ConstantInt(-1);
        }
        if (rhs instanceof ConstantInt && ((ConstantInt) rhs).getValue() == -1) {
            return new ConstantInt(-1);
        }
        return instr;
    }

    public static Value simplifyXor(Instruction instr) {
        Value lhs = instr.getOperand(0);
        Value rhs = instr.getOperand(1);
        if (lhs instanceof ConstantInt && ((ConstantInt) lhs).getValue() == 0) {
            return rhs;
        }
        if (rhs instanceof ConstantInt && ((ConstantInt) rhs).getValue() == 0) {
            return lhs;
        }
        return instr;
    }
}
