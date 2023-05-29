package de.westnordost.streetcomplete.osm.maxspeed

import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.osm.expandDirections
import de.westnordost.streetcomplete.osm.hasCheckDateForKey
import de.westnordost.streetcomplete.osm.lit.applyTo
import de.westnordost.streetcomplete.osm.lit.createLitStatus
import de.westnordost.streetcomplete.osm.maxspeed.RoadType.UNKNOWN
import de.westnordost.streetcomplete.osm.mergeDirections
import de.westnordost.streetcomplete.osm.updateCheckDateForKey
import de.westnordost.streetcomplete.util.ktx.toYesNo

fun ForwardAndBackwardAllSpeedInformation.applyTo(tags: Tags) {
    if (forward == null && backward == null && wholeRoadType == null) return
    // We want to use the same type key everywhere, but we can't use zone:traffic or zone:maxspeed
    // if any of the types is "sign", so need to check that here
    anyTypeIsSigned = determineIfAnyTypeIsSigned(forward) || determineIfAnyTypeIsSigned(backward)

    /* for being able to modify only one direction (e.g. `forward` is null while `backward` is not null),
       the directions conflated in the default keys need to be separated first. e.g. `maxspeed=60`
       when forward direction is made `50` should become
       - maxspeed:backward=60
       - maxspeed:forward=50
       First separating the values and then later conflating them again, if possible, solves this.
     */

    // VEHICLE_TYPES does not contain "null" and we need to expand bare tags
    tags.expandAllMaxspeedTags()

    forward.applyTo(tags, "forward")
    backward.applyTo(tags, "backward")

    // Unless an explicit speed limit is provided, changing to a living street or school zone
    // removes all maxspeed tagging because we are in the context of speed limits. So the user was
    // shown the current speed limit/type and answered that in fact it is a living street/school
    // zone, thereby saying that what was tagged before was wrong.
    // If the user also provides an explicit maxspeed then we tag the type as "xx:living_street"
    // to make it clear. There is no equivalent accepted tag for school zones, so
    // "hazard=school_zone" suffices
    if (wholeRoadType is LivingStreet) {
        // Only set type if explicit speed has been given or if it is a school zone
        // (school zone takes  precedence over living street in parsing because there's no speed
        // limit type tag for school zones)
        val typeKeyForward = getTypeKey(tags, null, ":forward")
        val typeKeyBackward = getTypeKey(tags, null, ":backward")
        if (isSchoolZone(tags) || forward?.vehicles?.get(null)?.get(NoCondition)?.explicit != null) {
            tags[typeKeyForward] = wholeRoadType.toTypeOsmValue()!!
        }
        if (isSchoolZone(tags) || backward?.vehicles?.get(null)?.get(NoCondition)?.explicit != null) {
            tags[typeKeyBackward] = wholeRoadType.toTypeOsmValue()!!
        }

        // Don't change highway type if "living_street" is set
        if (tags["highway"] != "living_street" && tags["living_street"] != "yes") {
            tags["highway"] = "living_street"
            // In case "living_street" was set to e.g. "no"
            // (values other than yes and no are so rarely used that they are not worth dealing with
            tags.remove("living_street")
        }
    }

    tags.mergeAllMaxspeedTags()

    if (wholeRoadType is IsSchoolZone) {
        tags["hazard"] = "school_zone"
        tags.removeMaxspeedTypeTaggingForAllVehiclesAndDirections(null)
        tags.removeMaxspeedTypeTaggingForAllVehiclesAndDirections("conditional")
    }

    // TODO: hmm, this sets check date for maxspeed, even if it is a school zone/living street
    // But fine to be setting it when actually putting in directions/type/whatever
    // update check date
    if (!tags.hasChanges || tags.hasCheckDateForKey("maxspeed")) {
        tags.updateCheckDateForKey("maxspeed")
    }
}

