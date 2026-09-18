package com.apkmanager.app.adb

/** Result returned by an ADB shell command. */
data class ShellResult(
    val output: String,
    val exitCode: Int
) {
    val isSuccess: Boolean get() = exitCode == 0

    fun lines(): List<String> = output.lines().filter { it.isNotBlank() }
}
