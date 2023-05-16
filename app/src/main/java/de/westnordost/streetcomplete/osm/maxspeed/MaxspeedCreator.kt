package de.westnordost.streetcomplete.osm.maxspeed

import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.util.ktx.toYesNo

/** Apply the maxspeed and type to the given [tags], with optional [direction], e.g. "forward" for
 *  "maxspeed:forward.
 *  TODO: extend to forward and backward */
fun MaxspeedAndType.applyTo(tags: Tags, direction: String? = null) {
    val dir = if (direction != null) ":$direction" else ""
    val speedKey = "maxspeed$dir"
    val previousSpeedOsmValue = tags[speedKey]

    // e.g. "maxspeed="RU:zone30" and "maxspeed:type=sign" is valid as a zone can be signed
    val isSignedZone = explicit is MaxSpeedZone && type is JustSign
    val signedZoneKey = when {
        tags.containsKey("maxspeed:type$dir") -> "maxspeed:type$dir"
        tags.containsKey("source:maxspeed$dir") -> "source:maxspeed$dir"
        else -> "maxspeed:type$dir"
    }

    // Preserve "source:maxspeed" if it exists and is not a valid maxspeed type
    val preserveSourceMaxspeed = tags["source:maxspeed$dir"] != null && !isValidMaxspeedType(tags["source:maxspeed$dir"])

    // Take a single type key in this order of preference
    val previousTypeKey = when {
        // Use "maxspeed" for type if it was used before and there is no numerical speed limit to tag
        isValidMaxspeedType(tags["maxspeed$dir"]) && explicit == null -> "maxspeed$dir"
        // the only way we should have signed zone is if was like that before
        isSignedZone ->  "maxspeed$dir"
        tags.containsKey("maxspeed:type$dir") -> "maxspeed:type$dir"
        // Only take "source:maxspeed" if is a valid type (not actually being used as "source")
        tags.containsKey("source:maxspeed$dir") && isValidMaxspeedType(tags["source:maxspeed$dir"]) -> "source:maxspeed$dir"
        tags.containsKey("zone:maxspeed$dir") -> "zone:maxspeed$dir"
        tags.containsKey("zone:traffic$dir") -> "zone:traffic$dir"
        else -> null
    }

    val previousTypeOsmValue = tags[previousTypeKey]

    // Use the same type key as before, or else use "maxspeed:type"
    var typeKey = previousTypeKey ?: "maxspeed:type$dir"

    // "sign" is only valid in "maxspeed:type" and "source:maxspeed"
    if (
        type is JustSign &&
        (typeKey != "maxspeed:type$dir" || typeKey != "source:maxspeed$dir")
    ) {
        // don't change type key if it's a signed zone, as special treatment is needed to tag both sign and zone
        if (!isSignedZone) {
            typeKey = "maxspeed:type$dir"
        }
    }

    // We may have a maxspeed zone in type and no explicit value given
    val speedOsmValue = explicit?.toSpeedOsmValue() ?: type?.toSpeedOsmValue()

    // zone:maxspeed takes a different format for zone tagging
    val typeOsmValue = if (typeKey == "zone:maxspeed$dir") {
        type?.toTypeOsmValueZoneMaxspeed()
    } else if (isSignedZone) {
        explicit?.toTypeOsmValue()
    } else {
        type?.toTypeOsmValue()
    }

    // maxspeed is now not set
    // do this first in case "maxspeed" is used for the type
    if (speedOsmValue == null && previousSpeedOsmValue != null) {
        tags.removeMaxspeedTaggingForAllDirections()
    }
    // not doing the same if type is not set as that should not happen, it will at least be "sign"
    // or if we are marking living street or school zone then that is dealt with below

    // if type has changed then remove all possible old tagging
    if (typeOsmValue != null && typeOsmValue != previousTypeOsmValue) {
        tags.removeMaxspeedTaggingForAllDirections()
        tags.removeMaxspeedTypeTaggingForAllDirections(preserveSourceMaxspeed)
        tags[typeKey] = typeOsmValue
        if (isSignedZone) {
            tags[signedZoneKey] = "sign"
        }
        if (type is ImplicitMaxSpeed) {
            // Lit is either already set or has been answered by the user, so this wouldn't change the value of the lit tag
            type.lit?.let { tags["lit"] = it.toYesNo() }
        }
        // If user was shown that it was a school zone and selected something else remove school zone tag
        if (previousTypeOsmValue == null && tags["hazard"] == "school_zone") {
            tags.remove("hazard")
        }
    }

    // if maxspeed has changed remove all old maxspeed tagging but not type tagging
    // if it's a signed maxspeed zone then don't remove the (just tagged) "maxspeed=XX:zonexx"
    if (speedOsmValue != null && speedOsmValue != previousSpeedOsmValue && !isSignedZone) {
        tags.removeMaxspeedTaggingForAllDirections()
        tags[speedKey] = speedOsmValue
    }

    // Unless an explicit speed limit is provided, changing to a living street or school zone
    // removes all maxspeed tagging because we are in the context of speed limits. So the user was
    // shown the current speed limit/type and answered that in fact it is a living street/school
    // zone, thereby saying that what was tagged before was wrong.
    // If the user also provides an explicit maxspeed then we tag the type as "xx:living_street"
    // to make it clear. There is no equivalent accepted tag for school zones, so
    // "hazard=school_zone" suffices
    if (type is LivingStreet) {
        tags.removeMaxspeedTypeTaggingForAllDirections(preserveSourceMaxspeed)
        if (speedOsmValue == null) {
            tags.removeMaxspeedTaggingForAllDirections()
        } else if (type.countryCode != null) {
            tags[typeKey] = "${type.countryCode}:living_street"
        }
        // Don't change highway type if "living_street" is set
        if (tags["highway"] != "living_street" && tags["living_street"] != "yes") {
            tags["highway"] = "living_street"
            // In case "living_street" was set to e.g. "no"
            // (values other than yes and no are so rarely used that they are not worth dealing with
            tags.remove("living_street")
        }
    }
    if (type == IsSchoolZone) {
        if (speedOsmValue == null) {
            tags.removeMaxspeedTaggingForAllDirections()
        }
        tags.removeMaxspeedTypeTaggingForAllDirections(preserveSourceMaxspeed)
        tags["hazard"] = "school_zone"
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
