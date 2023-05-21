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

    /* ------------------------------------------------------------------------------------------ */
    /* ------------------------------ same in both directions ----------------------------------- */
    /* ------------------------------------------------------------------------------------------ */

    /* ------------------------------------------ invalid --------------------------------------- */

    @Test fun `invalid speed because of unknown units`() {
        val invalidSpeed = maxspeedBothDirections(Invalid, null)
        assertEquals(invalidSpeed, parse("maxspeed" to "1 knot"))
    }

    @Test fun `invalid speed`() {
        val invalidSpeed = maxspeedBothDirections(Invalid, null)
        assertEquals(invalidSpeed, parse("maxspeed" to "fixme"))
        assertEquals(invalidSpeed, parse("maxspeed" to "sign"))
    }

    @Test fun `invalid type due to unknown value`() {
        val invalidType = maxspeedBothDirections(null, Invalid)
        assertEquals(invalidType, parse("maxspeed:type" to "fixme"))
    }

    @Test fun `invalid type due to maxspeed value in type`() {
        val invalidType = maxspeedBothDirections(null, Invalid)
        assertEquals(invalidType, parse("maxspeed:type" to "walk"))
        assertEquals(invalidType, parse("maxspeed:type" to "none"))
    }

    @Test fun `invalid type because of contradicting types`() {
        val invalidType = maxspeedBothDirections(null, Invalid)
        assertEquals(invalidType, parseDE(
            "source:maxspeed" to "sign",
            "maxspeed:type" to "DE:zone30"
        ))
        assertEquals(invalidType, parseDE(
            "zone:maxspeed" to "DE:30",
            "maxspeed:type" to "sign"
        ))
        assertEquals(invalidType, parseDE(
            "source:maxspeed" to "sign",
            "zone:traffic" to "DE:urban"
        ))
        assertEquals(invalidType, parseDE(
            "source:maxspeed" to "sign",
            "maxspeed:type" to "sign",
            "zone:traffic" to "DE:urban"
        ))
        assertEquals(invalidType, parseDE(
            "source:maxspeed" to "sign",
            "maxspeed:type" to "sign",
            "zone:maxspeed" to "DE:30"
        ))
        assertEquals(invalidType, parseDE(
            "source:maxspeed" to "survey",
            "maxspeed:type" to "sign",
            "zone:maxspeed" to "DE:30"
        ))
    }

    @Test fun `invalid because of contradicting zone and speed`() {
        val invalidType = maxspeedBothDirections(Invalid, Invalid)
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
        val invalidType = maxspeedBothDirections(null, Invalid)
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
        val invalid = maxspeedBothDirections(Invalid, Invalid)
        assertEquals(invalid, parseRU(
            "source:maxspeed" to "sign",
            "maxspeed" to "RU:urban"
        ))
        assertEquals(invalid, parseDE(
            "source:maxspeed" to "sign",
            "maxspeed" to "DE:rural"
        ))
    }

    @Test fun `invalid because country code is wrong`() {
        val invalid = maxspeedBothDirections(null, Invalid)
        assertEquals(invalid, parseDE("maxspeed:type" to "xx:urban"))
    }

    @Test fun `invalid because of overtagging`() {
        val invalid = maxspeedBothDirections(Invalid, Invalid)
        assertEquals(
            invalid,
            parse(
                "maxspeed" to "30",
                "maxspeed:forward" to "20",
                "maxspeed:backward" to "30"
            )
        )
        assertEquals(
            invalid,
            parse(
                "maxspeed" to "30 mph",
                "maxspeed:forward" to "20 mph",
                "maxspeed:backward" to "30 mph"
            )
        )
        assertEquals(
            invalid,
            parseDE(
                "maxspeed:type" to "DE:urban",
                "maxspeed:type:forward" to "DE:urban",
                "maxspeed:type:backward" to "DE:rural"
            )
        )
        assertEquals(
            invalid,
            parseDE(
                "source:maxspeed" to "DE:urban",
                "source:maxspeed:forward" to "DE:urban",
                "source:maxspeed:backward" to "DE:rural"
            )
        )
        assertEquals(
            invalid,
            parseDE(
                "zone:maxspeed" to "DE:urban",
                "maxspeed:type:forward" to "DE:urban",
                "maxspeed:type:backward" to "DE:rural"
            )
        )
        assertEquals(
            invalid,
            parseDE(
                "maxspeed" to "DE:urban",
                "maxspeed:type:forward" to "DE:urban",
                "maxspeed:type:backward" to "sign"
            )
        )
    }

    /* ------------------------------------------ maxspeed -------------------------------------- */

    @Test fun `numerical maxspeed, unsigned`() {
        assertEquals(
            maxspeedBothDirections(MaxSpeedSign(Kmh(20)), null),
            parseDE("maxspeed" to "20")
        )
        assertEquals(
            maxspeedBothDirections(MaxSpeedSign(Kmh(100)), null),
            parseDE("maxspeed" to "100")
        )
        assertEquals(
            maxspeedBothDirections(MaxSpeedSign(Mph(20)), null),
            parseDE("maxspeed" to "20 mph")
        )
        assertEquals(
            maxspeedBothDirections(MaxSpeedSign(Mph(70)), null),
            parseGB("maxspeed" to "70 mph")
        )
    }

    @Test fun `numerical maxspeed, signed`() {
        assertEquals(
            maxspeedBothDirections(MaxSpeedSign(Kmh(20)), JustSign),
            parse("maxspeed" to "20", "maxspeed:type" to "sign")
        )
        assertEquals(
            maxspeedBothDirections(MaxSpeedSign(Kmh(100)), JustSign),
            parse("maxspeed" to "100", "source:maxspeed" to "sign")
        )
        assertEquals(
            maxspeedBothDirections(MaxSpeedSign(Mph(20)), JustSign),
            parse("maxspeed" to "20 mph", "source:maxspeed" to "sign")
        )
        assertEquals(
            maxspeedBothDirections(MaxSpeedSign(Mph(70)), JustSign),
            parse("maxspeed" to "70 mph", "maxspeed:type" to "sign")
        )
    }

    @Test fun `common non-numerical maxspeed`() {
        assertEquals(
            maxspeedBothDirections(MaxSpeedIsNone, null),
            parse("maxspeed" to "none")
        )
        assertEquals(
            maxspeedBothDirections(WalkMaxSpeed, null),
            parse("maxspeed" to "walk")
        )
    }

    @Test fun `maxspeed type in maxspeed tag`() {
        assertEquals(
            maxspeedBothDirections(null, ImplicitMaxSpeed("RU", URBAN, null)),
            parseRU("maxspeed" to "RU:urban")
        )
        assertEquals(
            maxspeedBothDirections(null, ImplicitMaxSpeed("RU", RURAL, null)),
            parseRU("maxspeed" to "RU:rural")
        )
        assertEquals(
            maxspeedBothDirections(
                MaxSpeedSign(Kmh(20)), MaxSpeedZone(Kmh(20), "DE", "zone20")),
            parseDE("maxspeed" to "DE:zone20")
        )
        assertEquals(
            maxspeedBothDirections(
                MaxSpeedSign(Mph(20)), MaxSpeedZone(Mph(20), "GB", "zone20")),
            parseGB("maxspeed" to "GB:zone20")
        )
    }

    @Test fun `signed and valid value in maxspeed tag`() {
        assertEquals(
            maxspeedBothDirections(MaxSpeedSign(Mph(30)), JustSign),
            parse(
                "maxspeed:type" to "sign",
                "maxspeed" to "30 mph"
            ))
        assertEquals(
            maxspeedBothDirections(MaxSpeedSign(Mph(30)), JustSign),
            parse(
                "source:maxspeed" to "sign",
                "maxspeed" to "30 mph"
            ))
        assertEquals(
            maxspeedBothDirections(MaxSpeedSign(Kmh(60)), JustSign),
            parse(
                "maxspeed:type" to "sign",
                "maxspeed" to "60"
            ))
        assertEquals(
            maxspeedBothDirections(WalkMaxSpeed, JustSign),
            parse(
                "maxspeed:type" to "sign",
                "maxspeed" to "walk"
            ))
        assertEquals(
            maxspeedBothDirections(MaxSpeedIsNone, JustSign),
            parse(
                "maxspeed:type" to "sign",
                "maxspeed" to "none"
            ))
    }

    /* ------------------------------------------ types ----------------------------------------- */

    @Test fun `standard implicit maxspeed`() {
        assertEquals(
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            parseDE("maxspeed:type" to "DE:urban")
        )
        assertEquals(
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            parseDE("source:maxspeed" to "DE:urban")
        )
        assertEquals(
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            parseDE("zone:maxspeed" to "DE:urban")
        )
        assertEquals(
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            parseDE("zone:traffic" to "DE:urban")
        )
    }

    @Test fun `implicit type with two-part country code`() {
        val countryInfoBEWAL: CountryInfo = mock()
        on(countryInfoBEWAL.countryCode).thenReturn("BE-WAL")

        assertEquals(
            maxspeedBothDirections(null, ImplicitMaxSpeed("BE-WAL", URBAN, null)),
            createForwardAndBackwardMaxspeedAndType(mapOf("maxspeed:type" to "BE-WAL:urban"), countryInfoBEWAL)
        )

        val countryInfoCAAB: CountryInfo = mock()
        on(countryInfoCAAB.countryCode).thenReturn("CA-AB")
        assertEquals(
            maxspeedBothDirections(null, ImplicitMaxSpeed("CA-AB", URBAN, null)),
            createForwardAndBackwardMaxspeedAndType(mapOf("maxspeed:type" to "CA-AB:urban"), countryInfoCAAB)
        )
    }

    @Test fun `unknown road type`() {
        assertEquals(
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", UNKNOWN, null)),
            parseDE("maxspeed:type" to "DE:flubberway")
        )
    }

    @Test fun `recognise lit tag for implicit types`() {
        assertEquals(
            maxspeedBothDirections(null, ImplicitMaxSpeed("GB", NSL_RESTRICTED, true)),
            parseGB(
                "maxspeed:type" to "GB:nsl_restricted",
                "lit" to "yes"
            )
        )
        assertEquals(
            maxspeedBothDirections(null, ImplicitMaxSpeed("GB", NSL_SINGLE, false)),
            parseGB(
                "maxspeed:type" to "GB:nsl_single",
                "lit" to "no"
            )
        )
    }

    @Test fun `invalid value for lit is null`() {
        assertEquals(
            maxspeedBothDirections(null, ImplicitMaxSpeed("GB", NSL_RESTRICTED, null)),
            parseGB(
                "maxspeed:type" to "GB:nsl_restricted",
                "lit" to "unknown"
            )
        )
    }

    @Test fun `maxspeed zone`() {
        assertEquals(
            maxspeedBothDirections(MaxSpeedSign(Kmh(30)), MaxSpeedZone(Kmh(30), "DE", "zone30")),
            parseDE("maxspeed:type" to "DE:zone30")
        )
        assertEquals(
            maxspeedBothDirections(MaxSpeedSign(Kmh(30)), MaxSpeedZone(Kmh(30), "DE", "zone30")),
            parseDE("source:maxspeed" to "DE:zone30")
        )
        assertEquals(
            maxspeedBothDirections(MaxSpeedSign(Kmh(30)), MaxSpeedZone(Kmh(30), "DE", "zone30")),
            parseDE("zone:maxspeed" to "DE:30")
        )
        assertEquals(
            maxspeedBothDirections(MaxSpeedSign(Kmh(30)), MaxSpeedZone(Kmh(30), "DE", "zone30")),
            parseDE("zone:traffic" to "DE:zone30")
        )
        assertEquals(
            maxspeedBothDirections(MaxSpeedSign(Mph(20)), MaxSpeedZone(Mph(20), "GB", "zone20")),
            parseGB("maxspeed:type" to "GB:zone20")
        )
        assertEquals(
            maxspeedBothDirections(MaxSpeedSign(Mph(20)), MaxSpeedZone(Mph(20), "GB", "zone20")),
            parseGB("source:maxspeed" to "GB:zone20")
        )
        assertEquals(
            maxspeedBothDirections(MaxSpeedSign(Mph(20)), MaxSpeedZone(Mph(20), "GB", "zone20")),
            parseGB("zone:maxspeed" to "GB:20")
        )
        assertEquals(
            maxspeedBothDirections(MaxSpeedSign(Mph(20)), MaxSpeedZone(Mph(20), "GB", "zone20")),
            parseGB("zone:traffic" to "GB:zone20")
        )
    }

    @Test fun `maxspeed type in maxspeed tag and type is 'sign'`() {
        assertEquals(
            maxspeedBothDirections(MaxSpeedZone(Kmh(20), "RU", "zone20"), JustSign),
            parseRU(
                "source:maxspeed" to "sign",
                "maxspeed" to "RU:zone20"
            )
        )
        assertEquals(
            maxspeedBothDirections(MaxSpeedZone(Mph(20), "GB", "zone20"), JustSign),
            parseGB(
                "source:maxspeed" to "sign",
                "maxspeed" to "GB:zone20"
            )
        )
    }

    @Test fun `duplicated type tag is valid`() {
        assertEquals(
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            parseDE(
                "source:maxspeed" to "DE:urban",
                "maxspeed:type" to "DE:urban"
            )
        )
        assertEquals(
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            parseDE(
                "source:maxspeed" to "DE:urban",
                "zone:traffic" to "DE:urban"
            )
        )
        assertEquals(
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            parseDE(
                "maxspeed:type" to "DE:urban",
                "zone:maxspeed" to "DE:urban"
            )
        )
        assertEquals(
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            parseDE(
                "source:maxspeed" to "DE:urban",
                "maxspeed:type" to "DE:urban",
                "zone:traffic" to "DE:urban"
            )
        )
        assertEquals(
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            parseDE(
                "source:maxspeed" to "DE:urban",
                "zone:maxspeed" to "DE:urban",
                "zone:traffic" to "DE:urban"
            )
        )
        assertEquals(
            maxspeedBothDirections(MaxSpeedSign(Kmh(30)), MaxSpeedZone(Kmh(30), "DE", "zone30")),
            parseDE(
                "source:maxspeed" to "DE:zone30",
                "zone:maxspeed" to "DE:30"
            )
        )
        assertEquals(
            maxspeedBothDirections(MaxSpeedSign(Kmh(30)), MaxSpeedZone(Kmh(30), "DE", "zone30")),
            parseDE(
                "maxspeed:type" to "DE:zone30",
                "zone:maxspeed" to "DE:30"
            )
        )
        assertEquals(
            maxspeedBothDirections(MaxSpeedSign(Kmh(30)), MaxSpeedZone(Kmh(30), "DE", "zone30")),
            parseDE(
                "source:maxspeed" to "DE:zone30",
                "maxspeed:type" to "DE:zone30"
            )
        )
        assertEquals(
            maxspeedBothDirections(MaxSpeedSign(Kmh(30)), MaxSpeedZone(Kmh(30), "DE", "zone30")),
            parseDE(
                "zone:traffic" to "DE:zone30",
                "zone:maxspeed" to "DE:30"
            )
        )
    }

    @Test fun `source_maxspeed unknown value and other valid type tag is valid`() {
        assertEquals(
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            parseDE(
                "source:maxspeed" to "survey",
                "maxspeed:type" to "DE:urban"
            )
        )
        assertEquals(
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            parseDE(
                "source:maxspeed" to "survey",
                "zone:maxspeed" to "DE:urban"
            )
        )
        assertEquals(
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            parseDE(
                "source:maxspeed" to "survey",
                "zone:traffic" to "DE:urban"
            )
        )
    }

    @Test fun `source_maxspeed unknown value and other duplicate valid type tags is valid`() {
        assertEquals(
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            parseDE(
                "source:maxspeed" to "survey",
                "maxspeed:type" to "DE:urban",
                "zone:maxspeed" to "DE:urban"
            )
        )
        assertEquals(
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            parseDE(
                "source:maxspeed" to "survey",
                "zone:maxspeed" to "DE:urban",
                "zone:traffic" to "DE:urban"
            )
        )
        assertEquals(
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            parseDE(
                "source:maxspeed" to "survey",
                "zone:traffic" to "DE:urban",
                "zone:maxspeed" to "DE:urban"
            )
        )
        assertEquals(
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
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
            maxspeedBothDirections(null, LivingStreet(null)),
            parse("highway" to "living_street")
        )
        assertEquals(
            maxspeedBothDirections(null, LivingStreet(null)),
            parse(
                "highway" to "residential",
                "living_street" to "yes"
            )
        )
        assertEquals(
            maxspeedBothDirections(null, LivingStreet(null)),
            parse(
                "highway" to "residential",
                "living_street" to "yes",
                "source:maxspeed" to "survey"
            )
        )
        assertEquals(
            maxspeedBothDirections(null, LivingStreet("DE")),
            parseDE(
                "highway" to "service",
                "maxspeed:type" to "DE:living_street"
            )
        )
    }

    @Test fun `not living street type if there is other valid maxspeed tagging`() {
        assertNotEquals(
            maxspeedBothDirections(null, LivingStreet(null)),
            parseDE(
                "highway" to "living_street",
                "maxspeed:type" to "DE:zone20"
            )
        )
        assertNotEquals(
            maxspeedBothDirections(null, LivingStreet(null)),
            parseDE(
                "highway" to "living_street",
                "source:maxspeed" to "DE:urban"
            )
        )
        assertNotEquals(
            maxspeedBothDirections(null, LivingStreet(null)),
            parseDE(
                "highway" to "residential",
                "living_street" to "yes",
                "zone:maxspeed" to "DE:urban"
            )
        )
    }

    @Test fun `living street with explicit speed limit`() {
        assertEquals(
            maxspeedBothDirections(MaxSpeedSign(Kmh(20)), LivingStreet(null)),
            parse(
                "highway" to "living_street",
                "maxspeed" to "20"
            )
        )
        assertEquals(
            maxspeedBothDirections(MaxSpeedSign(Kmh(20)), LivingStreet(null)),
            parse(
                "highway" to "residential",
                "living_street" to "yes",
                "maxspeed" to "20"
            )
        )
        assertEquals(
            maxspeedBothDirections(WalkMaxSpeed, LivingStreet("DE")),
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
            maxspeedBothDirections(null, IsSchoolZone),
            parse("hazard" to "school_zone")
        )
        assertEquals(
            maxspeedBothDirections(null, IsSchoolZone),
            parse(
                "hazard" to "school_zone",
                "highway" to "residential",
                "source:maxspeed" to "survey"
            )
        )
        assertEquals(
            maxspeedBothDirections(MaxSpeedSign(Kmh(20)), IsSchoolZone),
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
            maxspeedBothDirections(null, IsSchoolZone),
            parse(
                "hazard" to "school_zone",
                "highway" to "living_street"
            )
        )
        assertEquals(
            maxspeedBothDirections(null, IsSchoolZone),
            parse(
                "hazard" to "school_zone",
                "highway" to "service",
                "living_street" to "yes"
            )
        )
    }

    @Test fun `not school zone if there is other valid maxspeed type tagging`() {
        assertNotEquals(
            maxspeedBothDirections(null, IsSchoolZone),
            parseDE(
                "hazard" to "school_zone",
                "maxspeed:type" to "DE:zone20"
            )
        )
        assertNotEquals(
            maxspeedBothDirections(null, IsSchoolZone),
            parseDE(
                "hazard" to "school_zone",
                "source:maxspeed" to "DE:urban"
            )
        )
        assertNotEquals(
            maxspeedBothDirections(null, IsSchoolZone),
            parseDE(
                "hazard" to "school_zone",
                "zone:maxspeed" to "DE:urban"
            )
        )
    }

    /* ------------------------------------------------------------------------------------------ */
    /* ------------------------------ different directions -------------------------------------- */
    /* ------------------------------------------------------------------------------------------ */

    /* ------------------------------------------ invalid --------------------------------------- */

    @Test fun `invalid speed in one direction`() {
        assertEquals(
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(Invalid, null),
                MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)
            ),
            parse(
                "maxspeed:forward" to "10 knots",
                "maxspeed:backward" to "40"
            )
        )
        assertEquals(
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(MaxSpeedSign(Kmh(40)), null),
                MaxspeedAndType(Invalid, null)
            ),
            parse(
                "maxspeed:forward" to "40",
                "maxspeed:backward" to "10 knots"
            )
        )
        assertEquals(
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(Invalid, null),
                MaxspeedAndType(MaxSpeedSign(Mph(40)), null)
            ),
            parse(
                "maxspeed:forward" to "10 knots",
                "maxspeed:backward" to "40 mph"
            )
        )
        assertEquals(
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(MaxSpeedSign(Mph(40)), null),
                MaxspeedAndType(Invalid, null)
            ),
            parse(
                "maxspeed:forward" to "40 mph",
                "maxspeed:backward" to "10 knots"
            )
        )
    }

    @Test fun `invalid type in one direction`() {
        assertEquals(
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(null, ImplicitMaxSpeed("DE", URBAN, null)),
                MaxspeedAndType(null, Invalid)
            ),
            parseDE(
                "maxspeed:type:forward" to "DE:urban",
                "maxspeed:type:backward" to "unknown"
            )
        )
        assertEquals(
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(null, Invalid),
                MaxspeedAndType(null, ImplicitMaxSpeed("DE", URBAN, null))
            ),
            parseDE(
                "maxspeed:type:forward" to "unknown",
                "maxspeed:type:backward" to "DE:urban"
            )
        )
    }

    @Test fun `different invalid type in each direction`() {
        assertEquals(
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(null, Invalid),
                MaxspeedAndType(null, Invalid)
            ),
            parseDE(
                "maxspeed:type:forward" to "xx:urban", // incorrect country code
                "maxspeed:type:backward" to "unknown"
            )
        )
        assertEquals(
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(null, Invalid),
                MaxspeedAndType(null, Invalid)
            ),
            parseDE(
                "maxspeed:type:forward" to "unknown",
                "maxspeed:type:backward" to "sign" // sign but no value
            )
        )
    }

    /* ------------------------------------ only one direction ---------------------------------- */

    @Test fun `explicit speed in only one direction`() {
        assertEquals(
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(MaxSpeedSign(Kmh(40)), null),
                null
            ),
            parse("maxspeed:forward" to "40")
        )
        assertEquals(
            ForwardAndBackwardMaxspeedAndType(
                null,
                MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)
            ),
            parse("maxspeed:backward" to "40")
        )
        assertEquals(
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(MaxSpeedSign(Mph(40)), null),
                null
            ),
            parse("maxspeed:forward" to "40 mph")
        )
        assertEquals(
            ForwardAndBackwardMaxspeedAndType(
                null,
                MaxspeedAndType(MaxSpeedSign(Mph(40)), null)
            ),
            parse("maxspeed:backward" to "40 mph")
        )
    }

    @Test fun `type in only one direction`() {
        assertEquals(
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(null, ImplicitMaxSpeed("DE", URBAN, null)),
                null
            ),
            parseDE("maxspeed:type:forward" to "DE:urban",)
        )
        assertEquals(
            ForwardAndBackwardMaxspeedAndType(
                null,
                MaxspeedAndType(null, ImplicitMaxSpeed("DE", URBAN, null))
            ),
            parseDE("maxspeed:type:backward" to "DE:urban")
        )
    }

    @Test fun `explicit speed and type in only one direction`() {
        assertEquals(
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(MaxSpeedSign(Kmh(40)), JustSign),
                null
            ),
            parse(
                "maxspeed:forward" to "40",
                "maxspeed:type:forward" to "sign"
            )
        )
        assertEquals(
            ForwardAndBackwardMaxspeedAndType(
                null,
                MaxspeedAndType(MaxSpeedSign(Kmh(40)), JustSign)
            ),
            parse(
                "maxspeed:backward" to "40",
                "maxspeed:type:backward" to "sign"
            )
        )
        assertEquals(
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(MaxSpeedSign(Mph(40)), JustSign),
                null
            ),
            parse(
                "maxspeed:forward" to "40 mph",
                "maxspeed:type:forward" to "sign"
            )
        )
        assertEquals(
            ForwardAndBackwardMaxspeedAndType(
                null,
                MaxspeedAndType(MaxSpeedSign(Mph(40)), JustSign)
            ),
            parse(
                "maxspeed:backward" to "40 mph",
                "maxspeed:type:backward" to "sign"
            )
        )
    }

    /* ------------------------------------ both directions ------------------------------------- */

    @Test fun `same explicit speed in both directions`() {
        assertEquals(
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(MaxSpeedSign(Kmh(40)), null),
                MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)
            ),
            parse(
                "maxspeed:forward" to "40",
                "maxspeed:backward" to "40"
            )
        )
        assertEquals(
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(MaxSpeedSign(Mph(40)), null),
                MaxspeedAndType(MaxSpeedSign(Mph(40)), null)
            ),
            parse(
                "maxspeed:forward" to "40 mph",
                "maxspeed:backward" to "40 mph"
            )
        )
    }

    @Test fun `different explicit speed in both directions`() {
        assertEquals(
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(MaxSpeedSign(Kmh(40)), null),
                MaxspeedAndType(MaxSpeedSign(Kmh(50)), null)
            ),
            parse(
                "maxspeed:forward" to "40",
                "maxspeed:backward" to "50"
            )
        )
        assertEquals(
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(MaxSpeedSign(Mph(40)), null),
                MaxspeedAndType(MaxSpeedSign(Mph(50)), null)
            ),
            parse(
                "maxspeed:forward" to "40 mph",
                "maxspeed:backward" to "50 mph"
            )
        )
    }

    @Test fun `different speed and type in each direction`() {
        assertEquals(
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(MaxSpeedSign(Kmh(40)), JustSign),
                MaxspeedAndType(MaxSpeedSign(Kmh(60)), ImplicitMaxSpeed("DE", URBAN, null))
            ),
            parseDE(
                "maxspeed:forward" to "40",
                "maxspeed:type:forward" to "sign",
                "maxspeed:backward" to "60",
                "maxspeed:type:backward" to "DE:urban"
            )
        )
        assertEquals(
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(MaxSpeedSign(Kmh(60)), ImplicitMaxSpeed("DE", URBAN, null)),
                MaxspeedAndType(MaxSpeedSign(Kmh(40)), JustSign)
            ),
            parseDE(
                "maxspeed:forward" to "60",
                "maxspeed:type:forward" to "DE:urban",
                "maxspeed:backward" to "40",
                "maxspeed:type:backward" to "sign"
            )
        )
        assertEquals(
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(MaxSpeedSign(Mph(40)), JustSign),
                MaxspeedAndType(MaxSpeedSign(Mph(60)), ImplicitMaxSpeed("GB", NSL_SINGLE, null))
            ),
            parseGB(
                "maxspeed:forward" to "40 mph",
                "maxspeed:type:forward" to "sign",
                "maxspeed:backward" to "60 mph",
                "maxspeed:type:backward" to "GB:nsl_single"
            )
        )
        assertEquals(
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(MaxSpeedSign(Mph(60)), ImplicitMaxSpeed("GB", NSL_SINGLE, null)),
                MaxspeedAndType(MaxSpeedSign(Mph(40)), JustSign)
            ),
            parseGB(
                "maxspeed:forward" to "60 mph",
                "maxspeed:type:forward" to "GB:nsl_single",
                "maxspeed:backward" to "40 mph",
                "maxspeed:type:backward" to "sign"
            )
        )
    }
}

