package it.manzolo.bluetoothwatcher.mqtt.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.preference.PreferenceManager
import it.manzolo.bluetoothwatcher.mqtt.enums.BluetoothEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import kotlin.coroutines.CoroutineContext

class DiscoveryService : Service() {
    companion object {
        val TAG: String = DiscoveryService::class.java.simpleName
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onDiscoveryStartJob")
        startDiscoveryTask()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
    }

    private fun startDiscoveryTask() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this.applicationContext)
        val debug = preferences.getBoolean("debugApp", false)
        val enabled = preferences.getBoolean("enabled", true)
        val address = preferences.getString("devices", "")

        if (address!!.replace("\\s".toRegex(), "").isEmpty()) {
            val intent = Intent(BluetoothEvents.ERROR)
            // You can also include some extra data.
            intent.putExtra("message", "No devices in settings")
            this.applicationContext.sendBroadcast(intent)
            Log.e(TAG, "No devices in settings")
            if (debug) {
                Toast.makeText(this, "No devices in settings", Toast.LENGTH_LONG).show()
            }
        } else {
            if (enabled) {
                try {
                    val discoveryTask = DiscoveryTask(applicationContext)
                    discoveryTask.execute()
                } catch (e: InterruptedException) {
                    //e.printStackTrace()
                    Log.e(TAG, e.message.toString())
                    //Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                }
            } else {
                val intent = Intent(BluetoothEvents.ERROR)
                // You can also include some extra data.
                intent.putExtra("message", "Service disabled in settings")
                this.applicationContext.sendBroadcast(intent)
                Log.w(TAG, "Service disabled in settings")
                if (debug) {
                    Toast.makeText(this, "Service disabled in settings", Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
    }
}

class DiscoveryTask(
    private val context: Context,
) : CoroutineScope {
    private var job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job // to run code in Main(UI) Thread

    fun execute() = launch {
        //onPreExecute()
        doInBackground(context) // runs in background thread without blocking the Main Thread
        //onPostExecute(result)
    }

    private suspend fun doInBackground(vararg args: Context): String =
        withContext(Dispatchers.IO) { // to run code in Background Thread
            val context = args[0]

            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            val address = preferences.getString("devices", "")
            val bluetoothDevices = address!!.split(",")
            for (element in bluetoothDevices) {
                val bluetoothDeviceAddress = element.replace("\\s".toRegex(), "")
                loopOk@ for (attempt in 1..2) {
                    if (tryDiscoveryConnection(context, bluetoothDeviceAddress)) {
                        Handler(Looper.getMainLooper()).postDelayed({}, 1000)
                        break@loopOk
                    }
                }
            }
            return@withContext "OK"

        }

    private fun tryDiscoveryConnection(context: Context, address: String): Boolean {
        return try {

            val haId = address.replace(":", "").lowercase()
            val jsonObject = JSONObject()
            jsonObject.put("json_attributes_topic", "$haId/attributes")
            jsonObject.put("name", address)
            jsonObject.put("unique_id", haId)
            jsonObject.put("object_id", "bluetooth_tracker")

            val mqttUrl =
                PreferenceManager.getDefaultSharedPreferences(context)
                    .getString("mqttUrl", "")
            val mqttPort =
                PreferenceManager.getDefaultSharedPreferences(context)
                    .getString("mqttPort", "")
            val userName =
                PreferenceManager.getDefaultSharedPreferences(context)
                    .getString("mqttUsername", "")
            val password =
                PreferenceManager.getDefaultSharedPreferences(context)
                    .getString("mqttPassword", "")
            val brokerUrl = "tcp://$mqttUrl:$mqttPort"

            val topic = "homeassistant/device_tracker/" + haId + "/config"
            val client = MqttClient(brokerUrl, "bluetooth_watcher", MemoryPersistence())
            val options = MqttConnectOptions()
            options.isCleanSession = true
            options.userName = userName
            if (password != null) {
                options.password = password.toCharArray()
            }
            client.connect(options)

            val message =
                MqttMessage(jsonObject.toString().toByteArray(charset("UTF-8")))

            client.publish(topic, message)

            client.disconnect()

            true
        } catch (e: Exception) {
            Log.e(DiscoveryService.TAG, e.message.toString())
            false
        }
    }
}