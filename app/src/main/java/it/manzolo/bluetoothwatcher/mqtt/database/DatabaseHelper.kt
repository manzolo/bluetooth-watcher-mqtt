package it.manzolo.bluetoothwatcher.mqtt.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Environment
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class DatabaseHelper     // Costruttore
    (private val context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    // Questo metodo viene chiamato durante la creazione del database
    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(DB_CREATE_VOLTWATCHER)
        database.execSQL(DB_CREATE_LOG)
    }

    // Questo metodo viene chiamato durante l'upgrade del database, ad esempio quando viene incrementato il numero di versione
    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        database.execSQL("DROP TABLE IF EXISTS voltwatcher")
        database.execSQL("DROP TABLE IF EXISTS log")
        onCreate(database)
    }

    fun backup() {
        try {

            val sd = context.getExternalFilesDir("backup")
            val data = Environment.getDataDirectory()
            if (sd!!.canWrite()) {
                val currentDBPath = "//data/" + context.packageName + "/databases/" + DATABASE_NAME
                val currentDB = File(data, currentDBPath)
                val backupDB = File(sd, DATABASE_NAME)
                if (currentDB.exists()) {
                    val src = FileInputStream(currentDB).channel
                    val dst = FileOutputStream(backupDB).channel
                    dst.transferFrom(src, 0, src.size())
                    src.close()
                    dst.close()
                    Toast.makeText(context, "Backup is successful to $sd", Toast.LENGTH_SHORT)
                        .show()
                } else {
                    Toast.makeText(context, "$currentDB not exists ", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(context, "Unable to write to : $sd", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Backup error: " + e.message, Toast.LENGTH_LONG).show()
        }
    }

    fun restore() {
        try {
            val sd = context.getExternalFilesDir("backup")
            val data = Environment.getDataDirectory()
            if (sd!!.canRead()) {
                val currentDBPath = "//data/" + context.packageName + "/databases/" + DATABASE_NAME
                val currentDB = File(data, currentDBPath)
                val backupDB = File(sd, DATABASE_NAME)
                if (currentDB.exists()) {
                    val src = FileInputStream(backupDB).channel
                    val dst = FileOutputStream(currentDB).channel
                    dst.transferFrom(src, 0, src.size())
                    src.close()
                    dst.close()
                    Toast.makeText(context, "Database Restored successfully", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Restore error: " + e.message, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val DATABASE_NAME = "voltwatcher.db"
        private const val DATABASE_VERSION = 2

        // Lo statement SQL di creazione del database
        private const val DB_CREATE_VOLTWATCHER =
            "create table voltwatcher (_id integer primary key autoincrement, device text not null, data text not null, volts text not null, temps text not null, longitude text not null, latitude text not null, detectorbattery integer ,sent integer);"
        private const val DB_CREATE_LOG =
            "create table log (_id integer primary key autoincrement, data text not null, message text not null, type text);"
    }
}