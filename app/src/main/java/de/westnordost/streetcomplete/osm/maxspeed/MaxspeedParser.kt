package de.westnordost.streetcomplete.osm.maxspeed

import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.quests.max_speed.Kmh
import de.westnordost.streetcomplete.quests.max_speed.Mph
import kotlin.math.roundToInt

fun createForwardAndBackwardAllSpeedInformation(tags: Map<String, String>, countryInfo: CountryInfo): ForwardAndBackwardAllSpeedInformation? {
    if (!hasAnyMaxspeedTagging(tags) && !isSchoolZone(tags) && !isLivingStreet(tags)) return null
    val advisory = createForwardAndBackwardAdvisorySpeedSign(tags)
    val variable = createForwardAndBackwardVariableLimit(tags)
    val vehicleMap = createVehicleConditionalMap(tags, countryInfo)

    val forwardVehicleMap = vehicleMap.mapValues { (_, v) -> v?.forward }.filterValues { !it.isNullOrEmpty() }
    val backwardVehicleMap = vehicleMap.mapValues { (_, v) -> v?.backward }.filterValues { !it.isNullOrEmpty() }

    return ForwardAndBackwardAllSpeedInformation(
        AllSpeedInformation(forwardVehicleMap, advisory?.forward, variable?.forward),
        AllSpeedInformation(backwardVehicleMap, advisory?.backward, variable?.backward),
        createWholeRoadType(tags)
    )
}

/** Combines directions of advisory speeds, returns null if there is no tagging or if the
 *  tagging is invalid, e.g. maxspeed:advisory= and maxspeed:advisory:forward= */
fun createForwardAndBackwardAdvisorySpeedSign(tags: Map<String, String>): ForwardAndBackwardAdvisorySpeedSign? {
    val forward = createAdvisoryMaxspeed(tags, "forward")
    val backward = createAdvisoryMaxspeed(tags, "backward")
    val both = createAdvisoryMaxspeed(tags, null)

    return when {
        both == null && forward == null && backward == null -> null
        both == null -> ForwardAndBackwardAdvisorySpeedSign(forward, backward)
        forward == null && backward == null -> ForwardAndBackwardAdvisorySpeedSign(both, both)
        else -> null // All three have something, this is over-tagging so return null
    }
}

// maxspeed:advisory has not been used for different vehicles (other than 12 times for bicycle),
// so only creating for the blank tag
fun createAdvisoryMaxspeed(tags: Map<String, String>, direction: String?): AdvisorySpeedSign? {
    val dir = if (direction != null) ":$direction" else ""
    val speed = createExplicitMaxspeed(tags["maxspeed:advisory$dir"])
    return if (speed !is MaxSpeedSign) {
        null
    } else {
        AdvisorySpeedSign(speed.value)
    }
}

/** Returns the combined directions of variable limits. null if there is no tagging or there is
 *  conflicting tagging. */
fun createForwardAndBackwardVariableLimit(tags: Map<String, String>): ForwardAndBackwardVariableLimit? {
    val forward = isVariableLimit(tags, "forward")
    val backward = isVariableLimit(tags, "backward")
    val both = isVariableLimit(tags, null)

    return when {
        both == null && forward == null && backward == null -> null
        both == null -> ForwardAndBackwardVariableLimit(forward, backward)
        forward == null && backward == null -> ForwardAndBackwardVariableLimit(both, both)
        both == forward && both == backward -> ForwardAndBackwardVariableLimit(both, both)
        else -> null
    }
}

/** Returns if there is a variable limit in the given [direction]. null if there is no variable
 *  limit, false if there is explicitly no variable limit, true for any other value */
fun isVariableLimit(tags: Map<String, String>, direction: String?): Boolean? {
    val dir = if (direction != null) ":$direction" else ""
    return when (tags["maxspeed:variable$dir"]) {
        null -> null
        "no" -> false
        else -> true
    }
}

fun createVehicleConditionalMap(tags: Map<String, String>, countryInfo: CountryInfo): Map<String?, ForwardAndBackwardConditionalMaxspeed?> {
    val vehicleMap = mutableMapOf<String?, ForwardAndBackwardConditionalMaxspeed?>()
    (setOf(null) + VEHICLE_TYPES).forEach { v ->
        val m = createForwardAndBackwardConditionalMaxspeed(tags, countryInfo, v)
        vehicleMap[v] = ForwardAndBackwardConditionalMaxspeed(
            m.forward?.filterValues { it != null },
            m.backward?.filterValues { it != null }
        )
    }
    return vehicleMap
}

/*  Not gathering type with conditional maxspeed because it has barely been used
 *  (source:maxspeed:conditional has the most uses, with fewer than 1300 and almost all values
 *  are "sign" anyway. The return type still needs to include type so we can merge with plain speed */
