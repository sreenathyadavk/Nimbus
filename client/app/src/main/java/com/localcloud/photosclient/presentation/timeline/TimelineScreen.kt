package com.localcloud.photosclient.presentation.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.LazyGridState
import com.localcloud.photosclient.MediaItem
import com.localcloud.photosclient.data.LocalMedia
import com.localcloud.photosclient.presentation.home.HomeSelectionEvent
import com.localcloud.photosclient.presentation.home.HomeSelectionState
import com.localcloud.photosclient.ui.MainViewModel

@Composable
fun TimelineScreen(
    viewModel: MainViewModel,
    onMediaClick: (LocalMedia, List<LocalMedia>) -> Unit,
    gridState: LazyGridState = rememberLazyGridState()
) {
    val timelineGroups by viewModel.timelineFlow.collectAsState()
    val selectionState by viewModel.selectionState.collectAsState()
    val allMedia = remember(timelineGroups) { timelineGroups.flatMap { it.items } }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        timelineGroups.forEach { group ->
            // Header
            item(span = { GridItemSpan(maxLineSpan) }) {
                TimelineHeader(title = group.title)
            }

            // Items
            items(group.items, key = { it.id }) { media ->
                val isSelected = selectionState.selectedItems.contains(media.id)
                MediaItem(
                    media = media,
                    isSelected = isSelected,
                    onClick = {
                        if (selectionState.isSelectionMode) {
                            viewModel.onSelectionEvent(HomeSelectionEvent.ToggleSelection(media))
                        } else {
                            onMediaClick(media, allMedia)
                        }
                    },
                    onLongPress = {
                        viewModel.onSelectionEvent(HomeSelectionEvent.LongPress(media))
                    }
                )
            }
            
            // Subtle Divider
            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun TimelineHeader(title: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 12.dp, start = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
}
