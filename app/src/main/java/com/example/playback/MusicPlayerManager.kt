package com.example.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.example.data.TrackRepository
import com.example.data.local.TrackEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class PlaybackState {
    object Idle : PlaybackState()
    object Loading : PlaybackState()
    object Playing : PlaybackState()
    object Paused : PlaybackState()
    data class Error(val message: String) : PlaybackState()
}

class MusicPlayerManager(
    private val context: Context,
    private val repository: TrackRepository
) {
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Player states
    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState = _playbackState.asStateFlow()

    private val _currentTrack = MutableStateFlow<TrackEntity?>(null)
    val currentTrack = _currentTrack.asStateFlow()

    private val _currentPosition = MutableStateFlow<Long>(0L)
    val currentPosition = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow<Long>(0L)
    val duration = _duration.asStateFlow()

    // Playlist Queue
    private var queue = listOf<TrackEntity>()
    private var currentIndex = -1

    private var positionJob: Job? = null

    init {
        initializePlayer()
    }

    private fun initializePlayer() {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setOnPreparedListener { mp ->
                _duration.value = mp.duration.toLong()
                _playbackState.value = PlaybackState.Playing
                mp.start()
                startPositionPolling()
            }
            setOnCompletionListener {
                handlePlaybackCompletion()
            }
            setOnErrorListener { _, what, extra ->
                Log.e("MusicPlayer", "MediaPlayer error: what=$what extra=$extra")
                _playbackState.value = PlaybackState.Error("Failed to play track. Retrying...")
                // Try playing next on fatal errors
                scope.launch {
                    delay(2000)
                    next()
                }
                true
            }
        }
    }

    fun playTrack(track: TrackEntity, playlist: List<TrackEntity>) {
        queue = playlist
        currentIndex = playlist.indexOfFirst { it.id == track.id }
        if (currentIndex == -1) {
            queue = playlist + track
            currentIndex = queue.lastIndex
        }

        _currentTrack.value = track
        _currentPosition.value = 0L
        _duration.value = 0L
        _playbackState.value = PlaybackState.Loading

        stopPositionPolling()

        scope.launch {
            try {
                var dataSource = if (track.localFilePath != null) {
                    track.localFilePath
                } else {
                    null
                }

                // Check if local file actually exists
                if (dataSource != null) {
                    val file = java.io.File(dataSource)
                    if (!file.exists()) {
                        dataSource = null
                    }
                }

                // Fall back to streaming if local file is missing or null
                if (dataSource == null) {
                    dataSource = repository.getStreamUrl(track.id)
                }

                if (dataSource == null) {
                    _playbackState.value = PlaybackState.Error("Could not resolve stream URL")
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    try {
                        initializePlayer()
                        if (dataSource.startsWith("/") || dataSource.startsWith("file:")) {
                            mediaPlayer?.setDataSource(dataSource)
                        } else {
                            val headers = mapOf(
                                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                            )
                            mediaPlayer?.setDataSource(context, android.net.Uri.parse(dataSource), headers)
                        }
                        mediaPlayer?.prepareAsync()
                    } catch (e: Exception) {
                        Log.e("MusicPlayer", "Error loading data source: ", e)
                        _playbackState.value = PlaybackState.Error("Error preparing audio")
                    }
                }
            } catch (e: Exception) {
                Log.e("MusicPlayer", "Failed to play track: ", e)
                _playbackState.value = PlaybackState.Error("Network/IO error")
            }
        }
    }

    fun togglePlay() {
        val player = mediaPlayer ?: return
        when (_playbackState.value) {
            is PlaybackState.Playing -> {
                player.pause()
                _playbackState.value = PlaybackState.Paused
                stopPositionPolling()
            }
            is PlaybackState.Paused -> {
                player.start()
                _playbackState.value = PlaybackState.Playing
                startPositionPolling()
            }
            is PlaybackState.Idle -> {
                _currentTrack.value?.let { playTrack(it, queue) }
            }
            is PlaybackState.Error -> {
                _currentTrack.value?.let { playTrack(it, queue) }
            }
            else -> {}
        }
    }

    fun next() {
        if (queue.isNotEmpty() && currentIndex < queue.size - 1) {
            currentIndex++
            playTrack(queue[currentIndex], queue)
        } else if (queue.isNotEmpty()) {
            // Loop back to start
            currentIndex = 0
            playTrack(queue[currentIndex], queue)
        }
    }

    fun prev() {
        if (mediaPlayer != null && mediaPlayer!!.currentPosition > 3000) {
            seekTo(0L)
            return
        }
        if (queue.isNotEmpty() && currentIndex > 0) {
            currentIndex--
            playTrack(queue[currentIndex], queue)
        } else if (queue.isNotEmpty()) {
            // Go to end
            currentIndex = queue.size - 1
            playTrack(queue[currentIndex], queue)
        }
    }

    fun seekTo(position: Long) {
        mediaPlayer?.seekTo(position.toInt())
        _currentPosition.value = position
    }

    fun release() {
        stopPositionPolling()
        mediaPlayer?.release()
        mediaPlayer = null
        scope.cancel()
    }

    private fun handlePlaybackCompletion() {
        _playbackState.value = PlaybackState.Paused
        _currentPosition.value = _duration.value
        stopPositionPolling()
        next() // Auto play next track
    }

    private fun startPositionPolling() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive && _playbackState.value is PlaybackState.Playing) {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        _currentPosition.value = it.currentPosition.toLong()
                    }
                }
                delay(400)
            }
        }
    }

    private fun stopPositionPolling() {
        positionJob?.cancel()
        positionJob = null
    }
}
