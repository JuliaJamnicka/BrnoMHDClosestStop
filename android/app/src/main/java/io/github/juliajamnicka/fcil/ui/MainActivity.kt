package io.github.juliajamnicka.fcil.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.huawei.wearengine.HiWear
import com.huawei.wearengine.auth.AuthCallback
import com.huawei.wearengine.auth.Permission
import io.github.juliajamnicka.fcil.FcilApp
import io.github.juliajamnicka.fcil.R
import io.github.juliajamnicka.fcil.data.Language
import io.github.juliajamnicka.fcil.wear.WatchService
import io.github.juliajamnicka.fcil.widget.DeparturesWidget
import io.github.juliajamnicka.fcil.widget.WidgetClock
import io.github.juliajamnicka.fcil.widget.WidgetState
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val graph by lazy { (application as FcilApp).graph }

    private var hasLocation by mutableStateOf(false)
    private var hasBackgroundLocation by mutableStateOf(false)
    private var setupMessage by mutableStateOf<String?>(null)

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshPermissions()
    }
    private val singlePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refreshPermissions()
        setContent {
            FcilTheme {
                AppScreen(
                    hasLocation = hasLocation,
                    requestLocation = ::requestLocation,
                    settings = { padding ->
                        SettingsScreen(
                            settingsFlow = graph.settings.settings,
                            watchEnabled = WatchService.isEnabled(this),
                            hasLocation = hasLocation,
                            hasBackgroundLocation = hasBackgroundLocation,
                            setupMessage = setupMessage,
                            actions = SettingsActions(
                                requestLocation = ::requestLocation,
                                requestBackgroundLocation = ::requestBackgroundLocation,
                                connectWatch = ::connectWatch,
                                disconnectWatch = { WatchService.stop(this) },
                                setLanguage = ::setLanguage,
                                setDepartureCount = { n -> lifecycleScope.launch { graph.settings.setDepartureCount(n) } },
                                setDepartureTime = { time ->
                                    // app scope: the widgets must be redrawn even if the activity closes meanwhile
                                    graph.scope.launch {
                                        graph.settings.setDepartureTime(time)
                                        WidgetClock.sync(applicationContext)
                                    }
                                },
                                openWidgetStyle = ::openWidgetStyle,
                            ),
                            contentPadding = padding,
                        )
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun refreshPermissions() {
        hasLocation = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        hasBackgroundLocation = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    private fun requestLocation() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissions += Manifest.permission.POST_NOTIFICATIONS
        locationPermission.launch(permissions.toTypedArray())
    }

    /** Android only offers "Allow all the time" after the foreground permission, as a separate step. */
    private fun requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) singlePermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    /** Asks Huawei Health for the Wear Engine permission, then starts the watch link service. */
    private fun connectWatch() {
        setupMessage = null
        try {
            HiWear.getAuthClient(this)
                .requestPermission(object : AuthCallback {
                    override fun onOk(permissions: Array<out Permission>?) {
                        runOnUiThread { WatchService.start(this@MainActivity) }
                    }

                    override fun onCancel() {
                        runOnUiThread { setupMessage = getString(R.string.setup_auth_cancelled) }
                    }
                }, Permission.DEVICE_MANAGER)
                .addOnFailureListener { e ->
                    runOnUiThread { setupMessage = getString(R.string.setup_auth_failed, e.message ?: "") }
                }
        } catch (e: Exception) {
            setupMessage = getString(R.string.setup_auth_failed, e.message ?: "")
        }
    }

    /** Opens the widget style screen; with several widgets the user picks one by what it shows. */
    private fun openWidgetStyle() {
        lifecycleScope.launch {
            val manager = GlanceAppWidgetManager(this@MainActivity)
            val ids = manager.getGlanceIds(DeparturesWidget::class.java)
            when (ids.size) {
                0 -> Toast.makeText(this@MainActivity, R.string.widget_style_none, Toast.LENGTH_LONG).show()
                1 -> startActivity(WidgetStyleActivity.intent(this@MainActivity, manager.getAppWidgetId(ids.single())))
                else -> {
                    val labels = ids.mapIndexed { i, id ->
                        val prefs = getAppWidgetState<Preferences>(this@MainActivity, PreferencesGlanceStateDefinition, id)
                        WidgetState.response(prefs)?.stop ?: "${getString(R.string.widget_title)} ${i + 1}"
                    }
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(R.string.widget_style_choose)
                        .setItems(labels.toTypedArray()) { _, which ->
                            startActivity(WidgetStyleActivity.intent(this@MainActivity, manager.getAppWidgetId(ids[which])))
                        }
                        .show()
                }
            }
        }
    }

    private fun setLanguage(language: Language) {
        lifecycleScope.launch {
            graph.settings.setLanguage(language)
            AppCompatDelegate.setApplicationLocales(
                if (language == Language.SYSTEM) LocaleListCompat.getEmptyLocaleList()
                else LocaleListCompat.forLanguageTags(language.code),
            )
        }
    }
}
