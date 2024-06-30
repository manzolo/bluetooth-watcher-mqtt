package it.manzolo.bluetoothwatcher.mqtt.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.preference.PreferenceManager
import it.manzolo.bluetoothwatcher.mqtt.bluetooth.BluetoothClient
import it.manzolo.bluetoothwatcher.mqtt.device.DebugData
import it.manzolo.bluetoothwatcher.mqtt.enums.BluetoothEvents
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

class BluetoothService : Service() {
    companion object {
        val TAG: String = BluetoothService::class.java.simpleName
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onBluetoothStartJob")
        if (Build.FINGERPRINT.contains("generic")) {
            DebugData().insertDebugData(applicationContext)
        } else {
            startBluetoothTask()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
    }

    private fun startBluetoothTask() {
        val preferences = getPreferences()
        val debug = preferences.getBoolean("debugApp", false)
        val enabled = preferences.getBoolean("enabled", true)
        val address = preferences.getString("devices", "")

        if (address.isNullOrBlank()) {
            sendBroadcast(BluetoothEvents.ERROR, "No devices in settings", debug)
            Log.e(TAG, "No devices in settings")
        } else {
            if (enabled) {
                try {
                    BtTask(applicationContext).execute()
                } catch (e: InterruptedException) {
                    Log.e(TAG, e.message.toString())
                }
            } else {
                sendBroadcast(BluetoothEvents.ERROR, "Service disabled in settings", debug)
                Log.w(TAG, "Service disabled in settings")
            }
        }
    }

    private fun getPreferences() = PreferenceManager.getDefaultSharedPreferences(this.applicationContext)

    private fun sendBroadcast(event: String, message: String, debug: Boolean) {
        val intent = Intent(event)
        intent.putExtra("message", message)
        this.applicationContext.sendBroadcast(intent)
        if (debug) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }
}

class BtTask(private val context: Context) {
    private val job: Job = Job()
    private val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    fun execute() {
        GlobalScope.launch(coroutineContext) {
            doInBackground(context)
        }
    }

    private suspend fun doInBackground(context: Context) {
        withContext(Dispatchers.IO) {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            val address = preferences.getString("devices", "")
            val bluetoothDevices = address?.split(",") ?: emptyList()

            for (deviceAddress in bluetoothDevices.map { it.trim() }) {
                repeat(3) { attempt ->
                    if (tryBluetoothConnection(context, deviceAddress)) {
                        delay(1000)
                        return@repeat
                    }
                }
            }
        }
    }

    private fun tryBluetoothConnection(context: Context, address: String): Boolean {
        return try {
            val bluetoothClient = BluetoothClient(context, address)
            bluetoothClient.retrieveData()
            true
        } catch (e: Exception) {
            Log.e(BluetoothService.TAG, e.message.toString())
            sendErrorBroadcast(context, e.message.toString())
            false
        }
    }

    private fun sendErrorBroadcast(context: Context, message: String) {
        val intent = Intent(BluetoothEvents.ERROR)
        intent.putExtra("message", message)
        context.sendBroadcast(intent)
    }
}