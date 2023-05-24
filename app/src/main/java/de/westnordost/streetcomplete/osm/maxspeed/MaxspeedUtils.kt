package de.westnordost.streetcomplete.osm.maxspeed

import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.meta.CountryInfos
import de.westnordost.streetcomplete.data.meta.SpeedMeasurementUnit
import de.westnordost.streetcomplete.osm.maxspeed.Inequality.*
import de.westnordost.streetcomplete.osm.opening_hours.parser.toOpeningHoursRules
import de.westnordost.streetcomplete.osm.weight.createWeight
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

// Everything with more than 500 uses at the time which is not an actual type or a special comment
// These will only be removed when the type is changed
val SOURCE_MAXSPEED_VALUES_THAT_CAN_BE_REMOVED = setOf(
    "Bing", "DNIT", "implicit", "knowledge", "local_knowledge", "Mapillary", "mapillary", "markings", "massgis",
    "OpenStreetCam", "state_limit", "Stats19", "survey", "survey;image", "traffic_sign", "traffic_zone",
    "FDOT \"Maximum Speed Limits\" GIS data, updated August 27, 2011: http://www.dot.state.fl.us/planning/statistics/gis/roaddata.shtm",
    "http://monolitos.montevideo.gub.uy/resoluci.nsf/WEB/Numero/954-13",
    "THE HAMMERSMITH AND FULHAM (20 MPH SPEED LIMIT) EXPERIMENTAL TRAFFIC ORDER 2016",
    "default residential speed limit in Australia", "Sapulpa Sec. 15-401", "PDDUA-PMPA", "MMDA",
    "Stats19 2010-2012", "Arrêté du maire, date d'effet 1/2/2021", "BDOrtho IGN", "survey 2014-06-14",
    "passage à 30km/h sur la commune de Colombes le 1/12/2021", "California vehicle code 22352",
    "Digiroad.fi", "THE CROYDON (20MPH SPEED LIMIT) (NO.2) TRAFFIC ORDER 2016",
    "https://data-atgis.opendata.arcgis.com/datasets/d43c489e7027489b88bcdedffc3be6c6_3/data",
    "CDOT", "Speed Limits Bylaw 2020", "http://www.txdot.gov/safety/speed_limit/75mph.htm"
)

private val implicitRegex = Regex("([A-Z]+-?[A-Z]*):(.*)")
private val zoneRegex = Regex("([A-Z-]+-?[A-Z]*):(?:zone)?:?([0-9]+)")
private val livingStreetRegex = Regex("([A-Z-]+-?[A-Z]*):(?:living_street)?")
private val mphRegex = Regex("([0-9]+) mph")
// private val conditionalRegex = Regex("([0-9]+(?: mph)?) @ [(]?([^)]*)[)]?(?:; ([0-9]+) @ [(]?([^)]*)[)]?)*?")
private val conditionalRegex = Regex("(?:(\\d+(?: mph)?) @ (\\((?:[^)]+)\\)|(?:[^;]+))(?:; )?)+?")
private val weightRegex = Regex("weight ?([<>]=?) ?(\\d+\\.?\\d* ?(?:t|st|lbs|kg)?)")

fun isValidConditionalMaxspeed(value: String?): Boolean {
    return value?.let { conditionalRegex.matchEntire(it) } != null
}

/** Returns a map of the condition and the speed, null if either the condition or speed is invalid */
fun getConditionalMaxspeed(value: String?): Map<Condition, MaxSpeedAnswer>? {
    if (value == null) return null
    val matchResults = conditionalRegex.findAll(value)
    val conditions = mutableMapOf<Condition, MaxSpeedAnswer>()
    for (matchResult in matchResults) {
        val speed = createExplicitMaxspeed(matchResult.groupValues[1])
        val condition = getCondition(matchResult.groupValues[2].removePrefix("(").removeSuffix(")"))
        if (speed != null && speed !is Invalid && condition != null) {
            conditions[condition] = speed
        }
    }
    return conditions
}

