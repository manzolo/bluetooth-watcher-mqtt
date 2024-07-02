package it.manzolo.bluetoothwatcher.mqtt.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.IBinder
import androidx.preference.PreferenceManager
import it.manzolo.bluetoothwatcher.mqtt.device.getDeviceBatteryPercentage
import it.manzolo.bluetoothwatcher.mqtt.enums.BluetoothEvents
import it.manzolo.bluetoothwatcher.mqtt.enums.DatabaseEvents
import it.manzolo.bluetoothwatcher.mqtt.enums.MainEvents
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
    }

    private val localBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            intent?.let { intent ->
                when (intent.action) {
                    BluetoothEvents.DATA_RETRIEVED -> {
                        //Log.e("ManzoloWHY",intent.getStringExtra("message")!!)
                        val intentLog = Intent(MainEvents.BROADCAST_LOG).apply {
                            putExtra("message", intent.getStringExtra("message")!!)
                            putExtra("type", MainEvents.INFO)
                        }
                        sendBroadcast(intentLog)
                        if (isInternetAvailable(context)) {
                            val device = intent.getStringExtra("device")!!
                            val volt = intent.getStringExtra("volt")!!
                            val temp = intent.getStringExtra("tempC")!!

                            try {
                                val bp = getDeviceBatteryPercentage(applicationContext)
                                val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
                                val longitude = preferences.getString("longitude", "N/A")
                                val latitude = preferences.getString("latitude", "N/A")

                                val jsonObject = JSONObject().apply {
                                    put("voltage", volt)
                                    put("temperature", temp)
                                    put("tracker_battery", bp.toString())
                                    put("longitude", longitude)
                                    put("latitude", latitude)
                                }

                                val mqttUrl =
                                    preferences
                                        .getString("mqttUrl", "") ?: ""
                                val mqttPort =
                                    preferences
                                        .getString("mqttPort", "") ?: ""
                                val userName =
                                    preferences
                                        .getString("mqttUsername", "") ?: ""
                                val password =
                                    preferences
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
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(BluetoothEvents.DATA_RETRIEVED)
        }
        registerReceiver(localBroadcastReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(localBroadcastReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun handleException(errorMessage: String) {
        applicationContext.sendBroadcast(Intent(DatabaseEvents.ERROR).apply {
            putExtra("message", errorMessage)
        })
    }

    private fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val networkCapabilities =
            connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

}