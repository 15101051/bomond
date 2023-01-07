package backend.mc;

import java.util.ArrayList;
import java.util.HashMap;

import backend.mc.MCOperand.*;
import backend.register.RegisterManager;
import midend.ir.GlobalVariable;

public class MCModule {
    public static final MCModule module = new MCModule();
    public final HashMap<GlobalVariable, MCGlobalVariable> lirGVs = new HashMap<>();
    public final HashMap<String, MCFunction> functions = new HashMap<>();
    public final HashMap<String, MCString> strings = new HashMap<>();
    public final ArrayList<MCGlobalData> mcGlobalDatas = new ArrayList<>();
    public final HashMap<Integer, MCInstr> idToMCInstr = new HashMap<>();
    public final HashMap<MCLabel, MCBasicBlock> label2BB = new HashMap<>();
    public final RegisterManager registerManager = new RegisterManager();
}
