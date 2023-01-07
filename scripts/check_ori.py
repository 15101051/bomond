import os

# 运行前先跑一遍compiler？
functional_tests_path = "D:/compiler/anlnmc/test/official/functional"
performance_tests_path = "D:/compiler/anlnmc/test/official/performance"
command = "D:/ProgramFiles/JAVA/jdk18.0.1.1/bin/java.exe \"-javaagent:D:/ProgramFiles/IntelliJ IDEA 2022.1.3/lib/idea_rt.jar=54398:D:/ProgramFiles/IntelliJ IDEA 2022.1.3/bin\" -Dfile.encoding=UTF-8 -classpath D:/compiler/anlnmc/out/production/anlnmc;D:/compiler/anlnmc/lib/antlr-4.10.1-complete.jar; Compiler"
llvm_path = "D:/ProgramFiles/llvm-source/llvm-project/build/bin/"

use_performance_test = False
tests_path = performance_tests_path if use_performance_test else functional_tests_path
for root, dirs, files in os.walk(tests_path):
    for file in files:
        if file.endswith(".sy") and file.startswith(""):
            print(file)
            status = os.system(command + " " + tests_path + "/" + file)
            if status != 0:
                break
            os.system(llvm_path + "llvm-as.exe " + "D:/compiler/anlnmc/frontendTest/testmy.ll")
            my_bc = "D:/compiler/anlnmc/frontendTest/testmy.bc"
            sylib_bc = "D:/compiler/anlnmc/frontendTest/sylib.bc"
            final_bc = "D:/compiler/anlnmc/frontendTest/final.bc"
            os.system(llvm_path + "llvm-link.exe " + "-o " + final_bc + " " + my_bc + " " + sylib_bc)
            my_out = "D:/compiler/anlnmc/frontendTest/testmy.out"
            in_file = file.replace(".sy", ".in")
            if files.count(in_file) != 0:
                # print("use input")
                in_file = tests_path + "/" + in_file
                x = os.system(llvm_path + "lli.exe " + final_bc + " > " + my_out + " < " + in_file)
            else:
                x = os.system(llvm_path + "lli.exe " + final_bc + " > " + my_out)
            x = (x % 256 + 256) % 256
            out = open(my_out)
            z = out.read()
            if z.endswith("\n\n"):
                z = z[0: -1]
            if z != "" and not z.endswith("\n"):
                z = z + "\n"
            x = z + str(x) + "\n"
            ans = open(tests_path + "/" + file.replace(".sy", ".out"))
            y = ans.read()
            if x != y:
                print(x)
                print(y)
                print("ERROR occur in " + file)
