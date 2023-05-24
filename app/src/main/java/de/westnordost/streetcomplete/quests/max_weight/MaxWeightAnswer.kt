package de.westnordost.streetcomplete.quests.max_weight

import de.westnordost.streetcomplete.osm.weight.Weight

sealed interface MaxWeightAnswer

data class MaxWeight(val sign: MaxWeightSign, val weight: Weight) : MaxWeightAnswer
object NoMaxWeightSign : MaxWeightAnswer
