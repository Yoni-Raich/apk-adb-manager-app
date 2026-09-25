package com.apkmanager.app.adb

import org.junit.Assert.assertEquals
import org.junit.Test

class PackageListParserTest {

    @Test
    fun readsPackageNotInstallerWhenPathContainsEquals() {
        val output = """
            Error: java.lang.SecurityException: Shell does not have permission to access user 11
            package:/data/app/~~8Wg7pcuw2uCgU5BDeXdHFw==/tech.butterfly.app-ekUFShQ5mQaqt-eWkr6zJg==/base.apk=tech.butterfly.app  installer=com.android.vending
            package:/data/app/~~9bplGtJo4MQpn0zegY4_bg==/org.videolan.vlc-Bj3rYzKVH2IXyKr9VccGQQ==/base.apk=org.videolan.vlc  installer=null
            package:/system/app/Foo/Foo.apk=com.example.foo
        """.trimIndent()

        val entries = AdbClient.parsePackageList(output)

        assertEquals(listOf("com.example.foo", "org.videolan.vlc", "tech.butterfly.app"), entries.map { it.packageName })
        assertEquals("com.android.vending", entries.first { it.packageName == "tech.butterfly.app" }.installer)
        assertEquals("", entries.first { it.packageName == "org.videolan.vlc" }.installer)
        assertEquals("/system/app/Foo/Foo.apk", entries.first { it.packageName == "com.example.foo" }.apkPath)
    }
}
