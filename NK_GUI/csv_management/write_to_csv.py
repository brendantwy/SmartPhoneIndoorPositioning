import csv
import random
import pandas as pd
from datetime import datetime, date
import os


def initialise_AT_CSV(csv_type):
    id = []
    x = []
    y = []
    fieldnames = ['beacon_id', 'xPos', 'yPos']
    df = pd.DataFrame(list(zip(id, x, y)),
                      columns=fieldnames)
    df.to_csv('data/' + csv_type + '.csv', encoding='utf-8', index=True)


def initialize_Phone_CSV():
    id = []
    x = []
    y = []
    timestamp = []
    fieldnames = ['device_name', 'xPos', 'yPos', 'timestamp']
    df = pd.DataFrame(list(zip(id, x, y, timestamp)),
                      columns=fieldnames)
    df.to_csv('data/Phone_Positions.csv', encoding='utf-8', index=False)


def phoneCSV_Positioning(data):
    phone_Id = data[0]
    phone_X = float(data[1])
    phone_Y = float(data[2])
    curr_datetime = str(datetime.now().strftime("%b-%d-%Y %H:%M:%S"))
    output_path = "data/Phone_Positions.csv"
    df = pd.DataFrame([[phone_Id, phone_X, phone_Y,
                      curr_datetime]], columns=['device_name', 'xPos', 'yPos', 'timestamp'])

    if(os.path.exists(output_path)):
        head = False
    else:
        head = ['device_name', 'xPos', 'yPos', 'timestamp']

    df.to_csv(output_path, mode='a', header=head,
              encoding='utf-8', index=False, errors="ignore")


def initialiseCSV_Anchor(data):
    anchor_Id = []
    anchor_X = []
    anchor_Y = []
    fieldnames = ['beacon_id', 'xPos', 'yPos']
    for i in data:
        print(i[0])
        anchor_Id.append(i[0].replace("�", ""))
        anchor_X.append(i[1])
        anchor_Y.append(i[2])
    df = pd.DataFrame(list(zip(anchor_Id, anchor_X, anchor_Y)),
                      columns=fieldnames)
    with open("data/Anchor_Positions.csv", "w+") as f:
        df.to_csv(f, encoding='utf-8', index=True, errors="ignore")


def initialiseCSV_Tag(data):
    tag_Id = []
    tag_X = []
    tag_Y = []
    fieldnames = ['beacon_id', 'xPos', 'yPos']
    for i in data:
        print(i[0])
        tag_Id.append(i[0].replace("�", ""))
        tag_X.append(i[1])
        tag_Y.append(i[2])
    df = pd.DataFrame(list(zip(tag_Id, tag_X, tag_Y)),
                      columns=fieldnames)
    with open("data/Tag_Positions.csv", "w+") as f:
        df.to_csv(f, encoding='utf-8', index=True, errors="ignore")