private fun AllSpeedInformation?.applyTo(tags: Tags, direction: String?) {
    if (this == null) {
        tags.removeMaxspeedTaggingForAllVehicles(direction, null)
        tags.removeMaxspeedTaggingForAllVehicles(direction, "conditional")
        tags.removeMaxspeedTypeTaggingForAllVehicles(direction, null)
        tags.removeMaxspeedTypeTaggingForAllVehicles(direction, "conditional")
        return
    }
    val dir = if (direction != null) ":$direction" else ""

    this.vehicles?.forEach { (k, v) -> v?.applyTo(tags, direction, k) }

    // Remove maxspeed tagging for any vehicles not specified
    (setOf(null) + VEHICLE_TYPES).forEach {
        if (this.vehicles?.contains(it) == false || this.vehicles == null) {
            tags.removeMaxspeedTagging(direction, it, null)
            tags.removeMaxspeedTagging(direction, it, "conditional")
            tags.removeMaxspeedTypeTagging(direction, it, null)
            tags.removeMaxspeedTypeTagging(direction, it, "conditional")
        }
    }

    if (this.advisory == null) {
        tags.remove("maxspeed:advisory$dir")
        tags.remove("maxspeed:advised$dir")
    } else {
        this.advisory.applyTo(tags, direction)
        tags.remove("maxspeed:advised$dir")
    }

    if (this.variable == null) {
        tags.remove("maxspeed:variable$dir")
        tags.remove("maxspeed:variable:max$dir")
    } else {
        tags["maxspeed:variable$dir"] = this.variable.toVariableLimit(tags, direction)
        if (this.vehicles?.get(null)?.get(NoCondition) != null) {
            tags.remove("maxspeed:variable:max$dir") // this should go in maxspeed tag
        }
    }
}

private fun AdvisorySpeedSign.applyTo(tags: Tags, direction: String?) {
    val dir = if (direction != null) ":$direction" else ""
    val advisoryKey = "maxspeed:advisory$dir"
    // Doesn't matter what the previous type key was, as getTypeKey only needs that to check if it's "maxspeed"
    val typeKey = getTypeKey(tags, null, ":advisory$dir")
    tags[advisoryKey] = this.value.toString()
    tags[typeKey] = "sign"
}

private fun Boolean.toVariableLimit(tags: Tags, direction: String?): String {
    val dir = if (direction != null) ":$direction" else ""
    val previousValue = tags["maxspeed:variable$dir"] ?: return this.toYesNo()
    return when (previousValue) {
        "no" -> this.toYesNo()
        "yes" -> this.toYesNo()
        else -> {
            // Anything other than "yes" is a more specific way of saying yes, so preserve previous value
            if (this) previousValue
            else "no"
        }
    }
}

private fun Map<Condition, MaxspeedAndType?>.applyTo(tags: Tags, direction: String?, vehicleType: String?) {
    val dir = if (direction != null) ":$direction" else ""
    val veh = if (vehicleType != null) ":$vehicleType" else ""
    val conditionalKey = "maxspeed$veh$dir:conditional"
    val conditionalOsmValues = mutableListOf<String>()

    // Don't change the value if nothing has changed
    val previousConditional = createConditionalMaxspeed(tags, direction, vehicleType)
    if (this == previousConditional) return

    // Be consistent throughout with use of brackets
    val useBrackets = this.keys.any { it.needsBrackets() }

    this.forEach {
        when (it.key) {
            NoCondition -> it.value.applyTo(tags, direction, vehicleType)
            else -> {
                val osmValue = it.value?.explicit?.toSpeedOsmValue() ?: return@forEach
                if (useBrackets) conditionalOsmValues.add("$osmValue @ (${it.key.toOsmValue()})")
                else conditionalOsmValues.add("$osmValue @ ${it.key.toOsmValue()}")
            }
        }
    }
    if (conditionalOsmValues.isEmpty()) {
        tags.removeMaxspeedTagging(direction, vehicleType, "conditional")
        tags.removeMaxspeedTypeTagging(direction, vehicleType, "conditional")
    } else {
        // Existing practice appears to be that you start with the fastest speed and decrease
        conditionalOsmValues.sortByDescending { it.split(" ")[0].toInt() }
        tags[conditionalKey] = conditionalOsmValues.joinToString("; ")
    }

    // TODO: remove if proposal is rejected
    CONDITIONAL_MAXSPEED_TAGS.forEach { tags.remove("$it$veh$dir") }
}

private fun Tags.expandAllMaxspeedTags() {
    (setOf(null) + VEHICLE_TYPES).forEach { this.expandMaxspeedTags(it) }
}

private fun Tags.expandMaxspeedTags(vehicleType: String?) {
    val veh = if (vehicleType != null) ":$vehicleType" else ""
    MAXSPEED_KEYS.forEach {
        this.expandDirections("$it$veh", null)
        this.expandDirections("$it$veh", "conditional")
        this.expandDirections("$it$veh", "signed")
    }
    this.expandDirections("maxspeed:variable$veh:max", null)
    MAXSPEED_TYPE_KEYS_EXCEPT_SOURCE.forEach {
        this.expandDirections("$it$veh", null)
        this.expandDirections("$it$veh", "conditional")
    }
    // Do not split source:maxspeed* if it is some special value that we will not touch
    if (canThisSourceMaxspeedBeRemoved(this["source:maxspeed$veh"])) {
        this.expandDirections("source:maxspeed$veh", null)
        this.expandDirections("source:maxspeed$veh", "conditional")
    }
    CONDITIONAL_MAXSPEED_TAGS.forEach {
        this.expandDirections("$it$veh", null)
    }
}

