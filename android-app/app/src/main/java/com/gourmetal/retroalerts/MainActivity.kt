
package com.gourmetal.retroalerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.media.AudioManager
import android.media.ToneGenerator
import android.graphics.Canvas
import android.graphics.Movie
import android.graphics.Paint
import android.view.View
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.work.*
import kotlinx.coroutines.*
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.withContext
private const val PREFS = "retro_alerts"
private const val CHANNEL = "achievement_alerts_v2"
private const val WORK = "achievement_checker"

private fun playRetroSound(context: Context, alert: Boolean = false) {
    if (!context.getSharedPreferences(PREFS, 0).getBoolean("retro_sounds", true)) return
    try {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
        tone.startTone(if (alert) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP, if (alert) 180 else 75)
        Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 300)
    } catch (_: Exception) { }
}

data class Achievement(
    val id: String,
    val game: String,
    val title: String,
    val points: Int,
    val hardcore: Boolean,
    val date: String,
    val description: String,
    val badgeName: String,
    val gameId: String = "",
    val gameIcon: String = "",
    val trueRatio: Double = 0.0
)
data class AppTheme(
    val name: String,
    val background: Color,
    val panel: Color,
    val card: Color,
    val accent: Color,
    val text: Color,
    val muted: Color
)
private val themes = listOf(
    AppTheme("GBA • Classic Orange", Color(0xFF08090F), Color(0xFF171923), Color(0xFF222531), Color(0xFFFF941F), Color.White, Color(0xFFB8BBC8)),
    AppTheme("GBA • Midnight Black", Color(0xFF080E1B), Color(0xFF14213A), Color(0xFF1D2D4A), Color(0xFF75A9FF), Color.White, Color(0xFFB5C4DC)),
    AppTheme("GBA • Neon Purple", Color(0xFF160D24), Color(0xFF28163C), Color(0xFF38204F), Color(0xFFD38AFF), Color.White, Color(0xFFD0B9E5)),
    AppTheme("DMG • Classic Green", Color(0xFF17251A), Color(0xFF344C32), Color(0xFF486744), Color(0xFFD9F0C0), Color(0xFFF0F8E9), Color(0xFFC2D7B5)),
    AppTheme("GBA • Glacier Purple", Color(0xFF17142A), Color(0xFF34285F), Color(0xFF493B78), Color(0xFFB8A9E8), Color.White, Color(0xFFD8CFF4)),
    AppTheme("GBA • Emerald", Color(0xFF091A15), Color(0xFF15372B), Color(0xFF20503C), Color(0xFF55E0A0), Color.White, Color(0xFFB2DCC8))
)



private object KeyVault {
    private const val ALIAS = "retro_alerts_key"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
        }.generateKey()
    }

    fun save(context: Context, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.iv + cipher.doFinal(value.toByteArray())
        context.getSharedPreferences(PREFS, 0).edit()
            .putString("api_key", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    fun load(context: Context): String? = try {
        val saved = context.getSharedPreferences(PREFS, 0)
            .getString("api_key", null) ?: return null
        val data = Base64.decode(saved, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(128, data.copyOfRange(0, 12))
        )
        String(cipher.doFinal(data.copyOfRange(12, data.size)))
    } catch (_: Exception) {
        null
    }

    fun remove(context: Context) {
        context.getSharedPreferences(PREFS, 0).edit().remove("api_key").apply()
    }
}


private fun parseAchievementList(json: JSONArray): List<Achievement> {
    val byId = linkedMapOf<String, Achievement>()
    for (i in 0 until json.length()) {
        val item = json.optJSONObject(i) ?: continue
        val achievement = Achievement(
            item.optString("AchievementID"),
            item.optString("GameTitle", "Unknown game"),
            item.optString("Title", "Achievement"),
            item.optInt("Points"),
            item.optInt("HardcoreMode") == 1,
            item.optString("Date", ""),
            item.optString("Description", "No description available."),
            item.optString("BadgeName", ""),
            item.optString("GameID", ""),
            item.optString("GameIcon", ""),
            item.optDouble("TrueRatio", item.optDouble("Rarity", 0.0))
        )
        if (achievement.id.isNotBlank()) {
            val previous = byId[achievement.id]
            if (previous == null || (!previous.hardcore && achievement.hardcore)) {
                byId[achievement.id] = achievement
            }
        }
    }
    return byId.values.sortedByDescending { it.date }
}

private suspend fun getAchievements(apiKey: String): List<Achievement> =
    withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(apiKey, "UTF-8")
        val from = 946684800L // 2000-01-01 UTC
        val to = System.currentTimeMillis() / 1000L
        val url = URL(
            "https://retroachievements.org/API/API_GetAchievementsEarnedBetween.php" +
                "?u=Gourmetal&f=$from&t=$to&y=$encoded"
        )
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.connectTimeout = 20000
            connection.readTimeout = 30000
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) {
                throw Exception("Connection error: \${connection.responseCode}")
            }
            val json = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
            parseAchievementList(json)
        } finally {
            connection.disconnect()
        }
    }

private suspend fun getRecentAchievements(apiKey: String): List<Achievement> =
    withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(apiKey, "UTF-8")
        val url = URL(
            "https://retroachievements.org/API/API_GetUserRecentAchievements.php" +
                "?u=Gourmetal&m=43200&y=$encoded"
        )
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) {
                throw Exception("Connection error: \${connection.responseCode}")
            }
            val json = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
            parseAchievementList(json)
        } finally {
            connection.disconnect()
        }
    }

private fun createChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL,
            "Achievement alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Rich RetroAchievements unlock alerts"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }
}

private fun scheduleChecks(context: Context, intervalMinutes: Int = 15) {
    val request = PeriodicWorkRequestBuilder<AchievementWorker>(
        intervalMinutes.coerceAtLeast(15).toLong(), TimeUnit.MINUTES
    ).build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        WORK,
        ExistingPeriodicWorkPolicy.UPDATE,
        request
    )
}

private fun cancelChecks(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork(WORK)
}
private fun franchiseColor(game: String): Int {
    val name = game.lowercase()
    val hex = when {
        "zelda" in name || "minish cap" in name -> 0xFF70B85A
        "mario" in name || "donkey kong" in name -> 0xFFE94B35
        "pokemon" in name -> 0xFFE6B93D
        "metroid" in name -> 0xFF55C6C8
        "sonic" in name -> 0xFF3987E8
        "castlevania" in name -> 0xFF9B69D6
        "kirby" in name -> 0xFFE98AB8
        "final fantasy" in name -> 0xFF8B9ED8
        else -> 0xFFFF941F
    }
    return hex.toInt()
}

private fun gameArtworkUrl(gameIcon: String): String? {
    if (gameIcon.isBlank()) return null
    return if (gameIcon.startsWith("http")) gameIcon
    else "https://media.retroachievements.org" + if (gameIcon.startsWith("/")) gameIcon else "/$gameIcon"
}

