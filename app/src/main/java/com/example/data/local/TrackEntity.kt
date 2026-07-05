package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String, // YouTube Video ID
    val title: String,
    val artist: String,
    val thumbUrl: String,
    val duration: String,
    val localFilePath: String? = null, // Path to local storage if downloaded
    val isFavorite: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)
