package com.localcloud.photosclient.presentation.timeline

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.localcloud.photosclient.data.LocalMedia
import com.localcloud.photosclient.ui.MainViewModel
import com.localcloud.photosclient.ui.theme.*
import java.text.SimpleDateFormat
import java.io.File
import java.util.*

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    viewModel: MainViewModel,
    onMediaClick: (LocalMedia, List<LocalMedia>) -> Unit,
    gridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    overrideTimelineFlow: kotlinx.coroutines.flow.Flow<List<MainViewModel.TimelineGroup>>? = null
) {
    val mediaItems by viewModel.timelineItems.collectAsStateWithLifecycle(emptyList())
    val syncStats by viewModel.syncStatsFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val timelineGroups by remember(mediaItems, overrideTimelineFlow) {
        if (overrideTimelineFlow != null) {
            mutableStateOf(emptyList<MainViewModel.TimelineGroup>())
        } else {
            derivedStateOf {
                val sorted = mediaItems.sortedByDescending { it.dateAdded }
                val groupedMap = LinkedHashMap<String, MutableList<LocalMedia>>()
                sorted.forEach { item ->
                    val label = getDateLabel(item.dateAdded)
                    if (!groupedMap.containsKey(label)) {
                        groupedMap[label] = mutableListOf()
                    }
                    groupedMap[label]?.add(item)
                }
                groupedMap.map { (title, items) -> 
                    MainViewModel.TimelineGroup(title, items) 
                }
            }
        }
    }

    val finalGroups = if (overrideTimelineFlow != null) {
        overrideTimelineFlow.collectAsState(initial = emptyList()).value
    } else {
        timelineGroups
    }

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(PureBlack)) {
        LazyVerticalStaggeredGrid(
            state = gridState,
            columns = StaggeredGridCells.Fixed(4),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalItemSpacing = 1.dp
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Pictures",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    if (syncStats.uploading > 0 || syncStats.pending > 0) {
                        SyncStatusCard(syncStats)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatCard("${mediaItems.size}", "Total", Icons.Default.Photo, Modifier.weight(1f))
                        StatCard("${syncStats.synced}", "Synced", Icons.Default.CloudDone, Modifier.weight(1f))
                        StatCard("${syncStats.uploading}", "Uploading", Icons.Default.CloudUpload, Modifier.weight(1f))
                        StatCard("${syncStats.pending}", "Pending", Icons.Default.Pending, Modifier.weight(1f))
                    }
                }
            }

            finalGroups.forEach { group ->
                item(span = StaggeredGridItemSpan.FullLine, key = group.title) {
                    TimelineSectionHeader(label = group.title)
                }

                items(group.items, key = { it.id }) { item ->
                    key(item.id) {
                        val isSelected = item.id in selectedIds
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .background(CardDark)
                                .graphicsLayer {
                                    alpha = if (isSelectionMode && !isSelected) 0.6f else 1f
                                    scaleX = if (isSelectionMode) 0.93f else 1f
                                    scaleY = if (isSelectionMode) 0.93f else 1f
                                }
                                .then(
                                    if (isSelected) Modifier.border(2.dp, AccentOrange) else Modifier
                                )
                                .combinedClickable(
                                    onClick = {
                                        if (isSelectionMode) {
                                            selectedIds = if (isSelected)
                                                selectedIds - item.id
                                            else
                                                selectedIds + item.id
                                            if (selectedIds.isEmpty()) isSelectionMode = false
                                        } else {
                                            // Pass the item and the full list (or override list)
                                            val fullList = if (overrideTimelineFlow != null) {
                                                finalGroups.flatMap { it.items }
                                            } else {
                                                mediaItems
                                            }
                                            onMediaClick(item, fullList)
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelectionMode) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            isSelectionMode = true
                                        }
                                        selectedIds = selectedIds + item.id
                                    }
                                )
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(Uri.parse(getMediaLocalUri(item)))
                                    .crossfade(false)
                                    .size(200, 200)
                                    .memoryCacheKey(getMediaLocalUri(item))
                                    .diskCacheKey(getMediaLocalUri(item))
                                    .allowHardware(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                placeholder = ColorPainter(CardDark)
                            )

                            if (item.mediaType.startsWith("video", ignoreCase = true)) {
                                VideoBadge(item.duration)
                            }

                            if (item.isFavorite) {
                                Icon(
                                    imageVector = Icons.Filled.Favorite,
                                    contentDescription = null,
                                    tint = Color.Red,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(14.dp)
                                )
                            }

                            if (isSelectionMode) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(4.dp)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) AccentOrange else Color(0x88000000)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item(span = StaggeredGridItemSpan.FullLine) { Spacer(Modifier.height(100.dp)) }
        }

        // Selection Mode Top Bar
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.9f),
                modifier = Modifier.fillMaxSize().statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { 
                        isSelectionMode = false
                        selectedIds = emptySet()
                    }) {
                        Icon(Icons.Filled.Close, "Close", tint = Color.White)
                    }
                    Text(
                        "${selectedIds.size} selected",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Share selected
                    IconButton(onClick = {
                        val uris = mediaItems
                            .filter { it.id in selectedIds }
                            .map { Uri.parse(getMediaLocalUri(it)) }
                        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                            type = "image/*"
                            putParcelableArrayListExtra(
                                Intent.EXTRA_STREAM, 
                                ArrayList(uris)
                            )
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share"))
                    }) {
                        Icon(imageVector = Icons.Filled.Share, contentDescription = "Share", tint = Color.White)
                    }

                    IconButton(onClick = { showBulkDeleteDialog = true }) {
                        Icon(imageVector = Icons.Filled.Delete, contentDescription = "Delete", tint = Color.White)
                    }
                }
            }
        }

        if (showBulkDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showBulkDeleteDialog = false },
                containerColor = Color(0xFF1A1A1A),
                title = { Text("Move to recycle bin?", color = Color.White) },
                text = { Text("Move ${selectedIds.size} items to recycle bin?", color = Color(0xFF888888)) },
                confirmButton = {
                    TextButton(onClick = {
                        val idsToRemove = selectedIds.toSet()
                        mediaItems.filter { it.id in idsToRemove }.forEach { 
                            viewModel.deleteMedia(it)
                        }
                        isSelectionMode = false
                        selectedIds = emptySet()
                        showBulkDeleteDialog = false
                    }) {
                        Text("Move", color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBulkDeleteDialog = false }) {
                        Text("Cancel", color = Color.White)
                    }
                }
            )
        }
    }
}

