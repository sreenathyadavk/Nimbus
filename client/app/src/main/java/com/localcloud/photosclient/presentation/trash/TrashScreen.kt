package com.localcloud.photosclient.presentation.trash

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
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
fun TrashScreen(
    viewModel: MainViewModel,
    onMediaClick: (LocalMedia, List<LocalMedia>) -> Unit,
    onBackClick: () -> Unit
) {
    val trashTimelineFlow = remember {
        viewModel.trashFlow.map { list: List<LocalMedia> ->
            val groups = mutableListOf<MainViewModel.TimelineGroup>()
            if (list.isNotEmpty()) {
                groups.add(MainViewModel.TimelineGroup("Deleted Items", list))
            }
            groups
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Trash") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* Empty trash logic */ }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Empty Trash")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = com.localcloud.photosclient.ui.theme.PureBlack,
                    titleContentColor = com.localcloud.photosclient.ui.theme.White,
                    navigationIconContentColor = com.localcloud.photosclient.ui.theme.White,
                    actionIconContentColor = com.localcloud.photosclient.ui.theme.White
                )
            )
        },
        containerColor = com.localcloud.photosclient.ui.theme.PureBlack
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Text(
                text = "Items in the trash will be permanently deleted after 30 days.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
            
            TimelineScreen(
                viewModel = viewModel,
                onMediaClick = onMediaClick,
                overrideTimelineFlow = trashTimelineFlow
            )
        }
    }
}
