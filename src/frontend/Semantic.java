package frontend;

import midend.ir.Instruction.*;
import midend.ir.*;
import midend.ir.Constant.*;
import midend.ir.Type.*;
import utils.Logger;
import utils.IList;
import utils.ValueCopy;

import java.util.*;

public class Semantic {
    private final Module m = new Module();
    private final Scope scope = new Scope();
    private static final Constant.ConstantInt CONST0 = Constant.ConstantInt.getConst0();
    private final Type I32 = Type.IntegerType.getI32();
    private BasicBlock curBB;
    private Function curFunc;

    public void checkDuplicateName(Lexer.Token token) {
        if (scope.top().get(token.getText()) != null) {
            Logger.logError(token.getLine(), ErrorHandler.Error.ErrorType.DuplicateName);
        }
    }

    private Value buildBinaryInst(InstrTag tag, Value lhs, Value rhs) {
        if (lhs.getType().isI1Type()) {
            lhs = new Zext(lhs, I32, curBB);
        }
        if (rhs.getType().isI1Type()) {
            rhs = new Zext(rhs, I32, curBB);
        }
        return new BinaryInst(tag, lhs, rhs, curBB);
    }

    private void buildCondBr(Value cond, BasicBlock trueBB, BasicBlock falseBB) {
        if (cond.getType() == I32) {
            cond = buildBinaryInst(InstrTag.Ne, CONST0, cond);
        }
        new Br(cond, trueBB, falseBB, curBB);
    }

    public Semantic(Ast ast) {
        scope.put("getint", new Function("getint", new FunctionType(I32, new ArrayList<>()), null, true, m));
        Type Void = VoidType.getType();
        scope.put("printf", new Function("printf", new FunctionType(Void, new ArrayList<>()), null, true, m));
        for (Ast.CompUnit compUnit : ast.getCompUnits()) {
            visitCompUnit(compUnit);
        }
    }

    private void visitCompUnit(Ast.CompUnit compUnit) {
        if (compUnit instanceof Ast.Decl) {
            visitDecl((Ast.Decl) compUnit);
        } else {
            visitFuncDef((Ast.FuncDef) compUnit);
        }
    }

    private void visitDecl(Ast.Decl decl) {
        boolean isConstant = decl.isConstant();
        visitDef(decl.getDefs().get(0), isConstant);
        for (int i = 1; i < decl.getDefs().size(); ++i) {
            visitDef(decl.getDefs().get(i), isConstant);
        }
    }

    private Constant calcGlobalInitVal(ArrayList<Ast.InitVal> vals, Type type) {
        if (type.isI32Type()) {
            if (vals.size() == 0) {
                return CONST0;
            }
            Constant res = (Constant) visitExp((Ast.Exp) vals.get(0).getInitValItem(), true);
            vals.remove(0);
            return res;
        } else {
            if (vals.size() == 0) {
                return new ConstantArray(type, new HashMap<>());
            }
            HashMap<Integer, Constant> constantRes = new HashMap<>();
            ArrayType arrayType = (ArrayType) type;
            for (int i = 0; i < arrayType.getLength() && vals.size() != 0; ++i) {
                if (vals.get(0).getInitValItem() instanceof Ast.Exp) {
                    constantRes.put(i, calcGlobalInitVal(vals, arrayType.getChildType()));
                } else {
                    ArrayList<Ast.InitVal> childVals = new ArrayList<>(((Ast.InitArrayVal) vals.get(0).getInitValItem()).getInitVals());
                    vals.remove(0);
                    constantRes.put(i, calcGlobalInitVal(childVals, arrayType.getChildType()));
                }
            }
            return new ConstantArray(type, constantRes);
        }
    }

    private Constant calcGlobalInitVal(Ast.InitVal initVal, Type type) {
        Constant res;
        if (type.isI32Type()) {
            res = (Constant) visitExp((Ast.Exp) initVal.getInitValItem(), true);
        } else {
            res = calcGlobalInitVal(new ArrayList<>(((Ast.InitArrayVal) initVal.getInitValItem()).getInitVals()), type);
        }
        return res;
    }

