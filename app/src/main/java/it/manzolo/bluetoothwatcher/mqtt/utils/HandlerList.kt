package it.manzolo.bluetoothwatcher.mqtt.utils

import android.os.Handler

class HandlerList(val classname: Class<*>, val handler: Handler, val runnable: Runnable, val frequency: Long)