private suspend fun notifyAchievement(context: Context, item: Achievement) {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    ) return

    createChannel(context)

    val badgeBitmap = withContext(Dispatchers.IO) {
        if (item.badgeName.isBlank()) null
        else try {
            val url = URL("https://media.retroachievements.org/Badge/${item.badgeName}.png")
            url.openConnection().getInputStream().use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) { null }
    }
    val gameBitmap = withContext(Dispatchers.IO) {
        val artworkUrl = gameArtworkUrl(item.gameIcon) ?: return@withContext null
        try {
            val connection = URL(artworkUrl).openConnection().apply {
                connectTimeout = 10000
                readTimeout = 10000
            }
            connection.getInputStream().use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) { null }
    }

    val achievementUrl = "https://retroachievements.org/achievement/${item.id}"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(achievementUrl)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val pending = PendingIntent.getActivity(
        context, item.id.hashCode(), intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val rarity = item.trueRatio.takeIf { it > 0.0 }
    val rarityLabel = when {
        rarity != null && rarity <= 5.0 -> " • ULTRA RARE"
        rarity != null && rarity <= 10.0 -> " • RARE"
        else -> ""
    }
    val mode = if (item.hardcore) "HARDCORE" else "SOFTCORE"
    val details = buildString {
        append(item.game)
        append(" • ${item.points} points • $mode")
        if (rarityLabel.isNotEmpty()) append(rarityLabel)
        append("\n\n")
        append(item.description)
    }
    val accent = franchiseColor(item.game)
    val art = gameBitmap ?: badgeBitmap
    val titleLine = item.title + rarityLabel

    val builder = NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(android.R.drawable.star_on)
        .setContentTitle("ACHIEVEMENT UNLOCKED")
        .setContentText("$titleLine • ${item.game}")
        .setSubText("$mode • ${item.points} POINTS")
        .setStyle(
            if (art != null) {
                NotificationCompat.BigPictureStyle()
                    .bigPicture(art)
                    .bigLargeIcon(badgeBitmap)
                    .setSummaryText(details)
            } else {
                NotificationCompat.BigTextStyle().bigText("$titleLine\n$details")
            }
        )
        .setContentIntent(pending)
        .setAutoCancel(true)
        .setColor(accent)
        .setColorized(true)
        .setPriority(
            if (rarity != null && rarity <= 10.0)
                NotificationCompat.PRIORITY_MAX
            else NotificationCompat.PRIORITY_DEFAULT
        )

    if (badgeBitmap != null) builder.setLargeIcon(badgeBitmap)

    NotificationManagerCompat.from(context)
        .notify(item.id.hashCode(), builder.build())
}

class AchievementWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val key = KeyVault.load(applicationContext) ?: return Result.success()
        val prefs = applicationContext.getSharedPreferences(PREFS, 0)

        return try {
            // Prefer the recent feed, but fall back to full history if that endpoint fails.
            val recent = try {
                getRecentAchievements(key)
            } catch (_: Exception) {
                emptyList()
            }
            val achievements = if (recent.isNotEmpty()) {
                recent
            } else {
                // An empty or unavailable recent feed should not disable checking.
                getAchievements(key)
            }

            if (!prefs.getBoolean("unread_initialized", false)) {
                loadMessages(applicationContext).forEach {
                    setMessageMeta(applicationContext, it.id, read = true)
                }
                prefs.edit().putBoolean("unread_initialized", true).apply()
            }

            val previousIds = prefs.getStringSet("known_unlocks", null)?.toSet()
            val lastCheck = prefs.getString("last_check", null)?.toLongOrNull() ?: 0L

            achievements.take(100).asReversed().forEach { saveMessage(applicationContext, it) }

            if (previousIds == null || lastCheck <= 0L) {
                achievements.forEach {
                    setMessageMeta(applicationContext, it.id, read = true)
                }
            } else {
                val newUnlocks = achievements.filter { item ->
                    item.id.isNotBlank() &&
                        item.id !in previousIds &&
                        achievementTimestamp(item.date) >= (lastCheck - 5 * 60 * 1000L)
                }
                newUnlocks.forEach { item ->
                    saveMessage(applicationContext, item)
                    playRetroSound(applicationContext, alert = true)
                    notifyAchievement(applicationContext, item)
                }
            }

            // Keep previously seen IDs when the recent feed rolls older items off.
            val known = previousIds.orEmpty() +
                achievements.map { it.id }.filter { it.isNotBlank() }
            prefs.edit()
                .putStringSet("known_unlocks", known)
                .putString("last_check", System.currentTimeMillis().toString())
                .apply()

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createChannel(this)
        setContent {
            var splashFinished by remember { mutableStateOf(false) }
            if (splashFinished) {
                RetroAlertsApp()
            } else {
                BootSplash { splashFinished = true }
            }
        }
    }
}

private class BootGifView(
    context: Context,
    private val onFinished: () -> Unit
) : View(context) {
    private val movie: Movie? = try {
        context.assets.open("boot-animation.gif").use { Movie.decodeStream(it) }
    } catch (_: Exception) {
        null
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val startedAt = SystemClock.uptimeMillis()
    private val playbackSpeed = 1.5f
    private val gifDuration = (movie?.duration() ?: 0).takeIf { it > 0 } ?: 3000
    private var finished = false

    init {
        setBackgroundColor(android.graphics.Color.BLACK)
        if (movie == null) post { finishSplash() }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(android.graphics.Color.BLACK)
        val gif = movie ?: return
        val elapsed = SystemClock.uptimeMillis() - startedAt
        val gifTime = (elapsed * playbackSpeed).toInt()

        if (gifTime >= gifDuration) {
            gif.setTime(gifDuration - 1)
            drawMovie(canvas, gif)
            finishSplash()
            return
        }

        gif.setTime(gifTime)
        drawMovie(canvas, gif)
        postInvalidateDelayed(16)
    }

    private fun drawMovie(canvas: Canvas, gif: Movie) {
        val sourceWidth = gif.width().coerceAtLeast(1)
        val sourceHeight = gif.height().coerceAtLeast(1)
        val scale = minOf(
            width.toFloat() / sourceWidth,
            height.toFloat() / sourceHeight
        ).coerceAtLeast(0f)
        val drawWidth = sourceWidth * scale
        val drawHeight = sourceHeight * scale
        val left = (width - drawWidth) / 2f
        val top = (height - drawHeight) / 2f
        canvas.save()
        canvas.translate(left, top)
        canvas.scale(scale, scale)
        gif.draw(canvas, 0f, 0f, paint)
        canvas.restore()
    }

    private fun finishSplash() {
        if (!finished) {
            finished = true
            post { onFinished() }
        }
    }
}

@Composable
private fun BootSplash(onFinished: () -> Unit) {
    AndroidView(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        factory = { context -> BootGifView(context, onFinished) }
    )
}

@Composable
private fun ThemeBackdrop(theme: AppTheme, animated: Boolean) {
    val motion = rememberInfiniteTransition(label = "theme_background")
    val drift by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "background_drift"
    )
    val pulse by motion.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.24f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "background_pulse"
    )
    val motionDrift = if (animated) drift else 0f
    val motionPulse = if (animated) pulse else 0.10f
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val accent = theme.accent.copy(alpha = motionPulse)
        val isGameBoy = theme.name == "Game Boy"
        val isNeon = theme.name == "Retro Neon"
        val spacing = if (isGameBoy) 34f else 46f
        for (x in 0..(w / spacing).toInt()) {
            for (y in 0..(h / spacing).toInt()) {
                val offset = if ((x + y) % 2 == 0) motionDrift * spacing else -motionDrift * spacing
                val px = x * spacing + offset
                val py = y * spacing
                if (isGameBoy) {
                    drawRect(
                        color = accent.copy(alpha = pulse * 0.45f),
                        topLeft = androidx.compose.ui.geometry.Offset(px, py),
                        size = androidx.compose.ui.geometry.Size(3f, 3f)
                    )
                } else if (isNeon) {
                    drawCircle(accent, radius = 2.5f + pulse * 7f,
                        center = androidx.compose.ui.geometry.Offset(px, py))
                } else {
                    drawCircle(accent.copy(alpha = pulse * 0.55f), radius = 1.8f,
                        center = androidx.compose.ui.geometry.Offset(px, py))
                }
            }
        }
        val bandY = (h * motionDrift)
        drawLine(
            color = theme.accent.copy(alpha = 0.035f),
            start = androidx.compose.ui.geometry.Offset(0f, bandY),
            end = androidx.compose.ui.geometry.Offset(w, bandY),
            strokeWidth = if (isGameBoy) 18f else 30f
        )
        if (!isGameBoy) {
            for (line in 0..(h / 7f).toInt()) {
                val y = line * 7f
                drawLine(
                    color = theme.accent.copy(alpha = 0.025f),
                    start = androidx.compose.ui.geometry.Offset(0f, y),
                    end = androidx.compose.ui.geometry.Offset(w, y),
                    strokeWidth = 1f
                )
            }
        }
        // Theme-specific pixel weather: falling leaves, rain, and drifting stars.
        val weather = when (theme.name) {
            "Classic Orange", "Emerald", "Game Boy" -> "leaves"
            "Midnight", "GBA Purple" -> "stars"
            else -> "rain"
        }
        for (i in 0..22) {
            val baseX = ((i * 83f + motionDrift * (if (weather == "rain") 95f else 42f)) % (w + 30f))
            val baseY = ((i * 67f + motionDrift * 230f) % (h + 30f))
            val point = androidx.compose.ui.geometry.Offset(baseX, baseY)
            when (weather) {
                "rain" -> drawLine(
                    color = theme.accent.copy(alpha = 0.20f),
                    start = point,
                    end = androidx.compose.ui.geometry.Offset(baseX - 5f, baseY + 15f),
                    strokeWidth = 1.5f
                )
                "leaves" -> drawOval(
                    color = theme.accent.copy(alpha = 0.22f),
                    topLeft = androidx.compose.ui.geometry.Offset(baseX - 3f, baseY - 5f),
                    size = androidx.compose.ui.geometry.Size(6f, 10f)
                )
                else -> {
                    val twinkle = if ((i % 3) == 0) 3.2f else 1.8f
                    drawCircle(theme.accent.copy(alpha = 0.18f + pulse * 0.3f), twinkle, point)
                }
            }
        }
    }
}

