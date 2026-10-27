package io.github.juliajamnicka.fcil.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.juliajamnicka.fcil.R
import io.github.juliajamnicka.fcil.data.DepartureDto
import io.github.juliajamnicka.fcil.data.DeparturesResponse
import io.github.juliajamnicka.fcil.ui.MainActivity
import io.github.juliajamnicka.fcil.ui.StopPickerActivity
import io.github.juliajamnicka.fcil.ui.departureTimeLabel
import java.text.DateFormat
import java.util.Date

// Colours come from the widget's style (WidgetStyle, default: Fčil night blue).

/** A failed refresh shows its error only once the data shown is this old. */
private const val ERROR_AFTER_MS = 30 * 60_000L
private fun provider(argb: Int) = ColorProvider(Color(argb))

class DeparturesWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val pickerIntent = Intent(context, StopPickerActivity::class.java)
            .putExtra(StopPickerActivity.EXTRA_APP_WIDGET_ID, appWidgetId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val appIntent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        provideContent {
            val prefs = currentState<Preferences>()
            Content(
                context = context,
                palette = WidgetColors.palette(WidgetState.style(prefs), systemColors(context)),
                response = WidgetState.response(prefs),
                pinned = prefs[WidgetState.pinnedPlatform] != null || prefs[WidgetState.pinnedList] != null,
                loading = prefs[WidgetState.loading] == true,
                updatedAt = prefs[WidgetState.updatedAt],
                error = prefs[WidgetState.error]?.let { runCatching { WidgetError.valueOf(it) }.getOrNull() },
                clock = prefs[WidgetState.clockTimes] == true,
                pickerIntent = pickerIntent,
                appIntent = appIntent,
            )
        }
    }
}

@Composable
private fun Content(
    context: Context,
    palette: WidgetPalette,
    response: DeparturesResponse?,
    pinned: Boolean,
    loading: Boolean,
    updatedAt: Long?,
    error: WidgetError?,
    clock: Boolean,
    pickerIntent: Intent,
    appIntent: Intent,
) {
    // read on every composition: WidgetClock recomposes the widget once a minute to recount minutes
    val now = System.currentTimeMillis() / 1000
    // the nearest stop is far away (abroad): no stop, just say so
    val out = error == WidgetError.OUT_OF_RANGE
    Column(
        GlanceModifier
            .fillMaxSize()
            .background(Color(palette.background))
            .cornerRadius(20.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(pickerIntent))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (pinned) {
                        Image(
                            ImageProvider(R.drawable.ic_pin),
                            context.getString(R.string.widget_pinned),
                            GlanceModifier.size(14.dp),
                            colorFilter = ColorFilter.tint(provider(palette.warning)),
                        )
                        Spacer(GlanceModifier.width(4.dp))
                    }
                    Text(
                        if (out) context.getString(R.string.out_of_range_title) else response?.stop ?: context.getString(R.string.widget_title),
                        style = TextStyle(color = provider(palette.text), fontSize = 17.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                }
                // a stop list's board has no single direction; its rows name their stops
                val subtitle = response?.takeIf { it.listId == null }?.let { r -> "→ ${r.dir}" + (r.d?.let { " · $it m" } ?: "") }
                if (subtitle != null) Text(subtitle, style = TextStyle(color = provider(palette.secondary), fontSize = 13.sp), maxLines = 1)
            }
        }
        Spacer(GlanceModifier.height(4.dp))

        // takes the space left above the buttons; rows that do not fit scroll here, the title and the buttons stay
        Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
            // between data loads, departures that have left drop off at the next minute redraw
            val upcoming = response?.dep?.filter { it.e >= now - 60 }
            when {
                out -> Message(palette, context.getString(R.string.out_of_range_widget))
                response == null && loading -> Message(palette, context.getString(R.string.preview_loading))
                response == null -> Message(palette, errorText(context, error) ?: context.getString(R.string.widget_tap_refresh))
                upcoming.isNullOrEmpty() -> Message(palette, context.getString(R.string.no_departures))
                else -> LazyColumn(GlanceModifier.fillMaxSize()) {
                    items(upcoming) { DepartureRow(context, palette, it, now, clock) }
                }
            }
        }

        // A failed refresh keeps the last data and stays quiet (it is retried once online); the
        // error shows only when there is nothing to show or what is shown is old.
        val showError = error != null && !out && !loading &&
            (response == null || updatedAt == null || System.currentTimeMillis() - updatedAt > ERROR_AFTER_MS)
        val footer = when {
            loading -> context.getString(R.string.preview_loading)
            showError && response != null -> errorText(context, error)
            updatedAt != null -> context.getString(R.string.widget_updated, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(updatedAt)))
            else -> null
        }
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                footer ?: "",
                style = TextStyle(color = provider(if (showError) palette.warning else palette.muted), fontSize = 11.sp),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            // a stop list's board has no opposite direction, and outside the area there is no stop
            if (response?.listId == null && !out) {
                IconButton(palette, R.drawable.ic_reverse, context.getString(R.string.widget_reverse), actionRunCallback<ReverseAction>())
                Spacer(GlanceModifier.width(6.dp))
            }
            IconButton(palette, R.drawable.ic_list, context.getString(R.string.widget_pick_stop), actionStartActivity(pickerIntent))
            Spacer(GlanceModifier.width(6.dp))
            IconButton(palette, R.drawable.ic_notification, context.getString(R.string.widget_open_app), actionStartActivity(appIntent))
            Spacer(GlanceModifier.width(6.dp))
            IconButton(palette, R.drawable.ic_refresh, context.getString(R.string.widget_refresh), actionRunCallback<RefreshAction>())
        }
    }
}

