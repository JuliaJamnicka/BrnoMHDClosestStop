package io.github.juliajamnicka.fcil.ui

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import io.github.juliajamnicka.fcil.FcilApp
import io.github.juliajamnicka.fcil.R
import io.github.juliajamnicka.fcil.data.DepartureDto
import io.github.juliajamnicka.fcil.widget.DeparturesWidget
import io.github.juliajamnicka.fcil.widget.SystemColors
import io.github.juliajamnicka.fcil.widget.WidgetColors
import io.github.juliajamnicka.fcil.widget.WidgetPalette
import io.github.juliajamnicka.fcil.widget.WidgetState
import io.github.juliajamnicka.fcil.widget.WidgetStyle
import io.github.juliajamnicka.fcil.widget.systemColors
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * The widget's style screen: opened from the widget's settings (long press > pencil, Android 12+) and
 * from Settings in the app. Changes show in a live preview and are stored only on "Apply".
 */
class WidgetStyleActivity : AppCompatActivity() {
    private val graph by lazy { (application as FcilApp).graph }
    private var loaded by mutableStateOf<Loaded?>(null)

    private class Loaded(val glanceId: GlanceId, val style: WidgetStyle, val title: String?, val rows: List<DepartureDto>)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        // As a configuration activity, leaving without "Apply" must still keep the widget (older Androids
        // open this screen when the widget is placed and remove the widget on a cancelled result).
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        lifecycleScope.launch {
            val id = runCatching { GlanceAppWidgetManager(this@WidgetStyleActivity).getGlanceIdBy(appWidgetId) }.getOrNull()
            if (id == null) {
                finish()
                return@launch
            }
            val prefs = getAppWidgetState<Preferences>(this@WidgetStyleActivity, PreferencesGlanceStateDefinition, id)
            val response = WidgetState.response(prefs)
            loaded = Loaded(id, WidgetState.style(prefs), response?.stop, response?.dep.orEmpty())
        }
        val system = systemColors(this)
        setContent {
            FcilTheme {
                loaded?.let { l ->
                    WidgetStyleScreen(l.style, l.title, l.rows, system, onBack = ::finish) { style ->
                        // not lifecycleScope: finish() would cancel the save
                        graph.scope.launch {
                            updateAppWidgetState(applicationContext, l.glanceId) { WidgetState.setStyle(it, style) }
                            DeparturesWidget().update(applicationContext, l.glanceId)
                        }
                        finish()
                    }
                } ?: Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            }
        }
    }

    companion object {
        fun intent(context: Context, appWidgetId: Int): Intent =
            Intent(context, WidgetStyleActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    }
}

