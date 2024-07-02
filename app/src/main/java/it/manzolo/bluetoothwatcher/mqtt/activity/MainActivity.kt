package it.manzolo.bluetoothwatcher.mqtt.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import it.manzolo.bluetoothwatcher.mqtt.App
import it.manzolo.bluetoothwatcher.mqtt.R
import it.manzolo.bluetoothwatcher.mqtt.database.DatabaseHelper
import it.manzolo.bluetoothwatcher.mqtt.database.DatabaseLog
import it.manzolo.bluetoothwatcher.mqtt.device.getDeviceBatteryPercentage
import it.manzolo.bluetoothwatcher.mqtt.enums.BluetoothEvents
import it.manzolo.bluetoothwatcher.mqtt.enums.DatabaseEvents
import it.manzolo.bluetoothwatcher.mqtt.enums.LocationEvents
import it.manzolo.bluetoothwatcher.mqtt.enums.MainEvents
import it.manzolo.bluetoothwatcher.mqtt.enums.WebserviceEvents
import it.manzolo.bluetoothwatcher.mqtt.error.UnCaughtExceptionHandler
import it.manzolo.bluetoothwatcher.mqtt.log.BluetoothWatcherLog
import it.manzolo.bluetoothwatcher.mqtt.log.MyRecyclerViewAdapter
import it.manzolo.bluetoothwatcher.mqtt.service.BluetoothService
import it.manzolo.bluetoothwatcher.mqtt.service.DiscoveryService
import it.manzolo.bluetoothwatcher.mqtt.service.LocationService
import it.manzolo.bluetoothwatcher.mqtt.utils.Date
import it.manzolo.bluetoothwatcher.mqtt.utils.Session
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    companion object {
        private const val PERMISSION_REQUEST_CODE = 0
        private const val MAX_LOG_ITEMS = 16
        private const val DEFAULT_LOG_ITEMS = 10
        private const val MQTT_CLIENT_ID = "bluetooth_watcher"
    }

    private val logList: ArrayList<BluetoothWatcherLog> = ArrayList(MAX_LOG_ITEMS)
    private lateinit var recyclerView: RecyclerView
    private lateinit var logViewAdapter: MyRecyclerViewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        setupPermissions()
        setupRecyclerView()

        logList.add(0, BluetoothWatcherLog(Date.now(), "System ready", MainEvents.INFO))
        PreferenceManager.getDefaultSharedPreferences(applicationContext).edit()
            .putBoolean("enabled", true).apply()

        App.cancelAllWorkers(this)
        App.scheduleBluetoothService(this)

        registerLocalBroadcast()
        Thread.setDefaultUncaughtExceptionHandler(UnCaughtExceptionHandler(this))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }

            R.id.action_trigger_bluetooth_service -> {
                startService(Intent(this, BluetoothService::class.java))
                true
            }

            R.id.action_trigger_mqtt_discovery_service -> {
                startService(Intent(this, DiscoveryService::class.java))
                true
            }

            R.id.action_trigger_location_service -> {
                startService(Intent(this, LocationService::class.java))
                true
            }

            R.id.action_dbbackup -> {
                showConfirmationDialog(
                    "Backup",
                    "Do you want to backup?",
                    DialogInterface.OnClickListener { _, _ ->
                        val db = DatabaseHelper(applicationContext)
                        db.backup()
                    })
                true
            }

            R.id.action_dbrestore -> {
                showConfirmationDialog(
                    "Restore",
                    "Do you want to restore?",
                    DialogInterface.OnClickListener { _, _ ->
                        val db = DatabaseHelper(applicationContext)
                        db.restore()
                    })
                true
            }

            R.id.action_clear_log -> {
                clearLog()
                true
            }

            else -> super.onOptionsItemSelected(menuItem)
        }
    }

    private fun setupPermissions() {
        val permissions = arrayOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.BLUETOOTH_SCAN
        )
        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.myRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        logViewAdapter = MyRecyclerViewAdapter(logList)
        recyclerView.adapter = logViewAdapter
    }

    private fun registerLocalBroadcast() {
        val intentFilters = arrayOf(
            getConnectionOkLocalIntentFilter(),
            getConnectionErrorLocalIntentFilter(),
            getWebserviceDataSentLocalIntentFilter(),
            getWebserviceErrorDataSentLocalIntentFilter(),
            getWebserviceInfoDataSentLocalIntentFilter(),
            getDebugLocalIntentFilter(),
            getUpgradeLocalIntentFilter(),
            getUpdateAvailableLocalIntentFilter(),
            getCheckUpdateLocalIntentFilter(),
            getNoUpdateLocalIntentFilter(),
            getUpdateErrorLocalIntentFilter(),
            getDatabaseErrorIntentFilter(),
            getLocationChangedIntentFilter(),
            getMainServiceIntentFilter(),
            getMainServiceInfoIntentFilter(),
            getMainServiceErrorIntentFilter()
        )

        intentFilters.forEach { filter ->
            applicationContext.registerReceiver(localBroadcastReceiver, filter)
        }
    }

    fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val networkCapabilities =
            connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private val localBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            intent?.let { intent ->
                when (intent.action) {
                    MainEvents.BROADCAST -> captureLog(
                        intent.getStringExtra("message")!!,
                        intent.getStringExtra("type")!!
                    )

                    MainEvents.INFO, WebserviceEvents.INFO ->
                        captureLog(intent.getStringExtra("message")!!, MainEvents.INFO)

                    MainEvents.ERROR, BluetoothEvents.ERROR, WebserviceEvents.ERROR ->
                        captureLog(intent.getStringExtra("message")!!, MainEvents.ERROR)

                    BluetoothEvents.DATA_RETRIEVED -> {
                        captureLog(intent.getStringExtra("message")!!, MainEvents.INFO)
                        if (isInternetAvailable(context)) {

                            val device = intent.getStringExtra("device")!!
                            val volt = intent.getStringExtra("volt")!!
                            val temp = intent.getStringExtra("tempC")!!

                            try {
                                val session = Session(applicationContext)
                                val bp = getDeviceBatteryPercentage(applicationContext)

                                val jsonObject = JSONObject().apply {
                                    put("voltage", volt)
                                    put("temperature", temp)
                                    put("tracker_battery", bp.toString())
                                    put("longitude", session.longitude)
                                    put("latitude", session.latitude)
                                }

                                val mqttUrl =
                                    PreferenceManager.getDefaultSharedPreferences(applicationContext)
                                        .getString("mqttUrl", "") ?: ""
                                val mqttPort =
                                    PreferenceManager.getDefaultSharedPreferences(applicationContext)
                                        .getString("mqttPort", "") ?: ""
                                val userName =
                                    PreferenceManager.getDefaultSharedPreferences(applicationContext)
                                        .getString("mqttUsername", "") ?: ""
                                val password =
                                    PreferenceManager.getDefaultSharedPreferences(applicationContext)
                                        .getString("mqttPassword", "")

                                val brokerUrl = "tcp://$mqttUrl:$mqttPort"
                                val topic = "${device.replace(":", "").lowercase()}/attributes"
                                val client =
                                    MqttClient(brokerUrl, MQTT_CLIENT_ID, MemoryPersistence())
                                val options = MqttConnectOptions().apply {
                                    isCleanSession = true
                                    this.userName = userName
                                    password?.let { setPassword(it.toCharArray()) }
                                }

                                client.connect(options)
                                client.publish(
                                    topic,
                                    MqttMessage(jsonObject.toString().toByteArray(Charsets.UTF_8))
                                )
                                client.disconnect()

                            } catch (e: MqttException) {
                                handleException("MQTT Exception: ${e.message}")
                            } catch (e: Exception) {
                                handleException("Exception: ${e.message}")
                            }
                        } else {
                            handleException("MQTT Exception: No internet available")
                        }
                    }

                    WebserviceEvents.DATA_SENT -> captureLog(
                        "Data sent ${intent.getStringExtra("message")}",
                        MainEvents.INFO
                    )

                    LocationEvents.LOCATION_CHANGED -> captureLog(
                        "Obtain longitude: ${
                            intent.getStringExtra(
                                "longitude"
                            )
                        } latitude: ${intent.getStringExtra("latitude")}", MainEvents.INFO
                    )

                    DatabaseEvents.ERROR -> captureLog(
                        intent.getStringExtra("message")!!,
                        MainEvents.ERROR
                    )

                    MainEvents.DEBUG -> {
                        if (PreferenceManager.getDefaultSharedPreferences(applicationContext)
                                .getBoolean("debugApp", false)
                        ) {
                            Toast.makeText(
                                applicationContext,
                                intent.getStringExtra("message"),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
                recyclerView.layoutManager?.scrollToPosition(0)
                logViewAdapter.notifyItemInserted(0)
            }
        }
    }

    private fun captureLog(message: String, type: String) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val debug = preferences.getBoolean("debugApp", false)

        DatabaseLog(applicationContext).apply {
            open()
            createRow(Date.now(), message, type)
            close()
        }

        synchronized(logList) {
            logList.add(0, BluetoothWatcherLog(Date.now(), message, type))
            if (logList.size > MAX_LOG_ITEMS) {
                logList.removeAt(MAX_LOG_ITEMS)
                logViewAdapter.notifyItemRangeRemoved(0, MAX_LOG_ITEMS)
            }
        }

        if (debug) {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun handleException(errorMessage: String) {
        applicationContext.sendBroadcast(Intent(DatabaseEvents.ERROR).apply {
            putExtra("message", errorMessage)
        })
    }

    private fun showConfirmationDialog(
        title: String,
        message: String,
        onClickListener: DialogInterface.OnClickListener
    ) {
        AlertDialog.Builder(this).apply {
            setTitle(title)
            setMessage(message)
            setPositiveButton("YES", onClickListener)
            setNegativeButton("NO") { _, _ -> }
        }.create().show()
    }

    private fun clearLog() {
        val size = logList.size
        logList.clear()
        DatabaseLog(applicationContext).apply {
            open()
            clear()
            close()
        }
        logViewAdapter.notifyItemRangeRemoved(0, size)
        Toast.makeText(applicationContext, "Log Cleared", Toast.LENGTH_SHORT).show()
    }

    // Intent filters
    private fun getConnectionOkLocalIntentFilter() = IntentFilter(BluetoothEvents.DATA_RETRIEVED)

    private fun getConnectionErrorLocalIntentFilter() = IntentFilter(BluetoothEvents.ERROR)

    private fun getWebserviceDataSentLocalIntentFilter() = IntentFilter(WebserviceEvents.DATA_SENT)

    private fun getWebserviceErrorDataSentLocalIntentFilter() = IntentFilter(WebserviceEvents.ERROR)

    private fun getWebserviceInfoDataSentLocalIntentFilter() = IntentFilter(WebserviceEvents.INFO)

    private fun getDebugLocalIntentFilter() = IntentFilter(MainEvents.DEBUG)

    private fun getUpgradeLocalIntentFilter() = IntentFilter(WebserviceEvents.APP_UPDATE)

    private fun getUpdateAvailableLocalIntentFilter() = IntentFilter(WebserviceEvents.APP_AVAILABLE)

    private fun getCheckUpdateLocalIntentFilter() = IntentFilter(WebserviceEvents.APP_CHECK_UPDATE)

    private fun getNoUpdateLocalIntentFilter() =
        IntentFilter(WebserviceEvents.APP_NO_AVAILABLE_UPDATE)

    private fun getUpdateErrorLocalIntentFilter() = IntentFilter(WebserviceEvents.APP_UPDATE_ERROR)

    private fun getDatabaseErrorIntentFilter() = IntentFilter(DatabaseEvents.ERROR)

    private fun getLocationChangedIntentFilter() = IntentFilter(LocationEvents.LOCATION_CHANGED)

    private fun getMainServiceIntentFilter() = IntentFilter(MainEvents.BROADCAST)

    private fun getMainServiceInfoIntentFilter() = IntentFilter(MainEvents.INFO)

    private fun getMainServiceErrorIntentFilter() = IntentFilter(MainEvents.ERROR)

    override fun onDestroy() {
        super.onDestroy()
        applicationContext.unregisterReceiver(localBroadcastReceiver)
    }
}