package de.westnordost.streetcomplete.osm.maxspeed

import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.osm.lit.createLitStatus
import de.westnordost.streetcomplete.osm.maxspeed.RoadType.*
import de.westnordost.streetcomplete.quests.max_speed.Kmh
import de.westnordost.streetcomplete.quests.max_speed.Mph
import kotlin.math.roundToInt

fun createForwardAndBackwardAllSpeedInformation(tags: Map<String, String>, countryInfo: CountryInfo): ForwardAndBackwardAllSpeedInformation? {
    if (!hasAnyMaxspeedTagging(tags) && !isSchoolZone(tags) && !isLivingStreet(tags)) return null
    val vehicleMap = createVehicleConditionalMap(tags, countryInfo)
    val advisory = createForwardAndBackwardAdvisorySpeedSign(tags)
    val variable = createForwardAndBackwardVariableLimit(tags)
    val wholeRoadType = createWholeRoadType(tags, countryInfo)

    var forwardVehicleMap = vehicleMap?.mapValues { (_, v) -> v?.forward }?.filterValues { !it.isNullOrEmpty() }
    if (forwardVehicleMap.isNullOrEmpty()) forwardVehicleMap = null
    var backwardVehicleMap = vehicleMap?.mapValues { (_, v) -> v?.backward }?.filterValues { !it.isNullOrEmpty() }
    if (backwardVehicleMap.isNullOrEmpty()) backwardVehicleMap = null

    val allSpeedForward = if (
        forwardVehicleMap == null
        && advisory?.forward == null
        && variable?.forward == null
    ) {
        null
    } else {
        AllSpeedInformation(forwardVehicleMap, advisory?.forward, variable?.forward)
    }

    val allSpeedBackward = if (
        backwardVehicleMap == null
        && advisory?.backward == null
        && variable?.backward == null
    ) {
        null
    } else {
        AllSpeedInformation(backwardVehicleMap, advisory?.backward, variable?.backward)
    }

    if (allSpeedForward == null && allSpeedBackward == null && wholeRoadType == null) {
        return null
    }

    return ForwardAndBackwardAllSpeedInformation(
        allSpeedForward,
        allSpeedBackward,
        wholeRoadType
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
    if (speed !is MaxSpeedSign) return null
    return AdvisorySpeedSign(speed.value)
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
 *  limit, or there is invalid tagging, false if there is explicitly no variable limit, true for any
 *  other value. */
fun isVariableLimit(tags: Map<String, String>, direction: String?): Boolean? {
    val dir = if (direction != null) ":$direction" else ""
    val maxspeedVariableTag = when (tags["maxspeed:variable$dir"]) {
        null -> null
        "no" -> false
        else -> true
    }
    val maxspeedTag = when (tags["maxspeed$dir"]) {
        null -> null
        "signals" -> true
        else -> null
    }
    return when {
        maxspeedVariableTag == null -> maxspeedTag
        maxspeedTag == null -> maxspeedVariableTag
        maxspeedTag == maxspeedVariableTag -> maxspeedTag
        else -> null // mismatching values, treat as not set
    }
}

fun createVehicleConditionalMap(tags: Map<String, String>, countryInfo: CountryInfo): Map<String?, ForwardAndBackwardConditionalMaxspeed?>? {
    val vehicleMap = mutableMapOf<String?, ForwardAndBackwardConditionalMaxspeed?>()
    (setOf(null) + VEHICLE_TYPES).forEach { v ->
        val m = createForwardAndBackwardConditionalMaxspeed(tags, countryInfo, v)
        vehicleMap[v] = ForwardAndBackwardConditionalMaxspeed(
            m.forward?.filterValues { it != null },
            m.backward?.filterValues { it != null }
        )
    }
    if (vehicleMap.isEmpty()) return null
    return vehicleMap
}

/** Creates a ForwardAndBackwardConditionalMaxspeed object of maps of conditions to MaxspeedAndType
 *  for each direction for the given [vehicleType]. Conditions applying to both directions are split
 *  into each direction. Any clashing values are replaced with Invalid. */
fun createForwardAndBackwardConditionalMaxspeed(tags: Map<String, String>, countryInfo: CountryInfo, vehicleType: String?): ForwardAndBackwardConditionalMaxspeed {
    val forwardConditional = createConditionalMaxspeed(tags, "forward", vehicleType)
    val backwardConditional = createConditionalMaxspeed(tags, "backward", vehicleType)
    val bothConditional = createConditionalMaxspeed(tags, null, vehicleType)
    val unconditional = createForwardAndBackwardMaxspeedAndType(tags, countryInfo, vehicleType)

    val unconditionalMapForward = mapOf<Condition, MaxspeedAndType?>(NoCondition to unconditional?.forward)
    val unconditionalMapBackward = mapOf<Condition, MaxspeedAndType?>(NoCondition to unconditional?.backward)

    return when {
        forwardConditional == null && backwardConditional == null && bothConditional == null -> {
            ForwardAndBackwardConditionalMaxspeed(unconditionalMapForward, unconditionalMapBackward)
        }
        forwardConditional == null && backwardConditional == null -> {
            ForwardAndBackwardConditionalMaxspeed(
                combineConditionalMaxspeedMaps(bothConditional, unconditionalMapForward),
                combineConditionalMaxspeedMaps(bothConditional, unconditionalMapBackward)
            )
        }
        bothConditional == null -> {
            ForwardAndBackwardConditionalMaxspeed(
                combineConditionalMaxspeedMaps(forwardConditional, unconditionalMapForward),
                combineConditionalMaxspeedMaps(backwardConditional, unconditionalMapBackward)
            )
        }
        // Need to combine directions, e.g. we might have maxspeed:conditional and maxspeed:backward:conditional
        else -> {
            ForwardAndBackwardConditionalMaxspeed(
                combineConditionalMaxspeedMaps(
                    combineConditionalMaxspeedMaps(forwardConditional, unconditionalMapForward),
                    bothConditional
                ),
                combineConditionalMaxspeedMaps(
                    combineConditionalMaxspeedMaps(backwardConditional, unconditionalMapBackward),
                    bothConditional
                )
            )
        }
    }
}

/*  Not gathering type with conditional maxspeed because it has barely been used
 *  (source:maxspeed:conditional has the most uses, with fewer than 1300 and almost all values
 *  are "sign" anyway. The return type still needs to include type so we can merge with plain speed */
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
fun combineConditionalMaxspeedMaps(a: Map<Condition, MaxspeedAndType?>?, b: Map<Condition, MaxspeedAndType?>?): Map<Condition, MaxspeedAndType?>? {
    if (a == null) {
        return b
    } else if (b == null) {
        return a
    }
    // plus prefers the elements on the right, so these are different if there is a clash of values
    else if (a + b == b + a) {
        return a + b
    }

    val newMap = mutableMapOf<Condition, MaxspeedAndType?>()

    // Any mismatching values get changed to Invalid
    // Reason: it seems that someone wanted to set a different speed e.g. when it's wet, so we
    // want to indicate that there 'should' be a value here, rather than just returning null
    a.forEach { (k, v) ->
        if (b.keys.contains(k) && b[k] != v) {
            newMap[k] = MaxspeedAndType(Invalid, Invalid)
        } else {
            newMap[k] = v
        }
    }
    // plus prefers values on the right, so the invalid values replace anything in second
    return b + newMap
}

/** Creates a combined set of MaxspeedAndType for forwards and backwards. If there is any over-
 *  defined tagging (e.g. maxspeed= and maxspeed:forward= then Invalid speed and type are returned. */
fun createForwardAndBackwardMaxspeedAndType(tags: Map<String, String>, countryInfo: CountryInfo, vehicleType: String? = null): ForwardAndBackwardMaxspeedAndType? {
    if ( !hasAnyMaxspeedTaggingForDirectionAndVehicle(tags, null, vehicleType) ) return null

    val forward = createMaxspeedAndType(tags, countryInfo, "forward", vehicleType)
    val backward = createMaxspeedAndType(tags, countryInfo, "backward", vehicleType)
    val both = createMaxspeedAndType(tags, countryInfo, null, vehicleType)

    if (forward == null && backward == null && both == null) return null

    // Invalid to have speed tagged for one directions and no direction both tagged
    // e.g. maxspeed along with maxspeed:forward or maxspeed:backward
    val overDefinedExplicits = ( forward?.explicit != null || backward?.explicit != null ) && both?.explicit != null
    val overDefinedTypes = ( forward?.type != null || backward?.type != null ) && both?.type != null

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

    if ( !hasAnyMaxspeedTaggingForDirectionAndVehicle(tags, direction, vehicleType) ) return null

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

    if (maxspeedValue == null && maxspeedType == null) return null
    return MaxspeedAndType(maxspeedValue, maxspeedType)
}

/** Looks at tags that affect everything and are unrelated to vehicle and direction
 *  e.g. living street or school zone. */
private fun createWholeRoadType(tags: Map<String, String>, countryInfo: CountryInfo): MaxSpeedAnswer? {
    // Check for school zone first, because if there is a road that is both then if it was displayed
    // as a living street then there would be no way to change the tags to mark it as a school zone
    val livingStreetAsType = createMaxspeedAndType(tags, countryInfo, null, null)
    val isLivingStreetAsType = livingStreetAsType?.type is LivingStreet
    return when {
        isSchoolZone(tags) -> IsSchoolZone
        isLivingStreet(tags) -> LivingStreet(null)
        isLivingStreetAsType -> livingStreetAsType?.type
        isMotorway(tags) -> ImplicitMaxSpeed("", MOTORWAY, createLitStatus(tags))
        isTrunk(tags) -> ImplicitMaxSpeed("", TRUNK, createLitStatus(tags))
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
    return when {
        value == "signals" -> null // Dealt with as variable maxspeed, treat as if maxspeed is not set
        maxspeedIsWalk(value) -> WalkMaxSpeed
        maxspeedIsNone(value) -> MaxSpeedIsNone
        isInMph(value) -> {
            when (val speed = getMaxspeedInMph(value)?.roundToInt()) {
                null -> Invalid
                else -> MaxSpeedSign(Mph(speed))
            }
        }
        else -> {
            // Null if speed can't be converted to float, i.e. it is not just a number
            // maybe it has other units, that is invalid here
            when (val speed = getMaxspeedInKmh(value)?.roundToInt()) {
                null -> Invalid
                else -> MaxSpeedSign(Kmh(speed))
            }
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
