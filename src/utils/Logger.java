package utils;

import backend.mc.MCInstr;
import midend.ir.EmitLLVM;
import frontend.Ast;
import frontend.ErrorHandler;
import frontend.Lexer;
import midend.ir.BasicBlock;
import midend.ir.Function;
import midend.ir.Instruction;
import midend.ir.Module;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;

public class Logger {
    private static Lexer.TokenStream tokens;
    private static Ast ast;
    private static final ArrayList<ErrorHandler.Error> errors = new ArrayList<>();
    private static final ArrayList<MCInstr.MCMove> coalescedMoves = new ArrayList<>();
    private static final ArrayList<MCInstr.MCMove> constrainedMoves = new ArrayList<>();
    private static final ArrayList<MCInstr.MCMove> frozenMoves = new ArrayList<>();

    public static void logLexerResult(Lexer.TokenStream lexerTokens) {
        tokens = lexerTokens;
    }

    @SuppressWarnings({"unused"})
    public static void printLexerResult() {
        tokens.print();
    }

    @SuppressWarnings({"unused"})
    public static void printLexerResult(String filePath) {
        tokens.print(filePath);
    }

    public static void logAst(Ast parserAst) {
        ast = parserAst;
    }

    @SuppressWarnings({"unused"})
    public static void printParserResult() {
        for (String s : ast.getRes()) {
            System.out.println(s);
        }
    }

    @SuppressWarnings({"unused"})
    public static void printParserResult(String path) {
        File file = new File(path);
        try {
            FileOutputStream fos = new FileOutputStream(file);
            OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
            BufferedWriter writer = new BufferedWriter(osw);
            for (String s : ast.getRes()) {
                writer.append(s).append("\n");
            }
            writer.flush();
            writer.close();
            osw.close();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings({"unused"})
    public static void logLLVM(String name) {
        new EmitLLVM("D:\\compile\\test\\" + name + ".ll").run(Module.module);
    }

    @SuppressWarnings({"unused"})
    public static void printLLVM() {
        for (IList.INode<Function, Module> fNode : Module.module.functionList) {
            System.out.println(fNode.getValue());
            for (IList.INode<BasicBlock, Function> bNode : fNode.getValue().getList()) {
                System.out.println(bNode.getValue());
                for (IList.INode<Instruction, BasicBlock> iNode : bNode.getValue().getList()) {
                    System.out.println(iNode.getValue().getLLVM());
                }
            }
        }
    }

    public static void logError(int line, ErrorHandler.Error.ErrorType type) {
        errors.add(new ErrorHandler.Error(line, type));
    }

    @SuppressWarnings({"unused"})
    public static void printErrors(String path) {
        for (ErrorHandler.Error error : errors) {
            System.out.println(error);
        }
        File file = new File(path);
        try {
            FileOutputStream fos = new FileOutputStream(file);
            OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
            BufferedWriter writer = new BufferedWriter(osw);
            Collections.sort(errors);
            for (ErrorHandler.Error error : errors) {
                writer.append(error.toString()).append("\n");
            }
            writer.flush();
            writer.close();
            osw.close();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings({"unused"})
    public static void printMIPS(String mips) {
        File file = new File(Config.mipsFileName);
        try {
            FileOutputStream fos = new FileOutputStream(file);
            OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
            BufferedWriter writer = new BufferedWriter(osw);
            writer.append(mips);
            writer.flush();
            writer.close();
            osw.close();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings({"unused"})
    public static void printMIPS(String mips, String name) {
        File file = new File("D:\\compile\\test\\" + name + ".asm");
        try {
            FileOutputStream fos = new FileOutputStream(file);
            OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
            BufferedWriter writer = new BufferedWriter(osw);
            writer.append(mips);
            writer.flush();
            writer.close();
            osw.close();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings({"unused"})
    public static void printPerformanceResult(String path, int divCount, int multCount, int jumpBranchCount, int memCount, int otherCount) {
        File file = new File(path);
        try {
            FileOutputStream fos = new FileOutputStream(file);
            OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
            BufferedWriter writer = new BufferedWriter(osw);
            writer.append("Div Count        : ").append(String.valueOf(divCount)).append("\n");
            writer.append("Mult Count       : ").append(String.valueOf(multCount)).append("\n");
            writer.append("JumpBranch Count : ").append(String.valueOf(jumpBranchCount)).append("\n");
            writer.append("Memory Count     : ").append(String.valueOf(memCount)).append("\n");
            writer.append("Other Count      : ").append(String.valueOf(otherCount)).append("\n");
            writer.append("Total Count      : ").append(String.valueOf(500 * divCount + 40 * multCount + 12 * jumpBranchCount + 20 * memCount + 10 * otherCount)).append("\n");
            writer.flush();
            writer.close();
            osw.close();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void logCoalescedMove(MCInstr.MCMove move) {
        coalescedMoves.add(move);
    }

    @SuppressWarnings({"unused"})
    public static void printCoalescedMoves() {
        System.out.println(coalescedMoves);
    }

    public static void logConstrainedMove(MCInstr.MCMove move) {
        constrainedMoves.add(move);
    }

    @SuppressWarnings({"unused"})
    public static void printConstrainedMoves() {
        System.out.println(constrainedMoves);
    }

    public static void logFrozenMoves(MCInstr.MCMove move) {
        frozenMoves.add(move);
    }

    @SuppressWarnings({"unused"})
    public static void printFrozenMoves() {
        System.out.println(frozenMoves);
    }
}
