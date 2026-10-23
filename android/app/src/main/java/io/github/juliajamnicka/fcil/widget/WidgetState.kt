package io.github.juliajamnicka.fcil.widget

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.juliajamnicka.fcil.data.DeparturesResponse
import kotlinx.serialization.json.Json

/** Per-widget state stored by Glance (PreferencesGlanceStateDefinition). */
object WidgetState {
    private val json = Json { ignoreUnknownKeys = true }

    val pinnedPlatform = stringPreferencesKey("pinned_platform")
    val reversedGroup = stringPreferencesKey("reversed_group")
    val pinnedList = stringPreferencesKey("pinned_list")
    val response = stringPreferencesKey("response")
    val updatedAt = longPreferencesKey("updated_at")
    val loading = booleanPreferencesKey("loading")
    /** One of the WidgetError names, or absent. */
    val error = stringPreferencesKey("error")
    /** The departure time setting (DepartureTime.CLOCK), copied here by WidgetClock. */
    val clockTimes = booleanPreferencesKey("clock_times")
    /** Last minute redraw (WidgetClock); changing it makes Glance recompose the widget. */
    val redrawnAt = longPreferencesKey("redrawn_at")

    private val styleSystem = booleanPreferencesKey("style_system")
    private val styleBackground = intPreferencesKey("style_background")
    private val styleAccent = intPreferencesKey("style_accent")
    private val styleOpacity = intPreferencesKey("style_opacity")
    private val styleText = stringPreferencesKey("style_text")
    private val styleDelay = intPreferencesKey("style_delay")
    private val styleBadges = stringPreferencesKey("style_badges")

    fun style(p: Preferences): WidgetStyle {
        val d = WidgetStyle()
        return WidgetStyle(
            system = p[styleSystem] ?: d.system,
            background = p[styleBackground] ?: d.background,
            accent = p[styleAccent] ?: d.accent,
            opacity = p[styleOpacity] ?: d.opacity,
            text = p[styleText]?.let { runCatching { WidgetStyle.TextMode.valueOf(it) }.getOrNull() } ?: d.text,
            delay = p[styleDelay] ?: d.delay,
            badges = p[styleBadges]?.let { runCatching { WidgetStyle.BadgeSet.valueOf(it) }.getOrNull() } ?: d.badges,
        )
    }

    fun setStyle(p: MutablePreferences, s: WidgetStyle) {
        p[styleSystem] = s.system
        p[styleBackground] = s.background
        p[styleAccent] = s.accent
        p[styleOpacity] = s.opacity
        p[styleText] = s.text.name
        p[styleDelay] = s.delay
        p[styleBadges] = s.badges.name
    }

    fun config(p: Preferences) = WidgetConfig(p[pinnedPlatform], p[reversedGroup], p[pinnedList])

    fun setConfig(p: MutablePreferences, config: WidgetConfig) {
        if (config.pinnedPlatform != null) p[pinnedPlatform] = config.pinnedPlatform else p.remove(pinnedPlatform)
        if (config.reversedGroup != null) p[reversedGroup] = config.reversedGroup else p.remove(reversedGroup)
        if (config.pinnedList != null) p[pinnedList] = config.pinnedList else p.remove(pinnedList)
    }

    fun response(p: Preferences): DeparturesResponse? =
        p[response]?.let { runCatching { json.decodeFromString<DeparturesResponse>(it) }.getOrNull() }

    fun setResponse(p: MutablePreferences, r: DeparturesResponse) {
        p[response] = json.encodeToString(DeparturesResponse.serializer(), r)
    }
}

enum class WidgetError { NO_LOCATION, OUT_OF_RANGE, NETWORK, AUTH, SERVER }
