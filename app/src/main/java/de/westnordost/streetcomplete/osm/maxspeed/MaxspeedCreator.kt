package de.westnordost.streetcomplete.osm.maxspeed

import de.westnordost.streetcomplete.osm.maxspeed.RoadType.*
import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.osm.expandDirections
import de.westnordost.streetcomplete.osm.hasCheckDateForKey
import de.westnordost.streetcomplete.osm.mergeDirections
import de.westnordost.streetcomplete.osm.updateCheckDateForKey
import de.westnordost.streetcomplete.util.ktx.toYesNo

/** Apply the maxspeed and type for each direction to the given [tags], with optional [vehicleType],
 *  e.g. "hgv" for "maxspeed:hgv. */

fun ForwardAndBackwardMaxspeedAndType.applyTo(tags: Tags, vehicleType: String?) {
    if (forward == null && backward == null) return
    val veh = if (vehicleType != null) ":$vehicleType" else ""
    /* for being able to modify only one direction (e.g. `forward` is null while `backward` is not null),
       the directions conflated in the default keys need to be separated first. e.g. `maxspeed=60`
       when forward direction is made `50` should become
       - maxspeed:backward=60
       - maxspeed:forward=50
       First separating the values and then later conflating them again, if possible, solves this.
     */
    MAXSPEED_KEYS.forEach {
        tags.expandDirections("$it$veh", null)
        tags.expandDirections("$it$veh", "conditional")
        tags.expandDirections("$it$veh:lanes", null)
        tags.expandDirections("$it$veh:lanes", "conditional")
    }
    MAXSPEED_TYPE_KEYS.forEach {
        tags.expandDirections("$it$veh", null)
        tags.expandDirections("$it$veh", "conditional")
    }

    forward?.applyTo(tags, vehicleType)
    backward?.applyTo(tags, vehicleType)

    MAXSPEED_KEYS.forEach {
        tags.mergeDirections("$it$veh", null)
        tags.mergeDirections("$it$veh", "conditional")
        tags.mergeDirections("$it$veh:lanes", null)
        tags.mergeDirections("$it$veh:lanes", "conditional")
    }
    MAXSPEED_TYPE_KEYS.forEach {
        tags.mergeDirections("$it$veh", null)
        tags.mergeDirections("$it$veh", "conditional")
    }

    // Unless an explicit speed limit is provided, changing to a living street or school zone
    // removes all maxspeed tagging because we are in the context of speed limits. So the user was
    // shown the current speed limit/type and answered that in fact it is a living street/school
    // zone, thereby saying that what was tagged before was wrong.
    // If the user also provides an explicit maxspeed then we tag the type as "xx:living_street"
    // to make it clear. There is no equivalent accepted tag for school zones, so
    // "hazard=school_zone" suffices
    if ((forward?.type is LivingStreet || backward?.type is LivingStreet) && forward != backward) {
        throw IllegalStateException("Living street, but differs by direction")
    } else if (forward?.type is LivingStreet) {
        if (vehicleType != null) {
            throw IllegalStateException("Attempting to tag living street only for certain vehicle, $vehicleType")
        }
        // Explicit value has already been set above, if it was provided
        // Type has been set as "xx:living_street" - remove that if there is no explicit value
        // Also keep type if it is a school zone (school zone takes  precedence over living street
        // in parsing because there's no speed limit type tag for school zones)
        if (forward.explicit == null && !isSchoolZone(tags)) {
            MAXSPEED_TYPE_KEYS.forEach {
                if (tags[it]?.let { it1 -> isLivingStreetMaxspeed(it1) } == true) { tags.remove(it) }
            }
        }
        // Don't change highway type if "living_street" is set
        if (tags["highway"] != "living_street" && tags["living_street"] != "yes") {
            tags["highway"] = "living_street"
            // In case "living_street" was set to e.g. "no"
            // (values other than yes and no are so rarely used that they are not worth dealing with
            tags.remove("living_street")
        }
    }
    if ((forward?.type is IsSchoolZone || backward?.type is IsSchoolZone) && forward != backward) {
        // zones surely apply to whole road, not just directions
        // also, hazard:forward or :backward=school_zone has not been used at all
        throw IllegalStateException("School zone, but differs by direction")
    } else if (forward?.type is IsSchoolZone) {
        if (vehicleType != null) {
            throw IllegalStateException("Attempting to tag school zone only for certain vehicle, $vehicleType")
        }
        tags["hazard"] = "school_zone"
    }

    // update check date
    if (!tags.hasChanges || tags.hasCheckDateForKey("maxspeed")) {
        tags.updateCheckDateForKey("maxspeed")
    }
}
fun MaxspeedAndType.applyTo(tags: Tags, vehicleType: String? = null) {
    val veh = if (vehicleType != null) ":$vehicleType" else ""
    val speedKey = "maxspeed$veh"
    val previousSpeedOsmValue = tags[speedKey]

    val maxspeedTypeKey = "maxspeed:type$veh"
    val sourceMaxspeedKey = "source:maxspeed$veh"
    val zoneMaxspeedKey = "zone:maxspeed$veh"
    val zoneTrafficKey = "zone:traffic$veh"

    // e.g. "maxspeed="RU:zone30" and "maxspeed:type=sign" is valid as a zone can be signed
    val isSignedZone = explicit is MaxSpeedZone && type is JustSign
    val signedZoneKey = when {
        tags.containsKey(maxspeedTypeKey) -> maxspeedTypeKey
        tags.containsKey(sourceMaxspeedKey) -> sourceMaxspeedKey
        else -> maxspeedTypeKey
    }

    // Preserve "source:maxspeed" if it exists and is not a valid maxspeed type
    val preserveSourceMaxspeed =
        tags[sourceMaxspeedKey] != null &&
        !isValidMaxspeedType(tags[sourceMaxspeedKey]) &&
        !SOURCE_MAXSPEED_VALUES_THAT_CAN_BE_REMOVED.contains(tags[sourceMaxspeedKey])

    // Take a single type key in this order of preference
    val previousTypeKey = when {
        // Use "maxspeed" for type if it was used before and there is no numerical speed limit to tag
        isValidMaxspeedType(tags[speedKey]) && explicit == null -> speedKey
        // the only way we should have a signed zone is if was like that before
        isSignedZone ->  speedKey
        tags.containsKey(maxspeedTypeKey) -> maxspeedTypeKey
        // Only take "source:maxspeed" if is a valid type (not actually being used as "source")
        tags.containsKey(sourceMaxspeedKey) && isValidMaxspeedType(tags[sourceMaxspeedKey]) -> sourceMaxspeedKey
        tags.containsKey(zoneMaxspeedKey) -> zoneMaxspeedKey
        tags.containsKey(zoneTrafficKey) -> zoneTrafficKey
        else -> null
    }

    val previousTypeOsmValue = tags[previousTypeKey]

    // Use the same type key as before, or else use "maxspeed:type"
    var typeKey = previousTypeKey ?: maxspeedTypeKey

    // "sign" is only valid in "maxspeed:type" and "source:maxspeed"
    if (type is JustSign && (typeKey != maxspeedTypeKey || typeKey != sourceMaxspeedKey)) {
        // don't change type key if it's a signed zone, as special treatment is needed to tag both sign and zone
        if (!isSignedZone) {
            typeKey = maxspeedTypeKey
        }
    }

    // We may have a maxspeed zone in type and no explicit value given
    val speedOsmValue = explicit?.toSpeedOsmValue() ?: type?.toSpeedOsmValue()

    // zone:maxspeed takes a different format for zone tagging
    val typeOsmValue = when {
        typeKey == zoneMaxspeedKey -> type?.toTypeOsmValueZoneMaxspeed()
        isSignedZone -> explicit?.toTypeOsmValue()
        else -> type?.toTypeOsmValue()
    }

    // TODO put this somewhere else or deal with it better
    if (type is ImplicitMaxSpeed && type.roadType == UNKNOWN) {
        throw IllegalStateException("Attempting to tag unknown road type")
    }

    // maxspeed is now not set
    // do this first in case "maxspeed" is used for the type
    if (speedOsmValue == null && previousSpeedOsmValue != null) {
        when (vehicleType) {
            null -> tags.removeMaxspeedTaggingForAllVehicles()
            else -> tags.removeMaxspeedTagging(vehicleType)
        }
    }
    // not doing the same if type is not set as that should not happen, it will at least be "sign"
    // or if we are marking living street or school zone then that is dealt with separately

    // if type has changed then remove all possible old tagging
    if (typeOsmValue != null && typeOsmValue != previousTypeOsmValue) {
        when (vehicleType) {
            null -> {
                tags.removeMaxspeedTaggingForAllVehicles()
                tags.removeMaxspeedTypeTaggingForAllVehicles(preserveSourceMaxspeed)
            }
            else -> {
                tags.removeMaxspeedTagging(vehicleType)
                tags.removeMaxspeedTypeTagging(vehicleType, preserveSourceMaxspeed)
            }
        }

        tags[typeKey] = typeOsmValue
        if (isSignedZone) {
            tags[signedZoneKey] = "sign"
        }
        // TODO what if different vehicles or directions provide different lit values? (lit:forward and lit:backward do not exist)
        if (type is ImplicitMaxSpeed) {
            // Lit is either already set or has been answered by the user, so this wouldn't change the value of the lit tag
            type.lit?.let { tags["lit"] = it.toYesNo() }
        }
        // If user was shown that it was a school zone and selected something else, remove school zone tag
        if (previousTypeOsmValue == null && tags["hazard"] == "school_zone" && vehicleType == null) {
            tags.remove("hazard")
        }
    }

    // if maxspeed has changed remove all old maxspeed tagging but not type tagging
    // if it's a signed maxspeed zone then don't remove the (just tagged) "maxspeed=XX:zonexx"
    if (speedOsmValue != null && speedOsmValue != previousSpeedOsmValue && !isSignedZone) {
        when (vehicleType) {
            null -> tags.removeMaxspeedTaggingForAllVehicles()
            else -> tags.removeMaxspeedTagging(vehicleType)
        }
        tags[speedKey] = speedOsmValue
    }
}

