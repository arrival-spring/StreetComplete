package de.westnordost.streetcomplete.osm.weight

import org.junit.Assert.*
import org.junit.Test

class WeightParserKtTest {
    @Test fun `invalid weight returns null`() {
        assertEquals(
            createWeight("fixme"),
            null
        )
        assertEquals(
            createWeight("unknown"),
            null
        )
        assertEquals(
            createWeight("30 x"),
            null
        )
        assertEquals(
            createWeight("7,5"),
            null
        )
    }

    @Test fun `parse weight without units`() {
        assertEquals(
            createWeight("5"),
            MetricTons(5.0)
        )
        assertEquals(
            createWeight("30"),
            MetricTons(30.0)
        )
        assertEquals(
            createWeight("7.5"),
            MetricTons(7.5)
        )
        assertEquals(
            createWeight("0.35"),
            MetricTons(0.35)
        )
    }

    @Test fun `parse weight in metric tons with unit`() {
        assertEquals(
            createWeight("30 t"),
            MetricTons(30.0)
        )
        assertEquals(
            createWeight("7.5 t"),
            MetricTons(7.5)
        )
        assertEquals(
            createWeight("30t"),
            MetricTons(30.0)
        )
        assertEquals(
            createWeight("7.5t"),
            MetricTons(7.5)
        )
    }

    @Test fun `parse weight in short tons`() {
        assertEquals(
            createWeight("30 st"),
            ShortTons(30.0)
        )
        assertEquals(
            createWeight("7.5 st"),
            ShortTons(7.5)
        )
        assertEquals(
            createWeight("30st"),
            ShortTons(30.0)
        )
        assertEquals(
            createWeight("7.5st"),
            ShortTons(7.5)
        )
    }

    @Test fun `parse weight in imperial pounds`() {
        assertEquals(
            createWeight("3000 lbs"),
            ImperialPounds(3000)
        )
        assertEquals(
            createWeight("3000lbs"),
            ImperialPounds(3000)
        )
    }

    @Test fun `parse weight in kilograms`() {
        assertEquals(
            createWeight("150 kg"),
            Kilograms(150)
        )
        assertEquals(
            createWeight("150kg"),
            Kilograms(150)
        )
    }
}
