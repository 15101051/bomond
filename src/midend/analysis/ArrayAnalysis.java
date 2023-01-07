package midend.analysis;

import midend.ir.Function;
import midend.ir.GlobalVariable;
import midend.ir.Use;
import midend.ir.Value;
import midend.ir.Instruction.*;

public class ArrayAnalysis {
    public static Value getArray(Value pointer) {
        while (true) {
            if (pointer instanceof GEP) {
                pointer = ((GEP) pointer).getOperand(0);
            } else if (pointer instanceof Load) {
                pointer = ((Load) pointer).getOperand(0);
            } else if (pointer instanceof GlobalVariable) {
                return pointer;
            } else if (pointer instanceof Function.Param) {
                return pointer;
            } else if (pointer instanceof Alloca) {
                if (((Alloca) pointer).getAllocated().isPointerType()) {
                    boolean flag = false;
                    for (Use use : pointer.getUses()) {
                        if (use.getUser() instanceof Store) {
                            pointer = ((Store) use.getUser()).getValue();
                            flag = true;
                            break;
                        }
                    }
                    if (flag) {
                        continue;
                    }
                }
                return pointer;
            } else {
                return null;
            }
        }
    }
}
