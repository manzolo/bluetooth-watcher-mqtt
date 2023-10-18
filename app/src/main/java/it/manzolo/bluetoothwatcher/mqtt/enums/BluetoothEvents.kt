package it.manzolo.bluetoothwatcher.mqtt.enums

interface BluetoothEvents {
    companion object {
        const val ERROR = "Connection error"
        const val DATA_RETRIEVED = "Data retrieved"
        const val CLOSECONNECTION = "Close connection"
    }
}