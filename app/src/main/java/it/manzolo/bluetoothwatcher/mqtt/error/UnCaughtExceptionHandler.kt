package it.manzolo.bluetoothwatcher.mqtt.error

import android.app.Activity
import com.jakewharton.processphoenix.ProcessPhoenix

class UnCaughtExceptionHandler(private val activity: Activity) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, ex: Throwable) {
        //do your life saving stuff here
        ProcessPhoenix.triggerRebirth(activity.applicationContext)
    }
}