    private HashMap<Integer, Value> calcLocalInitVal(ArrayList<Ast.InitVal> vals, Type type, boolean isConstant) {
        if (type.isI32Type()) {
            if (vals.size() == 0) {
                HashMap<Integer, Value> res = new HashMap<>();
                res.put(0, CONST0);
                return res;
            }
            Value value = visitExp((Ast.Exp) vals.get(0).getInitValItem(), isConstant);
            vals.remove(0);
            return new HashMap<>(Collections.singletonMap(0, value));
        } else {
            ArrayType arrayType = (ArrayType) type;
            if (vals.size() == 0) {
                HashMap<Integer, Value> res = new HashMap<>();
                for (int i = 0; i < arrayType.getNumOfAtomElements(); ++i) {
                    res.put(i, CONST0);
                }
                return res;
            }
            HashMap<Integer, Value> res = new HashMap<>();
            for (int i = 0; i < arrayType.getLength() && vals.size() != 0; ++i) {
                HashMap<Integer, Value> map;
                if (vals.get(0).getInitValItem() instanceof Ast.Exp) {
                    map = calcLocalInitVal(vals, arrayType.getChildType(), isConstant);
                } else {
                    map = calcLocalInitVal(new ArrayList<>(((Ast.InitArrayVal) vals.get(0).getInitValItem()).getInitVals()), arrayType.getChildType(), isConstant);
                    vals.remove(0);
                }
                for (int index : map.keySet()) {
                    res.put(index + i * arrayType.getChildNumOfAtomElements(), map.get(index));
                }
            }
            return res;
        }
    }

    private HashMap<Integer, Value> calcLocalInitVal(Ast.InitVal initVal, Type type, boolean isConstant) {
        if (initVal == null) {
            return null;
        }
        HashMap<Integer, Value> res;
        if (initVal.getInitValItem() instanceof Ast.Exp) {
            res = new HashMap<>(Collections.singletonMap(0, visitExp((Ast.Exp) initVal.getInitValItem(), isConstant)));
        } else {
            res = calcLocalInitVal(new ArrayList<>(((Ast.InitArrayVal) initVal.getInitValItem()).getInitVals()), type, isConstant);
        }
        return res;
    }

    private void storeArrayInFunctionInit(HashMap<Integer, Value> array, Instruction ptr, int size) {
        for (int i = 0; i < size; ++i) {
            if (array.get(i) != null) {
                int ii = i;
                GEP elePtr = new GEP(ptr, new ArrayList<Value>() {{ add(new ConstantInt(IntegerType.getI32(), ii)); }}, curBB);
                new Store(array.get(i), elePtr, curBB);
            } else {
                int ii = i;
                GEP elePtr = new GEP(ptr, new ArrayList<Value>() {{ add(new ConstantInt(IntegerType.getI32(), ii)); }}, curBB);
                new Store(CONST0, elePtr, curBB);
            }
        }
    }

    private void visitDef(Ast.Def def, boolean isConstant) {
        String name = def.getIdent().getText();
        checkDuplicateName(def.getIdent());
        Type arrayType = I32;
        ArrayList<Integer> indexes = new ArrayList<>();
        for (Ast.Exp len : def.getIndexes()) {
            indexes.add(((ConstantInt) visitExp(len, true)).getValue());
        }
        for (int i = indexes.size() - 1; i >= 0; --i) {
            arrayType = new ArrayType(arrayType, indexes.get(i));
        }
        if (scope.isGlobal()) {
            Constant initVal = null;
            if (def.getInitVal() != null) {
                initVal = calcGlobalInitVal(def.getInitVal(), arrayType);
            }
            if (initVal instanceof ConstantArray && initVal.isZero()) {
                initVal = null;
            }
            GlobalVariable GV = new GlobalVariable(name, arrayType, initVal, isConstant);
            scope.put(name, new Scope.Symbol(isConstant, initVal, GV));
            m.globalList.add(GV);
        } else {
            Alloca alloca = new Alloca(arrayType, curBB);
            HashMap<Integer, Value> res = calcLocalInitVal(def.getInitVal(), arrayType, isConstant);
            if (res != null) {
                Instruction ptr = alloca;
                for (int i = 0; i < indexes.size(); ++i) {
                    ptr = new GEP(ptr, new ArrayList<Value>() {{
                        add(CONST0);
                        add(CONST0);
                    }}, curBB);
                }
                storeArrayInFunctionInit(res, ptr, arrayType instanceof ArrayType ? ((ArrayType) arrayType).getNumOfAtomElements() : 1);
            }
            scope.put(name, new Scope.Symbol(isConstant, isConstant ? Constant.buildConstantFromValues(arrayType, res) : null, alloca));
        }
    }

