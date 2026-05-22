package com.example.simulation

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.random.Random

class MLSurrogate(val outNx: Int = 24, val outNy: Int = 12) {
    val inputSize = 4 // (inletVel, viscosity, obstacleTypeIndex, angleOfAttack)
    val hiddenSize1 = 24
    val hiddenSize2 = 12
    val outputSize = outNx * outNy + 2 // Spatial pressure grid + Lift + Drag

    // Weights and Biases
    var w1 = Array(hiddenSize1) { FloatArray(inputSize) }
    var b1 = FloatArray(hiddenSize1)
    var w2 = Array(hiddenSize2) { FloatArray(hiddenSize1) }
    var b2 = FloatArray(hiddenSize2)
    var w3 = Array(outputSize) { FloatArray(hiddenSize2) }
    var b3 = FloatArray(outputSize)

    // Dataset store
    class TrainingSample(val inputs: FloatArray, val targets: FloatArray)
    val trainingSet = ArrayList<TrainingSample>()

    // Visual indicators
    val isTrained = MutableStateFlow(false)
    val trainingLossHistory = MutableStateFlow<List<Float>>(emptyList())
    val trainingProgress = MutableStateFlow(0.0f) // 0 to 1

    init {
        initializeWeights()
    }

    fun initializeWeights() {
        val rand = Random(42) // Seeded for reproducibility
        // Xavier-like Glorot initialization
        for (i in 0 until hiddenSize1) {
            b1[i] = 0.0f
            for (j in 0 until inputSize) {
                w1[i][j] = (rand.nextFloat() * 2.0f - 1.0f) * (2.0f / inputSize).toFloat().let { kotlin.math.sqrt(it) }
            }
        }
        for (i in 0 until hiddenSize2) {
            b2[i] = 0.0f
            for (j in 0 until hiddenSize1) {
                w2[i][j] = (rand.nextFloat() * 2.0f - 1.0f) * (2.0f / hiddenSize1).toFloat().let { kotlin.math.sqrt(it) }
            }
        }
        for (i in 0 until outputSize) {
            b3[i] = 0.0f
            for (j in 0 until hiddenSize2) {
                w3[i][j] = (rand.nextFloat() * 2.0f - 1.0f) * (2.0f / hiddenSize2).toFloat().let { kotlin.math.sqrt(it) }
            }
        }
        isTrained.value = false
        trainingLossHistory.value = emptyList()
        trainingProgress.value = 0.0f
    }

    // Forward pass
    fun forward(input: FloatArray): FloatArray {
        // Layer 1
        val h1 = FloatArray(hiddenSize1)
        for (i in 0 until hiddenSize1) {
            var sum = b1[i]
            for (j in 0 until inputSize) {
                sum += w1[i][j] * input[j]
            }
            h1[i] = max(0.0f, sum) // ReLU Act
        }

        // Layer 2
        val h2 = FloatArray(hiddenSize2)
        for (i in 0 until hiddenSize2) {
            var sum = b2[i]
            for (j in 0 until hiddenSize1) {
                sum += w2[i][j] * h1[i]
            }
            h2[i] = max(0.0f, sum) // ReLU Act
        }

        // Layer 3 (Output Layer)
        val out = FloatArray(outputSize)
        for (i in 0 until outputSize) {
            var sum = b3[i]
            for (j in 0 until hiddenSize2) {
                sum += w3[i][j] * h2[j]
            }
            out[i] = sum // Linear output
        }
        return out
    }

    // Subsample pressure field to targets
    fun createTargetVector(solver: FluidSolver, lift: Float, drag: Float): FloatArray {
        val target = FloatArray(outputSize)
        // Subsample solver pressure field (Nx * Ny) down to (outNx * outNy)
        val xStep = solver.Nx / outNx
        val yStep = solver.Ny / outNy

        var count = 0
        for (y in 0 until outNy) {
            for (x in 0 until outNx) {
                val origX = (x * xStep).coerceIn(0, solver.Nx - 1)
                val origY = (y * yStep).coerceIn(0, solver.Ny - 1)
                target[count++] = solver.p[solver.index(origX, origY)]
            }
        }

        // Append Lift & Drag to end of vector
        target[outputSize - 2] = lift
        target[outputSize - 1] = drag

        return target
    }

    // Capture training data pair
    fun addSample(vel: Float, visc: Float, obsTypeIndex: Int, aoa: Float, targetVec: FloatArray) {
        val inpVec = floatArrayOf(
            vel / 20.0f,            // Normalize velocity X
            visc / 0.1f,            // Normalize viscosity
            obsTypeIndex.toFloat(),  // Encoded Obstacle
            aoa / 45.0f             // Normalize AoA
        )
        trainingSet.add(TrainingSample(inpVec, targetVec))
    }

