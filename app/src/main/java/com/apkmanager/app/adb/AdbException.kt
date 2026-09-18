package com.apkmanager.app.adb

class AdbException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
