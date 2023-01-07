import backend.CodeGen;
import backend.mc.Interpreter;
import backend.mc.MCModule;
import midend.MidendRunner;
import utils.Config;
import frontend.Lexer;
import frontend.Parser;
import frontend.Visitor;
import midend.ir.Module;
import utils.Logger;

import java.io.*;
import java.util.Arrays;

public class Compiler {
    public static void main(String[] args) throws IOException {
        String inputFile = Arrays.stream(args).filter(name -> name.endsWith(".sy")).findAny().orElse(Config.inputFileName);
        FileInputStream fis = new FileInputStream(inputFile);
        BufferedInputStream bis = new BufferedInputStream(fis);
        Lexer lexer = new Lexer(bis);
        Lexer.TokenStream tokens = lexer.getRes();
        // Logger.logLexerResult(tokens);
        // Logger.printLexerResult(Config.outputFileName);
        Parser parser = new Parser(tokens);
        // Logger.logAst(parser.getRes());
        // parser.getRes().print();
        // new Semantic(parser.getRes());
        // Logger.printErrors(Config.errorOutputFileName);
        // Logger.printParserResult(Config.outputFileName);
        new Visitor(parser.getRes());
        // Logger.printLLVM();
        if (Config.optimize) {
            new MidendRunner().run(Module.module);
        }
        Logger.logLLVM("beforeCodeGen");
        CodeGen cg = new CodeGen();
        cg.codeGen();
        Logger.printMIPS(cg.getMIPS());
        if (!Config.submit) {
            Interpreter interpreter = new Interpreter();
            interpreter.interpret(MCModule.module, false);
        }
    }
}
