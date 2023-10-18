package it.manzolo.bluetoothwatcher.mqtt.activity

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import it.manzolo.bluetoothwatcher.mqtt.App
import it.manzolo.bluetoothwatcher.mqtt.R
import it.manzolo.bluetoothwatcher.mqtt.enums.MainEvents
import it.manzolo.bluetoothwatcher.mqtt.service.BluetoothService
import it.manzolo.bluetoothwatcher.mqtt.service.LocationService

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
        private fun serviceEnabled(enabled: Boolean) {
            val intent = Intent(MainEvents.BROADCAST)

            if (enabled) {
                this.context?.let { App.scheduleBluetoothService(it) }
                this.context?.let { App.scheduleLocationService(it) }

                intent.putExtra("message", "Services have been started from settings")
                intent.putExtra("type", MainEvents.INFO)
                //bluetoothService?.handler?.postDelayed(bluetoothService.runnable, frequencyBluetoothService);
            } else {
                val handlersList = App.getHandlers()
                val bluetoothService = App.findHandler(BluetoothService::class.java, handlersList)
                val locationService = App.findHandler(LocationService::class.java, handlersList)

                bluetoothService?.handler?.removeCallbacks(bluetoothService.runnable)
                locationService?.handler?.removeCallbacks(locationService.runnable)

                handlersList.clear()

                intent.putExtra("message", "Services have been stopped from settings")
                intent.putExtra("type", MainEvents.INFO)
            }
            context?.let { this.context?.sendBroadcast(intent) }

        }

        override fun onResume() {
            super.onResume()
            preferenceScreen.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onPause() {
            super.onPause()
            preferenceScreen.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.settings_preferences, rootKey)

            val webserviceUrlPreference: EditTextPreference? = findPreference("webserviceUrl")

            webserviceUrlPreference?.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            }

            val passwordPreference: EditTextPreference? = findPreference("webservicePassword")

            passwordPreference?.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }

            val bluetoothEverySecondsPreference: EditTextPreference? = findPreference("bluetoothServiceEverySeconds")

            bluetoothEverySecondsPreference?.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_NUMBER
            }
            val webserviceEverySecondsPreference: EditTextPreference? = findPreference("webserviceServiceEverySeconds")

            webserviceEverySecondsPreference?.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_NUMBER
            }
            val locationEverySecondsPreference: EditTextPreference? = findPreference("locationServiceEverySeconds")

            locationEverySecondsPreference?.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_NUMBER
            }
            val updateEverySecondsPreference: EditTextPreference? =
                findPreference("updateServiceEverySeconds")

            updateEverySecondsPreference?.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_NUMBER
            }

            val restartAppEverySecondsPreference: EditTextPreference? =
                findPreference("restartAppServiceEverySeconds")

            restartAppEverySecondsPreference?.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_NUMBER
            }

        }

        override fun onSharedPreferenceChanged(
            sharedPreferences: SharedPreferences?,
            key: String?
        ) {
            val enabled = getString(R.string.enabledSetting)
            when (key) {
                enabled -> if (sharedPreferences != null) {
                    serviceEnabled(sharedPreferences.getBoolean(enabled, false))
                }
                //else -> Log.d("Settings", "unknown key $key")
            }
        }
    }

}