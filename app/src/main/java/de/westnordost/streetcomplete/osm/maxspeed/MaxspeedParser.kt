package de.westnordost.streetcomplete.osm.maxspeed

import de.westnordost.streetcomplete.data.meta.CountryInfos
import de.westnordost.streetcomplete.quests.max_speed.Kmh
import de.westnordost.streetcomplete.quests.max_speed.Mph
import de.westnordost.streetcomplete.util.ktx.containsAny
import kotlin.math.roundToInt

/** Returns explicit and implicit maxspeed. Returns null if there is no such tagging */
fun createMaxspeedAndType(tags: Map<String, String>, countryInfos: CountryInfos): MaxspeedAndType? {
    if (
        !tags.keys.containsAny(MAXSPEED_TYPE_KEYS) &&
        !tags.keys.containsAny(MAXSPEED_KEYS) &&
        !isLivingStreet(tags) &&
        !isSchoolZone(tags)
    ) return null

    val maxspeedTag = tags["maxspeed"]

    var maxspeedType: MaxSpeedAnswer?
    val impliedMaxspeedValue: MaxSpeedAnswer?

    val taggedMaxspeedType = createImplicitMaxspeed(tags["maxspeed:type"], countryInfos)
    val taggedSourceMaxspeed = createImplicitMaxspeed(tags["source:maxspeed"], countryInfos)
    val taggedZoneMaxspeed = createImplicitMaxspeed(tags["zone:maxspeed"], countryInfos) // e.g. "DE:30", "DE:urban" etc.
    val taggedZoneTraffic = createImplicitMaxspeed(tags["zone:traffic"], countryInfos) // e.g. "DE:urban", "DE:zone30" etc.

    // Assume that invalid "source:maxspeed" means that it is actual "source", not type
    val maxspeedTypesList = if (taggedSourceMaxspeed == Invalid) {
        listOf(taggedMaxspeedType, taggedZoneMaxspeed, taggedZoneTraffic)
    } else {
        listOf(taggedMaxspeedType, taggedSourceMaxspeed, taggedZoneMaxspeed, taggedZoneTraffic)
    }

    // Determine if there is mismatching tagging of types
    val distinctMaxspeedTypesList = maxspeedTypesList.distinct().filterNotNull()
    // Empty if all values are null
    maxspeedType = if (distinctMaxspeedTypesList.isEmpty()) {
        null
    }
    // More than one unique value means invalid
    else if (distinctMaxspeedTypesList.size != 1) {
        Invalid
    }
    // All non-blank values are the same
    else {
        distinctMaxspeedTypesList.first()
    }

    var maxspeedValue = createExplicitMaxspeed(tags)

    // maxspeed has a non-numerical value, see if it is valid
    if (maxspeedValue == Invalid && maxspeedTag != null) {
        val maxspeedAsType = if (isImplicitMaxspeed(maxspeedTag)) {
            createImplicitMaxspeed(maxspeedTag, countryInfos)
        } else {
            null
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

    // If there is no other maxspeed tagging over-riding it then there are other tags that are
    // essentially an implicit maxspeed of themselves
    if (maxspeedType == null && maxspeedValue == null) {
        maxspeedType = when {
            isLivingStreet(tags) -> IsLivingStreet
            isSchoolZone(tags) -> IsSchoolZone
            else -> null
        }
    }

    return MaxspeedAndType(maxspeedValue, maxspeedType)
}

private fun createImplicitMaxspeed(value: String?, countryInfos: CountryInfos): MaxSpeedAnswer? {
    return when {
        value == null -> null
        value == "sign" -> JustSign
        // Check for other implicit types first because the format is the same as implicit format
        isZoneMaxspeed(value) -> getZoneMaxspeed(value, countryInfos)
        isLivingStreetMaxspeed(value) -> IsLivingStreet
        isImplicitMaxspeed(value) -> getImplicitMaxspeed(value)
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
