package com.kevin.splitwisewhiteboard.update

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val versionTag: String,
    val apkDownloadUrl: String
)

/**
 * Checks GitHub Releases for a newer build of this app. The repo is public,
 * so this is a plain unauthenticated call to GitHub's REST API — no token
 * needed, and none should ever be added here, since this class ships inside
 * the APK and anything embedded in it is extractable.
 */
object UpdateChecker {

    private const val RELEASES_URL =
        "https://api.github.com/repos/AB-Kevin/SplitwiseWhiteboard/releases/latest"
    private const val USER_AGENT = "SplitwiseWhiteboard-UpdateChecker"

    private const val PREFS_NAME = "update_checker"
    private const val KEY_LAST_CHECKED_AT = "last_checked_at"
    private val AUTO_CHECK_INTERVAL_MS = TimeUnit.HOURS.toMillis(24)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Checks GitHub for the latest release. On success, the payload is the
     * newer [UpdateInfo] if one exists, or null if [currentVersionName] is
     * already current. On failure (offline, GitHub down, no release
     * published yet), this never throws — it returns a failed [Result]
     * instead, distinct from "already up to date".
     */
    fun checkForUpdate(currentVersionName: String): Result<UpdateInfo?> {
        return try {
            Result.success(fetchLatestRelease()?.takeIf { isNewer(it.versionTag, currentVersionName) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Whether it's been long enough since the last automatic check to run another one. */
    fun shouldAutoCheck(context: Context): Boolean {
        val lastChecked = prefs(context).getLong(KEY_LAST_CHECKED_AT, 0)
        return System.currentTimeMillis() - lastChecked > AUTO_CHECK_INTERVAL_MS
    }

    /** Records that an automatic check just happened, so [shouldAutoCheck] holds off for a while. */
    fun markCheckedNow(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_CHECKED_AT, System.currentTimeMillis()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun fetchLatestRelease(): UpdateInfo? {
        val request = Request.Builder()
            .url(RELEASES_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            val tag = json.optString("tag_name")
            if (tag.isBlank()) return null

            val assets = json.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                if (asset.optString("name").endsWith(".apk")) {
                    val url = asset.optString("browser_download_url")
                    if (url.isNotBlank()) {
                        return UpdateInfo(versionTag = tag, apkDownloadUrl = url)
                    }
                }
            }
            return null
        }
    }

    /** Compares dotted version numbers; an optional leading "v" (as in a git tag) is ignored. */
    private fun isNewer(latestTag: String, currentVersionName: String): Boolean {
        val latest = parseVersion(latestTag)
        val current = parseVersion(currentVersionName)
        for (i in 0 until maxOf(latest.size, current.size)) {
            val l = latest.getOrElse(i) { 0 }
            val c = current.getOrElse(i) { 0 }
            if (l != c) return l > c
        }
        return false
    }

    private fun parseVersion(raw: String): List<Int> =
        raw.trimStart('v', 'V')
            .split(".")
            .map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
}