    private void visitFuncDef(Ast.FuncDef funcDef) {
        String name = funcDef.getIdent().getText();
        checkDuplicateName(funcDef.getIdent());
        Type retType;
        if (funcDef.getReturnType().getIRType().isVoidType()) {
            retType = VoidType.getType();
        } else {
            retType = IntegerType.getI32();
        }
        ArrayList<Function.Param> params = new ArrayList<>();
        ArrayList<Type> paramTypes = new ArrayList<>();
        if (!funcDef.getFuncFParams().isEmpty()) {
            Function.Param param = visitFuncFParam(funcDef.getFuncFParams().get(0));
            params.add(param);
            paramTypes.add(param.getType());
            for (int i = 1; i < funcDef.getFuncFParams().size(); ++i) {
                param = visitFuncFParam(funcDef.getFuncFParams().get(i));
                params.add(param);
                paramTypes.add(param.getType());
            }
        }
        FunctionType functionType = new FunctionType(retType, paramTypes);
        Function function = new Function(name, functionType, params, false, m);
        scope.put(name, function);
        BasicBlock thisBasicBlock = curBB;
        curBB = new BasicBlock(name + "function" + (++m.basicBlockIdx), function);
        curFunc = function;
        visitBlock(funcDef.getBlock(), retType.isI32Type(), funcDef);
        if (curBB.getList().getLast().getValue() == null ||
                !curBB.getList().getLast().getValue().getTag().isTerminator()) {
            if (functionType.getReturnType().isVoidType()) {
                new Ret(null, curBB);
            } else {
                new Ret(CONST0, curBB);
            }
        }
        curBB = thisBasicBlock;
    }

    private void visitBlock(Ast.Block block, boolean needReturnAtEnd, Ast.FuncDef funcDef) {
        scope.push();
        if (funcDef != null) {
            for (int i = 0; i < ((FunctionType) curFunc.getType()).getParamLength(); ++i) {
                Alloca allocaInst = new Alloca(((FunctionType) curFunc.getType()).getParamType(i), curBB);
                Function.Param param = curFunc.getParamList().get(i);
                new Store(param, allocaInst, curBB);
                checkDuplicateName(funcDef.getFuncFParams().get(i).getIdent());
                scope.put(param.getName(), new Scope.Symbol(false, null, allocaInst));
            }
        }
        for (Ast.BlockItem blockItem : block.getBlockItems()) {
            visitBlockItem(blockItem);
        }
        scope.pop();
        if (needReturnAtEnd) {
            if (block.getBlockItems().isEmpty()) {
                Logger.logError(block.getRBraceToken().getLine(), ErrorHandler.Error.ErrorType.LackReturn);
            } else {
                Ast.BlockItem.BlockItemItem lastBII = block.getBlockItems().get(block.getBlockItems().size() - 1).getBlockItemItem();
                if (!(lastBII instanceof Ast.Stmt)) {
                    Logger.logError(block.getRBraceToken().getLine(), ErrorHandler.Error.ErrorType.LackReturn);
                } else {
                    Ast.Stmt lastStmt = (Ast.Stmt) lastBII;
                    if (!(lastStmt.getStmtItem() instanceof Ast.ReturnStmt)) {
                        Logger.logError(block.getRBraceToken().getLine(), ErrorHandler.Error.ErrorType.LackReturn);
                    }
                }
            }
        }
    }

