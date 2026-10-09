package io.github.juliajamnicka.brnomhd.ui

import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import io.github.juliajamnicka.brnomhd.MhdApp
import io.github.juliajamnicka.brnomhd.R
import io.github.juliajamnicka.brnomhd.data.NearbyStop
import io.github.juliajamnicka.brnomhd.widget.WidgetUpdater
import kotlinx.coroutines.launch

/** Opened from the widget: nearby stops and their platforms; picking one pins the widget to it. */
class StopPickerActivity : AppCompatActivity() {
    private var stops by mutableStateOf<List<NearbyStop>?>(null)
    private var error by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appWidgetId = intent.getIntExtra(EXTRA_APP_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        lifecycleScope.launch {
            try {
                stops = (application as MhdApp).graph.repository.nearby().stops
            } catch (e: Exception) {
                error = getString(R.string.widget_stops_failed)
            }
        }
        setContent {
            MhdTheme {
                PickerScreen(stops, error) { platform ->
                    lifecycleScope.launch {
                        WidgetUpdater.pin(applicationContext, appWidgetId, platform)
                    }
                    finish()
                }
            }
        }
    }

    companion object {
        const val EXTRA_APP_WIDGET_ID = "app_widget_id"
    }
}

@Composable
private fun PickerScreen(stops: List<NearbyStop>?, error: String?, onPick: (String?) -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            Modifier.safeDrawingPadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.widget_pick_stop),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
                )
            }
            item {
                PickerCard {
                    Text(
                        stringResource(R.string.widget_nearest_stop),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(null) }.padding(16.dp),
                    )
                }
            }
            when {
                error != null -> item { Text(error, color = MaterialTheme.colorScheme.error) }
                stops == null -> item { Text(stringResource(R.string.preview_loading)) }
                else -> items(stops, key = { it.id }) { stop ->
                    PickerCard {
                        Column {
                            Text(
                                "${stop.n} · ${stop.d} m",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                            )
                            stop.p.forEachIndexed { i, platform ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                                Column(
                                    Modifier.fillMaxWidth().clickable { onPick(platform.id) }.padding(horizontal = 16.dp, vertical = 10.dp),
                                ) {
                                    Text("→ ${platform.dir}", fontSize = 16.sp)
                                    Text(platform.l, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
            item { Box(Modifier.padding(bottom = 24.dp)) }
        }
    }
}

@Composable
private fun PickerCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) { content() }
}
