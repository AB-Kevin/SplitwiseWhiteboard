package com.kevin.splitwisewhiteboard

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kevin.splitwisewhiteboard.network.GroupSummary
import com.kevin.splitwisewhiteboard.network.SplitwiseAuthException
import com.kevin.splitwisewhiteboard.network.SplitwiseClient
import com.kevin.splitwisewhiteboard.storage.SecureStore
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

        statusText = findViewById(R.id.statusText)
        loginButton = findViewById(R.id.loginButton)
        groupListView = findViewById(R.id.groupListView)
        refreshWidgetButton = findViewById(R.id.refreshWidgetButton)

        loginButton.setOnClickListener {
            loginLauncher.launch(Intent(this, LoginActivity::class.java))
        }

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
