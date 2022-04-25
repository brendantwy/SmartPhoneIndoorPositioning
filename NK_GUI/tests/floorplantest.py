import matplotlib.pyplot as plt
from scipy.stats.kde import gaussian_kde
from csv_management.read_csv import readDeviceList
import numpy as np
import matplotlib.cm as cm

deviceXPosList = []
deviceYPosList = []
image_name = r"images\SR4F.png"
deviceList = readDeviceList()

for i in range(len(deviceList)):
    deviceXPosList.append(deviceList[i][1])
    deviceYPosList.append(deviceList[i][2])

k = gaussian_kde(np.vstack([deviceXPosList, deviceYPosList]))
xi, yi = np.mgrid[0:12:len(deviceXPosList) **
                  1.2*1j, 0:5:len(deviceYPosList)**1.2*1j]
zi = k(np.vstack([xi.flatten(), yi.flatten()]))

fig = plt.figure()
ax = fig.add_subplot(111)
heatmap = ax.pcolormesh(xi, yi, zi.reshape(xi.shape), alpha=0.2, cmap=cm.jet)
ax.set_xlim(0, 12)
ax.set_ylim(0, 5)

im = plt.imread(image_name)
plt.plot(deviceXPosList, deviceYPosList, 'ro',
         color='red', marker='o', markeredgecolor='black')
ax.imshow(im, extent=[0, 12, 0, 5], aspect='auto')
plt.colorbar(heatmap)

print(len(deviceXPosList))
plt.show()
