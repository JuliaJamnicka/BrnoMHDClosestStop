package io.github.juliajamnicka.brnomhd.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.juliajamnicka.brnomhd.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** "system", "cs" or "en". */
enum class Language(val code: String) { SYSTEM("system"), CZECH("cs"), ENGLISH("en") }

data class AppSettings(
    val language: Language = Language.SYSTEM,
    val departureCount: Int = 4,
    val apiUrl: String = BuildConfig.DEFAULT_API_URL,
    val apiKey: String = BuildConfig.DEFAULT_API_KEY,
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val language = stringPreferencesKey("language")
        val departureCount = intPreferencesKey("departure_count")
        val apiUrl = stringPreferencesKey("api_url")
        val apiKey = stringPreferencesKey("api_key")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            language = Language.entries.firstOrNull { it.code == p[Keys.language] } ?: Language.SYSTEM,
            departureCount = p[Keys.departureCount] ?: 4,
            apiUrl = p[Keys.apiUrl]?.takeIf { it.isNotBlank() } ?: BuildConfig.DEFAULT_API_URL,
            apiKey = p[Keys.apiKey]?.takeIf { it.isNotBlank() } ?: BuildConfig.DEFAULT_API_KEY,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setLanguage(language: Language) = context.dataStore.edit { it[Keys.language] = language.code }
    suspend fun setDepartureCount(count: Int) = context.dataStore.edit { it[Keys.departureCount] = count.coerceIn(3, 4) }
    suspend fun setApiUrl(url: String) = context.dataStore.edit { it[Keys.apiUrl] = url.trim() }
    suspend fun setApiKey(key: String) = context.dataStore.edit { it[Keys.apiKey] = key.trim() }
}