    private Function.Param visitFuncFParam(Ast.FuncFParam funcFParam) {
        String name = funcFParam.getIdent().getText();
        Type type = I32;
        if (funcFParam.hasExp()) {
            ArrayList<Integer> dims = new ArrayList<>();
            for (Ast.Exp exp : funcFParam.getExps()) {
                dims.add(((ConstantInt) visitExp(exp, true)).getValue());
            }
            for (int i = dims.size() - 1; i >= 0; --i) {
                type = new ArrayType(type, dims.get(i));
            }
            type = new PointerType(type);
        }
        return new Function.Param(type, name);
    }

    private void visitBlockItem(Ast.BlockItem blockItem) {
        if (blockItem.getBlockItemItem() instanceof Ast.Stmt) {
            visitStmt((Ast.Stmt) blockItem.getBlockItemItem());
        } else {
            visitDecl((Ast.Decl) blockItem.getBlockItemItem());
        }
    }

    private void visitStmt(Ast.Stmt stmt) {
        if (stmt.getStmtItem() instanceof Ast.AssignStmt) {
            visitAssign((Ast.AssignStmt) stmt.getStmtItem());
        } else if (stmt.getStmtItem() instanceof Ast.ExpStmt) {
            visitExpStmt((Ast.ExpStmt) stmt.getStmtItem());
        } else if (stmt.getStmtItem() instanceof Ast.Block) {
            visitBlock((Ast.Block) stmt.getStmtItem(), false, null);
        } else if (stmt.getStmtItem() instanceof Ast.WhileStmt) {
            visitWhile((Ast.WhileStmt) stmt.getStmtItem());
        } else if (stmt.getStmtItem() instanceof Ast.BreakStmt) {
            visitBreak();
        } else if (stmt.getStmtItem() instanceof Ast.ContinueStmt) {
            visitContinue();
        } else if (stmt.getStmtItem() instanceof Ast.ReturnStmt) {
            visitReturn((Ast.ReturnStmt) stmt.getStmtItem());
        } else if (stmt.getStmtItem() instanceof Ast.IfStmt) {
            visitIfStmt((Ast.IfStmt) stmt.getStmtItem());
        } else if (stmt.getStmtItem() instanceof Ast.GetIntStmt) {
            visitGetInt((Ast.GetIntStmt) stmt.getStmtItem());
        } else {
            visitPrintf((Ast.PrintfStmt) stmt.getStmtItem());
        }
    }

    private Value visitLVal(Ast.LVal lVal, boolean returnValue, boolean allConst) {
        String ident = lVal.getIdent().getText();
        Scope.Symbol symbol = scope.find(ident);
        if (symbol == null) {
            Logger.logError(lVal.getIdent().getLine(), ErrorHandler.Error.ErrorType.UndefinedName);
            return null;
        }
        Value pointer = symbol.getValue();
        PointerType pointerType = (PointerType) pointer.getType();
        if (allConst) {
            assert symbol.isConstant();
            Constant init = symbol.getInit();
            for (Ast.Exp exp : lVal.getExps()) {
                int index = ((ConstantInt) visitExp(exp, true)).getValue();
                init = ((ConstantArray) init).getValue(index);
            }
            return init;
        } else {
            if (pointerType.getPointTo().isArrayType()) {
                for (Ast.Exp exp : lVal.getExps()) {
                    Value index = visitExp(exp, false);
                    pointer = new GEP(pointer, new ArrayList<Value>() {{
                        add(CONST0);
                        add(index);
                    }}, curBB);
                }
            } else if (pointerType.getPointTo().isPointerType()) {
                for (int i = 0; i < lVal.getExps().size(); ++i) {
                    Value index = visitExp(lVal.getExps().get(i), false);
                    if (i == 0) {
                        pointer = new Load(pointer, curBB);
                        pointer = new GEP(pointer, new ArrayList<Value>() {{
                            add(index);
                        }}, curBB);
                    } else {
                        pointer = new GEP(pointer, new ArrayList<Value>() {{
                            add(CONST0);
                            add(index);
                        }}, curBB);
                    }
                }
            }
            if (returnValue) {
                if (((PointerType) pointer.getType()).getPointTo().isI32Type()) {
                    pointer = new Load(pointer, curBB);
                } else if (((PointerType) pointer.getType()).getPointTo().isArrayType()) {
                    pointer = new GEP(pointer, new ArrayList<Value>() {{ add(CONST0); add(CONST0); }}, curBB);
                } else {
                    pointer = new Load(pointer, curBB);
                }
            }
            return pointer;
        }
    }