private fun parse(vararg tags: Pair<String, String>): ForwardAndBackwardMaxspeedAndType? {
    val countryInfo: CountryInfo = mock()
    return createForwardAndBackwardMaxspeedAndType(mapOf(*tags), countryInfo)
}

private fun parseDE(vararg tags: Pair<String, String>): ForwardAndBackwardMaxspeedAndType? {
    val countryInfoDE: CountryInfo = mock()
    on(countryInfoDE.countryCode).thenReturn("DE")
    on(countryInfoDE.speedUnits).thenReturn(listOf(SpeedMeasurementUnit.KILOMETERS_PER_HOUR))
    return createForwardAndBackwardMaxspeedAndType(mapOf(*tags), countryInfoDE)
}

private fun parseGB(vararg tags: Pair<String, String>): ForwardAndBackwardMaxspeedAndType? {
    val countryInfoGB: CountryInfo = mock()
    on(countryInfoGB.countryCode).thenReturn("GB")
    on(countryInfoGB.speedUnits).thenReturn(listOf(SpeedMeasurementUnit.MILES_PER_HOUR))
    return createForwardAndBackwardMaxspeedAndType(mapOf(*tags), countryInfoGB)
}

private fun parseRU(vararg tags: Pair<String, String>): ForwardAndBackwardMaxspeedAndType? {
    val countryInfoRU: CountryInfo = mock()
    on(countryInfoRU.countryCode).thenReturn("RU")
    on(countryInfoRU.speedUnits).thenReturn(listOf(SpeedMeasurementUnit.KILOMETERS_PER_HOUR))
    return createForwardAndBackwardMaxspeedAndType(mapOf(*tags), countryInfoRU)
}

private fun maxspeedBothDirections(explicit: MaxSpeedAnswer?, type: MaxSpeedAnswer?) =
    ForwardAndBackwardMaxspeedAndType(
        MaxspeedAndType(explicit, type),
        MaxspeedAndType(explicit, type)
    )
