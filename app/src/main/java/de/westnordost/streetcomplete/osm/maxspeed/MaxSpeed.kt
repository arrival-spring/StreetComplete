package de.westnordost.streetcomplete.osm.maxspeed

import de.westnordost.streetcomplete.osm.lit.LitStatus

data class ForwardAndBackwardAllSpeedInformation(
    val forward: AllSpeedInformation?,
    val backward: AllSpeedInformation?,
    val wholeRoadType: MaxSpeedAnswer? = null, // e.g. living street or school zone that is not linked to a vehicle
    val lit: LitStatus? = null,
    val dualCarriageway: Boolean? = null
    )

data class AllSpeedInformation(
    val vehicles: Map<String?, Map<Condition, MaxspeedAndType?>?>?,
    val advisory: AdvisorySpeedSign?,
    val variable: Boolean?
    )

data class ForwardAndBackwardAdvisorySpeedSign(val forward: AdvisorySpeedSign?, val backward: AdvisorySpeedSign?)

data class ForwardAndBackwardVariableLimit(val forward: Boolean?, val backward: Boolean?)

data class ForwardAndBackwardConditionalMaxspeed(
    val forward: Map<Condition, MaxspeedAndType>?,
    val backward: Map<Condition, MaxspeedAndType>?
    )

data class ForwardAndBackwardMaxspeedAndType(val forward: MaxspeedAndType?, val backward: MaxspeedAndType?)

data class MaxspeedAndType(val explicit: MaxSpeedAnswer?, val type: MaxSpeedAnswer?)

sealed interface MaxSpeedAnswer

data class MaxSpeedSign(val value: Speed) : MaxSpeedAnswer
data class MaxSpeedZone(val value: Speed, val countryCode: String, val roadType: String) : MaxSpeedAnswer
data class AdvisorySpeedSign(val value: Speed) : MaxSpeedAnswer
data class ImplicitMaxSpeed(val countryCode: String, val roadType: RoadType) : MaxSpeedAnswer
// LivingStreet needs a country code in case it is used as a type
data class LivingStreet(val countryCode: String?) : MaxSpeedAnswer
// BicycleBoulevard needs a country code in case it is used as a type
data class BicycleBoulevardType(val countryCode: String?) : MaxSpeedAnswer
object IsSchoolZone : MaxSpeedAnswer
object WalkMaxSpeed : MaxSpeedAnswer
object MaxSpeedIsNone : MaxSpeedAnswer
object JustSign : MaxSpeedAnswer
object NoSign : MaxSpeedAnswer
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
