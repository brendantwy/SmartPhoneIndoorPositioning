package com.example.tp.model

data class BLEDetails (
    val beaconID:String,
    val type:String,
    val RSSI:Double,
    val beaconCoordinateX:Double,
    val beaconCoordinateY:Double
    )