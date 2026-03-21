package com.localcloud.photosclient.presentation.albums

import android.content.ContentUris
import android.provider.MediaStore
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.localcloud.photosclient.ui.theme.PureBlack
import com.localcloud.photosclient.ui.theme.SamsungBlue
import com.localcloud.photosclient.ui.theme.White

@Composable
fun AlbumsScreen(viewModel: AlbumsViewModel = hiltViewModel()) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    
    Log.d("ALBUM_DEBUG", "Albums count: ${albums.size}")
    
    Column(modifier = Modifier.fillMaxSize().background(PureBlack)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Special Collections Row
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SpecialCollectionCard("Recents", Icons.Default.History, Modifier.weight(1f))
                    SpecialCollectionCard("Favorites", Icons.Default.FavoriteBorder, Modifier.weight(1f))
                    SpecialCollectionCard("Videos", Icons.Default.PlayCircleOutline, Modifier.weight(1f))
                }
            }

            items(albums) { album ->
                AlbumCard(
                    title = album.name,
                    count = album.count,
                    coverPath = album.coverUri,
                    onClick = { /* Navigate to detail */ }
                )
            }
        }
    }
}

@Composable
fun SpecialCollectionCard(title: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.aspectRatio(1.1f),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = SamsungBlue, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, color = White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun AlbumCard(title: String, count: Int, coverPath: String?, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1A1A1A))
        ) {
            if (coverPath != null) {
                // Simplified URI fix for grid: we need the ID but we only have path in Album for now.
                // In production, we'd add the ID to the Album model. 
                // For now, using path since Coil handles file:// well, but following the content:// pattern as best as possible.
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(if (coverPath.startsWith("content://")) coverPath else "file://$coverPath")
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(title, color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
        Text("$count items", color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
    }
}
