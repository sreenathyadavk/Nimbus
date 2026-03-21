package com.localcloud.photosclient

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.localcloud.photosclient.data.LocalMedia
import com.localcloud.photosclient.presentation.albums.AlbumsScreen
import com.localcloud.photosclient.presentation.home.HomeSelectionEvent
import com.localcloud.photosclient.presentation.home.HomeSelectionState
import com.localcloud.photosclient.presentation.timeline.TimelineScreen
import com.localcloud.photosclient.presentation.viewer.MediaViewerScreen
import com.localcloud.photosclient.ui.MainViewModel
import com.localcloud.photosclient.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhotosClientTheme {
                MainContainer()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainContainer(viewModel: MainViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onMediaClick = { media: LocalMedia, list: List<LocalMedia> ->
                    val index = list.indexOf(media)
                    Log.d("NAV_DEBUG", "MainActivity: onMediaClick index=$index size=${list.size}")
                    viewModel.setActiveViewerList(list)
                    navController.navigate("viewer/${if (index != -1) index else 0}")
                }
            )
        }
        composable(
            route = "viewer/{index}",
            arguments = listOf(navArgument("index") { type = NavType.IntType })
        ) { backStackEntry ->
            val index = backStackEntry.arguments?.getInt("index") ?: 0
            MediaViewerScreen(
                initialIndex = index,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onMediaClick: (LocalMedia, List<LocalMedia>) -> Unit
) {
    val selectionState by viewModel.selectionState.collectAsState()
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopNavigationBar(
                selectionState = selectionState,
                onClearSelection = { viewModel.onSelectionEvent(HomeSelectionEvent.ClearSelection) }
            )
        },
        bottomBar = {
            if (!selectionState.isSelectionMode) {
                SamsungBottomNav(
                    selectedTabIndex = pagerState.currentPage,
                    onTabSelected = { index: Int ->
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    }
                )
            }
        },
        containerColor = PureBlack
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !selectionState.isSelectionMode
            ) { page ->
                when (page) {
                    0 -> TimelineScreen(
                        viewModel = viewModel, 
                        onMediaClick = onMediaClick
                    )
                    1 -> AlbumsScreen()
                }
            }
        }
    }
}

@Composable
fun TopNavigationBar(
    selectionState: HomeSelectionState,
    onClearSelection: () -> Unit
) {
    Surface(
        color = PureBlack,
        contentColor = White
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionState.isSelectionMode) {
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Default.Close, contentDescription = "Clear")
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "${selectionState.selectedCount} selected",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { /* Share */ }) { Icon(Icons.Default.Share, "Share") }
                IconButton(onClick = { /* Delete */ }) { Icon(Icons.Default.Delete, "Delete") }
            } else {
                Text(
                    text = "Samsung Photos",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { /* Search */ }) { Icon(Icons.Default.Search, "Search") }
                IconButton(onClick = { /* Menu */ }) { Icon(Icons.Default.MoreVert, "More") }
            }
        }
    }
}

@Composable
fun SamsungBottomNav(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    Surface(
        color = BottomNavBackground,
        contentColor = White,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(80.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(
                icon = if (selectedTabIndex == 0) Icons.Default.Photo else Icons.Default.Photo,
                label = "Pictures",
                isSelected = selectedTabIndex == 0,
                onClick = { onTabSelected(0) }
            )
            BottomNavItem(
                icon = if (selectedTabIndex == 1) Icons.Default.Collections else Icons.Default.Collections,
                label = "Albums",
                isSelected = selectedTabIndex == 1,
                onClick = { onTabSelected(1) }
            )
        }
    }
}

@Composable
fun BottomNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) SamsungBlue else UnselectedNav,
            modifier = Modifier.size(26.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (isSelected) SamsungBlue else UnselectedNav,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