private fun getCondition(condition: String?): Condition? {
    if (condition == null) return null
    val conditionAsOpeningHours = condition.toOpeningHoursRules()
    return when {
        condition == "snow" -> Snow
        condition == "wet" -> Wet
        condition == "flashing" -> Flashing
        condition == "winter" -> Winter
        condition.startsWith("weight") -> getWeightAndComparison(condition)
        conditionAsOpeningHours != null -> TimeCondition(condition.toOpeningHoursRules()!!)
        else -> null
    }
}

fun getWeightAndComparison(value: String?): WeightAndComparison? {
    if (value == null) return null
    val matchResult = weightRegex.matchEntire(value) ?: return null
    val inequalityValue = matchResult.groupValues[1]
    val weight = createWeight(matchResult.groupValues[2])
    val inequality = when (inequalityValue) {
        "<" -> LESS_THAN
        ">" -> MORE_THAN
        "<=" -> LESS_THAN_OR_EQUAL
        ">=" -> MORE_THAN_OR_EQUAL
        else -> null
    }
    if (weight == null || inequality == null) return null
    return WeightAndComparison(weight, inequality)
}

fun isImplicitMaxspeed(value: String): Boolean {
    return implicitRegex.matchEntire(value) != null
}

fun getImplicitMaxspeed(value: String, tags: Map<String, String>): ImplicitMaxSpeed? {
    val matchResult = implicitRegex.matchEntire(value) ?: return null
    val typeSpeed = matchResult.groupValues[2]
    val countryCode = matchResult.groupValues[1]
    val lit = when {
        tags["lit"] == null -> null
        tags["lit"] == "yes" -> true
        tags["lit"] == "no" -> false
        // null on unknown values
        else -> null
    }
    val roadType = RoadType.values().find { it.osmValue == typeSpeed } ?: RoadType.UNKNOWN

    return ImplicitMaxSpeed(countryCode, roadType, lit)
}

fun isZoneMaxspeed(value: String): Boolean {
    return zoneRegex.matchEntire(value) != null
}

/** Needs to know the country code because MaxSpeedZone contains a Speed, which requires units.
 *  (At the moment) there are no countries which have both maxspeed zones and a mixture of speed
 *  units (per country_metadata). */
fun getZoneMaxspeed(value: String, countryInfo: CountryInfo): MaxSpeedZone? {
    val matchResult = zoneRegex.matchEntire(value) ?: return null
    val zoneSpeed = matchResult.groupValues[2].toIntOrNull()
    val countryCode = matchResult.groupValues[1]
    val speedUnit = countryInfo.speedUnits.first()
    if (zoneSpeed != null) {
        val speed = when (speedUnit) {
            SpeedMeasurementUnit.MILES_PER_HOUR -> Mph(zoneSpeed)
            SpeedMeasurementUnit.KILOMETERS_PER_HOUR -> Kmh(zoneSpeed)
        }
        return MaxSpeedZone(speed, countryCode, "zone$zoneSpeed")
    }
    return null
}

fun isLivingStreetMaxspeed(value: String): Boolean {
    return livingStreetRegex.matchEntire(value) != null
}

fun isValidMaxspeedType(value: String?): Boolean {
    return if (value == null) false
    else isImplicitMaxspeed(value) || isZoneMaxspeed(value) || value == "sign"
}

fun maxspeedIsNone(value: String?): Boolean {
    return value == "none"
}

fun maxspeedIsWalk(value: String?): Boolean {
    return value == "walk"
}

fun isInMph(value: String): Boolean {
    return mphRegex.matchEntire(value) != null
}

fun getMaxspeedInMph(value: String?): Float? {
    if (value == null) return null
    return if (value.endsWith(" mph")) {
        return value.substring(0, value.length - 4).toFloatOrNull()
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
    return getMaxspeedInKmh(tags["maxspeed"])
}

fun getMaxspeedInKmh(value: String?): Float? {
    if (value == null) return null
    return if (value.endsWith(" mph")) {
        (getMaxspeedInMph(value)?.times(1.609344f))
    } else {
        value.toFloatOrNull()
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
