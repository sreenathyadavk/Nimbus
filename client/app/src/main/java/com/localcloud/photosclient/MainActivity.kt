package com.localcloud.photosclient

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.localcloud.photosclient.sync.MediaStoreObserver
import com.localcloud.photosclient.sync.MediaStoreManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.LazyGridState
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.activity.compose.BackHandler
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import androidx.compose.ui.draw.scale
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.localcloud.photosclient.data.LocalMedia
import com.localcloud.photosclient.ui.MainViewModel
import com.localcloud.photosclient.presentation.viewer.MediaViewerScreen
import java.io.File
import androidx.navigation.NavType
import androidx.navigation.navArgument
import android.net.Uri
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import com.localcloud.photosclient.presentation.album.AlbumDetailScreen
import com.localcloud.photosclient.presentation.timeline.TimelineScreen
import com.localcloud.photosclient.presentation.failed.FailedUploadsScreen
import androidx.compose.animation.*
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.hilt.navigation.compose.hiltViewModel
import com.localcloud.photosclient.presentation.settings.SettingsScreen
import com.localcloud.photosclient.presentation.settings.SettingsViewModel
import com.localcloud.photosclient.presentation.home.HomeSelectionEvent
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ErrorOutline
import com.localcloud.photosclient.ui.SyncStats
import com.localcloud.photosclient.ui.Album
import com.localcloud.photosclient.presentation.home.HomeSelectionState
import androidx.compose.foundation.BorderStroke
import com.localcloud.photosclient.presentation.components.DenseMediaItem

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var mediaStoreObserver: MediaStoreObserver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        mediaStoreObserver = MediaStoreObserver(
            context = this,
            scope = lifecycleScope,
            mediaStoreManager = viewModel.mediaStoreManager
        )
        
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    primary = Color(0xFFBB86FC)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                viewModel = viewModel,
                                onMediaClick = { media, allMedia ->
                                    val index = allMedia.indexOf(media)
                                    android.util.Log.d("MainActivity", "onMediaClick triggered. Index: $index / ${allMedia.size}")
                                    if (index != -1) {
                                        viewModel.onMediaClick(media, allMedia)
                                        android.util.Log.d("MainActivity", "Navigating to viewer/$index")
                                        navController.navigate("viewer/$index")
                                    }
                                },
                                onSettingsClick = { navController.navigate("settings") },
                                onAlbumClick = { folderPath -> 
                                    navController.navigate("album/${Uri.encode(folderPath)}")
                                },
                                onFailedClick = { navController.navigate("failed_uploads") }
                            )
                        }
                        composable(
                            route = "album/{folderPath}",
                            arguments = listOf(navArgument("folderPath") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val encodedFolder = backStackEntry.arguments?.getString("folderPath") ?: ""
                            val folderPath = Uri.decode(encodedFolder)
                            
                            AlbumDetailScreen(
                                folderPath = folderPath,
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onMediaClick = { media, allMedia ->
                                    val index = allMedia.indexOf(media)
                                    if (index != -1) {
                                        viewModel.onMediaClick(media, allMedia)
                                        navController.navigate("viewer/$index")
                                    }
                                }
                            )
                        }
                        composable(
                            route = "viewer/{startIndex}",
                            arguments = listOf(navArgument("startIndex") { type = NavType.IntType })
                        ) {
                            MediaViewerScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable("failed_uploads") {
                            FailedUploadsScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable("settings") {
                            val settingsViewModel = hiltViewModel<SettingsViewModel>()
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToFreeUpSpace = { navController.navigate("free_up_space") }
                            )
                        }
                        composable("free_up_space") {
                            com.localcloud.photosclient.presentation.settings.FreeUpSpaceScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
    override fun onStart() {
        super.onStart()
        mediaStoreObserver.register()
    }

    override fun onStop() {
        super.onStop()
        mediaStoreObserver.unregister()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel, 
    onMediaClick: (LocalMedia, List<LocalMedia>) -> Unit,
    onSettingsClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onFailedClick: () -> Unit
) {
    val context = LocalContext.current
    val isSyncing by viewModel.isSyncing.collectAsState()
    val selectionState by viewModel.selectionState.collectAsState()
    val stats by viewModel.syncStatsFlow.collectAsState()
    
    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    var hasPermissions by remember {
        mutableStateOf(permissionsToRequest.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val areGranted = permissionsMap.values.all { it }
        hasPermissions = areGranted
        if (areGranted) {
            viewModel.refreshMedia()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            launcher.launch(permissionsToRequest)
        } else {
            viewModel.refreshMedia()
        }
    }

    if (selectionState.isSelectionMode) {
        BackHandler {
            viewModel.onSelectionEvent(HomeSelectionEvent.ClearSelection)
        }
    }

    var selectedTabIndex by remember { mutableStateOf(0) }
    
    val photosGridState = rememberLazyGridState()
    val timelineGridState = rememberLazyGridState()
    val albumsGridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.uiEvent) {
        viewModel.uiEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val pagerState = rememberPagerState(pageCount = { 3 })
    
    // Sync selectedTabIndex with pagerState
    LaunchedEffect(selectedTabIndex) {
        if (pagerState.currentPage != selectedTabIndex) {
            pagerState.animateScrollToPage(selectedTabIndex)
        }
    }
    
    // Sync pagerState with selectedTabIndex
    LaunchedEffect(pagerState.currentPage) {
        selectedTabIndex = pagerState.currentPage
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!selectionState.isSelectionMode) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    tonalElevation = 0.dp
                ) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Photo, contentDescription = "Photos") },
                        label = { Text("Photos") },
                        selected = selectedTabIndex == 0,
                        onClick = { 
                            if (selectedTabIndex == 0) {
                                coroutineScope.launch { photosGridState.animateScrollToItem(0) }
                            } else {
                                selectedTabIndex = 0 
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.PhotoAlbum, contentDescription = "Albums") },
                        label = { Text("Albums") },
                        selected = selectedTabIndex == 1,
                        onClick = { 
                            if (selectedTabIndex == 1) {
                                coroutineScope.launch { albumsGridState.animateScrollToItem(0) }
                            } else {
                                selectedTabIndex = 1 
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.ViewTimeline, contentDescription = "Timeline") },
                        label = { Text("Timeline") },
                        selected = selectedTabIndex == 2,
                        onClick = { 
                            if (selectedTabIndex == 2) {
                                coroutineScope.launch { timelineGridState.animateScrollToItem(0) }
                            } else {
                                selectedTabIndex = 2 
                            }
                        }
                    )
                }
            }
        },
        topBar = {
            if (selectionState.isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectionState.selectedCount}") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.onSelectionEvent(HomeSelectionEvent.ClearSelection) }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear Selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.triggerManualSyncForSelectedItems() }) {
                            Icon(imageVector = Icons.Default.CloudUpload, contentDescription = "Backup Selected")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            } else {
                MediumTopAppBar(
                    title = { 
                        Text(
                            text = when(selectedTabIndex) {
                                0 -> "Photos"
                                1 -> "Albums"
                                2 -> "Timeline"
                                else -> "Nimbus"
                            },
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                        ) 
                    },
                    actions = {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp).padding(end = 16.dp), 
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                        } else if (stats.failed > 0) {
                             IconButton(onClick = onFailedClick) {
                                Icon(imageVector = Icons.Default.Error, contentDescription = "Failed Uploads", tint = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            IconButton(onClick = viewModel::triggerGlobalManualBackup) {
                                Icon(imageVector = Icons.Default.CloudDone, contentDescription = "Synced", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.mediumTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background
                    ),
                    scrollBehavior = scrollBehavior
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // PHASE 8: Redesigned Sync Dashboard
                SyncDashboard(stats = stats)
 
                // PHASE 3: Sync Status Bar
                if (stats.uploading > 0 || stats.pending > 0) {
                    SyncStatusBar(stats = stats)
                }
 
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = !selectionState.isSelectionMode
                    ) { index ->
                        when (index) {
                            0 -> {
                                GalleryGrid(
                                    viewModel = viewModel, 
                                    onMediaClick = onMediaClick,
                                    selectionState = selectionState,
                                    onSelectionEvent = viewModel::onSelectionEvent,
                                    gridState = photosGridState
                                )
                            }
                            1 -> {
                                AlbumsGrid(
                                    viewModel = viewModel,
                                    onAlbumClick = onAlbumClick,
                                    gridState = albumsGridState
                                )
                            }
                            2 -> {
                                TimelineScreen(
                                    viewModel = viewModel,
                                    onMediaClick = onMediaClick,
                                    gridState = timelineGridState
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SyncStatusBar(stats: SyncStats) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Backing up ${stats.synced} of ${stats.total}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${stats.overallProgressPercentage}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            LinearProgressIndicator(
                progress = (stats.overallProgressPercentage / 100f).coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
            )
            
            if (stats.uploading > 0) {
                Text(
                    text = "Currently uploading ${stats.uploading} item${if (stats.uploading > 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}



@Composable
fun AlbumsGrid(viewModel: MainViewModel, onAlbumClick: (String) -> Unit, gridState: LazyGridState = rememberLazyGridState()) {
    val albums by viewModel.albumFlow.collectAsState()
    
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        items(albums) { album ->
            AlbumGridCard(album = album, onClick = { onAlbumClick(album.folderPath) })
        }
    }
}

@Composable
fun AlbumGridCard(album: Album, onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        try {
                            awaitRelease()
                        } finally {
                            isPressed = false
                        }
                    },
                    onTap = { onClick() }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.DarkGray)
        ) {
            AsyncImage(
                model = File(album.coverPath),
                contentDescription = album.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = album.name, 
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, 
            maxLines = 1,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = album.count.toString(), 
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FailedUploadsCard(count: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Failed Uploads",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "$count items could not be backed up. Tap to manage.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun GalleryGrid(
    viewModel: MainViewModel, 
    onMediaClick: (LocalMedia, List<LocalMedia>) -> Unit,
    selectionState: HomeSelectionState,
    onSelectionEvent: (HomeSelectionEvent) -> Unit,
    gridState: LazyGridState = rememberLazyGridState()
) {
    val timelineGroups by viewModel.photosFlow.collectAsState()
    val allMedia = remember(timelineGroups) { timelineGroups.flatMap { it.items } }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        timelineGroups.forEach { group ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
                )
            }
            
            items(group.items, key = { it.id }) { media ->
                val isSelected = selectionState.selectedItems.contains(media.id)
                DenseMediaItem(
                    media = media, 
                    isSelected = isSelected,
                    onClick = { 
                        if (selectionState.isSelectionMode) {
                            onSelectionEvent(HomeSelectionEvent.ToggleSelection(media))
                        } else {
                            onMediaClick(media, allMedia) 
                        }
                    },
                    onLongPress = {
                        onSelectionEvent(HomeSelectionEvent.LongPress(media))
                    }
                )
            }
        }
    }
}

@Composable
fun MediaItem(
    media: LocalMedia, 
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.DarkGray)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        try {
                            awaitRelease()
                        } finally {
                            isPressed = false
                        }
                    },
                    onTap = { onClick() },
                    onLongPress = { onLongPress() }
                )
            }
    ) {
        val isCloudOnly = media.localAvailability == com.localcloud.photosclient.data.LocalAvailability.CLOUD_ONLY

        AsyncImage(
            model = if (isCloudOnly) media.remoteId else File(media.path), // Temporarily use remoteId for coil placeholder, or just path if file doesn't exist coil will handle it. We should ideally have a cache mechanism here later.
            contentDescription = "Photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            alpha = if (isCloudOnly) 0.5f else 1f
        )
        
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            )
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(24.dp)
                    .background(Color.White, CircleShape)
            )
        }

        if (isCloudOnly) {
            Icon(
                imageVector = Icons.Default.CloudDone,
                contentDescription = "Cloud Only",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp)
            )
        }
        
        // Video Indicator overlay
        if (media.mediaType.startsWith("video")) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Video",
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(32.dp).background(Color.Black.copy(alpha = 0.3f), CircleShape)
            )
            if (media.duration != null && media.duration > 0) {
                val sec = (media.duration / 1000) % 60
                val min = (media.duration / 1000) / 60
                Text(
                    text = String.format("%d:%02d", min, sec),
                    color = Color.White,
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha=0.6f), RoundedCornerShape(2.dp)).padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
        
        // Sync Status Indicator Overlay
        val statusIcon = when (media.uploadStatus) {
            "SUCCESS" -> Icons.Default.CloudDone
            "UPLOADING" -> Icons.Default.CloudUpload
            "FAILED" -> Icons.Default.Error
            else -> null
        }
        val statusColor = when (media.uploadStatus) {
            "SUCCESS" -> Color(0xFF4CAF50)
            "PENDING" -> Color(0xFFFFC107)
            "UPLOADING" -> Color(0xFF2196F3)
            "FAILED" -> Color(0xFFF44336)
            else -> Color.Transparent
        }
        
        if (statusIcon != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.8f))
                    .padding(2.dp)
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = media.uploadStatus,
                    tint = statusColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else if (media.uploadStatus == "PENDING") {
             Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
        }
    }
}

@Composable
fun SyncDashboard(stats: SyncStats) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SyncStatChip(
            count = stats.total,
            label = "Total",
            icon = Icons.Default.PhotoLibrary,
            color = MaterialTheme.colorScheme.primary
        )
        SyncStatChip(
            count = stats.synced,
            label = "Synced",
            icon = Icons.Default.CloudDone,
            color = Color(0xFF4CAF50) // Material Green
        )
        if (stats.uploading > 0) {
            SyncStatChip(
                count = stats.uploading,
                label = "Uploading",
                icon = Icons.Default.CloudUpload,
                color = Color(0xFF2196F3) // Material Blue
            )
        }
        if (stats.pending > 0) {
            SyncStatChip(
                count = stats.pending,
                label = "Pending",
                icon = Icons.Default.Schedule,
                color = Color(0xFFFF9800) // Material Orange
            )
        }
        if (stats.failed > 0) {
            SyncStatChip(
                count = stats.failed,
                label = "Failed",
                icon = Icons.Default.ErrorOutline,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun SyncStatChip(
    count: Int,
    label: String,
    icon: ImageVector,
    color: Color
) {
    Surface(
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = color.copy(alpha = 0.8f)
                )
            }
        }
    }
}


