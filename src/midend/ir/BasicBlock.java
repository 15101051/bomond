package midend.ir;

import utils.IList;

import java.util.ArrayList;

public class BasicBlock extends Value {
    public final IList.INode<BasicBlock, Function> node = new IList.INode<>(this);
    private final IList<Instruction, BasicBlock> list = new IList<>(this);
    private final ArrayList<BasicBlock> predecessors = new ArrayList<>();
    private final ArrayList<BasicBlock> successors = new ArrayList<>();
    private BasicBlock idominator;//被谁直接支配
    private final ArrayList<BasicBlock> idominateds = new ArrayList<>();//该bb直接支配的bb
    private final ArrayList<BasicBlock> dominators = new ArrayList<>();//该bb的支配者们
    private final ArrayList<BasicBlock> dominanceFrontier = new ArrayList<>();//支配边界
    private int dominanceLevel;

    public BasicBlock(String name, Function function) {
        super(Type.LabelType.getType(), name);
        if (function != null) {
            node.insertAtEnd(function.getList());
        }
    }

    public IList<Instruction, BasicBlock> getList() {
        return list;
    }

    public Function getParent() {
        return node.getParent().getHolder();
    }

    public void removeSelf() {
        ArrayList<Instruction> instructionsToBeDeleted = new ArrayList<>();
        for (IList.INode<Instruction, BasicBlock> iNode : this.list) {
            instructionsToBeDeleted.add(iNode.getValue());
        }
        for (Instruction instruction : instructionsToBeDeleted) {
            instruction.removeSelf();
        }
        this.node.removeSelf();
    }

    public ArrayList<BasicBlock> getPredecessors() {
        return predecessors;
    }

    public ArrayList<BasicBlock> getSuccessors() {
        return successors;
    }

    @Override
    public String toString() {
        return this.getName();
    }

    public String getLLVM() {
        return "label %" + this.getName();
    }

    public BasicBlock getIdominator() {
        return idominator;
    }

    public void setIdominator(BasicBlock idominator) {
        this.idominator = idominator;
    }

    public ArrayList<BasicBlock> getIdominateds() {
        return idominateds;
    }

    public ArrayList<BasicBlock> getDominators() {
        return dominators;
    }

    public ArrayList<BasicBlock> getDominanceFrontier() {
        return dominanceFrontier;
    }

    public void setDominanceLevel(int dominanceLevel) {
        this.dominanceLevel = dominanceLevel;
    }

    public int getDominanceLevel() {
        return dominanceLevel;
    }
}
