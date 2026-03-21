package com.localcloud.photosclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.localcloud.photosclient.presentation.albums.AlbumsScreen
import com.localcloud.photosclient.presentation.home.HomeSelectionEvent
import com.localcloud.photosclient.presentation.timeline.TimelineScreen
import com.localcloud.photosclient.ui.MainViewModel
import com.localcloud.photosclient.ui.theme.PhotosClientTheme
import com.localcloud.photosclient.ui.theme.PureBlack
import com.localcloud.photosclient.ui.theme.BottomNavBackground
import com.localcloud.photosclient.ui.theme.SamsungBlue
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContent {
            PhotosClientTheme {
                MainContainer()
            }
        }
    }
}

@Composable
fun MainContainer(viewModel: MainViewModel = hiltViewModel()) {
    val selectionState by viewModel.selectionState.collectAsState()
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopNavigationBar(
                selectionState = selectionState,
                onClearSelection = { viewModel.onSelectionEvent(HomeSelectionEvent.ClearSelection) },
                onShare = { /* TODO */ },
                onDelete = { /* TODO */ }
            )
        },
        bottomBar = {
            if (!selectionState.isSelectionMode) {
                SamsungBottomNav(
                    selectedTabIndex = pagerState.currentPage,
                    onTabSelected = { index ->
                        androidx.compose.runtime.rememberCoroutineScope().launch {
                            pagerState.animateScrollToPage(index)
                        }
                    }
                )
            }
        },
        containerColor = PureBlack
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !selectionState.isSelectionMode
            ) { page ->
                when (page) {
                    0 -> TimelineScreen(viewModel = viewModel, onMediaClick = { _, _ -> /* Navigate to Viewer */ })
                    1 -> AlbumsScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopNavigationBar(
    selectionState: com.localcloud.photosclient.presentation.home.SelectionState,
    onClearSelection: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    TopAppBar(
        title = {
            if (selectionState.isSelectionMode) {
                Text(
                    text = "${selectionState.selectedCount} selected",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            } else {
                Text(
                    text = "Nimbus",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        navigationIcon = {
            if (selectionState.isSelectionMode) {
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        },
        actions = {
            if (selectionState.isSelectionMode) {
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                }
            } else {
                IconButton(onClick = { }) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                }
                IconButton(onClick = { }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = BottomNavBackground.copy(alpha = 0.9f)
        )
    )
}

@Composable
fun SamsungBottomNav(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    NavigationBar(
        containerColor = BottomNavBackground,
        tonalElevation = 0.dp,
        modifier = Modifier.height(60.dp)
    ) {
        NavigationBarItem(
            selected = selectedTabIndex == 0,
            onClick = { onTabSelected(0) },
            icon = {
                Icon(
                    imageVector = if (selectedTabIndex == 0) Icons.Filled.Photo else Icons.Outlined.Photo,
                    contentDescription = "Photos"
                )
            },
            label = { Text("Photos", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = Color.White,
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray,
                indicatorColor = Color.Transparent
            )
        )
        NavigationBarItem(
            selected = selectedTabIndex == 1,
            onClick = { onTabSelected(1) },
            icon = {
                Icon(
                    imageVector = if (selectedTabIndex == 1) Icons.Filled.PhotoLibrary else Icons.Outlined.PhotoLibrary,
                    contentDescription = "Albums"
                )
            },
            label = { Text("Albums", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = Color.White,
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray,
                indicatorColor = Color.Transparent
            )
        )
    }
}
