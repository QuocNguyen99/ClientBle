package com.example.bleclient

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.le.ScanCallback

interface BluetoothAction {
    fun scan(scanCallback: ScanCallback)
    fun stop(scanCallback: ScanCallback)
    fun connect(device: BluetoothDevice, bluetoothGattCallback: BluetoothGattCallback)
    fun getCharacteristic(bluetoothGatt: BluetoothGatt): BluetoothGattCharacteristic
    fun read(bluetoothGatt: BluetoothGatt)
    fun write(bluetoothGatt: BluetoothGatt, byteArray: ByteArray)
    fun enableNotifications(bluetoothGatt: BluetoothGatt)
    fun disableNotifications(bluetoothGatt: BluetoothGatt)
}