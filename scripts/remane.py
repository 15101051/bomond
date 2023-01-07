import os

test_path = "D:/compile/testcases/test"

files_tobe_rename = []
for root, dirs, files in os.walk(test_path):
    for file in files:
        if file[2] == '_':
            files_tobe_rename.append(file)

for file in files_tobe_rename:
    id_ori = int(file[0:2])
    id_new = id_ori + 290
    print(test_path + "/" + str(id_new) + file[2:])
    os.rename(test_path + "/" + file, test_path + "/" + str(id_new) + file[2:])
