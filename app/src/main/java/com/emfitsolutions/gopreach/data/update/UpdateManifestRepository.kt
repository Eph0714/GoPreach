package com.emfitsolutions.gopreach.data.update

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "UpdateManifest"

/**
 * The "update server" for GoPreach's auto-update system is GitHub Releases
 * itself — not a bespoke backend. GitHub already publishes exactly the fields
 * a proper update manifest needs (version, a stable per-release download URL,
 * release notes, release date, and a SHA-256 digest of the asset) via a free,
 * publicly-readable, unauthenticated, always-current API endpoint:
 * `repos/{owner}/{repo}/releases/latest`. Standing up and hosting a separate
 * update server (with its own uptime, security, and release-publishing
 * tooling to maintain) would just be reimplementing what this endpoint
 * already does reliably — GitHub's CDN serves the actual APK bytes too, so
 * there's no separate file host to run either. Publishing v1.2.0, v1.3.0,
 * etc. later is just `gh release create vX.Y.Z <apk>`; nothing here needs to
 * change to pick that up, since this always asks for "latest".
 */
@Singleton
class UpdateManifestRepository @Inject constructor(
    private val gson: Gson,
) {
    /** Public GitHub repo — no auth token needed for read access. */
    private val manifestUrl = "https://api.github.com/repos/Eph0714/GoPreach/releases/latest"

    private data class GithubAsset(
        val name: String,
        @SerializedName("browser_download_url") val browserDownloadUrl: String,
        val digest: String?,
    )

    private data class GithubRelease(
        @SerializedName("tag_name") val tagName: String,
        val body: String?,
        @SerializedName("published_at") val publishedAt: String?,
        val assets: List<GithubAsset>,
    )

    suspend fun fetchLatest(): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val connection = URL(manifestUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            // GitHub's REST API documents this as a hard requirement — a
            // request with no (or an unrecognized) User-Agent can be
            // rejected outright. HttpURLConnection's own JVM-default
            // User-Agent isn't guaranteed to satisfy that on every Android
            // build, so this is set explicitly rather than left to chance —
            // a request silently rejected here is exactly what "automatic
            // detection of new update is not working" looks like from the
            // user's side (nothing is shown either way; see [Idle]'s
            // handling below for why a failed *check* stays silent).
            connection.setRequestProperty("User-Agent", "GoPreach-Android")
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                // GitHub's unauthenticated REST API is capped at 60
                // requests/hour per IP; exceeding it returns 403 with
                // X-RateLimit-Remaining: 0. Called out specifically (rather
                // than folded into the generic HTTP-error message below) so
                // it's diagnosable from a logcat capture instead of looking
                // identical to every other kind of failure.
                val rateLimited = responseCode == 403 && connection.getHeaderField("X-RateLimit-Remaining") == "0"
                val message = if (rateLimited) {
                    "Update check failed: GitHub API rate limit exceeded (resets hourly)"
                } else {
                    val errorBody = runCatching { connection.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
                    "Update check failed (HTTP $responseCode)${errorBody?.let { ": $it" } ?: ""}"
                }
                Log.e(TAG, message)
                return@withContext Result.failure(Exception(message))
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val release = gson.fromJson(body, GithubRelease::class.java)
            // The APK asset — the one non-source-code file GitHub attaches to the release.
            val asset = release.assets.firstOrNull { it.name.endsWith(".apk") }
                ?: return@withContext Result.failure(Exception("Latest release has no APK asset").also { Log.e(TAG, it.message ?: "") })

            Result.success(
                UpdateInfo(
                    version = release.tagName.removePrefix("v"),
                    apkUrl = asset.browserDownloadUrl,
                    releaseNotes = release.body?.trim().takeUnless { it.isNullOrBlank() } ?: "Bug fixes and improvements.",
                    releaseDate = release.publishedAt?.substringBefore("T") ?: "",
                    sha256 = asset.digest?.removePrefix("sha256:"),
                )
            )
        } catch (e: Exception) {
            // Log.e, not Log.w — a silent [UpdateViewModel.check] drops this
            // straight to Idle with no UI of any kind (by design: don't
            // surface a transient network blip as an error dialog), which
            // means this log line is the *only* place the actual cause is
            // ever recorded. It was a warning before, easy to miss/filter
            // out of a real logcat capture while diagnosing exactly this
            // "not working" report.
            Log.e(TAG, "Update check failed", e)
            Result.failure(e)
        }
    }

    /** True when [latestVersion] is newer than [currentVersion] — both plain
     * dotted version strings (e.g. "1.0.0"), no "v" prefix. Compares numerically
     * per segment rather than as a string, so "1.9.0" < "1.10.0" correctly. */
    fun isNewer(currentVersion: String, latestVersion: String): Boolean {
        val current = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }
        val latest = latestVersion.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(current.size, latest.size)) {
            val c = current.getOrElse(i) { 0 }
            val l = latest.getOrElse(i) { 0 }
            if (l != c) return l > c
        }
        return false
    }
}
