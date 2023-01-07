package utils;

import java.util.Iterator;

public class IList<Value, Holder> implements Iterable<IList.INode<Value, Holder>> {
    private final Holder holder;
    private final INode<Value, Holder> head;
    private final INode<Value, Holder> tail;
    private int nodeNum = 0;

    public IList(Holder holder) {
        this.holder = holder;
        this.head = new INode<>(this);
        this.tail = new INode<>(this);
        head.next = tail;
        tail.prev = head;
    }

    public void clear() {
        head.next = tail;
        tail.prev = head;
        this.nodeNum = 0;
    }

    public boolean isEmpty() {
        return head.next == tail && tail.prev == head;
    }

    public Holder getHolder() {
        return holder;
    }

    public INode<Value, Holder> getEntry() {
        return head.next;
    }

    public INode<Value, Holder> getLast() {
        return tail.prev;
    }

    public int getNodeNum() {
        return nodeNum;
    }

    @Override
    public Iterator<INode<Value, Holder>> iterator() {
        return new IIterator(head, tail);
    }

    public static class INode<Value, Holder> {
        private final boolean isGuard;
        private final Value value;
        private INode<Value, Holder> prev = null;
        private INode<Value, Holder> next = null;
        private IList<Value, Holder> parent = null;

        public INode(IList<Value, Holder> parent) {
            this.parent = parent;
            this.value = null;
            this.isGuard = true;
        }

        public INode(Value value) {
            this.value = value;
            this.isGuard = false;
        }

        public Value getValue() {
            return value;
        }

        public void setParent(IList<Value, Holder> parent) {
            this.parent = parent;
        }

        public IList<Value, Holder> getParent() {
            return parent;
        }

        public void insertAfter(INode<Value, Holder> prev) {
            this.parent = prev.parent;
            ++this.parent.nodeNum;
            this.prev = prev;
            this.next = prev.next;
            prev.next = this;
            if (this.next != null) this.next.prev = this;
        }

        public void insertBefore(INode<Value, Holder> next) {
            this.parent = next.parent;
            ++this.parent.nodeNum;
            this.prev = next.prev;
            this.next = next;
            next.prev = this;
            if (this.prev != null) this.prev.next = this;
        }

        public void insertAtEntry(IList<Value, Holder> father) {
            this.setParent(father);
            insertAfter(father.head);
        }

        public void insertAtEnd(IList<Value, Holder> father) {
            this.setParent(father);
            insertBefore(father.tail);
        }

        public void removeSelf() {
            this.next.prev = this.prev;
            this.prev.next = this.next;
            --this.parent.nodeNum;
            this.parent = null;
        }

        public boolean isGuard() {
            return isGuard;
        }

        public INode<Value, Holder> getPrev() {
            return prev;
        }

        public INode<Value, Holder> getNext() {
            return next;
        }

        public boolean isEntry() {
            return this.equals(this.getParent().getEntry());
        }
    }

    class IIterator implements Iterator<INode<Value, Holder>> {
        INode<Value, Holder> head;
        INode<Value, Holder> tail;
        INode<Value, Holder> cur;

        IIterator(INode<Value, Holder> head, INode<Value, Holder> tail) {
            this.head = head;
            this.tail = tail;
            cur = head;
        }

        @Override
        public boolean hasNext() {
            return cur.next != tail;
        }

        @Override
        public INode<Value, Holder> next() {
            cur = cur.next;
            return cur;
        }
    }
}
