package midend.ir;

import java.util.ArrayList;
import java.util.HashSet;

public class Value {
    private final Type type;
    private String name;
    private final HashSet<Use> uses = new HashSet<>();

    public Value(Type type, String name) {
        this.type = type;
        this.name = name;
    }

    public Type getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void addUse(Use use) {
        if (this.uses.contains(use)) {
            throw new RuntimeException("Failed to add use because useList already contains this use.");
        }
        this.uses.add(use);
    }

    public void removeUse(Use use) {
        if (!this.uses.contains(use)) {
            throw new RuntimeException("Failed to remove use because useList does not contain this use.");
        }
        this.uses.remove(use);
    }

    public boolean allUsersAre(Instruction.InstrTag tag) {
        for (Use use : this.uses) {
            if (use.getUser().tag != tag) {
                return false;
            }
        }
        return true;
    }

    public boolean containsUser(Instruction.InstrTag tag) {
        for (Use use : this.uses) {
            if (use.getUser().tag == tag) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return type + " " + name;
    }

    public void replaceSelfWith(Value newValue) {
        ArrayList<Use> usesToBeDeleted = new ArrayList<>(uses);
        for (Use use : usesToBeDeleted) {
            use.getUser().setOperand(use.getOperandRank(), newValue);
        }
        if (!uses.isEmpty()) {
            throw new RuntimeException("replaceSelfWith didn't clear the useList");
        }
    }

    public HashSet<Use> getUses() {
        return uses;
    }
}
