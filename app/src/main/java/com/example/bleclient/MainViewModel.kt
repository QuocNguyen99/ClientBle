package com.example.bleclient

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class MainUiState(
    val isConnected: Boolean = false,
    val deviceScan: List<BluetoothDevice> = listOf()
//    val bleSelected:
)

@SuppressLint("MissingPermission")
class MainViewModel(private val bleManager: BleManager) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    val isEnableBluetoothAdapter by lazy {
        bleManager.bluetoothAdapter.isEnabled
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private var mtuSize = 23 //Default

    fun scanBle() {
        bleManager.scan(scanCallback)
    }

    fun stopScanBle() {
        bleManager.stop(scanCallback)
    }

    fun connectToDevice(device: BluetoothDevice) {
        stopScanBle()
        bleManager.connect(device, gattCallback)
    }

    fun sendPassword(password: String = "thisispasswordofwifisamsung") {
        try {
            val payload = password.toByteArray()
            val wifiApPacket = BlePacketModel(
                controlFrag = ControlFlag.CONTROL_0,
                responseFrag = ResponseFlag.RESPONSE_0,
                idType = IDType.ID_TYPE_0,
                moreFrag = MoreFlag.MORE_1,
                id = 0,
                dataSize = payload.size,
                cid = decimalToBinary(20).toByte(2),
                data = payload
            )

            val data = BlePacketModel.createByteArray(wifiApPacket)
            Log.d("BluetoothGattCallback", "writeCharacteristic data: ${data.joinToString(separator = " ") { byte ->
                byte.toString(2).padStart(8, '0') // Chuyển đổi byte thành chuỗi nhị phân
            }}")


//            bluetoothGatt?.let { gatt ->
//                val payload = password.toByteArray()
//
//                Log.d("BluetoothGattCallback", "writeCharacteristic payload: ${payload.size}")
//
//                val bleCommunication = BleCommunication()
//                val numberFragmentPacket = bleCommunication.checkNumberFragment(payload, mtuSize)
//
//                if (numberFragmentPacket == 1) {
//                    val wifiApPacket = BlePacketModel(
//                        controlFrag = ControlFlag.CONTROL_0,
//                        responseFrag = ResponseFlag.RESPONSE_0,
//                        idType = IDType.ID_TYPE_0,
//                        moreFrag = MoreFlag.MORE_1,
//                        id = 0,
//                        dataSize = payload.size,
//                        cid = decimalToBinary(20).toByte(2),
//                        data = payload
//                    )
//
//                    val data = BlePacketModel.createByteArray(wifiApPacket)
//                    Log.d("BluetoothGattCallback", "writeCharacteristic data: $data")
//
//
//                    bleManager.write(bluetoothGatt = gatt, BlePacketModel.createByteArray(wifiApPacket))
//                } else {
//
//                }
//            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            Log.i("BluetoothGattCallback", "Found BLE device! Name: ${{ result.device.name }}, address: ${result.device.address}")
            if (result.device.name != null && !_uiState.value.deviceScan.contains(result.device)) {
                val currentList = _uiState.value.deviceScan.toMutableList()
                currentList.add(result.device)
                viewModelScope.launch(Dispatchers.IO) {
                    _uiState.emit(
                        _uiState.value.copy(deviceScan = currentList)
                    )
                }
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.w("BluetoothGattCallback", "Successfully connected to $deviceAddress")
                    bluetoothGatt = gatt
                    gatt.discoverServices()

                    viewModelScope.launch(Dispatchers.IO) {
                        _uiState.emit(
                            _uiState.value.copy(isConnected = true)
                        )
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.w("BluetoothGattCallback", "Successfully disconnected from $deviceAddress")
                    gatt.close()
                    bluetoothGatt = null
                }
            } else {
                Log.w("BluetoothGattCallback", "Error $status encountered for $deviceAddress! Disconnecting...")
                gatt.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            gatt?.let {
                it.printGattTable()
                it.let { bleManager.enableNotifications(it) }
                it.requestMtu(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BluetoothGattCallback", "onMtuChanged mtuSize: $mtuSize")
                mtuSize = mtu
            } else {
                Log.e("BluetoothGattCallback", "onMtuChanged status: $status")
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            super.onDescriptorWrite(gatt, descriptor, status)
            val uuid = descriptor?.uuid.toString()
            Log.d("BluetoothGattCallback", "onDescriptorWrite uuid: $uuid")
            Log.d("BluetoothGattCallback", "onDescriptorWrite status: $status")
            if (status == BluetoothGatt.GATT_SUCCESS && uuid == BleManager.CCC_DESCRIPTOR_UUID) {

            }
        }

        // For Characteristic have response
        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            Log.d("BluetoothGattCallback", "onCharacteristicWrite uuid: ${characteristic?.uuid}")
            Log.d("BluetoothGattCallback", "onCharacteristicWrite status: $status")
        }

        @Deprecated("Deprecated for Android 13+")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            with(characteristic) {
                Log.i("BluetoothGattCallback", "Characteristic $uuid changed | value: ${value.toHexString()}")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            val newValueHex = value.toHexString()
            with(characteristic) {
                Log.i("BluetoothGattCallback", "Characteristic $uuid changed | value: $newValueHex")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopScanBle()
    }
}

fun decimalToBinary(decimalNumber: Int): String {
    // Chuyển đổi số thập phân thành chuỗi nhị phân
    val binaryString = Integer.toBinaryString(decimalNumber)

    // Bổ sung các số 0 để đảm bảo độ dài của chuỗi là 8 bit
    val paddedBinaryString = binaryString.padStart(8, '0')

    return paddedBinaryString
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