package it.manzolo.bluetoothwatcher.mqtt.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import it.manzolo.bluetoothwatcher.mqtt.R
import it.manzolo.bluetoothwatcher.mqtt.database.DatabaseHelper
import it.manzolo.bluetoothwatcher.mqtt.database.DatabaseLog
import it.manzolo.bluetoothwatcher.mqtt.device.getDeviceBatteryPercentage
import it.manzolo.bluetoothwatcher.mqtt.enums.BluetoothEvents
import it.manzolo.bluetoothwatcher.mqtt.enums.DatabaseEvents
import it.manzolo.bluetoothwatcher.mqtt.enums.LocationEvents
import it.manzolo.bluetoothwatcher.mqtt.enums.MainEvents
import it.manzolo.bluetoothwatcher.mqtt.enums.WebserviceEvents
import it.manzolo.bluetoothwatcher.mqtt.error.UnCaughtExceptionHandler
import it.manzolo.bluetoothwatcher.mqtt.log.BluetoothWatcherLog
import it.manzolo.bluetoothwatcher.mqtt.log.MyRecyclerViewAdapter
import it.manzolo.bluetoothwatcher.mqtt.service.BluetoothService
import it.manzolo.bluetoothwatcher.mqtt.service.DiscoveryService
import it.manzolo.bluetoothwatcher.mqtt.service.LocationService
import it.manzolo.bluetoothwatcher.mqtt.utils.Date
import it.manzolo.bluetoothwatcher.mqtt.utils.Session
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject


class MainActivity : AppCompatActivity() {
    companion object {
        val TAG: String = MainActivity::class.java.simpleName
    }

    private val logList: ArrayList<BluetoothWatcherLog> = ArrayList()
    private var recyclerView: RecyclerView? = null
    val logViewAdapter = MyRecyclerViewAdapter(logList)

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            super.onCreate(savedInstanceState)

            registerLocalBroadcast()

            Thread.setDefaultUncaughtExceptionHandler(UnCaughtExceptionHandler(this))

            setContentView(R.layout.activity_main)
            setSupportActionBar(findViewById(R.id.toolbar))

