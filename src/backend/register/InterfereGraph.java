package backend.register;

import backend.mc.MCOperand;
import utils.Pair;

import java.util.HashMap;
import java.util.HashSet;

public class InterfereGraph {
    private final int INF = 0x3f3f3f3f;
    private final HashSet<Pair<MCOperand, MCOperand>> edges = new HashSet<>();
    private final HashMap<MCOperand, HashSet<MCOperand>> edge = new HashMap<>();
    private final HashMap<MCOperand, Integer> degree = new HashMap<>();

    public void init() {
        edge.clear();
        edges.clear();
        degree.clear();
    }

    public void addEdge(MCOperand u, MCOperand v) {
        if (u.equals(v) || edges.contains(new Pair<>(u, v))) {
            return;
        }
        edges.add(new Pair<>(u, v));
        edges.add(new Pair<>(v, u));
        if (u instanceof MCOperand.MCVirtualReg) {
            edge.computeIfAbsent(u, key -> new HashSet<>()).add(v);
            degree.put(u, degree.computeIfAbsent(u, key -> 0) + 1);
        }
        if (v instanceof MCOperand.MCVirtualReg) {
            edge.computeIfAbsent(v, key -> new HashSet<>()).add(u);
            degree.put(v, degree.computeIfAbsent(v, key -> 0) + 1);
        }
    }

    public HashSet<MCOperand> getAllAdjacent(MCOperand k) {
        return edge.getOrDefault(k, new HashSet<>());
    }

    public void decrementDegree(MCOperand k) {
        assert degree.containsKey(k) : "decreaseDegree not containsKey?";
        degree.put(k, degree.get(k) - 1);
    }

    public void setDegreeINF(MCOperand k) {
        degree.put(k, INF);
    }

    public int getDegree(MCOperand k) {
        return degree.getOrDefault(k, 0);
    }

    public boolean isLinked(MCOperand a, MCOperand b) {
        return edges.contains(new Pair<>(a, b));
    }
}
