package utils;

import midend.ir.*;
import midend.ir.Instruction.*;

import java.util.ArrayList;
import java.util.HashMap;

public class ValueCopy {
    private final HashMap<Value, Value> initMap;

    public ValueCopy(HashMap<Value, Value> initMap) {
        this.initMap = initMap;
    }

    private final HashMap<BasicBlock, BasicBlock> basicBlockMap = new HashMap<>();
    private final HashMap<Function.Param, Function.Param> paramMap = new HashMap<>();
    private final HashMap<Instruction, Value> instrMap = new HashMap<>();

    public Value findValue(Value value) {
        if (initMap.containsKey(value)) {
            return initMap.get(value);
        } else if (value instanceof GlobalVariable) {
            return value;
        } else if (value instanceof Function) {
            return value;
        } else if (value instanceof Constant) {
            return value;
        } else if (value instanceof BasicBlock && basicBlockMap.containsKey(value)) {
            return basicBlockMap.get(value);
        } else if (value instanceof Function.Param && paramMap.containsKey(value)) {
            return paramMap.get(value);
        } else if (value instanceof Instruction && instrMap.containsKey(value)) {
            return instrMap.get(value);
        } else {
            return value;
        }
    }

    public void putValue(Value source, Value copy) {
        if (source instanceof BasicBlock && !basicBlockMap.containsKey(source)) {
            basicBlockMap.put((BasicBlock) source, (BasicBlock) copy);
        } else if (source instanceof Function.Param && !paramMap.containsKey(source)) {
            paramMap.put((Function.Param) source, (Function.Param) copy);
        } else if (source instanceof Instruction && !instrMap.containsKey(source)) {
            instrMap.put((Instruction) source, copy);
        }
    }

    public Value getInstrCopy(Instruction instrSource, BasicBlock bbCopy, ArrayList<Phi> phis, ArrayList<Move> moves) {
        Instruction instrCopy;
        if (instrSource instanceof BinaryInst) {
            Value operand0 = findValue(instrSource.getOperand(0));
            Value operand1 = findValue(instrSource.getOperand(1));
            instrCopy = new BinaryInst(instrSource.tag, operand0, operand1, bbCopy);
        } else if (instrSource.tag == InstrTag.Alloca) {
            instrCopy = new Alloca(((Alloca) instrSource).getAllocated(), bbCopy);
        } else if (instrSource.tag == InstrTag.Br) {
            Value operand0 = findValue(instrSource.getOperand(0));
            if (instrSource.getOperandNum() == 1) {
                instrCopy = new Br((BasicBlock) operand0, bbCopy);
            } else {
                Value operand1 = findValue(instrSource.getOperand(1));
                Value operand2 = findValue(instrSource.getOperand(2));
                instrCopy = new Br(operand0, (BasicBlock) operand1, (BasicBlock) operand2, bbCopy);
            }
        } else if (instrSource.tag == InstrTag.Call) {
            ArrayList<Value> paramsCopy = new ArrayList<>();
            Call callSource = (Call) instrSource;
            Value operand0 = findValue(instrSource.getOperand(0));
            for (int i = 1; i < callSource.getOperandNum(); ++i) {
                paramsCopy.add(findValue(callSource.getOperand(i)));
            }
            instrCopy = new Call((Function) operand0, paramsCopy, bbCopy);
        } else if (instrSource.tag == InstrTag.Ret) {
            Value operand0Source = instrSource.getOperand(0);
            Value operand0 = operand0Source == null ? null : findValue(operand0Source);
            instrCopy = new Ret(operand0, bbCopy);
        } else if (instrSource.tag == InstrTag.Load) {
            Value operand0 = findValue(instrSource.getOperand(0));
            instrCopy = new Load(operand0, bbCopy);
        } else if (instrSource.tag == InstrTag.Store) {
            Value operand0 = findValue(instrSource.getOperand(0));
            Value operand1 = findValue(instrSource.getOperand(1));
            instrCopy = new Store(operand0, operand1, bbCopy);
        } else if (instrSource.tag == InstrTag.GetElementPtr) {
            Value operand0 = findValue(instrSource.getOperand(0));
            ArrayList<Value> indicesCopy = new ArrayList<>();
            for (int i = 1; i < instrSource.getOperandNum(); ++i) {
                indicesCopy.add(findValue(instrSource.getOperand(i)));
            }
            instrCopy = new GEP(operand0, indicesCopy, bbCopy);
        } else if (instrSource.tag == InstrTag.Phi) {
            if (initMap.containsKey(instrSource)) {
                return initMap.get(instrSource);
            }
            phis.add((Phi) instrSource);
            instrCopy = new Phi(instrSource.getType(), instrSource.getOperandNum(), bbCopy);
        } else if (instrSource.tag == InstrTag.Move) {
            Move move = (Move) instrSource;
            if (move.getDst() == null) {
                instrCopy = new Move(findValue(move.getSrc()), bbCopy);
            } else {
                moves.add(move);
                instrCopy = new Move(findValue(move.getDst()), findValue(move.getSrc()), bbCopy);
            }
        } else if (instrSource.tag == InstrTag.Zext) {
            Value operand0 = findValue(instrSource.getOperand(0));
            instrCopy = new Zext(operand0, ((Zext) instrSource).getDstType(), bbCopy);
        } else {
            throw new RuntimeException("error in getInstrCopy " + instrSource.tag);
        }
        return instrCopy;
    }

