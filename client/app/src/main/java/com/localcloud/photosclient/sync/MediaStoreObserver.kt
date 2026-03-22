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
        
        // Debounce triggers to avoid multiple rapid scans (burst photo mode)
        debounceJob?.cancel()
        debounceJob = scope.launch(Dispatchers.IO) {
            delay(1000) // 1 second debounce wait after last change
            mediaStoreManager.scanAndSyncMediaStore()
        }
    }

    fun startObserving() {
        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true, // notifyForDescendants
            this
        )
        context.contentResolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            true,
            this
        )
    }

    fun stopObserving() {
        context.contentResolver.unregisterContentObserver(this)
        debounceJob?.cancel()
    }
}
