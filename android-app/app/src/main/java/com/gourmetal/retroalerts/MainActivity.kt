
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
    AppTheme("Classic Orange", Color(0xFF08090F), Color(0xFF171923), Color(0xFF222531), Color(0xFFFF941F), Color.White, Color(0xFFB8BBC8)),
    AppTheme("Midnight", Color(0xFF080E1B), Color(0xFF14213A), Color(0xFF1D2D4A), Color(0xFF75A9FF), Color.White, Color(0xFFB5C4DC)),
    AppTheme("Retro Neon", Color(0xFF160D24), Color(0xFF28163C), Color(0xFF38204F), Color(0xFFD38AFF), Color.White, Color(0xFFD0B9E5)),
    AppTheme("Game Boy", Color(0xFF17251A), Color(0xFF344C32), Color(0xFF486744), Color(0xFFD9F0C0), Color(0xFFF0F8E9), Color(0xFFC2D7B5)),
    AppTheme("GBA Purple", Color(0xFF17142A), Color(0xFF34285F), Color(0xFF493B78), Color(0xFFB8A9E8), Color.White, Color(0xFFD8CFF4)),
    AppTheme("Emerald", Color(0xFF091A15), Color(0xFF15372B), Color(0xFF20503C), Color(0xFF55E0A0), Color.White, Color(0xFFB2DCC8))
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


private suspend fun getAchievements(apiKey: String): List<Achievement> =
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

            (0 until json.length()).map { i ->
                val item = json.getJSONObject(i)
                Achievement(
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
            }
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

private fun scheduleChecks(context: Context) {
    val request = PeriodicWorkRequestBuilder<AchievementWorker>(
        15, TimeUnit.MINUTES
    ).build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        WORK,
        ExistingPeriodicWorkPolicy.KEEP,
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
        append("\\n\\n")
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
                NotificationCompat.BigTextStyle().bigText("$titleLine\\n$details")
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

        return try {
            val achievements = getAchievements(key)
            val prefs = applicationContext.getSharedPreferences(PREFS, 0)
            val currentIds = achievements.map { it.id }.filter { it.isNotBlank() }.toSet()
            val previousIds = prefs.getStringSet("known_unlocks", null)

            if (previousIds == null) {
                prefs.edit().putStringSet("known_unlocks", currentIds).apply()
            } else {
                achievements.filter {
                    it.id.isNotBlank() && it.id !in previousIds
                }.forEach {
                    notifyAchievement(applicationContext, it)
                }
                prefs.edit().putStringSet("known_unlocks", currentIds).apply()
            }

            prefs.edit().putString("last_check", System.currentTimeMillis().toString()).apply()
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
fun RetroAlertsApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences(PREFS, 0) }

    var settings by remember { mutableStateOf(false) }
    var dashboard by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf("") }
    var connected by remember { mutableStateOf(KeyVault.load(context) != null) }
    var achievements by remember { mutableStateOf(emptyList<Achievement>()) }
    var status by remember { mutableStateOf("READY, PLAYER?") }
    var loading by remember { mutableStateOf(false) }
    var notifications by remember { mutableStateOf(prefs.getBoolean("notifications", false)) }
    var selectedTheme by remember {
        mutableIntStateOf(prefs.getInt("selected_theme", 0).coerceIn(0, themes.lastIndex))
    }

    val theme = themes[selectedTheme]

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
                status = "${achievements.size} UNLOCKS LOADED"
            } catch (e: Exception) {
                status = "ERROR: ${e.message ?: "CONNECTION FAILED"}"
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        if (connected) refresh()
    }

    Box(
        Modifier.fillMaxSize().background(theme.background).padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(theme.panel, RoundedCornerShape(24.dp))
                .border(2.dp, theme.accent.copy(alpha = 0.65f), RoundedCornerShape(24.dp))
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.gourmetal_avatar),
                    contentDescription = "Gourmetal avatar",
                    modifier = Modifier.size(64.dp)
                        .border(2.dp, theme.accent, RoundedCornerShape(14.dp))
                )
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
                    when { dashboard -> "DASHBOARD"; settings -> "SETTINGS"; else -> "READY, PLAYER?" },
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
                    when { dashboard -> "RETROACHIEVEMENTS DASHBOARD"; settings -> "PERSONALIZE YOUR APP"; else -> "RECENT UNLOCKS" },
                    color = theme.text,
                    fontWeight = FontWeight.Bold
                )
                if (!settings && !dashboard) {
                    IconButton(onClick = { refresh() }, enabled = !loading) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = theme.accent)
                    }
                }
            }

            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = theme.accent)
                Spacer(Modifier.height(8.dp))
            }

            if (dashboard) {
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
                        Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {items(achievements) { item ->
        AchievementCard(item, theme) {
        scope.launch {
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

                    Text("NOTIFICATIONS", color = theme.accent, fontWeight = FontWeight.Black)
                    Text(
                        "Check for new unlocks in the background about every 15 minutes.",
                        color = theme.muted,
                        fontSize = 11.sp
                    )

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
                    onClick = { dashboard = false; settings = false },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!settings && !dashboard) theme.accent else theme.card
                    )
                ) {
                    Text("HOME", color = if (!settings && !dashboard) theme.background else theme.text, fontSize = 10.sp, maxLines = 1, softWrap = false)
                }
                Button(
                    onClick = { dashboard = true; settings = false },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (dashboard) theme.accent else theme.card
                    )
                ) {
                    Text("DASHBOARD", color = if (dashboard) theme.background else theme.text, fontSize = 10.sp, maxLines = 1, softWrap = false)
                }
                Button(
                    onClick = { settings = true; dashboard = false },
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
                "GBA EDITION • VERSION 0.6.0",
                Modifier.align(Alignment.CenterHorizontally),
                color = theme.muted,
                fontSize = 9.sp,
                letterSpacing = 1.sp
            )
        }
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

@Composable
private fun AchievementCard(
    item: Achievement,
    theme: AppTheme,
    onResend: () -> Unit
) {
    var badge by remember(item.badgeName) {
        mutableStateOf<Bitmap?>(null)
    }

    LaunchedEffect(item.badgeName) {
        badge = loadBadgeBitmap(item.badgeName)
    }

    Column(
        Modifier.fillMaxWidth()
            .background(theme.card, RoundedCornerShape(12.dp))
            .border(1.dp, theme.accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (badge != null) {
                Image(
                    bitmap = badge!!.asImageBitmap(),
                    contentDescription = "Achievement badge",
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.width(12.dp))
            }

            Column(Modifier.weight(1f)) {
                Text(item.game, color = theme.accent, fontSize = 11.sp)
                Text(item.title, color = theme.text, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            item.description,
            color = theme.muted,
            fontSize = 11.sp
        )

        Spacer(Modifier.height(6.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                if (item.hardcore) "HARDCORE" else "SOFTCORE",
                color = if (item.hardcore) theme.accent else theme.muted,
                fontSize = 10.sp
            )
            Text("${item.points} POINTS", color = theme.text, fontSize = 10.sp)
        }

        if (item.date.isNotBlank()) {
            Text(item.date, color = theme.muted, fontSize = 9.sp)
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = onResend,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("RESEND ALERT")
        }
    }
}
