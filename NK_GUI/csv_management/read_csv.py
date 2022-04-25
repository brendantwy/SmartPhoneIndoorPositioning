import pandas as pd


'''
Retrieves coordinates of nearest Tags
'''


def readNearTags(neartags):

    # Read CSV file
    df = pd.read_csv('data/Tag_Positions.csv',
                     usecols=['beacon_id', 'xPos', 'yPos'])
    # Append query result to list
    for i in range(len(neartags)-1):
        option = neartags[i][0].replace("�", "")
        results = df[df['beacon_id'] == option]
        neartags[i].append(results.iloc[0]['xPos'])
        neartags[i].append(results.iloc[0]['yPos'])
    print(neartags)
    # returns a list of nearest tags
    return neartags


'''
Retrieves coordinates of all calibrated tags
'''


def readTagList():
    tempList = []
    # Read CSV file
    df = pd.read_csv('data/Tag_Positions.csv',
                     usecols=['beacon_id', 'xPos', 'yPos'])
    # appends result to list
    for index, row in df.iterrows():
        tempList.append([row['beacon_id'], row['xPos'], row['yPos']])
    print(tempList)
    # returns a list of all calibrated tags
    return tempList


'''
Retrieves coordinates of all calibrated anchors
'''


def readAnchorList():
    tempList = []
    # Read csv file
    df = pd.read_csv('data/Anchor_Positions.csv',
                     usecols=['beacon_id', 'xPos', 'yPos'])
    # appends result to list
    for index, row in df.iterrows():
        tempList.append([row['beacon_id'], row['xPos'], row['yPos']])
    print(tempList)
    # returns a list of all calibrated anchors
    return tempList


'''
Retrieves location history of all devices
'''


def readDeviceList():
    tempList = []
    # Read csv file
    df = pd.read_csv('data/Phone_Positions.csv',
                     usecols=['device_name', 'xPos', 'yPos'])
    # appends result to list
    for index, row in df.iterrows():
        tempList.append([row['device_name'], row['xPos'], row['yPos']])
    print(tempList)
    # returns a list of device location history
    return tempList
