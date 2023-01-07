package frontend;

import utils.Logger;

import java.util.ArrayList;
import static frontend.Lexer.Token.*;

public class Parser {
    private final Lexer.TokenStream tokens;
    private final Ast ast;

    public Parser(Lexer.TokenStream tokens) {
        this.tokens = tokens;
        this.ast = new Ast();
        parseCompUnit();
    }

    public Ast getRes() {
        return ast;
    }

    private void parseCompUnit() {
        while (tokens.hasNext()) {
            if (tokens.haveAhead(2) && tokens.ahead(2).getType() == TokenType.LParent) {
                ast.getCompUnits().add(parseFuncDef());
            } else {
                ast.getCompUnits().add(parseDecl());
            }
        }
    }

    private Ast.Decl parseDecl() {
        boolean isConstant = false;
        if (tokens.get().getType() == TokenType.ConstTK) {
            isConstant = true;
            tokens.getAndConsume();
        }
        tokens.getAndConsume();
        ArrayList<Ast.Def> defs = new ArrayList<>();
        defs.add(parseDef(isConstant));
        while (tokens.haveCur() && tokens.get().getType() == TokenType.Comma) {
            tokens.getAndConsume();
            defs.add(parseDef(isConstant));
        }
        if (tokens.get().getType() != TokenType.Semicn) {
            Logger.logError(tokens.getPre().getLine(), ErrorHandler.Error.ErrorType.LackSemicolon);
        } else {
            tokens.getAndConsume();
        }
        return new Ast.Decl(isConstant, defs);
    }

    private Ast.Def parseDef(boolean isConstant) {
        Lexer.Token ident = tokens.getAndConsume();
        ArrayList<Ast.Exp> exps = new ArrayList<>();
        while (tokens.haveCur() && tokens.get().getType() == TokenType.LBrack) {
            tokens.getAndConsume();
            exps.add(parseExp());
            if (tokens.get().getType() != TokenType.RBrack) {
                Logger.logError(tokens.getPre().getLine(), ErrorHandler.Error.ErrorType.LackRBrack);
            } else {
                tokens.getAndConsume();
            }
        }
        Ast.InitVal initVal = null;
        if (tokens.haveCur() && tokens.get().getType() == TokenType.Assign) {
            tokens.getAndConsume();
            initVal = parseInitVal(isConstant);
        }
        return new Ast.Def(ident, exps, initVal);
    }

    private Ast.InitVal parseInitVal(boolean isConstant) {
        Ast.InitVal initVal;
        if (tokens.get().getType() == TokenType.LBrace) {
            initVal = new Ast.InitVal(parseInitArrayVal(isConstant));
        } else {
            initVal = new Ast.InitVal(parseExp());
        }
        return initVal;
    }

    private Ast.InitArrayVal parseInitArrayVal(boolean isConstant) {
        ArrayList<Ast.InitVal> initVals = new ArrayList<>();
        tokens.getAndConsume();
        if (tokens.haveCur() && tokens.get().getType() != TokenType.RBrace) {
            initVals.add(parseInitVal(isConstant));
            while (tokens.haveCur() && tokens.get().getType() == TokenType.Comma) {
                tokens.getAndConsume();
                initVals.add(parseInitVal(isConstant));
            }
        }
        tokens.getAndConsume();
        return new Ast.InitArrayVal(initVals);
    }

    private boolean isExp(Lexer.Token.TokenType tokenType) {
        return tokenType == TokenType.MainTK ||
                tokenType == TokenType.Idenfr ||
                tokenType == TokenType.IntCon ||
                tokenType == TokenType.StrCon ||
                tokenType == TokenType.Not ||
                tokenType == TokenType.Minu ||
                tokenType == TokenType.LParent ||
                tokenType == TokenType.Plus ||
                tokenType == TokenType.GetIntTK;
    }

