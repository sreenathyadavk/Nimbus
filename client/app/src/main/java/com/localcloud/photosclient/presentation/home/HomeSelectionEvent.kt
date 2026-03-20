package com.localcloud.photosclient.presentation.home

import com.localcloud.photosclient.data.LocalMedia

sealed class HomeSelectionEvent {
    data class ToggleSelection(val media: LocalMedia) : HomeSelectionEvent()
    data class LongPress(val media: LocalMedia) : HomeSelectionEvent()
    object ClearSelection : HomeSelectionEvent()
}
