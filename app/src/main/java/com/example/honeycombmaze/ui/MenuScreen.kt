package com.example.honeycombmaze.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.honeycombmaze.game.GameMode
import com.example.honeycombmaze.ui.theme.BackgroundDark
import com.example.honeycombmaze.ui.theme.CardBackground
import com.example.honeycombmaze.ui.theme.CardBorder
import com.example.honeycombmaze.ui.theme.NeonGreen
import com.example.honeycombmaze.ui.theme.NeonCoral
import com.example.honeycombmaze.ui.theme.NeonYellow
import com.example.honeycombmaze.ui.theme.NeonPurple
import com.example.honeycombmaze.ui.theme.TextSecondary

@Composable
fun MainMenuScreen(
    prefsManager: com.example.honeycombmaze.data.PreferencesManager,
    totalLevels: Int,
    onModeSelected: (GameMode) -> Unit,
    onWatchAdForReward: () -> Unit = {},
    onBuyProduct: (String) -> Unit = {},
    onResetAllData: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentHoney = prefsManager.honey
    var showShopDialog by remember { mutableStateOf(false) }
    var showAvatarDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("⚠️ Reset All Data?", fontWeight = FontWeight.Bold, color = NeonCoral) },
            text = {
                Text(
                    text = "Are you sure you want to reset all game data? Your coins, unlocked modes, avatars, and level progress will be completely erased.",
                    color = Color.White
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetDialog = false
                        onResetAllData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCoral)
                ) {
                    Text("RESET ALL", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("CANCEL", color = TextSecondary)
                }
            },
            containerColor = CardBackground
        )
    }

    if (showAvatarDialog) {
        AvatarSelectionDialog(
            prefsManager = prefsManager,
            onDismiss = { showAvatarDialog = false }
        )
    }

    if (showShopDialog) {
        AlertDialog(
            onDismissRequest = { showShopDialog = false },
            title = { Text("Coin Store", fontWeight = FontWeight.Bold, color = NeonYellow) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Select a pack to purchase via Google Play Billing:")

                    Button(
                        onClick = {
                            showShopDialog = false
                            onBuyProduct(com.example.honeycombmaze.data.BillingManager.PRODUCT_HONEY_100)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonYellow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🍯 100 Coins — ₹250", color = BackgroundDark, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            showShopDialog = false
                            onBuyProduct(com.example.honeycombmaze.data.BillingManager.PRODUCT_HONEY_500)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🍯 500 Coins — ₹1,000", color = BackgroundDark, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            showShopDialog = false
                            onBuyProduct(com.example.honeycombmaze.data.BillingManager.PRODUCT_HONEY_1000)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9900FF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🍯 1,000 Coins — ₹2,000", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            showShopDialog = false
                            onBuyProduct(com.example.honeycombmaze.data.BillingManager.PRODUCT_REMOVE_ADS)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCoral),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🚫 Remove Ads — ₹1,000", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showShopDialog = false }) {
                    Text("CLOSE", color = TextSecondary)
                }
            },
            containerColor = CardBackground
        )
    }

    Scaffold(
        containerColor = BackgroundDark
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(BackgroundDark)
        ) {
            TopBar(
                honey = currentHoney,
                onOpenShop = { showShopDialog = true },
                onOpenAvatars = { showAvatarDialog = true },
                onOpenReset = { showResetDialog = true }
            )
            HorizontalDivider(color = CardBorder, thickness = 1.dp)
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    ModeCard(
                        mode = GameMode.CLASSIC,
                        title = "Classic",
                        subtitle = "The traditional relaxing maze puzzle",
                        color = NeonGreen,
                        icon = Icons.Default.Eco,
                        isNew = false,
                        prefsManager = prefsManager,
                        currentHoney = currentHoney,
                        onUnlock = { },
                        onClick = { onModeSelected(GameMode.CLASSIC) }
                    )
                }
                item {
                    ModeCard(
                        mode = GameMode.CHASERS,
                        title = "Chasers",
                        subtitle = "The enemies hunt you down",
                        color = NeonCoral,
                        icon = Icons.Default.Face,
                        isNew = false,
                        prefsManager = prefsManager,
                        currentHoney = currentHoney,
                        onUnlock = { },
                        onClick = { onModeSelected(GameMode.CHASERS) }
                    )
                }
                item {
                    ModeCard(
                        mode = GameMode.TRAPS,
                        title = "Traps",
                        subtitle = "Beware the orange spikes!",
                        color = NeonYellow,
                        icon = Icons.Default.Warning,
                        isNew = false,
                        prefsManager = prefsManager,
                        currentHoney = currentHoney,
                        onUnlock = { },
                        onClick = { onModeSelected(GameMode.TRAPS) }
                    )
                }
                item {
                    ModeCard(
                        mode = GameMode.LAVA_FLOOR,
                        title = "Lava Floor",
                        subtitle = "The floor is lava! Don't retrace steps",
                        color = Color(0xFFFF3300), // Vibrant Fiery Red/Orange
                        icon = Icons.Default.Whatshot,
                        isNew = false,
                        prefsManager = prefsManager,
                        currentHoney = currentHoney,
                        onUnlock = { },
                        onClick = { onModeSelected(GameMode.LAVA_FLOOR) }
                    )
                }
                item {
                    ModeCard(
                        mode = GameMode.DARKNESS,
                        title = "Darkness",
                        subtitle = "Fog of War mechanics",
                        color = NeonPurple,
                        icon = Icons.Default.VisibilityOff,
                        isNew = false,
                        prefsManager = prefsManager,
                        currentHoney = currentHoney,
                        onUnlock = { },
                        onClick = { onModeSelected(GameMode.DARKNESS) }
                    )
                }
                item {
                    ModeCard(
                        mode = GameMode.ICE_SLIDE,
                        title = "Ice Slide",
                        subtitle = "Slide until you hit a wall!",
                        color = Color(0xFF00BFFF), // Deep Sky Blue
                        icon = Icons.Default.AcUnit,
                        isNew = false,
                        prefsManager = prefsManager,
                        currentHoney = currentHoney,
                        onUnlock = { },
                        onClick = { onModeSelected(GameMode.ICE_SLIDE) }
                    )
                }
                item {
                    ModeCard(
                        mode = GameMode.TIME_RUSH,
                        title = "Time Rush",
                        subtitle = "Race against the ticking clock!",
                        color = Color(0xFFFF5722), // Vibrant Neon Orange
                        icon = Icons.Default.Timer,
                        isNew = false,
                        prefsManager = prefsManager,
                        currentHoney = currentHoney,
                        onUnlock = { },
                        onClick = { onModeSelected(GameMode.TIME_RUSH) }
                    )
                }
                
                item {
                    // Removed StatsSection as requested
                }
            }
        }
    }
}

