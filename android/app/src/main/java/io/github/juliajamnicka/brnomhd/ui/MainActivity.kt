package io.github.juliajamnicka.brnomhd.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import com.huawei.wearengine.HiWear
import com.huawei.wearengine.auth.AuthCallback
import com.huawei.wearengine.auth.Permission
import io.github.juliajamnicka.brnomhd.MhdApp
import io.github.juliajamnicka.brnomhd.R
import io.github.juliajamnicka.brnomhd.data.ApiException
import io.github.juliajamnicka.brnomhd.data.Language
import io.github.juliajamnicka.brnomhd.data.NoLocationException
import io.github.juliajamnicka.brnomhd.wear.WatchService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val graph by lazy { (application as MhdApp).graph }

    private var hasLocation by mutableStateOf(false)
    private var hasBackgroundLocation by mutableStateOf(false)
    private var preview by mutableStateOf<PreviewState>(PreviewState.Idle)
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
            MhdTheme {
                SettingsScreen(
                    settingsFlow = graph.settings.settings,
                    watchEnabled = WatchService.isEnabled(this),
                    hasLocation = hasLocation,
                    hasBackgroundLocation = hasBackgroundLocation,
                    setupMessage = setupMessage,
                    preview = preview,
                    actions = SettingsActions(
                        requestLocation = ::requestLocation,
                        requestBackgroundLocation = ::requestBackgroundLocation,
                        connectWatch = ::connectWatch,
                        disconnectWatch = { WatchService.stop(this) },
                        loadPreview = ::loadPreview,
                        setLanguage = ::setLanguage,
                        setDepartureCount = { n -> lifecycleScope.launch { graph.settings.setDepartureCount(n) } },
                        setApiUrl = { url -> lifecycleScope.launch { graph.settings.setApiUrl(url) } },
                        setApiKey = { key -> lifecycleScope.launch { graph.settings.setApiKey(key) } },
                    ),
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

    private fun loadPreview() {
        preview = PreviewState.Loading
        lifecycleScope.launch {
            preview = try {
                val count = graph.settings.current().departureCount
                PreviewState.Loaded(graph.repository.departuresForPreview(count))
            } catch (e: NoLocationException) {
                PreviewState.Failed(getString(R.string.error_no_location))
            } catch (e: ApiException) {
                PreviewState.Failed(
                    when (e.kind) {
                        ApiException.Kind.AUTH -> getString(R.string.error_auth)
                        ApiException.Kind.NETWORK -> getString(R.string.error_network)
                        ApiException.Kind.SERVER -> getString(R.string.error_server, e.message ?: "")
                    },
                )
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
