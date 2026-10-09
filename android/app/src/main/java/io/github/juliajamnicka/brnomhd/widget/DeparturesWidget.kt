package io.github.juliajamnicka.brnomhd.widget

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
import io.github.juliajamnicka.brnomhd.R
import io.github.juliajamnicka.brnomhd.data.DepartureDto
import io.github.juliajamnicka.brnomhd.data.DeparturesResponse
import io.github.juliajamnicka.brnomhd.ui.StopPickerActivity
import java.text.DateFormat
import java.util.Date

// Same look as the watch app: black, white text, mode badges, teal live dot, amber delays.
private val Background = Color(0xFF000000)
private val Primary = ColorProvider(Color.White)
private val Secondary = ColorProvider(Color(0xFFC7C7CC))
private val Muted = ColorProvider(Color(0xFF8E8E93))
private val Live = ColorProvider(Color(0xFF5EEAD4))
private val Amber = ColorProvider(Color(0xFFFFB020))
private val ButtonBackground = Color(0xFF2C2C2E)

class DeparturesWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val pickerIntent = Intent(context, StopPickerActivity::class.java)
            .putExtra(StopPickerActivity.EXTRA_APP_WIDGET_ID, appWidgetId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        provideContent {
            val prefs = currentState<Preferences>()
            Content(
                context = context,
                response = WidgetState.response(prefs),
                pinned = prefs[WidgetState.pinnedPlatform] != null,
                loading = prefs[WidgetState.loading] == true,
                updatedAt = prefs[WidgetState.updatedAt],
                error = prefs[WidgetState.error]?.let { runCatching { WidgetError.valueOf(it) }.getOrNull() },
                pickerIntent = pickerIntent,
            )
        }
    }
}

@Composable
private fun Content(
    context: Context,
    response: DeparturesResponse?,
    pinned: Boolean,
    loading: Boolean,
    updatedAt: Long?,
    error: WidgetError?,
    pickerIntent: Intent,
) {
    Column(
        GlanceModifier
            .fillMaxSize()
            .background(Background)
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
                            colorFilter = ColorFilter.tint(Amber),
                        )
                        Spacer(GlanceModifier.width(4.dp))
                    }
                    Text(
                        response?.stop ?: context.getString(R.string.widget_title),
                        style = TextStyle(color = Primary, fontSize = 17.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                }
                val subtitle = response?.let { r -> "→ ${r.dir}" + (r.d?.let { " · $it m" } ?: "") }
                if (subtitle != null) Text(subtitle, style = TextStyle(color = Secondary, fontSize = 13.sp), maxLines = 1)
            }
            IconButton(R.drawable.ic_reverse, context.getString(R.string.widget_reverse), actionRunCallback<ReverseAction>())
            Spacer(GlanceModifier.width(6.dp))
            IconButton(R.drawable.ic_list, context.getString(R.string.widget_pick_stop), actionStartActivity(pickerIntent))
            Spacer(GlanceModifier.width(6.dp))
            IconButton(R.drawable.ic_refresh, context.getString(R.string.widget_refresh), actionRunCallback<RefreshAction>())
        }
        Spacer(GlanceModifier.height(6.dp))

        when {
            response == null && loading -> Message(context.getString(R.string.preview_loading))
            response == null -> Message(errorText(context, error) ?: context.getString(R.string.widget_tap_refresh))
            response.dep.isEmpty() -> Message(context.getString(R.string.no_departures))
            else -> response.dep.forEach { DepartureRow(it) }
        }

        Spacer(GlanceModifier.defaultWeight())
        val footer = when {
            loading -> context.getString(R.string.preview_loading)
            error != null && response != null -> errorText(context, error)
            updatedAt != null -> context.getString(R.string.widget_updated, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(updatedAt)))
            else -> null
        }
        if (footer != null) Text(footer, style = TextStyle(color = if (error != null && !loading) Amber else Muted, fontSize = 11.sp), maxLines = 1)
    }
}

@Composable
private fun DepartureRow(d: DepartureDto) {
    Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            GlanceModifier
                .width(40.dp)
                .background(badgeColor(d.m))
                .cornerRadius(if (d.m == "T") 6.dp else if (d.m == "V") 2.dp else 10.dp)
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(d.l, style = TextStyle(color = Primary, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
        Spacer(GlanceModifier.width(10.dp))
        Text(d.h, style = TextStyle(color = Primary, fontSize = 15.sp), maxLines = 1, modifier = GlanceModifier.defaultWeight())
        if (d.lv == 1) Text("● ", style = TextStyle(color = Live, fontSize = 10.sp))
        Text(
            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(d.e * 1000)),
            style = TextStyle(color = if (d.dl >= 120) Amber else Primary, fontSize = 15.sp, fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun IconButton(icon: Int, description: String, onClick: androidx.glance.action.Action) {
    Box(
        GlanceModifier.size(36.dp).background(ButtonBackground).cornerRadius(18.dp).clickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(ImageProvider(icon), description, GlanceModifier.size(20.dp), colorFilter = ColorFilter.tint(Primary))
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = TextStyle(color = Secondary, fontSize = 14.sp), modifier = GlanceModifier.padding(vertical = 8.dp))
}

private fun badgeColor(mode: String): Color = when (mode) {
    "T" -> Color(0xFFC8262C)
    "V" -> Color(0xFF1D5FD1)
    else -> Color(0xFF1F7A4D)
}

private fun errorText(context: Context, error: WidgetError?): String? = when (error) {
    WidgetError.NO_LOCATION -> context.getString(R.string.widget_no_location)
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
