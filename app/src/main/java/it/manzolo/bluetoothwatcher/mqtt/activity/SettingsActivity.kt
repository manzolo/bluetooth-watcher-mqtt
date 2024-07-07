package it.manzolo.bluetoothwatcher.mqtt.activity

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import it.manzolo.bluetoothwatcher.mqtt.App
import it.manzolo.bluetoothwatcher.mqtt.R
import it.manzolo.bluetoothwatcher.mqtt.enums.MainEvents

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings, SettingsFragment())
            .commit()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

        val TAG: String = SettingsFragment::class.toString()

        private fun serviceEnabled(enabled: Boolean) {
            val intent = Intent(MainEvents.BROADCAST)
            Log.d(TAG, "Service enabled: $enabled")

            if (enabled) {
                context?.let {
                    Log.d(TAG, "Canceling all workers and scheduling services")
                    App.cancelAllWorkers(it)
                    App.scheduleBluetoothService(it)
                    App.scheduleLocationService(it)
                }

                intent.putExtra("message", "Services have been started from settings")
                intent.putExtra("type", MainEvents.INFO)
            } else {
                context?.let {
                    Log.d(TAG, "Canceling all workers")
                    App.cancelAllWorkers(it)
                }

                intent.putExtra("message", "Services have been stopped from settings")
                intent.putExtra("type", MainEvents.INFO)
            }
            context?.sendBroadcast(intent)
            Log.d(TAG, "Broadcast sent: ${intent.extras}")
        }

        override fun onResume() {
            super.onResume()
            Log.d(TAG, "Registering OnSharedPreferenceChangeListener")
            preferenceScreen.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onPause() {
            super.onPause()
            Log.d(TAG, "Unregistering OnSharedPreferenceChangeListener")
            preferenceScreen.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.settings_preferences, rootKey)
            Log.d(TAG, "Preferences created")

            configureEditTextPreference("mqttUrl", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
            configureEditTextPreference("mqttPort", InputType.TYPE_CLASS_NUMBER)
            configureEditTextPreference("mqttPassword", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
            configureEditTextPreference("bluetoothServiceEverySeconds", InputType.TYPE_CLASS_NUMBER)
            configureEditTextPreference("locationServiceEverySeconds", InputType.TYPE_CLASS_NUMBER)

            val enabledPreference: SwitchPreferenceCompat? = findPreference("enabledSetting")
            enabledPreference?.let {
                Log.d(TAG, "Initial service state: ${it.isChecked}")
                serviceEnabled(it.isChecked)
            }
        }

        private fun configureEditTextPreference(key: String, inputType: Int) {
            val preference: EditTextPreference? = findPreference(key)
            preference?.setOnBindEditTextListener { editText ->
                editText.inputType = inputType
                Log.d(TAG, "Configured EditTextPreference: $key with input type: $inputType")
            }
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            if (key == "enabledSetting") {
                sharedPreferences?.let {
                    val enabled = it.getBoolean(key, false)
                    Log.d(TAG, "Preference changed: $key to $enabled")
                    serviceEnabled(enabled)
                }
            }
        }
    }
}