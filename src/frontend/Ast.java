package frontend;

import java.util.ArrayList;
import frontend.Lexer.Token.TokenType;

public class Ast {
    private final ArrayList<CompUnit> compUnits;
    private final ArrayList<String> res = new ArrayList<>();

    public Ast() {
        this.compUnits = new ArrayList<>();
    }

    public ArrayList<String> getRes() {
        return res;
    }

    public void addLine(String s) {
        res.add(s);
    }

    public void addLine(Lexer.Token token) {
        this.addLine(token.toString());
    }

    public void addLine(TokenType tokenType) {
        this.addLine(tokenType.getTokenFromType());
    }

    public void print() {
        for (CompUnit compUnit : this.compUnits) {
            compUnit.print(this);
        }
        this.addLine("<CompUnit>");
    }

    public ArrayList<CompUnit> getCompUnits() {
        return compUnits;
    }

    // CompUnit -> Decl | FuncDef
    public interface CompUnit {
        void print(Ast ast);
    }

    // Decl -> 'const'? 'int' Def (',' Def)* ';'
    public static class Decl implements CompUnit, BlockItem.BlockItemItem {
        private final boolean isConstant;
        private final ArrayList<Def> defs;

        public Decl(boolean isConstant, ArrayList<Def> defs) {
            this.isConstant = isConstant;
            this.defs = new ArrayList<>(defs);
        }

        public boolean isConstant() {
            return isConstant;
        }

        public ArrayList<Def> getDefs() {
            return defs;
        }

        public void print(Ast ast) {
            if (this.isConstant) {
                ast.addLine(Lexer.Token.TokenType.ConstTK);
            }
            ast.addLine(Lexer.Token.TokenType.IntTK);
            this.defs.get(0).print(ast, isConstant);
            for (int i = 1; i < this.defs.size(); ++i) {
                ast.addLine(TokenType.Comma);
                this.defs.get(i).print(ast, isConstant);
            }
            ast.addLine(Lexer.Token.TokenType.Semicn);
            ast.addLine(isConstant ? "<ConstDecl>" : "<VarDecl>");
        }
    }

    // Def -> Ident ('[' Exp ']')* '=' InitVal
    public static class Def {
        private final Lexer.Token ident;
        private final ArrayList<Exp> indexes;
        private final InitVal initVal;

        public Def(Lexer.Token ident, ArrayList<Exp> indexes, InitVal initVal) {
            this.ident = ident;
            this.indexes = new ArrayList<>(indexes);
            this.initVal = initVal;
        }

        public Lexer.Token getIdent() {
            return ident;
        }

        public ArrayList<Exp> getIndexes() {
            return indexes;
        }

        public InitVal getInitVal() {
            return initVal;
        }

        public void print(Ast ast, boolean isConstant) {
            ast.addLine(this.ident);
            for (Exp len : this.indexes) {
                ast.addLine(TokenType.LBrack);
                len.print(ast, true);
                ast.addLine(TokenType.RBrack);
            }
            if (this.initVal != null) {
                ast.addLine(TokenType.Assign);
                this.initVal.print(ast, isConstant);
            }
            ast.addLine(isConstant ? "<ConstDef>" : "<VarDef>");
        }
    }

    // InitVal -> Exp | InitArrayVal
    public static class InitVal {
        public interface InitValItem {
            void print(Ast ast, boolean isConstant);
        }

        private final InitValItem initValItem;

        public InitVal(InitValItem initValItem) {
            this.initValItem = initValItem;
        }

        public InitValItem getInitValItem() {
            return initValItem;
        }

        public void print(Ast ast, boolean isConstant) {
            this.initValItem.print(ast, isConstant);
            ast.addLine(isConstant ? "<ConstInitVal>" : "<InitVal>");
        }
    }

    // InitArrayVal -> '{' (InitVal (',' InitVal)*)? '}'
    public static class InitArrayVal implements InitVal.InitValItem {
        private final ArrayList<InitVal> initVals;

