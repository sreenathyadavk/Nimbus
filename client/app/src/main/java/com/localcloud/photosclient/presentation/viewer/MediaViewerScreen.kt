package com.localcloud.photosclient.presentation.viewer

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.localcloud.photosclient.domain.model.MediaItem
import com.localcloud.photosclient.domain.model.MediaType
import com.localcloud.photosclient.presentation.viewer.MediaViewerEvent
import com.localcloud.photosclient.presentation.viewer.MediaViewerState
import com.localcloud.photosclient.presentation.viewer.MediaViewerUiEvent
import com.localcloud.photosclient.presentation.viewer.MediaViewerViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaViewerScreen(
    viewModel: MediaViewerViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    val window = (context as? Activity)?.window
    
    LaunchedEffect(viewModel.uiEvent) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is MediaViewerUiEvent.ShareMedia -> {
                    try {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = if (event.media.type == MediaType.IMAGE) "image/*" else "video/*"
                            putExtra(Intent.EXTRA_STREAM, event.media.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Media"))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Failed to share: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                is MediaViewerUiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                MediaViewerUiEvent.NavigateBack -> {
                    onNavigateBack()
                }
            }
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    var swipeOffset by remember { mutableStateOf(0f) }
    var isPagingEnabled by remember { mutableStateOf(true) }

    val swipeProgress = (swipeOffset / 600f).coerceIn(0f, 1f)
    val scale = 1f - (swipeProgress * 0.2f)
    val alpha = 1f - (swipeProgress * 0.5f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = alpha))
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = swipeOffset
            }
            .pointerInput(isPagingEnabled) {
                if (isPagingEnabled) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dragAmount ->
                            swipeOffset = (swipeOffset + dragAmount).coerceAtLeast(0f)
                        },
                        onDragEnd = {
                            if (swipeOffset > 300f) {
                                onNavigateBack()
                            } else {
                                swipeOffset = 0f
                            }
                        },
                        onDragCancel = { swipeOffset = 0f }
                    )
                }
            }
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (state.mediaItems.isEmpty() || state.error != null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Error",
                    tint = Color.Red,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = state.error ?: "No media found", 
                    color = Color.White
                )
            }
        } else {
            MediaPager(
                state = state,
                viewModel = viewModel,
                onZoomChanged = { isZoomed -> isPagingEnabled = !isZoomed },
                onTap = { 
                    viewModel.onEvent(MediaViewerEvent.ToggleUiVisibility)
                    if (window != null) {
                        val insetsController = WindowCompat.getInsetsController(window, view)
                        if (state.isUiVisible) {
                            insetsController.hide(WindowInsetsCompat.Type.systemBars())
                            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        } else {
                            insetsController.show(WindowInsetsCompat.Type.systemBars())
                        }
                    }
                }
            )
        }

        if (state.mediaItems.isNotEmpty() && !state.isLoading) {
            val safeIndex = state.currentIndex.coerceIn(0, (state.mediaItems.size - 1).coerceAtLeast(0))
            val currentItem = state.mediaItems.getOrNull(safeIndex)

            AnimatedVisibility(
                visible = state.isUiVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                TopOverlay(
                    currentItem = currentItem,
                    onBackClick = {
                        viewModel.onEvent(MediaViewerEvent.OnBackClick)
                        onNavigateBack()
                    },
                    onInfoClick = { viewModel.onEvent(MediaViewerEvent.ToggleInfoSheetVisibility) }
                )
            }

            AnimatedVisibility(
                visible = state.isUiVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                BottomOverlay(
                    isCloudOnly = currentItem?.localAvailability == com.localcloud.photosclient.data.LocalAvailability.CLOUD_ONLY,
                    onShare = { viewModel.onEvent(MediaViewerEvent.OnShare) },
                    onDelete = { viewModel.onEvent(MediaViewerEvent.OnDelete) },
                    onBackup = { viewModel.onEvent(MediaViewerEvent.OnManualBackup) },
                    onRestore = { viewModel.onEvent(MediaViewerEvent.OnRestore) }
                )
            }

            if (state.isInfoSheetVisible && currentItem != null) {
                MediaInfoSheet(
                    item = currentItem,
                    onDismiss = { viewModel.onEvent(MediaViewerEvent.ToggleInfoSheetVisibility) }
                )
            } else if (currentItem != null && !state.isUiVisible) {
                BottomInfoPill(
                    media = currentItem,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                )
            }
        }
    }
}

