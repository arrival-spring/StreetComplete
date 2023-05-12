package de.westnordost.streetcomplete.osm.maxspeed

import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.meta.CountryInfos
import de.westnordost.streetcomplete.data.meta.SpeedMeasurementUnit
import de.westnordost.streetcomplete.quests.max_speed.Kmh
import de.westnordost.streetcomplete.quests.max_speed.Mph
import de.westnordost.streetcomplete.testutils.mock
import de.westnordost.streetcomplete.testutils.on
import org.junit.Assert.assertEquals
import org.junit.Test

class MaxspeedParserKtTest {
    /* These are a lot of tests because maxspeed type tagging is a mess and so there are many
    *  possible permutations.
    *  For these tests, RU and DE are hard-coded to be countries in km/h and GB is hard-coded
    *  to be in mph. In real use, this is pulled from the country_info */

    @Test fun `null with no tags or unrelated tags`() {
        assertEquals(null, parse())
        assertEquals(null, parse("unrelated" to "something"))
    }

    /* ------------------------------------------ invalid --------------------------------------- */

    @Test fun `invalid speed because of unknown units`() {
        val invalidSpeed = MaxspeedAndType(Invalid, null)
        assertEquals(invalidSpeed, parse("maxspeed" to "1 knot"))
    }

    @Test fun `invalid speed`() {
        val invalidSpeed = MaxspeedAndType(Invalid, null)
        assertEquals(invalidSpeed, parse("maxspeed" to "fixme"))
        assertEquals(invalidSpeed, parse("maxspeed" to "sign"))
    }

    @Test fun `invalid type due to unknown value`() {
        val invalidType = MaxspeedAndType(null, Invalid)
        assertEquals(invalidType, parse("maxspeed:type" to "fixme"))
    }

    @Test fun `invalid type due to maxspeed value in type`() {
        val invalidType = MaxspeedAndType(null, Invalid)
        assertEquals(invalidType, parse("maxspeed:type" to "walk"))
        assertEquals(invalidType, parse("maxspeed:type" to "none"))
    }

    @Test fun `invalid type because of contradicting types`() {
        val invalidType = MaxspeedAndType(null, Invalid)
        assertEquals(invalidType, parse(
            "source:maxspeed" to "sign",
            "maxspeed:type" to "DE:zone30"
        ))
        assertEquals(invalidType, parse(
            "zone:maxspeed" to "DE:30",
            "maxspeed:type" to "sign"
        ))
        assertEquals(invalidType, parse(
            "source:maxspeed" to "sign",
            "zone:traffic" to "DE:urban"
        ))
        assertEquals(invalidType, parse(
            "source:maxspeed" to "sign",
            "maxspeed:type" to "sign",
            "zone:traffic" to "DE:urban"
        ))
        assertEquals(invalidType, parse(
            "source:maxspeed" to "sign",
            "maxspeed:type" to "sign",
            "zone:maxspeed" to "DE:30"
        ))
        assertEquals(invalidType, parse(
            "source:maxspeed" to "survey",
            "maxspeed:type" to "sign",
            "zone:maxspeed" to "DE:30"
        ))
    }

    @Test fun `invalid because of contradicting zone and speed`() {
        val invalidType = MaxspeedAndType(Invalid, Invalid)
        assertEquals(invalidType, parse(
                "maxspeed" to "20",
                "zone:maxspeed" to "DE:30"
        ))
        assertEquals(invalidType, parse(
                "maxspeed" to "20",
                "source:maxspeed" to "DE:30"
        ))
        assertEquals(invalidType, parse(
                "maxspeed" to "20",
                "maxspeed:type" to "DE:zone30"
        ))
        assertEquals(invalidType, parse(
                "maxspeed" to "20",
                "zone:maxspeed" to "DE:zone30"
        ))
    }

    @Test fun `invalid because type is sign but no value given`() {
        val invalidType = MaxspeedAndType(null, Invalid)
        assertEquals(invalidType, parse("maxspeed:type" to "sign"))
        assertEquals(invalidType, parse("source:maxspeed" to "sign"))
        assertEquals(invalidType, parse(
            "source:maxspeed" to "sign",
            "maxspeed:type" to "sign"
        ))
        assertEquals(invalidType, parse(
            "source:maxspeed" to "survey",
            "maxspeed:type" to "sign"
        ))
    }

    @Test fun `invalid because type is sign and implicit value in maxspeed tag`() {
        val invalid = MaxspeedAndType(Invalid, Invalid)
        assertEquals(invalid, parse(
            "source:maxspeed" to "sign",
            "maxspeed" to "RU:urban"
        ))
        assertEquals(invalid, parse(
            "source:maxspeed" to "sign",
            "maxspeed" to "DE:rural"
        ))
    }