            //Ask user for permission
            val permissions = arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.BLUETOOTH_SCAN,
            )
            ActivityCompat.requestPermissions(this, permissions, 0)

            //EventViewer
            //Reference of RecyclerView
            recyclerView = findViewById(R.id.myRecyclerView)
            //Linear Layout Manager
            val linearLayoutManager = LinearLayoutManager(this@MainActivity, RecyclerView.VERTICAL, false)
            //Set Layout Manager to RecyclerView
            recyclerView!!.layoutManager = linearLayoutManager
            //Set adapter to RecyclerView
            recyclerView!!.adapter = logViewAdapter

            logList.add(0, BluetoothWatcherLog(Date.now(), "System ready", MainEvents.INFO))

            //Service enabled by default
            PreferenceManager.getDefaultSharedPreferences(applicationContext).edit().putBoolean("enabled", true).apply()

        }

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    private val localBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            when (intent?.action) {
                MainEvents.BROADCAST -> {
                    captureLog(intent.getStringExtra("message")!!, intent.getStringExtra("type")!!)
                }
                MainEvents.INFO -> {
                    captureLog(intent.getStringExtra("message")!!, MainEvents.INFO)
                }
                MainEvents.ERROR -> {
                    captureLog(intent.getStringExtra("message")!!, MainEvents.ERROR)
                }
                BluetoothEvents.ERROR -> {
                    captureLog(intent.getStringExtra("message")!!, MainEvents.ERROR)
                }
                WebserviceEvents.ERROR -> {
                    captureLog(intent.getStringExtra("message")!!, MainEvents.ERROR)
                }
                WebserviceEvents.INFO -> {
                    captureLog(intent.getStringExtra("message")!!, MainEvents.INFO)
                }
                BluetoothEvents.DATA_RETRIEVED -> {
                    captureLog(intent.getStringExtra("message")!!, MainEvents.INFO)

                    val device = intent.getStringExtra("device")!!
                    val volt = intent.getStringExtra("volt")!!
                    val temp = intent.getStringExtra("tempC")!!

                    try {
                        // Session and location retrieval (assuming these are managed elsewhere)
                        val session = Session(applicationContext)
                        val bp = getDeviceBatteryPercentage(applicationContext)

                        // Prepare JSON payload
                        val jsonObject = JSONObject().apply {
                            put("voltage", volt)
                            put("temperature", temp)
                            put("tracker_battery", bp.toString())
                            put("longitude", session.longitude)
                            put("latitude", session.latitude)
                        }

                        // MQTT Client setup
                        val mqttUrl = PreferenceManager.getDefaultSharedPreferences(applicationContext)
                            .getString("mqttUrl", "") ?: ""
                        val mqttPort = PreferenceManager.getDefaultSharedPreferences(applicationContext)
                            .getString("mqttPort", "") ?: ""
                        val userName = PreferenceManager.getDefaultSharedPreferences(applicationContext)
                            .getString("mqttUsername", "") ?: ""
                        val password = PreferenceManager.getDefaultSharedPreferences(applicationContext)
                            .getString("mqttPassword", "")

                        val brokerUrl = "tcp://$mqttUrl:$mqttPort"
                        val topic = "${device.replace(":", "").lowercase()}/attributes"

                        val client = MqttClient(brokerUrl, "bluetooth_watcher", MemoryPersistence())
                        val options = MqttConnectOptions().apply {
                            isCleanSession = true
                            this.userName = userName // Setting userName property of MqttConnectOptions
                            password?.let { setPassword(it.toCharArray()) }
                        }

                        // Connect to MQTT broker
                        client.connect(options)

                        // Publish message
                        val message = MqttMessage(jsonObject.toString().toByteArray(Charsets.UTF_8))
                        client.publish(topic, message)

                        // Disconnect MQTT client
                        client.disconnect()

                    } catch (e: MqttException) {
                        // Handle MQTT exceptions
                        val dbIntent = Intent(DatabaseEvents.ERROR).apply {
                            putExtra("message", "MQTT Exception: ${e.message}")
                        }
                        applicationContext.sendBroadcast(dbIntent)

                    } catch (e: Exception) {
                        // Handle other exceptions
                        val dbIntent = Intent(DatabaseEvents.ERROR).apply {
                            putExtra("message", "Exception: ${e.message}")
                        }
                        applicationContext.sendBroadcast(dbIntent)
                    }
                }
                WebserviceEvents.DATA_SENT -> {
                    captureLog("Data sent " + intent.getStringExtra("message"), MainEvents.INFO)
                }
                LocationEvents.LOCATION_CHANGED -> {
                    captureLog("Obtain longitude:" + intent.getStringExtra("longitude")!! + " latitude:" + intent.getStringExtra("latitude")!!, MainEvents.INFO)
                }
                DatabaseEvents.ERROR -> {
                    captureLog(intent.getStringExtra("message")!!, MainEvents.ERROR)
                }
                MainEvents.DEBUG -> {
                    //saveLog(intent.getStringExtra("message"), MainEvents.DEBUG)
                    if (PreferenceManager.getDefaultSharedPreferences(applicationContext).getBoolean("debugApp", false)) {
                        Toast.makeText(applicationContext, intent.getStringExtra("message"), Toast.LENGTH_LONG).show()
                    }
                    return
                }
            }
            recyclerView!!.layoutManager?.scrollToPosition(0)
            logViewAdapter.notifyItemInserted(0)
        }
    }

    override fun onOptionsItemSelected(menuItem: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (menuItem.itemId) {
            R.id.action_settings -> {
                val intentSettings = Intent(this, SettingsActivity::class.java)
                this.startActivity(intentSettings)
                return true
            }

            R.id.action_trigger_bluetooth_service -> {
                val serviceIntent = Intent(this, BluetoothService::class.java)
                this.startService(serviceIntent)
                return true
            }

            R.id.action_trigger_mqtt_discovery_service -> {
                val serviceIntent = Intent(this, DiscoveryService::class.java)
                this.startService(serviceIntent)
                return true
            }

            R.id.action_trigger_location_service -> {
                val serviceIntent = Intent(this, LocationService::class.java)
                this.startService(serviceIntent)
                return true
            }

            R.id.action_dbbackup -> {
                showBackupDialog()
                return true
            }

            R.id.action_dbrestore -> {
                showRestoreDialog()
                return true
            }
            R.id.action_clear_log -> {
                val size: Int = logList.size
                logList.clear()
                val db = DatabaseLog(applicationContext)
                db.open()
                db.clear()
                db.close()
                logViewAdapter.notifyItemRangeRemoved(0, size)
                Toast.makeText(applicationContext, "Done", Toast.LENGTH_SHORT).show()
                return true
            }
            else -> super.onOptionsItemSelected(menuItem)
        }
    }

    private fun showBackupDialog() {
        // Late initialize an alert dialog object
        lateinit var dialog: AlertDialog


        // Initialize a new instance of alert dialog builder object
        val builder = AlertDialog.Builder(this)

        // Set a title for alert dialog
        builder.setTitle("Are you sure?")

        // On click listener for dialog buttons
        val dialogClickListener = DialogInterface.OnClickListener { _, which ->
            when (which) {
                DialogInterface.BUTTON_POSITIVE -> {
                    val db = DatabaseHelper(applicationContext)
                    db.backup()
                }
            }
        }

        // Set the alert dialog positive/yes button
        builder.setPositiveButton("YES", dialogClickListener)

        // Set the alert dialog negative/no button
        builder.setNegativeButton("NO", dialogClickListener)

        // Initialize the AlertDialog using builder object
        dialog = builder.create()

        // Finally, display the alert dialog
        dialog.show()
    }

    private fun showRestoreDialog() {
        // Late initialize an alert dialog object
        lateinit var dialog: AlertDialog


        // Initialize a new instance of alert dialog builder object
        val builder = AlertDialog.Builder(this)

        // Set a title for alert dialog
        builder.setTitle("Are you sure?")

        // On click listener for dialog buttons
        val dialogClickListener = DialogInterface.OnClickListener { _, which ->
            when (which) {
                DialogInterface.BUTTON_POSITIVE -> {
                    val db = DatabaseHelper(applicationContext)
                    db.restore()
                }
            }
        }

        // Set the alert dialog positive/yes button
        builder.setPositiveButton("YES", dialogClickListener)

        // Set the alert dialog negative/no button
        builder.setNegativeButton("NO", dialogClickListener)

        // Initialize the AlertDialog using builder object
        dialog = builder.create()

        // Finally, display the alert dialog
        dialog.show()
    }

    private fun getUpgradeLocalIntentFilter(): IntentFilter {
        val iFilter = IntentFilter()
        iFilter.addAction(WebserviceEvents.APP_UPDATE)
        return iFilter
    }

    private fun getUpdateErrorLocalIntentFilter(): IntentFilter {
        val iFilter = IntentFilter()
        iFilter.addAction(WebserviceEvents.APP_UPDATE_ERROR)
        return iFilter
    }

    private fun getCheckUpdateLocalIntentFilter(): IntentFilter {
        val iFilter = IntentFilter()
        iFilter.addAction(WebserviceEvents.APP_CHECK_UPDATE)
        return iFilter
    }

    private fun getNoUpdateLocalIntentFilter(): IntentFilter {
        val iFilter = IntentFilter()
        iFilter.addAction(WebserviceEvents.APP_NO_AVAILABLE_UPDATE)
        return iFilter
    }

    private fun getUpdateAvailableLocalIntentFilter(): IntentFilter {
        val iFilter = IntentFilter()
        iFilter.addAction(WebserviceEvents.APP_AVAILABLE)
        return iFilter
    }

    private fun getConnectionErrorLocalIntentFilter(): IntentFilter {
        val iFilter = IntentFilter()
        iFilter.addAction(BluetoothEvents.ERROR)
        return iFilter
    }

    private fun getConnectionOkLocalIntentFilter(): IntentFilter {
        val iFilter = IntentFilter()
        iFilter.addAction(BluetoothEvents.DATA_RETRIEVED)
        return iFilter
    }

    private fun getWebserviceErrorDataSentLocalIntentFilter(): IntentFilter {
        val iFilter = IntentFilter()
        iFilter.addAction(WebserviceEvents.ERROR)
        return iFilter
    }

    private fun getWebserviceInfoDataSentLocalIntentFilter(): IntentFilter {
        val iFilter = IntentFilter()
        iFilter.addAction(WebserviceEvents.INFO)
        return iFilter
    }

    private fun getWebserviceDataSentLocalIntentFilter(): IntentFilter {
        val iFilter = IntentFilter()
        iFilter.addAction(WebserviceEvents.DATA_SENT)
        return iFilter
    }

    private fun getDebugLocalIntentFilter(): IntentFilter {
        val iFilter = IntentFilter()
        iFilter.addAction(MainEvents.DEBUG)
        return iFilter
    }

    private fun getDatabaseErrorIntentFilter(): IntentFilter {
        val iFilter = IntentFilter()
        iFilter.addAction(DatabaseEvents.ERROR)
        return iFilter
    }

    private fun getLocationChangedIntentFilter(): IntentFilter {
        val iFilter = IntentFilter()
        iFilter.addAction(LocationEvents.LOCATION_CHANGED)
        return iFilter
    }

    private fun getMainServiceIntentFilter(): IntentFilter {
        val iFilter = IntentFilter()
        iFilter.addAction(MainEvents.BROADCAST)
        return iFilter
    }

    private fun getMainServiceInfoIntentFilter(): IntentFilter {
        val iFilter = IntentFilter()
        iFilter.addAction(MainEvents.INFO)
        return iFilter
    }

    private fun getMainServiceErrorIntentFilter(): IntentFilter {
        val iFilter = IntentFilter()
        iFilter.addAction(MainEvents.ERROR)
        return iFilter
    }

    private fun registerLocalBroadcast() {
        //LocalBroadcast
        applicationContext.registerReceiver(
            localBroadcastReceiver,
            getConnectionOkLocalIntentFilter()
        )
        applicationContext.registerReceiver(
            localBroadcastReceiver,
            getConnectionErrorLocalIntentFilter()
        )

        applicationContext.registerReceiver(
            localBroadcastReceiver,
            getWebserviceDataSentLocalIntentFilter()
        )
        applicationContext.registerReceiver(
            localBroadcastReceiver,
            getWebserviceErrorDataSentLocalIntentFilter()
        )
        applicationContext.registerReceiver(
            localBroadcastReceiver,
            getWebserviceInfoDataSentLocalIntentFilter()
        )
        applicationContext.registerReceiver(localBroadcastReceiver, getDebugLocalIntentFilter())

        applicationContext.registerReceiver(localBroadcastReceiver, getUpgradeLocalIntentFilter())
        applicationContext.registerReceiver(
            localBroadcastReceiver,
            getUpdateAvailableLocalIntentFilter()
        )
        applicationContext.registerReceiver(
            localBroadcastReceiver,
            getCheckUpdateLocalIntentFilter()
        )
        applicationContext.registerReceiver(localBroadcastReceiver, getNoUpdateLocalIntentFilter())
        applicationContext.registerReceiver(
            localBroadcastReceiver,
            getUpdateErrorLocalIntentFilter()
        )

        applicationContext.registerReceiver(localBroadcastReceiver, getDatabaseErrorIntentFilter())

        applicationContext.registerReceiver(
            localBroadcastReceiver,
            getLocationChangedIntentFilter()
        )

        applicationContext.registerReceiver(localBroadcastReceiver, getMainServiceIntentFilter())
        applicationContext.registerReceiver(
            localBroadcastReceiver,
            getMainServiceInfoIntentFilter()
        )
        applicationContext.registerReceiver(
            localBroadcastReceiver,
            getMainServiceErrorIntentFilter()
        )

    }

    private fun captureLog(message: String, type: String) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val debug = preferences.getBoolean("debugApp", false)

        // Aggiunta della riga di log al database
        val dbLog = DatabaseLog(applicationContext)
        dbLog.open()
        dbLog.createRow(Date.now(), message, type)
        dbLog.close()

        // Aggiunta della riga di log alla lista mantenendo solo le ultime 10 righe
        synchronized(logList) {
            logList.add(0, BluetoothWatcherLog(Date.now(), message, type))
            while (logList.size > 16) {
                logList.removeAt(16)
                logViewAdapter.notifyItemRangeRemoved(0, 16)
            }
        }

        // Visualizzazione del Toast se debug è attivo
        if (debug) {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

}
