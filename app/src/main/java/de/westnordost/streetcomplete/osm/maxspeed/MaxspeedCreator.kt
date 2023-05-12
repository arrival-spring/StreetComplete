package de.westnordost.streetcomplete.osm.maxspeed

import de.westnordost.streetcomplete.osm.Tags

/** Apply the maxspeed and type to the given [tags], with optional [direction], e.g. "forward" for
 *  "maxspeed:forward. */
fun MaxspeedAndType.applyTo(tags: Tags, direction: String? = null) {
    val dir = if (direction != null) ":$direction" else ""
    val speedKey = "maxspeed$dir"
    val previousSpeedOsmValue = tags[speedKey]
    var preserveSourceMaxspeed = false

    // Take a single type key in this order of preference
    var previousTypeKey = when {
        tags.containsKey("maxspeed:type$dir") -> "maxspeed:type$dir"
        tags.containsKey("source:maxspeed$dir") -> "source:maxspeed$dir"
        tags.containsKey("zone:maxspeed$dir") -> "zone:maxspeed$dir"
        tags.containsKey("zone:traffic$dir") -> "zone:traffic$dir"
        else -> null
    }

    // If "source:maxspeed" is not a valid type then don't use that for the type key
    if (previousTypeKey == "source:maxspeed$dir" && !isValidMaxspeedType(tags[previousTypeKey])) {
        previousTypeKey = when {
            tags.containsKey("zone:maxspeed$dir") -> "zone:maxspeed$dir"
            tags.containsKey("zone:traffic$dir") -> "zone:traffic$dir"
            else -> null
        }
        preserveSourceMaxspeed = true
    }

    val previousTypeOsmValue = tags[previousTypeKey]

    var typeKey = previousTypeKey ?: "maxspeed:type$dir"

    // "sign" is only valid in "maxspeed:type" and "source:maxspeed"
    if (type is JustSign && (typeKey == "zone:maxspeed$dir" || typeKey == "zone:traffic$dir")) {
        typeKey = "maxspeed:type$dir"
    }

    // We may have a maxspeed zone in type and no explicit value given
    val speedOsmValue = explicit?.toSpeedOsmValue() ?: type?.toSpeedOsmValue()

    // zone:maxspeed takes a different format for zone tagging
    val typeOsmValue = if (typeKey == "zone:maxspeed") {
        type?.toTypeOsmValueZoneMaxspeed()
    } else {
        type?.toTypeOsmValue()
    }

    // if type has changed then remove all possible old tagging
    if (typeOsmValue != null && typeOsmValue != previousTypeOsmValue) {
        tags.removeMaxspeedTaggingForAllDirections()
        tags.removeMaxspeedTypeTaggingForAllDirections(preserveSourceMaxspeed)
        tags[typeKey] = typeOsmValue
    }

    // if maxspeed has changed remove all old maxspeed tagging but not type tagging
    if (speedOsmValue != null && speedOsmValue != previousSpeedOsmValue) {
        tags.removeMaxspeedTaggingForAllDirections()
        tags[speedKey] = speedOsmValue
    }

    // maxspeed is now not set
    if (speedOsmValue == null && previousSpeedOsmValue != null) {
        tags.removeMaxspeedTaggingForAllDirections()
    }
}

fun Tags.removeMaxspeedTagging(direction: String?) {
    val dir = if (direction != null) ":$direction" else ""
    VEHICLE_TYPES.forEach { remove("maxspeed:$it$dir") }
    VEHICLE_TYPES.forEach { remove("maxspeed:$it$dir:conditional") }
    VEHICLE_TYPES.forEach { remove("maxspeed:$it$dir:lanes") }
    VEHICLE_TYPES.forEach { remove("maxspeed:$it$dir:lanes:conditional") }
    MAXSPEED_KEYS.forEach { remove("$it$dir") }
    MAXSPEED_KEYS.forEach { remove("$it$dir:conditional") }
    MAXSPEED_KEYS.forEach { remove("$it$dir:lanes") }
    MAXSPEED_KEYS.forEach { remove("$it$dir:lanes:conditional") }
}

fun Tags.removeMaxspeedTaggingForAllDirections() {
    for (dir in listOf(null, "forward", "backward")) {
        this.removeMaxspeedTagging(dir)
    }
}

fun Tags.removeMaxspeedTypeTagging(direction: String?, preserveSourceMaxspeed: Boolean) {
    val dir = if (direction != null) ":$direction" else ""
    if (preserveSourceMaxspeed) {
        MAXSPEED_TYPE_KEYS_EXCEPT_SOURCE.forEach { remove("$it$dir") }
    } else {
        MAXSPEED_TYPE_KEYS.forEach { remove("$it$dir") }
    }
}

fun Tags.removeMaxspeedTypeTaggingForAllDirections(preserveSourceMaxspeed: Boolean) {
    for (dir in listOf(null, "forward", "backward")) {
        this.removeMaxspeedTypeTagging(dir, preserveSourceMaxspeed)
    }
}
