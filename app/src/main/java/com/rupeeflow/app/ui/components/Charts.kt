package com.rupeeflow.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rupeeflow.app.ui.theme.Rf

data class Slice(val label: String, val value: Long, val color: Color)

/** Donut chart with the total (or the tapped slice) in the middle. */
@Composable
fun DonutChart(
    slices: List<Slice>,
    centerTitle: String,
    centerValue: String,
    modifier: Modifier = Modifier,
    sizeDp: Int = 200,
    format: (Long) -> String,
) {
    val total = slices.sumOf { it.value }.coerceAtLeast(1)
    var selected by remember(slices) { mutableIntStateOf(-1) }
    val sweep = remember(slices) { Animatable(0f) }
    LaunchedEffect(slices) { sweep.snapTo(0f); sweep.animateTo(1f, tween(700)) }
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier.size(sizeDp.dp).semantics {
            contentDescription = "Spending breakdown: " + slices.joinToString { "${it.label} ${format(it.value)}" }
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier.size(sizeDp.dp).pointerInput(slices) {
                detectTapGestures { pos ->
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val dx = pos.x - c.x; val dy = pos.y - c.y
                    var ang = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                    if (ang < 0) ang += 360f
                    var acc = 0f
                    var hit = -1
                    slices.forEachIndexed { i, s ->
                        val sw = s.value.toFloat() / total * 360f
                        if (ang >= acc && ang < acc + sw) hit = i
                        acc += sw
                    }
                    selected = if (hit == selected) -1 else hit
                }
            },
        ) {
            val stroke = size.minDimension * 0.14f
            val inset = stroke / 2 + 4
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            var start = -90f
            slices.forEachIndexed { i, s ->
                val sw = s.value.toFloat() / total * 360f * sweep.value
                val gap = if (slices.size > 1) 1.2f else 0f
                drawArc(
                    color = if (selected == -1 || selected == i) s.color else s.color.copy(alpha = 0.3f),
                    startAngle = start + gap / 2, sweepAngle = (sw - gap).coerceAtLeast(0.1f), useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize,
                    style = Stroke(if (selected == i) stroke * 1.18f else stroke, cap = StrokeCap.Butt),
                )
                start += sw
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val s = slices.getOrNull(selected)
            Text(s?.label ?: centerTitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.padding(horizontal = 24.dp))
            Text(s?.let { format(it.value) } ?: centerValue, style = MaterialTheme.typography.titleLarge)
            if (s != null) Text("${(s.value * 100 / total)}%", style = MaterialTheme.typography.labelMedium, color = Rf.colors.subtle)
        }
    }
}

/**
 * Bar chart. Tap a bar to see its exact value. [highlightLast] tints the newest bar.
 * An optional [limitLine] draws a dashed-looking target (e.g. daily allowance).
 */
@Composable
fun BarChart(
    values: List<Long>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    secondary: List<Long>? = null,
    secondaryColor: Color = Rf.colors.positive,
    highlightLast: Boolean = false,
    limitLine: Long? = null,
    heightDp: Int = 160,
    showEveryNthLabel: Int = 1,
    format: (Long) -> String,
) {
    var selected by remember(values) { mutableIntStateOf(-1) }
    val maxV = (values + (secondary ?: emptyList()) + listOfNotNull(limitLine)).maxOrNull()?.coerceAtLeast(1) ?: 1
    val grow = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { grow.snapTo(0f); grow.animateTo(1f, tween(600)) }
    val faded = color.copy(alpha = 0.35f)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val warn = Rf.colors.warning
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(22.dp), contentAlignment = Alignment.CenterStart) {
            val i = selected
            if (i in values.indices) {
                val t = labels.getOrElse(i) { "" } + ": " + format(values[i]) + (secondary?.getOrNull(i)?.let { " · in " + format(it) } ?: "")
                Text(t, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
            } else {
                Text("Tap a bar for details", style = MaterialTheme.typography.labelMedium, color = Rf.colors.subtle)
            }
        }
        Canvas(
            Modifier.fillMaxWidth().height(heightDp.dp)
                .semantics { contentDescription = labels.zip(values).joinToString { (l, v) -> "$l ${format(v)}" } }
                .pointerInput(values) {
                    detectTapGestures { pos ->
                        if (values.isEmpty()) return@detectTapGestures
                        val slot = size.width.toFloat() / values.size
                        val i = (pos.x / slot).toInt().coerceIn(0, values.size - 1)
                        selected = if (selected == i) -1 else i
                    }
                },
        ) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val groups = if (secondary != null) 2 else 1
            val barW = (slot * 0.62f / groups).coerceAtMost(28.dp.toPx())
            for (k in 1..3) {
                val y = size.height * k / 4f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            values.forEachIndexed { i, v ->
                val h = size.height * (v.toFloat() / maxV) * grow.value
                val cx = slot * i + slot / 2
                val left = if (groups == 2) cx - barW else cx - barW / 2
                val isSel = selected == i
                val c = when {
                    isSel -> color
                    selected != -1 -> faded
                    highlightLast && i == values.lastIndex -> color
                    highlightLast -> color.copy(alpha = 0.55f)
                    else -> color
                }
                if (v > 0) drawRoundRect(c, Offset(left, size.height - h), Size(barW, h), CornerRadius(barW / 3, barW / 3))
                secondary?.getOrNull(i)?.let { sv ->
                    val sh = size.height * (sv.toFloat() / maxV) * grow.value
                    if (sv > 0) drawRoundRect(if (selected == -1 || isSel) secondaryColor else secondaryColor.copy(alpha = 0.35f),
                        Offset(cx, size.height - sh), Size(barW, sh), CornerRadius(barW / 3, barW / 3))
                }
            }
            limitLine?.let { l ->
                val y = size.height - size.height * (l.toFloat() / maxV)
                var x = 0f
                while (x < size.width) {
                    drawLine(warn, Offset(x, y), Offset((x + 10f).coerceAtMost(size.width), y), strokeWidth = 3f)
                    x += 18f
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, l ->
                Text(
                    if (i % showEveryNthLabel == 0 || i == labels.lastIndex) l else "",
                    style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle,
                    textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Two-part horizontal split bar, e.g. essential vs non-essential. */
@Composable
fun SplitBar(a: Long, b: Long, colorA: Color, colorB: Color, modifier: Modifier = Modifier) {
    val total = (a + b).coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val r = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        val wa = size.width * a / total
        if (a > 0) drawRoundRect(colorA, size = Size(wa, size.height), cornerRadius = r)
        if (b > 0) drawRoundRect(colorB, topLeft = Offset(wa, 0f), size = Size(size.width - wa, size.height), cornerRadius = r)
    }
}

/** Circular progress ring used for goals and the health score. */
@Composable
fun Ring(fraction: Float, color: Color, modifier: Modifier = Modifier, sizeDp: Int = 64, stroke: Float = 8f, content: @Composable () -> Unit = {}) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val anim = remember { Animatable(0f) }
    LaunchedEffect(fraction) { anim.animateTo(fraction.coerceIn(0f, 1f), tween(700)) }
    Box(modifier.size(sizeDp.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(sizeDp.dp)) {
            val s = stroke.dp.toPx()
            val inset = s / 2
            val arc = Size(size.width - s, size.height - s)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(s))
            drawArc(color, -90f, 360f * anim.value, false, Offset(inset, inset), arc, style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}
