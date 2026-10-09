package io.github.juliajamnicka.fcil.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.juliajamnicka.fcil.R

enum class Tab(@StringRes val label: Int, @DrawableRes val icon: Int) {
    DEPARTURES(R.string.tab_departures, R.drawable.ic_notification),
    STOPS(R.string.tab_stops, R.drawable.ic_list),
    RADAR(R.string.tab_radar, R.drawable.ic_radar),
    SETTINGS(R.string.settings_title, R.drawable.ic_settings),
}

/** The app: departures, stops and radar (the same as on the watch and the widget), plus settings. */
@Composable
fun AppScreen(
    hasLocation: Boolean,
    requestLocation: () -> Unit,
    settings: @Composable (PaddingValues) -> Unit,
    model: AppViewModel = viewModel(),
) {
    var tab by rememberSaveable { mutableStateOf(Tab.DEPARTURES) }
    // the stop-list editor covers the whole screen: null closed, "" a new list, else the list's id
    var editorFor by rememberSaveable { mutableStateOf<String?>(null) }
    editorFor?.let { id ->
        val lists by model.stopLists.collectAsState(initial = null)
        // wait for the stored lists before opening an existing one
        if (id.isEmpty() || lists != null) {
            ListEditorScreen(
                model = model,
                list = lists?.firstOrNull { it.id == id },
                nearby = model.stops.data?.stops,
                onClose = { editorFor = null },
            )
        }
        return
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(painterResource(t.icon), contentDescription = null) },
                        label = { Text(stringResource(t.label)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        when (tab) {
            Tab.DEPARTURES -> DeparturesScreen(model, hasLocation, requestLocation, padding)
            Tab.STOPS -> StopsScreen(
                model,
                hasLocation,
                onPicked = { tab = Tab.DEPARTURES },
                onNewList = { editorFor = "" },
                onEditList = { editorFor = it },
                contentPadding = padding,
            )
            Tab.RADAR -> RadarScreen(model, hasLocation, padding)
            Tab.SETTINGS -> settings(padding)
        }
    }
}
