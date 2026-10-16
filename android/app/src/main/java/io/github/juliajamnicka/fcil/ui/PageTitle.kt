package io.github.juliajamnicka.fcil.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import io.github.juliajamnicka.fcil.R

private val Comfortaa = FontFamily(Font(R.font.comfortaa_bold, FontWeight.Bold))

/** Outline that brings Comfortaa Bold to the wordmark's stroke weight, in em. */
private const val OUTLINE = 0.045f
/** The red copy's outline is a little wider so no blue edge shows around an accent. */
private const val ACCENT_OUTLINE = 0.06f

/**
 * Where the red copy of the title stops for an accented letter, in em above the baseline: above
 * the x-height for lowercase, above the cap height for capitals. "í" sits lower than the other
 * accents. ď and ť carry their háček beside the ascender, so they stay blue.
 */
internal fun accentCut(c: Char): Float? = when (c) {
    'í' -> 0.546f
    in "áčéěňóřšúůýž" -> 0.581f
    in "ÁČĎÉĚÍŇÓŘŠŤÚŮÝŽ" -> 0.811f
    else -> null
}

/**
 * A page title in the style of the wordmark: Comfortaa in brand blue with the accents in red.
 * Comfortaa composes a letter and its accent into one glyph, so the accents cannot be coloured as
 * characters; the title is drawn in blue and again in red, clipped to above each accented letter.
 */
@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier, fontSize: TextUnit = 34.sp) {
    val dark = isSystemInDarkTheme()
    // as the wordmark (drawable-night: white with the brighter red)
    val blue = if (dark) Color.White else BrandBlue
    val red = if (dark) BrightRed else BrandRed
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val layout = remember(text, fontSize, density) {
        measurer.measure(
            text,
            TextStyle(fontFamily = Comfortaa, fontWeight = FontWeight.Bold, fontSize = fontSize, letterSpacing = (-0.2).sp),
            softWrap = false,
            maxLines = 1,
        )
    }
    val em = with(density) { fontSize.toPx() }
    val top = em * 0.25f // capital accents rise above the font's ascent
    val side = em * ACCENT_OUTLINE / 2
    val width = with(density) { (layout.size.width + 2 * side).toDp() }
    val height = with(density) { (layout.size.height + top + side).toDp() }
    Canvas(modifier.size(width, height).semantics { heading(); contentDescription = text }) {
        translate(side, top) {
            drawTitle(layout, blue, em * OUTLINE)
            text.forEachIndexed { i, c ->
                val cut = accentCut(c) ?: return@forEachIndexed
                val box = layout.getBoundingBox(i)
                val baseline = layout.getLineBaseline(layout.getLineForOffset(i))
                clipRect(left = box.left - side, top = -top, right = box.right + side, bottom = baseline - cut * em) {
                    drawTitle(layout, red, em * ACCENT_OUTLINE)
                }
            }
        }
    }
}

private fun DrawScope.drawTitle(layout: TextLayoutResult, color: Color, outline: Float) {
    drawText(layout, color = color, drawStyle = Fill)
    drawText(layout, color = color, drawStyle = Stroke(width = outline, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
