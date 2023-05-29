package de.westnordost.streetcomplete.osm.maxspeed

import de.westnordost.streetcomplete.osm.opening_hours.parser.OpeningHoursRuleList
import de.westnordost.streetcomplete.osm.weight.Weight

sealed interface Condition

object Wet : Condition
object Snow : Condition
object Flashing : Condition
object Winter : Condition
data class WeightAndComparison(val weight: Weight, val comparison: Inequality) : Condition
data class TimeCondition(val times: OpeningHoursRuleList) : Condition
object NoCondition : Condition

fun Condition.toOsmValue(): String {
    return when (this) {
        Wet -> "wet"
        Snow -> "snow"
        Flashing -> "flashing"
        Winter -> "winter"
        is WeightAndComparison -> "weight${comparison.osmValue}$weight"
        is TimeCondition -> times.toString()
        NoCondition -> throw IllegalStateException()
    }
}

fun Condition.needsBrackets(): Boolean {
    if (this is NoCondition) return  false
    return this.toOsmValue().contains(";")
}

enum class Inequality(val osmValue: String?) {
    LESS_THAN("<"),
    MORE_THAN(">"),
    LESS_THAN_OR_EQUAL("<="),
    MORE_THAN_OR_EQUAL(">=")
}
