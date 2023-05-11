package de.westnordost.streetcomplete.osm.maxspeed

import de.westnordost.streetcomplete.osm.Tags

/** Apply the maxspeed and type to the given [tags], with optional [direction], e.g. "forward" for
 *  "maxspeed:forward. */
fun MaxspeedAndType.applyTo(tags: Tags, direction: String? = null) {
    val dir = if (direction != null) ":$direction" else ""
    val speedKey = "maxspeed$dir"
    val previousSpeedOsmValue = tags[speedKey]

    val previousTypeKey = when {
        tags.containsKey("maxspeed:type$dir") -> "maxspeed:type$dir"
        tags.containsKey("source:mxaspeed$dir") -> "source:maxspeed$dir"
        tags.containsKey("zone:maxspeed$dir") -> "zone:maxspeed$dir"
        tags.containsKey("zone:traffic$dir") -> "zone:traffic$dir"
        else -> null
    }
    val typeKey = previousTypeKey ?: "maxspeed:type$dir"
    val previousTypeOsmValue = tags[previousTypeKey]

    val speedOsmValue = explicit?.toSpeedOsmValue()

    // zone:maxspeed takes a different format for zone tagging
    val typeOsmValue = if (typeKey == "zone:maxspeed") {
        type?.toTypeOsmValueZoneMaxspeed()
    } else {
        type?.toTypeOsmValue()
    }

    // if maxspeed has changed remove all old maxspeed tagging but not type tagging
    if (speedOsmValue != null && speedOsmValue != previousSpeedOsmValue) {
        tags.removeMaxspeedTagging(direction)
        tags["maxspeed$dir"] = speedOsmValue
    }
    // maxspeed is now not set
    if (speedOsmValue == null && previousSpeedOsmValue != null) {
        tags.removeMaxspeedTagging(direction)
    }

    // if type has changed then remove all possible old tagging
    if (typeOsmValue != null && typeOsmValue != previousTypeOsmValue) {
        tags.removeMaxspeedTagging(direction)
        tags.removeMaxspeedTypeTagging(direction)
        tags["$typeKey$dir"] = typeOsmValue
    }
}

fun Tags.removeMaxspeedTagging(direction: String?) {
    val dir = if (direction != null) ":$direction" else ""
    VEHICLE_TYPES.forEach { remove("maxspeed:$it$dir") }
    VEHICLE_TYPES.forEach { remove("maxspeed:$it$dir:conditional") }
    MAXSPEED_KEYS.forEach { remove("$it$dir") }
    MAXSPEED_KEYS.forEach { remove("$it$dir:conditional") }
}

fun Tags.removeMaxspeedTypeTagging(direction: String?) {
    val dir = if (direction != null) ":$direction" else ""
    MAXSPEED_TYPE_KEYS.forEach { remove("$it$dir") }
}
