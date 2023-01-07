import sys

from run import run
import os

functional_path = "D:/compile/testcases/test/"

input_llvm_file = sys.argv[1]
case = ""
if len(sys.argv) > 2:
    case = sys.argv[2]
for root, dirs, all_files in os.walk(functional_path):
    for file in all_files:
        if file.endswith(".sy") and file[0:3] >= "000" and file.startswith(case):
            print("testing : " + file)
            if run(input_llvm_file, file, all_files):
                print("\033[1;32m" + "correct!!!" + "\033[1;0m")
            else:
                print("\033[1;31m" + "wrong!!!" + "\033[1;0m")
                break
