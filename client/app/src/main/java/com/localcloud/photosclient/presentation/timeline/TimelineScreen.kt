package com.localcloud.photosclient.presentation.timeline

import android.content.ContentUris
import android.provider.MediaStore
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.localcloud.photosclient.data.LocalMedia
import com.localcloud.photosclient.presentation.home.HomeSelectionEvent
import com.localcloud.photosclient.ui.MainViewModel
import com.localcloud.photosclient.ui.theme.*
import kotlinx.coroutines.flow.Flow
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimelineScreen(
    viewModel: MainViewModel,
    onMediaClick: (LocalMedia, List<LocalMedia>) -> Unit,
    gridState: LazyGridState = rememberLazyGridState(),
    overrideTimelineFlow: Flow<List<MainViewModel.TimelineGroup>>? = null
) {
    val timelineGroups by (overrideTimelineFlow ?: viewModel.timelineFlow).collectAsStateWithLifecycle(initialValue = emptyList())
    val selectionState by viewModel.selectionState.collectAsStateWithLifecycle()
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
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        if (zoom != 1f) {
                            val newScale = (columnScale / zoom).coerceIn(1.5f, 4.5f)
                            if (newScale.roundToInt() != columnScale.roundToInt()) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            columnScale = newScale
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
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
                                val allFlattened = timelineGroups.flatMap { it.items }
                                Log.d("NAV_DEBUG", "Tapping media ${media.id}, list size ${allFlattened.size}")
                                onMediaClick(media, allFlattened)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SamsungMediaCell(
    media: LocalMedia,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    scale: Float,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val context = LocalContext.current
    val uri = remember(media.id, media.mediaType) {
        if (media.mediaType.startsWith("video")) {
            ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, media.id)
        } else {
            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, media.id)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(1.dp)
            .scale(scale)
            .clip(RoundedCornerShape(if (isSelected) 12.dp else 4.dp))
            .background(Color(0xFF1A1A1A))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(uri)
                .crossfade(true)
                .build(),
            contentDescription = null,
            placeholder = ColorPainter(Color(0xFF1A1A1A)),
            error = ColorPainter(Color(0xFF2A2A2A)),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (isSelectionMode && !isSelected) 0.7f else 1f },
            contentScale = ContentScale.Crop
        )

        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(3.dp, SamsungBlue, RoundedCornerShape(12.dp))
            )
        }

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
                // Format duration properly if available
                val dur = media.duration ?: 0L
                val min = (dur / 1000) / 60
                val sec = (dur / 1000) % 60
                Text(
                    text = String.format("%d:%02d", min, sec),
                    color = White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
