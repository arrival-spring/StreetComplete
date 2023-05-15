package de.westnordost.streetcomplete.osm.maxspeed

import de.westnordost.streetcomplete.quests.max_speed.Speed

sealed interface MaxSpeedAnswer

data class MaxSpeedSign(val value: Speed) : MaxSpeedAnswer
data class MaxSpeedZone(val value: Speed, val countryCode: String, val roadType: String) :
    MaxSpeedAnswer
data class AdvisorySpeedSign(val value: Speed) : MaxSpeedAnswer
data class ImplicitMaxSpeed(val countryCode: String, val roadType: String, val lit: Boolean?) :
    MaxSpeedAnswer
object IsLivingStreet : MaxSpeedAnswer
object IsSchoolZone : MaxSpeedAnswer
object WalkMaxSpeed : MaxSpeedAnswer
object MaxSpeedIsNone : MaxSpeedAnswer
object JustSign : MaxSpeedAnswer
object Invalid : MaxSpeedAnswer

data class MaxspeedAndType(val explicit: MaxSpeedAnswer?, val type: MaxSpeedAnswer?)

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
        is ImplicitMaxSpeed -> this.countryCode + ":" + this.roadType
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
