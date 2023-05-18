package de.westnordost.streetcomplete.osm.maxspeed

import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.meta.SpeedMeasurementUnit
import de.westnordost.streetcomplete.osm.maxspeed.RoadType.*
import de.westnordost.streetcomplete.quests.max_speed.Kmh
import de.westnordost.streetcomplete.quests.max_speed.Mph
import de.westnordost.streetcomplete.testutils.mock
import de.westnordost.streetcomplete.testutils.on
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
        assertEquals(invalidType, parseDE(
                "maxspeed" to "20",
                "zone:maxspeed" to "DE:30"
        ))
        assertEquals(invalidType, parseDE(
                "maxspeed" to "20",
                "source:maxspeed" to "DE:30"
        ))
        assertEquals(invalidType, parseDE(
                "maxspeed" to "20",
                "maxspeed:type" to "DE:zone30"
        ))
        assertEquals(invalidType, parseDE(
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

    @Test fun `invalid because country code is wrong`() {
        val invalid = MaxspeedAndType(null, Invalid)
        assertEquals(invalid, parseDE("maxspeed:type" to "xx:urban"))
    }

    /* ------------------------------------------ maxspeed -------------------------------------- */

    @Test fun `numerical maxspeed, unsigned`() {
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Kmh(20)), null),
            parseDE("maxspeed" to "20")
        )
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Kmh(100)), null),
            parseDE("maxspeed" to "100")
        )
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Mph(20)), null),
            parseDE("maxspeed" to "20 mph")
        )
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Mph(70)), null),
            parseGB("maxspeed" to "70 mph")
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
            MaxspeedAndType(null, ImplicitMaxSpeed("RU", URBAN, null)),
            parseRU("maxspeed" to "RU:urban")
        )
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("RU", RURAL, null)),
            parseRU("maxspeed" to "RU:rural")
        )
        assertEquals(
            MaxspeedAndType(
                MaxSpeedSign(Kmh(20)), MaxSpeedZone(Kmh(20), "DE", "zone20")),
            parseDE("maxspeed" to "DE:zone20")
        )
        assertEquals(
            MaxspeedAndType(
                MaxSpeedSign(Mph(20)), MaxSpeedZone(Mph(20), "GB", "zone20")),
            parseGB("maxspeed" to "GB:zone20")
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
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", URBAN, null)),
            parseDE("maxspeed:type" to "DE:urban")
        )
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", URBAN, null)),
            parseDE("source:maxspeed" to "DE:urban")
        )
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", URBAN, null)),
            parseDE("zone:maxspeed" to "DE:urban")
        )
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", URBAN, null)),
            parseDE("zone:traffic" to "DE:urban")
        )
    }

    @Test fun `implicit type with two-part country code`() {
        val countryInfoBEWAL: CountryInfo = mock()
        on(countryInfoBEWAL.countryCode).thenReturn("BE-WAL")

        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("BE-WAL", URBAN, null)),
            createMaxspeedAndType(mapOf("maxspeed:type" to "BE-WAL:urban"), countryInfoBEWAL)
        )

        val countryInfoCAAB: CountryInfo = mock()
        on(countryInfoCAAB.countryCode).thenReturn("CA-AB")
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("CA-AB", URBAN, null)),
            createMaxspeedAndType(mapOf("maxspeed:type" to "CA-AB:urban"), countryInfoCAAB)
        )
    }

    @Test fun `unknown road type`() {
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", UNKNOWN, null)),
            parseDE("maxspeed:type" to "DE:flubberway")
        )
    }

    @Test fun `recognise lit tag for implicit types`() {
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("GB", NSL_RESTRICTED, true)),
            parseGB(
                "maxspeed:type" to "GB:nsl_restricted",
                "lit" to "yes"
            )
        )
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("GB", NSL_SINGLE, false)),
            parseGB(
                "maxspeed:type" to "GB:nsl_single",
                "lit" to "no"
            )
        )
    }

    @Test fun `invalid value for lit is null`() {
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("GB", NSL_RESTRICTED, null)),
            parseGB(
                "maxspeed:type" to "GB:nsl_restricted",
                "lit" to "unknown"
            )
        )
    }

    @Test fun `maxspeed zone`() {
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Kmh(30)), MaxSpeedZone(Kmh(30), "DE", "zone30")),
            parseDE("maxspeed:type" to "DE:zone30")
        )
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Kmh(30)), MaxSpeedZone(Kmh(30), "DE", "zone30")),
            parseDE("source:maxspeed" to "DE:zone30")
        )
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Kmh(30)), MaxSpeedZone(Kmh(30), "DE", "zone30")),
            parseDE("zone:maxspeed" to "DE:30")
        )
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Kmh(30)), MaxSpeedZone(Kmh(30), "DE", "zone30")),
            parseDE("zone:traffic" to "DE:zone30")
        )
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Mph(20)), MaxSpeedZone(Mph(20), "GB", "zone20")),
            parseGB("maxspeed:type" to "GB:zone20")
        )
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Mph(20)), MaxSpeedZone(Mph(20), "GB", "zone20")),
            parseGB("source:maxspeed" to "GB:zone20")
        )
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Mph(20)), MaxSpeedZone(Mph(20), "GB", "zone20")),
            parseGB("zone:maxspeed" to "GB:20")
        )
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Mph(20)), MaxSpeedZone(Mph(20), "GB", "zone20")),
            parseGB("zone:traffic" to "GB:zone20")
        )
    }

    @Test fun `maxspeed type in maxspeed tag and type is 'sign'`() {
        assertEquals(
            MaxspeedAndType(MaxSpeedZone(Kmh(20), "RU", "zone20"), JustSign),
            parseRU(
                "source:maxspeed" to "sign",
                "maxspeed" to "RU:zone20"
            )
        )
        assertEquals(
            MaxspeedAndType(MaxSpeedZone(Mph(20), "GB", "zone20"), JustSign),
            parseGB(
                "source:maxspeed" to "sign",
                "maxspeed" to "GB:zone20"
            )
        )
    }

    @Test fun `duplicated type tag is valid`() {
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", URBAN, null)),
            parseDE(
                "source:maxspeed" to "DE:urban",
                "maxspeed:type" to "DE:urban"
            )
        )
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", URBAN, null)),
            parseDE(
                "source:maxspeed" to "DE:urban",
                "zone:traffic" to "DE:urban"
            )
        )
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", URBAN, null)),
            parseDE(
                "maxspeed:type" to "DE:urban",
                "zone:maxspeed" to "DE:urban"
            )
        )
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", URBAN, null)),
            parseDE(
                "source:maxspeed" to "DE:urban",
                "maxspeed:type" to "DE:urban",
                "zone:traffic" to "DE:urban"
            )
        )
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", URBAN, null)),
            parseDE(
                "source:maxspeed" to "DE:urban",
                "zone:maxspeed" to "DE:urban",
                "zone:traffic" to "DE:urban"
            )
        )
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Kmh(30)), MaxSpeedZone(Kmh(30), "DE", "zone30")),
            parseDE(
                "source:maxspeed" to "DE:zone30",
                "zone:maxspeed" to "DE:30"
            )
        )
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Kmh(30)), MaxSpeedZone(Kmh(30), "DE", "zone30")),
            parseDE(
                "maxspeed:type" to "DE:zone30",
                "zone:maxspeed" to "DE:30"
            )
        )
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Kmh(30)), MaxSpeedZone(Kmh(30), "DE", "zone30")),
            parseDE(
                "source:maxspeed" to "DE:zone30",
                "maxspeed:type" to "DE:zone30"
            )
        )
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Kmh(30)), MaxSpeedZone(Kmh(30), "DE", "zone30")),
            parseDE(
                "zone:traffic" to "DE:zone30",
                "zone:maxspeed" to "DE:30"
            )
        )
    }

    @Test fun `source_maxspeed unknown value and other valid type tag is valid`() {
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", URBAN, null)),
            parseDE(
                "source:maxspeed" to "survey",
                "maxspeed:type" to "DE:urban"
            )
        )
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", URBAN, null)),
            parseDE(
                "source:maxspeed" to "survey",
                "zone:maxspeed" to "DE:urban"
            )
        )
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", URBAN, null)),
            parseDE(
                "source:maxspeed" to "survey",
                "zone:traffic" to "DE:urban"
            )
        )
    }

    @Test fun `source_maxspeed unknown value and other duplicate valid type tags is valid`() {
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", URBAN, null)),
            parseDE(
                "source:maxspeed" to "survey",
                "maxspeed:type" to "DE:urban",
                "zone:maxspeed" to "DE:urban"
            )
        )
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", URBAN, null)),
            parseDE(
                "source:maxspeed" to "survey",
                "zone:maxspeed" to "DE:urban",
                "zone:traffic" to "DE:urban"
            )
        )
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", URBAN, null)),
            parseDE(
                "source:maxspeed" to "survey",
                "zone:traffic" to "DE:urban",
                "zone:maxspeed" to "DE:urban"
            )
        )
        assertEquals(
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", URBAN, null)),
            parseDE(
                "source:maxspeed" to "survey",
                "maxspeed:type" to "DE:urban",
                "zone:traffic" to "DE:urban",
                "zone:maxspeed" to "DE:urban"
            )
        )
    }

    /* -------------------------------------- living_street ------------------------------------- */

    @Test fun `living street type`() {
        assertEquals(
            MaxspeedAndType(null, LivingStreet(null)),
            parse("highway" to "living_street")
        )
        assertEquals(
            MaxspeedAndType(null, LivingStreet(null)),
            parse(
                "highway" to "residential",
                "living_street" to "yes"
            )
        )
        assertEquals(
            MaxspeedAndType(null, LivingStreet(null)),
            parse(
                "highway" to "residential",
                "living_street" to "yes",
                "source:maxspeed" to "survey"
            )
        )
        assertEquals(
            MaxspeedAndType(null, LivingStreet("DE")),
            parseDE(
                "highway" to "service",
                "maxspeed:type" to "DE:living_street"
            )
        )
    }

    @Test fun `not living street type if there is other valid maxspeed tagging`() {
        assertNotEquals(
            MaxspeedAndType(null, LivingStreet(null)),
            parseDE(
                "highway" to "living_street",
                "maxspeed:type" to "DE:zone20"
            )
        )
        assertNotEquals(
            MaxspeedAndType(null, LivingStreet(null)),
            parseDE(
                "highway" to "living_street",
                "source:maxspeed" to "DE:urban"
            )
        )
        assertNotEquals(
            MaxspeedAndType(null, LivingStreet(null)),
            parseDE(
                "highway" to "residential",
                "living_street" to "yes",
                "zone:maxspeed" to "DE:urban"
            )
        )
    }

    @Test fun `living street with explicit speed limit`() {
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Kmh(20)), LivingStreet(null)),
            parse(
                "highway" to "living_street",
                "maxspeed" to "20"
            )
        )
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Kmh(20)), LivingStreet(null)),
            parse(
                "highway" to "residential",
                "living_street" to "yes",
                "maxspeed" to "20"
            )
        )
        assertEquals(
            MaxspeedAndType(WalkMaxSpeed, LivingStreet("DE")),
            parseDE(
                "highway" to "living_street",
                "maxspeed:type" to "DE:living_street",
                "maxspeed" to "walk"
            )
        )
    }

    /* -------------------------------------- school zone --------------------------------------- */

    @Test fun `school zone type`() {
        assertEquals(
            MaxspeedAndType(null, IsSchoolZone),
            parse("hazard" to "school_zone")
        )
        assertEquals(
            MaxspeedAndType(null, IsSchoolZone),
            parse(
                "hazard" to "school_zone",
                "highway" to "residential",
                "source:maxspeed" to "survey"
            )
        )
        assertEquals(
            MaxspeedAndType(MaxSpeedSign(Kmh(20)), IsSchoolZone),
            parse(
                "hazard" to "school_zone",
                "maxspeed" to "20"
            )
        )
    }

    /* If there is a road that is both then if we displayed it as a living street then there would
     * be no way to change the tags to mark it as a school zone */
    @Test fun `school zone type even if also a living street`() {
        assertEquals(
            MaxspeedAndType(null, IsSchoolZone),
            parse(
                "hazard" to "school_zone",
                "highway" to "living_street"
            )
        )
        assertEquals(
            MaxspeedAndType(null, IsSchoolZone),
            parse(
                "hazard" to "school_zone",
                "highway" to "service",
                "living_street" to "yes"
            )
        )
    }

    @Test fun `not school zone if there is other valid maxspeed type tagging`() {
        assertNotEquals(
            MaxspeedAndType(null, IsSchoolZone),
            parseDE(
                "hazard" to "school_zone",
                "maxspeed:type" to "DE:zone20"
            )
        )
        assertNotEquals(
            MaxspeedAndType(null, IsSchoolZone),
            parseDE(
                "hazard" to "school_zone",
                "source:maxspeed" to "DE:urban"
            )
        )
        assertNotEquals(
            MaxspeedAndType(null, IsSchoolZone),
            parseDE(
                "hazard" to "school_zone",
                "zone:maxspeed" to "DE:urban"
            )
        )
    }
}

