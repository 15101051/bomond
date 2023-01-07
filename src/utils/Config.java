package utils;

import backend.register.RegisterAllocator.RegisterAllocatorChoice;
import backend.register.SpillScheme.SpillSchemeChoice;

public class Config {
    public static final boolean submit = false;
    public static final boolean optimize = false;
    public static final String inputFileName = "testfile.txt";
    public static final String errorOutputFileName = "error.txt";
    public static final String outputFileName = "output.txt";
    public static final String mipsFileName = "mips.txt";
    public static final boolean allocateAllRegisters = false;
    public static final boolean allocateOtherPhyRegs = false;
    public static final boolean collectGEPConstant = false;
    public static final boolean mergeBranchWithIcmp = false;
    public static final SpillSchemeChoice spillChoice = SpillSchemeChoice.CountInstr;
    public static final boolean useT0InsteadOfVRInSingleLLVMInstr = true;
    public static final RegisterAllocatorChoice registerAllocatorChoice = RegisterAllocatorChoice.full;
    public static final boolean mainReturnVal = true;
    public static final String statisticFileName = "statistic.txt";
}
