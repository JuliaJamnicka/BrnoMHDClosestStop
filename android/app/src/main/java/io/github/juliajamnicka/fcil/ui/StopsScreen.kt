package io.github.juliajamnicka.fcil.ui

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.juliajamnicka.fcil.R
import io.github.juliajamnicka.fcil.data.NearbyStop
import io.github.juliajamnicka.fcil.data.StopList

/** Nearby stops and the user's stop lists; picking a platform or a list pins the departures screen. */
@Composable
fun StopsScreen(
    model: AppViewModel,
    hasLocation: Boolean,
    onPicked: () -> Unit,
    onNewList: () -> Unit,
    onEditList: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    if (hasLocation) RefreshWhileVisible(REFRESH_MS) { model.refreshStops() }
    val lists by model.stopLists.collectAsState(initial = emptyList())
    StopList(
        title = stringResource(R.string.tab_stops),
        stops = model.stops.data?.stops,
        error = if (hasLocation) model.stops.error else stringResource(R.string.location_needed),
        selected = model.config.pinnedPlatform,
        lists = lists,
        selectedList = model.config.pinnedList,
        contentPadding = contentPadding,
        onRefresh = if (hasLocation) model::refreshStops else null,
        onNewList = onNewList,
        onEditList = onEditList,
        onPickList = { id ->
            model.pinList(id)
            onPicked()
        },
    ) { platform ->
        model.pin(platform)
        onPicked()
    }
}

/**
 * "Nearest stop (automatic)", the user's stop lists, then stops ordered by distance with their
 * platforms. Used by the app and by the widget's stop picker. [selected] / [selectedList] is what
 * is pinned, if anything.
 */
@Composable
fun StopList(
    title: String,
    stops: List<NearbyStop>?,
    error: String?,
    selected: String?,
    lists: List<StopList> = emptyList(),
    selectedList: String? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onRefresh: (() -> Unit)? = null,
    /** The app passes these to edit lists; the widget's stop picker only picks. */
    onNewList: (() -> Unit)? = null,
    onEditList: ((String) -> Unit)? = null,
    onPickList: (String) -> Unit = {},
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
                        .background(if (selected == null && selectedList == null) highlight else MaterialTheme.colorScheme.surface)
                        .clickable { onPick(null) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_notification), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.widget_nearest_stop), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (lists.isNotEmpty() || onNewList != null) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SectionTitle(stringResource(R.string.lists_title)) }
                    if (onNewList != null) {
                        IconButton(onClick = onNewList) {
                            Icon(painterResource(R.drawable.ic_add), stringResource(R.string.lists_new), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
        if (lists.isEmpty() && onNewList != null) {
            item { Text(stringResource(R.string.lists_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp) }
        }
        items(lists, key = { "list-" + it.id }) { list ->
            ListCard(list, list.id == selectedList, highlight, onEditList, onPickList)
        }

        item { SectionTitle(stringResource(R.string.stops_nearby)) }
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
                            stop.d?.let { Text(distanceLabel(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        stop.p.forEachIndexed { i, platform ->
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(if (platform.id == selected) highlight else MaterialTheme.colorScheme.surface)
                                    .clickable { onPick(platform.id) }
                                    .padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                                    Text("→ ${platform.dir}", fontSize = 16.sp)
                                    Text(platform.l, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                                }
                            }
                        }
                        Box(Modifier.padding(bottom = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ListCard(
    list: StopList,
    pinned: Boolean,
    highlight: Color,
    onEditList: ((String) -> Unit)?,
    onPickList: (String) -> Unit,
) {
    BrandCard {
        Row(
            Modifier.background(if (pinned) highlight else MaterialTheme.colorScheme.surface),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .clickable(enabled = list.entries.isNotEmpty()) { onPickList(list.id) }
                    .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_stop_list), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(list.name, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        list.entries.map { it.stop }.distinct().joinToString(" · "),
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (onEditList != null) {
                IconButton(onClick = { onEditList(list.id) }, modifier = Modifier.padding(end = 4.dp)) {
                    Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.lists_edit))
                }
            }
        }
    }
}

private const val REFRESH_MS = 60_000L
