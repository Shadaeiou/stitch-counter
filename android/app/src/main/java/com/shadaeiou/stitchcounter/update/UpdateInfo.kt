package com.shadaeiou.stitchcounter.update

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val notes: String?,
)

sealed class DownloadResult {
    data object Success : DownloadResult()
    data class Failure(val reason: String) : DownloadResult()
}

sealed class UpdateCheckResult {
    data object UpToDate : UpdateCheckResult()
    data class Available(val info: UpdateInfo) : UpdateCheckResult()
    data class Error(val reason: String) : UpdateCheckResult()
}
