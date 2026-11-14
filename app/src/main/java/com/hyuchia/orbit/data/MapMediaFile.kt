package com.hyuchia.orbit.data

import java.io.File
import java.io.IOException

/**
 * The file named `name` in the media folder of the map in `mapFolder`, as
 * everything that leaves the phone reads it, or null when there's none
 * there: a name Orbit gives one ([MediaNames.NAME]), and a file that is
 * itself in that folder, never through a link, whether the file, the media
 * folder or the map's folder is one. The folders above the map's may be
 * reached through links, as an app's own storage is on many phones
 * (`/data/user/0` is `/data/data`). It's the file as its storage names it
 * (its canonical path), so what's planned, approved and copied for it is
 * said of one path.
 */
internal fun mapMediaFile(mapFolder: File, name: String): File? {
    if (!MediaNames.NAME.matches(name)) {
        return null
    }

    return try {
        val folder = mapFolder.canonicalFile
        val parent = mapFolder.absoluteFile.parentFile?.canonicalFile ?: return null
        val file = File(File(folder, MEDIA_DIRECTORY), name)

        // The map's folder no link, and the file reached as it's named there, through no link on the way.
        if (folder != File(parent, mapFolder.name) || File(File(mapFolder, MEDIA_DIRECTORY), name).canonicalFile != file || !file.isFile) {
            null
        } else {
            file
        }
    } catch (unreadable: IOException) {
        null
    } catch (refused: SecurityException) {
        null
    }
}
