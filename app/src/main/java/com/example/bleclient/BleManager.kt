package com.example.bleclient

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
import android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID

@SuppressLint("MissingPermission")
class BleManager(private val context: Context) : BluetoothAction {

    companion object {
        private val DIS_SERVICE_UUID = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb")
        private val MANUFACTURER_NAME_CHARACTERISTIC_UUID = UUID.fromString("00002A2B-0000-1000-8000-00805f9b34fb")

        private val HEART_RATE_SERVICE_UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b3333")
        private val HEART_RATE_MEASUREMENT_CHARACTERISTIC_UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b3333")

        //TODO don't know
        val CCC_DESCRIPTOR_UUID = "00002A2B-0000-1000-8000-00805f9b34fb"
    }

    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    override fun scan(scanCallback: ScanCallback) {
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(
                    ParcelUuid(HEART_RATE_SERVICE_UUID)
                )
                .build()
        )

        bleScanner.startScan(filters, scanSettings, scanCallback)
    }

    override fun stop(scanCallback: ScanCallback) {
        bleScanner.stopScan(scanCallback)
    }

    override fun connect(device: BluetoothDevice, bluetoothGattCallback: BluetoothGattCallback) {
        device.connectGatt(context, false, bluetoothGattCallback)
    }

    override fun getCharacteristic(bluetoothGatt: BluetoothGatt): BluetoothGattCharacteristic {
        return bluetoothGatt.getService(HEART_RATE_SERVICE_UUID).getCharacteristic(HEART_RATE_MEASUREMENT_CHARACTERISTIC_UUID)
    }

    override fun read(bluetoothGatt: BluetoothGatt) {
        val char = getCharacteristic(bluetoothGatt)

        if (char.isReadable()) {
            bluetoothGatt.readCharacteristic(char)
        }
    }

    override fun write(bluetoothGatt: BluetoothGatt, byteArray: ByteArray) {
        val char = getCharacteristic(bluetoothGatt)

        val writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bluetoothGatt.writeCharacteristic(char, byteArray, writeType)
        } else {
            // Fall back to deprecated version of writeCharacteristic for Android <13
            legacyCharacteristicWrite(bluetoothGatt, char, byteArray, writeType)
        }
    }

    override fun enableNotifications(bluetoothGatt: BluetoothGatt) {
        val characteristic = getCharacteristic(bluetoothGatt)
        val payload = when {
            characteristic.isIndicatable() -> ENABLE_INDICATION_VALUE
            characteristic.isNotifiable() -> ENABLE_NOTIFICATION_VALUE
            else -> {
                Log.e("ConnectionManager", "${characteristic.uuid} doesn't support notifications/indications")
                return
            }
        }

        characteristic.descriptors.firstOrNull()?.let { cccDescriptor ->
            if (!bluetoothGatt.setCharacteristicNotification(characteristic, true)) {
                Log.e("ConnectionManager", "setCharacteristicNotification failed for ${characteristic.uuid}")
                return
            }
            writeDescriptor(bluetoothGatt, cccDescriptor, payload)
        } ?: Log.e("ConnectionManager", "${characteristic.uuid} doesn't contain the CCC descriptor!")
    }

    override fun disableNotifications(bluetoothGatt: BluetoothGatt) {
        val characteristic = getCharacteristic(bluetoothGatt)
        if (!characteristic.isNotifiable() && !characteristic.isIndicatable()) {
            Log.e("BleManager", "${characteristic.uuid} doesn't support indications/notifications")
            return
        }

        characteristic.descriptors.firstOrNull()?.let { cccDescriptor ->
            if (!bluetoothGatt.setCharacteristicNotification(characteristic, false)) {
                Log.e("BleManager", "setCharacteristicNotification failed for ${characteristic.uuid}")
                return
            }
            writeDescriptor(bluetoothGatt, cccDescriptor, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
        } ?: Log.e("BleManager", "${characteristic.uuid} doesn't contain the CCC descriptor!")
    }

    @Suppress("DEPRECATION")
    private fun legacyCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int
    ) {
        characteristic.writeType = writeType
        characteristic.value = value
        gatt.writeCharacteristic(characteristic)
    }

    private fun writeDescriptor(bluetoothGatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, byteArray: ByteArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bluetoothGatt.writeDescriptor(descriptor, ENABLE_INDICATION_VALUE)
            } else {
                // Fall back to deprecated version of writeDescriptor for Android <13
                bluetoothGatt.legacyDescriptorWrite(descriptor, byteArray)
            }
        } catch (ex: Exception) {
            Log.d("BleManager", "writeDescriptor ex: ${ex.message} ")
        }
    }

    @TargetApi(Build.VERSION_CODES.S)
    @Suppress("DEPRECATION")
    private fun BluetoothGatt.legacyDescriptorWrite(
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ): Boolean {
        descriptor.value = value
        return writeDescriptor(descriptor)
    }
}