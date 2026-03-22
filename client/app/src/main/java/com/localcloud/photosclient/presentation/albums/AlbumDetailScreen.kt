package com.localcloud.photosclient.presentation.albums

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.localcloud.photosclient.data.LocalMedia
import com.localcloud.photosclient.presentation.timeline.TimelineScreen
import com.localcloud.photosclient.ui.MainViewModel
import com.localcloud.photosclient.ui.theme.PureBlack
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumId: String,
    albumName: String,
    viewModel: MainViewModel,
    albumsViewModel: AlbumsViewModel,
    onBackClick: () -> Unit,
    onMediaClick: (LocalMedia, List<LocalMedia>) -> Unit
) {
    val albumTimelineFlow = remember(albumId) {
        albumsViewModel.getMediaByAlbum(albumId).map { list ->
            listOf(MainViewModel.TimelineGroup(albumName, list))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(albumName, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PureBlack)
            )
        },
        containerColor = PureBlack
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            TimelineScreen(
                viewModel = viewModel,
                onMediaClick = onMediaClick,
                overrideTimelineFlow = albumTimelineFlow
            )
        }
    }
}
