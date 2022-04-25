import csv
import random
from datetime import datetime
import pandas as pd

fieldnames = ['log_id', 'device_id', 'type', 'rssi', 'xPos', 'yPos', 'datetime']
listLogId = []
listBeaconId = []
listType = []
listRSSI = []
listXPos = []
listYPos = []
listDateTime = []


def initialiseCSV():
    df = pd.DataFrame(list(zip(listLogId, listBeaconId, listType, listRSSI, listXPos, listYPos, listDateTime)),
                      columns=fieldnames)
    df.to_csv('data/device.csv', encoding='utf-8', index=None)

#Takes the last log id and adds one during appending
def addItemToCSV():
    #TODO call mqtt and append

    df = pd.read_csv('data/device.csv')
    dff = df['log_id'].max()
    print(dff)
    listLogId.append(int(dff+1))
    listBeaconId.append(round(random.uniform(20, 25), 2))
    listType.append(random.randint(0,1))
    listRSSI.append(random.randint(-80, -30))
    listXPos.append(round(random.uniform(7, 9), 2))
    listYPos.append(round(random.uniform(0, 1), 2))
    listDateTime.append(str(datetime.now().strftime("%d/%m/%Y %H:%M:%S")))
    df = pd.DataFrame(list(zip(listLogId, listBeaconId, listType, listRSSI, listXPos, listYPos, listDateTime)),
                      columns=fieldnames)
    df.to_csv('data/device.csv', mode='a', encoding='utf-8', index=None, header=False)
    print(df)

initialiseCSV()
for i in range(1, 13):
    listLogId.append(i)
    listBeaconId.append(i)
    listType.append(3)
    listRSSI.append(random.randint(-80, -30))
    listXPos.append(round(random.uniform(7, 9), 2))
    listYPos.append(round(random.uniform(0, 1), 2))
    listDateTime.append(str(datetime.now().strftime("%d/%m/%Y %H:%M:%S")))

df = pd.DataFrame(list(zip(listLogId, listBeaconId, listType, listRSSI, listXPos, listYPos, listDateTime)),
                  columns=fieldnames)
df.to_csv('data/device.csv', mode='a', encoding='utf-8', index=None, header=False)



