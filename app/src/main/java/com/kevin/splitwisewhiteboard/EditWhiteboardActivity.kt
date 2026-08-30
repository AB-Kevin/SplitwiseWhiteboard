package com.kevin.splitwisewhiteboard

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kevin.splitwisewhiteboard.network.SplitwiseAuthException
import com.kevin.splitwisewhiteboard.network.SplitwiseClient
import com.kevin.splitwisewhiteboard.storage.SecureStore
import com.kevin.splitwisewhiteboard.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The "tap the widget to edit it" screen. Android widgets can't host a real
 * editable text field, so this floating, dialog-styled activity stands in
 * for one: tapping the widget launches this, you edit, Save writes back to
 * Splitwise and refreshes the widget.
 */
class EditWhiteboardActivity : AppCompatActivity() {

    private lateinit var editText: EditText
    private lateinit var titleView: TextView
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button

    private var groupId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_whiteboard)

        editText = findViewById(R.id.whiteboardEditText)
        titleView = findViewById(R.id.whiteboardTitle)
        saveButton = findViewById(R.id.saveButton)
        cancelButton = findViewById(R.id.cancelButton)

        groupId = SecureStore.getSelectedGroupId(this)
        titleView.text = SecureStore.getSelectedGroupName(this) ?: getString(R.string.app_name)

        if (!SecureStore.isLoggedIn(this) || groupId < 0) {
            Toast.makeText(this, R.string.setup_required_message, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Show the last-known text immediately, then quietly refresh it from
        // the server in case it changed on another device.
        editText.setText(SecureStore.getLastWhiteboard(this).orEmpty())
        loadLatest()

        cancelButton.setOnClickListener { finish() }
        saveButton.setOnClickListener { save() }
    }

    private fun loadLatest() {
        lifecycleScope.launch {
            val cookie = SecureStore.getCookieHeader(this@EditWhiteboardActivity) ?: return@launch
            try {
                val latest = withContext(Dispatchers.IO) {
                    SplitwiseClient.getWhiteboard(cookie, groupId)
                }
                if (latest != null) {
                    editText.setText(latest)
                    editText.setSelection(editText.text.length)
                    SecureStore.saveLastWhiteboard(this@EditWhiteboardActivity, latest, null)
                }
            } catch (e: SplitwiseAuthException) {
                Toast.makeText(
                    this@EditWhiteboardActivity,
                    R.string.session_expired_message,
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                // Offline or a transient error — just keep showing the cached text.
            }
        }
    }

    private fun save() {
        val newText = editText.text.toString()
        saveButton.isEnabled = false
        lifecycleScope.launch {
            val cookie = SecureStore.getCookieHeader(this@EditWhiteboardActivity)
            if (cookie == null) {
                Toast.makeText(
                    this@EditWhiteboardActivity,
                    R.string.setup_required_message,
                    Toast.LENGTH_LONG
                ).show()
                finish()
                return@launch
            }
            try {
                withContext(Dispatchers.IO) {
                    SplitwiseClient.setWhiteboard(cookie, groupId, newText)
                }
                SecureStore.saveLastWhiteboard(this@EditWhiteboardActivity, newText, null)
                withContext(Dispatchers.IO) {
                    WidgetUpdater.refreshAll(this@EditWhiteboardActivity)
                }
                finish()
            } catch (e: SplitwiseAuthException) {
                saveButton.isEnabled = true
                Toast.makeText(
                    this@EditWhiteboardActivity,
                    R.string.session_expired_message,
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                saveButton.isEnabled = true
                Toast.makeText(this@EditWhiteboardActivity, R.string.save_failed_message, Toast.LENGTH_LONG).show()
            }
        }
    }
}
