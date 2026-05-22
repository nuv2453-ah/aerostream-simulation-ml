package com.example.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.FluidViewModel

enum class NotchType {
    NONE,
    DYNAMIC_ISLAND,
    CLASSIC_NOTCH_SLIM,
    CLASSIC_NOTCH_WIDE,
    BEZEL_TOUCH_ID
}

enum class ApplePhoneModel(
    val displayName: String,
    val notchType: NotchType,
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val cornerRadiusDp: Int,
    val bezelThicknessDp: Int,
    val frameWeight: String
) {
    NONE("Standard Android Layout", NotchType.NONE, 0, 0, 0, 0, "N/A"),
    IPHONE_15_PRO_MAX("iPhone 15 Pro Max", NotchType.DYNAMIC_ISLAND, 385, 830, 42, 6, "Titanium Gray"),
    IPHONE_15_PRO("iPhone 15 Pro", NotchType.DYNAMIC_ISLAND, 360, 780, 40, 6, "Space Black"),
    IPHONE_14_PRO_MAX("iPhone 14 Pro Max", NotchType.DYNAMIC_ISLAND, 385, 830, 40, 7, "Deep Purple"),
    IPHONE_13_PRO("iPhone 13 / 14 Pro", NotchType.CLASSIC_NOTCH_SLIM, 360, 780, 36, 8, "Surgical Steel"),
    IPHONE_11_XR("iPhone 11 / XR", NotchType.CLASSIC_NOTCH_WIDE, 360, 780, 32, 11, "Brushed Aluminum"),
    IPHONE_SE_RETRO("iPhone SE / 8 Retro", NotchType.BEZEL_TOUCH_ID, 340, 680, 16, 2, "Space Gray")
}

