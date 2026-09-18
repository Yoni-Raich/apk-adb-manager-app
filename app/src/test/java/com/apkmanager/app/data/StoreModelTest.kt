package com.apkmanager.app.data

import com.apkmanager.app.data.store.StoreApp
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class StoreModelTest {

    @Test
    fun testStoreAppProperties() {
        val app = StoreApp(
            id = "youtube-downloader",
            name = "YouTube Downloader",
            description = "Download YouTube videos",
            githubRepo = "Yoni-Raich/youtube-downloader-releases",
            packageNames = listOf("com.splendid.ytdl"),
            category = "Media"
        )

        assertEquals("com.splendid.ytdl", app.primaryPackage)
        assertEquals("https://github.com/Yoni-Raich/youtube-downloader-releases", app.repoUrl)
    }

    @Test
    fun testStoreAppsJsonValidity() {
        val jsonFile = listOf(
            File("src/main/assets/store_apps.json"),
            File("../store_apps.json"),
            File("store_apps.json")
        ).firstOrNull { it.exists() }
        assertNotNull("store_apps.json should exist in assets or project root", jsonFile)

        val jsonString = jsonFile!!.readText()
        assertTrue("store_apps.json should contain youtube-downloader", jsonString.contains("youtube-downloader"))
        assertTrue("store_apps.json should contain streamflix", jsonString.contains("streamflix"))
        assertTrue("store_apps.json should contain hey-mike", jsonString.contains("hey-mike"))
        assertTrue("store_apps.json should contain wall-climber", jsonString.contains("wall-climber"))
        assertTrue("store_apps.json should contain com.splendid.ytdl", jsonString.contains("com.splendid.ytdl"))
        assertTrue("store_apps.json should contain com.example.wallclimber", jsonString.contains("com.example.wallclimber"))
        assertTrue("store_apps.json should contain Yoni-Raich/youtube-downloader-releases", jsonString.contains("Yoni-Raich/youtube-downloader-releases"))
        assertTrue("store_apps.json should contain Haim098/streamflix-releases", jsonString.contains("Haim098/streamflix-releases"))
        assertTrue("store_apps.json should contain Yoni-Raich/hey-mike", jsonString.contains("Yoni-Raich/hey-mike"))
        assertTrue("store_apps.json should contain Yoni-Raich/wall-climber", jsonString.contains("Yoni-Raich/wall-climber"))
    }
}
