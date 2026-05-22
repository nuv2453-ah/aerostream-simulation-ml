package com.example.simulation

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

class FluidSolver(val Nx: Int = 64, val Ny: Int = 32) {
    val size = Nx * Ny

    // Physical fields
    var u = FloatArray(size)      // Velocity X
    var v = FloatArray(size)      // Velocity Y
    var uPrev = FloatArray(size)  // Temp Velocity X
    var vPrev = FloatArray(size)  // Temp Velocity Y
    var p = FloatArray(size)      // Pressure Field
    var div = FloatArray(size)    // Divergence field
    var solid = BooleanArray(size)// Solid obstacle mask
    var vort = FloatArray(size)   // Vorticity (curl of velocity)

    // Dynamic visual seed particles
    class FlowParticle(var x: Float, var y: Float)
    val particles = ArrayList<FlowParticle>()
    private val maxParticles = 300

    init {
        resetSolver()
        initializeParticles()
    }

    fun resetSolver() {
        for (i in 0 until size) {
            u[i] = 0.0f
            v[i] = 0.0f
            uPrev[i] = 0.0f
            vPrev[i] = 0.0f
            p[i] = 0.0f
            div[i] = 0.0f
            vort[i] = 0.0f
            solid[i] = false
        }
        // Force bounds boundaries as solid walls
        for (x in 0 until Nx) {
            solid[index(x, 0)] = true
            solid[index(x, Ny - 1)] = true
        }
        initializeParticles()
    }

    private fun initializeParticles() {
        particles.clear()
        for (i in 0 until maxParticles) {
            particles.add(
                FlowParticle(
                    Random.nextFloat() * (Nx - 2) + 1.0f,
                    Random.nextFloat() * (Ny - 2) + 1.0f
                )
            )
        }
    }

    fun index(x: Int, y: Int): Int {
        val cx = x.coerceIn(0, Nx - 1)
        val cy = y.coerceIn(0, Ny - 1)
        return cy * Nx + cx
    }

    // Set obstacle type
    fun setObstacle(type: String, angleOfAttackDeg: Float = 0.0f) {
        // First reset solid field but keep boundaries
        for (y in 1 until Ny - 1) {
            for (x in 1 until Nx - 1) {
                solid[index(x, y)] = false
            }
        }

        val centerX = Nx / 3
        val centerY = Ny / 2
        val angleRad = Math.toRadians(angleOfAttackDeg.toDouble()).toFloat()

        when (type) {
            "Cylinder" -> {
                val radius = Ny / 6.0f
                for (y in 1 until Ny - 1) {
                    for (x in 1 until Nx - 1) {
                        val dx = x - centerX
                        val dy = y - centerY
                        if (dx * dx + dy * dy <= radius * radius) {
                            solid[index(x, y)] = true
                        }
                    }
                }
            }
            "Airfoil" -> {
                // NACA four-digit style custom analytical airfoil rotated by angleOfAttack
                val chord = Nx / 3.0f
                for (y in 1 until Ny - 1) {
                    for (x in 1 until Nx - 1) {
                        // Project back to rotated chord coordinate
                        val rx = x - centerX
                        val ry = y - centerY
                        // Rotate coordinates by alpha
                        val rotX = rx * kotlin.math.cos(angleRad) + ry * kotlin.math.sin(angleRad)
                        val rotY = -rx * kotlin.math.sin(angleRad) + ry * kotlin.math.cos(angleRad)

                        val xc = rotX / chord // normalized chord standard coordinates [0, 1]
                        if (xc in 0.0f..1.0f) {
                            // Symmetrical airfoil thickness NACA 0012 formula
                            // t = 12% chord. y_t = 5*t*(0.2969*sqrt(xc) - 0.126*xc - 0.3516*xc^2 + 0.2843*xc^3 - 0.1015*xc^4)
                            val thickness = 0.14f
                            val term1 = 0.2969f * sqrt(xc)
                            val term2 = -0.1260f * xc
                            val term3 = -0.3516f * xc * xc
                            val term4 = 0.2843f * xc * xc * xc
                            val term5 = -0.1030f * xc * xc * xc * xc // Slightly tweaked for closed trailing edge
                            val yt = 5.0f * thickness * (term1 + term2 + term3 + term4 + term5) * chord

                            // Mean camber line (NACA 4412 style chamber)
                            val m = 0.04f  // max camber
                            val pLoc = 0.4f // position of max camber
                            val yc = if (xc < pLoc) {
                                (m / (pLoc * pLoc)) * (2.0f * pLoc * xc - xc * xc)
                            } else {
                                (m / ((1.0f - pLoc) * (1.0f - pLoc))) * ((1.0f - 2.0f * pLoc) + 2.0f * pLoc * xc - xc * xc)
                            } * chord

                            val upperLimit = yc + yt
                            val lowerLimit = yc - yt

                            if (rotY in lowerLimit..upperLimit) {
                                solid[index(x, y)] = true
                            }
                        }
                    }
                }
            }
            "Flat Plate" -> {
                // Vertical drag block
                val halfHeight = Ny / 5
                for (y in centerY - halfHeight..centerY + halfHeight) {
                    for (x in centerX - 1..centerX + 1) {
                        solid[index(x, y)] = true
                    }
                }
            }
            "Venturi Nozzle" -> {
                // Creates a lovely converging-diverging nozzle
                for (x in 0 until Nx) {
                    // Profile equation constricting in the middle
                    val distFraction = abs(x - Nx / 2) / (Nx / 2.0f)
                    val constriction = 1.0f - 0.5f * (1.0f - distFraction * distFraction)
                    val innerHeight = (Ny * 0.4f * constriction).toInt()
                    for (y in 0 until Ny) {
                        if (y < (Ny / 2 - innerHeight) || y > (Ny / 2 + innerHeight)) {
                            solid[index(x, y)] = true
                        }
                    }
                }
                // Free the left entrance and right outlet boundaries
                for (x in 0 until Nx) {
                    solid[index(x, 0)] = true
                    solid[index(x, Ny - 1)] = true
                }
            }
            "Custom Sketch" -> {
                // Initial empty - will be updated by user custom sketch
            }
        }
    }

