package backend.mc;

import backend.register.RegisterManager;
import midend.ir.Constant;
import midend.ir.GlobalVariable;
import midend.ir.Module;
import midend.ir.Type;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

public class MCOperand {

    public static class MCImm extends MCOperand {
        private final int imm;

        public MCImm(int imm) {
            assert canEncodeImm(imm);
            this.imm = imm;
        }

        public MCImm(int imm, boolean checkCanEncode) {
            assert !checkCanEncode || canEncodeImm(imm);
            this.imm = imm;
        }

        public static boolean canEncodeImm(int imm) {
            return -32768 <= imm && imm <= 32767;
        }

        public static MCImm HI(int val) {
            return new MCImm((val - LO(val).getImm()) >> 16);
        }

        public static MCImm LO(int val) {
            val = val % 65536;
            if (val > 32767) {
                val -= 65536;
            }
            if (val < -32768) {
                val += 65536;
            }
            return new MCImm(val);
        }

        public static int lowbit(int x) {
            return x & (-x);
        }

        public static int one_bit(int val) {
            if (val == lowbit(val)) {
                int cnt = 0;
                while (val != 1) {
                    ++cnt;
                    val = val >> 1;
                }
                return cnt;
            } else {
                return -1;
            }
        }

        public int getImm() {
            return imm;
        }

        @Override
        public String toString() {
            return String.valueOf(this.imm);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MCImm mcImm = (MCImm) o;
            return imm == mcImm.imm;
        }

        @Override
        public int hashCode() {
            return Objects.hash(imm);
        }
    }

    public static class MCReg extends MCOperand {

    }

    public static class MCPhyReg extends MCReg {

        private final RegisterManager.MCPhyRegTag tag;

        public MCPhyReg(RegisterManager.MCPhyRegTag tag) {
            this.tag = tag;
        }

        public RegisterManager.MCPhyRegTag getTag() {
            return tag;
        }

        @Override
        public String toString() {
            return "$" + tag;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MCPhyReg mcPhyReg = (MCPhyReg) o;
            return tag == mcPhyReg.tag;
        }

        @Override
        public int hashCode() {
            return Objects.hash(tag);
        }
    }

    public static class MCVirtualReg extends MCReg {
        private final String name;
        private static int curId = 0;

        public MCVirtualReg() {
            this.name = "$virtual" + ++curId;
        }

