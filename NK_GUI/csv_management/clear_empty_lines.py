import sys

if len(sys.argv) != 2:
    print("Requires one file")
    exit()

try:

    lines = []
    with open(sys.argv[1], "r", encoding="utf-8") as file:
        lines.extend(file.readlines())

    with open(sys.argv[1], "w", encoding="utf-8") as file:
        for line in lines:
            if line != "\n":
                print(line)
                file.write(line)
    print("done!")
except:
    print("Possible missing file")