package de.westnordost.streetcomplete.osm.maxspeed

enum class RoadType(val osmValue: String?) {
    RURAL("rural"),
    URBAN("urban"),
    MOTORWAY("motorway"),
    TRUNK("trunk"),
    LIVING_STREET("living_street"),
    BICYCLE_ROAD("bicycle_road"),
    NSL_SINGLE("nsl_single"),
    NSL_DUAL("nsl_dual"),
    NSL_RESTRICTED("nsl_restricted"),
    UNKNOWN(null)
}
