
package com.gourmetal.retroalerts

import android.content.Context
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64

private val Shell = Color(0xFF5B4B9A)
private val ShellDark = Color(0xFF34285F)
private val Screen = Color(0xFFD9F0C0)
private val Ink = Color(0xFF20351F)
private val ButtonRed = Color(0xFFC94C70)
private val ButtonBlue = Color(0xFF496DC5)

data class Achievement(
    val game: String,
    val title: String,
    val points: Int,
    val mode: String,
    val date: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RetroAlertsApp() }
    }
}

private object SecureKeyStore {
    private const val ALIAS = "gourmetal_retro_alerts_key"
    private const val PREFS = "retro_alerts_secure"
    private const val KEY_VALUE = "encrypted_api_key"

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        val existing = keyStore.getKey(ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )

        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )

        return generator.generateKey()
    }

    fun save(context: Context, apiKey: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())

        val encrypted = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
        val combined = cipher.iv + encrypted
        val encoded = Base64.encodeToString(combined, Base64.NO_WRAP)

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_VALUE, encoded)
            .apply()
    }

    fun load(context: Context): String? {
        return try {
            val encoded = context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            ).getString(KEY_VALUE, null) ?: return null

            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, 12)
            val encrypted = combined.copyOfRange(12, combined.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getSecretKey(),
                GCMParameterSpec(128, iv)
            )

            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun remove(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_VALUE)
            .apply()
    }
}

private suspend fun fetchRecent(apiKey: String): List<Achievement> =
    withContext(Dispatchers.IO) {
        val encodedKey = URLEncoder.encode(apiKey, "UTF-8")
        val address =
            "https://retroachievements.org/API/API_GetUserRecentAchievements.php" +
            "?u=Gourmetal&m=20&y=$encodedKey"

        val connection = URL(address).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("Accept", "application/json")

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (responseCode !in 200..299) {
                throw Exception("RetroAchievements returned HTTP $responseCode")
            }

            val json = JSONArray(response)
            val results = mutableListOf<Achievement>()

            for (i in 0 until json.length()) {
                val item = json.getJSONObject(i)

                val hardcore = item.optInt("HardcoreMode", 0) == 1
                val points = item.optInt("Points", 0)

                results.add(
                    Achievement(
                        game = item.optString("GameTitle", "Unknown game"),
                        title = item.optString("Title", "Achievement unlocked"),
                        points = points,
                        mode = if (hardcore) "HARDCORE" else "SOFTCORE",
                        date = item.optString("Date", "")
                    )
                )
            }

            results
        } finally {
            connection.disconnect()
        }
    }

