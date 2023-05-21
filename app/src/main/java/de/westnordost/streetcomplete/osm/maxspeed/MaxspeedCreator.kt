package de.westnordost.streetcomplete.osm.maxspeed

import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.osm.expandDirections
import de.westnordost.streetcomplete.osm.hasCheckDateForKey
import de.westnordost.streetcomplete.osm.maxspeed.RoadType.UNKNOWN
import de.westnordost.streetcomplete.osm.mergeDirections
import de.westnordost.streetcomplete.osm.updateCheckDateForKey
import de.westnordost.streetcomplete.util.ktx.toYesNo

/** Apply the maxspeed and type for each direction to the given [tags], with optional [vehicleType],
 *  e.g. "hgv" for "maxspeed:hgv. */
fun ForwardAndBackwardMaxspeedAndType.applyTo(tags: Tags, vehicleType: String?) {
    if (forward == null && backward == null) return
    if (forward?.explicit == null && forward?.type == null && backward?.explicit == null && backward?.type == null) return
    anyTypeIsSigned = (forward?.type is JustSign) || (backward?.type is JustSign)
    /* for being able to modify only one direction (e.g. `forward` is null while `backward` is not null),
       the directions conflated in the default keys need to be separated first. e.g. `maxspeed=60`
       when forward direction is made `50` should become
       - maxspeed:backward=60
       - maxspeed:forward=50
       First separating the values and then later conflating them again, if possible, solves this.
     */

    // VEHICLE_TYPES does not contain "null" and we need to expand bare tags
    (setOf(null) + VEHICLE_TYPES).forEach { tags.expandAllMaxspeedTags(it) }

    forward?.applyTo(tags, "forward", vehicleType)
    backward?.applyTo(tags, "backward", vehicleType)

    (setOf(null) + VEHICLE_TYPES).forEach { tags.mergeAllMaxspeedTags(it) }

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
        // also, hazard:forward and :backward=school_zone have not been used at all
        throw IllegalStateException("School zone, but differs by direction")
    } else if (forward?.type is IsSchoolZone) {
        if (vehicleType != null) {
            throw IllegalStateException("Attempting to tag school zone only for certain vehicle, $vehicleType")
        }
        tags["hazard"] = "school_zone"
        tags.removeMaxspeedTypeTaggingForAllVehiclesAndDirections()
    }

    // update check date
    if (!tags.hasChanges || tags.hasCheckDateForKey("maxspeed")) {
        tags.updateCheckDateForKey("maxspeed")
    }
}

private fun Tags.expandAllMaxspeedTags(vehicleType: String?) {
    val veh = if (vehicleType != null) ":$vehicleType" else ""
    MAXSPEED_KEYS.forEach {
        this.expandDirections("$it$veh", null)
        this.expandDirections("$it$veh", "conditional")
        this.expandDirections("$it$veh", "lanes")
        this.expandDirections("$it$veh", "lanes:conditional")
    }
    MAXSPEED_TYPE_KEYS_EXCEPT_SOURCE.forEach {
        this.expandDirections("$it$veh", null)
        this.expandDirections("$it$veh", "conditional")
    }
    // Do not split source:maxspeed* if it is some special value that we will not touch
    if (canThisSourceMaxspeedBeRemoved(this["source:maxspeed$veh"])) {
        this.expandDirections("source:maxspeed$veh", null)
        this.expandDirections("source:maxspeed$veh", "conditional")
    }
}

private fun Tags.mergeAllMaxspeedTags(vehicleType: String?) {
    val veh = if (vehicleType != null) ":$vehicleType" else ""
    MAXSPEED_KEYS.forEach {
        this.mergeDirections("$it$veh", null)
        this.mergeDirections("$it$veh", "conditional")
        this.mergeDirections("$it$veh", ":lanes")
        this.mergeDirections("$it$veh", ":lanes:conditional")
    }
    // Only merge type tags if directions have been merged
    // i.e. maxspeed:forward=xx maxspeed:backward=yy should be combined with
    // maxspeed:type:forward=sign and maxspeed:type:backward=sign, not just maxspeed:type=sign
    MAXSPEED_TYPE_KEYS.forEach {
        if (!directionsExistAndDiffer(this, veh)) {
            this.mergeDirections("$it$veh", null)
            this.mergeDirections("$it$veh", "conditional")
        }
    }
}

