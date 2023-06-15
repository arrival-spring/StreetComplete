package de.westnordost.streetcomplete.osm.maxspeed

import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.osm.bicycle_boulevard.BicycleBoulevard
import de.westnordost.streetcomplete.osm.bicycle_boulevard.createBicycleBoulevard
import de.westnordost.streetcomplete.osm.lit.createLitStatus
import de.westnordost.streetcomplete.osm.maxspeed.Direction.* // ktlint-disable no-unused-imports
import kotlin.math.roundToInt

fun createForwardAndBackwardAllSpeedInformation(tags: Map<String, String>, countryInfo: CountryInfo): ForwardAndBackwardAllSpeedInformation? {
    val wholeRoadType = createWholeRoadType(tags, countryInfo)
    if (!hasAnyMaxspeedTagging(tags) && wholeRoadType == null) return null
    val vehicleMap = createVehicleConditionalMap(tags, countryInfo)
    val advisory = createForwardAndBackwardAdvisorySpeedSign(tags)
    val variable = createForwardAndBackwardVariableLimit(tags)
    val lit = createLitStatus(tags)
    val dualCarriageway = isDualCarriageway(tags)

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

    return ForwardAndBackwardAllSpeedInformation(allSpeedForward, allSpeedBackward, wholeRoadType, lit, dualCarriageway)
}

/** Combines directions of advisory speeds, returns null if there is no tagging or if the
 *  tagging is invalid, e.g. maxspeed:advisory= and maxspeed:advisory:forward= */
private fun createForwardAndBackwardAdvisorySpeedSign(tags: Map<String, String>): ForwardAndBackwardAdvisorySpeedSign? {
    val forward = createAdvisoryMaxspeed(tags, FORWARD)
    val backward = createAdvisoryMaxspeed(tags, BACKWARD)
    val both = createAdvisoryMaxspeed(tags, BOTH)

    return when {
        both == null && forward == null && backward == null -> null
        both == null -> ForwardAndBackwardAdvisorySpeedSign(forward, backward)
        forward == null && backward == null -> ForwardAndBackwardAdvisorySpeedSign(both, both)
        else -> null // All three have something, this is over-tagging so return null
    }
}

// maxspeed:advisory has not been used for different vehicles (other than 12 times for bicycle),
// so only creating for the blank tag
private fun createAdvisoryMaxspeed(tags: Map<String, String>, direction: Direction): AdvisorySpeedSign? {
    val speed = createExplicitMaxspeed(tags["maxspeed:advisory$direction"]) ?: createExplicitMaxspeed(tags["maxspeed:advised$direction"])
    if (speed !is MaxSpeedSign) return null
    return AdvisorySpeedSign(speed.value)
}

/** Returns the combined directions of variable limits. null if there is no tagging or there is
 *  conflicting tagging. */