@Composable
fun TopBar(
    honey: Int,
    onOpenShop: () -> Unit = {},
    onOpenAvatars: () -> Unit = {},
    onOpenReset: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Side: AVATARS & RESET Buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBackground)
                    .clickable { onOpenAvatars() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Face,
                    contentDescription = "Avatars",
                    tint = Color(0xFF00FFCC),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "AVATARS",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Reset Data Button right next to Avatars Icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBackground)
                    .clickable { onOpenReset() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset All Data",
                    tint = NeonCoral,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "RESET",
                    color = NeonCoral,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Right Side: Coins Shop Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(CardBackground)
                .clickable { onOpenShop() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Coins",
                tint = NeonYellow,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$honey",
                color = NeonYellow,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.AddCircle,
                contentDescription = "Buy Honey",
                tint = NeonGreen,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun AvatarSelectionDialog(
    prefsManager: com.example.honeycombmaze.data.PreferencesManager,
    onDismiss: () -> Unit
) {
    val currentHoney = prefsManager.honey
    val selectedAvatarId = prefsManager.selectedAvatar

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Select Avatar 🎭", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 20.sp)
                Text("🍯 $currentHoney", fontWeight = FontWeight.Bold, color = NeonYellow, fontSize = 16.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val chunks = com.example.honeycombmaze.data.AvatarRegistry.AVATARS.chunked(4)
                chunks.forEach { rowAvatars ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        rowAvatars.forEach { avatar ->
                            val isUnlocked = prefsManager.isAvatarUnlocked(avatar.id)
                            val isEquipped = selectedAvatarId == avatar.id

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(68.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isEquipped) Color(0xFF1E293B) 
                                        else if (isUnlocked) CardBackground 
                                        else Color(0xFF161B26)
                                    )
                                    .border(
                                        width = if (isEquipped) 2.dp else 1.dp,
                                        color = if (isEquipped) NeonGreen else if (isUnlocked) CardBorder else Color(0xFF2A3447),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        if (isUnlocked) {
                                            prefsManager.selectedAvatar = avatar.id
                                        } else if (currentHoney >= avatar.cost) {
                                            prefsManager.honey -= avatar.cost
                                            prefsManager.unlockAvatar(avatar.id)
                                            prefsManager.selectedAvatar = avatar.id
                                        }
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp)
                            ) {
                                // Avatar Icon / Emoji
                                Box(
                                    modifier = Modifier.size(40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (avatar.id == "default") {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF5B8DEF))
                                        )
                                    } else {
                                        Text(
                                            text = avatar.emoji,
                                            fontSize = 30.sp,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(6.dp))

                                // Title / Status
                                if (isEquipped) {
                                    Text(
                                        text = "EQUIPPED",
                                        color = NeonGreen,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else if (isUnlocked) {
                                    Text(
                                        text = avatar.name.uppercase(),
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "🍯${avatar.cost}",
                                            color = if (currentHoney >= avatar.cost) NeonYellow else TextSecondary,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CLOSE", color = TextSecondary, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color(0xFF0F172A)
    )
}

@Composable
fun ModeCard(
    mode: GameMode,
    title: String,
    subtitle: String,
    color: Color,
    icon: ImageVector,
    isNew: Boolean,
    prefsManager: com.example.honeycombmaze.data.PreferencesManager,
    currentHoney: Int,
    onUnlock: () -> Unit,
    onClick: () -> Unit
) {
    val isUnlocked = prefsManager.isModeUnlocked(mode.id)
    val cost = com.example.honeycombmaze.data.PreferencesManager.MODE_COSTS[mode.id] ?: 0
    var showBuyDialog by remember { mutableStateOf(false) }

    if (showBuyDialog) {
        AlertDialog(
            onDismissRequest = { showBuyDialog = false },
            title = { Text("Unlock Mode", fontWeight = FontWeight.Bold) },
            text = { Text("Unlock $title for $cost Coins? You have $currentHoney Coins.") },
            confirmButton = {
                TextButton(onClick = {
                    showBuyDialog = false
                    if (currentHoney >= cost) {
                        prefsManager.honey -= cost
                        prefsManager.unlockMode(mode.id)
                        onUnlock()
                    }
                }) {
                    Text("UNLOCK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBuyDialog = false }) {
                    Text("CANCEL")
                }
            }
        )
    }

    val displayColor = if (isUnlocked) color else Color.DarkGray
    val displayIcon = if (isUnlocked) icon else Icons.Default.Lock

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isUnlocked) {
                    onClick()
                } else {
                    showBuyDialog = true
                }
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, displayColor.copy(alpha = 0.3f))
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Hexagon Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(HexagonShape())
                        .background(color.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(HexagonShape())
                            .background(color),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = displayIcon,
                            contentDescription = null,
                            tint = CardBackground,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column {
                    Text(
                        text = title,
                        color = displayColor,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = subtitle,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            
            if (isNew && isUnlocked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(displayColor, RoundedCornerShape(bottomStart = 8.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "NEW",
                        color = CardBackground,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (!isUnlocked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(displayColor, RoundedCornerShape(bottomStart = 8.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$cost COINS",
                        color = CardBackground,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun BottomNavBar() {
    Surface(
        color = CardBackground,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavBarItem(icon = Icons.Default.GridView, label = "Play", isSelected = true)
            NavBarItem(icon = Icons.Default.Widgets, label = "Modes", isSelected = false)
            NavBarItem(icon = Icons.Default.ShoppingCart, label = "Shop", isSelected = false)
            NavBarItem(icon = Icons.Default.Menu, label = "Menu", isSelected = false)
        }
    }
}

@Composable
fun NavBarItem(icon: ImageVector, label: String, isSelected: Boolean) {
    val color = if (isSelected) BackgroundDark else TextSecondary
    val bgModifier = if (isSelected) {
        Modifier
            .background(NeonYellow, CircleShape)
            .padding(16.dp)
    } else {
        Modifier.padding(16.dp)
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { }
    ) {
        Box(modifier = bgModifier, contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = color)
        }
        if (isSelected) {
            Text(label, color = NeonYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        } else {
            Text(label, color = TextSecondary, fontSize = 12.sp)
        }
    }
}
