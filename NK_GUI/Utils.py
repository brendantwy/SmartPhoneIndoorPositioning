from writeCSV import initialiseCSV_Anchor, initialiseCSV_Tag
from readCSV import readNearTags, readTagList
from numpy import *


def do_operation(data, widget):
    device_list = []
    parsed_string = data.split("|")
    # print(parsed_string)
    operation_mode = parsed_string[0]
    temp = parsed_string[1].split(":")
    print(temp)
    if(operation_mode == "A"):
        for i in range(len(temp)-1):
            temp2 = temp[i].split(",")
            print(temp2)
            device_list.append(temp2)
        print(device_list)
        initialiseCSV_Anchor(device_list)
        widget.plotAnchors()
    if(operation_mode == "T"):
        for i in range(len(temp)-1):
            temp2 = temp[i].split(",")
            print(temp2)
            device_list.append(temp2)
        print(device_list)
        initialiseCSV_Tag(device_list)
        widget.plotTags()
    if(operation_mode == "P"):
        for i in range(len(temp)):
            temp2 = temp[i].split(",")
            print(temp2)
            device_list.append(temp2)
        print(device_list)
        taglist = readNearTags(device_list)
        x, y = localisation(taglist[0][2], taglist[0][3], taglist[1][2], taglist[1][3], taglist[2][2], taglist[2][3], int(
            taglist[0][1]), int(taglist[1][1]), int(taglist[2][1]))
        print("Coorinates:")
        print(str(taglist[0][0])+": " +
              str(taglist[0][2]) + "," + str(taglist[0][3]))
        print(str(taglist[1][0])+": " +
              str(taglist[1][2]) + "," + str(taglist[1][3]))
        print(str(taglist[2][0])+": " +
              str(taglist[2][2]) + "," + str(taglist[2][3]))
        print("RSSI:")
        print(int(taglist[0][1]))
        print(int(taglist[1][1]))
        print(int(taglist[2][1]))
        print(x)
        print(y)
        widget.plotLiveMode(x, y)


def distanceMobileToTag(RSSI):
    txPower = -64.5
    N = 2  # N (Constant depends on the Environmental factor. Range 2-4)
    calcDist = ((txPower-(RSSI)))/(10*N)
    distance = pow(10, calcDist)
    return distance


def localisation(tag1CoordinateX, tag1CoordinateY, tag2CoordinateX, tag2CoordinateY, tag3CoordinateX, tag3CoordinateY, rssiTag1, rssiTag2, rssiTag3):
    distanceTag1 = distanceMobileToTag(rssiTag1)
    distanceTag2 = distanceMobileToTag(rssiTag2)
    distanceTag3 = distanceMobileToTag(rssiTag3)
    matrixRow1A_X = 2*tag1CoordinateX-2*tag2CoordinateX
    matrixRow1A_Y = 2*tag1CoordinateY-2*tag2CoordinateY
    matrixRow2A_X = 2*tag1CoordinateX-2*tag3CoordinateX
    matrixRow2A_Y = 2*tag1CoordinateY-2*tag3CoordinateY

    matrixRow1B = (pow(tag1CoordinateX, 2)-pow(tag2CoordinateX, 2))+(pow(tag1CoordinateY,
                                                                         2)-pow(tag2CoordinateY, 2))+(pow(distanceTag2, 2)-pow(distanceTag1, 2))
    matrixRow2B = (pow(tag1CoordinateX, 2)-pow(tag3CoordinateX, 2))+(pow(tag1CoordinateY,
                                                                         2)-pow(tag3CoordinateY, 2))+(pow(distanceTag3, 2)-pow(distanceTag1, 2))
    matrixBArray = array([matrixRow1B, matrixRow2B])
    matrixB = reshape(matrixBArray, (2, 1))

    matrixATransposeArray = array(
        [matrixRow1A_X, matrixRow2A_X, matrixRow1A_Y, matrixRow2A_Y])
    matrixATranspose = reshape(matrixATransposeArray, (2, 2))

    multipleATb = matmul(matrixATranspose, matrixB)

    determinant = 1/(((matrixRow1A_X*matrixRow1A_X+matrixRow2A_X*matrixRow2A_X)*(matrixRow1A_Y*matrixRow1A_Y+matrixRow2A_Y*matrixRow2A_Y)) -
                     ((matrixRow1A_Y*matrixRow1A_X+matrixRow2A_Y*matrixRow2A_X)*(matrixRow1A_X*matrixRow1A_Y+matrixRow2A_X*matrixRow2A_Y)))

    multipleATAMatrixAdj_Array = array([(matrixRow1A_Y*matrixRow1A_Y+matrixRow2A_Y*matrixRow2A_Y), -(matrixRow1A_X*matrixRow1A_Y+matrixRow2A_X*matrixRow2A_Y), -(
        matrixRow1A_Y*matrixRow1A_X+matrixRow2A_Y*matrixRow2A_X), (matrixRow1A_X*matrixRow1A_X+matrixRow2A_X*matrixRow2A_X)])
    multipleATAMatrixAdj = reshape(multipleATAMatrixAdj_Array, (2, 2))

    multipleATAMatrixInverse = multipleATAMatrixAdj*(determinant)

    output = matmul(multipleATAMatrixInverse, multipleATb)

    return output[0], output[1]
