package org.openhab.habdroid.wear.shared.sync

/**
 * Version compatibility checker used by both phone and watch modules.
 * Determines whether sync should be blocked based on version mismatch.
 */
object VersionCompat {

    private const val DEV_SUFFIX = ".dev"

    /**
     * Returns true if sync should be blocked due to a version mismatch.
     *
     * Rules:
     * - If either version ends with ".dev", never block (development builds).
     * - Only the first two version segments (major.minor) must match.
     *   Patch/bugfix differences are allowed (e.g., 1.5.0 phone + 1.5.1 watch = OK).
     */
    fun shouldBlockSync(phoneVersion: String, watchVersion: String): Boolean {
        if (isDevBuild(phoneVersion) || isDevBuild(watchVersion)) return false
        return majorMinor(phoneVersion) != majorMinor(watchVersion)
    }

    /**
     * Returns true if the given versionName represents a development build.
     */
    fun isDevBuild(versionName: String): Boolean {
        return versionName.endsWith(DEV_SUFFIX)
    }

    /**
     * Strips the ".dev" suffix to get the base version for display purposes.
     * e.g., "1.3.0.dev" → "1.3.0"
     */
    fun baseVersion(versionName: String): String {
        return versionName.removeSuffix(DEV_SUFFIX)
    }

    /**
     * Extracts the first two version segments (major.minor).
     * e.g., "1.5.2" → "1.5", "1.5.0.dev" → "1.5"
     */
    private fun majorMinor(versionName: String): String {
        val parts = baseVersion(versionName).split(".")
        return if (parts.size >= 2) "${parts[0]}.${parts[1]}" else versionName
    }
}
