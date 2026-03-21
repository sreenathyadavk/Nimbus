package com.localcloud.photosclient.presentation.timeline

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.localcloud.photosclient.data.LocalMedia
import com.localcloud.photosclient.presentation.components.DenseMediaItem
import com.localcloud.photosclient.presentation.home.HomeSelectionEvent
import com.localcloud.photosclient.ui.MainViewModel
import com.localcloud.photosclient.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimelineScreen(
    viewModel: MainViewModel,
    onMediaClick: (LocalMedia, List<LocalMedia>) -> Unit,
    gridState: LazyGridState = rememberLazyGridState()
) {
    val timelineGroups by viewModel.timelineFlow.collectAsState(initial = emptyList())
    val selectionState by viewModel.selectionState.collectAsState()
    val haptic = LocalHapticFeedback.current
    
    // Pinch to resize logic
    var columnScale by remember { mutableFloatStateOf(3f) }
    val columns by animateIntAsState(
        targetValue = columnScale.roundToInt().coerceIn(2, 4),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "columns"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    val newScale = (columnScale / zoom).coerceIn(1.5f, 4.5f)
                    if (newScale.roundToInt() != columnScale.roundToInt()) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    columnScale = newScale
                }
            }
    ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            timelineGroups.forEach { group ->
                item(span = { GridItemSpan(columns) }) {
                    TimelineHeader(group.title)
                }

                items(group.items, key = { it.id }) { media ->
                    val isSelected = selectionState.selectedItems.contains(media.id)
                    val scale by animateFloatAsState(
                        targetValue = if (selectionState.isSelectionMode) 0.92f else 1f,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "cellScale"
                    )

                    SamsungMediaCell(
                        media = media,
                        isSelected = isSelected,
                        isSelectionMode = selectionState.isSelectionMode,
                        scale = scale,
                        onClick = {
                            if (selectionState.isSelectionMode) {
                                viewModel.onSelectionEvent(HomeSelectionEvent.ToggleSelection(media))
                            } else {
                                onMediaClick(media, timelineGroups.flatMap { it.items })
                            }
                        },
                        onLongPress = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.onSelectionEvent(HomeSelectionEvent.LongPress(media))
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TimelineHeader(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(PureBlack)
            .padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
    ) {
        Text(
            text = title,
            color = White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SamsungMediaCell(
    media: LocalMedia,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    scale: Float,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(1.dp) // Minimal spacing to match Samsung closely
            .scale(scale)
            .clip(RoundedCornerShape(if (isSelected) 12.dp else 4.dp))
            .background(DarkSurfaceVariant)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
    ) {
        AsyncImage(
            model = media.uri,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (isSelectionMode && !isSelected) 0.7f else 1f },
            contentScale = ContentScale.Crop
        )

        // Selected Border
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(3.dp, SamsungBlue, RoundedCornerShape(12.dp))
            )
        }

        // Selection Checkmark
        AnimatedVisibility(
            visible = isSelectionMode && isSelected,
            enter = scaleIn(spring(dampingRatio = 0.6f)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(SamsungBlue, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = White, modifier = Modifier.size(16.dp))
            }
        }

        // Video Duration Badge
        if (media.mediaType.startsWith("video", ignoreCase = true)) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = White, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text(text = "0:15", color = White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
