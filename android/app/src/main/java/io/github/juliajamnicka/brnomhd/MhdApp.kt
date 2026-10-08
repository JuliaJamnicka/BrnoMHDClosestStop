package io.github.juliajamnicka.brnomhd

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import io.github.juliajamnicka.brnomhd.data.ApiClient
import io.github.juliajamnicka.brnomhd.data.FusedLocationSource
import io.github.juliajamnicka.brnomhd.data.Language
import io.github.juliajamnicka.brnomhd.data.SettingsStore
import io.github.juliajamnicka.brnomhd.data.TransitRepository
import java.util.Locale

class MhdApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}

/** Manual dependency wiring; small enough not to need a DI framework. */
class AppGraph(app: Application) {
    val settings = SettingsStore(app)
    val location = FusedLocationSource(app)
    val api = ApiClient(config = {
        val s = settings.current()
        ApiClient.ApiConfig(s.apiUrl, s.apiKey)
    })
    val repository = TransitRepository(api, location)

    /** Language code sent to the watch: the app language if set, else the phone language. */
    fun watchLanguage(): String {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val language = if (!appLocales.isEmpty) appLocales[0]?.language else Locale.getDefault().language
        return if (language == Language.CZECH.code) "cs" else "en"
    }
}
