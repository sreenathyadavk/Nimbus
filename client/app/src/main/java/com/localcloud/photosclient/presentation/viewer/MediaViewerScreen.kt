package com.localcloud.photosclient.presentation.viewer

import android.app.Activity
import android.content.ContentUris
import android.provider.MediaStore
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.localcloud.photosclient.data.LocalMedia
import com.localcloud.photosclient.ui.MainViewModel
import com.localcloud.photosclient.ui.theme.PureBlack
import com.localcloud.photosclient.ui.theme.White
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaViewerScreen(
    initialIndex: Int,
    viewModel: MainViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val mediaList by viewModel.activeViewerList.collectAsStateWithLifecycle()
    
    // ROOT CAUSE FIX: Wait for data before rendering or initializing pager
    if (mediaList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = White)
        }
        return
    }

    // Initialize state only when list is ready
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, mediaList.lastIndex),
        pageCount = { mediaList.size }
    )
    
    var barsVisible by remember { mutableStateOf(true) }
    var isPagingEnabled by remember { mutableStateOf(true) }
    
    val context = LocalContext.current
    val window = (context as? Activity)?.window
    val insetsController = remember { window?.let { WindowCompat.getInsetsController(it, it.decorView) } }

    LaunchedEffect(barsVisible) {
        if (barsVisible) {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController?.hide(WindowInsetsCompat.Type.systemBars())
            insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    Scaffold(containerColor = Color.Black) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondBoundsPageCount = 1,
                userScrollEnabled = isPagingEnabled
            ) { page ->
                if (page < mediaList.size) {
                    SamsungGestureViewer(
                        media = mediaList[page],
                        isCurrentPage = page == pagerState.currentPage,
                        onTap = { barsVisible = !barsVisible },
                        onZoomChanged = { isZoomed -> isPagingEnabled = !isZoomed },
                        onDismiss = onNavigateBack
                    )
                }
            }

            // Top Bar
            AnimatedVisibility(
                visible = barsVisible,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it }
            ) {
                ViewerTopBar(
                    title = if (pagerState.currentPage < mediaList.size) File(mediaList[pagerState.currentPage].path).name else "",
                    onBack = onNavigateBack
                )
            }

            // Bottom Bar
            AnimatedVisibility(
                visible = barsVisible,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                ViewerBottomBar()
            }
        }
    }
}

@Composable
fun SamsungGestureViewer(
    media: LocalMedia,
    isCurrentPage: Boolean,
    onTap: () -> Unit,
    onZoomChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var dragY by remember { mutableFloatStateOf(0f) }
    
    val isZoomed = remember(scale) { scale > 1.05f }
    LaunchedEffect(isZoomed) { onZoomChanged(isZoomed) }

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            scale = 1f
            offset = Offset.Zero
            dragY = 0f
        }
    }

    val animScale by animateFloatAsState(scale, spring(dampingRatio = 0.8f, stiffness = 400f))
    val animOffset by animateOffsetAsState(offset, spring(dampingRatio = 0.8f, stiffness = 400f))
    val animDragY by animateFloatAsState(dragY, spring(dampingRatio = 0.8f, stiffness = 400f))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale > 1f) offset += pan else offset = Offset.Zero
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1.1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    }
                )
            }
            .pointerInput(isZoomed) {
                if (!isZoomed) {
                    detectVerticalDragGestures(
                        onDragEnd = { if (dragY > 200f) onDismiss() else dragY = 0f },
                        onVerticalDrag = { _, delta -> dragY = (dragY + delta).coerceAtLeast(0f) }
                    )
                }
            }
            .graphicsLayer {
                val progress = (dragY / 400f).coerceIn(0f, 1f)
                translationX = animOffset.x
                translationY = animOffset.y + animDragY
                scaleX = animScale * (1f - progress * 0.3f)
                scaleY = animScale * (1f - progress * 0.3f)
                alpha = 1f - progress * 0.6f
            },
        contentAlignment = Alignment.Center
    ) {
        val uri = remember(media.id, media.mediaType) {
            if (media.mediaType.startsWith("video")) {
                ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, media.id)
            } else {
                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, media.id)
            }
        }

        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(uri)
                .crossfade(true)
                .size(coil.size.Size.ORIGINAL)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
            placeholder = ColorPainter(Color(0xFF111111)),
            error = ColorPainter(Color(0xFF222222))
        )
    }
}

@Composable
fun ViewerTopBar(title: String, onBack: () -> Unit) {
    Surface(color = Color.Black.copy(alpha = 0.4f), contentColor = White) {
        Row(
            modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun ViewerBottomBar() {
    Surface(color = Color.Black.copy(alpha = 0.6f), contentColor = White) {
        Row(
            modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {}) { Icon(Icons.Default.Share, "Share"); Text("Share", fontSize = 10.sp) }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {}) { Icon(Icons.Default.Edit, "Edit"); Text("Edit", fontSize = 10.sp) }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {}) { Icon(Icons.Default.Delete, "Delete"); Text("Delete", fontSize = 10.sp) }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {}) { Icon(Icons.Default.MoreHoriz, "More"); Text("More", fontSize = 10.sp) }
        }
    }
}

@Composable
fun animateOffsetAsState(
    targetValue: Offset,
    animationSpec: AnimationSpec<Offset> = spring()
): State<Offset> {
    val animX = animateFloatAsState(targetValue.x, animationSpec = spring())
    val animY = animateFloatAsState(targetValue.y, animationSpec = spring())
    return remember { derivedStateOf { Offset(animX.value, animY.value) } }
}