fun createForwardAndBackwardConditionalMaxspeed(tags: Map<String, String>, countryInfo: CountryInfo, vehicleType: String?): ForwardAndBackwardConditionalMaxspeed {
    val forwardConditional = createConditionalMaxspeed(tags, "forward", vehicleType)
    val backwardConditional = createConditionalMaxspeed(tags, "backward", vehicleType)
    val bothConditional = createConditionalMaxspeed(tags, null, vehicleType)
    val unconditional = createForwardAndBackwardMaxspeedAndType(tags, countryInfo, vehicleType)

    // maxspeed:conditional without maxspeed is an error
    // but e.g. maxspeed:hgv:conditional without maxspeed:hgv would be ok
    if (vehicleType == null && unconditional == null) {
        return ForwardAndBackwardConditionalMaxspeed(mapOf(NoCondition to null), mapOf(NoCondition to null))
    }

    val unconditionalMapForward = mapOf<Condition, MaxspeedAndType?>(NoCondition to unconditional?.forward)
    val unconditionalMapBackward = mapOf<Condition, MaxspeedAndType?>(NoCondition to unconditional?.backward)

    return when {
        forwardConditional == null && backwardConditional == null && bothConditional == null -> {
            ForwardAndBackwardConditionalMaxspeed(unconditionalMapForward, unconditionalMapBackward)
        }
        forwardConditional == null && backwardConditional == null -> ForwardAndBackwardConditionalMaxspeed(
            combineConditionalsMaxspeedMaps(bothConditional, unconditionalMapForward),
            combineConditionalsMaxspeedMaps(bothConditional, unconditionalMapBackward)
        )
        bothConditional == null -> ForwardAndBackwardConditionalMaxspeed(
            combineConditionalsMaxspeedMaps(forwardConditional, unconditionalMapForward),
            combineConditionalsMaxspeedMaps(backwardConditional, unconditionalMapBackward)
        )
        // Need to combine directions, e.g. we might have maxspeed:conditional and maxspeed:backward:conditional
        else -> ForwardAndBackwardConditionalMaxspeed(
            combineConditionalsMaxspeedMaps(combineConditionalsMaxspeedMaps(forwardConditional, unconditionalMapForward), bothConditional),
            combineConditionalsMaxspeedMaps(combineConditionalsMaxspeedMaps(backwardConditional, unconditionalMapBackward), bothConditional)
        )
    }
}

/** Creates a map of conditions to MaxspeedAndType for the given [direction] and [vehicleType],
 *  including a map from NoCondition for the bare tags. */
fun createConditionalMaxspeed(tags: Map<String, String>, direction: String?, vehicleType: String?): Map<Condition, MaxspeedAndType>? {
    val dir = if (direction != null) ":$direction" else ""
    val veh = if (vehicleType != null) ":$vehicleType" else ""
    val speedKey = "maxspeed$veh$dir:conditional"
    return getConditionalMaxspeed(tags[speedKey])?.mapValues { MaxspeedAndType(it.value, null) }
}

/** Combines two conditional maxspeed maps, setting any to invalid where the values for the
 *  conditions are not equal. */
fun combineConditionalsMaxspeedMaps(first: Map<Condition, MaxspeedAndType?>?, second: Map<Condition, MaxspeedAndType?>?): Map<Condition, MaxspeedAndType?>? {
    if (first == null) {
        return second
    } else if (second == null) {
        return first
    }
    // plus prefers the elements on the right, so these are different if there is a clash of values
    else if (first + second == second + first) {
        return first + second
    }

    val newMap = mutableMapOf<Condition, MaxspeedAndType?>()

    // Any mismatching values get changed to Invalid
    first.forEach { (k, v) ->
        if (second.keys.contains(k) && second[k] != v) {
            newMap[k] = MaxspeedAndType(Invalid, Invalid)
        } else {
            newMap[k] = v
        }
    }
    // plus prefers values on the right, so the invalid values replace anything in second
    return second + newMap
}

/** Creates a combined set of MaxspeedAndType for forwards and backwards. If there is any over-
 *  defined tagging (e.g. maxspeed= and maxspeed:forward= then Invalid speed and type are returned. */
