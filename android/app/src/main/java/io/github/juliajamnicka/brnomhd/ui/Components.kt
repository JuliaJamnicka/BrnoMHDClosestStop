package io.github.juliajamnicka.brnomhd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.juliajamnicka.brnomhd.R
import io.github.juliajamnicka.brnomhd.data.DepartureDto
import kotlinx.coroutines.delay
import java.util.Locale

/** Runs [action] now and then every [periodMs] while the screen is visible. */
@Composable
fun RefreshWhileVisible(periodMs: Long, action: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                action()
                delay(periodMs)
            }
        }
    }
}

/** Line badge: tram rounded, bus pill, train square (as on the watch). */
@Composable
fun LineBadge(line: String, mode: String, minWidth: Dp = 44.dp, fontSize: TextUnit = 16.sp) {
    Box(
        Modifier
            .widthIn(min = minWidth)
            .background(modeColor(mode), RoundedCornerShape(if (mode == "T") 6.dp else if (mode == "V") 2.dp else 12.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) { Text(line, color = Color.White, fontWeight = FontWeight.Bold, fontSize = fontSize, maxLines = 1) }
}

/**
 * One departure: badge, headsign, delay, live dot and minutes ("now" in the háček red).
 * On a stop list's board the row's stop is named under the headsign.
 */
@Composable
fun DepartureRow(d: DepartureDto, now: Long) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        LineBadge(d.l, d.m)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(d.h, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (d.sn != null) {
                Text(d.sn, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val delayMin = d.dl / 60
        if (delayMin >= 1) {
            Text(
                "+$delayMin min",
                color = if (delayMin >= 2) delayColor else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
        }
        if (d.lv == 1) {
            Box(Modifier.padding(end = 6.dp).size(8.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)))
        }
        val minutes = ((d.e - now) / 60).coerceAtLeast(0)
        Text(
            if (d.e - now < 30) stringResource(R.string.now) else "$minutes min",
            color = when {
                d.e - now < 30 -> nowColor
                delayMin >= 2 -> delayColor
                else -> MaterialTheme.colorScheme.onSurface
            },
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.widthIn(min = 56.dp),
            textAlign = TextAlign.End,
        )
    }
}

/** Direction of travel; [heading] in degrees, 0 north. Nothing when the heading is unknown (-1). */
@Composable
fun HeadingArrow(heading: Int, size: Dp = 16.dp, tint: Color = MaterialTheme.colorScheme.onSurface) {
    if (heading < 0) return
    Icon(
        painterResource(R.drawable.ic_arrow),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(size).rotate(heading.toFloat()),
    )
}

@Composable
fun BrandCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) { content() }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        title.uppercase(),
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title)
        BrandCard { content() }
    }
}

/** "85 m" / "1,2 km" */
fun distanceLabel(metres: Int): String =
    if (metres < 1000) "$metres m" else String.format(Locale.ROOT, "%.1f km", metres / 1000.0).replace('.', ',')
