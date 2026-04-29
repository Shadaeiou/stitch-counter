package com.shadaeiou.stitchcounter.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class Updater(
    private val context: Context,
    private val owner: String,
    private val repo: String,
) {
    private val client = OkHttpClient.Builder().build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkForUpdate(currentVersionCode: Int): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/releases?per_page=10")
                .header("Accept", "application/vnd.github+json")
                .build()
            val res = runCatching { client.newCall(req).execute() }.getOrElse {
                return@withContext UpdateCheckResult.Error("network: ${it.message}")
            }
            res.use {
                if (!it.isSuccessful) {
                    return@withContext UpdateCheckResult.Error("HTTP ${it.code}")
                }
                val body = it.body?.string()
                    ?: return@withContext UpdateCheckResult.Error("empty body")
                val releases = runCatching {
                    json.decodeFromString<List<GithubRelease>>(body)
                }.getOrElse { e ->
                    return@withContext UpdateCheckResult.Error("parse: ${e.message}")
                }
                val best = releases.mapNotNull { rel ->
                    val parsed = parseVersionFromTag(rel.tagName) ?: return@mapNotNull null
                    val asset = rel.assets.firstOrNull { a -> a.name.endsWith(".apk") }
                        ?: return@mapNotNull null
                    Triple(parsed, asset, rel)
                }.maxByOrNull { (p, _, _) -> p.code }
                    ?: return@withContext UpdateCheckResult.Error("no APK release found")
                val (parsed, asset, release) = best
                if (parsed.code <= currentVersionCode) return@withContext UpdateCheckResult.UpToDate
                UpdateCheckResult.Available(UpdateInfo(parsed.code, parsed.name, asset.url, release.body))
            }
        }

    fun startDownload(info: UpdateInfo): Long {
        val req = DownloadManager.Request(Uri.parse(info.downloadUrl))
            .setTitle("$repo ${info.versionName}")
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setDestinationInExternalFilesDir(
                context, Environment.DIRECTORY_DOWNLOADS,
                "$repo-${info.versionCode}.apk",
            )
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return dm.enqueue(req)
    }

    suspend fun awaitDownload(id: Long): DownloadResult = withContext(Dispatchers.IO) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val q = DownloadManager.Query().setFilterById(id)
        while (true) {
            dm.query(q).use { c ->
                if (c.moveToFirst()) {
                    val statusIdx = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    when (c.getInt(statusIdx)) {
                        DownloadManager.STATUS_SUCCESSFUL ->
                            return@withContext DownloadResult.Success
                        DownloadManager.STATUS_FAILED -> {
                            val reasonIdx = c.getColumnIndex(DownloadManager.COLUMN_REASON)
                            return@withContext DownloadResult.Failure("reason ${c.getInt(reasonIdx)}")
                        }
                    }
                } else {
                    return@withContext DownloadResult.Failure("download disappeared")
                }
            }
            delay(500)
        }
        @Suppress("UNREACHABLE_CODE") DownloadResult.Failure("unreachable")
    }

    fun launchInstall(id: Long) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = dm.getUriForDownloadedFile(id) ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }
}

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("body") val body: String? = null,
    @SerialName("assets") val assets: List<Asset> = emptyList(),
)

@Serializable
private data class Asset(
    @SerialName("name") val name: String,
    @SerialName("browser_download_url") val url: String,
)

private data class ParsedVersion(val code: Int, val name: String)

private fun parseVersionFromTag(tag: String): ParsedVersion? {
    val cleaned = tag.removePrefix("v")
    val plus = cleaned.lastIndexOf('+')
    if (plus < 1 || plus == cleaned.length - 1) return null
    val code = cleaned.substring(plus + 1).toIntOrNull() ?: return null
    return ParsedVersion(code, cleaned.substring(0, plus))
}
