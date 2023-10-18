package it.manzolo.bluetoothwatcher.mqtt.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.preference.PreferenceManager
import it.manzolo.bluetoothwatcher.mqtt.enums.WebserviceEvents


class SentinelService : Service() {

    companion object {
        val TAG: String = SentinelService::class.java.simpleName

    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onSentinelStartJob")
        starSentinelTask()
        return START_NOT_STICKY
    }

    private fun starSentinelTask() {
        Log.d(TAG, "onSentinelStartCommand")
        val pref = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        if (pref.getString("webserviceUrl", "").toString().isEmpty()) {
            val intent = Intent(WebserviceEvents.ERROR)
            // You can also include some extra data.
            intent.putExtra("message", "No webservice url in settings")
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