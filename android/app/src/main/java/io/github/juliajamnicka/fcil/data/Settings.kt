package io.github.juliajamnicka.fcil.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "settings")

/** "system", "cs" or "en". */
enum class Language(val code: String) { SYSTEM("system"), CZECH("cs"), ENGLISH("en") }

/** How departures show their time: minutes until departure ("4 min") or the clock time ("14:32"). */
enum class DepartureTime { MINUTES, CLOCK }

data class AppSettings(
    val language: Language = Language.SYSTEM,
    val departureCount: Int = 4,
    /** For the app, the widget and the watch. */
    val departureTime: DepartureTime = DepartureTime.MINUTES,
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val language = stringPreferencesKey("language")
        val departureCount = intPreferencesKey("departure_count")
        val departureTime = stringPreferencesKey("departure_time")
        val stopLists = stringPreferencesKey("stop_lists")
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(StopList.serializer())

    /** The user's stop lists (docs/SPEC.md 6.2). */
    val stopLists: Flow<List<StopList>> = context.dataStore.data.map { p ->
        p[Keys.stopLists]?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() } ?: emptyList()
    }

    suspend fun currentStopLists(): List<StopList> = stopLists.first()

    suspend fun updateStopLists(change: (List<StopList>) -> List<StopList>) = context.dataStore.edit { p ->
        val current = p[Keys.stopLists]?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() } ?: emptyList()
        p[Keys.stopLists] = json.encodeToString(listSerializer, change(current))
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            language = Language.entries.firstOrNull { it.code == p[Keys.language] } ?: Language.SYSTEM,
            departureCount = p[Keys.departureCount] ?: 4,
            departureTime = DepartureTime.entries.firstOrNull { it.name == p[Keys.departureTime] } ?: DepartureTime.MINUTES,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setLanguage(language: Language) = context.dataStore.edit { it[Keys.language] = language.code }
    suspend fun setDepartureCount(count: Int) = context.dataStore.edit { it[Keys.departureCount] = count.coerceIn(3, 5) }
    suspend fun setDepartureTime(time: DepartureTime) = context.dataStore.edit { it[Keys.departureTime] = time.name }
}
