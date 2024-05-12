package com.example.bleclient

import kotlin.math.ceil

class BleCommunication {

    fun checkNumberFragment(byteArray: ByteArray, mtuSize: Int): Int {
        val currentSize = byteArray.size
        val attOverHeader = 3
        val header = 2

        return if ((currentSize - attOverHeader - header) <= mtuSize) {
            1
        } else {
            ceil((currentSize / mtuSize).toDouble()).toInt()
        }
    }

}