    private Ast.FuncDef parseFuncDef() {
        Lexer.Token funcType = tokens.getAndConsume();
        Lexer.Token ident = tokens.getAndConsume();
        tokens.getAndConsume();
        ArrayList<Ast.FuncFParam> funcFParams = new ArrayList<>();
        if (tokens.haveCur() && tokens.get().getType() == TokenType.IntTK) {
            funcFParams.add(parseFuncFParam());
            while (tokens.haveCur() && tokens.get().getType() == TokenType.Comma) {
                tokens.getAndConsume();
                funcFParams.add(parseFuncFParam());
            }
        }
        if (tokens.get().getType() != TokenType.RParent) {
            Logger.logError(tokens.getPre().getLine(), ErrorHandler.Error.ErrorType.LackRParent);
        } else {
            tokens.getAndConsume();
        }
        Ast.Block block = parseBlock(false);
        return new Ast.FuncDef(funcType, ident, funcFParams, block);
    }

    private Ast.FuncFParam parseFuncFParam() {
        tokens.getAndConsume();
        Lexer.Token ident = tokens.getAndConsume();
        ArrayList<Ast.Exp> exps = new ArrayList<>();
        if (tokens.haveCur() && tokens.get().getType() == TokenType.LBrack) {
            tokens.getAndConsume();
            tokens.getAndConsume();
            while (tokens.haveCur() && tokens.get().getType() == TokenType.LBrack) {
                tokens.getAndConsume();
                exps.add(parseExp());
                if (tokens.get().getType() != TokenType.RBrack) {
                    Logger.logError(tokens.getPre().getLine(), ErrorHandler.Error.ErrorType.LackRBrack);
                } else {
                    tokens.getAndConsume();
                }
            }
            return new Ast.FuncFParam(ident, true, exps);
        }
        return new Ast.FuncFParam(ident, false, exps);
    }

    private Ast.Block parseBlock(boolean inLoop) {
        ArrayList<Ast.BlockItem> blockItems = new ArrayList<>();
        tokens.getAndConsume();
        while (tokens.haveCur() && tokens.get().getType() != TokenType.RBrace) {
            blockItems.add(parseBlockItem(inLoop));
        }
        Lexer.Token rBrace = tokens.getAndConsume();
        return new Ast.Block(blockItems, rBrace);
    }

    private Ast.BlockItem parseBlockItem(boolean inLoop) {
        TokenType tokenType = tokens.get().getType();
        Ast.BlockItem blockItem;
        if (tokenType == TokenType.ConstTK || tokenType == TokenType.IntTK) {
            blockItem = new Ast.BlockItem(parseDecl());
        } else {
            blockItem = new Ast.BlockItem(parseStmt(inLoop));
        }
        return blockItem;
    }

    private Ast.LVal calcLValFromExp(Ast.Exp exp) {
        Ast.AddExp addExp = exp.getExpItem();
        Ast.MulExp mulExp = addExp.getLhs();
        Ast.UnaryExp unaryExp = mulExp.getLhs();
        Ast.PrimaryExp primaryExp = unaryExp.getPrimaryExp();
        return (Ast.LVal) primaryExp.getPrimaryExpItem();
    }