        public InitArrayVal(ArrayList<InitVal> initVals) {
            this.initVals = new ArrayList<>(initVals);
        }

        public ArrayList<InitVal> getInitVals() {
            return initVals;
        }

        public void print(Ast ast, boolean isConstant) {
            ast.addLine(TokenType.LBrace);
            if (!this.initVals.isEmpty()) {
                this.initVals.get(0).print(ast, isConstant);
                for (int i = 1; i < this.initVals.size(); ++i) {
                    ast.addLine(TokenType.Comma);
                    this.initVals.get(i).print(ast, isConstant);
                }
            }
            ast.addLine(TokenType.RBrace);
        }
    }

    // FuncDef -> ('void' | 'int') Ident '(' FuncFParams? ')'Block
    public static class FuncDef implements CompUnit {
        private final Lexer.Token returnType;
        private final Lexer.Token ident;
        private final ArrayList<FuncFParam> funcFParams;
        private final Block block;

        public FuncDef(Lexer.Token returnType, Lexer.Token ident, ArrayList<FuncFParam> funcFParams, Block block) {
            this.returnType = returnType;
            this.ident = ident;
            this.funcFParams = funcFParams;
            this.block = block;
        }

        public Lexer.Token getReturnType() {
            return returnType;
        }

        public Lexer.Token getIdent() {
            return ident;
        }

        public ArrayList<FuncFParam> getFuncFParams() {
            return funcFParams;
        }

        public Block getBlock() {
            return block;
        }

        public void print(Ast ast) {
            ast.addLine(this.returnType);
            if (!this.ident.getText().equals("main")) {
                ast.addLine("<FuncType>");
            }
            ast.addLine(this.ident);
            ast.addLine(Lexer.Token.TokenType.LParent);
            if (!this.funcFParams.isEmpty()) {
                this.funcFParams.get(0).print(ast);
                for (int i = 1; i < this.funcFParams.size(); ++i) {
                    ast.addLine(TokenType.Comma);
                    this.funcFParams.get(i).print(ast);
                }
                ast.addLine("<FuncFParams>");
            }
            ast.addLine(Lexer.Token.TokenType.RParent);
            this.block.print(ast);
            ast.addLine(this.ident.getText().equals("main") ? "<MainFuncDef>" : "<FuncDef>");
        }
    }

    // FuncFParam -> BType Ident ('[' ']' ('[' Exp ']')*)?
    public static class FuncFParam {
        private final Lexer.Token ident;
        private final boolean hasExp;
        private final ArrayList<Exp> exps;

        public FuncFParam(Lexer.Token ident, boolean hasExp, ArrayList<Exp> exps) {
            this.ident = ident;
            this.hasExp = hasExp;
            this.exps = exps;
        }

        public Lexer.Token getIdent() {
            return ident;
        }

        public boolean hasExp() {
            return hasExp;
        }

        public ArrayList<Exp> getExps() {
            return exps;
        }

        public void print(Ast ast) {
            ast.addLine(Lexer.Token.TokenType.IntTK);
            ast.addLine(this.ident);
            if (this.hasExp) {
                ast.addLine(Lexer.Token.TokenType.LBrack);
                ast.addLine(Lexer.Token.TokenType.RBrack);
                for (Exp exp : this.exps) {
                    ast.addLine(Lexer.Token.TokenType.LBrack);
                    exp.print(ast, true);
                    ast.addLine(Lexer.Token.TokenType.RBrack);
                }
            }
            ast.addLine("<FuncFParam>");
        }
    }

    // Block -> (BlockItem)*
    public static class Block implements Stmt.StmtItem {
        private final ArrayList<BlockItem> blockItems;
        private final Lexer.Token rBraceToken;

        public Block(ArrayList<BlockItem> blockItems, Lexer.Token rBraceToken) {
            this.blockItems = blockItems;
            this.rBraceToken = rBraceToken;
        }