@Composable
fun RetroAlertsApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences(PREFS, 0) }

    var settings by remember { mutableStateOf(false) }
    var dashboard by remember { mutableStateOf(false) }
    var messagesScreen by remember { mutableStateOf(false) }
    var playerScreen by remember { mutableStateOf(false) }
    var messages by remember { mutableStateOf(loadMessages(context)) }
    var inboxRefresh by remember { mutableIntStateOf(0) }
    var retroSounds by remember { mutableStateOf(prefs.getBoolean("retro_sounds", true)) }
    var messageSearch by remember { mutableStateOf("") }
    var messageFilter by remember { mutableStateOf("ALL") }
    var selectedGame by remember { mutableStateOf<String?>(null) }
    var stickerAlbum by remember { mutableStateOf(false) }
    var backupText by remember { mutableStateOf("") }
    var checkMinutes by remember { mutableIntStateOf(prefs.getInt("check_minutes", 15)) }
    var quietEnabled by remember { mutableStateOf(prefs.getBoolean("quiet_enabled", false)) }
    var quietStart by remember { mutableStateOf(prefs.getString("quiet_start", "22:00") ?: "22:00") }
    var quietEnd by remember { mutableStateOf(prefs.getString("quiet_end", "08:00") ?: "08:00") }
    var apiKey by remember { mutableStateOf("") }
    var connected by remember { mutableStateOf(KeyVault.load(context) != null) }
    var achievements by remember { mutableStateOf(emptyList<Achievement>()) }
    var status by remember { mutableStateOf("READY, PLAYER?") }
    var loading by remember { mutableStateOf(false) }
    var notifications by remember { mutableStateOf(prefs.getBoolean("notifications", false)) }
    var selectedTheme by remember {
        mutableIntStateOf(prefs.getInt("selected_theme", 0).coerceIn(0, themes.lastIndex))
    }
    var animatedBackground by remember { mutableStateOf(prefs.getBoolean("animated_background", true)) }
    var pixelBorders by remember { mutableStateOf(prefs.getBoolean("pixel_borders", true)) }
    var pixelFont by remember { mutableStateOf(prefs.getBoolean("pixel_font", false)) }

    val theme = themes[selectedTheme]
    val messageMeta = remember(messages, inboxRefresh) { loadMessageMeta(context) }
    val unreadCount = messages.count { !(messageMeta[it.id]?.read ?: false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            notifications = true
            prefs.edit().putBoolean("notifications", true).apply()
            scheduleChecks(context)
            status = "NOTIFICATIONS ENABLED"
        } else {
            notifications = false
            prefs.edit().putBoolean("notifications", false).apply()
            status = "NOTIFICATION PERMISSION DENIED"
        }
    }

    fun refresh() {
        val key = KeyVault.load(context)
        if (key.isNullOrBlank()) {
            settings = true
            status = "ADD YOUR API KEY IN SETTINGS"
            return
        }

        scope.launch {
            loading = true
            status = "CONNECTING..."
            try {
                achievements = getAchievements(key)
                // Build the inbox from the full account history so it still works if the
                // separate recent-feed endpoint is unavailable. Keep the latest 100 alerts.
                if (!prefs.getBoolean("unread_initialized", false)) {
                    loadMessages(context).forEach { setMessageMeta(context, it.id, read = true) }
                    prefs.edit().putBoolean("unread_initialized", true).apply()
                }
                val hadInboxHistory = loadMessages(context).isNotEmpty()
                achievements.take(100).asReversed().forEach { item ->
                    saveMessage(context, item)
                    if (!hadInboxHistory) setMessageMeta(context, item.id, read = true)
                }
                messages = loadMessages(context)
                status = "${achievements.size} UNLOCKS LOADED • ${messages.size} ALERTS READY"
            } catch (e: Exception) {
                status = "ERROR: ${e.message ?: "CONNECTION FAILED"}"
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        if (connected) refresh()
    }

    LaunchedEffect(notifications, checkMinutes) {
        if (notifications) scheduleChecks(context, checkMinutes)
    }

    LaunchedEffect(Unit) {
        while (true) {
            messages = loadMessages(context)
            inboxRefresh++
            delay(2500)
        }
    }

    Box(
        Modifier.fillMaxSize().background(theme.background),
        contentAlignment = Alignment.TopStart
    ) {
        ThemeBackdrop(theme, animatedBackground)
        CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.copy(
                fontFamily = if (pixelFont) FontFamily.Monospace else FontFamily.Default
            )
        ) {
        Column(
            Modifier.fillMaxSize()
                .safeDrawingPadding()
                .background(theme.panel)
                .border(
                    if (pixelBorders) 3.dp else 1.dp,
                    theme.accent.copy(alpha = 0.75f),
                    if (pixelBorders) RoundedCornerShape(3.dp) else RoundedCornerShape(16.dp)
                )
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier.size(64.dp).background(theme.background, RoundedCornerShape(14.dp))
                        .border(2.dp, theme.accent, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚡", fontSize = 30.sp)
                }
                Column {
                    Text(
                        "GOURMETAL",
                        color = theme.accent,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp
                    )
                    Text("RETRO ALERTS", color = theme.muted, fontSize = 11.sp)
                    Text("PLAYER 1 • ACHIEVEMENT LOG", color = theme.accent, fontSize = 9.sp)
                }
            }

            Spacer(Modifier.height(14.dp))

            Column(
                Modifier.fillMaxWidth()
                    .background(theme.card, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Text(
                    when { dashboard -> "DASHBOARD"; messagesScreen -> "MESSAGES"; playerScreen -> "PLAYER PROFILE"; settings -> "SETTINGS"; else -> "READY, PLAYER?" },
                    color = theme.text,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Black
                )
                Text(status, color = theme.muted, fontSize = 11.sp)
                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Stat("UNLOCKS", achievements.size.toString(), Modifier.weight(1f), theme)
                    Stat("POINTS", achievements.sumOf { it.points }.toString(), Modifier.weight(1f), theme)
                    Stat("STATUS", if (connected) "LINKED" else "OFFLINE", Modifier.weight(1f), theme)
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    when { dashboard -> "RETROACHIEVEMENTS DASHBOARD"; messagesScreen -> "ALERT INBOX"; playerScreen -> "YOUR PLAYER PROFILE"; settings -> "PERSONALIZE YOUR APP"; else -> "RECENT UNLOCKS" },
                    color = theme.text,
                    fontWeight = FontWeight.Bold
                )
                if (!settings && !dashboard && !messagesScreen && !playerScreen) {
                    IconButton(onClick = { refresh() }, enabled = !loading) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = theme.accent)
                    }
                }
            }

            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = theme.accent)
                Spacer(Modifier.height(8.dp))
            }

            if (messagesScreen) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (stickerAlbum) "${achievements.size} ACCOUNT UNLOCKS" else "${messages.size} SAVED ALERTS", color = theme.muted, fontSize = 10.sp)
                    if (!stickerAlbum) {
                        TextButton(onClick = { prefs.edit().putString("messages", "[]").apply(); messages = emptyList() }) { Text("CLEAR ALL", color = theme.accent) }
                    }
                }
                val meta = messageMeta
                val collection = if (stickerAlbum) achievements else messages
                val filtered = collection.filter { item ->
                    val m = meta[item.id] ?: MessageMeta()
                    val textMatch = messageSearch.isBlank() || item.title.contains(messageSearch, true) || item.game.contains(messageSearch, true) || item.description.contains(messageSearch, true)
                    val filterMatch = if (stickerAlbum) {
                        when (messageFilter) {
                            "HARDCORE" -> item.hardcore
                            "SOFTCORE" -> !item.hardcore
                            else -> true
                        }
                    } else {
                        when (messageFilter) {
                            "UNREAD" -> !m.read
                            "READ" -> m.read
                            "FAVORITES" -> m.favorite
                            "HARDCORE" -> item.hardcore
                            "SOFTCORE" -> !item.hardcore
                            else -> true
                        }
                    }
                    textMatch && filterMatch && (selectedGame == null || selectedGame == item.game)
                }
                OutlinedTextField(value = messageSearch, onValueChange = { messageSearch = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(if (stickerAlbum) "Search unlocked achievements" else "Search messages") })
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("ALL", "UNREAD", "READ", "FAVORITES", "HARDCORE", "SOFTCORE").forEach { filter ->
                        FilterChip(selected = messageFilter == filter, onClick = { messageFilter = filter }, label = { Text(filter) })
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    FilterChip(
                        selected = stickerAlbum,
                        onClick = { stickerAlbum = !stickerAlbum },
                        label = { Text(if (stickerAlbum) "📋 LIST VIEW" else "🏅 STICKER ALBUM") }
                    )
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    FilterChip(selected = selectedGame == null, onClick = { selectedGame = null }, label = { Text("ALL GAMES") })
                    collection.map { it.game }.distinct().sorted().forEach { game ->
                        FilterChip(selected = selectedGame == game, onClick = { selectedGame = if (selectedGame == game) null else game }, label = { Text(game) })
                    }
                }
                Text(
                    "${filtered.size} MATCHING ${if (stickerAlbum) "UNLOCKS" else "ALERTS"} • ${collection.sumOf { it.points }} TOTAL POINTS",
                    color = theme.accent,
                    fontSize = 10.sp
                )
                if (filtered.isEmpty()) {
                    Text(if (stickerAlbum) "NO MATCHING UNLOCKS" else "NO MATCHING MESSAGES", color = theme.text, modifier = Modifier.padding(24.dp))
                } else if (stickerAlbum) {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        items(filtered.chunked(3)) { rowItems ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowItems.forEach { item ->
                                    Column(
                                        Modifier.weight(1f).aspectRatio(0.78f)
                                            .background(theme.card, RoundedCornerShape(13.dp))
                                            .border(1.dp, theme.accent.copy(alpha = 0.65f), RoundedCornerShape(13.dp))
                                            .padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        var stickerBadge by remember(item.id, item.badgeName) {
                                            mutableStateOf<Bitmap?>(null)
                                        }
                                        LaunchedEffect(item.badgeName) {
                                            stickerBadge = loadBadgeBitmap(item.badgeName)
                                        }
                                        if (stickerBadge != null) {
                                            Image(
                                                bitmap = stickerBadge!!.asImageBitmap(),
                                                contentDescription = "${item.title} achievement badge",
                                                modifier = Modifier.size(58.dp)
                                            )
                                        } else {
                                            Box(
                                                Modifier.size(58.dp).background(theme.background, RoundedCornerShape(9.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(if (item.hardcore) "🏆" else "🏅", fontSize = 27.sp)
                                            }
                                        }
                                        Spacer(Modifier.height(5.dp))
                                        Text(item.title, color = theme.text, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 3)
                                        Spacer(Modifier.height(4.dp))
                                        Text(item.game, color = theme.accent, fontSize = 8.sp, maxLines = 2)
                                        Text("${item.points} PTS", color = theme.muted, fontSize = 8.sp)
                                    }
                                }
                                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                } else {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filtered, key = { it.id }) { item ->
                            val m = meta[item.id] ?: MessageMeta()
                            Column(Modifier.fillMaxWidth().background(theme.card, RoundedCornerShape(12.dp)).padding(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (m.read) "READ" else "UNREAD", color = theme.accent, modifier = Modifier.weight(1f), fontSize = 10.sp)
                                    TextButton(onClick = { setMessageMeta(context, item.id, favorite = !m.favorite); messages = loadMessages(context); inboxRefresh++ }) { Text(if (m.favorite) "★ FAVORITE" else "☆ FAVORITE", color = theme.accent) }
                                    TextButton(onClick = { setMessageMeta(context, item.id, read = !m.read); messages = loadMessages(context); inboxRefresh++ }) { Text(if (m.read) "UNREAD" else "MARK READ", color = theme.accent) }
                                }
                                AchievementCard(item, theme) {
                                    scope.launch {
                                        saveMessage(context, item)
                                        messages = loadMessages(context)
                                        playRetroSound(context, alert = true)
                                        notifyAchievement(context, item)
                                        status = "ALERT RESENT • SAVED TO INBOX"
                                    }
                                }
                                TextButton(onClick = { removeMessage(context, item.id); messages = loadMessages(context) }) { Text("DELETE", color = theme.muted) }
                            }
                        }
                    }
                }
            } else if (playerScreen) {
                val totalPoints = achievements.sumOf { it.points }
                val level = (totalPoints / 1000) + 1
                val favorites = runCatching { loadMessageMeta(context).values.count { it.favorite } }.getOrDefault(0)
                val hardcoreCount = achievements.count { it.hardcore }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth().background(theme.card, RoundedCornerShape(18.dp)).border(2.dp, theme.accent, RoundedCornerShape(18.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        val savedAvatar = remember {
                            runCatching {
                                BitmapFactory.decodeResource(context.resources, R.drawable.gourmetal_avatar)
                                    ?.let { trimAvatarArtwork(it) }
                                    ?.asImageBitmap()
                            }.getOrNull()
                        }
                        if (savedAvatar != null) {
                            Image(
                                bitmap = savedAvatar,
                                contentDescription = "Gourmetal player avatar",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(142.dp)
                                    .background(theme.background, RoundedCornerShape(16.dp))
                                    .border(3.dp, theme.accent, RoundedCornerShape(16.dp))
                            )
                        } else {
                            PixelPlayerAvatar(
                                modifier = Modifier
                                    .size(142.dp)
                                    .border(3.dp, theme.accent, RoundedCornerShape(16.dp))
                            )
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("PLAYER ONE", color = theme.accent, fontWeight = FontWeight.Black, fontSize = 18.sp)
                            Text("LEVEL $level", color = theme.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("GOLDEN VOLT • RETRO HUNTER", color = theme.muted, fontSize = 9.sp)
                            Text("$totalPoints POINTS", color = theme.accent, fontWeight = FontWeight.Black, fontSize = 12.sp)
                            val pointsIntoLevel = totalPoints % 1000
                            LinearProgressIndicator(
                                progress = { pointsIntoLevel / 1000f },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = theme.accent,
                                trackColor = theme.background
                            )
                            Text("${pointsIntoLevel}/1000 XP • ${1000 - pointsIntoLevel} TO NEXT LEVEL",
                                color = theme.muted, fontSize = 8.sp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat("UNLOCKS", achievements.size.toString(), Modifier.weight(1f), theme)
                        Stat("HARDCORE", hardcoreCount.toString(), Modifier.weight(1f), theme)
                        Stat("FAVORITES", favorites.toString(), Modifier.weight(1f), theme)
                    }
                    AchievementCalendar(achievements, theme) { item ->
                        scope.launch {
                            saveMessage(context, item)
                            messages = loadMessages(context)
                            playRetroSound(context, alert = true)
                            notifyAchievement(context, item)
                            status = "ALERT RESENT • SAVED TO INBOX"
                        }
                    }
                    Column(
                        Modifier.fillMaxWidth()
                            .background(theme.card, RoundedCornerShape(if (pixelBorders) 4.dp else 14.dp))
                            .border(1.dp, theme.accent.copy(alpha = 0.55f), RoundedCornerShape(if (pixelBorders) 4.dp else 14.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Text("GAME CARTRIDGES", color = theme.accent, fontWeight = FontWeight.Black, fontSize = 16.sp)
                        Text("YOUR UNLOCK COLLECTION BY GAME", color = theme.muted, fontSize = 9.sp)
                        achievements.groupBy { it.game }.entries.sortedByDescending { it.value.size }.forEach { entry ->
                            GameCartridgeCard(entry.key, entry.value, theme, pixelBorders)
                        }
                    }
                    Column(Modifier.fillMaxWidth().background(theme.card, RoundedCornerShape(14.dp)).padding(14.dp)) {
                        Text("STICKER COLLECTION", color = theme.accent, fontWeight = FontWeight.Black, fontSize = 16.sp)
                        Text("Every unlock loaded from your RetroAchievements account becomes a collectible. Your inbox stays separate.", color = theme.muted, fontSize = 11.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("${achievements.size} STICKERS COLLECTED", color = theme.text, fontWeight = FontWeight.Bold)
                        Button(onClick = { messagesScreen = true; playerScreen = false; dashboard = false; settings = false; stickerAlbum = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = theme.accent)) {
                            Text("OPEN STICKER ALBUM", color = theme.background, fontWeight = FontWeight.Black)
                        }
                    }
                    Column(Modifier.fillMaxWidth().background(theme.card, RoundedCornerShape(14.dp)).padding(14.dp)) {
                        Text("RECENT TROPHY", color = theme.accent, fontWeight = FontWeight.Black, fontSize = 15.sp)
                        if (achievements.isNotEmpty()) {
                            val latest = achievements.first()
                            Text(latest.title, color = theme.text, fontWeight = FontWeight.Bold)
                            Text("${latest.game} • ${latest.points} PTS", color = theme.muted, fontSize = 11.sp)
                        } else Text("Your first saved unlock will appear here.", color = theme.muted, fontSize = 11.sp)
                    }
                }
            } else if (dashboard) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp, max = 470.dp),
                    factory = { viewContext ->
                        WebView(viewContext).apply {
                            setBackgroundColor(theme.background.toArgb())
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView, url: String) {
                                    val pageColor = String.format(
                                        "#%06X",
                                        0xFFFFFF and theme.background.toArgb()
                                    )
                                    view.evaluateJavascript(
                                        "document.documentElement.style.backgroundColor='$pageColor';" +
                                            "document.body.style.backgroundColor='$pageColor';",
                                        null
                                    )
                                }
                            }
                            this.settings.javaScriptEnabled = true
                            this.settings.domStorageEnabled = true
                            this.settings.mediaPlaybackRequiresUserGesture = false
                            this.settings.allowFileAccess = false
                            this.settings.loadsImagesAutomatically = true
                            this.webChromeClient = WebChromeClient()
                            loadUrl("https://gourmetal.github.io/Retroachievement-dashboard/")
                        }
                    }
                )
            } else if (!settings) {
                if (achievements.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = 150.dp)
                            .background(theme.card, RoundedCornerShape(14.dp))
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("🎮", fontSize = 32.sp)
                        Text("NO ACHIEVEMENTS TO DISPLAY", color = theme.text, fontWeight = FontWeight.Bold)
                        Text("Connect your account in Settings.", color = theme.muted, fontSize = 11.sp)
                    }
                } else {
                    LazyColumn(
                        Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {items(achievements) { item ->
        AchievementCard(item, theme) {
        scope.launch {
            saveMessage(context, item)
            messages = loadMessages(context)
            playRetroSound(context, alert = true)
            notifyAchievement(context, item)
            status = "NOTIFICATION RESENT"
        }
    }
}}
                }
            } else {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 470.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("APPEARANCE", color = theme.accent, fontWeight = FontWeight.Black)
                    themes.forEachIndexed { index, option ->
                        Row(
                            Modifier.fillMaxWidth()
                                .background(
                                    if (selectedTheme == index) theme.card else theme.background,
                                    RoundedCornerShape(12.dp)
                                )
                                .border(
                                    1.dp,
                                    if (selectedTheme == index) theme.accent else theme.muted.copy(alpha = 0.3f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    selectedTheme = index
                                    prefs.edit().putInt("selected_theme", index).apply()
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(38.dp).background(option.background, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("G", color = option.accent, fontWeight = FontWeight.Black)
                            }
                            Text(
                                option.name,
                                Modifier.weight(1f).padding(start = 12.dp),
                                color = theme.text,
                                fontWeight = FontWeight.Bold
                            )
                            if (selectedTheme == index) {
                                Icon(Icons.Default.CheckCircle, "Selected", tint = theme.accent)
                            }
                        }
                    }

                    HorizontalDivider(color = theme.muted.copy(alpha = 0.3f))

                    Text("HANDHELD COSMETICS", color = theme.accent, fontWeight = FontWeight.Black)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Animated pixel background", color = theme.text, fontWeight = FontWeight.Bold)
                            Text("Drifting stars, pixels and scanline shimmer", color = theme.muted, fontSize = 10.sp)
                        }
                        Switch(checked = animatedBackground, onCheckedChange = {
                            animatedBackground = it
                            prefs.edit().putBoolean("animated_background", it).apply()
                        })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Pixel-cut borders", color = theme.text, fontWeight = FontWeight.Bold)
                            Text("Square corners and crisp handheld frames", color = theme.muted, fontSize = 10.sp)
                        }
                        Switch(checked = pixelBorders, onCheckedChange = {
                            pixelBorders = it
                            prefs.edit().putBoolean("pixel_borders", it).apply()
                        })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Pixel font mode", color = theme.text, fontWeight = FontWeight.Bold)
                            Text("Monospaced retro lettering throughout the app", color = theme.muted, fontSize = 10.sp)
                        }
                        Switch(checked = pixelFont, onCheckedChange = {
                            pixelFont = it
                            prefs.edit().putBoolean("pixel_font", it).apply()
                        })
                    }
                    HorizontalDivider(color = theme.muted.copy(alpha = 0.3f))

                    Text("SOUND & FEEDBACK", color = theme.accent, fontWeight = FontWeight.Black)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Retro sound effects", color = theme.text, fontWeight = FontWeight.Bold)
                            Text("Arcade bleeps for taps and new unlocks", color = theme.muted, fontSize = 10.sp)
                        }
                        Switch(checked = retroSounds, onCheckedChange = { enabled ->
                            retroSounds = enabled
                            prefs.edit().putBoolean("retro_sounds", enabled).apply()
                            if (enabled) playRetroSound(context)
                        })
                    }
                    HorizontalDivider(color = theme.muted.copy(alpha = 0.3f))
                    Text("NOTIFICATIONS", color = theme.accent, fontWeight = FontWeight.Black)
                    Text("Background check frequency", color = theme.muted, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(15, 30, 60).forEach { mins ->
                            FilterChip(selected = checkMinutes == mins, onClick = { checkMinutes = mins; prefs.edit().putInt("check_minutes", mins).apply(); if (notifications) scheduleChecks(context, mins) }, label = { Text("$mins min") })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Quiet hours", Modifier.weight(1f), color = theme.text)
                        Switch(checked = quietEnabled, onCheckedChange = { quietEnabled = it; prefs.edit().putBoolean("quiet_enabled", it).apply() })
                    }
                    if (quietEnabled) {
                        OutlinedTextField(value = quietStart, onValueChange = { quietStart = it; prefs.edit().putString("quiet_start", it).apply() }, label = { Text("Quiet start (HH:mm)") }, singleLine = true)
                        OutlinedTextField(value = quietEnd, onValueChange = { quietEnd = it; prefs.edit().putString("quiet_end", it).apply() }, label = { Text("Quiet end (HH:mm)") }, singleLine = true)
                    }
                    OutlinedButton(onClick = {
                        val payload = prefs.getString("messages", "[]") ?: "[]"
                        val send = Intent(Intent.ACTION_SEND).apply { type = "application/json"; putExtra(Intent.EXTRA_SUBJECT, "Retro Alerts Backup"); putExtra(Intent.EXTRA_TEXT, payload) }
                        context.startActivity(Intent.createChooser(send, "Export backup"))
                    }, modifier = Modifier.fillMaxWidth()) { Text("EXPORT MESSAGE BACKUP", color = theme.text) }
                    OutlinedTextField(value = backupText, onValueChange = { backupText = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Paste backup JSON to restore") }, minLines = 2)
                    OutlinedButton(onClick = {
                        try { val parsed = JSONArray(backupText); prefs.edit().putString("messages", parsed.toString()).apply(); messages = loadMessages(context); backupText = ""; status = "BACKUP RESTORED" }
                        catch (_: Exception) { status = "INVALID BACKUP" }
                    }, modifier = Modifier.fillMaxWidth(), enabled = backupText.isNotBlank()) { Text("RESTORE BACKUP", color = theme.text) }

                                        Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Achievement alerts", Modifier.weight(1f), color = theme.text)

                        Switch(
                            checked = notifications,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    if (Build.VERSION.SDK_INT >= 33 &&
                                        ContextCompat.checkSelfPermission(
                                            context, Manifest.permission.POST_NOTIFICATIONS
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        notifications = true
                                        prefs.edit().putBoolean("notifications", true).apply()
                                        scheduleChecks(context)
                                        status = "NOTIFICATIONS ENABLED"
                                    }
                                } else {
                                    notifications = false
                                    prefs.edit().putBoolean("notifications", false).apply()
                                    cancelChecks(context)
                                    status = "NOTIFICATIONS DISABLED"
                                }
                            }
                        )
                                        }
                    Text(
                        if (notifications) "BACKGROUND CHECKS ARE ON" else "BACKGROUND CHECKS ARE OFF",
                        color = theme.muted,
                        fontSize = 10.sp
                    )

                    HorizontalDivider(color = theme.muted.copy(alpha = 0.3f))

                    Text("RETROACHIEVEMENTS ACCOUNT", color = theme.accent, fontWeight = FontWeight.Black)
                    Text(
                        "Your API key is encrypted and stored on this phone.",
                        color = theme.muted,
                        fontSize = 11.sp
                    )

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )

                    Button(
                        onClick = {
                            if (apiKey.isNotBlank()) {
                                try {
                                    KeyVault.save(context, apiKey.trim())
                                    apiKey = ""
                                    connected = true
                                    settings = false
                                    refresh()
                                } catch (_: Exception) {
                                    status = "COULD NOT SAVE KEY"
                                }
                            } else status = "ENTER YOUR API KEY"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
                    ) {
                        Text("SAVE KEY & CONNECT", color = theme.background)
                    }

                    if (connected) {
                        OutlinedButton(
                            onClick = {
                                KeyVault.remove(context)
                                connected = false
                                achievements = emptyList()
                                notifications = false
                                prefs.edit().putBoolean("notifications", false).remove("known_unlocks").apply()
                                cancelChecks(context)
                                status = "API KEY REMOVED"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("REMOVE SAVED API KEY", color = theme.text)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Button(
                    onClick = { playRetroSound(context); dashboard = false; messagesScreen = false; playerScreen = false; settings = false },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!settings && !dashboard) theme.accent else theme.card
                    )
                ) {
                    Text("HOME", color = if (!settings && !dashboard) theme.background else theme.text, fontSize = 10.sp, maxLines = 1, softWrap = false)
                }
                Button(
                    onClick = { playRetroSound(context); messagesScreen = true; dashboard = false; playerScreen = false; settings = false },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (messagesScreen) theme.accent else theme.card
                    )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Text("MESSAGES", color = if (messagesScreen) theme.background else theme.text, fontSize = 10.sp, maxLines = 1, softWrap = false)
                        if (unreadCount > 0) {
                            Spacer(Modifier.width(4.dp))
                            Text(unreadCount.coerceAtMost(99).toString(), color = theme.background, fontSize = 9.sp, fontWeight = FontWeight.Black,
                                modifier = Modifier.background(theme.accent, RoundedCornerShape(10.dp)).padding(horizontal = 5.dp, vertical = 2.dp))
                        }
                    }
                }
                Button(
                    onClick = { playRetroSound(context); playerScreen = true; dashboard = false; messagesScreen = false; settings = false },
                    modifier = Modifier.weight(0.85f),
                    colors = ButtonDefaults.buttonColors(containerColor = if (playerScreen) theme.accent else theme.card)
                ) { Text("PLAYER", color = if (playerScreen) theme.background else theme.text, fontSize = 9.sp, maxLines = 1, softWrap = false) }
                Button(
                    onClick = { playRetroSound(context); dashboard = true; messagesScreen = false; playerScreen = false; settings = false },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (dashboard) theme.accent else theme.card
                    )
                ) {
                    Text("DASHBOARD", color = if (dashboard) theme.background else theme.text, fontSize = 10.sp, maxLines = 1, softWrap = false)
                }
                Button(
                    onClick = { playRetroSound(context); settings = true; dashboard = false; messagesScreen = false; playerScreen = false },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (settings) theme.accent else theme.card
                    )
                ) {
                    Text("SETTINGS", color = if (settings) theme.background else theme.text, fontSize = 10.sp, maxLines = 1, softWrap = false)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "GBA EDITION • VERSION 0.8.0",
                Modifier.align(Alignment.CenterHorizontally),
                color = theme.muted,
                fontSize = 9.sp,
                letterSpacing = 1.sp
            )
        }
    }
}


