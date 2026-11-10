package ca.safehaven.intake.ui.intake

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ca.safehaven.intake.R
import ca.safehaven.intake.domain.model.Signature
import ca.safehaven.intake.domain.model.SignaturePoint
import kotlin.math.abs

private val PAD_HEIGHT = 180.dp
private val INK_WIDTH = 3.dp
private val BASELINE_INSET = 24.dp
private val INK = Color.Black
private val BASELINE = Color(0xFFB0ABA3)
private val HINT = Color(0xFF9A958D)

/** Points closer than this (in px) to the previous one are dropped; they add nothing visible. */
private const val MIN_POINT_DISTANCE_PX = 1.5f

/**
 * A white box the visitor signs with a finger or stylus.
 *
 * Driven by [signature] (state hoisting): a Clear or a form reset empties it. The stroke being
 * drawn is kept locally and reported through [onSignatureChange] only when the finger lifts,
 * as the full [Signature] in this pad's pixel coordinates.
 *
 * The pad sits inside a vertically scrolling form, so it consumes every pointer change from the
 * first down event on: drawing never scrolls the page.
 *
 * The inactivity timer sees every touch through `recordInteractions` in `VisitorApp` (it runs
 * on the Initial pass, before this pad consumes anything), and each committed stroke goes
 * through `updateInput`, so the "Are you still there?" countdown can't fire mid-signature.
 */
@Composable
fun SignaturePad(
    signature: Signature,
    onSignatureChange: (Signature) -> Unit,
    enabled: Boolean,
    isError: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = PAD_HEIGHT,
) {
    val currentSignature by rememberUpdatedState(signature)
    val currentOnChange by rememberUpdatedState(onSignatureChange)
    // The stroke under the finger right now, in pad pixels. Only the draw phase reads it.
    val inProgress = remember { mutableStateListOf<SignaturePoint>() }
    val isBlank by remember { derivedStateOf { inProgress.isEmpty() } }

    val shape = RoundedCornerShape(8.dp)
    val borderColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
    val description = stringResource(R.string.signature_pad_description)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(Color.White, shape)
            .border(width = if (isError) 3.dp else 2.dp, color = borderColor, shape = shape)
            .semantics { contentDescription = description }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val pointerId = down.id
                    val padWidth = size.width.toFloat()
                    val padHeight = size.height.toFloat()

                    fun add(position: Offset) {
                        val point = SignaturePoint(position.x.coerceIn(0f, padWidth), position.y.coerceIn(0f, padHeight))
                        val last = inProgress.lastOrNull()
                        if (last == null ||
                            abs(last.x - point.x) >= MIN_POINT_DISTANCE_PX ||
                            abs(last.y - point.y) >= MIN_POINT_DISTANCE_PX
                        ) {
                            inProgress.add(point)
                        }
                    }

                    inProgress.clear()
                    add(down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        // Consume everything (other fingers, a resting palm) so the form never scrolls.
                        event.changes.forEach { it.consume() }
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!change.pressed) break
                        add(change.position)
                    }

                    val stroke = inProgress.toList()
                    inProgress.clear()
                    if (stroke.isNotEmpty()) {
                        val existing = currentSignature
                        currentOnChange(
                            Signature(
                                width = padWidth,
                                height = padHeight,
                                strokes = existing.strokesScaledTo(padWidth, padHeight) + listOf(stroke),
                            ),
                        )
                    }
                }
            },
    ) {
        if (signature.strokes.isEmpty() && isBlank) {
            Text(
                stringResource(R.string.signature_hint),
                style = MaterialTheme.typography.titleMedium,
                color = HINT,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = BASELINE_INSET, bottom = height / 4 + 8.dp),
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            val baselineY = size.height * 0.75f
            val inset = BASELINE_INSET.toPx()
            drawLine(
                color = BASELINE,
                start = Offset(inset, baselineY),
                end = Offset(size.width - inset, baselineY),
                strokeWidth = 1.dp.toPx(),
            )

            // Committed strokes are stored in the size they were drawn at; fit them to today's size.
            val sx = if (signature.width > 0f) size.width / signature.width else 1f
            val sy = if (signature.height > 0f) size.height / signature.height else 1f
            signature.strokes.forEach { drawInk(it, sx, sy) }
            drawInk(inProgress, 1f, 1f)
        }
    }
}

private fun DrawScope.drawInk(points: List<SignaturePoint>, sx: Float, sy: Float) {
    if (points.isEmpty()) return
    val inkPx = INK_WIDTH.toPx()
    if (points.size == 1) {
        drawCircle(INK, radius = inkPx / 2f, center = Offset(points[0].x * sx, points[0].y * sy))
        return
    }
    val path = Path().apply {
        moveTo(points[0].x * sx, points[0].y * sy)
        for (i in 1 until points.size) lineTo(points[i].x * sx, points[i].y * sy)
    }
    drawPath(path, INK, style = Stroke(width = inkPx, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** The existing strokes in a [width] x [height] pad (only differs if the pad was resized). */
private fun Signature.strokesScaledTo(width: Float, height: Float): List<List<SignaturePoint>> {
    if (strokes.isEmpty() || this.width <= 0f || this.height <= 0f) return strokes
    if (this.width == width && this.height == height) return strokes
    val sx = width / this.width
    val sy = height / this.height
    return strokes.map { stroke -> stroke.map { SignaturePoint(it.x * sx, it.y * sy) } }
}