        public ArrayList<BlockItem> getBlockItems() {
            return blockItems;
        }

        public void print(Ast ast) {
            ast.addLine(Lexer.Token.TokenType.LBrace);
            for (BlockItem blockItem : this.blockItems) {
                blockItem.print(ast);
            }
            ast.addLine(Lexer.Token.TokenType.RBrace);
            ast.addLine("<Block>");
        }

        public Lexer.Token getRBraceToken() {
            return rBraceToken;
        }
    }

    // BlockItem -> Decl | Stmt
    public static class BlockItem {
        public interface BlockItemItem {
            void print(Ast ast);
        }

        private final BlockItemItem blockItemItem;

        public BlockItem(BlockItemItem blockItemItem) {
            this.blockItemItem = blockItemItem;
        }

        public BlockItemItem getBlockItemItem() {
            return blockItemItem;
        }

        public void print(Ast ast) {
            this.blockItemItem.print(ast);
        }
    }

    // Stmt -> AssignStmt | ExpStmt | Block | IfStmt | WhileStmt | BreakStmt | ContinueStmt | ReturnStmt
    public static class Stmt implements BlockItem.BlockItemItem {
        public interface StmtItem  {
            void print(Ast ast);
        }

        private final StmtItem stmtItem;

        public Stmt(StmtItem stmtItem) {
            this.stmtItem = stmtItem;
        }

        public StmtItem getStmtItem() {
            return stmtItem;
        }

        public void print(Ast ast) {
            this.stmtItem.print(ast);
            ast.addLine("<Stmt>");
        }
    }

    // AssignStmt -> LVal '=' Exp ';'
    public static class AssignStmt implements Stmt.StmtItem {
        private final LVal lVal;
        private final Exp exp;

        public AssignStmt(LVal lVal, Exp exp) {
            this.lVal = lVal;
            this.exp = exp;
        }

        public LVal getLVal() {
            return lVal;
        }

        public Exp getExp() {
            return exp;
        }

        public void print(Ast ast) {
            this.lVal.print(ast, false);
            ast.addLine(Lexer.Token.TokenType.Assign);
            this.exp.print(ast, false);
            ast.addLine(Lexer.Token.TokenType.Semicn);
        }
    }

    // ExpStmt -> (Exp)? ';'
    public static class ExpStmt implements Stmt.StmtItem {
        private final Exp exp;

        public ExpStmt(Exp exp) {
            this.exp = exp;
        }

        public Exp getExp() {
            return exp;
        }

        public void print(Ast ast) {
            if (this.exp != null) {
                this.exp.print(ast, false);
            }
            ast.addLine(Lexer.Token.TokenType.Semicn);
        }
    }

    // IfStmt -> 'if' '(' Cond ')' Stmt ('else' Stmt)?
    public static class IfStmt implements Stmt.StmtItem {
        private final LOrExp exp;
        private final Stmt trueStmt;
        private final Stmt falseStmt;

        public IfStmt(LOrExp exp, Stmt trueStmt, Stmt falseStmt) {
            this.exp = exp;
            this.trueStmt = trueStmt;
            this.falseStmt = falseStmt;
        }

        public LOrExp getCond() {
            return exp;
        }

        public Stmt getTrueStmt() {
            return trueStmt;
        }

        public Stmt getFalseStmt() {
            return falseStmt;
        }

        public void print(Ast ast) {
            ast.addLine(Lexer.Token.TokenType.IfTK);
            ast.addLine(Lexer.Token.TokenType.LParent);
            this.exp.print(ast);
            ast.addLine("<Cond>");
            ast.addLine(Lexer.Token.TokenType.RParent);
            this.trueStmt.print(ast);
            if (this.falseStmt != null) {
                ast.addLine(Lexer.Token.TokenType.ElseTK);
                this.falseStmt.print(ast);
            }
        }
    }