    private void visitPrintf(Ast.PrintfStmt printf) {
        ArrayList<Value> args = new ArrayList<>();
        args.add(visitFormatString(printf.getFormatString()));
        for (Ast.Exp exp : printf.getExps()) {
            args.add(visitExp(exp, false));
        }
        ErrorHandler.checkPrintfArgsMatchesFormatString(printf.getFormatString().getContext().getText(), args.size() - 1, printf.getPrintfToken().getLine());
        new Call(scope.getFunc("printf"), args, curBB);
    }

    private Value visitPrimaryExp(Ast.PrimaryExp exp, boolean allConst) {
        Value value;
        if (exp.getPrimaryExpItem() instanceof Ast.Exp) {
            value = visitExp((Ast.Exp) exp.getPrimaryExpItem(), allConst);
        } else if (exp.getPrimaryExpItem() instanceof Ast.Number) {
            value = visitNumber((Ast.Number) exp.getPrimaryExpItem());
        } else if (exp.getPrimaryExpItem() instanceof Ast.LVal) {
            value = visitLVal((Ast.LVal) exp.getPrimaryExpItem(), true, allConst);
        } else {
            value = visitCallee((Ast.Callee) exp.getPrimaryExpItem());
        }
        return value;
    }

    private ConstantInt visitNumber(Ast.Number number) {
        if (number.getToken().getText().equals("2147483648")) {
            return new ConstantInt(-2147483648);
        } else {
            return new ConstantInt(number.getVal());
        }
    }

    private Value visitCallee(Ast.Callee callee) {
        Function function = scope.getFunc(callee.getIdent().getText());
        if (function == null) {
            Logger.logError(callee.getIdent().getLine(), ErrorHandler.Error.ErrorType.UndefinedName);
            return null;
        }
        ArrayList<Value> args = new ArrayList<>();
        if (callee.getFuncRParams().size() != function.getParamList().size()) {
            Logger.logError(callee.getIdent().getLine(), ErrorHandler.Error.ErrorType.UnmatchedParamNumber);
            return null;
        }
        for (int i = 0; i < callee.getFuncRParams().size(); ++i) {
            Ast.FuncRParam funcRParam = callee.getFuncRParams().get(i);
            Value rParam = visitFuncRParam(funcRParam);
            Type funcThisParamType = function.getParamList().get(i).getType();
            if (!rParam.getType().equals(funcThisParamType)) {
                if (rParam.getType().isPointerType() && funcThisParamType.isPointerType()) {

                }
            }
            if (!rParam.getType().equals(function.getParamList().get(i).getType())) {
                Logger.logError(callee.getIdent().getLine(), ErrorHandler.Error.ErrorType.UnmatchedParamType);
            }
            args.add(rParam);
        }
        return new Call(function, args, curBB);
    }

    private Value visitFuncRParam(Ast.FuncRParam funcRParam) {
        Value value;
        if (funcRParam.getFuncRParamItem() instanceof Ast.Exp) {
            value = visitExp((Ast.Exp) funcRParam.getFuncRParamItem(), false);
        } else {
            value = visitFormatString((Ast.FormatString) funcRParam.getFuncRParamItem());
        }
        return value;
    }

    private int formatStringCnt = 0;

    private Value visitFormatString(Ast.FormatString string) {
        String content = string.getContext().getText();
        ConstantString cs = new ConstantString(content);
        String gvName = "FormatString" + ++formatStringCnt;
        GlobalVariable gv = new GlobalVariable(gvName, cs.getType(), cs, true);
        scope.putGlobal(gvName, new Scope.Symbol(true, cs, gv));
        m.globalList.add(gv);
        return gv;
    }

