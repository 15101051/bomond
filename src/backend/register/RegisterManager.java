package backend.register;

import backend.mc.MCFunction;
import midend.ir.Instruction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

public class RegisterManager {
    public enum MCPhyRegTag {

        zero, at, v0, v1, a0, a1, a2, a3,
        t0, t1, t2, t3, t4, t5, t6, t7,
        s0, s1, s2, s3, s4, s5, s6, s7,
        t8, t9, k0, k1, gp, sp, fp, ra;

        public static HashSet<MCPhyRegTag> getSs() {
            return new HashSet<>(Arrays.asList(s0, s1, s2, s3, s4, s5, s6, s7, fp));
        }

        public static HashSet<MCPhyRegTag> getTs() {
            return new HashSet<>(Arrays.asList(t0, t1, t2, t3, t4, t5, t6, t7));
        }

        public static HashSet<MCPhyRegTag> getVs() {
            return new HashSet<>(Arrays.asList(v0, v1));
        }

        public static HashSet<MCPhyRegTag> getAs() {
            return new HashSet<>(Arrays.asList(a0, a1, a2, a3));
        }
    }

    public void calcCrossCallGlobalRegisters(MCFunction mcf) {

    }

    public static HashSet<MCPhyRegTag> getAllocatableRegisters() {
        HashSet<MCPhyRegTag> regs = new HashSet<>();
        regs.addAll(MCPhyRegTag.getSs());
        regs.addAll(MCPhyRegTag.getTs());
        return regs;
    }

    public ArrayList<MCPhyRegTag> getCrossCallGlobalRegisters(Instruction.Call call) {
        ArrayList<MCPhyRegTag> rs = new ArrayList<>(MCPhyRegTag.getSs());
        rs.addAll(MCPhyRegTag.getVs());
        rs.addAll(MCPhyRegTag.getAs());
        return rs;
    }
}