private fun directionsExistAndDiffer(tags: Tags, veh: String): Boolean {
    return tags["maxspeed$veh:forward"] != null &&
        tags["maxspeed$veh:backward"] != null &&
        tags["maxspeed$veh:forward"] != tags["maxspeed$veh:backward"]
}

/** Return a single type key that was used before for the given postfix [post] to "maxspeed"
 *  In the case of multiple values existing, take the first one in the order of preference of
 *  type:maxspeed, source:maxspeed, zone:maxspeed and zone:traffic
 *  Will only be source:maxspeed if it was used for type before (not a special value or source value)
 *  null if no such key exists */
private fun getPreviousTypeKey(tags: Tags, post: String, explicitValueGiven: Boolean, isSignedZone: Boolean): String? {
    val speedKey = "maxspeed$post"
    val maxspeedTypeKey = "maxspeed:type$post"
    val sourceMaxspeedKey = "source:maxspeed$post"
    val zoneMaxspeedKey = "zone:maxspeed$post"
    val zoneTrafficKey = "zone:traffic$post"

    return when {
        // Use "maxspeed" for type if it was used before and there is no numerical speed limit to tag
        isValidMaxspeedType(tags[speedKey]) && !explicitValueGiven -> speedKey
        // the only way we should have a signed zone is if was like that before
        isSignedZone ->  speedKey
        tags.containsKey(maxspeedTypeKey) -> maxspeedTypeKey
        // Only take "source:maxspeed" if it is a valid type (not actually being used as "source")
        tags.containsKey(sourceMaxspeedKey) && isValidMaxspeedType(tags[sourceMaxspeedKey]) -> sourceMaxspeedKey
        tags.containsKey(zoneMaxspeedKey) -> zoneMaxspeedKey
        tags.containsKey(zoneTrafficKey) -> zoneTrafficKey
        else -> null
    }
}

/** Return a key to be used for the type. This is maxspeed:type if nothing was set before or the
 *  same as [previousTypeKey] unless that is not suitable for the new type.
 *  If there were different tags used for different vehicles/directions then always return
 *  the same value for all vehicles and directions, in the order of preference of type:maxspeed,
 *  source:maxspeed, zone:traffic, zone:maxspeed. */
private fun getTypeKey(tags: Tags, previousTypeKey: String?, post: String): String {
    val speedKey = "maxspeed$post"
    val maxspeedTypeKey = "maxspeed:type$post"
    val sourceMaxspeedKey = "source:maxspeed$post"
    val zoneMaxspeedKey = "zone:maxspeed$post"
    val zoneTrafficKey = "zone:traffic$post"

    val containsMaxspeedType = tags.filterKeys { it.startsWith("maxspeed:type") }.isNotEmpty()
    val containsSourceMaxspeed = tags.filterKeys { it.startsWith("source:maxspeed") }.isNotEmpty()
    val containsZoneMaxspeed = tags.filterKeys { it.startsWith("zone:maxspeed") }.isNotEmpty()
    val containsZoneTraffic = tags.filterKeys { it.startsWith("zone:traffic") }.isNotEmpty()

    return when {
        previousTypeKey == speedKey -> speedKey
        containsMaxspeedType -> maxspeedTypeKey
        containsSourceMaxspeed && canUseSourceMaxspeedForType(tags) -> sourceMaxspeedKey
        anyTypeIsSigned -> maxspeedTypeKey // "sign" is only valid in "maxspeed:type" and "source:maxspeed"
        containsZoneMaxspeed -> zoneMaxspeedKey
        containsZoneTraffic -> zoneTrafficKey
        else -> maxspeedTypeKey
    }
}

