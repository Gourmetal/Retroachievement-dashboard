
package com.gourmetal.retroalerts

import android.content.Context
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Purple = Color(0xFF5B4B9A)
private val DarkPurple = Color(0xFF34285F)
private val Green = Color(0xFFD9F0C0)
private val Ink = Color(0xFF20351F)
private val Gold = Color(0xFFFFD98A)

data class Achievement(
    val game: String,
    val title: String,
    val points: Int,
    val hardcore: Boolean,
    val date: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RetroAlertsApp() }
    }
}

private object KeyVault {
    private const val ALIAS = "retro_alerts_key"
    private const val PREFS = "retro_alerts"
    private const val VALUE = "api_key"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        ).apply {
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
        val data = cipher.iv + cipher.doFinal(value.toByteArray())
        context.getSharedPreferences(PREFS, 0).edit()
            .putString(VALUE, Base64.encodeToString(data, Base64.NO_WRAP))
            .apply()
    }

    fun load(context: Context): String? = try {
        val saved = context.getSharedPreferences(PREFS, 0)
            .getString(VALUE, null) ?: return null
        val data = Base64.decode(saved, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE, key(),
            GCMParameterSpec(128, data.copyOfRange(0, 12))
        )
        String(cipher.doFinal(data.copyOfRange(12, data.size)))
    } catch (_: Exception) {
        null
    }

    fun remove(context: Context) {
        context.getSharedPreferences(PREFS, 0).edit().remove(VALUE).apply()
    }
}

private suspend fun getAchievements(apiKey: String): List<Achievement> =
    withContext(Dispatchers.IO) {
        val key = URLEncoder.encode(apiKey, "UTF-8")
        val url = URL(
            "https://retroachievements.org/API/API_GetUserRecentAchievements.php" +
                "?u=Gourmetal&m=20&y=$key"
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
                    item.optString("GameTitle", "Unknown game"),
                    item.optString("Title", "Achievement"),
                    item.optInt("Points"),
                    item.optInt("HardcoreMode") == 1,
                    item.optString("Date", "")
                )
            }
        } finally {
            connection.disconnect()
        }
    }

@Composable
fun RetroAlertsApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf("") }
    var connected by remember { mutableStateOf(KeyVault.load(context) != null) }
    var achievements by remember { mutableStateOf(emptyList<Achievement>()) }
    var status by remember { mutableStateOf("READY, PLAYER?") }
    var loading by remember { mutableStateOf(false) }

    fun refresh() {
        val savedKey = KeyVault.load(context)
        if (savedKey.isNullOrBlank()) {
            tab = true
            status = "ADD YOUR API KEY IN SETTINGS"
            return
        }

        scope.launch {
            loading = true
            status = "CONNECTING..."
            try {
                achievements = getAchievements(savedKey)
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
        Modifier.fillMaxSize()
            .background(Color(0xFF17142A))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF8172B8), Purple, DarkPurple)),
                    RoundedCornerShape(28.dp)
                )
                .border(3.dp, Color(0xFFB8A9E8), RoundedCornerShape(28.dp))
                .padding(14.dp)
        ) {
            Text(
                "GOURMETAL",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp
            )
            Text("RETRO ALERTS", color = Gold, fontSize = 11.sp)

            Spacer(Modifier.height(12.dp))

            Column(
                Modifier.fillMaxWidth()
                    .background(Green, RoundedCornerShape(12.dp))
                    .border(4.dp, Color(0xFF29223F), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text("● PLAYER 1 / ACHIEVEMENT LOG", color = Ink, fontSize = 10.sp)
                Text(
                    if (tab) "ACCOUNT SETTINGS" else "READY, PLAYER?",
                    color = Ink,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
                Text(status, color = Ink, fontSize = 10.sp)

                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Stat("UNLOCKS", achievements.size.toString(), Modifier.weight(1f))
                    Stat("POINTS", achievements.sumOf { it.points }.toString(), Modifier.weight(1f))
                    Stat("STATUS", if (connected) "LINKED" else "OFFLINE", Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (tab) "ACCOUNT SETTINGS" else "RECENT UNLOCKS",
                    color = Color.White,
                    fontWeight = FontWeight.Black
                )
                if (!tab) {
                    IconButton(onClick = { refresh() }, enabled = !loading) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = Color.White)
                    }
                }
            }

            if (loading) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth(),
                    color = Gold
                )
            }

            if (!tab) {
                if (achievements.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth()
                            .heightIn(min = 160.dp)
                            .background(DarkPurple, RoundedCornerShape(12.dp))
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("🎮", fontSize = 32.sp)
                        Text(
                            "NO ACHIEVEMENTS TO DISPLAY",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Connect your account in Settings.",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                    }
                } else {
                    LazyColumn(
                        Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(achievements) { AchievementCard(it) }
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxWidth()
                        .background(DarkPurple, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("RETROACHIEVEMENTS API KEY", color = Color.White)
                    Text(
                        "Your key is encrypted and stored on this phone.",
                        color = Color.LightGray,
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
                                    tab = false
                                    refresh()
                                } catch (e: Exception) {
                                    status = "COULD NOT SAVE KEY"
                                }
                            } else status = "ENTER YOUR API KEY"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFC94C70)
                        )
                    ) {
                        Text("SAVE KEY & CONNECT")
                    }

                    if (connected) {
                        OutlinedButton(
                            onClick = {
                                KeyVault.remove(context)
                                connected = false
                                achievements = emptyList()
                                status = "API KEY REMOVED"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("REMOVE SAVED API KEY")
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { tab = false },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!tab) Color(0xFFC94C70) else DarkPurple
                    )
                ) { Text("HOME") }

                Button(
                    onClick = { tab = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (tab) Color(0xFFC94C70) else DarkPurple
                    )
                ) { Text("SETTINGS") }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "GBA EDITION • VERSION 0.3.0",
                Modifier.align(Alignment.CenterHorizontally),
                color = Color(0xFFD8CFF4),
                fontSize = 9.sp,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(
        modifier.background(Color(0xFFBBD7A5), RoundedCornerShape(7.dp))
            .padding(8.dp)
    ) {
        Text(label, color = Ink, fontSize = 9.sp)
        Text(value, color = Ink, fontWeight = FontWeight.Black, fontSize = 13.sp)
    }
}

@Composable
private fun AchievementCard(item: Achievement) {
    Column(
        Modifier.fillMaxWidth()
            .background(Color(0xFF2C244D), RoundedCornerShape(10.dp))
            .border(1.dp, Purple, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Text(item.game, color = Gold, fontSize = 10.sp)
        Text(
            item.title,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                if (item.hardcore) "HARDCORE" else "SOFTCORE",
                color = if (item.hardcore) Gold else Color(0xFFB9E7A5),
                fontSize = 10.sp
            )
            Text("${item.points} POINTS", color = Color.White, fontSize = 10.sp)
        }
        if (item.date.isNotBlank()) {
            Text(item.date, color = Color.LightGray, fontSize = 9.sp)
        }
    }
}
