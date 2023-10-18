package it.manzolo.bluetoothwatcher.mqtt.database

import android.content.ContentValues
import android.content.Context
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.util.Log

class DatabaseLog(private val context: Context) {
    private var database: SQLiteDatabase? = null
    private var dbHelper: DatabaseHelper? = null

    @Throws(SQLException::class)
    fun open(): DatabaseLog {
        dbHelper = DatabaseHelper(context)
        database = dbHelper!!.writableDatabase
        return this
    }

    fun close() {
        dbHelper!!.close()
    }

    private fun createContentValues(data: String, message: String, type: String): ContentValues {
        val values = ContentValues()
        values.put(KEY_MESSAGE, message)
        values.put(KEY_DATA, data)
        values.put(KEY_TYPE, type)
        return values
    }

    // create a contact
    fun createRow(data: String, message: String, type: String): Long {
        val initialValues = createContentValues(data, message, type)
        return database!!.insertOrThrow(DATABASE_TABLE, null, initialValues)
    }

    fun clear() {
        val deleteQuery = "delete from log"
        Log.d("TAG", deleteQuery)
        val c = database!!.rawQuery(deleteQuery, null)
        c.moveToFirst()
        c.close()
    }

    companion object {
        const val KEY_ID = "_id"
        const val KEY_DATA = "data"
        const val KEY_TYPE = "type"
        const val KEY_MESSAGE = "message"
        private val LOG_TAG = DatabaseLog::class.java.simpleName

        // Database fields
        private const val DATABASE_TABLE = "log"
    }
}