package com.example.data

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.data.local.TrackDao
import com.example.data.local.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class TrackRepository(private val trackDao: TrackDao) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Map of trackId -> download progress (0.0f to 1.0f)
    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress = _downloadProgress.asStateFlow()

    // Fallback list of cobalt instances for streaming/download URL extraction
    private val cobaltInstances = listOf(
        "https://api.cobalt.tools/api/json",
        "https://co.wuk.sh/api/json",
        "https://cobalt.perv.cat/api/json",
        "https://api.cobalt.tools",
        "https://co.wuk.sh",
        "https://cobalt.perv.cat"
    )

    private val invidiousInstances = listOf(
        "https://invidious.lunar.icu",
        "https://inv.us.projectsegfau.lt",
        "https://invidious.flokinet.to",
        "https://iv.melmac.space",
        "https://yewtu.be",
        "https://invidious.nerdvpn.de",
        "https://invidious.projectsegfau.lt",
        "https://invidious.privacydev.net",
        "https://inv.tux.im",
        "https://invidious.no-logs.com",
        "https://invidious.esmailelbob.xyz"
    )

    // Room Database access
    val allTracks: Flow<List<TrackEntity>> = trackDao.getAllTracks()
    val downloadedTracks: Flow<List<TrackEntity>> = trackDao.getDownloadedTracks()
    val favoriteTracks: Flow<List<TrackEntity>> = trackDao.getFavoriteTracks()

    suspend fun getTrackById(id: String): TrackEntity? = withContext(Dispatchers.IO) {
        trackDao.getTrackById(id)
    }

    suspend fun saveTrack(track: TrackEntity) = withContext(Dispatchers.IO) {
        trackDao.insertTrack(track)
    }

    suspend fun deleteTrack(track: TrackEntity) = withContext(Dispatchers.IO) {
        trackDao.deleteTrack(track)
    }

    suspend fun deleteTrackById(id: String) = withContext(Dispatchers.IO) {
        trackDao.deleteTrackById(id)
    }

    private fun extractYtInitialData(html: String): String? {
        val keys = listOf("ytInitialData = ", "window['ytInitialData'] = ", "window[\"ytInitialData\"] = ")
        var startIndex = -1
        for (key in keys) {
            val idx = html.indexOf(key)
            if (idx != -1) {
                startIndex = idx + key.length
                break
            }
        }
        if (startIndex == -1) return null
        
        // Find matching brace
        val braceStart = html.indexOf("{", startIndex)
        if (braceStart == -1) return null
        
        var openBraces = 0
        var inString = false
        var escape = false
        for (i in braceStart until html.length) {
            val c = html[i]
            if (escape) {
                escape = false
                continue
            }
            if (c == '\\') {
                escape = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (c == '{') {
                    openBraces++
                } else if (c == '}') {
                    openBraces--
                    if (openBraces == 0) {
                        return html.substring(braceStart, i + 1)
                    }
                }
            }
        }
        return null
    }

    private suspend fun <T> raceFirstSuccess(tasks: List<suspend () -> T?>): T? = withContext(Dispatchers.IO) {
        val channel = Channel<T?>(tasks.size)
        val jobsList = tasks.map { task ->
            launch {
                try {
                    val res = task()
                    channel.send(res)
                } catch (e: Exception) {
                    try { channel.send(null) } catch (ignored: Exception) {}
                }
            }
        }
        
        var receivedCount = 0
        var finalResult: T? = null
        withTimeoutOrNull(6000) {
            while (receivedCount < tasks.size) {
                val result = channel.receive()
                receivedCount++
                if (result != null) {
                    finalResult = result
                    break
                }
            }
        }
        jobsList.forEach { it.cancel() }
        finalResult
    }

    private suspend fun searchTracksInvidious(query: String): List<TrackEntity> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val tasks = invidiousInstances.map { instance ->
            suspend {
                try {
                    val url = "$instance/api/v1/search?q=$encodedQuery&type=video"
                    val fastClient = client.newBuilder()
                        .connectTimeout(3, TimeUnit.SECONDS)
                        .readTimeout(3, TimeUnit.SECONDS)
                        .build()
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .build()
                    fastClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val bodyStr = response.body?.string() ?: return@use null
                            val jsonArr = JSONArray(bodyStr)
                            val results = mutableListOf<TrackEntity>()
                            for (i in 0 until jsonArr.length()) {
                                val obj = jsonArr.optJSONObject(i) ?: continue
                                val videoId = obj.optString("videoId") ?: continue
                                if (videoId.isEmpty() || videoId.length != 11) continue
                                
                                val title = obj.optString("title", "Unknown Track")
                                val artist = obj.optString("author", "Unknown Artist")
                                
                                val lengthSeconds = obj.optInt("lengthSeconds", 0)
                                val duration = if (lengthSeconds > 0) {
                                    val min = lengthSeconds / 60
                                    val sec = lengthSeconds % 60
                                    String.format("%d:%02d", min, sec)
                                } else {
                                    "3:45"
                                }
                                
                                val thumbArr = obj.optJSONArray("videoThumbnails")
                                var thumbUrl = ""
                                if (thumbArr != null && thumbArr.length() > 0) {
                                    thumbUrl = thumbArr.optJSONObject(0).optString("url", "")
                                }
                                if (thumbUrl.isEmpty()) {
                                    thumbUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                                }
                                
                                val localTrack = trackDao.getTrackById(videoId)
                                results.add(
                                    TrackEntity(
                                        id = videoId,
                                        title = title,
                                        artist = artist,
                                        thumbUrl = thumbUrl,
                                        duration = duration,
                                        localFilePath = localTrack?.localFilePath,
                                        isFavorite = localTrack?.isFavorite ?: false
                                    )
                                )
                            }
                            if (results.isNotEmpty()) {
                                Log.d("TrackRepository", "Successfully fetched search results from Invidious instance: $instance")
                                results
                            } else {
                                null
                            }
                        } else {
                            null
                        }
                    }
                } catch (e: Exception) {
                    Log.w("TrackRepository", "Invidious search fallback failed for $instance: ${e.message}")
                    null
                }
            }
        }
        return raceFirstSuccess(tasks) ?: emptyList()
    }

    /**
     * Search YouTube by scraping results. No API key required.
     */
    suspend fun searchTracks(query: String): List<TrackEntity> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        
        // 1. Try Invidious search first (highly parallel, extremely fast, no captcha blocks)
        val invidiousResults = searchTracksInvidious(query)
        if (invidiousResults.isNotEmpty()) {
            Log.d("TrackRepository", "Search results fetched successfully from Invidious first-pass")
            return@withContext invidiousResults
        }

        // 2. Fallback to YouTube scraping ONLY if Invidious search is empty
        Log.w("TrackRepository", "Invidious search returned empty, falling back to YouTube scrape...")
        val results = mutableListOf<TrackEntity>()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.youtube.com/results?search_query=$encodedQuery&hl=en"

            val fastClient = client.newBuilder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            fastClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: ""
                    val jsonStr = extractYtInitialData(html)
                    if (jsonStr != null) {
                        val json = JSONObject(jsonStr)
                        val contents = json.optJSONObject("contents")
                            ?.optJSONObject("sectionListRenderer")
                            ?.optJSONArray("contents")

                        if (contents != null) {
                            for (i in 0 until contents.length()) {
                                val section = contents.optJSONObject(i)
                                val itemSection = section?.optJSONObject("itemSectionRenderer") ?: continue
                                val items = itemSection.optJSONArray("contents") ?: continue

                                for (j in 0 until items.length()) {
                                    val item = items.optJSONObject(j) ?: continue
                                    val vr = item.optJSONObject("videoRenderer") 
                                        ?: item.optJSONObject("videoWithContextRenderer") 
                                        ?: continue

                                    val videoId = vr.optString("videoId") ?: continue
                                    if (videoId.isEmpty() || videoId.length != 11) continue

                                    // Get title
                                    val titleObj = vr.optJSONObject("title") ?: vr.optJSONObject("headline")
                                    val title = titleObj?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                                        ?: titleObj?.optString("simpleText")
                                        ?: "Unknown Track"

                                    // Get artist / channel
                                    val ownerObj = vr.optJSONObject("ownerText") ?: vr.optJSONObject("longBylineText")
                                    val artist = ownerObj?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                                        ?: "Unknown Artist"

                                    // Get duration
                                    val lengthObj = vr.optJSONObject("lengthText")
                                    val duration = lengthObj?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                                        ?: lengthObj?.optString("simpleText")
                                        ?: "3:45"

                                    // Get thumbnail
                                    val thumbObj = vr.optJSONObject("thumbnail")
                                    val thumbnails = thumbObj?.optJSONArray("thumbnails")
                                    val thumbUrl = if (thumbnails != null && thumbnails.length() > 0) {
                                        thumbnails.optJSONObject(thumbnails.length() - 1).optString("url")
                                    } else {
                                        "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                                    }

                                    // Avoid duplicates in search results
                                    if (results.none { it.id == videoId }) {
                                        val localTrack = trackDao.getTrackById(videoId)
                                        results.add(
                                            TrackEntity(
                                                id = videoId,
                                                title = title,
                                                artist = artist,
                                                thumbUrl = thumbUrl,
                                                duration = duration,
                                                localFilePath = localTrack?.localFilePath,
                                                isFavorite = localTrack?.isFavorite ?: false
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("TrackRepository", "YouTube web search scrape failed: ", e)
        }
        return@withContext results
    }

    /**
     * Fetch stream URL using multiple public cobalt instances and Invidious fallbacks in a single parallel race
     */
    suspend fun getStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        val ytUrl = "https://www.youtube.com/watch?v=$videoId"
        
        // 1. Prepare Cobalt payloads for both v10 and v7/v8 APIs
        val bodyV10 = JSONObject().apply {
            put("url", ytUrl)
            put("downloadMode", "audio")
            put("audioFormat", "mp3")
        }
        val bodyV7 = JSONObject().apply {
            put("url", ytUrl)
            put("isAudioOnly", true)
            put("audioFormat", "mp3")
        }

        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val requestBodyV10 = bodyV10.toString().toRequestBody(mediaType)
        val requestBodyV7 = bodyV7.toString().toRequestBody(mediaType)

        // Try both Cobalt v10 and v7 request schemas in parallel across all root instances
        val cobaltTasks = cobaltInstances.flatMap { instance ->
            val rootUrl = instance
            listOf(
                // Cobalt V10 payload task
                suspend {
                    try {
                        val fastClient = client.newBuilder()
                            .connectTimeout(3, TimeUnit.SECONDS)
                            .readTimeout(3, TimeUnit.SECONDS)
                            .build()
                        val request = Request.Builder()
                            .url(rootUrl)
                            .post(requestBodyV10)
                            .addHeader("Accept", "application/json")
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Origin", "https://cobalt.tools")
                            .addHeader("Referer", "https://cobalt.tools/")
                            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            .build()

                        fastClient.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                val respStr = response.body?.string() ?: ""
                                val respJson = JSONObject(respStr)
                                val streamUrl = respJson.optString("url")
                                    .ifEmpty { respJson.optString("text") }
                                    .ifEmpty { respJson.optString("picker") }
                                if (streamUrl.isNotEmpty()) {
                                    Log.d("TrackRepository", "Resolved stream URL from Cobalt v10 at $rootUrl")
                                    return@use streamUrl
                                }
                            }
                            null
                        }
                    } catch (e: Exception) {
                        null
                    }
                },
                // Cobalt V7/V8 payload task
                suspend {
                    try {
                        val fastClient = client.newBuilder()
                            .connectTimeout(3, TimeUnit.SECONDS)
                            .readTimeout(3, TimeUnit.SECONDS)
                            .build()
                        val request = Request.Builder()
                            .url(rootUrl)
                            .post(requestBodyV7)
                            .addHeader("Accept", "application/json")
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Origin", "https://cobalt.tools")
                            .addHeader("Referer", "https://cobalt.tools/")
                            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            .build()

                        fastClient.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                val respStr = response.body?.string() ?: ""
                                val respJson = JSONObject(respStr)
                                val streamUrl = respJson.optString("url")
                                    .ifEmpty { respJson.optString("text") }
                                    .ifEmpty { respJson.optString("picker") }
                                if (streamUrl.isNotEmpty()) {
                                    Log.d("TrackRepository", "Resolved stream URL from Cobalt v7/v8 at $rootUrl")
                                    return@use streamUrl
                                }
                            }
                            null
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
            )
        }

        // 2. Prepare Invidious tasks
        val invidiousTasks = invidiousInstances.map { instance ->
            suspend {
                try {
                    val url = "$instance/api/v1/videos/$videoId"
                    val fastClient = client.newBuilder()
                        .connectTimeout(3, TimeUnit.SECONDS)
                        .readTimeout(3, TimeUnit.SECONDS)
                        .build()
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .build()

                    fastClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val bodyStr = response.body?.string() ?: return@use null
                            val json = JSONObject(bodyStr)
                            
                            // Try adaptiveFormats (audio-only streams) first
                            val adaptiveFormats = json.optJSONArray("adaptiveFormats")
                            if (adaptiveFormats != null) {
                                var bestAudioUrl: String? = null
                                var bestBitrate = 0
                                for (i in 0 until adaptiveFormats.length()) {
                                    val format = adaptiveFormats.optJSONObject(i) ?: continue
                                    val type = format.optString("type", "")
                                    if (type.startsWith("audio/")) {
                                        val streamUrl = format.optString("url", "")
                                        val bitrateStr = format.optString("bitrate", "0")
                                        val bitrate = bitrateStr.toIntOrNull() ?: 0
                                        if (streamUrl.isNotEmpty()) {
                                            var resolvedUrl = streamUrl
                                            if (resolvedUrl.startsWith("/")) {
                                                resolvedUrl = ""
                                            }
                                            if (bestAudioUrl == null || bitrate > bestBitrate) {
                                                bestAudioUrl = resolvedUrl
                                                bestBitrate = bitrate
                                            }
                                        }
                                    }
                                }
                                if (bestAudioUrl != null) {
                                    Log.d("TrackRepository", "Successfully resolved audio stream from Invidious: $instance")
                                    return@use bestAudioUrl
                                }
                            }
                            
                            // Fallback to formatStreams (progressive formats containing video/audio combined)
                            val formatStreams = json.optJSONArray("formatStreams")
                            if (formatStreams != null && formatStreams.length() > 0) {
                                val format = formatStreams.optJSONObject(0)
                                var streamUrl = format?.optString("url") ?: ""
                                if (streamUrl.isNotEmpty()) {
                                    if (streamUrl.startsWith("/")) {
                                        streamUrl = ""
                                    }
                                    Log.d("TrackRepository", "Successfully resolved progressive stream from Invidious: ")
                                    return@use streamUrl
                                }
                            }
                        }
                        null
                    }
                } catch (e: Exception) {
                    Log.w("TrackRepository", "Invidious stream URL resolver failed for $instance: ${e.message}")
                    null
                }
            }
        }

        // Combine both sources and race them simultaneously
        val combinedTasks = cobaltTasks + invidiousTasks
        val resolvedUrl = raceFirstSuccess(combinedTasks)
        if (resolvedUrl != null) {
            return@withContext resolvedUrl
        }

        return@withContext null
    }

    /**
     * Fetch lyrics from lyrics.ovh
     */
    suspend fun getLyrics(artist: String, title: String): String? = withContext(Dispatchers.IO) {
        val cleanTitle = cleanLyricsTitle(title, artist)
        val url = "https://api.lyrics.ovh/v1/${URLEncoder.encode(artist, "UTF-8")}/${URLEncoder.encode(cleanTitle, "UTF-8")}"
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val lyrics = json.optString("lyrics")
                    if (lyrics.isNotEmpty() && lyrics != "Not found") {
                        return@withContext lyrics
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("TrackRepository", "Lyrics failed for $artist - $cleanTitle: ${e.message}")
        }
        return@withContext null
    }

    private fun cleanLyricsTitle(title: String, artist: String): String {
        var t = title
        if (t.startsWith("$artist - ", ignoreCase = true)) {
            t = t.substring("$artist - ".length)
        }
        t = t.replace(Regex("""\s*\((Official\s*)?(Music\s*)?(Video|Audio)\)\s*""", RegexOption.IGNORE_CASE), "")
        t = t.replace(Regex("""\s*\(Lyrics?\)\s*""", RegexOption.IGNORE_CASE), "")
        t = t.replace(Regex("""\s*\(Lyric\s*Video\)\s*""", RegexOption.IGNORE_CASE), "")
        t = t.replace(Regex("""\s*\[.*?\]\s*"""), "")
        t = t.replace(Regex("""\s*\|.*$"""), "")
        return t.trim()
    }

    /**
     * Download track to device local storage
     */
    suspend fun downloadTrack(context: Context, track: TrackEntity): Boolean = withContext(Dispatchers.IO) {
        val progressMap = _downloadProgress.value.toMutableMap()
        progressMap[track.id] = 0.01f
        _downloadProgress.value = progressMap

        val streamUrl = getStreamUrl(track.id) ?: run {
            progressMap.remove(track.id)
            _downloadProgress.value = progressMap
            return@withContext false
        }

        try {
            val request = Request.Builder()
                .url(streamUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "*/*")
                .header("Connection", "keep-alive")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    progressMap.remove(track.id)
                    _downloadProgress.value = progressMap
                    return@withContext false
                }

                val body = response.body ?: return@withContext false
                val contentLength = body.contentLength()
                val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                    ?: context.filesDir
                val file = File(musicDir, "${track.id}.m4a")

                body.byteStream().use { inputStream ->
                    FileOutputStream(file).use { outputStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytesRead = 0L

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            if (contentLength > 0) {
                                val progress = totalBytesRead.toFloat() / contentLength
                                progressMap[track.id] = progress.coerceIn(0.01f, 0.99f)
                                _downloadProgress.value = progressMap
                            }
                        }
                    }
                }

                // Update database
                val updatedTrack = track.copy(
                    localFilePath = file.absolutePath,
                    addedAt = System.currentTimeMillis()
                )
                trackDao.insertTrack(updatedTrack)

                progressMap.remove(track.id)
                _downloadProgress.value = progressMap
                return@withContext true
            }
        } catch (e: Exception) {
            Log.e("TrackRepository", "Download failed for ${track.id}: ", e)
            progressMap.remove(track.id)
            _downloadProgress.value = progressMap
        }
        return@withContext false
    }
}
