package midend.pass;

import midend.ir.Module;

public interface Pass {
    boolean run(Module m);
}
