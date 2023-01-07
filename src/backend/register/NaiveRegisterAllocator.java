package backend.register;

import backend.mc.MCFunction;
import backend.mc.MCOperand;

public class NaiveRegisterAllocator extends RegisterAllocator {
    public NaiveRegisterAllocator(MCFunction mcf) {
        super(mcf);
    }

    @Override
    public void allocate() {
        do {
            init();
            buildGraph();
            for (MCOperand.MCReg vr : this.mcf.str2Reg().values()) {
                if (vr instanceof MCOperand.MCVirtualReg) {
                    this.selectedStack.push((MCOperand.MCVirtualReg) vr);
                }
            }
            assignColors();
        } while (haveSpillAndDeal());
    }
}
