package it.manzolo.bluetoothwatcher.mqtt.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import it.manzolo.bluetoothwatcher.mqtt.R
import it.manzolo.bluetoothwatcher.mqtt.enums.LocationEvents
import java.util.concurrent.TimeUnit

class LocationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        Log.d("LocationWorker", "doWork called")

        // Avvia il servizio come foreground service
        val intent = Intent(applicationContext, LocationService::class.java)
        intent.action = LocationService.ACTION_START_FOREGROUND
        applicationContext.startForegroundService(intent)

        // Programma il prossimo lavoro
        val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val seconds = preferences.getString("locationServiceEverySeconds", "600")?.toLong() ?: 600L
        val workRequest = OneTimeWorkRequestBuilder<LocationWorker>()
            .setInitialDelay(seconds, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(applicationContext).enqueue(workRequest)

        return Result.success()
    }
}

class LocationService : Service() {
    companion object {
        private const val LOCATION_INTERVAL = 1000L
        private const val LOCATION_DISTANCE = 10f
        private const val NOTIFICATION_CHANNEL_ID = "LocationServiceChannel"
        private const val NOTIFICATION_ID = 2
        const val ACTION_START_FOREGROUND =
            "it.manzolo.bluetoothwatcher.mqtt.service.action.START_FOREGROUND"
    }

    private var mLocationManager: LocationManager? = null
    private val mLocationListeners = arrayOf(LocationListener(LocationManager.GPS_PROVIDER))

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        initializeLocationManager()
        try {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            mLocationManager!!.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                LOCATION_INTERVAL,
                LOCATION_DISTANCE,
                mLocationListeners[0]
            )
        } catch (ex: SecurityException) {
            Log.e("LocationService", "Location permission error", ex)
        } catch (ex: IllegalArgumentException) {
            Log.e("LocationService", "Provider does not exist " + ex.message)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("LocationService", "onStartCommand called")
        if (intent?.action == ACTION_START_FOREGROUND) {
            startForegroundService()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        mLocationManager?.let {
            for (listener in mLocationListeners) {
                try {
                    it.removeUpdates(listener)
                } catch (ex: Exception) {
                    Log.e("LocationService", "Failed to remove location listeners", ex)
                }
            }
        }
    }

    private fun initializeLocationManager() {
        if (mLocationManager == null) {
            mLocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        }
    }

    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Location Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Manzolo Location Service")
            .setContentText("Updating location in background")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    inner class LocationListener(provider: String) : android.location.LocationListener {
        private var mLastLocation: Location = Location(provider)
        override fun onLocationChanged(location: Location) {
            mLastLocation.set(location)

            val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            preferences.edit().apply {
                putString("longitude", location.longitude.toString())
                putString("latitude", location.latitude.toString())
                apply()
            }

            Log.d(
                "LocationService",
                "Location Changed: ${location.latitude}, ${location.longitude}"
            )
            val intent = Intent(LocationEvents.LOCATION_CHANGED).apply {
                putExtra("longitude", location.longitude.toString())
                putExtra("latitude", location.latitude.toString())
            }
            sendBroadcast(intent)
        }

        override fun onProviderDisabled(provider: String) {}
        override fun onProviderEnabled(provider: String) {}

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
        }
    }
}