    private Ast.Stmt parseStmt(boolean inLoop) {
        TokenType tokenType = tokens.get().getType();
        Ast.Stmt.StmtItem res;
        if (tokenType == TokenType.IfTK) {
            res = parseIfStmt(inLoop);
        } else if (tokenType == TokenType.WhileTK) {
            res = parseWhileStmt();
        } else if (tokenType == TokenType.BreakTK) {
            res = parseBreakStmt(inLoop);
        } else if (tokenType == TokenType.ContinueTK) {
            res = parseContinueStmt(inLoop);
        } else if (tokenType == TokenType.LBrace) {
            res = parseBlock(inLoop);
        } else if (tokenType == TokenType.ReturnTK) {
            res = parseReturnStmt();
        } else if (tokenType == TokenType.Semicn) {
            tokens.getAndConsume();
            res = new Ast.ExpStmt(null);
        } else if (tokenType == TokenType.PrintfTK) {
            Lexer.Token printfToken = tokens.getAndConsume();
            tokens.getAndConsume();
            Ast.FormatString string = parseFormatString();
            ArrayList<Ast.Exp> args = new ArrayList<>();
            while (tokens.haveCur() && tokens.get().getType() == TokenType.Comma) {
                tokens.getAndConsume();
                args.add(parseExp());
            }
            if (tokens.get().getType() != TokenType.RParent) {
                Logger.logError(tokens.getPre().getLine(), ErrorHandler.Error.ErrorType.LackRParent);
            } else {
                tokens.getAndConsume();
            }
            if (tokens.get().getType() != TokenType.Semicn) {
                Logger.logError(tokens.getPre().getLine(), ErrorHandler.Error.ErrorType.LackSemicolon);
            } else {
                tokens.getAndConsume();
            }
            res = new Ast.PrintfStmt(printfToken, string, args);
        } else if (isExp(tokenType)) {
            Ast.Exp exp = parseExp();
            if (tokens.haveCur() && tokens.get().getType() != TokenType.Assign) {
                tokens.getAndConsume();
                res = new Ast.ExpStmt(exp);
            } else {
                Ast.LVal lVal = calcLValFromExp(exp);
                tokens.getAndConsume();
                if (tokens.get().getType() == TokenType.GetIntTK) {
                    tokens.getAndConsume();
                    tokens.getAndConsume();
                    if (tokens.get().getType() != TokenType.RParent) {
                        Logger.logError(tokens.getPre().getLine(), ErrorHandler.Error.ErrorType.LackRParent);
                    } else {
                        tokens.getAndConsume();
                    }
                    if (tokens.get().getType() != TokenType.Semicn) {
                        Logger.logError(tokens.getPre().getLine(), ErrorHandler.Error.ErrorType.LackSemicolon);
                    } else {
                        tokens.getAndConsume();
                    }
                    res = new Ast.GetIntStmt(lVal);
                } else {
                    Ast.Exp rVal = parseExp();
                    if (tokens.get().getType() != TokenType.Semicn) {
                        Logger.logError(tokens.getPre().getLine(), ErrorHandler.Error.ErrorType.LackSemicolon);
                    } else {
                        tokens.getAndConsume();
                    }
                    res = new Ast.AssignStmt(lVal, rVal);
                }
            }
        } else {
            Ast.Exp exp = parseExp();
            if (tokens.get().getType() != TokenType.Semicn) {
                Logger.logError(tokens.getPre().getLine(), ErrorHandler.Error.ErrorType.LackSemicolon);
            } else {
                tokens.getAndConsume();
            }
            res = new Ast.ExpStmt(exp);
        }
        return new Ast.Stmt(res);
    }

    private Ast.IfStmt parseIfStmt(boolean inLoop) {
        tokens.getAndConsume();
        tokens.getAndConsume();
        Ast.LOrExp cond = parseLOrExp();
        if (tokens.get().getType() != TokenType.RParent) {
            Logger.logError(tokens.getPre().getLine(), ErrorHandler.Error.ErrorType.LackRParent);
        } else {
            tokens.getAndConsume();
        }
        Ast.Stmt trueStmt = parseStmt(inLoop);
        Ast.Stmt falseStmt = null;
        if (tokens.haveCur() && tokens.get().getType() == TokenType.ElseTK) {
            tokens.getAndConsume();
            falseStmt = parseStmt(inLoop);
        }
        return new Ast.IfStmt(cond, trueStmt, falseStmt);
    }

    private Ast.WhileStmt parseWhileStmt() {
        tokens.getAndConsume();
        tokens.getAndConsume();
        Ast.LOrExp cond = parseLOrExp();
        if (tokens.get().getType() != TokenType.RParent) {
            Logger.logError(tokens.getPre().getLine(), ErrorHandler.Error.ErrorType.LackRParent);
        } else {
            tokens.getAndConsume();
        }
        Ast.Stmt body = parseStmt(true);
        return new Ast.WhileStmt(cond, body);
    }

