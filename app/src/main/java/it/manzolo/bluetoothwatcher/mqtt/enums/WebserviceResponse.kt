package it.manzolo.bluetoothwatcher.mqtt.enums

interface WebserviceResponse {
    companion object {
        const val ERROR = "ERROR"
        const val OK = "OK"
        const val TOKEN_EXPIRED = "TOKEN_EXPIRED"
    }
}