private fun trimAvatarArtwork(source: Bitmap): Bitmap {
    if (source.width < 2 || source.height < 2) return source
    val background = source.getPixel(0, 0)
    val bgR = android.graphics.Color.red(background)
    val bgG = android.graphics.Color.green(background)
    val bgB = android.graphics.Color.blue(background)
    var left = source.width
    var top = source.height
    var right = -1
    var bottom = -1

    // The supplied portrait has a large flat dark area below the character.
    // Find the actual artwork bounds so that empty canvas doesn't consume half the frame.
    for (y in 0 until source.height) {
        for (x in 0 until source.width) {
            val pixel = source.getPixel(x, y)
            val alpha = android.graphics.Color.alpha(pixel)
            val delta = kotlin.math.abs(android.graphics.Color.red(pixel) - bgR) +
                kotlin.math.abs(android.graphics.Color.green(pixel) - bgG) +
                kotlin.math.abs(android.graphics.Color.blue(pixel) - bgB)
            if (alpha > 24 && (alpha < 245 || delta > 28)) {
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x)
                bottom = maxOf(bottom, y)
            }
        }
    }
    if (right < left || bottom < top) return source

    val padX = (source.width * 0.035f).toInt()
    val padY = (source.height * 0.035f).toInt()
    val cropLeft = (left - padX).coerceAtLeast(0)
    val cropTop = (top - padY).coerceAtLeast(0)
    val cropRight = (right + padX + 1).coerceAtMost(source.width)
    val cropBottom = (bottom + padY + 1).coerceAtMost(source.height)
    if (cropRight - cropLeft < source.width / 4 || cropBottom - cropTop < source.height / 4) return source
    return Bitmap.createBitmap(source, cropLeft, cropTop, cropRight - cropLeft, cropBottom - cropTop)
        }

}

