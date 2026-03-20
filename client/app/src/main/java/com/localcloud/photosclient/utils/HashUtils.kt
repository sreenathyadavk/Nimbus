package com.localcloud.photosclient.utils

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object HashUtils {
    fun calculateSHA256(inputStream: java.io.InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream.use { fis ->
            val byteArray = ByteArray(1024)
            var bytesCount: Int
            while (fis.read(byteArray).also { bytesCount = it } != -1) {
                digest.update(byteArray, 0, bytesCount)
            }
        }
        val bytes = digest.digest()
        val sb = java.lang.StringBuilder()
        for (i in bytes.indices) {
            sb.append(Integer.toString((bytes[i].toInt() and 0xff) + 0x100, 16).substring(1))
        }
        return sb.toString()
    }
}
