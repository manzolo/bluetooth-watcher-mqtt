package it.manzolo.bluetoothwatcher.mqtt

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.preference.PreferenceManager
import it.manzolo.bluetoothwatcher.mqtt.enums.MainEvents
import it.manzolo.bluetoothwatcher.mqtt.service.BluetoothService
import it.manzolo.bluetoothwatcher.mqtt.service.LocationService
import it.manzolo.bluetoothwatcher.mqtt.service.SentinelService
import it.manzolo.bluetoothwatcher.mqtt.utils.HandlerList

class App : Application() {
    companion object {
        fun getHandlers(): ArrayList<HandlerList> {
            return handlers
        }

        private val handlers: ArrayList<HandlerList> = ArrayList()
        private fun cron(context: Context, serviceClass: Class<*>, seconds: String) {
            val handler = Handler(Looper.getMainLooper())
            val frequency = seconds.toInt() * 1000.toLong() // in ms
            val runnable = object : Runnable {
                override fun run() {
                    val intent = Intent(context, serviceClass)
                    context.startService(intent)
                    handler.postDelayed(this, frequency) //now is every 2 minutes
                }
            }
            handler.postDelayed(runnable, frequency) //Every 120000 ms (2 minutes)
            handlers.add(0, HandlerList(serviceClass, handler, runnable, frequency))

        }

        fun scheduleSentinelService(context: Context) {
            val intent = Intent(MainEvents.BROADCAST)
            intent.putExtra("message", "Start sentinel service every 60 seconds")
            intent.putExtra("type", MainEvents.INFO)
            context.sendBroadcast(intent)
            cron(context, SentinelService::class.java, "60")
        }

        fun scheduleBluetoothService(context: Context) {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            val seconds = preferences.getString("bluetoothServiceEverySeconds", "90")
            val debug = preferences.getBoolean("debugApp", false)
            if (debug) {
                Toast.makeText(context, "Start bluetooth service every $seconds seconds", Toast.LENGTH_SHORT).show()
            }

            if (seconds != null) {
                val intent = Intent(MainEvents.BROADCAST)
                intent.putExtra("message", "Start bluetooth service every $seconds seconds")
                intent.putExtra("type", MainEvents.INFO)
                context.sendBroadcast(intent)
                cron(context, BluetoothService::class.java, seconds)
            }
        }

        fun scheduleLocationService(context: Context) {

            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            val seconds = preferences.getString("locationServiceEverySeconds", "600")

            if (seconds != null) {
                val intent = Intent(MainEvents.BROADCAST)
                intent.putExtra("message", "Start location service every $seconds seconds")
                intent.putExtra("type", MainEvents.INFO)
                context.sendBroadcast(intent)
                cron(context, LocationService::class.java, seconds)
            }

        }

        fun findHandler(
                name: Class<*>, handlers: List<HandlerList>): HandlerList? {
            val iterator: Iterator<HandlerList> = handlers.iterator()
            while (iterator.hasNext()) {
                val currentHandler: HandlerList = iterator.next()
                if (currentHandler.classname == name) {
                    return currentHandler
                }
            }
            return null
        }
    }
}