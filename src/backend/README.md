## 后端优化思想

大部分都是窥孔要注意

1. 开一个 ```value2MCVR``` 记录 ```Value``` 与虚拟寄存器的映射，在取值时能用就用，具体安排等寄存器分配的时候再 ```spill```。
2. GVN 时避免 $3-x$ 和 $x-3$ 这种情况
3. ```icmp ne/eq``` + ```br``` => ```bne/beq``` （循环入口的放在条件块后减少跳转？
4. 两个相同的 ```div``` 可能可以合并。
5. 两个相同的二元运算优化为一个二元运算和一个 ```move```。
6. 连续的 ```ne,eq``` 可以优化
7. 前端不做位运算，优化交给后端（后端也不做，中端做，包括左右移和常数除法）
8. 连续多个 ```gep``` 可以优化为 ```madd```。
9. ```gep```+```load``` 优化为一个 ```load```。
10. 溢出时如果溢出一个常数看一眼是从栈里取优还是新造一个优
11. 直接翻译是最后构建两个branch，考虑重排基本块删掉一个
12. 务必保证不会出现 ```binary``` 两边都是常数的情况
13. 一切binary尽量把常数放在右边，便于分支优化
14. 合并一串gep，收集常数，最后看怎么操作。
15. 分配 ```$v1```
16. 将```s```也分配，只不过可以spill。
17. 只溢出必须溢出的，即已经定义，会被使用，且会被修改。
18. 把常量保存起来，需要直接load。
19. 扩充GlobalVariable的大小从而只有高16位。
20. ```T0``` 用于直接分配的临时寄存器。
21. 暂时还是用开头进去先减 ```SP``` 的做法，最后再优化掉。
22. 暂时把所有寄存器都存了，到时候再优化。
23. 提前生成很多地址最后直接取而不是两遍构造？（没必要，因为栈不能一次读）
24. 在函数返回后和溢出时可以构造一个新数而不是去取。（统计cost）
25. 同一个变量多次def拆开！！！这个没做！！！
26. 优化gep，各种，包括系数只有一个0的情况
27. 现在constant会被尽可能地保存在寄存器里，到时候可以考虑重构常数。
28. 未定义数组没必要清空？
29. 现在main会返回数，可以改成不返回（指令-1）
30. 溢出参数用原来的位置（有必要吗？）
31. 第一次存就存0
32. 删除没用到的Expr;（GVN）
33. 各种没用的load store，一是按地址算没用的，二是按寄存器算没用的
34. 无初始化变量不清空
35. 不调用其他函数的函数可以分配at寄存器
36. 多次调用其他函数的函数可以只保存一次ra？
37. 寄存器分配前把addiu a, a, 0和addu a, a, zero都换成move
38. 循环交换
39. 参数！不必再存了
40. isInsert
41. br %x A, A
42. mem2reg为无初始值变量添加了初始值
43. 参数move一下，如果没有A0自己会coal的
44. 保护的时候没必要保存！！！直接move等它自己消，我是脑残
45. 测试一下寄存器分配各个之间是if-if好还是if-else if好。
46. 注意特殊的寄存器（Alloca，GV）的溢出优化。
47. move 0到别的地方
48. 和0加换成move
49. gep split

完成的：

1. 延迟槽利用起来
2. 最后总的检查一遍use和def有没有能消的
3. gp放到10010000那，全局变量直接调


