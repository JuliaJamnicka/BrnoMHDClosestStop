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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import io.github.juliajamnicka.fcil.data.PlatformDto
import io.github.juliajamnicka.fcil.data.StopList
import io.github.juliajamnicka.fcil.data.StopListEntry

/** Nearby stops and the user's stop lists; picking a platform or a list pins the departures screen. */
@Composable
fun StopsScreen(model: AppViewModel, hasLocation: Boolean, onPicked: () -> Unit, contentPadding: PaddingValues) {
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
        editor = ListEditor(
            create = model::createList,
            add = model::addToList,
            remove = model::removeFromList,
            delete = model::deleteList,
        ),
        onPickList = { id ->
            model.pinList(id)
            onPicked()
        },
    ) { platform ->
        model.pin(platform)
        onPicked()
    }
}

/** Editing actions for stop lists; the widget's stop picker only picks. */
class ListEditor(
    val create: (name: String, first: StopListEntry?) -> Unit,
    val add: (listId: String, entry: StopListEntry) -> Unit,
    val remove: (listId: String, platform: String) -> Unit,
    val delete: (listId: String) -> Unit,
)

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
    editor: ListEditor? = null,
    onPickList: (String) -> Unit = {},
    onPick: (String?) -> Unit,
) {
    val highlight = MaterialTheme.colorScheme.secondaryContainer
    // a new list, optionally created from a platform's "+" menu with that platform in it
    var newList by remember { mutableStateOf<NewList?>(null) }

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

        if (lists.isNotEmpty() || editor != null) {
            item { SectionTitle(stringResource(R.string.lists_title)) }
        }
        items(lists, key = { "list-" + it.id }) { list ->
            ListCard(list, list.id == selectedList, highlight, editor, onPickList)
        }
        if (editor != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (lists.isEmpty()) {
                        Text(stringResource(R.string.lists_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                    }
                    OutlinedButton(onClick = { newList = NewList(null) }) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.lists_new))
                    }
                }
            }
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
                            Text(distanceLabel(stop.d), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                val entry = entryOf(stop, platform)
                                if (editor != null && entry != null) {
                                    AddToListButton(lists, entry, editor) { newList = NewList(entry) }
                                }
                            }
                        }
                        Box(Modifier.padding(bottom = 6.dp))
                    }
                }
            }
        }
    }

    newList?.let { pending ->
        NewListDialog(
            onDismiss = { newList = null },
            onCreate = { name ->
                editor?.create?.invoke(name, pending.first)
                newList = null
            },
        )
    }
}

private class NewList(val first: StopListEntry?)

private fun entryOf(stop: NearbyStop, platform: PlatformDto): StopListEntry? {
    val lat = platform.la ?: return null
    val lon = platform.lo ?: return null
    return StopListEntry(platform.id, stop.n, platform.dir, lat, lon)
}

@Composable
private fun ListCard(
    list: StopList,
    pinned: Boolean,
    highlight: Color,
    editor: ListEditor?,
    onPickList: (String) -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    BrandCard {
        Column(Modifier.background(if (pinned) highlight else MaterialTheme.colorScheme.surface)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = list.entries.isNotEmpty()) { onPickList(list.id) }
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_stop_list), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(list.name, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
                if (editor != null) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.lists_delete))
                    }
                }
            }
            if (list.entries.isEmpty()) {
                Text(
                    stringResource(R.string.lists_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                )
            }
            list.entries.forEach { e ->
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${e.stop} → ${e.dir}",
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                    )
                    if (editor != null) {
                        IconButton(onClick = { editor.remove(list.id, e.platform) }) {
                            Icon(
                                painterResource(R.drawable.ic_close),
                                stringResource(R.string.lists_remove_stop),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
    if (confirmDelete && editor != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.lists_delete_title, list.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    editor.delete(list.id)
                }) { Text(stringResource(R.string.lists_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

/** "+" on a platform: add it to one of the lists, or to a new one. */
@Composable
private fun AddToListButton(lists: List<StopList>, entry: StopListEntry, editor: ListEditor, onNewList: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_add), stringResource(R.string.lists_add), tint = MaterialTheme.colorScheme.primary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            lists.forEach { list ->
                val already = list.entries.any { it.platform == entry.platform }
                DropdownMenuItem(
                    text = { Text(list.name) },
                    enabled = !already,
                    onClick = {
                        open = false
                        editor.add(list.id, entry)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.lists_new)) },
                onClick = {
                    open = false
                    onNewList()
                },
            )
        }
    }
}

@Composable
private fun NewListDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lists_new)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = { Text(stringResource(R.string.lists_name)) },
                placeholder = { Text(stringResource(R.string.lists_name_example)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.lists_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

private const val REFRESH_MS = 60_000L
