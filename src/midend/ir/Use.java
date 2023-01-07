package midend.ir;

import java.util.Objects;

public class Use {
    private final Instruction user;
    private final int operandRank;

    public Use(Instruction user, int operandRank) {
        this.user = user;
        this.operandRank = operandRank;
    }

    public Instruction getUser() {
        return user;
    }

    public int getOperandRank() {
        return operandRank;
    }

    @Override
    public String toString() {
        return "Use{user=" + user + ", operandRank=" + operandRank + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Use use = (Use) o;
        return operandRank == use.operandRank && Objects.equals(user, use.user);
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, operandRank);
    }
}
