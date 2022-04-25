package com.example.tp.ble

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.*
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.example.tp.BleScannerActivity.Companion.ANCHOR_MODE
import com.example.tp.BleScannerActivity.Companion.OPERATION_MODE
import com.example.tp.BleScannerActivity.Companion.POSITIONING_MODE
import com.example.tp.BleScannerActivity.Companion.TAG_MODE
import com.example.tp.BleScannerActivity.Companion.mqttC
import com.example.tp.R
import com.example.tp.ble.ConnectionManager.coordinatesMap
import com.example.tp.ble.ConnectionManager.getCoordinateMap
import com.example.tp.model.LocationData
import com.example.tp.repository.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.NonCancellable.cancel
import kotlinx.coroutines.delay
import org.jetbrains.anko.runOnUiThread
import java.lang.StringBuilder
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.ArrayList

class ScanningService : Service(), CoroutineScope by MainScope() {

    /*******************************************
     * Global Variables
     *******************************************/

    companion object {
        private val TAG = ScanningService::class.simpleName
        var MAX_TAG = 0
        var MAX_ANCHOR = 0
        const val MQTT_TOPIC= "NipponKoeiTP123"

    }

    //Binder to be given to clients aka Activity/ViewModel
    private val binder = ServiceStateBinder()

    /*******************************************
     * Bluetooth Properties
     *******************************************/

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    private val scanSettingsForPositioning = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .setReportDelay(5000)
        .build()

    var tagList: MutableList<ScanResult> = mutableListOf()

    val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    var isScanning = false

    val scanResults = mutableListOf<ScanResult>()

    var toggleOn: Boolean = false

    var isCalibrated: Boolean = false

    lateinit var deviceName: String

    private val comparatorOne = ComparatorOne()

    private lateinit var mHandler: Handler

    lateinit var dataPrefs: UserPreferencesRepository

    /*******************************************
     * Extra Classes
     *******************************************/

    inner class ServiceStateBinder : Binder(){
        fun getService(): ScanningService = this@ScanningService
    }

    /**
     * Class that sorts the ScanResults based on RSSI values in descending order
     */
    class ComparatorOne: Comparator<ScanResult> {
        override fun compare(lhs: ScanResult?, rhs: ScanResult?): Int {
            if(lhs == null || rhs == null){
                return 0;
            }
            return if (rhs.rssi < lhs.rssi) -1 else if (rhs.rssi === lhs.rssi) 0 else 1
        }
    }

    /*******************************************
     * Activity Lifecycles Override Functions
     *******************************************/

    /**
     *  Overrides android's onCreate function to include event listeners,
     *  getting android device's name,
     *  initialising of Handler's Looper function,
     *  and datastore's user preferences.
     */
    override fun onCreate() {
        ConnectionManager.registerListener(connectionEventListener)

        //Gets device name from Android's Settings API
        deviceName = Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME)
        mHandler = Handler(Looper.getMainLooper())
        dataPrefs = UserPreferencesRepository(this)
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY //Start with null intent
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    fun onServiceResume(){
        ConnectionManager.registerListener(connectionEventListener)
    }

    /*******************************************
     * Scanner Service Functions
     *******************************************/

    /**
     * Returns a filter that checks for tag's uuid for tag
     */
    private fun buildTagFilter(): ScanFilter {
        return ScanFilter.Builder().setServiceData(
            ParcelUuid.fromString(getString(R.string.ble_uuid)),
            byteArrayOf(0x02, 0x08.toByte()),byteArrayOf(0xFF.toByte(),0x00)).build()
    }

    /**
     * Returns a filter that checks for tag's uuid for anchor
     */
    private fun buildAnchorFilter(): ScanFilter {
        return ScanFilter.Builder().setServiceData(
            ParcelUuid.fromString(getString(R.string.ble_uuid)),
            byteArrayOf(0x80.toByte(), 0x08.toByte()),byteArrayOf(0xF0.toByte(),0x00)).build()
    }

