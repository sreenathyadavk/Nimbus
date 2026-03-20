package com.localcloud.photosclient.presentation.album

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.animation.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.localcloud.photosclient.MediaItem
import com.localcloud.photosclient.data.LocalMedia
import com.localcloud.photosclient.presentation.home.HomeSelectionEvent
import com.localcloud.photosclient.ui.MainViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    folderPath: String,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onMediaClick: (LocalMedia, List<LocalMedia>) -> Unit
) {
    val mediaFiles by viewModel.getMediaByFolder(folderPath).collectAsState(initial = emptyList())
    val selectionState by viewModel.selectionState.collectAsState()
    
    val albumName = File(folderPath).name

    if (selectionState.isSelectionMode) {
        BackHandler {
            viewModel.onSelectionEvent(HomeSelectionEvent.ClearSelection)
        }
    }

    Scaffold(
        topBar = {
            if (selectionState.isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectionState.selectedCount} Selected") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.onSelectionEvent(HomeSelectionEvent.ClearSelection) }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear Selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.triggerManualSyncForSelectedItems() }) {
                            Icon(imageVector = Icons.Default.Send, contentDescription = "Backup Selected")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            } else {
                TopAppBar(
                    title = { Text(albumName, style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    ) { padding ->
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }
        
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + expandVertically()
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(mediaFiles, key = { it.id }) { media ->
                    val isSelected = selectionState.selectedItems.contains(media.id)
                    MediaItem(
                        media = media,
                        isSelected = isSelected,
                        onClick = {
                            if (selectionState.isSelectionMode) {
                                viewModel.onSelectionEvent(HomeSelectionEvent.ToggleSelection(media))
                            } else {
                                onMediaClick(media, mediaFiles)
                            }
                        },
                        onLongPress = {
                            viewModel.onSelectionEvent(HomeSelectionEvent.LongPress(media))
                        }
                    )
                }
            }
        }
    }
}
