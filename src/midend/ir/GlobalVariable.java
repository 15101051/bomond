package midend.ir;

import midend.ir.Type.*;

public class GlobalVariable extends Value {
    private boolean isConstant;
    private final Constant init;

    public GlobalVariable(String name, Type type, Constant init, boolean isConstant) {
        super(new PointerType(type), name);
        this.isConstant = isConstant;
        this.init = init;
    }

    public Constant getInit() {
        return init;
    }

    public void setConstant(boolean constant) {
        isConstant = constant;
    }

    public boolean isConstant() {
        return isConstant;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getName()).append(" = dso_local ");
        if (isConstant) {
            sb.append("constant ");
        } else {
            sb.append("global ");
        }
        if (((PointerType) this.getType()).getPointTo().isIntegerType()) {
            sb.append(((PointerType) this.getType()).getPointTo().toString()).append(" ");
            sb.append(this.init == null ? "0 " : ((Constant.ConstantInt) this.init).getValue());
        } else if (((PointerType) this.getType()).getPointTo().isArrayType()) {
            if (this.init == null) {
                sb.append(((PointerType) this.getType()).getPointTo().toString()).append(" ");
                sb.append("zeroinitializer ");
            } else {
                sb.append(this.init);
            }
        }
        return sb.toString();
    }
}
