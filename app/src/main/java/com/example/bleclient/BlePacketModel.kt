package com.example.bleclient

enum class ControlFlag(val value: Int) {
    CONTROL_0(0),
    CONTROL_1(1),
}

enum class ResponseFlag(val value: Int) {
    RESPONSE_0(0),
    RESPONSE_1(1),
}

enum class IDType(val value: Int) {
    ID_TYPE_0(0),
    ID_TYPE_1(1),
}

enum class MoreFlag(val value: Int) {
    MORE_0(0),
    MORE_1(1),
}

data class BlePacketModel(val controlFrag: ControlFlag, val responseFrag: ResponseFlag, val idType: IDType, val moreFrag: MoreFlag, val id: Int, val cid: Byte, val dataSize: Int, val data: ByteArray) {
    companion object {
        fun createByteArray(bleData: BlePacketModel): ByteArray {
            val headerByteFirst = (bleData.controlFrag.value shl 7) or
                    (bleData.responseFrag.value shl 6) or
                    (bleData.idType.value shl 5) or
                    (bleData.moreFrag.value shl 4) or
                    (bleData.id and 0x07 shl 1) or
                    (bleData.dataSize shr 8)

            val headerByteSecond = (bleData.data.size and 0xFF)

            return byteArrayOf(headerByteFirst.toByte(), headerByteSecond.toByte(), bleData.cid) + bleData.data
        }

        fun createBlePacketModel(byteArray: ByteArray): BlePacketModel {
            val headerByteFirst = byteArray.getOrNull(0)?.toInt() ?: 0
            val headerByteSecond = byteArray.getOrNull(1)?.toInt() ?: 0

            val controlFrag = if ((headerByteFirst and (1 shl 7)) != 0) ControlFlag.CONTROL_1 else ControlFlag.CONTROL_0
            val response = if ((headerByteFirst and (1 shl 6)) != 0) ResponseFlag.RESPONSE_1 else ResponseFlag.RESPONSE_0
            val idType = if ((headerByteFirst and (1 shl 5)) != 0) IDType.ID_TYPE_1 else IDType.ID_TYPE_0
            val moreFrag = if ((headerByteFirst and (1 shl 4)) != 0) MoreFlag.MORE_1 else MoreFlag.MORE_0
            val id = (headerByteFirst shr 1) and 0x07

            val dataSize = ((headerByteFirst and 0x01) shl 8) or (headerByteSecond and 0xFF)

            val cid = byteArray[2]

            val data = byteArray.copyOfRange(3, byteArray.size)

            return BlePacketModel(controlFrag, response, idType, moreFrag, id, cid, dataSize, data)
        }
    }
}