package com.localcloud.photosclient.presentation.albums

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.localcloud.photosclient.ui.theme.*

@Composable
fun AlbumsScreen(
    viewModel: AlbumsViewModel = hiltViewModel(),
    onAlbumClick: (Album) -> Unit = {}
) {
    val albums by viewModel.albums.collectAsState()

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().background(PureBlack),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        items(albums) { album ->
            AlbumCard(album = album, onClick = { onAlbumClick(album) })
        }
    }
}

@Composable
fun AlbumCard(album: Album, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurfaceVariant)
        ) {
            AsyncImage(
                model = album.coverUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // Bottom Gradient for text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                            startY = 300f
                        )
                    )
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = album.name,
            color = White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            text = "${album.count}",
            color = InactiveGrey,
            fontSize = 12.sp
        )
    }
}
