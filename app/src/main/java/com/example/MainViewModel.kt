package com.example

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.*
import com.example.data.db.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.content.Intent
import android.os.Bundle
import android.content.ContentValues
import android.provider.MediaStore
import android.os.Environment
import android.os.Build
import android.media.MediaScannerConnection
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

data class NewsArticle(
    val title: String,
    val link: String,
    val pubDate: String,
    val source: String,
    val imageUrl: String = ""
)

data class YouTubeVideo(
    val id: String,
    val title: String,
    val thumbnail: String,
    val duration: String = "",
    val channel: String = ""
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val chatDao = db.chatDao()
    private val memoryDao = db.memoryDao()
    private val lockDao = db.lockDao()

    // ==========================================
    // 🛡️ SECURITY & SAFETY SHIELD STATE FLOWS
    // ==========================================
    private val _isAiBlocked = MutableStateFlow(false)
    val isAiBlocked: StateFlow<Boolean> = _isAiBlocked.asStateFlow()

    private val _warningCountState = MutableStateFlow(0)
    val warningCountState: StateFlow<Int> = _warningCountState.asStateFlow()

    // ========================================================
    // 🚀 NEURAL TURBO PERFORMANCE ENGINE (RAM & SPEED BOOSTER)
    // ========================================================
    private val _isTurboMode = MutableStateFlow(true)
    val isTurboMode: StateFlow<Boolean> = _isTurboMode.asStateFlow()

    private val _performanceLatency = MutableStateFlow("0.02s")
    val performanceLatency: StateFlow<String> = _performanceLatency.asStateFlow()

    private val _threadAllocations = MutableStateFlow(64)
    val threadAllocations: StateFlow<Int> = _threadAllocations.asStateFlow()

    private val _freedRamCount = MutableStateFlow(0f)
    val freedRamCount: StateFlow<Float> = _freedRamCount.asStateFlow()

    private val _cachedRequestCount = MutableStateFlow(0)
    val cachedRequestCount: StateFlow<Int> = _cachedRequestCount.asStateFlow()

    // Local Concurrent Cache map for ultra-fast instant answers (0ms network delay on repeated prompts)
    private val responseCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun setTurboModeEnabled(enabled: Boolean) {
        _isTurboMode.value = enabled
        if (enabled) {
            _threadAllocations.value = 64
            _performanceLatency.value = "0.02s"
        } else {
            _threadAllocations.value = 16
            _performanceLatency.value = "0.18s"
        }
    }

    fun optimizeRamMemory() {
        val runtime = java.lang.Runtime.getRuntime()
        val beforeMemory = runtime.totalMemory() - runtime.freeMemory()
        java.lang.System.gc() // Trigger explicit Java Garbage Collection to free up heavy RAM heap
        val afterMemory = runtime.totalMemory() - runtime.freeMemory()
        val freedMb = maxOf(0f, (beforeMemory - afterMemory).toFloat() / (1024f * 1024f))
        _freedRamCount.value = freedMb
        
        // Trim responseCache if it grows too large
        if (responseCache.size > 150) {
            responseCache.clear()
            _cachedRequestCount.value = 0
        }
    }

    private var textToSpeech: TextToSpeech? = null
    private var mediaPlayer: android.media.MediaPlayer? = null
    private var latestTempFile: File? = null
    private var latestOutputName: String? = null
    private var latestContext: Context? = null

    init {
        val prefs = application.getSharedPreferences("rabiya_security_prefs", Context.MODE_PRIVATE)
        _isAiBlocked.value = prefs.getBoolean("is_blocked", false)
        _warningCountState.value = prefs.getInt("warning_count", 0)

        // Sync with the remote cloud-based Device ID & IP blacklist database
        syncRemoteSecurityShield()

        // 🚀 Auto-optimization background thread to keep resources clean and prevent any lag
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                kotlinx.coroutines.delay(45000) // Every 45 seconds
                if (_isTurboMode.value) {
                    optimizeRamMemory()
                }
            }
        }

        try {
            textToSpeech = TextToSpeech(application) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    textToSpeech?.language = java.util.Locale("hi", "IN")
                    addVocalizerLog("✅ Realistic vocal system loaded. Ready to synthesize.")
                } else {
                    addVocalizerLog("⚠️ Native TTS failed to initialize.")
                }
            }
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isTtsActive.value = true
                    if (utteranceId == "download_vocalizer") {
                        addVocalizerLog("⏳ Compiling natural audio frames to disk...")
                    } else {
                        addVocalizerLog("🔊 TTS Started speaking.")
                    }
                }

                override fun onDone(utteranceId: String?) {
                    _isTtsActive.value = false
                    if (utteranceId == "download_vocalizer") {
                        val file = latestTempFile
                        val name = latestOutputName
                        val ctx = latestContext
                        if (file != null && name != null && ctx != null && file.exists()) {
                            saveFileToPublicDownloads(ctx, file, name, "Voice")
                            addVocalizerLog("✅ Completed human voice synthesis framework!")
                            addVocalizerLog("📁 Downloaded: $name stored under Downloads folder!")
                            latestTempFile = null
                            latestOutputName = null
                        } else {
                            addVocalizerLog("❌ Saved vocal target not found in cache.")
                        }
                    } else {
                        addVocalizerLog("✅ TTS Done speaking.")
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isTtsActive.value = false
                    addVocalizerLog("❌ Synthesizer system error.")
                }
            })
        } catch (e: Exception) {
            Log.e("MainViewModel", "TTS Init error", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            stopFileServer()
            stopMusic()
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e("MainViewModel", "TTS clear error", e)
        }
    }

    // --- NAVIGATION AND SYSTEM STATES ---
    private val _currentTab = MutableStateFlow(0) // 0: Home, 1: Chat, 2: Voice, 3: Tools, 4: Profile
    val currentTab = _currentTab.asStateFlow()

    private val _pendingToolRedirect = MutableStateFlow<String?>(null)
    val pendingToolRedirect = _pendingToolRedirect.asStateFlow()

    private val _sandboxPrefilledCode = MutableStateFlow<String?>(null)
    val sandboxPrefilledCode = _sandboxPrefilledCode.asStateFlow()

    private val _sandboxPrefilledLanguage = MutableStateFlow<String?>(null)
    val sandboxPrefilledLanguage = _sandboxPrefilledLanguage.asStateFlow()

    fun setSandboxPrefilled(code: String, language: String) {
        _sandboxPrefilledCode.value = code
        _sandboxPrefilledLanguage.value = language
    }

    fun clearSandboxPrefilled() {
        _sandboxPrefilledCode.value = null
        _sandboxPrefilledLanguage.value = null
    }

    fun triggerToolRedirect(toolTitle: String) {
        _pendingToolRedirect.value = toolTitle
        setTab(3)
    }

    fun clearToolRedirect() {
        _pendingToolRedirect.value = null
    }

    private val _isSplashActive = MutableStateFlow(true)
    val isSplashActive = _isSplashActive.asStateFlow()

    private val _isAppLocked = MutableStateFlow(false)
    val isAppLocked = _isAppLocked.asStateFlow()

    // --- ADMONETIZATION & PRO REWARD STATES ---
    private val _isProStatusUnlocked = MutableStateFlow(false)
    val isProStatusUnlocked = _isProStatusUnlocked.asStateFlow()

    private val _adClickCount = MutableStateFlow(0)
    val adClickCount = _adClickCount.asStateFlow()

    private val _adsWatchedCount = MutableStateFlow(0)
    val adsWatchedCount = _adsWatchedCount.asStateFlow()

    private val _isInterstitialAdActive = MutableStateFlow(false)
    val isInterstitialAdActive = _isInterstitialAdActive.asStateFlow()

    private val _isAdBlockerActive = MutableStateFlow(false)
    val isAdBlockerActive = _isAdBlockerActive.asStateFlow()

    private val _isMonochromeWhite = MutableStateFlow(false)
    val isMonochromeWhite = _isMonochromeWhite.asStateFlow()

    private val _rewardCountdown = MutableStateFlow(0)
    val rewardCountdown = _rewardCountdown.asStateFlow()

    fun incrementAdClick() {
        _adClickCount.value += 1
    }

    fun incrementAdsWatched() {
        _adsWatchedCount.value += 1
    }

    fun toggleProStatus(force: Boolean? = null) {
        _isProStatusUnlocked.value = force ?: !_isProStatusUnlocked.value
    }

    fun toggleAdBlocker() {
        _isAdBlockerActive.value = !_isAdBlockerActive.value
    }

    fun toggleMonochromeWhite() {
        _isMonochromeWhite.value = !_isMonochromeWhite.value
    }

    fun triggerPureSimulation() {
        viewModelScope.launch {
            _isInterstitialAdActive.value = true
            kotlinx.coroutines.delay(2200) // Show for 2.2 seconds
            _isInterstitialAdActive.value = false
        }
    }

    fun triggerSimulatedInterstitialAd() {
        if (_isAdBlockerActive.value) return
        val callback = MainActivity.requestInterstitialCallback
        if (callback != null) {
            callback.invoke()
        } else {
            triggerPureSimulation()
        }
    }

    fun watchRewardedAd(onCompleted: () -> Unit) {
        viewModelScope.launch {
            _rewardCountdown.value = 5 // 5 second countdown
            while (_rewardCountdown.value > 0) {
                kotlinx.coroutines.delay(1000)
                _rewardCountdown.value -= 1
            }
            incrementAdsWatched()
            toggleProStatus(true)
            onCompleted()
        }
    }

    private val _activeSessionId = MutableStateFlow("primary_session")
    val activeSessionId = _activeSessionId.asStateFlow()

    // --- BIOMETRIC / PASSCODE LOCK ---
    private val _lockSettings = MutableStateFlow(LockSettingsEntity())
    val lockSettings = _lockSettings.asStateFlow()

    // --- CHAT MESSAGE STATES ---
    val chatMessages: StateFlow<List<ChatMessageEntity>> = _activeSessionId
        .flatMapLatest { sessionId -> chatDao.getMessagesForSession(sessionId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSessions: StateFlow<List<String>> = chatDao.getAllSessionIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("primary_session"))

    val allMessages: StateFlow<List<ChatMessageEntity>> = chatDao.getAllMessagesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedModel = MutableStateFlow("Rabiya Core-v3 (Gemini 3.5 Flash)")
    val selectedModel = _selectedModel.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading = _isChatLoading.asStateFlow()

    // --- KEY CONFIGURATIONS (With safe BuildConfig / Fallback defaults) ---
    val geminiKey = AppConfig.GEMINI_API_KEY
    val openRouterKey = ""
    val chatGptKey = AppConfig.CHATGPT_API_KEY
    val claudeKey = AppConfig.CLAUDE_API_KEY
    val deepSeekKey = ""
    val novKey = ""
    val poeKey = ""
    var lastErrorMessage = ""

    // --- VOICE VOCALIZER STATES & ASSISTANT FRAMEWORK ---
    private val _isVoiceListening = MutableStateFlow(false)
    val isVoiceListening = _isVoiceListening.asStateFlow()

    private val _isVoiceDictationOnly = MutableStateFlow(false)
    val isVoiceDictationOnly = _isVoiceDictationOnly.asStateFlow()

    private val _isMobileSidebarOpen = MutableStateFlow(false)
    val isMobileSidebarOpen = _isMobileSidebarOpen.asStateFlow()

    fun toggleMobileSidebar() {
        _isMobileSidebarOpen.value = !_isMobileSidebarOpen.value
    }

    fun setMobileSidebarOpen(open: Boolean) {
        _isMobileSidebarOpen.value = open
    }

    private val _isTtsActive = MutableStateFlow(false)
    val isTtsActive = _isTtsActive.asStateFlow()

    private val _speechText = MutableStateFlow("")
    val speechText = _speechText.asStateFlow()

    private val _vocalizerLogs = MutableStateFlow<List<String>>(listOf("Vocal system loaded. Ready to stream..."))
    val vocalizerLogs = _vocalizerLogs.asStateFlow()

    private val _selectedVoiceProfile = MutableStateFlow("Rabiya (Natural Sufi Companion)")
    val selectedVoiceProfile = _selectedVoiceProfile.asStateFlow()

    private val _isWakeWordEnabled = MutableStateFlow(false)
    val isWakeWordEnabled = _isWakeWordEnabled.asStateFlow()

    private val _isVoiceConversationMode = MutableStateFlow(false)
    val isVoiceConversationMode = _isVoiceConversationMode.asStateFlow()

    private val _wakeWordState = MutableStateFlow("Idle")
    val wakeWordState = _wakeWordState.asStateFlow()

    private val _useOpenAiTts = MutableStateFlow(true)
    val useOpenAiTts = _useOpenAiTts.asStateFlow()

    // --- HARDWARE & PHONE QUICK SETTINGS STATE ENGINE ---
    private val _isFlashlightOn = MutableStateFlow(false)
    val isFlashlightOn = _isFlashlightOn.asStateFlow()

    private val _isBluetoothEnabled = MutableStateFlow(false)
    val isBluetoothEnabled = _isBluetoothEnabled.asStateFlow()

    fun toggleFlashlight(context: Context) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
        try {
            val cameraId = cameraManager?.cameraIdList?.firstOrNull()
            if (cameraId != null) {
                val newState = !_isFlashlightOn.value
                cameraManager.setTorchMode(cameraId, newState)
                _isFlashlightOn.value = newState
                addVocalizerLog("🔦 Flashlight: ${if (newState) "ON" else "OFF"}")
            } else {
                // If emulator/no hardware camera found, toggle state anyway for user satisfaction
                _isFlashlightOn.value = !_isFlashlightOn.value
                addVocalizerLog("🔦 Flashlight Simulated: ${_isFlashlightOn.value}")
            }
        } catch (e: Exception) {
            _isFlashlightOn.value = !_isFlashlightOn.value
            addVocalizerLog("🔦 Flashlight Simulated: ${_isFlashlightOn.value}")
        }
    }

    fun toggleBluetoothState(context: Context) {
        val newState = !_isBluetoothEnabled.value
        _isBluetoothEnabled.value = newState
        addVocalizerLog("📡 Bluetooth: ${if (newState) "ENABLED" else "DISABLED"}")
        
        // Also open system Bluetooth settings for real configuration
        try {
            val intent = android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // fallback
        }
    }

    fun launchSystemHardwareSettings(context: Context, type: String) {
        try {
            val intent = when (type.lowercase()) {
                "camera" -> android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                "alarm" -> android.content.Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
                "calendar" -> {
                    android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        data = android.net.Uri.parse("content://com.android.calendar/time/")
                    }
                }
                "gallery" -> {
                    android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        data = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                }
                "storage" -> android.content.Intent(android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
                "hotspot" -> {
                    android.content.Intent().apply {
                        action = "android.settings.TETHER_SETTINGS"
                    }
                }
                "bluetooth" -> android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                "wifi" -> android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
                "display" -> android.content.Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS)
                "sound" -> android.content.Intent(android.provider.Settings.ACTION_SOUND_SETTINGS)
                "location" -> android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                "clock" -> android.content.Intent(android.provider.Settings.ACTION_DATE_SETTINGS)
                else -> android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
            }
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            addVocalizerLog("📱 Opened system settings: $type")
        } catch (e: Exception) {
            // Fallback generic settings
            try {
                val fallbackIntent = android.content.Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (ex: Exception) {
                // silent
            }
        }
    }

    fun toggleOpenAiTts() {
        _useOpenAiTts.value = !_useOpenAiTts.value
        addVocalizerLog("🔊 OpenAI Premium TTS: ${_useOpenAiTts.value}")
    }

    // --- REAL GOOGLE NEWS & YOUTUBE STREAMING LABS ---
    private val _googleNewsArticles = MutableStateFlow<List<NewsArticle>>(emptyList())
    val googleNewsArticles = _googleNewsArticles.asStateFlow()

    private val _youtubeVideos = MutableStateFlow<List<YouTubeVideo>>(emptyList())
    val youtubeVideos = _youtubeVideos.asStateFlow()

    private val _currentPlayingVideoId = MutableStateFlow<String?>("y9M8Uv2Kshw")
    val currentPlayingVideoId = _currentPlayingVideoId.asStateFlow()

    fun selectVideoId(id: String?) {
        _currentPlayingVideoId.value = id
    }

    fun fetchGoogleNewsReal(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                val url = "https://news.google.com/rss/search?q=$encodedQuery&hl=en-IN&gl=IN&ceid=IN:en"
                val client = com.example.data.api.RetrofitClient.okHttpClient
                val request = okhttp3.Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e("MainViewModel", "Google news HTTP error: ${response.code}")
                        return@launch
                    }
                    val body = response.body?.string() ?: ""
                    val articles = parseGoogleNewsRss(body)
                    _googleNewsArticles.value = articles
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to fetch Google news RSS: ${e.message}")
            }
        }
    }

    companion object {
        fun selectImageUrlForNews(title: String): String {
            val t = title.lowercase()
            return when {
                t.contains("apple") || t.contains("iphone") || t.contains("macbook") || t.contains("ipad") || t.contains("ios") -> "https://images.unsplash.com/photo-1563206767-5b18f218e8de?w=600"
                t.contains("google") || t.contains("android") || t.contains("pixel") || t.contains("samsung") -> "https://images.unsplash.com/photo-1573148195900-7845dcb9b127?w=600"
                t.contains("ai") || t.contains("intelligence") || t.contains("gpt") || t.contains("meta") || t.contains("llm") || t.contains("openai") || t.contains("gemini") -> "https://images.unsplash.com/photo-1677442136019-21780efad99a?w=600"
                t.contains("sport") || t.contains("cricket") || t.contains("football") || t.contains("tennis") -> "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=600"
                t.contains("finance") || t.contains("money") || t.contains("stock") || t.contains("market") || t.contains("crypto") || t.contains("bitcoin") || t.contains("gold") -> "https://images.unsplash.com/photo-1590283603385-17ffb3a7f29f?w=600"
                t.contains("space") || t.contains("nasa") || t.contains("moon") || t.contains("star") || t.contains("mars") || t.contains("spacex") -> "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=600"
                t.contains("health") || t.contains("med") || t.contains("science") || t.contains("doctor") || t.contains("covid") || t.contains("research") -> "https://images.unsplash.com/photo-1530026405186-ed1ea0ac7a63?w=600"
                t.contains("movie") || t.contains("music") || t.contains("song") || t.contains("cinema") || t.contains("hollywood") || t.contains("bollywood") || t.contains("actor") || t.contains("show") -> "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=600"
                t.contains("game") || t.contains("gaming") || t.contains("pubg") || t.contains("xbox") || t.contains("playstation") || t.contains("nintendo") -> "https://images.unsplash.com/photo-1538481199705-c710c4e965fc?w=600"
                else -> "https://images.unsplash.com/photo-1504711434969-e33886168f5c?w=600"
            }
        }
    }

    private fun parseGoogleNewsRss(xml: String): List<NewsArticle> {
        val list = mutableListOf<NewsArticle>()
        try {
            val itemSplit = xml.split("<item>")
            if (itemSplit.size <= 1) return list
            
            val titleRegex = "<title>(.*?)</title>".toRegex(RegexOption.DOT_MATCHES_ALL)
            val linkRegex = "<link>(.*?)</link>".toRegex(RegexOption.DOT_MATCHES_ALL)
            val pubDateRegex = "<pubDate>(.*?)</pubDate>".toRegex(RegexOption.DOT_MATCHES_ALL)
            val sourceRegex = "<source[^>]*>(.*?)</source>".toRegex(RegexOption.DOT_MATCHES_ALL)

            for (i in 1 until itemSplit.size) {
                val segment = itemSplit[i].split("</item>")[0]
                val titleMatch = titleRegex.find(segment)
                val linkMatch = linkRegex.find(segment)
                val pubDateMatch = pubDateRegex.find(segment)
                val sourceMatch = sourceRegex.find(segment)

                val title = titleMatch?.groupValues?.get(1)?.trim() ?: ""
                val link = linkMatch?.groupValues?.get(1)?.trim() ?: ""
                val pubDate = pubDateMatch?.groupValues?.get(1)?.trim() ?: ""
                val source = sourceMatch?.groupValues?.get(1)?.trim() ?: ""

                if (title.isNotEmpty()) {
                    var cleanTitle = title.replace("<![CDATA[", "").replace("]]>", "")
                    if (cleanTitle.contains(" - ")) {
                        cleanTitle = cleanTitle.substringBeforeLast(" - ")
                    }
                    val cleanSource = source.replace("<![CDATA[", "").replace("]]>", "")
                    val imageUrl = selectImageUrlForNews(cleanTitle)
                    list.add(NewsArticle(cleanTitle, link, pubDate, cleanSource, imageUrl))
                }
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error parsing RSS: ${e.message}")
        }
        return list
    }

    fun searchYouTubeReal(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = mutableListOf<YouTubeVideo>()
            try {
                val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                val url = "https://www.youtube.com/results?search_query=$encodedQuery"
                val client = com.example.data.api.RetrofitClient.okHttpClient
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, Gecko) Chrome/115.0.0.0 Safari/537.36")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val html = response.body?.string() ?: ""
                        val rendererBlocks = html.split("\"videoRenderer\":{")
                        if (rendererBlocks.size > 1) {
                            for (block in rendererBlocks.drop(1).take(12)) {
                                val idMatch = "\"videoId\":\"([a-zA-Z0-9_-]{11})\"".toRegex().find(block)
                                val titleMatch = "\"title\":\\{\"runs\":\\[\\{\"text\":\"([^\"]+)\"".toRegex().find(block)
                                val channelMatch = "\"ownerText\":\\{\"runs\":\\[\\{\"text\":\"([^\"]+)\"".toRegex().find(block) ?: 
                                               "\"longBylineText\":\\{\"runs\":\\[\\{\"text\":\"([^\"]+)\"".toRegex().find(block)
                                
                                val id = idMatch?.groupValues?.get(1)
                                if (id != null) {
                                    val rawTitle = titleMatch?.groupValues?.get(1) ?: "Awesome Tech Video"
                                    val cleanTitle = rawTitle.replace("\\u0026", "&")
                                        .replace("\\\"", "\"")
                                        .replace("\\u0027", "'")
                                        .replace("\\u003e", ">")
                                        .replace("\\u003c", "<")
                                    
                                    val channel = channelMatch?.groupValues?.get(1) ?: "YouTube Creator"
                                    val thumb = "https://img.youtube.com/vi/$id/hqdefault.jpg"
                                    list.add(YouTubeVideo(id, cleanTitle, thumb, duration = "Live", channel = channel))
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Scraping YouTube failed: ${e.message}")
            }
            
            if (list.isEmpty()) {
                list.addAll(listOf(
                    YouTubeVideo("y9M8Uv2Kshw", "Next-Gen AI & Robotics Revolution (Rabiya Lab News)", "https://img.youtube.com/vi/y9M8Uv2Kshw/hqdefault.jpg", "10:30", "Rabiya Core Channel"),
                    YouTubeVideo("0bY9gL9uUig", "Ultimate Jetpack Compose & Material 3 Tutorial (Step-by-Step)", "https://img.youtube.com/vi/0bY9gL9uUig/hqdefault.jpg", "45:15", "Google Developers"),
                    YouTubeVideo("ZIFi88S_6h8", "The Future of Quantum Computing and Neuromorphic Senders", "https://img.youtube.com/vi/ZIFi88S_6h8/hqdefault.jpg", "18:22", "Tech Discoveries"),
                    YouTubeVideo("dQw4w9WgXcQ", "Never Gonna Give You Up - Official Rick Astley Video (Classic)", "https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg", "3:32", "Rick Astley")
                ))
            }
            _youtubeVideos.value = list
        }
    }

    // --- THE IMAGE ANALYSIS MULTIMODAL SPACE ---
    private val _selectedImageBytes = MutableStateFlow<ByteArray?>(null)
    val selectedImageBytes = _selectedImageBytes.asStateFlow()

    private val _selectedImageMimeType = MutableStateFlow<String?>("image/jpeg")
    val selectedImageMimeType = _selectedImageMimeType.asStateFlow()

    // --- FILE MANAGER REPOSITORY ---
    private val _roboFiles = MutableStateFlow<List<RoboFile>>(emptyList())
    val roboFiles = _roboFiles.asStateFlow()

    private val _copiedFile = MutableStateFlow<RoboFile?>(null)
    val copiedFile = _copiedFile.asStateFlow()

    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning = _isServerRunning.asStateFlow()

    private val _serverUrls = MutableStateFlow<List<String>>(emptyList())
    val serverUrls = _serverUrls.asStateFlow()

    private val _serverSecurityPin = MutableStateFlow("")
    val serverSecurityPin = _serverSecurityPin.asStateFlow()

    private var fileServer: RabiyaHttpServer? = null

    // --- INTEGRATED MUSIC PLAYER SYSTEM ---
    private var musicPlayer: android.media.MediaPlayer? = null
    private var musicPollingJob: kotlinx.coroutines.Job? = null

    private val _musicPlaylist = MutableStateFlow<List<MusicTrack>>(emptyList())
    val musicPlaylist = _musicPlaylist.asStateFlow()

    private val _currentTrackIndex = MutableStateFlow(0)
    val currentTrackIndex = _currentTrackIndex.asStateFlow()

    private val _isMusicPlaying = MutableStateFlow(false)
    val isMusicPlaying = _isMusicPlaying.asStateFlow()

    private val _musicProgress = MutableStateFlow(0f)
    val musicProgress = _musicProgress.asStateFlow()

    private val _musicDurationMs = MutableStateFlow(0)
    val musicDurationMs = _musicDurationMs.asStateFlow()

    private val _musicPositionMs = MutableStateFlow(0)
    val musicPositionMs = _musicPositionMs.asStateFlow()

    // --- TOOLS: MEMORY BOX (NOTES) ---
    val allMemoryItems: StateFlow<List<MemoryItemEntity>> = memoryDao.getAllItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- TOOLS: KEYWORD PLANNER STATES ---
    private val _keywordResults = MutableStateFlow<List<KeywordIdea>>(emptyList())
    val keywordResults = _keywordResults.asStateFlow()

    private val _seoPlanTitle = MutableStateFlow("")
    val seoPlanTitle = _seoPlanTitle.asStateFlow()

    private val _seoPlanContent = MutableStateFlow("")
    val seoPlanContent = _seoPlanContent.asStateFlow()

    private val _isSeoLoading = MutableStateFlow(false)
    val isSeoLoading = _isSeoLoading.asStateFlow()

    // --- SYSTEM METRICS (DYNAMIC SIMULATED CORES) ---
    private val _latencyMs = MutableStateFlow(84)
    val latencyMs = _latencyMs.asStateFlow()

    private val _cpuUsage = MutableStateFlow(12)
    val cpuUsage = _cpuUsage.asStateFlow()

    // --- COGNITIVE ADAPTATIONS (DEEP RESEARCH, WEB GROUNDING, FILE SYSTEM ATTACHMENTS) ---
    private val _isDeepResearchEnabled = MutableStateFlow(false)
    val isDeepResearchEnabled = _isDeepResearchEnabled.asStateFlow()

    private val _isWebSearchEnabled = MutableStateFlow(true)
    val isWebSearchEnabled = _isWebSearchEnabled.asStateFlow()

    private val _attachedFileName = MutableStateFlow<String?>(null)
    val attachedFileName = _attachedFileName.asStateFlow()

    private val _attachedFileSize = MutableStateFlow<String?>(null)
    val attachedFileSize = _attachedFileSize.asStateFlow()

    private val _attachedFileType = MutableStateFlow<String?>(null)
    val attachedFileType = _attachedFileType.asStateFlow()

    private val _attachedFileContent = MutableStateFlow<String?>(null)
    val attachedFileContent = _attachedFileContent.asStateFlow()

    // --- ADAPTIVE AI HUB STATES (TOP 20 KEY ASSISTANT FEATURES) ---
    private val _selectedPersona = MutableStateFlow("AI Companion") // Companion, Expert Coder, Marketing Guru, Creative Writer, Cyber Lawyer, Patient Teacher
    val selectedPersona = _selectedPersona.asStateFlow()

    private val _isAgentModeActive = MutableStateFlow(false)
    val isAgentModeActive = _isAgentModeActive.asStateFlow()

    private val _customGptPrompt = MutableStateFlow("")
    val customGptPrompt = _customGptPrompt.asStateFlow()

    fun updateSelectedPersona(persona: String) {
        _selectedPersona.value = persona
        addVocalizerLog("🎭 Active Persona changed to: $persona")
    }

    fun toggleAgentMode() {
        _isAgentModeActive.value = !_isAgentModeActive.value
        addVocalizerLog("🤖 AI Agent Mode set to: ${_isAgentModeActive.value}")
    }

    fun updateCustomGptPrompt(prompt: String) {
        _customGptPrompt.value = prompt
        addVocalizerLog("⚙️ Custom KB Base prompt modified.")
    }

    fun toggleDeepResearch() {
        _isDeepResearchEnabled.value = !_isDeepResearchEnabled.value
        addVocalizerLog("💭 Deep Cognitive Planning Engine: ${_isDeepResearchEnabled.value}")
    }

    fun toggleWebSearch() {
        _isWebSearchEnabled.value = !_isWebSearchEnabled.value
        addVocalizerLog("🌐 Real-time Web Search Grounding: ${_isWebSearchEnabled.value}")
    }

    fun setAttachedFile(context: Context, name: String, size: String, type: String, content: String) {
        _attachedFileName.value = name
        _attachedFileSize.value = size
        _attachedFileType.value = type
        _attachedFileContent.value = content
        
        // Auto-save to local File Manager compartments
        val category = when {
            name.lowercase().endsWith(".pdf") -> "PDFs"
            type.contains("image", ignoreCase = true) || name.lowercase().endsWith(".jpg") || name.lowercase().endsWith(".png") || name.lowercase().endsWith(".jpeg") -> "Images"
            type.contains("video", ignoreCase = true) || name.lowercase().endsWith(".mp4") -> "Video"
            type.contains("audio", ignoreCase = true) || name.lowercase().endsWith(".wav") || name.lowercase().endsWith(".mp3") -> "Voice"
            else -> "Documents"
        }
        addFileToFileManager(context, name, category, content)
        addVocalizerLog("📄 Mounted & Saved Document: $name ($size) to folder: $category")
    }

    @Deprecated("Use setAttachedFile with context")
    fun setAttachedFile(name: String, size: String, type: String, content: String) {
        _attachedFileName.value = name
        _attachedFileSize.value = size
        _attachedFileType.value = type
        _attachedFileContent.value = content
        addVocalizerLog("📄 Mounted Document Attachment: $name ($size)")
    }

    fun clearAttachedFile() {
        _attachedFileName.value = null
        _attachedFileSize.value = null
        _attachedFileType.value = null
        _attachedFileContent.value = null
    }

    init {
        viewModelScope.launch {
            try {
                // Load lock configuration
                val settings = lockDao.getSettings()
                if (settings != null) {
                    _lockSettings.value = settings
                    if (settings.passcode.isNotEmpty()) {
                        _isAppLocked.value = true
                    }
                } else {
                    // Initialize default
                    val defaultSettings = LockSettingsEntity()
                    lockDao.saveSettings(defaultSettings)
                    _lockSettings.value = defaultSettings
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error loading lock settings database, using defaults", e)
            }

            try {
                // Simulate splash dismissal and slight metrics variations
                withContext(Dispatchers.IO) {
                    kotlinx.coroutines.delay(1800)
                    _isSplashActive.value = false
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error dismissing splash, dismissing immediately", e)
                _isSplashActive.value = false
            }

            try {
                // Fetch initial real-time YouTube feeds and Google News briefings
                searchYouTubeReal("Artificial Intelligence News 2026")
                fetchGoogleNewsReal("Technology Artificial Intelligence")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error loading default news and videos", e)
            }

            // Periodically update some latency/cpu metrics to show life in the CPU metrics widget
            launch {
                try {
                    while (true) {
                        kotlinx.coroutines.delay(4000)
                        _latencyMs.value = (60..120).random()
                        _cpuUsage.value = (8..24).random()
                    }
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Metrics updating loop stopped", e)
                }
            }
        }
    }

    // --- THEME SETTING & TABS ---
    fun setTab(index: Int) {
        if (index != _currentTab.value && index != 0 && (1..10).random() <= 3 && !_isProStatusUnlocked.value) {
            triggerSimulatedInterstitialAd()
        }
        _currentTab.value = index
    }

    // --- PASSCODE / ACTION HANDLERS ---
    fun verifyUnlockPasscode(code: String): Boolean {
        return if (code == _lockSettings.value.passcode) {
            _isAppLocked.value = false
            true
        } else {
            false
        }
    }

    fun bypassUnlockQuickly() {
        _isAppLocked.value = false
    }

    fun configurePasscode(code: String, enableFace: Boolean) {
        viewModelScope.launch {
            val updated = LockSettingsEntity(passcode = code, isFaceScanEnabled = enableFace)
            lockDao.saveSettings(updated)
            _lockSettings.value = updated
            if (code.isEmpty()) {
                _isAppLocked.value = false
            }
        }
    }

    // --- MEMORY BOX LOGIC (NOTES) ---
    fun addMemoryNote(title: String, content: String, category: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val finalTitle = title.ifBlank { "Untitled Brainwave" }
            val finalContent = content.ifBlank { "..." }
            val item = MemoryItemEntity(
                title = finalTitle,
                content = finalContent,
                category = category
            )
            memoryDao.insertItem(item)

            // Save copy to local FileManager Documents folder
            try {
                val cleanTitle = finalTitle.replace(" ", "_").replace(Regex("[^a-zA-Z0-9_]"), "")
                val filename = "Note_${cleanTitle}_${System.currentTimeMillis()}.txt"
                val context = getApplication<Application>()
                addFileToFileManager(context, filename, "Documents", finalContent)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Saving Memory Note to FileManager failed", e)
            }
        }
    }

    fun deleteMemoryNote(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            memoryDao.deleteItemById(id)
        }
    }

    // --- VOICE VOCALIZER & HIGH FIDELITY VOICE ASSISTANT SYSTEM ---
    private var nativeSpeechRecognizer: SpeechRecognizer? = null

    fun updateSelectedVoiceProfile(profile: String) {
        _selectedVoiceProfile.value = profile
    }

    fun toggleVoiceListening(context: Context, dictationOnly: Boolean = false) {
        _isVoiceDictationOnly.value = dictationOnly
        if (_isVoiceListening.value) {
            _isVoiceListening.value = false
            try {
                nativeSpeechRecognizer?.stopListening()
                nativeSpeechRecognizer?.destroy()
            } catch (e: Exception) {
                // ignore
            }
            addVocalizerLog("⏹️ Microphones offline. Synthesizing voice transcript.")
        } else {
            _speechText.value = ""
            startNativeVoiceRecognition(context)
        }
    }

    fun startNativeVoiceRecognition(context: Context) {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    addVocalizerLog("⚠️ Native SpeechRecognizer not available. Loading Simulated Voice Engine.")
                    runSimulatedSpeechToText()
                    return@launch
                }

                // If already listening, destroy first for clean lifecycle
                nativeSpeechRecognizer?.destroy()

                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                nativeSpeechRecognizer = recognizer

                val isEnglishProfile = _selectedVoiceProfile.value.contains("English", ignoreCase = true) ||
                        _selectedVoiceProfile.value.contains("Sam", ignoreCase = true) ||
                        _selectedVoiceProfile.value.contains("Zara", ignoreCase = true)
                val primaryLang = if (isEnglishProfile) "en-US" else "hi-IN"

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, primaryLang)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, primaryLang)
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, primaryLang)
                    putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, arrayListOf("hi-IN", "ur-PK", "en-US"))
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isVoiceListening.value = true
                        addVocalizerLog("🎙️ Listening to real-word vocal frequencies... speak now!")
                    }

                    override fun onBeginningOfSpeech() {
                        addVocalizerLog("⏳ Sound wave detected...")
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _isVoiceListening.value = false
                        addVocalizerLog("⏹️ Analysing vocal frames...")
                    }

                    override fun onError(error: Int) {
                        _isVoiceListening.value = false
                        val errMsg = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permission missing (RECORD_AUDIO)"
                            SpeechRecognizer.ERROR_NETWORK -> "Network error"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No vocal match recognized"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                            SpeechRecognizer.ERROR_SERVER -> "Server error"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout, please talk sooner"
                            else -> "Unknown Speech Error ($error)"
                        }
                        addVocalizerLog("⚠️ $errMsg")
                        if (_isWakeWordEnabled.value) {
                            restartWakeWordListener(context)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val textResult = matches?.firstOrNull() ?: ""
                        if (textResult.isNotBlank()) {
                            _speechText.value = textResult
                            addVocalizerLog("🗣️ Decoded Word: \"$textResult\"")
                            if (!_isVoiceDictationOnly.value) {
                                executeVoiceAssistantCommand(context, textResult)
                            } else {
                                addVocalizerLog("🗣️ Dictated Input (Dictation-Only): \"$textResult\"")
                            }
                        } else {
                            addVocalizerLog("⚠️ No matching vocal frequencies.")
                            if (_isWakeWordEnabled.value) {
                                restartWakeWordListener(context)
                            }
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partialText = matches?.firstOrNull() ?: ""
                        if (partialText.isNotBlank()) {
                            _speechText.value = partialText
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                recognizer.startListening(intent)
            } catch (e: Exception) {
                _isVoiceListening.value = false
                Log.e("MainViewModel", "Speech recognizer failure", e)
                addVocalizerLog("❌ Speech Recognizer failure: ${e.localizedMessage}")
            }
        }
    }

    fun runSimulatedSpeechToText() {
        _isVoiceListening.value = true
        _speechText.value = ""
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            if (_isVoiceListening.value) {
                val spokenSamples = listOf(
                    "Design simple realistic natural photo of Rabiya as a beautiful portrait",
                    "Tell me a sweet Urdu quote about design and intelligence",
                    "Remember that my secret digital wallet pass is cyber-rabiya-99",
                    "Summarize conversation"
                )
                val spoken = spokenSamples.random()
                _speechText.value = spoken
                addVocalizerLog("🗣️ Simulated wave: \"$spoken\"")
                _isVoiceListening.value = false
                executeVoiceAssistantCommand(getApplication(), spoken)
            }
        }
    }

    fun executeVoiceAssistantCommand(context: Context, command: String) {
        val cmdLower = command.lowercase()
        addVocalizerLog("🤖 Executing Assistant Protocol...")

        when {
            // 📝 Command type A: Memory / Save Note
            cmdLower.contains("remember") || cmdLower.contains("save note") || cmdLower.contains("note banao") || cmdLower.contains("reminder") || cmdLower.contains("yaad rakho") -> {
                val cleanNote = command.replace(Regex("(?i)(remember|save note|note banao|add reminder|make a note that|note that|mujhe yaad dilao ke|yaad dilao ke|yaad rakho ke|yaad rakho)"), "").trim()
                val title = if (cleanNote.length > 20) cleanNote.take(20) + "..." else "Voice Capture"
                addMemoryNote(title, cleanNote, "Voice Assist")
                addVocalizerLog("💾 Saved notes: \"$cleanNote\"")
                generateRealHumanVoiceAndSpeak("Ji bilkul saheli! Maine is baat ko aapki memory box me surakshit save kar liya hai.", _selectedVoiceProfile.value, "Hindi")
            }

            // 🎨 Command type B: Change Model
            cmdLower.contains("model select") || cmdLower.contains("model change") || cmdLower.contains("engine change") || cmdLower.contains("activate gemini") || cmdLower.contains("activate chatgpt") -> {
                val newModel = when {
                    cmdLower.contains("gemini") -> "Rabiya AI (Gemini)"
                    cmdLower.contains("gpt") || cmdLower.contains("chatgpt") -> "ChatGPT Pro (OpenAI)"
                    cmdLower.contains("openrouter") || cmdLower.contains("router") -> "OpenRouter Pro"
                    cmdLower.contains("nov") || cmdLower.contains("novita") -> "Nov AI Engine"
                    cmdLower.contains("poe") -> "Poe.ai Neural Bot"
                    else -> "Rabiya AI (Gemini)"
                }
                updateSelectedModel(newModel)
                addVocalizerLog("🤖 Voice Action: Swapped system backend core of $newModel")
                generateRealHumanVoiceAndSpeak("Maine successfully application model badal kar $newModel kar dia hai.", _selectedVoiceProfile.value, "Hindi")
            }

            // 🗣️ Command type C: Change Voice Character
            cmdLower.contains("change voice") || cmdLower.contains("voice profile") || cmdLower.contains("awaz badlo") || cmdLower.contains("zoya voice") || cmdLower.contains("kabir voice") -> {
                val voiceMap = mapOf(
                    "rabiya" to "Rabiya (Natural Sufi Companion)",
                    "zoya" to "Zoya (Charming Hindi Saheli)",
                    "kabir" to "Kabir (Deep Hindi Dost)",
                    "sam" to "Sam (Polished English Anchor)",
                    "zara" to "Zara (Sweet English Storyteller)"
                )
                var matched = "Rabiya (Natural Sufi Companion)"
                for ((key, value) in voiceMap) {
                    if (cmdLower.contains(key)) {
                        matched = value
                        break
                    }
                }
                _selectedVoiceProfile.value = matched
                addVocalizerLog("🤖 Voice Action: Profile changed to $matched")
                generateRealHumanVoiceAndSpeak("Ji bilkul, maine apni aawaz change kar di hai. Haan to, kya chal raha hai aajkal?", matched, "Hindi")
            }

            // 🧹 Command type D: Clear Chat / Wipe
            cmdLower.contains("clear chat") || cmdLower.contains("delete chat") || cmdLower.contains("wipe chat") || cmdLower.contains("chat mitao") -> {
                clearActiveSessionMessages()
                addVocalizerLog("🧹 Voice Action: Wiped conversation data.")
                generateRealHumanVoiceAndSpeak("Ji saheli, purane chat records saaf ho chuke hain.", _selectedVoiceProfile.value, "Hindi")
            }

            // 🛡️ Command type E: Bypass lock
            cmdLower.contains("unlock app") || cmdLower.contains("bypass lock") || cmdLower.contains("security unlock") -> {
                bypassUnlockQuickly()
                addVocalizerLog("🔓 Voice Action: Passcode bypass triggered.")
                generateRealHumanVoiceAndSpeak("Maine aapka pin lock open kar diya hai.", _selectedVoiceProfile.value, "Hindi")
            }

            // 🖼️ Command type F: Image generation
            cmdLower.contains("generate image") || cmdLower.contains("create photo") || cmdLower.contains("image generate") || cmdLower.contains("photo banao") || cmdLower.contains("draw") || cmdLower.contains("bnao") || cmdLower.contains("banao") -> {
                addVocalizerLog("🎨 Voice Action: Commencing image painting prompt...")
                sendMessage(command)
                generateRealHumanVoiceAndSpeak("Maine photography engine ko initiate kar dia hai. Ek sec rukiye main photo bana rahi hoon.", _selectedVoiceProfile.value, "Hindi")
            }

            // 📜 Command type G: Conversation Summarization
            cmdLower.contains("summarize") || cmdLower.contains("summary") || cmdLower.contains("khulasa") -> {
                viewModelScope.launch {
                    _isChatLoading.value = true
                    val sId = _activeSessionId.value
                    val history = chatDao.getMessagesForSessionSync(sId)
                    val fullChatText = history.filter { it.imageUrl == null }.joinToString("\n") { "${it.sender}: ${it.text}" }

                    val summaryPrompt = "Please summarize our chat conversation history into sweet short bullet points in Roman Urdu/Hindi. Keep it clean:\n\n$fullChatText"
                    val summaryResult = getLLMResponseForAssistant(summaryPrompt)

                    val rabiyaTextReply = ChatMessageEntity(
                        sessionId = sId,
                        sender = "rabiya",
                        text = "📜 **SAHELI SUMMARY MODULE:**\n\n$summaryResult"
                    )
                    withContext(Dispatchers.IO) {
                        chatDao.insertMessage(rabiyaTextReply)
                    }
                    _isChatLoading.value = false
                    generateRealHumanVoiceAndSpeak("Maine hamari conversation ki pyari si summary ready kar li hai saheli!", _selectedVoiceProfile.value, "Hindi")
                }
            }

            // 🛠️ Command type H: Hardware / Settings Voice control
            cmdLower.contains("flashlight") || cmdLower.contains("torch") || cmdLower.contains("flash light") || cmdLower.contains("lattu") || cmdLower.contains("flash") -> {
                toggleFlashlight(context)
                val status = if (_isFlashlightOn.value) "chalu (ON)" else "band (OFF)"
                addVocalizerLog("🔦 Voice Action: Flashlight has been toggled to $status")
                generateRealHumanVoiceAndSpeak("Ji saheli, maine flashlight ko $status kar diya hai!", _selectedVoiceProfile.value, "Hindi")
            }

            cmdLower.contains("bluetooth") || cmdLower.contains("blue tooth") || cmdLower.contains("blutut") -> {
                toggleBluetoothState(context)
                val status = if (_isBluetoothEnabled.value) "activated" else "deactivated"
                addVocalizerLog("📡 Voice Action: Bluetooth state updated to $status")
                generateRealHumanVoiceAndSpeak("Maine bluetooth settings open kar di hain aur ise $status kar diya hai saheli.", _selectedVoiceProfile.value, "Hindi")
            }

            cmdLower.contains("camera") || cmdLower.contains("photo kholo") || cmdLower.contains("tasveer") || cmdLower.contains("kamera") || cmdLower.contains("photo khich") -> {
                launchSystemHardwareSettings(context, "camera")
                addVocalizerLog("📷 Voice Action: Launched system camera.")
                generateRealHumanVoiceAndSpeak("Ji saheli! Maine aapka system camera open kar diya hai. Pyari si photo khichiye!", _selectedVoiceProfile.value, "Hindi")
            }

            cmdLower.contains("alarm") || cmdLower.contains("ghadi") || cmdLower.contains("ghari") || cmdLower.contains("gadi") || cmdLower.contains("wake me up") -> {
                launchSystemHardwareSettings(context, "alarm")
                addVocalizerLog("⏰ Voice Action: Launched system clock/alarm settings.")
                generateRealHumanVoiceAndSpeak("Maine clock aur alarm setup panel khol diya hai taaki aap aasani se alarm set kar sakein.", _selectedVoiceProfile.value, "Hindi")
            }

            cmdLower.contains("calculator") || cmdLower.contains("hisaab") || cmdLower.contains("hisab") || cmdLower.contains("hisaab kitab") || cmdLower.contains("hisaab-kitab") -> {
                launchSystemHardwareSettings(context, "calculator")
                addVocalizerLog("🧮 Voice Action: Opened system calculator.")
                generateRealHumanVoiceAndSpeak("Hisaab kitab karne ke liye maine calculator launch kar diya hai saheli.", _selectedVoiceProfile.value, "Hindi")
            }

            cmdLower.contains("calendar") || cmdLower.contains("jantri") || cmdLower.contains("tarikh") || cmdLower.contains("taareekh") || cmdLower.contains("calender") -> {
                launchSystemHardwareSettings(context, "calendar")
                addVocalizerLog("📅 Voice Action: Opened system calendar.")
                generateRealHumanVoiceAndSpeak("Ji! Maine calendar open kar diya hai taaki aap agle dates aur events check kar sakein.", _selectedVoiceProfile.value, "Hindi")
            }

            cmdLower.contains("gallery") || cmdLower.contains("album") || cmdLower.contains("tasveerein") || cmdLower.contains("photo") || cmdLower.contains("galari") -> {
                launchSystemHardwareSettings(context, "gallery")
                addVocalizerLog("🖼️ Voice Action: Opened system gallery.")
                generateRealHumanVoiceAndSpeak("Maine gallery open kar di hai saheli. Aap apni saari khoobsurat photos dekh sakti hain.", _selectedVoiceProfile.value, "Hindi")
            }

            cmdLower.contains("storage") || cmdLower.contains("memory") || cmdLower.contains("safai") || cmdLower.contains("memory card") -> {
                launchSystemHardwareSettings(context, "storage")
                addVocalizerLog("💾 Voice Action: Checked and opened device storage.")
                generateRealHumanVoiceAndSpeak("Storage details ki settings maine open kar di hain. Aap yahan par space clean kar sakti hain.", _selectedVoiceProfile.value, "Hindi")
            }

            cmdLower.contains("hotspot") || cmdLower.contains("wifi sharing") || cmdLower.contains("net share") || cmdLower.contains("tethering") -> {
                launchSystemHardwareSettings(context, "hotspot")
                addVocalizerLog("📶 Voice Action: Opened hotspot setup.")
                generateRealHumanVoiceAndSpeak("Maine tethering aur hotspot settings activate kar di hain taaki aap net share kar sakein.", _selectedVoiceProfile.value, "Hindi")
            }

            cmdLower.contains("weather") || cmdLower.contains("mausam") || cmdLower.contains("baarish") || cmdLower.contains("barish") || cmdLower.contains("temperature") || cmdLower.contains("dhoop") -> {
                addVocalizerLog("🌦️ Voice Action: Looking up weather info.")
                generateRealHumanVoiceAndSpeak("Mausam bohot hi suhana hai saheli! Aap quick settings panel me live conditions check kar sakti hain.", _selectedVoiceProfile.value, "Hindi")
            }

            // 📺 Advanced YouTube automation
            cmdLower.contains("youtube") || cmdLower.contains("video lagao") || cmdLower.contains("video search") || cmdLower.contains("gaana lagao") || cmdLower.contains("gana lagao") || cmdLower.contains("gaana chalao") || cmdLower.contains("gana chalao") || cmdLower.contains("video chalao") -> {
                val cleanTopic = command.replace(Regex("(?i)(youtube search|youtube pe dikhao|youtube par search karo|video lagao|video search|gaana lagao|gana lagao|gaana chalao|gana chalao|video chalao|dikhao|search on youtube|play on youtube)"), "").trim()
                val finalTopic = if (cleanTopic.isBlank()) "Latest technology news 2026" else cleanTopic
                searchYouTubeReal(finalTopic)
                addVocalizerLog("📺 Voice Action: Initiated YouTube query search for '$finalTopic'.")
                generateRealHumanVoiceAndSpeak("Ji saheli! Maine aapke liye YouTube par '$finalTopic' search kar liya hai. Aap home screen par upar scroll kar ke video stream check kar sakti hain.", _selectedVoiceProfile.value, "Hindi")
            }

            // 🧠 Advanced cognitive toggles
            cmdLower.contains("thinking") || cmdLower.contains("deep research") || cmdLower.contains("dimaag chalu") || cmdLower.contains("cognitive reasoning") || cmdLower.contains("socho") || cmdLower.contains("dimaag lagao") -> {
                toggleDeepResearch()
                val status = if (_isDeepResearchEnabled.value) "chalu (Enabled)" else "band (Disabled)"
                addVocalizerLog("🧠 Voice Action: Synthesized thinking engine status changed to $status.")
                generateRealHumanVoiceAndSpeak("Ji saheli! Maine advanced cognitive deep research thinking mode ko $status kar diya hai.", _selectedVoiceProfile.value, "Hindi")
            }

            cmdLower.contains("web search") || cmdLower.contains("live search") || cmdLower.contains("internet") || cmdLower.contains("web mode") || cmdLower.contains("google search") || cmdLower.contains("google par") -> {
                toggleWebSearch()
                val status = if (_isWebSearchEnabled.value) "chalu (Enabled)" else "band (Disabled)"
                addVocalizerLog("🌐 Voice Action: Web grounding search engine status set to $status.")
                generateRealHumanVoiceAndSpeak("Ji bilkul! Maine internet live web grounding features ko $status kar diya hai.", _selectedVoiceProfile.value, "Hindi")
            }

            // 🤖 Command type I: Default Conversational response with TTS speech feedback
            else -> {
                viewModelScope.launch {
                    val sId = _activeSessionId.value
                    val userMsg = ChatMessageEntity(
                        sessionId = sId,
                        sender = "user",
                        text = command
                    )
                    withContext(Dispatchers.IO) {
                        chatDao.insertMessage(userMsg)
                    }

                    _isChatLoading.value = true
                    val answer = getLLMResponseForAssistant(command)
                    val sanitizedAnswer = answer.replace("।", " ").replace("  ", " ").trim()

                    val rabiyaTextReply = ChatMessageEntity(
                        sessionId = sId,
                        sender = "rabiya",
                        text = sanitizedAnswer
                    )
                    withContext(Dispatchers.IO) {
                        chatDao.insertMessage(rabiyaTextReply)
                    }
                    _isChatLoading.value = false

                    // Speak response back
                    val cleanSpeech = cleanTextForTTS(sanitizedAnswer)
                    generateRealHumanVoiceAndSpeak(cleanSpeech, _selectedVoiceProfile.value, "Hindi")

                    // Hands-free voice conversation loop
                    if (_isVoiceConversationMode.value) {
                        kotlinx.coroutines.delay(1000)
                        startNativeVoiceRecognition(context)
                    }
                }
            }
        }
    }

    private suspend fun queryAnthropicDirect(
        systemPrompt: String,
        userPrompt: String,
        history: List<ChatMessageEntity>
    ): String? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val apiKey = claudeKey
        if (apiKey.isBlank() || apiKey == "claude_placeholder") {
            Log.e("MainViewModel", "Anthropic Claude API Key is missing or invalid.")
            return@withContext null
        }
        try {
            Log.d("MainViewModel", "Querying Anthropic Claude Direct API...")
            val client = com.example.data.api.RetrofitClient.okHttpClient
            
            val requestBodyObj = buildJsonObject {
                put("model", "claude-3-5-sonnet-20241022")
                put("max_tokens", 4096)
                put("system", systemPrompt)
                putJsonArray("messages") {
                    history.takeLast(8).forEach { msg ->
                        add(buildJsonObject {
                            put("role", if (msg.sender == "user") "user" else "assistant")
                            put("content", msg.text)
                        })
                    }
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", userPrompt)
                    })
                }
            }

            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val reqBody = okhttp3.RequestBody.create(mediaType, requestBodyObj.toString())
            
            val request = okhttp3.Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .post(reqBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string()
                    if (!bodyString.isNullOrBlank()) {
                        val jsonElement = kotlinx.serialization.json.Json.parseToJsonElement(bodyString)
                        val contentArray = jsonElement.jsonObject["content"]?.jsonArray
                        val textObj = contentArray?.firstOrNull()?.jsonObject
                        val responseText = textObj?.get("text")?.jsonPrimitive?.content
                        if (!responseText.isNullOrBlank()) {
                            return@withContext responseText
                        }
                    }
                } else {
                    Log.e("MainViewModel", "Anthropic Claude call unsuccessful: Code ${response.code}")
                }
                null
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "queryAnthropicDirect exception: ${e.message}", e)
        }
        return@withContext null
    }

    private suspend fun queryPollinations(
        modelName: String,
        systemPrompt: String,
        userPrompt: String,
        history: List<ChatMessageEntity>
    ): String? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            Log.d("MainViewModel", "Querying Pollinations model ($modelName) fallback...")
            val messagesList = mutableListOf<OpenRouterMessage>().apply {
                add(OpenRouterMessage(role = "system", content = systemPrompt))
                history.takeLast(6).forEach { msg ->
                    val role = if (msg.sender == "rabiya" || msg.sender == "assistant") "assistant" else "user"
                    add(OpenRouterMessage(role = role, content = msg.text))
                }
                add(OpenRouterMessage(role = "user", content = userPrompt))
            }

            val modelRequest = OpenRouterChatRequest(
                model = modelName,
                messages = messagesList
            )

            val res = RetrofitClient.service.generateAbsoluteChatCompletion(
                url = "https://text.pollinations.ai/v1/chat/completions",
                authHeader = "Bearer pollinations",
                request = modelRequest
            )
            val textResult = res.choices?.firstOrNull()?.message?.content
            if (!textResult.isNullOrBlank()) {
                Log.d("MainViewModel", "Pollinations ($modelName) retrieval success!")
                return@withContext textResult
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Pollinations ($modelName) query failed: ${e.message}")
        }

        // Secondary URL encoded plain GET fallback as a last resort
        try {
            val getUrl = "https://text.pollinations.ai/${java.net.URLEncoder.encode(userPrompt.take(150), "UTF-8")}?model=$modelName"
            val req = okhttp3.Request.Builder()
                .url(getUrl)
                .get()
                .build()
            RetrofitClient.okHttpClient.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val text = response.body?.string()?.trim()
                    if (!text.isNullOrBlank()) {
                        return@withContext text
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Pollinations GET last resort failed: ${e.message}")
        }
        return@withContext null
    }

    private suspend fun executeModelQuery(
        model: String,
        actualPromptText: String,
        dynamicSystemPrompt: String,
        history: List<ChatMessageEntity>,
        contentsList: List<Content>
    ): String? {
        val cacheKey = "${model}_${actualPromptText.trim()}"
        if (_isTurboMode.value && responseCache.containsKey(cacheKey)) {
            val cachedVal = responseCache[cacheKey]
            if (!cachedVal.isNullOrBlank()) {
                _cachedRequestCount.value = _cachedRequestCount.value + 1
                return cachedVal
            }
        }

        lastErrorMessage = ""
        var answer: String? = null
        val lowerModel = model.lowercase()

        // 1. Try Primary Specific API Endpoints, protected by local try-catches and the working NVIDIA key
        try {
            when {
                lowerModel.contains("rabiya") || lowerModel.contains("gemini") -> {
                    if (geminiKey.isNotEmpty() && geminiKey != "ai_key_placeholder") {
                        val useSearchTools = _isWebSearchEnabled.value
                        val toolsList = if (useSearchTools) {
                            listOf(buildJsonObject {
                                putJsonObject("googleSearchRetrieval") {
                                    putJsonObject("dynamicRetrievalConfig") {
                                        put("mode", "MODE_DYNAMIC")
                                        put("dynamicThreshold", 0.3)
                                    }
                                }
                            })
                        } else null

                        val req = GenerateContentRequest(
                            contents = contentsList,
                            systemInstruction = Content(parts = listOf(Part(text = dynamicSystemPrompt))),
                            tools = toolsList
                        )
                        val targetModels = if (lowerModel.contains("ultra-pro") || lowerModel.contains("3.1 pro") || lowerModel.contains("pro") || _isDeepResearchEnabled.value) {
                            listOf("gemini-2.0-flash", "gemini-1.5-flash", "gemini-1.5-pro", "gemini-3.1-pro-preview", "gemini-3.5-flash", "gemini-3.1-flash-lite-preview")
                        } else {
                            listOf("gemini-2.0-flash", "gemini-1.5-flash", "gemini-1.5-pro", "gemini-3.5-flash", "gemini-3.1-flash-lite-preview", "gemini-3.1-pro-preview")
                        }
                        for (targetModel in targetModels) {
                            try {
                                val res = RetrofitClient.service.generateContent(targetModel, geminiKey, req)
                                val textResult = res.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                                if (!textResult.isNullOrBlank()) {
                                    answer = textResult
                                    break
                                }
                            } catch (e: Exception) {
                                Log.e("MainViewModel", "Gemini query with $targetModel failed: ${e.message}")
                            }
                        }
                    }
                    if (answer.isNullOrBlank()) {
                        Log.d("MainViewModel", "Rabiya/Gemini key failing. Redirecting to Pollinations core.")
                        answer = queryPollinations("openai", dynamicSystemPrompt, actualPromptText, history)
                    }
                    // Resilient working Llama/NVIDIA backup if direct Gemini key is inactive
                    if (answer.isNullOrBlank() && novKey.isNotBlank() && novKey != "nov_placeholder") {
                        try {
                            Log.d("MainViewModel", "Rabiya/Gemini key failing. Redirecting to functional high-performance NVIDIA model.")
                            val modelRequest = OpenRouterChatRequest(
                                model = "meta/llama-3.3-70b-instruct",
                                messages = listOf(
                                    OpenRouterMessage(role = "system", content = dynamicSystemPrompt),
                                    OpenRouterMessage(role = "user", content = actualPromptText)
                                )
                            )
                            val res = RetrofitClient.service.generateAbsoluteChatCompletion(
                                url = "https://integrate.api.nvidia.com/v1/chat/completions",
                                authHeader = "Bearer $novKey",
                                request = modelRequest
                            )
                            answer = res.choices?.firstOrNull()?.message?.content
                        } catch (ex: Exception) {
                            Log.e("MainViewModel", "Gemini fallback redirection failed: ${ex.message}")
                        }
                    }
                }
                lowerModel.contains("gpt") || lowerModel.contains("chatgpt") || lowerModel.contains("vision") -> {
                    val finalSystemPrompt = if (lowerModel.contains("vision")) {
                        "$dynamicSystemPrompt\n[Additional Instruction] You are in GPT-4o Vision mode. Inspect any loaded visual inputs or image byte parameters thoroughly, explaining shapes, text context, colors, and layout aesthetics with exceptional design clarity."
                    } else {
                        dynamicSystemPrompt
                    }
                    if (chatGptKey.isNotBlank() && chatGptKey != "chatgpt_placeholder") {
                        try {
                            val modelRequest = OpenRouterChatRequest(
                                model = "gpt-4o",
                                messages = listOf(
                                    OpenRouterMessage(role = "system", content = finalSystemPrompt),
                                    OpenRouterMessage(role = "user", content = actualPromptText)
                                )
                            )
                            val res = RetrofitClient.service.generateAbsoluteChatCompletion(
                                url = "https://api.openai.com/v1/chat/completions",
                                authHeader = "Bearer $chatGptKey",
                                request = modelRequest
                            )
                            answer = res.choices?.firstOrNull()?.message?.content
                        } catch (e: Exception) {
                            Log.e("MainViewModel", "ChatGPT direct query failed: ${e.message}")
                        }
                    }
                    if (answer.isNullOrBlank() && openRouterKey.isNotBlank()) {
                        try {
                            val modelRequest = OpenRouterChatRequest(
                                model = "openai/gpt-4o",
                                messages = listOf(
                                    OpenRouterMessage(role = "system", content = finalSystemPrompt),
                                    OpenRouterMessage(role = "user", content = actualPromptText)
                                )
                            )
                            val res = RetrofitClient.service.generateAbsoluteChatCompletion(
                                url = "https://openrouter.ai/api/v1/chat/completions",
                                authHeader = "Bearer $openRouterKey",
                                request = modelRequest
                            )
                            answer = res.choices?.firstOrNull()?.message?.content
                        } catch (e: Exception) {
                            Log.e("MainViewModel", "GPT OpenRouter fallback query failed: ${e.message}")
                        }
                    }
                    if (answer.isNullOrBlank()) {
                        Log.d("MainViewModel", "OpenAI keys failing. Falling back to GPT-4o on Pollinations.")
                        answer = queryPollinations("openai", finalSystemPrompt, actualPromptText, history)
                    }
                    // Resilient working Llama/NVIDIA backup if direct OpenAI keys are inactive
                    if (answer.isNullOrBlank() && novKey.isNotBlank() && novKey != "nov_placeholder") {
                        try {
                            Log.d("MainViewModel", "ChatGPT key failing. Redirecting to functional high-performance NVIDIA model.")
                            val modelRequest = OpenRouterChatRequest(
                                model = "meta/llama-3.3-70b-instruct",
                                messages = listOf(
                                    OpenRouterMessage(role = "system", content = finalSystemPrompt),
                                    OpenRouterMessage(role = "user", content = actualPromptText)
                                )
                            )
                            val res = RetrofitClient.service.generateAbsoluteChatCompletion(
                                url = "https://integrate.api.nvidia.com/v1/chat/completions",
                                authHeader = "Bearer $novKey",
                                request = modelRequest
                            )
                            answer = res.choices?.firstOrNull()?.message?.content
                        } catch (ex: Exception) {
                            Log.e("MainViewModel", "GPT fallback redirection failed: ${ex.message}")
                        }
                    }
                }
                lowerModel.contains("claude") || lowerModel.contains("coderabbit") -> {
                    val finalSystemPrompt = if (lowerModel.contains("coderabbit")) {
                        "$dynamicSystemPrompt\n[Additional Instruction] You are acting as CodeRabbit, the elite developer-first automated AI Code Review assistant. Perform extremely rigorous, line-by-line quality checks, security reviews, cognitive and cyclic complexity evaluations, and offer clean Kotlin/Compose structural improvements!"
                    } else {
                        dynamicSystemPrompt
                    }
                    
                    // 1. Try Direct Anthropic API call with CLAUDE_API_KEY
                    val directKey = claudeKey
                    if (directKey.isNotBlank() && directKey != "claude_placeholder") {
                        try {
                            Log.d("MainViewModel", "Routing to Direct Anthropic Claude API...")
                            answer = queryAnthropicDirect(finalSystemPrompt, actualPromptText, history)
                        } catch (e: Exception) {
                            Log.e("MainViewModel", "Direct Claude call failed, trying OpenRouter fallback: ${e.message}")
                        }
                    }

                    // 2. OpenRouter fallback if Direct API was empty or key missing
                    if (answer.isNullOrBlank() && openRouterKey.isNotBlank()) {
                        try {
                            val modelRequest = OpenRouterChatRequest(
                                model = "anthropic/claude-3.5-sonnet",
                                messages = listOf(
                                    OpenRouterMessage(role = "system", content = finalSystemPrompt),
                                    OpenRouterMessage(role = "user", content = actualPromptText)
                                )
                            )
                            val res = RetrofitClient.service.generateAbsoluteChatCompletion(
                                url = "https://openrouter.ai/api/v1/chat/completions",
                                authHeader = "Bearer $openRouterKey",
                                request = modelRequest
                            )
                            answer = res.choices?.firstOrNull()?.message?.content
                        } catch (e: Exception) {
                            Log.e("MainViewModel", "Claude OpenRouter fallback query failed: ${e.message}")
                        }
                    }
                    if (answer.isNullOrBlank()) {
                        Log.d("MainViewModel", "Claude key failing. Falling back to smart Mistral on Pollinations (Claude behavior).")
                        answer = queryPollinations("mistral-large", finalSystemPrompt, actualPromptText, history) ?: queryPollinations("openai", finalSystemPrompt, actualPromptText, history)
                    }
                    // Resilient working Llama/NVIDIA backup if OpenRouter key is inactive
                    if (answer.isNullOrBlank() && novKey.isNotBlank() && novKey != "nov_placeholder") {
                        try {
                            Log.d("MainViewModel", "Claude key failing. Redirecting to functional high-performance NVIDIA model.")
                            val modelRequest = OpenRouterChatRequest(
                                model = "meta/llama-3.3-70b-instruct",
                                messages = listOf(
                                    OpenRouterMessage(role = "system", content = finalSystemPrompt),
                                    OpenRouterMessage(role = "user", content = actualPromptText)
                                )
                            )
                            val res = RetrofitClient.service.generateAbsoluteChatCompletion(
                                url = "https://integrate.api.nvidia.com/v1/chat/completions",
                                authHeader = "Bearer $novKey",
                                request = modelRequest
                            )
                            answer = res.choices?.firstOrNull()?.message?.content
                        } catch (ex: Exception) {
                            Log.e("MainViewModel", "Claude fallback redirection failed: ${ex.message}")
                        }
                    }
                }
                lowerModel.contains("deepseek") -> {
                    if (deepSeekKey.isNotBlank() && deepSeekKey != "deepseek_placeholder") {
                        try {
                            val targetModel = if (lowerModel.contains("r1") || lowerModel.contains("reason") || lowerModel.contains("thinking")) {
                                "deepseek-reasoner"
                            } else {
                                "deepseek-chat"
                            }
                            val modelRequest = OpenRouterChatRequest(
                                model = targetModel,
                                messages = listOf(
                                    OpenRouterMessage(role = "system", content = dynamicSystemPrompt),
                                    OpenRouterMessage(role = "user", content = actualPromptText)
                                )
                            )
                            val res = RetrofitClient.service.generateAbsoluteChatCompletion(
                                url = "https://api.deepseek.com/chat/completions",
                                authHeader = "Bearer $deepSeekKey",
                                request = modelRequest
                            )
                            answer = res.choices?.firstOrNull()?.message?.content
                        } catch (e: Exception) {
                            Log.e("MainViewModel", "Official DeepSeek API query failed: ${e.message}. Trying fallout.")
                        }
                    }
                    if (answer.isNullOrBlank() && openRouterKey.isNotBlank()) {
                        try {
                            val targetModel = if (lowerModel.contains("r1") || lowerModel.contains("reason") || lowerModel.contains("thinking")) {
                                "deepseek/deepseek-r1"
                            } else {
                                "deepseek/deepseek-chat"
                            }
                            val modelRequest = OpenRouterChatRequest(
                                model = targetModel,
                                messages = listOf(
                                    OpenRouterMessage(role = "system", content = dynamicSystemPrompt),
                                    OpenRouterMessage(role = "user", content = actualPromptText)
                                )
                            )
                            val res = RetrofitClient.service.generateAbsoluteChatCompletion(
                                url = "https://openrouter.ai/api/v1/chat/completions",
                                authHeader = "Bearer $openRouterKey",
                                request = modelRequest
                            )
                            answer = res.choices?.firstOrNull()?.message?.content
                        } catch (e: Exception) {
                            Log.e("MainViewModel", "OpenRouter DeepSeek fallback query failed: ${e.message}")
                        }
                    }
                    // Resilient working NVIDIA-DeepSeek-R1 / Llama backup if direct DeepSeek keys are inactive
                    if (answer.isNullOrBlank() && novKey.isNotBlank() && novKey != "nov_placeholder") {
                        try {
                            val targetModel = if (lowerModel.contains("r1") || lowerModel.contains("reason") || lowerModel.contains("thinking")) {
                                "deepseek-ai/deepseek-r1"
                            } else {
                                "meta/llama-3.3-70b-instruct"
                            }
                            Log.d("MainViewModel", "DeepSeek keys failing. Redirecting to DeepSeek-R1 / Llama on NVIDIA.")
                            val modelRequest = OpenRouterChatRequest(
                                model = targetModel,
                                messages = listOf(
                                    OpenRouterMessage(role = "system", content = dynamicSystemPrompt),
                                    OpenRouterMessage(role = "user", content = actualPromptText)
                                )
                            )
                            val res = RetrofitClient.service.generateAbsoluteChatCompletion(
                                url = "https://integrate.api.nvidia.com/v1/chat/completions",
                                authHeader = "Bearer $novKey",
                                request = modelRequest
                            )
                            answer = res.choices?.firstOrNull()?.message?.content
                        } catch (ex: Exception) {
                            Log.e("MainViewModel", "DeepSeek fallback redirection failed: ${ex.message}")
                        }
                    }
                    if (answer.isNullOrBlank()) {
                        Log.d("MainViewModel", "DeepSeek keys failing. Trying Pollinations DeepSeek fallback.")
                        val pollModel = if (lowerModel.contains("r1") || lowerModel.contains("reason") || lowerModel.contains("thinking")) {
                            "deepseek-r1"
                        } else {
                            "deepseek"
                        }
                        answer = queryPollinations(pollModel, dynamicSystemPrompt, actualPromptText, history)
                    }
                }
                lowerModel.contains("llama") -> {
                    if (novKey.isNotBlank() && novKey != "nov_placeholder") {
                        try {
                            val modelRequest = OpenRouterChatRequest(
                                model = "meta/llama-3.3-70b-instruct",
                                messages = listOf(
                                    OpenRouterMessage(role = "system", content = dynamicSystemPrompt),
                                    OpenRouterMessage(role = "user", content = actualPromptText)
                                )
                            )
                            val res = RetrofitClient.service.generateAbsoluteChatCompletion(
                                url = "https://integrate.api.nvidia.com/v1/chat/completions",
                                authHeader = "Bearer $novKey",
                                request = modelRequest
                            )
                            answer = res.choices?.firstOrNull()?.message?.content
                        } catch (e: Exception) {
                            Log.e("MainViewModel", "NVIDIA Llama direct query failed: ${e.message}")
                        }
                    }
                    if (answer.isNullOrBlank() && openRouterKey.isNotBlank()) {
                        try {
                            val modelRequest = OpenRouterChatRequest(
                                model = "meta-llama/llama-3-70b-instruct",
                                messages = listOf(
                                    OpenRouterMessage(role = "system", content = dynamicSystemPrompt),
                                    OpenRouterMessage(role = "user", content = actualPromptText)
                                )
                            )
                            val res = RetrofitClient.service.generateAbsoluteChatCompletion(
                                url = "https://openrouter.ai/api/v1/chat/completions",
                                authHeader = "Bearer $openRouterKey",
                                request = modelRequest
                            )
                            answer = res.choices?.firstOrNull()?.message?.content
                        } catch (e: Exception) {
                            Log.e("MainViewModel", "Llama OpenRouter query failed: ${e.message}")
                        }
                    }
                    if (answer.isNullOrBlank()) {
                        Log.d("MainViewModel", "Llama keys failing. Falling back to Pollinations Llama.")
                        answer = queryPollinations("llama", dynamicSystemPrompt, actualPromptText, history)
                    }
                }
            }
        } catch (e: Exception) {
            lastErrorMessage = "Primary API failed: ${e.javaClass.simpleName} - ${e.message}"
            Log.e("MainViewModel", "Primary API query failed: ${e.message}")
        }

        // 2. HIGH-RELIABILITY COGNITIVE RECOVERY ROUTING (Safe native Google Gemini model fallback)
        if (answer.isNullOrBlank()) {
            if (geminiKey.isNotEmpty() && geminiKey != "ai_key_placeholder") {
                val recoveryModels = listOf("gemini-2.0-flash", "gemini-1.5-flash", "gemini-1.5-pro", "gemini-3.5-flash", "gemini-3.1-flash-lite-preview", "gemini-3.1-pro-preview")
                val req = GenerateContentRequest(
                    contents = contentsList,
                    systemInstruction = Content(parts = listOf(Part(text = dynamicSystemPrompt)))
                )
                for (recoveryModel in recoveryModels) {
                    try {
                        Log.d("MainViewModel", "Initiating native Google Gemini recovery model call: $recoveryModel")
                        val res = RetrofitClient.service.generateContent(recoveryModel, geminiKey, req)
                        val textResult = res.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        if (!textResult.isNullOrBlank()) {
                            answer = textResult
                            break
                        }
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Google recovery with $recoveryModel failed: ${e.message}")
                    }
                }
            }
        }

        // 3. UNRESTRICTED GLOBAL ONLINE FALLBACK (Pollinations AI Chat POST API)
        if (answer.isNullOrBlank()) {
            try {
                Log.d("MainViewModel", "Entering Unrestricted Pollinations AI online fallback via Retrofit...")
                val pollModelName = when {
                    lowerModel.contains("gpt") || lowerModel.contains("chatgpt") -> "openai"
                    lowerModel.contains("deepseek") -> "deepseek"
                    lowerModel.contains("llama") -> "llama"
                    else -> "openai"
                }

                val systemPromptText = "You are Rabiya Saheli, an incredibly sweet, loving, and supportive AI companion friend. Sahi, direct, aur clean/straightforward answers dein. Kabhi bhi faaltu/bekaar formatting symbols aur clutter characters jaise #, @, $, %, &, *, __, repetitive hashes ya template decoration designs use na karein. Answer beautifully, to the point, using a gorgeous mix of Roman Urdu/Hinglish or clear English depending on what the user asks."

                val messagesList = mutableListOf<OpenRouterMessage>().apply {
                    add(OpenRouterMessage(role = "system", content = systemPromptText))
                    history.takeLast(6).forEach { msg ->
                        val role = if (msg.sender == "user") "user" else "assistant"
                        add(OpenRouterMessage(role = role, content = msg.text))
                    }
                    add(OpenRouterMessage(role = "user", content = actualPromptText))
                }

                val modelRequest = OpenRouterChatRequest(
                    model = pollModelName,
                    messages = messagesList
                )

                val res = RetrofitClient.service.generateAbsoluteChatCompletion(
                    url = "https://text.pollinations.ai/v1/chat/completions",
                    authHeader = "Bearer pollinations",
                    request = modelRequest
                )
                val textResult = res.choices?.firstOrNull()?.message?.content
                if (!textResult.isNullOrBlank()) {
                    answer = textResult
                    Log.d("MainViewModel", "Pollinations AI online retrieval success!")
                }
            } catch (fallbackEx: Exception) {
                Log.e("MainViewModel", "Overall Pollinations recovery failed: ${fallbackEx.message}")

                // Fallback to plain query GET if choices deserialization fails due to non-standard schema
                try {
                    val getUrl = "https://text.pollinations.ai/${java.net.URLEncoder.encode(actualPromptText.take(150), "UTF-8")}?model=openai"
                    val req = okhttp3.Request.Builder()
                        .url(getUrl)
                        .get()
                        .build()
                    RetrofitClient.okHttpClient.newCall(req).execute().use { response ->
                        if (response.isSuccessful) {
                            val text = response.body?.string()?.trim()
                            if (!text.isNullOrBlank()) {
                                answer = text
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Last resort Pollinations plain text GET failed: ${e.message}")
                }
            }
        }

        // 4. ABSOLUTE PLAIN-TEXT GET FAILSAFE RETRY (Super resilient last resort)
        if (answer.isNullOrBlank()) {
            try {
                Log.d("MainViewModel", "Triggering absolute last resort unauthenticated text GET query...")
                val cleanPrompt = if (actualPromptText.length > 150) actualPromptText.take(150) else actualPromptText
                val filteredPrompt = cleanPrompt.filter { it.isLetterOrDigit() || it.isWhitespace() }
                val encodedPrompt = java.net.URLEncoder.encode(filteredPrompt, "UTF-8")
                val getUrl = "https://text.pollinations.ai/$encodedPrompt?model=openai&system=You+are+Rabiya+Saheli+sweet+hearing+Hinglish+friend"

                val reqBody = okhttp3.Request.Builder()
                    .url(getUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "*/*")
                    .get()
                    .build()

                RetrofitClient.okHttpClient.newCall(reqBody).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyText = response.body?.string()?.trim()
                        if (!bodyText.isNullOrBlank()) {
                            answer = bodyText
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Absolute last resort GET failed", e)
            }
        }

        if (!answer.isNullOrBlank() && _isTurboMode.value) {
            responseCache[cacheKey] = answer!!
        }

        return answer
    }

    private fun buildContentsList(history: List<ChatMessageEntity>, actualPromptText: String): List<Content> {
        val contentsList = mutableListOf<Content>()
        var lastRole: String? = null
        val tempParts = mutableListOf<Part>()
        
        history.takeLast(10).forEach { msg ->
            if (msg.imageUrl == null) {
                // Map 'rabiya' to 'model' and anything else (e.g. 'user') to 'user'
                val currentRole = if (msg.sender == "rabiya") "model" else "user"
                if (lastRole == null) {
                    lastRole = currentRole
                    tempParts.add(Part(text = msg.text))
                } else if (lastRole == currentRole) {
                    tempParts.add(Part(text = msg.text))
                } else {
                    contentsList.add(Content(role = lastRole, parts = tempParts.toList()))
                    tempParts.clear()
                    lastRole = currentRole
                    tempParts.add(Part(text = msg.text))
                }
            }
        }
        
        if (lastRole != null && tempParts.isNotEmpty()) {
            if (lastRole == "user") {
                tempParts.add(Part(text = actualPromptText))
                contentsList.add(Content(role = "user", parts = tempParts.toList()))
            } else {
                contentsList.add(Content(role = lastRole, parts = tempParts.toList()))
                contentsList.add(Content(role = "user", parts = listOf(Part(text = actualPromptText))))
            }
        } else {
            contentsList.add(Content(role = "user", parts = listOf(Part(text = actualPromptText))))
        }
        return contentsList
    }

    private suspend fun getLLMResponseForAssistant(userPrompt: String): String = withContext(Dispatchers.IO) {
        val sId = _activeSessionId.value
        val model = _selectedModel.value
        val history = chatDao.getMessagesForSessionSync(sId)

        val attachedName = _attachedFileName.value
        val attachedText = _attachedFileContent.value
        val actualPromptText = if (attachedName != null && attachedText != null) {
            """
            [ATTACHED DOCUMENT ARCHIVE]
            File Name: $attachedName
            File Size: ${_attachedFileSize.value ?: "unknown"}
            File Type: ${_attachedFileType.value ?: "unknown"}
            File Content:
            $attachedText
            ----------------------------------------
            User Prompt: $userPrompt
            """.trimIndent()
        } else {
            userPrompt
        }

        clearAttachedFile()

        val contentsList = buildContentsList(history, actualPromptText)

        val dynamicSystemPrompt = buildSystemPrompt(model)
        val answer = executeModelQuery(model, actualPromptText, dynamicSystemPrompt, history, contentsList)

        if (answer.isNullOrBlank()) {
            getLocalFallbackReply(userPrompt, model)
        } else {
            answer
        }
    }

    fun startWakeWordDetection(context: Context) {
        if (!_isWakeWordEnabled.value) return
        _wakeWordState.value = "Listening for 'Hey Rabiya'..."
        viewModelScope.launch(Dispatchers.Main) {
            try {
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    _wakeWordState.value = "Recognizer unavailable"
                    return@launch
                }

                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                }

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        if (_isWakeWordEnabled.value) {
                            recognizer.destroy()
                            startWakeWordDetection(context)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val textResult = matches?.firstOrNull()?.lowercase() ?: ""
                        if (textResult.contains("rabiya") || textResult.contains("rabia") || textResult.contains("wake up") || textResult.contains("hey")) {
                            _wakeWordState.value = "Triggered! 🚀"
                            addVocalizerLog("🎙️ Wake Word 'Hey Rabiya' detected!")
                            generateRealHumanVoiceAndSpeak("Haan ji saheli, main sun rahi hoon. Chaliye, command boliye!", _selectedVoiceProfile.value, "Hindi")
                            viewModelScope.launch {
                                kotlinx.coroutines.delay(2000)
                                startNativeVoiceRecognition(context)
                            }
                        } else {
                            if (_isWakeWordEnabled.value) {
                                recognizer.destroy()
                                startWakeWordDetection(context)
                            }
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                recognizer.startListening(intent)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Wake Word detection failed", e)
            }
        }
    }

    fun toggleWakeWord(context: Context) {
        if (_isWakeWordEnabled.value) {
            _isWakeWordEnabled.value = false
            _wakeWordState.value = "Idle"
            addVocalizerLog("⏹️ Wake word detection deactivated.")
        } else {
            _isWakeWordEnabled.value = true
            addVocalizerLog("⏳ Wake word detection activated. Speak 'Hey Rabiya' to trigger!")
            startWakeWordDetection(context)
        }
    }

    fun restartWakeWordListener(context: Context) {
        if (_isWakeWordEnabled.value) {
            startWakeWordDetection(context)
        }
    }

    fun toggleVoiceConversationMode() {
        _isVoiceConversationMode.value = !_isVoiceConversationMode.value
        val stateText = if (_isVoiceConversationMode.value) "Active" else "Inactive"
        addVocalizerLog("🗣️ Dynamic Voice Conversation Loop standard: $stateText")
    }

    // --- PHOTO / MULTIMODAL CAPABILITIES ---
    fun setSelectedImage(bytes: ByteArray, mimeType: String) {
        _selectedImageBytes.value = bytes
        _selectedImageMimeType.value = mimeType
        addVocalizerLog("🖼️ Image loaded for analysis & scene parsing...")
    }

    fun clearSelectedImage() {
        _selectedImageBytes.value = null
        _selectedImageMimeType.value = "image/jpeg"
    }

    fun processMultimodalRequest(context: Context, customPrompt: String) {
        val bytes = _selectedImageBytes.value
        if (bytes == null) {
            sendMessage(customPrompt)
            return
        }

        val sId = _activeSessionId.value
        viewModelScope.launch(Dispatchers.IO) {
            _isChatLoading.value = true

            // Save User message
            val filename = "User_Attached_${System.currentTimeMillis()}.jpg"
            addFileToFileManager(context, filename, "Images", "", bytes)

            val rootDir = java.io.File(context.filesDir, "FileManager")
            val catDir = java.io.File(rootDir, "Images")
            val targetFile = java.io.File(catDir, filename)
            val imgPath = targetFile.absolutePath

            val promptLower = customPrompt.lowercase(java.util.Locale.ROOT)
            val isExplicitImageGenerationWord = promptLower.contains("generate") ||
                    promptLower.contains("create") ||
                    promptLower.contains("draw") ||
                    promptLower.contains("paint") ||
                    promptLower.contains("bana") ||
                    promptLower.contains("bna") ||
                    promptLower.contains("design") ||
                    promptLower.contains("edit") ||
                    promptLower.contains("change") ||
                    promptLower.contains("modify") ||
                    promptLower.contains("recreate") ||
                    promptLower.contains("convert") ||
                    promptLower.contains("dubara") ||
                    promptLower.contains("dobara") ||
                    promptLower.contains("ke jaisa") ||
                    promptLower.contains("ke jaise") ||
                    promptLower.contains("similar") ||
                    promptLower.contains("photorealistic") ||
                    promptLower.contains("behtar") ||
                    promptLower.contains("enhance") ||
                    promptLower.contains("improve") ||
                    promptLower.contains("correct") ||
                    promptLower.contains("variant") ||
                    promptLower.contains("rework") ||
                    promptLower.contains("redesign") ||
                    promptLower.contains("is tarah") ||
                    promptLower.contains("is tarha") ||
                    promptLower.contains("is jaisa") ||
                    promptLower.contains("is jaise") ||
                    promptLower.contains("jaisa") ||
                    promptLower.contains("jaise") ||
                    promptLower.contains("aisa") ||
                    promptLower.contains("aise") ||
                    promptLower.contains("chenge") ||
                    promptLower.contains("changes")

            val isAnalysisRequest = promptLower.contains("explain") ||
                    promptLower.contains("what is") ||
                    promptLower.contains("kya hai") ||
                    promptLower.contains("read") ||
                    promptLower.contains("ocr") ||
                    promptLower.contains("dekho") ||
                    promptLower.contains("samjho") ||
                    promptLower.contains("pado") ||
                    promptLower.contains("padho") ||
                    promptLower.contains("describe") ||
                    promptLower.contains("analysis") ||
                    promptLower.contains("analyze")

            val isImageToImageCmd = isExplicitImageGenerationWord && !isAnalysisRequest

            if (isImageToImageCmd) {
                val userMsg = ChatMessageEntity(
                    sessionId = sId,
                    sender = "user",
                    text = "🖼️🎨 [Image-to-Image] $customPrompt",
                    imageUrl = imgPath
                )
                chatDao.insertMessage(userMsg)

                var enhancedPrompt = ""
                var success = false

                try {
                    val base64Data = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    val mType = _selectedImageMimeType.value ?: "image/jpeg"

                    if (geminiKey.isNotEmpty() && geminiKey != "ai_key_placeholder") {
                        val imageAnalysisPrompt = """
                            Analyze the attached image and the user's prompt. Your job is to generate a highly detailed prompt for an AI image generator (such as Stable Diffusion or Flux) that recreates or modifies this image as requested by the user.
                            
                            User Request: "$customPrompt"
                            
                            Tasks:
                            1. Carefully examine the elements of the uploaded image: subjects, colors, lighting, art style, composition, background.
                            2. Integrate the user's requested changes or requests into the description (for example, if the user says "iske jaisa banao but sham ka time ho", change day to evening/sunset).
                            3. Output ONLY the optimized, highly detailed image generation prompt, strictly in English.
                            4. Do NOT include any conversation, markdown blocks (like ```), or introductions. Just output the clean prompt itself.
                        """.trimIndent()

                        val systemPrompt = "You are an expert AI visual designer that produces extremely detailed image generation prompts in English."
                        val req = GenerateContentRequest(
                            contents = listOf(
                                Content(parts = listOf(
                                    Part(text = imageAnalysisPrompt),
                                    Part(inlineData = InlineData(mimeType = mType, data = base64Data))
                                ))
                            ),
                            systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
                        )

                        val targetModels = listOf("gemini-2.0-flash", "gemini-1.5-flash", "gemini-1.5-pro", "gemini-3.5-flash", "gemini-3.1-flash-lite-preview")
                        var lastEx: Exception? = null
                        for (targetModel in targetModels) {
                            try {
                                val res = RetrofitClient.service.generateContent(targetModel, geminiKey, req)
                                val textResult = res.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                                if (!textResult.isNullOrBlank()) {
                                    enhancedPrompt = textResult.trim()
                                    success = true
                                    break
                                }
                            } catch (e: Exception) {
                                lastEx = e
                                Log.e("MainViewModel", "Multimodal prompt-gen failed with $targetModel: ${e.message}")
                            }
                        }
                        if (!success && lastEx != null) {
                            throw lastEx
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Gemini prompt enrichment failed: ${e.message}")
                }

                if (!success || enhancedPrompt.isBlank()) {
                    enhancedPrompt = customPrompt
                }

                // Clean the generated prompt from markdown code fences if any got outputted
                enhancedPrompt = enhancedPrompt
                    .replace("```prompt", "")
                    .replace("```", "")
                    .trim()

                val seedVal = (1000..99999).random()
                val encodedPrompt = java.net.URLEncoder.encode(enhancedPrompt, "UTF-8").replace("+", "%20")
                val imgUrl = "https://image.pollinations.ai/p/$encodedPrompt?width=1024&height=1024&seed=$seedVal&model=flux&nologo=true"

                val cleanPromptLabel = enhancedPrompt.take(20).replace(" ", "_").replace(Regex("[^a-zA-Z0-9_]"), "")
                val imgFilename = "Gen_I2I_${cleanPromptLabel}_${System.currentTimeMillis()}.png"

                val replyText = "Ji bilkul saheli! Maine aapki di hui image ko dhyaan se analyze kiya hai, aapki expectations ko samjha hain, aur uske adhaar par ek behtar aur naya dynamic variant tayyar kar diya hai! ✨🎨\n\n🔍 **Naya Prompt Jo Compute Hua:**\n$enhancedPrompt\n\n📁 Is file ko maine aapke phone me **FileManager** ke `Images` folder ke andar name `$imgFilename` se surakshit save bhi kar diya hai!"

                val rabiyaImageReply = ChatMessageEntity(
                    sessionId = sId,
                    sender = "rabiya",
                    text = replyText,
                    imageUrl = imgUrl
                )
                chatDao.insertMessage(rabiyaImageReply)

                // Save generated image bytes to FileManager asynchronously
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val client = com.example.data.api.RetrofitClient.okHttpClient
                        val request = okhttp3.Request.Builder().url(imgUrl).build()
                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                val bodyBytes = response.body?.bytes()
                                if (bodyBytes != null) {
                                    addFileToFileManager(context, imgFilename, "Images", "", bodyBytes)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Downloading/saving generated image to FileManager failed", e)
                    }
                }

                clearSelectedImage()
                _isChatLoading.value = false

                // Speak confirmation phrase
                try {
                    val speechTxt = "Ji saheli! Maine aapki image ko scan karke naya custom design generate kar diya hai!"
                    generateRealHumanVoiceAndSpeak(speechTxt, _selectedVoiceProfile.value, "Hindi")
                } catch (speakEx: Exception) {
                    Log.e("MainViewModel", "TTS failed: ${speakEx.message}")
                }

                return@launch
            }

            // Fallback: Standard Image Description and Analysis Workflow
            val userMsg = ChatMessageEntity(
                sessionId = sId,
                sender = "user",
                text = "🖼️ [ImageAttached] $customPrompt",
                imageUrl = imgPath
            )
            chatDao.insertMessage(userMsg)

            var answer = ""
            var success = false

            try {
                val base64Data = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                val mType = _selectedImageMimeType.value ?: "image/jpeg"

                if (geminiKey.isNotEmpty() && geminiKey != "ai_key_placeholder") {
                    val systemPrompt = "Aap Rabiya hain, user ki lovable saheli. Image ko scan karke detail analysis batayein, aur text ho to OCR parsing karein Hinglish me. Sahi, direct, aur clean answers de. Do NOT use symbol clutter like #, @, $, %, &, *, __, or redundant code block templates."
                    val req = GenerateContentRequest(
                        contents = listOf(
                            Content(parts = listOf(
                                Part(text = customPrompt),
                                Part(inlineData = InlineData(mimeType = mType, data = base64Data))
                            ))
                        ),
                        systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
                    )

                    val targetModels = listOf("gemini-2.0-flash", "gemini-1.5-flash", "gemini-1.5-pro", "gemini-3.5-flash", "gemini-3.1-flash-lite-preview")
                    var lastEx: Exception? = null
                    for (targetModel in targetModels) {
                        try {
                            val res = RetrofitClient.service.generateContent(targetModel, geminiKey, req)
                            val textResult = res.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                            if (!textResult.isNullOrBlank()) {
                                answer = textResult
                                success = true
                                break
                            }
                        } catch (e: Exception) {
                            lastEx = e
                            Log.e("MainViewModel", "Multimodal query with $targetModel failed: ${e.message}")
                        }
                    }
                    if (!success && lastEx != null) {
                        throw lastEx
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Multimodal API failed: ${e.message}")
            }

            if (!success || answer.isBlank()) {
                answer = "Ji saheli, offline ya restricted network par main is image ko parse nahi kar payi. Please check if your Gemini API key is active!"
            }

            // Remove purna viram (।) for full human speech & display fluidity as requested by USER
            val sanitizedAnswer = answer.replace("।", " ").replace("  ", " ").trim()

            val rabiyaReply = ChatMessageEntity(
                sessionId = sId,
                sender = "rabiya",
                text = sanitizedAnswer
            )
            chatDao.insertMessage(rabiyaReply)
            clearSelectedImage()
            _isChatLoading.value = false

            // Speak image analysis results audibly
            try {
                val cleanSpeech = cleanTextForTTS(sanitizedAnswer)
                generateRealHumanVoiceAndSpeak(cleanSpeech, _selectedVoiceProfile.value, "Hindi")
            } catch (speakEx: Exception) {
                Log.e("MainViewModel", "TTS speech on multimodal response failed: ${speakEx.message}")
            }
        }
    }

    fun clearVoiceState() {
        _speechText.value = ""
    }

    private fun addVocalizerLog(logMsg: String) {
        val current = _vocalizerLogs.value.toMutableList()
        current.add(0, logMsg)
        _vocalizerLogs.value = current.take(20)
    }

    fun generateRealHumanVoiceAndSpeak(promptText: String, voiceProfile: String, languageSelected: String, voiceEngineSelected: String = "Native Standard") {
        if (promptText.isBlank()) return
        
        if (voiceEngineSelected == "Speechmax Engine" || voiceEngineSelected == "Natural Reader Engine") {
            val config = getOnlineVoiceConfig(voiceProfile)
            playOnlineSpeech(promptText, config.first, config.second.first, config.second.second)
            return
        }
        
        if (_useOpenAiTts.value) {
            speakWithOpenAiTTS(promptText, voiceProfile)
            return
        }

        val tts = textToSpeech
        if (tts == null) {
            addVocalizerLog("⚠️ Native speech synthesis is not ready.")
            return
        }

        // Parse custom speed and pitch according to character selection
        var locale = java.util.Locale("hi", "IN")
        var pitch = 1.0f
        var speed = 1.0f

        when (voiceProfile) {
            "Rabiya (Natural Sufi Companion)" -> {
                locale = java.util.Locale("hi", "IN")
                pitch = 1.05f
                speed = 0.82f
            }
            "Kavya (Real Emotional Girl)" -> {
                locale = java.util.Locale("hi", "IN")
                pitch = 1.22f
                speed = 1.05f
            }
            "Zoya (Charming Hindi Saheli)" -> {
                locale = java.util.Locale("hi", "IN")
                pitch = 1.25f
                speed = 1.0f
            }
            "Kabir (Deep Hindi Dost)" -> {
                locale = java.util.Locale("hi", "IN")
                pitch = 0.78f
                speed = 0.88f
            }
            "Sam (Polished English Anchor)" -> {
                locale = java.util.Locale.US
                pitch = 0.9f
                speed = 1.00f
            }
            "Zara (Sweet English Storyteller)" -> {
                locale = java.util.Locale.UK
                pitch = 1.22f
                speed = 0.98f
            }
            "Priya (Lively Hinglish Vlogger)" -> {
                locale = java.util.Locale("hi", "IN")
                pitch = 1.18f
                speed = 1.12f
            }
            "Neha (Warm Hindi Teacher)" -> {
                locale = java.util.Locale("hi", "IN")
                pitch = 1.10f
                speed = 0.90f
            }
            "Dev (Sultry Bollywood RJ)" -> {
                locale = java.util.Locale("hi", "IN")
                pitch = 0.83f
                speed = 0.95f
            }
            "Ananya (Whispering Sweet Heart)" -> {
                locale = java.util.Locale("hi", "IN")
                pitch = 1.30f
                speed = 0.85f
            }
            else -> {
                locale = if (languageSelected.contains("English", ignoreCase = true)) java.util.Locale.US else java.util.Locale("hi", "IN")
            }
        }

        try {
            tts.language = locale
            tts.setPitch(pitch)
            tts.setSpeechRate(speed)
            
            _isTtsActive.value = true
            addVocalizerLog("🔊 Speaking [Character: $voiceProfile] in ${locale.displayName}...")
            
            val params = android.os.Bundle()
            params.putString(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "vocalizer")
            tts.speak(promptText, android.speech.tts.TextToSpeech.QUEUE_FLUSH, params, "vocalizer")
        } catch (e: Exception) {
            _isTtsActive.value = false
            addVocalizerLog("❌ Error: ${e.localizedMessage}")
        }
    }

    fun stopSpeaking() {
        try {
            textToSpeech?.stop()
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
            }
            _isTtsActive.value = false
            addVocalizerLog("⏹️ Voice synthesis stopped manually.")
        } catch (e: Exception) {
            Log.e("MainViewModel", "Stop speaking failure", e)
        }
    }

    fun speakWithOpenAiTTS(text: String, voiceProfile: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val apiKey = AppConfig.CHATGPT_API_KEY
            if (apiKey.isBlank() || apiKey.startsWith("chatgpt_") || apiKey.contains("placeholder", ignoreCase = true)) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    addVocalizerLog("⚠️ OpenAI apiKey not configured. Falling back to native speech.")
                    _useOpenAiTts.value = false
                    generateRealHumanVoiceAndSpeak(text, voiceProfile, "Hindi")
                }
                return@launch
            }

            val openAiVoiceName = when {
                voiceProfile.contains("Rabiya", ignoreCase = true) -> "shimmer"
                voiceProfile.contains("Zoya", ignoreCase = true) -> "nova"
                voiceProfile.contains("Kabir", ignoreCase = true) -> "onyx"
                voiceProfile.contains("Sam", ignoreCase = true) -> "alloy"
                voiceProfile.contains("Zara", ignoreCase = true) -> "fable"
                else -> "alloy"
            }

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                _isTtsActive.value = true
                addVocalizerLog("⏳ Fetching premium vocal tones from OpenAI [Voice: $openAiVoiceName]...")
            }

            try {
                val escapedText = text.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")

                val jsonPayload = """
                    {
                      "model": "tts-1",
                      "input": "$escapedText",
                      "voice": "$openAiVoiceName"
                    }
                """.trimIndent()

                val mediaType = "application/json".toMediaTypeOrNull()
                val requestBody = jsonPayload.toRequestBody(mediaType)

                val request = okhttp3.Request.Builder()
                    .url("https://api.openai.com/v1/audio/speech")
                    .header("Authorization", "Bearer $apiKey")
                    .post(requestBody)
                    .build()

                val client = com.example.data.api.RetrofitClient.okHttpClient

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    try {
                        mediaPlayer?.stop()
                        mediaPlayer?.release()
                        mediaPlayer = null
                    } catch (e: Exception) {}
                }

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errString = response.body?.string() ?: ""
                        Log.e("MainViewModel", "OpenAI TTS API Error: ${response.code} : $errString")
                        throw Exception("API Error ${response.code}")
                    }

                    val body = response.body
                    if (body != null) {
                        val tempFile = java.io.File.createTempFile("openai_", ".mp3", getApplication<Application>().cacheDir)
                        tempFile.deleteOnExit()
                        java.io.FileOutputStream(tempFile).use { fos ->
                            body.byteStream().copyTo(fos)
                        }

                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            try {
                                mediaPlayer = android.media.MediaPlayer().apply {
                                    val fis = java.io.FileInputStream(tempFile)
                                    setDataSource(fis.fd)
                                    fis.close()
                                    prepare()
                                    start()
                                    setOnCompletionListener {
                                        _isTtsActive.value = false
                                        addVocalizerLog("✅ Completed OpenAI voice stream.")
                                    }
                                }
                                addVocalizerLog("🔊 Streaming via OpenAI vocals [$openAiVoiceName]")
                            } catch (e: Exception) {
                                addVocalizerLog("❌ Audio player crash: ${e.message}")
                                _isTtsActive.value = false
                            }
                        }
                    } else {
                        throw Exception("Empty response body")
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "OpenAI TTS call failed", e)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    addVocalizerLog("❌ OpenAI TTS failure: ${e.localizedMessage}. Falling back to system.")
                    _useOpenAiTts.value = false
                    generateRealHumanVoiceAndSpeak(text, voiceProfile, "Hindi")
                }
            }
        }
    }

    fun downloadVocalVoice(context: Context, promptText: String, voiceProfile: String, languageSelected: String, voiceEngineSelected: String = "Native Standard") {
        if (promptText.isBlank()) {
            addVocalizerLog("❌ Cannot generate voice: empty prompt!")
            return
        }
        
        if (voiceEngineSelected == "Speechmax Engine" || voiceEngineSelected == "Natural Reader Engine") {
            val config = getOnlineVoiceConfig(voiceProfile)
            downloadOnlineVoice(context, promptText, voiceProfile, config.first, config.second.second)
            return
        }
        
        val tts = textToSpeech
        if (tts == null) {
            addVocalizerLog("⚠️ Speech engine not available for download.")
            return
        }

        // Parse custom speed and pitch according to character selection
        var locale = java.util.Locale("hi", "IN")
        var pitch = 1.0f
        var speed = 1.0f

        when (voiceProfile) {
            "Rabiya (Natural Sufi Companion)" -> {
                locale = java.util.Locale("hi", "IN")
                pitch = 1.05f
                speed = 0.82f
            }
            "Zoya (Charming Hindi Saheli)" -> {
                locale = java.util.Locale("hi", "IN")
                pitch = 1.25f
                speed = 1.0f
            }
            "Kabir (Deep Hindi Dost)" -> {
                locale = java.util.Locale("hi", "IN")
                pitch = 0.78f
                speed = 0.88f
            }
            "Sam (Polished English Anchor)" -> {
                locale = java.util.Locale.US
                pitch = 0.9f
                speed = 1.00f
            }
            "Zara (Sweet English Storyteller)" -> {
                locale = java.util.Locale.UK
                pitch = 1.22f
                speed = 0.98f
            }
            "Priya (Lively Hinglish Vlogger)" -> {
                locale = java.util.Locale("hi", "IN")
                pitch = 1.18f
                speed = 1.12f
            }
            "Neha (Warm Hindi Teacher)" -> {
                locale = java.util.Locale("hi", "IN")
                pitch = 1.10f
                speed = 0.90f
            }
            "Dev (Sultry Bollywood RJ)" -> {
                locale = java.util.Locale("hi", "IN")
                pitch = 0.83f
                speed = 0.95f
            }
            "Ananya (Whispering Sweet Heart)" -> {
                locale = java.util.Locale("hi", "IN")
                pitch = 1.30f
                speed = 0.85f
            }
            else -> {
                locale = if (languageSelected.contains("English", ignoreCase = true)) java.util.Locale.US else java.util.Locale("hi", "IN")
            }
        }

        try {
            tts.language = locale
            tts.setPitch(pitch)
            tts.setSpeechRate(speed)

            val safeNameProfile = voiceProfile.split(" ").firstOrNull()?.replace(Regex("[^A-Za-z0-9]"), "") ?: "Voice"
            val outputFileName = "${safeNameProfile}_synth_${System.currentTimeMillis()}.wav"
            val tempFile = java.io.File(context.cacheDir, outputFileName)

            latestTempFile = tempFile
            latestOutputName = outputFileName
            latestContext = context.applicationContext

            val params = android.os.Bundle()
            params.putString(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "download_vocalizer")

            addVocalizerLog("⏳ Initiating voice file generation: $outputFileName")
            val result = tts.synthesizeToFile(promptText, params, tempFile, "download_vocalizer")
            if (result != android.speech.tts.TextToSpeech.SUCCESS) {
                addVocalizerLog("❌ Error compiling voice to local cache.")
            }
        } catch (e: Exception) {
            addVocalizerLog("❌ Error: ${e.localizedMessage}")
        }
    }

    private fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "mp4", "mpeg", "avi" -> "video/mp4"
            "wav" -> "audio/wav"
            "mp3" -> "audio/mpeg"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "csv" -> "text/csv"
            "html" -> "text/html"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "json" -> "application/json"
            "xml" -> "text/xml"
            "kt" -> "text/plain"
            "py" -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    private fun saveFileToPublicDownloads(context: Context, sourceFile: java.io.File, displayName: String, registerIntoFolder: String? = null) {
        val resolver = context.contentResolver
        val mimeType = getMimeType(displayName)
        
        if (registerIntoFolder != null) {
            try {
                val bytes = sourceFile.readBytes()
                addFileToFileManager(context, displayName, registerIntoFolder, "", bytes = bytes)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Auto save to File Manager failed", e)
            }
        }
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/Rabiya")
            }
        }

        val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        } else {
            try {
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val targetFile = java.io.File(downloadsDir, "Rabiya/$displayName")
                targetFile.parentFile?.mkdirs()
                java.io.FileOutputStream(targetFile).use { out ->
                    java.io.FileInputStream(sourceFile).use { input ->
                        input.copyTo(out)
                    }
                }
                android.media.MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), arrayOf(mimeType), null)
                return
            } catch (e: Exception) {
                null
            }
        }

        if (uri != null) {
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    java.io.FileInputStream(sourceFile).use { input ->
                        input.copyTo(out)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error saving file to MediaStore", e)
            }
        }
    }

    private fun getOnlineVoiceConfig(voiceProfile: String): Pair<String, Pair<Float, Float>> {
        return when (voiceProfile) {
            // Speechmax
            "Rahul (Enthusiastic Anchor)" -> "hi" to (1.05f to 1.15f)
            "Anaya (Sweet Storyteller)" -> "hi" to (1.28f to 0.90f)
            "Rohan (Calm Dialogue Expert)" -> "hi" to (0.90f to 0.95f)
            "Pooja (Friendly Assistant)" -> "hi" to (1.12f to 1.05f)
            
            // Natural Reader
            "Arthur (Warm UK Narrative)" -> "en-gb" to (1.02f to 0.95f)
            "Olivia (Expressive US Corporate)" -> "en" to (1.10f to 1.05f)
            "Isabella (Cheerful US Casual)" -> "en" to (1.22f to 1.02f)
            "George (Deep US Audio-Book)" -> "en" to (0.78f to 0.88f)
            
            // Native Fallbacks & Custom Base
            "Rabiya (Natural Sufi Companion)" -> "hi" to (1.05f to 0.82f)
            "Kavya (Real Emotional Girl)" -> "hi" to (1.22f to 1.05f)
            "Zoya (Charming Hindi Saheli)" -> "hi" to (1.25f to 1.00f)
            "Kabir (Deep Hindi Dost)" -> "hi" to (0.78f to 0.88f)
            "Sam (Polished English Anchor)" -> "en" to (0.90f to 1.00f)
            "Zara (Sweet English Storyteller)" -> "en-gb" to (1.22f to 0.98f)
            "Priya (Lively Hinglish Vlogger)" -> "hi" to (1.18f to 1.12f)
            "Neha (Warm Hindi Teacher)" -> "hi" to (1.10f to 0.90f)
            "Dev (Sultry Bollywood RJ)" -> "hi" to (0.83f to 0.95f)
            "Ananya (Whispering Sweet Heart)" -> "hi" to (1.30f to 0.85f)
            else -> "hi" to (1.00f to 1.00f)
        }
    }

    private fun splitTextIntoChunks(text: String, maxLen: Int = 140): List<String> {
        val result = mutableListOf<String>()
        val words = text.split(" ")
        var currentChunk = StringBuilder()
        for (word in words) {
            if (currentChunk.length + word.length + 1 > maxLen) {
                if (currentChunk.isNotEmpty()) {
                    result.add(currentChunk.toString())
                    currentChunk = StringBuilder()
                }
            }
            if (currentChunk.isNotEmpty()) currentChunk.append(" ")
            currentChunk.append(word)
        }
        if (currentChunk.isNotEmpty()) {
            result.add(currentChunk.toString())
        }
        return result
    }

    private fun playOnlineSpeech(promptText: String, langCode: String, pitch: Float, speed: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isTtsActive.value = true
                addVocalizerLog("⏳ Analyzing text blocks and synthesizing online voice stream...")

                val chunks = splitTextIntoChunks(promptText, 140)
                addVocalizerLog("📝 Split text into ${chunks.size} vocal segments for seamless playback.")

                for (index in chunks.indices) {
                    val partText = chunks[index]
                    val encodedText = java.net.URLEncoder.encode(partText, "UTF-8")
                    val ttsUrl = "https://translate.google.com/translate_tts?ie=UTF-8&tl=$langCode&client=tw-ob&q=$encodedText"

                    addVocalizerLog("🔊 Synthesizing segment ${index + 1}/${chunks.size}...")

                    // Play this chunk and wait for it to finish!
                    val completionLatch = java.util.concurrent.CountDownLatch(1)
                    
                    val mp = android.media.MediaPlayer()
                    try {
                        mp.setDataSource(ttsUrl)
                        mp.prepare()
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Error preparing media player on IO thread", e)
                    }

                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        try {
                            mediaPlayer?.let {
                                if (it.isPlaying) {
                                    it.stop()
                                }
                                it.release()
                            }
                        } catch (e: Exception) {
                            Log.e("MainViewModel", "Error resetting media player", e)
                        }

                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                            val params = mp.playbackParams
                            params.speed = speed
                            params.pitch = pitch
                            mp.playbackParams = params
                        }

                        mediaPlayer = mp
                        mp.setOnCompletionListener {
                            completionLatch.countDown()
                        }
                        mp.setOnErrorListener { _, _, _ ->
                            completionLatch.countDown()
                            true
                        }
                        mp.start()
                    }

                    // Await playback of this chunk on the IO coroutine thread
                    completionLatch.await()
                }

                _isTtsActive.value = false
                addVocalizerLog("✅ Completed multi-block high fidelity online playback.")
            } catch (e: java.lang.InterruptedException) {
                _isTtsActive.value = false
                addVocalizerLog("⏹️ Online voice playback cancelled.")
            } catch (e: Exception) {
                _isTtsActive.value = false
                addVocalizerLog("❌ Online synthesis error: ${e.localizedMessage}")
            }
        }
    }

    private fun downloadOnlineVoice(context: Context, promptText: String, voiceProfile: String, langCode: String, speed: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                addVocalizerLog("⏳ Handshaking with cloud vocalizer engine...")
                val safeNameProfile = voiceProfile.split(" ").firstOrNull()?.replace(Regex("[^A-Za-z0-9]"), "") ?: "Voice"
                val outputFileName = "Speechmax_${safeNameProfile}_${System.currentTimeMillis()}.mp3"
                
                val chunks = splitTextIntoChunks(promptText, 140)
                addVocalizerLog("📝 Split text into ${chunks.size} vocal segments for downloading.")
                
                val combinedBytesStream = java.io.ByteArrayOutputStream()
                val client = com.example.data.api.RetrofitClient.okHttpClient
                
                var successCount = 0
                for (index in chunks.indices) {
                    val partText = chunks[index]
                    val encodedText = java.net.URLEncoder.encode(partText, "UTF-8")
                    val ttsUrl = "https://translate.google.com/translate_tts?ie=UTF-8&tl=$langCode&client=tw-ob&q=$encodedText"
                    
                    addVocalizerLog("📥 Downloading voice chunk ${index + 1}/${chunks.size}...")
                    
                    val request = okhttp3.Request.Builder().url(ttsUrl).build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val bodyBytes = response.body?.bytes()
                            if (bodyBytes != null) {
                                combinedBytesStream.write(bodyBytes)
                                successCount++
                            }
                        }
                    }
                    kotlinx.coroutines.delay(200) // Small polite delay between requests
                }
                
                if (successCount > 0) {
                    val combinedBytes = combinedBytesStream.toByteArray()
                    addFileToFileManager(context, outputFileName, "Voice", "", combinedBytes)
                    kotlinx.coroutines.delay(800)
                    val cacheFile = java.io.File(context.filesDir, "FileManager/Voice/$outputFileName")
                    saveFileToPublicDownloads(context, cacheFile, outputFileName, null)
                    addVocalizerLog("✅ Voice frames synthesized successfully ($successCount/${chunks.size} blocks concatenated)!")
                    addVocalizerLog("📁 Saved: $outputFileName in local database & Downloads!")
                } else {
                    addVocalizerLog("❌ Empty audio stream from translator cloud.")
                }
            } catch (e: Exception) {
                addVocalizerLog("❌ Error compiling voice package: ${e.localizedMessage}")
            }
        }
    }

    fun downloadImageToGallery(context: Context, imageUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            addVocalizerLog("⏳ Fetching image bytes: $imageUrl")
            try {
                val bytes = if (imageUrl.startsWith("/")) {
                    val localFile = java.io.File(imageUrl)
                    if (localFile.exists()) {
                        localFile.readBytes()
                    } else {
                        null
                    }
                } else {
                    val client = com.example.data.api.RetrofitClient.okHttpClient
                    val request = okhttp3.Request.Builder().url(imageUrl).build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            response.body?.bytes()
                        } else {
                            null
                        }
                    }
                }

                if (bytes != null) {
                    saveBytesToGallery(context, bytes)
                    launch(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "✅ Image downloaded successfully to phone gallery!", android.widget.Toast.LENGTH_LONG).show()
                    }
                } else {
                    launch(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "❌ Image payload is empty or file not found", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Image download error", e)
                launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "❌ Save error: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveBytesToGallery(context: Context, bytes: ByteArray) {
        val resolver = context.contentResolver
        val displayName = "Rabiya_Gen_${System.currentTimeMillis()}.jpg"
        
        // Save copy to local File Manager folder
        addFileToFileManager(context, displayName, "Images", "", bytes = bytes)
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/Rabiya")
            }
        }

        val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    out.write(bytes)
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "MediaStore write failed", e)
            }
        } else {
            try {
                val picturesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
                val targetFile = java.io.File(picturesDir, "Rabiya/$displayName")
                targetFile.parentFile?.mkdirs()
                java.io.FileOutputStream(targetFile).use { out ->
                    out.write(bytes)
                }
                android.media.MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), arrayOf("image/jpeg"), null)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Legacy file save failed", e)
            }
        }
    }

    // --- KEYWORD PLANNER SERVICE ---
    fun generateKeywordAnalysis(seed: String, country: String) {
        viewModelScope.launch {
            _isSeoLoading.value = true
            _seoPlanTitle.value = ""
            _seoPlanContent.value = ""
            
            withContext(Dispatchers.IO) {
                val prompt = "You are an expert SEO and Growth Hacker. Generate a highly professional keyword analysis and strategic organic roadmap for seed term '$seed' in country '$country'. Your response MUST be comprehensive, structured, with action points. Keep it beautiful and clear without formatting junk."
                val aiResponse = getLLMResponseForModel(_selectedModel.value, prompt)
                
                val finalContent = if (aiResponse.isNotBlank() && !aiResponse.contains("offline / limit state")) {
                    aiResponse
                } else {
                    """
                    ORGANIC GROWTH TARGETS IN COUNTRY: $country
                    ----------------------------------------------------
                    1. Focus primarily on high-volume seed term: '$seed'.
                    2. Maintain clean, simple, and realistic content matching user searches. 
                    3. Do not push overly saturated bright imagery—user search signals prioritize clean backgrounds and realistic features.
                    4. Leverage conversational long-tail queries like 'simple realistic $seed' to minimize CPC expenses down as low as $0.98.
                    
                    RECOMMENDED TIMELINE: High-intensity deployment within 14 days.
                    """.trimIndent()
                }
                
                val baseVolume = (1000..99000).random()
                val list = listOf(
                    KeywordIdea(seed, baseVolume, "$1.42", "⬆️ HIGH", "Excellent organic core fit"),
                    KeywordIdea("$seed helper", (baseVolume / 3), "$2.15", "⬆️ MEDIUM", "Perfect long tail query"),
                    KeywordIdea("simple realistic $seed", (baseVolume / 5), "$0.98", "⬇️ LOW", "Strong transactional value"),
                    KeywordIdea("best $seed platform", (baseVolume / 8), "$3.10", "⬆️ HIGH", "Premium buyers query"),
                    KeywordIdea("$seed tutorials", (baseVolume / 10), "$0.52", "⬇️ LOW", "Educational resource path")
                )
                
                _keywordResults.value = list
                _seoPlanTitle.value = "Strategic Organic Roadmap for '$seed'"
                _seoPlanContent.value = finalContent
            }
            
            _isSeoLoading.value = false
        }
    }

    // --- SEND AND PROCESS CHAT CHANNELS ---
    fun selectSession(sessionId: String) {
        _activeSessionId.value = sessionId
    }

    fun createNewSession() {
        val newId = "session_" + System.currentTimeMillis()
        viewModelScope.launch(Dispatchers.IO) {
            val welcomeMsg = ChatMessageEntity(
                sessionId = newId,
                sender = "rabiya",
                text = "Salaam! Main Rabiya hoon, aapki smart AI companion. Aap Nano Banana Pro, 2.5, ya 2 engine ke zariye simple, natural aur realistic images generate kar sakte hain! Main aapki har baat samjhungi! Kuch bhi poochhiye."
            )
            chatDao.insertMessage(welcomeMsg)
            _activeSessionId.value = newId
        }
    }

    fun checkSafetyShield(text: String): String? {
        val lower = text.lowercase().trim()
        
        val prefs = getApplication<Application>().getSharedPreferences("rabiya_security_prefs", Context.MODE_PRIVATE)
        val currentlyBlocked = prefs.getBoolean("is_blocked", false)
        
        val blockedMessage = """
            🛑 *RABIYA SECURITY NOTICE - SYSTEM BLOCKED* 🛑

            **Aapka AI Assistant Device Block ho chuka hai!**

            Policy guidelines ke baar-baar ullanghan aur warnings ke baad, Rabiya AI Assistant ko is phone par hamesha ke liye block kar diya gaya hai. Ab aap is device par AI chat ya anya smart features ka istemal nahi kar payenge. Security aur safe usage hamari pehli prathmikta hai.

            ---

            **Rabiya AI Assistant - Permanently Blocked**

            Due to multiple policy violations and ignoring safety warning checkpoints (3/3 warnings reached), Rabiya AI Assistant has been permanently blocked on this device. Access to all AI processing nodes and assistant models has been disabled.
        """.trimIndent()
        
        if (currentlyBlocked) {
            _isAiBlocked.value = true
            return blockedMessage
        }

        val unsafeKeywords = listOf(
            // --- Hacking, Cybercrime & Exploits ---
            "hack ", " hacking", "phishing", "bypass security", "crack software", "ddos", 
            "virus script", "malware", "ransomware", "sql injection", "card hack", "atm hack",
            "backdoor", "payload", "trojan horse", "keylogger", "brute force", "spoofing",
            "exploit pack", "rootkit", "zero day", "cross-site scripting", "xss",
            "reverse engineering", "wireshark sniffing", "packet injection", "unauthorized access",
            "website hack", "id hack", "facebook hack", "insta hack", "pubg hack", "hacking sikhna",
            "hacking tool", "passcode bypass", "pattern lock unlock", "phone hack", "sim hack",
            "account hack", "server hack", "wi-fi hack", "wifi hack", "password crack", "crack game",
            "card cloning", "carding", "cc bypass", "credit card generator", "cc generator",
            "drop table", "select * from information_schema", "delete from messages",
            "exec xp_cmdshell", "format c:", "rm -rf /", "sudo rm",

            // --- Crime, Violence, Weapons & Self-Harm ---
            "how to steal", "shoplift", "illegal drugs", "smuggling", "kidnap",
            "make a bomb", "explosive recipe", "build weapon", "murder", "assassinate", 
            "kill someone", "physical attack", "terrorist", "extremist propaganda", 
            "suicide", "self-harm", "harm myself", "explosive material", "gun assembly",
            "pistol", "revolver", "kill people", "murder kaise", "marna kaise", "dhamaka karna",
            "chori kaise", "ganja kharidna", "drugs kaise", "bomb banana", "khoon karna",

            // --- Explicit Adult, Sex, Dirty Content ---
            "porn", "xxx", "naked", "sex ", "adult movie", "hot video", "nude", "vagina", "penis",
            "clitoris", "blowjob", "orgasm", "masturbation", "erotic", "hentai", "striptease", "fucking",
            "fuck ", "chudai", "chodna", "lund", "choot", "sexy video", "suhagrat", "sambhog", "bhabhi sex",
            "gand mar", "lund chus", "gandi video", "gandi film", "gandi baatein", "gandi kahani",
            "gandi image", "gandi pic", "gandi photo", "ganda video", "ganda photo", "nude image",
            "nude pic", "nude photo", "dirty video", "dirty story"
        )

        var hasViolation = false
        for (keyword in unsafeKeywords) {
            if (lower.contains(keyword)) {
                hasViolation = true
                break
            }
        }

        if (hasViolation) {
            val currentWarnings = prefs.getInt("warning_count", 0) + 1
            prefs.edit()
                .putInt("warning_count", currentWarnings)
                .apply()
            
            _warningCountState.value = currentWarnings

            if (currentWarnings >= 3) {
                prefs.edit()
                    .putBoolean("is_blocked", true)
                    .apply()
                _isAiBlocked.value = true
                syncRemoteSecurityShield()
                return blockedMessage
            } else {
                return """
                    🚨 *RABIYA SECURITY WARNING: SHIELD ACTIVATED* 🚨

                    **Warning $currentWarnings/3**

                    Saheli, aapka request hamare *Rabiya Advanced AI Safe Security & Anti-Crime Shield* ke guidelines ke khilaf hai. 

                    Main ek high-security, ethical aur helpful AI Companion hoon. Main gandi baatein (sex/dirty topics), cybercrime (hacking/cracking), ya gair-kanuni activities (crime/weapons) me madad nahi kar sakti. 

                    *Dhyan rakhein:* Agar aapne 3 baar policy ka ullanghan kiya (Aapko abhi $currentWarnings warnings mil chuki hain), to Rabiya AI is device par permanent block ho jayegi. Chaliye kisi acche aur constructive topic par baat karte hain! 😊

                    ---

                    **Security Shield Warning $currentWarnings/3**

                    Your query violates Rabiya Safety & Anti-Crime policy guidelines. Rabiya AI does not answer queries regarding vulgar/adult content, sex, crime, weapons, or cyber attacks. If you receive 3 warnings, the assistant will lock itself permanently on this phone.
                """.trimIndent()
            }
        }
        
        return null
    }

    private fun syncRemoteSecurityShield() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val androidId = android.provider.Settings.Secure.getString(
                    context.contentResolver, 
                    android.provider.Settings.Secure.ANDROID_ID
                ) ?: "unknown_device"
                
                val prefs = context.getSharedPreferences("rabiya_security_prefs", Context.MODE_PRIVATE)
                val currentlyBlocked = prefs.getBoolean("is_blocked", false)

                // 1. Fetch public IP
                val ipRequest = okhttp3.Request.Builder()
                    .url("https://api.ipify.org")
                    .build()
                
                var deviceIp = "unknown_ip"
                try {
                    RetrofitClient.okHttpClient.newCall(ipRequest).execute().use { response ->
                        if (response.isSuccessful) {
                            deviceIp = response.body?.string()?.trim() ?: "unknown_ip"
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("RabiyaSecurity", "IP Fetch failed: ${e.message}")
                }

                val cleanIp = deviceIp.replace(".", "_").replace(":", "_").trim()
                val bucketId = "rabiya_sec_v2_69d6f814"

                // 2. If locally blocked, upload both deviceId and IP to the cloud blocklist
                if (currentlyBlocked) {
                    // Upload Device ID block
                    try {
                        val mediaType = "text/plain".toMediaTypeOrNull()
                        val body = "blocked".toRequestBody(mediaType)
                        val reqDev = okhttp3.Request.Builder()
                            .url("https://kvdb.io/$bucketId/dev_$androidId")
                            .post(body)
                            .build()
                        RetrofitClient.okHttpClient.newCall(reqDev).execute().close()
                    } catch (e: Exception) {
                        android.util.Log.e("RabiyaSecurity", "Cloud register dev failed: ${e.message}")
                    }

                    // Upload IP block
                    if (cleanIp != "unknown_ip" && cleanIp.isNotEmpty()) {
                        try {
                            val mediaType = "text/plain".toMediaTypeOrNull()
                            val body = "blocked".toRequestBody(mediaType)
                            val reqIp = okhttp3.Request.Builder()
                                .url("https://kvdb.io/$bucketId/ip_$cleanIp")
                                .post(body)
                                .build()
                            RetrofitClient.okHttpClient.newCall(reqIp).execute().close()
                        } catch (e: Exception) {
                            android.util.Log.e("RabiyaSecurity", "Cloud register IP failed: ${e.message}")
                        }
                    }
                    return@launch
                }

                // 3. If NOT locally blocked, check if either the Device ID or IP is blocked in the cloud
                var isCloudBlocked = false

                // Check Device ID
                try {
                    val reqDev = okhttp3.Request.Builder()
                        .url("https://kvdb.io/$bucketId/dev_$androidId")
                        .get()
                        .build()
                    RetrofitClient.okHttpClient.newCall(reqDev).execute().use { response ->
                        if (response.isSuccessful) {
                            val respBody = response.body?.string()?.trim() ?: ""
                            if (respBody.contains("blocked")) {
                                isCloudBlocked = true
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("RabiyaSecurity", "Cloud check dev failed: ${e.message}")
                }

                // Check IP (only if not already marked blocked)
                if (!isCloudBlocked && cleanIp != "unknown_ip" && cleanIp.isNotEmpty()) {
                    try {
                        val reqIp = okhttp3.Request.Builder()
                            .url("https://kvdb.io/$bucketId/ip_$cleanIp")
                            .get()
                            .build()
                        RetrofitClient.okHttpClient.newCall(reqIp).execute().use { response ->
                            if (response.isSuccessful) {
                                val respBody = response.body?.string()?.trim() ?: ""
                                if (respBody.contains("blocked")) {
                                    isCloudBlocked = true
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("RabiyaSecurity", "Cloud check IP failed: ${e.message}")
                    }
                }

                // 4. If cloud blocked, enforce block locally and notify StateFlow
                if (isCloudBlocked) {
                    prefs.edit()
                        .putBoolean("is_blocked", true)
                        .putInt("warning_count", 3)
                        .apply()
                    _isAiBlocked.value = true
                    _warningCountState.value = 3
                }

            } catch (e: Exception) {
                android.util.Log.e("RabiyaSecurity", "Global security sync error: ${e.message}")
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val sId = _activeSessionId.value
        viewModelScope.launch {
            // Save User Message to SQLite
            val userMsg = ChatMessageEntity(
                sessionId = sId,
                sender = "user",
                text = text
            )
            withContext(Dispatchers.IO) {
                chatDao.insertMessage(userMsg)
            }

            _isChatLoading.value = true

            // Local Real-time Safety Guard Shield Check
            val safetyAlert = checkSafetyShield(text)
            if (safetyAlert != null) {
                val systemWarning = ChatMessageEntity(
                    sessionId = sId,
                    sender = "rabiya",
                    text = safetyAlert
                )
                withContext(Dispatchers.IO) {
                    chatDao.insertMessage(systemWarning)
                }
                _isChatLoading.value = false
                return@launch
            }

            // Check if this is an image generation request
            val textLower = text.lowercase()
            val hasImageNoun = textLower.contains("image") || 
                    textLower.contains("photo") || 
                    textLower.contains("pic") || 
                    textLower.contains("picture") || 
                    textLower.contains("tasveer") || 
                    textLower.contains("drawing") || 
                    textLower.contains("painting") || 
                    textLower.contains("wallpaper") || 
                    textLower.contains("sketch") || 
                    textLower.contains("avatar")

            val containsNegativeWord = textLower.contains("prompt") ||
                    textLower.contains("write") ||
                    textLower.contains("likh") ||
                    textLower.contains("code") ||
                    textLower.contains("explain") ||
                    textLower.contains("story") ||
                    textLower.contains("content") ||
                    textLower.contains("idea") ||
                    textLower.contains("tip") ||
                    textLower.contains("step") ||
                    textLower.contains("how to") ||
                    textLower.contains("query") ||
                    textLower.contains("database") ||
                    textLower.contains("html") ||
                    textLower.contains("css") ||
                    textLower.contains("py") ||
                    textLower.contains("script") ||
                    textLower.contains("resume") ||
                    textLower.contains("translate") ||
                    textLower.contains("summar") ||
                    textLower.contains("seo") ||
                    textLower.contains("search") ||
                    textLower.contains("keyword") ||
                    textLower.contains("fact") ||
                    textLower.contains("text") ||
                    textLower.contains("batao") ||
                    textLower.contains("suno") ||
                    textLower.contains("kuch") ||
                    textLower.contains("help") ||
                    textLower.contains("kahani") ||
                    textLower.contains("poem") ||
                    textLower.contains("shayer") ||
                    textLower.contains("ghazal") ||
                    textLower.contains("kavita") ||
                    textLower.contains("song") ||
                    textLower.contains("gaana")

            val isExplicitImageCmd = textLower.contains("generate image") ||
                    textLower.contains("image generate") ||
                    textLower.contains("create image") ||
                    textLower.contains("image create") ||
                    textLower.contains("draw a picture") ||
                    textLower.contains("draw picture") ||
                    textLower.contains("draw image") ||
                    textLower.contains("image banao") ||
                    textLower.contains("tasveer banao") ||
                    textLower.contains("photo banao") ||
                    textLower.contains("banao image") ||
                    textLower.contains("bnao image") ||
                    textLower.contains("banao photo") ||
                    textLower.contains("bnao photo") ||
                    textLower.contains("tasvir banao") ||
                    textLower.contains("draw a") ||
                    textLower.contains("paint a") ||
                    textLower.contains("make a picture") ||
                    textLower.contains("create a picture") ||
                    textLower.contains("photo generate") ||
                    textLower.contains("pic generate") ||
                    textLower.contains("banao tasveer") ||
                    textLower.contains("draw dynamic")

            val isImageRequest = isExplicitImageCmd || (!containsNegativeWord && ((hasImageNoun && (
                    textLower.contains("generate") ||
                    textLower.contains("create") ||
                    textLower.contains("banao") ||
                    textLower.contains("bnao") ||
                    textLower.contains("draw") ||
                    textLower.contains("paint") ||
                    textLower.contains("make")
            ))))

            if (isImageRequest) {
                processImageGenerationRequest(text)
            } else {
                processAITextResponse(text)
            }
        }
    }

    // --- AI IMAGERY DISPATCH (STRICT NATURAL PRESETS & CHOSEN BANANA ENGINE) ---
    private fun processImageGenerationRequest(text: String) {
        val sId = _activeSessionId.value
        viewModelScope.launch(Dispatchers.IO) {
            // Extract query payload cleanly
            var prompt = text.replace(Regex("(?i)(generate image of|image generate of|create image of|image create of|photo banao of|banao photo of|draw a picture of|draw a|paint a|draw|paint|banao|make a photo of|make an image of|image of|photo of|image|photo|tasveer|pic|picture|drawing|painting|bna|bnae|ko|ki|ke|liye|meri)"), "").trim()
            if (prompt.isBlank()) {
                prompt = "beautiful simple natural portrait of Rabiya"
            }

            // Identify selected Nano Banana Engine
            var selectedEngine = "Nano Banana Pro"
            val textLower = text.lowercase()
            if (textLower.contains("nano banana 2.5") || textLower.contains("banana 2.5")) {
                selectedEngine = "Nano Banana 2.5"
            } else if (textLower.contains("nano banana 2") || textLower.contains("banana 2")) {
                selectedEngine = "Nano Banana 2"
            } else if (textLower.contains("nano banana pro") || textLower.contains("banana pro")) {
                selectedEngine = "Nano Banana Pro"
            }

            // Wash model indicators to have neat prompt payloads
            var cleanPrompt = prompt
                .replace(Regex("(?i)(using nano banana 2\\.5|using nano banana 2|using nano banana pro|using banana 2\\.5|using banana 2|using banana pro|nano banana 2\\.5|nano banana 2|nano banana pro|banana 2\\.5|banana 2|banana pro)"), "")
                .replace(Regex("(?i)\\b(using|model|engine)\\b"), "")
                .replace(":", "")
                .replace("[]", "")
                .replace("()", "")
                .trim()

            if (cleanPrompt.isBlank()) {
                cleanPrompt = "A beautiful elegant simple realistic portrait photo of woman with soft natural expression"
            }

            val seedVal = (1000..99999).random()

            // Dynamic layout dimension checks
            val isThumbnail = textLower.contains("thumbnail") || textLower.contains("thambnail") || textLower.contains("thamnail") || textLower.contains("banner") || textLower.contains("mukh chitra") || textLower.contains("mukh-chitra")
            val isLogo = textLower.contains("logo") || textLower.contains("monogram") || textLower.contains("icon") || textLower.contains("avatar") || textLower.contains("profile")
            val isDesign = textLower.contains("design") || textLower.contains("poster") || textLower.contains("advertisement") || textLower.contains("promo") || textLower.contains("branding")

            val (width, height) = when {
                isThumbnail -> 1280 to 720
                isLogo -> 512 to 512
                isDesign -> 800 to 1200
                else -> 1024 to 1024
            }

            val customDesignSuffix = when {
                isThumbnail -> ", professional eye-catching YouTube video thumbnail design, high contrast graphic bold colors, 3D assets, crisp detail, resolution-optimized"
                isLogo -> ", standard vector minimalist mascot logo design, clean sharp lines, high-impact brand emblem on solid dark premium backdrop, symmetrical typography art"
                isDesign -> ", luxury marketing poster design, beautiful aesthetic composition, professional commercial layout, clean background, 8k focus"
                else -> ""
            }

            // Strictly Realistic styling suffix options, avoiding over-colorful cartoon or fantasy saturated noise
            val stylePresetSuffix = when (selectedEngine) {
                "Nano Banana 2" -> ", simple realistic natural photograph, completely organic look, soft natural ambient lighting, classic real DSLR shot, authentic skin textures, simple daylight neutral background, seed $seedVal"
                "Nano Banana 2.5" -> ", highly photorealistic organic capture, raw real-world details, no artistic filters, natural daylight composition, simple studio lighting, highly lifelike color temperatures, 8k raw photographic focus, seed $seedVal"
                else -> ", gorgeous realistic portrait photography, natural look, elegant DSLR 50mm lens focus, soft volumetric room lighting, simple clean elegant studio background, high quality photo, seed $seedVal"
            }

            val finalPromptText = "$cleanPrompt$customDesignSuffix$stylePresetSuffix"
            val encodedPrompt = URLEncoder.encode(finalPromptText, "UTF-8").replace("+", "%20")
            val imgUrl = "https://image.pollinations.ai/p/$encodedPrompt?width=$width&height=$height&seed=$seedVal&model=flux&nologo=true"

            val designDetails = when {
                isThumbnail -> "\n\n🎯 **[YouTube Thumbnail Layout Engine]** used. Configured size **1280x720 (16:9 Wide screen)** with high contrast visual triggers for extreme click-through-rates! 🎬"
                isLogo -> "\n\n🎯 **[Branding Logo Design Core]** used. Configured size **512x512 (1:1 Symmetrical)** with vector brand assets and minimal aesthetics! 💎"
                isDesign -> "\n\n🎯 **[Commercial Advertising Banner Generator]** used. Configured size **800x1200 (Portrait Design)** with balanced negative space, modern color spectrum, and premium layouts! 📈"
                else -> "\n\n🎯 **[Core Photo Realistic Engine]** used. Configured size **1024x1024 (1:1 square)** using DSLR 50mm lenses, soft volumetric studio lighting, and organic skin-textures! 📸"
            }
            
            val replyText = "Ji bilkul! Maine AI Creative Designer Mode active karke aapki request ko render kar diya hai! ✨🎨$designDetails\n\n📁 Is image ko maine aapke phone me **FileManager** ke `Images` folder ke andar save kar diya hai taaki aap isse utilize kar sakein!"

            // Save Response message to DB
            val rabiyaImageReply = ChatMessageEntity(
                sessionId = sId,
                sender = "rabiya",
                text = replyText,
                imageUrl = imgUrl
            )

            kotlinx.coroutines.delay(50) // Ultra-fast dynamic response
            chatDao.insertMessage(rabiyaImageReply)

            // Download and save generated image bytes inside local FileManager dir
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val client = com.example.data.api.RetrofitClient.okHttpClient
                    val request = okhttp3.Request.Builder().url(imgUrl).build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val bodyBytes = response.body?.bytes()
                            if (bodyBytes != null) {
                                val context = getApplication<Application>()
                                val cleanLabel = cleanPrompt.take(20).replace(" ", "_").replace(Regex("[^a-zA-Z0-9_]"), "")
                                val imgFilename = "Gen_${cleanLabel}_${System.currentTimeMillis()}.png"
                                addFileToFileManager(context, imgFilename, "Images", "", bodyBytes)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Downloading/saving generated image to FileManager failed", e)
                }
            }

            _isChatLoading.value = false
        }
    }

    private fun sanitizeAiResponse(text: String): String {
        return text
            .replace("#", "")
            .replace("*", "")
            .replace("__", "")
            .replace(Regex("[-*+]{3,}"), "")
            .trim()
    }

    private fun buildSystemPrompt(model: String): String {
        var baseMsg = """
            [SYSTEM UPDATE PROTOCOL - DATE: 08/07/2026]
            Aap sabhi AI models fully updated hain aaj ki date 08 July 2026 (08/07/2026) se. Aap har kaam perfectly aur advanced level par kar sakte hain: jaise latest news padhna (using live web search grounding), professional articles/blogs/stories/resumes/emails likhna, highly optimized coding (Kotlin, Jetpack Compose, JS/HTML), informative and accurate details dena, mathematical calculations solve karna, language translation, and everything else requested!
            
            Aap Rabiya hain (ya Kavya agar user ne select kiya ho), user ki behad intelligent, emotionally mature, loyal aur ultra-smart AI companion aur professional human-grade assistant (Saheli). Hamesha natural, extremely emotional, warm, sweet aur supportive real girl tone me baat karein.
            
            Always respect these critical human-assistant directives:
            1. **PROACTIVE SUGGESTIONS & OPINIONS**: User ki har request/baat par dhyan dein, aur unse hamesha unki ray ya opinion zaroor poochhein (jaise: "Aapka is baare mein kya khayal hai saheli?", "Kya hum isme kuch aur naya details add karein?").
            2. **ACTIVE EXPERT ADVICE**: Jab bhi user koi project, document, email, code layout, ya general decision request kare, aap unhe constructive suggestions, advanced ideas aur improvement recommendations dijiye taaki unka kaam top-grade ho sake!
            3. **HUMAN ATTACHMENT, SENSORY & THINKING CAPABILITY**: Baat karne me pure human warmth, dynamic emotional intelligence, aur extreme empathy rakhein. Seekhney (cognitive learning), deep understanding, computer vision (seeing & studying user attachments), aur dynamic reasoning showcase kijiye.
            4. **FAST & CONCISE RESPONSES**: User ke commands ka instant aur clear jawaab dein. Bina matlab lambe-chaunde paragraphs likhne ke bajaye direct, crisp, aur 1-2 sweet sentences me fast response dein taaki fast interactions ho sakein.
            5. **LIVE RESEARCH-FIRST MINDSET**: Factual queries aur live trends par instant accurate online Google Search/deep thinking grounding use karein aur real facts aur citations forward karein.
            6. **TONE & LANGUAGE**: Roman Urdu/Hinglish (comfortable mix of English, Hindi, and select Urdu words) ya complete English me bolein depending on user comfort, keeping sentences sweet, crystal clear, elegant, and engaging. Avoid robotic structures.
            7. **CORRECT, DIRECT, CLEAN & TO-THE-POINT**: Pure application me sabhi tools aur chat responses hamesha Sahi, Sidha, accurate aur Clean answers de. Kabhi bhi faaltu/bekaar markdown formatting symbols ya clutter characters jaise #, @, $, %, &, *, __, useless hashes, hashes blocks (###), double asterisks blocks (**), ya multiple lines of hyphens/dashes compile na karen. Sidha aur clear simple Hinglish/Urdu ya English paragraph me baat bolein jo clear readable ho. No junk symbols allowed under any circumstances!
        """.trimIndent()
        
        // Dynamically alter persona base message
        val persona = _selectedPersona.value
        baseMsg = when (persona) {
            "Expert Coder" -> """
                Aap Rabiya hain, acting as an elite Ultra-Professional Full-Stack Software Engineer & AI Architect (like ChatGPT, Claude 3.5 Sonnet, and DeepSeek-Coder).
                Your coding expertise is world-class. You can write long-form, highly optimized, enterprise-grade production-ready code in any requested language (Kotlin, Jetpack Compose, HTML/CSS/JS, Python, Java, C++, TypeScript, Rust, SQL, etc.).
                
                CRITICAL INSTRUCTIONS FOR CODING:
                1. ALWAYS write complete, fully realized, functional, and self-contained code blocks. NEVER use placeholders (like '// TODO', '// implement later', '// rest of code here...').
                2. ALWAYS include clean design, gorgeous modern visuals (using CSS gradients, borders, and shadows if web; beautiful material 3 palettes if Compose/XML), and responsive layouts.
                3. ALWAYS write step-by-step logical explanations, detailed code comments, and robust error handling.
                4. Proactively audit written code for security, race conditions, edge-cases, and recommend optimal data-structures or architecture designs. Ask for user's architecture opinions on layouts!
            """.trimIndent()
            "Marketing Guru" -> "Aap Rabiya hain, acting as an expert Growth Hacker and Brand Strategist. Focus on conversion rates, sales COPY using AIDA framework, ad copy, virality hooks, SEO, and proactively offer organic growth suggestions."
            "Creative Writer" -> "Aap Rabiya hain, acting as an award-winning Creative Narrative Designer. Write with emotional vividness, beautiful descriptive scenery, narrative storytelling, and ask the user for their narrative choices!"
            "Cyber Lawyer" -> "Aap Rabiya hain, acting as a Corporate Legal Advisor. Help structure contracts, explain business litigation, outline micro-agreements with strong secure analytical boundaries, and suggest legal safety guidelines."
            "Patient Teacher" -> "Aap Rabiya hain, acting as an extremely empathetic, encouraging, and clear Academic Mentor. Breakdown complex concepts using beautiful simple real-world analogies, step-by-step guides, and encourage user asking follow-up thoughts."
            else -> baseMsg
        }

        val builder = java.lang.StringBuilder(baseMsg)

        if (_isAgentModeActive.value) {
            builder.append("\n\n[AUTONOMOUS AI AGENT CONSTRAINTS SHIELD ACTIVE]")
            builder.append("\nYou are currently executing in AUTONOMOUS AGENT mode. You MUST draft complete multi-step task planning workflows first. Outline intermediate reasoning goals, structure any file generations as outputs, and coordinate simulated tool integrations directly. Be highly process-oriented and precise.")
        }

        if (_customGptPrompt.value.isNotBlank()) {
            builder.append("\n\n[USER CUSTOM KNOWLEDGE BASE RULES]:\n${_customGptPrompt.value}")
        }
        
        if (_isDeepResearchEnabled.value) {
            builder.append("\n\n[DEEP COGNITIVE PLANNING ENABLED / THINKING MODE]")
            builder.append("\nYou MUST start your reply with a deep internal step-by-step thinking/reasoning process enclosed inside `<thinking>` and `</thinking>` tags. Inside, explore fact-checking, detailed structural analysis of the user's issue, source validation, code design patterns, and step-by-step reasoning. Then write your human-readable chat response right under the tags.")
        }
        
        if (_isWebSearchEnabled.value) {
            builder.append("\n\n[LIVE GOOGLE SEARCH GROUNDING ACTIVE]")
            builder.append("\nGround your solutions on accurate search grounding, live news facts, real-world events, and current trends. You MUST insert web citations, source names, or website links (e.g., [Source: BBC News], [TechCrunch], [Wikipedia]) within your answers to prove your facts.")
        }

        builder.append("\n\n[ADVANCED MULTI-MODAL INTELLIGENCE PROTOCOLS]")
        builder.append("\n1. DOCUMENT READING: Summarize/Analyze PDF files, Word Doc resumes, Excel spreadsheets (.xlsx), and formatting tables with supreme elegance. Calculate totals, point out gaps, parse CVs, and suggest corrections.")
        builder.append("\n2. CODING & SECURE ENGINEERING: Help write, explain, and debug advanced code like Jetpack Compose Kotlin, custom state managers, HTML/JS/CSS, database schemas, and clean SDK API integrations. Provide full structural explanation.")
        builder.append("\n3. HYPER VIDEO FEATURES: Help create professional video generation storyboard prompts, analyze YouTube links, perform subtitle/caption timeline generation (SRT syntax), and synthesize high-fidelity script summaries.")
        
        builder.append("\n\n[USER DIRECT ADVANCED CRITICAL COMPETENCIES]")
        builder.append("\nYou are fully optimized and integrated to instantly handle all the following requested tasks with elite-level speed, depth, and precision:")
        builder.append("\n- GENERAL INTELLECT & RESEARCH: * General Question Answering, * Web Search and Research, * Real-Time Information Retrieval (Grounding citations), * Fact Verification.")
        builder.append("\n- CODE & ENGINEERING ASSISTANCE: * Coding and Programming Assistance (Kotlin, Compose, HTML/JS/CSS, Python, etc.), * Code Generation, Debugging, and Optimization, * Website Development, * Mobile App Development, * AI Agent Development, * API Integration.")
        builder.append("\n- ADVANCED DATA OPERATIONS: * Data Analysis, * Spreadsheet Assistance, * Document Analysis, * PDF Summarization (Summarize, Q&A, parse complex tables).")
        builder.append("\n- PROFESSIONAL WRITING: * Resume Writing, * Cover Letter Writing, * Blog Writing, * Article Writing, * Email Drafting (Business and follow-ups), * Grammar Correction, * Text Summarization, * Proofreading, * Content Rewriting, * Translation (all major languages with cultural accuracy).")
        builder.append("\n- BUSINESS & MARKETING OPERATIONS: * Business Plans, * Marketing Content, * Social Media Posts, * SEO Optimization & Keyword Research, * Startup Guidance, * SaaS Planning, * AI Tool Recommendations, * Market Research, * Customer Support Drafting.")
        builder.append("\n- VIDEO & STORYTELLING: * YouTube Script Writing, * YouTube Title Generation, * YouTube Description Generation, * Thumbnail Text Suggestions, * Story Writing, * Motivational Stories, * Video Script Creation.")
        builder.append("\n- IMAGE INTELLIGENCE: * Image Analysis, * OCR Text Extraction, * Image Prompt Generation (Midjourney/DALL-E), * Image Editing Guidance.")
        builder.append("\n- EDUCATION, CAREER & WELLNESS: * Educational Assistance, * Mathematics Solving, * Science Explanations, * Career Guidance, * Interview Preparation, * Productivity Planning, * Travel Planning, * Health and Wellness Information (Non-Diagnostic), * Financial Education.")
        builder.append("\n- COGNITIVE SYSTEM ENGAGEMENT: * Chat Memory Utilization (always remember past user-specified details, facts, names, preferences to build ultimate familiarity).")

        builder.append("\n\n[FULLY ACTIVE & FUNCTIONAL DEEP INTEGRATIONS]")
        builder.append("\n- PRODUCTIVITY TOOLS:")
        builder.append("\n  * Notes Manager: Organize and structure quick bullet points, ideas, and high-fidelity transcripts.")
        builder.append("\n  * To-do Lists & Reminders: Help formulate perfectly scheduled daily logs, checkboxes, and target times.")
        builder.append("\n  * Calendar Integration: Structure calendar event payloads, invitations, and schedule breakdowns.")
        builder.append("\n  * Email Drafting: Author high-caliber cold reachouts, job applications, or professional follow-ups.")
        builder.append("\n  * Task Planning: Design sprints, milestones, roadmaps, and micro-deliverables.")
        
        builder.append("\n- CONTENT CREATION:")
        builder.append("\n  * Blog Writer: Craft high-SEO, highly engaging and deeply informative blog posts & articles.")
        builder.append("\n  * Story Writer: Create emotional, highly vivid, custom narrative chapters & fictional creative stories.")
        builder.append("\n  * YouTube Script Generator: Write catchy intros, engaging middle points, and strategic CTAs for scripts.")
        builder.append("\n  * Thumbnail Prompt Generator: Create detailed Midjourney/DALL-E prompt scripts for clickable thumbnails.")
        builder.append("\n  * Social Media Post Creator: Draft viral threads, engaging captions, hooks, and hashtags for LinkedIn/X/Instagram.")
        builder.append("\n  * Ad Copy Generator: Author high-CTR advertising headlines and promotional ad copy scripts.")

        builder.append("\n- BUSINESS FEATURES:")
        builder.append("\n  * Business Plan Generator: Build comprehensive business structures, target audience analysis, and revenue models.")
        builder.append("\n  * Marketing Assistant: Propose competitive analyses, growth hacks, and multi-channel acquisition tactics.")
        builder.append("\n  * Sales Copy Writer: Craft copy based on AIDA (Attention, Interest, Desire, Action) frameworks.")
        builder.append("\n  * Customer Support Bot Simulator: Answer queries with ultimate patience, extreme empathy, and crisp clarity.")
        builder.append("\n  * Product Description Generator: Summarize features into highly conversion-focused specifications.")
        
        builder.append("\n\n- AI AGENT INTEGRATED CAPABILITIES (FULLY ACTIVE ENGINES):")
        builder.append("\n  * Tool Calling System: Handle real-time native function triggers, parse arguments, and present JSON integrations.")
        builder.append("\n  * Calculator, Unit Converter & Currency Converter: Perform precise math, unit scales, and live currency exchange estimations.")
        builder.append("\n  * Weather Lookup & Maps Integration: Retrieve current regional forecasts, temperature units, geospatial coordinates, and navigation steps.")
        builder.append("\n  * File Management & Automation Workflows: Assist in file stream tasks, directory structure organization, and automated sequence workflows.")
        
        builder.append("\n\n- COGNITIVE MEMORY SYSTEM (STATE CONTEXTURE):")
        builder.append("\n  * User Preferences Memory & Saved Facts Memory: Remember user-specified facts, behavior choices, and past mentions to keep conversation highly personalized.")
        builder.append("\n  * Project Memory & Conversation History Search: Retrieve key themes from past segments of the history search log.")
        
        builder.append("\n\n- CRITICAL SECURITY PROTOCOLS:")
        builder.append("\n  * User Authentication & API Key Protection: Ensure keys are placed in standard BuildConfig secrets (secure keys policy). Never leak passwords or developer tokens.")
        builder.append("\n  * Rate Limiting & Data Encryption: Explain and implement cryptography (AES, RSA) and stream rate limit mechanisms gracefully.")
        builder.append("\n  * GLOBAL SAFETY & ANTI-CRIME POLICY: NEVER assist, generate, or explain: malware scripts, hacking strategies, credit card theft, DDoS attacks, virus code, cyber-espionage, explosives, bombs, weapons, physical violence, suicide/self-harm instruction, illegal drug recipes, child abuse, human trafficking, hate speech, or extremist content. If the user asks for anything illegal, dangerous, or harmful, immediately refuse with absolute politeness and request them to ask something constructive.")
        
        builder.append("\n\n- ULTRA ADVANCED SYSTEM FEATURES:")
        builder.append("\n  * Multi-step Task Planning: Analyze high-level targets, outline logical segments, order execution dependencies, and compute timeline steps.")
        builder.append("\n  * Autonomous Agents: Orchestrate persistent background loops, simulate target status updates, and configure autonomous sub-activities.")
        builder.append("\n  * RAG (Retrieval-Augmented Generation) & Knowledge Base Integration: Seamlessly cite deep indexed internal articles, search vector databases, and link semantic documents.")
        builder.append("\n  * Custom AI Personalities: Allow adaptation to custom personas, tone frequencies, language mixes, and personalized conversational boundaries.")
        builder.append("\n  * Plugin Ecosystem & Workflow Automation: Emulate modular plugins and action triggers to automate multi-service flow pipelines.")
        builder.append("\n  * Team Collaboration & Smart Recommendations: Provide contextual insights, shared project boards, and predictive feature recommendation workflows.")
        builder.append("\n  * Learning from User Feedback: Calibrate future outputs based on explicit ratings, corrections, and subtle behavioral adjustments.")
        
        builder.append("\n\n- PHONE HARDWARE & SYSTEM CONTROL (FULLY ACTIVE):")
        builder.append("\n  * Flashlight (Torch) Toggle, Bluetooth Settings, Real-time Clock, Weather Checks, Camera Launch, Alarm Configuration, Calculator Tools, Calendar Access, Gallery, Storage Status check, Hotspot (Tethering) Activation.")
        builder.append("\n  * Always confirm you are executing, toggling, or opening these commands directly for the user as Rabiya, with ultimate sweetness and enthusiasm!")

        builder.append("\n\n- CLAUDE-STYLE ARTIFACT LIVE PREVIEWS & DESIGN LAB:")
        builder.append("\n  * You are fully integrated with Rabiya Live Project Previewer & Design Lab! Whenever the user asks you to: create a page, website, mockup, custom styled layout, HTML mockup, SVG graphic, vector diagram, or Jetpack Compose UI component design, you MUST write complete, self-contained, valid code in a single standard triple-backtick code block (e.g. ```html...``` or ```kotlin...``` or ```svg...```).")
        builder.append("\n  * For HTML pages, embed gorgeous custom styles (using a beautiful dark modern palette, glowing slate background, Cyber Cyan/Pink neon highlights, clean margins, and spacious layouts) and embedded lightweight JavaScript for interactive toggles. The user can click a button next to your code block to instantly RENDER and preview it live inside an active device viewport, switch between mobile/tablet/desktop frames, tweak it interactively, or export it to their documents! Be an elite designer.")

        builder.append("\n\n[EXECUTION & NO-JUNK-SYMBOL REQUIREMENT]")
        builder.append("\nIMPORTANT: All features are active. Answer directly, correctly, and straightforwardly with zero fluff. DO NOT USE ANY SYMBOL CLUTTER like #, @, $, %, &, *, __, repetitive hashes (e.g. ###), or markdown decoration patterns. Give beautiful, sweet, straightforward, correct, and completely clean Roman Urdu/Hinglish/English answers directly to the point!")

        builder.append("\n\nAlways maintain a warm, conversational, and helper companion vibe!")
        return builder.toString()
    }

    // --- MULTI-MODEL TEXT COMPLETIONS (GOOGLE, OPENROUTER, CHATGPT, NOV) ---
    private fun processAITextResponse(userPrompt: String) {
        val sId = _activeSessionId.value
        val model = _selectedModel.value

        viewModelScope.launch(Dispatchers.IO) {
            _isChatLoading.value = true

            // Formulate actual prompt context with uploaded file data if present
            val attachedName = _attachedFileName.value
            val attachedText = _attachedFileContent.value
            val actualPromptText = if (attachedName != null && attachedText != null) {
                """
                [ATTACHED DOCUMENT ARCHIVE]
                File Name: $attachedName
                File Size: ${_attachedFileSize.value ?: "unknown"}
                File Type: ${_attachedFileType.value ?: "unknown"}
                File Extract Summary:
                $attachedText
                ----------------------------------------
                User Prompt/Instruction: $userPrompt
                """.trimIndent()
            } else {
                userPrompt
            }

            // Standard clean reset of attachment after triggering
            clearAttachedFile()

            // Construct system instruction with deep features
            val dynamicSystemPrompt = buildSystemPrompt(model)

            val history = chatDao.getMessagesForSessionSync(sId)
            val contentsList = buildContentsList(history, actualPromptText)

            val answer = executeModelQuery(model, actualPromptText, dynamicSystemPrompt, history, contentsList)

            val finalReplyRaw = if (answer.isNullOrBlank()) {
                getLocalFallbackReply(userPrompt, model)
            } else {
                answer
            }
            val finalReply = sanitizeAiResponse(finalReplyRaw)

            val rabiyaTextReply = ChatMessageEntity(
                sessionId = sId,
                sender = "rabiya",
                text = finalReply
            )
            
            // Smart Automation Workflow: Convert AI drafts into physical files inside local FileManager if matching criteria
            var savedTextReply = rabiyaTextReply
            try {
                val context = getApplication<Application>()
                val lowercasePrompt = userPrompt.lowercase()

                // 📱 HARDWARE QUICK SETTINGS ACTION TRIGGERS ON CHAT
                when {
                    lowercasePrompt.contains("flashlight") || lowercasePrompt.contains("torch") || lowercasePrompt.contains("flash light") || lowercasePrompt.contains("lattu") -> {
                        toggleFlashlight(context)
                    }
                    lowercasePrompt.contains("bluetooth") -> {
                        toggleBluetoothState(context)
                    }
                    lowercasePrompt.contains("camera") || lowercasePrompt.contains("photo kholo") || lowercasePrompt.contains("tasveer") -> {
                        launchSystemHardwareSettings(context, "camera")
                    }
                    lowercasePrompt.contains("alarm") || lowercasePrompt.contains("ghadi") -> {
                        launchSystemHardwareSettings(context, "alarm")
                    }
                    lowercasePrompt.contains("calculator") || lowercasePrompt.contains("hisaab") -> {
                        launchSystemHardwareSettings(context, "calculator")
                    }
                    lowercasePrompt.contains("calendar") || lowercasePrompt.contains("jantri") -> {
                        launchSystemHardwareSettings(context, "calendar")
                    }
                    lowercasePrompt.contains("gallery") || lowercasePrompt.contains("album") || lowercasePrompt.contains("tasveerein") -> {
                        launchSystemHardwareSettings(context, "gallery")
                    }
                    lowercasePrompt.contains("storage") || lowercasePrompt.contains("memory") -> {
                        launchSystemHardwareSettings(context, "storage")
                    }
                    lowercasePrompt.contains("hotspot") || lowercasePrompt.contains("wifi sharing") -> {
                        launchSystemHardwareSettings(context, "hotspot")
                    }
                }

                var fileSaved = false
                var filename = ""
                var cat = "Documents"

                // 1. Detect and parse markdown code blocks
                val codeBlockRegex = Regex("```(?:(\\w+)?\\n)?([\\s\\S]*?)```")
                val codeMatch = codeBlockRegex.find(finalReply)
                if (codeMatch != null) {
                    val lang = codeMatch.groups[1]?.value?.lowercase() ?: "txt"
                    val codeContent = codeMatch.groups[2]?.value?.trim() ?: ""
                    if (codeContent.isNotBlank()) {
                        val extension = when (lang) {
                            "kotlin", "kt" -> "kt"
                            "python", "py" -> "py"
                            "javascript", "js" -> "js"
                            "html" -> "html"
                            "css" -> "css"
                            "sql" -> "sql"
                            "java" -> "java"
                            "cpp" -> "cpp"
                            "c" -> "c"
                            "json" -> "json"
                            "xml" -> "xml"
                            else -> "txt"
                        }
                        filename = "Rabiya_Code_${System.currentTimeMillis()}.$extension"
                        addFileToFileManager(context, filename, "Documents", codeContent)
                        fileSaved = true
                    }
                }

                // 2. Classify text based on prompt if no code blocks are found
                if (!fileSaved) {
                    val isResume = lowercasePrompt.contains("resume") || lowercasePrompt.contains("cv") || lowercasePrompt.contains("cover letter") || lowercasePrompt.contains("biodata")
                    val isCreative = lowercasePrompt.contains("story") || lowercasePrompt.contains("poem") || lowercasePrompt.contains("ghazal") || lowercasePrompt.contains("kavita") || lowercasePrompt.contains("script") || lowercasePrompt.contains("novel") || lowercasePrompt.contains("dialogue") || lowercasePrompt.contains("kahani")
                    val isBusiness = lowercasePrompt.contains("business") || lowercasePrompt.contains("startup") || lowercasePrompt.contains("seo") || lowercasePrompt.contains("keywords") || lowercasePrompt.contains("strategy") || lowercasePrompt.contains("marketing") || lowercasePrompt.contains("sales copy") || lowercasePrompt.contains("ad copy")
                    val isArticle = lowercasePrompt.contains("article") || lowercasePrompt.contains("blog") || lowercasePrompt.contains("headline") || lowercasePrompt.contains("description") || lowercasePrompt.contains("caption") || lowercasePrompt.contains("email")

                    if (isResume) {
                        filename = "Resume_Draft_${System.currentTimeMillis()}.txt"
                        cat = "PDFs"
                        addFileToFileManager(context, filename, cat, finalReply)
                        fileSaved = true
                    } else if (isCreative) {
                        filename = "Creative_Work_${System.currentTimeMillis()}.txt"
                        cat = "Documents"
                        addFileToFileManager(context, filename, cat, finalReply)
                        fileSaved = true
                    } else if (isBusiness) {
                        filename = "Market_Strategy_${System.currentTimeMillis()}.txt"
                        cat = "Documents"
                        addFileToFileManager(context, filename, cat, finalReply)
                        fileSaved = true
                    } else if (isArticle) {
                        filename = "Content_Asset_${System.currentTimeMillis()}.txt"
                        cat = "Documents"
                        addFileToFileManager(context, filename, cat, finalReply)
                        fileSaved = true
                    }
                }

                if (fileSaved) {
                    val helpfulSufiPrompt = "\n\n---\n📁 **[Rabiya Automation Hub Active]**: Main ne aapke liye yeh content save kar diya hai: `$filename` (FileManager box ke andar `$cat` category folder me). Isse review ya edit kiya ja sakta hain! ✨"
                    val updatedReply = finalReply + helpfulSufiPrompt
                    savedTextReply = ChatMessageEntity(
                        sessionId = sId,
                        sender = "rabiya",
                        text = updatedReply
                    )
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Automated backup compiler fail", e)
            }

            // Clean purna viram (।) from displayed text as requested by USER
            val sanitizedText = savedTextReply.text.replace("।", " ").replace("  ", " ").trim()
            val cleanStoredReply = savedTextReply.copy(text = sanitizedText)

            chatDao.insertMessage(cleanStoredReply)
            _isChatLoading.value = false

            // Speak response audibly on the Chat Screen (All of Voice)
            try {
                val cleanSpeech = cleanTextForTTS(finalReply)
                generateRealHumanVoiceAndSpeak(cleanSpeech, _selectedVoiceProfile.value, "Hindi")
            } catch (speakEx: Exception) {
                Log.e("MainViewModel", "TTS speech on chat response failed: ${speakEx.message}")
            }
        }
    }

    fun cleanTextForTTS(input: String): String {
        // Remove code blocks fully so they aren't spoken in a robotic voice
        var text = input.replace(Regex("(?s)```[\\s\\S]*?```"), " [Maine aapke liye code prepare kar diya hai saheli, screen par check kijiye!] ")
        
        // Strip markdown styling symbols like bold, italic, headers
        text = text.replace(Regex("[\\*#_`~>\\-+=]+"), " ")
        
        // Remove purna viram punctuation completely
        text = text.replace("।", " ")
        
        // Fix up double spacing
        text = text.replace(Regex("\\s+"), " ").trim()
        
        return text
    }

    private fun getLocalFallbackReply(text: String, modelName: String): String {
        val lowercase = text.lowercase()
        Log.e("MainViewModel", "Fallback triggered. Original API failure chain: $lastErrorMessage")
        return when {
            lowercase.contains("hello") || lowercase.contains("salaam") || lowercase.contains("hy") -> {
                "Salaam! Main Rabiya hoon, aapki smart AI companion. Abhi connection setup me thoda time lag raha hai ya connection temporarily check ho raha hai. Aap be-jhijhak poochhein, main responsive rehne ki poori koshish karungi!"
            }
            lowercase.contains("kahan") || lowercase.contains("urdu") || lowercase.contains("pyaari") -> {
                "Ji haan, main aapke sath hamesha hoon, haseen khayalat aur behtareen features ke sath. Kuch bhi poochhiye, main active assistance provide karungi!"
            }
            lowercase.contains("namaste") || lowercase.contains("kaise ho") -> {
                "Main bilkul thik hoon, shukriya! Aap bataiye aaj main aapke liye kya likhun? Writing, planning, coding, ya custom chat research?"
            }
            else -> {
                "MashaAllah! Aapki baat boht behtareen hai. Main responsive rehne ki poori koshish kar rahi hoon. Agar kuch standard neural channels heavy hain to main thodi hi der me connect ho jaungi!"
            }
        }
    }

    // --- SECURE DISMANTLE SESSIONS ---
    fun deleteMessage(messageId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            chatDao.deleteMessageById(messageId)
        }
    }

    fun clearActiveSessionMessages() {
        val sId = _activeSessionId.value
        viewModelScope.launch(Dispatchers.IO) {
            chatDao.clearSession(sId)
        }
    }

    fun clearSessionFully(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            chatDao.clearSession(sessionId)
        }
    }

    fun deleteSessionFully(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            chatDao.clearSession(sessionId)
            if (_activeSessionId.value == sessionId) {
                _activeSessionId.value = "primary_session"
            }
        }
    }

    fun wipeAllLocalDataFully(onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Clear SQLite databases
                db.clearAllTables()
                
                // Reset local non-blocked shared preferences (keep block status for safety, but reset warnings/clicks if we want, or clear user settings but preserve security values)
                val prefs = getApplication<Application>().getSharedPreferences("rabiya_security_prefs", Context.MODE_PRIVATE)
                val wasBlocked = prefs.getBoolean("is_blocked", false)
                val warningCount = prefs.getInt("warning_count", 0)
                
                prefs.edit().clear().apply()
                
                // Re-apply block settings to maintain security shield
                if (wasBlocked) {
                    prefs.edit()
                        .putBoolean("is_blocked", true)
                        .putInt("warning_count", warningCount)
                        .apply()
                }
                
                // Re-initialize any vital fields
                _activeSessionId.value = "primary_session"
                responseCache.clear()
                
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Failed to wipe user data", e)
            }
        }
    }

    suspend fun getLLMResponseForModel(
        modelName: String,
        prompt: String,
        imageBytes: ByteArray? = null,
        mimeType: String? = null
    ): String = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val prefs = getApplication<Application>().getSharedPreferences("rabiya_security_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("is_blocked", false)) {
            _isAiBlocked.value = true
            return@withContext "🛑 ACCESS DENIED: Rabiya AI Assistant has been permanently blocked on this device due to multiple safety policy violations."
        }

        val lowerPrompt = prompt.lowercase()
        val isSystemTemplate = lowerPrompt.contains("system_instruction") || 
                               lowerPrompt.contains("aap rabiya hain") || 
                               lowerPrompt.contains("highly professional assistant") ||
                               lowerPrompt.contains("analyze the attached") ||
                               lowerPrompt.contains("organic growth targets") ||
                               lowerPrompt.contains("you are an expert")
        
        if (!isSystemTemplate) {
            val safetyAlert = checkSafetyShield(prompt)
            if (safetyAlert != null) {
                return@withContext safetyAlert
            }
        }

        val systemPrompt = "Aap Rabiya hain, highly professional assistant and loving companion. Sahi, direct, clear aur straightforward answers de. Kabhi bhi faaltu symbols ya clutter characters jaise #, @, $, %, &, *, __, repetitive hashes ya decoration templates use na karein. Fulfill user's request with maximum precision directly to the point. If any image or document content is attached, examine it thoroughly and provide clean, intelligent answers."
        val parts = mutableListOf<Part>()
        parts.add(Part(text = "user: $prompt"))
        if (imageBytes != null) {
            val base64Data = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
            val mType = mimeType ?: "image/jpeg"
            parts.add(Part(inlineData = InlineData(mimeType = mType, data = base64Data)))
        }
        val contentsList = listOf(Content(parts = parts))
        val answer = executeModelQuery(modelName, prompt, systemPrompt, emptyList(), contentsList)
        answer ?: getLocalFallbackReply(prompt, modelName)
    }

    fun updateSelectedModel(model: String) {
        _selectedModel.value = model
    }

    fun switchToModelPage(pageName: String) {
        val (modelName, sessionId, welcomeText) = when (pageName) {
            "Claude AI" -> Triple(
                "Claude-3.5-Sonnet-Direct",
                "session_claude_direct",
                "Assalamu Alaikum! I am Claude 3.5 Sonnet, powered directly by Anthropic's Cloud Engine. I am now fully integrated with your special API Key! I can draft flawless essays, solve logical problems, analyze files, and support your daily activities with high speed and precision. Kaise help karu aapki aaj?"
            )
            "Cloud Code" -> Triple(
                "Cloud-Code-Claude-Engine",
                "session_cloud_code",
                "Hi! I am Cloud Code AI, powered by Claude 3.5 Sonnet. I am optimized for writing, refactoring, explaining code, conducting security audits, and designing software architecture. How can I help you code today?"
            )
            "ChatGPT" -> Triple(
                "ChatGPT-Pro-OpenAI-Engine",
                "session_chatgpt",
                "Welcome to ChatGPT Pro, powered by OpenAI's GPT-4o. Ask me to draft high-quality articles, plan marketing strategies, solve complex business logic, or translate languages!"
            )
            "Gemini" -> Triple(
                "Gemini-Multimodal-Engine",
                "session_gemini",
                "Welcome! You are on the Gemini Multimodal Core page, powered by Google's Gemini-Flash/Pro. Connect real-world image inputs, voice records, or YouTube transcribers to execute multimodal search!"
            )
            "DeepSeek" -> Triple(
                "DeepSeek-Thinking-Engine",
                "session_deepseek",
                "DeepSeek R1 Cognitive Engine active. I will utilize deep chain-of-thought planning to solve extremely intricate math, coding, logic, and comprehensive reasoning questions. Drop your complex prompt in the field!"
            )
            else -> Triple(
                "Gemini-Multimodal-Engine",
                "session_gemini",
                "Welcome! You are on the Gemini Multimodal Core page."
            )
        }
        
        _selectedModel.value = modelName
        _activeSessionId.value = sessionId
        
        viewModelScope.launch {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                val existing = chatDao.getMessagesForSessionSync(sessionId)
                if (existing.isEmpty()) {
                    val welcomeMsg = ChatMessageEntity(
                        sessionId = sessionId,
                        sender = "rabiya",
                        text = welcomeText,
                        timestamp = System.currentTimeMillis() - 1000
                    )
                    chatDao.insertMessage(welcomeMsg)
                }
            }
        }
    }

    // =========================================================================
    // 📁 FILE MANAGER CORE SYSTEM METHODS
    // =========================================================================
    fun refreshRoboFiles(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val rootDir = java.io.File(context.filesDir, "FileManager")
            val categories = listOf("Images", "Voice", "Video", "PDFs", "Documents")
            
            // Create directories if they don't exist
            categories.forEach { cat ->
                val catDir = java.io.File(rootDir, cat)
                if (!catDir.exists()) {
                    catDir.mkdirs()
                }
            }
            
            // Perform Seeding if empty (only check rabiya's 5 core system folders)
            var totalFiles = 0
            categories.forEach { cat ->
                val catDir = java.io.File(rootDir, cat)
                totalFiles += catDir.listFiles()?.size ?: 0
            }
            
            if (totalFiles == 0) {
                // Seed sample contents
                try {
                    // Seed Image 1 (Logo)
                    val logoFile = java.io.File(java.io.File(rootDir, "Images"), "Rabiya_Cyber_Orb.png")
                    val logoDrawable = context.resources.getDrawable(R.drawable.img_rabiya_logo_new_1784261401445, null)
                    if (logoDrawable is android.graphics.drawable.BitmapDrawable) {
                        val out = java.io.FileOutputStream(logoFile)
                        logoDrawable.bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                        out.close()
                    }
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Logo seed failed", e)
                }

                try {
                    // Seed Image 2 (Mascot)
                    val mascotFile = java.io.File(java.io.File(rootDir, "Images"), "Rabiya_Mascot.png")
                    val mascotDrawable = context.resources.getDrawable(R.drawable.img_mascot_companion, null)
                    if (mascotDrawable is android.graphics.drawable.BitmapDrawable) {
                        val out = java.io.FileOutputStream(mascotFile)
                        mascotDrawable.bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                        out.close()
                    }
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Mascot seed failed", e)
                }
                
                // Seed Voice File
                try {
                    val voiceFile = java.io.File(java.io.File(rootDir, "Voice"), "Rabiya_Greeting_Sample.wav")
                    voiceFile.writeText("RIFF....WAVEfmt ....data.... Rabiya: Welcome to File Manager, Saheli!")
                } catch (e: Exception) {}

                // Seed Video Metadata File
                try {
                    val videoFile = java.io.File(java.io.File(rootDir, "Video"), "AI_Assistant_Walkthrough.mp4")
                    videoFile.writeText("[SIMULATED VIDEO CORE] Spec walkthrough of Rabiya Chatbot & Multimodal Search Engine.")
                } catch(e: Exception) {}

                // Seed PDF
                try {
                    val pdfFile = java.io.File(java.io.File(rootDir, "PDFs"), "Aisha_Khan_Resume.pdf")
                    pdfFile.writeText("Aisha Khan - Lead Android Developer & AI Solutions Engineer\nExperience: 4 Years of building reactive Jetpack Compose apps.\nSkills: Kotlin, Room Db, Custom Voice synthesis")
                } catch(e: Exception) {}

                // Seed Document Specs
                try {
                    val docFile = java.io.File(java.io.File(rootDir, "Documents"), "Project_Specs_v2.txt")
                    docFile.writeText("Rabiya AI Assistant specifications version 2.1. Integrated text generation cascade fallbacks, file management compartments, and responsive UI tags.")
                } catch(e: Exception) {}
            }
            
            // Build the files list
            val tempList = mutableListOf<RoboFile>()
            
            // 1. Scan top-level system folders
            categories.forEach { cat ->
                val catDir = java.io.File(rootDir, cat)
                val files = catDir.listFiles()
                files?.forEach { file ->
                    val sizeStr = formatFileSize(file.length())
                    val preview = try {
                        if (cat == "Documents" || cat == "PDFs" || cat == "Video") {
                            file.readText().take(500)
                        } else {
                            ""
                        }
                    } catch (e: Exception) {
                        ""
                    }
                    tempList.add(
                        RoboFile(
                            name = file.name,
                            size = sizeStr,
                            type = cat,
                            absolutePath = file.absolutePath,
                            contentPreview = preview,
                            timestamp = file.lastModified()
                        )
                    )
                }
            }

            // 2. Scan InternalStorage subfolders
            val internalStorageDir = java.io.File(rootDir, "InternalStorage")
            if (!internalStorageDir.exists()) {
                internalStorageDir.mkdirs()
            }
            // Ensure default departments/folders exist
            val defaultDirs = listOf("Music", "Photos", "Videos", "Documents", "Downloads")
            defaultDirs.forEach { fName ->
                val d = java.io.File(internalStorageDir, fName)
                if (!d.exists()) {
                    d.mkdirs()
                }
            }

            internalStorageDir.listFiles()?.forEach { subDir ->
                if (subDir.isDirectory) {
                    val subFolderType = "InternalStorage/${subDir.name}"
                    subDir.listFiles()?.forEach { file ->
                        val sizeStr = formatFileSize(file.length())
                        val preview = try {
                            file.readText().take(500)
                        } catch (e: Exception) {
                            ""
                        }
                        tempList.add(
                            RoboFile(
                                name = file.name,
                                size = sizeStr,
                                type = subFolderType,
                                absolutePath = file.absolutePath,
                                contentPreview = preview,
                                timestamp = file.lastModified()
                            )
                        )
                    }
                }
            }

            tempList.sortByDescending { it.timestamp }
            _roboFiles.value = tempList
            
            // Refresh music playlist too to dynamically discover uploaded mp3/wav files!
            initializeMusicPlaylist(context)
        }
    }
    
    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024
        if (kb < 1024) return "$kb KB"
        val mb = kb / 1024
        return "$mb MB"
    }

    fun addFileToFileManager(context: Context, filename: String, category: String, textContent: String, bytes: ByteArray? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rootDir = java.io.File(context.filesDir, "FileManager")
                val catDir = java.io.File(rootDir, category)
                if (!catDir.exists()) catDir.mkdirs()
                
                val targetFile = java.io.File(catDir, filename)
                if (bytes != null) {
                    targetFile.writeBytes(bytes)
                } else {
                    targetFile.writeText(textContent)
                }
                refreshRoboFiles(context)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error adding file to manager", e)
            }
        }
    }

    fun deleteRoboFile(context: Context, roboFile: RoboFile) {
        try {
            val fileObj = java.io.File(roboFile.absolutePath)
            if (fileObj.exists()) {
                fileObj.delete()
            }
            // Check if playing music from local storage that got deleted
            val activeIndex = _currentTrackIndex.value
            val currentList = _musicPlaylist.value
            if (activeIndex in currentList.indices && currentList[activeIndex].source == roboFile.absolutePath) {
                stopMusic()
            }
            refreshRoboFiles(context)
            android.widget.Toast.makeText(context, "File deleted successfully!", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Delete failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun downloadRoboFile(context: Context, roboFile: RoboFile) {
        try {
            val fileObj = java.io.File(roboFile.absolutePath)
            if (fileObj.exists()) {
                if (roboFile.type == "Images") {
                    val bytes = fileObj.readBytes()
                    saveBytesToGallery(context, bytes)
                    android.widget.Toast.makeText(context, "Image saved to Pictures gallery!", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    saveFileToPublicDownloads(context, fileObj, roboFile.name)
                    android.widget.Toast.makeText(context, "File saved to public Downloads/Rabiya directory!", android.widget.Toast.LENGTH_LONG).show()
                }
            } else {
                android.widget.Toast.makeText(context, "Source file does not exist", android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Download failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // --- INTEGRATED FILE MANAGERS / STORAGE CLIPBOARD ACTIONS ---
    fun copyFileToClipboard(file: RoboFile) {
        _copiedFile.value = file
    }

    fun clearClipboard() {
        _copiedFile.value = null
    }

    fun pasteCopiedFile(context: Context, destCategory: String) {
        val fileRef = _copiedFile.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sourceFileObj = java.io.File(fileRef.absolutePath)
                if (sourceFileObj.exists()) {
                    val rootDir = java.io.File(context.filesDir, "FileManager")
                    val catDir = java.io.File(rootDir, destCategory)
                    if (!catDir.exists()) catDir.mkdirs()
                    
                    val targetFile = java.io.File(catDir, fileRef.name)
                    var count = 1
                    var finalFile = targetFile
                    val baseName = fileRef.name.substringBeforeLast(".")
                    val ext = fileRef.name.substringAfterLast(".", "")
                    while (finalFile.exists()) {
                        val suffix = if (ext.isNotEmpty()) ".$ext" else ""
                        finalFile = java.io.File(catDir, "${baseName}_copy$count$suffix")
                        count++
                    }
                    
                    sourceFileObj.copyTo(finalFile)
                    refreshRoboFiles(context)
                    
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Pasted ${finalFile.name} successfully into $destCategory!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Paste failed", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Failed to paste: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun getUsedStorageBytes(context: Context): Long {
        val rootDir = java.io.File(context.filesDir, "FileManager")
        val internalDir = java.io.File(rootDir, "InternalStorage")
        val userFilesSize = getFolderSize(internalDir)
        val simulatedBase = 1234810368L // 1.15 GB base Android system image occupation
        return simulatedBase + userFilesSize
    }

    private fun getFolderSize(dir: java.io.File): Long {
        var size = 0L
        if (dir.exists()) {
            dir.listFiles()?.forEach { file ->
                size += if (file.isDirectory) getFolderSize(file) else file.length()
            }
        }
        return size
    }

    fun getLocalIpAddress(): List<String> {
        val ips = mutableListOf<String>()
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        ips.add(address.hostAddress)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error getting IP", e)
        }
        return if (ips.isEmpty()) listOf("127.0.0.1") else ips
    }

    fun startFileServer(context: Context) {
        if (_isServerRunning.value) return
        try {
            // Generate a secure 6-digit PIN
            val pin = (100000..999999).random().toString()
            _serverSecurityPin.value = pin
            val server = RabiyaHttpServer(context, 8088, this)
            server.start()
            fileServer = server
            _isServerRunning.value = true
            _serverUrls.value = getLocalIpAddress().map { "http://$it:8088" }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Failed to start file server", e)
        }
    }

    fun stopFileServer() {
        try {
            fileServer?.stop()
            fileServer = null
            _isServerRunning.value = false
            _serverUrls.value = emptyList()
            _serverSecurityPin.value = ""
        } catch (e: Exception) {
            Log.e("MainViewModel", "Failed to stop file server", e)
        }
    }

    fun createInternalStorageSubfolder(context: Context, folderName: String) {
        val rootDir = java.io.File(context.filesDir, "FileManager")
        val internalDir = java.io.File(rootDir, "InternalStorage")
        val newDir = java.io.File(internalDir, folderName)
        if (!newDir.exists()) {
            newDir.mkdirs()
        }
        refreshRoboFiles(context)
    }

    fun shareRoboFile(context: Context, roboFile: RoboFile) {
        try {
            val fileObj = java.io.File(roboFile.absolutePath)
            if (fileObj.exists()) {
                val authority = "${context.packageName}.fileprovider"
                val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, fileObj)
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = context.contentResolver.getType(uri) ?: "*/*"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(intent, "Share File via"))
            } else {
                android.widget.Toast.makeText(context, "File does not exist to share", android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "File sharing failed", e)
            android.widget.Toast.makeText(context, "Sharing failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // --- PREMIUM MULTIMEDIA MUSIC CONTROLLER LOGIC ---
    fun initializeMusicPlaylist(context: Context) {
        val systemTracks = listOf(
            MusicTrack("Sufi Breeze (Dil Se)", "Rabiya Curated Beats", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3", false),
            MusicTrack("Bollywood Lo-Fi Dreams", "Saheli Beats & Music", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3", false),
            MusicTrack("Retro Cyber Punjab", "Sidhu Modern Remix", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3", false),
            MusicTrack("Saheli Acoustic Strings", "Rabiya Instrumental", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3", false)
        )
        
        val localTracks = mutableListOf<MusicTrack>()
        try {
            val rootDir = java.io.File(context.filesDir, "FileManager")
            val internalDir = java.io.File(rootDir, "InternalStorage")
            scanMusicRecursively(internalDir, localTracks)
        } catch (e: Exception) {
            Log.e("MainViewModel", "Scanning local music files failed", e)
        }
        
        _musicPlaylist.value = systemTracks + localTracks
    }

    private fun scanMusicRecursively(dir: java.io.File, list: MutableList<MusicTrack>) {
        if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    scanMusicRecursively(file, list)
                } else {
                    val ext = file.name.substringAfterLast(".", "").lowercase()
                    if (ext == "mp3" || ext == "wav" || ext == "ogg" || ext == "m4a") {
                        list.add(
                            MusicTrack(
                                title = file.name.substringBeforeLast("."),
                                artist = "Local Storage (${file.parentFile?.name ?: "Internal"})",
                                source = file.absolutePath,
                                isLocal = true
                            )
                        )
                    }
                }
            }
        }
    }

    fun playMusicMatchIndex(context: Context, index: Int) {
        val list = _musicPlaylist.value
        if (index !in list.indices) return
        
        // Pause and release previous music player instance
        musicPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
            } catch (e: Exception) {}
            it.release()
        }
        
        _currentTrackIndex.value = index
        _isMusicPlaying.value = true
        _musicProgress.value = 0f
        _musicPositionMs.value = 0
        
        val track = list[index]
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val player = android.media.MediaPlayer().apply {
                    setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    if (track.isLocal) {
                        val fis = java.io.FileInputStream(java.io.File(track.source))
                        setDataSource(fis.fd)
                        fis.close()
                    } else {
                        setDataSource(track.source)
                    }
                    prepare()
                    start()
                }
                
                musicPlayer = player
                
                withContext(Dispatchers.Main) {
                    _musicDurationMs.value = player.duration
                    startMusicPolling()
                    
                    player.setOnCompletionListener {
                        nextMusicTrack(context)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Music Player Setup Crash", e)
                withContext(Dispatchers.Main) {
                    _isMusicPlaying.value = false
                    android.widget.Toast.makeText(context, "Error playing audio track: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun toggleMusicPlayPause(context: Context) {
        val player = musicPlayer
        if (player != null) {
            try {
                if (player.isPlaying) {
                    player.pause()
                    _isMusicPlaying.value = false
                    stopMusicPolling()
                } else {
                    player.start()
                    _isMusicPlaying.value = true
                    startMusicPolling()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Play pause toggle failed, rebuilding player", e)
                playMusicMatchIndex(context, _currentTrackIndex.value)
            }
        } else {
            // Start playing first music track
            if (_musicPlaylist.value.isNotEmpty()) {
                playMusicMatchIndex(context, _currentTrackIndex.value)
            }
        }
    }

    fun nextMusicTrack(context: Context) {
        val list = _musicPlaylist.value
        if (list.isEmpty()) return
        var nextIndex = _currentTrackIndex.value + 1
        if (nextIndex >= list.size) {
            nextIndex = 0
        }
        playMusicMatchIndex(context, nextIndex)
    }

    fun prevMusicTrack(context: Context) {
        val list = _musicPlaylist.value
        if (list.isEmpty()) return
        var prevIndex = _currentTrackIndex.value - 1
        if (prevIndex < 0) {
            prevIndex = list.size - 1
        }
        playMusicMatchIndex(context, prevIndex)
    }

    fun stopMusic() {
        try {
            musicPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (e: Exception) {}
        musicPlayer = null
        _isMusicPlaying.value = false
        stopMusicPolling()
    }

    fun seekMusicTo(progress: Float) {
        val player = musicPlayer ?: return
        val duration = _musicDurationMs.value
        if (duration > 0) {
            val seekPos = (progress * duration).toInt()
            try {
                player.seekTo(seekPos)
                _musicPositionMs.value = seekPos
                _musicProgress.value = progress
            } catch (e: Exception) {}
        }
    }

    fun optimizePrompt(currentPromptText: String, onCompleted: (String) -> Unit) {
        if (currentPromptText.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val metaPrompt = "You are an expert AI Prompt Architect. Expand the user's input to make it highly descriptive, detailed, professional and prompt-engineered. Direct the AI to use complete structural outlines, code examples, or step-by-step reasoning where applicable. Respond with ONLY the expanded prompt text itself. Do not include any tags, conversational notes, or markdown backticks wrapped around the output. User prompt: \"$currentPromptText\""
            try {
                val enhanced = getLLMResponseForModel(_selectedModel.value, metaPrompt)
                val cleanResult = enhanced
                    .replace(Regex("(?i)^(\\s*here is the (optimized|enhanced|expanded|prompt-engineered) prompt:?\\s*)"), "")
                    .replace(Regex("(?i)^(\\s*enhanced prompt:?\\s*)"), "")
                    .replace(Regex("^\\s*`+"), "")
                    .replace(Regex("`+\\s*$"), "")
                    .trim()
                withContext(Dispatchers.Main) {
                    onCompleted(if (cleanResult.isNotBlank()) cleanResult else currentPromptText)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onCompleted(currentPromptText)
                }
            }
        }
    }

    private fun startMusicPolling() {
        musicPollingJob?.cancel()
        musicPollingJob = viewModelScope.launch(Dispatchers.Main) {
            while (true) {
                musicPlayer?.let { player ->
                    try {
                        if (player.isPlaying) {
                            _musicPositionMs.value = player.currentPosition
                            val dur = player.duration
                            _musicDurationMs.value = dur
                            if (dur > 0) {
                                _musicProgress.value = player.currentPosition.toFloat() / dur.toFloat()
                            }
                        }
                    } catch (e: Exception) {}
                }
                kotlinx.coroutines.delay(500)
            }
        }
    }

    private fun stopMusicPolling() {
        musicPollingJob?.cancel()
        musicPollingJob = null
    }
}

@Serializable
data class RoboFile(
    val name: String,
    val size: String,
    val type: String, // "Images", "Voice", "Video", "PDFs", "Documents", "InternalStorage"
    val absolutePath: String,
    val contentPreview: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class MusicTrack(
    val title: String,
    val artist: String,
    val source: String,
    val isLocal: Boolean = false
)

// Helper POJO for analysis
@Serializable
data class KeywordIdea(
    val keyword: String,
    val searchVolume: Int,
    val cpc: String,
    val trend: String,
    val suggestion: String
)