    public static class WhileStmt implements Stmt.StmtItem {
        private final LOrExp cond;
        private final Stmt body;

        public WhileStmt(LOrExp cond, Stmt body) {
            this.cond = cond;
            this.body = body;
        }

        public LOrExp getCond() {
            return cond;
        }

        public Stmt getBody() {
            return body;
        }

        public void print(Ast ast) {
            ast.addLine(Lexer.Token.TokenType.WhileTK);
            ast.addLine(Lexer.Token.TokenType.LParent);
            this.cond.print(ast);
            ast.addLine("<Cond>");
            ast.addLine(Lexer.Token.TokenType.RParent);
            this.body.print(ast);
        }
    }

    // BreakStmt -> 'break' ';'
    public static class BreakStmt implements Stmt.StmtItem {
        public void print(Ast ast){
            ast.addLine(Lexer.Token.TokenType.BreakTK);
            ast.addLine(Lexer.Token.TokenType.Semicn);
        }
    }

    // ContinueStmt -> 'continue' ';'
    public static class ContinueStmt implements Stmt.StmtItem {
        public void print(Ast ast) {
            ast.addLine(Lexer.Token.TokenType.ContinueTK);
            ast.addLine(Lexer.Token.TokenType.Semicn);
        }
    }

    // ReturnStmt -> 'return (Exp)?' ';'
    public static class ReturnStmt implements Stmt.StmtItem {
        private final Exp returnVal;
        private final Lexer.Token returnToken;

        public ReturnStmt(Exp returnVal, Lexer.Token returnToken) {
            this.returnVal = returnVal;
            this.returnToken = returnToken;
        }

        public Exp getReturnVal() {
            return returnVal;
        }

        public Lexer.Token getReturnToken() {
            return returnToken;
        }

        public void print(Ast ast) {
            ast.addLine(Lexer.Token.TokenType.ReturnTK);
            if (this.returnVal != null) {
                this.returnVal.print(ast, false);
            }
            ast.addLine(Lexer.Token.TokenType.Semicn);
        }
    }

    public static class GetIntStmt implements Stmt.StmtItem {
        private final LVal lVal;

        public GetIntStmt(LVal lVal) {
            this.lVal = lVal;
        }

        public LVal getLVal() {
            return lVal;
        }

        public void print(Ast ast) {
            this.lVal.print(ast, false);
            ast.addLine(Lexer.Token.TokenType.Assign);
            ast.addLine(Lexer.Token.TokenType.GetIntTK);
            ast.addLine(Lexer.Token.TokenType.LParent);
            ast.addLine(Lexer.Token.TokenType.RParent);
            ast.addLine(Lexer.Token.TokenType.Semicn);
        }
    }

    public static class PrintfStmt implements Stmt.StmtItem {
        private final Lexer.Token printfToken;
        private final FormatString formatString;
        private final ArrayList<Exp> exps;

        public PrintfStmt(Lexer.Token printfToken, FormatString formatString, ArrayList<Exp> exps) {
            this.formatString = formatString;
            this.exps = new ArrayList<>(exps);
            this.printfToken = printfToken;
        }

        public FormatString getFormatString() {
            return formatString;
        }

        public ArrayList<Exp> getExps() {
            return exps;
        }

        public Lexer.Token getPrintfToken() {
            return printfToken;
        }

        public void print(Ast ast) {
            ast.addLine(Lexer.Token.TokenType.PrintfTK);
            ast.addLine(Lexer.Token.TokenType.LParent);
            this.formatString.print(ast, false);
            for (Exp exp : this.exps) {
                ast.addLine(Lexer.Token.TokenType.Comma);
                exp.print(ast, false);
            }
            ast.addLine(Lexer.Token.TokenType.RParent);
            ast.addLine(Lexer.Token.TokenType.Semicn);
        }
    }

