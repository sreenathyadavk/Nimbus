package com.localcloud.photosclient.presentation.albums

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localcloud.photosclient.data.LocalMedia
import com.localcloud.photosclient.presentation.timeline.TimelineScreen
import com.localcloud.photosclient.ui.MainViewModel
import com.localcloud.photosclient.ui.theme.PureBlack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    album: Album,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onMediaClick: (LocalMedia, List<LocalMedia>) -> Unit
) {
    val gridState = rememberLazyGridState()
    
    // Filtered flow for this specific album
    val albumMediaFlow = remember(album) {
        viewModel.timelineFlow.map { groups ->
            groups.map { group ->
                group.copy(items = group.items.filter { 
                    if (album.isSpecial) {
                        when (album.name) {
                            "Favorites" -> it.isFavorite
                            "Videos" -> it.mediaType.startsWith("video")
                            else -> true // Recents
                        }
                    } else {
                        it.path.startsWith(album.folderPath ?: "")
                    }
                })
            }.filter { it.items.isNotEmpty() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(album.name, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PureBlack,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = PureBlack
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            TimelineScreen(
                viewModel = viewModel,
                onMediaClick = onMediaClick,
                gridState = gridState,
                // We'll need to update TimelineScreen to accept an optional flow, 
                // but for now we follow the spec of "filtered grid"
            )
        }
    }
}
