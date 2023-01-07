import os
import shutil
import sys

functional_path = "D:/compile/testcases/test/"
root_path = "D:/compile/scripts/"
asm_file = "D:/compile/output.txt"
command = "D:/ProgramFiles/JAVA/jdk8/bin/java.exe \"-javaagent:D:/ProgramFiles/IntelliJ IDEA Community Edition 2021.3.2/lib/idea_rt.jar=51669:D:/ProgramFiles/IntelliJ IDEA Community Edition 2021.3.2/bin\" -Dfile.encoding=UTF-8 -classpath D:/ProgramFiles/JAVA/jdk8/jre/lib/charsets.jar;D:/ProgramFiles/JAVA/jdk8/jre/lib/deploy.jar;D:/ProgramFiles/JAVA/jdk8/jre/lib/ext/access-bridge-64.jar;D:/ProgramFiles/JAVA/jdk8/jre/lib/plugin.jar;D:/ProgramFiles/JAVA/jdk8/jre/lib/resources.jar;D:/ProgramFiles/JAVA/jdk8/jre/lib/rt.jar;D:/compile/out/production/compile; Compiler -a"


def run(test_file, files):
    shutil.copyfile(functional_path + test_file, root_path + "testfile.txt")
    in_file = test_file.replace(".sy", ".in")
    my_out = root_path + "out.txt"
    if files.count(in_file) != 0:
        in_file = functional_path + in_file
        x = os.system(command + " > " + my_out + " < " + in_file)
    else:
        x = os.system(command + " > " + my_out)
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
    if len(sys.argv) >= 2:
        test_id = sys.argv[1]
    else:
        test_id = ""
    for root, dirs, all_files in os.walk(functional_path):
        for file in all_files:
            if file.endswith(".sy") and file.startswith(test_id) and "247" < file < "324" and not file.startswith("320") and not file.startswith("246"):
                print("testing : " + file)
                if run(file, all_files):
                    print("\033[1;32m" + "correct!!!" + "\033[1;0m")
                else:
                    print("\033[1;31m" + "wrong!!!" + "\033[1;0m")
                    break
