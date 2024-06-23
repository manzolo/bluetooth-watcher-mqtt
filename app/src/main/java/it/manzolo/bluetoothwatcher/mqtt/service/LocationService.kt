package it.manzolo.bluetoothwatcher.mqtt.service

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.ActivityCompat
import it.manzolo.bluetoothwatcher.mqtt.enums.LocationEvents
import it.manzolo.bluetoothwatcher.mqtt.utils.Session

class LocationService : Service() {
    companion object {
        //val TAG: String = LocationService::class.java.simpleName
        private const val LOCATION_INTERVAL = 1000
        private const val LOCATION_DISTANCE = 10f
    }

    private var mLocationListeners = arrayOf(
        LocationListener(LocationManager.GPS_PROVIDER)
    )
    private var mLocationManager: LocationManager? = null

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        //Log.d(TAG, "onLocationStartJob")
        startLocationTask()
        return START_NOT_STICKY
    }

    private fun startLocationTask() {
        //Log.d(TAG, "onStartCommand")
    }

    override fun onCreate() {
        //Log.d(TAG, "onCreate")
        initializeLocationManager()
        try {
            mLocationManager!!.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    LOCATION_INTERVAL.toLong(),
                    LOCATION_DISTANCE,
                    mLocationListeners[0]
            )
        } catch (ex: SecurityException) {
            //Log.i(TAG, "fail to request location update, ignore", ex)
        } catch (ex: IllegalArgumentException) {
            //Log.d(TAG, "network provider does not exist, " + ex.message)
        }
    }

    override fun onDestroy() {
        //Log.d(TAG, "onDestroy")
        super.onDestroy()
        if (mLocationManager != null) {
            for (i in mLocationListeners.indices) {
                try {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        return
                    }
                    mLocationManager!!.removeUpdates(mLocationListeners[i])
                } catch (ex: Exception) {
                    //Log.i(TAG, "fail to remove location listener, ignore", ex)
                }
            }
        }
    }

    private fun initializeLocationManager() {
        //Log.d(TAG, "initializeLocationManager - LOCATION_INTERVAL: $LOCATION_INTERVAL LOCATION_DISTANCE: $LOCATION_DISTANCE")
        if (mLocationManager == null) {
            mLocationManager = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        }
    }

    inner class LocationListener(provider: String) : android.location.LocationListener {
        private var mLastLocation: Location = Location(provider)
        override fun onLocationChanged(location: Location) {
            //Log.d(TAG, "onLocationChanged: $location")
            mLastLocation.set(location)
            val session = Session(applicationContext)

            //GPS
            //Log.d(TAG, location.longitude.toString())
            //Log.d(TAG, location.latitude.toString())
            session.longitude = location.longitude.toString()
            session.latitude = location.latitude.toString()

            val intent = Intent(LocationEvents.LOCATION_CHANGED)
            // You can also include some extra data.
            intent.putExtra("longitude", location.longitude.toString())
            intent.putExtra("latitude", location.latitude.toString())
            applicationContext.sendBroadcast(intent)

        }

        override fun onProviderDisabled(provider: String) {
            //Log.d(TAG, "onProviderDisabled: $provider")
        }

        override fun onProviderEnabled(provider: String) {
            //Log.d(TAG, "onProviderEnabled: $provider")
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
            //Log.d(TAG, "onStatusChanged: $provider")
        }
    }
}