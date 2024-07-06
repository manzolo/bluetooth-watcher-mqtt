package it.manzolo.bluetoothwatcher.mqtt.activity

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import it.manzolo.bluetoothwatcher.mqtt.App
import it.manzolo.bluetoothwatcher.mqtt.R
import it.manzolo.bluetoothwatcher.mqtt.database.DatabaseHelper
import it.manzolo.bluetoothwatcher.mqtt.database.DatabaseLog
import it.manzolo.bluetoothwatcher.mqtt.enums.BluetoothEvents
import it.manzolo.bluetoothwatcher.mqtt.enums.DatabaseEvents
import it.manzolo.bluetoothwatcher.mqtt.enums.LocationEvents
import it.manzolo.bluetoothwatcher.mqtt.enums.MainEvents
import it.manzolo.bluetoothwatcher.mqtt.enums.MqttEvents
import it.manzolo.bluetoothwatcher.mqtt.log.BluetoothWatcherLog
import it.manzolo.bluetoothwatcher.mqtt.log.MyRecyclerViewAdapter
import it.manzolo.bluetoothwatcher.mqtt.service.BluetoothService
import it.manzolo.bluetoothwatcher.mqtt.service.DiscoveryService
import it.manzolo.bluetoothwatcher.mqtt.service.LocationService
import it.manzolo.bluetoothwatcher.mqtt.service.MqttService
import it.manzolo.bluetoothwatcher.mqtt.utils.Date

