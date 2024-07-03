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
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.util.concurrent.Executors

class MqttService : Service() {
    companion object {
        val TAG: String = MqttService::class.java.simpleName
        private const val MQTT_CLIENT_ID = "bluetooth_watcher"
        private const val MAX_RECONNECT_ATTEMPTS = 1 // Numero massimo di tentativi di riconnessione
    }

    private lateinit var mqttClient: MqttClient
    private val executorService = Executors.newFixedThreadPool(6) // Pool di thread con 6 thread
    private var reconnectAttempts = 0 // Contatore di tentativi di riconnessione

    override fun onCreate() {
        super.onCreate()
        registerLocalBroadcast()
        setupMqttClient()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        mqttClient.disconnect()
        executorService.shutdown()
        unregisterReceiver(localBroadcastReceiver)
    }

    private val localBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            intent?.let {
                when (it.action) {
                    BluetoothEvents.DATA_RETRIEVED -> {
                        val message = it.getStringExtra("message") ?: return
                        val device = it.getStringExtra("device") ?: return
                        val volt = it.getStringExtra("volt") ?: return
                        val temp = it.getStringExtra("tempC") ?: return

                        Log.d(TAG, message)

                        val logIntent = Intent(MainEvents.BROADCAST_LOG).apply {
                            putExtra("message", message)
                            putExtra("type", MainEvents.INFO)
                        }
                        context.sendBroadcast(logIntent)

                        if (isInternetAvailable(context)) {
                            executorService.submit {
                                handleMqttPublish(context, device, volt, temp)
                            }
                        } else {
                            handleException(context, "MQTT Exception: No internet available")
                        }
                    }

                    else -> {}
                }
            }
        }
    }

    private fun setupMqttClient() {
        try {
            val preferences = PreferenceManager.getDefaultSharedPreferences(this)
            val mqttUrl = preferences.getString("mqttUrl", "") ?: ""
            val mqttPort = preferences.getString("mqttPort", "") ?: ""
            val userName = preferences.getString("mqttUsername", "") ?: ""
            val password = preferences.getString("mqttPassword", "")

            val brokerUrl = "tcp://$mqttUrl:$mqttPort"
            mqttClient = MqttClient(brokerUrl, MQTT_CLIENT_ID, MemoryPersistence())
            connectMqttClient(userName, password)
        } catch (e: MqttException) {
            Log.e(TAG, "MQTT Connection Exception: ${e.message}")
        }
    }

    private fun connectMqttClient(userName: String, password: String?) {
        try {
            val options = MqttConnectOptions().apply {
                isCleanSession = true
                this.userName = userName
                password?.let { setPassword(it.toCharArray()) }
                connectionTimeout = 7 // Timeout di connessione di 7 secondi
                keepAliveInterval = 10 // Intervallo di keep-alive
            }

            mqttClient.connect(options)
            reconnectAttempts = 0 // Resetta il contatore dopo una connessione riuscita

        } catch (e: MqttException) {
            Log.e(TAG, "MQTT Connection Exception: ${e.message}")
            handleReconnect()
        }
    }

    private fun handleMqttPublish(context: Context, device: String, volt: String, temp: String) {
        try {
            if (!mqttClient.isConnected) {
                Log.d(TAG, "MQTT client not connected. Reconnecting...")
                handleReconnect()
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
            mqttClient.publish(
                topic,
                MqttMessage(jsonObject.toString().toByteArray(Charsets.UTF_8))
            )

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

    private fun handleReconnect() {
        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempts++
            reconnectAndRetry()
        } else {
            Log.e(TAG, "Reached maximum reconnect attempts.")
        }
    }

    private fun reconnectAndRetry() {
        try {
            setupMqttClient()
        } catch (e: MqttException) {
            Log.e(TAG, "Reconnection attempt failed: ${e.message}")
        }
    }

    private fun handleException(context: Context, message: String) {
        Log.e(TAG, message)
        val logIntent = Intent(MainEvents.BROADCAST_LOG).apply {
            putExtra("message", message)
            putExtra("type", MainEvents.ERROR)
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

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}