        public MCVirtualReg(String name) {
            this.name = "$virtual-" + name + ":" + ++curId;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

    public static class MCLabel extends MCOperand {
        private final String name;

        public MCLabel(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static abstract class MCGlobalData extends MCOperand {
        public abstract String getMIPS();

        public static void allocate(MCModule mcm, int startPos) {
            int curPos = startPos;
            Module m = Module.module;
            for (GlobalVariable gv : m.globalList) {
                if (gv.getInit() instanceof Constant.ConstantString) {
                    String origin = ((Constant.ConstantString) gv.getInit()).getString();
                    String content = origin.substring(1, origin.length() - 1);
                    ArrayList<String> pieces = new ArrayList<>(Arrays.asList(content.split("%d")));
                    for (int i = 0; i < pieces.size(); ++i) {
                        String str = pieces.get(i);
                        if (str.equals("") || mcm.strings.containsKey(str)) {
                            continue;
                        }
                        String name = gv.getName() + "_" + i;
                        MCOperand.MCString mcGD = new MCOperand.MCString(name, str, curPos);
                        mcm.strings.put(str, mcGD);
                        mcm.mcGlobalDatas.add(mcGD);
                        curPos += Constant.ConstantString.countLength(str) + 1;
                    }
                } else {
                    while (curPos % 4 != 0) {
                        ++curPos;
                    }
                    MCOperand.MCGlobalVariable mcGV = new MCOperand.MCGlobalVariable(gv.getName(),
                            gv.getInit(),
                            ((Type.PointerType) gv.getType()).getPointTo().needBytes(),
                            curPos);
                    mcm.lirGVs.put(gv, mcGV);
                    mcm.mcGlobalDatas.add(mcGV);
                    curPos += ((Type.PointerType) gv.getType()).getPointTo().needBytes();
                }
            }
        }
    }

    public static class MCGlobalVariable extends MCGlobalData {
        private final String name;
        private final Segs segs;
        private final int pos;

        public MCGlobalVariable(String name, Constant init, int needBytes, int pos) {
            this.name = name.startsWith("@") ? name.substring(1) : name;
            if (init != null) {
                segs = calcConstantSegs(init);
            } else {
                segs = new MCGlobalVariable.Segs(new MCGlobalVariable.Seg(MCGlobalVariable.Seg.SegType.space, needBytes));
            }
            this.pos = pos;
        }

        public Segs getSegs() {
            return segs;
        }

        public String getMIPS() {
            return this.name + ":\n" + this.segs;
        }

        public int getPos() {
            return pos;
        }

        private MCGlobalVariable.Segs calcConstantSegs(Constant constant) {
            if (constant instanceof Constant.ConstantInt) {
                return new MCGlobalVariable.Segs(new MCGlobalVariable.Seg(MCGlobalVariable.Seg.SegType.word, ((Constant.ConstantInt) constant).getValue()));
            } else {
                MCGlobalVariable.Segs res = new MCGlobalVariable.Segs();
                Constant.ConstantArray ca = (Constant.ConstantArray) constant;
                Type.ArrayType arrayType = (Type.ArrayType) ca.getType();
                for (int i = 0; i < arrayType.getLength(); ++i) {
                    if (ca.haveValue(i)) {
                        Constant child = ca.getValue(i);
                        res.mergeWith(calcConstantSegs(child));
                    } else {
                        res.mergeWith(new MCGlobalVariable.Segs(new MCGlobalVariable.Seg(MCGlobalVariable.Seg.SegType.space, arrayType.getChildNumOfAtomElements() * 4)));
                    }
                }
                return res;
            }
        }

        public static class Seg {
            public enum SegType { word, space }
            private int val;
            private final MCGlobalVariable.Seg.SegType type;

            private Seg(MCGlobalVariable.Seg.SegType type, int val) {
                this.type = type;
                this.val = val;
            }

            public SegType getType() {
                return type;
            }

            public int getVal() {
                return val;
            }
        }

        public static class Segs {
            private final ArrayList<Seg> segs = new ArrayList<>();

            private Segs(MCGlobalVariable.Seg seg) {
                this.segs.add(seg);
            }

            private Segs() {}

            private void mergeWith(MCGlobalVariable.Segs that) {
                if (this.segs.isEmpty()) {
                    this.segs.addAll(that.segs);
                    return;
                }
                MCGlobalVariable.Seg lastSeg = this.segs.get(this.segs.size() - 1);
                if (lastSeg.type == MCGlobalVariable.Seg.SegType.space && that.segs.get(0).type == MCGlobalVariable.Seg.SegType.space) {
                    lastSeg.val += that.segs.get(0).val;
                    for (int i = 1; i < that.segs.size(); ++i) {
                        this.segs.add(that.segs.get(i));
                    }
                } else {
                    this.segs.addAll(that.segs);
                }
            }

            @Override
            public String toString() {
                StringBuilder sb = new StringBuilder();
                for (MCGlobalVariable.Seg seg : this.segs) {
                    sb.append("\t.").append(seg.type).append("\t").append(seg.val).append("\n");
                }
                return sb.toString();
            }

            public ArrayList<Seg> getSegs() {
                return segs;
            }
        }
    }

    public static class MCString extends MCGlobalData {
        private final String name;
        private final String content;
        private final int pos;

        public MCString(String name, String content, int pos) {
            this.name = name.startsWith("@") ? name.substring(1) : name;
            this.content = content;
            this.pos = pos;
        }

        public int getPos() {
            return pos;
        }

        public String getMIPS() {
            return this.name + ":\n" + "\t.asciiz\t\"" + content + "\"\n";
        }

        public String getContent() {
            return content;
        }
    }
}