@Composable
fun SyncStatusCard(syncStats: com.localcloud.photosclient.ui.SyncStats) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(12.dp))
            .background(Color(0xFF0D0D0D))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Backing up ${syncStats.synced} of ${syncStats.total}",
                    color = Color.White,
                    fontSize = 14.sp
                )
                Text(
                    text = "${syncStats.overallProgressPercentage}%",
                    color = Color(0xFF4CAF50),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = syncStats.overallProgressPercentage / 100f,
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = Color(0xFF4CAF50),
                trackColor = Color(0xFF1A1A1A)
            )
        }
    }
}

@Composable
fun StatCard(count: String, label: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(
        color = CardDark,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.height(56.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(count, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp)
                Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, lineHeight = 13.sp)
            }
        }
    }
}

@Composable
fun TimelineSectionHeader(label: String) {
    Text(
        text = label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().background(PureBlack).padding(start = 12.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun VideoBadge(duration: Long?) {
    Box(modifier = Modifier.fillMaxSize().padding(4.dp), contentAlignment = Alignment.BottomEnd) {
        Row(
            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
            if (duration != null) {
                Spacer(Modifier.width(2.dp))
                val min = (duration / 1000) / 60
                val sec = (duration / 1000) % 60
                Text(String.format("%d:%02d", min, sec), color = Color.White, fontSize = 10.sp)
            }
        }
    }
}

fun getDateLabel(dateMillis: Long): String {
    val photoDate = Calendar.getInstance().apply { timeInMillis = dateMillis }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val isSameDay = { a: Calendar, b: Calendar ->
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }
    return when {
        isSameDay(photoDate, today) -> "Today"
        isSameDay(photoDate, yesterday) -> "Yesterday"
        photoDate.timeInMillis > today.timeInMillis - (6 * 24 * 60 * 60 * 1000L) -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(dateMillis))
        photoDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) -> SimpleDateFormat("d MMMM", Locale.getDefault()).format(Date(dateMillis))
        else -> SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(dateMillis))
    }
}

private fun getMediaLocalUri(item: LocalMedia): String {
    val baseUri = if (item.mediaType.startsWith("video", ignoreCase = true)) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    return ContentUris.withAppendedId(baseUri, item.id).toString()
}
