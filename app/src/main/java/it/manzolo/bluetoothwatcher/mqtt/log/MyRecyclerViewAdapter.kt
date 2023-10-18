package it.manzolo.bluetoothwatcher.mqtt.log

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import it.manzolo.bluetoothwatcher.mqtt.R
import it.manzolo.bluetoothwatcher.mqtt.enums.MainEvents
import it.manzolo.bluetoothwatcher.mqtt.log.MyRecyclerViewAdapter.MyViewHolder
import java.util.*

class MyRecyclerViewAdapter(private val mLogs: ArrayList<BluetoothWatcherLog>) : RecyclerView.Adapter<MyViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        //Inflate RecyclerView row
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_log, parent, false)

        //Create View Holder
        return MyViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        holder.textViewData.text = mLogs[position].data
        val message = mLogs[position].message
        holder.textViewMessage.text = message
        val type = mLogs[position].type
        when (type) {
            MainEvents.ERROR -> {
                holder.textViewMessage.setTextColor(Color.RED)
                holder.imageViewType.setImageResource(android.R.drawable.presence_busy)
            }
            MainEvents.INFO -> {
                holder.textViewMessage.setTextColor(Color.BLACK)
                holder.imageViewType.setImageResource(0)
            }
            MainEvents.WARNING -> {
                holder.textViewMessage.setTextColor(Color.BLACK)
                holder.imageViewType.setImageResource(android.R.drawable.presence_invisible)
            }
        }
    }

    override fun getItemCount(): Int {
        return mLogs.size
    }

    override fun getItemViewType(position: Int): Int {
        return position
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    //RecyclerView View Holder
    inner class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textViewData: TextView = itemView.findViewById(R.id.logData)
        val textViewMessage: TextView = itemView.findViewById(R.id.logMessage)
        val imageViewType: ImageView = itemView.findViewById(R.id.logstatus)

    }
}