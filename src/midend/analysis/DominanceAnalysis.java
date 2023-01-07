package midend.analysis;

import midend.ir.BasicBlock;
import midend.ir.Function;
import midend.ir.Module;
import utils.IList;

import java.util.HashMap;
import java.util.HashSet;

public class DominanceAnalysis {
    public void run(Module m) {
        for (IList.INode<Function, Module> fNode : m.functionList) {
            Function func = fNode.getValue();
            if (!func.isBuiltin()) {
                run(func);
            }
        }
    }

    private final HashMap<BasicBlock, HashSet<BasicBlock>> g = new HashMap<>();
    private final HashMap<BasicBlock, HashSet<BasicBlock>> rg = new HashMap<>();
    private final HashMap<BasicBlock, HashSet<BasicBlock>> s = new HashMap<>();
    private final HashMap<BasicBlock, BasicBlock> semi = new HashMap<>();
    private final HashMap<BasicBlock, Integer> dfn = new HashMap<>();
    private final HashMap<Integer, BasicBlock> id = new HashMap<>();
    private final HashMap<BasicBlock, BasicBlock> fa = new HashMap<>();
    private final HashMap<BasicBlock, BasicBlock> f = new HashMap<>();
    private final HashMap<BasicBlock, BasicBlock> val = new HashMap<>();
    private int tot = 0;

    private void init() {
        g.clear();
        rg.clear();
        s.clear();
        semi.clear();
        dfn.clear();
        id.clear();
        fa.clear();
        tot = 0;
        f.clear();
        val.clear();
    }

    BasicBlock find(BasicBlock x) {
        if (x.equals(f.get(x))) {
            return x;
        }
        BasicBlock anc = find(f.get(x));
        if (dfn.get(semi.get(val.get(f.get(x)))) < dfn.get(semi.get(val.get(x)))) {
            val.put(x, val.get(f.get(x)));
        }
        f.put(x, anc);
        return anc;
    }

    private void dfs(BasicBlock k) {
        dfn.put(k, ++tot);
        id.put(tot, k);
        for (BasicBlock v : k.getSuccessors()) {
            if (dfn.containsKey(v)) {
                continue;
            }
            fa.put(v, k);
            dfs(v);
        }
    }

    void tarjan() {
        int n = tot;
        for (int p = n; p > 1; --p) {
            BasicBlock k = id.get(p);
            for (BasicBlock v : rg.getOrDefault(k, new HashSet<>())) {
                if (!dfn.containsKey(v)) {
                    continue;
                }
                find(v);
                if (dfn.get(semi.get(val.get(v))) < dfn.get(semi.get(k))) {
                    semi.put(k, semi.get(val.get(v)));
                }
            }
            if (!s.containsKey(semi.get(k))) {
                s.put(semi.get(k), new HashSet<>());
            }
            s.get(semi.get(k)).add(k);
            f.put(k, fa.get(k));
            k = fa.get(k);
            for (BasicBlock v : s.getOrDefault(k, new HashSet<>())) {
                find(v);
                if (semi.get(val.get(v)).equals(k)) {
                    v.setIdominator(k);
                } else {
                    v.setIdominator(val.get(v));
                }
            }
        }
        for (int p = 2; p <= n; ++p) {
            BasicBlock k = id.get(p);
            if (!k.getIdominator().equals(semi.get(k))) {
                k.setIdominator(k.getIdominator().getIdominator());
            }
        }
    }

    public void run(Function func) {
        init();
        for (IList.INode<BasicBlock, Function> bNode : func.getList()) {
            BasicBlock bb = bNode.getValue();
            g.put(bb, new HashSet<>());
            rg.put(bb, new HashSet<>());
        }
        for (IList.INode<BasicBlock, Function> bNode : func.getList()) {
            BasicBlock bb = bNode.getValue();
            for (BasicBlock succ : bb.getSuccessors()) {
                g.get(bb).add(succ);
                rg.get(succ).add(bb);
            }
            semi.put(bb, bb);
            f.put(bb, bb);
            val.put(bb, bb);
        }
        dfs(func.getList().getEntry().getValue());
        tarjan();
        for (IList.INode<BasicBlock, Function> bNode : func.getList()) {
            BasicBlock bb = bNode.getValue();
            for (BasicBlock pred : bb.getPredecessors()) {
                BasicBlock anc = pred;
                while (anc != bb.getIdominator()) {
                    anc.getDominanceFrontier().add(bb);
                    anc = anc.getIdominator();
                }
            }
        }
        for (IList.INode<BasicBlock, Function> bNode : func.getList()) {
            BasicBlock bb = bNode.getValue();
            bb.getIdominateds().clear();
        }
        for (IList.INode<BasicBlock, Function> bNode : func.getList()) {
            BasicBlock cur = bNode.getValue();
            BasicBlock bb = bNode.getValue();
            while (true) {
                bb.getDominators().add(cur);
                if (cur.equals(func.getList().getEntry().getValue())) {
                    break;
                }
                cur = cur.getIdominator();
            }
            if (bNode.equals(func.getList().getEntry())) {
                continue;
            }
            bb.getIdominator().getIdominateds().add(bb);
        }
        dfsDominanceLevel(func.getList().getEntry().getValue(), 0);
    }

    public void dfsDominanceLevel(BasicBlock bb, int level) {
        bb.setDominanceLevel(level);
        for (BasicBlock dominated : bb.getIdominateds()) {
            dfsDominanceLevel(dominated, level + 1);
        }
    }
}
