package com.localcloud.photosclient.presentation.viewer

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.localcloud.photosclient.domain.model.MediaItem
import kotlinx.coroutines.flow.distinctUntilChanged
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalLayoutDirection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import androidx.core.content.FileProvider

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
    
    // Manage system bars visibility based on state
    LaunchedEffect(viewModel.uiEvent) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is MediaViewerUiEvent.ShareMedia -> {
                    try {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = if (event.media.type == com.localcloud.photosclient.domain.model.MediaType.IMAGE) "image/*" else "video/*"
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
    
    // Ensure system bars are restored when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
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
                viewModel = viewModel
            )
        }

        // Overlay UI
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
            }
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
                    icon = androidx.compose.material.icons.Icons.Default.CloudDownload, 
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
    viewModel: MediaViewerViewModel
) {
    // Guards against invalid startIndex
    val initialPage = state.currentIndex.coerceIn(0, (state.mediaItems.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { state.mediaItems.size }
    )

    // Sync pager state changes back to ViewModel
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                if (page != state.currentIndex && page in state.mediaItems.indices) {
                    viewModel.onEvent(MediaViewerEvent.OnPageChanged(page))
                }
            }
    }

    LaunchedEffect(state.currentIndex) {
        // Guard checking inside LaunchedEffect
        val boundedIndex = state.currentIndex.coerceIn(0, (state.mediaItems.size - 1).coerceAtLeast(0))
        if (boundedIndex != pagerState.currentPage && !pagerState.isScrollInProgress) {
            pagerState.scrollToPage(boundedIndex)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondBoundsPageCount = 1
    ) { page ->
        // Defensive bounds checking for pager
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
                    onTap = { viewModel.onEvent(MediaViewerEvent.ToggleUiVisibility) }
                )
            }
            com.localcloud.photosclient.domain.model.MediaType.VIDEO -> {
                VideoPlayer(
                    mediaItem = mediaItem,
                    cachedUri = cachedUri,
                    isCurrentPage = isCurrentPage,
                    onTap = { viewModel.onEvent(MediaViewerEvent.ToggleUiVisibility) }
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