```java
package backend.mc;

import backend.removedMC.MCInstr;
import utils.Config;
import midend.ir.*;
import midend.ir.Instruction.*;
import backend.removedMC.MCOperand.*;
import midend.ir.Constant.*;
import utils.Pair;

import java.util.ArrayList;

public class MCInstrFactory {
    private final MCFunction callee;
    private final MCBasicBlock mcBB;
    private final MCPhyReg ZERO = new MCPhyReg(MCPhyReg.MCPhyRegTag.zero);
    private final MCPhyReg SP = new MCPhyReg(MCPhyReg.MCPhyRegTag.sp);
    private final MCPhyReg A0 = new MCPhyReg(MCPhyReg.MCPhyRegTag.a0);
    private final MCPhyReg V0 = new MCPhyReg(MCPhyReg.MCPhyRegTag.v0);
    private final MCPhyReg T0 = new MCPhyReg(MCPhyReg.MCPhyRegTag.t0);

    public MCInstrFactory(MCBasicBlock mcBB) {
        this.mcBB = mcBB;
        this.callee = mcBB.node.getParent().getHolder();
    }

    public void newInstr(Instruction instr) {
        switch (instr.tag) {


            case Sdiv: {
                Value lhs = ((BinaryInst) instr).lhs();
                Value rhs = ((BinaryInst) instr).rhs();
                MCReg lhsR = irOp2mcRegAnyway(lhs);
                MCReg rhsR = irOp2mcRegAnyway(rhs);
                MCReg dstR = irOp2mcR(instr);
                MCInstr.buildDivMulAtEnd(MCInstr.MCInstrTag.div, lhsR, rhsR, mcBB);
                MCInstr.buildMfAtEnd(MCInstr.MCInstrTag.mflo, dstR, mcBB);
                break;
            }
            case Srem: {
                Value lhs = ((BinaryInst) instr).lhs();
                Value rhs = ((BinaryInst) instr).rhs();
                MCReg lhsR = irOp2mcRegAnyway(lhs);
                MCReg rhsR = irOp2mcRegAnyway(rhs);
                MCReg dstR = irOp2mcR(instr);
                MCInstr.buildDivMulAtEnd(MCInstr.MCInstrTag.div, lhsR, rhsR, mcBB);
                MCInstr.buildMfAtEnd(MCInstr.MCInstrTag.mfhi, dstR, mcBB);
                break;
            }
            case Shl: {
                Value lhs = ((BinaryInst) instr).lhs();
                Value rhs = ((BinaryInst) instr).rhs();
                assert rhs instanceof ConstantInt;
                assert MCImm.canEncodeImm(((ConstantInt) rhs).getValue());
                MCReg lhsR = irOp2mcR(lhs);
                MCReg dstR = irOp2mcR(instr);
                MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.sll, dstR, lhsR, new MCImm(((ConstantInt) rhs).getValue()), mcBB);
                break;
            }
            case Eq: {
                if (Config.mergeBranchWithIcmp && instr.allUsersAre(InstrTag.Br)) {
                    break;
                }
                Value lhs = ((BinaryInst) instr).lhs();
                Value rhs = ((BinaryInst) instr).rhs();
                if (lhs instanceof ConstantInt) {
                    Value tmp = lhs;
                    lhs = rhs;
                    rhs = tmp;
                }
                MCReg lhsR = irOp2mcR(lhs);
                MCReg dstR = irOp2mcR(instr);
                if (rhs instanceof ConstantInt && MCImm.canEncodeImm(((ConstantInt) rhs).getValue())) {
                    MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.xori, dstR, lhsR, new MCImm(((ConstantInt) rhs).getValue()), mcBB);
                } else {
                    MCReg rhsR = irOp2mcRegAnyway(rhs);
                    MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.xor, dstR, lhsR, rhsR, mcBB);
                }
                MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.sltiu, dstR, dstR, new MCImm(1), mcBB);
                break;
            }
            case Ne: {
                if (Config.mergeBranchWithIcmp && instr.allUsersAre(InstrTag.Br)) {
                    break;
                }
                Value lhs = ((BinaryInst) instr).lhs();
                Value rhs = ((BinaryInst) instr).rhs();
                if (lhs instanceof ConstantInt) {
                    Value tmp = lhs;
                    lhs = rhs;
                    rhs = tmp;
                }
                MCReg lhsR = irOp2mcR(lhs);
                MCReg dstR = irOp2mcR(instr);
                if (rhs instanceof ConstantInt && MCImm.canEncodeImm(((ConstantInt) rhs).getValue())) {
                    MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.xori, dstR, lhsR, new MCImm(((ConstantInt) rhs).getValue()), mcBB);
                } else {
                    MCReg rhsR = irOp2mcRegAnyway(rhs);
                    MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.xor, dstR, lhsR, rhsR, mcBB);
                }
                MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.sltu, dstR, ZERO, dstR, mcBB);
                break;
            }
            case Slt: {
                Value lhs = ((BinaryInst) instr).lhs();
                Value rhs = ((BinaryInst) instr).rhs();
                if (Config.mergeBranchWithIcmp && instr.allUsersAre(InstrTag.Br)) {
                    if (lhs instanceof ConstantInt && ((ConstantInt) lhs).isZero()) {
                        break;
                    }
                    if (rhs instanceof ConstantInt && ((ConstantInt) rhs).isZero()) {
                        break;
                    }
                    MCReg dstR = irOp2mcR(instr);
                    if (rhs instanceof ConstantInt && MCImm.canEncodeImm(-((ConstantInt) rhs).getValue())) {
                        MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.addiu, dstR, irOp2mcRegAnyway(lhs), new MCImm(-((ConstantInt) rhs).getValue()), mcBB);
                    } else {
                        MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.subu, dstR, irOp2mcRegAnyway(lhs), irOp2mcRegAnyway(rhs), mcBB);
                    }
                    break;
                }
                MCReg lhsR = irOp2mcRegAnyway(lhs);
                MCReg dstR = irOp2mcR(instr);
                if (rhs instanceof ConstantInt && MCImm.canEncodeImm(((ConstantInt) rhs).getValue())) {
                    MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.slti, dstR, lhsR, new MCImm(((ConstantInt) rhs).getValue()), mcBB);
                } else {
                    MCReg rhsR = irOp2mcRegAnyway(rhs);
                    MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.slt, dstR, lhsR, rhsR, mcBB);
                }
                break;
            }
            case Sle: {
                Value lhs = ((BinaryInst) instr).lhs();
                Value rhs = ((BinaryInst) instr).rhs();
                if (Config.mergeBranchWithIcmp && instr.allUsersAre(InstrTag.Br)) {
                    if (lhs instanceof ConstantInt && ((ConstantInt) lhs).isZero()) {
                        break;
                    }
                    if (rhs instanceof ConstantInt && ((ConstantInt) rhs).isZero()) {
                        break;
                    }
                    MCReg dstR = irOp2mcR(instr);
                    if (rhs instanceof ConstantInt && MCImm.canEncodeImm(-((ConstantInt) rhs).getValue())) {
                        MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.addiu, dstR, irOp2mcRegAnyway(lhs), new MCImm(-((ConstantInt) rhs).getValue()), mcBB);
                    } else {
                        MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.subu, dstR, irOp2mcRegAnyway(lhs), irOp2mcRegAnyway(rhs), mcBB);
                    }
                    break;
                }
                MCReg rhsR = irOp2mcRegAnyway(rhs);
                MCReg dstR = irOp2mcR(instr);
                if (lhs instanceof ConstantInt && MCImm.canEncodeImm(((ConstantInt) lhs).getValue())) {
                    MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.slti, dstR, rhsR, new MCImm(((ConstantInt) lhs).getValue()), mcBB);
                } else {
                    MCReg lhsR = irOp2mcRegAnyway(rhs);
                    MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.slt, dstR, rhsR, lhsR, mcBB);
                }
                MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.xori, dstR, dstR, new MCImm(1), mcBB);
                break;
            }
            case Sgt: {
                Value lhs = ((BinaryInst) instr).lhs();
                Value rhs = ((BinaryInst) instr).rhs();
                if (Config.mergeBranchWithIcmp && instr.allUsersAre(InstrTag.Br)) {
                    if (lhs instanceof ConstantInt && ((ConstantInt) lhs).isZero()) {
                        break;
                    }
                    if (rhs instanceof ConstantInt && ((ConstantInt) rhs).isZero()) {
                        break;
                    }
                    MCReg dstR = irOp2mcR(instr);
                    if (rhs instanceof ConstantInt && MCImm.canEncodeImm(-((ConstantInt) rhs).getValue())) {
                        MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.addiu, dstR, irOp2mcRegAnyway(lhs), new MCImm(-((ConstantInt) rhs).getValue()), mcBB);
                    } else {
                        MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.subu, dstR, irOp2mcRegAnyway(lhs), irOp2mcRegAnyway(rhs), mcBB);
                    }
                    break;
                }
                MCReg rhsR = irOp2mcRegAnyway(rhs);
                MCReg dstR = irOp2mcR(instr);
                if (lhs instanceof ConstantInt && MCImm.canEncodeImm(((ConstantInt) lhs).getValue())) {
                    MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.slti, dstR, rhsR, new MCImm(((ConstantInt) lhs).getValue()), mcBB);
                } else {
                    MCReg lhsR = irOp2mcRegAnyway(rhs);
                    MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.slt, dstR, rhsR, lhsR, mcBB);
                }
                break;
            }
            case Sge: {
                Value lhs = ((BinaryInst) instr).lhs();
                Value rhs = ((BinaryInst) instr).rhs();
                if (Config.mergeBranchWithIcmp && instr.allUsersAre(InstrTag.Br)) {
                    if (lhs instanceof ConstantInt && ((ConstantInt) lhs).isZero()) {
                        break;
                    }
                    if (rhs instanceof ConstantInt && ((ConstantInt) rhs).isZero()) {
                        break;
                    }
                    MCReg dstR = irOp2mcR(instr);
                    if (rhs instanceof ConstantInt && MCImm.canEncodeImm(-((ConstantInt) rhs).getValue())) {
                        MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.addiu, dstR, irOp2mcRegAnyway(lhs), new MCImm(-((ConstantInt) rhs).getValue()), mcBB);
                    } else {
                        MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.subu, dstR, irOp2mcRegAnyway(lhs), irOp2mcRegAnyway(rhs), mcBB);
                    }
                    break;
                }
                MCReg lhsR = irOp2mcRegAnyway(lhs);
                MCReg dstR = irOp2mcR(instr);
                if (rhs instanceof ConstantInt && MCImm.canEncodeImm(((ConstantInt) rhs).getValue())) {
                    MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.slti, dstR, lhsR, new MCImm(((ConstantInt) rhs).getValue()), mcBB);
                } else {
                    MCReg rhsR = irOp2mcRegAnyway(rhs);
                    MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.slt, dstR, lhsR, rhsR, mcBB);
                }
                MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.xori, dstR, dstR, new MCImm(1), mcBB);
                break;
            }
            case Alloca: {
                Alloca alloca = (Alloca) instr;
                int size = alloca.getAllocated().needBytes();
                MCReg dst = irOp2mcRegAnyway(alloca);
                MCImm offset = new MCImm(callee.getStackSize());
                MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.addu, dst, SP, offset, mcBB);
                callee.getAllocaImm().put(dst, offset);
                callee.addStackSize(size);
                if (!MCImm.canEncodeImm(callee.getStackSize())) {
                    throw new RuntimeException("Alloca stack size exceeded.");
                }
                break;
            }
            case Load: {
                Load load = (Load) instr;
                MCReg dst = irOp2mcRegAnyway(load);
                if (load.getPointer() instanceof GlobalVariable) {
                    int pos = MCModule.module.lirGVs.get((GlobalVariable) load.getPointer()).getPos();
                    if (MCImm.HI(pos).getImm() != 0) {
                        MCInstr.buildLuiAtEnd(T0, MCImm.HI(pos), mcBB);
                        MCInstr.buildLwAtEnd(dst, MCImm.LO(pos), T0, mcBB);
                    } else {
                        MCInstr.buildLwAtEnd(dst, MCImm.LO(pos), ZERO, mcBB);
                    }
                } else if (load.getPointer() instanceof GEP) {
                    MCReg base = irOp2mcRegAnyway(load.getPointer());
                    MCInstr.buildLwAtEnd(dst, new MCImm(callee.getGepOffsets().get((GEP) load.getPointer())), base, mcBB);
                } else {
                    MCReg base = irOp2mcRegAnyway(load.getPointer());
                    MCInstr.buildLwAtEnd(dst, new MCImm(0), base, mcBB);
                }
                break;
            }
            case Store: {
                Store store = (Store) instr;
                MCReg n = irOp2mcRegAnyway(store.getValue());
                if (store.getPointer() instanceof GlobalVariable) {
                    int pos = MCModule.module.lirGVs.get((GlobalVariable) store.getPointer()).getPos();
                    if (MCImm.HI(pos).getImm() != 0) {
                        MCInstr.buildLuiAtEnd(T0, MCImm.HI(pos), mcBB);
                        MCInstr.buildSwAtEnd(n, MCImm.LO(pos), T0, mcBB);
                    } else {
                        MCInstr.buildSwAtEnd(n, MCImm.LO(pos), ZERO, mcBB);
                    }
                } else if (store.getPointer() instanceof GEP) {
                    MCReg base = irOp2mcRegAnyway(store.getPointer());
                    MCInstr.buildSwAtEnd(n, new MCImm(callee.getGepOffsets().get((GEP) store.getPointer())), base, mcBB);
                } else {
                    MCReg base = irOp2mcRegAnyway(store.getPointer());
                    MCInstr.buildSwAtEnd(n, new MCImm(0), base, mcBB);
                }
                break;
            }
            case Zext: {
                // TODO: 2022/9/27 maybe ?
                break;
            }
            case Ret: {
                MCInstr.buildRetAtEnd(callee, mcBB);
                if (instr.getOperand(0) != null) {
                    MCReg v0 = V0;
                    if (instr.getOperand(0) instanceof ConstantInt) {
                        buildSaveCIIn(v0, ((ConstantInt) instr.getOperand(0)).getValue());
                    } else {
                        MCInstr.buildMoveAtEnd(v0, irOp2mcRegAnyway(instr.getOperand(0)), mcBB);
                    }
                }
                break;
            }
            case Br: {
                Br br = (Br) instr;
                if (br.getOperandNum() == 1) {
                    MCInstr.buildJAtEnd(MCInstr.MCInstrTag.j, callee.getBBLabelMap().get((BasicBlock) br.getOperand(0)), mcBB);
                } else {
                    Value cond = br.getOperand(0);
                    MCLabel trueLabel = callee.getBBLabelMap().get((BasicBlock) br.getOperand(1));
                    MCLabel falseLabel = callee.getBBLabelMap().get((BasicBlock) br.getOperand(2));
                    if (cond instanceof ConstantInt) {
                        // TODO: 2022/10/2 不应存在条件为Constant的跳转！
                        if (((ConstantInt) cond).isZero()) {
                            MCInstr.buildJAtEnd(MCInstr.MCInstrTag.j, trueLabel, mcBB);
                        } else {
                            MCInstr.buildJAtEnd(MCInstr.MCInstrTag.j, falseLabel, mcBB);
                        }
                        MCInstr.buildNopAtEnd(mcBB);
                    } else if (cond instanceof BinaryInst && cond.allUsersAre(InstrTag.Br)) {
                        BinaryInst bi = (BinaryInst) cond;
                        MCReg lhsR = irOp2mcRegAnyway(bi.lhs());
                        MCReg rhsR = irOp2mcRegAnyway(bi.rhs());
                        if (bi.getTag() == InstrTag.Eq) {
                            MCInstr.buildBranchEAtEnd(MCInstr.MCInstrTag.beq, lhsR, rhsR, trueLabel, mcBB);
                        } else if (bi.getTag() == InstrTag.Ne) {
                            MCInstr.buildBranchEAtEnd(MCInstr.MCInstrTag.beq, lhsR, rhsR, trueLabel, mcBB);
                        } else if (bi.getTag() == InstrTag.Sle ||
                                bi.getTag() == InstrTag.Slt ||
                                bi.getTag() == InstrTag.Sge ||
                                bi.getTag() == InstrTag.Sgt) {
                            if (bi.lhs() instanceof ConstantInt && ((ConstantInt) bi.lhs()).isZero()) {
                                MCInstr.buildBranchZAtEnd(cmp2BCondInv(bi.getTag()), irOp2mcRegAnyway(bi.rhs()), trueLabel, mcBB);
                            } else if (bi.rhs() instanceof ConstantInt && ((ConstantInt) bi.rhs()).isZero()) {
                                MCInstr.buildBranchZAtEnd(cmp2BCond(bi.getTag()), irOp2mcRegAnyway(bi.lhs()), trueLabel, mcBB);
                            } else {
                                assert callee.getValue2MCReg().containsKey(bi);
                                MCReg dstR = irOp2mcR(bi);
                                MCInstr.buildBranchZAtEnd(cmp2BCond(bi.getTag()), dstR, trueLabel, mcBB);
                            }
                        } else {
                            MCReg condR = irOp2mcRegAnyway(cond);
                            MCInstr.buildBranchEAtEnd(MCInstr.MCInstrTag.bne, condR, ZERO, trueLabel, mcBB);
                        }
                        MCInstr.buildNopAtEnd(mcBB);
                        MCInstr.buildJAtEnd(MCInstr.MCInstrTag.j, falseLabel, mcBB);
                        MCInstr.buildNopAtEnd(mcBB);
                    } else {
                        MCReg condR = irOp2mcRegAnyway(cond);
                        MCInstr.buildBranchEAtEnd(MCInstr.MCInstrTag.bne, condR, ZERO, trueLabel, mcBB);
                        MCInstr.buildNopAtEnd(mcBB);
                        MCInstr.buildJAtEnd(MCInstr.MCInstrTag.j, falseLabel, mcBB);
                        MCInstr.buildNopAtEnd(mcBB);
                    }
                }
                break;
            }
            case Call: {
                Call call = (Call) instr;
                if (call.getOperand(0).getName().equals("printf")) {
                    String origin = ((ConstantString) ((GlobalVariable) call.getOperand(1)).getInit()).getString();
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
                                MCInstr.buildSyscallAtEnd(mcBB);
                            }
                            ++i;
                            if (call.getOperand(cur) instanceof ConstantInt) {
                                buildSaveCIIn(A0, ((ConstantInt) call.getOperand(cur)).getValue());
                            } else {
                                MCInstr.buildMoveAtEnd(A0, irOp2mcRegAnyway(call.getOperand(cur)), mcBB);
                            }
                            buildSaveCIIn(V0, 1);
                            MCInstr.buildSyscallAtEnd(mcBB);
                        } else {
                            sb.append(c);
                        }
                    }
                    String str = sb.toString();
                    if (!str.equals("")) {
                        buildSaveCIIn(A0, MCModule.module.strings.get(str).getPos());
                        buildSaveCIIn(V0, 4);
                        MCInstr.buildSyscallAtEnd(mcBB);
                    }
                } else if (call.getOperand(0).getName().equals("getint")) {
                    MCReg dst = irOp2mcR(call);
                    buildSaveCIIn(V0, 5);
                    MCInstr.buildSyscallAtEnd(mcBB);
                    MCInstr.buildMoveAtEnd(dst, V0, mcBB);
                } else {
                    int cnt = call.getOperandNum() - 2;
                    ArrayList<MCReg> argsRegs = new ArrayList<>();
                    for (int i = 2; i < call.getOperandNum(); ++i) {
                        MCReg arg = irOp2mcRegAnyway(call.getOperand(i));
                        if (call.getOperand(i) instanceof GEP && callee.getGepOffsets().get((GEP) call.getOperand(i)) != 0) {
                            MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.addiu, arg, arg, new);
                        }
                    }
                    if (cnt <= 4) {

                    }
                }
            }
            case GetElementPtr: {
                GEP gep = (GEP) instr;
                ArrayList<Integer> dims;
                Type thisInstrType = ((Type.PointerType) gep.getPointer().getType()).getPointTo();
                if (thisInstrType.isArrayType()) {
                    dims = ((Type.ArrayType) thisInstrType).getDims();
                } else {
                    dims = new ArrayList<>();
                }
                for (int i = dims.size() - 2; i >= 0; --i) {
                    dims.set(i, dims.get(i) * dims.get(i + 1));
                }
                ArrayList<Pair<MCReg, MCReg>> addMuls = new ArrayList<>();
                int offsetNum = 0;
                for (int i = 1; i < gep.getOperandNum(); ++i) {
                    Value op = gep.getOperand(i);
                    if (op instanceof ConstantInt) {
                        offsetNum += ((ConstantInt) op).getValue() * dims.get(i - 1) * 4;
                    } else {
                        addMuls.add(new Pair<>(irOp2mcRegAnyway(op), irOp2mcRegAnyway(new ConstantInt(dims.get(i - 1) * 4))));
                    }
                }
                MCReg dst = irOp2mcRegAnyway(instr);
                if (gep.getPointer() instanceof GlobalVariable) {
                    MCGlobalVariable mcGV = MCModule.module.lirGVs.get((GlobalVariable) gep.getPointer());
                    if (Config.collectGEPConstant) {
                        // TODO: 2022/10/6 collectGEPConstant
                    } else {
                        if (!addMuls.isEmpty()) {
                            MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.mul, dst, addMuls.get(0).getFir(), addMuls.get(0).getSec(), mcBB);
                            if (addMuls.size() > 1) {
                                for (int i = 1; i < addMuls.size(); ++i) {
                                    MCInstr.buildDivMulAtEnd(MCInstr.MCInstrTag.madd, addMuls.get(0).getFir(), addMuls.get(1).getSec(), mcBB);
                                }
                                MCInstr.buildMfAtEnd(MCInstr.MCInstrTag.mflo, dst, mcBB);
                            }
                            MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.addu, dst, dst, irCI2mcRegImm(new ConstantInt(mcGV.getPos() + offsetNum)), mcBB);
                        } else {
                            buildSaveCIIn(dst, mcGV.getPos() + offsetNum);
                        }
                    }
                } else {
                    MCReg base = irOp2mcRegAnyway(gep.getPointer());
                    if (Config.collectGEPConstant && gep.getPointer() instanceof GEP) {
                        offsetNum += callee.getGepOffsets().get((GEP) gep.getPointer());
                        callee.getGepOffsets().put(gep, offsetNum);
                    } else {
                        if (!addMuls.isEmpty()) {
                            MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.mul, dst, addMuls.get(0).getFir(), addMuls.get(0).getSec(), mcBB);
                            if (addMuls.size() > 1) {
                                for (int i = 1; i < addMuls.size(); ++i) {
                                    MCInstr.buildDivMulAtEnd(MCInstr.MCInstrTag.madd, addMuls.get(0).getFir(), addMuls.get(1).getSec(), mcBB);
                                }
                                MCInstr.buildMfAtEnd(MCInstr.MCInstrTag.mflo, dst, mcBB);
                            }
                        }
                        if (!addMuls.isEmpty()) {
                            MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.addu, dst, base, dst, mcBB);
                        } else {
                            if (offsetNum == 0) {
                                MCInstr.buildMoveAtEnd(dst, base, mcBB);
                            } else if (MCImm.canEncodeImm(offsetNum)) {
                                MCInstr.buildBinaryIAtEnd(MCInstr.MCInstrTag.addiu, dst, base, new MCImm(offsetNum), mcBB);
                            } else {
                                MCInstr.buildBinaryRAtEnd(MCInstr.MCInstrTag.addu, dst, base, irCI2mcRegImm(new ConstantInt(offsetNum)), mcBB);
                            }
                        }
                    }

                }
                break;
            }
        }
    }

    private MCInstr.MCInstrTag cmp2BCond(InstrTag tag) {
        switch (tag) {
            case Sle:
                return MCInstr.MCInstrTag.blez;
            case Slt:
                return MCInstr.MCInstrTag.bltz;
            case Sge:
                return MCInstr.MCInstrTag.bgez;
            case Sgt:
                return MCInstr.MCInstrTag.bgtz;
            default: {
                throw new RuntimeException("not legal branch cond!");
            }
        }
    }

    private MCInstr.MCInstrTag cmp2BCondInv(InstrTag tag) {
        switch (tag) {
            case Sle:
                return MCInstr.MCInstrTag.bgez;
            case Slt:
                return MCInstr.MCInstrTag.bgtz;
            case Sge:
                return MCInstr.MCInstrTag.blez;
            case Sgt:
                return MCInstr.MCInstrTag.bltz;
            default: {
                throw new RuntimeException("not legal branch cond!");
            }
        }
    }
}
```
