package com.hyuchia.orbit.data

import android.content.Context
import android.content.pm.PackageManager
import com.hyuchia.orbit.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * This device as it describes itself to the others that keep their maps in
 * the same folder (see [FolderDevice]), named as nearby devices see it
 * (`name`). Its id is kept in a file of its own, outside what Android backs
 * up and moves to a new phone, so a new phone is a new device and never
 * takes the old one's place in the folder. Beside it is when Orbit was
 * installed as the id was made: an id copied to another install with the
 * rest of Orbit's files, as a cloning backup tool copies them, isn't that
 * install's, which makes its own, so two installs never write over each
 * other's records in the folder.
 */
class ThisDevice(private val context: Context, private val name: suspend () -> String) {
    private val idFile = File(context.filesDir, "folder-device")
    private val installFile = File(context.filesDir, "folder-device-installed")

    suspend fun describe(): FolderDevice = FolderDevice(
        id = id(),
        name = name(),
        kind = deviceKind(context),
        appVersion = BuildConfig.VERSION_NAME,
        appVersionCode = BuildConfig.VERSION_CODE,
        mapFormat = MAP_FORMAT,
        mediaTypes = carriedMediaTypes().sorted(),
        synced = System.currentTimeMillis(),
    )

    private suspend fun id(): String = withContext(Dispatchers.IO) {
        val installed = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime.toString() }.getOrNull()
        val kept = idFile.takeIf { it.isFile }?.readText()?.trim()?.takeIf { ID.matches(it) }
        val keptInstall = installFile.takeIf { it.isFile }?.readText()?.trim()
        val id = keptDeviceId(kept, keptInstall, installed) ?: UUID.randomUUID().toString().also { writeAtomically(idFile, it) }

        if (installed != null && keptInstall != installed) {
            writeAtomically(installFile, installed)
        }

        id
    }

    private companion object {
        val ID = Regex("[A-Za-z0-9-]{8,64}")
    }
}

/**
 * The device id `kept`, unless another install made it: `keptInstall` says
 * when the install that made it was installed, and `installed` when this one
 * was. Kept from before installs were written down, an id is this install's.
 */
internal fun keptDeviceId(kept: String?, keptInstall: String?, installed: String?): String? =
    kept?.takeIf { installed == null || keptInstall == null || keptInstall == installed }

/** What kind of device this is, for its icon: a computer (a Chromebook), a tablet (600 dp wide or more either way), or a phone. */
fun deviceKind(context: Context, smallestWidth: Int = context.resources.configuration.smallestScreenWidthDp): String {
    val packages = context.packageManager

    return when {
        packages.hasSystemFeature(PackageManager.FEATURE_PC) || packages.hasSystemFeature("org.chromium.arc") -> FolderDevice.COMPUTER
        smallestWidth >= 600 -> FolderDevice.TABLET
        else -> FolderDevice.PHONE
    }
}
