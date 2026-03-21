package com.localcloud.photosclient.presentation.components

import android.content.ContentUris
import android.provider.MediaStore
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.localcloud.photosclient.data.LocalMedia
import com.localcloud.photosclient.data.LocalAvailability
import java.io.File

@Composable
fun DenseMediaItem(
    media: LocalMedia, 
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else if (isSelected) 0.88f else 1f,
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
            .clip(RoundedCornerShape(if (isSelected) 12.dp else 0.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(isSelectionMode) {
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
                    onLongPress = { if (!isSelectionMode) onLongPress() }
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
            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(model)
                .memoryCacheKey(media.id.toString())
                .build(),
            contentDescription = "Photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            alpha = if (isCloudOnly) 0.6f else 1f
        )
        
        // Selection Overlay
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.2f))
            )
        }

        // Selection Checkbox (Animated)
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
        ) {
            Icon(
                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        
        // Video Badge
        if (media.mediaType.startsWith("video", ignoreCase = true)) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .size(20.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayCircle,
                    contentDescription = "Video",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            if (media.duration != null) {
                val seconds = (media.duration / 1000) % 60
                val minutes = (media.duration / 1000) / 60
                val durationText = String.format(java.util.Locale.getDefault(), "%d:%02d", minutes, seconds)
                Text(
                    text = durationText,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                )
            }
        }
        
        // Cloud Status Overlay
        if (isCloudOnly) {
            Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = "Cloud Only",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(16.dp)
            )
        }

        // Sync Status Indicator Overlay
        val statusIcon = when (media.uploadStatus) {
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
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(4.dp)
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = media.uploadStatus,
                    tint = statusColor,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
