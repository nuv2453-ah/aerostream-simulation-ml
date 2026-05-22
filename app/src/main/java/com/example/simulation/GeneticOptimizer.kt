package com.example.simulation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.random.Random

class GeneticOptimizer {
    // Fitness objectives
    enum class Objective {
        MAX_LIFT_TO_DRAG,
        MIN_DRAG,
        MAX_LIFT
    }

    // A candidate design chromosome
    // Genome: [Thickness (0.06 to 0.22), MaxCamber (0.0 to 0.12), CamberPosition (0.15 to 0.75), AngleOfAttack (-5.0 to 18.0)]
    class Candidate(val genome: FloatArray) {
        var fitness = -9999.0f
        var lift = 0.0f
        var drag = 0.0f
        var liftToDrag = 0.0f

        fun copy(): Candidate {
            return Candidate(genome.clone()).also {
                it.fitness = this.fitness
                it.lift = this.lift
                it.drag = this.drag
                it.liftToDrag = this.liftToDrag
            }
        }
    }

    // Parameters
    val populationSize = 6
    val maxGenerations = 10
    val mutationRate = 0.25f

    // Reactive states
    val isRunning = MutableStateFlow(false)
    val currentGeneration = MutableStateFlow(0)
    val bestFitnessHistory = MutableStateFlow<List<Float>>(emptyList())
    val eliteCandidate = MutableStateFlow<Candidate?>(null)
    val activeCandidateProfile = MutableStateFlow<FloatArray?>(null) // [thickness, camber, pos, aoa] for morphing visualization

    // Run programmatic shape optimization loop
    suspend fun optimize(
        objective: Objective,
        inletVel: Float,
        visc: Float,
        onEvaluationStep: suspend (FloatArray) -> Pair<Float, Float> // returns (Lift, Drag) dynamically from CFD simulation
    ) = withContext(Dispatchers.Default) {
        isRunning.value = true
        currentGeneration.value = 0
        bestFitnessHistory.value = emptyList()
        eliteCandidate.value = null

        val rand = Random(101)

        // 1. Initialize random population
        var population = ArrayList<Candidate>()
        for (i in 0 until populationSize) {
            val thickness = 0.06f + rand.nextFloat() * 0.14f
            val camber = 0.0f + rand.nextFloat() * 0.10f
            val position = 0.15f + rand.nextFloat() * 0.55f
            val aoa = -5.0f + rand.nextFloat() * 20.0f
            population.add(Candidate(floatArrayOf(thickness, camber, position, aoa)))
        }

        val fitnessHistory = ArrayList<Float>()

        // 2. Generation loop
        for (gen in 1..maxGenerations) {
            currentGeneration.value = gen

            // Evaluate fitness of all candidates
            for (candidate in population) {
                // Update morphing profile visualization
                activeCandidateProfile.value = candidate.genome

                // Run fast numerical CFD simulation step
                val (lift, drag) = onEvaluationStep(candidate.genome)

                candidate.lift = lift
                candidate.drag = drag
                candidate.liftToDrag = if (drag != 0.0f) lift / max(0.005f, drag) else lift

                // Compute fitness based on chosen objective
                candidate.fitness = when (objective) {
                    Objective.MAX_LIFT_TO_DRAG -> candidate.liftToDrag
                    Objective.MIN_DRAG -> -drag // higher fitness means smaller drag
                    Objective.MAX_LIFT -> lift
                }
            }

            // Rank population
            population.sortByDescending { it.fitness }

            // Track elite candidate
            val currentBest = population.first()
            val savedElite = currentBest.copy()
            eliteCandidate.value = savedElite
            fitnessHistory.add(savedElite.fitness)
            bestFitnessHistory.value = fitnessHistory.toList()

            if (gen == maxGenerations) break

            // 3. Selection & Crossover (Evolve next gen)
            val nextGeneration = ArrayList<Candidate>()
            // Elitism: carry over top 2 directly
            nextGeneration.add(population[0].copy())
            nextGeneration.add(population[1].copy())

            // Spawn the remaining candidates
            while (nextGeneration.size < populationSize) {
                // Tournament selection
                val parent1 = tournamentSelect(population, rand)
                val parent2 = tournamentSelect(population, rand)

                // Crossover
                val childGenome = FloatArray(4)
                val crossoverSplit = rand.nextInt(3) + 1 // split index (1, 2, or 3)
                for (j in 0 until 4) {
                    childGenome[j] = if (j < crossoverSplit) parent1.genome[j] else parent2.genome[j]
                }

                // Mutation
                if (rand.nextFloat() < mutationRate) {
                    val geneIndex = rand.nextInt(4)
                    val offset = (rand.nextFloat() * 2.0f - 1.0f) * 0.04f
                    childGenome[geneIndex] += offset

                    // Clamping
                    when (geneIndex) {
                        0 -> childGenome[0] = childGenome[0].coerceIn(0.05f, 0.22f) // thickness
                        1 -> childGenome[1] = childGenome[1].coerceIn(0.0f, 0.12f)  // camber
                        2 -> childGenome[2] = childGenome[2].coerceIn(0.15f, 0.75f) // position
                        3 -> childGenome[3] = childGenome[3].coerceIn(-5.0f, 18.0f) // angle of attack
                    }
                }
                nextGeneration.add(Candidate(childGenome))
            }
            population = nextGeneration
        }

        isRunning.value = false
        activeCandidateProfile.value = eliteCandidate.value?.genome // return to best
    }

    private fun tournamentSelect(pop: List<Candidate>, rand: Random): Candidate {
        val i1 = rand.nextInt(pop.size)
        val i2 = rand.nextInt(pop.size)
        return if (pop[i1].fitness > pop[i2].fitness) pop[i1] else pop[i2]
    }
}
