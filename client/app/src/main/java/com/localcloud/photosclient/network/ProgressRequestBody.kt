package com.localcloud.photosclient.network

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A RequestBody that accepts a stream factory lambda instead of a raw InputStream.
 * This ensures every writeTo() call (including OkHttp retries) gets a fresh stream,
 * preventing "Stream Closed" IOException on subsequent writes.
 */
class ProgressRequestBody(
    private val contentType: MediaType?,
    private val streamFactory: () -> InputStream,
    private val contentLength: Long,
    private val scope: CoroutineScope,
    private val onProgressUpdate: suspend (Int) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = contentType

    override fun contentLength(): Long = contentLength

    // OkHttp will not retry if isOneShot() returns true.
    // We keep this false now since the stream factory supports fresh streams.
    override fun isOneShot(): Boolean = false

    override fun writeTo(sink: BufferedSink) {
        val buffer = ByteArray(8192)
        var uploaded: Long = 0
        var lastUpdatePercent = -1

        streamFactory().use { stream ->
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                uploaded += read
                sink.write(buffer, 0, read)

                if (contentLength > 0) {
                    val progress = ((uploaded.toDouble() / contentLength.toDouble()) * 100).toInt()
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
