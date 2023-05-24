package de.westnordost.streetcomplete.osm.weight

fun createWeight(weight: String?): Weight? {
    if (weight == null) return null
    weightRegex.matchEntire(weight) ?: return null
    return when {
        weight.endsWith("kg") -> Kilograms(weight.removeSuffix("kg").trim().toInt())
        weight.endsWith("lbs") -> ImperialPounds(weight.removeSuffix("lbs").trim().toInt())
        weight.endsWith("st") -> ShortTons(weight.removeSuffix("st").trim().toDouble())
        weight.endsWith("t") -> MetricTons(weight.removeSuffix("t").trim().toDouble())
        else -> MetricTons(weight.trim().toDouble())
    }
}

private val weightRegex = Regex("\\d+\\.?\\d+ ?(?:t|st|lbs|kg)?")
