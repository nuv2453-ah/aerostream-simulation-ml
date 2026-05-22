package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.simulation.GeneticOptimizer
import com.example.viewmodel.FluidViewModel
import kotlin.math.max
import com.example.ui.screens.ApplePhoneModel
import com.example.ui.screens.AppleDeviceSimulatorWrapper

@Composable
fun DashboardScreen(
    viewModel: FluidViewModel,
    modifier: Modifier = Modifier
) {
    // Collect solver/simulation states
    val obstacleType by viewModel.obstacleType.collectAsState()
    val inletVel by viewModel.inletVelocity.collectAsState()
    val viscosity by viewModel.viscosity.collectAsState()
    val angleOfAttack by viewModel.angleOfAttack.collectAsState()
    val heatmapMode by viewModel.heatmapMode.collectAsState()
    val showParticles by viewModel.showParticles.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isSurrogateMode by viewModel.isSurrogateMode.collectAsState()

    // Aerodynamic outputs
    val liftForce by viewModel.liftForce.collectAsState()
    val dragForce by viewModel.dragForce.collectAsState()

    // Trigger state changes
    val renderTrigger by viewModel.renderTrigger.collectAsState()

    // Screen tab selection (0 = Tunnel, 1 = Surrogate, 2 = Optim, 3 = Assistant, 4 = Archive)
    var selectedTab by remember { mutableIntStateOf(0) }

    // Dialog state for savers
    var showSaveDialog by remember { mutableStateOf(false) }
    var simNameInput by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    var activeAppleModel by remember { mutableStateOf(ApplePhoneModel.NONE) }

    // Base dark industrial science background brush
    val backgroundBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0F172A), // Deep Slate-900
                Color(0xFF020617)  // Pitch Black
            )
        )
    }

    AppleDeviceSimulatorWrapper(
        selectedModel = activeAppleModel,
        viewModel = viewModel,
        onModelChange = { activeAppleModel = it },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(top = 4.dp)
        ) {
        // App Header Status Control Room
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "AEROSTREAM CFD",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (isPlaying) Color(0xFF10B981) else Color(0xFFEF4444))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isSurrogateMode) "NEURAL SURROGATE INFERENCE" else "REALTIME NAVIER-STOKES solver",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSurrogateMode) Color(0xFF0EA5E9) else Color(0xFFA1A1AA),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Quick Play/Pause and Save buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.togglePlaying() }) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Ctrl solver loop",
                        tint = if (isPlaying) Color(0xFFEF4444) else Color(0xFF10B981),
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(onClick = { showSaveDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Save active scenario",
                        tint = Color(0xFF09F3C1),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Integrated Wind Tunnel CFD viewport Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF090D16))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                FluidCanvas(
                    solver = viewModel.solver,
                    heatmapMode = heatmapMode,
                    showParticles = showParticles,
                    renderTrigger = renderTrigger,
                    isDrawingMode = (obstacleType == "Custom Sketch"),
                    onSketchGrid = { gx, gy -> viewModel.handleCustomSketch(gx, gy) }
                )

                // Inline quick stats labels
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .background(Color(0xE60F172A), RoundedCornerShape(4.dp))
                        .padding(6.dp)
                ) {
                    val reynolds = (inletVel * 12.0f) / max(0.001f, viscosity)
                    Text(
                        text = "REYNOLDS: ${reynolds.toInt()} (${if (reynolds >= 20000) "Turbulent" else "Laminar"})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF9F1C),
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "VISCOUS DRAG: ${String.format("%.3f", dragForce)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF4444),
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "PRESSURE LIFT: ${String.format("%.3f", liftForce)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981),
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Interactive Brush Help tag inside Custom Sketch
                if (obstacleType == "Custom Sketch" && isPlaying) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                            .background(Color(0xCC0369A1), RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "FINGER PAINT BLOCKS ON CANVAS",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Dynamic metrics panel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Lift-to-Drag Ratio Badge
            val lod = if (dragForce != 0.0f) liftForce / max(0.005f, dragForce) else liftForce
            val lodColor = if (lod >= 1.5f) Color(0xFF10B981) else if (lod < 0.0f) Color(0xFFEF4444) else Color(0xFFFF9F1C)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFF131A2A), RoundedCornerShape(8.dp))
                    .border(0.5.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("LIFT-TO-DRAG (L/D)", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(String.format("%.3f", lod), fontSize = 16.sp, color = lodColor, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFF131A2A), RoundedCornerShape(8.dp))
                    .border(0.5.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("HEATMAP SCANNER", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.Center) {
                        listOf("Velocity", "Pressure", "Vorticity").forEach { mode ->
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 2.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (heatmapMode == mode) Color(0xFF1E3A8A) else Color.Transparent)
                                    .clickable { viewModel.setHeatmapMode(mode) }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(mode.uppercase().take(4), fontSize = 9.sp, color = if (heatmapMode == mode) Color.Cyan else Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Navigation Tabs Deck
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .background(Color(0xFF090D16), RoundedCornerShape(10.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val tabs = listOf("TUNNEL", "ML SURR", "OPTIM", "CONSULT", "DATA")
            tabs.forEachIndexed { idx, title ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selectedTab == idx) Color(0xFF1E293B) else Color.Transparent)
                        .clickable { selectedTab = idx }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedTab == idx) Color.White else Color.Gray,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Sub Screen Active Section Viewport
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            when (selectedTab) {
                0 -> TunnelWorkspace(viewModel, obstacleType, inletVel, viscosity, angleOfAttack, showParticles)
                1 -> MLSurrogateWorkspace(viewModel)
                2 -> ShapeOptimizerWorkspace(viewModel)
                3 -> AIConsultantWorkspace(viewModel)
                4 -> SimulationsDatabaseWorkspace(viewModel) { selectedTab = 0 }
            }
        }
    }
}

    // Modal Saving Dialog
    if (showSaveDialog) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f))
                .clickable { /* Block bottom clicks */ }
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "SAVE SIMULATION SCENARIO",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = simNameInput,
                        onValueChange = { simNameInput = it },
                        label = { Text("Simulation Archive Title", color = Color.Gray, fontSize = 12.sp, fontFamily = FontFamily.Monospace) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF09F3C1),
                            unfocusedBorderColor = Color(0xFF475569),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = {
                                showSaveDialog = false
                                simNameInput = ""
                                focusManager.clearFocus()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                        ) {
                            Text("Cancel", color = Color.Gray, fontFamily = FontFamily.Monospace)
                        }
                        Button(
                            onClick = {
                                if (simNameInput.isNotBlank()) {
                                    viewModel.saveActiveSimulation(simNameInput)
                                }
                                showSaveDialog = false
                                simNameInput = ""
                                focusManager.clearFocus()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF09F3C1))
                        ) {
                            Text("Save Archive", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}


// -------------------------------------------------------------
// Workspace Component Layouts
// -------------------------------------------------------------

@Composable
fun TunnelWorkspace(
    viewModel: FluidViewModel,
    obstacleType: String,
    inletVel: Float,
    viscosity: Float,
    angleOfAttack: Float,
    showParticles: Boolean
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Shapes Selector presets bar
        item {
            Text(
                "AERODYNAMIC OBSTACLE TYPE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF090D16), RoundedCornerShape(8.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("Cylinder", "Airfoil", "Flat Plate", "Venturi Nozzle").forEach { type ->
                    val active = (obstacleType == type)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (active) Color(0xFF1E293B) else Color.Transparent)
                            .clickable { viewModel.setObstacleType(type) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = type.split(" ").first(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (active) Color.Cyan else Color.White,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Boundary parameters Sliders deck
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1220)),
                modifier = Modifier.border(0.5.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Sliders Title
                    Text(
                        "BOUNDARY VELOCITY & REYNOLDS TUNNEL SETTINGS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Cyan,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Inlet velocity
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Inlet Wind Velocity", fontSize = 12.sp, color = Color.White)
                        Text("${String.format("%.1f", inletVel)} m/s", fontSize = 12.sp, color = Color.Cyan, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = inletVel,
                        onValueChange = { viewModel.setInletVelocity(it) },
                        valueRange = 5.0f..25.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF0EA5E9),
                            activeTrackColor = Color(0xFF0EA5E9),
                            inactiveTrackColor = Color(0xFF334155)
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Fluid kinematic viscosity
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Fluid Viscosity (v)", fontSize = 12.sp, color = Color.White)
                        Text(String.format("%.4f", viscosity), fontSize = 12.sp, color = Color.Cyan, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = viscosity,
                        onValueChange = { viewModel.setViscosity(it) },
                        valueRange = 0.001f..0.05f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF10B981),
                            activeTrackColor = Color(0xFF10B981),
                            inactiveTrackColor = Color(0xFF334155)
                        )
                    )

                    // Angle of attack (render if airfoil active)
                    if (obstacleType == "Airfoil") {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Angle of Attack (Alpha)", fontSize = 12.sp, color = Color.White)
                            Text("${angleOfAttack.toInt()}°", fontSize = 12.sp, color = Color.Cyan, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = angleOfAttack,
                            onValueChange = { viewModel.setAngleOfAttack(it) },
                            valueRange = -10.0f..20.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFFF9F1C),
                                activeTrackColor = Color(0xFFFF9F1C),
                                inactiveTrackColor = Color(0xFF334155)
                            )
                        )
                    }
                }
            }
        }

        // Particle particle tracers overlay controls
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D1220), RoundedCornerShape(10.dp))
                    .border(0.5.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("ACTIVE FLOW SEED PARTICLE TRACERS", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Overlay physical stream particle smoke trails around obstacles", fontSize = 10.sp, color = Color.Gray)
                }
                Switch(
                    checked = showParticles,
                    onCheckedChange = { viewModel.toggleParticles() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Cyan,
                        checkedTrackColor = Color(0xFF1E3A8A)
                    )
                )
            }
        }

        // Custom Paint sketch reset block
        if (obstacleType == "Custom Sketch") {
            item {
                Button(
                    onClick = { viewModel.clearCustomSketch() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7F1D1D)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Clear sketch")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CLEAR HAND SKETCH DESIGN", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun MLSurrogateWorkspace(viewModel: FluidViewModel) {
    val surrogate = viewModel.surrogate
    val isTrained by surrogate.isTrained.collectAsState()
    val isSurrogateMode by viewModel.isSurrogateMode.collectAsState()
    val lossHistory by surrogate.trainingLossHistory.collectAsState()
    val trainProgress by surrogate.trainingProgress.collectAsState()

    val isGenerating by viewModel.isDatasetGenerating.collectAsState()
    val datasetsProgress by viewModel.datasetProgress.collectAsState()
    val isTrainingActive by viewModel.isTraining.collectAsState()

    val activeDatasetSize = surrogate.trainingSet.size

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                "ON-DEVICE CFD NEURAL NETWORK SURROGATE SURFACES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                fontFamily = FontFamily.Monospace
            )
        }

        // Informational panel explaining physical context
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF10162B)),
                modifier = Modifier.border(0.5.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "HOW IT WORKS",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF9F1C),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Instead of computing the partial differential Navier-Stokes equations element-by-element (~3ms solver delay), our deep surrogate neural architecture approximates pressure profiles in under 0.1ms. Watch the training loss descend on-device to lock down accuracy!",
                        fontSize = 11.sp,
                        color = Color.LightGray,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // Activator status controls
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D1220), RoundedCornerShape(10.dp))
                    .border(0.5.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("TOGGLE AI SURROGATE BOUNDARY", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        if (isTrained) "Active: Speeding up calculations by 300x!" else "Requires model neural network training first",
                        fontSize = 10.sp,
                        color = if (isTrained) Color(0xFF10B981) else Color.Gray
                    )
                }
                Switch(
                    checked = isSurrogateMode,
                    onCheckedChange = { viewModel.toggleSurrogateMode() },
                    enabled = isTrained,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF0EA5E9),
                        checkedTrackColor = Color(0xFF1E3A8A)
                    )
                )
            }
        }

        // Core Model Setup actions
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.generateMLDataset() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    enabled = !isGenerating && !isTrainingActive,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(color = Color.Cyan, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sweeping ${String.format("%.0f", datasetsProgress * 100)}%", fontSize = 11.sp, color = Color.Cyan, fontFamily = FontFamily.Monospace)
                    } else {
                        Text(if (activeDatasetSize > 0) "Regenerate Dataset" else "1. Generate Dataset", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }

                Button(
                    onClick = { viewModel.trainMLSurrogate() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
                    enabled = (activeDatasetSize > 0) && !isGenerating && !isTrainingActive,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isTrainingActive) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Epochs ${String.format("%.0f", trainProgress * 100)}%", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    } else {
                        Text("2. Train AI Surrogate", fontSize = 11.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        // Live Training Loss History Chart
        if (lossHistory.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF090D16)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "ON-DEVICE BACKPROPAGATION LOSS HISTORY",
                                fontSize = 10.sp,
                                color = Color.Cyan,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                "MSE: ${String.format("%.5f", lossHistory.last())}",
                                fontSize = 10.sp,
                                color = Color.Gray,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Custom line chart drawing
                        SimpleLineChart(
                            points = lossHistory,
                            color = Color(0xFF10B981),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun ShapeOptimizerWorkspace(viewModel: FluidViewModel) {
    val optimizer = viewModel.optimizer
    val isRunning by optimizer.isRunning.collectAsState()
    val generation by optimizer.currentGeneration.collectAsState()
    val fitnessHistory by optimizer.bestFitnessHistory.collectAsState()
    val elite by optimizer.eliteCandidate.collectAsState()
    val activeProfile by optimizer.activeCandidateProfile.collectAsState()

    var activeObjective by remember { mutableStateOf(GeneticOptimizer.Objective.MAX_LIFT_TO_DRAG) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(
                "GENETIC AERODYNAMIC PROFILE OPTIMIZER",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                fontFamily = FontFamily.Monospace
            )
        }

        // Active parameter selection Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1220)),
                modifier = Modifier.border(0.5.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "OPTIMIZATION CRITERIA (FITNESS)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Cyan,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    GeneticOptimizer.Objective.values().forEach { obj ->
                        val label = when (obj) {
                            GeneticOptimizer.Objective.MAX_LIFT_TO_DRAG -> "Maximize Lift-to-Drag Ratio (L/D)"
                            GeneticOptimizer.Objective.MIN_DRAG -> "Minimize Aerodynamic Wall Drag"
                            GeneticOptimizer.Objective.MAX_LIFT -> "Maximize Structural Flight Lift"
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!isRunning) activeObjective = obj
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = (activeObjective == obj),
                                onClick = { if (!isRunning) activeObjective = obj },
                                colors = RadioButtonDefaults.colors(selectedColor = Color.Cyan, unselectedColor = Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(label, fontSize = 12.sp, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { viewModel.runShapeOptimization(activeObjective) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9F1C)),
                        enabled = !isRunning,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isRunning) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Evolving Generation $generation/10", fontFamily = FontFamily.Monospace, color = Color.White)
                        } else {
                            Text("RUN EVOLUTION OPTIMIZATION", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        }
                    }
                }
            }
        }

        // Active testing profile indicators morphing
        if (activeProfile != null && isRunning) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF10162B)),
                    modifier = Modifier.border(0.5.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "CFD WIND TUNNEL SIMULATING SPECIMENS PROFILE",
                            fontSize = 9.sp,
                            color = Color(0xFFEF4444),
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Camber: ${String.format("%.3f", activeProfile!![1])}", fontSize = 11.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                            Text("Thickness: ${String.format("%.3f", activeProfile!![0])}", fontSize = 11.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                            Text("Pitch Angle: ${activeProfile!![3].toInt()}°", fontSize = 11.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        // Elite output analysis
        if (elite != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1E1A)),
                    modifier = Modifier.border(0.5.dp, Color(0xFF09F3C1), RoundedCornerShape(10.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "ELITE DESIGN OUTCOME (GENERATION $generation)",
                            fontSize = 10.sp,
                            color = Color(0xFF09F3C1),
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Thickness:", fontSize = 11.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                                Text(String.format("%.1f%% chord", elite!!.genome[0] * 100), fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Max Camber:", fontSize = 11.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                                Text(String.format("%.1f%% chord", elite!!.genome[1] * 100), fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Pitch Angle:", fontSize = 11.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                                Text("${elite!!.genome[3].toInt()}° incidence", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Measured Lod:", fontSize = 11.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                                Text(String.format("%.3f L/D", elite!!.liftToDrag), fontSize = 13.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }

        // Generational convergence line chart plotting
        if (fitnessHistory.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF090D16)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "GENETIC ALGORITHM OPTIMAL FITNESS LANDSCAPE CONVERGENCE",
                            fontSize = 9.sp,
                            color = Color.Cyan,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        SimpleLineChart(
                            points = fitnessHistory,
                            color = Color(0xFFFF9F1C),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun AIConsultantWorkspace(viewModel: FluidViewModel) {
    val activeAIAnswer by viewModel.aiResponse.collectAsState()
    val isAILoading by viewModel.isAiLoading.collectAsState()
    var customStemPrompt by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(
                "GEMINI ADVANCED STEM AEROSPACE CONSULTANT",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                fontFamily = FontFamily.Monospace
            )
        }

        // Floating Action prompt sender
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1220)),
                modifier = Modifier.border(0.5.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "INQUIRE AI ENGINEERING EXPERT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Cyan,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = customStemPrompt,
                        onValueChange = { customStemPrompt = it },
                        placeholder = { Text("Ask about Stall, Laminar separators, or PINNs...", color = Color.Gray, fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF09F3C1),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.askAiAboutActiveSetup()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            modifier = Modifier.weight(1.3f)
                        ) {
                            Text("Analyze State", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }

                        Button(
                            onClick = {
                                if (customStemPrompt.isNotBlank()) {
                                    focusManager.clearFocus()
                                    viewModel.askAiAboutActiveSetup(customStemPrompt)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
                            enabled = customStemPrompt.isNotBlank() && !isAILoading,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Consult", fontSize = 11.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        // Live streaming output content
        if (isAILoading || activeAIAnswer.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF020617)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (isAILoading) Color.Cyan else Color(0xFF10B981))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "GEMINI ENGINEERING FEEDBACK SUMMARY",
                                fontSize = 10.sp,
                                color = Color.Cyan,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        if (isAILoading && activeAIAnswer.isEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = Color.Cyan, modifier = Modifier.size(24.dp))
                            }
                        } else {
                            Text(
                                text = activeAIAnswer,
                                fontSize = 12.sp,
                                color = Color.LightGray,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun SimulationsDatabaseWorkspace(
    viewModel: FluidViewModel,
    onNavigateBackToTunnel: () -> Unit
) {
    val historyGroup by viewModel.simulationHistory.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "SAVED CFD-ML SPECIMENS ARCHIVES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                fontFamily = FontFamily.Monospace
            )

            if (historyGroup.isNotEmpty()) {
                IconButton(onClick = { viewModel.clearHistory() }) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Wipe entries", tint = Color.Red)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (historyGroup.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Archive Empty",
                        tint = Color.DarkGray,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "No saved simulation templates yet.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(historyGroup) { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131A2A)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name.uppercase(),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Obstacle: ${item.obstacleType}", fontSize = 10.sp, color = Color.Gray)
                                    Text("Inlet: ${item.inletVelocity} m/s", fontSize = 10.sp, color = Color.Gray)
                                    Text("Lift: ${String.format("%.2f", item.avgLift)}", fontSize = 10.sp, color = Color(0xFF10B981))
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = {
                                        viewModel.loadSelectedSimulation(item)
                                        onNavigateBackToTunnel()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A)),
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                ) {
                                    Text("Load", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                }

                                IconButton(onClick = { viewModel.deleteHistoryRecord(item.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Expunge entry",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
fun SimpleLineChart(
    points: List<Float>,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val maxVal = points.maxOrNull() ?: 1.0f
        val minVal = points.minOrNull() ?: 0.0f
        val range = maxVal - minVal
        val dy = if (range == 0.0001f) 1.0f else range

        val pointsToDraw = points.mapIndexed { index, value ->
            val px = (index.toFloat() / (points.size - 1)) * w
            val py = h - ((value - minVal) / dy) * h * 0.85f - (h * 0.05f) // Pad a little
            Offset(px, py)
        }

        // Draw dynamic grid lines
        drawLine(Color.Gray.copy(alpha = 0.15f), Offset(0f, 0f), Offset(w, 0f), strokeWidth = 1.5f)
        drawLine(Color.Gray.copy(alpha = 0.15f), Offset(0f, h / 2), Offset(w, h / 2), strokeWidth = 1.5f)
        drawLine(Color.Gray.copy(alpha = 0.15f), Offset(0f, h), Offset(w, h), strokeWidth = 1.5f)

        for (i in 0 until pointsToDraw.size - 1) {
            drawLine(
                color = color,
                start = pointsToDraw[i],
                end = pointsToDraw[i + 1],
                strokeWidth = 3.5f
            )
        }
        // Draw glow accent points on the latest output epoch
        drawCircle(
            color = color,
            radius = 5.0f,
            center = pointsToDraw.last()
        )
    }
}
