package de.westnordost.streetcomplete.osm.maxspeed

import de.westnordost.streetcomplete.osm.opening_hours.parser.OpeningHoursRuleList
import de.westnordost.streetcomplete.quests.max_speed.Speed

data class ConditionalForwardAndBackwardMaxspeed(
    val forward: Map<Condition, MaxspeedAndType?>?,
    val backward: Map<Condition, MaxspeedAndType?>?
    )

sealed interface Condition

object Wet : Condition
object Snow : Condition
object Flashing : Condition
data class Weight(val weight: Float, val comparison: Inequality) : Condition
data class TimeCondition(val times: OpeningHoursRuleList) : Condition
object NoCondition : Condition

fun Condition.toOsmValue(): String {
    return when (this) {
        Wet -> "wet"
        Snow -> "snow"
        Flashing -> "flashing"
        is Weight -> "weight${comparison.osmValue}$weight"
        is TimeCondition -> times.toString()
        NoCondition -> throw IllegalStateException()
    }
}

data class ForwardAndBackwardMaxspeedAndType(val forward: MaxspeedAndType?, val backward: MaxspeedAndType?)

data class MaxspeedAndType(val explicit: MaxSpeedAnswer?, val type: MaxSpeedAnswer?)

// TODO: this
// fun MaxspeedAndType.isValidInBothDirections(): Boolean =
//     type in listOf(MaxSpeedSign, AdvisorySpeedSign, ImplicitMaxSpeed, WalkMaxSpeed)
// to not include living street, school zone...

sealed interface MaxSpeedAnswer

data class MaxSpeedSign(val value: Speed) : MaxSpeedAnswer
data class MaxSpeedZone(val value: Speed, val countryCode: String, val roadType: String) :
    MaxSpeedAnswer
data class AdvisorySpeedSign(val value: Speed) : MaxSpeedAnswer
data class ImplicitMaxSpeed(val countryCode: String, val roadType: RoadType, val lit: Boolean?) : MaxSpeedAnswer
// LivingStreet needs a country code in case it is used as a type
data class LivingStreet(val countryCode: String?) : MaxSpeedAnswer
object IsSchoolZone : MaxSpeedAnswer
object WalkMaxSpeed : MaxSpeedAnswer
object MaxSpeedIsNone : MaxSpeedAnswer
object JustSign : MaxSpeedAnswer
object Invalid : MaxSpeedAnswer

fun MaxSpeedAnswer.toSpeedOsmValue(): String? {
    return when (this) {
        is MaxSpeedSign -> this.value.toString()
        is MaxSpeedZone -> this.value.toString()
        is MaxSpeedIsNone -> "none"
        is WalkMaxSpeed -> "walk"
        else -> null
    }
}

fun MaxSpeedAnswer.toTypeOsmValue(): String? {
    return when (this) {
        is MaxSpeedSign -> "sign"
        is JustSign -> "sign"
        is MaxSpeedZone -> this.countryCode + ":" + this.roadType
        is ImplicitMaxSpeed -> this.countryCode + ":" + this.roadType.osmValue
        is LivingStreet -> this.countryCode + ":" + "living_street"
        else -> null
    }
}

/** "zone:maxspeed" should have e.g. "DE:30" instead of "DE:zone30" but still has e.g. "DE:urban" */
fun MaxSpeedAnswer.toTypeOsmValueZoneMaxspeed(): String? {
    return when (this) {
        is MaxSpeedZone -> this.countryCode + ":" + this.value.toValue()
        else -> toTypeOsmValue()
    }
}

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

enum class Inequality(val osmValue: String?) {
    LESS_THAN("<"),
    MORE_THAN(">"),
    LESS_THAN_OR_EQUAL("<="),
    MORE_THAN_OR_EQUAL(">=")
}
