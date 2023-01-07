package midend.ir;

import utils.IList;

import java.util.HashMap;

public class Simulate {
    private final HashMap<Integer, Integer> res = new HashMap<>();
    
    private int getValue(Value value, int param, HashMap<Value, Integer> map) {
        if (value instanceof Constant.ConstantInt) {
            return ((Constant.ConstantInt) value).getValue();
        } else if (value instanceof Function.Param) {
            return param;
        } else {
            return map.get(value);
        }
    }

    public int simulate(Function function, int val) {
        // System.out.println("Simulating function " + function.getName() + " with param " + val);
        if (res.containsKey(val)) {
            return res.get(val);
        }
        HashMap<Value, Integer> valueMap = new HashMap<>();
        IList.INode<Instruction, BasicBlock> ptr = function.getList().getEntry().getValue().getList().getEntry();
        BasicBlock last = null;
        while (true) {// System.out.println("ptr = " + ptr.getValue().getLLVM());
            if (ptr.getValue() instanceof Instruction.BinaryInst) {
                int val1 = getValue(ptr.getValue().getOperand(0), val, valueMap);
                int val2 = getValue(ptr.getValue().getOperand(1), val, valueMap);
                int res = Instruction.BinaryInst.calcBinaryInt(ptr.getValue().getTag(), val1, val2);
                valueMap.put(ptr.getValue(), res);
                ptr = ptr.getNext();
            } else if (ptr.getValue() instanceof Instruction.Zext) {
                valueMap.put(ptr.getValue(), getValue(ptr.getValue().getOperand(0), val, valueMap));
                ptr = ptr.getNext();
            } else if (ptr.getValue() instanceof Instruction.Move) {
                valueMap.put(ptr.getValue(), getValue(ptr.getValue().getOperand(0), val, valueMap));
                ptr = ptr.getNext();
            } else if (ptr.getValue() instanceof Instruction.Call) {
                valueMap.put(ptr.getValue(), simulate(((Instruction.Call) ptr.getValue()).getFunc(), getValue(ptr.getValue().getOperand(1), val, valueMap)));
                ptr = ptr.getNext();
            } else if (ptr.getValue() instanceof Instruction.Br) {
                last = ptr.getParent().getHolder();
                if (ptr.getValue().getOperandNum() == 1) {
                    ptr = ((BasicBlock) ptr.getValue().getOperand(0)).getList().getEntry();
                } else {
                    // System.out.println(valueMap.keySet());
                    if (getValue(ptr.getValue().getOperand(0), val, valueMap) != 0) {
                        ptr = ((BasicBlock) ptr.getValue().getOperand(1)).getList().getEntry();
                    } else {
                        ptr = ((BasicBlock) ptr.getValue().getOperand(2)).getList().getEntry();
                    }
                }
            } else if (ptr.getValue() instanceof Instruction.Ret) {
                if (ptr.getValue().getOperandNum() == 0) {
                    res.put(val, 0);
                    return 0;
                } else {
                    res.put(val, getValue(ptr.getValue().getOperand(0), val, valueMap));
                    return getValue(ptr.getValue().getOperand(0), val, valueMap);
                }
            } else if (ptr.getValue() instanceof Instruction.Phi) {
                int index = ptr.getParent().getHolder().getPredecessors().indexOf(last);
                valueMap.put(ptr.getValue(), getValue(ptr.getValue().getOperand(index), val, valueMap));
                ptr = ptr.getNext();
            }
        }
    }
}
