package it.manzolo.bluetoothwatcher.mqtt

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import androidx.preference.PreferenceManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import it.manzolo.bluetoothwatcher.mqtt.enums.MainEvents
import it.manzolo.bluetoothwatcher.mqtt.receiver.BootBroadcastReceiver
import it.manzolo.bluetoothwatcher.mqtt.service.BluetoothWorker
import it.manzolo.bluetoothwatcher.mqtt.service.LocationWorker
import it.manzolo.bluetoothwatcher.mqtt.service.SentinelService
import java.util.concurrent.TimeUnit

class App : Application() {
    companion object {
        private const val BLUETOOTH_WORKER_TAG =
            "it.manzolo.bluetoothwatcher.mqtt.service.BluetoothWorker"
        private const val LOCATION_WORKER_TAG =
            "it.manzolo.bluetoothwatcher.mqtt.service.LocationWorker"
        private const val SENTINEL_WORKER_TAG =
            "it.manzolo.bluetoothwatcher.mqtt.service.SentinelWorker"

        fun cancelAllWorkers(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(BLUETOOTH_WORKER_TAG)
            WorkManager.getInstance(context).cancelAllWorkByTag(LOCATION_WORKER_TAG)
            WorkManager.getInstance(context).cancelAllWorkByTag(SENTINEL_WORKER_TAG)
        }

        fun scheduleSentinelService(context: Context) {
            val seconds = 60L
            val intent = Intent(MainEvents.BROADCAST)
            intent.putExtra("message", "Start sentinel service every $seconds seconds")
            intent.putExtra("type", MainEvents.INFO)
            context.sendBroadcast(intent)

            // Cancel any existing work with the same tag before enqueuing new work
            WorkManager.getInstance(context).cancelAllWorkByTag(SENTINEL_WORKER_TAG)

            val workRequest = OneTimeWorkRequestBuilder<SentinelService.SentinelWorker>()
                .setInitialDelay(seconds, TimeUnit.SECONDS)
                .addTag(SENTINEL_WORKER_TAG)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                SENTINEL_WORKER_TAG,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )


        }

        fun scheduleBluetoothService(context: Context) {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            val seconds =
                preferences.getString("bluetoothServiceEverySeconds", "90")?.toLong() ?: 90L
            val debug = preferences.getBoolean("debugApp", false)

            if (debug) {
                Toast.makeText(
                    context,
                    "Start bluetooth service every $seconds seconds",
                    Toast.LENGTH_SHORT
                ).show()
            }

            val intent = Intent(MainEvents.BROADCAST)
            intent.putExtra("message", "Start bluetooth service every $seconds seconds")
            intent.putExtra("type", MainEvents.INFO)
            context.sendBroadcast(intent)

            // Cancel any existing work with the same tag before enqueuing new work
            WorkManager.getInstance(context).cancelAllWorkByTag(BLUETOOTH_WORKER_TAG)

            if (seconds >= 900) { // 15 minutes
                val periodicWorkRequest =
                    PeriodicWorkRequestBuilder<BluetoothWorker>(seconds, TimeUnit.SECONDS)
                        .addTag(BLUETOOTH_WORKER_TAG)
                        .build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    BLUETOOTH_WORKER_TAG,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    periodicWorkRequest
                )
            } else {
                val workRequest = OneTimeWorkRequestBuilder<BluetoothWorker>()
                    .setInitialDelay(seconds, TimeUnit.SECONDS)
                    .addTag(BLUETOOTH_WORKER_TAG)
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    BLUETOOTH_WORKER_TAG,
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
            }
        }

        fun scheduleLocationService(context: Context) {

            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            val seconds =
                preferences.getString("locationServiceEverySeconds", "600")?.toLong() ?: 600L

            val intent = Intent(MainEvents.BROADCAST)
            intent.putExtra("message", "Start location service every $seconds seconds")
            intent.putExtra("type", MainEvents.INFO)
            //cron(context, LocationService::class.java, seconds)
            context.sendBroadcast(intent)

            // Cancel any existing work with the same tag before enqueuing new work
            WorkManager.getInstance(context).cancelAllWorkByTag(LOCATION_WORKER_TAG)

            if (seconds >= 900) { // 15 minutes
                val periodicWorkRequest =
                    PeriodicWorkRequestBuilder<LocationWorker>(seconds, TimeUnit.SECONDS)
                        .addTag(LOCATION_WORKER_TAG)
                        .build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    LOCATION_WORKER_TAG,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    periodicWorkRequest
                )
            } else {
                val workRequest = OneTimeWorkRequestBuilder<LocationWorker>()
                    .setInitialDelay(seconds, TimeUnit.SECONDS)
                    .addTag(LOCATION_WORKER_TAG)
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    LOCATION_WORKER_TAG,
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
            }

        }
    }

}