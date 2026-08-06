package com.example.honeycombmaze.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.example.honeycombmaze.game.GameState
import com.example.honeycombmaze.game.HexLayout
import com.example.honeycombmaze.game.Point
import com.example.honeycombmaze.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2

data class Particle(
    val id: Int,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float,
    val color: Color
)

@Composable
fun GameScreen(
    gameState: GameState,
    prefsManager: com.example.honeycombmaze.data.PreferencesManager? = null,
    onNextLevel: () -> Unit = { gameState.nextLevel() }
) {
    var dragAccumulator by remember { mutableStateOf(Offset.Zero) }
    var particles by remember { mutableStateOf(emptyList<Particle>()) }
    var particleIdCounter by remember { mutableStateOf(0) }

    LaunchedEffect(gameState.isWon, gameState.isGameOver, gameState.isPaused) {
        while (!gameState.isWon && !gameState.isGameOver && !gameState.isPaused) {
            delay(1000L) // Enemies move every 1000ms
            gameState.moveEnemies()
            gameState.timeSeconds++
        }
    }

    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GoalPulse"
    )
    
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(gameState.level, gameState.gameMode) {
        panOffset = Offset.Zero
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        val widthF = constraints.maxWidth.toFloat()
        val heightF = constraints.maxHeight.toFloat()
        
        val density = androidx.compose.ui.platform.LocalDensity.current.density
        val topOffset = minOf(65f * density, heightF * 0.075f)
        val bottomOffset = minOf(145f * density, heightF * 0.17f)
        
        val availableHeight = kotlin.math.max(heightF - topOffset - bottomOffset, heightF * 0.6f)
    
        val hexSizeX = (widthF - 12f * density) / (gameState.gridCols + 0.5f) / kotlin.math.sqrt(3f)
        val hexSizeY = (availableHeight - 8f * density) / (gameState.gridRows * 1.5f + 0.5f)
        val hexSize = kotlin.math.min(hexSizeX, hexSizeY)

        val center = Point(
            x = widthF / 2f - 0.25f * hexSize * kotlin.math.sqrt(3f),
            y = topOffset + availableHeight / 2f
        )

        val layout = HexLayout(hexSize, center)

        val goalTextPaint = remember(hexSize) {
            android.graphics.Paint().apply {
                textSize = hexSize * 0.38f
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                color = android.graphics.Color.WHITE
                isAntiAlias = true
                setShadowLayer(10f, 0f, 0f, android.graphics.Color.parseColor("#FFD740"))
            }
        }

        fun isVisible(coord: com.example.honeycombmaze.game.HexCoord): Boolean {
            if (gameState.gameMode != com.example.honeycombmaze.game.GameMode.DARKNESS) return true
            val dist = (kotlin.math.abs(coord.q - gameState.playerPos.q) + 
                        kotlin.math.abs(coord.q + coord.r - gameState.playerPos.q - gameState.playerPos.r) + 
                        kotlin.math.abs(coord.r - gameState.playerPos.r)) / 2
            return dist <= 2
        }

        var playerCenterPixel by remember { mutableStateOf(Offset.Zero) }

        LaunchedEffect(gameState.gameMode, gameState.isWon, gameState.isGameOver, gameState.isPaused) {
            if (gameState.gameMode == com.example.honeycombmaze.game.GameMode.TIME_RUSH) {
                while (!gameState.isWon && !gameState.isGameOver && !gameState.isPaused && gameState.timeRemaining > 0) {
                    kotlinx.coroutines.delay(1000L)
                    if (!gameState.isPaused && !gameState.isWon && !gameState.isGameOver) {
                        gameState.timeRemaining--
                        if (gameState.timeRemaining <= 0) {
                            gameState.isGameOver = true
                            com.example.honeycombmaze.game.SoundManager.playGameOverSound()
                        }
                    }
                }
            }
        }
        
        LaunchedEffect(gameState.isWon) {
            if (gameState.isWon) {
                val center = layout.hexToPixel(gameState.goalPos)
                val newParticles = (0..50).map { i ->
                    val angle = (Math.random() * PI * 2).toFloat()
                    val speed = (Math.random() * 20 + 10).toFloat()
                    Particle(
                        id = particleIdCounter++,
                        x = center.x,
                        y = center.y,
                        vx = kotlin.math.cos(angle) * speed,
                        vy = kotlin.math.sin(angle) * speed,
                        life = 1f,
                        maxLife = (Math.random() * 0.5f + 0.5f).toFloat(),
                        color = if (Math.random() > 0.5) Color(0xFF00FFCC) else Color(0xFFFFD740)
                    )
                }
                particles = particles + newParticles
            }
        }

        LaunchedEffect(gameState.isGameOver) {
            if (gameState.isGameOver && gameState.gameMode == com.example.honeycombmaze.game.GameMode.TRAPS && gameState.traps.contains(gameState.playerPos)) {
                val center = layout.hexToPixel(gameState.playerPos)
                val newParticles = (0..50).map { i ->
                    val angle = (Math.random() * PI * 2).toFloat()
                    val speed = (Math.random() * 20 + 10).toFloat()
                    Particle(
                        id = particleIdCounter++,
                        x = center.x,
                        y = center.y,
                        vx = kotlin.math.cos(angle) * speed,
                        vy = kotlin.math.sin(angle) * speed,
                        life = 1f,
                        maxLife = (Math.random() * 0.5f + 0.5f).toFloat(),
                        color = Color(0xFFFF5500)
                    )
                }
                particles = particles + newParticles
            }
        }

        LaunchedEffect(Unit) {
            var lastTime = 0L
            while (true) {
                androidx.compose.runtime.withFrameMillis { frameTime ->
                    if (lastTime == 0L) lastTime = frameTime
                    val dt = (frameTime - lastTime) / 1000f
                    lastTime = frameTime
                    
                    if (particles.isNotEmpty()) {
                        particles = particles.mapNotNull { p ->
                            val newLife = p.life - dt
                            if (newLife > 0) {
                                p.copy(
                                    x = p.x + p.vx,
                                    y = p.y + p.vy,
                                    life = newLife
                                )
                            } else null
                        }
                    }
                }
            }
        }

        var dragAccumulator by remember { mutableStateOf(Offset.Zero) }
        var dragTriggered by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(gameState.level) {
                        detectDragGestures(
                            onDragStart = { 
                                dragAccumulator = Offset.Zero 
                                dragTriggered = false
                            },
                            onDragEnd = {
                                if (!dragTriggered) {
                                    val dx = dragAccumulator.x
                                    val dy = dragAccumulator.y
                                    if (dx * dx + dy * dy > 200) {
                                        val dir = getDirectionFromSwipe(dx, dy)
                                        if (dir != -1) {
                                            gameState.movePlayer(dir)
                                        }
                                    }
                                }
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            dragAccumulator += dragAmount
                            val dx = dragAccumulator.x
                            val dy = dragAccumulator.y
                            if (!dragTriggered && dx * dx + dy * dy > 400) {
                                val dir = getDirectionFromSwipe(dx, dy)
                                if (dir != -1) {
                                    gameState.movePlayer(dir)
                                    dragTriggered = true
                                }
                            }
                        }
                    }
                    .pointerInput(gameState.level) {
                        detectTapGestures { offset ->
                            val dx = offset.x - playerCenterPixel.x
                            val dy = offset.y - playerCenterPixel.y
                            if (dx * dx + dy * dy > 300) { 
                                val dir = getDirectionFromSwipe(dx, dy)
                                if (dir != -1) {
                                    gameState.movePlayer(dir)
                                }
                            }
                        }
                    }
            ) {
                // Draw Hex Grid and Glowing Walls
                for ((coord, cell) in gameState.grid) {
                if (!isVisible(coord)) continue
                
                val corners = layout.polygonCorners(coord)
                
                val path = Path().apply {
                    moveTo(corners[0].x, corners[0].y)
                    for (i in 1..5) {
                        lineTo(corners[i].x, corners[i].y)
                    }
                    close()
                }
                
                // Base glowing dark grid fill
                drawPath(
                    path = path,
                    color = Color(0xFF14192B)
                )

                // Subtle Hexagon Grid Outline
                drawPath(
                    path = path,
                    color = Color(0x1A69F0AE),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
                )

                val (wallGlowColor, wallCoreColor) = when (gameState.gameMode) {
                    com.example.honeycombmaze.game.GameMode.CLASSIC -> 
                        Pair(Color(0x4400FFCC), Color(0xFF00FFCC))
                    com.example.honeycombmaze.game.GameMode.CHASERS -> 
                        Pair(Color(0x44FF0055), Color(0xFFFF0055))
                    com.example.honeycombmaze.game.GameMode.TRAPS -> 
                        Pair(Color(0x44FFAB00), Color(0xFFFFAB00))
                    com.example.honeycombmaze.game.GameMode.DARKNESS -> 
                        Pair(Color(0x44D500F9), Color(0xFFD500F9))
                    com.example.honeycombmaze.game.GameMode.LAVA_FLOOR -> 
                        Pair(Color(0x44FF3D00), Color(0xFFFF3D00))
                    com.example.honeycombmaze.game.GameMode.ICE_SLIDE -> 
                        Pair(Color(0x4400E5FF), Color(0xFF00E5FF))
                    com.example.honeycombmaze.game.GameMode.TIME_RUSH -> 
                        Pair(Color(0x4476FF03), Color(0xFF76FF03))
                }

                // Dual-Pass Glowing Walls
                for (i in 0..5) {
                    if (cell.walls[i]) {
                        val p1 = corners[i]
                        val p2 = corners[(i + 1) % 6]
                        // Outer translucent neon bloom glow
                        drawLine(
                            color = wallGlowColor,
                            start = Offset(p1.x, p1.y),
                            end = Offset(p2.x, p2.y),
                            strokeWidth = 14f
                        )
                        // Inner crisp bright neon line
                        drawLine(
                            color = wallCoreColor,
                            start = Offset(p1.x, p1.y),
                            end = Offset(p2.x, p2.y),
                            strokeWidth = 6f
                        )
                    }
                }
            }

            // Draw Goal Exit Portal (Golden Vortex + Attractive GOAL Text)
            if (isVisible(gameState.goalPos)) {
                val goalCenter = layout.hexToPixel(gameState.goalPos)
                // Outer pulsing bloom ring
                drawCircle(
                    color = Color(0x44FFD740),
                    radius = hexSize * pulseScale * 1.25f,
                    center = Offset(goalCenter.x, goalCenter.y)
                )
                drawCircle(
                    color = Color(0x88FFD740),
                    radius = hexSize * pulseScale * 1.05f,
                    center = Offset(goalCenter.x, goalCenter.y)
                )
                drawCircle(
                    color = Color(0xFFFFC107),
                    radius = hexSize * 0.65f,
                    center = Offset(goalCenter.x, goalCenter.y)
                )
                drawCircle(
                    color = Color(0xFF14192B),
                    radius = hexSize * 0.54f,
                    center = Offset(goalCenter.x, goalCenter.y)
                )
                // Render attractive GOAL text
                drawContext.canvas.nativeCanvas.drawText(
                    "🎯 GOAL",
                    goalCenter.x,
                    goalCenter.y + (hexSize * 0.14f),
                    goalTextPaint
                )
            }

            // Draw Traps
            for (trapPos in gameState.traps) {
                if (!isVisible(trapPos)) continue
                val trapCenter = layout.hexToPixel(trapPos)
                val path = Path().apply {
                    moveTo(trapCenter.x, trapCenter.y - hexSize * 0.42f)
                    lineTo(trapCenter.x - hexSize * 0.35f, trapCenter.y + hexSize * 0.35f)
                    lineTo(trapCenter.x + hexSize * 0.35f, trapCenter.y + hexSize * 0.35f)
                    close()
                }
                drawPath(path, Color(0xFFFF5500))
            }

            // Draw Lava Floor Tiles
            if (gameState.gameMode == com.example.honeycombmaze.game.GameMode.LAVA_FLOOR) {
                for (lavaPos in gameState.lavaTiles) {
                    if (isVisible(lavaPos)) {
                        val lc = layout.hexToPixel(lavaPos)
                        drawCircle(
                            color = Color(0x88FF3300),
                            radius = hexSize * pulseScale,
                            center = Offset(lc.x, lc.y)
                        )
                        drawCircle(
                            color = Color(0xFFFF3300),
                            radius = hexSize * 0.42f,
                            center = Offset(lc.x, lc.y)
                        )
                        drawCircle(
                            color = Color(0xFFFFCC00),
                            radius = hexSize * 0.2f,
                            center = Offset(lc.x, lc.y)
                        )
                    }
                }
            }

            // Draw Time Rush Bonus Orbs (+5s)
            if (gameState.gameMode == com.example.honeycombmaze.game.GameMode.TIME_RUSH) {
                for (orbPos in gameState.timeBonusOrbs) {
                    if (isVisible(orbPos)) {
                        val oc = layout.hexToPixel(orbPos)
                        drawCircle(
                            color = Color(0x88FF5722),
                            radius = hexSize * pulseScale,
                            center = Offset(oc.x, oc.y)
                        )
                        drawCircle(
                            color = Color(0xFFFF5722),
                            radius = hexSize * 0.38f,
                            center = Offset(oc.x, oc.y)
                        )
                        drawCircle(
                            color = Color(0xFFFFCC00),
                            radius = hexSize * 0.2f,
                            center = Offset(oc.x, oc.y)
                        )
                    }
                }
            }
        }
        
        val playerTarget = layout.hexToPixel(gameState.playerPos)
        val playerX = remember(gameState.level) { androidx.compose.animation.core.Animatable(playerTarget.x) }
        val playerY = remember(gameState.level) { androidx.compose.animation.core.Animatable(playerTarget.y) }
        
        LaunchedEffect(playerTarget) {
            if (kotlin.math.abs(playerX.value - playerTarget.x) > hexSize * 2) {
                playerX.snapTo(playerTarget.x)
                playerY.snapTo(playerTarget.y)
            } else {
                launch { playerX.animateTo(playerTarget.x, tween(150)) }
                launch { playerY.animateTo(playerTarget.y, tween(150)) }
            }
        }
        playerCenterPixel = Offset(playerX.value, playerY.value)

        val animatedEnemies = gameState.enemies.mapIndexed { index, enemyPos ->
            val target = layout.hexToPixel(enemyPos)
            val ex = remember(gameState.level, index) { androidx.compose.animation.core.Animatable(target.x) }
            val ey = remember(gameState.level, index) { androidx.compose.animation.core.Animatable(target.y) }
            LaunchedEffect(target) {
                if (kotlin.math.abs(ex.value - target.x) > hexSize * 2) {
                    ex.snapTo(target.x)
                    ey.snapTo(target.y)
                } else {
                    launch { ex.animateTo(target.x, tween(150)) }
                    launch { ey.animateTo(target.y, tween(150)) }
                }
            }
            Offset(ex.value, ey.value)
        }

        val avatarId = prefsManager?.selectedAvatar ?: "default"
        val avatar = com.example.honeycombmaze.data.AvatarRegistry.getAvatar(avatarId)

        val emojiPaint = remember(hexSize) {
            android.graphics.Paint().apply {
                textSize = hexSize * 0.95f
                textAlign = android.graphics.Paint.Align.CENTER
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            if (avatar.id == "default") {
                // Default Glowing Cyan Ball
                drawCircle(
                    color = Color(0x4400FFCC),
                    radius = hexSize * pulseScale * 1.1f,
                    center = playerCenterPixel
                )
                drawCircle(
                    color = Color(0xAA00FFCC),
                    radius = hexSize * 0.55f,
                    center = playerCenterPixel
                )
                drawCircle(
                    color = Color(0xFF00FFCC),
                    radius = hexSize * 0.42f,
                    center = playerCenterPixel
                )
            } else {
                // Draw Emoji Character Avatar
                drawContext.canvas.nativeCanvas.drawText(
                    avatar.emoji,
                    playerCenterPixel.x,
                    playerCenterPixel.y + (hexSize * 0.32f),
                    emojiPaint
                )
            }
            
            for (enemyOffset in animatedEnemies) {
                drawCircle(
                    color = Color(0x44FF0000),
                    radius = hexSize * 0.55f,
                    center = enemyOffset
                )
                drawCircle(
                    color = Color(0xFFFF0000),
                    radius = hexSize * 0.42f,
                    center = enemyOffset
                )
            }
            
            for (p in particles) {
                val alpha = (p.life / p.maxLife).coerceIn(0f, 1f)
                drawCircle(
                    color = p.color.copy(alpha = alpha),
                    radius = hexSize * 0.2f * alpha,
                    center = Offset(p.x, p.y)
                )
            }
        }
    }
        
        // Futuristic Cyber Glass HUD Card
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground.copy(alpha = 0.92f)),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Level Pill Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF232A42))
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "LEVEL ${gameState.level}",
                        color = NeonYellow,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                // Stats Row Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Moves Stat
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "🎯 MOVES",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = String.format(Locale.US, "%02d", gameState.moves),
                            color = NeonGreen,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Divider
                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(CardBorder))

                    // Time / Time Rush Stat
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (gameState.gameMode == com.example.honeycombmaze.game.GameMode.TIME_RUSH) {
                            Text(
                                text = "⏱️ TIME LEFT",
                                color = Color(0xFFFF5722),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "${gameState.timeRemaining}s",
                                color = if (gameState.timeRemaining <= 5) Color(0xFFFF3366) else NeonYellow,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = "⏱️ TIME",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = String.format(Locale.US, "%02d:%02d", gameState.timeSeconds / 60, gameState.timeSeconds % 60),
                                color = Color(0xFF00FFCC),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Divider
                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(CardBorder))

                    // Best Stat
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "🏆 BEST",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = if (gameState.bestMoves == -1) "--" else String.format(Locale.US, "%02d", gameState.bestMoves),
                            color = NeonPurple,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        
        HexController(
            modifier = Modifier.align(Alignment.BottomCenter),
            onMove = { direction -> gameState.movePlayer(direction) },
            isPaused = gameState.isPaused,
            onTogglePause = { gameState.isPaused = !gameState.isPaused }
        )
        
        if (gameState.isWon) {
            val isLastLevel = gameState.level >= 100
            val honeyAwarded = ((gameState.level - 1) / 10) + 1
            var animatedHoney by remember { mutableStateOf(0) }
            
            LaunchedEffect(Unit) {
                for (i in 1..honeyAwarded) {
                    delay(30)
                    animatedHoney = i
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(24.dp))
                    .background(CardBackground.copy(alpha = 0.95f))
                    .border(2.dp, NeonGreen, RoundedCornerShape(24.dp))
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isLastLevel) "ALL LEVELS DONE!" else "LEVEL COMPLETE!",
                    color = NeonGreen,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                if (isLastLevel) {
                    Text(
                        text = "🎉 All levels done for this mode!",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Animated Honey Reward Display
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF232A42))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "🍯",
                        fontSize = 24.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "+$animatedHoney Coins!",
                        color = NeonYellow,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = {
                        if (isLastLevel) {
                            gameState.level = 1
                            gameState.startNewGame()
                        } else {
                            onNextLevel()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                    modifier = Modifier.fillMaxWidth(0.8f).height(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isLastLevel) "REPLAY GAME" else "NEXT LEVEL",
                            color = BackgroundDark,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = BackgroundDark
                        )
                    }
                }
            }
        }

        if (gameState.isGameOver) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(24.dp))
                    .background(CardBackground.copy(alpha = 0.95f))
                    .border(2.dp, NeonCoral, RoundedCornerShape(24.dp))
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "GAME OVER!",
                    color = NeonCoral,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = if (gameState.gameMode == com.example.honeycombmaze.game.GameMode.TRAPS) 
                            "💥 You hit a trap spike!" 
                           else if (gameState.gameMode == com.example.honeycombmaze.game.GameMode.TIME_RUSH)
                            "⏱️ Time ran out!"
                           else 
                            "👾 Caught by enemy!",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { gameState.startNewGame() },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCoral),
                    modifier = Modifier.fillMaxWidth(0.8f).height(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "TRY AGAIN",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

fun getDirectionFromSwipe(dx: Float, dy: Float): Int {
    val angle = atan2(dy, dx) * 180 / PI
    val normalizedAngle = if (angle < 0) angle + 360 else angle
    return when (normalizedAngle) {
        in 330.0..360.0, in 0.0..30.0 -> 0
        in 30.0..90.0 -> 5
        in 90.0..150.0 -> 4
        in 150.0..210.0 -> 3
        in 210.0..270.0 -> 2
        in 270.0..330.0 -> 1
        else -> 0
    }
}

@Composable
fun HexController(modifier: Modifier = Modifier, onMove: (Int) -> Unit, isPaused: Boolean, onTogglePause: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(bottom = 4.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ControlButton(rotation = -60f, onClick = { onMove(2) })
            ControlButton(rotation = 60f, onClick = { onMove(1) })
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlButton(rotation = -90f, onClick = { onMove(3) })
            
            IconButton(
                onClick = onTogglePause,
                modifier = Modifier
                    .size(60.dp)
                    .background(Color(0xFF232A42), CircleShape)
                    .border(2.dp, NeonPurple, CircleShape)
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (isPaused) "Resume" else "Pause",
                    tint = NeonPurple,
                    modifier = Modifier.size(30.dp)
                )
            }

            ControlButton(rotation = 90f, onClick = { onMove(0) })
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ControlButton(rotation = -120f, onClick = { onMove(4) })
            ControlButton(rotation = 120f, onClick = { onMove(5) })
        }
    }
}

@Composable
fun ControlButton(rotation: Float, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .size(60.dp)
            .border(2.dp, Color(0xFF00FFCC).copy(alpha = 0.8f), CircleShape)
            .clip(CircleShape),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2236)),
        contentPadding = PaddingValues(0.dp)
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowUp,
            contentDescription = null,
            tint = Color(0xFF00FFCC),
            modifier = Modifier
                .size(34.dp)
                .rotate(rotation)
        )
    }
}
