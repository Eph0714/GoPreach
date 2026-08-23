package com.emfitsolutions.gopreach.data.update

/**
 * The update manifest for this app, sourced from GitHub Releases' own public
 * API rather than a bespoke backend — see [UpdateManifestRepository] for why.
 * Every field here maps directly onto GitHub's release/asset JSON.
 */
data class UpdateInfo(
    val version: String,
    val apkUrl: String,
    val releaseNotes: String,
    val releaseDate: String,
    val sha256: String?,
)