    // LVal -> Ident ('[' Exp ']')*
    public static class LVal implements PrimaryExp.PrimaryExpItem {
        private final Lexer.Token ident;
        private final ArrayList<Exp> exps;

        public LVal(Lexer.Token ident, ArrayList<Exp> exps) {
            this.ident = ident;
            this.exps = new ArrayList<>(exps);
        }

        public Lexer.Token getIdent() {
            return ident;
        }

        public ArrayList<Exp> getExps() {
            return exps;
        }

        public void print(Ast ast, boolean allConst) {
            ast.addLine(this.ident);
            for (Exp exp : this.exps) {
                ast.addLine(TokenType.LBrack);
                exp.print(ast, false);
                ast.addLine(TokenType.RBrack);
            }
            ast.addLine("<LVal>");
        }
    }

    // PrimaryExp -> ('(' Exp ')') | LVal | Number | Callee
    public static class PrimaryExp {
        public interface PrimaryExpItem {
            void print(Ast ast, boolean allConst);
        }

        private final PrimaryExpItem primaryExpItem;

        public PrimaryExp(PrimaryExpItem primaryExpItem) {
            this.primaryExpItem = primaryExpItem;
        }

        public PrimaryExpItem getPrimaryExpItem() {
            return primaryExpItem;
        }

        public void print(Ast ast, boolean allConst) {
            if (this.primaryExpItem instanceof Exp) {
                ast.addLine(TokenType.LParent);
                this.primaryExpItem.print(ast, false);
                ast.addLine(TokenType.RParent);
                ast.addLine("<PrimaryExp>");
                return;
            }
            this.primaryExpItem.print(ast, allConst);
            if (!(this.primaryExpItem instanceof Callee)) {
                ast.addLine("<PrimaryExp>");
            }
        }
    }

    // FuncRParam -> Exp | FormatString
    public static class FuncRParam {
        public interface FuncRParamItem {
            void print(Ast ast, boolean allConst);
        }

        private final FuncRParamItem funcRParamItem;

        public FuncRParam(FuncRParamItem funcRParamItem) {
            this.funcRParamItem = funcRParamItem;
        }

        public FuncRParamItem getFuncRParamItem() {
            return funcRParamItem;
        }

        public void print(Ast ast, boolean allConst) {
            this.funcRParamItem.print(ast, allConst);
        }
    }

    public static class Callee implements PrimaryExp.PrimaryExpItem {
        private final Lexer.Token ident;
        private final ArrayList<FuncRParam> funcRParams;

        public Callee(Lexer.Token ident, ArrayList<FuncRParam> funcRParams) {
            this.ident = ident;
            this.funcRParams = new ArrayList<>(funcRParams);
        }

        public Lexer.Token getIdent() {
            return ident;
        }

        public ArrayList<FuncRParam> getFuncRParams() {
            return funcRParams;
        }

        public void print(Ast ast, boolean allConst) {
            ast.addLine(this.ident);
            ast.addLine(Lexer.Token.TokenType.LParent);
            boolean first = true;
            for (FuncRParam funcRParam : this.funcRParams) {
                if (first) {
                    first = false;
                } else {
                    ast.addLine(TokenType.Comma);
                }
                funcRParam.print(ast, false);
            }
            if (this.funcRParams.size() != 0) {
                ast.addLine("<FuncRParams>");
            }
            ast.addLine(Lexer.Token.TokenType.RParent);
        }
    }

    // UnaryExp -> UnaryOp* PrimaryExp
    public static class UnaryExp {
        private final ArrayList<UnaryOp> unaryOps;
        private final PrimaryExp primaryExp;

        public UnaryExp(ArrayList<UnaryOp> unaryOps, PrimaryExp primaryExp) {
            this.unaryOps = new ArrayList<>(unaryOps);
            this.primaryExp = primaryExp;
        }

        public ArrayList<UnaryOp> getUnaryOps() {
            return unaryOps;
        }

