package com.localcloud.photosclient.presentation.components

import android.content.ContentUris
import android.provider.MediaStore
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.localcloud.photosclient.data.LocalMedia
import com.localcloud.photosclient.data.LocalAvailability
import java.io.File

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DenseMediaItem(
    media: LocalMedia, 
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else if (isSelected) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "scale"
    )

    val aspectRatio = if (media.width > 0 && media.height > 0) {
        media.width.toFloat() / media.height
    } else {
        1f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .scale(scale)
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
        val isCloudOnly = media.localAvailability == LocalAvailability.CLOUD_ONLY

        val model = if (isCloudOnly) {
            media.remoteId
        } else {
            if (media.mediaType.startsWith("video")) {
                ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, media.id)
            } else {
                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, media.id)
            }
        }

        AsyncImage(
            model = model,
            contentDescription = "Photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            alpha = if (isCloudOnly) 0.5f else 1f
        )
        
        if (isSelected) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(8.dp).size(24.dp).align(Alignment.TopStart)
            )
        }
        
        if (isCloudOnly) {
            Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = "Cloud Only",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(16.dp)
                    .background(Color.Black.copy(alpha = 0.2f), CircleShape)
                    .padding(2.dp)
            )
        }
        
        if (media.mediaType.startsWith("video", ignoreCase = true) && media.duration != null) {
            val seconds = (media.duration / 1000) % 60
            val minutes = (media.duration / 1000) / 60
            val durationText = String.format(java.util.Locale.getDefault(), "%d:%02d", minutes, seconds)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = durationText,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(24.dp)
                    .background(Color.Black.copy(alpha = 0.3f), CircleShape)
            )
        }
        
        // Sync Status Indicator Overlay
        val statusIcon = when (media.uploadStatus) {
            "SUCCESS" -> null
            "UPLOADING" -> Icons.Default.CloudUpload
            "FAILED" -> Icons.Default.Error
            else -> null
        }
        val statusColor = when (media.uploadStatus) {
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
                    modifier = Modifier.size(12.dp)
                )
            }
        } else if (media.uploadStatus == "PENDING") {
             Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFC107))
            )
        }
    }
}
