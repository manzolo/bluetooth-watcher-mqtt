package it.manzolo.bluetoothwatcher.mqtt.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import it.manzolo.bluetoothwatcher.mqtt.enums.MqttEvents
import java.util.concurrent.TimeUnit


class SentinelService : Service() {

    companion object {
        val TAG: String = SentinelService::class.java.simpleName

    }
    class SentinelWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
        override fun doWork(): Result {
            Log.d(TAG, "doWork called")

            // Avvia il servizio come foreground service
            val intent = Intent(applicationContext, SentinelService::class.java)
            intent.action = LocationService.ACTION_START_FOREGROUND
            applicationContext.startForegroundService(intent)

            // Programma il prossimo lavoro
            val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            val seconds = 60L
            val workRequest = OneTimeWorkRequestBuilder<LocationWorker>()
                .setInitialDelay(seconds, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(applicationContext).enqueue(workRequest)

            return Result.success()
        }
    }
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onSentinelStartJob")
        starSentinelTask()
        return START_STICKY
    }

    private fun starSentinelTask() {
        Log.d(TAG, "onSentinelStartCommand")
        val pref = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        if (pref.getString("mqttUrl", "").toString().isEmpty()) {
            val intent = Intent(MqttEvents.ERROR)
            // You can also include some extra data.
            intent.putExtra("message", "No mqtt url in settings")
            applicationContext.sendBroadcast(intent)
            return
        }
    }

    override fun onCreate() {
        Log.d(TAG, "onCreate")
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()
    }
}