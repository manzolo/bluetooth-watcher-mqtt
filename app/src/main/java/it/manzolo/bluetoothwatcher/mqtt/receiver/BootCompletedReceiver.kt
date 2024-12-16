package it.manzolo.bluetoothwatcher.mqtt.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import it.manzolo.bluetoothwatcher.mqtt.App
import it.manzolo.bluetoothwatcher.mqtt.activity.MainActivity

class BootBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            // Quando il dispositivo si avvia, avvia i servizi necessari
            val launchIntent = Intent(context, MainActivity::class.java)
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // Necessario per avviare una Activity da un BroadcastReceiver
            context.startActivity(launchIntent)
        }
    }
}