@Composable
fun AppleDeviceSimulatorWrapper(
    selectedModel: ApplePhoneModel,
    viewModel: FluidViewModel,
    onModelChange: (ApplePhoneModel) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (paddingValues: PaddingValues) -> Unit
) {
    if (selectedModel == ApplePhoneModel.NONE) {
        Column(modifier = modifier.fillMaxSize()) {
            DeviceSelectorBar(selectedModel, onModelChange)
            Box(modifier = Modifier.weight(1f)) {
                content(PaddingValues(0.dp))
            }
        }
    } else {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF020617)),
            contentAlignment = Alignment.Center
        ) {
            val availableWidth = maxWidth
            val availableHeight = maxHeight

            // Core simulated screen parameters
            val devW = selectedModel.screenWidthDp.dp
            val devH = selectedModel.screenHeightDp.dp

            // Compute ideal scale factor to fit standard devices
            val padX = 24.dp
            val padY = 88.dp // Space for selector header and some breathing room
            val scaleX = (availableWidth - padX) / devW
            val scaleY = (availableHeight - padY) / devH
            val scale = minOf(scaleX, scaleY).coerceIn(0.2f, 1.0f)

            var isIslandExpanded by remember { mutableStateOf(false) }
            val isPlaying by viewModel.isPlaying.collectAsState()
            val isAiLoading by viewModel.isAiLoading.collectAsState()
            val isTraining by viewModel.isTraining.collectAsState()
            val datasetGenerating by viewModel.isDatasetGenerating.collectAsState()
            val dragForce by viewModel.dragForce.collectAsState()
            val liftForce by viewModel.liftForce.collectAsState()
            val inletVel by viewModel.inletVelocity.collectAsState()

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                // Preset quick swap menu above the simulated device
                DeviceSelectorBar(selectedModel, onModelChange)

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .size(
                            devW + (selectedModel.bezelThicknessDp * 2).dp,
                            devH + (selectedModel.bezelThicknessDp * 2).dp
                        )
                        .scale(scale)
                        .clip(RoundedCornerShape((selectedModel.cornerRadiusDp + selectedModel.bezelThicknessDp).dp))
                        .background(
                            Brush.verticalGradient(
                                colors = when {
                                    selectedModel.displayName.contains("15") -> listOf(
                                        Color(0xFF8E8E93), // Brushed Gray Titanium
                                        Color(0xFF333336),
                                        Color(0xFF1C1C1E)
                                    )
                                    selectedModel.displayName.contains("14") -> listOf(
                                        Color(0xFF5B4D6C), // Deep Purple surgical frame tint
                                        Color(0xFF1F1A26)
                                    )
                                    else -> listOf(
                                        Color(0xFF48484A), // Dark slate steel highlights
                                        Color(0xFF1C1C1E)
                                    )
                                }
                            )
                        )
                        .border(
                            width = 2.5.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.4f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.8f)
                                )
                            ),
                            shape = RoundedCornerShape((selectedModel.cornerRadiusDp + selectedModel.bezelThicknessDp).dp)
                        )
                        .padding((selectedModel.bezelThicknessDp).dp)
                ) {
                    // Virtual phone glass display screen
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(selectedModel.cornerRadiusDp.dp))
                            .background(Color(0xFF090D16))
                    ) {
                        // Safe area offsets
                        val topSectionPadding = when (selectedModel.notchType) {
                            NotchType.NONE -> 0.dp
                            NotchType.DYNAMIC_ISLAND -> 42.dp
                            NotchType.CLASSIC_NOTCH_SLIM -> 36.dp
                            NotchType.CLASSIC_NOTCH_WIDE -> 38.dp
                            NotchType.BEZEL_TOUCH_ID -> 64.dp
                        }

                        val bottomSectionPadding = when (selectedModel.notchType) {
                            NotchType.BEZEL_TOUCH_ID -> 74.dp
                            else -> 22.dp
                        }

                        // Embedded application viewport inside safe areas
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = topSectionPadding, bottom = bottomSectionPadding)
                        ) {
                            content(PaddingValues(0.dp))
                        }

                        // Draw classic status bars, status items, dynamic islands, home mechanical keys
                        when (selectedModel.notchType) {
                            NotchType.DYNAMIC_ISLAND -> {
                                iOSStatusBar()
                                DynamicIslandOverlay(
                                    isExpanded = isIslandExpanded,
                                    onToggleExpand = { isIslandExpanded = !isIslandExpanded },
                                    isPlaying = isPlaying,
                                    isAiLoading = isAiLoading,
                                    isTraining = isTraining || datasetGenerating,
                                    dragForce = dragForce,
                                    liftForce = liftForce,
                                    inletVel = inletVel,
                                    onTogglePlay = { viewModel.togglePlaying() }
                                )
                            }
                            NotchType.CLASSIC_NOTCH_SLIM -> {
                                iOSStatusBar()
                                ClassicNotchOverlay(isWide = false)
                            }
                            NotchType.CLASSIC_NOTCH_WIDE -> {
                                iOSStatusBar()
                                ClassicNotchOverlay(isWide = true)
                            }
                            NotchType.BEZEL_TOUCH_ID -> {
                                RetroBezelOverlay(
                                    onHomeClick = {
                                        viewModel.clearCustomSketch()
                                        viewModel.setObstacleType("Cylinder")
                                    }
                                )
                            }
                            else -> {}
                        }

                        // iOS bottom indicator horizontal sweep pill bar
                        if (selectedModel.notchType != NotchType.BEZEL_TOUCH_ID && selectedModel.notchType != NotchType.NONE) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 6.dp)
                                    .width(130.dp)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color.White.copy(alpha = 0.85f))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceSelectorBar(
    selectedModel: ApplePhoneModel,
    onModelChange: (ApplePhoneModel) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131A2A)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .border(0.5.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "🍏 APPLE PHONE ADAPTIVE GRAPHICS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
                Box(
                    modifier = Modifier
                        .background(Color(0xFF0369A1), RoundedCornerShape(20.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = selectedModel.displayName.uppercase(),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(ApplePhoneModel.values().toList()) { model ->
                        val isActive = (model == selectedModel)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isActive) Color(0xFF0EA5E9) else Color(0xFF1E293B))
                                .border(
                                    width = 1.dp,
                                    color = if (isActive) Color.White else Color.Transparent,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { onModelChange(model) }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = when (model) {
                                    ApplePhoneModel.NONE -> "ANDROID DESIGN"
                                    ApplePhoneModel.IPHONE_15_PRO_MAX -> "IPHONE 15 PRO MAX"
                                    ApplePhoneModel.IPHONE_15_PRO -> "IPHONE 15 PRO"
                                    ApplePhoneModel.IPHONE_14_PRO_MAX -> "IPHONE 14 PRO MAX"
                                    ApplePhoneModel.IPHONE_13_PRO -> "IPHONE 13/14"
                                    ApplePhoneModel.IPHONE_11_XR -> "IPHONE 11/XR"
                                    ApplePhoneModel.IPHONE_SE_RETRO -> "SE / 8"
                                },
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isActive) Color.White else Color.Gray,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BoxScope.iOSStatusBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .padding(horizontal = 16.dp)
            .align(Alignment.TopCenter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Classic bold left ear physical clock
        Text(
            text = "9:41",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontFamily = FontFamily.SansSerif
        )

        Spacer(modifier = Modifier.weight(1f))

        // Standard Right ear physical indicator arrays
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Signal metrics bar
            Canvas(modifier = Modifier.size(width = 14.dp, height = 7.dp)) {
                val stepW = 2.0f
                val spacing = 1.0f
                for (i in 0 until 4) {
                    val progressH = (i + 1) * (size.height / 4)
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(i * (stepW + spacing), size.height - progressH),
                        size = Size(stepW, progressH)
                    )
                }
            }

            Text(
                text = "5G",
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                fontFamily = FontFamily.SansSerif
            )

            // Dynamic iOS battery block representation
            Box(
                modifier = Modifier
                    .width(19.dp)
                    .height(9.5.dp)
                    .border(0.7.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
                    .padding(0.8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.9f)
                        .clip(RoundedCornerShape(0.8.dp))
                        .background(Color(0xFF10B981))
                )
            }
        }
    }
}

