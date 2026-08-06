package com.example.honeycombmaze

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.honeycombmaze.data.AppDatabase
import com.example.honeycombmaze.data.GameData
import com.example.honeycombmaze.data.LevelData
import com.example.honeycombmaze.game.GameMode
import com.example.honeycombmaze.game.GameState
import com.example.honeycombmaze.ui.GameScreen
import com.example.honeycombmaze.ui.HexagonShape
import com.example.honeycombmaze.ui.MainMenuScreen
import com.example.honeycombmaze.ui.theme.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

sealed class Screen {
    object Menu : Screen()
    data class LevelSelection(val mode: GameMode) : Screen()
    object Game : Screen()
}

class MainActivity : ComponentActivity() {
    private var mInterstitialAd: InterstitialAd? = null
    private var isAdLoading = false
    private var lastAdTime: Long = 0

    private fun loadInterstitialAd() {
        if (mInterstitialAd != null || isAdLoading) return
        isAdLoading = true
        val adRequest = AdRequest.Builder().build()
        // Standard Interstitial Ad Unit ID with top 'X' close button!
        val adUnitId = "ca-app-pub-5055629303728798/2664966984"
        InterstitialAd.load(this, adUnitId, adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                isAdLoading = false
                ad.setImmersiveMode(true)
                mInterstitialAd = ad
                android.util.Log.d("AdMob", "Interstitial Ad with top X close button loaded successfully!")
            }
            override fun onAdFailedToLoad(adError: LoadAdError) {
                isAdLoading = false
                mInterstitialAd = null
                android.util.Log.e("AdMob", "Ad failed to load: ${adError.message}. Retrying in 5s...")
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    loadInterstitialAd()
                }, 5000)
            }
        })
    }

    private var isAdShowing = false

    private fun showAdWithSafetyTimeout(
        prefsManager: com.example.honeycombmaze.data.PreferencesManager,
        onFullAdWatched: (() -> Unit)? = null,
        onComplete: () -> Unit
    ) {
        if (isAdShowing) {
            onComplete()
            return
        }

        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var hasHandledCompletion = false

        fun safeComplete(wasAdShown: Boolean) {
            if (hasHandledCompletion) return
            hasHandledCompletion = true
            isAdShowing = false
            
            mInterstitialAd = null
            if (wasAdShown) {
                lastAdTime = System.currentTimeMillis()
            }
            loadInterstitialAd()
            
            handler.post {
                onComplete()
            }
        }

        val currentAd = mInterstitialAd
        if (currentAd == null) {
            safeComplete(wasAdShown = false)
            return
        }

        isAdShowing = true
        var adStartTime = 0L

        currentAd.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                lastAdTime = System.currentTimeMillis()
                adStartTime = System.currentTimeMillis()
                android.util.Log.d("AdMob", "Interstitial Ad displayed on screen.")
            }

            override fun onAdDismissedFullScreenContent() {
                val duration = System.currentTimeMillis() - adStartTime
                val FULL_AD_THRESHOLD_MS = 14000L // 14s threshold for watching full ad
                android.util.Log.d("AdMob", "Ad closed by user after ${duration / 1000}s.")

                if (duration >= FULL_AD_THRESHOLD_MS) {
                    prefsManager.honey += 3
                    com.example.honeycombmaze.data.CloudSaveManager.saveToCloud(this@MainActivity, prefsManager)
                    this@MainActivity.runOnUiThread {
                        onFullAdWatched?.invoke()
                    }
                } else {
                    this@MainActivity.runOnUiThread {
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "⚠️ Ad was closed early. Watch the full ad to earn 3 Coins!",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                handler.post { safeComplete(wasAdShown = true) }
            }

            override fun onAdFailedToShowFullScreenContent(e: AdError) {
                android.util.Log.e("AdMob", "Ad failed to show: ${e.message}")
                handler.post { safeComplete(wasAdShown = false) }
            }
        }

        this@MainActivity.runOnUiThread {
            try {
                currentAd.show(this@MainActivity)
            } catch (e: Exception) {
                android.util.Log.e("AdMob", "Exception showing ad: ${e.message}")
                safeComplete(wasAdShown = false)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        com.example.honeycombmaze.game.SoundManager.init()
        com.example.honeycombmaze.data.CloudSaveManager.initializeAndSignIn(this)
        
        val testDeviceIds = listOf("AA258783AFC4376739AEB16BC62D2817", AdRequest.DEVICE_ID_EMULATOR)
        val configuration = com.google.android.gms.ads.RequestConfiguration.Builder()
            .setTestDeviceIds(testDeviceIds)
            .build()
        MobileAds.setRequestConfiguration(configuration)

        // Initialize AdMob & preload ad on completion
        MobileAds.initialize(this) {
            loadInterstitialAd()
        }
        lastAdTime = 0L
        
        enableEdgeToEdge()
        setContent {
            HoneyCombMazeTheme {
                val context = LocalContext.current
                val activity = context as? android.app.Activity
                
                var isNetworkConnected by remember { mutableStateOf(isNetworkAvailable(context)) }

                // Continuous 1-second ticker loop to instantly catch Airplane Mode or Wi-Fi/Data toggles during gameplay
                LaunchedEffect(Unit) {
                    while (true) {
                        val currentConnected = isNetworkAvailable(context)
                        if (isNetworkConnected != currentConnected) {
                            isNetworkConnected = currentConnected
                        }
                        kotlinx.coroutines.delay(1000)
                    }
                }

                androidx.compose.runtime.DisposableEffect(context) {
                    val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                    val callback = object : android.net.ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: android.net.Network) {
                            isNetworkConnected = isNetworkAvailable(context)
                        }
                        override fun onLost(network: android.net.Network) {
                            isNetworkConnected = isNetworkAvailable(context)
                        }
                        override fun onCapabilitiesChanged(network: android.net.Network, capabilities: android.net.NetworkCapabilities) {
                            isNetworkConnected = isNetworkAvailable(context)
                        }
                    }
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                            connectivityManager?.registerDefaultNetworkCallback(callback)
                        } else {
                            val request = android.net.NetworkRequest.Builder()
                                .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                                .build()
                            connectivityManager?.registerNetworkCallback(request, callback)
                        }
                    } catch (_: Exception) {}
                    onDispose {
                        try {
                            connectivityManager?.unregisterNetworkCallback(callback)
                        } catch (_: Exception) {}
                    }
                }

                if (!isNetworkConnected) {
                    AlertDialog(
                        onDismissRequest = { },
                        title = {
                            Text(
                                text = "📡 Network Required",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        },
                        text = {
                            Text(
                                text = "Please turn on Wi-Fi or Mobile Data to continue playing HoneyCombMaze.",
                                color = TextSecondary,
                                fontSize = 16.sp
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    isNetworkConnected = isNetworkAvailable(context)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                            ) {
                                Text("RETRY", color = BackgroundDark, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    try {
                                        val intent = android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        try {
                                            val intent = android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    }
                                }
                            ) {
                                Text("SETTINGS", color = NeonYellow)
                            }
                        },
                        containerColor = CardBackground
                    )
                }

                val dao = remember { AppDatabase.getDatabase(context).gameDataDao() }
                val prefsManager = remember { com.example.honeycombmaze.data.PreferencesManager(context) }

                var showAdPromptDialog by remember { mutableStateOf(false) }
                var showAdRewardDialog by remember { mutableStateOf(false) }
                var pendingAdPromptAction by remember { mutableStateOf<(() -> Unit)?>(null) }

                val triggerAdWithPrompt: (() -> Unit) -> Unit = { onProceed ->
                    if (mInterstitialAd == null) {
                        loadInterstitialAd()
                        onProceed()
                    } else {
                        pendingAdPromptAction = onProceed
                        showAdPromptDialog = true
                    }
                }

                if (showAdPromptDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            showAdPromptDialog = false
                            val action = pendingAdPromptAction
                            pendingAdPromptAction = null
                            action?.invoke()
                        },
                        title = {
                            Text(
                                text = "🎬 Watch Full Ad for +3 Coins!",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        },
                        text = {
                            Text(
                                text = "Watch the full ad to earn 🍯 +3 free Coins! Make sure to watch until the end without closing early to claim your reward.",
                                color = TextSecondary,
                                fontSize = 16.sp
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showAdPromptDialog = false
                                    val action = pendingAdPromptAction
                                    pendingAdPromptAction = null
                                    showAdWithSafetyTimeout(
                                        prefsManager = prefsManager,
                                        onFullAdWatched = {
                                            showAdRewardDialog = true
                                        },
                                        onComplete = {
                                            action?.invoke()
                                        }
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                            ) {
                                Text("WATCH AD (+3 🍯)", color = BackgroundDark, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showAdPromptDialog = false
                                    val action = pendingAdPromptAction
                                    pendingAdPromptAction = null
                                    action?.invoke()
                                }
                            ) {
                                Text("SKIP AD", color = TextSecondary)
                            }
                        },
                        containerColor = CardBackground
                    )
                }

                if (showAdRewardDialog) {
                    var animatedCoins by remember { mutableStateOf(0) }
                    LaunchedEffect(Unit) {
                        com.example.honeycombmaze.game.SoundManager.playWinSound()
                        for (i in 1..3) {
                            kotlinx.coroutines.delay(120)
                            animatedCoins = i
                        }
                        kotlinx.coroutines.delay(2000)
                        showAdRewardDialog = false
                    }

                    androidx.compose.ui.window.Dialog(onDismissRequest = { showAdRewardDialog = false }) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .clip(RoundedCornerShape(24.dp))
                                .background(CardBackground.copy(alpha = 0.96f))
                                .border(2.dp, NeonYellow, RoundedCornerShape(24.dp))
                                .clickable { showAdRewardDialog = false }
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "AD REWARD CLAIMED!",
                                color = NeonYellow,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "🎉 You watched the full ad! Reward unlocked:",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 8.dp)
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            // Animated Honey Coins Display
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF232A42))
                                    .border(1.dp, NeonYellow.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 24.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = "🍯",
                                    fontSize = 28.sp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "+$animatedCoins Coins!",
                                    color = NeonYellow,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                
                val scope = rememberCoroutineScope()
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Menu) }
                val gameState = remember { GameState() }
                var isLoaded by remember { mutableStateOf(false) }
                val maxLevels = remember { androidx.compose.runtime.mutableStateMapOf<GameMode, Int>() }

                val refreshMaxLevels: () -> Unit = {
                    scope.launch(Dispatchers.IO) {
                        GameMode.values().forEach { mode ->
                            val data = dao.getGameData(mode.id)
                            val dbLevel = data?.maxUnlockedLevel ?: 1
                            val prefLevel = prefsManager.getMaxUnlockedLevel(mode.id)
                            val isThisModeLevelsUnlocked = prefsManager.isModeLevelsUnlocked(mode.id)
                            val highest = if (isThisModeLevelsUnlocked) 100 else maxOf(dbLevel, prefLevel)
                            withContext(Dispatchers.Main) {
                                maxLevels[mode] = highest
                            }
                            if (highest > dbLevel) {
                                dao.saveGameData(GameData(mode.id, highest))
                            }
                        }
                    }
                }

                val unlockModeLevelsAction: (GameMode?) -> Unit = { targetMode ->
                    val modeToUnlock = targetMode ?: (currentScreen as? Screen.LevelSelection)?.mode
                    if (modeToUnlock != null) {
                        maxLevels[modeToUnlock] = 100
                        prefsManager.setModeLevelsUnlocked(modeToUnlock.id, true)
                        prefsManager.setMaxUnlockedLevel(modeToUnlock.id, 100)
                        scope.launch(Dispatchers.IO) {
                            dao.saveGameData(GameData(modeToUnlock.id, 100))
                        }
                        com.example.honeycombmaze.data.CloudSaveManager.saveToCloud(context, prefsManager)
                        refreshMaxLevels()
                        android.widget.Toast.makeText(context, "🔓 All 100 levels unlocked for ${modeToUnlock.title}!", android.widget.Toast.LENGTH_LONG).show()
                    }
                }

                val billingManager = remember {
                    com.example.honeycombmaze.data.BillingManager(
                        context = context,
                        prefsManager = prefsManager,
                        onHoneyPurchased = {
                            com.example.honeycombmaze.data.CloudSaveManager.saveToCloud(context, prefsManager)
                        },
                        onModeLevelsUnlocked = { unlockedMode ->
                            unlockModeLevelsAction(unlockedMode)
                        }
                    )
                }

                LaunchedEffect(Unit) {
                    refreshMaxLevels()
                    isLoaded = true
                    com.example.honeycombmaze.data.CloudSaveManager.initializeAndSignIn(context) {
                        com.example.honeycombmaze.data.CloudSaveManager.loadFromCloud(context, prefsManager) {
                            refreshMaxLevels()
                        }
                    }
                }
                
                // Save whenever level changes during gameplay (if it exceeds max)
                LaunchedEffect(gameState.level) {
                    if (isLoaded && currentScreen == Screen.Game) {
                        val currentMax = maxLevels[gameState.gameMode] ?: 1
                        if (gameState.level > currentMax) {
                            maxLevels[gameState.gameMode] = gameState.level
                            prefsManager.setMaxUnlockedLevel(gameState.gameMode.id, gameState.level, syncCloud = true)
                            withContext(Dispatchers.IO) {
                                dao.saveGameData(GameData(gameState.gameMode.id, gameState.level))
                            }
                            com.example.honeycombmaze.data.CloudSaveManager.saveToCloud(context, prefsManager)
                        }
                    }
                }
                
                // Load best moves when starting a new level
                LaunchedEffect(gameState.level, gameState.gameMode, currentScreen) {
                    if (isLoaded && currentScreen == Screen.Game) {
                        withContext(Dispatchers.IO) {
                            val levelData = dao.getLevelData(gameState.gameMode.id, gameState.level)
                            gameState.bestMoves = levelData?.bestMoves ?: -1
                        }
                    }
                }

                // Save best moves and award Honey when won
                LaunchedEffect(gameState.isWon) {
                    if (gameState.isWon) {
                        loadInterstitialAd() // Preload ad for level end
                        prefsManager.honey += (((gameState.level - 1) / 10) + 1)
                        com.example.honeycombmaze.data.CloudSaveManager.saveToCloud(context, prefsManager)
                        com.example.honeycombmaze.game.SoundManager.playHoneyCollectSound()
                        if (gameState.bestMoves == -1 || gameState.moves < gameState.bestMoves) {
                            gameState.bestMoves = gameState.moves
                            withContext(Dispatchers.IO) {
                                dao.saveLevelData(LevelData(gameState.gameMode.id, gameState.level, gameState.bestMoves, gameState.timeSeconds))
                            }
                        }
                    }
                }

                if (!isLoaded) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    return@HoneyCombMazeTheme
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        when (val screen = currentScreen) {
                            is Screen.Menu -> {
                                val totalLevels = maxLevels.values.sum()
                                MainMenuScreen(
                                    prefsManager = prefsManager,
                                    totalLevels = totalLevels,
                                    onModeSelected = { mode ->
                                        currentScreen = Screen.LevelSelection(mode = mode)
                                    },
                                    onWatchAdForReward = {
                                        triggerAdWithPrompt {}
                                    },
                                    onBuyProduct = { productId ->
                                        activity?.let {
                                            billingManager.launchPurchaseFlow(it, productId)
                                        }
                                    },
                                    onResetAllData = {
                                        billingManager.consumeUnlockAllLevels()
                                        prefsManager.resetAllData()
                                        scope.launch(Dispatchers.IO) {
                                            dao.deleteAllGameData()
                                            dao.deleteAllLevelData()
                                            GameMode.values().forEach { mode ->
                                                maxLevels[mode] = 1
                                                prefsManager.setMaxUnlockedLevel(mode.id, 1, syncCloud = false)
                                                dao.saveGameData(GameData(mode.id, 1))
                                            }
                                        }
                                        android.widget.Toast.makeText(context, "🗑️ All game data reset! Purchases cancelled.", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                            is Screen.LevelSelection -> {
                                BackHandler {
                                    currentScreen = Screen.Menu
                                }
                                val isAllUnlocked = prefsManager.isAllLevelsUnlocked
                                val maxUnlocked = if (isAllUnlocked) 100 else (maxLevels[screen.mode] ?: 1)
                                val title = screen.mode.title
                                
                                var showTutorial by remember { mutableStateOf(false) }
                                
                                if (showTutorial) {
                                    val tutorialText =
                                        when(screen.mode) {
                                        GameMode.CLASSIC -> "Navigate the maze to reach the goal. Swipe or use the on-screen controls to move."
                                        GameMode.CHASERS -> "Watch out for the chasers! They will hunt you down. Reach the goal before they catch you."
                                        GameMode.TRAPS -> "The maze is littered with hidden traps. Memorize their locations and step carefully to reach the goal."
                                        GameMode.DARKNESS -> "Your vision is limited. You can only see the immediate surroundings. Reach the goal before you get lost."
                                        GameMode.LAVA_FLOOR -> "The floor is lava! Tiles crumble into fiery lava as you step off them. Plan your path carefully to reach the goal!"
                                        GameMode.TIME_RUSH -> "Race against the clock! Reach the goal before time runs out. Collect +5s time bonus orbs along the way."
                                        GameMode.ICE_SLIDE -> "Once you move, you won't stop sliding until you hit a wall! Plan your path carefully to reach the goal."
                                    }
                                    AlertDialog(
                                        onDismissRequest = { showTutorial = false },
                                        title = { Text("How to Play", fontWeight = FontWeight.Bold) },
                                        text = { Text(tutorialText) },
                                        confirmButton = {
                                            TextButton(onClick = { showTutorial = false }) {
                                                Text("GOT IT")
                                            }
                                        }
                                    )
                                }

                                Column(
                                    modifier = Modifier.fillMaxSize().background(BackgroundDark)
                                ) {
                                    androidx.compose.foundation.layout.Row(
                                         modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
                                         horizontalArrangement = Arrangement.Center,
                                         verticalAlignment = Alignment.CenterVertically
                                     ) {
                                         Text(
                                             text = title,
                                             color = Color.White,
                                             fontSize = 24.sp,
                                             fontWeight = FontWeight.Bold
                                         )
                                         Spacer(modifier = Modifier.size(8.dp))
                                         androidx.compose.material3.IconButton(onClick = { showTutorial = true }) {
                                             androidx.compose.material3.Icon(
                                                 imageVector = androidx.compose.material.icons.Icons.Default.Info,
                                                 contentDescription = "How to Play",
                                                 tint = Color.White
                                             )
                                         }
                                     }

                                     if (maxUnlocked < 100 && !prefsManager.isModeLevelsUnlocked(screen.mode.id)) {
                                         val modeProductId = com.example.honeycombmaze.data.BillingManager.getProductIdForMode(screen.mode)
                                         Button(
                                             onClick = {
                                                 activity?.let {
                                                     billingManager.launchPurchaseFlow(it, modeProductId)
                                                 }
                                             },
                                             colors = ButtonDefaults.buttonColors(containerColor = NeonYellow),
                                             modifier = Modifier
                                                 .fillMaxWidth()
                                                 .padding(horizontal = 16.dp, vertical = 6.dp)
                                         ) {
                                             Text("🔓 UNLOCK ALL 100 LEVELS", color = BackgroundDark, fontWeight = FontWeight.Bold)
                                         }
                                     }
                                     
                                     LazyVerticalGrid(
                                         columns = GridCells.Fixed(5),
                                         modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)
                                     ) {
                                         items(100) { index ->
                                            val levelNumber = index + 1
                                            val maxUnlocked = maxLevels[screen.mode] ?: 1
                                     
                                            LaunchedEffect(screen.mode) {
                                                refreshMaxLevels()
                                            }
                                            val isUnlocked = levelNumber <= maxUnlocked
                                            
                                            val itemColor = if (isUnlocked) NeonGreen else CardBackground
                                            val textColor = if (isUnlocked) BackgroundDark else TextSecondary
                                            
                                            Box(
                                                modifier = Modifier
                                                    .padding(6.dp)
                                                    .aspectRatio(1f)
                                                    .clip(HexagonShape())
                                                    .background(itemColor)
                                                    .clickable(enabled = isUnlocked) {
                                                        gameState.level = levelNumber
                                                        gameState.gameMode = screen.mode
                                                        gameState.startNewGame()

                                                        currentScreen = Screen.Game
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isUnlocked) {
                                                    Text(
                                                        text = levelNumber.toString(),
                                                        color = textColor,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 18.sp
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.Lock,
                                                        contentDescription = "Locked",
                                                        tint = TextSecondary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            is Screen.Game -> {
                                BackHandler {
                                    currentScreen = Screen.Menu
                                }
                                GameScreen(
                                    gameState = gameState,
                                    prefsManager = prefsManager,
                                    onNextLevel = {
                                        if (gameState.level >= 100) {
                                            currentScreen = Screen.LevelSelection(mode = gameState.gameMode)
                                        } else {
                                            // Show ad after every 1 minute (60,000 ms) of app usage
                                            val ONE_MINUTE_MS = 1 * 60 * 1000L
                                            val timeSinceLastAd = System.currentTimeMillis() - lastAdTime
                                            val isRemoveAds = prefsManager.isRemoveAdsPurchased
                                            val shouldShowAd = !isRemoveAds && (timeSinceLastAd >= ONE_MINUTE_MS)
                                            android.util.Log.d("AdMob", "onNextLevel -> timeSinceLastAd=${timeSinceLastAd/1000}s, isRemoveAds=$isRemoveAds, shouldShowAd=$shouldShowAd")
                                            if (shouldShowAd) {
                                                triggerAdWithPrompt {
                                                    gameState.nextLevel()
                                                }
                                            } else {
                                                gameState.nextLevel()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = com.example.honeycombmaze.data.PreferencesManager(this)
        com.example.honeycombmaze.data.CloudSaveManager.initializeAndSignIn(this) {
            com.example.honeycombmaze.data.CloudSaveManager.loadFromCloud(this, prefs)
        }
        if (isAdShowing) {
            android.util.Log.w("AdMob", "App resumed while ad was active. Clearing ad state.")
            isAdShowing = false
            mInterstitialAd = null
            loadInterstitialAd()
        }
    }

    override fun onPause() {
        super.onPause()
        com.example.honeycombmaze.data.CloudSaveManager.saveToCloud(this, com.example.honeycombmaze.data.PreferencesManager(this))
    }

    override fun onStop() {
        super.onStop()
        com.example.honeycombmaze.data.CloudSaveManager.saveToCloud(this, com.example.honeycombmaze.data.PreferencesManager(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        com.example.honeycombmaze.data.CloudSaveManager.saveToCloud(this, com.example.honeycombmaze.data.PreferencesManager(this))
        com.example.honeycombmaze.game.SoundManager.release()
    }
}

fun isNetworkAvailable(context: android.content.Context): Boolean {
    val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
    val activeNetwork = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
    return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
