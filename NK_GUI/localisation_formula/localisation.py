import numpy as np
from numpy.linalg import inv
import math
from threading import Thread

'''
Calculates estimated distance from phone to tag via RSSI
'''


def distanceMobileToTag(RSSI):
    txPower = -70
    N = 2  # N (Constant depends on the Environmental factor. Range 2-4)
    calcDist = ((txPower-(RSSI)))/(10*N)
    distance = pow(10, calcDist)
    return distance


'''
Weighted Least Sqaure algo
'''


def localisation(data):
    # Preassigned weight values
    w1 = 0.6
    w2 = 0.3
    w3 = 0.1
    distanceTag1 = distanceMobileToTag(float(data[0][1]))
    distanceTag2 = distanceMobileToTag(float(data[1][1]))
    distanceTag3 = distanceMobileToTag(float(data[2][1]))

    # initialize A
    matrix_A = np.array([[-2*(data[0][2]), -2*(data[0][3]), 1],
                        [-2*(data[1][2]), -2*(data[1][3]), 1],
                        [-2*(data[2][2]), -2*(data[2][3]), 1]])
    # initialize b
    matrix_b = np.array([[pow(distanceTag1, 2)-pow(data[0][2], 2)-pow(data[0][3], 2)],
                        [pow(distanceTag2, 2)-pow(data[1][2], 2) -
                         pow(data[1][3], 2)],
                        [pow(distanceTag3, 2)-pow(data[2][2], 2)-pow(data[2][3], 2)]])
    # initialize weights
    matrix_W = np.array([[w1, 0, 0], [0, w2, 0], [0, 0, w3]])
    # compute coordinates
    trans_matrix_A = matrix_A.transpose()
    inv_product_transA_A = inv(
        np.matmul(np.matmul(trans_matrix_A, matrix_W), matrix_A))
    product_transA_b = np.matmul(
        np.matmul(trans_matrix_A, matrix_W), matrix_b)
    theta = np.matmul(inv_product_transA_A, product_transA_b)

    return theta