@Composable
fun BottomInfoPill(media: MediaItem, modifier: Modifier = Modifier) {
    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault()) }
    val dateStr = dateFormatter.format(Date(media.dateModified))
    val sizeStr = android.text.format.Formatter.formatShortFileSize(LocalContext.current, media.size)
    val dimenStr = if (media.width > 0) "${media.width}x${media.height}" else ""

    Surface(
        color = Color.Black.copy(alpha = 0.6f),
        shape = CircleShape,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = dateStr,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp
            )
            if (dimenStr.isNotEmpty()) {
                Box(Modifier.size(3.dp).background(Color.White.copy(alpha = 0.4f), CircleShape))
                Text(
                    text = dimenStr,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }
            Box(Modifier.size(3.dp).background(Color.White.copy(alpha = 0.4f), CircleShape))
            Text(
                text = sizeStr,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaInfoSheet(
    item: MediaItem,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Details",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            InfoRow(label = "Name", value = item.displayName)
            InfoRow(label = "Type", value = item.type.name)
            
            val sizeMb = item.size / (1024f * 1024f)
            InfoRow(label = "Size", value = String.format("%.2f MB", sizeMb))
            
            InfoRow(label = "Resolution", value = "${item.width} x ${item.height}")
            
            val date = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault()).format(Date(item.dateModified * 1000))
            InfoRow(label = "Date Modified", value = date)
            
            InfoRow(label = "Path", value = item.uri.path ?: "Unknown")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun BottomOverlay(
    isCloudOnly: Boolean,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit
) {
    Surface(
        color = Color.Black.copy(alpha = 0.5f),
        contentColor = Color.White,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionButton(icon = Icons.Default.Share, label = "Share", onClick = onShare)
            
            if (isCloudOnly) {
                ActionButton(
                    icon = Icons.Default.CloudDownload, 
                    label = "Restore", 
                    onClick = onRestore
                )
            } else {
                ActionButton(
                    icon = Icons.Default.CloudUpload, 
                    label = "Backup", 
                    onClick = onBackup
                )
            }

            ActionButton(icon = Icons.Default.Delete, label = "Delete", onClick = onDelete, tint = Color.Red)
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = Color.White
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaPager(
    state: MediaViewerState,
    viewModel: MediaViewerViewModel,
    onZoomChanged: (Boolean) -> Unit,
    onTap: () -> Unit
) {
    var isZoomed by remember { mutableStateOf(false) }
    
    val initialPage = state.currentIndex.coerceIn(0, (state.mediaItems.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { state.mediaItems.size }
    )

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                if (page != state.currentIndex && page in state.mediaItems.indices) {
                    viewModel.onEvent(MediaViewerEvent.OnPageChanged(page))
                    isZoomed = false
                    onZoomChanged(false)
                }
            }
    }

    LaunchedEffect(state.currentIndex) {
        val boundedIndex = state.currentIndex.coerceIn(0, (state.mediaItems.size - 1).coerceAtLeast(0))
        if (boundedIndex != pagerState.currentPage && !pagerState.isScrollInProgress) {
            pagerState.scrollToPage(boundedIndex)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondBoundsPageCount = 1,
        userScrollEnabled = !isZoomed
    ) { page ->
        if (page !in state.mediaItems.indices) return@HorizontalPager
        
        val mediaItem = state.mediaItems[page]
        val isCurrentPage = pagerState.currentPage == page
        
        val cachedPath = mediaItem.remoteId?.let { state.downloadedMediaMap[it] }
        val cachedUri = cachedPath?.let { android.net.Uri.fromFile(java.io.File(it)) }
        
        when (mediaItem.type) {
            com.localcloud.photosclient.domain.model.MediaType.IMAGE -> {
                ZoomableImage(
                    mediaItem = mediaItem,
                    cachedUri = cachedUri,
                    isCurrentPage = isCurrentPage,
                    onTap = onTap,
                    onZoomChanged = {
                        isZoomed = it
                        onZoomChanged(it)
                    }
                )
            }
            com.localcloud.photosclient.domain.model.MediaType.VIDEO -> {
                VideoPlayer(
                    mediaItem = mediaItem,
                    cachedUri = cachedUri,
                    isCurrentPage = isCurrentPage,
                    onTap = onTap
                )
            }
        }
    }
}

@Composable
private fun TopOverlay(
    currentItem: MediaItem?,
    onBackClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    Surface(
        color = Color.Black.copy(alpha = 0.5f),
        contentColor = Color.White,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = currentItem?.displayName ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (currentItem != null) {
                    val sizeMb = currentItem.size / (1024f * 1024f)
                    Text(
                        text = String.format("%.2f MB", sizeMb),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                }
            }

            IconButton(onClick = onInfoClick) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Information",
                    tint = Color.White
                )
            }
        }
    }
}