    // Step simulation
    fun step(dt: Float, viscosity: Float, inletVelocity: Float, iterations: Int = 20) {
        // Save current velocity to prev
        System.arraycopy(u, 0, uPrev, 0, size)
        System.arraycopy(v, 0, vPrev, 0, size)

        // 1. Advect
        advect(0, u, uPrev, uPrev, vPrev, dt)
        advect(1, v, vPrev, uPrev, vPrev, dt)

        // 2. Add continuous fluid speed at inlet
        setInletFlow(inletVelocity)

        // 3. Diffuse (for viscosity/laminar drag)
        if (viscosity > 0.0001f) {
            System.arraycopy(u, 0, uPrev, 0, size)
            diffuse(0, u, uPrev, viscosity, dt, iterations)
            System.arraycopy(v, 0, vPrev, 0, size)
            diffuse(1, v, vPrev, viscosity, dt, iterations)
            setInletFlow(inletVelocity)
        }

        // 4. Projection Step (divergence correction and pressure solving)
        project(iterations)

        // 5. Compute Vorticity for visual rendering
        computeVorticity()

        // 6. Update tracer particles
        advectParticles(dt, inletVelocity)
    }

    private fun setInletFlow(inletVel: Float) {
        for (y in 1 until Ny - 1) {
            if (!solid[index(0, y)]) {
                u[index(0, y)] = inletVel
                v[index(0, y)] = 0.0f
            }
        }
    }

    private fun advect(b: Int, d: FloatArray, d0: FloatArray, du: FloatArray, dv: FloatArray, dt: Float) {
        val dt0 = dt * (Nx - 2) // Grid scaling factor for stability
        for (y in 1 until Ny - 1) {
            for (x in 1 until Nx - 1) {
                val idx = index(x, y)
                if (solid[idx]) {
                    d[idx] = 0.0f
                    continue
                }

                // Trace back coordinates
                var prevX = x - dt0 * du[idx]
                var prevY = y - dt0 * dv[idx]

                prevX = prevX.coerceIn(0.5f, Nx - 1.5f)
                prevY = prevY.coerceIn(0.5f, Ny - 1.5f)

                val x0 = prevX.toInt()
                val x1 = x0 + 1
                val y0 = prevY.toInt()
                val y1 = y0 + 1

                val s1 = prevX - x0
                val s0 = 1.0f - s1
                val t1 = prevY - y0
                val t0 = 1.0f - t1

                d[idx] = s0 * (t0 * d0[index(x0, y0)] + t1 * d0[index(x0, y1)]) +
                        s1 * (t0 * d0[index(x1, y0)] + t1 * d0[index(x1, y1)])
            }
        }
        applyBoundaryCondition(b, d)
    }

