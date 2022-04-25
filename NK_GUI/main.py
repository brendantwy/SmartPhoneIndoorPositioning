import sys
from queue import Queue
from PyQt5 import QtWidgets
from mqtt.mqtt_client import MQTTClient
from localisation_formula.utils import do_operation, init_threads
from threading import Thread
from ui.nippon_koei_gui import Ui_MainWindow

# set queue size to 100
operation_q = Queue(maxsize=100)

# Create the application object
app = QtWidgets.QApplication(sys.argv)

# Create the form object and occupy the UI
first_window = QtWidgets.QMainWindow()
ui = Ui_MainWindow()
ui.setupUi(first_window)

# Show form
first_window.show()
# initialize threads
init_threads(operation_q, ui)
# initialize MQTT client
mqttc = MQTTClient(ui.widgetLiveMap, operation_q)
# connects and runs client
rc = mqttc.run()
# debugging purpose to observe state of MQTT connection
print("rc: "+str(rc))
# Run the program
sys.exit(app.exec())
