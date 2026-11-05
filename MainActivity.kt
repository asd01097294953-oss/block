package com.example.endlessbrickbreaker

import android.app.Activity
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.max
import kotlin.math.min

class MainActivity : Activity() {
    private lateinit var game: GameView
    private val prefs by lazy { getSharedPreferences("game", MODE_PRIVATE) }
    private val brickPicker = 101
    private val ballPicker = 102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 8)
            setBackgroundColor(Color.rgb(24, 25, 42))
        }
        val title = TextView(this).apply {
            text = "무한 벽돌깨기"
            textSize = 25f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
        }
        root.addView(title, LinearLayout.LayoutParams(-1, 54))
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val brickButton = Button(this).apply {
            text = "벽돌 사진 선택"
            setOnClickListener { pickImage(brickPicker) }
        }
        val ballButton = Button(this).apply {
            text = "공 사진 선택"
            setOnClickListener { pickImage(ballPicker) }
        }
        buttons.addView(brickButton, LinearLayout.LayoutParams(0, 54, 1f))
        buttons.addView(ballButton, LinearLayout.LayoutParams(0, 54, 1f))
        root.addView(buttons)
        val help = TextView(this).apply {
            text = "화면 아래 패들을 좌우로 움직여 공을 튕기세요!"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            gravity = android.view.Gravity.CENTER
        }
        root.addView(help, LinearLayout.LayoutParams(-1, 38))
        game = GameView()
        root.addView(game, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        prefs.getString("brick", null)?.let { game.setImage(Uri.parse(it), true) }
        prefs.getString("ball", null)?.let { game.setImage(Uri.parse(it), false) }
    }

    private fun pickImage(request: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, request)
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) { }
        if (requestCode == brickPicker) {
            prefs.edit().putString("brick", uri.toString()).apply()
            game.setImage(uri, true)
        } else if (requestCode == ballPicker) {
            prefs.edit().putString("ball", uri.toString()).apply()
            game.setImage(uri, false)
        }
    }

    inner class GameView : View(this@MainActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var brickBitmap: Bitmap? = null
        private var ballBitmap: Bitmap? = null
        private val bricks = mutableListOf<RectF>()
        private var ballX = 0f
        private var ballY = 0f
        private var vx = 210f
        private var vy = 300f
        private var paddleX = 0f
        private var paddleY = 0f
        private var score = 0
        private var startedAt = System.currentTimeMillis()
        private var lastFrame = System.currentTimeMillis()
        private var initialized = false

        fun setImage(uri: Uri, isBrick: Boolean) {
            try {
                val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                if (bitmap != null) {
                    if (isBrick) brickBitmap = bitmap else ballBitmap = bitmap
                    invalidate()
                } else Toast.makeText(this@MainActivity, "사진을 불러오지 못했어요.", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(this@MainActivity, "사진을 불러오지 못했어요.", Toast.LENGTH_SHORT).show()
            }
        }

        private fun createBricks() {
            bricks.clear()
            val cols = 7
            val rows = 5
            val gap = width * 0.012f
            val brickW = (width - gap * (cols + 1)) / cols
            val brickH = max(22f, height * 0.035f)
            for (r in 0 until rows) for (c in 0 until cols) {
                val left = gap + c * (brickW + gap)
                val top = height * 0.16f + r * (brickH + gap)
                bricks.add(RectF(left, top, left + brickW, top + brickH))
            }
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w > 0 && h > 0) {
                paddleX = w / 2f
                paddleY = h - 55f
                ballX = w / 2f
                ballY = h * 0.68f
                createBricks()
                initialized = true
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!initialized) return
            val now = System.currentTimeMillis()
            val dt = min(0.032f, (now - lastFrame) / 1000f)
            lastFrame = now
            val speedMultiplier = 1f + ((now - startedAt) / 5000L) * 0.05f
            val radius = 15f
            ballX += vx * speedMultiplier * dt
            ballY += vy * speedMultiplier * dt

            if (ballX < radius || ballX > width - radius) vx = -vx
            if (ballY < radius) vy = kotlin.math.abs(vy)

            val paddle = RectF(paddleX - 65f, paddleY, paddleX + 65f, paddleY + 16f)
            if (vy > 0 && ballY + radius >= paddle.top && ballY - radius <= paddle.bottom &&
                ballX >= paddle.left && ballX <= paddle.right) {
                vy = -kotlin.math.abs(vy)
                val offset = (ballX - paddleX) / 65f
                vx = 260f * offset
            }
            if (ballY > height + radius) {
                ballX = width / 2f
                ballY = height * 0.68f
                vx = 210f
                vy = 300f
            }

            val ballRect = RectF(ballX - radius, ballY - radius, ballX + radius, ballY + radius)
            val hit = bricks.firstOrNull { RectF.intersects(it, ballRect) }
            if (hit != null) {
                bricks.remove(hit)
                vy = -vy
                score += 10
            }
            if (bricks.isEmpty()) createBricks()

            canvas.drawColor(Color.rgb(24, 25, 42))
            paint.color = Color.WHITE
            paint.textSize = 18f
            canvas.drawText("점수: $score", 14f, 28f, paint)
            canvas.drawText("속도: ${(speedMultiplier * 100).toInt()}%", 14f, 52f, paint)

            for (rect in bricks) {
                if (brickBitmap != null) {
                    canvas.drawBitmap(brickBitmap!!, null, rect, paint)
                } else {
                    paint.color = Color.rgb(255, 105, 180)
                    canvas.drawRoundRect(rect, 5f, 5f, paint)
                }
            }

            if (ballBitmap != null) {
                canvas.drawBitmap(ballBitmap!!, null, ballRect, paint)
            } else {
                paint.color = Color.WHITE
                canvas.drawCircle(ballX, ballY, radius, paint)
            }
            paint.color = Color.WHITE
            canvas.drawRoundRect(paddle, 8f, 8f, paint)
            postInvalidateOnAnimation()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    paddleX = event.x.coerceIn(65f, width - 65f)
                    invalidate()
                    return true
                }
            }
            return true
        }
    }
}
