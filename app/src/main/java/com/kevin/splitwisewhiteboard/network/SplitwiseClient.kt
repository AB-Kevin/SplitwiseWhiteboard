package com.kevin.splitwisewhiteboard.network

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

data class GroupSummary(val id: Long, val name: String)

/** The Splitwise session cookie is missing, invalid, or expired. */
class SplitwiseAuthException(message: String) : IOException(message)

/**
 * Talks to the Splitwise *website's* internal endpoints (session-cookie +
 * CSRF authenticated), not the public OAuth developer API — because the
 * whiteboard feature isn't exposed by Splitwise's public API at all. These
 * endpoints were found by inspecting the site's own network traffic in a
 * browser; they are not documented or supported by Splitwise and could
 * change or break at any time.
 */
object SplitwiseClient {

    private const val BASE = "https://secure.splitwise.com"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Mobile Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val csrfMetaTagRegex =
        Regex("""<meta\s+name="csrf-token"\s+content="([^"]+)"""")

    /** Fetches a fresh CSRF token by loading the (server-rendered) app shell. */
    private fun fetchCsrfToken(cookieHeader: String): String {
        val request = Request.Builder()
            .url(BASE)
            .header("Cookie", cookieHeader)
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw SplitwiseAuthException(
                    "Could not load Splitwise (HTTP ${response.code}) to fetch a CSRF token"
                )
            }
            val body = response.body?.string().orEmpty()
            return csrfMetaTagRegex.find(body)?.groupValues?.get(1)
                ?: throw SplitwiseAuthException(
                    "Could not find a CSRF token on the Splitwise page — you may need to log in again"
                )
        }
    }

    /**
     * Calls the same internal endpoint the Splitwise web app uses to
     * bootstrap itself (get_main_data), and returns the raw JSON.
     */
    private fun fetchMainData(cookieHeader: String): JSONObject {
        val cacheBust = Random.nextDouble()
        val url = "$BASE/api/v3.0/get_main_data?no_expenses=1&limit=1&cachebust=$cacheBust"
        val request = Request.Builder()
            .url(url)
            .header("Cookie", cookieHeader)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("X-Requested-With", "XMLHttpRequest")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403) {
                throw SplitwiseAuthException("Splitwise session expired — please log in again")
            }
            if (!response.isSuccessful) {
                throw IOException("get_main_data failed: HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            return JSONObject(body)
        }
    }

    /**
     * Splitwise's internal response envelope isn't documented, so rather than
     * hard-code a JSON path (e.g. `groups[]`) that might not match, this
     * walks the whole response looking for group-shaped objects — anything
     * with both an "id" and a "whiteboard" key. That's resilient to
     * Splitwise reshaping the envelope around it.
     */
    private fun findGroupObjects(root: JSONObject): List<JSONObject> {
        val found = mutableListOf<JSONObject>()

        fun walk(value: Any?) {
            when (value) {
                is JSONObject -> {
                    if (value.has("id") && value.has("whiteboard")) {
                        found.add(value)
                    }
                    val keys = value.keys()
                    while (keys.hasNext()) {
                        walk(value.opt(keys.next()))
                    }
                }
                is JSONArray -> {
                    for (i in 0 until value.length()) {
                        walk(value.opt(i))
                    }
                }
            }
        }

        walk(root)
        return found
    }

    fun listGroups(cookieHeader: String): List<GroupSummary> {
        val data = fetchMainData(cookieHeader)
        return findGroupObjects(data).map { obj ->
            GroupSummary(id = obj.getLong("id"), name = obj.optString("name", "(unnamed group)"))
        }
    }

    /** Returns the whiteboard text for one group, or null if that group wasn't found. */
    fun getWhiteboard(cookieHeader: String, groupId: Long): String? {
        val data = fetchMainData(cookieHeader)
        val group = findGroupObjects(data).firstOrNull { it.getLong("id") == groupId } ?: return null
        return if (group.isNull("whiteboard")) "" else group.optString("whiteboard", "")
    }

    /** Saves new whiteboard text. Throws on failure. */
    fun setWhiteboard(cookieHeader: String, groupId: Long, text: String) {
        val csrfToken = fetchCsrfToken(cookieHeader)

        val formBody = FormBody.Builder()
            .add("id", groupId.toString())
            .add("whiteboard", text)
            .build()

        val request = Request.Builder()
            .url("$BASE/api/v3.0/update_group_settings")
            .header("Cookie", cookieHeader)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Origin", BASE)
            .header("Referer", "$BASE/")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("X-CSRF-Token", csrfToken)
            .post(formBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403) {
                throw SplitwiseAuthException("Splitwise session expired — please log in again")
            }
            if (!response.isSuccessful) {
                throw IOException("update_group_settings failed: HTTP ${response.code}")
            }
        }
    }
}