@Composable
fun RetroAlertsApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf("HOME") }
    var apiKeyInput by remember { mutableStateOf("") }
    var hasKey by remember { mutableStateOf(SecureKeyStore.load(context) != null) }
    var achievements by remember { mutableStateOf<List<Achievement>>(emptyList()) }
    var status by remember { mutableStateOf("ADD YOUR API KEY IN SETTINGS") }
    var loading by remember { mutableStateOf(false) }

    fun refreshAchievements() {
        val key = SecureKeyStore.load(context)

        if (key.isNullOrBlank()) {
            status = "ADD YOUR API KEY IN SETTINGS"
            selectedTab = "SETTINGS"
            return
        }

        scope.launch {
            loading = true
            status = "CONNECTING TO RETROACHIEVEMENTS..."

            try {
                achievements = fetchRecent(key)
                status = if (achievements.isEmpty()) {
                    "NO RECENT UNLOCKS FOUND"
                } else {
                    "CONNECTED • ${achievements.size} UNLOCKS LOADED"
                }
            } catch (error: Exception) {
                status = "ERROR: ${error.message ?: "CONNECTION FAILED"}"
            }

            loading = false
        }
    }

    LaunchedEffect(Unit) {
        if (hasKey) refreshAchievements()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF17142A), Color(0xFF292044))
                )
            )
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF8172B8), Shell, ShellDark)
                    ),
                    RoundedCornerShape(30.dp)
                )
                .border(3.dp, Color(0xFFB8A9E8), RoundedCornerShape(30.dp))
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        "GOURMETAL",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp
                    )
                    Text(
                        "RETRO ALERTS",
                        color = Color(0xFFE7DFFF),
                        fontSize = 10.sp,
                        letterSpacing = 2.sp
                    )
                }

                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White
                )
            }

            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Screen, RoundedCornerShape(12.dp))
                    .border(4.dp, Color(0xFF29223F), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("●", color = Color(0xFF5E9A48), fontSize = 11.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "PLAYER 1 / ACHIEVEMENT LOG",
                        color = Ink,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    if (selectedTab == "HOME") "READY, PLAYER?"
                    else "CONNECT YOUR ACCOUNT",
                    color = Ink,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black
                )

                Text(
                    status,
                    color = Ink.copy(alpha = 0.8f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBox("UNLOCKS", achievements.size.toString(), Modifier.weight(1f))
                    StatBox(
                        "POINTS",
                        achievements.sumOf { it.points }.toString(),
                        Modifier.weight(1f)
                    )
                    StatBox(
                        "STATUS",
                        if (hasKey) "LINKED" else "OFFLINE",
                        Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = Color(0xFFFFE18A)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (selectedTab == "HOME") "RECENT UNLOCKS" else "ACCOUNT SETTINGS",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        fontSize = 12.sp
                    )
                }

                if (selectedTab == "HOME") {
                    IconButton(
                        onClick = { refreshAchievements() },
                        enabled = !loading
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Color.White
                        )
                    }
                }
            }

            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFFFD98A)
                )
                Spacer(Modifier.height(8.dp))
            }

            if (selectedTab == "HOME") {
                if (achievements.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 150.dp)
                            .background(Color(0xFF2C244D), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("🎮", fontSize = 30.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "NO ACHIEVEMENTS TO DISPLAY",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Text(
                            "Connect your RetroAchievements account in Settings.",
                            color = Color(0xFFD0C6EB),
                            fontSize = 10.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .heightIn(max = 300.dp)
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(achievements) { item ->
                            AchievementCard(item)
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2C244D), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "RETROACHIEVEMENTS API KEY",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )

                    Text(
                        "Enter your API key here. It is encrypted and stored only on this phone.",
                        color = Color(0xFFD0C6EB),
                        fontSize = 10.sp
                    )

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFFD98A),
                            unfocusedBorderColor = Color(0xFF9A8BC5),
                            focusedLabelColor = Color(0xFFFFD98A),
                            unfocusedLabelColor = Color(0xFFD0C6EB),
                            cursorColor = Color.White
                        )
                    )

                    Button(
                        onClick = {
                            val key = apiKeyInput.trim()
                            if (key.isNotEmpty()) {
                                try {
                                    SecureKeyStore.save(context, key)
                                    hasKey = true
                                    apiKeyInput = ""
                                    refreshAchievements()
                                    selectedTab = "HOME"
                                } catch (error: Exception) {
                                    status = "COULD NOT SAVE KEY: ${error.message}"
                                }
                            } else {
                                status = "ENTER YOUR API KEY FIRST"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ButtonRed
                        )
                    ) {
                        Text("SAVE KEY & CONNECT", fontWeight = FontWeight.Black)
                    }

                    if (hasKey) {
                        Text(
                            "An API key is saved on this device.",
                            color = Color(0xFFB9E7A5),
                            fontSize = 10.sp
                        )

                        OutlinedButton(
                            onClick = {
                                SecureKeyStore.remove(context)
                                hasKey = false
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

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                NavButton(
                    "HOME",
                    selectedTab == "HOME",
                    Modifier.weight(1f)
                ) {
                    selectedTab = "HOME"
                }

                NavButton(
                    "SETTINGS",
                    selectedTab == "SETTINGS",
                    Modifier.weight(1f)
                ) {
                    selectedTab = "SETTINGS"
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "GBA EDITION • VERSION 0.2.0",
                color = Color(0xFFD8CFF4),
                fontSize = 9.sp,
                letterSpacing = 1.5.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(Color(0xFFBBD7A5), RoundedCornerShape(7.dp))
            .padding(8.dp)
    ) {
        Text
