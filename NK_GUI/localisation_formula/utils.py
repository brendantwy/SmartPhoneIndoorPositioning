from csv_management.write_to_csv import initialiseCSV_Anchor, initialiseCSV_Tag, phoneCSV_Positioning
from csv_management.read_csv import readNearTags
from localisation_formula.localisation import localisation
from threading import Thread

'''
Parse Anchor or Tag mode MQTT message
'''


def parse_string_mode_A_T(string):
    device_list = []
    for i in range(len(string)-1):
        temp = string[i].split(",")
        temp[0] = temp[0].replace("�", "")
        print(f"Appending {temp}")
        device_list.append(temp)
    print(device_list)
    return device_list


'''
Parse Positioning mode MQTT message
'''


def parse_string_mode_P(string):
    device_list = []
    for i in range(len(string)):
        temp = string[i].split(",")
        temp[0] = temp[0].replace("�", "")
        print(f"Appending {temp}")
        device_list.append(temp)
    print(device_list)
    return device_list


'''
Do operation based on operation mode. plots and saves data into respective csv files
Loop runs forever while Queue is not empty
'''


def do_operation(operation_q, widget, i):

    while True:
        thread_num = i
        data = operation_q.get()
        device_list = []
        parsed_string = data.split("|")
        operation_mode = parsed_string[0]
        print(f"THREAD {thread_num} ASSIGNED {operation_mode}")
        temp = parsed_string[1].split(":")
        print(temp)
        if(operation_mode == "A"):
            device_list = parse_string_mode_A_T(temp)
            initialiseCSV_Anchor(device_list)
            widget.plotAnchors()
        if(operation_mode == "T"):
            device_list = parse_string_mode_A_T(temp)
            initialiseCSV_Tag(device_list)
            widget.plotTags()
        if(operation_mode == "P"):
            device_list = parse_string_mode_P(temp)
            print(device_list)
            taglist = readNearTags(device_list)
            theta = localisation(taglist)
            x = float(theta[0])
            y = float(theta[1])
            print(
                f"{taglist[0][0]}: {taglist[0][2]}, {taglist[0][3]} RSSI: {float(taglist[0][1])}")
            print(
                f"{taglist[1][0]}: {taglist[1][2]}, {taglist[1][3]} RSSI: {float(taglist[1][1])}")
            print(
                f"{taglist[2][0]}: {taglist[2][2]}, {taglist[2][3]} RSSI: {float(taglist[2][1])}")
            print(f"Phone coordinates: x = {float(x)}, y = {float(y)}")
            device_name = ''.join(taglist[3])
            graph_limx = widget.mplCanvas.ax.get_xlim()[1] - 1
            graph_limy = widget.mplCanvas.ax.get_ylim()[1] - 1
            if(x > 0 and y > 0 and x < graph_limx and y < graph_limy):
                widget.plotLiveMode(round(
                    float(x), 3), round(float(y), 3), device_name)
                device_info = [device_name, round(
                    float(x), 3), round(float(y), 3)]
                phoneCSV_Positioning(device_info)
            else:
                print("COORDINATE OFF GRID!")
        operation_q.task_done()
        print(f"END OF THREAD {thread_num}")


'''
Initialize threads for multi device support
'''


def init_threads(operation_q, ui):
    print("Assigning threads...")
    for i in range(4):
        # allocate thread to do_operation function
        worker = Thread(target=do_operation, args=(
            operation_q, ui.widgetLiveMap, i,))
        # Daemon allows thread to terminate upon completion of task
        worker.setDaemon(True)
        # starts thread
        worker.start()
        print(f"Thread {i} assigned!")
