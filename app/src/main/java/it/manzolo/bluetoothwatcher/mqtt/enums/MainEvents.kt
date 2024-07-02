package it.manzolo.bluetoothwatcher.mqtt.enums

interface MainEvents {
    companion object {
        const val BROADCAST = "BROADCAST"
        const val BROADCAST_LOG = "BROADCAST_LOG"
        const val ERROR = "E"
        const val INFO = "I"
        const val WARNING = "W"
        const val DEBUG = "D"

    }
}