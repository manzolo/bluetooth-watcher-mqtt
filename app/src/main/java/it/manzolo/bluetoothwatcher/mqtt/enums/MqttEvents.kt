package it.manzolo.bluetoothwatcher.mqtt.enums

interface MqttEvents {
    companion object {
        const val ERROR = "Error"
        const val INFO = "Info"
        const val DEBUG = "Debug"
        const val DATA_SENT = "Data sent"
        const val APP_UPDATE = "App update"
        const val APP_UPDATE_ERROR = "App update error"
        const val APP_AVAILABLE = "App update available"
        const val APP_CHECK_UPDATE = "App check for update"
        const val APP_NO_AVAILABLE_UPDATE = "No update"
    }
}