    /* ------------------------------------------ maxspeed -------------------------------------- */

    @Test fun `numerical maxspeed, unsigned`() {
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Kmh(20)), null),
            parse("maxspeed" to "20")
        )
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Kmh(100)), null),
            parse("maxspeed" to "100")
        )
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Mph(20)), null),
            parse("maxspeed" to "20 mph")
        )
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Mph(70)), null),
            parse("maxspeed" to "70 mph")
        )
    }

    @Test fun `numerical maxspeed, signed`() {
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Kmh(20)), JustSign),
            parse("maxspeed" to "20", "maxspeed:type" to "sign")
        )
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Kmh(100)), JustSign),
            parse("maxspeed" to "100", "source:maxspeed" to "sign")
        )
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Mph(20)), JustSign),
            parse("maxspeed" to "20 mph", "source:maxspeed" to "sign")
        )
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Mph(70)), JustSign),
            parse("maxspeed" to "70 mph", "maxspeed:type" to "sign")
        )
    }

    @Test fun `common non-numerical maxspeed`() {
        assertEquals(
            MaxspeedAndType(MaxSpeedIsNone, null),
            parse("maxspeed" to "none")
        )
        assertEquals(
            MaxspeedAndType(WalkMaxSpeed, null),
            parse("maxspeed" to "walk")
        )
    }

    @Test fun `maxspeed type in maxspeed tag`() {
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("RU", "urban", null)),
            parse("maxspeed" to "RU:urban")
        )
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("RU", "rural", null)),
            parse("maxspeed" to "RU:rural")
        )
        assertEquals(
            MaxspeedAndType(
                MaxSpeedSign(Kmh(20)), MaxSpeedZone(Kmh(20), "DE", "zone20")),
            parse("maxspeed" to "DE:zone20")
        )
        assertEquals(
            MaxspeedAndType(
                MaxSpeedSign(Mph(20)), MaxSpeedZone(Mph(20), "GB", "zone20")),
            parse("maxspeed" to "GB:zone20")
        )
    }

    @Test fun `signed and valid value in maxspeed tag`() {
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Mph(30)), JustSign),
            parse(
                "maxspeed:type" to "sign",
                "maxspeed" to "30 mph"
            ))
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Mph(30)), JustSign),
            parse(
                "source:maxspeed" to "sign",
                "maxspeed" to "30 mph"
            ))
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Kmh(60)), JustSign),
            parse(
                "maxspeed:type" to "sign",
                "maxspeed" to "60"
            ))
        assertEquals(
            MaxspeedAndType(WalkMaxSpeed, JustSign),
            parse(
                "maxspeed:type" to "sign",
                "maxspeed" to "walk"
            ))
        assertEquals(
            MaxspeedAndType(MaxSpeedIsNone, JustSign),
            parse(
                "maxspeed:type" to "sign",
                "maxspeed" to "none"
            ))
    }

    /* ------------------------------------------ types ----------------------------------------- */

    @Test fun `standard implicit maxspeed`() {
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", "urban", null)),
            parse("maxspeed:type" to "DE:urban")
        )
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", "urban", null)),
            parse("source:maxspeed" to "DE:urban")
        )
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", "urban", null)),
            parse("zone:maxspeed" to "DE:urban")
        )
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", "urban", null)),
            parse("zone:traffic" to "DE:urban")
        )
    }

    @Test fun `maxspeed zone`() {
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Kmh(30)), MaxSpeedZone(Kmh(30), "DE", "zone30")),
            parse("maxspeed:type" to "DE:zone30")
        )
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Kmh(30)), MaxSpeedZone(Kmh(30), "DE", "zone30")),
            parse("source:maxspeed" to "DE:zone30")
        )
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Kmh(30)), MaxSpeedZone(Kmh(30), "DE", "zone30")),
            parse("zone:maxspeed" to "DE:30")
        )
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Kmh(30)), MaxSpeedZone(Kmh(30), "DE", "zone30")),
            parse("zone:traffic" to "DE:zone30")
        )
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Mph(20)), MaxSpeedZone(Mph(20), "GB", "zone20")),
            parse("maxspeed:type" to "GB:zone20")
        )
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Mph(20)), MaxSpeedZone(Mph(20), "GB", "zone20")),
            parse("source:maxspeed" to "GB:zone20")
        )
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Mph(20)), MaxSpeedZone(Mph(20), "GB", "zone20")),
            parse("zone:maxspeed" to "GB:20")
        )
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Mph(20)), MaxSpeedZone(Mph(20), "GB", "zone20")),
            parse("zone:traffic" to "GB:zone20")
        )
    }

    @Test fun `maxspeed type in maxspeed tag and type is 'sign'`() {
        assertEquals(
            MaxspeedAndType(MaxSpeedZone(Kmh(20), "RU", "zone20"), JustSign),
            parse(
            "source:maxspeed" to "sign",
            "maxspeed" to "RU:zone20"
        ))
        assertEquals(
            MaxspeedAndType(MaxSpeedZone(Mph(20), "GB", "zone20"), JustSign),
            parse(
                "source:maxspeed" to "sign",
                "maxspeed" to "GB:zone20"
            ))
    }

    @Test fun `duplicated type tag is valid`() {
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", "urban", null)),
            parse(
                "source:maxspeed" to "DE:urban",
                "maxspeed:type" to "DE:urban"
            )
        )
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", "urban", null)),
            parse(
                "source:maxspeed" to "DE:urban",
                "zone:traffic" to "DE:urban"
            )
        )
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", "urban", null)),
            parse(
                "maxspeed:type" to "DE:urban",
                "zone:maxspeed" to "DE:urban"
            )
        )
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", "urban", null)),
            parse(
                "source:maxspeed" to "DE:urban",
                "maxspeed:type" to "DE:urban",
                "zone:traffic" to "DE:urban"
            )
        )
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", "urban", null)),
            parse(
                "source:maxspeed" to "DE:urban",
                "zone:maxspeed" to "DE:urban",
                "zone:traffic" to "DE:urban"
            )
        )
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Kmh(30)), MaxSpeedZone(Kmh(30), "DE", "zone30")),
            parse(
                "source:maxspeed" to "DE:zone30",
                "zone:maxspeed" to "DE:30"
            )
        )
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Kmh(30)), MaxSpeedZone(Kmh(30), "DE", "zone30")),
            parse(
                "maxspeed:type" to "DE:zone30",
                "zone:maxspeed" to "DE:30"
            )
        )
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Kmh(30)), MaxSpeedZone(Kmh(30), "DE", "zone30")),
            parse(
                "source:maxspeed" to "DE:zone30",
                "maxspeed:type" to "DE:zone30"
            )
        )
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Kmh(30)), MaxSpeedZone(Kmh(30), "DE", "zone30")),
            parse(
                "zone:traffic" to "DE:zone30",
                "zone:maxspeed" to "DE:30"
            )
        )
    }

    @Test fun `source_maxspeed unknown value and other valid type tag`() {
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", "urban", null)),
            parse(
                "source:maxspeed" to "survey",
                "maxspeed:type" to "DE:urban"
            )
        )
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", "urban", null)),
            parse(
                "source:maxspeed" to "survey",
                "zone:maxspeed" to "DE:urban"
            )
        )
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", "urban", null)),
            parse(
                "source:maxspeed" to "survey",
                "zone:traffic" to "DE:urban"
            )
        )
    }

    @Test fun `source_maxspeed unknown value and other duplicate valid type tags`() {
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", "urban", null)),
            parse(
                "source:maxspeed" to "survey",
                "maxspeed:type" to "DE:urban",
                "zone:maxspeed" to "DE:urban"
            )
        )
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", "urban", null)),
            parse(
                "source:maxspeed" to "survey",
                "zone:maxspeed" to "DE:urban",
                "zone:traffic" to "DE:urban"
            )
        )
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", "urban", null)),
            parse(
                "source:maxspeed" to "survey",
                "zone:traffic" to "DE:urban",
                "zone:maxspeed" to "DE:urban"
            )
        )
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", "urban", null)),
            parse(
                "source:maxspeed" to "survey",
                "maxspeed:type" to "DE:urban",
                "zone:traffic" to "DE:urban",
                "zone:maxspeed" to "DE:urban"
            )
        )
    }
}

private fun parse(vararg tags: Pair<String, String>): MaxspeedAndType? {
    val countryInfos: CountryInfos = mock()
    val countryInfoGB: CountryInfo = mock()
    on(countryInfoGB.speedUnits).thenReturn(listOf(SpeedMeasurementUnit.MILES_PER_HOUR))
    val countryInfoDE: CountryInfo = mock()
    on(countryInfoDE.speedUnits).thenReturn(listOf(SpeedMeasurementUnit.KILOMETERS_PER_HOUR))
    val countryInfoRU: CountryInfo = mock()
    on(countryInfoRU.speedUnits).thenReturn(listOf(SpeedMeasurementUnit.KILOMETERS_PER_HOUR))
    on(countryInfos.get(listOf("RU"))).thenReturn(countryInfoRU)
    on(countryInfos.get(listOf("DE"))).thenReturn(countryInfoDE)
    on(countryInfos.get(listOf("GB"))).thenReturn(countryInfoGB)
    return createMaxspeedAndType(mapOf(*tags), countryInfos)
}
