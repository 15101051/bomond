package midend.ir;

import java.util.HashMap;
import java.util.Objects;

public abstract class Constant extends Value {
    public abstract boolean isZero();

    public Constant(Type type) {
        super(type, "");
    }

    public static Constant buildConstantFromValues(Type type, HashMap<Integer, ? extends Value> values) {
        if (type.isI32Type()) {
            return (Constant) values.get(0);
        } else {
            Type.ArrayType arrayType = (Type.ArrayType) type;
            int childSize = arrayType.getChildNumOfAtomElements();
            HashMap<Integer, HashMap<Integer, Constant> > newValues = new HashMap<>();
            for (int index : values.keySet()) {
                int dim = index / childSize;
                newValues.computeIfAbsent(dim, k -> new HashMap<>());
                newValues.get(dim).put(index - dim * childSize, (Constant) values.get(index));
            }
            HashMap<Integer, Constant> res = new HashMap<>();
            for (int index : newValues.keySet()) {
                res.put(index, buildConstantFromValues(arrayType.getChildType(), newValues.get(index)));
            }
            return new ConstantArray(type, res);
        }
    }

    public static class ConstantInt extends Constant {
        private final int value;
        private final static ConstantInt const0 = new ConstantInt(Type.IntegerType.getI32(), 0);

        public static ConstantInt getConst0() {
            return const0;
        }

        public ConstantInt(Type type, int value) {
            super(type);
            this.value = value;
        }

        public ConstantInt(int value) {
            super(Type.IntegerType.getI32());
            this.value = value;
        }

        public ConstantInt(boolean value) {
            super(Type.IntegerType.getI1());
            this.value = value ? 1 : 0;
        }

        public ConstantInt neg() {
            return new ConstantInt(-this.value);
        }

        public int getValue() {
            return value;
        }

        @Override
        public boolean isZero() {
            return this.value == 0;
        }

        @Override
        public String toString() {
            return this.getType() + " " + this.value;
        }

        @Override
        public String getName() {
            return String.valueOf(this.value);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ConstantInt that = (ConstantInt) o;
            return value == that.value;
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    public static class ConstantArray extends Constant {
        private final Type.ArrayType arrayType;
        private final HashMap<Integer, Constant> values = new HashMap<>();

        public ConstantArray(Type type, HashMap<Integer, Constant> values) {
            super(type);
            this.arrayType = (Type.ArrayType) type;
            for (int i : values.keySet()) {
                if (!values.get(i).isZero()) {
                    this.values.put(i, values.get(i));
                }
            }
        }

        @Override
        public boolean isZero() {
            return values.isEmpty();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(this.getType().toString()).append(" [");
            for (int i = 0; i < arrayType.getLength(); ++i) {
                if (values.containsKey(i)) {
                    sb.append(values.get(i));
                } else {
                    if (arrayType.getChildType().isArrayType()) {
                        sb.append(new ConstantArray(arrayType.getChildType(), new HashMap<>()));
                    } else {
                        sb.append(ConstantInt.getConst0());
                    }
                }
                if (i != arrayType.getLength() - 1) {
                    sb.append(", ");
                }
            }
            sb.append("]");
            return sb.toString();
        }

        public Constant getValue(int index) {
            Constant res = this.values.get(index);
            if (res == null) {
                if (this.arrayType.getChildType().isI32Type()) {
                    return ConstantInt.getConst0();
                } else {
                    return new ConstantArray(this.arrayType.getChildType(), new HashMap<>());
                }
            } else {
                return res;
            }
        }

        public boolean haveValue(int index) {
            return this.values.containsKey(index);
        }
    }

    public static class ConstantString extends Constant {
        @Override
        public boolean isZero() {
            return false;
        }

        private final String string;

        public static int countLength(String string) {
            int l = string.length();
            for (int i = 0; i < string.length(); ++i) {
                if (string.charAt(i) == '\\') {
                    --l;
                }
            }
            return l;
        }

        public ConstantString(String string) {
            super(new Type.ArrayType(new Type.IntegerType(8), countLength(string) - 1));
            this.string = string;
        }

        public String getString() {
            return string;
        }

        @Override
        public String toString() {
            String content = string.substring(1, string.length() - 1);
            return this.getType() + " c\"" + content.replaceAll("\\\\n", "\\\\0A") + "\\00\"";
        }

        @Override
        public String getName() {
            return string;
        }
    }
}