        public PrimaryExp getPrimaryExp() {
            return primaryExp;
        }

        public void print(Ast ast, boolean allConst) {
            for (UnaryOp unaryOp : this.unaryOps) {
                ast.addLine(unaryOp.getOp());
                ast.addLine("<UnaryOp>");
            }
            this.primaryExp.print(ast, allConst);
            ast.addLine("<UnaryExp>");
            for (int i = 0; i < this.unaryOps.size(); ++i) {
                ast.addLine("<UnaryExp>");
            }
        }
    }

    // UnaryOp -> '+' | '-' | '!'
    public static class UnaryOp {
        private final Lexer.Token op;

        public UnaryOp(Lexer.Token op) {
            this.op = op;
        }

        public Lexer.Token getOp() {
            return op;
        }
    }

    // Exp -> BinaryExp | UnaryExp
    public static class Exp implements PrimaryExp.PrimaryExpItem, InitVal.InitValItem, FuncRParam.FuncRParamItem {
        private final AddExp addExp;

        public Exp(AddExp addExp) {
            this.addExp = addExp;
        }

        public AddExp getExpItem() {
            return addExp;
        }

        public void print(Ast ast, boolean allConst) {
            this.addExp.print(ast, allConst);
            ast.addLine(allConst ? "<ConstExp>" : "<Exp>");
        }
    }

    public static class MulExp {
        private final UnaryExp lhs;
        private final ArrayList<Lexer.Token> ops;
        private final ArrayList<UnaryExp> rhs;

        public MulExp(UnaryExp lhs, ArrayList<Lexer.Token> ops, ArrayList<UnaryExp> rhs) {
            this.lhs = lhs;
            this.ops = ops;
            this.rhs = rhs;
        }

        public UnaryExp getLhs() {
            return lhs;
        }

        public ArrayList<Lexer.Token> getOps() {
            return ops;
        }

        public ArrayList<UnaryExp> getRhs() {
            return rhs;
        }

        public static boolean isSignal(Lexer.Token token) {
            TokenType type = token.getType();
            return type == TokenType.Mult || type == TokenType.Div || type == TokenType.Mod || type == TokenType.BitAnd;
        }

        public void print(Ast ast, boolean allConst) {
            this.lhs.print(ast, allConst);
            ast.addLine("<MulExp>");
            for (int i = 0; i < this.ops.size(); ++i) {
                ast.addLine(this.ops.get(i));
                this.rhs.get(i).print(ast, allConst);
                ast.addLine("<MulExp>");
            }
        }
    }

    public static class AddExp {
        private final MulExp lhs;
        private final ArrayList<Lexer.Token> ops;
        private final ArrayList<MulExp> rhs;

        public AddExp(MulExp lhs, ArrayList<Lexer.Token> ops, ArrayList<MulExp> rhs) {
            this.lhs = lhs;
            this.ops = ops;
            this.rhs = rhs;
        }

        public MulExp getLhs() {
            return lhs;
        }

        public ArrayList<Lexer.Token> getOps() {
            return ops;
        }

        public ArrayList<MulExp> getRhs() {
            return rhs;
        }

        public static boolean isSignal(Lexer.Token token) {
            TokenType type = token.getType();
            return type == TokenType.Plus || type == TokenType.Minu;
        }

        public void print(Ast ast, boolean allConst) {
            this.lhs.print(ast, allConst);
            ast.addLine("<AddExp>");
            for (int i = 0; i < this.ops.size(); ++i) {
                ast.addLine(this.ops.get(i));
                this.rhs.get(i).print(ast, allConst);
                ast.addLine("<AddExp>");
            }
        }
    }

    public static class RelExp {
        private final AddExp lhs;
        private final ArrayList<Lexer.Token> ops;
        private final ArrayList<AddExp> rhs;

