package de.westnordost.streetcomplete.osm.maxspeed

import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.meta.SpeedMeasurementUnit
import de.westnordost.streetcomplete.osm.lit.LitStatus.*
import de.westnordost.streetcomplete.osm.maxspeed.Inequality.*
import de.westnordost.streetcomplete.osm.maxspeed.RoadType.*
import de.westnordost.streetcomplete.osm.weight.*
import de.westnordost.streetcomplete.testutils.mock
import de.westnordost.streetcomplete.testutils.on
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MaxspeedParserKtTest {
    /* For these tests, RU and DE are hard-coded to be countries in km/h and GB is hard-coded
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
        val invalidSpeed = bareMaxspeedBothDirections(Invalid, null, null)
        assertEquals(invalidSpeed, parse("maxspeed" to "1 knot"))
    }

    @Test fun `invalid speed`() {
        val invalidSpeed = bareMaxspeedBothDirections(Invalid, null, null)
        assertEquals(invalidSpeed, parse("maxspeed" to "fixme"))
        assertEquals(invalidSpeed, parse("maxspeed" to "sign"))
    }

    @Test fun `invalid type due to unknown value`() {
        val invalidType = bareMaxspeedBothDirections(null, Invalid, null)
        assertEquals(invalidType, parse("maxspeed:type" to "fixme"))
    }

    @Test fun `invalid type due to maxspeed value in type`() {
        val invalidType = bareMaxspeedBothDirections(null, Invalid, null)
        assertEquals(invalidType, parse("maxspeed:type" to "walk"))
        assertEquals(invalidType, parse("maxspeed:type" to "none"))
    }

    @Test fun `invalid type because of contradicting types`() {
        val invalidType = bareMaxspeedBothDirections(null, Invalid, null)
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
        val invalidType = bareMaxspeedBothDirections(Invalid, Invalid, null)
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
        val invalidType = bareMaxspeedBothDirections(null, Invalid, null)
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
        val invalid = bareMaxspeedBothDirections(Invalid, Invalid, null)
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
        val invalid = bareMaxspeedBothDirections(null, Invalid, null)
        assertEquals(invalid, parseDE("maxspeed:type" to "xx:urban"))
    }

    @Test fun `invalid because of over-tagging`() {
        val invalid = bareMaxspeedBothDirections(Invalid, Invalid, null)
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
            bareMaxspeedBothDirections(MaxSpeedSign(Kmh(20)), null, null),
            parseDE("maxspeed" to "20")
        )
        assertEquals(
            bareMaxspeedBothDirections(MaxSpeedSign(Kmh(100)), null, null),
            parseDE("maxspeed" to "100")
        )
        assertEquals(
            bareMaxspeedBothDirections(MaxSpeedSign(Mph(20)), null, null),
            parseDE("maxspeed" to "20 mph")
        )
        assertEquals(
            bareMaxspeedBothDirections(MaxSpeedSign(Mph(70)), null, null),
            parseGB("maxspeed" to "70 mph")
        )
    }

    @Test fun `numerical maxspeed, signed`() {
        assertEquals(
            bareMaxspeedBothDirections(MaxSpeedSign(Kmh(20)), JustSign, null),
            parse("maxspeed" to "20", "maxspeed:type" to "sign")
        )
        assertEquals(
            bareMaxspeedBothDirections(MaxSpeedSign(Kmh(100)), JustSign, null),
            parse("maxspeed" to "100", "source:maxspeed" to "sign")
        )
        assertEquals(
            bareMaxspeedBothDirections(MaxSpeedSign(Mph(20)), JustSign, null),
            parse("maxspeed" to "20 mph", "source:maxspeed" to "sign")
        )
        assertEquals(
            bareMaxspeedBothDirections(MaxSpeedSign(Mph(70)), JustSign, null),
            parse("maxspeed" to "70 mph", "maxspeed:type" to "sign")
        )
    }

    @Test fun `common non-numerical maxspeed`() {
        assertEquals(
            bareMaxspeedBothDirections(MaxSpeedIsNone, null, null),
            parse("maxspeed" to "none")
        )
        assertEquals(
            bareMaxspeedBothDirections(WalkMaxSpeed, null, null),
            parse("maxspeed" to "walk")
        )
    }

    @Test fun `maxspeed type in maxspeed tag`() {
        assertEquals(
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("RU", URBAN, null), null),
            parseRU("maxspeed" to "RU:urban")
        )
        assertEquals(
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("RU", RURAL, null), null),
            parseRU("maxspeed" to "RU:rural")
        )
        assertEquals(
            bareMaxspeedBothDirections(
                MaxSpeedSign(Kmh(20)), MaxSpeedZone(Kmh(20), "DE", "zone20"), null),
            parseDE("maxspeed" to "DE:zone20")
        )
        assertEquals(
            bareMaxspeedBothDirections(
                MaxSpeedSign(Mph(20)), MaxSpeedZone(Mph(20), "GB", "zone20"), null),
            parseGB("maxspeed" to "GB:zone20")
        )
    }

    @Test fun `signed and valid value in maxspeed tag`() {
        assertEquals(
            bareMaxspeedBothDirections(MaxSpeedSign(Mph(30)), JustSign, null),
            parse(
                "maxspeed:type" to "sign",
                "maxspeed" to "30 mph"
            ))
        assertEquals(
            bareMaxspeedBothDirections(MaxSpeedSign(Mph(30)), JustSign, null),
            parse(
                "source:maxspeed" to "sign",
                "maxspeed" to "30 mph"
            ))
        assertEquals(
            bareMaxspeedBothDirections(MaxSpeedSign(Kmh(60)), JustSign, null),
            parse(
                "maxspeed:type" to "sign",
                "maxspeed" to "60"
            ))
        assertEquals(
            bareMaxspeedBothDirections(WalkMaxSpeed, JustSign, null),
            parse(
                "maxspeed:type" to "sign",
                "maxspeed" to "walk"
            ))
        assertEquals(
            bareMaxspeedBothDirections(MaxSpeedIsNone, JustSign, null),
            parse(
                "maxspeed:type" to "sign",
                "maxspeed" to "none"
            ))
    }

    /* ------------------------------------------ types ----------------------------------------- */

    @Test fun `standard implicit maxspeed`() {
        assertEquals(
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null), null),
            parseDE("maxspeed:type" to "DE:urban")
        )
        assertEquals(
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null), null),
            parseDE("source:maxspeed" to "DE:urban")
        )
        assertEquals(
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null), null),
            parseDE("zone:maxspeed" to "DE:urban")
        )
        assertEquals(
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null), null),
            parseDE("zone:traffic" to "DE:urban")
        )
    }

    @Test fun `implicit type with two-part country code`() {
        val countryInfoBEWAL: CountryInfo = mock()
        on(countryInfoBEWAL.countryCode).thenReturn("BE-WAL")

        assertEquals(
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("BE-WAL", URBAN, null), null),
            createForwardAndBackwardAllSpeedInformation(mapOf("maxspeed:type" to "BE-WAL:urban"), countryInfoBEWAL)
        )

        val countryInfoCAAB: CountryInfo = mock()
        on(countryInfoCAAB.countryCode).thenReturn("CA-AB")
        assertEquals(
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("CA-AB", URBAN, null), null),
            createForwardAndBackwardAllSpeedInformation(mapOf("maxspeed:type" to "CA-AB:urban"), countryInfoCAAB)
        )
    }

    @Test fun `unsigned maxspeed`() {
        assertEquals(
            bareMaxspeedBothDirections(null, NoSign, null),
            parse("maxspeed:signed" to "no")
        )
    }

    @Test fun `unsigned maxspeed with implicit type also tagged`() {
        assertEquals(
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null), null),
            parseDE(
                "maxspeed:type" to "DE:urban",
                "maxspeed:signed" to "no"
            )
        )
    }

    @Test fun `unknown road type`() {
        assertEquals(
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", UNKNOWN, null), null),
            parseDE("maxspeed:type" to "DE:flubberway")
        )
    }

    @Test fun `recognise lit tag for implicit types`() {
        assertEquals(
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("GB", NSL_RESTRICTED, YES), null),
            parseGB(
                "maxspeed:type" to "GB:nsl_restricted",
                "lit" to "yes"
            )
        )
        assertEquals(
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("GB", NSL_SINGLE, NO), null),
            parseGB(
                "maxspeed:type" to "GB:nsl_single",
                "lit" to "no"
            )
        )
    }

    @Test fun `invalid value for lit is null`() {
        assertEquals(
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("GB", NSL_RESTRICTED, null), null),
            parseGB(
                "maxspeed:type" to "GB:nsl_restricted",
                "lit" to "unknown"
            )
        )
    }

    @Test fun `maxspeed zone`() {
        assertEquals(
            bareMaxspeedBothDirections(MaxSpeedSign(Kmh(30)), MaxSpeedZone(Kmh(30), "DE", "zone30"), null),
            parseDE("maxspeed:type" to "DE:zone30")
        )
        assertEquals(
            bareMaxspeedBothDirections(MaxSpeedSign(Kmh(30)), MaxSpeedZone(Kmh(30), "DE", "zone30"), null),
            parseDE("source:maxspeed" to "DE:zone30")
        )
        assertEquals(
            bareMaxspeedBothDirections(MaxSpeedSign(Kmh(30)), MaxSpeedZone(Kmh(30), "DE", "zone30"), null),
            parseDE("zone:maxspeed" to "DE:30")
        )
        assertEquals(
            bareMaxspeedBothDirections(MaxSpeedSign(Kmh(30)), MaxSpeedZone(Kmh(30), "DE", "zone30"), null),
            parseDE("zone:traffic" to "DE:zone30")
        )
        assertEquals(
            bareMaxspeedBothDirections(MaxSpeedSign(Mph(20)), MaxSpeedZone(Mph(20), "GB", "zone20"), null),
            parseGB("maxspeed:type" to "GB:zone20")
        )
        assertEquals(
            bareMaxspeedBothDirections(MaxSpeedSign(Mph(20)), MaxSpeedZone(Mph(20), "GB", "zone20"), null),
            parseGB("source:maxspeed" to "GB:zone20")
        )
        assertEquals(
            bareMaxspeedBothDirections(MaxSpeedSign(Mph(20)), MaxSpeedZone(Mph(20), "GB", "zone20"), null),
            parseGB("zone:maxspeed" to "GB:20")
        )
        assertEquals(
            bareMaxspeedBothDirections(MaxSpeedSign(Mph(20)), MaxSpeedZone(Mph(20), "GB", "zone20"), null),
            parseGB("zone:traffic" to "GB:zone20")
        )
    }

    @Test fun `maxspeed type in maxspeed tag and type is 'sign'`() {
        assertEquals(
            bareMaxspeedBothDirections(MaxSpeedZone(Kmh(20), "RU", "zone20"), JustSign, null),
            parseRU(
                "source:maxspeed" to "sign",
                "maxspeed" to "RU:zone20"
            )
        )
        assertEquals(
            bareMaxspeedBothDirections(MaxSpeedZone(Mph(20), "GB", "zone20"), JustSign, null),
            parseGB(
                "source:maxspeed" to "sign",
                "maxspeed" to "GB:zone20"
            )
        )
    }

    @Test fun `duplicated type tag is valid`() {
        assertEquals(
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null), null),
            parseDE(
                "source:maxspeed" to "DE:urban",
                "maxspeed:type" to "DE:urban"
            )
        )
        assertEquals(
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null), null),
            parseDE(
                "source:maxspeed" to "DE:urban",
                "zone:traffic" to "DE:urban"
            )
        )
        assertEquals(
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null), null),
            parseDE(
                "maxspeed:type" to "DE:urban",
                "zone:maxspeed" to "DE:urban"
            )
        )
        assertEquals(
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null), null),
            parseDE(
                "source:maxspeed" to "DE:urban",
                "maxspeed:type" to "DE:urban",
                "zone:traffic" to "DE:urban"
            )
        )
        assertEquals(
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null), null),
            parseDE(
                "source:maxspeed" to "DE:urban",
                "zone:maxspeed" to "DE:urban",
                "zone:traffic" to "DE:urban"
            )
        )
        assertEquals(
            bareMaxspeedBothDirections(MaxSpeedSign(Kmh(30)), MaxSpeedZone(Kmh(30), "DE", "zone30"), null),
            parseDE(
                "source:maxspeed" to "DE:zone30",
                "zone:maxspeed" to "DE:30"
            )
        )
        assertEquals(
            bareMaxspeedBothDirections(MaxSpeedSign(Kmh(30)), MaxSpeedZone(Kmh(30), "DE", "zone30"), null),
            parseDE(
                "maxspeed:type" to "DE:zone30",
                "zone:maxspeed" to "DE:30"
            )
        )
        assertEquals(
            bareMaxspeedBothDirections(MaxSpeedSign(Kmh(30)), MaxSpeedZone(Kmh(30), "DE", "zone30"), null),
            parseDE(
                "source:maxspeed" to "DE:zone30",
                "maxspeed:type" to "DE:zone30"
            )
        )
        assertEquals(
            bareMaxspeedBothDirections(MaxSpeedSign(Kmh(30)), MaxSpeedZone(Kmh(30), "DE", "zone30"), null),
            parseDE(
                "zone:traffic" to "DE:zone30",
                "zone:maxspeed" to "DE:30"
            )
        )
    }

    @Test fun `source_maxspeed unknown value and other valid type tag is valid`() {
        assertEquals(
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null), null),
            parseDE(
                "source:maxspeed" to "survey",
                "maxspeed:type" to "DE:urban"
            )
        )
        assertEquals(
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null), null),
            parseDE(
                "source:maxspeed" to "survey",
                "zone:maxspeed" to "DE:urban"
            )
        )
        assertEquals(
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null), null),
            parseDE(
                "source:maxspeed" to "survey",
                "zone:traffic" to "DE:urban"
            )
        )
    }

    @Test fun `source_maxspeed unknown value and other duplicate valid type tags is valid`() {
        assertEquals(
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null), null),
            parseDE(
                "source:maxspeed" to "survey",
                "maxspeed:type" to "DE:urban",
                "zone:maxspeed" to "DE:urban"
            )
        )
        assertEquals(
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null), null),
            parseDE(
                "source:maxspeed" to "survey",
                "zone:maxspeed" to "DE:urban",
                "zone:traffic" to "DE:urban"
            )
        )
        assertEquals(
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null), null),
            parseDE(
                "source:maxspeed" to "survey",
                "zone:traffic" to "DE:urban",
                "zone:maxspeed" to "DE:urban"
            )
        )
        assertEquals(
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null), null),
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
            ForwardAndBackwardAllSpeedInformation(null, null, LivingStreet(null)),
            parse("highway" to "living_street")
        )
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(null, null, LivingStreet(null)),
            parse(
                "highway" to "residential",
                "living_street" to "yes"
            )
        )
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(null, null, LivingStreet(null)),
            parse(
                "highway" to "residential",
                "living_street" to "yes",
                "source:maxspeed" to "survey"
            )
        )
        assertEquals(
            bareMaxspeedBothDirections(null, LivingStreet("DE"), LivingStreet("DE")),
            parseDE(
                "highway" to "service",
                "maxspeed:type" to "DE:living_street"
            )
        )
    }

    @Test fun `not living street type if there is other valid maxspeed tagging`() {
        assertNotEquals(
            bareMaxspeedBothDirections(null, LivingStreet(null), null),
            parseDE(
                "highway" to "living_street",
                "maxspeed:type" to "DE:zone20"
            )
        )
        assertNotEquals(
            bareMaxspeedBothDirections(null, LivingStreet(null), null),
            parseDE(
                "highway" to "living_street",
                "source:maxspeed" to "DE:urban"
            )
        )
        assertNotEquals(
            bareMaxspeedBothDirections(null, LivingStreet(null), null),
            parseDE(
                "highway" to "residential",
                "living_street" to "yes",
                "zone:maxspeed" to "DE:urban"
            )
        )
    }

    @Test fun `living street with explicit speed limit`() {
        assertEquals(
            bareMaxspeedBothDirections(MaxSpeedSign(Kmh(20)), null, LivingStreet(null)),
            parse(
                "highway" to "living_street",
                "maxspeed" to "20"
            )
        )
        assertEquals(
            bareMaxspeedBothDirections(MaxSpeedSign(Kmh(20)), null, LivingStreet(null)),
            parse(
                "highway" to "residential",
                "living_street" to "yes",
                "maxspeed" to "20"
            )
        )
        assertEquals(
            bareMaxspeedBothDirections(WalkMaxSpeed, LivingStreet("DE"), LivingStreet(null)),
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
            ForwardAndBackwardAllSpeedInformation(null, null, IsSchoolZone),
            parse("hazard" to "school_zone")
        )
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(null, null, IsSchoolZone),
            parse(
                "hazard" to "school_zone",
                "highway" to "residential",
                "source:maxspeed" to "survey"
            )
        )
        assertEquals(
            bareMaxspeedBothDirections(MaxSpeedSign(Kmh(20)), null, IsSchoolZone),
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
            ForwardAndBackwardAllSpeedInformation(null, null, IsSchoolZone),
            parse(
                "hazard" to "school_zone",
                "highway" to "living_street"
            )
        )
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(null, null, IsSchoolZone),
            parse(
                "hazard" to "school_zone",
                "highway" to "service",
                "living_street" to "yes"
            )
        )
    }

    /* ------------------------------------------ variable -------------------------------------- */

    @Test fun `variable limit`() {
        assertEquals(
            maxspeedBothDirections(null, null, true, null),
            parse("maxspeed:variable" to "yes")
        )
        assertEquals(
            maxspeedBothDirections(null, null, true, null),
            parse("maxspeed:variable" to "peak_traffic")
        )
        assertEquals(
            maxspeedBothDirections(null, null, true, null),
            parse("maxspeed:variable" to "weather")
        )
        assertEquals(
            maxspeedBothDirections(null, null, true, null),
            parse("maxspeed:variable" to "environment")
        )
        assertEquals(
            maxspeedBothDirections(null, null, true, null),
            parse("maxspeed:variable" to "obstruction")
        )
        assertEquals(
            maxspeedBothDirections(null, null, true, null),
            parse("maxspeed" to "signals")
        )
    }

    @Test fun `variable limit in one direction`() {
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(
                AllSpeedInformation(null, null, true),
                null,
                null
            ),
            parse("maxspeed:variable:forward" to "yes")
        )
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(
                null,
                AllSpeedInformation(null, null, true),
                null
            ),
            parse("maxspeed:variable:backward" to "yes")
        )
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(
                AllSpeedInformation(null, null, true),
                AllSpeedInformation(null, null, false),
                null
            ),
            parse(
                "maxspeed:variable:forward" to "yes",
                "maxspeed:variable:backward" to "no"
            )
        )
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(
                AllSpeedInformation(null, null, false),
                AllSpeedInformation(null, null, true),
                null
            ),
            parse(
                "maxspeed:variable:forward" to "no",
                "maxspeed:variable:backward" to "yes"
            )
        )
    }

    @Test fun `invalid variable because of conflicting values`() {
        assertEquals(
            null,
            parse(
                "maxspeed:variable" to "no",
                "maxspeed" to "signals"
            )
        )
    }

    @Test fun `definitely not variable`() {
        assertEquals(
            maxspeedBothDirections(null, null, false, null),
            parse("maxspeed:variable" to "no")
        )
    }

    /* ------------------------------------------ advisory -------------------------------------- */

    @Test fun `invalid advisory`() {
        assertEquals(
            null,
            parse("maxspeed:advisory" to "yes")
        )
        assertEquals(
            null,
            parseDE("maxspeed:advisory" to "DE:urban")
        )
    }

    @Test fun `advisory limit`() {
        assertEquals(
            maxspeedBothDirections(null, AdvisorySpeedSign(Kmh(50)), null, null),
            parse("maxspeed:advisory" to "50")
        )
        assertEquals(
            maxspeedBothDirections(null, AdvisorySpeedSign(Mph(50)), null, null),
            parse("maxspeed:advisory" to "50 mph")
        )
        assertEquals(
            maxspeedBothDirections(null, AdvisorySpeedSign(Kmh(50)), null, null),
            parse("maxspeed:advised" to "50")
        )
    }

    @Test fun `advisory limit in one direction`() {
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(
                AllSpeedInformation(null, AdvisorySpeedSign(Kmh(50)), null),
                null,
                null
            ),
            parse("maxspeed:advisory:forward" to "50")
        )
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(
                AllSpeedInformation(null, AdvisorySpeedSign(Mph(50)), null),
                null,
                null
            ),
            parse("maxspeed:advisory:forward" to "50 mph")
        )
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(
                null,
                AllSpeedInformation(null, AdvisorySpeedSign(Kmh(50)), null),
                null
            ),
            parse("maxspeed:advisory:backward" to "50")
        )
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(
                null,
                AllSpeedInformation(null, AdvisorySpeedSign(Mph(50)), null),
                null
            ),
            parse("maxspeed:advisory:backward" to "50 mph")
        )
    }

    @Test fun `different advisory limit in each direction`() {
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(
                AllSpeedInformation(null, AdvisorySpeedSign(Kmh(50)), null),
                AllSpeedInformation(null, AdvisorySpeedSign(Kmh(40)), null),
                null
            ),
            parse(
                "maxspeed:advisory:forward" to "50",
                "maxspeed:advisory:backward" to "40"
            )
        )
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(
                AllSpeedInformation(null, AdvisorySpeedSign(Mph(50)), null),
                AllSpeedInformation(null, AdvisorySpeedSign(Mph(40)), null),
                null
            ),
            parse(
                "maxspeed:advisory:forward" to "50 mph",
                "maxspeed:advisory:backward" to "40 mph"
            )
        )
    }

    /* ------------------------------------------------------------------------------------------ */
    /* ------------------------------ different directions -------------------------------------- */
    /* ------------------------------------------------------------------------------------------ */

    /* ------------------------------------------ invalid --------------------------------------- */

    @Test fun `invalid speed in one direction`() {
        assertEquals(
            noConditionsOrVehicles(
                MaxspeedAndType(Invalid, null),
                MaxspeedAndType(MaxSpeedSign(Kmh(40)), null),
                null
            ),
            parse(
                "maxspeed:forward" to "10 knots",
                "maxspeed:backward" to "40"
            )
        )
        assertEquals(
            noConditionsOrVehicles(
                MaxspeedAndType(MaxSpeedSign(Kmh(40)), null),
                MaxspeedAndType(Invalid, null),
                null
            ),
            parse(
                "maxspeed:forward" to "40",
                "maxspeed:backward" to "10 knots"
            )
        )
        assertEquals(
            noConditionsOrVehicles(
                MaxspeedAndType(Invalid, null),
                MaxspeedAndType(MaxSpeedSign(Mph(40)), null),
                null
            ),
            parse(
                "maxspeed:forward" to "10 knots",
                "maxspeed:backward" to "40 mph"
            )
        )
        assertEquals(
            noConditionsOrVehicles(
                MaxspeedAndType(MaxSpeedSign(Mph(40)), null),
                MaxspeedAndType(Invalid, null),
                null
            ),
            parse(
                "maxspeed:forward" to "40 mph",
                "maxspeed:backward" to "10 knots"
            )
        )
    }

    @Test fun `invalid type in one direction`() {
        assertEquals(
            noConditionsOrVehicles(
                MaxspeedAndType(null, ImplicitMaxSpeed("DE", URBAN, null)),
                MaxspeedAndType(null, Invalid),
                null
            ),
            parseDE(
                "maxspeed:type:forward" to "DE:urban",
                "maxspeed:type:backward" to "unknown"
            )
        )
        assertEquals(
            noConditionsOrVehicles(
                MaxspeedAndType(null, Invalid),
                MaxspeedAndType(null, ImplicitMaxSpeed("DE", URBAN, null)),
                null
            ),
            parseDE(
                "maxspeed:type:forward" to "unknown",
                "maxspeed:type:backward" to "DE:urban"
            )
        )
    }

    @Test fun `different invalid type in each direction`() {
        assertEquals(
            noConditionsOrVehicles(
                MaxspeedAndType(null, Invalid),
                MaxspeedAndType(null, Invalid),
                null
            ),
            parseDE(
                "maxspeed:type:forward" to "xx:urban", // incorrect country code
                "maxspeed:type:backward" to "unknown"
            )
        )
        assertEquals(
            noConditionsOrVehicles(
                MaxspeedAndType(null, Invalid),
                MaxspeedAndType(null, Invalid),
                null
            ),
            parseDE(
                "maxspeed:type:forward" to "unknown",
                "maxspeed:type:backward" to "sign" // sign but no value
            )
        )
    }

    @Test fun `over-defined maxspeed, direction and bare tag`() {
        assertEquals(
            bareMaxspeedBothDirections(Invalid, Invalid, null),
            parse(
                "maxspeed" to "50",
                "maxspeed:forward" to "60"
            )
        )
        assertEquals(
            bareMaxspeedBothDirections(Invalid, Invalid, null),
            parse(
                "maxspeed" to "50",
                "maxspeed:backward" to "60"
            )
        )
        assertEquals(
            bareMaxspeedBothDirections(Invalid, Invalid, null),
            parse(
                "maxspeed" to "50",
                "maxspeed:forward" to "60",
                "maxspeed:backward" to "70"
            )
        )
    }

    @Test fun `over-defined type, direction and bare tag`() {
        assertEquals(
            bareMaxspeedBothDirections(Invalid, Invalid, null),
            parseDE(
                "maxspeed:type" to "DE:urban",
                "maxspeed:type:forward" to "DE:rural"
            )
        )
        assertEquals(
            bareMaxspeedBothDirections(Invalid, Invalid, null),
            parseDE(
                "maxspeed:type" to "DE:urban",
                "maxspeed:type:backward" to "DE:rural"
            )
        )
        assertEquals(
            bareMaxspeedBothDirections(Invalid, Invalid, null),
            parseDE(
                "maxspeed:type" to "DE:urban",
                "maxspeed:type:forward" to "DE:bicycle_road",
                "maxspeed:type:backward" to "DE:rural"
            )
        )
    }

    @Test fun `different maxspeed in each direction but clashing types is invalid`() {
        assertEquals(
            bareMaxspeedBothDirections(Invalid, Invalid, null),
            parseDE(
                "maxspeed:forward" to "50",
                "maxspeed:backward" to "60",
                "maxspeed:type" to "DE:urban",
                "maxspeed:type:forward" to "DE:rural"
            )
        )
        assertEquals(
            bareMaxspeedBothDirections(Invalid, Invalid, null),
            parseDE(
                "maxspeed:forward" to "50",
                "maxspeed:backward" to "60",
                "maxspeed:type" to "DE:urban",
                "maxspeed:type:backward" to "DE:rural"
            )
        )
        assertEquals(
            bareMaxspeedBothDirections(Invalid, Invalid, null),
            parseDE(
                "maxspeed:forward" to "50",
                "maxspeed:backward" to "60",
                "maxspeed:type" to "DE:urban",
                "maxspeed:type:forward" to "DE:bicycle_road",
                "maxspeed:type:backward" to "DE:rural"
            )
        )
    }

    /* ------------------------------------ only one direction ---------------------------------- */

    @Test fun `explicit speed in only one direction`() {
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(
                AllSpeedInformation(
                    mapOf(null to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null))),
                    null, null
                ),
                null,
                null
            ),
            parse("maxspeed:forward" to "40")
        )
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(
                null,
                AllSpeedInformation(
                    mapOf(null to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null))),
                    null, null
                ),
                null
            ),
            parse("maxspeed:backward" to "40")
        )
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(
                AllSpeedInformation(
                    mapOf(null to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Mph(40)), null))),
                    null, null
                ),
                null,
                null
            ),
            parse("maxspeed:forward" to "40 mph")
        )
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(
                null,
                AllSpeedInformation(
                    mapOf(null to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Mph(40)), null))),
                    null, null
                ),
                null
            ),
            parse("maxspeed:backward" to "40 mph")
        )
    }

    @Test fun `type in only one direction`() {
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(
                AllSpeedInformation(
                    mapOf(null to mapOf(
                        NoCondition to MaxspeedAndType(null, ImplicitMaxSpeed("DE", URBAN, null)))
                    ), null, null
                ),
                null,
                null
            ),
            parseDE("maxspeed:type:forward" to "DE:urban")
        )
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(
                null,
                AllSpeedInformation(
                    mapOf(null to mapOf(
                        NoCondition to MaxspeedAndType(null, ImplicitMaxSpeed("DE", URBAN, null)))
                    ), null, null
                ),
                null
            ),
            parseDE("maxspeed:type:backward" to "DE:urban")
        )
    }

    @Test fun `explicit speed and type in only one direction`() {
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(
                AllSpeedInformation(
                    mapOf(null to mapOf(
                        NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(40)), JustSign))
                    ), null, null
                ),
                null,
                null
            ),
            parse(
                "maxspeed:forward" to "40",
                "maxspeed:type:forward" to "sign"
            )
        )
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(
                null,
                AllSpeedInformation(
                    mapOf(null to mapOf(
                        NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(40)), JustSign))
                    ), null, null
                ),
                null
            ),
            parse(
                "maxspeed:backward" to "40",
                "maxspeed:type:backward" to "sign"
            )
        )
    }

    /* ------------------------------------ both directions ------------------------------------- */

    @Test fun `same explicit speed in both directions`() {
        assertEquals(
            noConditionsOrVehicles(
                MaxspeedAndType(MaxSpeedSign(Kmh(40)), null),
                MaxspeedAndType(MaxSpeedSign(Kmh(40)), null),
                null
            ),
            parse(
                "maxspeed:forward" to "40",
                "maxspeed:backward" to "40"
            )
        )
        assertEquals(
            noConditionsOrVehicles(
                MaxspeedAndType(MaxSpeedSign(Mph(40)), null),
                MaxspeedAndType(MaxSpeedSign(Mph(40)), null),
                null
            ),
            parse(
                "maxspeed:forward" to "40 mph",
                "maxspeed:backward" to "40 mph"
            )
        )
    }

    @Test fun `different explicit speed in both directions`() {
        assertEquals(
            noConditionsOrVehicles(
                MaxspeedAndType(MaxSpeedSign(Kmh(40)), null),
                MaxspeedAndType(MaxSpeedSign(Kmh(50)), null),
                null
            ),
            parse(
                "maxspeed:forward" to "40",
                "maxspeed:backward" to "50"
            )
        )
        assertEquals(
            noConditionsOrVehicles(
                MaxspeedAndType(MaxSpeedSign(Mph(40)), null),
                MaxspeedAndType(MaxSpeedSign(Mph(50)), null),
                null
            ),
            parse(
                "maxspeed:forward" to "40 mph",
                "maxspeed:backward" to "50 mph"
            )
        )
    }

    @Test fun `different speed and type in each direction`() {
        assertEquals(
            noConditionsOrVehicles(
                MaxspeedAndType(MaxSpeedSign(Kmh(40)), JustSign),
                MaxspeedAndType(MaxSpeedSign(Kmh(60)), ImplicitMaxSpeed("DE", URBAN, null)),
                null
            ),
            parseDE(
                "maxspeed:forward" to "40",
                "maxspeed:type:forward" to "sign",
                "maxspeed:backward" to "60",
                "maxspeed:type:backward" to "DE:urban"
            )
        )
        assertEquals(
            noConditionsOrVehicles(
                MaxspeedAndType(MaxSpeedSign(Kmh(60)), ImplicitMaxSpeed("DE", URBAN, null)),
                MaxspeedAndType(MaxSpeedSign(Kmh(40)), JustSign),
                null
            ),
            parseDE(
                "maxspeed:forward" to "60",
                "maxspeed:type:forward" to "DE:urban",
                "maxspeed:backward" to "40",
                "maxspeed:type:backward" to "sign"
            )
        )
        assertEquals(
            noConditionsOrVehicles(
                MaxspeedAndType(MaxSpeedSign(Mph(40)), JustSign),
                MaxspeedAndType(MaxSpeedSign(Mph(60)), ImplicitMaxSpeed("GB", NSL_SINGLE, null)),
                null
            ),
            parseGB(
                "maxspeed:forward" to "40 mph",
                "maxspeed:type:forward" to "sign",
                "maxspeed:backward" to "60 mph",
                "maxspeed:type:backward" to "GB:nsl_single"
            )
        )
        assertEquals(
            noConditionsOrVehicles(
                MaxspeedAndType(MaxSpeedSign(Mph(60)), ImplicitMaxSpeed("GB", NSL_SINGLE, null)),
                MaxspeedAndType(MaxSpeedSign(Mph(40)), JustSign),
                null
            ),
            parseGB(
                "maxspeed:forward" to "60 mph",
                "maxspeed:type:forward" to "GB:nsl_single",
                "maxspeed:backward" to "40 mph",
                "maxspeed:type:backward" to "sign"
            )
        )
    }

    /* ------------------------------------------------------------------------------------------ */
    /* -------------------------------- conditional speeds -------------------------------------- */
    /* ------------------------------------------------------------------------------------------ */

    /* ------------------------------------------ invalid --------------------------------------- */

    @Test fun `invalid conditional format`() {
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(50)), null)),
                null, null, null
            ),
            parse(
                "maxspeed" to "50",
                "maxspeed:conditional" to "40 when wet"
            )
        )
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(50)), null)),
                null, null, null
            ),
            parse(
                "maxspeed" to "50",
                "maxspeed:conditional" to "wet=40"
            )
        )
    }

    @Test fun `invalid speed in condition`() {
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(50)), null)),
                null, null, null
            ),
            parse(
                "maxspeed" to "50",
                "maxspeed:conditional" to "40 knots @ wet"
            )
        )
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(50)), null)),
                null, null, null
            ),
            parse(
                "maxspeed" to "50",
                "maxspeed:conditional" to "unknown @ wet"
            )
        )
    }

    @Test fun `invalid condition`() {
        assertEquals(
            null,
            parse("maxspeed:conditional" to "40 @ fixme")
        )
        assertEquals(
            null,
            parse("maxspeed:conditional" to "50")
        )
        assertEquals(
            null,
            parse("maxspeed:conditional" to "40 @ weight")
        )
    }

    @Test fun `invalid condition with valid condition`() {
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(Wet to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 @ wet; 50 @ other")
        )
    }

    @Test fun `clashing conditions in one direction with non-direction`() {
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(
                AllSpeedInformation(
                    mapOf(null to mapOf(Wet to MaxspeedAndType(Invalid, Invalid))),
                    null, null
                ),
                AllSpeedInformation(
                    mapOf(null to mapOf(Wet to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null))),
                    null, null
                ),
                null
            ),
            parse(
                "maxspeed:conditional" to "40 @ wet",
                "maxspeed:forward:conditional" to "30 @ wet"
            )
        )
    }

    // TODO: valid and invalid times, well, leave most of that to the opening hours parser

    /* ------------------------------------- valid conditional ---------------------------------- */

    @Test fun `basic conditional maxspeed`() {
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(Wet to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 @ (wet)")
        )
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(Wet to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 @ wet")
        )
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(Wet to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40@wet")
        )
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(Snow to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 @ (snow)")
        )
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(Snow to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 @ snow")
        )
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(Winter to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 @ (winter)")
        )
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(Winter to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 @ winter")
        )
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(Wet to MaxspeedAndType(MaxSpeedSign(Mph(40)), null)),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 mph @ wet")
        )
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(Snow to MaxspeedAndType(MaxSpeedSign(Mph(40)), null)),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 mph @ snow")
        )
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(Winter to MaxspeedAndType(MaxSpeedSign(Mph(40)), null)),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 mph @ winter")
        )
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(Flashing to MaxspeedAndType(MaxSpeedSign(Mph(40)), null)),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 mph @ flashing")
        )
    }

    @Test fun `parse flashing conditions`() {
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(Flashing to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 @ flashing")
        )
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(Flashing to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 @ when flashing")
        )
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(Flashing to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 @ flashing light")
        )
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(Flashing to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 @ 'flashing'")
        )
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(Flashing to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 @ When Lights Flashing")
        )
    }

    @Test fun `parse children_present conditions`() {
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(ChildrenPresent to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 @ children_present")
        )
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(ChildrenPresent to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 @ children present")
        )
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(ChildrenPresent to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 @ when_children_present")
        )
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(ChildrenPresent to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 @ \"when children are present\"")
        )
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(ChildrenPresent to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 @ Children Present")
        )
    }

    @Test fun `parse night conditions`() {
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(Night to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 @ sunset-sunrise")
        )
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(Night to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 @ dusk-dawn")
        )
    }

    @Test fun `parse other conditional tags as conditions`() {
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(Night to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)),
                null, null, null
            ),
            parse("maxspeed:night" to "40")
        )
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(Winter to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)),
                null, null, null
            ),
            parse("maxspeed:seasonal:winter" to "40")
        )
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(Wet to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)),
                null, null, null
            ),
            parse("maxspeed:wet" to "40")
        )
    }

    @Test fun `parse weight conditions`() {
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(
                    WeightAndComparison(MetricTons(3.5), MORE_THAN) to
                        MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)
                ),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 @ (weight>3.5)")
        )
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(
                    WeightAndComparison(MetricTons(3.5), MORE_THAN) to
                        MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)
                ),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 @ weight>3.5")
        )
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(
                    WeightAndComparison(MetricTons(5.0), LESS_THAN) to
                        MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)
                ),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 @ weight<5")
        )
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(
                    WeightAndComparison(MetricTons(7.5), MORE_THAN_OR_EQUAL) to
                        MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)
                ),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 @ weight>=7.5")
        )
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(
                    WeightAndComparison(MetricTons(10.0), LESS_THAN_OR_EQUAL) to
                        MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)
                ),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 @ weight<=10")
        )
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(
                    WeightAndComparison(ShortTons(3.5), MORE_THAN) to
                        MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)
                ),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 @ weight>3.5st")
        )
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(
                    WeightAndComparison(ShortTons(3.5), MORE_THAN) to
                        MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)
                ),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 @ weight>3.5 st")
        )
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(
                    WeightAndComparison(ImperialPounds(3500), MORE_THAN) to
                        MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)
                ),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 @ weight>3500lbs")
        )
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(
                    WeightAndComparison(ImperialPounds(3500), MORE_THAN) to
                        MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)
                ),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 @ weight>3500 lbs")
        )
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(
                    WeightAndComparison(Kilograms(200), MORE_THAN) to
                        MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)
                ),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 @ weight>200kg")
        )
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(
                    WeightAndComparison(Kilograms(200), MORE_THAN) to
                        MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)
                ),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 @ weight>200 kg")
        )
    }

    @Test fun `parse weight conditions with spaces around comparator`() {
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(
                    WeightAndComparison(MetricTons(3.5), MORE_THAN) to
                        MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)
                ),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 @ weight > 3.5")
        )
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(
                    WeightAndComparison(MetricTons(3.5), MORE_THAN) to
                        MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)
                ),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 @ weight> 3.5")
        )
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(
                    WeightAndComparison(MetricTons(3.5), MORE_THAN) to
                        MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)
                ),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 @ weight >3.5")
        )
    }

    @Test fun `conditional maxspeed with multiple conditions`() {
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(
                    Wet to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null),
                    Snow to MaxspeedAndType(MaxSpeedSign(Kmh(30)), null)
                ),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 @ (wet); 30 @ (snow)")
        )
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(
                    Wet to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null),
                    Snow to MaxspeedAndType(MaxSpeedSign(Kmh(30)), null)
                ),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 @ wet; 30 @ snow")
        )
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(
                    WeightAndComparison(MetricTons(3.5), MORE_THAN) to
                        MaxspeedAndType(MaxSpeedSign(Kmh(40)), null),
                    WeightAndComparison(MetricTons(10.0), MORE_THAN) to
                        MaxspeedAndType(MaxSpeedSign(Kmh(20)), null)
                ),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 @ (weight>3.5); 20 @ weight>10")
        )
        assertEquals(
            maxspeedNoVehiclesBothDirections(
                mapOf(
                    WeightAndComparison(MetricTons(3.5), MORE_THAN) to
                        MaxspeedAndType(MaxSpeedSign(Kmh(40)), null),
                    WeightAndComparison(MetricTons(10.0), MORE_THAN) to
                        MaxspeedAndType(MaxSpeedSign(Kmh(20)), null),
                    Wet to MaxspeedAndType(MaxSpeedSign(Kmh(50)), null),
                    Winter to MaxspeedAndType(MaxSpeedSign(Kmh(30)), null),
                ),
                null, null, null
            ),
            parse("maxspeed:conditional" to "40 @ (weight>3.5); 20 @ weight>10; 50 @ wet; 30 @ winter")
        )
    }

    /* ---------------------------- valid conditional with directions --------------------------- */

    @Test fun `conditional in only one direction`() {
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(
                AllSpeedInformation(
                    mapOf(null to mapOf(Wet to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null))),
                    null, null
                ),
                null,
                null
            ),
            parse("maxspeed:forward:conditional" to "40 @ wet")
        )
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(
                null,
                AllSpeedInformation(
                    mapOf(
                        null to mapOf(WeightAndComparison(MetricTons(3.5), LESS_THAN) to
                        MaxspeedAndType(MaxSpeedSign(Kmh(30)), null))
                    ),
                    null, null
                ),
                null
            ),
            parse("maxspeed:backward:conditional" to "30 @ weight < 3.5")
        )
    }

    @Test fun `conditional in each direction`() {
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(
                AllSpeedInformation(
                    mapOf(null to mapOf(Wet to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null))),
                    null, null
                ),
                AllSpeedInformation(
                    mapOf(null to mapOf(Wet to MaxspeedAndType(MaxSpeedSign(Kmh(30)), null))),
                    null, null
                ),
                null
            ),
            parse(
                "maxspeed:forward:conditional" to "40 @ wet",
                "maxspeed:backward:conditional" to "30 @ wet"
            )
        )
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(
                AllSpeedInformation(
                    mapOf(null to mapOf(Wet to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null))),
                    null, null
                ),
                AllSpeedInformation(
                    mapOf(null to mapOf(WeightAndComparison(MetricTons(3.5), LESS_THAN) to MaxspeedAndType(MaxSpeedSign(
                        Kmh(30)
                    ), null))),
                    null, null
                ),
                null
            ),
            parse(
                "maxspeed:forward:conditional" to "40 @ wet",
                "maxspeed:backward:conditional" to "30 @ weight < 3.5"
            )
        )
    }

    @Test fun `main speed and conditional by directions`() {
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(
                AllSpeedInformation(
                    mapOf(null to mapOf(
                        NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(80)), null),
                        Wet to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null))
                    ),
                    null, null
                ),
                AllSpeedInformation(
                    mapOf(null to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(80)), null))),
                    null, null
                ),
                null
            ),
            parse(
                "maxspeed" to "80",
                "maxspeed:forward:conditional" to "40 @ wet"
            )
        )
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(
                AllSpeedInformation(
                    mapOf(null to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(80)), null))),
                    null, null
                ),
                AllSpeedInformation(
                    mapOf(null to mapOf(
                        NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(80)), null),
                        WeightAndComparison(MetricTons(3.5), LESS_THAN) to
                            MaxspeedAndType(MaxSpeedSign(Kmh(30)), null))
                    ),
                    null, null
                ),
                null
            ),
            parse(
                "maxspeed" to "80",
                "maxspeed:backward:conditional" to "30 @ weight < 3.5")
        )
    }

    @Test fun `non-clashing conditional for both directions and conditional in each direction`() {
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(
                AllSpeedInformation(
                    mapOf(null to
                        mapOf(
                            WeightAndComparison(MetricTons(10.0), MORE_THAN) to
                                MaxspeedAndType(MaxSpeedSign(Kmh(50)), null),
                            Wet to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)
                        )
                    ),
                    null, null
                ),
                AllSpeedInformation(
                    mapOf(null to
                        mapOf(
                            WeightAndComparison(MetricTons(10.0), MORE_THAN) to
                                MaxspeedAndType(MaxSpeedSign(Kmh(50)), null),
                            Wet to MaxspeedAndType(MaxSpeedSign(Kmh(30)), null)
                        )
                    ),
                    null, null
                ),
                null
            ),
            parse(
                "maxspeed:conditional" to "50 @ weight>10",
                "maxspeed:forward:conditional" to "40 @ wet",
                "maxspeed:backward:conditional" to "30 @ wet"
            )
        )
    }

    // TODO: conditional with times

    /* ------------------------------------------------------------------------------------------ */
    /* ------------------------------------- vehicles ------------------------------------------- */
    /* ------------------------------------------------------------------------------------------ */

    /* ------------------------------------------ invalid --------------------------------------- */

    @Test fun `unknown vehicle type`() {
        assertEquals(
            null,
            parse("maxspeed:aeroplane" to "50")
        )
    }

    @Test fun `clash of main vehicle speed tag with directional speed tag`() {
        assertEquals(
            maxspeedBothDirections(
                mapOf("hgv" to mapOf(NoCondition to MaxspeedAndType(Invalid, Invalid))),
                null, null, null
            ),
            parse(
                "maxspeed:hgv" to "50",
                "maxspeed:hgv:forward" to "60"
            )
        )
        assertEquals(
            maxspeedBothDirections(
                mapOf("hgv" to mapOf(NoCondition to MaxspeedAndType(Invalid, Invalid))),
                null, null, null
            ),
            parse(
                "maxspeed:hgv" to "50",
                "maxspeed:hgv:backward" to "60"
            )
        )
        assertEquals(
            maxspeedBothDirections(
                mapOf("hgv" to mapOf(NoCondition to MaxspeedAndType(Invalid, Invalid))),
                null, null, null
            ),
            parse(
                "maxspeed:hgv" to "50",
                "maxspeed:hgv:forward" to "60",
                "maxspeed:hgv:backward" to "70"
            )
        )
    }

    /* ------------------------------------------ valid ----------------------------------------- */

    @Test fun `vehicle with maxspeed`() {
        assertEquals(
            maxspeedBothDirections(
                mapOf("hgv" to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(50)), null))),
                null, null, null
            ),
            parse("maxspeed:hgv" to "50")
        )
        assertEquals(
            maxspeedBothDirections(
                mapOf("coach" to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(50)), null))),
                null, null, null
            ),
            parse("maxspeed:coach" to "50")
        )
        assertEquals(
            maxspeedBothDirections(
                mapOf("bus" to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(50)), null))),
                null, null, null
            ),
            parse("maxspeed:bus" to "50")
        )
    }

    @Test fun `vehicle with speed and type`() {
        assertEquals(
            maxspeedBothDirections(
                mapOf("hgv" to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(50)), JustSign))),
                null, null, null
            ),
            parse(
                "maxspeed:hgv" to "50",
                "maxspeed:type:hgv" to "sign"
            )
        )
        assertEquals(
            maxspeedBothDirections(
                mapOf("coach" to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(50)), JustSign))),
                null, null, null
            ),
            parse(
                "maxspeed:coach" to "50",
                "maxspeed:type:coach" to "sign"
            )
        )
        assertEquals(
            maxspeedBothDirections(
                mapOf("bus" to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(50)), JustSign))),
                null, null, null
            ),
            parse(
                "maxspeed:bus" to "50",
                "maxspeed:type:bus" to "sign"
            )
        )
    }

    @Test fun `multiple vehicles with maxspeed`() {
        assertEquals(
            maxspeedBothDirections(
                mapOf(
                    null to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(100)), null)),
                    "hgv" to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(90)), null)),
                    "coach" to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(80)), null)),
                    "trailer" to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(70)), null))
                ),
                null, null, null
            ),
            parse(
                "maxspeed" to "100",
                "maxspeed:hgv" to "90",
                "maxspeed:coach" to "80",
                "maxspeed:trailer" to "70"
            )
        )
    }

    /* ---------------------------------- vehicle with direction -------------------------------- */

    @Test fun `vehicle with maxspeed in one direction`() {
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(
                AllSpeedInformation(
                    mapOf("hgv" to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(50)), null))),
                    null, null
                ),
                null,
                null
            ),
            parse("maxspeed:hgv:forward" to "50")
        )
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(
                null,
                AllSpeedInformation(
                    mapOf("hgv" to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(50)), null))),
                    null, null
                ),
                null
            ),
            parse("maxspeed:hgv:backward" to "50")
        )
    }

    @Test fun `vehicle with different maxspeed in each direction`() {
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(
                AllSpeedInformation(
                    mapOf("hgv" to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(50)), null))),
                    null, null
                ),
                AllSpeedInformation(
                    mapOf("hgv" to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(60)), null))),
                    null, null
                ),
                null
            ),
            parse(
                "maxspeed:hgv:forward" to "50",
                "maxspeed:hgv:backward" to "60"
            )
        )
    }

    /* ---------------------------------- vehicle with condition -------------------------------- */

    @Test fun `vehicle with conditional maxspeed`() {
        assertEquals(
            maxspeedBothDirections(
                mapOf("hgv" to mapOf(Wet to MaxspeedAndType(MaxSpeedSign(Kmh(50)), null))),
                null, null, null
            ),
            parse("maxspeed:hgv:conditional" to "50 @ wet")
        )
        assertEquals(
            maxspeedBothDirections(
                mapOf("hgv" to mapOf(Wet to MaxspeedAndType(MaxSpeedSign(Kmh(50)), null))),
                null, null, null
            ),
            parse("maxspeed:hgv:conditional" to "50 @ wet")
        )
    }

    @Test fun `vehicle with conditional maxspeed in one direction`() {
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(
                AllSpeedInformation(
                    mapOf("hgv" to mapOf(Wet to MaxspeedAndType(MaxSpeedSign(Kmh(50)), null))),
                    null, null
                ),
                null,
                null
            ),
            parse("maxspeed:hgv:forward:conditional" to "50 @ wet")
        )
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(
                null,
                AllSpeedInformation(
                    mapOf("hgv" to mapOf(Wet to MaxspeedAndType(MaxSpeedSign(Kmh(50)), null))),
                    null, null
                ),
                null
            ),
            parse("maxspeed:hgv:backward:conditional" to "50 @ wet")
        )
    }

    @Test fun `vehicle with conditional maxspeed in both directions`() {
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(
                AllSpeedInformation(
                    mapOf("hgv" to mapOf(Wet to MaxspeedAndType(MaxSpeedSign(Kmh(50)), null))),
                    null, null
                ),
                AllSpeedInformation(
                    mapOf("hgv" to mapOf(Wet to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null))),
                    null, null
                ),
                null
            ),
            parse(
                "maxspeed:hgv:forward:conditional" to "50 @ wet",
                "maxspeed:hgv:backward:conditional" to "40 @ wet"
            )
        )
    }

    @Test fun `vehicle with conditional main maxspeed and conditional in directions`() {
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(
                AllSpeedInformation(
                    mapOf("hgv" to
                        mapOf(
                            Snow to MaxspeedAndType(MaxSpeedSign(Kmh(50)), null),
                            Wet to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)
                        )
                    ),
                    null, null
                ),
                AllSpeedInformation(
                    mapOf("hgv" to mapOf(Snow to MaxspeedAndType(MaxSpeedSign(Kmh(50)), null))),
                    null, null
                ),
                null
            ),
            parse(
                "maxspeed:hgv:conditional" to "50 @ snow",
                "maxspeed:hgv:forward:conditional" to "40 @ wet"
            )
        )
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(
                AllSpeedInformation(
                    mapOf("hgv" to
                        mapOf(
                            Snow to MaxspeedAndType(MaxSpeedSign(Kmh(50)), null),
                            Wet to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)
                        )
                    ),
                    null, null
                ),
                AllSpeedInformation(
                    mapOf("hgv" to
                        mapOf(
                            Snow to MaxspeedAndType(MaxSpeedSign(Kmh(50)), null),
                            Wet to MaxspeedAndType(MaxSpeedSign(Kmh(20)), null)
                        )
                    ),
                    null, null
                ),
                null
            ),
            parse(
                "maxspeed:hgv:conditional" to "50 @ snow",
                "maxspeed:hgv:forward:conditional" to "40 @ wet",
                "maxspeed:hgv:backward:conditional" to "20 @ wet"
            )
        )
    }

    /* ------------------------------------------------------------------------------------------ */
    /* ---------------------------------- bit of everything ------------------------------------- */
    /* ------------------------------------------------------------------------------------------ */

    @Test fun `bit of everything`() {
        assertEquals(
            ForwardAndBackwardAllSpeedInformation(
                AllSpeedInformation(
                    mapOf(
                        null to mapOf(
                            NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(100)), ImplicitMaxSpeed("DE", MOTORWAY, YES))
                        ),
                        "hgv" to mapOf(
                            NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(40)), JustSign)
                        ),
                        "coach" to mapOf(
                            NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(30)), JustSign)
                        )
                    ),
                    AdvisorySpeedSign(Kmh(90)),
                    true
                ),
                AllSpeedInformation(
                    mapOf(
                        null to mapOf(
                            NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(100)), ImplicitMaxSpeed("DE", MOTORWAY, YES)),
                            Wet to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)
                        ),
                        "hgv" to mapOf(
                            NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(40)), JustSign)
                        ),
                        "coach" to mapOf(
                            NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(60)), JustSign)
                        ),
                        "trailer" to mapOf(
                            WeightAndComparison(MetricTons(10.0), MORE_THAN_OR_EQUAL) to
                                MaxspeedAndType(MaxSpeedSign(Kmh(30)), null)
                        )
                    ),
                    AdvisorySpeedSign(Kmh(80)),
                    false
                ),
                null
            ),
            parseDE(
                "maxspeed" to "100",
                "zone:maxspeed" to "DE:motorway",
                "maxspeed:backward:conditional" to "40 @ wet",
                "maxspeed:hgv" to "40",
                "maxspeed:type:hgv" to "sign",
                "maxspeed:coach:forward" to "30",
                "source:maxspeed:coach:forward" to "sign",
                "maxspeed:coach:backward" to "60",
                "maxspeed:type:coach:backward" to "sign",
                "maxspeed:trailer:backward:conditional" to "30 @ weight >= 10",
                "maxspeed:advisory:forward" to "90",
                "maxspeed:advisory:backward" to "80",
                "maxspeed:variable:forward" to "obstruction",
                "maxspeed:variable:backward" to "no",
                "lit" to "yes"
            )
        )
    }
}