    private void visitAssign(Ast.AssignStmt assign) {
        Ast.LVal lVal = assign.getLVal();
        Scope.Symbol lValSymbol = scope.find(lVal.getIdent().getText());
        if (lValSymbol != null && lValSymbol.isConstant()) {
            Logger.logError(lVal.getIdent().getLine(), ErrorHandler.Error.ErrorType.AssignToConst);
        }
        Value lValPointer = visitLVal(lVal, false, false);
        Value rVal = visitExp(assign.getExp(), false);
        if (lValSymbol != null) {
            new Store(rVal, lValPointer, curBB);
        }
    }

    private void visitGetInt(Ast.GetIntStmt getInt) {
        if (scope.find(getInt.getLVal().getIdent().getText()).isConstant()) {
            Logger.logError(getInt.getLVal().getIdent().getLine(), ErrorHandler.Error.ErrorType.AssignToConst);
        }
        Value lValPointer = visitLVal(getInt.getLVal(), false, false);
        Value rVal = new Call(scope.getFunc("getint"), new ArrayList<>(), curBB);
        new Store(rVal, lValPointer, curBB);
    }

    private void visitExpStmt(Ast.ExpStmt expStmt) {
        if (expStmt.getExp() == null) {
            return;
        }
        visitExp(expStmt.getExp(), false);
    }

    private void visitIfStmt(Ast.IfStmt ifStmt) {
        BasicBlock trueBB = new BasicBlock("true" + (++m.basicBlockIdx), curFunc);
        BasicBlock nextBB = new BasicBlock("next" + (++m.basicBlockIdx), curFunc);
        BasicBlock falseBB = nextBB;
        if (ifStmt.getFalseStmt() != null) {
            falseBB = new BasicBlock("false" + (++m.basicBlockIdx), curFunc);
        }
        visitLOrExp(ifStmt.getCond(), trueBB, falseBB);
        curBB = trueBB;
        visitStmt(ifStmt.getTrueStmt());
        new Br(nextBB, curBB);
        if (ifStmt.getFalseStmt() != null) {
            curBB = falseBB;
            visitStmt(ifStmt.getFalseStmt());
            new Br(nextBB, curBB);
        }
        curBB = nextBB;
    }

    private final String breakBackpatchSign = "BREAK_BACKPATCH";
    private final String continueBackpatchSign = "CONTINUE_BACKPATCH";

    private void visitWhile(Ast.WhileStmt stmt) {
        BasicBlock enterBB = curBB;
        BasicBlock condBB = new BasicBlock("cond" + (++m.basicBlockIdx), curFunc);
        BasicBlock whileBB = new BasicBlock("while body" + (++m.basicBlockIdx), curFunc);
        BasicBlock nxtBB = new BasicBlock("after while" + (++m.basicBlockIdx), curFunc);
        new Br(condBB, enterBB);
        curBB = condBB;
        visitLOrExp(stmt.getCond(), whileBB, nxtBB);
        curBB = whileBB;
        visitStmt(stmt.getBody());
        if (curBB.getList().isEmpty() || !(curBB.getList().getLast() != null && curBB.getList().getLast().getValue().tag.isTerminator())) {
            ValueCopy valueMap = new ValueCopy(new HashMap<>());
            for (IList.INode<Instruction, BasicBlock> iNode : condBB.getList()) {
                ArrayList<Phi> phis = new ArrayList<>();
                ArrayList<Move> moves = new ArrayList<>();
                Value instrCopy = valueMap.getInstrCopy(iNode.getValue(), curBB, phis, moves);
                assert phis.isEmpty() && moves.isEmpty();
                valueMap.putValue(iNode.getValue(), instrCopy);
            }
        }
        backpatch(breakBackpatchSign, whileBB, nxtBB, nxtBB);
        backpatch(continueBackpatchSign, whileBB, nxtBB, condBB);
        curBB = nxtBB;
    }