@Composable
private fun WidgetStyleScreen(
    initial: WidgetStyle,
    title: String?,
    rows: List<DepartureDto>,
    system: SystemColors?,
    onBack: () -> Unit,
    onApply: (WidgetStyle) -> Unit,
) {
    var style by remember { mutableStateOf(initial) }
    val palette = WidgetColors.palette(style, system)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back)) }
            PageTitle(stringResource(R.string.widget_style_title), fontSize = 28.sp)
        }
        WidgetPreview(palette, title ?: stringResource(R.string.widget_title), rows)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (system != null) {
                BrandCard {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(style.system, role = Role.Switch) { style = style.copy(system = !style.system) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.widget_style_system), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.widget_style_system_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = style.system, onCheckedChange = null)
                    }
                }
            }
            ColourPicker(
                stringResource(R.string.widget_style_background), WidgetStyle.BACKGROUNDS, style.background, enabled = !style.system,
            ) { style = style.copy(background = it) }
            ColourPicker(
                stringResource(R.string.widget_style_accent), WidgetStyle.ACCENTS, style.accent, enabled = !style.system,
            ) { style = style.copy(accent = it) }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(stringResource(R.string.widget_style_opacity))
                BrandCard {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = style.opacity.toFloat(),
                            onValueChange = { style = style.copy(opacity = (it / 5).toInt() * 5) },
                            valueRange = 0f..100f,
                            steps = 19,
                            modifier = Modifier.weight(1f),
                        )
                        Text("${style.opacity} %", Modifier.width(56.dp).padding(start = 8.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(stringResource(R.string.widget_style_text))
                val modes = listOf(
                    WidgetStyle.TextMode.AUTO to stringResource(R.string.widget_style_text_auto),
                    WidgetStyle.TextMode.LIGHT to stringResource(R.string.widget_style_text_light),
                    WidgetStyle.TextMode.DARK to stringResource(R.string.widget_style_text_dark),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { i, (mode, label) ->
                        SegmentedButton(
                            selected = style.text == mode,
                            onClick = { style = style.copy(text = mode) },
                            shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                        ) { Text(label) }
                    }
                }
                Text(stringResource(R.string.widget_style_text_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
            }

            ColourPicker(
                stringResource(R.string.widget_style_delay), WidgetStyle.DELAYS, style.delay, enabled = true,
            ) { style = style.copy(delay = it) }
            Text(stringResource(R.string.widget_style_delay_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)

            BadgeSets(style.badges, palette) { style = style.copy(badges = it) }

            TextButton(onClick = { style = WidgetStyle() }) {
                Text(stringResource(R.string.widget_style_reset), fontWeight = FontWeight.SemiBold)
            }
        }

        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Button(onClick = { onApply(style) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.widget_style_apply), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Swatches plus a hex field. */
@Composable
private fun ColourPicker(title: String, swatches: List<Int>, value: Int, enabled: Boolean, onPick: (Int) -> Unit) {
    var hex by remember(value) { mutableStateOf(WidgetColors.hex(value)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title)
        BrandCard {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.SpaceBetween) {
                    swatches.forEach { c ->
                        val selected = c == value
                        val label = "#" + WidgetColors.hex(c)
                        Box(
                            Modifier
                                .size(36.dp)
                                .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape).padding(4.dp) else Modifier)
                                .clip(CircleShape)
                                .background(Color(c).copy(alpha = if (enabled) 1f else 0.4f))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                .selectable(selected, enabled = enabled, role = Role.RadioButton) { onPick(c) }
                                .semantics { contentDescription = label },
                        )
                    }
                }
                TextField(
                    value = hex,
                    onValueChange = { text ->
                        hex = text.filter { it.isLetterOrDigit() }.take(6).uppercase()
                        WidgetColors.parseHex(hex)?.let(onPick)
                    },
                    enabled = enabled,
                    singleLine = true,
                    prefix = { Text("#") },
                    leadingIcon = { Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(Color(value))) },
                    label = { Text(stringResource(R.string.widget_style_hex, title)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** The line colour sets, each shown as its tram, bus and train badges. */
@Composable
private fun BadgeSets(value: WidgetStyle.BadgeSet, palette: WidgetPalette, onPick: (WidgetStyle.BadgeSet) -> Unit) {
    val names = mapOf(
        WidgetStyle.BadgeSet.FCIL to "Fčil",
        WidgetStyle.BadgeSet.PASTEL to stringResource(R.string.widget_style_badges_pastel),
        WidgetStyle.BadgeSet.VIVID to stringResource(R.string.widget_style_badges_vivid),
        WidgetStyle.BadgeSet.MUTED to stringResource(R.string.widget_style_badges_muted),
        WidgetStyle.BadgeSet.MONO to stringResource(R.string.widget_style_badges_mono),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.widget_style_badges))
        BrandCard {
            Column(Modifier.selectableGroup()) {
                WidgetStyle.BadgeSet.entries.forEachIndexed { i, set ->
                    // MONO follows the widget's text colour, so show it with this palette
                    val p = WidgetColors.palette(WidgetStyle(badges = set), null).let { if (set == WidgetStyle.BadgeSet.MONO) palette else it }
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(set == value, role = Role.RadioButton) { onPick(set) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf("T" to "4", "B" to "44", "V" to "S3").forEach { (mode, line) ->
                            // a mono badge is translucent: show it over the widget background, as on the widget
                            val bg = if (set == WidgetStyle.BadgeSet.MONO) Modifier.background(Color(palette.background)) else Modifier
                            Box(Modifier.clip(RoundedCornerShape(if (mode == "T") 6.dp else if (mode == "V") 2.dp else 10.dp)).then(bg)) {
                                Box(
                                    Modifier.width(40.dp).background(Color(p.badge(mode))),
                                    contentAlignment = Alignment.Center,
                                ) { Text(line, color = Color(p.badgeText), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                        Text(names.getValue(set), Modifier.weight(1f).padding(start = 8.dp), fontSize = 17.sp)
                        RadioButton(selected = set == value, onClick = null)
                    }
                }
            }
        }
    }
}

/** A Compose copy of the widget (DeparturesWidget) on a wallpaper-like backdrop. */
@Composable
private fun WidgetPreview(palette: WidgetPalette, title: String, rows: List<DepartureDto>) {
    val shown = rows.take(4).ifEmpty { SAMPLE }
    val text = Color(palette.text)
    Box(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFB9A9B4), Color(0xFFC9A79A), Color(0xFFA9AEBE))))
            .padding(14.dp)
            .semantics(mergeDescendants = true) { },
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(palette.background)).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, color = text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            shown.forEach { d ->
                Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.width(40.dp).clip(RoundedCornerShape(if (d.m == "T") 6.dp else if (d.m == "V") 2.dp else 10.dp)).background(Color(palette.badge(d.m))),
                        contentAlignment = Alignment.Center,
                    ) { Text(d.l, color = Color(palette.badgeText), fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                    Spacer(Modifier.width(10.dp))
                    Text(d.sn ?: d.h, Modifier.weight(1f), color = text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (d.lv == 1) Text("● ", color = Color(palette.accent), fontSize = 10.sp)
                    Text(
                        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(d.e * 1000)),
                        color = if (d.dl >= 120) Color(palette.warning) else text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.widget_updated, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())),
                    Modifier.weight(1f),
                    color = Color(palette.muted),
                    fontSize = 11.sp,
                )
                // as in DeparturesWidget: a stop list's board (rows naming their stops) has no reverse button
                val buttons = listOfNotNull(
                    R.drawable.ic_reverse.takeIf { shown.none { it.sn != null } },
                    R.drawable.ic_list,
                    R.drawable.ic_notification,
                    R.drawable.ic_refresh,
                )
                buttons.forEach {
                    Box(Modifier.padding(start = 6.dp).size(32.dp).clip(CircleShape).background(Color(palette.button)), contentAlignment = Alignment.Center) {
                        Icon(painterResource(it), null, tint = text, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

private val SAMPLE = run {
    val now = System.currentTimeMillis() / 1000
    listOf(
        DepartureDto("S3", "V", "Lesná, nádraží", now + 360, now + 360, 0, 1),
        DepartureDto("44", "B", "Poliklinika Lesná", now + 720, now + 720, 0, 1),
        DepartureDto("4", "T", "Náměstí Míru", now + 1080, now + 960, 120, 1),
    )
}
