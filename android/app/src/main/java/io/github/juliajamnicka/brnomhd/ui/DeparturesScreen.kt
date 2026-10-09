package io.github.juliajamnicka.brnomhd.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.juliajamnicka.brnomhd.R
import io.github.juliajamnicka.brnomhd.data.DeparturesResponse
import java.text.DateFormat
import java.util.Date

/** The app's main screen: the same departures as the watch and the widget, with more rows. */
@Composable
fun DeparturesScreen(
    model: AppViewModel,
    hasLocation: Boolean,
    requestLocation: () -> Unit,
    contentPadding: PaddingValues,
) {
    if (hasLocation) RefreshWhileVisible(REFRESH_MS) { model.refreshDepartures() }
    val state = model.departures
    val pinned = model.config.pinnedPlatform != null || model.config.pinnedList != null

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .widthIn(max = 640.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(R.drawable.ic_wordmark),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.height(36.dp),
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = model::refreshDepartures, enabled = hasLocation && !state.loading) {
                Icon(painterResource(R.drawable.ic_refresh), stringResource(R.string.widget_refresh))
            }
        }

        if (!hasLocation) {
            BrandCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.location_needed), style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = requestLocation) { Text(stringResource(R.string.grant_location)) }
                }
            }
            return@Column
        }

        val r = state.data
        if (r == null) {
            BrandCard {
                Text(
                    state.error ?: stringResource(R.string.preview_loading),
                    color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(16.dp),
                )
            }
            return@Column
        }

        StopHeader(r, pinned, model)

        if (r.pl.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                r.pl.forEach { p ->
                    FilterChip(
                        selected = p.id == r.p,
                        onClick = { if (p.id != r.p) model.pin(p.id) },
                        label = { Text("→ ${p.dir}", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
        }

        BrandCard {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                if (r.dep.isEmpty()) {
                    Text(stringResource(R.string.no_departures), Modifier.padding(vertical = 12.dp))
                }
                r.dep.forEachIndexed { i, d ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    DepartureRow(d, r.t)
                }
            }
        }

        Text(
            when {
                state.loading -> stringResource(R.string.preview_loading)
                state.error != null -> state.error
                else -> state.updatedAt?.let {
                    stringResource(R.string.widget_updated, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)))
                } ?: ""
            },
            color = if (state.error != null && !state.loading) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(stringResource(R.string.preview_legend), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.attribution), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StopHeader(r: DeparturesResponse, pinned: Boolean, model: AppViewModel) {
    BrandCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (r.listId != null) {
                            Icon(
                                painterResource(R.drawable.ic_stop_list),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        if (pinned) {
                            Icon(
                                painterResource(R.drawable.ic_pin),
                                stringResource(R.string.widget_pinned),
                                tint = delayColor,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(r.stop, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        // a stop list's board has no single direction; its rows name their stops
                        if (r.listId != null) stringResource(R.string.lists_board)
                        else "→ ${r.dir}" + (r.d?.let { " · ${distanceLabel(it)}" } ?: ""),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp,
                    )
                }
                if (r.opp != null) {
                    FilledTonalIconButton(onClick = model::reverse, modifier = Modifier.size(52.dp)) {
                        Icon(painterResource(R.drawable.ic_reverse), stringResource(R.string.widget_reverse))
                    }
                }
            }
            if (pinned) {
                TextButton(onClick = { model.pin(null) }, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.widget_nearest_stop))
                }
            }
        }
    }
}

private const val REFRESH_MS = 20_000L
