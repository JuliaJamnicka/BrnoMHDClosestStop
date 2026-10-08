package io.github.juliajamnicka.brnomhd.widget

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.juliajamnicka.brnomhd.data.DeparturesResponse
import kotlinx.serialization.json.Json

/** Per-widget state stored by Glance (PreferencesGlanceStateDefinition). */
object WidgetState {
    private val json = Json { ignoreUnknownKeys = true }

    val pinnedPlatform = stringPreferencesKey("pinned_platform")
    val reversedGroup = stringPreferencesKey("reversed_group")
    val response = stringPreferencesKey("response")
    val updatedAt = longPreferencesKey("updated_at")
    val loading = booleanPreferencesKey("loading")
    /** One of the WidgetError names, or absent. */
    val error = stringPreferencesKey("error")

    fun config(p: Preferences) = WidgetConfig(p[pinnedPlatform], p[reversedGroup])

    fun setConfig(p: MutablePreferences, config: WidgetConfig) {
        if (config.pinnedPlatform != null) p[pinnedPlatform] = config.pinnedPlatform else p.remove(pinnedPlatform)
        if (config.reversedGroup != null) p[reversedGroup] = config.reversedGroup else p.remove(reversedGroup)
    }

    fun response(p: Preferences): DeparturesResponse? =
        p[response]?.let { runCatching { json.decodeFromString<DeparturesResponse>(it) }.getOrNull() }

    fun setResponse(p: MutablePreferences, r: DeparturesResponse) {
        p[response] = json.encodeToString(DeparturesResponse.serializer(), r)
    }
}

enum class WidgetError { NO_LOCATION, NETWORK, AUTH, SERVER }
