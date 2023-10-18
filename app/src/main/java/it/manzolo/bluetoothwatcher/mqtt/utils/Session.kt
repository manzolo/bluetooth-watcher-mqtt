package it.manzolo.bluetoothwatcher.mqtt.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

class Session(cntx: Context) {
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(cntx)
    var webserviceToken: String?
        get() = prefs.getString("token", "")
        set(token) {
            prefs.edit().putString("token", token).apply()
        }
    var longitude: String?
        get() = prefs.getString("longitude", "")
        set(longitude) {
            prefs.edit().putString("longitude", longitude).apply()
        }
    var latitude: String?
        get() = prefs.getString("latitude", "")
        set(latitude) {
            prefs.edit().putString("latitude", latitude).apply()
        }
    var updateApkUrl: String?
        get() = prefs.getString("updateapkurl", "")
        set(updateapkurl) {
            prefs.edit().putString("updateapkurl", updateapkurl).apply()
        }
    var isAvailableUpdate: Boolean
        get() = prefs.getBoolean("availableUpdate", false)
        set(isAvailableUpdate) {
            prefs.edit().putBoolean("availableUpdate", isAvailableUpdate).apply()
        }

}