private fun canUseSourceMaxspeedForType(tags: Tags): Boolean {
    val sourceMaxspeedTags = tags.filterKeys { it.startsWith("source:maxspeed") }
    val anyIsValid = sourceMaxspeedTags.any { isValidMaxspeedType(it.value) }
    val allCanBeRemoved = sourceMaxspeedTags.all { canThisSourceMaxspeedBeRemoved(it.value) }
    return anyIsValid && allCanBeRemoved
}

fun MaxspeedAndType.applyTo(tags: Tags, direction: String? = null, vehicleType: String? = null) {
    val dir = if (direction != null) ":$direction" else ""
    val veh = if (vehicleType != null) ":$vehicleType" else ""
    val post = "$veh$dir"
    val speedKey = "maxspeed$post"
    val previousSpeedOsmValue = tags[speedKey]

    val zoneMaxspeedKey = "zone:maxspeed$post"

    // e.g. "maxspeed="RU:zone30" and "maxspeed:type=sign" is valid as a zone can be signed
    val isSignedZone = explicit is MaxSpeedZone && type is JustSign
    // previousTypeKey would give "maxspeed", so get the next best
    val signedZoneKey = getTypeKey(tags, null, post)

    // Take a single type key in this order of preference
    val previousTypeKey = getPreviousTypeKey(tags, post, explicit != null, isSignedZone)

    val previousTypeOsmValue = tags[previousTypeKey]

    // Use the same type key as before, or else use "maxspeed:type"
    val typeKey = getTypeKey(tags, previousTypeKey, post)

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
        when {
            vehicleType == null && direction == null -> tags.removeMaxspeedTaggingForAllVehiclesAndDirections()
            vehicleType == null -> tags.removeMaxspeedTaggingForAllVehicles(direction)
            direction == null -> tags.removeMaxspeedTaggingForAllDirections(vehicleType)
            else -> tags.removeMaxspeedTagging(direction, vehicleType)
        }
    }

    // If there is no type or type has not changed we should still clean up the tagging
    if (typeOsmValue == null || typeOsmValue == previousTypeOsmValue) {
        when {
            vehicleType == null && direction == null -> tags.removeMaxspeedTypeTaggingForAllVehiclesAndDirections()
            vehicleType == null -> tags.removeMaxspeedTypeTaggingForAllVehicles(direction)
            direction == null -> tags.removeMaxspeedTypeTaggingForAllDirections(vehicleType)
            else -> tags.removeMaxspeedTypeTagging(direction, vehicleType)
        }
        if (typeOsmValue != null) {
            tags[typeKey] = typeOsmValue
        } else if (previousTypeOsmValue != null) {
            tags[typeKey] = previousTypeOsmValue
        }
    }

    // if type has changed then remove all possible old tagging
    if (typeOsmValue != null && typeOsmValue != previousTypeOsmValue) {
        when {
            vehicleType == null && direction == null -> {
                tags.removeMaxspeedTaggingForAllVehiclesAndDirections()
                tags.removeMaxspeedTypeTaggingForAllVehiclesAndDirections()
            }
            vehicleType == null -> {
                tags.removeMaxspeedTaggingForAllVehicles(direction)
                tags.removeMaxspeedTypeTaggingForAllVehicles(direction)
            }
            direction == null -> {
                tags.removeMaxspeedTaggingForAllDirections(vehicleType)
                tags.removeMaxspeedTypeTaggingForAllDirections(vehicleType)
            }
            else -> {
                tags.removeMaxspeedTagging(direction, vehicleType)
                tags.removeMaxspeedTypeTagging(direction, vehicleType)
            }
        }

        tags[typeKey] = typeOsmValue
        if (isSignedZone) {
            tags[signedZoneKey] = "sign"
        }
        // TODO what if different vehicles or directions provide different lit values? (lit:forward and lit:backward do not exist), obviously
        if (type is ImplicitMaxSpeed) {
            // Lit is either already set or has been answered by the user, so this wouldn't change the value of the lit tag
            type.lit?.let { tags["lit"] = it.toYesNo() }
        }
        // If user was shown that it was a school zone and selected something else, remove school zone tag
        // Checking if it's a living street because user was also shown it as a school zone if there was
        // no type tagged but it was a living street
        // TODO do we want to keep vehicle type check in here?
        if (previousTypeOsmValue == null && !isLivingStreet(tags) && tags["hazard"] == "school_zone" && vehicleType == null) {
            tags.remove("hazard")
        }
    }

    // if maxspeed has changed remove all old maxspeed tagging but not type tagging
    // if it's a signed maxspeed zone then don't remove the (just tagged) "maxspeed=XX:zoneyy"
    if (speedOsmValue != null && speedOsmValue != previousSpeedOsmValue && !isSignedZone) {
        when (vehicleType) {
            null -> tags.removeMaxspeedTaggingForAllVehicles(direction)
            else -> tags.removeMaxspeedTagging(direction, vehicleType)
        }
        tags[speedKey] = speedOsmValue
    }
}

