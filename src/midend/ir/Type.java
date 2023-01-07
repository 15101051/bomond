package midend.ir;

import java.util.ArrayList;
import java.util.Objects;

public abstract class Type {
    public abstract int needBytes();

    public static class VoidType extends Type {
        private final static VoidType type = new VoidType();

        public static Type getType() {
            return type;
        }

        @Override
        public String toString() {
            return "void";
        }

        @Override
        public int needBytes() {
            return 4;
        }
    }

    public static class LabelType extends Type {
        private final static LabelType type = new LabelType();

        public static Type getType() {
            return type;
        }

        @Override
        public String toString() {
            return "label";
        }

        @Override
        public int needBytes() {
            return 4;
        }
    }

    public static class IntegerType extends Type {
        private final static IntegerType i1 = new IntegerType(1);
        private final static IntegerType i32 = new IntegerType(32);
        private final int bits;

        public IntegerType(int bits) {
            this.bits = bits;
            if (!(bits == 32 || bits == 1 || bits == 8)) {
                throw new RuntimeException("illegal integerType bits " + bits);
            }
        }

        public static IntegerType getI32() {
            return i32;
        }

        public static IntegerType getI1() {
            return i1;
        }

        @Override
        public String toString() {
            return "i" + bits;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            IntegerType that = (IntegerType) o;
            return bits == that.bits;
        }

        @Override
        public int hashCode() {
            return Objects.hash(bits);
        }

        @Override
        public int needBytes() {
            return 4;
        }
    }

    public static class ArrayType extends Type {
        private final int numOfAtomElements;
        private final Type childType;
        private final int length;
        private final int childNumOfAtomElements;
        private final Type atomType;

        public ArrayType(Type childType, int length) {
            if (length < 0) {
                throw new RuntimeException("illegal ArrayType length " + length);
            }
            this.childType = childType;
            this.length = length;
            if (childType.isIntegerType()) {
                this.childNumOfAtomElements = 1;
                this.atomType = childType;
            } else {
                this.childNumOfAtomElements = ((ArrayType) childType).numOfAtomElements;
                this.atomType = ((ArrayType) childType).atomType;
            }
            this.numOfAtomElements = this.childNumOfAtomElements * this.length;
        }

        public int getNumOfAtomElements() {
            return numOfAtomElements;
        }

        public int getChildNumOfAtomElements() {
            return childNumOfAtomElements;
        }

        public int getLength() {
            return length;
        }

        public Type getChildType() {
            return childType;
        }

        public ArrayList<Integer> getDims() {
            ArrayList<Integer> res = new ArrayList<>();
            Type type = this;
            while (type instanceof ArrayType) {
                res.add(((ArrayType) type).getLength());
                type = ((ArrayType) type).getChildType();
            }
            return res;
        }

        @Override
        public String toString() {
            return "[" + length + " x " + childType + "]";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ArrayType that = (ArrayType) o;
            return length == that.length && Objects.equals(childType, that.childType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(length, childType);
        }

        @Override
        public int needBytes() {
            return this.numOfAtomElements * 4;
        }
    }

    public static class PointerType extends Type {
        private final Type pointTo;

        public PointerType(Type pointTo) {
            this.pointTo = pointTo;
        }

        public Type getPointTo() {
            return pointTo;
        }

        @Override
        public String toString() {
            return pointTo + "*";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PointerType that = (PointerType) o;
            return Objects.equals(pointTo, that.pointTo);
        }

        @Override
        public int hashCode() {
            return Objects.hash(pointTo);
        }

        @Override
        public int needBytes() {
            return 4;
        }
    }

    public static class FunctionType extends Type {
        private final Type returnType;
        private final ArrayList<Type> paramTypes;

        public FunctionType(Type returnType, ArrayList<Type> paramTypes) {
            this.returnType = returnType;
            this.paramTypes = new ArrayList<>(paramTypes);
        }

        public Type getParamType(int index) {
            return this.paramTypes.get(index);
        }

        public int getParamLength() {
            return this.paramTypes.size();
        }

        public Type getReturnType() {
            return returnType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FunctionType that = (FunctionType) o;
            return Objects.equals(returnType, that.returnType) && Objects.equals(paramTypes, that.paramTypes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(returnType, paramTypes);
        }

        @Override
        public String toString() {
            throw new RuntimeException("call functionType toString");
        }

        @Override
        public int needBytes() {
            return -1;
        }
    }

    public boolean isVoidType() {
        return this instanceof VoidType;
    }

    public boolean isIntegerType() {
        return this instanceof IntegerType;
    }

    public boolean isI1Type() {
        return this.equals(IntegerType.getI1());
    }

    public boolean isI32Type() {
        return this.equals(IntegerType.getI32());
    }

    public boolean isArrayType() {
        return this instanceof ArrayType;
    }

    public boolean isPointerType() {
        return this instanceof PointerType;
    }
}
