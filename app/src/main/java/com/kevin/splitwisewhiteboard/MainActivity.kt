package com.kevin.splitwisewhiteboard

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.kevin.splitwisewhiteboard.network.GroupSummary
import com.kevin.splitwisewhiteboard.network.SplitwiseAuthException
import com.kevin.splitwisewhiteboard.network.SplitwiseClient
import com.kevin.splitwisewhiteboard.storage.SecureStore
import com.kevin.splitwisewhiteboard.update.UpdateChecker
import com.kevin.splitwisewhiteboard.update.UpdateInfo
import com.kevin.splitwisewhiteboard.update.UpdateInstaller
import com.kevin.splitwisewhiteboard.widget.WidgetUpdater
import com.kevin.splitwisewhiteboard.work.WhiteboardRefreshScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var loginButton: Button
    private lateinit var groupListView: ListView
    private lateinit var refreshWidgetButton: Button
    private lateinit var checkForUpdatesButton: Button

    private var groups: List<GroupSummary> = emptyList()

    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            refreshStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // The app targets edge-to-edge display, so the root layout draws
        // under the status bar by default — on phones with a hole-punch
        // camera up there, that put statusText right behind the camera and
        // notification icons. Push the content down by the system bar insets
        // (on top of the layout's own padding) so nothing overlaps.
        val root = findViewById<android.view.View>(R.id.main)
        val basePadding = intArrayOf(root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                basePadding[0] + bars.left,
                basePadding[1] + bars.top,
                basePadding[2] + bars.right,
                basePadding[3] + bars.bottom
            )
            insets
        }

        statusText = findViewById(R.id.statusText)
        loginButton = findViewById(R.id.loginButton)
        groupListView = findViewById(R.id.groupListView)
        refreshWidgetButton = findViewById(R.id.refreshWidgetButton)
        checkForUpdatesButton = findViewById(R.id.checkForUpdatesButton)

        loginButton.setOnClickListener {
            loginLauncher.launch(Intent(this, LoginActivity::class.java))
        }

        checkForUpdatesButton.setOnClickListener { checkForUpdates(showResultToast = true) }

        groupListView.setOnItemClickListener { _, _, position, _ ->
            val group = groups.getOrNull(position) ?: return@setOnItemClickListener
            SecureStore.saveSelectedGroup(this, group.id, group.name)
            WhiteboardRefreshScheduler.schedule(this)
            Toast.makeText(this, getString(R.string.group_selected_toast, group.name), Toast.LENGTH_SHORT).show()
            refreshWidgetNow()
        }

        refreshWidgetButton.setOnClickListener { refreshWidgetNow() }

        WhiteboardRefreshScheduler.schedule(this)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()

        if (UpdateChecker.shouldAutoCheck(this)) {
            UpdateChecker.markCheckedNow(this)
            checkForUpdates(showResultToast = false)
        }
    }

    /**
     * Checks GitHub Releases for a newer build. [showResultToast] controls
     * whether "you're up to date" / "couldn't check" get surfaced — the
     * silent background check on launch stays quiet unless there's actually
     * an update to show; the manual button always reports something.
     */
    @Suppress("DEPRECATION")
    private fun checkForUpdates(showResultToast: Boolean) {
        lifecycleScope.launch {
            val currentVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "0"
            val result = withContext(Dispatchers.IO) { UpdateChecker.checkForUpdate(currentVersion) }
            result.fold(
                onSuccess = { update ->
                    if (update != null) {
                        showUpdateDialog(update)
                    } else if (showResultToast) {
                        Toast.makeText(this@MainActivity, R.string.update_none_toast, Toast.LENGTH_SHORT).show()
                    }
                },
                onFailure = {
                    if (showResultToast) {
                        Toast.makeText(this@MainActivity, R.string.update_check_failed_toast, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    private fun showUpdateDialog(update: UpdateInfo) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_available_title, update.versionTag))
            .setMessage(R.string.update_available_message)
            .setPositiveButton(R.string.update_download_button) { _, _ -> startUpdateDownload(update) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun startUpdateDownload(update: UpdateInfo) {
        if (UpdateInstaller.needsInstallPermission(this)) {
            Toast.makeText(this, R.string.install_permission_needed_toast, Toast.LENGTH_LONG).show()
            UpdateInstaller.requestInstallPermission(this)
            return
        }
        UpdateInstaller.downloadUpdate(this, update.apkDownloadUrl, update.versionTag)
        Toast.makeText(this, R.string.update_downloading_toast, Toast.LENGTH_SHORT).show()
    }

    private fun refreshStatus() {
        if (!SecureStore.isLoggedIn(this)) {
            statusText.text = getString(R.string.not_logged_in_status)
            loginButton.text = getString(R.string.log_in_button)
            groupListView.adapter = null
            return
        }

        loginButton.text = getString(R.string.log_in_again_button)
        val selectedName = SecureStore.getSelectedGroupName(this)
        statusText.text = if (selectedName != null) {
            getString(R.string.logged_in_status_with_group, selectedName)
        } else {
            getString(R.string.logged_in_status_no_group)
        }

        loadGroups()
    }

    private fun loadGroups() {
        lifecycleScope.launch {
            val cookie = SecureStore.getCookieHeader(this@MainActivity) ?: return@launch
            try {
                val fetched = withContext(Dispatchers.IO) { SplitwiseClient.listGroups(cookie) }
                groups = fetched
                groupListView.adapter = ArrayAdapter(
                    this@MainActivity,
                    android.R.layout.simple_list_item_1,
                    fetched.map { it.name }
                )
            } catch (e: SplitwiseAuthException) {
                statusText.text = getString(R.string.session_expired_message)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, R.string.could_not_load_groups_toast, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshWidgetNow() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { WidgetUpdater.refreshAll(this@MainActivity) }
            Toast.makeText(this@MainActivity, R.string.widget_refreshed_toast, Toast.LENGTH_SHORT).show()
        }
    }
}
