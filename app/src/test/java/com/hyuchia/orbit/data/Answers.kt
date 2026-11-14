package com.hyuchia.orbit.data

import java.io.File

/**
 * What the person sharing answers about `files`, as they are now: each asked
 * about and chosen to go as it is ([AsTheyAreFiles]), and any other the way
 * out meets that Orbit can't clean left on this device, as one that can't
 * ask does ([AsTheyAreFiles.lasting]).
 */
internal fun chosenAsTheyAre(vararg files: File): AsTheyAreFiles {
    val stamps = files.mapNotNull { FileStamp.of(it) }.toSet()
    return AsTheyAreFiles(asked = stamps, chosen = stamps, stops = false)
}

/** What the person sharing answers about the files named `names` in the media folder of the map in `mapFolder`, as every way out reads them ([mapMediaFile]). */
internal fun chosenAsTheyAreIn(mapFolder: File, vararg names: String): AsTheyAreFiles = chosenAsTheyAre(*names.mapNotNull { mapMediaFile(mapFolder, it) }.toTypedArray())