    private void backpatch(String sign, BasicBlock start, BasicBlock end, BasicBlock target) {
        HashSet<BasicBlock> vis = new HashSet<>();
        vis.add(start);
        vis.add(end);
        Queue<BasicBlock> q = new LinkedList<>();
        q.add(start);
        while (!q.isEmpty()) {
            BasicBlock k = q.poll();
            for (IList.INode<Instruction, BasicBlock> iNode : k.getList()) {
                Instruction instr = iNode.getValue();
                if (instr.tag == InstrTag.Br) {
                    Br br = (Br) instr;
                    if (br.getOperandNum() == 1) {
                        BasicBlock brBlock;
                        brBlock = (BasicBlock) br.getOperand(0);
                        if (brBlock.getName().equals(sign)) {
                            if (sign.equals(continueBackpatchSign)) {
                                ValueCopy valueMap = new ValueCopy(new HashMap<>());
                                for (IList.INode<Instruction, BasicBlock> copyINode : target.getList()) {
                                    ArrayList<Phi> phis = new ArrayList<>();
                                    ArrayList<Move> moves = new ArrayList<>();
                                    Value instrCopy = valueMap.getInstrCopy(copyINode.getValue(), k, phis, moves);
                                    assert phis.isEmpty() && moves.isEmpty();
                                    valueMap.putValue(copyINode.getValue(), instrCopy);
                                }
                                br.node.removeSelf();
                            } else {
                                br.setOperand(0, target);
                            }
                            brBlock.node.removeSelf();
                        } else if (!vis.contains(brBlock)) {
                            q.add(brBlock);
                            vis.add(brBlock);
                        }
                    } else {
                        BasicBlock trueBlock = (BasicBlock) br.getOperand(1);
                        if (trueBlock.getName().equals(sign)) {
                            br.setOperand(1, target);
                            trueBlock.node.removeSelf();
                        } else if (!vis.contains(trueBlock)) {
                            q.add(trueBlock);
                            vis.add(trueBlock);
                        }
                        BasicBlock falseBlock = (BasicBlock) br.getOperand(2);
                        if (falseBlock.getName().equals(sign)) {
                            br.setOperand(2, target);
                            falseBlock.node.removeSelf();
                        } else if (!vis.contains(falseBlock)) {
                            q.add(falseBlock);
                            vis.add(falseBlock);
                        }
                    }
                }
            }
        }
    }

    private void visitBreak() {
        new Br(new BasicBlock(breakBackpatchSign, curFunc), curBB);
    }

    private void visitContinue() {
        new Br(new BasicBlock(continueBackpatchSign, curFunc), curBB);
    }

    private void visitReturn(Ast.ReturnStmt stmt) {
        if (stmt.getReturnVal() != null) {
            if (((FunctionType) curFunc.getType()).getReturnType().isVoidType()) {
                Logger.logError(stmt.getReturnToken().getLine(), ErrorHandler.Error.ErrorType.ReturnUnmatchedExp);
            }
            Value returnExp = visitExp(stmt.getReturnVal(), false);
            new Ret(returnExp, curBB);
        } else {
            new Ret(null, curBB);
        }
    }

    private void visitLOrExp(Ast.LOrExp exp, BasicBlock trueBB, BasicBlock falseBB) {
        for (int i = 0; i < exp.getExps().size() - 1; ++i) {
            BasicBlock nxtCondBlock = new BasicBlock("LOrNext" + (++m.basicBlockIdx), curFunc);
            visitLAndExp(exp.getExps().get(i), trueBB, nxtCondBlock);
            curBB = nxtCondBlock;
        }
        visitLAndExp(exp.getExps().get(exp.getExps().size() - 1), trueBB, falseBB);
    }

    private void visitLAndExp(Ast.LAndExp exp, BasicBlock trueBB, BasicBlock falseBB) {
        for (int i = 0; i < exp.getExps().size(); ++i) {
            BasicBlock nxtCondBlock = new BasicBlock("LAndNext" + (++m.basicBlockIdx), curFunc);
            Value condExp = visitEqExp(exp.getExps().get(i));
            buildCondBr(condExp, nxtCondBlock, falseBB);
            curBB = nxtCondBlock;
        }
        new Br(trueBB, curBB);
    }

