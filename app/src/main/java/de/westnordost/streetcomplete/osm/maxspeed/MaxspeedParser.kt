package de.westnordost.streetcomplete.osm.maxspeed

import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.quests.max_speed.Kmh
import de.westnordost.streetcomplete.quests.max_speed.Mph
import de.westnordost.streetcomplete.util.ktx.containsAny
import kotlin.math.roundToInt

/** Returns explicit and implicit maxspeed. Returns null if there is no such tagging */
fun createMaxspeedAndType(tags: Map<String, String>, countryInfo: CountryInfo): MaxspeedAndType? {
    if (
        !tags.keys.containsAny(MAXSPEED_TYPE_KEYS) &&
        !tags.keys.containsAny(MAXSPEED_KEYS) &&
        !isLivingStreet(tags) &&
        !isSchoolZone(tags)
    ) return null

    val maxspeedTag = tags["maxspeed"]

    var maxspeedType: MaxSpeedAnswer?
    val impliedMaxspeedValue: MaxSpeedAnswer?

    val taggedMaxspeedType = createImplicitMaxspeed(tags["maxspeed:type"], tags, countryInfo)
    val taggedSourceMaxspeed = createImplicitMaxspeed(tags["source:maxspeed"], tags, countryInfo)
    val taggedZoneMaxspeed = createImplicitMaxspeed(tags["zone:maxspeed"], tags, countryInfo) // e.g. "DE:30", "DE:urban" etc.
    val taggedZoneTraffic = createImplicitMaxspeed(tags["zone:traffic"], tags, countryInfo) // e.g. "DE:urban", "DE:zone30" etc.

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

    var maxspeedValue = createExplicitMaxspeed(tags)

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
private fun createExplicitMaxspeed(tags: Map<String, String>): MaxSpeedAnswer? {
    val maxspeed = tags["maxspeed"] ?: return null
    return if (isInMph(maxspeed)) {
        val speed = getMaxspeedinMph(tags)?.roundToInt()
        if (speed != null) {
            MaxSpeedSign(Mph(speed))
        } else {
            Invalid
        }
    }
    // "walk" and "none" are values in "maxspeed", rather than "maxspeed:type"
    else if (maxspeedIsWalk(tags)) {
        WalkMaxSpeed
    } else if (maxspeedIsNone(tags)) {
        MaxSpeedIsNone
    } else {
        // Null if speed can't be converted to float, i.e. it is not just a number
        // maybe it has other units, that is invalid here
        val speed = getMaxspeedInKmh(tags)?.roundToInt()
        if (speed != null) {
            MaxSpeedSign(Kmh(speed))
        } else {
            Invalid
        }
    }
}
