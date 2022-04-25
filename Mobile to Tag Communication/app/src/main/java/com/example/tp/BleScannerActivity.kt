/**
 * App Flow for Indoor Localisation Phone Tracking using BLE:
 *
 * Context: Using Decawave tag-anchor hardware system
 *
 * SCANNING
 * 1. User toggles on Scan switch (Service)
 * 2. Loop activated (Service/WorkManager?)
 * (a) Continuously scans for 3 ble tags and stops scan
 * (b) Concurrently connect to each of the 3 tags
 * (c) Discover services in each tag
 * (d) Write request to read the 3 tags' location coordinates
 * (e) Run triangulation algorithm to get phone's coordinates
 * (f) Display and Store values into log/database
 * (g) Starts (a) again
 *
 *
 */

package com.example.tp
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.jetbrains.anko.alert
import android.content.ComponentName
import android.content.ServiceConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.activity.viewModels
import com.example.tp.ble.ScanningService.Companion.MAX_ANCHOR
import com.example.tp.ble.ScanningService.Companion.MAX_TAG
import com.example.tp.ble.ConnectionManager
import com.example.tp.ble.ScanningService
import com.example.tp.mqtt.Mqtt
import com.example.tp.repository.UserPreferencesRepository
import kotlinx.android.synthetic.main.activity_ble_scanner.*
import kotlinx.coroutines.*
import org.jetbrains.anko.backgroundColor
import kotlin.properties.Delegates
import kotlinx.coroutines.launch as launch


private const val ENABLE_BLUETOOTH_REQUEST_CODE = 1
private const val LOCATION_PERMISSION_REQUEST_CODE = 2

class BleScannerActivity : AppCompatActivity(), CoroutineScope by MainScope() {

    companion object{
        private val TAG = BleScannerActivity::class.simpleName
        lateinit var mqttC : Mqtt
        var OPERATION_MODE = 3
        const val ANCHOR_MODE = 1
        const val TAG_MODE = 2
        const val POSITIONING_MODE = 3
        const val TIMEOUT_PERIOD = 60000L
    }

    /*******************************************
     * Global variables
     *******************************************/