private fun parse(vararg tags: Pair<String, String>): ForwardAndBackwardAllSpeedInformation? {
    val countryInfo: CountryInfo = mock()
    return parseWithCountryInfo(tags, countryInfo)
}

private fun parseWithCountryInfo(tags: Array<out Pair<String, String>>, countryInfo: CountryInfo): ForwardAndBackwardAllSpeedInformation? {
    return createForwardAndBackwardAllSpeedInformation(mapOf(*tags), countryInfo)
}

private fun parseDE(vararg tags: Pair<String, String>): ForwardAndBackwardAllSpeedInformation? {
    val countryInfoDE: CountryInfo = mock()
    on(countryInfoDE.countryCode).thenReturn("DE")
    on(countryInfoDE.speedUnits).thenReturn(listOf(SpeedMeasurementUnit.KILOMETERS_PER_HOUR))
    return parseWithCountryInfo(tags, countryInfoDE)
}

private fun parseGB(vararg tags: Pair<String, String>): ForwardAndBackwardAllSpeedInformation? {
    val countryInfoGB: CountryInfo = mock()
    on(countryInfoGB.countryCode).thenReturn("GB")
    on(countryInfoGB.speedUnits).thenReturn(listOf(SpeedMeasurementUnit.MILES_PER_HOUR))
    return parseWithCountryInfo(tags, countryInfoGB)
}

