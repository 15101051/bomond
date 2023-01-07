package midend.ir;

import utils.IList;

import java.util.ArrayList;

public class Module {
    public final static Module module = new Module();
    public final IList<Function, Module> functionList = new IList<>(this);
    public int basicBlockIdx;
    public final ArrayList<GlobalVariable> globalList = new ArrayList<>();
}
