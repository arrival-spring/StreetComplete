package de.westnordost.streetcomplete.osm.maxspeed

import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.quests.max_speed.Kmh
import de.westnordost.streetcomplete.quests.max_speed.Mph
import de.westnordost.streetcomplete.util.ktx.containsAny
import kotlin.math.roundToInt

fun createForwardAndBackwardMaxspeedAndType(tags: Map<String, String>, countryInfo: CountryInfo): ForwardAndBackwardMaxspeedAndType? {
    if (
        !hasAnyMaxspeedTagging(tags) &&
        !isLivingStreet(tags) &&
        !isSchoolZone(tags)
    ) return null

    val forward = createMaxspeedAndType(tags, countryInfo, "forward", null)
    val backward = createMaxspeedAndType(tags, countryInfo, "backward", null)
    val both = createMaxspeedAndType(tags, countryInfo, null, null)

    // Invalid to have speed tagged for one directions and no direction both tagged
    // e.g. maxspeed along with maxspeed:forward and maxspeed:backward
    val overDefinedExplicits = forward?.explicit != null && backward?.explicit != null && both?.explicit != null
    val overDefinedTypes = forward?.type != null && backward?.type != null && both?.type != null
    // Types show as over-defined for school zones and living streets
    if ((overDefinedExplicits || overDefinedTypes) && !isLivingStreet(tags) && !isSchoolZone(tags)) {
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

private fun hasAnyMaxspeedTagging(tags: Map<String, String>): Boolean {
    val containsAnyMaxspeed = MAXSPEED_KEYS.any { m -> tags.filterKeys { it.startsWith(m) }.isNotEmpty() }
    val containsAnyMaxspeedType = MAXSPEED_TYPE_KEYS.any { m -> tags.filterKeys { it.startsWith(m) }.isNotEmpty() }
    return containsAnyMaxspeed || containsAnyMaxspeedType
}

/** Returns explicit and implicit maxspeed. Returns null if there is no such tagging */
fun createMaxspeedAndType(tags: Map<String, String>, countryInfo: CountryInfo, direction: String? = null, vehicleType: String? = null): MaxspeedAndType? {
    val dir = if (direction != null) ":$direction" else ""
    val veh = if (vehicleType != null) ":$vehicleType" else ""

    if (
        !tags.keys.containsAny(MAXSPEED_TYPE_KEYS.map { "$it$veh$dir" }) &&
        !tags.keys.containsAny(MAXSPEED_KEYS.map { "$it$veh$dir" }) &&
        !isLivingStreet(tags) &&
        !isSchoolZone(tags)
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

    // If there is no maxspeed type tagging over-riding it then there are other tags that are
    // essentially an implicit maxspeed of themselves
    if (maxspeedType == null) {
        maxspeedType = when {
            // Check for school zone first, because if there is a road that is both then if we displayed
            // it as a living street then there would be no way to change the tags to mark it as a school zone
            isSchoolZone(tags) -> IsSchoolZone
            isLivingStreet(tags) -> LivingStreet(null)
            else -> null
        }
    }

    return MaxspeedAndType(maxspeedValue, maxspeedType)
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
private fun createExplicitMaxspeed(value: String?): MaxSpeedAnswer? {
    if (value == null) return null
    // "walk" and "none" are values in "maxspeed", rather than "maxspeed:type"
    return if (maxspeedIsWalk(value)) {
        WalkMaxSpeed
    } else if (maxspeedIsNone(value)) {
        MaxSpeedIsNone
    } else if (isInMph(value)) {
        val speed = getMaxspeedinMph(value)?.roundToInt()
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
