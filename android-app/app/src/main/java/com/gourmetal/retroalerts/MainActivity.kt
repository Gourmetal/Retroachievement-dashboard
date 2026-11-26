
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
import android.graphics.Typeface
import android.view.View
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.discord.socialsdk.DiscordSocialSdkInit
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.*
import androidx.work.*
import kotlinx.coroutines.*
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.DatagramSocket
import java.net.DatagramPacket
import java.net.InetAddress
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
data class RetroGame(
    val id: String,
    val title: String,
    val console: String,
    val total: Int,
    val earned: Int,
    val lastPlayed: String,
    val image: String = ""
) {
    val progress: Int get() = if (total > 0) ((earned * 100f) / total).toInt().coerceIn(0, 100) else 0
}
data class GameAchievement(
    val id: String,
    val title: String,
    val description: String,
    val points: Int,
    val earned: Boolean,
    val hardcore: Boolean,
    val badgeName: String,
    val trueRatio: Double
)
data class GameRoadmap(val game: RetroGame, val achievements: List<GameAchievement>)
private fun loadBacklog(context: Context): Map<String, String> {
    val json = org.json.JSONObject(context.getSharedPreferences(PREFS, 0).getString("game_backlog", "{}") ?: "{}")
    return json.keys().asSequence().associateWith { json.optString(it, "WANT TO PLAY") }
}
private fun saveBacklog(context: Context, id: String, status: String) {
    val prefs = context.getSharedPreferences(PREFS, 0)
    val json = org.json.JSONObject(prefs.getString("game_backlog", "{}") ?: "{}")
    if (status == "NONE") json.remove(id) else json.put(id, status)
    prefs.edit().putString("game_backlog", json.toString()).apply()
}
private data class SaveJournalEntry(val id: String, val game: String, val savePoint: String, val notes: String, val savedAt: Long)
private fun loadSaveJournal(context: Context): List<SaveJournalEntry> {
    val raw = context.getSharedPreferences(PREFS, 0).getString("save_point_journal", "[]") ?: "[]"
    val arr = JSONArray(raw)
    return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { SaveJournalEntry(it.optString("id"), it.optString("game"), it.optString("savePoint"), it.optString("notes"), it.optLong("savedAt")) } }
}
private fun persistSaveJournal(context: Context, entries: List<SaveJournalEntry>) {
    val arr = JSONArray()
    entries.forEach { e -> arr.put(org.json.JSONObject().apply { put("id", e.id); put("game", e.game); put("savePoint", e.savePoint); put("notes", e.notes); put("savedAt", e.savedAt) }) }
    context.getSharedPreferences(PREFS, 0).edit().putString("save_point_journal", arr.toString()).apply()
}
private fun loadMissableFlags(context: Context): Set<String> =
    context.getSharedPreferences(PREFS, 0).getStringSet("missable_flags", emptySet())?.toSet() ?: emptySet()
private fun saveMissableFlag(context: Context, id: String, flagged: Boolean) {
    val prefs = context.getSharedPreferences(PREFS, 0)
    val flags = prefs.getStringSet("missable_flags", emptySet())?.toMutableSet() ?: mutableSetOf()
    if (flagged) flags.add(id) else flags.remove(id)
    prefs.edit().putStringSet("missable_flags", flags).apply()
}

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
                throw Exception("Connection error: ${connection.responseCode}")
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
                throw Exception("Connection error: ${connection.responseCode}")
            }
            val json = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
            parseAchievementList(json)
        } finally {
            connection.disconnect()
        }
    }

