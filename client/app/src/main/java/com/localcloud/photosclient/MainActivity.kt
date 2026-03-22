package com.localcloud.photosclient

import android.os.Bundle
import android.net.Uri
import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.localcloud.photosclient.presentation.albums.AlbumDetailScreen
import com.localcloud.photosclient.presentation.albums.AlbumsScreen
import com.localcloud.photosclient.presentation.albums.AlbumsViewModel
import com.localcloud.photosclient.presentation.stories.StoriesScreen
import com.localcloud.photosclient.presentation.timeline.TimelineScreen
import com.localcloud.photosclient.presentation.trash.RecycleBinScreen
import com.localcloud.photosclient.presentation.viewer.MediaViewerScreen
import com.localcloud.photosclient.ui.MainViewModel
import com.localcloud.photosclient.ui.theme.NimbusTheme
import com.localcloud.photosclient.ui.theme.*
import com.localcloud.photosclient.presentation.settings.SettingsScreen
import com.localcloud.photosclient.presentation.settings.SettingsViewModel
import com.localcloud.photosclient.presentation.favourites.FavouritesScreen
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val albumsViewModel: AlbumsViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        
        setContent {
            NimbusTheme {
                val view = LocalView.current
                SideEffect {
                    val window = (view.context as Activity).window
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                }
                
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val selectionState by viewModel.selectionState.collectAsState()
                var showMenu by remember { mutableStateOf(false) }

                Scaffold(
                    bottomBar = {
                        val showBottomBar = currentRoute in listOf("pictures", "albums", "stories")
                        if (showBottomBar && !selectionState.isSelectionMode) {
                            NavigationBar(
                                modifier = Modifier.navigationBarsPadding(),
                                containerColor = Color(0xFF0D0D0D),
                                tonalElevation = 0.dp
                            ) {
                                NavigationBarItem(
                                    selected = currentRoute == "pictures",
                                    onClick = { 
                                        if (currentRoute != "pictures") {
                                            navController.navigate("pictures") {
                                                popUpTo("pictures") { inclusive = true }
                                            }
                                        }
                                    },
                                    icon = { Icon(Icons.Filled.Photo, contentDescription = null, modifier = Modifier.size(24.dp)) },
                                    label = { Text("Pictures", fontSize = 11.sp) },
                                    colors = navigationItemColors()
                                )
                                NavigationBarItem(
                                    selected = currentRoute == "albums",
                                    onClick = { 
                                        if (currentRoute != "albums") {
                                            navController.navigate("albums")
                                        }
                                    },
                                    icon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(24.dp)) },
                                    label = { Text("Albums", fontSize = 11.sp) },
                                    colors = navigationItemColors()
                                )
                                NavigationBarItem(
                                    selected = currentRoute == "stories",
                                    onClick = { 
                                        if (currentRoute != "stories") {
                                            navController.navigate("stories")
                                        }
                                    },
                                    icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(24.dp)) },
                                    label = { Text("Stories", fontSize = 11.sp) },
                                    colors = navigationItemColors()
                                )
                                NavigationBarItem(
                                    selected = false,
                                    onClick = { showMenu = true },
                                    icon = { Icon(Icons.Filled.Menu, contentDescription = null, modifier = Modifier.size(24.dp)) },
                                    label = { Text("Menu", fontSize = 11.sp) },
                                    colors = navigationItemColors()
                                )
                            }
                        }
                    },
                    containerColor = PureBlack,
                    contentWindowInsets = WindowInsets(0)
                ) { padding ->
                    Box(modifier = Modifier.padding(padding).imePadding()) {
                        NavHost(
                            navController = navController,
                            startDestination = "pictures"
                        ) {
                            composable("pictures") {
                                TimelineScreen(
                                    viewModel = viewModel,
                                    onMediaClick = { media, allMedia ->
                                        viewModel.setActiveViewerList(allMedia)
                                        val index = allMedia.indexOf(media)
                                        navController.navigate("viewer/$index")
                                    }
                                )
                            }
                            composable("albums") {
                                AlbumsScreen(
                                    viewModel = albumsViewModel,
                                    onAlbumClick = { id, name ->
                                        navController.navigate("album/${Uri.encode(id)}/${Uri.encode(name)}")
                                    }
                                )
                            }
                            composable("stories") {
                                StoriesScreen()
                            }
                            composable("favourites") {
                                FavouritesScreen(navController = navController, viewModel = viewModel)
                            }
                            composable("recycle_bin") {
                                RecycleBinScreen(navController = navController, viewModel = viewModel)
                            }
                            composable(
                                route = "viewer/{index}",
                                arguments = listOf(navArgument("index") { type = NavType.IntType })
                            ) { backStackEntry ->
                                val index = backStackEntry.arguments?.getInt("index") ?: 0
                                MediaViewerScreen(
                                    initialIndex = index,
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable(
                                route = "album/{id}/{name}",
                                arguments = listOf(
                                    navArgument("id") { type = NavType.StringType },
                                    navArgument("name") { type = NavType.StringType }
                                )
                            ) { backStackEntry ->
                                val id = Uri.decode(backStackEntry.arguments?.getString("id") ?: "")
                                val name = Uri.decode(backStackEntry.arguments?.getString("name") ?: "")
                                AlbumDetailScreen(
                                    albumId = id,
                                    albumName = name,
                                    viewModel = viewModel,
                                    albumsViewModel = albumsViewModel,
                                    onBackClick = { navController.popBackStack() },
                                    onMediaClick = { media, allMedia ->
                                        viewModel.setActiveViewerList(allMedia)
                                        val index = allMedia.indexOf(media)
                                        navController.navigate("viewer/$index")
                                    }
                                )
                            }
                            composable("settings") {
                                val settingsViewModel: SettingsViewModel = hiltViewModel()
                                SettingsScreen(
                                    viewModel = settingsViewModel,
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToFavorites = { navController.navigate("favourites") },
                                    onNavigateToTrash = { navController.navigate("recycle_bin") },
                                    onNavigateToFreeUpSpace = { /* TODO */ }
                                )
                            }
                        }

                        if (showMenu) {
                            ModalBottomSheet(
                                onDismissRequest = { showMenu = false },
                                containerColor = Color(0xFF1A1A1A),
                                scrimColor = Color(0x99000000),
                                dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray) },
                                windowInsets = WindowInsets(0)
                            ) {
                                MenuSheetContent(
                                    onDismiss = { showMenu = false },
                                    navController = navController
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun navigationItemColors() = NavigationBarItemDefaults.colors(
        selectedIconColor = AccentOrange,
        selectedTextColor = AccentOrange,
        unselectedIconColor = Color(0xFF666666),
        unselectedTextColor = Color(0xFF666666),
        indicatorColor = Color.Transparent
    )
}

@Composable
fun MenuSheetContent(onDismiss: () -> Unit, navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A))
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        
        // Quick Action Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SheetQuickAction("Videos", Icons.Filled.PlayCircle) { /* Navigate if needed */ }
            SheetQuickAction("Favourites", Icons.Filled.Favorite) {
                onDismiss()
                navController.navigate("favourites")
            }
            SheetQuickAction("Recent", Icons.Filled.History) { /* Navigate if needed */ }
            SheetQuickAction("Locations", Icons.Filled.LocationOn) { /* Navigate if needed */ }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Grid Actions (2x2)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MenuActionCard(
                icon = Icons.Outlined.Group,
                label = "Shared albums",
                modifier = Modifier.weight(1f)
            )
            MenuActionCard(
                icon = Icons.Outlined.CleaningServices,
                label = "Clean out",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MenuActionCard(
                icon = Icons.Outlined.Delete,
                label = "Recycle bin",
                modifier = Modifier.weight(1f),
                onClick = {
                    onDismiss()
                    navController.navigate("recycle_bin")
                }
            )
            MenuActionCard(
                icon = Icons.Outlined.Settings,
                label = "Settings",
                modifier = Modifier.weight(1f),
                onClick = {
                    onDismiss()
                    navController.navigate("settings")
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Studio Link
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onDismiss() }
                .padding(horizontal = 24.dp)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = "Go to Studio",
                color = AccentOrange,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = AccentOrange,
                modifier = Modifier.size(20.dp).padding(start = 4.dp)
            )
        }
        
        Spacer(modifier = Modifier.navigationBarsPadding())
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun MenuActionCard(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit = {}) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF2A2A2A))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun SheetQuickAction(label: String, icon: ImageVector, onClick: () -> Unit = {}) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(70.dp).clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(QuickActionBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp
        )
    }
}