    // Backprop Trainer
    suspend fun trainOnDevice(epochs: Int = 150, lr: Float = 0.02f) = withContext(Dispatchers.Default) {
        if (trainingSet.isEmpty()) return@withContext

        val lossHistory = ArrayList<Float>()
        val batchSize = trainingSet.size

        for (epoch in 1..epochs) {
            var totalLoss = 0.0f

            // Iterate over all collected configurations
            for (sample in trainingSet) {
                val input = sample.inputs
                val target = sample.targets

                // 1. Forward Pass with saved activations
                val h1 = FloatArray(hiddenSize1)
                val h1Raw = FloatArray(hiddenSize1)
                for (i in 0 until hiddenSize1) {
                    var sum = b1[i]
                    for (j in 0 until inputSize) {
                        sum += w1[i][j] * input[j]
                    }
                    h1Raw[i] = sum
                    h1[i] = max(0.0f, sum)
                }

                val h2 = FloatArray(hiddenSize2)
                val h2Raw = FloatArray(hiddenSize2)
                for (i in 0 until hiddenSize2) {
                    var sum = b2[i]
                    for (j in 0 until hiddenSize1) {
                        sum += w2[i][j] * h1[j]
                    }
                    h2Raw[i] = sum
                    h2[i] = max(0.0f, sum)
                }

                val out = FloatArray(outputSize)
                for (i in 0 until outputSize) {
                    var sum = b3[i]
                    for (j in 0 until hiddenSize2) {
                        sum += w3[i][j] * h2[j]
                    }
                    out[i] = sum
                }

                // Compute loss and error gradient at output
                val dOut = FloatArray(outputSize)
                for (i in 0 until outputSize) {
                    val diff = out[i] - target[i]
                    totalLoss += diff * diff
                    dOut[i] = diff // Mean squared error gradient derivative: dL/dOut
                }

                // 2. Backward Pass (Gradients calculation)
                // Gradients w3, b3 (Output Layer)
                val dw3 = Array(outputSize) { FloatArray(hiddenSize2) }
                val db3 = FloatArray(outputSize)
                val dh2 = FloatArray(hiddenSize2)

                for (i in 0 until outputSize) {
                    db3[i] = dOut[i]
                    for (j in 0 until hiddenSize2) {
                        dw3[i][j] = dOut[i] * h2[j]
                        dh2[j] += dOut[i] * w3[i][j] // backprop to hidden Layer 2
                    }
                }

                // Gradients w2, b2 (Hidden Layer 2)
                val dw2 = Array(hiddenSize2) { FloatArray(hiddenSize1) }
                val db2 = FloatArray(hiddenSize2)
                val dh1 = FloatArray(hiddenSize1)

                for (i in 0 until hiddenSize2) {
                    val actSlope = if (h2Raw[i] > 0.0f) 1.0f else 0.0f
                    val layerGrad = dh2[i] * actSlope
                    db2[i] = layerGrad
                    for (j in 0 until hiddenSize1) {
                        dw2[i][j] = layerGrad * h1[j]
                        dh1[j] += layerGrad * w2[i][j] // backprop to hidden Layer 1
                    }
                }

                // Gradients w1, b1 (Hidden Layer 1)
                val dw1 = Array(hiddenSize1) { FloatArray(inputSize) }
                val db1 = FloatArray(hiddenSize1)

                for (i in 0 until hiddenSize1) {
                    val actSlope = if (h1Raw[i] > 0.0f) 1.0f else 0.0f
                    val layerGrad = dh1[i] * actSlope
                    db1[i] = layerGrad
                    for (j in 0 until inputSize) {
                        dw1[i][j] = layerGrad * input[j]
                    }
                }

                // 3. Update Weights and Biases (gradient descent steps)
                for (i in 0 until outputSize) {
                    b3[i] -= lr * db3[i]
                    for (j in 0 until hiddenSize2) {
                        w3[i][j] -= lr * dw3[i][j]
                    }
                }
                for (i in 0 until hiddenSize2) {
                    b2[i] -= lr * db2[i]
                    for (j in 0 until hiddenSize1) {
                        w2[i][j] -= lr * dw2[i][j]
                    }
                }
                for (i in 0 until hiddenSize1) {
                    b1[i] -= lr * db1[i]
                    for (j in 0 until inputSize) {
                        w1[i][j] -= lr * dw1[i][j]
                    }
                }
            }

            // Record epoch average loss
            val avgLoss = totalLoss / (batchSize * outputSize)
            lossHistory.add(avgLoss)

            if (epoch % 5 == 0 || epoch == epochs) {
                trainingLossHistory.value = lossHistory.toList()
                trainingProgress.value = epoch.toFloat() / epochs
            }
        }
        isTrained.value = true
        Log.d("MLSurrogate", "ML Surrogate Training Complate! Final Loss: ${lossHistory.lastOrNull()}")
    }
}