    private fun diffuse(b: Int, xField: FloatArray, x0Field: FloatArray, diff: Float, dt: Float, iterations: Int) {
        val a = dt * diff * (Nx - 2) * (Ny - 2)
        val c = 1.0f + 4.0f * a
        for (k in 0 until iterations) {
            for (y in 1 until Ny - 1) {
                for (x in 1 until Nx - 1) {
                    val idx = index(x, y)
                    if (solid[idx]) {
                        xField[idx] = 0.0f
                        continue
                    }
                    xField[idx] = (x0Field[idx] + a * (
                            xField[index(x - 1, y)] + xField[index(x + 1, y)] +
                                    xField[index(x, y - 1)] + xField[index(x, y + 1)]
                            )) / c
                }
            }
            applyBoundaryCondition(b, xField)
        }
    }

    private fun project(iterations: Int) {
        // Calculate divergence
        for (y in 1 until Ny - 1) {
            for (x in 1 until Nx - 1) {
                val idx = index(x, y)
                if (solid[idx]) {
                    div[idx] = 0.0f
                    p[idx] = 0.0f
                    continue
                }

                // If neighbor is solid, adjust divergence and gradient
                val uEast = if (solid[index(x + 1, y)]) -u[idx] else u[index(x + 1, y)]
                val uWest = if (solid[index(x - 1, y)]) -u[idx] else u[index(x - 1, y)]
                val vNorth = if (solid[index(x, y + 1)]) -v[idx] else v[index(x, y + 1)]
                val vSouth = if (solid[index(x, y - 1)]) -v[idx] else v[index(x, y - 1)]

                div[idx] = -0.5f * (uEast - uWest + vNorth - vSouth)
                p[idx] = 0.0f
            }
        }

        applyBoundaryCondition(0, div)
        applyBoundaryCondition(0, p)

        // Solve Poisson Equation for Pressure
        for (k in 0 until iterations) {
            for (y in 1 until Ny - 1) {
                for (x in 1 until Nx - 1) {
                    val idx = index(x, y)
                    if (solid[idx]) continue

                    // Zero-gradient pressure boundary conditions normal to solid walls
                    val pEast = if (solid[index(x + 1, y)]) p[idx] else p[index(x + 1, y)]
                    val pWest = if (solid[index(x - 1, y)]) p[idx] else p[index(x - 1, y)]
                    val pNorth = if (solid[index(x, y + 1)]) p[idx] else p[index(x, y + 1)]
                    val pSouth = if (solid[index(x, y - 1)]) p[idx] else p[index(x, y - 1)]

                    p[idx] = (div[idx] + pEast + pWest + pNorth + pSouth) / 4.0f
                }
            }
            applyBoundaryCondition(0, p)
        }

        // Project correction onto velocity fields
        for (y in 1 until Ny - 1) {
            for (x in 1 until Nx - 1) {
                val idx = index(x, y)
                if (solid[idx]) {
                    u[idx] = 0.0f
                    v[idx] = 0.0f
                    continue
                }

                val pEast = if (solid[index(x + 1, y)]) p[idx] else p[index(x + 1, y)]
                val pWest = if (solid[index(x - 1, y)]) p[idx] else p[index(x - 1, y)]
                val pNorth = if (solid[index(x, y + 1)]) p[idx] else p[index(x, y + 1)]
                val pSouth = if (solid[index(x, y - 1)]) p[idx] else p[index(x, y - 1)]

                u[idx] -= 0.5f * (pEast - pWest)
                v[idx] -= 0.5f * (pNorth - pSouth)
            }
        }

        applyBoundaryCondition(1, u)
        applyBoundaryCondition(2, v)
    }

    private fun applyBoundaryCondition(b: Int, xField: FloatArray) {
        // Left side inlet (fixed or copy)
        for (y in 1 until Ny - 1) {
            if (b == 1) {
                // Maintain velocity X at inlet
            } else {
                xField[index(0, y)] = xField[index(1, y)]
            }
        }
        // Right side outlet (extrapolate)
        for (y in 1 until Ny - 1) {
            xField[index(Nx - 1, y)] = xField[index(Nx - 2, y)]
        }
        // Top and Bottom walls boundary conditions
        for (x in 1 until Nx - 1) {
            xField[index(x, 0)] = if (b == 2) -xField[index(x, 1)] else xField[index(x, 1)]
            xField[index(x, Ny - 1)] = if (b == 2) -xField[index(x, Ny - 2)] else xField[index(x, Ny - 2)]
        }

        // Corner cells average
        xField[index(0, 0)] = 0.5f * (xField[index(1, 0)] + xField[index(0, 1)])
        xField[index(0, Ny - 1)] = 0.5f * (xField[index(1, Ny - 1)] + xField[index(0, Ny - 2)])
        xField[index(Nx - 1, 0)] = 0.5f * (xField[index(Nx - 2, 0)] + xField[index(Nx - 1, 1)])
        xField[index(Nx - 1, Ny - 1)] = 0.5f * (xField[index(Nx - 2, Ny - 1)] + xField[index(Nx - 1, Ny - 2)])
    }

