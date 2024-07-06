package it.manzolo.bluetoothwatcher.mqtt.activity

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import it.manzolo.bluetoothwatcher.mqtt.App
import it.manzolo.bluetoothwatcher.mqtt.R


class MainActivityFragment : Fragment() {

    companion object {
        val TAG: String = MainActivityFragment::class.java.simpleName
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState == null) {
            startSentinelService()
            startBluetoothService()
            startLocationService()
        }
    }

    private fun startSentinelService() {
        Log.d(TAG, "startSentinelService")
        App.scheduleSentinelService(activity as Context)
    }

    private fun startBluetoothService() {
        Log.d(TAG, "startBluetoothService")
        App.scheduleBluetoothService(activity as Context)
    }

    private fun startLocationService() {
        Log.d(TAG, "startLocationService")
        App.scheduleLocationService(activity as Context)
    }

}
