package com.localcloud.photosclient.sync

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MediaStoreObserver(
    private val context: Context,
    private val scope: CoroutineScope,
    private val mediaStoreManager: MediaStoreManager
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private var debounceJob: Job? = null

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        android.util.Log.d("SYNC_DEBUG", "MediaStoreObserver: onChange triggered for uri: $uri")
        
        // Debounce triggers to avoid multiple rapid scans
        debounceJob?.cancel()
        debounceJob = scope.launch(Dispatchers.IO) {
            android.util.Log.d("SYNC_DEBUG", "MediaStoreObserver: Debounce job starting sync after 2000ms")
            delay(2000) // 2 second debounce
            mediaStoreManager.scanAndSyncMediaStore()
        }
    }

    fun register() {
        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            this
        )
        context.contentResolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            true,
            this
        )
    }

    fun unregister() {
        context.contentResolver.unregisterContentObserver(this)
        debounceJob?.cancel()
    }
}