@Composable
fun BoxScope.DynamicIslandOverlay(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    isPlaying: Boolean,
    isAiLoading: Boolean,
    isTraining: Boolean,
    dragForce: Float,
    liftForce: Float,
    inletVel: Float,
    onTogglePlay: () -> Unit
) {
    // Dynamic island size updates smoothly inside physics solver loops
    val islandW by animateDpAsState(
        targetValue = when {
            isExpanded -> 280.dp
            isAiLoading -> 200.dp
            isTraining -> 190.dp
            isPlaying -> 120.dp
            else -> 92.dp
        },
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 160f)
    )

    val islandH by animateDpAsState(
        targetValue = when {
            isExpanded -> 92.dp
            isAiLoading -> 28.dp
            else -> 24.dp
        },
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 160f)
    )

    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 9.dp)
            .size(islandW, islandH)
            .clip(RoundedCornerShape(13.dp))
            .background(Color.Black)
            .clickable { onToggleExpand() }
            .padding(horizontal = 10.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isExpanded) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CFD FLIGHT TELEMETRY STATUS",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF38BDF8),
                        fontFamily = FontFamily.Monospace
                    )
                    IconButton(
                        onClick = { onTogglePlay() },
                        modifier = Modifier.size(16.dp)
                    ) {
                        val playIcon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow
                        val tintColor = if (isPlaying) Color(0xFFEF4444) else Color(0xFF10B981)
                        Icon(
                            imageVector = playIcon,
                            contentDescription = "Stop",
                            tint = tintColor,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "LIFT FORCE: ${String.format("%.3f", liftForce)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981),
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "DRAG FORCE: ${String.format("%.3f", dragForce)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEF4444),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "WIND: ${String.format("%.1f", inletVel)} m/s",
                            fontSize = 9.5.sp,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "[TAP TO SHRINK]",
                            fontSize = 6.5.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left interactive status indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isAiLoading) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.Cyan)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "GEMINI ANALYZING...",
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Cyan,
                            fontFamily = FontFamily.Monospace
                        )
                    } else if (isTraining) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFFFF9F1C))
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "TRAINING NETWORK",
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFFF9F1C),
                            fontFamily = FontFamily.Monospace
                        )
                    } else if (isPlaying) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFF10B981))
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "CFD ACTIVE",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981),
                            fontFamily = FontFamily.Monospace
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.DarkGray)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "CFD STANDBY",
                            fontSize = 8.sp,
                            color = Color.Gray,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Camera mirror lens shine indicator
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF111116))
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(1.dp)
                            .size(2.dp)
                            .background(Color(0xFF3B82F6))
                    )
                }
            }
        }
    }
}

@Composable
fun BoxScope.ClassicNotchOverlay(isWide: Boolean) {
    val notchW = if (isWide) 160.dp else 120.dp
    val notchH = if (isWide) 28.dp else 22.dp

    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .size(notchW, notchH)
            .clip(
                RoundedCornerShape(
                    bottomStart = 12.dp,
                    bottomEnd = 12.dp,
                    topStart = 0.dp,
                    topEnd = 0.dp
                )
            )
            .background(Color.Black)
    ) {
        // Speaker horizontal ear grill
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 4.dp)
                .size(width = 32.dp, height = 2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(Color(0xFF262629))
        )
        // Matte lens circle
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = if (isWide) 24.dp else 16.dp)
                .size(7.5.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF0F0F12))
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(2.dp)
                    .background(Color(0xFF1E40AF))
            )
        }
    }
}

@Composable
fun BoxScope.RetroBezelOverlay(onHomeClick: () -> Unit) {
    // Upper horizontal panel containing speaker & front glass dots
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(58.dp)
            .background(Color(0xFF0D1117))
    ) {
        // Recessed metal speaker grill
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(width = 44.dp, height = 3.5.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF2D3748))
        )
        // Camera lens dot
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 100.dp)
                .size(6.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF1A202C))
        )
    }

    // Classic Lower horizontal bezel with a fully interactable Touch ID circle key
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(68.dp)
            .background(Color(0xFF0D1117)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1E293B),
                            Color(0xFF0F172A)
                        )
                    )
                )
                .border(2.dp, Color(0xFF4A5568), RoundedCornerShape(50))
                .clickable { onHomeClick() }
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            // White iOS Home rounded square icon
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .border(1.5.dp, Color(0xFF718096), RoundedCornerShape(3.dp))
            )
        }
    }
}
