package com.frezzybuilds.devnotch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Rendert das adaptive App-Icon wie ein Launcher (Kreis, Squircle, abgerundetes Quadrat,
 * Themed Icon) und das 512-px-Icon für den Play Store nach build/screenshots/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AppIconRenderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Beide Ebenen auf die volle 108-dp-Fläche (size px) zeichnen. */
    private fun layers(size: Int, vararg ids: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        ids.forEach { id ->
            ContextCompat.getDrawable(context, id)!!.apply { setBounds(0, 0, size, size) }.draw(canvas)
        }
        return bitmap
    }

    /** Sichtbarer Ausschnitt: die mittleren 72 von 108 dp, maskiert. */
    private fun masked(full: Bitmap, out: Int, mask: Path.(Float) -> Unit): Bitmap {
        val crop = full.width * 18 / 108
        val visible = Bitmap.createBitmap(full, crop, crop, full.width - 2 * crop, full.width - 2 * crop)
        val result = Bitmap.createBitmap(out, out, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawPath(Path().apply { mask(out.toFloat()) }, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(Bitmap.createScaledBitmap(visible, out, out, true), 0f, 0f, paint)
        return result
    }

    private fun save(bitmap: Bitmap, name: String) {
        File("build/screenshots/$name.png").apply { parentFile?.mkdirs() }.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun renderIcons() {
        val full = layers(1080, R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground)

        // Play Store: 512 × 512, quadratisch (Google rundet selbst ab).
        save(masked(full, 512) { s -> addRect(0f, 0f, s, s, Path.Direction.CW) }, "icon_play_512")

        val circle: Path.(Float) -> Unit = { s -> addCircle(s / 2, s / 2, s / 2, Path.Direction.CW) }
        val squircle: Path.(Float) -> Unit = { s -> addRoundRect(RectF(0f, 0f, s, s), s * 0.38f, s * 0.38f, Path.Direction.CW) }
        val rounded: Path.(Float) -> Unit = { s -> addRoundRect(RectF(0f, 0f, s, s), s * 0.2f, s * 0.2f, Path.Direction.CW) }

        // Themed Icon: Monochrom-Ebene, wie Android 13 sie einfärbt.
        val mono = layers(1080, R.drawable.ic_launcher_monochrome)
        val themed = Bitmap.createBitmap(1080, 1080, Bitmap.Config.ARGB_8888).also { b ->
            val c = Canvas(b)
            c.drawColor(Color.parseColor("#D7E3FF"))
            val tint = Paint().apply {
                colorFilter = android.graphics.PorterDuffColorFilter(Color.parseColor("#1B3A6B"), PorterDuff.Mode.SRC_IN)
            }
            c.drawBitmap(mono, 0f, 0f, tint)
        }

        // Übersicht: verschiedene Masken und Größen auf hellem und dunklem Hintergrund.
        val sizes = listOf(192, 144, 96, 48)
        val sheet = Bitmap.createBitmap(4 * 220 + 40, 2 * 260 + 2 * 200 + 40, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.parseColor("#F1F1F4"))
        canvas.drawRect(0f, (260 + 20).toFloat() * 2, sheet.width.toFloat(), sheet.height.toFloat(), Paint().apply { color = Color.parseColor("#1B1B1F") })
        listOf(circle, squircle, rounded).forEachIndexed { i, mask ->
            canvas.drawBitmap(masked(full, 192, mask), 20f + i * 220, 20f, null)
        }
        canvas.drawBitmap(masked(themed, 192, circle), 20f + 3 * 220, 20f, null)
        var x = 20f
        sizes.forEach { s ->
            canvas.drawBitmap(masked(full, s, circle), x, 300f + (192 - s) / 2f, null)
            x += s + 30
        }
        x = 20f
        sizes.forEach { s ->
            canvas.drawBitmap(masked(full, s, squircle), x, 600f + (192 - s) / 2f, null)
            x += s + 30
        }
        canvas.drawBitmap(masked(full, 192, circle), 20f + 3 * 220 - 40, 780f, null)
        save(sheet, "icon_sheet")
    }
}