@Composable
private fun DepartureRow(context: Context, palette: WidgetPalette, d: DepartureDto, now: Long, clock: Boolean) {
    Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            GlanceModifier
                .width(40.dp)
                .background(Color(palette.badge(d.m)))
                .cornerRadius(if (d.m == "T") 6.dp else if (d.m == "V") 2.dp else 10.dp)
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(d.l, style = TextStyle(color = provider(palette.badgeText), fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
        Spacer(GlanceModifier.width(10.dp))
        Text(
            d.sn ?: d.h, // a stop list's row shows only its stop; the user knows where the lines go
            style = TextStyle(color = provider(palette.text), fontSize = 15.sp),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        val delayMin = d.dl / 60
        // with minutes the delay is only the colour (the minutes include it); beside a clock time it does not clash
        if (clock && delayMin >= 1) {
            Text(
                "+$delayMin min",
                style = TextStyle(color = provider(if (delayMin >= 2) palette.warning else palette.muted), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Spacer(GlanceModifier.width(6.dp))
        }
        if (d.lv == 1) Text("● ", style = TextStyle(color = provider(palette.accent), fontSize = 10.sp))
        Text(
            departureTimeLabel(d.e, now, clock, context.getString(R.string.now)),
            style = TextStyle(color = provider(if (d.dl >= 120) palette.warning else palette.text), fontSize = 15.sp, fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun IconButton(palette: WidgetPalette, icon: Int, description: String, onClick: androidx.glance.action.Action) {
    Box(
        GlanceModifier.size(36.dp).background(Color(palette.button)).cornerRadius(18.dp).clickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(ImageProvider(icon), description, GlanceModifier.size(20.dp), colorFilter = ColorFilter.tint(provider(palette.text)))
    }
}

@Composable
private fun Message(palette: WidgetPalette, text: String) {
    Text(text, style = TextStyle(color = provider(palette.secondary), fontSize = 14.sp), modifier = GlanceModifier.padding(vertical = 8.dp))
}

private fun errorText(context: Context, error: WidgetError?): String? = when (error) {
    WidgetError.NO_LOCATION -> context.getString(R.string.widget_no_location)
    WidgetError.OUT_OF_RANGE -> context.getString(R.string.out_of_range_widget)
    WidgetError.NETWORK -> context.getString(R.string.error_network)
    WidgetError.AUTH -> context.getString(R.string.error_auth)
    WidgetError.SERVER -> context.getString(R.string.error_server, "")
    null -> null
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetUpdater.refresh(context, glanceId)
    }
}

class ReverseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetUpdater.refresh(context, glanceId) { config, shown -> WidgetLogic.reverse(config, shown) }
    }
}
