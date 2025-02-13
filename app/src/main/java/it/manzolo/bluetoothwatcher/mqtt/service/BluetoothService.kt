package it.manzolo.bluetoothwatcher.mqtt.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import it.manzolo.bluetoothwatcher.mqtt.R
import it.manzolo.bluetoothwatcher.mqtt.bluetooth.BluetoothClient
import it.manzolo.bluetoothwatcher.mqtt.device.DebugData
import it.manzolo.bluetoothwatcher.mqtt.enums.BluetoothEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

class BluetoothWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        // Avvia il servizio come foreground service
        val intent = Intent(applicationContext, BluetoothService::class.java)
        intent.action = BluetoothService.ACTION_START_FOREGROUND
        applicationContext.startForegroundService(intent)

        // Schedule the next work
        val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val seconds = preferences.getString("bluetoothServiceEverySeconds", "90")?.toLong() ?: 90L
        val workRequest = OneTimeWorkRequestBuilder<BluetoothWorker>()
            .setInitialDelay(seconds, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(applicationContext).enqueue(workRequest)

        return Result.success()
    }
}

class BluetoothService : Service() {
    companion object {
        val TAG: String = BluetoothService::class.java.simpleName
        const val ACTION_START_FOREGROUND =
            "it.manzolo.bluetoothwatcher.mqtt.service.action.START_FOREGROUND"
        private const val NOTIFICATION_CHANNEL_ID = "BluetoothServiceChannel"
        private const val NOTIFICATION_ID = 1
    }

    private fun startForegroundService() {
        Log.d(TAG, "onBluetoothStartJob")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Bluetooth Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Manzolo Bluetooth Service")
            .setContentText("Running Bluetooth service in background")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        //Log.w(TAG,Build.FINGERPRINT)
        if (Build.FINGERPRINT.contains("generic") || Build.FINGERPRINT.contains("userdebug")) {
            DebugData().insertDebugData(applicationContext)
        } else {
            startBluetoothTask()
        }

    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_FOREGROUND) {
            startForegroundService()
        }
        // Logica per gestire i comandi di avvio
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
    }

    private fun startBluetoothTask() {
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
                    val btTask = BtTask(applicationContext)
                    btTask.execute()
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

class BtTask(
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
                loopOk@ for (attempt in 1..1) {
                    if (tryBluetoothConnection(context, bluetoothDeviceAddress)) {
                        Handler(Looper.getMainLooper()).postDelayed({}, 1000)
                        break@loopOk
                    }
                }
            }
            return@withContext "OK"

        }

    private fun tryBluetoothConnection(context: Context, address: String): Boolean {
        return try {
            val bluetoothClient = BluetoothClient(context, address)
            bluetoothClient.retrieveData()
            Thread.sleep(500)
            //bluetoothClient.close()
            true
        } catch (e: Exception) {
            //e.printStackTrace()
            Log.e(BluetoothService.TAG, e.message.toString())
            val intent = Intent(BluetoothEvents.ERROR)
            // You can also include some extra data.
            intent.putExtra("message", e.message)
            context.sendBroadcast(intent)
            false
        }
    }
}