    private Ast.BreakStmt parseBreakStmt(boolean inLoop) {
        Lexer.Token breakToken = tokens.getAndConsume();
        if (!inLoop) {
            Logger.logError(breakToken.getLine(), ErrorHandler.Error.ErrorType.BreakContinueOutOfLoop);
        }
        if (tokens.get().getType() != TokenType.Semicn) {
            Logger.logError(tokens.getPre().getLine(), ErrorHandler.Error.ErrorType.LackSemicolon);
        } else {
            tokens.getAndConsume();
        }
        return new Ast.BreakStmt();
    }

    private Ast.ContinueStmt parseContinueStmt(boolean inLoop) {
        Lexer.Token continueToken = tokens.getAndConsume();
        if (!inLoop) {
            Logger.logError(continueToken.getLine(), ErrorHandler.Error.ErrorType.BreakContinueOutOfLoop);
        }
        if (tokens.get().getType() != TokenType.Semicn) {
            Logger.logError(tokens.getPre().getLine(), ErrorHandler.Error.ErrorType.LackSemicolon);
        } else {
            tokens.getAndConsume();
        }
        return new Ast.ContinueStmt();
    }

    private Ast.ReturnStmt parseReturnStmt() {
        Lexer.Token returnToken = tokens.getAndConsume();
        Ast.Exp exp = null;
        if (isExp(tokens.get().getType())) {
            exp = parseExp();
        }
        if (tokens.get().getType() != TokenType.Semicn) {
            Logger.logError(tokens.getPre().getLine(), ErrorHandler.Error.ErrorType.LackSemicolon);
        } else {
            tokens.getAndConsume();
        }
        return new Ast.ReturnStmt(exp, returnToken);
    }

    private Ast.LOrExp parseLOrExp() {
        ArrayList<Ast.LAndExp> exps = new ArrayList<>();
        exps.add(parseLAndExp());
        while (tokens.haveCur() && tokens.get().getType() == TokenType.Or) {
            tokens.getAndConsume();
            exps.add(parseLAndExp());
        }
        return new Ast.LOrExp(exps);
    }

    private Ast.LAndExp parseLAndExp() {
        ArrayList<Ast.EqExp> exps = new ArrayList<>();
        exps.add(parseEqExp());
        while (tokens.haveCur() && tokens.get().getType() == TokenType.And) {
            tokens.getAndConsume();
            exps.add(parseEqExp());
        }
        return new Ast.LAndExp(exps);
    }

    private Ast.Exp parseExp() {
        return new Ast.Exp(parseAddExp());
    }

    private Ast.EqExp parseEqExp() {
        Ast.RelExp lhs = parseRelExp();
        ArrayList<Lexer.Token> ops = new ArrayList<>();
        ArrayList<Ast.RelExp> rhs = new ArrayList<>();
        while (tokens.haveCur() && Ast.EqExp.isSignal(tokens.get())) {
            ops.add(tokens.getAndConsume());
            rhs.add(parseRelExp());
        }
        return new Ast.EqExp(lhs, ops, rhs);
    }

    private Ast.RelExp parseRelExp() {
        Ast.AddExp lhs = parseAddExp();
        ArrayList<Lexer.Token> ops = new ArrayList<>();
        ArrayList<Ast.AddExp> rhs = new ArrayList<>();
        while (tokens.haveCur() && Ast.RelExp.isSignal(tokens.get())) {
            ops.add(tokens.getAndConsume());
            rhs.add(parseAddExp());
        }
        return new Ast.RelExp(lhs, ops, rhs);
    }

    private Ast.AddExp parseAddExp() {
        Ast.MulExp lhs = parseMulExp();
        ArrayList<Lexer.Token> ops = new ArrayList<>();
        ArrayList<Ast.MulExp> rhs = new ArrayList<>();
        while (tokens.haveCur() && Ast.AddExp.isSignal(tokens.get())) {
            ops.add(tokens.getAndConsume());
            rhs.add(parseMulExp());
        }
        return new Ast.AddExp(lhs, ops, rhs);
    }