private fun parse(vararg tags: Pair<String, String>): MaxspeedAndType? {
    val countryInfo: CountryInfo = mock()
    return createMaxspeedAndType(mapOf(*tags), countryInfo)
}

private fun parseDE(vararg tags: Pair<String, String>): MaxspeedAndType? {
    val countryInfoDE: CountryInfo = mock()
    on(countryInfoDE.countryCode).thenReturn("DE")
    on(countryInfoDE.speedUnits).thenReturn(listOf(SpeedMeasurementUnit.KILOMETERS_PER_HOUR))
    return createMaxspeedAndType(mapOf(*tags), countryInfoDE)
}

private fun parseGB(vararg tags: Pair<String, String>): MaxspeedAndType? {
    val countryInfoGB: CountryInfo = mock()
    on(countryInfoGB.countryCode).thenReturn("GB")
    on(countryInfoGB.speedUnits).thenReturn(listOf(SpeedMeasurementUnit.MILES_PER_HOUR))
    return createMaxspeedAndType(mapOf(*tags), countryInfoGB)
}

private fun parseRU(vararg tags: Pair<String, String>): MaxspeedAndType? {
    val countryInfoRU: CountryInfo = mock()
    on(countryInfoRU.countryCode).thenReturn("RU")
    on(countryInfoRU.speedUnits).thenReturn(listOf(SpeedMeasurementUnit.KILOMETERS_PER_HOUR))
    return createMaxspeedAndType(mapOf(*tags), countryInfoRU)
}