@Composable
private fun PixelPlayerAvatar(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cell = size.minDimension / 20f
        fun block(x: Int, y: Int, w: Int, h: Int, color: Color) {
            drawRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(x * cell, y * cell),
                size = androidx.compose.ui.geometry.Size(w * cell, h * cell)
            )
        }

        val void = Color(0xFF101018)
        val shadow = Color(0xFF171923)
        val hood = Color(0xFF242936)
        val hoodie = Color(0xFF11151E)
        val trim = Color(0xFF596174)
        val gold = Color(0xFFFFC928)
        val glow = Color(0xFFFFE58A)

        block(0, 0, 20, 20, void)

        // Electric gold lightning framing the character.
        block(2, 1, 2, 2, gold)
        block(1, 3, 2, 2, gold)
        block(2, 5, 2, 2, gold)
        block(3, 7, 2, 2, gold)
        block(1, 9, 2, 2, glow)
        block(3, 11, 2, 2, gold)
        block(16, 1, 2, 2, gold)
        block(17, 3, 2, 2, gold)
        block(16, 5, 2, 2, gold)
        block(15, 7, 2, 2, gold)
        block(17, 9, 2, 2, glow)
        block(15, 11, 2, 2, gold)

        // Hood silhouette and shoulders.
        block(5, 4, 10, 2, trim)
        block(4, 6, 12, 8, hood)
        block(3, 12, 14, 6, hood)
        block(2, 16, 16, 3, hoodie)
        block(5, 17, 10, 3, hoodie)

        // Face and black cap.
        block(6, 6, 8, 8, Color(0xFF080A10))
        block(6, 4, 8, 2, Color(0xFF050609))
        block(5, 5, 10, 2, Color(0xFF050609))
        block(4, 6, 12, 1, Color(0xFF050609))
        block(7, 3, 6, 1, trim)
        block(8, 4, 4, 1, Color(0xFF343B4A))

        // Glowing golden eyes.
        block(7, 9, 2, 2, gold)
        block(11, 9, 2, 2, gold)
        block(7, 9, 1, 1, glow)
        block(11, 9, 1, 1, glow)
        block(8, 12, 4, 1, trim)

        // Hoodie collar, zipper, and electric details.
        block(7, 14, 6, 2, Color(0xFF080A10))
        block(9, 15, 2, 4, gold)
        block(9, 16, 1, 2, glow)
        block(4, 15, 2, 2, trim)
        block(14, 15, 2, 2, trim)
        block(3, 19, 5, 1, gold)
        block(12, 19, 5, 1, gold)
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier, theme: AppTheme) {
    Column(modifier.background(theme.background, RoundedCornerShape(9.dp)).padding(8.dp)) {
        Text(label, color = theme.muted, fontSize = 9.sp)
        Text(value, color = theme.text, fontWeight = FontWeight.Black, fontSize = 13.sp)
    }
}

