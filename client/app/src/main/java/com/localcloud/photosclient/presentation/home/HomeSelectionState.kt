package com.localcloud.photosclient.presentation.home

data class HomeSelectionState(
    val selectedItems: Set<Long> = emptySet(),
    val isSelectionMode: Boolean = false
) {
    val selectedCount: Int get() = selectedItems.size
}
