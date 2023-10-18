package it.manzolo.bluetoothwatcher.mqtt.device

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

fun getDeviceBatteryPercentage(context: Context): Int {

    val iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    val batteryStatus = context.registerReceiver(null, iFilter)

    val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

    val batteryPct = level / scale.toFloat()

    return (batteryPct * 100).toInt()
}