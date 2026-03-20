package com.localcloud.photosclient.presentation.favorites

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.localcloud.photosclient.data.LocalMedia
import com.localcloud.photosclient.presentation.timeline.TimelineScreen
import com.localcloud.photosclient.ui.MainViewModel
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: MainViewModel,
    onMediaClick: (LocalMedia, List<LocalMedia>) -> Unit,
    onBackClick: () -> Unit
) {
    // Group favorites by date for TimelineScreen
    val favoritesTimelineFlow = remember {
        viewModel.favoritesFlow.map { list ->
            val groups = mutableListOf<MainViewModel.TimelineGroup>()
            val dateFormat = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault())
            val dateGroups = list.groupBy { dateFormat.format(java.util.Date(it.dateAdded)) }
            
            dateGroups.entries.sortedByDescending { it.value.first().dateAdded }.forEach { (title, items) ->
                groups.add(MainViewModel.TimelineGroup(title, items))
            }
            groups
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Favorites") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            TimelineScreen(
                viewModel = viewModel,
                onMediaClick = onMediaClick,
                overrideTimelineFlow = favoritesTimelineFlow
            )
        }
    }
}
