package com.localcloud.photosclient.network

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ProgressRequestBody(
    private val contentType: MediaType?,
    private val inputStream: InputStream,
    private val contentLength: Long,
    private val scope: CoroutineScope,
    private val onProgressUpdate: suspend (Int) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = contentType

    override fun contentLength(): Long = contentLength
    
    override fun isOneShot(): Boolean = true

    override fun writeTo(sink: BufferedSink) {
        val buffer = ByteArray(8192)
        var uploaded: Long = 0

        inputStream.use { stream ->
            var read: Int
            var lastUpdatePercent = -1

            while (stream.read(buffer).also { read = it } != -1) {
                uploaded += read
                sink.write(buffer, 0, read)
                
                if (contentLength > 0) {
                    val progress = ((uploaded.toDouble() / contentLength.toDouble()) * 100).toInt()
                    // Throttle updates to only when percentage changes to avoid spamming the DB
                    if (progress != lastUpdatePercent) {
                        lastUpdatePercent = progress
                        scope.launch {
                            onProgressUpdate(progress)
                        }
                    }
                }
            }
        }
    }
}
