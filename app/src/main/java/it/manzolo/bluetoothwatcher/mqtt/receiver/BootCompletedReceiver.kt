package it.manzolo.bluetoothwatcher.mqtt.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootCompletedReceiver : BroadcastReceiver() {
    private var context: Context? = null
    private var arg1: Intent? = null
    override fun onReceive(context: Context, arg1: Intent) {
        this.context = context
        this.arg1 = arg1
        Log.w("boot_broadcast_poc", "starting service")
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage("it.manzolo.bluetoothwatcher.mqtt")
        context.startActivity(launchIntent)
    }
}