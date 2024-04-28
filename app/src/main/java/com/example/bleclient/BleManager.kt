package com.example.bleclient

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanSettings
import android.content.Context

@SuppressLint("MissingPermission")
class BleManager(private val context: Context) : BluetoothAction {
    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    // From the previous section:
    val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    override fun scan(scanSettings: ScanSettings, scanCallback: ScanCallback) {
        bleScanner.startScan(null, scanSettings, scanCallback)
    }

    override fun stop(scanCallback: ScanCallback) {
        bleScanner.stopScan(scanCallback)
    }

    override fun connect(device: BluetoothDevice, bluetoothGattCallback: BluetoothGattCallback) {
        device.connectGatt(context, false, bluetoothGattCallback)
    }

}