package com.localcloud.photosclient.presentation.viewer

sealed class MediaViewerEvent {
    data class OnPageChanged(val index: Int) : MediaViewerEvent()
    object ToggleUiVisibility : MediaViewerEvent()
    object ToggleInfoSheetVisibility : MediaViewerEvent()
    object OnBackClick : MediaViewerEvent()
    object OnRetry : MediaViewerEvent()
    object OnDelete : MediaViewerEvent()
    object OnShare : MediaViewerEvent()
    object OnManualBackup : MediaViewerEvent()
    object OnRestore : MediaViewerEvent()
}
