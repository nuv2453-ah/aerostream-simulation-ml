package com.example.viewmodel

import android.app.Application
import android.util.Log
import kotlin.math.max
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.RetrofitClient
import com.example.data.local.AeroDatabase
import com.example.data.local.SimulationRecord
import com.example.data.local.SimulationRepository
import com.example.simulation.FluidSolver
import com.example.simulation.GeneticOptimizer
import com.example.simulation.MLSurrogate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FluidViewModel(application: Application) : AndroidViewModel(application) {

    // Room persistence Repository
    private val repository: SimulationRepository
    val simulationHistory: StateFlow<List<SimulationRecord>>

    init {
        val database = AeroDatabase.getDatabase(application)
        repository = SimulationRepository(database.simulationDao())
        simulationHistory = repository.allSimulations.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    // CFD Numeric Solver
    val solver = FluidSolver(64, 32)

    // ML Surrogate Network
    val surrogate = MLSurrogate(24, 12)

    // Genetic Shape Optimizer
    val optimizer = GeneticOptimizer()

    // -------------------------------------------------------------
    // UI Reactive State Flows
    // -------------------------------------------------------------
    private val _obstacleType = MutableStateFlow("Airfoil")
    val obstacleType: StateFlow<String> = _obstacleType.asStateFlow()

    private val _inletVelocity = MutableStateFlow(12.0f)
    val inletVelocity: StateFlow<Float> = _inletVelocity.asStateFlow()

    private val _viscosity = MutableStateFlow(0.005f)
    val viscosity: StateFlow<Float> = _viscosity.asStateFlow()

    private val _angleOfAttack = MutableStateFlow(4.0f)
    val angleOfAttack: StateFlow<Float> = _angleOfAttack.asStateFlow()

    private val _heatmapMode = MutableStateFlow("Velocity") // "Velocity", "Pressure", "Vorticity"
    val heatmapMode: StateFlow<String> = _heatmapMode.asStateFlow()

    private val _showParticles = MutableStateFlow(true)
    val showParticles: StateFlow<Boolean> = _showParticles.asStateFlow()

    private val _isPlaying = MutableStateFlow(true)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isSurrogateMode = MutableStateFlow(false)
    val isSurrogateMode: StateFlow<Boolean> = _isSurrogateMode.asStateFlow()

    // CFD Aerodynamic Output Metrics
    private val _liftForce = MutableStateFlow(0.0f)
    val liftForce: StateFlow<Float> = _liftForce.asStateFlow()

    private val _dragForce = MutableStateFlow(0.0f)
    val dragForce: StateFlow<Float> = _dragForce.asStateFlow()

    // Gemini AI STEM integration
    private val _aiResponse = MutableStateFlow<String>("")
    val aiResponse: StateFlow<String> = _aiResponse.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    // On-device ML dataset capture status
    private val _isDatasetGenerating = MutableStateFlow(false)
    val isDatasetGenerating: StateFlow<Boolean> = _isDatasetGenerating.asStateFlow()

    private val _datasetProgress = MutableStateFlow(0.0f)
    val datasetProgress: StateFlow<Float> = _datasetProgress.asStateFlow()

    private val _isTraining = MutableStateFlow(false)
    val isTraining: StateFlow<Boolean> = _isTraining.asStateFlow()

    // Active visual rendering state tags (synchronized copy for UI refresh rate)
    val renderTrigger = MutableStateFlow(0L)

    // Simulation updater Job
    private var simulationJob: Job? = null

    init {
        applyObstaclePreset()
        startSimulationLoop()
    }

    private fun startSimulationLoop() {
        simulationJob?.cancel()
        simulationJob = viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                if (_isPlaying.value) {
                    if (!_isSurrogateMode.value) {
                        // 1. Numerical Solver Step
                        solver.step(
                            dt = 0.08f,
                            viscosity = _viscosity.value,
                            inletVelocity = _inletVelocity.value,
                            iterations = 10
                        )

                        // Compute active physical metrics
                        val (l, d) = solver.computeAerodynamicForces()
                        _liftForce.value = l
                        _dragForce.value = d
                    } else {
                        // 2. ML Neural Surrogate Inference Step
                        val params = floatArrayOf(
                            _inletVelocity.value / 20.0f,
                            _viscosity.value / 0.1f,
                            getObstacleTypeIndex().toFloat(),
                            _angleOfAttack.value / 45.0f
                        )
                        val predictionVec = surrogate.forward(params)

                        // Unpack predicted spatial pressure field (compressed outNx x outNy)
                        // Bilinearly upscale and overwrite solver pressure cache for instant smooth visual heatmap rendering
                        unpackPredictedField(predictionVec)

                        // Extract forces appended at final indices
                        _liftForce.value = predictionVec[surrogate.outputSize - 2]
                        _dragForce.value = predictionVec[surrogate.outputSize - 1]

                        // Trick dynamic visual flow particles using approximated velocities
                        // Solves velocity field roughly proportional to inlet velocity running around solid shape
                        solver.step(0.08f, _viscosity.value, _inletVelocity.value, 1) // 1 quick step keeps tracing active
                    }

                    // Flush synchronization tag to Compose UI
                    renderTrigger.value = System.nanoTime()
                }
                delay(12) // Limit loop frequency to ~70 FPS to prevent core hogging
            }
        }
    }

    // Interpolates upsampled surrogate predictions back to fluid solver pressure field grid
    private fun unpackPredictedField(predVec: FloatArray) {
        val outNx = surrogate.outNx
        val outNy = surrogate.outNy

        for (y in 0 until solver.Ny) {
            val normY = y.toFloat() / (solver.Ny - 1)
            val pyIdxFloat = normY * (outNy - 1)
            val py0 = pyIdxFloat.toInt()
            val py1 = (py0 + 1).coerceAtMost(outNy - 1)
            val dy = pyIdxFloat - py0

            for (x in 0 until solver.Nx) {
                val normX = x.toFloat() / (solver.Nx - 1)
                val pxIdxFloat = normX * (outNx - 1)
                val px0 = pxIdxFloat.toInt()
                val px1 = (px0 + 1).coerceAtMost(outNx - 1)
                val dx = pxIdxFloat - px0

                val p00 = predVec[py0 * outNx + px0]
                val p10 = predVec[py0 * outNx + px1]
                val p01 = predVec[py1 * outNx + px0]
                val p11 = predVec[py1 * outNx + px1]

                // Bilinear scaling
                val interpolP = (1.0f - dx) * ((1.0f - dy) * p00 + dy * p01) + dx * ((1.0f - dy) * p10 + dy * p11)
                solver.p[solver.index(x, y)] = interpolP
            }
        }
    }

    fun setObstacleType(type: String) {
        _obstacleType.value = type
        applyObstaclePreset()
    }

    fun setInletVelocity(vel: Float) {
        _inletVelocity.value = vel.coerceIn(5.0f, 25.0f)
    }

    fun setViscosity(visc: Float) {
        _viscosity.value = visc.coerceIn(0.001f, 0.05f)
    }

    fun setAngleOfAttack(angle: Float) {
        _angleOfAttack.value = angle.coerceIn(-10.0f, 20.0f)
        applyObstaclePreset()
    }

    fun togglePlaying() {
        _isPlaying.value = !_isPlaying.value
    }

    fun toggleSurrogateMode() {
        if (!_isSurrogateMode.value && !surrogate.isTrained.value) {
            // Force block if not trained, helping guide the user setup process
            Log.w("FluidViewModel", "Cannot toggle Surrogate mode: MLP surrogate network not trained yet.")
            return
        }
        _isSurrogateMode.value = !_isSurrogateMode.value
    }

    fun setHeatmapMode(mode: String) {
        _heatmapMode.value = mode
    }

    fun toggleParticles() {
        _showParticles.value = !_showParticles.value
    }

    fun applyObstaclePreset() {
        viewModelScope.launch {
            if (!_isSurrogateMode.value) {
                solver.setObstacle(_obstacleType.value, _angleOfAttack.value)
            }
        }
    }

    fun handleCustomSketch(gridX: Int, gridY: Int) {
        viewModelScope.launch {
            if (!_isSurrogateMode.value) {
                _obstacleType.value = "Custom Sketch"
                solver.addBrushSolid(gridX, gridY, radius = 2)
            }
        }
    }

    fun clearCustomSketch() {
        viewModelScope.launch {
            solver.resetSolver()
            setObstacleType("Cylinder")
        }
    }

    private fun getObstacleTypeIndex(): Int {
        return when (_obstacleType.value) {
            "Cylinder" -> 0
            "Airfoil" -> 1
            "Flat Plate" -> 2
            "Venturi Nozzle" -> 3
            else -> 4
        }
    }

    // -------------------------------------------------------------
    // Automated ML Dataset Gathering
    // -------------------------------------------------------------
    fun generateMLDataset() {
        viewModelScope.launch(Dispatchers.Default) {
            _isDatasetGenerating.value = true
            _datasetProgress.value = 0.0f
            _isPlaying.value = false

            surrogate.trainingSet.clear()

            // Define parameter distribution grids (combination sweeps) over velocities, geometries
            val sweepVelocities = floatArrayOf(8.0f, 12.0f, 15.0f, 18.0f)
            val sweepViscosities = floatArrayOf(0.003f, 0.012f, 0.03f)
            val sweepObstacles = intArrayOf(0, 1, 2) // Cylinder, Airfoil, Flat Plate
            val sweepAoAs = floatArrayOf(0.0f, 6.0f, 12.0f)

            val totalSteps = sweepVelocities.size * sweepViscosities.size * sweepObstacles.size * sweepAoAs.size
            var currentStep = 0

            for (vel in sweepVelocities) {
                for (visc in sweepViscosities) {
                    for (obsIdx in sweepObstacles) {
                        for (aoa in sweepAoAs) {
                            val obsTypeStr = when (obsIdx) {
                                0 -> "Cylinder"
                                1 -> "Airfoil"
                                2 -> "Flat Plate"
                                else -> "Cylinder"
                            }

                            // 1. Target CFD settings
                            solver.setObstacle(obsTypeStr, aoa)

                            // 2. Perform steady state settle iterations
                            for (frame in 0 until 35) {
                                solver.step(0.08f, visc, vel, 8)
                            }

                            // 3. Integrate forces
                            val (lift, drag) = solver.computeAerodynamicForces()

                            // 4. Capture downsampled spatial matrices
                            val spatialTarget = surrogate.createTargetVector(solver, lift, drag)

                            // 5. Package and add to model dataset
                            surrogate.addSample(vel, visc, obsIdx, aoa, spatialTarget)

                            currentStep++
                            _datasetProgress.value = currentStep.toFloat() / totalSteps
                        }
                    }
                }
            }

            _isDatasetGenerating.value = false
            _isPlaying.value = true
            applyObstaclePreset()
        }
    }

    fun trainMLSurrogate() {
        viewModelScope.launch {
            _isTraining.value = true
            // Run backprop in neural thread
            surrogate.trainOnDevice(epochs = 160, lr = 0.03f)
            _isTraining.value = false
        }
    }

    // -------------------------------------------------------------
    // Shape Genetic Optimization Loop
    // -------------------------------------------------------------
    fun runShapeOptimization(objective: GeneticOptimizer.Objective) {
        viewModelScope.launch {
            _isPlaying.value = false // Freeze solver display loop

            optimizer.optimize(
                objective = objective,
                inletVel = _inletVelocity.value,
                visc = _viscosity.value,
                onEvaluationStep = { genome ->
                    // Set parameter values based on genetic airfoil chromosome input
                    // Genome: [thickness, camber, position, aoa]
                    _angleOfAttack.value = genome[3]

                    withContext(Dispatchers.Default) {
                        // Rebuild aerodynamic obstacle shape
                        solver.setObstacle("Airfoil", angleOfAttackDeg = genome[3])

                        // Apply custom camber modifications onto standard airfoil analytically inside solver
                        applyCustomAirfoilCamber(genome[0], genome[1], genome[2], genome[3])

                        // Settle simulation frames rapidly
                        for (i in 0 until 20) {
                            solver.step(0.08f, _viscosity.value, _inletVelocity.value, 6)
                        }

                        // Collect settled coefficients
                        val (l, d) = solver.computeAerodynamicForces()
                        Pair(l, d)
                    }
                }
            )

            // Auto load converged optimal airfoil configurations
            _obstacleType.value = "Airfoil"
            val bestGenome = optimizer.eliteCandidate.value?.genome
            if (bestGenome != null) {
                _angleOfAttack.value = bestGenome[3]
                applyObstaclePreset()
            }
            _isPlaying.value = true
        }
    }

    private fun applyCustomAirfoilCamber(thickness: Float, camber: Float, pos: Float, aoa: Float) {
        // Redraws the solver mask with exact custom genetic parameters
        val centerX = solver.Nx / 3
        val centerY = solver.Ny / 2
        val chord = solver.Nx / 3.0f
        val angleRad = Math.toRadians(aoa.toDouble()).toFloat()

        for (y in 1 until solver.Ny - 1) {
            for (x in 1 until solver.Nx - 1) {
                val rx = x - centerX
                val ry = y - centerY
                val rotX = rx * kotlin.math.cos(angleRad) + ry * kotlin.math.sin(angleRad)
                val rotY = -rx * kotlin.math.sin(angleRad) + ry * kotlin.math.cos(angleRad)

                val xc = rotX / chord
                if (xc in 0.0f..1.0f) {
                    val term1 = 0.2969f * kotlin.math.sqrt(xc)
                    val term2 = -0.1260f * xc
                    val term3 = -0.3516f * xc * xc
                    val term4 = 0.2843f * xc * xc * xc
                    val term5 = -0.1030f * xc * xc * xc * xc
                    val yt = 5.0f * thickness * (term1 + term2 + term3 + term4 + term5) * chord

                    // Custom camber positioning
                    val yc = if (xc < pos) {
                        (camber / (pos * pos)) * (2.0f * pos * xc - xc * xc)
                    } else {
                        (camber / ((1.0f - pos) * (1.0f - pos))) * ((1.0f - 2.0f * pos) + 2.0f * pos * xc - xc * xc)
                    } * chord

                    val upperLimit = yc + yt
                    val lowerLimit = yc - yt

                    if (rotY in lowerLimit..upperLimit) {
                        solver.solid[solver.index(x, y)] = true
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------
    // Gemini STEM Explainer
    // -------------------------------------------------------------
    fun askAiAboutActiveSetup(customQuestion: String? = null) {
        viewModelScope.launch {
            _isAiLoading.value = true
            _aiResponse.value = ""

            val reynolds = (_inletVelocity.value * 12.0f) / max(0.001f, _viscosity.value)
            val stateString = if (reynolds >= 20000.0f) "Fully Turbulent Separation Vortex Shedding" else "Laminar Boundary Layer Flow"

            val prompt = if (customQuestion != null) {
                "Active Setup: Obstacle='${_obstacleType.value}', InletVel=${_inletVelocity.value} m/s, Viscosity=${_viscosity.value}, AoA=${_angleOfAttack.value} deg, ReynoldsNumber=$reynolds ($stateString). " +
                        "Current measured aerodynamic values: Lift=${_liftForce.value}, Drag=${_dragForce.value}. " +
                        "Engineer's Question: $customQuestion"
            } else {
                "Provide an professional aerospace engineering summary of this wind tunnel state. " +
                        "Setup: Obstacle Type = '${_obstacleType.value}', Inlet Speed = ${_inletVelocity.value} m/s, Viscosity = ${_viscosity.value}, Angle of Attack = ${_angleOfAttack.value} degrees. " +
                        "Derived Reynolds Number = $reynolds. Tested lift coefficient metric = ${_liftForce.value}, drag coefficient metric = ${_dragForce.value}. " +
                        "Explain the fluid flow physics happening behind the shape based on these values (like Stall, vortex shedding, pressure differential, and separation). " +
                        "Also suggest how a Physics-Informed Neural Network (PINN) or Fourier Neural Operator (FNO) could model this scenario efficiently."
            }

            val systemPrompt = "You are a professional Aerospace and CFD Simulation Engineering Consultant. " +
                    "Keep explanations clear, objective, numerically sound, and focused on fluid dynamics physics. " +
                    "Explain STEM concepts like stalling, separations, boundary layer drag, lift coefficients, Reynolds numbers and ML surrogates in an insightful, engineer-friendly manner. Limit responses to 3 paragraphs."

            val response = RetrofitClient.askGemini(prompt, systemPrompt)
            _aiResponse.value = response
            _isAiLoading.value = false
        }
    }

    // -------------------------------------------------------------
    // Room Simulation Archive Handlers
    // -------------------------------------------------------------
    fun saveActiveSimulation(name: String) {
        viewModelScope.launch {
            val record = SimulationRecord(
                name = name,
                obstacleType = _obstacleType.value,
                inletVelocity = _inletVelocity.value,
                viscosity = _viscosity.value,
                avgLift = _liftForce.value,
                avgDrag = _dragForce.value,
                notes = "Reynolds: ${(_inletVelocity.value * 12.0f) / max(0.001f, _viscosity.value)}"
            )
            repository.insert(record)
        }
    }

    fun loadSelectedSimulation(record: SimulationRecord) {
        _obstacleType.value = record.obstacleType
        _inletVelocity.value = record.inletVelocity
        _viscosity.value = record.viscosity
        applyObstaclePreset()
    }

    fun deleteHistoryRecord(id: Int) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }
}
