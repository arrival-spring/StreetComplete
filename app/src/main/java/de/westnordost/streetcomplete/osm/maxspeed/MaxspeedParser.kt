package de.westnordost.streetcomplete.osm.maxspeed

import de.westnordost.streetcomplete.data.meta.CountryInfos
import de.westnordost.streetcomplete.quests.max_speed.Kmh
import de.westnordost.streetcomplete.quests.max_speed.Mph
import de.westnordost.streetcomplete.quests.max_speed.Speed
import de.westnordost.streetcomplete.util.ktx.containsAny
import kotlin.math.roundToInt

/** Returns explicit and implicit maxspeed. Returns null if there is no such tagging */
fun createMaxspeed(tags: Map<String, String>, countryInfos: CountryInfos): MaxspeedAndType? {
    if (!tags.keys.containsAny(MAXSPEED_TYPE_KEYS) && !tags.keys.containsAny(MAXSPEED_KEYS)) return null

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
    if (maxspeedValue == Invalid && maxspeedTag != null) {
        val maxspeedAsType = if (isImplicitMaxspeed(maxspeedTag)) {
            createImplicitMaxspeed(maxspeedTag, countryInfos)
        }
        // "walk" and "none" are only valid in "maxspeed" tag, not type tags
        else if (maxspeedIsWalk(tags)) {
            WalkMaxSpeed
        } else if (maxspeedIsNone(tags)) {
            MaxSpeedIsNone
        } else {
            null
        }

        // Compare type value in "maxspeed" with separately tagged type
        if (maxspeedAsType != null && maxspeedType == null) {
            maxspeedType = maxspeedAsType
        } else if (maxspeedAsType != maxspeedType) {
            maxspeedType = Invalid
        }
    }

    // "maxspeed:type=sign" and no or invalid "maxspeed" is invalid
    if (maxspeedType is JustSign && maxspeedValue !is Speed) {
        maxspeedType = Invalid
    }

    // Compare implied maxspeed (from zone type) with actual tagged maxspeed
    if (maxspeedType is MaxSpeedZone) {
        impliedMaxspeedValue = taggedZoneMaxspeed
        if (maxspeedValue == null) {
            maxspeedValue = impliedMaxspeedValue
        } else if (maxspeedValue != impliedMaxspeedValue) {
            maxspeedValue = Invalid
            maxspeedType = Invalid // implied maxspeed came from type tag, so mismatch means we don't know which is correct
        }
    }

    return MaxspeedAndType(maxspeedValue, maxspeedType)
}

private fun createImplicitMaxspeed(value: String?, countryInfos: CountryInfos): MaxSpeedAnswer? {
    return if (value == null) {
        null
    } else if (value == "sign") {
        JustSign
    } else if (isImplicitMaxspeed(value)) {
        getImplicitMaxspeed(value)
    } else if (isZoneMaxspeed(value)) {
        getZoneMaxspeed(value, countryInfos)
    } else {
        Invalid
    }
}

// Returns invalid if not in mph or a plain number (i.e. in km/h)
private fun createExplicitMaxspeed(tags: Map<String, String>): MaxSpeedAnswer? {
    val maxspeed = tags["maxspeed"] ?: return null
    return if (isInMph(maxspeed)) {
        val speed = getMaxspeedinMph(tags)?.roundToInt()
        if (speed != null) {
            MaxSpeedSign(Mph(speed))
        } else {
            Invalid
        }
    } else {
        val speed = getMaxspeedInKmh(tags)?.roundToInt() // Null if it can't be converted to float, i.e. it is not just a number
        if (speed != null) {
            MaxSpeedSign(Kmh(speed))
        } else {
            Invalid
        }
    }
}
