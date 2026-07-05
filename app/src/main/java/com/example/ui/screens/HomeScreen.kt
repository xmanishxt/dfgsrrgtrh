package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.example.data.local.TrackEntity
import com.example.playback.PlaybackState
import com.example.ui.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // View Model state collection
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()

    val downloadedTracks by viewModel.downloadedTracks.collectAsStateWithLifecycle()
    val favoriteTracks by viewModel.favoriteTracks.collectAsStateWithLifecycle()

    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()

    val lyrics by viewModel.currentLyrics.collectAsStateWithLifecycle()
    val isLyricsLoading by viewModel.isLyricsLoading.collectAsStateWithLifecycle()
    val progresses by viewModel.downloadProgress.collectAsStateWithLifecycle()

    // Tabs configuration
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Explore", "Downloads", "Favorites")

    // Full screen player overlay state
    var isPlayerExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF090616),
                        Color(0xFF03010A)
                    )
                )
            )
    ) {
        // Ambient soft color blobs in background for luxurious music vibe
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.18f)
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF9D4EDD), Color.Transparent),
                    radius = 350.dp.toPx()
                ),
                center = androidx.compose.ui.geometry.Offset(size.width * 0.85f, size.height * 0.1f),
                radius = 350.dp.toPx()
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFF85A1), Color.Transparent),
                    radius = 300.dp.toPx()
                ),
                center = androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.65f),
                radius = 300.dp.toPx()
            )
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent, // Let background canvas show through
            topBar = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.secondary
                                        )
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = "Logo",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "MUSE PLAYER",
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Search • Stream • Keep Offline",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.4.sp
                            )
                        }
                    }

                    // Sliding Premium Pill Tab Bar
                    CustomSlidingTabBar(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        tabs = tabs
                    )
                }
            },
            bottomBar = {
                // Persistent bottom mini-player
                Column {
                    if (currentTrack != null) {
                        MiniPlayer(
                            track = currentTrack!!,
                            playbackState = playbackState,
                            progress = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                            onTogglePlay = { viewModel.togglePlay() },
                            onNext = { viewModel.next() },
                            onExpand = { isPlayerExpanded = true }
                        )
                    }
                    Spacer(modifier = Modifier.navigationBarsPadding())
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (selectedTab) {
                    0 -> ExploreTab(
                        query = searchQuery,
                        results = searchResults,
                        isSearching = isSearching,
                        currentTrack = currentTrack,
                        progresses = progresses,
                        onQueryChange = { viewModel.updateSearchQuery(it) },
                        onSearch = { viewModel.search() },
                        onPlay = { track -> viewModel.playTrack(track, searchResults) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onDownload = { viewModel.downloadTrack(it) },
                        onDelete = { viewModel.deleteDownload(it) }
                    )
                    1 -> SavedTab(
                        title = "Downloads",
                        emptyMessage = "No downloaded tracks yet. Explore and download songs to play offline!",
                        tracks = downloadedTracks,
                        currentTrack = currentTrack,
                        progresses = progresses,
                        onPlay = { track -> viewModel.playTrack(track, downloadedTracks) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onDelete = { viewModel.deleteDownload(it) }
                    )
                    2 -> SavedTab(
                        title = "Favorites",
                        emptyMessage = "Your favorites list is empty. Heart songs to add them here!",
                        tracks = favoriteTracks,
                        currentTrack = currentTrack,
                        progresses = progresses,
                        onPlay = { track -> viewModel.playTrack(track, favoriteTracks) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onDelete = { viewModel.deleteDownload(it) }
                    )
                }
            }
        }

        // Now Playing Expandable Overlay
        AnimatedVisibility(
            visible = isPlayerExpanded,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(400)
            )
        ) {
            currentTrack?.let { track ->
                NowPlayingSheet(
                    track = track,
                    playbackState = playbackState,
                    position = currentPosition,
                    duration = duration,
                    lyrics = lyrics,
                    isLyricsLoading = isLyricsLoading,
                    downloadProgress = progresses[track.id],
                    onCollapse = { isPlayerExpanded = false },
                    onTogglePlay = { viewModel.togglePlay() },
                    onNext = { viewModel.next() },
                    onPrev = { viewModel.prev() },
                    onSeek = { viewModel.seekTo(it) },
                    onToggleFavorite = { viewModel.toggleFavorite(track) }
                )
            }
        }
    }
}

