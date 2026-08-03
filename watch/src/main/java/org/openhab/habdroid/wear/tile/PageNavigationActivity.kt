package org.openhab.habdroid.wear.tile

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import org.openhab.habdroid.wear.util.AppLog
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.wear.tiles.TileService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.openhab.habdroid.wear.data.repository.TilePreferenceStore
import javax.inject.Inject

/**
 * Transparent activity that handles tile page navigation.
 * 
 * For protected pages (needsConfirmation=true), prompts for device credentials
 * before navigating. For unprotected pages, navigates immediately.
 *
 * Flow:
 * 1. Receives target page and confirmation flag via intent extras
 * 2. If confirmation required → show device credential prompt
 * 3. On success (or no confirmation needed) → set current page in prefs
 * 4. Request tile refresh → finish
 */
@AndroidEntryPoint
class PageNavigationActivity : ComponentActivity() {

    @Inject
    lateinit var tilePreferenceStore: TilePreferenceStore

    private var targetPage: String = "main"

    private val credentialLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            navigateToPage()
        } else {
            AppLog.d(TAG, "Credential confirmation cancelled")
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        targetPage = intent.getStringExtra(EXTRA_TARGET_PAGE) ?: "main"
        val needsConfirmation = intent.getBooleanExtra(EXTRA_NEEDS_CONFIRMATION, false)

        AppLog.d(TAG, "Navigation to '$targetPage', confirmation=$needsConfirmation")

        if (needsConfirmation) {
            val keyguardManager = getSystemService(KeyguardManager::class.java)
            if (keyguardManager.isDeviceSecure) {
                val confirmIntent = keyguardManager.createConfirmDeviceCredentialIntent(
                    "Security",
                    "Confirm to access $targetPage"
                )
                if (confirmIntent != null) {
                    credentialLauncher.launch(confirmIntent)
                } else {
                    // No credential intent available — proceed without
                    navigateToPage()
                }
            } else {
                // Device has no lock set — proceed without confirmation
                navigateToPage()
            }
        } else {
            navigateToPage()
        }
    }

    private fun navigateToPage() {
        CoroutineScope(Dispatchers.IO).launch {
            tilePreferenceStore.setCurrentPage(targetPage)
            TileService.getUpdater(this@PageNavigationActivity)
                .requestUpdate(OpenHabTileService::class.java)
        }
        finish()
    }

    companion object {
        private const val TAG = "PageNavigation"
        const val EXTRA_TARGET_PAGE = "target_page"
        const val EXTRA_NEEDS_CONFIRMATION = "needs_confirmation"
    }
}