private fun createForwardAndBackwardVariableLimit(tags: Map<String, String>): ForwardAndBackwardVariableLimit? {
    val forward = isVariableLimit(tags, FORWARD)
    val backward = isVariableLimit(tags, BACKWARD)
    val both = isVariableLimit(tags, BOTH)

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
private fun isVariableLimit(tags: Map<String, String>, direction: Direction): Boolean? {
    val maxspeedVariableTag = when (tags["maxspeed:variable$direction"]) {
        null -> null
        "no" -> false
        else -> true
    }
    val maxspeedTag = when (tags["maxspeed$direction"]) {
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

private fun createVehicleConditionalMap(tags: Map<String, String>, countryInfo: CountryInfo): Map<String?, ForwardAndBackwardConditionalMaxspeed?>? {
    val vehicleMap = mutableMapOf<String?, ForwardAndBackwardConditionalMaxspeed?>()
    (setOf(null) + VEHICLE_TYPES).forEach { veh ->
        val m = createForwardAndBackwardConditionalMaxspeed(tags, countryInfo, veh)
        vehicleMap[veh] = ForwardAndBackwardConditionalMaxspeed(m.forward, m.backward)
    }
    if (vehicleMap.isEmpty()) return null
    return vehicleMap
}

/** Creates a ForwardAndBackwardConditionalMaxspeed object of maps of conditions to MaxspeedAndType
 *  for each direction for the given [vehicleType]. Conditions applying to both directions are split
 *  into each direction. Any clashing values are replaced with Invalid. */
private fun createForwardAndBackwardConditionalMaxspeed(tags: Map<String, String>, countryInfo: CountryInfo, vehicleType: String?): ForwardAndBackwardConditionalMaxspeed {
    val forwardConditional = createConditionalMaxspeed(tags, FORWARD, vehicleType)
    val backwardConditional = createConditionalMaxspeed(tags, BACKWARD, vehicleType)
    val bothConditional = createConditionalMaxspeed(tags, BOTH, vehicleType)
    val unconditional = createForwardAndBackwardMaxspeedAndType(tags, countryInfo, vehicleType)

    val unconditionalMapForward = if (unconditional?.forward != null) {
        mapOf<Condition, MaxspeedAndType>(NoCondition to unconditional.forward)
    } else null
    val unconditionalMapBackward = if (unconditional?.backward != null) {
        mapOf<Condition, MaxspeedAndType>(NoCondition to unconditional.backward)
    } else null

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
 *  are "sign" anyway. Additionally, some uses are actual conditional restrictions on the type.
 *  The return type still needs to include type so we can merge with plain speed */
/** Creates a map of conditions to MaxspeedAndType for the given [direction] and [vehicleType],
 *  including a map from NoCondition for the bare tags. */
fun createConditionalMaxspeed(tags: Map<String, String>, direction: Direction, vehicleType: String?): Map<Condition, MaxspeedAndType>? {
    val veh = if (vehicleType != null) ":$vehicleType" else ""
    val speedKey = "maxspeed$veh$direction:conditional"
    val fromMaxspeedConditional = getConditionalMaxspeed(tags[speedKey])?.mapValues { MaxspeedAndType(it.value, null) }
    val otherConditionals = mutableMapOf<Condition, MaxspeedAndType>()

    CONDITIONAL_VALUE_TAG_SYNONYMS.forEach {
        if (tags["${it.key}$veh$direction"] != null) {
            otherConditionals[it.value] = MaxspeedAndType(createExplicitMaxspeed(tags["${it.key}$veh$direction"]), null)
        }
    }

    return combineConditionalMaxspeedMaps(fromMaxspeedConditional, otherConditionals)
}

/** Combines two conditional maxspeed maps, setting any to invalid where the values for the
 *  conditions are not equal. */
private fun combineConditionalMaxspeedMaps(a: Map<Condition, MaxspeedAndType>?, b: Map<Condition, MaxspeedAndType>?): Map<Condition, MaxspeedAndType>? {
    when {
        a == null -> return b
        b == null -> return a
        // plus prefers the elements on the right, so these are different if there is a clash of values
        a + b == b + a -> return a + b
    }

    val newMap = mutableMapOf<Condition, MaxspeedAndType>()

    // Any mismatching values get changed to Invalid
    // Reason: it seems that someone wanted to set a different speed e.g. when it's wet, so we
    // want to indicate that there 'should' be a value here, rather than just returning null
    a!!.forEach { (k, v) ->
        if (b!!.keys.contains(k) && b[k] != v) {
            newMap[k] = MaxspeedAndType(Invalid, Invalid)
        } else {
            newMap[k] = v
        }
    }
    // plus prefers values on the right, so the invalid values replace anything in b
    return b!! + newMap
}

/** Creates a combined set of MaxspeedAndType for forwards and backwards. If there is any over-
 *  defined tagging (e.g. maxspeed= and maxspeed:forward= then Invalid speed and type are returned. */
private fun createForwardAndBackwardMaxspeedAndType(tags: Map<String, String>, countryInfo: CountryInfo, vehicleType: String? = null): ForwardAndBackwardMaxspeedAndType? {
    val veh = if (vehicleType != null) ":$vehicleType" else ""

    if ( !hasAnyMaxspeedTaggingForDirectionAndVehicle(tags, BOTH, vehicleType) ) return null

    val forward = createMaxspeedAndType(tags, countryInfo, FORWARD, vehicleType)
    val backward = createMaxspeedAndType(tags, countryInfo, BACKWARD, vehicleType)
    val both = createMaxspeedAndType(tags, countryInfo, BOTH, vehicleType)

    if (forward == null && backward == null && both == null) return null

    // Invalid to have speed tagged for one directions and no direction both tagged
    // e.g. maxspeed along with maxspeed:forward or maxspeed:backward
    val overDefinedExplicits = ( forward?.explicit != null || backward?.explicit != null ) && both?.explicit != null
    val overDefinedTypes = ( forward?.type != null || backward?.type != null ) && both?.type != null

    if (overDefinedExplicits || overDefinedTypes) {
        return ForwardAndBackwardMaxspeedAndType(MaxspeedAndType(Invalid, Invalid), MaxspeedAndType(Invalid, Invalid))
    }

    // Could have maxspeed and maxspeed:lanes:forward (without maxspeed:lanes)
    val maxspeedValueBoth = both?.explicit
    val taggedLanesForward = tags["maxspeed$veh:lanes$FORWARD"]
    val taggedLanesBackward = tags["maxspeed$veh:lanes$BACKWARD"]
    if (forward == null && backward == null
        && (taggedLanesForward != null || taggedLanesBackward != null)
    ) {
        val maxspeedValueForward =
            taggedLanesForward?.let { getMaxspeedValueWhenLanesIsGiven(maxspeedValueBoth, it) } ?: maxspeedValueBoth
        val maxspeedValueBackward =
            taggedLanesBackward?.let { getMaxspeedValueWhenLanesIsGiven(maxspeedValueBoth, it) } ?: maxspeedValueBoth
        return ForwardAndBackwardMaxspeedAndType(
            MaxspeedAndType(maxspeedValueForward, both?.type),
            MaxspeedAndType(maxspeedValueBackward, both?.type)
        )
    }

    // Could be that it is e.g. living street, so forward and backward are not null, but there is
    // also maxspeed tagged
    return when {
        forward?.explicit != null || backward?.explicit != null -> ForwardAndBackwardMaxspeedAndType(forward, backward)
        forward?.type == backward?.type && forward?.type != Invalid -> ForwardAndBackwardMaxspeedAndType(both, both)
        else -> ForwardAndBackwardMaxspeedAndType(forward, backward)
    }
}

/** Returns explicit and implicit maxspeed. Returns null if there is no such tagging for the
 *  given [direction] and [vehicleType] */
private fun createMaxspeedAndType(tags: Map<String, String>, countryInfo: CountryInfo, direction: Direction = BOTH, vehicleType: String? = null): MaxspeedAndType? {
    val veh = if (vehicleType != null) ":$vehicleType" else ""

    if ( !hasAnyMaxspeedTaggingForDirectionAndVehicle(tags, direction, vehicleType) ) return null

    val taggedMaxspeed = tags["maxspeed$veh$direction"]

    var maxspeedType: MaxSpeedAnswer?
    val impliedMaxspeedValue: MaxSpeedAnswer?

    val taggedMaxspeedType = createImplicitMaxspeed(
        tags["maxspeed:type$veh$direction"],
        countryInfo
    )
    val taggedSourceMaxspeed = createImplicitMaxspeed(
        tags["source:maxspeed$veh$direction"],
        countryInfo
    )
    val taggedZoneMaxspeed = createImplicitMaxspeed(
        tags["zone:maxspeed$veh$direction"],
        countryInfo
    ) // e.g. "DE:30", "DE:urban" etc.
    val taggedZoneTraffic = createImplicitMaxspeed(tags["zone:traffic$veh$direction"], countryInfo) // e.g. "DE:urban", "DE:zone30" etc.
    val taggedMaxspeedSigned = tags["maxspeed$veh$direction:signed"] != "no"

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

    // Prefer tagged implicit type to just "maxspeed:signed=no"
    if (maxspeedType == null && !taggedMaxspeedSigned) {
        maxspeedType = NoSign
    }

    var maxspeedValue = createExplicitMaxspeed(taggedMaxspeed)

    // If maxspeed has a non-numerical value, see if it is valid type instead
    if (maxspeedValue == Invalid && taggedMaxspeed != null) {
        val maxspeedAsType = when {
            isImplicitMaxspeed(taggedMaxspeed) -> createImplicitMaxspeed(
                taggedMaxspeed,
                countryInfo
            )
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

    // Compare maxspeed to values in :lanes
    val taggedLanesValue = tags["maxspeed$veh:lanes$direction"]
    if (taggedLanesValue != null) {
        maxspeedValue = getMaxspeedValueWhenLanesIsGiven(maxspeedValue, taggedLanesValue)
    }

    if (maxspeedValue == null && maxspeedType == null) return null
    return MaxspeedAndType(maxspeedValue, maxspeedType)
}

/** Returns the value that should be used for maxspeed when the speed is also given by lane.
 *  Invalid if the [maxspeedValue] is not equal to the maximum of the values in the lanes
 *  (unless any lane is empty) or if the [maxspeedValue] is less than the maximum of the lanes. */
private fun getMaxspeedValueWhenLanesIsGiven(maxspeedValue: MaxSpeedAnswer?, lanesValue: String): MaxSpeedAnswer? {
    val highestLaneSpeed = getHighestLaneSpeed(lanesValue)
    val highestLaneSpeedValue = getHighestLaneSpeedValue(lanesValue)
    val hasNoEmptyLanes = !anyLanesSpeedIsEmpty(lanesValue)
    return when {
        // Only take the max from lanes if all lanes have a speed
        maxspeedValue == null && hasNoEmptyLanes -> highestLaneSpeed
        maxspeedValue == null -> null
        maxspeedValue !is MaxSpeedSign -> maxspeedValue
        highestLaneSpeed == maxspeedValue -> maxspeedValue
        hasNoEmptyLanes -> Invalid
        highestLaneSpeedValue > maxspeedValue.value.toValue() -> Invalid
        else -> maxspeedValue
    }
}

/** Looks at tags that affect everything and are unrelated to vehicle and direction
 *  e.g. living street or school zone. */
private fun createWholeRoadType(tags: Map<String, String>, countryInfo: CountryInfo): MaxSpeedAnswer? {
    // Check for school zone first, because if there is a road that is both then if it was displayed
    // as a living street then there would be no way to change the tags to mark it as a school zone
    val wholeRoadSpeedAndType = createMaxspeedAndType(tags, countryInfo, BOTH, null)
    val isLivingStreetAsType = wholeRoadSpeedAndType?.type is LivingStreet
    val isBicycleBoulevard = createBicycleBoulevard(tags) == BicycleBoulevard.YES
    val isBicycleBoulevardAsType = wholeRoadSpeedAndType?.type is BicycleBoulevardType
    return when {
        isSchoolZone(tags) -> IsSchoolZone
        isLivingStreet(tags) -> LivingStreet(null)
        isLivingStreetAsType -> wholeRoadSpeedAndType!!.type
        isBicycleBoulevard -> BicycleBoulevardType(null)
        isBicycleBoulevardAsType -> wholeRoadSpeedAndType!!.type
        else -> null
    }
}

/** Create an ImplicitMaxspeed object for the given [value]. Needs all tags to determine if the way is lit */
private fun createImplicitMaxspeed(value: String?, countryInfo: CountryInfo): MaxSpeedAnswer? {
    return when {
        value == null -> null
        value == "sign" -> JustSign
        // Check for other implicit types first because the format is the same as implicit format
        getCountryCodeFromMaxspeedType(value) != countryInfo.countryCode -> Invalid
        isZoneMaxspeed(value) -> getZoneMaxspeed(value, countryInfo)
        isLivingStreetType(value) -> LivingStreet(getCountryCodeFromMaxspeedType(value))
        isBicycleBoulevardType(value) -> BicycleBoulevardType(getCountryCodeFromMaxspeedType(value))
        isImplicitMaxspeed(value) -> getImplicitMaxspeed(value)
        else -> Invalid
    }
}

/** Return the explicit maxspeed for the given [value].
 *  Returns invalid if not in mph or a plain number (i.e. in km/h) */
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
            when (val speed = value.toIntOrNull()) {
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

private fun hasAnyMaxspeedTaggingForDirectionAndVehicle(tags: Map<String, String>, direction: Direction, vehicleType: String?): Boolean {
    val veh = if (vehicleType != null) ":$vehicleType" else ""
    val containsAnyMaxspeed = MAXSPEED_KEYS.any { m -> tags.filterKeys { it.startsWith("$m$veh$direction") }.isNotEmpty() }
    val containsAnyLaneMaxspeed = MAXSPEED_KEYS.any { m -> tags.filterKeys { it.startsWith("$m$veh:lanes$direction") }.isNotEmpty() }
    val containsAnyMaxspeedType = MAXSPEED_TYPE_KEYS.any { m -> tags.filterKeys { it.startsWith("$m$veh$direction") }.isNotEmpty() }
    return containsAnyMaxspeed || containsAnyLaneMaxspeed || containsAnyMaxspeedType
}
