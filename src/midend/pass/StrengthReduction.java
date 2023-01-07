package midend.pass;

import midend.ir.*;
import midend.ir.Instruction.*;
import midend.ir.Constant.*;
import utils.IList;
import utils.Pair;

public class StrengthReduction {
    // TODO: 2022/12/17 有bug，来不及改了，详见258
    public void run(Module m) {
        for (IList.INode<Function, Module> fNode : m.functionList) {
            Function func = fNode.getValue();
            if (!fNode.getValue().isBuiltin()) {
                runCalc(func);
            }
        }
    }

    private void runCalc(Function f) {
        for (IList.INode<BasicBlock, Function> bNode : f.getList()) {
            for (IList.INode<Instruction, BasicBlock> iNode : bNode.getValue().getList()) {
                if (iNode.getValue().getTag() == InstrTag.Sdiv || iNode.getValue().getTag() == InstrTag.Srem) {
                    BinaryInst bi = (BinaryInst) iNode.getValue();
                    if (bi.rhs() instanceof ConstantInt) {
                        Pair<Value, Instruction> divRes = solveDiv(bi);
                        if (iNode.getValue().getTag() == InstrTag.Srem) {
                            BinaryInst mul = new BinaryInst(InstrTag.Mul, bi.rhs(), divRes.getFir(), divRes.getSec());
                            BinaryInst sub = new BinaryInst(InstrTag.Sub, bi.lhs(), mul, mul);
                            iNode.getValue().removeSelf(sub);
                        } else {
                            iNode.getValue().removeSelf(divRes.getFir());
                        }
                    }
                } else if (iNode.getValue().getTag() == InstrTag.Mul) {
                    BinaryInst bi = (BinaryInst) iNode.getValue();
                    if (bi.rhs() instanceof ConstantInt) {
                        int rhs = ((ConstantInt) bi.rhs()).getValue();
                        if (rhs == 1 << ceilLog2(rhs)) {
                            BinaryInst shl = new BinaryInst(InstrTag.Shl, bi.lhs(), new ConstantInt(ceilLog2(rhs)), bi);
                            iNode.getValue().removeSelf(shl);
                        }
                    }
                }
            }
        }
    }

    private int ceilLog2(int x) {
        int res = 0;
        x = x - 1;
        while (x > 1) {
            x >>= 1;
            ++res;
        }
        return res + 1;
    }

    private static class Multiplier {
        public final long multiplier;
        public final int shift;
        public final int log;

        Multiplier(long multiplier, int shift, int log) {
            this.multiplier = multiplier;
            this.shift = shift;
            this.log = log;
        }
    }

    private static Multiplier getMagicValue(int divisor) {
        int log = 0;
        while (divisor > (1L << log)) {
            log++;
        }
        int shift = log;
        long low = Long.divideUnsigned(1L << (32 + log), divisor);
        long high = Long.divideUnsigned((1L << (32 + log)) + (1L << (1 + log)), divisor);
        while (high >> 1 > low >> 1 && shift > 0) {
            high >>= 1;
            low >>= 1;
            shift--;
        }
        return new Multiplier(high, shift, log);
    }

    private Pair<Value, Instruction> solveDiv(BinaryInst div) {
        int divisor = ((Constant.ConstantInt) div.rhs()).getValue();
        Multiplier m = getMagicValue(Math.abs(divisor));
        Value finalValue;
        Instruction finalInst;
        if (divisor == -2147483648) {
            finalValue = finalInst = new BinaryInst(InstrTag.Slt, div.lhs(), new ConstantInt(-2147483648), div);
            return new Pair<>(finalValue, finalInst);
        } else if (Math.abs(divisor) == 1) {
            finalValue = div.lhs();
            finalInst = div;
        } else if (Integer.bitCount(Math.abs(divisor)) == 1) {
            Instruction prev = div;
            prev = new BinaryInst(InstrTag.Ashr, div.lhs(), new ConstantInt(m.log - 1), prev);
            if (m.log != 32) {
                prev = new BinaryInst(InstrTag.Lshr, prev, new ConstantInt(32 - m.log), prev);
            }
            prev = new BinaryInst(InstrTag.Add, prev, div.lhs(), prev);
            prev = new BinaryInst(InstrTag.Ashr, prev, new ConstantInt(m.log), prev);
            finalValue = prev;
            finalInst = prev;
        } else {
            Instruction prev = div;
            if (m.multiplier < Integer.MAX_VALUE) {
                prev = new BinaryInst(InstrTag.MulH, div.lhs(), new ConstantInt((int) m.multiplier), prev);
            } else {
                prev = new BinaryInst(InstrTag.MulH, div.lhs(), new ConstantInt((int) (m.multiplier - (1L << 32))), prev);
                prev = new BinaryInst(InstrTag.Add, prev, div.lhs(), prev);
            }
            prev = new BinaryInst(InstrTag.Ashr, prev, new ConstantInt(m.shift), prev);
            Instruction sign = new BinaryInst(InstrTag.Slt, div.lhs(), new ConstantInt(0), prev);
            finalValue = finalInst = new BinaryInst(InstrTag.Add, prev, sign, sign);
        }
        if (divisor < 0) {
            finalValue = finalInst = new BinaryInst(InstrTag.Sub, ConstantInt.getConst0(), finalValue, finalInst);
        }
        return new Pair<>(finalValue, finalInst);
    }
}
