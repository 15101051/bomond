package frontend;

import utils.Logger;

public class ErrorHandler {
    static boolean normalChar(char c) {
        return c == 32 || c == 33 || (40 <= c && c <= 126);
    }

    static void checkIllegalCharInFormatString(Lexer.Token string) {
        String str = string.getText();
        int l = str.length();
        boolean tag = true;
        for (int i = 1; i < l - 1 && tag; ++i) {
            char ci = str.charAt(i);
            if (ci == '%') {
                if (i == l - 2 || str.charAt(i + 1) != 'd') {
                    tag = false;
                }
            } else if (!normalChar(ci)) {
                tag = false;
            } else if (ci == '\\') {
                if (i == l - 2 || str.charAt(i + 1) != 'n') {
                    tag = false;
                }
            }
        }
        if (!tag) {
            Logger.logError(string.getLine(), Error.ErrorType.IllegalCharInFormatString);
        }
    }

    static void checkPrintfArgsMatchesFormatString(String formatString, int argsNum, int printfLine) {
        for (int i = 0; i < formatString.length(); ++i) {
            char ci = formatString.charAt(i);
            if (ci == '%' && i + 1 < formatString.length() && formatString.charAt(i + 1) == 'd') {
                --argsNum;
            }
        }
        if (argsNum != 0) {
            Logger.logError(printfLine, Error.ErrorType.UnmatchedPrintfFormatString);
        }
    }

    public static class Error implements Comparable<Error> {
        public enum ErrorType {
            IllegalCharInFormatString('a'), DuplicateName('b'), UndefinedName('c'), UnmatchedParamNumber('d'),
            UnmatchedParamType('e'), ReturnUnmatchedExp('f'), LackReturn('g'), AssignToConst('h'),
            LackSemicolon('i'), LackRParent('j'), LackRBrack('k'), UnmatchedPrintfFormatString('l'),
            BreakContinueOutOfLoop('m');

            private final char errorCode;

            ErrorType(char c) {
                this.errorCode = c;
            }
        }

        @Override
        public int compareTo(Error o) {
            if (this.line == o.line) {
                return this.type.errorCode - o.type.errorCode;
            } else {
                return this.line - o.line;
            }
        }

        private final int line;
        private final ErrorType type;

        public Error(int line, ErrorType type) {
            this.line = line;
            this.type = type;
        }

        @Override
        public String toString() {
            return line + " " + type.errorCode;
        }
    }
}
