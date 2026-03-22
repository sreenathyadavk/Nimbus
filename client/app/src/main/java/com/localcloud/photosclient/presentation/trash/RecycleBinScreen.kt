package com.localcloud.photosclient.presentation.trash

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.localcloud.photosclient.data.LocalMedia
import com.localcloud.photosclient.ui.MainViewModel
import com.localcloud.photosclient.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(
    viewModel: MainViewModel,
    navController: NavController
) {
    val deletedItems by viewModel.deletedMedia.collectAsStateWithLifecycle()
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showEmptyDialog by remember { mutableStateOf(false) }
    
    var viewingItem by remember { mutableStateOf<LocalMedia?>(null) }

    val intentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.refreshMedia()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.pendingDeleteIntent.collect { intentSender ->
            intentSenderLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
        }
    }

    // Days remaining badge helper
    fun daysRemaining(deletedAt: Long?): Int {
        if (deletedAt == null) return 30
        val daysElapsed = ((System.currentTimeMillis() - deletedAt) / (1000 * 60 * 60 * 24)).toInt()
        return (30 - daysElapsed).coerceAtLeast(0)
    }

    // Permanent delete
    fun permanentlyDeleteItems(items: List<LocalMedia>) {
        if (items.isEmpty()) return
        viewModel.permanentlyDeleteBatch(items)
        isSelectionMode = false
        selectedIds = emptySet()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (isSelectionMode) {
                    // Selection mode top bar
                    Surface(
                        color = PureBlack.copy(alpha = 0.9f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { 
                                isSelectionMode = false
                                selectedIds = emptySet()
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                            }
                            Text(
                                "${selectedIds.size} selected",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.weight(1f))
                            // Restore selected
                            TextButton(onClick = {
                                selectedIds.forEach { id ->
                                    deletedItems.find { it.id == id }?.let { viewModel.restoreMedia(it) }
                                }
                                isSelectionMode = false
                                selectedIds = emptySet()
                            }) {
                                Text("Restore", color = AccentOrange)
                            }
                            // Delete forever selected
                            TextButton(onClick = {
                                val toDelete = deletedItems.filter { it.id in selectedIds }
                                permanentlyDeleteItems(toDelete)
                            }) {
                                Text("Delete", color = Color.Red)
                            }
                        }
                    }
                } else {
                    // Normal top bar
                    TopAppBar(
                        title = {
                            Text(
                                "Recycle bin",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.Default.ArrowBack, tint = Color.White, contentDescription = "Back")
                            }
                        },
                        actions = {
                            if (deletedItems.isNotEmpty()) {
                                // Restore all
                                TextButton(onClick = {
                                    deletedItems.forEach { viewModel.restoreMedia(it) }
                                }) {
                                    Text("Restore all", color = AccentOrange)
                                }
                                // Empty bin
                                TextButton(onClick = { showEmptyDialog = true }) {
                                    Text("Empty", color = Color.Red)
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = PureBlack)
                    )
                }
            },
            containerColor = PureBlack
        ) { padding ->
            
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (deletedItems.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = Color(0xFF444444),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Recycle bin is empty",
                            color = Color(0xFF888888),
                            fontSize = 16.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Items are deleted permanently after 30 days",
                            color = Color(0xFF555555),
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        items(deletedItems, key = { it.id }) { item ->
                            val isSelected = item.id in selectedIds
                            val days = daysRemaining(item.deletedAt)
                            
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .background(CardDark)
                                    .combinedClickable(
                                        onClick = {
                                            if (isSelectionMode) {
                                                selectedIds = if (isSelected)
                                                    selectedIds - item.id
                                                else
                                                    selectedIds + item.id
                                                if (selectedIds.isEmpty()) isSelectionMode = false
                                            } else {
                                                viewingItem = item
                                            }
                                        },
                                        onLongClick = {
                                            isSelectionMode = true
                                            selectedIds = selectedIds + item.id
                                        }
                                    )
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(Uri.parse(item.localUri))
                                        .crossfade(true)
                                        .build(),
                                    error = ColorPainter(Color(0xFF2A2A2A)),
                                    placeholder = ColorPainter(Color(0xFF1A1A1A)),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                
                                // Days remaining badge - bottom left
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xCC000000))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "${days}d",
                                        color = if (days <= 3) Color.Red else Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Selection checkmark
                                if (isSelectionMode) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(4.dp)
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) AccentOrange
                                                else Color(0x88000000)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                    
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .border(2.dp, AccentOrange)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Empty bin confirmation dialog
            if (showEmptyDialog) {
                AlertDialog(
                    onDismissRequest = { showEmptyDialog = false },
                    containerColor = Color(0xFF1A1A1A),
                    title = { Text("Empty recycle bin?", color = Color.White) },
                    text = { Text("All ${deletedItems.size} items will be permanently deleted. This cannot be undone.", color = Color(0xFF888888)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showEmptyDialog = false
                            permanentlyDeleteItems(deletedItems.toList())
                        }) {
                            Text("Delete all", color = Color.Red)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEmptyDialog = false }) {
                            Text("Cancel", color = Color.White)
                        }
                    }
                )
            }
        }

        // Expanded Viewer Overlay
        viewingItem?.let { item ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable(enabled = false) { }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(Uri.parse(item.localUri))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                
                // Top bar for viewer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewingItem = null }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Row {
                        TextButton(onClick = {
                            viewModel.restoreMedia(item)
                            viewingItem = null
                        }) {
                            Text("Restore", color = AccentOrange)
                        }
                        TextButton(onClick = {
                            permanentlyDeleteItems(listOf(item))
                            viewingItem = null
                        }) {
                            Text("Delete forever", color = Color.Red)
                        }
                    }
                }
            }
        }
    }
}