    private Ast.MulExp parseMulExp() {
        Ast.UnaryExp lhs = parseUnaryExp();
        ArrayList<Lexer.Token> ops = new ArrayList<>();
        ArrayList<Ast.UnaryExp> rhs = new ArrayList<>();
        while (tokens.haveCur() && Ast.MulExp.isSignal(tokens.get())) {
            ops.add(tokens.getAndConsume());
            rhs.add(parseUnaryExp());
        }
        return new Ast.MulExp(lhs, ops, rhs);
    }

    private Ast.UnaryExp parseUnaryExp() {
        ArrayList<Ast.UnaryOp> unaryOps = new ArrayList<>();
        while (tokens.haveCur() && tokens.get().getType() == TokenType.Plus || tokens.get().getType() == TokenType.Minu || tokens.get().getType() == TokenType.Not) {
            unaryOps.add(new Ast.UnaryOp(tokens.getAndConsume()));
        }
        Ast.PrimaryExp primaryExp = parsePrimaryExp();
        return new Ast.UnaryExp(unaryOps, primaryExp);
    }

    private Ast.PrimaryExp parsePrimaryExp() {
        if (tokens.get().getType() == TokenType.IntCon) {
            return new Ast.PrimaryExp(parseNumber());
        } else if (tokens.get().getType() == TokenType.LParent) {
            tokens.getAndConsume();
            Ast.Exp exp = parseExp();
            tokens.getAndConsume();
            return new Ast.PrimaryExp(exp);
        } else if (tokens.ahead(1).getType() == TokenType.LParent) {
            return new Ast.PrimaryExp(parseCallee());
        } else {
            return new Ast.PrimaryExp(parseLVal());
        }
    }

    private Ast.Callee parseCallee() {
        Lexer.Token ident = tokens.getAndConsume();
        tokens.getAndConsume();
        ArrayList<Ast.FuncRParam> funcRParams = new ArrayList<>();
        if (tokens.haveCur() && isExp(tokens.get().getType())) {
            funcRParams.add(parseFuncRParam());
            while (tokens.haveCur() && tokens.get().getType() == TokenType.Comma) {
                tokens.getAndConsume();
                funcRParams.add(parseFuncRParam());
            }
        }
        if (tokens.get().getType() != TokenType.RParent) {
            Logger.logError(tokens.getPre().getLine(), ErrorHandler.Error.ErrorType.LackRParent);
        } else {
            tokens.getAndConsume();
        }
        return new Ast.Callee(ident, funcRParams);
    }

    private Ast.FuncRParam parseFuncRParam() {
        Ast.FuncRParam funcRParam;
        if (tokens.get().getType() == TokenType.StrCon) {
            funcRParam = new Ast.FuncRParam(parseFormatString());
        } else {
            funcRParam = new Ast.FuncRParam(parseExp());
        }
        return funcRParam;
    }

    private Ast.LVal parseLVal() {
        Lexer.Token ident = tokens.getAndConsume();
        ArrayList<Ast.Exp> exps = new ArrayList<>();
        while (tokens.haveCur() && tokens.get().getType() == TokenType.LBrack) {
            tokens.getAndConsume();
            exps.add(parseExp());
            if (tokens.get().getType() != TokenType.RBrack) {
                Logger.logError(tokens.getPre().getLine(), ErrorHandler.Error.ErrorType.LackRBrack);
            } else {
                tokens.getAndConsume();
            }
        }
        return new Ast.LVal(ident, exps);
    }

    private Ast.FormatString parseFormatString() {
        return new Ast.FormatString(tokens.getAndConsume());
    }

    private Ast.Number parseNumber() {
        return new Ast.Number(tokens.getAndConsume());
    }
}