// =============================================================================
// PREMIUM SLIDING PILL TAB BAR
// =============================================================================
@Composable
fun CustomSlidingTabBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    tabs: List<String>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .background(Color(0xFF141029).copy(alpha = 0.5f), RoundedCornerShape(24.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, title ->
            val isSelected = selectedTab == index
            val animatedBgAlpha by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "tab_pill_bg"
            )
            val animatedTextColor by animateColorAsState(
                targetValue = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                animationSpec = tween(200),
                label = "tab_pill_text"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = animatedBgAlpha)
                        else Color.Transparent
                    )
                    .clickable { onTabSelected(index) }
                    .minimumInteractiveComponentSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    fontSize = 13.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = animatedTextColor
                )
            }
        }
    }
}

// =============================================================================
// EXPLORE TAB
// =============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreTab(
    query: String,
    results: List<TrackEntity>,
    isSearching: Boolean,
    currentTrack: TrackEntity?,
    progresses: Map<String, Float>,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onPlay: (TrackEntity) -> Unit,
    onToggleFavorite: (TrackEntity) -> Unit,
    onDownload: (TrackEntity) -> Unit,
    onDelete: (TrackEntity) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp)
    ) {
        // Search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search songs, artists, links...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .testTag("search_input"),
                shape = RoundedCornerShape(27.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1E193C).copy(alpha = 0.65f),
                    unfocusedContainerColor = Color(0xFF1E193C).copy(alpha = 0.35f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.width(10.dp))
            Button(
                onClick = onSearch,
                modifier = Modifier
                    .height(54.dp)
                    .testTag("search_button"),
                shape = RoundedCornerShape(27.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Find", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        "Scraping results cleanly...",
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        } else if (results.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    ArtisticEmptyStateOrb(
                        color = MaterialTheme.colorScheme.primary,
                        icon = Icons.Default.MusicNote
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Your stage is empty",
                        fontWeight = FontWeight.Black,
                        fontSize = 19.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Type any song name, artist, or YouTube link to find and stream it instantly.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.widthIn(max = 280.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 84.dp)
            ) {
                items(results, key = { it.id }) { track ->
                    TrackItemRow(
                        track = track,
                        isPlaying = currentTrack?.id == track.id,
                        downloadProgress = progresses[track.id],
                        onClick = { onPlay(track) },
                        onToggleFavorite = { onToggleFavorite(track) },
                        onDownload = { onDownload(track) },
                        onDelete = { onDelete(track) }
                    )
                }
            }
        }
    }
}

// =============================================================================
// SAVED TAB (DOWNLOADS & FAVORITES)
// =============================================================================
@Composable
fun SavedTab(
    title: String,
    emptyMessage: String,
    tracks: List<TrackEntity>,
    currentTrack: TrackEntity?,
    progresses: Map<String, Float>,
    onPlay: (TrackEntity) -> Unit,
    onToggleFavorite: (TrackEntity) -> Unit,
    onDelete: (TrackEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    ArtisticEmptyStateOrb(
                        color = if (title == "Downloads") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                        icon = if (title == "Downloads") Icons.Default.Download else Icons.Default.Favorite
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "No tracks in $title",
                        fontWeight = FontWeight.Black,
                        fontSize = 19.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = emptyMessage,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.widthIn(max = 280.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 12.dp, bottom = 84.dp)
            ) {
                items(tracks, key = { it.id }) { track ->
                    TrackItemRow(
                        track = track,
                        isPlaying = currentTrack?.id == track.id,
                        downloadProgress = progresses[track.id],
                        onClick = { onPlay(track) },
                        onToggleFavorite = { onToggleFavorite(track) },
                        onDownload = {}, // Downloading isn't needed if already saved
                        onDelete = { onDelete(track) }
                    )
                }
            }
        }
    }
}

