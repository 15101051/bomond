package midend.pass;

import midend.ir.*;
import midend.ir.Constant.*;
import midend.ir.Instruction.*;
import midend.ir.Type.*;
import utils.IList;

public class InstructionCombination {
    public void run(Module m) {
        for (IList.INode<Function, Module> fNode : m.functionList) {
            Function func = fNode.getValue();
            if (!func.isBuiltin()) {
                run(func);
            }
        }
    }

    ConstantInt cst;
    Value val;

    public void run(Function func) {
        for (IList.INode<BasicBlock, Function> bNode : func.getList()) {
            BasicBlock bb = bNode.getValue();
            for (IList.INode<Instruction, BasicBlock> iNode : bb.getList()) {
                Instruction instr = iNode.getValue();
                if (instr.getTag() != InstrTag.Add) {
                    continue;
                }
                if (instr.getOperand(0) == instr.getOperand(1)) {
                    Value lhs = instr.getOperand(0);
                    if (isConstMulValue(lhs)) {
                        instr.setOperand(0, val);
                        instr.setOperand(1, new ConstantInt(IntegerType.getI32(), cst.getValue() * 2));
                        instr.tag = InstrTag.Mul;
                        continue;
                    }
                    instr.setOperand(1, new ConstantInt(IntegerType.getI32(), 2));
                    instr.tag = InstrTag.Mul;
                    continue;
                }
                Value lhs = instr.getOperand(0);
                Value rhs = instr.getOperand(1);
                if (lhs instanceof BinaryInst && ((BinaryInst) lhs).tag == InstrTag.Add
                        && lhs.getUses().size() == 1 && rhs instanceof ConstantInt) {
                    Value l_lhs = ((BinaryInst) lhs).getOperand(0);
                    Value r_lhs = ((BinaryInst) lhs).getOperand(1);
                    if (r_lhs instanceof ConstantInt) {
                        instr.setOperand(0, l_lhs);
                        ConstantInt c = new ConstantInt(IntegerType.getI32(), ((ConstantInt) rhs).getValue() + ((ConstantInt) r_lhs).getValue());
                        instr.setOperand(1, c);
                        ((BinaryInst) lhs).removeSelf();
                    } else if (l_lhs instanceof ConstantInt) {
                        instr.setOperand(0, r_lhs);
                        ConstantInt c = new ConstantInt(IntegerType.getI32(), ((ConstantInt) rhs).getValue() + ((ConstantInt) l_lhs).getValue());
                        instr.setOperand(1, c);
                        ((BinaryInst) lhs).removeSelf();
                    }
                }
                if (rhs instanceof BinaryInst && ((BinaryInst) rhs).tag == InstrTag.Add
                        && rhs.getUses().size() == 1 && lhs instanceof ConstantInt) {
                    Value l_rhs = ((BinaryInst) rhs).getOperand(0);
                    Value r_rhs = ((BinaryInst) rhs).getOperand(1);
                    if (r_rhs instanceof ConstantInt) {
                        instr.setOperand(1, l_rhs);
                        ConstantInt c = new ConstantInt(IntegerType.getI32(), ((ConstantInt) lhs).getValue() + ((ConstantInt) r_rhs).getValue());
                        instr.setOperand(0, c);
                        ((BinaryInst) rhs).removeSelf();
                    } else if (l_rhs instanceof ConstantInt) {
                        instr.setOperand(1, r_rhs);
                        ConstantInt c = new ConstantInt(IntegerType.getI32(), ((ConstantInt) lhs).getValue() + ((ConstantInt) l_rhs).getValue());
                        instr.setOperand(0, c);
                        ((BinaryInst) rhs).removeSelf();
                    }
                }
                if (isConstMulValue(lhs) && rhs == val) {
                    instr.setOperand(0, val);
                    instr.setOperand(1, new ConstantInt(IntegerType.getI32(), cst.getValue() + 1));
                    instr.tag = InstrTag.Mul;
                } else if (isConstMulValue(rhs) && lhs == val) {
                    instr.setOperand(0, val);
                    instr.setOperand(1, new ConstantInt(IntegerType.getI32(), cst.getValue() + 1));
                    instr.tag = InstrTag.Mul;
                } else if (isConstMulValue(lhs)) {
                    ConstantInt t_cst = cst;
                    Value t_val = val;
                    if (isConstMulValue(rhs)) {
                        if (t_val == val) {
                            instr.setOperand(0, val);
                            instr.setOperand(1, new ConstantInt(IntegerType.getI32(), cst.getValue() + t_cst.getValue()));
                            instr.tag = InstrTag.Mul;
                        }
                    }
                }
            }
        }
    }
    public boolean isConstMulValue(Value instr) {
        if (instr instanceof BinaryInst && ((BinaryInst) instr).tag == InstrTag.Mul) {
            Value lhs = ((BinaryInst) instr).getOperand(0);
            Value rhs = ((BinaryInst) instr).getOperand(1);
            if (lhs instanceof ConstantInt) {
                cst = (ConstantInt) lhs;
                val = rhs;
            } else if (rhs instanceof ConstantInt) {
                cst = (ConstantInt) rhs;
                val = lhs;
            } else {
                return false;
            }
            return true;
        }
        return false;
    }
}