private fun Tags.mergeAllMaxspeedTags() {
    (setOf(null) + VEHICLE_TYPES).forEach { this.mergeMaxspeedTags(it) }
}

private fun Tags.mergeMaxspeedTags(vehicleType: String?) {
    val veh = if (vehicleType != null) ":$vehicleType" else ""
    MAXSPEED_KEYS.forEach {
        this.mergeDirections("$it$veh", null)
        this.mergeDirections("$it$veh", "conditional")
        this.mergeDirections("$it$veh", "signed")
    }
    this.mergeDirections("maxspeed:variable$veh:max", null)
    // Only merge type tags if directions have been merged
    // i.e. maxspeed:forward=xx maxspeed:backward=yy should be combined with
    // maxspeed:type:forward=sign and maxspeed:type:backward=sign, not just maxspeed:type=sign
    MAXSPEED_TYPE_KEYS.forEach {
        if (!directionsExistAndDiffer(this, veh)) {
            this.mergeDirections("$it$veh", null)
        }
        if (!directionsExistAndDiffer(this, "$veh:advisory")) {
            this.mergeDirections("$it$veh:advisory", null)
        }
        if (!directionsExistAndDiffer(this, "$veh:conditional")) {
            this.mergeDirections("$it$veh", "conditional")
        }
    }
    CONDITIONAL_MAXSPEED_TAGS.forEach {
        this.mergeDirections("$it$veh", null)
    }
}

private fun directionsExistAndDiffer(tags: Tags, veh: String): Boolean {
    return tags["maxspeed$veh:forward"] != null
        && tags["maxspeed$veh:backward"] != null
        && tags["maxspeed$veh:forward"] != tags["maxspeed$veh:backward"]
}

/** Return a single type key that was used before for the given [postfix] to "maxspeed"
 *  In the case of multiple values existing, take the first one in the order of preference of
 *  type:maxspeed, source:maxspeed, zone:maxspeed and zone:traffic
 *  Will only be source:maxspeed if it was used for type before (not a special value or source value)
 *  null if no such key exists */