    /**
     * Adds the filter into a list of ScanFilters
     */
    private fun scanFilters(): List<ScanFilter>? {
        val list: MutableList<ScanFilter> = ArrayList()
        var scanFilterMAC: ScanFilter? = null
        when(OPERATION_MODE){
            ANCHOR_MODE -> scanFilterMAC = buildAnchorFilter()
            TAG_MODE, POSITIONING_MODE -> scanFilterMAC = buildTagFilter()
        }
        list.add(scanFilterMAC!!)
        return list
    }

    /**
     * Functions that takes in ScanResults and only taking in the 3 highest RSSI
     * Returns list that contains 3 highest RSSIs
     */
    @SuppressLint("MissingPermission")
    private fun get3RSSI(_tagList: List<ScanResult>){
        lateinit var finalOutput: String
        val builder = StringBuilder()

        //Sorts from highest to lowest rssi
        try{
            Collections.sort(_tagList, comparatorOne)
            tagList = _tagList.take(3) as MutableList<ScanResult>
            if (tagList.size == 3){
                for (i in tagList){
                    builder.append("${i.device.name},${i.rssi}:")
                }

                finalOutput = builder.toString()

                mqttC.publish(MQTT_TOPIC, "P|$finalOutput$deviceName")

                runOnUiThread {
                    Toast.makeText(this, finalOutput,Toast.LENGTH_LONG).show()
                }
            }
        }
        catch(e: Exception){
            Log.e(TAG,"$e")
        }
        if(tagList.isNotEmpty()){
            tagList.clear()
        }
    }

    /**
     * Function that takes in the 3 highest RSSIs and attempts to connect to all 3 of them
     */
    @SuppressLint("MissingPermission")
    private fun autoConnect(itemList: List<ScanResult>){
        Log.i("Main","Auto connecting...")
        try{
            for(i in itemList){
                if(coordinatesMap.containsKey(i.device.name)){
                    Log.d(TAG, "Exist: ${i.device.name} has been connected before and therefore will not be connected again.")
                    continue
                }
                else{
                    Log.d(TAG, "Found new device ${i.device.name}! Connecting to it...")
                    ConnectionManager.connect(i.device, this)
                }
            }
        }
        catch(e: Exception){
            Log.e(TAG,"Auto Connect Failed")
        }
    }

    suspend fun calibrateCycle(){
        lateinit var finalOutput: String
        val builder = StringBuilder()
        Log.d(TAG, "$MAX_TAG, $MAX_ANCHOR")
        isCalibrated = false
        while(!isCalibrated){
            when(OPERATION_MODE){
                ANCHOR_MODE -> {
                    Log.d(TAG, "$MAX_ANCHOR")
                    while (coordinatesMap.size < MAX_ANCHOR) {
                        runScanCycleForCalibration()
                    } //A|
                    isCalibrated = true
                    runOnUiThread {
                        Toast.makeText(this,"Anchors all Calibrated!",Toast.LENGTH_LONG).show()
                    }
                    Log.i("FINAL", "${getCoordinateMap()}")

                    for (anchor in coordinatesMap){
                        builder.append("${anchor.key}," +
                                "${anchor.value.xPos}," +
                                "${anchor.value.yPos}:")
                    }
                    finalOutput = builder.toString()
                    mqttC.publish(MQTT_TOPIC, "A|$finalOutput")
                }
                TAG_MODE ->{
                    Log.d(TAG, "$MAX_TAG")
                    while (coordinatesMap.size < MAX_TAG) {
                        runScanCycleForCalibration()
                    }
                    runOnUiThread {
                        Toast.makeText(this,"Tags Calibrated!",Toast.LENGTH_LONG).show()
                    }
                    isCalibrated = true

                    for (tag in coordinatesMap){
                        builder.append("${tag.key}," +
                                "${tag.value.xPos}," +
                                "${tag.value.yPos}:")
                    }
                    finalOutput = builder.toString()
                    mqttC.publish(MQTT_TOPIC, "T|$finalOutput")

                }
            }
            coordinatesMap.clear()
        }
    }

