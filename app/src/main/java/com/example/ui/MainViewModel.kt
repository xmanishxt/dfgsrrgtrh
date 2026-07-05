package com.example.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.TrackRepository
import com.example.data.local.TrackEntity
import com.example.playback.MusicPlayerManager
import com.example.playback.PlaybackState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(
    private val context: Context,
    private val repository: TrackRepository,
    private val playerManager: MusicPlayerManager
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<TrackEntity>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    // Database flows
    val allTracks = repository.allTracks.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val downloadedTracks = repository.downloadedTracks.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val favoriteTracks = repository.favoriteTracks.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    // Player forwarding
    val currentTrack = playerManager.currentTrack
    val playbackState = playerManager.playbackState
    val currentPosition = playerManager.currentPosition
    val duration = playerManager.duration

    // Lyrics State
    private val _currentLyrics = MutableStateFlow<String?>(null)
    val currentLyrics = _currentLyrics.asStateFlow()

    private val _isLyricsLoading = MutableStateFlow(false)
    val isLyricsLoading = _isLyricsLoading.asStateFlow()

    // Download Progress forwarding
    val downloadProgress = repository.downloadProgress

    init {
        // Observe current track changes to load lyrics automatically
        viewModelScope.launch {
            currentTrack.collect { track ->
                if (track != null) {
                    loadLyricsForTrack(track)
                } else {
                    _currentLyrics.value = null
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun search() {
        val query = _searchQuery.value.trim()
        if (query.isEmpty()) return
        viewModelScope.launch {
            _isSearching.value = true
            try {
                val results = repository.searchTracks(query)
                _searchResults.value = results
            } catch (e: Exception) {
                Log.e("MainViewModel", "Search error: ", e)
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun playTrack(track: TrackEntity, playlist: List<TrackEntity>) {
        playerManager.playTrack(track, playlist)
    }

    fun togglePlay() {
        playerManager.togglePlay()
    }

    fun next() {
        playerManager.next()
    }

    fun prev() {
        playerManager.prev()
    }

    fun seekTo(position: Long) {
        playerManager.seekTo(position)
    }

    fun toggleFavorite(track: TrackEntity) {
        viewModelScope.launch {
            val existing = repository.getTrackById(track.id)
            if (existing != null) {
                val updated = existing.copy(isFavorite = !existing.isFavorite)
                repository.saveTrack(updated)
            } else {
                val newFav = track.copy(isFavorite = true)
                repository.saveTrack(newFav)
            }
            // Update search results list if it contains this track
            _searchResults.value = _searchResults.value.map {
                if (it.id == track.id) it.copy(isFavorite = !it.isFavorite) else it
            }
        }
    }

    fun downloadTrack(track: TrackEntity) {
        viewModelScope.launch {
            val existing = repository.getTrackById(track.id) ?: track
            if (existing.localFilePath != null) {
                // Already downloaded
                return@launch
            }
            // Start download
            val success = repository.downloadTrack(context, existing)
            if (success) {
                // If the downloaded track is currently playing, update localFilePath
                val current = currentTrack.value
                if (current != null && current.id == track.id) {
                    val fresh = repository.getTrackById(track.id)
                    if (fresh != null) {
                        // Playback manager continues playing, but knows it has localFilePath now
                    }
                }
                // Refresh search results to show downloaded status
                val freshTrack = repository.getTrackById(track.id)
                if (freshTrack != null) {
                    _searchResults.value = _searchResults.value.map {
                        if (it.id == track.id) it.copy(localFilePath = freshTrack.localFilePath) else it
                    }
                }
            }
        }
    }

    fun deleteDownload(track: TrackEntity) {
        viewModelScope.launch {
            track.localFilePath?.let { path ->
                try {
                    val file = File(path)
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Failed to delete file: $path", e)
                }
            }

            val existing = repository.getTrackById(track.id)
            if (existing != null) {
                if (existing.isFavorite) {
                    // Keep in database as favorite, but remove local file path
                    val updated = existing.copy(localFilePath = null)
                    repository.saveTrack(updated)
                } else {
                    // Delete entirely from database
                    repository.deleteTrack(existing)
                }
            }

            // Refresh search list
            _searchResults.value = _searchResults.value.map {
                if (it.id == track.id) it.copy(localFilePath = null) else it
            }
        }
    }

    private fun loadLyricsForTrack(track: TrackEntity) {
        viewModelScope.launch {
            _isLyricsLoading.value = true
            _currentLyrics.value = null
            try {
                val lyrics = repository.getLyrics(track.artist, track.title)
                _currentLyrics.value = lyrics ?: "No lyrics found for this track."
            } catch (e: Exception) {
                Log.e("MainViewModel", "Lyrics error: ", e)
                _currentLyrics.value = "Failed to load lyrics."
            } finally {
                _isLyricsLoading.value = false
            }
        }
    }
}

class ViewModelFactory(
    private val context: Context,
    private val repository: TrackRepository,
    private val playerManager: MusicPlayerManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(context, repository, playerManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
