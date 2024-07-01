package it.manzolo.bluetoothwatcher.mqtt.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
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
import it.manzolo.bluetoothwatcher.mqtt.R
import it.manzolo.bluetoothwatcher.mqtt.database.DatabaseHelper
import it.manzolo.bluetoothwatcher.mqtt.database.DatabaseLog
import it.manzolo.bluetoothwatcher.mqtt.device.getDeviceBatteryPercentage
import it.manzolo.bluetoothwatcher.mqtt.enums.*
import it.manzolo.bluetoothwatcher.mqtt.error.UnCaughtExceptionHandler
import it.manzolo.bluetoothwatcher.mqtt.log.BluetoothWatcherLog
import it.manzolo.bluetoothwatcher.mqtt.log.MyRecyclerViewAdapter
import it.manzolo.bluetoothwatcher.mqtt.service.BluetoothService
import it.manzolo.bluetoothwatcher.mqtt.service.DiscoveryService
import it.manzolo.bluetoothwatcher.mqtt.service.LocationService
import it.manzolo.bluetoothwatcher.mqtt.utils.Date
import it.manzolo.bluetoothwatcher.mqtt.utils.Session
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    companion object {
        val TAG: String = MainActivity::class.java.simpleName
        private const val MAX_RETRY_ATTEMPTS = 3
    }

    private val logList: ArrayList<BluetoothWatcherLog> = ArrayList()
    private var recyclerView: RecyclerView? = null
    private val logViewAdapter = MyRecyclerViewAdapter(logList)
    private var retryAttempts = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            super.onCreate(savedInstanceState)

            registerLocalBroadcast()

            Thread.setDefaultUncaughtExceptionHandler(UnCaughtExceptionHandler(this))

            setContentView(R.layout.activity_main)
            setSupportActionBar(findViewById(R.id.toolbar))

            // Ask user for permission
            val permissions = arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.BLUETOOTH_SCAN,
            )
            ActivityCompat.requestPermissions(this, permissions, 0)

            // EventViewer
            recyclerView = findViewById(R.id.myRecyclerView)
            recyclerView!!.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
            recyclerView!!.adapter = logViewAdapter

            logList.add(0, BluetoothWatcherLog(Date.now(), "System ready", MainEvents.INFO))

            // Service enabled by default
            PreferenceManager.getDefaultSharedPreferences(applicationContext).edit().putBoolean("enabled", true).apply()
        }

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    private val localBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            when (intent?.action) {
                MainEvents.BROADCAST, MainEvents.INFO, MainEvents.ERROR, BluetoothEvents.ERROR, WebserviceEvents.ERROR, WebserviceEvents.INFO, DatabaseEvents.ERROR, MainEvents.DEBUG -> {
                    captureLog(intent.getStringExtra("message")!!, intent.action!!)
                }
                BluetoothEvents.DATA_RETRIEVED -> {
                    captureLog(intent.getStringExtra("message")!!, MainEvents.INFO)

                    val device = intent.getStringExtra("device")!!
                    val volt = intent.getStringExtra("volt")!!
                    val temp = intent.getStringExtra("tempC")!!

                    handleMqttPublish(device, volt, temp)
                }
                WebserviceEvents.DATA_SENT -> {
                    captureLog("Data sent " + intent.getStringExtra("message"), MainEvents.INFO)
                }
                LocationEvents.LOCATION_CHANGED -> {
                    captureLog("Obtain longitude:" + intent.getStringExtra("longitude")!! + " latitude:" + intent.getStringExtra("latitude")!!, MainEvents.INFO)
                }
            }
            recyclerView!!.layoutManager?.scrollToPosition(0)
            logViewAdapter.notifyItemInserted(0)
        }
    }

    private fun handleMqttPublish(device: String, volt: String, temp: String) {
        try {
            val session = Session(applicationContext)
            val bp = getDeviceBatteryPercentage(applicationContext)

            val jsonObject = JSONObject()
            jsonObject.put("voltage", volt)
            jsonObject.put("temperature", temp)
            jsonObject.put("tracker_battery", bp.toString())
            jsonObject.put("longitude", session.longitude)
            jsonObject.put("latitude", session.latitude)
            val mqttUrl = PreferenceManager.getDefaultSharedPreferences(applicationContext).getString("mqttUrl", "")
            val mqttPort = PreferenceManager.getDefaultSharedPreferences(applicationContext).getString("mqttPort", "")
            val userName = PreferenceManager.getDefaultSharedPreferences(applicationContext).getString("mqttUsername", "")
            val password = PreferenceManager.getDefaultSharedPreferences(applicationContext).getString("mqttPassword", "")
            val brokerUrl = "tcp://$mqttUrl:$mqttPort"
            val topic = device.replace(":", "").lowercase() + "/attributes"
            val client = MqttClient(brokerUrl, "bluetooth_watcher", MemoryPersistence())
            val options = MqttConnectOptions()
            options.isCleanSession = true
            options.userName = userName
            if (password != null) {
                options.password = password.toCharArray()
            }

            connectAndPublish(client, options, topic, jsonObject.toString())
        } catch (e: MqttException) {
            handleError(e.message)
        } catch (e: Exception) {
            handleError(e.message)
        }
    }

    private fun connectAndPublish(client: MqttClient, options: MqttConnectOptions, topic: String, payload: String) {
        try {
            client.connect(options)
            val message = MqttMessage(payload.toByteArray(Charsets.UTF_8))
            client.publish(topic, message)
            client.disconnect()
            retryAttempts = 0 // Reset retry attempts after a successful connection
        } catch (e: MqttException) {
            handleError(e.message)
            // Retry connection in case of failure
            retryConnection(client, options, topic, payload)
        }
    }

    private fun retryConnection(client: MqttClient, options: MqttConnectOptions, topic: String, payload: String) {
        if (retryAttempts < MAX_RETRY_ATTEMPTS) {
            retryAttempts++
            try {
                Thread.sleep(5000) // Wait for 5 seconds before retrying
                connectAndPublish(client, options, topic, payload)
            } catch (e: InterruptedException) {
                handleError(e.message)
            }
        } else {
            handleError("Max retry attempts reached. Giving up.")
        }
    }

    private fun handleError(message: String?) {
        val dbIntent = Intent(DatabaseEvents.ERROR)
        dbIntent.putExtra("message", message)
        applicationContext.sendBroadcast(dbIntent)
        Toast.makeText(applicationContext, "MQTT connection failed: $message", Toast.LENGTH_LONG).show()
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
                showBackupDialog()
                true
            }
            R.id.action_dbrestore -> {
                showRestoreDialog()
                true
            }
            R.id.action_clear_log -> {
                clearLog()
                true
            }
            else -> super.onOptionsItemSelected(menuItem)
        }
    }

    private fun showBackupDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Are you sure?")
        builder.setPositiveButton("YES") { _, _ ->
            val db = DatabaseHelper(applicationContext)
            db.backup()
        }
        builder.setNegativeButton("NO", null)
        builder.create().show()
    }

    private fun showRestoreDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Are you sure?")
        builder.setPositiveButton("YES") { _, _ ->
            val db = DatabaseHelper(applicationContext)
            db.restore()
        }
        builder.setNegativeButton("NO", null)
        builder.create().show()
    }

    private fun clearLog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Are you sure?")
        builder.setPositiveButton("YES") { _, _ ->
            logList.clear()
            logViewAdapter.notifyDataSetChanged()
            val db = DatabaseLog(applicationContext)
            db.open()
            db.clear()
            db.close()
            Toast.makeText(applicationContext, "Log cleared", Toast.LENGTH_SHORT).show()
        }
        builder.setNegativeButton("NO", null)
        builder.create().show()
    }

    private fun registerLocalBroadcast() {
        val intentFilter = IntentFilter()
        intentFilter.addAction(MainEvents.BROADCAST)
        intentFilter.addAction(MainEvents.INFO)
        intentFilter.addAction(MainEvents.ERROR)
        intentFilter.addAction(MainEvents.DEBUG)
        intentFilter.addAction(BluetoothEvents.DATA_RETRIEVED)
        intentFilter.addAction(BluetoothEvents.ERROR)
        intentFilter.addAction(WebserviceEvents.ERROR)
        intentFilter.addAction(WebserviceEvents.INFO)
        intentFilter.addAction(DatabaseEvents.ERROR)
        intentFilter.addAction(LocationEvents.LOCATION_CHANGED)
        registerReceiver(localBroadcastReceiver, intentFilter)
    }

    private fun captureLog(message: String, type: String) {
        logList.add(0, BluetoothWatcherLog(Date.now(), message, type))
        val db = DatabaseLog(applicationContext)
        db.open()
        db.createRow(Date.now(), message, type) // Ensure this method exists in DatabaseLog
        db.close()
    }
}