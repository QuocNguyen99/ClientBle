package com.example.bleclient

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.juul.kable.Scanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDateTime
import java.util.Calendar
import java.util.Date
import java.util.UUID

@SuppressLint("MissingPermission")
class MainViewModel(private val bleManager: BleManager) : ViewModel() {

    val CURRENT_TIME_SERVICE_UUID = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb")
    val CURRENT_TIME_CHARACTERISTIC_UUID = UUID.fromString("00002A2B-0000-1000-8000-00805f9b34fb")

    val isEnableBluetoothAdapter by lazy {
        bleManager.bluetoothAdapter.isEnabled
    }

    private val _deviceScan = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val deviceScan: StateFlow<List<BluetoothDevice>> = _deviceScan

    private var bluetoothGatt: BluetoothGatt? = null

    fun scanBle() {
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bleManager.scan(scanSettings, scanCallback)
    }

    fun stopBle() {
        bleManager.stop(scanCallback)
    }

    fun connectToDevice(device: BluetoothDevice) {
        stopBle()
        bleManager.connect(device, gattCallback)
    }

    fun readCharacteristic(gatt: BluetoothGatt) {
        val batteryLevelChar = gatt
            .getService(CURRENT_TIME_SERVICE_UUID)?.getCharacteristic(CURRENT_TIME_CHARACTERISTIC_UUID)

        if (batteryLevelChar?.isReadable() == true) {
            gatt.readCharacteristic(batteryLevelChar)
        }
    }

    fun writeCharacteristic() {
        try {
            bluetoothGatt?.let { gatt ->
                val characteristic = bluetoothGatt?.getService(CURRENT_TIME_SERVICE_UUID)?.getCharacteristic(CURRENT_TIME_CHARACTERISTIC_UUID)

                val writeType = when {
                    characteristic?.isWritable() == true -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    characteristic?.isWritableWithoutResponse() == true -> {
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    }

                    else -> error("Characteristic ${characteristic?.uuid} cannot be written to")
                }

                val currentTimeMillis = System.currentTimeMillis()
                val buffer = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
                buffer.putLong(currentTimeMillis)
                val payload = buffer.array()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(characteristic, payload, writeType)
                } else {
                    // Fall back to deprecated version of writeCharacteristic for Android <13
                    legacyCharacteristicWrite(gatt, characteristic, payload, writeType)
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    @TargetApi(Build.VERSION_CODES.S)
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

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            Log.i("BleManager", "Found BLE device! Name: ${{ result.device.name }}, address: ${result.device.address}")
            if (result.device.name != null && !_deviceScan.value.contains(result.device))
                _deviceScan.update { it + result.device }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.w("BluetoothGattCallback", "Successfully connected to $deviceAddress")
                    bluetoothGatt = gatt
                    viewModelScope.launch(Dispatchers.Main) {
                        gatt.discoverServices()
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.w("BluetoothGattCallback", "Successfully disconnected from $deviceAddress")
                    gatt.close()
                }
            } else {
                Log.w("BluetoothGattCallback", "Error $status encountered for $deviceAddress! Disconnecting...")
                gatt.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            gatt?.printGattTable()
            gatt?.let { readCharacteristic(it) }
        }

        @Deprecated("Deprecated for Android 13+")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            with(characteristic) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        Log.i("BluetoothGattCallback", "Read characteristic $uuid:\n${value}")
                    }

                    BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                        Log.e("BluetoothGattCallback", "Read not permitted for $uuid!")
                    }

                    else -> {
                        Log.e("BluetoothGattCallback", "Characteristic read failed for $uuid, error: $status")
                    }
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            val uuid = characteristic.uuid
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    Log.i("BluetoothGattCallback", "Read characteristic $uuid:\n${value}")

                    val YEAR = value[0].toUInt() // Giả sử byte đầu tiên là giây
                    val MONTH = value[2].toUInt() // Giả sử byte thứ hai là phút
                    val DATE = value[3].toUInt() // Giả sử byte thứ ba là giờ
                    val HOUR_OF_DAY = value[4].toUInt() // Giả sử byte thứ tư là ngày
                    val MINUTE = value[5].toUInt() // Giả sử byte thứ năm là tháng


                    val dateTime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        LocalDateTime.of(YEAR.toInt(), MONTH.toInt(), DATE.toInt(), HOUR_OF_DAY.toInt(), MINUTE.toInt()).toString()
                    } else {
                        ""
                    }

                    Log.i(
                        "BluetoothGattCallback", "Read characteristic $uuid:\n${dateTime}"
                    )
                }

                BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                    Log.e("BluetoothGattCallback", "Read not permitted for $uuid!")
                }

                else -> {
                    Log.e("BluetoothGattCallback", "Characteristic read failed for $uuid, error: $status")
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            with(characteristic) {
                val value = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    characteristic.value
                } else {

                    //TODO fix issue
                    ByteArray(10)
                }
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        Log.i("BluetoothGattCallback", "Wrote to characteristic $uuid | value: ${value.toHexString()}")
                    }

                    BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> {
                        Log.e("BluetoothGattCallback", "Write exceeded connection ATT MTU!")
                    }

                    BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> {
                        Log.e("BluetoothGattCallback", "Write not permitted for $uuid!")
                    }

                    else -> {
                        Log.e("BluetoothGattCallback", "Characteristic write failed for $uuid, error: $status")
                    }
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            if (gatt != null && characteristic != null) {
                onCharacteristicChanged(gatt, characteristic, characteristic.value)
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            Log.i("BluetoothGattCallback", "Wrote to characteristic ${characteristic.uuid} | value: ${value.toHexString()}")
        }
    }
}


@Suppress("UNCHECKED_CAST")
class MainViewModelFactory(private val bleManager: BleManager) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(bleManager = bleManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}