private fun getPreviousTypeKey(tags: Tags, postfix: String, explicitValueGiven: Boolean, isSignedZone: Boolean): String? {
    val speedKey = "maxspeed$postfix"
    val maxspeedTypeKey = "maxspeed:type$postfix"
    val sourceMaxspeedKey = "source:maxspeed$postfix"
    val zoneMaxspeedKey = "zone:maxspeed$postfix"
    val zoneTrafficKey = "zone:traffic$postfix"

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

private fun MaxspeedAndType?.applyTo(tags: Tags, direction: String? = null, vehicleType: String? = null) {
    if (this == null || ( this.type == null && this.explicit == null )) {
        tags.removeMaxspeedTagging(direction, vehicleType, null)
        tags.removeMaxspeedTypeTagging(direction, vehicleType, null)
        return
    }
    val dir = if (direction != null) ":$direction" else ""
    val veh = if (vehicleType != null) ":$vehicleType" else ""
    val postfix = "$veh$dir"
    val speedKey = "maxspeed$postfix"
    val previousSpeedOsmValue = tags[speedKey]
    val zoneMaxspeedKey = "zone:maxspeed$postfix"

    when {
        type is ImplicitMaxSpeed && type.roadType == UNKNOWN -> throw IllegalStateException()
        type is NoSign -> {
            tags["maxspeed$postfix:signed"] = "no"
            return
        }
    }

    // e.g. "maxspeed="RU:zone30" and "maxspeed:type=sign" is valid as a zone can be signed
    val isSignedZone = explicit is MaxSpeedZone && type is JustSign
    // previousTypeKey would give "maxspeed", so get the next best
    val signedZoneKey = getTypeKey(tags, null, postfix)

    val previousTypeKey = getPreviousTypeKey(tags, postfix, explicit != null, isSignedZone)
    val previousTypeOsmValue = tags[previousTypeKey]
    val typeKey = getTypeKey(tags, previousTypeKey, postfix)

    // We may have a maxspeed zone in type and no explicit value given
    val speedOsmValue = explicit?.toSpeedOsmValue() ?: type?.toSpeedOsmValue()

    // zone:maxspeed takes a different format for zone tagging
    val typeOsmValue = when {
        typeKey == zoneMaxspeedKey -> type?.toTypeOsmValueZoneMaxspeed()
        isSignedZone -> explicit?.toTypeOsmValue()
        else -> type?.toTypeOsmValue()
    }

    /* ---------------------------------- clean up old tags ------------------------------------- */

    // maxspeed is now not set
    // do this first in case "maxspeed" is used for the type
    if (speedOsmValue == null && previousSpeedOsmValue != null) {
        tags.removeMaxspeedTagging(direction, vehicleType, null)
    }

    // If there is no type or type has not changed we should still clean up the tagging
    if (typeOsmValue == null || typeOsmValue == previousTypeOsmValue) {
        tags.removeMaxspeedTypeTagging(direction, vehicleType, null)
        if (typeOsmValue != null) {
            tags[typeKey] = typeOsmValue
        } else if (previousTypeOsmValue != null) {
            tags[typeKey] = previousTypeOsmValue
        }
    }

    /* ---------------------------------- maxspeed type ----------------------------------------- */

    // if type has changed then remove all possible old tagging
    if (typeOsmValue != null && typeOsmValue != previousTypeOsmValue) {
        tags.removeMaxspeedTagging(direction, vehicleType, null)
        tags.removeMaxspeedTypeTagging(direction, vehicleType, null)

        tags[typeKey] = typeOsmValue

        when {
            isSignedZone -> tags[signedZoneKey] = "sign"
            type is JustSign -> tags.remove("maxspeed$postfix:signed")
            type is ImplicitMaxSpeed && type.lit != createLitStatus(tags) -> type.lit?.applyTo(tags)
        }

        // If user was shown that it was a school zone and selected something else, remove school zone tag
        // Checking if it's a living street because user was also shown it as a school zone if there was
        // no type tagged but it was a living street
        // TODO do we want to keep vehicle type check in here?
        if (previousTypeOsmValue == null && !isLivingStreet(tags) && tags["hazard"] == "school_zone" && vehicleType == null) {
            tags.remove("hazard")
        }
    }

    /* ---------------------------------- explicit maxspeed ------------------------------------- */

    // if maxspeed has changed remove all old maxspeed tagging but not type tagging
    // if it's a signed maxspeed zone then don't remove the (just tagged) "maxspeed=XX:zoneyy"
    if (speedOsmValue != null && speedOsmValue != previousSpeedOsmValue && !isSignedZone) {
        tags.removeMaxspeedTagging(direction, vehicleType, null)
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

private fun Tags.removeMaxspeedTagging(direction: String?, vehicleType: String?, post: String?) {
    val dir = if (direction != null) ":$direction" else ""
    val veh = if (vehicleType != null) ":$vehicleType" else ""
    val suffix = if (post != null) ":$post" else ""
    remove("maxspeed$veh$dir$suffix")
}

private fun Tags.removeMaxspeedTaggingForAllVehicles(direction: String?, postfix: String?) {
    (setOf(null) + VEHICLE_TYPES).forEach { this.removeMaxspeedTagging(direction, it, postfix) }
}

private fun Tags.removeMaxspeedTaggingForAllVehiclesAndDirections(postfix: String?) {
    DIRECTIONS.forEach { this.removeMaxspeedTaggingForAllVehicles(it, postfix) }
}

private fun Tags.removeMaxspeedTypeTagging(direction: String?, vehicleType: String?, postfix: String?) {
    val dir = if (direction != null) ":$direction" else ""
    val veh = if (vehicleType != null) ":$vehicleType" else ""
    val suffix = if (postfix != null) ":$postfix" else ""
    MAXSPEED_TYPE_KEYS_EXCEPT_SOURCE.forEach { remove("$it$veh$dir$suffix") }
    if (canThisSourceMaxspeedBeRemoved(this["source:maxspeed$veh$dir$suffix"])) {
        remove("source:maxspeed$veh$dir$suffix")
    }
}

private fun Tags.removeMaxspeedTypeTaggingForAllVehicles(direction: String?, post: String?) {
    (setOf(null) + VEHICLE_TYPES).forEach { this.removeMaxspeedTypeTagging(direction, it, post) }
}

private fun Tags.removeMaxspeedTypeTaggingForAllVehiclesAndDirections(post: String?) {
    DIRECTIONS.forEach { this.removeMaxspeedTypeTaggingForAllVehicles(it, post) }
}

private fun determineIfAnyTypeIsSigned(allSpeedInformation: AllSpeedInformation?): Boolean {
    if (allSpeedInformation == null) return false
    allSpeedInformation.vehicles?.forEach {
        it.value?.forEach { v ->
            if (v.value?.type is JustSign) return true
        }
    }
    return false
}

private val DIRECTIONS = setOf(
    null,
    "forward",
    "backward"
)

private var anyTypeIsSigned = false
