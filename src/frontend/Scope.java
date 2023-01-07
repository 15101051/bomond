package frontend;

import midend.ir.Constant;
import midend.ir.Function;
import midend.ir.Value;

import java.util.ArrayList;
import java.util.HashMap;

public class Scope {
    private final ArrayList<HashMap<String, Symbol>> tables;

    Scope() {
        this.tables = new ArrayList<>();
        tables.add(new HashMap<>());
    }

    public HashMap<String, Symbol> top() {
        return tables.get(tables.size() - 1);
    }

    public void push() {
        tables.add(new HashMap<>());
    }

    public void pop() {
        tables.remove(tables.size() - 1);
    }

    public boolean isGlobal() {
        return this.tables.size() == 1;
    }

    public Symbol find(String name) {
        for (int i = tables.size() - 1; i >= 0; i--) {
            Symbol t = tables.get(i).get(name);
            if (t != null) return t;
        }
        return null;
    }

    public void put(String name, Symbol v) {
        top().put(name, v);
    }

    public Function getFunc(String name) {
        return (Function) find(name).getValue();
    }

    public void put(String name, Function f) {
        assert isGlobal();
        top().put(name, new Symbol(false, null, f));
    }

    public void putGlobal(String name, Symbol v) {
        tables.get(0).put(name, v);
    }

    public static class Symbol {
        private final boolean isConstant;
        private final Constant init;
        private final Value value;

        public Symbol(boolean isConstant, Constant init, Value value) {
            this.isConstant = isConstant;
            this.init = init;
            this.value = value;
        }

        public boolean isConstant() {
            return isConstant;
        }

        public Constant getInit() {
            return init;
        }

        public Value getValue() {
            return value;
        }
    }
}