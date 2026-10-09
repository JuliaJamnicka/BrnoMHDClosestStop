package io.github.juliajamnicka.fcil.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.juliajamnicka.fcil.R
import io.github.juliajamnicka.fcil.data.AppSettings
import io.github.juliajamnicka.fcil.data.Language
import io.github.juliajamnicka.fcil.wear.WatchStatus
import kotlinx.coroutines.flow.Flow
import java.text.DateFormat
import java.util.Date

class SettingsActions(
    val requestLocation: () -> Unit,
    val requestBackgroundLocation: () -> Unit,
    val connectWatch: () -> Unit,
    val disconnectWatch: () -> Unit,
    val setLanguage: (Language) -> Unit,
    val setDepartureCount: (Int) -> Unit,
)

@Composable
fun SettingsScreen(
    settingsFlow: Flow<AppSettings>,
    watchEnabled: Boolean,
    hasLocation: Boolean,
    hasBackgroundLocation: Boolean,
    setupMessage: String?,
    actions: SettingsActions,
    contentPadding: PaddingValues,
) {
    val settings by settingsFlow.collectAsState(initial = null)
    val status by WatchStatus.state.collectAsState()

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Text(stringResource(R.string.settings_title), fontSize = 30.sp, fontWeight = FontWeight.Bold)

            WatchCard(status, watchEnabled, hasLocation, setupMessage, actions)

            if (hasLocation && !hasBackgroundLocation) {
                Section(stringResource(R.string.section_location)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.background_location_hint), style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = actions.requestBackgroundLocation) {
                            Text(stringResource(R.string.background_location_button))
                        }
                    }
                }
            }


            settings?.let { s ->
                Section(stringResource(R.string.section_language)) {
                    Column(Modifier.selectableGroup()) {
                        val options = listOf(
                            Language.SYSTEM to stringResource(R.string.language_system),
                            Language.CZECH to "Čeština",
                            Language.ENGLISH to "English",
                        )
                        options.forEachIndexed { i, (language, label) ->
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .selectable(s.language == language, role = Role.RadioButton) { actions.setLanguage(language) }
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(label, Modifier.weight(1f), fontSize = 18.sp)
                                RadioButton(selected = s.language == language, onClick = null)
                            }
                        }
                    }
                }

                Section(stringResource(R.string.section_watch)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.departure_count), Modifier.weight(1f), fontSize = 18.sp)
                        SingleChoiceSegmentedButtonRow {
                            listOf(3, 4).forEachIndexed { i, n ->
                                SegmentedButton(
                                    selected = s.departureCount == n,
                                    onClick = { actions.setDepartureCount(n) },
                                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                                    colors = SegmentedButtonDefaults.colors(
                                        activeContainerColor = MaterialTheme.colorScheme.primary,
                                        activeContentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                ) { Text(n.toString()) }
                            }
                        }
                    }
                }

            }

            Text(
                stringResource(R.string.attribution),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WatchCard(
    status: WatchStatus.State,
    watchEnabled: Boolean,
    hasLocation: Boolean,
    setupMessage: String?,
    actions: SettingsActions,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(12.dp)
                        .background(
                            if (status.running && status.connected) MaterialTheme.colorScheme.primary else Color(0xFF8E8E93),
                            RoundedCornerShape(6.dp),
                        ),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(status.deviceName ?: "Huawei Watch GT", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        when {
                            !status.running -> stringResource(R.string.status_off)
                            status.connected -> stringResource(R.string.status_connected)
                            else -> stringResource(R.string.status_waiting)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            status.lastRequestAt?.let {
                Text(
                    stringResource(R.string.status_last_request, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it))),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            (setupMessage ?: status.lastError)?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            when {
                !hasLocation -> Button(onClick = actions.requestLocation) { Text(stringResource(R.string.grant_location)) }
                !watchEnabled || !status.running -> Button(onClick = actions.connectWatch) { Text(stringResource(R.string.connect_watch)) }
                else -> TextButton(onClick = actions.disconnectWatch) { Text(stringResource(R.string.disconnect_watch)) }
            }
        }
    }
}



