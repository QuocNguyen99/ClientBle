package com.example.bleclient


data class BlePacketResponse(val controlFrag: ControlFlag, val responseFrag: ResponseFlag, val idType: IDType, val moreFrag: MoreFlag, val id: Int, val dataSize: Int, val result: Int, val pid: Int, val cid: Byte, val data: ByteArray) {
    companion object {
        fun createBlePacketResponse(byteArray: ByteArray): BlePacketResponse {
            val headerByteFirst = byteArray.getOrNull(0)?.toInt() ?: 0
            val headerByteSecond = byteArray.getOrNull(1)?.toInt() ?: 0

            val controlFrag = if ((headerByteFirst and (1 shl 7)) != 0) ControlFlag.CONTROL_1 else ControlFlag.CONTROL_0
            val response = if ((headerByteFirst and (1 shl 6)) != 0) ResponseFlag.RESPONSE_1 else ResponseFlag.RESPONSE_0
            val idType = if ((headerByteFirst and (1 shl 5)) != 0) IDType.ID_TYPE_1 else IDType.ID_TYPE_0
            val moreFrag = if ((headerByteFirst and (1 shl 4)) != 0) MoreFlag.MORE_1 else MoreFlag.MORE_0
            val id = (headerByteFirst shr 0x0E) shr 1

            val dataSize = ((headerByteFirst and 0x01) shl 8) or (headerByteSecond and 0xFF)

            val result = (byteArray.getOrNull(2)?.toInt() ?: 0) and 0xF8 shr 3
            val pid = (byteArray.getOrNull(2)?.toInt() ?: 0) and 0x07
            val cid = byteArray.getOrNull(3) ?: 0
            val data = byteArray.copyOfRange(4, byteArray.size)


            return BlePacketResponse(controlFrag, response, idType, moreFrag, id, dataSize, result, pid, cid, data)
        }
    }

}