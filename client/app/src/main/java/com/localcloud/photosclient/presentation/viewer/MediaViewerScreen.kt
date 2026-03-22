package com.localcloud.photosclient.presentation.viewer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem as Media3MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.localcloud.photosclient.data.LocalMedia
import com.localcloud.photosclient.domain.model.MediaType
import com.localcloud.photosclient.ui.MainViewModel
import com.localcloud.photosclient.ui.theme.*
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    initialIndex: Int,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val mediaList by viewModel.activeViewerList.collectAsStateWithLifecycle(emptyList())
    val context = LocalContext.current
    val view = LocalView.current
    
    // Ensure status bar is dark (white icons) for the black viewer background
    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
    }
    
    if (mediaList.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }

    val pagerState = rememberPagerState(
        initialPage = if (initialIndex < mediaList.size) initialIndex else 0,
        pageCount = { mediaList.size }
    )
    val coroutineScope = rememberCoroutineScope()
    var barsVisible by remember { mutableStateOf(true) }
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    var showInfo by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = true
        ) { page ->
            if (page < mediaList.size) {
                val item = mediaList[page]
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.type == MediaType.VIDEO) {
                        VideoPlayer(uri = item.uri)
                    } else {
                        ZoomableAsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(item.uri)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            onClick = { barsVisible = !barsVisible }
                        )
                    }
                }
            }
        }

        val currentItem = if (pagerState.currentPage < mediaList.size) mediaList[pagerState.currentPage] else null

        // Top Bar
        AnimatedVisibility(
            visible = barsVisible,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                
                Row {
                    IconButton(onClick = { rotationAngle += 90f }) {
                        Icon(Icons.Filled.RotateRight, contentDescription = "Rotate", tint = Color.White)
                    }
                    
                    currentItem?.let { item ->
                        val isBackedUp = item.uploadStatus == "SUCCESS"
                        IconButton(onClick = {
                            if (!isBackedUp) {
                                viewModel.triggerUploadForItem(item.toLocalMedia())
                            }
                        }) {
                            Icon(
                                imageVector = if (isBackedUp) Icons.Filled.CloudDone else Icons.Filled.CloudUpload,
                                contentDescription = if (isBackedUp) "Backed up" else "Backup now",
                                tint = if (isBackedUp) Color(0xFF4CAF50) else Color.White
                            )
                        }
                    }

                    IconButton(onClick = { /* More */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                    }
                }
            }
        }

        // Bottom Bar + Thumbnail Strip
        AnimatedVisibility(
            visible = barsVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .navigationBarsPadding()
            ) {
                // Thumbnail Strip
                ThumbnailStrip(
                    mediaList = mediaList,
                    currentIndex = pagerState.currentPage,
                    onThumbnailClick = { index ->
                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                    }
                )

                currentItem?.let { item ->
                    // Actions
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.toggleFavorite(item.toLocalMedia()) }) {
                            Icon(
                                imageVector = if (item.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                tint = if (item.isFavorite) Color.Red else Color.White,
                                contentDescription = "Favourite"
                            )
                        }

                        IconButton(onClick = { /* Edit */ }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = Color.White)
                        }

                        IconButton(onClick = { showInfo = true }) {
                            Icon(Icons.Outlined.Info, contentDescription = "Info", tint = Color.White)
                        }

                        IconButton(onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = if (item.type == MediaType.VIDEO) "video/*" else "image/*"
                                putExtra(Intent.EXTRA_STREAM, item.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share via"))
                        }) {
                            Icon(Icons.Outlined.Share, contentDescription = "Share", tint = Color.White)
                        }

                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = Color.White)
                        }
                    }
                }
            }
        }

        if (showInfo && currentItem != null) {
            ModalBottomSheet(
                onDismissRequest = { showInfo = false },
                containerColor = Color(0xFF1A1A1A),
                windowInsets = WindowInsets(0)
            ) {
                val media = currentItem.toLocalMedia()
                Column(Modifier.padding(24.dp)) {
                    Text("File info", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(24.dp))
                    InfoRow("Name", currentItem.displayName)
                    InfoRow("Date", formatDate(media.dateAdded))
                    InfoRow("Size", formatFileSize(currentItem.size))
                    InfoRow("Path", media.path)
                    Spacer(Modifier.navigationBarsPadding())
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        if (showDeleteDialog && currentItem != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                containerColor = Color(0xFF1A1A1A),
                title = { Text("Move to recycle bin?", color = Color.White) },
                text = { Text("This item will be moved to recycle bin.", color = Color(0xFF888888)) },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteDialog = false
                        viewModel.deleteMedia(currentItem.toLocalMedia())
                        if (mediaList.size <= 1) {
                            onBack()
                        }
                    }) {
                        Text("Move", color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel", color = Color.White)
                    }
                }
            )
        }
    }
}

@Composable
fun VideoPlayer(uri: Uri) {
    val context = LocalContext.current
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = Media3MediaItem.fromUri(uri)
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }
    
    DisposableEffect(uri) {
        onDispose { exoPlayer.release() }
    }
    
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                controllerAutoShow = true
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(label, color = Color(0xFF888888), modifier = Modifier.width(100.dp), fontSize = 14.sp)
        Text(value, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
fun ThumbnailStrip(
    mediaList: List<com.localcloud.photosclient.domain.model.MediaItem>,
    currentIndex: Int,
    onThumbnailClick: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    
    LaunchedEffect(currentIndex) {
        if (mediaList.isNotEmpty() && currentIndex < mediaList.size) {
            listState.animateScrollToItem(currentIndex)
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(mediaList) { index, item ->
            Box(
                modifier = Modifier
                    .size(if (index == currentIndex) 48.dp else 40.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(
                        width = if (index == currentIndex) 2.dp else 0.dp,
                        color = if (index == currentIndex) AccentOrange else Color.Transparent,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .clickable { onThumbnailClick(index) }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.uri)
                        .size(100, 100)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private fun com.localcloud.photosclient.domain.model.MediaItem.toLocalMedia(): LocalMedia {
    return LocalMedia(
        id = this.id,
        path = "", 
        hash = "",
        uploadStatus = this.uploadStatus,
        mediaType = if (this.type == MediaType.VIDEO) "video" else "image",
        dateAdded = this.dateModified,
        width = this.width,
        height = this.height,
        duration = this.duration,
        isFavorite = this.isFavorite,
        isDeleted = this.isDeleted
    )
}

fun formatDate(dateMillis: Long): String {
    return SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.getDefault()).format(Date(dateMillis))
}

fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return "${String.format("%.1f", size / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
}