    private Value visitExp(Ast.Exp exp, boolean allConst) {
        return visitAddExp(exp.getExpItem(), allConst);
    }

    private Value visitUnaryExp(Ast.UnaryExp exp, boolean allConst) {
        if (allConst) {
            ConstantInt val = (ConstantInt) visitPrimaryExp(exp.getPrimaryExp(), true);
            for (int i = exp.getUnaryOps().size() - 1; i >= 0; --i) {
                Ast.UnaryOp op = exp.getUnaryOps().get(i);
                if (op.getOp().getType() == Lexer.Token.TokenType.Minu) {
                    val = val.neg();
                }
            }
            return val;
        } else {
            Value val = visitPrimaryExp(exp.getPrimaryExp(), false);
            for (int i = exp.getUnaryOps().size() - 1; i >= 0; --i) {
                Ast.UnaryOp op = exp.getUnaryOps().get(i);
                if (op.getOp().getType() == Lexer.Token.TokenType.Minu) {
                    val = buildBinaryInst(InstrTag.Sub, CONST0, val);
                } else if (op.getOp().getType() == Lexer.Token.TokenType.Not) {
                    int cnt = 1;
                    while (i > 0 && exp.getUnaryOps().get(i - 1).getOp().getType() == Lexer.Token.TokenType.Not) {
                        --i;
                        ++cnt;
                    }
                    if (cnt % 2 == 0) {
                        val = buildBinaryInst(InstrTag.Ne, CONST0, val);
                    } else {
                        val = buildBinaryInst(InstrTag.Eq, CONST0, val);
                    }
                }
            }
            return val;
        }
    }

    private Value visitEqExp(Ast.EqExp exp) {
        Value cur = visitRelExp(exp.getLhs());
        for (int i = 0; i < exp.getOps().size(); ++i) {
            Value rhs = visitRelExp(exp.getRhs().get(i));
            cur = buildBinaryInst(exp.getOps().get(i).getType().getTag(), cur, rhs);
        }
        return cur;
    }

    private Value visitRelExp(Ast.RelExp exp) {
        Value cur = visitAddExp(exp.getLhs(), false);
        for (int i = 0; i < exp.getOps().size(); ++i) {
            Value rhs = visitAddExp(exp.getRhs().get(i), false);
            cur = buildBinaryInst(exp.getOps().get(i).getType().getTag(), cur, rhs);
        }
        return cur;
    }

    private Value visitAddExp(Ast.AddExp exp, boolean allConst) {
        if (allConst) {
            Constant cur = (Constant) visitMulExp(exp.getLhs(), true);
            for (int i = 0; i < exp.getOps().size(); ++i) {
                Constant rhs = (Constant) visitMulExp(exp.getRhs().get(i), true);
                cur = BinaryInst.calcBinary(exp.getOps().get(i).getType().getTag(), cur, rhs);
            }
            return cur;
        } else {
            Value cur = visitMulExp(exp.getLhs(), false);
            for (int i = 0; i < exp.getOps().size(); ++i) {
                Value rhs = visitMulExp(exp.getRhs().get(i), false);
                cur = buildBinaryInst(exp.getOps().get(i).getType().getTag(), cur, rhs);
            }
            return cur;
        }
    }

    private Value visitMulExp(Ast.MulExp exp, boolean allConst) {
        if (allConst) {
            Constant cur = (Constant) visitUnaryExp(exp.getLhs(), true);
            for (int i = 0; i < exp.getOps().size(); ++i) {
                Constant rhs = (Constant) visitUnaryExp(exp.getRhs().get(i), true);
                cur = BinaryInst.calcBinary(exp.getOps().get(i).getType().getTag(), cur, rhs);
            }
            return cur;
        } else {
            Value cur = visitUnaryExp(exp.getLhs(), false);
            for (int i = 0; i < exp.getOps().size(); ++i) {
                Value rhs = visitUnaryExp(exp.getRhs().get(i), false);
                cur = buildBinaryInst(exp.getOps().get(i).getType().getTag(), cur, rhs);
            }
            return cur;
        }
    }
}
