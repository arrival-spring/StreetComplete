package de.westnordost.streetcomplete.osm.maxspeed

import de.westnordost.streetcomplete.data.meta.CountryInfos
import de.westnordost.streetcomplete.data.meta.SpeedMeasurementUnit
import de.westnordost.streetcomplete.quests.max_speed.Kmh
import de.westnordost.streetcomplete.quests.max_speed.Mph

val MAXSPEED_KEYS = setOf(
    "maxspeed",
    "maxspeed:advisory",
    "maxspeed:advised",
)

val MAXSPEED_TYPE_KEYS_EXCEPT_SOURCE = setOf(
    "maxspeed:type",
    "zone:maxspeed",
    "zone:traffic"
)

val MAXSPEED_TYPE_KEYS = setOf(
    "source:maxspeed"
) + MAXSPEED_TYPE_KEYS_EXCEPT_SOURCE

// Taken fom "access" wiki page
// Many of these may not have been used (very much) for maxspeed, but it is possible that they would be
val VEHICLE_TYPES = setOf(
    "vehicle",
    "bicycle", "electric_bicycle", "kick_scooter",
    "carriage", "cycle_rickshaw", "trailer", "caravan",
    "motor_vehicle", "motorcycle", "moped", "speed_pedelec", "mofa", "small_electric_vehicle",
    "motorcar", "motorhome", "tourist_bus", "coach",
    "goods", "hgv", "hgv_articulated", "bdouble",
    "agricultural", "auto_rickshaw", "nev", "golf_cart", "atv", "ohv",
    "psv", "bus", "taxi", "minibus", "share_taxi",
    "hazmat"
)

private val implicitRegex = Regex("([A-Z]+-?[A-Z]*):(.*)")
private val zoneRegex = Regex("([A-Z-]+-?[A-Z]*):(?:zone)?:?([0-9]+)")
private val livingStreetRegex = Regex("([A-Z-]+-?[A-Z]*):(?:living_street)?")
private val mphRegex = Regex("([0-9]+) mph")

fun isImplicitMaxspeed(value: String): Boolean {
    return implicitRegex.matchEntire(value) != null
}

fun getImplicitMaxspeed(value: String, tags: Map<String, String>): ImplicitMaxSpeed? {
    val matchResult = implicitRegex.matchEntire(value)
    return if (matchResult != null) {
        val typeSpeed = matchResult.groupValues[2]
        val countryCode = matchResult.groupValues[1]
        val lit = when {
            tags["lit"] == null -> null
            tags["lit"] == "yes" || tags["lit"] == "24/7" -> true
            tags["lit"] == "no" -> false
            // null on unknown values
            else -> null
        }
        ImplicitMaxSpeed(countryCode, typeSpeed, lit)
    } else {
        null
    }
}

fun isZoneMaxspeed(value: String): Boolean {
    return zoneRegex.matchEntire(value) != null
}

/* Needs to know the country code because MaxSpeedZone contains a Speed, which requires units.
 * (At the moment) there are no countries which have both maxspeed zones and a mixture of speed
 * units (per country_metadata). */
fun getZoneMaxspeed(value: String, countryInfos: CountryInfos): MaxSpeedZone? {
    val matchResult = zoneRegex.matchEntire(value)
    if (matchResult != null) {
        val zoneSpeed = matchResult.groupValues[2].toIntOrNull()
        val countryCode = matchResult.groupValues[1]
        val speedUnit = countryInfos.get(listOf(countryCode)).speedUnits.first()
        if (zoneSpeed != null) {
            val speed = when (speedUnit) {
                SpeedMeasurementUnit.MILES_PER_HOUR -> Mph(zoneSpeed)
                SpeedMeasurementUnit.KILOMETERS_PER_HOUR -> Kmh(zoneSpeed)
            }
            return MaxSpeedZone(speed, countryCode, "zone$zoneSpeed")
        }
    }
    return null
}

fun isLivingStreetMaxspeed(value: String): Boolean {
    val matchResult = livingStreetRegex.matchEntire(value)
    return matchResult != null
}

fun isValidMaxspeedType(value: String?): Boolean {
    return if (value == null) false
    else isImplicitMaxspeed(value) || isZoneMaxspeed(value) || value == "sign"
}

fun maxspeedIsNone(tags: Map<String, String>): Boolean {
    return tags["maxspeed"] == "none"
}

fun maxspeedIsWalk(tags: Map<String, String>): Boolean {
    return tags["maxspeed"] == "walk"
}

fun isInMph(value: String): Boolean {
    return mphRegex.matchEntire(value) != null
}

fun getMaxspeedinMph(tags: Map<String, String>): Float? {
    val speed = tags["maxspeed"] ?: return null
    return if (speed.endsWith(" mph")) {
        return speed.substring(0, speed.length - 4).toFloatOrNull()
    } else null
}

fun isLivingStreet(tags: Map<String, String>): Boolean {
    return (tags["living_street"] == "yes" || tags["highway"] == "living_street")
}

fun isSchoolZone(tags: Map<String, String>): Boolean {
    return (tags["hazard"] == "school_zone")
}

fun getCountryCodeFromMaxspeedType(value: String): String? {
    val matchResult = implicitRegex.matchEntire(value)
    return when {
        matchResult != null -> matchResult.groupValues[1]
        else -> null
    }
}

/** Functions to get speed in km/h from tags */

fun getMaxspeedInKmh(tags: Map<String, String>): Float? {
    val speed = tags["maxspeed"] ?: return null
    return if (speed.endsWith(" mph")) {
        getMaxspeedinMph(tags)
    } else {
        speed.toFloatOrNull()
    }
}

fun guessMaxspeedInKmh(tags: Map<String, String>, countryInfos: CountryInfos? = null): Float? {
    for (key in (MAXSPEED_TYPE_KEYS + "maxspeed")) {
        val value = tags[key] ?: continue
        when {
            value.endsWith("living_street")  -> return 10f
            value.endsWith("urban")          -> return 50f
            value.endsWith("nsl_restricted") -> return 50f
            value.endsWith("nsl_single")     -> return 60f
            value.endsWith("rural")          -> return 70f
            value.endsWith("nsl_dual")       -> return 70f
            value.endsWith("trunk")          -> return 100f
            value.endsWith("motorway")       -> return 120f
            value == "walk"                  -> return 5f
        }

        val matchResult = zoneRegex.matchEntire(value)
        if (matchResult != null) {
            val zoneSpeed = matchResult.groupValues[2].toFloatOrNull()
            val countryCode = matchResult.groupValues[1]
            val isMilesPerHour = countryInfos?.get(listOf(countryCode))?.speedUnits?.first()?.let {
                it == SpeedMeasurementUnit.MILES_PER_HOUR
            }
            if (zoneSpeed != null) {
                return if (isMilesPerHour == true) zoneSpeed * 1.609344f else zoneSpeed
            }
        }
    }
    return null
}
