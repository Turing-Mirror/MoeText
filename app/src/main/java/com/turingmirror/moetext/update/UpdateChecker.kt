package com.turingmirror.moetext.update

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import com.turingmirror.moetext.data.readLimitedText

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val changelog: List<String>,
    val releasePage: String
)

object UpdateChecker {

    private const val LATEST_URL =
        "https://cnb.cool/Turing-Mirror/MoeText-Releases/-/git/raw/main/latest.json"
    private const val FALLBACK_PAGE =
        "https://github.com/Turing-Mirror/MoeText/releases/latest"
    private val MAIN = Handler(Looper.getMainLooper())

    fun currentVersionName(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

    fun currentVersionCode(ctx: Context): Int = try {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else info.versionCode
    } catch (e: Exception) {
        0
    }

    fun check(context: Context, onResult: (Result<UpdateInfo?>) -> Unit) {
        val appContext = context.applicationContext
        Thread {
            val outcome = fetchLatest()?.let { json ->
                val remoteCode = json.optInt("versionCode", 0)
                if (remoteCode > currentVersionCode(appContext)) {
                    val notes = mutableListOf<String>()
                    val arr = json.optJSONArray("changelog")
                    if (arr != null) for (i in 0 until arr.length()) notes.add(arr.optString(i))
                    Result.success(
                        UpdateInfo(
                            versionCode = remoteCode,
                            versionName = json.optString("versionName", ""),
                            changelog = notes,
                            releasePage = json.optString(
                                "releasePage",
                                json.optString("url", FALLBACK_PAGE)
                            ).takeIf { page -> runCatching { URL(page).protocol == "https" }.getOrDefault(false) } ?: FALLBACK_PAGE
                        )
                    )
                } else {
                    Result.success(null)
                }
            } ?: Result.failure(IllegalStateException("fetch failed"))
            MAIN.post { runCatching { onResult(outcome) } }
        }.start()
    }

    private fun fetchLatest(): JSONObject? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(LATEST_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/json")
            val body = conn.inputStream.bufferedReader().use {
                it.readLimitedText(65536)
            }
            JSONObject(body)
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
