package com.localcloud.photosclient.presentation.albums

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.localcloud.photosclient.data.LocalMedia
import com.localcloud.photosclient.presentation.timeline.TimelineScreen
import com.localcloud.photosclient.ui.MainViewModel
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    bucketName: String,
    viewModel: MainViewModel,
    onMediaClick: (LocalMedia, List<LocalMedia>) -> Unit,
    onBackClick: () -> Unit
) {
    // Create a temporary filtered viewModel or just filter the flow here
    // For simplicity, we'll filter the timelineFlow
    val albumTimelineFlow = remember(bucketName) {
        viewModel.timelineFlow.map { groups ->
            groups.map { group ->
                group.copy(items = group.items.filter { it.bucketName == bucketName })
            }.filter { it.items.isNotEmpty() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(bucketName) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            // We need a modified TimelineScreen that takes a custom flow
            // But TimelineScreen currently uses viewModel.timelineFlow directly.
            // Let's refactor TimelineScreen to take a flow.
            TimelineScreen(
                viewModel = viewModel,
                onMediaClick = onMediaClick,
                overrideTimelineFlow = albumTimelineFlow
            )
        }
    }
}