fun createForwardAndBackwardMaxspeedAndType(tags: Map<String, String>, countryInfo: CountryInfo, vehicleType: String? = null): ForwardAndBackwardMaxspeedAndType? {
    if (
        !hasAnyMaxspeedTaggingForDirectionAndVehicle(tags, null, vehicleType)
        // && !isLivingStreet(tags) &&
        // !isSchoolZone(tags)
    ) return null

    val forward = createMaxspeedAndType(tags, countryInfo, "forward", vehicleType)
    val backward = createMaxspeedAndType(tags, countryInfo, "backward", vehicleType)
    val both = createMaxspeedAndType(tags, countryInfo, null, vehicleType)

    if (forward == null && backward == null && both == null) return null

    // Invalid to have speed tagged for one directions and no direction to both tagged
    // e.g. maxspeed along with maxspeed:forward and maxspeed:backward
    val overDefinedExplicits = forward?.explicit != null && backward?.explicit != null && both?.explicit != null
    val overDefinedTypes = forward?.type != null && backward?.type != null && both?.type != null

    if (overDefinedExplicits || overDefinedTypes) {
        return ForwardAndBackwardMaxspeedAndType(MaxspeedAndType(Invalid, Invalid), MaxspeedAndType(Invalid, Invalid))
    }

    // Could be that it is e.g. living street, so forward and backward are not null, but there is
    // also maxspeed tagged
    return if (forward?.explicit == null && backward?.explicit == null) {
        if (forward?.type == backward?.type && forward?.type != Invalid) {
            ForwardAndBackwardMaxspeedAndType(both, both)
        } else {
            ForwardAndBackwardMaxspeedAndType(forward, backward)
        }
    } else {
        ForwardAndBackwardMaxspeedAndType(forward, backward)
    }
}

/** Returns explicit and implicit maxspeed. Returns null if there is no such tagging for the
 *  given [direction] and [vehicleType] */
fun createMaxspeedAndType(tags: Map<String, String>, countryInfo: CountryInfo, direction: String? = null, vehicleType: String? = null): MaxspeedAndType? {
    val dir = if (direction != null) ":$direction" else ""
    val veh = if (vehicleType != null) ":$vehicleType" else ""

    if (
        !hasAnyMaxspeedTaggingForDirectionAndVehicle(tags, direction, vehicleType)
        // && !isLivingStreet(tags) &&
        // !isSchoolZone(tags)
    ) return null

    val maxspeedTag = tags["maxspeed$veh$dir"]

    var maxspeedType: MaxSpeedAnswer?
    val impliedMaxspeedValue: MaxSpeedAnswer?

    val taggedMaxspeedType = createImplicitMaxspeed(tags["maxspeed:type$veh$dir"], tags, countryInfo)
    val taggedSourceMaxspeed = createImplicitMaxspeed(tags["source:maxspeed$veh$dir"], tags, countryInfo)
    val taggedZoneMaxspeed = createImplicitMaxspeed(tags["zone:maxspeed$veh$dir"], tags, countryInfo) // e.g. "DE:30", "DE:urban" etc.
    val taggedZoneTraffic = createImplicitMaxspeed(tags["zone:traffic$veh$dir"], tags, countryInfo) // e.g. "DE:urban", "DE:zone30" etc.

    // Assume that invalid "source:maxspeed" means that it is actual "source", not type
    val maxspeedTypesList = if (taggedSourceMaxspeed == Invalid) {
        listOf(taggedMaxspeedType, taggedZoneMaxspeed, taggedZoneTraffic)
    } else {
        listOf(taggedMaxspeedType, taggedSourceMaxspeed, taggedZoneMaxspeed, taggedZoneTraffic)
    }

    // Determine if there is mismatching tagging of types
    val distinctMaxspeedTypesList = maxspeedTypesList.distinct().filterNotNull()
    maxspeedType = when {
        // Empty if all values are null
        distinctMaxspeedTypesList.isEmpty() -> null
        // More than one unique value means invalid
        distinctMaxspeedTypesList.size != 1 -> Invalid
        // All non-blank values are the same
        else -> distinctMaxspeedTypesList.first()
    }

    var maxspeedValue = createExplicitMaxspeed(maxspeedTag)

    // maxspeed has a non-numerical value, see if it is valid
    if (maxspeedValue == Invalid && maxspeedTag != null) {
        val maxspeedAsType = when {
            isImplicitMaxspeed(maxspeedTag) -> createImplicitMaxspeed(maxspeedTag, tags, countryInfo)
            else -> null
        }

        // Save from dealing with this in checks further down
        // e.g. "maxspeed="RU:zone30" and "maxspeed:type=sign" is valid as a zone can be signed
        if (maxspeedAsType is MaxSpeedZone && maxspeedType is JustSign) {
            return MaxspeedAndType(maxspeedAsType, maxspeedType)
        }

        // Compare type value in "maxspeed" with separately tagged type
        if (maxspeedAsType != null && maxspeedType == null) {
            maxspeedValue = null
            maxspeedType = maxspeedAsType
        } else if (maxspeedAsType != maxspeedType) {
            maxspeedType = Invalid
        }
    }

    // sign type is only valid with appropriate "maxspeed" tagged
    if (maxspeedType is JustSign &&
        !(maxspeedValue is MaxSpeedSign || maxspeedValue is WalkMaxSpeed || maxspeedValue is MaxSpeedIsNone)) {
            maxspeedType = Invalid
    }

    // Compare implied maxspeed (from zone type) with actual tagged maxspeed
    if (maxspeedType is MaxSpeedZone) {
        impliedMaxspeedValue = MaxSpeedSign(maxspeedType.value)
        if (maxspeedValue == null) {
            maxspeedValue = impliedMaxspeedValue
        } else if (maxspeedValue != impliedMaxspeedValue) {
            maxspeedValue = Invalid
            maxspeedType = Invalid // implied maxspeed came from type tag, so mismatch means we don't know which is correct
        }
    }

    // TODO: remove this once we know it works
    // If there is no maxspeed type tagging over-riding it then there are other tags that are
    // essentially an implicit maxspeed of themselves
    // if (maxspeedType == null) {
    //     maxspeedType = when {
    //         // Check for school zone first, because if there is a road that is both then if we displayed
    //         // it as a living street then there would be no way to change the tags to mark it as a school zone
    //         isSchoolZone(tags) -> IsSchoolZone
    //         isLivingStreet(tags) -> LivingStreet(null)
    //         else -> null
    //     }
    // }
    if (maxspeedValue == null && maxspeedType == null) return null
    return MaxspeedAndType(maxspeedValue, maxspeedType)
}

