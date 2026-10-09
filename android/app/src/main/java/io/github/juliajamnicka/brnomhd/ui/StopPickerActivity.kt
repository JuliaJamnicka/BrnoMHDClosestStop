package io.github.juliajamnicka.brnomhd.ui

import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
                    StopList(stringResource(R.string.widget_pick_stop), stops, error, selected = null) { platform ->
                        lifecycleScope.launch {
                            WidgetUpdater.pin(applicationContext, appWidgetId, platform)
                        }
                        finish()
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_APP_WIDGET_ID = "app_widget_id"
    }
}
