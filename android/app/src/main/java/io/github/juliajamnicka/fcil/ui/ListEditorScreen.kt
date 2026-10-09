package io.github.juliajamnicka.fcil.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
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
import kotlinx.coroutines.delay

/**
 * Creates or edits a stop list (docs/SPEC.md 6.2): a name, the chosen platforms, and a search over all
 * stops of the network (nearby stops while the search is empty). Nothing is stored until "Save".
 */
@Composable
fun ListEditorScreen(model: AppViewModel, list: StopList?, nearby: List<NearbyStop>?, onClose: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(list?.name ?: "") }
    var query by rememberSaveable { mutableStateOf("") }
    val chosen = remember { mutableStateListOf<StopListEntry>().apply { addAll(list?.entries.orEmpty()) } }
    var confirmDelete by remember { mutableStateOf(false) }

    BackHandler(onBack = onClose)
    LaunchedEffect(query) {
        delay(SEARCH_DEBOUNCE_MS)
        model.searchStops(query)
    }

    val searching = query.trim().length >= 2
    val results = if (searching) model.stopSearch.data else nearby
    val canSave = name.isNotBlank() && chosen.isNotEmpty()
    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
    )

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back))
            }
            Text(
                stringResource(if (list == null) R.string.lists_new else R.string.lists_edit),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SectionTitle(stringResource(R.string.lists_name)) }
            item {
                TextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    placeholder = { Text(stringResource(R.string.lists_name_example)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item { Box(Modifier.padding(top = 8.dp)) { SectionTitle(stringResource(R.string.lists_in, chosen.size)) } }
            item {
                BrandCard {
                    Column {
                        if (chosen.isEmpty()) {
                            Text(
                                stringResource(R.string.lists_none_yet),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                        chosen.forEachIndexed { i, e ->
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                                    Text(
                                        "${e.stop} → ${e.dir}",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (e.lines.isNotEmpty()) {
                                        Text(e.lines, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                IconButton(onClick = { chosen.removeAt(i) }) {
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

            item {
                TextField(
                    value = query,
                    onValueChange = { query = it.take(40) },
                    placeholder = { Text(stringResource(R.string.lists_search)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.clear), modifier = Modifier.size(18.dp))
                            }
                        }
                    } else {
                        null
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            item { SectionTitle(stringResource(if (searching) R.string.lists_results else R.string.stops_nearby)) }
            when {
                searching && model.stopSearch.error != null -> item { Text(model.stopSearch.error!!, color = MaterialTheme.colorScheme.error) }
                results == null -> item { Text(stringResource(R.string.preview_loading)) }
                results.isEmpty() -> item { Text(stringResource(R.string.lists_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                else -> items(results, key = { "r-" + it.id }) { stop ->
                    ResultCard(stop, chosen)
                }
            }
            if (list != null) {
                item {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(stringResource(R.string.lists_delete), color = TramRed, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Button(
                onClick = {
                    model.saveList(list?.id, name, chosen.toList())
                    onClose()
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(
                    stringResource(R.string.lists_save) + " · " + pluralStringResource(R.plurals.stops_count, chosen.size, chosen.size),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    if (confirmDelete && list != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.lists_delete_title, list.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    model.deleteList(list.id)
                    onClose()
                }) { Text(stringResource(R.string.lists_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

/** A stop with its platforms; + adds a platform to the list, the filled check removes it again. */
@Composable
private fun ResultCard(stop: NearbyStop, chosen: MutableList<StopListEntry>) {
    BrandCard {
        Column {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stop.n, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                stop.d?.let { Text(distanceLabel(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            stop.p.forEachIndexed { i, platform ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                val entry = entryOf(stop, platform)
                val on = chosen.any { it.platform == platform.id }
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text("→ ${platform.dir}", fontSize = 16.sp)
                        Text(platform.l, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                    if (entry != null) {
                        val label = "${stop.n} → ${platform.dir}"
                        IconButton(onClick = { if (on) chosen.removeAll { it.platform == platform.id } else chosen.add(entry) }) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .then(
                                        if (on) Modifier.background(MaterialTheme.colorScheme.primary)
                                        else Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painterResource(if (on) R.drawable.ic_check else R.drawable.ic_add),
                                    stringResource(if (on) R.string.lists_added else R.string.lists_add, label),
                                    tint = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

private fun entryOf(stop: NearbyStop, platform: PlatformDto): StopListEntry? {
    val lat = platform.la ?: return null
    val lon = platform.lo ?: return null
    return StopListEntry(platform.id, stop.n, platform.dir, lat, lon, platform.l)
}

private const val SEARCH_DEBOUNCE_MS = 300L
