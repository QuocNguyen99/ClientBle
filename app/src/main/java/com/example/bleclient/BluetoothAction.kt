package com.example.bleclient

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanSettings

interface BluetoothAction {
    fun scan(scanSettings: ScanSettings, scanCallback: ScanCallback)
    fun stop(scanCallback: ScanCallback)
    fun connect(device: BluetoothDevice, bluetoothGattCallback: BluetoothGattCallback)
}