package com.apkmanager.app

import android.app.Application
import com.flyfishxu.kadb.cert.KadbCert
import com.apkmanager.app.repository.AdbRepository
import com.apkmanager.app.repository.PackageRepository
import java.io.File

/**
 * Application class for the APK Manager app.
 * Initializes shared repositories for dependency injection.
 */
class ApkManagerApplication : Application() {

    lateinit var adbRepository: AdbRepository
        private set

    lateinit var packageRepository: PackageRepository
        private set

    lateinit var appUpdateRepository: com.apkmanager.app.repository.AppUpdateRepository
        private set

    lateinit var storeRepository: com.apkmanager.app.repository.StoreRepository
        private set

    lateinit var selfUpdateRepository: com.apkmanager.app.repository.SelfUpdateRepository
        private set

    override fun onCreate() {
        super.onCreate()

        configureKadbIdentity()

        adbRepository = AdbRepository(this)
        packageRepository = PackageRepository(adbRepository)
        appUpdateRepository = com.apkmanager.app.repository.AppUpdateRepository(this, adbRepository.getPreferences(), adbRepository)
        storeRepository = com.apkmanager.app.repository.StoreRepository(this, adbRepository)
        selfUpdateRepository = com.apkmanager.app.repository.SelfUpdateRepository(this, adbRepository)
    }

    private fun configureKadbIdentity() {
        val identityDirectory = File(filesDir, "kadb_identity").apply { mkdirs() }
        val certificateFile = File(identityDirectory, "certificate.pem")
        val privateKeyFile = File(identityDirectory, "private_key.pem")

        if (certificateFile.exists() && privateKeyFile.exists()) {
            try {
                KadbCert.set(certificateFile.readBytes(), privateKeyFile.readBytes())
                return
            } catch (_: Exception) {
                certificateFile.delete()
                privateKeyFile.delete()
            }
        }

        val (certificate, privateKey) = KadbCert.get()
        certificateFile.writeBytes(certificate)
        privateKeyFile.writeBytes(privateKey)
    }
}