    /**
     * Function that calls bleScanner's start scan
     */
    @SuppressLint("MissingPermission")
    fun startBleScan() {
        Log.i(TAG, "Starting BLE Scan")

        //Resets lists
        scanResults.clear()

        //Starts scan based on operation mode
        when(OPERATION_MODE){
            ANCHOR_MODE, TAG_MODE -> bleScanner.startScan(scanFilters(), scanSettings, scanCallback)
            POSITIONING_MODE -> bleScanner.startScan(scanFilters(), scanSettingsForPositioning, scanCallback)
        }
        isScanning = true
    }

    /**
     * Function that calls bleScanner's stopScan function
     * Sets isScanning to false
     * Proceeds to call autoConnect function
     */
    @SuppressLint("MissingPermission")
    fun stopBleScan() {
        Log.i(TAG, "Stopping BLE Scan")
        bleScanner.stopScan(scanCallback)
        isScanning = false
        /***** autoConnect called here ************/
        when(OPERATION_MODE){
            ANCHOR_MODE, TAG_MODE -> {
                autoConnect(scanResults)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun checkForDuplicates(_scanResults: List<ScanResult>, _locationDatas: ConcurrentHashMap<String, LocationData>){
        Log.d(TAG,"Checking for duplicates...")
        var locationDataMap = _locationDatas
        var scanResultsList = _scanResults

        // Checks for duplicates
        for(i in 0..scanResultsList.size){
            when(OPERATION_MODE) {
                ANCHOR_MODE, TAG_MODE -> {
                    if (locationDataMap.containsKey(scanResultsList[i].device.name))
                    { // Means there is already a location tied to a scan result
                        Log.d(
                            "CALIBRATE",
                            "${scanResultsList[i].device.name} tied to a location already."
                        )
                    }
                }
            }
        }
    }

    /*******************************************
     * Bluetooth callback bodies
     *******************************************/

    /**
     * Sets ScanCallback that updates the recyclerview (removed as of now) as it scans
     */
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        @RequiresApi(Build.VERSION_CODES.O)
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            when(OPERATION_MODE) {
                ANCHOR_MODE, TAG_MODE->
                {
                    val indexQuery = scanResults.indexOfFirst { it.device.address == result.device.address }
                    if (indexQuery != -1) { // A scan result already exists with the same address
                        scanResults[indexQuery] = result
                    } else {
                        with(result.device) {
                            Log.i(
                                TAG,
                                "ADDED: Found BLE device! Name: ${name ?: "Unnamed"}, address: $address, RSSI: ${result.rssi}, ${
                                    result.rssi
                                }, TXPower: ${result.txPower}"
                            )
                        }
                        //DO NOT REMOVE.
                        scanResults.add(result)
                        Collections.sort(scanResults, comparatorOne)
                    }
                }
            }
        }
        
        @SuppressLint("MissingPermission")
        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            if (results != null) {
                get3RSSI(results)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("MainActivity","onScanFailed: code $errorCode")
        }
    }

    private val connectionEventListener by lazy {
        ConnectionEventListener().apply {
            onDisconnect = {
                Log.d(TAG,"Disconnect successful")
                checkForDuplicates(scanResults, getCoordinateMap())
            }
        }
    }

    /**
     * While the toggleOn is true, runs the scanning cycle.
     */
    suspend fun runLoopScan(){
        while(toggleOn){
            runScanCycleForPositioning()
        }
    }

    /**
     * Periodically runs the start and stop scan functions
     *
     *  NOTE: runCycle is separated cuz of readability purposes and editing purposes
     */
    private suspend fun runScanCycleForCalibration(){
        //Scan
        Log.i(TAG,"Calibration cycle is ON")
        if (isScanning){
            delay(2000L)
            stopBleScan()
        } else {
            startBleScan()
            delay(3000L)
            stopBleScan()
            delay(8000L)
        }
    }

    private suspend fun runScanCycleForPositioning(){
        //Scan
        Log.i(TAG,"Sending live location is ON")
        if (isScanning){
            delay(2000L)
            stopBleScan()
        } else {
            Log.d(TAG, "Start of 20 seconds cycle")
            startBleScan()
            delay(20000L)
            stopBleScan()
            Log.d(TAG, "End of 20 seconds cycle")
            delay(2000L)
        }
    }
}