    private var mBound: Boolean = false
    private lateinit var scanService: ScanningService
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bleScanner :BluetoothLeScanner
    lateinit var alertLoading: AlertDialog.Builder
    lateinit var alertLoad: AlertDialog

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            val binder = service as ScanningService.ServiceStateBinder
            if (requestInternetAccess()){
                scanService = binder.getService()
                mBound = true
                mqttC = Mqtt()
                try{
                    mqttC.connect(this@BleScannerActivity)
                }
                catch(e:Exception){
                    Log.d(TAG, "$e")
                    Toast.makeText(this@BleScannerActivity,"Unable to connect to MQTT Broker. Please restart the application!", Toast.LENGTH_LONG).show()
                }
                bluetoothAdapter = scanService.bluetoothAdapter
                bleScanner = scanService.bleScanner
                scanService.onServiceResume()
            }
        }
        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }
    }

    private val scanViewModel: BleScannerViewModel by viewModels {
        BleScannerViewModelFactory(UserPreferencesRepository(applicationContext),
            applicationContext
        )
    }

    lateinit var dataPref: UserPreferencesRepository
    var isAnchorCalibrated by Delegates.notNull<Boolean>()
    var isTagCalibrated by Delegates.notNull<Boolean>()

    var isCalibrateVisible: Boolean = false
    var isEditMode: Boolean = false

    private var anchorsNum by Delegates.notNull<Int>()
    private var tagsNum by Delegates.notNull<Int>()

    /*******************************************
     * Bluetooth Properties
     *******************************************/

    private val isLocationPermissionGranted
        get() = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

    /*******************************************
     * Activity Lifecycles Override Functions
     *******************************************/

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ble_scanner)
        dataPref = UserPreferencesRepository(this)
        //Retrieve calibration status
        launch{
            dataPref = UserPreferencesRepository(applicationContext)
            if(dataPref.readNumberOfAnchors(applicationContext)!=null){
                isAnchorCalibrated = dataPref.readAnchorCalibrationStatus(applicationContext) == true
                isTagCalibrated = dataPref.readTagCalibrationStatus(applicationContext) == true
                anchorsNum = dataPref.readNumberOfAnchors(applicationContext)!!

                tagsNum = if(dataPref.readNumberOfTags(applicationContext)!=null){
                    dataPref.readNumberOfTags(applicationContext)!!
                } else{
                    3
                }
                dataPref.updateCalibrationStatus(applicationContext, isAnchorCalibrated, isTagCalibrated)
            }else{
                isAnchorCalibrated = false
                isTagCalibrated  = false
                anchorsNum = 4
                tagsNum = 3
            }
            checkCalibrateState(isAnchorCalibrated, isTagCalibrated)
            checkEditMode(isAnchorCalibrated)
            runOnUiThread {
                setupNumberPicker(R.id.numberPickerAnchor).value = anchorsNum
                setupNumberPicker(R.id.numberPickerTag).value = tagsNum
            }
        }

        doAlertDialog()
        switchScan.isClickable = false
        textViewSwitch.text = getString(R.string.not_calibrate_message)

        buttonCalibrateAnchor.setOnClickListener {
            if(setupNumberPicker(R.id.numberPickerAnchor).value==0){
                Toast.makeText(this, "Please pick a number higher than zero", Toast.LENGTH_SHORT).show()
            }
            else{
                launch{
                    dataPref.updateNumberOfAnchors(applicationContext, setupNumberPicker(R.id.numberPickerAnchor).value)
                    MAX_ANCHOR = setupNumberPicker(R.id.numberPickerAnchor).value
                    OPERATION_MODE = ANCHOR_MODE
                    buttonEventListener()
                }
            }
        }
        buttonCalibrateTag.setOnClickListener {
            if(setupNumberPicker(R.id.numberPickerTag).value==0){
                Toast.makeText(this, "Please pick a number higher than zero", Toast.LENGTH_SHORT).show()
            }
            else{
                launch{
                    MAX_TAG = setupNumberPicker(R.id.numberPickerTag).value
                    dataPref.updateNumberOfTags(applicationContext, setupNumberPicker(R.id.numberPickerTag).value)
                    OPERATION_MODE = TAG_MODE
                    buttonEventListener()
                }
            }
        }

        buttonShowMenu.setOnClickListener{
            checkCalibrateMenu()
        }

        buttonEditMode.setOnClickListener {
            checkEditMode(isAnchorCalibrated)
        }

        textViewTitle.text = "Welcome!"
        setupAutoScan()
        checkCalibrateMenu()
    }

    /*******************************************
     * UI Components
     *******************************************/

    private fun checkCalibrateMenu(){
        if(isCalibrateVisible){
            buttonShowMenu.text = getString(R.string.hide_calibration_menu)
            isCalibrateVisible = false
            constraintLayoutCalibrate.visibility = View.VISIBLE
        }
        else{
            buttonShowMenu.text = getString(R.string.show_calibration_menu)
            isCalibrateVisible = true
            constraintLayoutCalibrate.visibility = View.GONE
        }
    }

    private fun checkEditMode(anchorCalibrated: Boolean){
        if(isEditMode){
            //Pickers Editable here
            if(!anchorCalibrated){
                markButtonDisable(buttonCalibrateTag)
                numberPickerTag.isEnabled = false
                numberPickerTag.backgroundColor = ContextCompat.getColor(this, R.color.grey)
            }else{
                markButtonEnable(buttonCalibrateTag)
                numberPickerTag.isEnabled = true
                numberPickerTag.backgroundColor = ContextCompat.getColor(this, R.color.gui)
            }
            markButtonEnable(buttonCalibrateAnchor)
            numberPickerAnchor.isEnabled = true
            numberPickerAnchor.backgroundColor = ContextCompat.getColor(this, R.color.gui)
            buttonEditMode.background = getDrawable(R.drawable.edit_mode)
            textViewEditMode.text = "Edit Mode ON"
            isEditMode = false
        }
        else{
            //Pickers not editable here
            markButtonDisable(buttonCalibrateTag)
            markButtonDisable(buttonCalibrateAnchor)
            numberPickerAnchor.isEnabled = false
            numberPickerAnchor.backgroundColor = ContextCompat.getColor(this, R.color.grey)
            numberPickerTag.isEnabled = false
            numberPickerTag.backgroundColor = ContextCompat.getColor(this, R.color.grey)
            buttonEditMode.background = getDrawable(R.drawable.edit_off)
            textViewEditMode.text = "Edit Mode OFF"
            isEditMode = true
        }
    }

    private fun setupNumberPicker(idPick: Int) : NumberPicker {
        val numberPicker = findViewById<NumberPicker>(idPick)
        numberPicker.minValue = 3
        numberPicker.maxValue = 12
        numberPicker.wrapSelectorWheel = true
        return numberPicker
    }


    // Function to display ProgressBar
    // inside AlertDialog
    @SuppressLint("SetTextI18n")
    fun setProgressDialog() : LinearLayout {
        // Creating a Linear Layout
        val llPadding = 30
        val ll = LinearLayout(this)
        ll.orientation = LinearLayout.HORIZONTAL
        ll.setPadding(llPadding, llPadding, llPadding, llPadding)
        ll.gravity = Gravity.CENTER
        var llParam = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        llParam.gravity = Gravity.CENTER
        ll.layoutParams = llParam

        // Creating a ProgressBar inside the layout
        val progressBar = ProgressBar(this)
        progressBar.isIndeterminate = true
        progressBar.setPadding(0, 0, llPadding, 0)
        progressBar.layoutParams = llParam
        llParam = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        llParam.gravity = Gravity.CENTER

        // Creating a TextView inside the layout
        val tvText = TextView(this)
        tvText.text = "Loading ..."
        tvText.textSize = 20f
        tvText.layoutParams = llParam
        ll.addView(progressBar)
        ll.addView(tvText)
        return ll
    }

    private fun doAlertDialog(){
        alertLoading = AlertDialog.Builder(this)
        alertLoading.setView(setProgressDialog())
        alertLoading.setTitle("Test Dialog")
        alertLoading.setCancelable(false)
        alertLoad = alertLoading.create()
    }

    private fun disableUI(){
        markButtonDisable(buttonCalibrateTag)
        markButtonDisable(buttonCalibrateAnchor)
        markButtonDisable(switchScan)
    }

    private fun enableUI(){
        markButtonEnable(buttonCalibrateTag)
        markButtonEnable(buttonCalibrateAnchor)
        markButtonEnable(switchScan)
    }

    private fun markButtonDisable(button: Button) {
        button?.isEnabled = false
        button?.isClickable = false
        when(button.id){
            R.id.switchScan -> button?.highlightColor = ContextCompat.getColor(this, R.color.grey)
            else -> button?.setBackgroundColor(ContextCompat.getColor(this, R.color.grey))
        }
    }

    private fun markButtonEnable(button: Button) {
        button?.isEnabled = true
        button?.isClickable = true
        when(button.id){
            R.id.switchScan -> button?.highlightColor = ContextCompat.getColor(this, R.color.gui)
            else -> button?.setBackgroundColor(ContextCompat.getColor(this, R.color.gui))
        }
    }

    /**
     * Event Listeners
     */

    private fun checkCalibrateState(anchorCalibrated: Boolean, tagCalibrated: Boolean){
        if(!anchorCalibrated){
            Log.d(TAG, "None of the two are calibrated.")
            textViewStatusMessage.text = getString(R.string.none_calibrated)
            textViewSwitch.text = getString(R.string.not_calibrate_message)
            runOnUiThread {
                checkEditMode(anchorCalibrated)
                markButtonDisable(switchScan)
            }
        }
        else if (anchorCalibrated&&!tagCalibrated){
            textViewStatusMessage.text = getString(R.string.only_anchors)
            textViewSwitch.text = getString(R.string.not_calibrate_message)
            runOnUiThread {
                checkEditMode(anchorCalibrated)
                markButtonDisable(switchScan)
            }
        }
        else if(anchorCalibrated&&tagCalibrated){
            textViewStatusMessage.text = getString(R.string.calibrated_message)
            textViewSwitch.text = getString(R.string.auto_scan)
            runOnUiThread {
                checkEditMode(anchorCalibrated)
                markButtonEnable(switchScan)
            }
        }
    }

    private fun buttonEventListener(){
        when(OPERATION_MODE){
            ANCHOR_MODE -> {
                alertLoad.setTitle("Calibrating Anchors... Please wait...")
                alertLoad.show()
                runCalibrationCycle()
            }
            TAG_MODE -> {
                alertLoad.setTitle("Calibrating Tags... Please wait...")
                alertLoad.show()
                runCalibrationCycle()
            }
        }
    }

    private fun runCalibrationCycle(){
        var modeString = ""
        modeString = when (OPERATION_MODE) {
            ANCHOR_MODE -> {
                "Anchor Calibration completed!"
            }
            TAG_MODE -> {
                "Tag Calibration completed!"
            }
            else -> {
                "Live location tracking is now on."
            }
        }
        launch{
            try{
                runOnUiThread {
                    textViewStatusMessage.text = "Calibrating... Please wait..."
                    when(OPERATION_MODE){
                        ANCHOR_MODE, TAG_MODE -> {
                            disableUI()
                        }
                    }
                }
                withTimeout(TIMEOUT_PERIOD){ // 60 seconds
                    scanService.calibrateCycle()
                }
                runOnUiThread {
                    when(OPERATION_MODE){
                        ANCHOR_MODE -> {
                            enableUI()
                            isAnchorCalibrated = true
                            alertLoad.dismiss()
                        }
                        TAG_MODE -> {
                            enableUI()
                            isTagCalibrated = true
                            alertLoad.dismiss()
                        }
                    }
                    textViewStatusMessage.text = modeString
                }
                dataPref.updateCalibrationStatus(applicationContext, isAnchorCalibrated, isTagCalibrated)
                checkCalibrateState(isAnchorCalibrated, isTagCalibrated)
                ConnectionManager.coordinatesMap.clear()
                Log.d("CLEARED", "${ConnectionManager.coordinatesMap.size}")
            }catch (e: Exception){
                Log.d(TAG, "$e")
                alertLoad.setTitle("Timed out after 60 seconds... Please retry.")
                ConnectionManager.coordinatesMap.clear()
                textViewStatusMessage.text = "Connection timed out! Please retry!"
                delay(1500)
            }finally {
                alertLoad.dismiss()
                checkCalibrateState(isAnchorCalibrated, isTagCalibrated)
            }
        }
    }



    override fun onResume() {
        super.onResume()
        if(mBound){
            scanService.onServiceResume()
            if (!bluetoothAdapter.isEnabled) {
                promptEnableBluetooth()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            ENABLE_BLUETOOTH_REQUEST_CODE -> {
                if (resultCode != Activity.RESULT_OK) {
                    promptEnableBluetooth()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_DENIED) {
                    requestLocationPermission()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG,"Connecting to service")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isLocationPermissionGranted) {
            requestLocationPermission()
        }
        bindService()
    }

    private fun bindService(){
        // Bind to LocalService
        Intent(this, ScanningService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
        Log.d(TAG,"Connecting to service")
    }

    /*******************************************
     * UI Setup Listeners Scanning Related Functions
     *******************************************/

    /**
     * Modularise the Switch Listener into this function in case more functions are to be called.
     */
    private fun setupAutoScan(){
        setupSwitchListener()
        Log.d(TAG,"Auto-scan setup")
    }

    /**
     * Switch state change listener:
     *      Returns true if switch is turned on; which calls loop function
     *      which constantly checks for tags
     */
    private fun setupSwitchListener(){
        switchScan?.setOnCheckedChangeListener { _, isChecked ->
            if(isChecked){
                OPERATION_MODE = POSITIONING_MODE
                scanService.toggleOn = true
                Log.d(TAG,"Scan Started")
                textViewSwitch.text = getString(R.string.running)
                launch{
                    scanService.runLoopScan()
                }
            } else{
                scanService.toggleOn = false
                textViewSwitch.text = getString(R.string.auto_scan)
                Log.d(TAG,"Scan Stopped")
            }
            scanViewModel.changeSwitchState(isChecked)
        }
    }

    /*******************************************
     * Bluetooth permissions functions
     *******************************************/

    private fun Context.hasPermission(permissionType: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permissionType) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun Activity.requestPermission(permission: String, requestCode: Int) {
        ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
    }

    @SuppressLint("MissingPermission")
    private fun promptEnableBluetooth() {
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST_CODE)
        }
    }

    /**
     * This function prompts user for location access and directs them to their location settings page
     */
    private fun requestLocationPermission() {
        if (isLocationPermissionGranted) {
            return
        }
        runOnUiThread {
            alert {
                title = "Location permission required"
                message = "Starting from Android M (6.0), the system requires apps to be granted " +
                        "location access in order to scan for BLE devices."
                isCancelable = false
                positiveButton(android.R.string.ok) {
                    requestPermission(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        LOCATION_PERMISSION_REQUEST_CODE
                    )
                }
            }.show()
        }
    }

    /**
     * This function prompts user for internet access and directs them to their wifi settings page
     */
    private fun requestInternetAccess() : Boolean{
        if (checkForInternet(this)) {
            return true
        }
        else{
            runOnUiThread {
                alert {
                    title = "No internet access found!"
                    message = "This application has detected that you are not connected to the internet! This application requires internet access to work."
                    isCancelable = false
                    positiveButton(android.R.string.ok) {
                        startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                    }
                }.show()
            }
            return false
        }
    }


    /**
     * This function checks for internet access and returns a boolean value
     */
    private fun checkForInternet(context: Context): Boolean {


        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false

            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                else -> false
            }
        } else {
            @Suppress("DEPRECATION") val networkInfo =
                connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
        }
    }
}