private fun parseRU(vararg tags: Pair<String, String>): ForwardAndBackwardAllSpeedInformation? {
    val countryInfoRU: CountryInfo = mock()
    on(countryInfoRU.countryCode).thenReturn("RU")
    on(countryInfoRU.speedUnits).thenReturn(listOf(SpeedMeasurementUnit.KILOMETERS_PER_HOUR))
    return parseWithCountryInfo(tags, countryInfoRU)
}

private fun bareMaxspeedBothDirections(explicit: MaxSpeedAnswer?, type: MaxSpeedAnswer?, wholeRoadType: MaxSpeedAnswer?) =
    ForwardAndBackwardAllSpeedInformation(
        AllSpeedInformation(mapOf(null to mapOf(NoCondition to MaxspeedAndType(explicit, type))), null, null),
        AllSpeedInformation(mapOf(null to mapOf(NoCondition to MaxspeedAndType(explicit, type))), null, null),
        wholeRoadType
    )

private fun maxspeedBothDirections(
    vehicles: Map<String?, Map<Condition, MaxspeedAndType?>?>?,
    advisory: AdvisorySpeedSign?,
    variable: Boolean?,
    wholeRoadType: MaxSpeedAnswer?
) =
    ForwardAndBackwardAllSpeedInformation(
        AllSpeedInformation(vehicles, advisory, variable),
        AllSpeedInformation(vehicles, advisory, variable),
        wholeRoadType
    )

private fun maxspeedNoVehiclesBothDirections(
    conditions: Map<Condition, MaxspeedAndType?>?,
    advisory: AdvisorySpeedSign?,
    variable: Boolean?,
    wholeRoadType: MaxSpeedAnswer?
) =
    ForwardAndBackwardAllSpeedInformation(
        AllSpeedInformation(mapOf(null to conditions), advisory, variable),
        AllSpeedInformation(mapOf(null to conditions), advisory, variable),
        wholeRoadType
    )

private fun noConditionsOrVehicles(forward: MaxspeedAndType?, backward: MaxspeedAndType?, wholeRoadType: MaxSpeedAnswer?) =
    ForwardAndBackwardAllSpeedInformation(
        AllSpeedInformation(mapOf(null to mapOf(NoCondition to forward)), null, null),
        AllSpeedInformation(mapOf(null to mapOf(NoCondition to backward.takeIf { it != null })), null, null),
        wholeRoadType
    )
