package frontend;

import midend.ir.Instruction;
import midend.ir.Type;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

public class Lexer {
    private final BufferedInputStream bis;
    private final TokenStream res = new TokenStream();
    private char curChar;
    private boolean end = false;
    private int line = 1;

    private boolean nextChar() {
        int ch = -1;
        try {
            ch = bis.read();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (ch == -1) {
            end = true;
            return false;
        }
        curChar = (char) ch;
        if (curChar == '\n') {
            ++line;
        }
        return true;
    }

    private boolean isBlank(char c) {
        return Character.isWhitespace(c);
    }

    private boolean isDigit(char c) {
        return '0' <= c && c <= '9';
    }

    private boolean isAlphabetic(char c) {
        return ('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z');
    }

    private boolean isIdent(char c) {
        return isAlphabetic(c) || isDigit(c) || c == '_';
    }

    private Token nextKeywordIdent() {
        StringBuilder sb = new StringBuilder();
        while (!end && isIdent(curChar)) {
            sb.append(curChar);
            nextChar();
        }
        String text = sb.toString();
        switch (text) {
            case "main": return new Token(Token.TokenType.MainTK, text, line);
            case "const": return new Token(Token.TokenType.ConstTK, text, line);
            case "int": return new Token(Token.TokenType.IntTK, text, line);
            case "break": return new Token(Token.TokenType.BreakTK, text, line);
            case "continue": return new Token(Token.TokenType.ContinueTK, text, line);
            case "if": return new Token(Token.TokenType.IfTK, text, line);
            case "else": return new Token(Token.TokenType.ElseTK, text, line);
            case "while": return new Token(Token.TokenType.WhileTK, text, line);
            case "return": return new Token(Token.TokenType.ReturnTK, text, line);
            case "void": return new Token(Token.TokenType.VoidTK, text, line);
            case "getint": return new Token(Token.TokenType.GetIntTK, text, line);
            case "printf": return new Token(Token.TokenType.PrintfTK, text, line);
            case "bitand": return new Token(Token.TokenType.BitAnd, text, line);
            default: return new Token(Token.TokenType.Idenfr, text, line);
        }
    }

    private Token nextDigit() {
        StringBuilder sb = new StringBuilder();
        while (!end && isIdent(curChar)) {
            sb.append(curChar);
            nextChar();
        }
        return new Token(Token.TokenType.IntCon, sb.toString(), line);
    }

    private Token nextString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\"");
        while (nextChar() && curChar != '\"') {
            sb.append(curChar);
        }
        sb.append("\"");
        nextChar();
        Token token = new Token(Token.TokenType.StrCon, sb.toString(), line);
        ErrorHandler.checkIllegalCharInFormatString(token);
        return token;
    }

    private Token nextOp() {
        char firChar = curChar;
        nextChar();
        char secChar = curChar;
        switch (firChar) {
            case '+': {
                return new Token(Token.TokenType.Plus, "+", line);
            }
            case '-': {
                return new Token(Token.TokenType.Minu, "-", line);
            }
            case '*': {
                return new Token(Token.TokenType.Mult, "*", line);
            }
            case '/': {
                if (secChar == '/') {
                    while (nextChar()) {
                        if (curChar == '\n' || curChar == '\r') {
                            break;
                        }
                    }
                    nextChar();
                    return null;
                } else if (secChar == '*') {
                    nextChar();
                    secChar = curChar;
                    while (!end) {
                        firChar = secChar;
                        nextChar();
                        secChar = curChar;
                        if (firChar == '*' && secChar == '/') {
                            break;
                        }
                    }
                    nextChar();
                    return null;
                } else {
                    return new Token(Token.TokenType.Div, "/", line);
                }
            }
            case '%': {
                return new Token(Token.TokenType.Mod, "%", line);
            }
            case '!': {
                if (secChar == '=') {
                    nextChar();
                    return new Token(Token.TokenType.Neq, "!=", line);
                } else {
                    return new Token(Token.TokenType.Not, "!", line);
                }
            }
            case '&': {
                nextChar();
                return new Token(Token.TokenType.And, "&&", line);
            }
            case '|': {
                nextChar();
                return new Token(Token.TokenType.Or, "||", line);
            }
            case '<': {
                if (secChar == '=') {
                    nextChar();
                    return new Token(Token.TokenType.Leq, "<=", line);
                } else {
                    return new Token(Token.TokenType.Lss, "<", line);
                }
            }
            case '>': {
                if (secChar == '=') {
                    nextChar();
                    return new Token(Token.TokenType.Geq, ">=", line);
                } else {
                    return new Token(Token.TokenType.Gre, ">", line);
                }
            }
            case '=': {
                if (secChar == '=') {
                    nextChar();
                    return new Token(Token.TokenType.Eql, "==", line);
                } else {
                    return new Token(Token.TokenType.Assign, "=", line);
                }
            }
            case ';': {
                return new Token(Token.TokenType.Semicn, ";", line);
            }
            case ',': {
                return new Token(Token.TokenType.Comma, ",", line);
            }
            case '(': {
                return new Token(Token.TokenType.LParent, "(", line);
            }
            case ')': {
                return new Token(Token.TokenType.RParent, ")", line);
            }
            case '[': {
                return new Token(Token.TokenType.LBrack, "[", line);
            }
            case ']': {
                return new Token(Token.TokenType.RBrack, "]", line);
            }
            case '{': {
                return new Token(Token.TokenType.LBrace, "{", line);
            }
            case '}': {
                return new Token(Token.TokenType.RBrace, "}", line);
            }
            default: {
                throw new RuntimeException(firChar + " illegal op!");
            }
        }
    }

    public Lexer(BufferedInputStream bis) {
        this.bis = bis;
        nextChar();
        while (!end) {
            if (isAlphabetic(curChar) || curChar == '_') {
                res.add(nextKeywordIdent());
            } else if (isBlank(curChar)) {
                nextChar();
            } else if (isDigit(curChar)) {
                res.add(nextDigit());
            } else if (curChar == '\"') {
                res.add(nextString());
            } else {
                Token token = nextOp();
                if (token != null) {
                    res.add(token);
                }
            }
        }

    }

    public TokenStream getRes() {
        return res;
    }

    public static class Token {
        public enum TokenType {
            Idenfr, IntCon, StrCon, MainTK("main"), ConstTK("const"), IntTK("int"),
            BreakTK("break"), ContinueTK("continue"), IfTK("if"), ElseTK("else"),
            Not("!"), And("&&"), Or("||"), WhileTK("while"),
            GetIntTK("getint"), PrintfTK("printf"), ReturnTK("return"),
            Plus("+", Instruction.InstrTag.Add), Minu("-", Instruction.InstrTag.Sub), VoidTK("void"),
            Mult("*", Instruction.InstrTag.Mul), Div("/", Instruction.InstrTag.Sdiv), Mod("%", Instruction.InstrTag.Srem),
            BitAnd("bitand", Instruction.InstrTag.And),
            Lss("<", Instruction.InstrTag.Slt), Leq("<=", Instruction.InstrTag.Sle),
            Gre(">", Instruction.InstrTag.Sgt), Geq(">=", Instruction.InstrTag.Sge),
            Eql("==", Instruction.InstrTag.Eq), Neq("!=", Instruction.InstrTag.Ne),
            Assign("="), Semicn(";"), Comma(","), LParent("("), RParent(")"), LBrack("["), RBrack("]"), LBrace("{"), RBrace("}");

            private final String text;
            private final Instruction.InstrTag tag;

            TokenType(String text) {
                this.text = text;
                this.tag = null;
            }

            TokenType() {
                this.text = null;
                this.tag = null;
            }

            TokenType(String text, Instruction.InstrTag tag) {
                this.text = text;
                this.tag = tag;
            }

            public Instruction.InstrTag getTag() {
                return tag;
            }

            @Override
            public String toString() {
                return this.name().toUpperCase(Locale.ROOT);
            }

            public Token getTokenFromType() {
                return new Token(this, this.text, null);
            }
        }

        private final TokenType type;
        private final String text;
        private final Integer line;

        public Token(TokenType type, String text, Integer line) {
            this.type = type;
            this.text = text;
            this.line = line;
        }

        public TokenType getType() {
            return type;
        }

        public Type getIRType() {
            if (this.type == TokenType.IntTK) {
                return new Type.IntegerType(32);
            } else if (this.type == TokenType.VoidTK) {
                return new Type.VoidType();
            } else {
                return null;
            }
        }

        public String getText() {
            return text;
        }

        public Integer getLine() {
            return line;
        }

        @Override
        public String toString() {
            return this.type.toString() + " " + this.text;
        }
    }

    public static class TokenStream {
        private final ArrayList<Token> tokens = new ArrayList<>();
        private int cur = 0;

        public void add(Token token) {
            tokens.add(token);
        }

        public boolean hasNext() {
            return cur < tokens.size();
        }

        public void print() {
            for (Token token : tokens) {
                System.out.println(token);
            }
        }

        public void print(String filePath) {
            File file = new File(filePath);
            try {
                FileOutputStream fos = new FileOutputStream(file);
                OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
                BufferedWriter writer = new BufferedWriter(osw);
                for (Token token : tokens) {
                    writer.append(String.valueOf(token)).append("\n");
                }
                writer.close();
                osw.close();
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public boolean haveAhead(int index) {
            return cur + index < tokens.size();
        }

        public Token ahead(int index) {
            return tokens.get(cur + index);
        }

        public Token getAndConsume() {
            return tokens.get(cur++);
        }

        public boolean haveCur() {
            return cur < tokens.size();
        }

        public Token get() {
            return tokens.get(cur);
        }

        public Token getPre() {
            return tokens.get(cur - 1);
        }
    }
}
