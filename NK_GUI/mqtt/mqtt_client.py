import paho.mqtt.client as mqtt
from ui.mplwidget import MplWidget


'''
MQTT client class. Responsible for handling MQTT operations
'''


class MQTTClient(MplWidget, mqtt.Client):

    def __init__(self, widget, q):
        super(MQTTClient, self).__init__(widget)
        self.widget = widget
        self.q = q

    client_id = "Nippon_Koei_"
    broker = 'broker.emqx.io'
    topic = "NipponKoeiTP123"
    port = 1883
    data = ""
    connected = False
    '''
    call back function. executed when connected to MQTT broker
    '''

    def on_connect(self, mqttc, obj, flags, rc):
        if rc == 0:
            self.connected = True
            print("Connected to MQTT Broker!")
            self.subscribe(self.topic)
        else:
            self.connected = False
            print("Failed to connect, return code %d\n", rc)

    def on_connect_fail(self, mqttc, obj):
        self.connected = False
        print("Connect failed")
    '''
    call back function. executed when MQTT message is recieved by UI
    '''

    def on_message(self, mqttc, obj, msg):
        # decodes message into string
        self.data = msg.payload.decode()
        print(f"Received `{self.data}` from `{msg.topic}` topic")
        # pushes message into Queue to be handled by thread
        self.q.put(self.data)
        print(f"{self.data} PUSHED INTO QUEUE | SIZE {self.q.qsize()}")

    def on_publish(self, mqttc, obj, mid):
        print("mid: "+str(mid))

    def on_subscribe(self, mqttc, obj, mid, granted_qos):
        print("Subscribed: "+str(mid)+" "+str(granted_qos))

    def on_log(self, mqttc, obj, level, string):
        print(string)

    def on_disconnect(self):
        self.connected = False
        print("Disconnected from MQTT Broker!")

    def run(self):
        # Initiates connection to broker
        self.connect(self.broker, self.port)
        # runs forever loop on separate thread. ensures MQTT connection is kept alive
        self.loop_start()