    private fun computeVorticity() {
        for (y in 1 until Ny - 1) {
            for (x in 1 until Nx - 1) {
                // Curl (du/dy - dv/dx)
                val du_dy = 0.5f * (u[index(x, y + 1)] - u[index(x, y - 1)])
                val dv_dx = 0.5f * (v[index(x + 1, y)] - v[index(x - 1, y)])
                vort[index(x, y)] = du_dy - dv_dx
            }
        }
    }

    // Bilinear velocity interpolation
    fun getVelocityAt(px: Float, py: Float): Pair<Float, Float> {
        val x = px.coerceIn(0.0f, Nx - 1.0f)
        val y = py.coerceIn(0.0f, Ny - 1.0f)

        val x0 = x.toInt()
        val x1 = (x0 + 1).coerceAtMost(Nx - 1)
        val y0 = y.toInt()
        val y1 = (y0 + 1).coerceAtMost(Ny - 1)

        val fx = x - x0
        val fy = y - y0

        val u00 = u[index(x0, y0)]
        val u10 = u[index(x1, y0)]
        val u01 = u[index(x0, y1)]
        val u11 = u[index(x1, y1)]

        val interpolU = (1.0f - fx) * ((1.0f - fy) * u00 + fy * u01) + fx * ((1.0f - fy) * u10 + fy * u11)

        val v00 = v[index(x0, y0)]
        val v10 = v[index(x1, y0)]
        val v01 = v[index(x0, y1)]
        val v11 = v[index(x1, y1)]

        val interpolV = (1.0f - fx) * ((1.0f - fy) * v00 + fy * v01) + fx * ((1.0f - fy) * v10 + fy * v11)

        return Pair(interpolU, interpolV)
    }

    private fun advectParticles(dt: Float, inletVel: Float) {
        for (p in particles) {
            val (pu, pv) = getVelocityAt(p.x, p.y)
            p.x += pu * dt * 8.0f // Scale up for visual tracer effect
            p.y += pv * dt * 8.0f

            // Recycle if exited wind tunnel or hit inside solid
            val isSolid = solid[index(p.x.toInt(), p.y.toInt())]
            if (p.x >= Nx - 1.5f || p.x <= 0.5f || p.y >= Ny - 1.5f || p.y <= 0.5f || isSolid) {
                p.x = 1.0f + Random.nextFloat() * 4.0f // reset on left inlet zone
                p.y = 1.5f + Random.nextFloat() * (Ny - 3.0f)
            }
        }
    }

    // Add user drawing support directly to solid mask
    fun addBrushSolid(gridX: Int, gridY: Int, radius: Int = 2) {
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                val cx = gridX + dx
                val cy = gridY + dy
                if (cx in 2 until Nx - 2 && cy in 2 until Ny - 2) {
                    if (dx * dx + dy * dy <= radius * radius) {
                        solid[index(cx, cy)] = true
                    }
                }
            }
        }
    }

    // Integating forces along boundary
    fun computeAerodynamicForces(): Pair<Float, Float> {
        var lift = 0.0f
        var drag = 0.0f

        // Scan solid boundaries for pressure differential
        for (y in 1 until Ny - 1) {
            for (x in 1 until Nx - 1) {
                if (solid[index(x, y)]) {
                    // Check neighbors to extract surface normal forces
                    // Normal towards East (+X)
                    if (!solid[index(x + 1, y)]) {
                        drag -= p[index(x + 1, y)] // Flow pushing on solid front
                    }
                    // Normal towards West (-X)
                    if (!solid[index(x - 1, y)]) {
                        drag += p[index(x - 1, y)] // Flow suction on trailing end
                    }
                    // Normal towards North (+Y) (forcing upward lift)
                    if (!solid[index(x, y + 1)]) {
                        lift += p[index(x, y + 1)]
                    }
                    // Normal towards South (-Y) (forcing downward)
                    if (!solid[index(x, y - 1)]) {
                        lift -= p[index(x, y - 1)]
                    }
                }
            }
        }

        // Tweak and scale physically for illustrative coefficients
        val scaler = 0.35f
        return Pair(lift * scaler, drag * scaler)
    }
}