// =============================================================================
// SINGLE TRACK LIST ITEM ROW
// =============================================================================
@Composable
fun TrackItemRow(
    track: TrackEntity,
    isPlaying: Boolean,
    downloadProgress: Float?,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val animatedBgAlpha by animateFloatAsState(
        targetValue = if (isPlaying) 0.16f else 0.4f,
        label = "row_bg_alpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable(onClick = onClick)
            .testTag("track_item_card_${track.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) {
                MaterialTheme.colorScheme.primary.copy(alpha = animatedBgAlpha)
            } else {
                Color(0xFF141029).copy(alpha = animatedBgAlpha)
            }
        ),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = if (isPlaying) {
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                    )
                } else {
                    listOf(
                        Color(0xFF26204E).copy(alpha = 0.4f),
                        Color(0xFF1E183B).copy(alpha = 0.1f)
                    )
                }
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album art with animation overlay
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1B1736))
            ) {
                Image(
                    painter = rememberAsyncImagePainter(model = track.thumbUrl),
                    contentDescription = "Thumbnail",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedAudioVisualizer(
                            modifier = Modifier.size(24.dp, 16.dp),
                            color = MaterialTheme.colorScheme.primary,
                            barCount = 3
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Title & Artist
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        color = if (isPlaying) MaterialTheme.colorScheme.primary else Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isPlaying) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track.artist,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (track.localFilePath != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF00FF7F).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Offline Available",
                                    tint = Color(0xFF00FF7F),
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "OFFLINE",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF00FF7F)
                                )
                            }
                        }
                    }
                }
            }

            // Duration
            Text(
                text = track.duration,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Action Buttons Row
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Favorite Heart Button
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("favorite_button_${track.id}")
                ) {
                    Icon(
                        imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (track.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Download Button States
                if (downloadProgress != null) {
                    // Downloading State
                    Box(
                        modifier = Modifier.size(38.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = downloadProgress,
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else if (track.localFilePath != null) {
                    // Downloaded state (allow deletion)
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("delete_download_button_${track.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Download",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    // Download idle state (allow download)
                    IconButton(
                        onClick = onDownload,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("download_button_${track.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// BOTTOM MINI PLAYER PANEL
// =============================================================================
@Composable
fun MiniPlayer(
    track: TrackEntity,
    playbackState: PlaybackState,
    progress: Float,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onExpand: () -> Unit
) {
    // Mini-player vinyl rotation
    val infiniteTransition = rememberInfiniteTransition()
    val miniAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "mini_vinyl_spin"
    )
    val miniRotation = if (playbackState is PlaybackState.Playing) Modifier.rotate(miniAngle) else Modifier

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .shadow(16.dp, RoundedCornerShape(24.dp))
            .clickable(onClick = onExpand)
            .testTag("mini_player"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141029).copy(alpha = 0.94f)),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    Color.Transparent
                )
            )
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Artwork with Vinyl rotation effect
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .then(miniRotation)
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(model = track.thumbUrl),
                        contentDescription = "Artwork",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    // Mini Vinyl Center hole
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFF141029), CircleShape)
                            .align(Alignment.Center)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Title & Artist info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Controls
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (playbackState is PlaybackState.Loading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .size(20.dp)
                        )
                    } else {
                        IconButton(
                            onClick = onTogglePlay,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("play_pause_button")
                        ) {
                            Icon(
                                imageVector = if (playbackState is PlaybackState.Playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onNext,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("next_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Beautiful mini track glowing progress line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        )
                )
            }
        }
    }
}

// =============================================================================
// FULL NOW PLAYING OVERLAY SHEET
// =============================================================================
@Composable
fun NowPlayingSheet(
    track: TrackEntity,
    playbackState: PlaybackState,
    position: Long,
    duration: Long,
    lyrics: String?,
    isLyricsLoading: Boolean,
    downloadProgress: Float?,
    onCollapse: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFavorite: () -> Unit
) {
    val context = LocalContext.current
    var showLyricsTab by remember { mutableStateOf(false) }

    // Big rotating disk animation when playing
    val infiniteTransition = rememberInfiniteTransition()
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(24000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vinyl_disk_rotation"
    )

    val diskRotationModifier = if (playbackState is PlaybackState.Playing) {
        Modifier.rotate(angle)
    } else Modifier

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF141029),
                        Color(0xFF03010A)
                    )
                )
            )
            .statusBarsPadding()
            .testTag("player_sheet")
    ) {
        // Decorative glowing background circles for a beautiful ambient visual theme
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.12f)
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF9D4EDD), Color.Transparent),
                    radius = 320.dp.toPx()
                ),
                center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height * 0.3f),
                radius = 320.dp.toPx()
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            // Header: Dropdown & Title & Heart
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "Collapse Player",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = "NOW PLAYING",
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    letterSpacing = 2.5.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (track.isFavorite) MaterialTheme.colorScheme.tertiary else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Body choice: Album Art vs Lyrics
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (showLyricsTab) {
                    // Modern scrolling Karaoke Lyric view with premium styles
                    Card(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1736).copy(alpha = 0.5f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "SING ALONG",
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                                letterSpacing = 2.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            if (isLyricsLoading) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                }
                            } else {
                                val lyricLines = lyrics?.split("\n") ?: listOf("No lyrics found for this track.")
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    contentPadding = PaddingValues(bottom = 16.dp)
                                ) {
                                    items(lyricLines) { line ->
                                        Text(
                                            text = line,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold,
                                            lineHeight = 26.sp,
                                            color = Color.White,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Rotating Disc Art with concentric vinyl groove drawings
                    Box(
                        modifier = Modifier
                            .size(280.dp)
                            .shadow(32.dp, CircleShape)
                            .background(Color(0xFF0F0E14), CircleShape)
                            .padding(4.dp)
                            .then(diskRotationModifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(model = track.thumbUrl),
                            contentDescription = "Rotating Album Art",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        
                        // Drawn Vinyl concentric LP grooves for extreme physical detail
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val centerOffset = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
                            // Draw multiple subtle concentric lines
                            for (r in 35..135 step 15) {
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.08f),
                                    radius = r.dp.toPx(),
                                    center = centerOffset,
                                    style = Stroke(width = 1f)
                                )
                            }
                        }

                        // Shiny physical center label & spindle
                        Box(
                            modifier = Modifier
                                .size(62.dp)
                                .background(Color(0xFF0C091A), CircleShape)
                                .shadow(6.dp, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Lyrics Toggle button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Card(
                    modifier = Modifier.clickable { showLyricsTab = !showLyricsTab },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (showLyricsTab) MaterialTheme.colorScheme.primary else Color(0xFF1B1736)
                    ),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (showLyricsTab) Icons.Default.Image else Icons.Default.MusicNote,
                            contentDescription = "Toggle Visual",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (showLyricsTab) "Show Album Art" else "Show Lyrics",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Metadata: Title & Artist
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = track.title,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = track.artist,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Progress Slider
            var sliderProgress by remember { mutableStateOf<Float?>(null) }
            val resolvedPosition = sliderProgress?.let { (it * duration).toLong() } ?: position
            val progressPercentage = if (duration > 0) resolvedPosition.toFloat() / duration else 0f

            Column {
                Slider(
                    value = progressPercentage,
                    onValueChange = { sliderProgress = it },
                    onValueChangeFinished = {
                        sliderProgress?.let {
                            onSeek((it * duration).toLong())
                        }
                        sliderProgress = null
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatMillis(resolvedPosition),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = formatMillis(duration),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Bottom Core Controls: Play, Pause, Next, Prev
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPrev,
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("big_prev_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(76.dp)
                ) {
                    if (playbackState is PlaybackState.Loading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    } else {
                        FloatingActionButton(
                            onClick = onTogglePlay,
                            shape = CircleShape,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("big_play_pause_button")
                        ) {
                            Icon(
                                imageVector = if (playbackState is PlaybackState.Playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                modifier = Modifier.size(42.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onNext,
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("big_next_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

// =============================================================================
// ANIMATED BOUNCING AUDIO VISUALIZER EFFECT
// =============================================================================
@Composable
fun AnimatedAudioVisualizer(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    barCount: Int = 3
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val infiniteTransition = rememberInfiniteTransition()
        val heights = List(barCount) { index ->
            infiniteTransition.animateFloat(
                initialValue = 0.15f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 400 + (index * 140),
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "audio_bar_spin_$index"
            )
        }

        heights.forEach { heightVal ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(heightVal.value)
                    .background(color, RoundedCornerShape(1.5.dp))
            )
        }
    }
}

// =============================================================================
// ARTISTIC GLOWING ORBITAL EMPTY STATE ILLUSTRATION
// =============================================================================
@Composable
fun ArtisticEmptyStateOrb(
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulsing"
    )
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(22000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_spin"
    )

    Box(
        modifier = modifier.size(160.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .rotate(rotationAngle)
        ) {
            val centerOffset = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
            val baseRadius = 54.dp.toPx() * pulseScale

            // Pulsing glowing background cloud
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.35f), Color.Transparent),
                    radius = baseRadius * 1.5f
                ),
                radius = baseRadius * 1.5f,
                center = centerOffset
            )

            // orbital groove track
            drawCircle(
                color = color.copy(alpha = 0.25f),
                radius = baseRadius * 1.1f,
                center = centerOffset,
                style = Stroke(width = 1.5.dp.toPx())
            )
            
            // dotted outer orbit ring
            drawCircle(
                color = color.copy(alpha = 0.15f),
                radius = baseRadius * 1.4f,
                center = centerOffset,
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 15f), 0f)
                )
            )

            // orbit particles
            drawCircle(
                color = color,
                radius = 4.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(
                    centerOffset.x + (baseRadius * 1.1f) * kotlin.math.cos(0f),
                    centerOffset.y + (baseRadius * 1.1f) * kotlin.math.sin(0f)
                )
            )
            drawCircle(
                color = tertiaryColor,
                radius = 3.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(
                    centerOffset.x + (baseRadius * 1.4f) * kotlin.math.cos(140f * (3.14159f / 180f)),
                    centerOffset.y + (baseRadius * 1.4f) * kotlin.math.sin(140f * (3.14159f / 180f))
                )
            )
        }

        // Center primary symbol
        Box(
            modifier = Modifier
                .size(54.dp)
                .background(Color(0xFF141029), CircleShape)
                .shadow(4.dp, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

// Format duration millis to MM:SS string
fun formatMillis(millis: Long): String {
    if (millis <= 0) return "0:00"
    val minutes = (millis / 1000) / 60
    val seconds = (millis / 1000) % 60
    return String.format("%d:%02d", minutes, seconds)
}