/** Looks at tags that affect everything and are unrelated to vehicle and direction
 *  e.g. living street or school zone. */
private fun createWholeRoadType(tags: Map<String, String>): MaxSpeedAnswer? {
    // Check for school zone first, because if there is a road that is both then if we displayed
    // it as a living street then there would be no way to change the tags to mark it as a school zone
    return when {
        isSchoolZone(tags) -> IsSchoolZone
        isLivingStreet(tags) -> LivingStreet(null)
        else -> null
    }
}

/** Needs all tags to determine if way is lit */
private fun createImplicitMaxspeed(value: String?, tags: Map<String, String>, countryInfo: CountryInfo): MaxSpeedAnswer? {
    return when {
        value == null -> null
        value == "sign" -> JustSign
        // Check for other implicit types first because the format is the same as implicit format
        getCountryCodeFromMaxspeedType(value) != countryInfo.countryCode -> Invalid
        isZoneMaxspeed(value) -> getZoneMaxspeed(value, countryInfo)
        isLivingStreetMaxspeed(value) -> LivingStreet(getCountryCodeFromMaxspeedType(value))
        isImplicitMaxspeed(value) -> getImplicitMaxspeed(value, tags)
        else -> Invalid
    }
}

/** Returns invalid if not in mph or a plain number (i.e. in km/h) */
fun createExplicitMaxspeed(value: String?): MaxSpeedAnswer? {
    if (value == null) return null
    // "walk" and "none" are values in "maxspeed", rather than "maxspeed:type"
    return if (maxspeedIsWalk(value)) {
        WalkMaxSpeed
    } else if (maxspeedIsNone(value)) {
        MaxSpeedIsNone
    } else if (isInMph(value)) {
        val speed = getMaxspeedInMph(value)?.roundToInt()
        if (speed != null) {
            MaxSpeedSign(Mph(speed))
        } else {
            Invalid
        }
    } else {
        // Null if speed can't be converted to float, i.e. it is not just a number
        // maybe it has other units, that is invalid here
        val speed = getMaxspeedInKmh(value)?.roundToInt()
        if (speed != null) {
            MaxSpeedSign(Kmh(speed))
        } else {
            Invalid
        }
    }
}

private fun hasAnyMaxspeedTagging(tags: Map<String, String>): Boolean {
    val containsAnyMaxspeed = MAXSPEED_KEYS.any { m -> tags.filterKeys { it.startsWith(m) }.isNotEmpty() }
    val containsAnyMaxspeedType = MAXSPEED_TYPE_KEYS.any { m -> tags.filterKeys { it.startsWith(m) }.isNotEmpty() }
    return containsAnyMaxspeed || containsAnyMaxspeedType
}

private fun hasAnyMaxspeedTaggingForDirectionAndVehicle(tags: Map<String, String>, direction: String?, vehicleType: String?): Boolean {
    val dir = if (direction != null) ":$direction" else ""
    val veh = if (vehicleType != null) ":$vehicleType" else ""
    val containsAnyMaxspeed = MAXSPEED_KEYS.any { m -> tags.filterKeys { it.startsWith("$m$veh$dir") }.isNotEmpty() }
    val containsAnyMaxspeedType = MAXSPEED_TYPE_KEYS.any { m -> tags.filterKeys { it.startsWith("$m$veh$dir") }.isNotEmpty() }
    return containsAnyMaxspeed || containsAnyMaxspeedType
}