private suspend fun getCompletionGames(apiKey: String): List<RetroGame> = withContext(Dispatchers.IO) {
    val encoded = URLEncoder.encode(apiKey, "UTF-8")
    val url = URL("https://retroachievements.org/API/API_GetUserCompletionProgress.php?u=Gourmetal&c=500&o=0&y=$encoded")
    val connection = url.openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 20000
        connection.readTimeout = 30000
        if (connection.responseCode !in 200..299) throw Exception("Game library error: ${connection.responseCode}")
        val root = org.json.JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        val rows = root.optJSONArray("Results") ?: JSONArray()
        (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val id = row.optString("GameID", row.optString("ID"))
            if (id.isBlank()) return@mapNotNull null
            RetroGame(
                id, row.optString("Title", "Unknown game"), row.optString("ConsoleName", ""),
                row.optInt("MaxPossible"), row.optInt("NumAwarded"),
                row.optString("MostRecentAward", row.optString("LastPlayed", "")),
                row.optString("ImageIcon", "")
            )
        }
    } finally { connection.disconnect() }
}
private suspend fun getGameRoadmap(apiKey: String, game: RetroGame): GameRoadmap = withContext(Dispatchers.IO) {
    val encoded = URLEncoder.encode(apiKey, "UTF-8")
    val url = URL("https://retroachievements.org/API/API_GetGameInfoAndUserProgress.php?u=Gourmetal&g=${game.id}&y=$encoded")
    val connection = url.openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 20000
        connection.readTimeout = 30000
        if (connection.responseCode !in 200..299) throw Exception("Game details error: ${connection.responseCode}")
        val root = org.json.JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        val raw = root.optJSONObject("Achievements") ?: org.json.JSONObject()
        val items = mutableListOf<GameAchievement>()
        val keys = raw.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val a = raw.optJSONObject(key) ?: continue
            val earnedDate = a.optString("DateEarned", "")
            val hardcoreDate = a.optString("DateEarnedHardcore", "")
            items.add(GameAchievement(
                a.optString("ID", key), a.optString("Title", "Achievement"),
                a.optString("Description", ""), a.optInt("Points"),
                earnedDate.isNotBlank(), hardcoreDate.isNotBlank(),
                a.optString("BadgeName", ""), a.optDouble("TrueRatio", a.optDouble("Rarity", 0.0))
            ))
        }
        GameRoadmap(game, items.sortedWith(compareBy<GameAchievement> { it.earned }.thenByDescending { it.points }))
    } finally { connection.disconnect() }
}
private fun possibleMissableHint(description: String): Boolean {
    val d = description.lowercase()
    return listOf("missable", "before you", "without taking", "no damage", "one chance", "never", "don't", "do not", "first time", "point of no return", "before defeating", "before leaving").any { it in d }
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

private fun isWithinQuietHours(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS, 0)
    if (!prefs.getBoolean("quiet_enabled", false)) return false
    val start = runCatching { java.time.LocalTime.parse(prefs.getString("quiet_start", "22:00") ?: "22:00") }.getOrNull() ?: return false
    val end = runCatching { java.time.LocalTime.parse(prefs.getString("quiet_end", "08:00") ?: "08:00") }.getOrNull() ?: return false
    if (start == end) return false
    val now = java.time.LocalTime.now()
    return if (start.isBefore(end)) !now.isBefore(start) && now.isBefore(end) else !now.isBefore(start) || now.isBefore(end)
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
                    if (!isWithinQuietHours(applicationContext)) {
                        playRetroSound(applicationContext, alert = true)
                        notifyAchievement(applicationContext, item)
                    }
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
private suspend fun queryRetroArchStatus(host: String, port: Int = 55355): String = withContext(Dispatchers.IO) {
    val address = InetAddress.getByName(host.trim())
    DatagramSocket().use { socket ->
        socket.soTimeout = 1800
        val request = "GET_STATUS".toByteArray(Charsets.UTF_8)
        socket.send(DatagramPacket(request, request.size, address, port))
        val buffer = ByteArray(4096)
        val response = DatagramPacket(buffer, buffer.size)
        socket.receive(response)
        val raw = String(response.data, 0, response.length, Charsets.UTF_8).trim()
        if (raw.contains("CONTENTLESS", ignoreCase = true)) {
            "CONNECTED • NO GAME LOADED"
        } else {
            val match = Regex("GET_STATUS\\s+(PLAYING|PAUSED)\\s+([^,]+),([^,]+),crc32=([0-9A-Fa-f]+)", RegexOption.IGNORE_CASE).find(raw)
            if (match != null) {
                val state = match.groupValues[1].uppercase()
                val system = match.groupValues[2].trim()
                val fileName = match.groupValues[3].substringAfterLast('/').substringAfterLast('\\')
                val title = fileName.substringBeforeLast('.', fileName).replace('_', ' ').replace('-', ' ').trim()
                "$state • $title • $system • CRC ${match.groupValues[4].uppercase()}"
            } else {
                "CONNECTED • ${raw.take(180)}"
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    private val discordCallbacks = Handler(Looper.getMainLooper())
    private val discordCallbackPump = object : Runnable {
        override fun run() {
            runCatching { nativeRunCallbacks() }
            discordCallbacks.postDelayed(this, 16)
        }
    }

    private external fun nativeInitialize(applicationId: Long)
    private external fun nativeUpdatePresence(details: String, state: String)
    private external fun nativeRunCallbacks()
    private external fun nativeDisconnect()

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

    fun testDiscordSdkClientCreation(applicationId: Long) {
        if (applicationId <= 0L) throw IllegalArgumentException("Invalid Discord Application ID")
        android.util.Log.i("RetroAlertsDiscord", "Loading Discord SDK native library")
        System.loadLibrary("retroalerts_discord")
        android.util.Log.i("RetroAlertsDiscord", "Initializing Discord Android activity/context")
        DiscordSocialSdkInit.setEngineActivity(this)
        android.util.Log.i("RetroAlertsDiscord", "Discord Android context initialized; creating native client")
        nativeInitialize(applicationId)
        android.util.Log.i("RetroAlertsDiscord", "Native Discord client created successfully")
    }

    override fun onDestroy() {
        discordCallbacks.removeCallbacks(discordCallbackPump)
        super.onDestroy()
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
private fun ThemeBackdrop(
    theme: AppTheme,
    animated: Boolean,
    wallpaper: String
) {
    val motion = rememberInfiniteTransition(label = "theme_background")
    val drift by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
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
        when (wallpaper) {
            "SUNSET" -> {
                drawRect(brush = Brush.verticalGradient(listOf(Color(0xFF24152F), Color(0xFF713B48), Color(0xFFDE854D), theme.background)))
                drawCircle(Color(0xFFFFB36B).copy(alpha = 0.30f), radius = w * 0.15f, center = androidx.compose.ui.geometry.Offset(w * 0.76f, h * 0.34f))
                for (i in 0..7) {
                    val y = h * (0.69f + i * 0.035f)
                    drawRect(Color(0xFFFFB36B).copy(alpha = 0.10f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Size(w, 2f))
                }
                for (i in 0..15) {
                    val bw = 18f + (i % 4) * 11f
                    val bh = 35f + ((i * 29) % 75).toFloat()
                    val x = i * (w / 15f) - bw / 2f
                    drawRect(Color(0xFF171323).copy(alpha = 0.62f), androidx.compose.ui.geometry.Offset(x, h - bh), androidx.compose.ui.geometry.Size(bw, bh))
                }
            }
            "PIXEL FOREST" -> {
                drawRect(brush = Brush.verticalGradient(listOf(Color(0xFF102B2A), Color(0xFF17483A), theme.background)))
                for (i in 0..18) {
                    val x = i * (w / 18f) - 12f
                    val height = 45f + ((i * 37) % 95).toFloat()
                    val top = h - height
                    val green = if (i % 2 == 0) Color(0xFF245D43) else Color(0xFF1B4938)
                    drawRect(green.copy(alpha = 0.42f), androidx.compose.ui.geometry.Offset(x, top), androidx.compose.ui.geometry.Size(24f, height))
                    drawRect(green.copy(alpha = 0.48f), androidx.compose.ui.geometry.Offset(x - 10f, top + 12f), androidx.compose.ui.geometry.Size(44f, 20f))
                    drawRect(Color(0xFF091B19).copy(alpha = 0.55f), androidx.compose.ui.geometry.Offset(x + 8f, top + 28f), androidx.compose.ui.geometry.Size(7f, height - 28f))
                }
                for (i in 0..20) {
                    val x = (i * 71f + motionDrift * 42f) % w
                    val y = (i * 53f + motionDrift * 120f) % (h * 0.72f)
                    drawRect(theme.accent.copy(alpha = 0.16f), androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Size(3f, 3f))
                }
            }
            "CYBER CITY" -> {
                drawRect(brush = Brush.verticalGradient(listOf(Color(0xFF10152E), Color(0xFF241B45), theme.background)))
                for (i in 0..17) {
                    val bw = 20f + (i % 3) * 12f
                    val bh = 55f + ((i * 31) % 115).toFloat()
                    val x = i * (w / 17f) - 8f
                    val top = h - bh
                    drawRect(Color(0xFF11182D).copy(alpha = 0.76f), androidx.compose.ui.geometry.Offset(x, top), androidx.compose.ui.geometry.Size(bw, bh))
                    for (window in 0..2) {
                        val wy = top + 10f + window * 15f
                        drawRect(theme.accent.copy(alpha = 0.18f), androidx.compose.ui.geometry.Offset(x + 5f, wy), androidx.compose.ui.geometry.Size(4f, 5f))
                    }
                }
                for (i in 0..10) {
                    val y = h * (0.35f + i * 0.045f)
                    drawLine(theme.accent.copy(alpha = 0.08f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(w, y), 1f)
                }
            }
            "STARFIELD" -> {
                drawRect(Color(0xFF080D20).copy(alpha = 0.72f))
                for (i in 0..64) {
                    val x = (i * 97f + motionDrift * (20f + (i % 4) * 18f)) % w
                    val y = (i * 59f) % h
                    val radius = if (i % 7 == 0) 2.8f else 1.4f
                    drawRect(theme.accent.copy(alpha = 0.12f + motionPulse * 0.38f), androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f))
                }
            }
            "RAINY WINDOW" -> {
                drawRect(brush = Brush.verticalGradient(listOf(Color(0xFF101D31), Color(0xFF182A3B), theme.background)))
                for (i in 0..54) {
                    val x = (i * 43f + motionDrift * 105f) % w
                    val y = (i * 71f + motionDrift * 210f) % h
                    drawLine(theme.accent.copy(alpha = 0.18f), androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Offset(x - 3f, y + 13f), 1.5f)
                }
                drawRect(Color(0xFF9AC7E8).copy(alpha = 0.035f), androidx.compose.ui.geometry.Offset(w * 0.49f, 0f), androidx.compose.ui.geometry.Size(2f, h))
            }
            else -> {
                val isGameBoy = theme.name == "DMG • Classic Green"
                val isNeon = theme.name == "GBA • Neon Purple"
                val spacing = if (isGameBoy) 34f else 46f
                for (x in 0..(w / spacing).toInt()) {
                    for (y in 0..(h / spacing).toInt()) {
                        val offset = if ((x + y) % 2 == 0) motionDrift * spacing else -motionDrift * spacing
                        val px = x * spacing + offset
                        val py = y * spacing
                        if (isGameBoy) {
                            drawRect(accent.copy(alpha = if (animated) 0.32f else 0.16f), androidx.compose.ui.geometry.Offset(px, py), androidx.compose.ui.geometry.Size(3f, 3f))
                        } else if (isNeon) {
                            drawCircle(accent.copy(alpha = if (animated) 0.34f else 0.18f), radius = 3.5f + motionPulse * 8f, center = androidx.compose.ui.geometry.Offset(px, py))
                        } else {
                            drawCircle(accent.copy(alpha = if (animated) 0.30f else 0.14f), radius = 2.5f, center = androidx.compose.ui.geometry.Offset(px, py))
                        }
                    }
                }
                val bandY = h * motionDrift
                drawLine(theme.accent.copy(alpha = if (animated) 0.10f else 0.045f), androidx.compose.ui.geometry.Offset(0f, bandY), androidx.compose.ui.geometry.Offset(w, bandY), if (isGameBoy) 18f else 30f)
                if (!isGameBoy) {
                    for (line in 0..(h / 7f).toInt()) {
                        val y = line * 7f
                        drawLine(theme.accent.copy(alpha = if (animated) 0.055f else 0.025f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(w, y), 1f)
                    }
                }
                val weather = when (theme.name) {
                    "GBA • Classic Orange", "GBA • Emerald", "DMG • Classic Green" -> "leaves"
                    else -> "stars"
                }
                for (i in 0..22) {
                    val baseX = ((i * 83f + motionDrift * 42f) % (w + 30f))
                    val baseY = ((i * 67f + motionDrift * 230f) % (h + 30f))
                    if (weather == "leaves") {
                        drawRect(theme.accent.copy(alpha = 0.24f), androidx.compose.ui.geometry.Offset(baseX - 3f, baseY - 3f), androidx.compose.ui.geometry.Size(6f, 6f))
                        drawRect(theme.accent.copy(alpha = 0.13f), androidx.compose.ui.geometry.Offset(baseX + 3f, baseY + 3f), androidx.compose.ui.geometry.Size(3f, 3f))
                    } else {
                        drawCircle(theme.accent.copy(alpha = 0.18f + motionPulse * 0.3f), if (i % 3 == 0) 3.2f else 1.8f, androidx.compose.ui.geometry.Offset(baseX, baseY))
                    }
                }
            }
        }
    }
}


@Composable
private fun CrtOverlay(theme: AppTheme, animated: Boolean) {
    val motion = rememberInfiniteTransition(label = "crt_scan_motion")
    val scanPosition by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "crt_scan_position"
    )
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val lineGap = 5.dp.toPx()
        val lineHeight = 1.25.dp.toPx()
        var y = 0f
        while (y < h) {
            drawRect(
                color = Color.Black.copy(alpha = 0.19f),
                topLeft = androidx.compose.ui.geometry.Offset(0f, y),
                size = androidx.compose.ui.geometry.Size(w, lineHeight)
            )
            y += lineGap
        }
        val bandY = h * (if (animated) scanPosition else 0.5f)
        val bandHeight = 18.dp.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    theme.accent.copy(alpha = 0.10f),
                    Color.Transparent
                ),
                startY = (bandY - bandHeight).coerceAtLeast(0f),
                endY = (bandY + bandHeight).coerceAtMost(h)
            ),
            topLeft = androidx.compose.ui.geometry.Offset(0f, (bandY - bandHeight).coerceAtLeast(0f)),
            size = androidx.compose.ui.geometry.Size(w, (bandHeight * 2f).coerceAtMost(h))
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f)),
                center = androidx.compose.ui.geometry.Offset(w / 2f, h / 2f),
                radius = maxOf(w, h) * 0.78f
            ),
            size = size
        )
    }
}