    public Function copyFunction(Function source) {
        ArrayList<Phi> phis = new ArrayList<>();
        ArrayList<Move> moves = new ArrayList<>();
        Function copy = new Function("copy_" + source.getName(), source.getType(), null, false, Module.module);
        for (IList.INode<BasicBlock, Function> bNode : source.getList()) {
            this.putValue(bNode.getValue(), new BasicBlock(bNode.getValue().getName() + "__copy__" + source.getName() + (++Module.module.basicBlockIdx), copy));
        }
        for (int i = 0; i < source.getParamList().size(); ++i) {
            this.putValue(source.getParamList().get(i), copy.getParamList().get(i));
        }
        for (IList.INode<BasicBlock, Function> iNode : source.getList()) {
            BasicBlock bbSource = iNode.getValue();
            BasicBlock bbCopy = (BasicBlock) this.findValue(bbSource);
            //copy bb 除dominator, dirty部分
            for (BasicBlock predecessorSource : bbSource.getPredecessors()) {
                bbCopy.getPredecessors().add((BasicBlock) this.findValue(predecessorSource));
            }
            for (BasicBlock successorSource : bbSource.getSuccessors()) {
                bbCopy.getSuccessors().add((BasicBlock) this.findValue(successorSource));
            }
        }
        for (Function callerFunc : source.getCallers()) {
            copy.getCallers().add(callerFunc);
        }
        for (Function calleeFunc : source.getCallees()) {
            copy.getCallees().add(calleeFunc);
        }
//        for (GlobalVariable gv : source.getStoreGVSet()) {
//            copy.getStoreGVSet().add(gv);
//        }
//        for (GlobalVariable gv : source.getLoadGVSet()) {
//            copy.getLoadGVSet().add(gv);
//        }
//        copy.setHasSideEffect(source.hasSideEffect());
//        copy.setUseGlobalVariable(source.useGlobalVariable());
        for (IList.INode<BasicBlock, Function> bNode : source.getList()) {
            BasicBlock bbSource = bNode.getValue();
            BasicBlock bbCopy = (BasicBlock) this.findValue(bbSource);
            for (IList.INode<Instruction, BasicBlock> iNode : bbSource.getList()) {
                Instruction instrSource = iNode.getValue();
                Value instrCopy = getInstrCopy(instrSource, bbCopy, phis, moves);
                this.putValue(instrSource, instrCopy);
            }
        }
        for (Instruction.Phi phi : phis) {
            Instruction.Phi instrCopy = (Instruction.Phi) this.findValue(phi);
            for (int i = 0; i < phi.getOperandNum(); ++i) {
                instrCopy.setOperand(i, this.findValue(phi.getOperand(i)));
            }
        }
        for (Move move : moves) {
            Move instrCopy = (Move) this.findValue(move);
            instrCopy.setDst(this.findValue(move.getDst()));
        }
        return copy;
    }
}
