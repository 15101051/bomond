import os
import sys

functional_path = "D:/compile/testcases/test/"
frontend_test_path = "D:/compile/test/"
command = "D:/ProgramFiles/JAVA/jdk8/bin/java.exe \"-javaagent:D:/ProgramFiles/IntelliJ IDEA Community Edition 2021.3.2/lib/idea_rt.jar=50202:D:/ProgramFiles/IntelliJ IDEA Community Edition 2021.3.2/bin\" -Dfile.encoding=UTF-8 -classpath D:/compile/out/production/compile; Compiler -ea"


def run(llvm_file, test_file, files):
    os.system(command + " " + functional_path + test_file)
    my_llvm_path = frontend_test_path + llvm_file + ".ll"
    my_bc = frontend_test_path + "testmy.bc"
    sylib_bc = frontend_test_path + "sylib.bc"
    final_bc = frontend_test_path + "final.bc"
    os.system("llvm-as " + my_llvm_path + " -o " + my_bc)
    os.system("llvm-link " + sylib_bc + " " + my_bc + " -o " + final_bc)
    in_file = test_file.replace(".sy", ".in")
    my_out = frontend_test_path + "testmy.out"
    if files.count(in_file) != 0:
        in_file = functional_path + in_file
        x = os.system("lli " + final_bc + " > " + my_out + " < " + in_file)
    else:
        x = os.system("lli " + final_bc + " > " + my_out)
    my_return = (x % 256 + 256) % 256
    out = open(my_out)
    my_output = out.read().strip()
    ans_out = functional_path + test_file.replace(".sy", ".out")
    ans = open(ans_out).read().strip()
    last_enter = ans.rfind("\n")
    if last_enter == -1:
        ans_return = ans
        ans_output = ""
    else:
        ans_return = ans[last_enter:].strip()
        ans_output = ans[:last_enter].strip()
    my_return = str(my_return)
    """
    print(">" + my_return + "<")
    print(">" + ans_return + "<")
    print(">" + my_output + "<")
    print(">" + ans_output + "<")
    """
    res = my_return == ans_return and my_output == ans_output
    if res:
        return True
    else:
        print(">" + my_return + "<")
        print(">" + ans_return + "<")
        print(">" + my_output + "<")
        print(">" + ans_output + "<")
        return False


if __name__ == '__main__':
    input_llvm_file = sys.argv[1]
    test_id = sys.argv[2]
    for root, dirs, all_files in os.walk(functional_path):
        for file in all_files:
            if file.endswith(".sy") and file.startswith(test_id):
                print("testing : " + file)
                if run(input_llvm_file, file, all_files):
                    print("correct!!!")
                else:
                    print("wrong!!!")
