package it.manzolo.bluetoothwatcher.mqtt.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.IBinder
import android.util.Log
import androidx.preference.PreferenceManager
import it.manzolo.bluetoothwatcher.mqtt.device.getDeviceBatteryPercentage
import it.manzolo.bluetoothwatcher.mqtt.enums.BluetoothEvents
import it.manzolo.bluetoothwatcher.mqtt.enums.MainEvents
import kotlinx.coroutines.*
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject

class MqttService : Service() {
    companion object {
        val TAG: String = MqttService::class.java.simpleName
        private const val MQTT_CLIENT_ID = "bluetooth_watcher"
        private const val MAX_RECONNECT_ATTEMPTS = 3
    }

    private lateinit var mqttClient: MqttClient
    private var reconnectAttempts = 0
    private val coroutineScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onCreate() {
        super.onCreate()
        registerLocalBroadcast()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(localBroadcastReceiver)
        coroutineScope.cancel()
        disconnectMqttClient()
    }

    private val localBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            intent?.let {
                if (it.action == BluetoothEvents.DATA_RETRIEVED) {
                    coroutineScope.launch {
                        setupMqttClient()

                        val message = it.getStringExtra("message") ?: return@launch
                        val device = it.getStringExtra("device") ?: return@launch
                        val volt = it.getStringExtra("volt") ?: return@launch
                        val temp = it.getStringExtra("tempC") ?: return@launch

                        Log.d(TAG, message)

                        withContext(Dispatchers.Main) {
                            sendLogBroadcast(context, message, MainEvents.INFO)
                        }

                        if (isInternetAvailable(context)) {
                            handleMqttPublish(context, device, volt, temp)
                        } else {
                            handleException(context, "MQTT Exception: No internet available")
                        }
                    }
                }
            }
        }
    }

    private suspend fun setupMqttClient() {
        withContext(Dispatchers.IO) {
            try {
                val preferences = PreferenceManager.getDefaultSharedPreferences(this@MqttService)
                val mqttUrl = preferences.getString("mqttUrl", "") ?: ""
                val mqttPort = preferences.getString("mqttPort", "") ?: ""
                val userName = preferences.getString("mqttUsername", "") ?: ""
                val password = preferences.getString("mqttPassword", "")

                val brokerUrl = "tcp://$mqttUrl:$mqttPort"
                mqttClient = MqttClient(brokerUrl, MQTT_CLIENT_ID, MemoryPersistence())
                connectMqttClient(userName, password)
            } catch (e: MqttException) {
                Log.e(TAG, "MQTT Connection Exception: ${e.message}")
                disconnectMqttClient()
            }
        }
    }

    private suspend fun connectMqttClient(userName: String, password: String?) {
        withContext(Dispatchers.IO) {
            try {
                val options = MqttConnectOptions().apply {
                    isCleanSession = true
                    this.userName = userName
                    password?.let { setPassword(it.toCharArray()) }
                    connectionTimeout = 7
                    keepAliveInterval = 10
                }

                mqttClient.connect(options)
                reconnectAttempts = 0
            } catch (e: MqttException) {
                Log.e(TAG, "MQTT Connection Exception: ${e.message}")
                handleReconnect()
            }
        }
    }

    private suspend fun handleMqttPublish(context: Context, device: String, volt: String, temp: String) {
        withContext(Dispatchers.IO) {
            try {
                if (!mqttClient.isConnected) {
                    Log.d(TAG, "MQTT client not connected. Reconnecting...")
                    handleReconnect()
                    return@withContext
                }

                val bp = getDeviceBatteryPercentage(context)
                val preferences = PreferenceManager.getDefaultSharedPreferences(context)
                val longitude = preferences.getString("longitude", "N/A") ?: "N/A"
                val latitude = preferences.getString("latitude", "N/A") ?: "N/A"

                val jsonObject = JSONObject().apply {
                    put("voltage", volt)
                    put("temperature", temp)
                    put("tracker_battery", bp.toString())
                    put("longitude", longitude)
                    put("latitude", latitude)
                }

                val topic = "${device.replace(":", "").lowercase()}/attributes"
                mqttClient.publish(topic, MqttMessage(jsonObject.toString().toByteArray(Charsets.UTF_8)))

                Log.d(TAG, jsonObject.toString())
            } catch (e: MqttException) {
                Log.e(TAG, "MQTT Exception: ${e.message}")
                handleException(context, "MQTT Exception: $e")
                e.printStackTrace()
                handleReconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Exception: ${e.message}")
                handleException(context, "Exception: $e")
                e.printStackTrace()
            }
        }
    }

    private suspend fun handleReconnect() {
        withContext(Dispatchers.IO) {
            if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                reconnectAttempts++
                reconnectAndRetry()
            } else {
                Log.e(TAG, "Reached maximum reconnect attempts. Data is now considered outdated.")
            }
        }
    }

    private suspend fun reconnectAndRetry() {
        withContext(Dispatchers.IO) {
            try {
                setupMqttClient()
            } catch (e: MqttException) {
                Log.e(TAG, "Reconnection attempt failed: ${e.message}")
            }
        }
    }

    private fun handleException(context: Context, message: String) {
        Log.e(TAG, message)
        sendLogBroadcast(context, message, MainEvents.ERROR)
    }

    private fun sendLogBroadcast(context: Context, message: String, type: String) {
        val logIntent = Intent(MainEvents.BROADCAST_LOG).apply {
            putExtra("message", message)
            putExtra("type", type)
        }
        context.sendBroadcast(logIntent)
    }

    private fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun registerLocalBroadcast() {
        val intentFilter = IntentFilter(BluetoothEvents.DATA_RETRIEVED)
        applicationContext.registerReceiver(localBroadcastReceiver, intentFilter)
    }

    private fun disconnectMqttClient() {
        try {
            if (this::mqttClient.isInitialized && mqttClient.isConnected) {
                mqttClient.disconnect()
            }
        } catch (e: MqttException) {
            Log.e(TAG, "Error during MQTT client disconnect: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}