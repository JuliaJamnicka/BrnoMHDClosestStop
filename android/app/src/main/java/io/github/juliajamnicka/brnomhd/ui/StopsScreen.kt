package io.github.juliajamnicka.brnomhd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.juliajamnicka.brnomhd.R
import io.github.juliajamnicka.brnomhd.data.NearbyStop

/** Nearby stops in the app; picking a platform pins the departures screen to it. */
@Composable
fun StopsScreen(model: AppViewModel, hasLocation: Boolean, onPicked: () -> Unit, contentPadding: PaddingValues) {
    if (hasLocation) RefreshWhileVisible(REFRESH_MS) { model.refreshStops() }
    StopList(
        title = stringResource(R.string.tab_stops),
        stops = model.stops.data?.stops,
        error = if (hasLocation) model.stops.error else stringResource(R.string.location_needed),
        selected = model.config.pinnedPlatform,
        contentPadding = contentPadding,
        onRefresh = if (hasLocation) model::refreshStops else null,
    ) { platform ->
        model.pin(platform)
        onPicked()
    }
}

/**
 * Stops ordered by distance, each with its platforms, plus "Nearest stop (automatic)" first.
 * Used by the app and by the widget's stop picker. [selected] is the pinned platform, if any.
 */
@Composable
fun StopList(
    title: String,
    stops: List<NearbyStop>?,
    error: String?,
    selected: String?,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onRefresh: (() -> Unit)? = null,
    onPick: (String?) -> Unit,
) {
    val highlight = MaterialTheme.colorScheme.secondaryContainer
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (onRefresh != null) {
                    IconButton(onClick = onRefresh) {
                        Icon(painterResource(R.drawable.ic_refresh), stringResource(R.string.widget_refresh))
                    }
                }
            }
        }
        item {
            BrandCard {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (selected == null) highlight else MaterialTheme.colorScheme.surface)
                        .clickable { onPick(null) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_notification),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.widget_nearest_stop), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        when {
            error != null && stops == null -> item { Text(error, color = MaterialTheme.colorScheme.error) }
            stops == null -> item { Text(stringResource(R.string.preview_loading)) }
            else -> items(stops, key = { it.id }) { stop ->
                BrandCard {
                    Column {
                        Row(
                            Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stop.n, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(distanceLabel(stop.d), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        stop.p.forEachIndexed { i, platform ->
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .background(if (platform.id == selected) highlight else MaterialTheme.colorScheme.surface)
                                    .clickable { onPick(platform.id) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Text("→ ${platform.dir}", fontSize = 16.sp)
                                Text(platform.l, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                            }
                        }
                        Box(Modifier.padding(bottom = 6.dp))
                    }
                }
            }
        }
    }
}

private const val REFRESH_MS = 60_000L
