package backend.register;

import backend.mc.MCInstr;

public interface SpillScheme {
    enum SpillSchemeChoice {
        CountInstr,
    }

    boolean checkToSpill();

    void look(MCInstr instr);

    void reset();

    class CountInstrSpillScheme implements SpillScheme {
        private int cntInstr = 0;

        @Override
        public boolean checkToSpill() {
            return cntInstr > 30;
        }

        @Override
        public void look(MCInstr instr) {
            ++cntInstr;
        }

        @Override
        public void reset() {
            cntInstr = 0;
        }
    }
}