        public RelExp(AddExp lhs, ArrayList<Lexer.Token> ops, ArrayList<AddExp> rhs) {
            this.lhs = lhs;
            this.ops = ops;
            this.rhs = rhs;
        }

        public AddExp getLhs() {
            return lhs;
        }

        public ArrayList<Lexer.Token> getOps() {
            return ops;
        }

        public ArrayList<AddExp> getRhs() {
            return rhs;
        }

        public static boolean isSignal(Lexer.Token token) {
            TokenType type = token.getType();
            return type == TokenType.Lss || type == TokenType.Leq || type == TokenType.Gre || type == TokenType.Geq;
        }

        public void print(Ast ast, boolean allConst) {
            this.lhs.print(ast, allConst);
            ast.addLine("<RelExp>");
            for (int i = 0; i < this.ops.size(); ++i) {
                ast.addLine(this.ops.get(i));
                this.rhs.get(i).print(ast, allConst);
                ast.addLine("<RelExp>");
            }
        }
    }

    public static class EqExp {
        private final RelExp lhs;
        private final ArrayList<Lexer.Token> ops;
        private final ArrayList<RelExp> rhs;

        public EqExp(RelExp lhs, ArrayList<Lexer.Token> ops, ArrayList<RelExp> rhs) {
            this.lhs = lhs;
            this.ops = ops;
            this.rhs = rhs;
        }

        public RelExp getLhs() {
            return lhs;
        }

        public ArrayList<Lexer.Token> getOps() {
            return ops;
        }

        public ArrayList<RelExp> getRhs() {
            return rhs;
        }

        public static boolean isSignal(Lexer.Token token) {
            TokenType type = token.getType();
            return type == TokenType.Eql || type == TokenType.Neq;
        }

        public void print(Ast ast, boolean allConst) {
            this.lhs.print(ast, allConst);
            ast.addLine("<EqExp>");
            for (int i = 0; i < this.ops.size(); ++i) {
                ast.addLine(this.ops.get(i));
                this.rhs.get(i).print(ast, allConst);
                ast.addLine("<EqExp>");
            }
        }
    }

    public static class LOrExp {
        private final ArrayList<LAndExp> exps;

        public LOrExp(ArrayList<LAndExp> exps) {
            this.exps = exps;
        }

        public ArrayList<LAndExp> getExps() {
            return exps;
        }

        public void print(Ast ast) {
            boolean notFirst = false;
            for (LAndExp exp : this.exps) {
                if (notFirst) {
                    ast.addLine(TokenType.Or);
                }
                notFirst = true;
                exp.print(ast);
                ast.addLine("<LOrExp>");
            }
        }
    }

    public static class LAndExp {
        private final ArrayList<EqExp> exps;

        public LAndExp(ArrayList<EqExp> exps) {
            this.exps = exps;
        }

        public ArrayList<EqExp> getExps() {
            return exps;
        }

        public void print(Ast ast) {
            for (int i = 0; i < this.exps.size(); ++i) {
                if (i != 0) {
                    ast.addLine(Lexer.Token.TokenType.And);
                }
                this.exps.get(i).print(ast, false);
                ast.addLine("<LAndExp>");
            }
        }
    }

    public static class Number implements PrimaryExp.PrimaryExpItem {
        private final Lexer.Token val;

        public Number(Lexer.Token val) {
            this.val = val;
        }

        public Lexer.Token getToken() {
            return val;
        }

        public int getVal() {
            return Integer.parseInt(val.getText());
        }

        public void print(Ast ast, boolean allConst) {
            ast.addLine(this.val);
            ast.addLine("<Number>");
        }
    }

    public static class FormatString implements FuncRParam.FuncRParamItem {
        private final Lexer.Token context;

        public FormatString(Lexer.Token context) {
            this.context = context;
        }

        public Lexer.Token getContext() {
            return context;
        }

        public void print(Ast ast, boolean allConst) {
            ast.addLine(this.context);
        }
    }
}