private suspend fun loadBadgeBitmap(badgeName: String): Bitmap? =
    withContext(Dispatchers.IO) {
        if (badgeName.isBlank()) return@withContext null

        try {
            val url = URL(
                "https://media.retroachievements.org/Badge/$badgeName.png"
            )
            val connection = url.openConnection()
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            connection.getInputStream().use {
                BitmapFactory.decodeStream(it)
            }
        } catch (_: Exception) {
            null
        }
    }

private suspend fun loadRemoteBitmap(url: String?): Bitmap? =
    withContext(Dispatchers.IO) {
        if (url.isNullOrBlank()) return@withContext null
        try {
            val connection = URL(url).openConnection().apply {
                connectTimeout = 10000
                readTimeout = 10000
            }
            connection.getInputStream().use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) {
            null
        }
    }

@Composable
@Composable
private fun GameCartridgeCard(
    game: String,
    unlocks: List<Achievement>,
    theme: AppTheme,
    pixelBorders: Boolean
) {
    var artwork by remember(game, unlocks.firstOrNull()?.gameIcon) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(game, unlocks.firstOrNull()?.gameIcon) {
        artwork = loadRemoteBitmap(gameArtworkUrl(unlocks.firstOrNull()?.gameIcon))
    }
    val points = unlocks.sumOf { it.points }
    val hardcore = unlocks.count { it.hardcore }
    val shape = if (pixelBorders) RoundedCornerShape(3.dp) else RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth()
            .background(theme.background, shape)
            .border(2.dp, Color(franchiseColor(game)).copy(alpha = 0.85f), shape)
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(width = 62.dp, height = 76.dp)
                .background(theme.card, shape)
                .border(1.dp, theme.accent.copy(alpha = 0.55f), shape),
            contentAlignment = Alignment.Center
        ) {
            if (artwork != null) {
                Image(
                    bitmap = artwork!!.asImageBitmap(),
                    contentDescription = "$game cartridge artwork",
                    modifier = Modifier.fillMaxSize().padding(3.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text("🎮", fontSize = 25.sp)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("GAME PAK", color = theme.muted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text(game, color = theme.text, fontWeight = FontWeight.Black, fontSize = 12.sp, maxLines = 2)
            Text("${unlocks.size} UNLOCKS • $points PTS", color = theme.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text("$hardcore HARDCORE • ${unlocks.size - hardcore} SOFTCORE", color = theme.muted, fontSize = 8.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("★", color = theme.accent, fontSize = 16.sp)
            Text("${unlocks.size}", color = theme.text, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
    }
}

private fun AchievementCard(
    item: Achievement,
    theme: AppTheme,
    onResend: () -> Unit
) {
    var badge by remember(item.badgeName) { mutableStateOf<Bitmap?>(null) }
    var gameArt by remember(item.gameIcon) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(item.badgeName) {
        badge = loadBadgeBitmap(item.badgeName)
    }
    LaunchedEffect(item.gameIcon) {
        gameArt = loadRemoteBitmap(gameArtworkUrl(item.gameIcon))
    }

    val rarityLabel = when {
        item.trueRatio > 0.0 && item.trueRatio <= 5.0 -> "ULTRA RARE"
        item.trueRatio > 0.0 && item.trueRatio <= 10.0 -> "RARE"
        else -> null
    }

    Column(
        Modifier.fillMaxWidth()
            .background(theme.card, RoundedCornerShape(4.dp))
            .border(2.dp, theme.accent.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
            .padding(12.dp)
    ) {
        Text(
            "ACHIEVEMENT UNLOCKED",
            color = theme.accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(8.dp))

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (badge != null) {
                Image(
                    bitmap = badge!!.asImageBitmap(),
                    contentDescription = "${item.title} achievement badge",
                    modifier = Modifier.size(68.dp)
                )
            } else if (gameArt != null) {
                Image(
                    bitmap = gameArt!!.asImageBitmap(),
                    contentDescription = "${item.game} game artwork",
                    modifier = Modifier.size(68.dp)
                )
            } else {
                Box(
                    Modifier.size(68.dp).background(theme.background, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) { Text(if (item.hardcore) "🏆" else "🏅", fontSize = 30.sp) }
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.game, color = theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(item.title, color = theme.text, fontWeight = FontWeight.Black, fontSize = 16.sp)
                if (rarityLabel != null) {
                    Text("✦ $rarityLabel", color = theme.accent, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            item.description.ifBlank { "No description available." },
            color = theme.text,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(10.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (item.hardcore) "HARDCORE MODE" else "SOFTCORE MODE",
                color = if (item.hardcore) theme.accent else theme.muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text("${item.points} POINTS", color = theme.text, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }

        if (item.date.isNotBlank()) {
            Spacer(Modifier.height(5.dp))
            Text("UNLOCKED • ${item.date}", color = theme.muted, fontSize = 9.sp)
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onResend, modifier = Modifier.fillMaxWidth()) {
            Text("RESEND FULL ALERT", color = Color.White)
        }
    }
}

private fun parseAchievementDate(value: String): java.time.LocalDate? {
    return runCatching { java.time.LocalDate.parse(value.trim().take(10)) }.getOrNull()
}

private fun achievementTimestamp(value: String): Long {
    val instant = runCatching { java.time.Instant.parse(value) }.getOrNull()
    if (instant != null) return instant.toEpochMilli()
    val localDateTime = runCatching {
        java.time.LocalDateTime.parse(
            value.take(19),
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        )
    }.getOrNull()
    if (localDateTime != null) return localDateTime.toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
    return parseAchievementDate(value)?.atStartOfDay(java.time.ZoneOffset.UTC)
        ?.toInstant()?.toEpochMilli() ?: 0L
}

@Composable
private fun AchievementCalendar(
    achievements: List<Achievement>,
    theme: AppTheme,
    onResend: (Achievement) -> Unit
) {
    val latestMonth = remember(achievements) {
        achievements.mapNotNull { parseAchievementDate(it.date) }
            .maxOrNull()?.let { java.time.YearMonth.from(it) } ?: java.time.YearMonth.now()
    }
    var month by remember(achievements) { mutableStateOf(latestMonth) }
    var selectedDate by remember(achievements) { mutableStateOf<java.time.LocalDate?>(null) }

    val datedAchievements = remember(achievements) {
        achievements.mapNotNull { item -> parseAchievementDate(item.date)?.let { it to item } }
    }
    val dayAchievements = remember(datedAchievements, selectedDate) {
        datedAchievements.filter { it.first == selectedDate }
            .map { it.second }.sortedByDescending { it.date }
    }
    val unlockDays = remember(datedAchievements, month) {
        datedAchievements.map { it.first }
            .filter { java.time.YearMonth.from(it) == month }
            .groupingBy { it.dayOfMonth }.eachCount()
    }

    Column(
        Modifier.fillMaxWidth()
            .background(theme.card, RoundedCornerShape(14.dp))
            .border(1.dp, theme.accent.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("ACHIEVEMENT HISTORY", color = theme.accent, fontWeight = FontWeight.Black, fontSize = 15.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { month = month.minusMonths(1); selectedDate = null }) {
                Icon(Icons.Default.ChevronLeft, "Previous month", tint = theme.accent)
            }
            Text("${month.month.name} ${month.year}", color = theme.text, fontWeight = FontWeight.Bold)
            IconButton(onClick = { month = month.plusMonths(1); selectedDate = null }) {
                Icon(Icons.Default.ChevronRight, "Next month", tint = theme.accent)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                Text(it, Modifier.weight(1f), color = theme.muted, fontSize = 10.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        val offset = month.atDay(1).dayOfWeek.value % 7
        val cellCount = ((offset + month.lengthOfMonth() + 6) / 7) * 7
        (0 until cellCount step 7).forEach { weekStart ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                (weekStart until weekStart + 7).forEach { cell ->
                    val day = cell - offset + 1
                    val valid = day in 1..month.lengthOfMonth()
                    val date = if (valid) month.atDay(day) else null
                    val count = if (valid) unlockDays[day] ?: 0 else 0
                    val selected = date != null && date == selectedDate
                    Box(
                        Modifier.weight(1f).aspectRatio(1f)
                            .background(
                                if (selected) theme.text else if (count > 0) theme.accent else theme.background,
                                RoundedCornerShape(7.dp)
                            )
                            .then(if (valid && count > 0) Modifier.clickable {
                                selectedDate = if (selectedDate == date) null else date
                            } else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        if (valid) Text(
                            if (count > 0) "$day\n$count" else day.toString(),
                            color = if (selected || count > 0) theme.background else theme.muted,
                            fontSize = if (count > 0) 9.sp else 10.sp,
                            fontWeight = if (count > 0 || selected) FontWeight.Black else FontWeight.Normal,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 10.sp
                        )
                    }
                }
            }
        }
        Text("${unlockDays.values.sum()} UNLOCKS • ${unlockDays.size} ACTIVE DAYS",
            color = theme.muted, fontSize = 10.sp)

        if (selectedDate != null) {
            HorizontalDivider(color = theme.accent.copy(alpha = 0.45f))
            Text(
                "UNLOCKS ON ${selectedDate!!.month.name} ${selectedDate!!.dayOfMonth}, ${selectedDate!!.year}",
                color = theme.accent, fontWeight = FontWeight.Black, fontSize = 12.sp
            )
            if (dayAchievements.isEmpty()) {
                Text("No achievements were recorded for this day.", color = theme.muted, fontSize = 11.sp)
            } else {
                dayAchievements.forEach { item ->
                    Column(
                        Modifier.fillMaxWidth().background(theme.background, RoundedCornerShape(9.dp)).padding(9.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(item.title, color = theme.text, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(item.game, color = theme.accent, fontSize = 10.sp)
                        Text("${item.points} PTS • ${if (item.hardcore) "HARDCORE" else "SOFTCORE"}",
                            color = theme.muted, fontSize = 9.sp)
                        Text(item.description, color = theme.muted, fontSize = 10.sp)
                        TextButton(onClick = { onResend(item) }, contentPadding = PaddingValues(0.dp)) {
                            Text("RESEND ALERT", color = theme.accent, fontSize = 10.sp)
                        }
                    }
                }
            }
        } else {
            Text("Tap a highlighted day to see its achievements.", color = theme.muted, fontSize = 10.sp)
        }
    }
}
private data class MessageMeta(val read: Boolean = false, val favorite: Boolean = false)

private fun loadMessageMeta(context: Context): Map<String, MessageMeta> {
    val json = org.json.JSONObject(context.getSharedPreferences(PREFS, 0).getString("message_meta", "{}") ?: "{}")
    return json.keys().asSequence().associateWith { id ->
        val value = json.optJSONObject(id) ?: org.json.JSONObject()
        MessageMeta(value.optBoolean("read"), value.optBoolean("favorite"))
    }
}

private fun setMessageMeta(context: Context, id: String, read: Boolean? = null, favorite: Boolean? = null) {
    val prefs = context.getSharedPreferences(PREFS, 0)
    val json = org.json.JSONObject(prefs.getString("message_meta", "{}") ?: "{}")
    val value = json.optJSONObject(id) ?: org.json.JSONObject()
    if (read != null) value.put("read", read)
    if (favorite != null) value.put("favorite", favorite)
    json.put(id, value)
    prefs.edit().putString("message_meta", json.toString()).apply()
}

private fun removeMessage(context: Context, id: String) {
    val prefs = context.getSharedPreferences(PREFS, 0)
    val old = JSONArray(prefs.getString("messages", "[]") ?: "[]")
    val next = JSONArray()
    for (i in 0 until old.length()) {
        val item = old.optJSONObject(i) ?: continue
        if (item.optString("id") != id) next.put(item)
    }
    val meta = org.json.JSONObject(prefs.getString("message_meta", "{}") ?: "{}").apply { remove(id) }
    prefs.edit().putString("messages", next.toString()).putString("message_meta", meta.toString()).apply()
}

private fun saveMessage(context: Context, item: Achievement) {
    val prefs = context.getSharedPreferences(PREFS, 0)
    val messages = JSONArray(prefs.getString("messages", "[]") ?: "[]")
    val entry = org.json.JSONObject().apply {
        put("id", item.id)
        put("game", item.game)
        put("title", item.title)
        put("points", item.points)
        put("hardcore", item.hardcore)
        put("date", item.date)
        put("description", item.description)
        put("badgeName", item.badgeName)
        put("gameIcon", item.gameIcon)
        put("trueRatio", item.trueRatio)
        put("savedAt", System.currentTimeMillis())
    }
    val updated = JSONArray().put(entry)
    for (i in 0 until minOf(messages.length(), 99)) {
        val old = messages.optJSONObject(i) ?: continue
        if (old.optString("id") != item.id) updated.put(old)
    }
    prefs.edit().putString("messages", updated.toString()).apply()
}

private fun loadMessages(context: Context): List<Achievement> {
    val messages = JSONArray(
        context.getSharedPreferences(PREFS, 0).getString("messages", "[]") ?: "[]"
    )
    return (0 until messages.length()).mapNotNull { i ->
        val item = messages.optJSONObject(i) ?: return@mapNotNull null
        Achievement(
            item.optString("id"), item.optString("game"), item.optString("title"),
            item.optInt("points"), item.optBoolean("hardcore"), item.optString("date"),
            item.optString("description"), item.optString("badgeName"),
            gameIcon = item.optString("gameIcon"),
            trueRatio = item.optDouble("trueRatio")
        )
    }
}

