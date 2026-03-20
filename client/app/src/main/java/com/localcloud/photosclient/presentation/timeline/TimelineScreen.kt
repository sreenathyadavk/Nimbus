package com.localcloud.photosclient.presentation.timeline

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.CircleShape
import com.localcloud.photosclient.data.LocalMedia
import com.localcloud.photosclient.presentation.components.DenseMediaItem
import com.localcloud.photosclient.presentation.home.HomeSelectionEvent
import com.localcloud.photosclient.ui.MainViewModel

@Composable
fun TimelineScreen(
    viewModel: MainViewModel,
    onMediaClick: (LocalMedia, List<LocalMedia>) -> Unit,
    gridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    overrideTimelineFlow: kotlinx.coroutines.flow.Flow<List<MainViewModel.TimelineGroup>>? = null
) {
    val timelineGroups by (overrideTimelineFlow ?: viewModel.timelineFlow).collectAsState(initial = emptyList())
    val selectionState by viewModel.selectionState.collectAsState()
    val columns by viewModel.gridColumns.collectAsState()
    val haptic = LocalHapticFeedback.current
    
    val allMedia = remember(timelineGroups) { timelineGroups.flatMap { it.items } }

    var zoomScale by remember { mutableStateOf(1f) }

    val showHeader by remember { derivedStateOf { gridState.firstVisibleItemIndex > 0 } }
    val currentHeaderTitle by remember {
        derivedStateOf {
            val firstVisibleIndex = gridState.firstVisibleItemIndex
            // Map flat index to group title
            var count = 0
            var title = ""
            for (group in timelineGroups) {
                count += 1 // Header item
                if (firstVisibleIndex < count) {
                    title = group.title
                    break
                }
                count += group.items.size
                if (firstVisibleIndex < count) {
                    title = group.title
                    break
                }
            }
            title
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalStaggeredGrid(
            state = gridState,
            columns = StaggeredGridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom > 1.1f && columns > 2) {
                        viewModel.updateGridColumns(columns - 1)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    } else if (zoom < 0.9f && columns < 4) {
                        viewModel.updateGridColumns(columns + 1)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }
            },
            verticalItemSpacing = 2.dp,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            timelineGroups.forEach { group ->
                item(span = StaggeredGridItemSpan.FullLine) {
                    DateHeader(title = group.title)
                }

                items(group.items, key = { it.id }) { media ->
                    val isSelected = selectionState.selectedItems.contains(media.id)
                    DenseMediaItem(
                        media = media,
                        isSelected = isSelected,
                        onClick = {
                            if (selectionState.isSelectionMode) {
                                viewModel.onSelectionEvent(HomeSelectionEvent.ToggleSelection(media))
                            } else {
                                val allMedia = timelineGroups.flatMap { it.items }
                                onMediaClick(media, allMedia)
                            }
                        },
                        onLongPress = {
                            viewModel.onSelectionEvent(HomeSelectionEvent.LongPress(media))
                        }
                    )
                }
            }

            // Bottom spacing
            item(span = StaggeredGridItemSpan.FullLine) {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        // Floating Sticky Header
        if (showHeader && currentHeaderTitle.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                Text(
                    text = currentHeaderTitle,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun DateHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
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
