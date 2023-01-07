package midend.analysis;

import midend.ir.Module;
import midend.ir.BasicBlock;
import midend.ir.Function;
import midend.ir.Value;
import midend.ir.Instruction.BinaryInst;
import midend.ir.Instruction.Phi;
import midend.ir.Instruction.Br;
import utils.BasicBlockToposort;
import utils.IList;

import java.util.*;

public class LoopInfoAnalysis {
    // 计算循环包括那些基本块
    // 计算循环出边（离开循环）（好像没用上）
    // 必须先run DominanceAnalysis
    public void run(Module m) {
        for (IList.INode<Function, Module> f : m.functionList) {
            if (f.getValue().isBuiltin()) {
                continue;
            }
            calcLoopInfo(f.getValue());
        }
    }

    public void calcLoopInfo(Function f) {
        f.getLoopInfo().init();
        ArrayList<BasicBlock> bbs = BasicBlockToposort.basicBlockToposort(f);
        Collections.reverse(bbs);
        for (BasicBlock header : bbs) {
            ArrayList<BasicBlock> latchBBs = new ArrayList<>();
            for (BasicBlock pred : header.getPredecessors()) {
                if (pred.getDominators().contains(header)) {
                    latchBBs.add(pred);
                    // 回边
                }
            }
            if (!latchBBs.isEmpty()) {
                HashSet<BasicBlock> loopBBSet = new HashSet<>();
                Queue<BasicBlock> loopBBs = new LinkedList<>(latchBBs);
                HashSet<BasicBlock> BBVisited = new HashSet<>(latchBBs);
                Loop loop = new Loop(header);
                loop.latchBBs.addAll(latchBBs);
                while (!loopBBs.isEmpty()) {
                    BasicBlock loopBB = loopBBs.poll();
                    loopBBSet.add(loopBB);
                    if (loopBB.equals(header)) {
                        continue;
                    }
                    for (BasicBlock nxtBB : loopBB.getPredecessors()) {
                        if (!BBVisited.contains(nxtBB)) {
                            BBVisited.add(nxtBB);
                            loopBBs.add(nxtBB);
                        }
                    }
                }
                for (BasicBlock fbb : bbs) {
                    if (loopBBSet.contains(fbb) && !loop.header.equals(fbb)) {
                        loop.bbs.add(fbb);
                    }
                }// System.out.println(loop.bbs);
                Collections.reverse(loop.bbs);
                loop.bbs.add(0, loop.header);
                loop.bbs.remove(loop.bbs.size() - 1);
                for (BasicBlock loopBB : loop.bbs) {
                    if (!f.getLoopInfo().bbToLoop.containsKey(loopBB)) {
                        f.getLoopInfo().bbToLoop.put(loopBB, loop);
                    } else {
                        f.getLoopInfo().bbToLoop.get(loopBB).parent = loop;
                    }
                    for (BasicBlock succ : loopBB.getSuccessors()) {
                        if (!BBVisited.contains(succ)) {
                            loop.exitBBs.add(loopBB);
                            break;
                        }
                    }
                }
                f.getLoopInfo().loops.add(loop);
            }
        }
        for (int i = f.getLoopInfo().loops.size() - 1; i >= 0; --i) {
            Loop loop = f.getLoopInfo().loops.get(i);
            if (loop.parent == null) {
                loop.depth = 1;
            } else {
                loop.depth = loop.parent.depth + 1;
            }
        }
    }

    public static class LoopInfo {
        public final ArrayList<Loop> loops = new ArrayList<>();

        public void init() {
            bbToLoop.clear();
            loops.clear();
        }

        public int BBLoopDepth(BasicBlock bb) {
            if (bbToLoop.get(bb) == null) {
                return 0;
            }
            return bbToLoop.get(bb).depth;
        }

        private final HashMap<BasicBlock, Loop> bbToLoop = new HashMap<>();
    }

    public static class Loop {
        public Loop parent = null;
        public BasicBlock header;
        public final HashSet<BasicBlock> exitBBs = new HashSet<>(); // 在循环内且存在一个跳出循环
        public final HashSet<BasicBlock> latchBBs = new HashSet<>();
        public final ArrayList<BasicBlock> bbs = new ArrayList<>();
        public int depth = 0;

        // simple for loop
        Phi phi;
        BinaryInst icmp;
        BinaryInst iAfter;
        BasicBlock exitBB;
        BasicBlock latchBB;
        Br headerBr;
        BasicBlock nxtBB;
        BasicBlock bodyEntryBB;
        HashMap<Value, Value> loopVariables =  new HashMap<>();

        // constant unroll
        int start;
        int end;
        int cnt;

        public Loop(BasicBlock header) {
            this.header = header;
            bbs.add(header);
        }
    }
}
