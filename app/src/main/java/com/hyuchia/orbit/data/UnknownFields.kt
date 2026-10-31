package com.hyuchia.orbit.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * `written`, a value of `descriptor` as this version writes it, with the
 * fields `original` held that this version doesn't know put back, at every
 * depth: inside an object, and inside each value of a map. A newer version's
 * fields so stay in a file an older one writes again. The fields this
 * version knows are as `written` has them, gone ones staying gone.
 */
@OptIn(ExperimentalSerializationApi::class)
internal fun keepUnknownFields(written: JsonElement, original: JsonElement, descriptor: SerialDescriptor): JsonElement {
    if (written !is JsonObject || original !is JsonObject) {
        return written
    }

    return when (descriptor.kind) {
        StructureKind.CLASS -> {
            val known = descriptor.elementNames.toSet()

            val fields = written.mapValues { (name, value) ->
                val index = descriptor.getElementIndex(name)
                val before = original[name]

                if (index == CompositeDecoder.UNKNOWN_NAME || before == null) {
                    value
                } else {
                    keepUnknownFields(value, before, descriptor.getElementDescriptor(index))
                }
            }

            JsonObject(original.filterKeys { it !in known } + fields)
        }
        StructureKind.MAP -> {
            val values = descriptor.getElementDescriptor(1)
            JsonObject(written.mapValues { (key, value) -> original[key]?.let { keepUnknownFields(value, it, values) } ?: value })
        }
        else -> written
    }
}
