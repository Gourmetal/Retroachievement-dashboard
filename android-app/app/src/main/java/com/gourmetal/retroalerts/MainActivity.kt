
package com.gourmetal.retroalerts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    val icon: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RetroAlertsApp() }
    }
}

@Composable
fun RetroAlertsApp() {
    var selectedTab by remember { mutableStateOf("HOME") }

    val demos = listOf(
        Achievement(
            "The Legend of Zelda: The Minish Cap",
            "Wind Ruins Explorer", 10, "SOFTCORE", "🗡️"
        ),
        Achievement(
            "Pokémon LeafGreen",
            "A New Adventure", 5, "HARDCORE", "⚡"
        ),
        Achievement(
            "Super Mario Advance",
            "Mushroom Kingdom", 15, "SOFTCORE", "🍄"
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF17142A), Color(0xFF292044))
                )
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF8172B8),
                            Shell,
                            ShellDark
                        )
                    ),
                    RoundedCornerShape(34.dp)
                )
                .border(
                    3.dp,
                    Color(0xFFB8A9E8),
                    RoundedCornerShape(34.dp)
                )
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

            Spacer(Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Screen, RoundedCornerShape(12.dp))
                    .border(
                        4.dp,
                        Color(0xFF29223F),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("●", color = Color(0xFF5E9A48), fontSize = 11.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "PLAYER 1  /  ACHIEVEMENT LOG",
                        color = Ink,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    if (selectedTab == "HOME")
                        "READY, PLAYER?"
                    else
                        "CUSTOMIZE YOUR SYSTEM",
                    color = Ink,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )

                Text(
                    if (selectedTab == "HOME")
                        "Your next victory starts here."
                    else
                        "More themes and controls coming soon.",
                    color = Ink.copy(alpha = 0.75f),
                    fontSize = 11.sp
                )

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBox("UNLOCKS", "003", Modifier.weight(1f))
                    StatBox("POINTS", "030", Modifier.weight(1f))
                    StatBox("STATUS", "READY", Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = Color(0xFFFFE18A)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (selectedTab == "HOME")
                        "RECENT UNLOCKS"
                    else
                        "SYSTEM SETTINGS",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    fontSize = 13.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            if (selectedTab == "HOME") {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(demos) { item ->
                        AchievementCard(item)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .heightIn(min = 180.dp)
                        .fillMaxWidth()
                        .background(
                            Color(0xFF2C244D),
                            RoundedCornerShape(14.dp)
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "CONSOLE SKIN",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "GAME BOY ADVANCE",
                        color = Color(0xFFFFD98A),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "Purple shell • Pixel-inspired screen • Custom alerts coming next",
                        color = Color(0xFFE1D9FA),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

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
                    "CUSTOMIZE",
                    selectedTab == "CUSTOMIZE",
                    Modifier.weight(1f)
                ) {
                    selectedTab = "CUSTOMIZE"
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                "GBA EDITION  •  VERSION 0.1.0",
                color = Color(0xFFD8CFF4),
                fontSize = 9.sp,
                letterSpacing = 1.5.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun StatBox(
    label: String,
    value: String,
    modifier: Modifier
) {
    Column(
        modifier = modifier
            .background(
                Color(0xFFBBD7A5),
                RoundedCornerShape(7.dp)
            )
            .padding(8.dp)
    ) {
        Text(
            label,
            color = Ink.copy(alpha = 0.7f),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            value,
            color = Ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun AchievementCard(item: Achievement) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color(0xFF2C244D),
                RoundedCornerShape(12.dp)
            )
            .border(
                1.dp,
                Color(0xFF7766A9),
                RoundedCornerShape(12.dp)
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(item.icon, fontSize = 25.sp)
        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Text(
                item.game,
                color = Color(0xFFD0C6EB),
                fontSize = 10.sp
            )
            Text(
                item.mode,
                color = if (item.mode == "HARDCORE")
                    Color(0xFFFF9C8D)
                else
                    Color(0xFF9ED6FF),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            "+${item.points}",
            color = Color(0xFFFFD98A),
            fontWeight = FontWeight.Black,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun NavButton(
    label: String,
    active: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) ButtonRed else ButtonBlue,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(22.dp)
    ) {
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
    }
}
