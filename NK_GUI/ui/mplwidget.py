# Imports
from PyQt5 import QtWidgets
from matplotlib.figure import Figure
from matplotlib.backends.backend_qt5agg import FigureCanvasQTAgg as Canvas
import matplotlib.pyplot as plt
import matplotlib.cm as cm
import matplotlib
import numpy as np

# Ensure using PyQt5 backend
from csv_management.read_csv import readTagList, readAnchorList, readDeviceList
from scipy.stats.kde import gaussian_kde


matplotlib.use('QT5Agg')


# Matplotlib canvas class to create figure
class MplCanvas(Canvas):
    def __init__(self):
        self.fig = Figure()
        self.ax = self.fig.add_subplot(111)
        Canvas.__init__(self, self.fig)
        Canvas.setSizePolicy(
            self, QtWidgets.QSizePolicy.Expanding, QtWidgets.QSizePolicy.Expanding)
        Canvas.updateGeometry(self)


'''
Matplotlib widget. Contains Matplot operations
'''


class MplWidget(QtWidgets.QWidget):
    def __init__(self, parent=None):
        self.image_name = r"images\SR4F.png"
        QtWidgets.QWidget.__init__(self, parent)  # Inherit from QWidget
        self.mplCanvas = MplCanvas()  # Create canvas object
        self.vbl = QtWidgets.QVBoxLayout()  # Set box for plotting
        self.vbl.addWidget(self.mplCanvas)  # adds widget to GUI
        self.setLayout(self.vbl)
        # plot checks if tags or anchors have been plotted before
        self.anchor_plot_check = False
        self.tag_plot_check = False
        self.anchor_xmax = 0
        self.anchor_ymax = 0
        self.plotAnchors()
        self.plotTags()
        # hashmap to keep track of devices
        self.device_hash_map = {}
        self.available_colours = ["black", "green", "magenta", "cyan"]

    '''
    plots calibrated tags
    '''

    def plotTags(self):
        tagList = readTagList()
        tag_id = []
        tagXPosList = []
        tagYPosList = []
        # gets tag coordinates
        for i in range(len(tagList)):
            tag_id.append(tagList[i][0])
            tagXPosList.append(tagList[i][1])
            tagYPosList.append(tagList[i][2])
        # clears axis if anchors are not plotted
        if self.anchor_plot_check is False:
            self.mplCanvas.ax.cla()
        # read floorplan image
        im = plt.imread(self.image_name)
        # set image to plot scale
        self.mplCanvas.ax.imshow(im, aspect='auto', extent=(
            0, self.anchor_xmax+2.5, 0, self.anchor_ymax+0.5))
        self.mplCanvas.ax.set_xlim(self.mplCanvas.ax.get_xlim()[
                                   0], self.mplCanvas.ax.get_xlim()[1])
        self.mplCanvas.ax.set_ylim(self.mplCanvas.ax.get_ylim()[
                                   0], self.mplCanvas.ax.get_ylim()[1])
        # set x and y intervals
        self.mplCanvas.ax.set_xticks(
            np.arange(0, self.anchor_xmax+2, 1))
        self.mplCanvas.ax.set_yticks(
            np.arange(0, self.anchor_ymax+0.5, 1))
        # plots tags
        self.mplCanvas.ax.scatter(
            tagXPosList, tagYPosList, color='blue', label='tag')
        # add legend
        self.mplCanvas.ax.legend(bbox_to_anchor=(1, 1), loc=2)
        # label points with respective tag names
        for i in range(len(tagList)):
            self.mplCanvas.ax.annotate(
                tag_id[i], (tagXPosList[i], tagYPosList[i]+0.1), ha="center")
        # draws scatter plot
        self.mplCanvas.draw_idle()
        self.mplCanvas.flush_events()
        self.tag_plot_check = True

    '''
    plots calibrated anchors
    '''

    def plotAnchors(self):
        anchorList = readAnchorList()
        anchor_id = []
        anchorXPosList = []
        anchorYPosList = []
        # get anchor coordinates
        for i in range(len(anchorList)):
            anchor_id.append(anchorList[i][0])
            anchorXPosList.append(anchorList[i][1])
            anchorYPosList.append(anchorList[i][2])
        # clears axis if tags are not plotted
        if self.tag_plot_check is False:
            self.mplCanvas.ax.cla()
        self.anchor_xmax = max(anchorXPosList)
        self.anchor_ymax = max(anchorYPosList)
        # read floorplan image
        im = plt.imread(self.image_name)
        # scale image to plot
        self.mplCanvas.ax.imshow(im, aspect='auto', extent=(
            0, max(anchorXPosList)+2.5, 0, max(anchorYPosList)+0.5))
        self.mplCanvas.ax.set_xlim(-1, 16)
        self.mplCanvas.ax.set_ylim(-1, 12)
        # set x and y intervals
        self.mplCanvas.ax.set_xticks(
            np.arange(0, max(anchorXPosList)+2, 1))
        self.mplCanvas.ax.set_yticks(
            np.arange(0, max(anchorYPosList)+0.5, 1))
        # plot anchors
        self.mplCanvas.ax.scatter(
            anchorXPosList, anchorYPosList, color='red', label='anchor')
        # label points with respective anchor names
        for i in range(len(anchorList)):
            self.mplCanvas.ax.annotate(
                anchor_id[i], (anchorXPosList[i], anchorYPosList[i]+0.1), ha="center")
        # add legend
        self.mplCanvas.ax.legend(bbox_to_anchor=(1, 1), loc=2)
        self.mplCanvas.fig.tight_layout()
        self.mplCanvas.ax.grid()
        # draws scatter plot
        self.mplCanvas.draw_idle()
        self.mplCanvas.flush_events()
        self.anchor_plot_check = True

    '''
    plots live phone coordinates
    '''

    def plotLiveMode(self, x, y, device_name):
        # clears axis
        self.mplCanvas.ax.cla()
        # replot anchors and tags
        self.plotAnchors()
        self.plotTags()
        # reset x y limits
        self.mplCanvas.ax.set_xlim(-1, self.mplCanvas.ax.get_xlim()[1])
        self.mplCanvas.ax.set_ylim(-1, self.mplCanvas.ax.get_ylim()[1])
        # checks if device exists
        if device_name in self.device_hash_map:
            print(f"{device_name} already added to hash map...")
            # updates coordinates if exists
            update_values = self.device_hash_map.get(device_name)
            update_values[0], update_values[1] = x, y
            self.device_hash_map.update({device_name: update_values})
        else:
            # adds new device to hashmap
            print(f"New device {device_name} detected! Adding to hash map...")
            colour = self.available_colours.pop()
            self.device_hash_map.update({device_name: [x, y, colour]})
        # plots devices with respective names
        for device, value in self.device_hash_map.items():
            self.mplCanvas.ax.scatter(
                value[0], value[1], color=value[2], label=device)

        self.mplCanvas.ax.legend(bbox_to_anchor=(1, 1), loc=2)
        self.mplCanvas.draw_idle()
        self.mplCanvas.flush_events()

    '''
    heatmap plot
    '''

    def plotHeatMapMode(self):
        deviceList = readDeviceList()
        deviceXPosList = []
        deviceYPosList = []
        for i in range(len(deviceList)):

            deviceXPosList.append(deviceList[i][1])
            deviceYPosList.append(deviceList[i][2])
        # Gussian Kernel Density Estimate for smoother colour gradient
        k = gaussian_kde(np.vstack([deviceXPosList, deviceYPosList]))
        # setting size of kernels
        xi, yi = np.mgrid[0:16:len(deviceXPosList) **
                          1.2*1j, 0:12:len(deviceYPosList)**1.2*1j]
        zi = k(np.vstack([xi.flatten(), yi.flatten()]))

        fig = plt.figure()
        ax = fig.add_subplot(111)
        # plots heatmap
        heatmap = ax.pcolormesh(xi, yi, zi.reshape(
            xi.shape), alpha=0.1, cmap=cm.jet)
        # read floorplan image to overlay heatmap
        im = plt.imread(self.image_name)
        # plots all past device coordinates
        plt.plot(deviceXPosList, deviceYPosList, 'ro',
                 color='red', marker='o', markeredgecolor='black')
        ax.imshow(im, extent=[0, self.anchor_xmax+2.5, 0,
                  self.anchor_ymax+0.5], aspect='auto')
        plt.colorbar(heatmap, label="Density")
        plt.title("Density Map")
        print(len(deviceXPosList))
        plt.show()