fun Tags.removeMaxspeedTagging(vehicleType: String?) {
    val veh = if (vehicleType != null) ":$vehicleType" else ""
    MAXSPEED_KEYS.forEach { remove("$it$veh") }
    MAXSPEED_KEYS.forEach { remove("$it$veh:conditional") }
    MAXSPEED_KEYS.forEach { remove("$it$veh:lanes") }
    MAXSPEED_KEYS.forEach { remove("$it$veh:lanes:conditional") }
}

fun Tags.removeMaxspeedTaggingForAllVehicles() {
    this.removeMaxspeedTagging(null)
    VEHICLE_TYPES.forEach { this.removeMaxspeedTagging(it) }
}

fun Tags.removeMaxspeedTypeTagging(vehicleType: String?, preserveSourceMaxspeed: Boolean) {
    val veh = if (vehicleType != null) ":$vehicleType" else ""
    if (preserveSourceMaxspeed) {
        MAXSPEED_TYPE_KEYS_EXCEPT_SOURCE.forEach { remove("$it$veh") }
    } else {
        MAXSPEED_TYPE_KEYS.forEach { remove("$it$veh") }
    }
}

fun Tags.removeMaxspeedTypeTaggingForAllVehicles(preserveSourceMaxspeed: Boolean) {
    this.removeMaxspeedTypeTagging(null, preserveSourceMaxspeed)
    VEHICLE_TYPES.forEach { this.removeMaxspeedTypeTagging(it, preserveSourceMaxspeed) }
}