@Composable
fun RetroAlertsApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences(PREFS, 0) }

    var hubScreen by remember { mutableStateOf(true) }
    var settings by remember { mutableStateOf(false) }
    var dashboard by remember { mutableStateOf(false) }
    var dashboardSection by remember { mutableStateOf("library") }
    var librarySearch by remember { mutableStateOf("") }
    var journalEntries by remember { mutableStateOf(loadSaveJournal(context)) }
    var journalGame by remember { mutableStateOf("") }
    var journalSavePoint by remember { mutableStateOf("") }
    var journalNotes by remember { mutableStateOf("") }
    var customFontUri by remember { mutableStateOf(prefs.getString("custom_font_uri", null)) }
    var customFontFamily by remember { mutableStateOf<FontFamily?>(null) }
    var appFontScale by remember { mutableFloatStateOf(prefs.getFloat("app_font_scale", 1.2f).coerceIn(1.0f, 1.5f)) }
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
    var discordApplicationId by remember { mutableStateOf(prefs.getString("discord_application_id", "1574859797902266368") ?: "1574859797902266368") }
    var retroArchTarget by remember { mutableStateOf(prefs.getString("discord_retroarch_target", "PHONE") ?: "PHONE") }
    var retroArchHost by remember { mutableStateOf(prefs.getString("discord_retroarch_host", "") ?: "") }
    var retroArchTestStatus by remember { mutableStateOf("NOT TESTED") }
    var retroArchTesting by remember { mutableStateOf(false) }
    var discordTestStatus by remember { mutableStateOf("NOT TESTED") }
    var discordTesting by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(KeyVault.load(context) != null) }
    var achievements by remember { mutableStateOf(emptyList<Achievement>()) }
    var recentAchievements by remember { mutableStateOf(emptyList<Achievement>()) }
    var roadmapScreen by remember { mutableStateOf(false) }
    var achievementRadarScreen by remember { mutableStateOf(false) }
    var backlogScreen by remember { mutableStateOf(false) }
    var achievementWorldMapScreen by remember { mutableStateOf(false) }
    var collectionShowcaseScreen by remember { mutableStateOf(false) }
    var gameLibrary by remember { mutableStateOf(emptyList<RetroGame>()) }
    var selectedRoadmap by remember { mutableStateOf<GameRoadmap?>(null) }
    var selectedAchievementDetail by remember { mutableStateOf<GameAchievement?>(null) }
    var roadmapLoading by remember { mutableStateOf(false) }
    var backlog by remember { mutableStateOf(loadBacklog(context)) }
    var missableFlags by remember { mutableStateOf(loadMissableFlags(context)) }
    var backlogFilter by remember { mutableStateOf("ALL") }
    var status by remember { mutableStateOf("READY, PLAYER?") }
    var loading by remember { mutableStateOf(false) }
    var notifications by remember { mutableStateOf(prefs.getBoolean("notifications", false)) }
    var selectedTheme by remember {
        mutableIntStateOf(prefs.getInt("selected_theme", 0).coerceIn(0, themes.lastIndex))
    }
    var animatedBackground by remember { mutableStateOf(prefs.getBoolean("animated_background", true)) }
    var pixelBorders by remember { mutableStateOf(prefs.getBoolean("pixel_borders", true)) }
    var pixelFont by remember { mutableStateOf(prefs.getBoolean("pixel_font", false)) }
    var crtEffects by remember { mutableStateOf(prefs.getBoolean("crt_effects", false)) }
    var wallpaperStyle by remember { mutableStateOf(prefs.getString("wallpaper_style", "PIXEL WEATHER") ?: "PIXEL WEATHER") }
    var cartridgeStyle by remember { mutableStateOf(prefs.getString("cartridge_style", "GBA SHELL") ?: "GBA SHELL") }
    var retroHqEnabled by remember { mutableStateOf(false) }

    val wallpaperOptions = listOf("PIXEL WEATHER", "STARFIELD", "SUNSET", "PIXEL FOREST", "CYBER CITY", "RAINY WINDOW")
    val cartridgeOptions = listOf("GBA SHELL", "MIDNIGHT", "NEON", "ARCADE")
    val theme = themes[selectedTheme]
    LaunchedEffect(customFontUri) { customFontFamily = try { val u = customFontUri?.let { Uri.parse(it) }; if (u == null) null else context.contentResolver.openFileDescriptor(u, "r")?.use { FontFamily(Typeface.Builder(it.fileDescriptor).build()) } } catch (_: Exception) { null } }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) try { context.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION); customFontUri = u.toString(); prefs.edit().putString("custom_font_uri", u.toString()).apply(); status = "CUSTOM FONT LOADED" } catch (_: Exception) { status = "COULD NOT LOAD FONT" } }
    val messageMeta = remember(messages, inboxRefresh) { loadMessageMeta(context) }
    val unreadCount = messages.count { !(messageMeta[it.id]?.read ?: false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            notifications = true
            prefs.edit().putBoolean("notifications", true).apply()
            scheduleChecks(context, checkMinutes)
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
                gameLibrary = try { getCompletionGames(key) } catch (_: Exception) { emptyList() }
                recentAchievements = try {
                    getRecentAchievements(key).ifEmpty { achievements.take(20) }
                } catch (_: Exception) {
                    achievements.take(20)
                }
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
        ThemeBackdrop(theme, animatedBackground, wallpaperStyle)
        val baseDensity = LocalDensity.current
        val adjustedDensity = Density(
            density = baseDensity.density,
            fontScale = baseDensity.fontScale * appFontScale
        )
        CompositionLocalProvider(
            LocalDensity provides adjustedDensity,
            LocalTextStyle provides LocalTextStyle.current.copy(
                fontFamily = customFontFamily ?: if (pixelFont) FontFamily.Monospace else FontFamily.Default
            )
        ) {
        Column(
            Modifier.fillMaxSize()
                .safeDrawingPadding()
                .background(theme.panel.copy(alpha = 0.72f))
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
                if (!hubScreen) {
                    IconButton(onClick = {
                        playRetroSound(context)
                        hubScreen = true
                        settings = false
                        dashboard = false
                        dashboardSection = "library"
                        librarySearch = ""
                        messagesScreen = false
                        playerScreen = false
                        roadmapScreen = false
                        achievementRadarScreen = false
                        backlogScreen = false
                        achievementWorldMapScreen = false
                        collectionShowcaseScreen = false
                        selectedRoadmap = null
                        selectedAchievementDetail = null
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back to home", tint = theme.accent)
                    }
                }
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

            if (hubScreen) {
                if (retroHqEnabled) {
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column { Text("RETRO HQ", color = theme.accent, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp); Text("YOUR PERSONAL GAMING ROOM", color = theme.muted, fontSize = 9.sp) }
                            OutlinedButton(onClick = { retroHqEnabled = false; prefs.edit().putBoolean("retro_hq_enabled", false).apply() }) { Text("CLASSIC HUB", color = theme.accent, fontSize = 10.sp) }
                        }
                        Column(Modifier.fillMaxWidth().background(theme.card, RoundedCornerShape(12.dp)).border(2.dp, theme.accent, RoundedCornerShape(12.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("▶ PLAYER ONE", color = theme.accent, fontSize = 10.sp, fontWeight = FontWeight.Black); Text(if (connected) "● ONLINE" else "○ OFFLINE", color = theme.muted, fontSize = 9.sp) }
                            Box(Modifier.fillMaxWidth().height(142.dp).background(Color(0xFF080B10), RoundedCornerShape(10.dp)).border(3.dp, theme.accent, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("GOURMETAL", color = theme.accent, fontSize = 23.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp); Text("ACHIEVEMENT COMMAND CENTER", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold); Text(if (loading) "SCANNING..." else "${achievements.size} UNLOCKS • ${gameLibrary.size} GAMES", color = Color(0xFFB9C7C0), fontSize = 10.sp) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { Stat("UNLOCKS", achievements.size.toString(), Modifier.weight(1f), theme); Stat("POINTS", achievements.sumOf { it.points }.toString(), Modifier.weight(1f), theme); Stat("GAMES", gameLibrary.size.toString(), Modifier.weight(1f), theme) }
                            OutlinedButton(onClick = { refresh() }, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text(if (loading) "REFRESHING..." else "↻ SCAN MY COLLECTION", color = theme.accent) }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("CARTRIDGE SHELF", color = theme.text, fontWeight = FontWeight.Black, fontSize = 14.sp); TextButton(onClick = { hubScreen = false; dashboard = true; dashboardSection = "library"; settings = false }) { Text("VIEW ALL ›", color = theme.accent, fontSize = 10.sp) } }
                        if (gameLibrary.isEmpty()) Text("Connect your account and scan your collection to fill this shelf.", color = theme.muted, fontSize = 11.sp)
                        else gameLibrary.take(4).forEach { game -> Row(Modifier.fillMaxWidth().background(theme.card, RoundedCornerShape(8.dp)).border(1.dp, theme.accent.copy(alpha = 0.55f), RoundedCornerShape(8.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Box(Modifier.size(42.dp).background(theme.background, RoundedCornerShape(6.dp)).border(2.dp, theme.accent, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) { Text("🎮", fontSize = 22.sp) }; Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(game.title, color = theme.text, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1); Text("${game.console} • ${game.progress}% COMPLETE", color = theme.muted, fontSize = 9.sp); LinearProgressIndicator(progress = { game.progress / 100f }, modifier = Modifier.fillMaxWidth().height(5.dp), color = theme.accent, trackColor = theme.background) } } }
                        Text("ROOM CONSOLES", color = theme.text, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        val roomItems = listOf(Triple("ACHIEVEMENT RADAR", "Find your next completion target", "radar"), Triple("TROPHY ROOM", "Show off your unlocked achievements", "trophies"), Triple("HUNTING ASSISTANT", "Roadmaps and missable targets", "roadmap"), Triple("SAVE POINT JOURNAL", "Your notes and save points", "journal"), Triple("PLAYER PROFILE", "Your stats and collections", "player"), Triple("BACKLOG MANAGER", "Plan what to play next", "backlog"), Triple("MESSAGES", "Your achievement alert inbox", "messages"))
                        roomItems.chunked(2).forEach { pair -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { pair.forEach { item -> Column(Modifier.weight(1f).heightIn(min = 82.dp).background(theme.card, RoundedCornerShape(8.dp)).border(1.dp, theme.accent.copy(alpha = 0.65f), RoundedCornerShape(8.dp)).clickable { hubScreen = false; settings = false; messagesScreen = item.third == "messages"; dashboard = item.third == "trophies" || item.third == "journal"; dashboardSection = item.third; playerScreen = item.third == "player"; roadmapScreen = item.third == "roadmap"; achievementRadarScreen = item.third == "radar"; backlogScreen = item.third == "backlog"; if (item.third == "messages") messages = loadMessages(context) }.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text("▣ " + item.first, color = theme.accent, fontSize = 10.sp, fontWeight = FontWeight.Black); Text(item.second, color = theme.muted, fontSize = 9.sp) } }; if (pair.size == 1) Spacer(Modifier.weight(1f)) } }
                        OutlinedButton(onClick = { settings = true; hubScreen = false }, modifier = Modifier.fillMaxWidth()) { Text("⚙ SETTINGS & ACCOUNT", color = theme.accent) }
                    }
                } else {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        Modifier.fillMaxWidth()
                            .background(theme.card, RoundedCornerShape(8.dp))
                            .border(2.dp, theme.accent, RoundedCornerShape(8.dp))
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("GOURMETAL", color = theme.accent, fontSize = 25.sp,
                            fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                        Text("RETRO ACHIEVEMENTS HUB", color = theme.text, fontSize = 11.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Text("PLAYER ONE • READY TO PLAY", color = theme.muted, fontSize = 9.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("CHOOSE YOUR DESTINATION", color = theme.text,
                            fontWeight = FontWeight.Black, fontSize = 14.sp)
                    }
                    val hubItems = listOf(
                        Triple("HOME", "Recent unlocks and live account stats", "home"),
                        Triple("GAME LIBRARY", "Browse games, search and filter your collection", "library"),
                        Triple("ACHIEVEMENT RADAR", "Find your closest games to 100% completion", "radar"),
                        Triple("TROPHY ROOM", "Achievement showcase and your trophy wall", "trophies"),
                        Triple("HUNTING ASSISTANT", "Roadmaps, unlock conditions and missable flags", "roadmap"),
                        Triple("SAVE POINT JOURNAL", "Record save points, progress and personal notes", "journal"),
                        Triple("PLAYER PROFILE", "Your player card, stats and collections", "player"),
                        Triple("BACKLOG MANAGER", "Track games you want to play or finish", "backlog"),
                        Triple("ACHIEVEMENT WORLD MAP", "Explore your progress across four game-world regions", "worldmap"),
                        Triple("GAME COLLECTION SHOWCASE", "A display shelf for your complete game collection", "showcase")
                    )
                    hubItems.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { item ->
                                Column(
                                    Modifier.weight(1f).heightIn(min = 108.dp)
                                        .background(theme.card, RoundedCornerShape(7.dp))
                                        .border(1.5.dp, theme.accent, RoundedCornerShape(7.dp))
                                        .clickable {
                                            hubScreen = false
                                            settings = false
                                            messagesScreen = false
                                            dashboard = item.third == "library" || item.third == "trophies" || item.third == "journal"
                                            dashboardSection = item.third
                                            librarySearch = ""
                                            playerScreen = item.third == "player"
                                            roadmapScreen = item.third == "roadmap"
                                            achievementRadarScreen = item.third == "radar"
                                            backlogScreen = item.third == "backlog"
                                            achievementWorldMapScreen = item.third == "worldmap"
                                            collectionShowcaseScreen = item.third == "showcase"
                                            if (item.third == "home") {
                                                dashboard = false; playerScreen = false
                                                roadmapScreen = false; achievementRadarScreen = false; backlogScreen = false
                                                achievementWorldMapScreen = false; collectionShowcaseScreen = false
                                            }
                                            if (dashboard) status = "GOURME HUB • IN-APP EDITION"
                                        }
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    Text("▸ " + item.first, color = theme.accent,
                                        fontWeight = FontWeight.Black, fontSize = 12.sp)
                                    Text(item.second, color = theme.muted, fontSize = 10.sp)
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    Text("${achievements.size} UNLOCKS • ${gameLibrary.size} GAMES IN YOUR LIBRARY",
                        color = theme.accent, fontSize = 10.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally))
                    OutlinedButton(
                        onClick = { settings = true; hubScreen = false },
                        modifier = Modifier.fillMaxWidth(),
                        border = androidx.compose.foundation.BorderStroke(1.dp, theme.accent)
                    ) { Text("SETTINGS & ACCOUNT CONNECTION", color = theme.accent) }
                }
                }
            } else {
            Spacer(Modifier.height(14.dp))

            Column(
                Modifier.fillMaxWidth()
                    .background(theme.card, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Text(
                    when { achievementWorldMapScreen -> "ACHIEVEMENT WORLD MAP"; collectionShowcaseScreen -> "GAME COLLECTION SHOWCASE"; achievementRadarScreen -> "ACHIEVEMENT RADAR"; roadmapScreen -> "ACHIEVEMENT ROADMAP"; backlogScreen -> "BACKLOG MANAGER"; dashboard -> "DASHBOARD"; messagesScreen -> "MESSAGES"; playerScreen -> "PLAYER PROFILE"; settings -> "SETTINGS"; else -> "READY, PLAYER?" },
                    color = theme.text,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Black
                )
                Text(status, color = theme.muted, fontSize = 11.sp)
                Spacer(Modifier.height(12.dp))

                if (!settings) {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Stat("UNLOCKS", achievements.size.toString(), Modifier.weight(1f), theme)
                        Stat("POINTS", achievements.sumOf { it.points }.toString(), Modifier.weight(1f), theme)
                        Stat("STATUS", if (connected) "LINKED" else "OFFLINE", Modifier.weight(1f), theme)
                    }
                } else {
                    Text(achievements.size.toString() + " UNLOCKS • " + (if (connected) "ACCOUNT LINKED" else "ACCOUNT OFFLINE"), color = theme.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    when { achievementWorldMapScreen -> "YOUR ACHIEVEMENT JOURNEY"; collectionShowcaseScreen -> "YOUR GAME SHELF"; achievementRadarScreen -> "YOUR NEXT COMPLETION TARGETS"; roadmapScreen -> "GAME ACHIEVEMENT ROADMAP"; backlogScreen -> "YOUR GAME BACKLOG"; dashboard -> "RETROACHIEVEMENTS DASHBOARD"; messagesScreen -> "ALERT INBOX"; playerScreen -> "YOUR PLAYER PROFILE"; settings -> "PERSONALIZE YOUR APP"; else -> "RECENT UNLOCKS" },
                    color = theme.text,
                    fontWeight = FontWeight.Bold
                )
                if (!settings && !dashboard && !messagesScreen && !playerScreen && !roadmapScreen && !backlogScreen && !achievementWorldMapScreen && !collectionShowcaseScreen) {
                    IconButton(onClick = { refresh() }, enabled = !loading) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = theme.accent)
                    }
                }
            }

            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = theme.accent)
                Spacer(Modifier.height(8.dp))
            }

            selectedAchievementDetail?.let { a ->
                val hinted = possibleMissableHint(a.description)
                val manuallyFlagged = a.id in missableFlags
                var detailBadge by remember(a.id, a.badgeName) { mutableStateOf<Bitmap?>(null) }
                LaunchedEffect(a.id, a.badgeName) { detailBadge = loadBadgeBitmap(a.badgeName) }
                AlertDialog(
                    onDismissRequest = { selectedAchievementDetail = null },
                    containerColor = theme.card,
                    title = { Text(a.title, color = theme.accent, fontWeight = FontWeight.Black) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(selectedRoadmap?.game?.title ?: "RETROACHIEVEMENTS", color = theme.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            detailBadge?.let { badge -> Image(bitmap = badge.asImageBitmap(), contentDescription = "${a.title} badge", modifier = Modifier.size(64.dp)) }
                            Text("${a.points} POINTS • ${if (a.earned) "UNLOCKED" else "LOCKED"}", color = theme.muted, fontSize = 10.sp)
                            Text(if (a.earned) "UNLOCKED ACHIEVEMENT" else "WHAT YOU NEED TO DO", color = theme.accent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text(a.description.ifBlank { "No unlock condition was supplied by RetroAchievements for this achievement." }, color = theme.text, fontSize = 13.sp)
                            if (!a.earned && (hinted || manuallyFlagged)) {
                                Text(if (manuallyFlagged) "⚠ YOU FLAGGED THIS AS NEEDING SPECIAL ATTENTION." else "⚠ THIS DESCRIPTION MAY CONTAIN A SPECIAL OR MISSABLE CONDITION. PLEASE VERIFY IT.", color = theme.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    confirmButton = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!a.earned) TextButton(onClick = {
                                saveMissableFlag(context, a.id, !manuallyFlagged)
                                missableFlags = loadMissableFlags(context)
                            }) { Text(if (manuallyFlagged) "REMOVE FLAG" else "FLAG IT", color = theme.accent) }
                            TextButton(onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://retroachievements.org/achievement/${a.id}")))
                            }) { Text("OPEN RA PAGE", color = theme.accent) }
                            TextButton(onClick = { selectedAchievementDetail = null }) { Text("CLOSE", color = theme.accent) }
                        }
                    }
                )
            }

            if (achievementWorldMapScreen) {
                val regions = listOf(
                    Triple("STARTER PLAINS", "0–24% • BEGIN YOUR QUEST", gameLibrary.filter { it.progress < 25 }),
                    Triple("ADVENTURE FOREST", "25–74% • BUILD MOMENTUM", gameLibrary.filter { it.progress in 25..74 }),
                    Triple("CHAMPION PEAKS", "75–99% • CLOSE TO THE TOP", gameLibrary.filter { it.progress in 75..99 }),
                    Triple("100% LEGENDS ISLE", "100% • COMPLETED WORLDS", gameLibrary.filter { it.progress >= 100 })
                )
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("YOUR PROGRESS, MAPPED INTO FOUR GAME WORLDS", color = theme.muted, fontSize = 10.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Stat("WORLDS", regions.count { it.third.isNotEmpty() }.toString(), Modifier.weight(1f), theme)
                        Stat("GAMES", gameLibrary.size.toString(), Modifier.weight(1f), theme)
                        Stat("UNLOCKS", achievements.size.toString(), Modifier.weight(1f), theme)
                    }
                    if (gameLibrary.isEmpty()) {
                        Text("Connect your RetroAchievements account and refresh Home to reveal your world map.", color = theme.text, modifier = Modifier.padding(12.dp))
                    } else {
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            items(regions, key = { it.first }) { region ->
                                Column(Modifier.fillMaxWidth().background(theme.card, RoundedCornerShape(12.dp)).border(1.5.dp, theme.accent.copy(alpha = if (region.third.isNotEmpty()) 0.9f else 0.25f), RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(region.first, color = theme.accent, fontWeight = FontWeight.Black, fontSize = 12.sp)
                                            Text(region.second, color = theme.muted, fontSize = 8.sp)
                                        }
                                        Text("${region.third.size} GAMES", color = theme.text, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                    }
                                    if (region.third.isEmpty()) Text("No games in this region yet", color = theme.muted, fontSize = 9.sp)
                                    else region.third.take(3).forEach { game ->
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("◆", color = theme.accent, fontSize = 12.sp)
                                            Column(Modifier.weight(1f)) {
                                                Text(game.title, color = theme.text, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                                Text(game.console, color = theme.muted, fontSize = 8.sp)
                                            }
                                            Text("${game.progress}%", color = theme.accent, fontWeight = FontWeight.Black, fontSize = 11.sp)
                                        }
                                    }
                                    if (region.third.size > 3) Text("+ ${region.third.size - 3} MORE GAMES", color = theme.muted, fontSize = 8.sp)
                                }
                            }
                        }
                    }
                }
            } else if (collectionShowcaseScreen) {
                val showcaseGames = gameLibrary.sortedWith(compareByDescending<RetroGame> { it.progress }.thenBy { it.title })
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("YOUR PERSONAL RETRO GAME DISPLAY • ${showcaseGames.size} CARTRIDGES", color = theme.muted, fontSize = 10.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Stat("COLLECTED", showcaseGames.size.toString(), Modifier.weight(1f), theme)
                        Stat("100% CLUB", showcaseGames.count { it.progress == 100 }.toString(), Modifier.weight(1f), theme)
                        Stat("POINTS", achievements.sumOf { it.points }.toString(), Modifier.weight(1f), theme)
                    }
                    if (showcaseGames.isEmpty()) {
                        Text("Your showcase is waiting. Connect your account and refresh Home to load your games.", color = theme.text, modifier = Modifier.padding(12.dp))
                    } else {
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            items(showcaseGames, key = { it.id }) { game ->
                                Column(Modifier.fillMaxWidth().background(theme.card, RoundedCornerShape(11.dp)).border(1.5.dp, theme.accent.copy(alpha = 0.65f), RoundedCornerShape(11.dp)).padding(11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Box(Modifier.size(width = 54.dp, height = 66.dp).background(theme.background, RoundedCornerShape(topStart = 5.dp, bottomStart = 5.dp)).border(2.dp, theme.accent, RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
                                            Text("🎮", fontSize = 25.sp)
                                        }
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(game.title, color = theme.text, fontWeight = FontWeight.Black, fontSize = 12.sp, maxLines = 2)
                                            Text(game.console.uppercase(), color = theme.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            Text("${game.earned} / ${game.total} ACHIEVEMENTS", color = theme.muted, fontSize = 9.sp)
                                            Text(if (game.progress == 100) "★ COMPLETED" else "LAST PLAYED: ${game.lastPlayed}", color = theme.muted, fontSize = 8.sp)
                                        }
                                        Text("${game.progress}%", color = theme.accent, fontSize = 17.sp, fontWeight = FontWeight.Black)
                                    }
                                    LinearProgressIndicator(progress = { game.progress / 100f }, modifier = Modifier.fillMaxWidth().height(6.dp), color = theme.accent, trackColor = theme.background)
                                }
                            }
                        }
                    }
                }
            } else if (achievementRadarScreen) {
                val radarGames = gameLibrary
                    .filter { it.total > 0 && it.earned < it.total }
                    .sortedWith(compareBy<RetroGame> { it.total - it.earned }.thenByDescending { it.progress })
                    .take(30)
                val nearlyComplete = radarGames.count { it.progress >= 75 }
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("SCAN YOUR GAME COLLECTION", color = theme.accent, fontWeight = FontWeight.Black, fontSize = 12.sp)
                            Text("Ranked by fewest achievements remaining", color = theme.muted, fontSize = 10.sp)
                        }
                        Text("◉ RADAR", color = theme.accent, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Stat("ACTIVE GAMES", radarGames.size.toString(), Modifier.weight(1f), theme)
                        Stat("NEAR FINISH", nearlyComplete.toString(), Modifier.weight(1f), theme)
                        Stat("READY TO HUNT", radarGames.count { it.progress >= 90 }.toString(), Modifier.weight(1f), theme)
                    }
                    if (gameLibrary.isEmpty()) {
                        Text("Connect your RetroAchievements account and refresh Home to scan your games.", color = theme.text, modifier = Modifier.padding(12.dp))
                    } else if (radarGames.isEmpty()) {
                        Text("Radar is clear! No unfinished games were found in the loaded library.", color = theme.accent, modifier = Modifier.padding(12.dp))
                    } else {
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(radarGames, key = { it.id }) { game ->
                                val remaining = (game.total - game.earned).coerceAtLeast(0)
                                val tier = when {
                                    game.progress >= 90 -> "FINAL STRETCH"
                                    game.progress >= 75 -> "NEAR COMPLETION"
                                    game.progress >= 40 -> "ACTIVE HUNT"
                                    else -> "EARLY PROGRESS"
                                }
                                Column(
                                    Modifier.fillMaxWidth()
                                        .background(theme.card, RoundedCornerShape(10.dp))
                                        .border(1.dp, theme.accent.copy(alpha = if (game.progress >= 75) 0.9f else 0.4f), RoundedCornerShape(10.dp))
                                        .padding(11.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(game.title, color = theme.text, fontWeight = FontWeight.Black, fontSize = 12.sp, maxLines = 2)
                                            Text(game.console, color = theme.muted, fontSize = 9.sp)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("${game.progress}%", color = theme.accent, fontWeight = FontWeight.Black, fontSize = 18.sp)
                                            Text(tier, color = theme.muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    LinearProgressIndicator(
                                        progress = { game.progress / 100f },
                                        modifier = Modifier.fillMaxWidth().height(7.dp),
                                        color = theme.accent,
                                        trackColor = theme.background
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("${game.earned}/${game.total} UNLOCKED  •  $remaining LEFT", color = theme.muted, fontSize = 9.sp, modifier = Modifier.weight(1f))
                                        TextButton(onClick = {
                                            val key = KeyVault.load(context)
                                            if (key != null) scope.launch {
                                                roadmapLoading = true
                                                selectedRoadmap = null
                                                roadmapScreen = true
                                                achievementRadarScreen = false
                                                status = "SCANNING ${game.title.uppercase()}..."
                                                try {
                                                    selectedRoadmap = getGameRoadmap(key, game)
                                                    status = "HUNT READY"
                                                } catch (e: Exception) {
                                                    status = "RADAR ERROR: ${e.message}"
                                                }
                                                roadmapLoading = false
                                            } else {
                                                status = "ADD YOUR API KEY IN SETTINGS"
                                            }
                                        }) {
                                            Text("VIEW TARGETS  ›", color = theme.accent, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (roadmapScreen) {
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Choose a game to see every remaining achievement. Tap any locked target to read exactly what its RetroAchievements description says you need to do.", color = theme.muted, fontSize = 11.sp)
                    if (gameLibrary.isEmpty()) {
                        Text("Game library hasn't loaded yet. Refresh Home while connected.", color = theme.text, modifier = Modifier.padding(12.dp))
                    } else {
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            items(gameLibrary, key = { it.id }) { game ->
                                Column(Modifier.fillMaxWidth().background(theme.card, RoundedCornerShape(10.dp)).border(1.dp, theme.accent.copy(alpha=.45f), RoundedCornerShape(10.dp)).clickable {
                                    val key = KeyVault.load(context)
                                    if (key != null) scope.launch {
                                        roadmapLoading = true
                                        selectedRoadmap = null
                                        try { selectedRoadmap = getGameRoadmap(key, game) } catch (e: Exception) { status = "ROADMAP ERROR: ${e.message}" }
                                        roadmapLoading = false
                                    }
                                }.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(game.title, Modifier.weight(1f), color = theme.text, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text("${game.earned}/${game.total}", color = theme.accent, fontSize = 10.sp)
                                    }
                                    LinearProgressIndicator(progress = { game.progress / 100f }, modifier = Modifier.fillMaxWidth().height(6.dp), color = theme.accent, trackColor = theme.background)
                                    Text("${game.progress}% COMPLETE • ${game.console}", color = theme.muted, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                    if (roadmapLoading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = theme.accent)
                    selectedRoadmap?.let { roadmap ->
                        Column(Modifier.fillMaxWidth().weight(1f, fill = false).background(theme.background, RoundedCornerShape(10.dp)).padding(10.dp)) {
                            Text("${roadmap.game.title} • ${roadmap.game.progress}% COMPLETE", color = theme.accent, fontWeight = FontWeight.Black)
                            Text("${roadmap.achievements.count { it.earned }} UNLOCKED • ${roadmap.achievements.count { !it.earned }} REMAINING", color = theme.muted, fontSize = 10.sp)
                            LazyColumn(Modifier.heightIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                items(roadmap.achievements.sortedWith(compareBy<GameAchievement> { it.earned }.thenBy { if (it.trueRatio > 0.0) it.trueRatio else Double.MAX_VALUE }.thenBy { it.points }), key = { it.id }) { a ->
                                    val hinted = possibleMissableHint(a.description)
                                    val manuallyFlagged = a.id in missableFlags
                                    Row(Modifier.fillMaxWidth().background(theme.card, RoundedCornerShape(7.dp)).clickable { selectedAchievementDetail = a }.padding(7.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(if (a.earned) "✓ ${a.title}" else a.title, color = if (a.earned) theme.muted else theme.text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text("${a.points} PTS • ${if (a.earned) "UNLOCKED" else "LOCKED"} • TAP FOR REQUIREMENTS", color = theme.muted, fontSize = 9.sp)
                                            if (!a.earned && (hinted || manuallyFlagged)) Text(if (manuallyFlagged) "⚠ USER-FLAGGED • CHECK REQUIREMENTS" else "⚠ POSSIBLE SPECIAL CONDITION • VERIFY", color = theme.accent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                        }
                                        if (!a.earned) TextButton(onClick = {
                                            saveMissableFlag(context, a.id, !manuallyFlagged)
                                            missableFlags = loadMissableFlags(context)
                                        }) { Text(if (manuallyFlagged) "UNFLAG" else "FLAG", color = theme.accent, fontSize = 9.sp) }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (backlogScreen) {
                val filteredGames = gameLibrary.filter { game ->
                    backlogFilter == "ALL" || (backlog[game.id] ?: "WANT TO PLAY") == backlogFilter
                }
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Organize games locally. Changes stay on this device.", color = theme.muted, fontSize = 10.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("ALL", "PLAYING", "WANT TO PLAY", "COMPLETED", "ABANDONED").forEach { f ->
                            FilterChip(selected = backlogFilter == f, onClick = { backlogFilter = f }, label = { Text(f, fontSize = 9.sp) })
                        }
                    }
                    Text("${filteredGames.size} GAMES • ${backlog.values.count { it == "PLAYING" }} PLAYING • ${backlog.values.count { it == "WANT TO PLAY" }} WANT TO PLAY", color = theme.accent, fontSize = 9.sp)
                    if (gameLibrary.isEmpty()) Text("Refresh Home while connected to load your game library.", color = theme.text, modifier = Modifier.padding(12.dp))
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(filteredGames, key = { it.id }) { game ->
                            val state = backlog[game.id] ?: "WANT TO PLAY"
                            Column(Modifier.fillMaxWidth().background(theme.card, RoundedCornerShape(9.dp)).padding(9.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(game.title, color = theme.text, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("${game.progress}% COMPLETE • ${game.earned}/${game.total} • ${game.console}", color = theme.muted, fontSize = 9.sp)
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf("PLAYING", "WANT TO PLAY", "COMPLETED", "ABANDONED").forEach { option ->
                                        FilterChip(selected = state == option, onClick = {
                                            saveBacklog(context, game.id, option)
                                            backlog = loadBacklog(context)
                                        }, label = { Text(option, fontSize = 8.sp) })
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (messagesScreen) {
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
                                AchievementCard(item, theme, pixelBorders) {
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
                        Text("CARTRIDGE SHELL STYLE", color = theme.muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            cartridgeOptions.forEach { option ->
                                FilterChip(
                                    selected = cartridgeStyle == option,
                                    onClick = {
                                        cartridgeStyle = option
                                        prefs.edit().putString("cartridge_style", option).apply()
                                    },
                                    label = { Text(option) }
                                )
                            }
                        }
                        achievements.groupBy { it.game }.entries.sortedByDescending { it.value.size }.forEach { entry ->
                            GameCartridgeCard(entry.key, entry.value, theme, pixelBorders, cartridgeStyle)
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
                when (dashboardSection) {
                    "library" -> {
                        val filteredGames = gameLibrary.filter { it.title.contains(librarySearch, ignoreCase = true) || it.console.contains(librarySearch, ignoreCase = true) }
                        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = librarySearch,
                                onValueChange = { librarySearch = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Search your game library") }
                            )
                            Text("${filteredGames.size} GAMES • TAP A GAME FOR ITS ACHIEVEMENT ROADMAP", color = theme.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            if (filteredGames.isEmpty()) {
                                Text(if (gameLibrary.isEmpty()) "Connect your RetroAchievements account and refresh Home to load your games." else "No games match that search.", color = theme.muted, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
                            } else {
                                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    items(filteredGames, key = { it.id }) { game ->
                                        Column(
                                            Modifier.fillMaxWidth().background(theme.card, RoundedCornerShape(10.dp))
                                                .border(1.dp, theme.accent.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                                .clickable {
                                                    val key = KeyVault.load(context)
                                                    if (key != null) scope.launch {
                                                        roadmapLoading = true
                                                        selectedRoadmap = null
                                                        try { selectedRoadmap = getGameRoadmap(key, game); roadmapScreen = true; dashboard = false }
                                                        catch (e: Exception) { status = "ROADMAP ERROR: ${e.message}" }
                                                        roadmapLoading = false
                                                    } else { settings = true; hubScreen = false; status = "CONNECT YOUR ACCOUNT FIRST" }
                                                }.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(game.title, Modifier.weight(1f), color = theme.text, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Text("${game.earned}/${game.total}", color = theme.accent, fontSize = 10.sp)
                                            }
                                            LinearProgressIndicator(progress = { game.progress / 100f }, modifier = Modifier.fillMaxWidth().height(6.dp), color = theme.accent, trackColor = theme.background)
                                            Text("${game.progress}% COMPLETE • ${game.console}", color = theme.muted, fontSize = 9.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "trophies" -> {
                        val totalPoints = achievements.sumOf { it.points }
                        val hardcoreCount = achievements.count { it.hardcore }
                        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                Stat("TROPHIES", achievements.size.toString(), Modifier.weight(1f), theme)
                                Stat("POINTS", totalPoints.toString(), Modifier.weight(1f), theme)
                                Stat("HARDCORE", hardcoreCount.toString(), Modifier.weight(1f), theme)
                            }
                            Text("YOUR UNLOCKED ACHIEVEMENTS", color = theme.accent, fontWeight = FontWeight.Black, fontSize = 12.sp)
                            if (achievements.isEmpty()) Text("No trophies yet. Connect your account and refresh Home.", color = theme.muted, fontSize = 12.sp)
                            else LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                items(achievements, key = { it.id }) { item ->
                                    Column(Modifier.fillMaxWidth().background(theme.card, RoundedCornerShape(10.dp)).border(1.dp, theme.accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(item.title, color = theme.text, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(item.game, color = theme.accent, fontSize = 10.sp)
                                        Text("${item.points} POINTS • ${if (item.hardcore) "HARDCORE" else "SOFTCORE"}", color = theme.muted, fontSize = 9.sp)
                                        if (item.description.isNotBlank()) Text(item.description, color = theme.text, fontSize = 10.sp, maxLines = 3)
                                    }
                                }
                            }
                        }
                    }
                    "journal" -> {
                        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("SAVE POINT JOURNAL", color = theme.accent, fontWeight = FontWeight.Black)
                            OutlinedTextField(journalGame, { journalGame = it }, Modifier.fillMaxWidth(), label = { Text("Game") }, singleLine = true)
                            OutlinedTextField(journalSavePoint, { journalSavePoint = it }, Modifier.fillMaxWidth(), label = { Text("Save point / location") }, singleLine = true)
                            OutlinedTextField(journalNotes, { journalNotes = it }, Modifier.fillMaxWidth(), label = { Text("Notes / next steps") }, minLines = 3)
                            Button(onClick = { if (journalGame.isNotBlank() || journalSavePoint.isNotBlank() || journalNotes.isNotBlank()) { val e = SaveJournalEntry(java.util.UUID.randomUUID().toString(), journalGame.ifBlank { "Untitled game" }, journalSavePoint.ifBlank { "Unspecified" }, journalNotes, System.currentTimeMillis()); journalEntries = listOf(e) + journalEntries; persistSaveJournal(context, journalEntries); journalGame = ""; journalSavePoint = ""; journalNotes = ""; status = "JOURNAL ENTRY SAVED" } else status = "ADD A NOTE FIRST" }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = theme.accent)) { Text("SAVE JOURNAL ENTRY", color = theme.background) }
                            Text("SAVED NOTES • ${journalEntries.size}", color = theme.accent, fontWeight = FontWeight.Bold)
                            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) { items(journalEntries, key = { it.id }) { e -> Column(Modifier.fillMaxWidth().background(theme.card, RoundedCornerShape(8.dp)).padding(10.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(e.game, Modifier.weight(1f), color = theme.accent, fontWeight = FontWeight.Bold); TextButton(onClick = { journalEntries = journalEntries.filterNot { it.id == e.id }; persistSaveJournal(context, journalEntries) }) { Text("DELETE") } }; Text("SAVE POINT: ${e.savePoint}", color = theme.text); if (e.notes.isNotBlank()) Text(e.notes, color = theme.text); Text(java.text.SimpleDateFormat("MMM d, yyyy h:mm a", java.util.Locale.getDefault()).format(java.util.Date(e.savedAt)), color = theme.muted, fontSize = 9.sp) } } }
                        }
                    }
                    else -> { Text("Choose a section from Home.", color = theme.muted) }
                    }
            } else if (!settings) {
                if (recentAchievements.isEmpty()) {
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
                    ) {items(recentAchievements.take(20), key = { it.id }) { item ->
        AchievementCard(item, theme, pixelBorders) {
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
                    Modifier.weight(1f).fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                .padding(vertical = 7.dp, horizontal = 9.dp),
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

                    Text("APP TEXT SIZE", color = theme.accent, fontWeight = FontWeight.Black)
                    Text("Make menus, buttons and game details easier to read: ${((appFontScale - 1f) * 100).toInt() + 100}%", color = theme.muted, fontSize = 11.sp)
                    Slider(
                        value = appFontScale,
                        onValueChange = { value ->
                            appFontScale = value
                            prefs.edit().putFloat("app_font_scale", value).apply()
                        },
                        valueRange = 1.0f..1.5f,
                        steps = 4
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("NORMAL", color = theme.muted, fontSize = 9.sp)
                        Text("LARGE", color = theme.muted, fontSize = 9.sp)
                        Text("EXTRA LARGE", color = theme.muted, fontSize = 9.sp)
                    }
                    HorizontalDivider(color = theme.muted.copy(alpha = 0.3f))

                    Text("CUSTOM FONT", color = theme.accent, fontWeight = FontWeight.Black)
                    Text(customFontUri?.substringAfterLast("/") ?: "No custom font selected", color = theme.muted, fontSize = 10.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/octet-stream")) }, modifier = Modifier.weight(1f)) { Text("CHOOSE FONT FILE") }; if (customFontUri != null) OutlinedButton(onClick = { customFontUri = null; customFontFamily = null; prefs.edit().remove("custom_font_uri").apply() }) { Text("RESET") } }
                    Text("Choose a TTF or OTF file. Your choice is remembered on this device.", color = theme.muted, fontSize = 10.sp)
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
                            Text("CRT display effects", color = theme.text, fontWeight = FontWeight.Bold)
                            Text("Subtle scanlines and a soft moving screen glow", color = theme.muted, fontSize = 10.sp)
                        }
                        Switch(checked = crtEffects, onCheckedChange = {
                            crtEffects = it
                            prefs.edit().putBoolean("crt_effects", it).apply()
                        })
                    }
                    HorizontalDivider(color = theme.muted.copy(alpha = 0.3f))
                    Text("WALLPAPER GALLERY", color = theme.accent, fontWeight = FontWeight.Black)
                    Text("Choose an animated scene for the app background.", color = theme.muted, fontSize = 10.sp)
                    wallpaperOptions.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            pair.forEach { option ->
                                FilterChip(
                                    selected = wallpaperStyle == option,
                                    onClick = {
                                        wallpaperStyle = option
                                        prefs.edit().putString("wallpaper_style", option).apply()
                                        status = "$option WALLPAPER SELECTED"
                                    },
                                    label = { Text(option.replace("PIXEL ", "").replace("RAINY ", "")) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    HorizontalDivider(color = theme.muted.copy(alpha = 0.3f))
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
                                        scheduleChecks(context, checkMinutes)
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

                    Text("DISCORD RICH PRESENCE", color = theme.accent, fontWeight = FontWeight.Black)
                    Text(
                        "Send a one-time test activity to the Discord app on this phone. Discord must be installed, signed in, and activity sharing enabled.",
                        color = theme.muted,
                        fontSize = 11.sp
                    )
                    OutlinedTextField(
                        value = discordApplicationId,
                        onValueChange = {
                            discordApplicationId = it.filter { ch -> ch.isDigit() }.take(24)
                            prefs.edit().putString("discord_application_id", discordApplicationId).apply()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Discord Application ID") },
                        singleLine = true
                    )
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.com/developers/applications")))
                            }.onFailure { status = "COULD NOT OPEN DISCORD DEVELOPER PORTAL" }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("OPEN DISCORD DEVELOPER PORTAL", color = theme.text)
                    }
                    OutlinedButton(
                        enabled = !discordTesting && discordApplicationId.isNotBlank(),
                        onClick = {
                            discordTesting = true
                            discordTestStatus = "CREATING DISCORD SDK CLIENT..."
                            try {
                                (context as? MainActivity)?.testDiscordSdkClientCreation(discordApplicationId.toLong())
                                discordTestStatus = "SDK CLIENT CREATED • APP STAYED OPEN"
                            } catch (error: Throwable) {
                                android.util.Log.e("RetroAlertsDiscord", "SDK initialization test failed", error)
                                discordTestStatus = "SDK CLIENT CREATION FAILED • CHECK LOG"
                            } finally {
                                discordTesting = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (discordTesting) "INITIALIZING..." else "TEST DISCORD SDK CLIENT CREATION", color = theme.text)
                    }
                    Text(discordTestStatus, color = if (discordTestStatus.startsWith("TEST SENT")) theme.accent else theme.muted, fontSize = 10.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                retroArchTarget = "PHONE"
                                prefs.edit().putString("discord_retroarch_target", "PHONE").apply()
                                retroArchTestStatus = "PHONE SELECTED"
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (retroArchTarget == "PHONE") theme.accent else theme.card
                            )
                        ) { Text("THIS PHONE", color = if (retroArchTarget == "PHONE") theme.background else theme.text) }
                        Button(
                            onClick = {
                                retroArchTarget = "MECOOL"
                                prefs.edit().putString("discord_retroarch_target", "MECOOL").apply()
                                retroArchTestStatus = "MECOOL SELECTED"
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (retroArchTarget == "MECOOL") theme.accent else theme.card
                            )
                        ) { Text("MECOOL BB2", color = if (retroArchTarget == "MECOOL") theme.background else theme.text) }
                    }
                    if (retroArchTarget == "MECOOL") {
                        OutlinedTextField(
                            value = retroArchHost,
                            onValueChange = {
                                retroArchHost = it
                                prefs.edit().putString("discord_retroarch_host", it).apply()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("MECOOL IP address") },
                            placeholder = { Text("Example: 192.168.1.50") },
                            singleLine = true
                        )
                    } else {
                        Text("Phone mode checks RetroArch on this phone at 127.0.0.1.", color = theme.muted, fontSize = 10.sp)
                    }
                    OutlinedButton(
                        enabled = !retroArchTesting && (retroArchTarget == "PHONE" || retroArchHost.isNotBlank()),
                        onClick = {
                            scope.launch {
                                retroArchTesting = true
                                retroArchTestStatus = "CHECKING RETROARCH..."
                                try {
                                    val host = if (retroArchTarget == "PHONE") "127.0.0.1" else retroArchHost.trim()
                                    retroArchTestStatus = queryRetroArchStatus(host)
                                } catch (_: Exception) {
                                    retroArchTestStatus = if (retroArchTarget == "PHONE") {
                                        "NO RESPONSE • ENABLE NETWORK COMMANDS IN PHONE RETROARCH"
                                    } else {
                                        "NO RESPONSE • CHECK BB2 IP, WI-FI AND NETWORK COMMANDS"
                                    }
                                } finally {
                                    retroArchTesting = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (retroArchTesting) "CHECKING..." else "TEST RETROARCH CONNECTION", color = theme.text)
                    }
                    Text(
                        retroArchTestStatus,
                        color = if (retroArchTestStatus.startsWith("PLAYING") || retroArchTestStatus.startsWith("PAUSED") || retroArchTestStatus.startsWith("CONNECTED")) theme.accent else theme.muted,
                        fontSize = 10.sp
                    )
                    Text(
                        "On RetroArch, enable Settings → Network → Network Commands (UDP port 55355). For the BB2, use its local Wi-Fi IP address. Keep this port on your trusted home network only; do not forward it to the internet.",
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

            }

            Spacer(Modifier.height(2.dp))
            Text(
                "GBA EDITION • VERSION 0.8.0",
                Modifier.align(Alignment.CenterHorizontally),
                color = theme.muted,
                fontSize = 9.sp,
                letterSpacing = 1.sp
            )
        }
        }
        if (crtEffects) {
            CrtOverlay(theme, animatedBackground)
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
private fun GameCartridgeCard(
    game: String,
    unlocks: List<Achievement>,
    theme: AppTheme,
    pixelBorders: Boolean,
    cartridgeStyle: String
) {
    var artwork by remember(game, unlocks.firstOrNull()?.gameIcon) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(game, unlocks.firstOrNull()?.gameIcon) {
        artwork = loadRemoteBitmap(gameArtworkUrl(unlocks.firstOrNull()?.gameIcon ?: ""))
    }
    val points = unlocks.sumOf { it.points }
    val hardcore = unlocks.count { it.hardcore }
    val shellColor = when (cartridgeStyle) {
        "MIDNIGHT" -> Color(0xFF596579)
        "NEON" -> Color(0xFF9B55D4)
        "ARCADE" -> Color(0xFF36A68A)
        else -> Color(franchiseColor(game))
    }
    val shellShape = if (cartridgeStyle == "ARCADE") RoundedCornerShape(16.dp) else if (pixelBorders) RoundedCornerShape(5.dp) else RoundedCornerShape(16.dp)
    val cartridgeLabel = when (cartridgeStyle) {
        "MIDNIGHT" -> "MIDNIGHT EDITION"
        "NEON" -> "NEON CARTRIDGE"
        "ARCADE" -> "ARCADE COLLECTION"
        else -> "GAME BOY ADVANCE"
    }

    Column(
        Modifier.fillMaxWidth()
            .background(shellColor.copy(alpha = 0.22f), shellShape)
            .border(2.dp, shellColor, shellShape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(Modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.Center) {
            Box(Modifier.width(72.dp).height(5.dp).background(shellColor.copy(alpha = 0.8f), RoundedCornerShape(3.dp)))
        }
        Row(
            Modifier.fillMaxWidth()
                .background(theme.card, if (pixelBorders) RoundedCornerShape(2.dp) else RoundedCornerShape(9.dp))
                .border(2.dp, shellColor.copy(alpha = 0.95f), if (pixelBorders) RoundedCornerShape(2.dp) else RoundedCornerShape(9.dp))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.size(width = 70.dp, height = 82.dp)
                    .background(theme.background, RoundedCornerShape(3.dp))
                    .border(2.dp, theme.accent.copy(alpha = 0.8f), RoundedCornerShape(3.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (artwork != null) {
                    Image(
                        bitmap = artwork!!.asImageBitmap(),
                        contentDescription = "$game cartridge label artwork",
                        modifier = Modifier.fillMaxSize().padding(3.dp),
                        contentScale = ContentScale.Crop
                    )
                } else Text("🎮", fontSize = 28.sp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(cartridgeLabel, color = theme.accent, fontWeight = FontWeight.Black, fontSize = 8.sp, letterSpacing = 1.sp)
                Text(game, color = theme.text, fontWeight = FontWeight.Black, fontSize = 12.sp, maxLines = 2)
                Text("${unlocks.size} UNLOCKS  •  $points PTS", color = theme.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text("$hardcore HARDCORE  •  ${unlocks.size - hardcore} SOFTCORE", color = theme.muted, fontSize = 8.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("★", color = theme.accent, fontSize = 18.sp)
                Text("${unlocks.size}", color = theme.text, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
        }
        Row(Modifier.fillMaxWidth().height(7.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            repeat(18) {
                Box(Modifier.padding(horizontal = 2.dp).width(5.dp).height(5.dp).background(theme.background.copy(alpha = 0.9f), RoundedCornerShape(1.dp)))
            }
        }
    }
}

@Composable
private fun AchievementCard(
    item: Achievement,
    theme: AppTheme,
    pixelBorders: Boolean,
    onResend: () -> Unit
) {
    val cardShape = if (pixelBorders) RoundedCornerShape(4.dp) else RoundedCornerShape(12.dp)
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
            .background(theme.card, cardShape)
            .border(2.dp, theme.accent.copy(alpha = 0.75f), cardShape)
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