class MainActivity : AppCompatActivity() {
    companion object {
        private const val PERMISSION_REQUEST_CODE = 0
        private const val MAX_LOG_ITEMS = 16

        // Costanti per le autorizzazioni
        private val PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        )
    }

    private val logList: ArrayList<BluetoothWatcherLog> = ArrayList(MAX_LOG_ITEMS)
    private lateinit var recyclerView: RecyclerView
    private lateinit var logViewAdapter: MyRecyclerViewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        setupPermissions()
        requestBluetooth()
        setupRecyclerView()
        registerLocalBroadcast()

        logList.add(0, BluetoothWatcherLog(Date.now(), "System ready", MainEvents.INFO))
        PreferenceManager.getDefaultSharedPreferences(applicationContext)
            .edit()
            .putBoolean("enabled", true)
            .apply()

        App.cancelAllWorkers(this)
        scheduleServices()
    }

    override fun onDestroy() {
        super.onDestroy()
        applicationContext.unregisterReceiver(localBroadcastReceiver)
    }

    // Funzione per gestire il setup delle autorizzazioni
    private fun setupPermissions() {
        ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_REQUEST_CODE)
    }


    // Funzione per avviare tutti i servizi necessari
    private fun scheduleServices() {
        App.scheduleBluetoothService(this)
        App.scheduleLocationService(this)

        startService(Intent(this, MqttService::class.java))
    }

    fun requestBluetooth() {
        // check android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestMultiplePermissions.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                )
            )
        } else {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            requestEnableBluetooth.launch(enableBtIntent)
        }
    }

    private val requestEnableBluetooth =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                // granted
            } else {
                // denied
            }
        }

    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                Log.d("MyTag", "${it.key} = ${it.value}")
            }
        }

    // Funzione per mostrare un dialog di conferma
    private fun showConfirmationDialog(
        title: String,
        message: String,
        onClickListener: DialogInterface.OnClickListener
    ) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("YES", onClickListener)
            .setNegativeButton("NO") { _, _ -> }
            .create()
            .show()
    }

    // Funzione per pulire il log
    private fun clearLog() {
        val size = logList.size
        logList.clear()

        DatabaseLog(applicationContext).apply {
            open()
            clear()
            close()
        }

        logViewAdapter.notifyItemRangeRemoved(0, size)
        Toast.makeText(applicationContext, "Log Cleared", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }

            R.id.action_trigger_bluetooth_service -> {
                startService(Intent(this, BluetoothService::class.java))
                true
            }

            R.id.action_trigger_mqtt_discovery_service -> {
                startService(Intent(this, DiscoveryService::class.java))
                true
            }

            R.id.action_trigger_location_service -> {
                startService(Intent(this, LocationService::class.java))
                true
            }

            R.id.action_dbbackup -> {
                showConfirmationDialog(
                    "Backup",
                    "Do you want to backup?",
                    DialogInterface.OnClickListener { _, _ ->
                        val db = DatabaseHelper(applicationContext)
                        db.backup()
                    })
                true
            }

            R.id.action_dbrestore -> {
                showConfirmationDialog(
                    "Restore",
                    "Do you want to restore?",
                    DialogInterface.OnClickListener { _, _ ->
                        val db = DatabaseHelper(applicationContext)
                        db.restore()
                    })
                true
            }

            R.id.action_clear_log -> {
                clearLog()
                true
            }

            else -> super.onOptionsItemSelected(menuItem)
        }
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.myRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        logViewAdapter = MyRecyclerViewAdapter(logList)
        recyclerView.adapter = logViewAdapter
    }

    private fun registerLocalBroadcast() {
        val intentFilters = arrayOf(
            getBluetoothErrorLocalIntentFilter(),
            getBroadcastLogIntentFilter(),
            getDebugLocalIntentFilter(),
            getDatabaseErrorIntentFilter(),
            getLocationChangedIntentFilter(),
            getMqttErrorIntentFilter(),
            getBroadcastIntentFilter(),
            getInfoIntentFilter(),
            getErrorIntentFilter()
        )

        intentFilters.forEach { filter ->
            applicationContext.registerReceiver(localBroadcastReceiver, filter)
        }
    }

    private val localBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            intent?.let { intent ->
                when (intent.action) {
                    MainEvents.BROADCAST, MainEvents.BROADCAST_LOG -> captureLog(
                        intent.getStringExtra("message")!!,
                        intent.getStringExtra("type")!!
                    )

                    MainEvents.INFO, MqttEvents.INFO ->
                        captureLog(intent.getStringExtra("message")!!, MainEvents.INFO)

                    MainEvents.ERROR, BluetoothEvents.ERROR, MqttEvents.ERROR ->
                        captureLog(intent.getStringExtra("message")!!, MainEvents.ERROR)

                    MqttEvents.DATA_SENT -> captureLog(
                        "Data sent ${intent.getStringExtra("message")}",
                        MainEvents.INFO
                    )

                    LocationEvents.LOCATION_CHANGED -> captureLog(
                        "Obtain longitude: ${
                            intent.getStringExtra(
                                "longitude"
                            )
                        } latitude: ${intent.getStringExtra("latitude")}", MainEvents.INFO
                    )

                    DatabaseEvents.ERROR -> captureLog(
                        intent.getStringExtra("message")!!,
                        MainEvents.ERROR
                    )

                    MainEvents.DEBUG -> {
                        if (PreferenceManager.getDefaultSharedPreferences(applicationContext)
                                .getBoolean("debugApp", false)
                        ) {
                            Toast.makeText(
                                applicationContext,
                                intent.getStringExtra("message"),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
                recyclerView.layoutManager?.scrollToPosition(0)
                logViewAdapter.notifyItemInserted(0)
            }
        }
    }

    private fun captureLog(message: String, type: String) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val debug = preferences.getBoolean("debugApp", false)

        DatabaseLog(applicationContext).apply {
            open()
            createRow(Date.now(), message, type)
            close()
        }

        synchronized(logList) {
            logList.add(0, BluetoothWatcherLog(Date.now(), message, type))
            if (logList.size > MAX_LOG_ITEMS) {
                logList.removeAt(MAX_LOG_ITEMS)
                logViewAdapter.notifyItemRangeRemoved(0, MAX_LOG_ITEMS)
            }
        }

        if (debug) {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    // Funzioni per ottenere gli intent filters
    private fun getBluetoothErrorLocalIntentFilter() = IntentFilter(BluetoothEvents.ERROR)

    private fun getDebugLocalIntentFilter() = IntentFilter(MainEvents.DEBUG)

    private fun getDatabaseErrorIntentFilter() = IntentFilter(DatabaseEvents.ERROR)

    private fun getMqttErrorIntentFilter() = IntentFilter(MqttEvents.ERROR)

    private fun getLocationChangedIntentFilter() = IntentFilter(LocationEvents.LOCATION_CHANGED)

    private fun getBroadcastLogIntentFilter() = IntentFilter(MainEvents.BROADCAST_LOG)

    private fun getBroadcastIntentFilter() = IntentFilter(MainEvents.BROADCAST)

    private fun getInfoIntentFilter() = IntentFilter(MainEvents.INFO)

    private fun getErrorIntentFilter() = IntentFilter(MainEvents.ERROR)
}