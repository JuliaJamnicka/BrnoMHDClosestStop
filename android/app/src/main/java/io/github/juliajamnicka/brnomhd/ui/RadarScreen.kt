package io.github.juliajamnicka.brnomhd.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.juliajamnicka.brnomhd.R
import io.github.juliajamnicka.brnomhd.data.TransitRepository
import io.github.juliajamnicka.brnomhd.data.VehicleDto
import io.github.juliajamnicka.brnomhd.data.VehiclesResponse
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Live vehicles around the user, north up, as on the watch; the nearest ones are listed below. */
@Composable
fun RadarScreen(model: AppViewModel, hasLocation: Boolean, contentPadding: PaddingValues) {
    if (hasLocation) RefreshWhileVisible(REFRESH_MS) { model.refreshRadar() }
    val state = model.radar

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .widthIn(max = 640.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tab_radar), fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (hasLocation) {
                IconButton(onClick = model::refreshRadar, enabled = !state.loading) {
                    Icon(painterResource(R.drawable.ic_refresh), stringResource(R.string.widget_refresh))
                }
            }
        }
        if (!hasLocation) {
            Text(stringResource(R.string.location_needed))
            return@Column
        }
        Radar(state.data)
        Text(
            state.error ?: stringResource(R.string.radar_legend),
            color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        val vehicles = state.data?.v.orEmpty().sortedBy { distance(it) }
        if (state.data != null) {
            Section(stringResource(R.string.radar_nearest, vehicles.size)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    if (vehicles.isEmpty()) Text(stringResource(R.string.radar_empty), Modifier.padding(vertical = 12.dp))
                    vehicles.forEachIndexed { i, v ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            LineBadge(v.l, v.m)
                            Spacer(Modifier.width(12.dp))
                            Text(distanceLabel(distance(v)), Modifier.weight(1f), fontSize = 17.sp)
                            val delayMin = v.dl / 60
                            if (delayMin >= 1) {
                                Text(
                                    "+$delayMin min",
                                    color = if (delayMin >= 2) delayColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Radar(data: VehiclesResponse?) {
    val ring = MaterialTheme.colorScheme.surfaceVariant
    val stopColor = MaterialTheme.colorScheme.onSurfaceVariant
    val me = nowColor
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
    ) {
        val sizePx = with(LocalDensity.current) { maxWidth.toPx() }
        val centre = sizePx / 2
        // the outer ring is the search radius
        val scale = centre * 0.92f / RANGE_M
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(ring, radius = centre * 0.92f, center = Offset(centre, centre), style = Stroke(2.dp.toPx()))
            drawCircle(ring, radius = centre * 0.46f, center = Offset(centre, centre), style = Stroke(2.dp.toPx()))
            data?.s?.forEach { s ->
                drawCircle(stopColor, radius = 6.dp.toPx(), center = Offset(centre + s.dx * scale, centre - s.dy * scale), style = Stroke(2.5.dp.toPx()))
            }
            drawCircle(me, radius = 8.dp.toPx(), center = Offset(centre, centre))
        }
        Text("N", Modifier.align(Alignment.TopCenter), fontWeight = FontWeight.Bold)
        Text("${RANGE_M / 2} m", Modifier.centreAt(centre + centre * 0.46f * 0.71f, centre - centre * 0.46f * 0.71f), fontSize = 12.sp, color = stopColor)
        Text("$RANGE_M m", Modifier.centreAt(centre + centre * 0.92f * 0.71f, centre - centre * 0.92f * 0.71f), fontSize = 12.sp, color = stopColor)
        data?.s?.forEach { s ->
            Text(
                s.n,
                Modifier.centreAt(centre + s.dx * scale, centre - s.dy * scale + 18.dp.value * LocalDensity.current.density),
                fontSize = 11.sp,
                color = stopColor,
                maxLines = 1,
            )
        }
        data?.v?.filter { distance(it) <= RANGE_M }?.forEach { v ->
            Box(Modifier.centreAt(centre + v.dx * scale, centre - v.dy * scale)) {
                LineBadge(v.l, v.m, minWidth = 34.dp, fontSize = 13.sp)
            }
        }
    }
}

/** Places the element with its centre at (x, y) pixels of the parent. */
private fun Modifier.centreAt(x: Float, y: Float) = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(p.width, p.height) { p.place((x - p.width / 2f).roundToInt(), (y - p.height / 2f).roundToInt()) }
}

private fun distance(v: VehicleDto): Int = sqrt((v.dx * v.dx + v.dy * v.dy).toDouble()).roundToInt()

private const val RANGE_M = TransitRepository.RADAR_RADIUS_M
private const val REFRESH_MS = 15_000L