private fun canThisSourceMaxspeedBeRemoved(value: String?): Boolean {
    return when {
        value == null -> true
        SOURCE_MAXSPEED_VALUES_THAT_CAN_BE_REMOVED.contains(value) -> true
        isValidMaxspeedType(value) -> true
        else -> false
    }
}

private fun Tags.removeMaxspeedTagging(direction: String?, vehicleType: String?) {
    val dir = if (direction != null) ":$direction" else ""
    val veh = if (vehicleType != null) ":$vehicleType" else ""
    MAXSPEED_KEYS.forEach { remove("$it$veh$dir") }
    MAXSPEED_KEYS.forEach { remove("$it$veh$dir:conditional") }
    MAXSPEED_KEYS.forEach { remove("$it$veh$dir:lanes") }
    MAXSPEED_KEYS.forEach { remove("$it$veh$dir:lanes:conditional") }
}

private fun Tags.removeMaxspeedTaggingForAllVehicles(direction: String?) {
    (setOf(null) + VEHICLE_TYPES).forEach { this.removeMaxspeedTagging(direction, it) }
}

private fun Tags.removeMaxspeedTaggingForAllDirections(vehicleType: String?) {
    DIRECTIONS.forEach { this.removeMaxspeedTagging(it, vehicleType) }
}

private fun Tags.removeMaxspeedTaggingForAllVehiclesAndDirections() {
    DIRECTIONS.forEach { this.removeMaxspeedTaggingForAllVehicles(it) }
}

private fun Tags.removeMaxspeedTypeTagging(direction: String?, vehicleType: String?) {
    val dir = if (direction != null) ":$direction" else ""
    val veh = if (vehicleType != null) ":$vehicleType" else ""
    MAXSPEED_TYPE_KEYS_EXCEPT_SOURCE.forEach { remove("$it$veh$dir") }
    if (canThisSourceMaxspeedBeRemoved(this["source:maxspeed$veh$dir"])) {
        remove("source:maxspeed$veh$dir")
    }
}

private fun Tags.removeMaxspeedTypeTaggingForAllVehicles(direction: String?) {
    (setOf(null) + VEHICLE_TYPES).forEach { this.removeMaxspeedTypeTagging(direction, it) }
}

private fun Tags.removeMaxspeedTypeTaggingForAllDirections(vehicleType: String?) {
    DIRECTIONS.forEach { this.removeMaxspeedTypeTagging(it, vehicleType) }
}

private fun Tags.removeMaxspeedTypeTaggingForAllVehiclesAndDirections() {
    DIRECTIONS.forEach { this.removeMaxspeedTypeTaggingForAllVehicles(it) }
}

private val DIRECTIONS = setOf(
    null,
    "forward",
    "backward"
)

private var anyTypeIsSigned = false
