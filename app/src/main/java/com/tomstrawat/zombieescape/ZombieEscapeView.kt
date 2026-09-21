package com.tomstrawat.zombieescape

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class ZombieEscapeView(context: Context) : View(context) {

    private enum class State { MENU, PLAYING, PAUSED, WON, LOST }

    private data class Zombie(
        var x: Float,
        var y: Float,
        var vx: Float = 0f,
        var vy: Float = 0f,
        var phase: Float = Random.nextFloat() * 6.28f
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonRect = RectF()
    private val exitRect = RectF()
    private val rng = Random(7)

    private val worldWidth = 1800f
    private val worldHeight = 1000f
    private val playerRadius = 22f
    private val playerSpeed = 260f

    private val zombies = ArrayList<Zombie>()
    private val obstacles = listOf(
        RectF(420f, 170f, 740f, 280f),
        RectF(940f, 130f, 1300f, 245f),
        RectF(240f, 560f, 520f, 690f),
        RectF(720f, 470f, 1040f, 590f),
        RectF(1240f, 620f, 1600f, 760f),
        RectF(1040f, 800f, 1230f, 910f)
    )

    private var state = State.MENU
    private var playerX = 140f
    private var playerY = 500f
    private var health = 100f
    private var shards = 0
    private var startTime = 0L
    private var lastFrame = 0L
    private var joystickPointer = MotionEvent.INVALID_POINTER_ID
    private var joystickX = 0f
    private var joystickY = 0f
    private var joystickBaseX = 0f
    private var joystickBaseY = 0f
    private var joystickActive = false
    private var sprintHeld = false

    private val shardPoints = arrayOf(
        floatArrayOf(300f, 310f),
        floatArrayOf(1160f, 355f),
        floatArrayOf(1510f, 540f)
    )
    private val collected = BooleanArray(shardPoints.size)

    init {
        isFocusable = true
        textPaint.typeface = android.graphics.Typeface.create(
            "sans",
            android.graphics.Typeface.BOLD
        )
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (state == State.PLAYING) update()
        val w = width.toFloat()
        val h = height.toFloat()

        when (state) {
            State.MENU -> drawMenu(canvas, w, h)
            else -> {
                drawWorld(canvas, w, h)
                drawHud(canvas, w, h)
                when (state) {
                    State.PAUSED -> drawOverlay(canvas, w, h, "PAUSED", "Tap resume to continue")
                    State.WON -> drawOverlay(canvas, w, h, "ESCAPED", "All beacons collected. You made it out.")
                    State.LOST -> drawOverlay(canvas, w, h, "CAUGHT", "The horde reached you before the exit.")
                    else -> Unit
                }
            }
        }
        if (state == State.PLAYING) postInvalidateOnAnimation()
    }

    private fun drawMenu(canvas: Canvas, w: Float, h: Float) {
        paint.style = Paint.Style.FILL
        paint.color = android.graphics.Color.rgb(6, 10, 12)
        canvas.drawColor(paint.color)

        paint.color = android.graphics.Color.rgb(17, 28, 31)
        canvas.drawCircle(w * 0.78f, h * 0.32f, h * 0.30f, paint)
        paint.color = android.graphics.Color.rgb(13, 21, 23)
        canvas.drawCircle(w * 0.80f, h * 0.35f, h * 0.21f, paint)

        drawTitle(canvas, "ZOMBIE ESCAPE", w * 0.09f, h * 0.27f, h * 0.10f)
        drawText(
            canvas,
            "Collect 3 energy beacons and reach the exit.",
            w * 0.09f,
            h * 0.39f,
            h * 0.035f,
            android.graphics.Color.LTGRAY,
            Paint.Align.LEFT
        )
        drawText(
            canvas,
            "Move with the left stick. Hold SPRINT to move faster.",
            w * 0.09f,
            h * 0.45f,
            h * 0.028f,
            android.graphics.Color.GRAY,
            Paint.Align.LEFT
        )

        buttonRect.set(w * 0.09f, h * 0.57f, w * 0.34f, h * 0.72f)
        drawButton(canvas, buttonRect, "PLAY", true)
        drawText(
            canvas,
            "Native Kotlin / Canvas",
            w * 0.09f,
            h * 0.82f,
            h * 0.024f,
            android.graphics.Color.DKGRAY,
            Paint.Align.LEFT
        )
    }

    private fun drawWorld(canvas: Canvas, w: Float, h: Float) {
        paint.style = Paint.Style.FILL
        paint.color = android.graphics.Color.rgb(9, 15, 16)
        canvas.drawColor(paint.color)

        val scale = min(w / 1280f, h / 720f)
        val offsetX = (w - worldWidth * scale) * 0.5f
        val offsetY = (h - worldHeight * scale) * 0.5f

        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)

        paint.color = android.graphics.Color.rgb(24, 39, 39)
        canvas.drawRect(0f, 0f, worldWidth, worldHeight, paint)
        drawGroundPattern(canvas)
        drawObstacles(canvas)
        drawExit(canvas)
        drawShards(canvas)
        drawZombies(canvas)
        drawPlayer(canvas)
        canvas.restore()

        drawControls(canvas, w, h)
    }

    private fun drawGroundPattern(canvas: Canvas) {
        paint.color = android.graphics.Color.rgb(31, 49, 47)
        paint.strokeWidth = 2f
        for (x in 0..1800 step 90) {
            canvas.drawLine(x.toFloat(), 0f, x.toFloat(), worldHeight, paint)
        }
        for (y in 0..1000 step 90) {
            canvas.drawLine(0f, y.toFloat(), worldWidth, y.toFloat(), paint)
        }

        paint.color = android.graphics.Color.rgb(37, 58, 55)
        for (i in 0 until 110) {
            val x = ((i * 137) % 1780 + 10).toFloat()
            val y = ((i * 83) % 980 + 10).toFloat()
            canvas.drawCircle(x, y, 3f + (i % 3), paint)
        }
    }

    private fun drawObstacles(canvas: Canvas) {
        for (rect in obstacles) {
            paint.color = android.graphics.Color.rgb(11, 19, 21)
            canvas.drawRoundRect(rect, 18f, 18f, paint)
            paint.color = android.graphics.Color.rgb(51, 70, 68)
            canvas.drawRoundRect(
                RectF(rect.left + 7f, rect.top + 7f, rect.right - 7f, rect.bottom - 7f),
                14f,
                14f,
                paint
            )
            paint.color = android.graphics.Color.rgb(69, 88, 83)
            canvas.drawLine(
                rect.left + 18f,
                rect.top + 24f,
                rect.right - 18f,
                rect.top + 24f,
                paint
            )
        }
    }

    private fun drawExit(canvas: Canvas) {
        exitRect.set(1660f, 380f, 1760f, 620f)
        paint.color = android.graphics.Color.rgb(30, 90, 72)
        canvas.drawRoundRect(exitRect, 26f, 26f, paint)
        paint.color = android.graphics.Color.rgb(57, 170, 124)
        canvas.drawRoundRect(RectF(1680f, 400f, 1740f, 600f), 20f, 20f, paint)
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(1710f, 500f, 7f, paint)
    }

    private fun drawShards(canvas: Canvas) {
        for (i in shardPoints.indices) {
            if (collected[i]) continue
            val x = shardPoints[i][0]
            val y = shardPoints[i][1]
            val pulse = 1f + 0.12f * sin((SystemClock.uptimeMillis() / 180f) + i)
            paint.color = android.graphics.Color.rgb(67, 207, 195)
            canvas.drawCircle(x, y, 19f * pulse, paint)
            paint.color = android.graphics.Color.WHITE
            canvas.drawCircle(x, y, 6f, paint)
        }
    }

    private fun drawZombies(canvas: Canvas) {
        for (zombie in zombies) {
            paint.color = android.graphics.Color.rgb(133, 67, 74)
            canvas.drawCircle(zombie.x, zombie.y, 25f, paint)
            paint.color = android.graphics.Color.rgb(198, 150, 120)
            canvas.drawCircle(zombie.x, zombie.y - 3f, 15f, paint)
            paint.color = android.graphics.Color.rgb(34, 42, 38)
            canvas.drawCircle(zombie.x - 6f, zombie.y - 5f, 3f, paint)
            canvas.drawCircle(zombie.x + 6f, zombie.y - 5f, 3f, paint)
            paint.color = android.graphics.Color.rgb(15, 18, 17)
            canvas.drawRect(
                zombie.x - 5f,
                zombie.y + 4f,
                zombie.x + 5f,
                zombie.y + 8f,
                paint
            )
        }
    }

    private fun drawPlayer(canvas: Canvas) {
        paint.color = android.graphics.Color.rgb(76, 190, 116)
        canvas.drawCircle(playerX, playerY, playerRadius, paint)
        paint.color = android.graphics.Color.rgb(218, 245, 223)
        canvas.drawCircle(playerX + 6f, playerY - 7f, 5f, paint)
        paint.color = android.graphics.Color.rgb(24, 61, 39)
        canvas.drawCircle(playerX - 5f, playerY - 3f, 3f, paint)
    }

    private fun drawHud(canvas: Canvas, w: Float, h: Float) {
        paint.color = android.graphics.Color.argb(205, 3, 7, 8)
        canvas.drawRoundRect(RectF(20f, 18f, w - 20f, 86f), 18f, 18f, paint)

        drawText(
            canvas,
            "BEACONS  " + shards + "/3",
            42f,
            58f,
            24f,
            android.graphics.Color.WHITE,
            Paint.Align.LEFT
        )
        drawText(canvas, "HEALTH", w * 0.40f, 44f, 17f, android.graphics.Color.LTGRAY, Paint.Align.LEFT)

        paint.color = android.graphics.Color.rgb(45, 57, 57)
        canvas.drawRoundRect(RectF(w * 0.40f, 51f, w * 0.59f, 66f), 8f, 8f, paint)
        paint.color = android.graphics.Color.rgb(80, 191, 116)
        canvas.drawRoundRect(
            RectF(w * 0.40f, 51f, w * (0.40f + 0.19f * health / 100f), 66f),
            8f,
            8f,
            paint
        )

        val seconds = ((SystemClock.uptimeMillis() - startTime) / 1000L).coerceAtLeast(0L)
        drawText(
            canvas,
            String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60),
            w * 0.67f,
            57f,
            23f,
            android.graphics.Color.WHITE,
            Paint.Align.LEFT
        )

        buttonRect.set(w - 135f, 25f, w - 35f, 75f)
        drawButton(canvas, buttonRect, "II", false)
    }

    private fun drawControls(canvas: Canvas, w: Float, h: Float) {
        val baseX = if (joystickActive) joystickBaseX else 120f
        val baseY = if (joystickActive) joystickBaseY else h - 130f

        paint.color = android.graphics.Color.argb(75, 255, 255, 255)
        canvas.drawCircle(baseX, baseY, 80f, paint)
        paint.color = android.graphics.Color.argb(115, 255, 255, 255)
        val knobX = if (joystickActive) joystickX else baseX
        val knobY = if (joystickActive) joystickY else baseY
        canvas.drawCircle(knobX, knobY, 34f, paint)

        buttonRect.set(w - 190f, h - 140f, w - 35f, h - 55f)
        drawButton(canvas, buttonRect, "SPRINT", sprintHeld)
    }

    private fun drawButton(canvas: Canvas, rect: RectF, label: String, active: Boolean) {
        paint.color = if (active) {
            android.graphics.Color.rgb(57, 170, 124)
        } else {
            android.graphics.Color.rgb(29, 40, 42)
        }
        canvas.drawRoundRect(rect, 20f, 20f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = android.graphics.Color.argb(160, 255, 255, 255)
        canvas.drawRoundRect(rect, 20f, 20f, paint)
        paint.style = Paint.Style.FILL

        drawText(
            canvas,
            label,
            rect.centerX(),
            rect.centerY() + 9f,
            min(25f, rect.height() * 0.34f),
            android.graphics.Color.WHITE,
            Paint.Align.CENTER
        )
    }

    private fun drawTitle(canvas: Canvas, title: String, x: Float, y: Float, size: Float) {
        drawText(canvas, title, x, y, size, android.graphics.Color.WHITE, Paint.Align.LEFT)
        paint.color = android.graphics.Color.rgb(57, 170, 124)
        canvas.drawRect(x, y + 14f, x + size * 2.6f, y + 18f, paint)
    }

    private fun drawText(
        canvas: Canvas,
        text: String,
        x: Float,
        baseline: Float,
        size: Float,
        color: Int,
        align: Paint.Align
    ) {
        textPaint.textSize = size
        textPaint.color = color
        textPaint.textAlign = align
        canvas.drawText(text, x, baseline, textPaint)
    }

    private fun drawOverlay(canvas: Canvas, w: Float, h: Float, title: String, subtitle: String) {
        paint.color = android.graphics.Color.argb(225, 4, 8, 9)
        canvas.drawRect(0f, 0f, w, h, paint)
        drawText(canvas, title, w * 0.5f, h * 0.36f, h * 0.10f, android.graphics.Color.WHITE, Paint.Align.CENTER)
        drawText(canvas, subtitle, w * 0.5f, h * 0.46f, h * 0.032f, android.graphics.Color.LTGRAY, Paint.Align.CENTER)

        buttonRect.set(w * 0.36f, h * 0.57f, w * 0.50f, h * 0.70f)
        drawButton(canvas, buttonRect, if (state == State.PAUSED) "RESUME" else "RESTART", true)

        buttonRect.set(w * 0.53f, h * 0.57f, w * 0.67f, h * 0.70f)
        drawButton(canvas, buttonRect, "MENU", false)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                val px = event.getX(index)
                val py = event.getY(index)

                if (state == State.MENU && buttonRect.contains(px, py)) {
                    startGame()
                    return true
                }

                if (state == State.PLAYING) {
                    val pauseRect = RectF(width - 135f, 25f, width - 35f, 75f)
                    if (pauseRect.contains(px, py)) {
                        state = State.PAUSED
                        invalidate()
                        return true
                    }

                    if (px < width * 0.45f && joystickPointer == MotionEvent.INVALID_POINTER_ID) {
                        joystickPointer = event.getPointerId(index)
                        joystickBaseX = px
                        joystickBaseY = py
                        joystickX = px
                        joystickY = py
                        joystickActive = true
                    } else if (px > width - 230f && py > height - 180f) {
                        sprintHeld = true
                    }
                } else if (state == State.PAUSED || state == State.WON || state == State.LOST) {
                    val restartRect = RectF(width * 0.36f, height * 0.57f, width * 0.50f, height * 0.70f)
                    val menuRect = RectF(width * 0.53f, height * 0.57f, width * 0.67f, height * 0.70f)
                    when {
                        restartRect.contains(px, py) -> {
                            if (state == State.PAUSED) {
                                state = State.PLAYING
                                lastFrame = SystemClock.uptimeMillis()
                            } else {
                                startGame()
                            }
                        }
                        menuRect.contains(px, py) -> state = State.MENU
                    }
                }
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                if (joystickPointer != MotionEvent.INVALID_POINTER_ID) {
                    val index = event.findPointerIndex(joystickPointer)
                    if (index >= 0) {
                        val dx = event.getX(index) - joystickBaseX
                        val dy = event.getY(index) - joystickBaseY
                        val len = sqrt(dx * dx + dy * dy)
                        val limit = 80f
                        val factor = if (len > limit) limit / len else 1f
                        joystickX = joystickBaseX + dx * factor
                        joystickY = joystickBaseY + dy * factor
                    }
                }
                invalidate()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val index = event.actionIndex
                val pointerId = event.getPointerId(index)
                if (pointerId == joystickPointer) {
                    joystickPointer = MotionEvent.INVALID_POINTER_ID
                    joystickActive = false
                    joystickX = 0f
                    joystickY = 0f
                }
                if (x > width - 230f && y > height - 180f) sprintHeld = false
                invalidate()
            }
        }
        return true
    }

    private fun startGame() {
        state = State.PLAYING
        playerX = 140f
        playerY = 500f
        health = 100f
        shards = 0
        collected.fill(false)
        zombies.clear()

        repeat(12) {
            var x: Float
            var y: Float
            do {
                x = 500f + rng.nextFloat() * 1050f
                y = 70f + rng.nextFloat() * 860f
            } while (distance(x, y, playerX, playerY) < 260f || isBlocked(x, y))
            zombies += Zombie(x, y)
        }

        startTime = SystemClock.uptimeMillis()
        lastFrame = startTime
        joystickPointer = MotionEvent.INVALID_POINTER_ID
        joystickActive = false
        sprintHeld = false
        invalidate()
    }

    private fun update() {
        if (state != State.PLAYING) return
        val now = SystemClock.uptimeMillis()
        var dt = (now - lastFrame) / 1000f
        lastFrame = now
        dt = clamp(dt, 0f, 0.05f)

        var inputX = 0f
        var inputY = 0f
        if (joystickActive) {
            inputX = (joystickX - joystickBaseX) / 80f
            inputY = (joystickY - joystickBaseY) / 80f
        }

        val magnitude = sqrt(inputX * inputX + inputY * inputY)
        if (magnitude > 1f) {
            inputX /= magnitude
            inputY /= magnitude
        }

        val speed = if (sprintHeld) playerSpeed * 1.55f else playerSpeed
        movePlayer(inputX * speed * dt, inputY * speed * dt)

        for (i in zombies.indices) {
            val zombie = zombies[i]
            zombie.phase += dt
            val dx = playerX - zombie.x
            val dy = playerY - zombie.y
            val d = max(1f, sqrt(dx * dx + dy * dy))
            val detection = 620f

            if (d < detection) {
                val targetVx = dx / d * 95f
                val targetVy = dy / d * 95f
                zombie.vx = moveToward(zombie.vx, targetVx, 260f * dt)
                zombie.vy = moveToward(zombie.vy, targetVy, 260f * dt)
            } else {
                zombie.vx = cos(zombie.phase * 0.7f + i) * 25f
                zombie.vy = sin(zombie.phase * 0.9f + i) * 25f
            }

            val nx = zombie.x + zombie.vx * dt
            val ny = zombie.y + zombie.vy * dt
            if (!isBlocked(nx, ny)) {
                zombie.x = clamp(nx, 25f, worldWidth - 25f)
                zombie.y = clamp(ny, 25f, worldHeight - 25f)
            } else {
                zombie.vx *= -0.5f
                zombie.vy *= -0.5f
            }

            if (d < 55f) health -= 18f * dt
        }

        separateZombies()

        for (i in shardPoints.indices) {
            if (!collected[i] && distance(playerX, playerY, shardPoints[i][0], shardPoints[i][1]) < 55f) {
                collected[i] = true
                shards++
            }
        }

        if (health <= 0f) {
            health = 0f
            state = State.LOST
        }

        if (shards == shardPoints.size && exitRect.contains(playerX, playerY)) {
            state = State.WON
        }
    }

    private fun movePlayer(dx: Float, dy: Float) {
        val nextX = clamp(playerX + dx, playerRadius, worldWidth - playerRadius)
        if (!isBlocked(nextX, playerY)) playerX = nextX

        val nextY = clamp(playerY + dy, playerRadius, worldHeight - playerRadius)
        if (!isBlocked(playerX, nextY)) playerY = nextY
    }

    private fun separateZombies() {
        for (i in zombies.indices) {
            for (j in i + 1 until zombies.size) {
                val a = zombies[i]
                val b = zombies[j]
                val dx = b.x - a.x
                val dy = b.y - a.y
                val d2 = dx * dx + dy * dy
                if (d2 in 1f..2500f) {
                    val d = sqrt(d2)
                    val push = (50f - d) * 0.04f
                    val nx = dx / d
                    val ny = dy / d
                    a.x -= nx * push
                    a.y -= ny * push
                    b.x += nx * push
                    b.y += ny * push
                }
            }
        }
    }

    private fun isBlocked(x: Float, y: Float): Boolean {
        for (rect in obstacles) {
            if (
                x + playerRadius > rect.left &&
                x - playerRadius < rect.right &&
                y + playerRadius > rect.top &&
                y - playerRadius < rect.bottom
            ) return true
        }
        return false
    }
}
