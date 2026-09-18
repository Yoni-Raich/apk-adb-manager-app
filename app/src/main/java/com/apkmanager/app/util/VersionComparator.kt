package com.apkmanager.app.util

/**
 * Robust semantic version comparison utility.
 * Handles prefixes like 'v', build numbers, and pre-release identifiers.
 */
object VersionComparator {

    /**
     * Returns true ONLY if [remote] is strictly newer than [installed].
     * If both are equal, or if remote is older, returns false.
     */
    fun isNewerVersion(installed: String?, remote: String?): Boolean {
        if (installed.isNullOrBlank() || remote.isNullOrBlank()) return false

        val cleanInstalled = cleanVersionString(installed)
        val cleanRemote = cleanVersionString(remote)

        if (cleanInstalled == cleanRemote) return false

        val (instParts, instPre) = parseVersion(cleanInstalled)
        val (remParts, remPre) = parseVersion(cleanRemote)

        val maxLength = maxOf(instParts.size, remParts.size)
        for (i in 0 until maxLength) {
            val instNum = instParts.getOrElse(i) { 0 }
            val remNum = remParts.getOrElse(i) { 0 }
            if (remNum > instNum) return true
            if (remNum < instNum) return false
        }

        // If numeric parts are equal, check pre-release tags:
        // A release with NO prerelease tag (e.g. 1.0.0) is newer than a prerelease (e.g. 1.0.0-rc1).
        if (instPre != null && remPre == null) {
            // remote is stable, installed was prerelease -> remote is newer
            return true
        }
        if (instPre == null && remPre != null) {
            // remote is prerelease, installed is stable -> remote is NOT newer
            return false
        }

        return false
    }

    private fun cleanVersionString(version: String): String {
        return version.trim()
            .trimStart('v', 'V')
            .trim()
    }

    private fun parseVersion(clean: String): Pair<List<Int>, String?> {
        val splitDash = clean.split('-', limit = 2)
        val mainPart = splitDash[0]
        val preRelease = splitDash.getOrNull(1)

        val numbers = mainPart.split('.', '_').mapNotNull { part ->
            part.filter { it.isDigit() }.toIntOrNull()
        }

        return numbers to preRelease
    }
}
