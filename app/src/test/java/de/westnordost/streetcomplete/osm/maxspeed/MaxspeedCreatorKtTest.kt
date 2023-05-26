package de.westnordost.streetcomplete.osm.maxspeed

import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryChange
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryDelete
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryModify
import de.westnordost.streetcomplete.osm.lit.LitStatus.*
import de.westnordost.streetcomplete.osm.maxspeed.RoadType.*
import de.westnordost.streetcomplete.quests.max_speed.Kmh
import de.westnordost.streetcomplete.quests.max_speed.Mph
import org.assertj.core.api.Assertions
import org.junit.Test

class MaxspeedCreatorKtTest {
    @Test fun `apply nothing applies nothing`() {
        verifyAnswer(
            mapOf(),
            maxspeedBothDirections(null, null),
            arrayOf()
        )
    }

    /* ------------------------------------------------------------------------------------------ */
    /* ------------------------------ same in both directions ----------------------------------- */
    /* ------------------------------------------------------------------------------------------ */

    /* ---------------------------------- apply plain values ------------------------------------ */

    @Test fun `apply plain maxspeed`() {
        verifyAnswer(
            mapOf(),
            maxspeedBothDirections(MaxSpeedSign(Kmh(20)), null),
            arrayOf(StringMapEntryAdd("maxspeed", "20"))
        )
        verifyAnswer(
            mapOf(),
            maxspeedBothDirections(MaxSpeedSign(Mph(20)), null),
            arrayOf(StringMapEntryAdd("maxspeed", "20 mph"))
        )
    }

    @Test fun `apply common non-numeric maxspeed`() {
        verifyAnswer(
            mapOf(),
            maxspeedBothDirections(WalkMaxSpeed, null),
            arrayOf(StringMapEntryAdd("maxspeed", "walk"))
        )
        verifyAnswer(
            mapOf(),
            maxspeedBothDirections(MaxSpeedIsNone, null),
            arrayOf(StringMapEntryAdd("maxspeed", "none"))
        )
    }

    @Test fun `apply plain maxspeed type`() {
        verifyAnswer(
            mapOf(),
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            arrayOf(StringMapEntryAdd("maxspeed:type", "DE:urban"))
        )
        verifyAnswer(
            mapOf(),
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", RURAL, null)),
            arrayOf(StringMapEntryAdd("maxspeed:type", "DE:rural"))
        )
    }

    @Test fun `tag lit status when given`() {
        verifyAnswer(
            mapOf(),
            maxspeedBothDirections(null, ImplicitMaxSpeed("GB", NSL_SINGLE, NO)),
            arrayOf(
                StringMapEntryAdd("maxspeed:type", "GB:nsl_single"),
                StringMapEntryAdd("lit", "no")
            )
        )
        verifyAnswer(
            mapOf(),
            maxspeedBothDirections(null, ImplicitMaxSpeed("GB", NSL_RESTRICTED, YES)),
            arrayOf(
                StringMapEntryAdd("maxspeed:type", "GB:nsl_restricted"),
                StringMapEntryAdd("lit", "yes")
            )
        )
    }

    @Test fun `do not modify lit tag if not given`() {
        verifyAnswer(
            mapOf("lit" to "yes"),
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            arrayOf(StringMapEntryAdd("maxspeed:type", "DE:urban"))
        )
        verifyAnswer(
            mapOf("lit" to "unknown"),
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", RURAL, null)),
            arrayOf(StringMapEntryAdd("maxspeed:type", "DE:rural"))
        )
    }

    @Test fun `apply zone maxspeed`() {
        verifyAnswer(
            mapOf(),
            maxspeedBothDirections(null, MaxSpeedZone(Kmh(20), "DE", "zone20")),
            arrayOf(
                StringMapEntryAdd("maxspeed:type", "DE:zone20"),
                StringMapEntryAdd("maxspeed", "20")
            )
        )
        verifyAnswer(
            mapOf(),
            maxspeedBothDirections(null, MaxSpeedZone(Mph(20), "GB", "zone20")),
            arrayOf(
                StringMapEntryAdd("maxspeed:type", "GB:zone20"),
                StringMapEntryAdd("maxspeed", "20 mph")
            )
        )
    }

    @Test fun `apply signed maxspeed`() {
        verifyAnswer(
            mapOf(),
            maxspeedBothDirections(MaxSpeedSign(Kmh(20)), JustSign),
            arrayOf(
                StringMapEntryAdd("maxspeed", "20"),
                StringMapEntryAdd("maxspeed:type", "sign")
            )
        )
        verifyAnswer(
            mapOf(),
            maxspeedBothDirections(MaxSpeedSign(Mph(20)), JustSign),
            arrayOf(
                StringMapEntryAdd("maxspeed", "20 mph"),
                StringMapEntryAdd("maxspeed:type", "sign")
            )
        )
    }

    @Test fun `change to living street`() {
        verifyAnswer(
            mapOf("highway" to "residential"),
            maxspeedBothDirections(null, LivingStreet("DE")),
            arrayOf(
                StringMapEntryModify("highway", "residential", "living_street")
            )
        )
    }

    @Test fun `mark as school zone`() {
        verifyAnswer(
            mapOf(),
            maxspeedBothDirections(null, IsSchoolZone),
            arrayOf(
                StringMapEntryAdd("hazard", "school_zone")
            )
        )
    }

    /* ----------------------------- change 'maxspeed' to another type -------------------------- */

    @Test fun `change plain maxspeed`() {
        verifyAnswer(
            mapOf("maxspeed" to "40"),
            maxspeedBothDirections(MaxSpeedSign(Kmh(60)), null),
            arrayOf(StringMapEntryModify("maxspeed", "40", "60"))
        )
        verifyAnswer(
            mapOf("maxspeed" to "30 mph"),
            maxspeedBothDirections(MaxSpeedSign(Mph(40)), null),
            arrayOf(StringMapEntryModify("maxspeed", "30 mph", "40 mph"))
        )
    }

    /* ----------------------------------- change to implicit  ---------------------------------- */

    @Test fun `change maxspeed type from one implicit value to another`() {
        verifyAnswer(
            mapOf("maxspeed:type" to "DE:urban"),
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", RURAL, null)),
            arrayOf(StringMapEntryModify("maxspeed:type", "DE:urban", "DE:rural"))
        )
        verifyAnswer(
            mapOf("maxspeed:type" to "DE:motorway"),
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", RURAL, null)),
            arrayOf(StringMapEntryModify("maxspeed:type", "DE:motorway", "DE:rural"))
        )
    }

    @Test fun `change signed maxspeed to implicit maxspeed`() {
        verifyAnswer(
            mapOf(
                "maxspeed" to "40",
                "maxspeed:type" to "sign"
            ),
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            arrayOf(
                StringMapEntryDelete("maxspeed", "40"),
                StringMapEntryModify("maxspeed:type", "sign", "DE:urban")
            )
        )
    }

    @Test fun `change zone maxspeed to implicit maxspeed`() {
        verifyAnswer(
            mapOf("maxspeed:type" to "DE:zone30"),
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            arrayOf(
                StringMapEntryModify("maxspeed:type", "DE:zone30", "DE:urban")
            )
        )
        verifyAnswer(
            mapOf(
                "maxspeed:type" to "DE:zone30",
                "maxspeed" to "30"
            ),
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            arrayOf(
                StringMapEntryModify("maxspeed:type", "DE:zone30", "DE:urban"),
                StringMapEntryDelete("maxspeed", "30")
            )
        )
    }

    /* ----------------------------------- change to zone --------------------------------------- */

    @Test fun `change one zone maxspeed to another`() {
        verifyAnswer(
            mapOf("maxspeed:type" to "DE:zone30"),
            maxspeedBothDirections(null, MaxSpeedZone(Kmh(20), "DE", "zone20")),
            arrayOf(
                StringMapEntryModify("maxspeed:type", "DE:zone30", "DE:zone20"),
                StringMapEntryAdd("maxspeed", "20")
            )
        )
        verifyAnswer(
            mapOf("maxspeed:type" to "GB:zone20"),
            maxspeedBothDirections(null, MaxSpeedZone(Mph(30), "GB", "zone30")),
            arrayOf(
                StringMapEntryModify("maxspeed:type", "GB:zone20", "GB:zone30"),
                StringMapEntryAdd("maxspeed", "30 mph")
            )
        )
        verifyAnswer(
            mapOf(
                "maxspeed:type" to "DE:zone30",
                "maxspeed" to "30"
            ),
            maxspeedBothDirections(null, MaxSpeedZone(Kmh(20), "DE", "zone20")),
            arrayOf(
                StringMapEntryModify("maxspeed:type", "DE:zone30", "DE:zone20"),
                StringMapEntryModify("maxspeed", "30", "20")
            )
        )
        verifyAnswer(
            mapOf(
                "maxspeed:type" to "GB:zone20",
                "maxspeed" to "20 mph"
            ),
            maxspeedBothDirections(null, MaxSpeedZone(Mph(30), "GB", "zone30")),
            arrayOf(
                StringMapEntryModify("maxspeed:type", "GB:zone20", "GB:zone30"),
                StringMapEntryModify("maxspeed", "20 mph", "30 mph")
            )
        )
    }

    @Test fun `change explicit maxspeed to zone maxspeed`() {
        verifyAnswer(
            mapOf("maxspeed" to "40"),
            maxspeedBothDirections(null, MaxSpeedZone(Kmh(30), "DE", "zone30")),
            arrayOf(
                StringMapEntryAdd("maxspeed:type", "DE:zone30"),
                StringMapEntryModify("maxspeed", "40", "30")
            )
        )
        verifyAnswer(
            mapOf("maxspeed" to "30 mph"),
            maxspeedBothDirections(null, MaxSpeedZone(Mph(20), "GB", "zone20")),
            arrayOf(
                StringMapEntryAdd("maxspeed:type", "GB:zone20"),
                StringMapEntryModify("maxspeed", "30 mph", "20 mph")
            )
        )
    }

    @Test fun `change signed maxspeed to zone maxspeed`() {
        verifyAnswer(
            mapOf(
                "maxspeed" to "40",
                "maxspeed:type" to "sign"
            ),
            maxspeedBothDirections(null, MaxSpeedZone(Kmh(30), "DE", "zone30")),
            arrayOf(
                StringMapEntryModify("maxspeed", "40", "30"),
                StringMapEntryModify("maxspeed:type", "sign", "DE:zone30")
            )
        )
        verifyAnswer(
            mapOf(
                "maxspeed" to "30 mph",
                "maxspeed:type" to "sign"
            ),
            maxspeedBothDirections(null, MaxSpeedZone(Mph(20), "GB", "zone20")),
            arrayOf(
                StringMapEntryModify("maxspeed", "30 mph", "20 mph"),
                StringMapEntryModify("maxspeed:type", "sign", "GB:zone20")
            )
        )
    }

    @Test fun `change implicit maxspeed to zone maxspeed`() {
        verifyAnswer(
            mapOf("maxspeed:type" to "DE:urban"),
            maxspeedBothDirections(null, MaxSpeedZone(Kmh(30), "DE", "zone30")),
            arrayOf(
                StringMapEntryModify("maxspeed:type", "DE:urban", "DE:zone30"),
                StringMapEntryAdd("maxspeed", "30")
            )
        )
        verifyAnswer(
            mapOf("maxspeed:type" to "GB:nsl_restricted"),
            maxspeedBothDirections(null, MaxSpeedZone(Mph(20), "GB", "zone20")),
            arrayOf(
                StringMapEntryModify("maxspeed:type", "GB:nsl_restricted", "GB:zone20"),
                StringMapEntryAdd("maxspeed", "20 mph")
            )
        )
    }

    /* ----------------------------------- change to signed maxspeed ---------------------------- */

    @Test fun `change one signed maxspeed to another`() {
        verifyAnswer(
            mapOf(
                "maxspeed" to "60",
                "maxspeed:type" to "sign"
            ),
            maxspeedBothDirections(MaxSpeedSign(Kmh(40)), JustSign),
            arrayOf(
                StringMapEntryModify("maxspeed:type", "sign", "sign"),
                StringMapEntryModify("maxspeed", "60", "40")
            )
        )
        verifyAnswer(
            mapOf(
                "maxspeed" to "40 mph",
                "maxspeed:type" to "sign"
            ),
            maxspeedBothDirections(MaxSpeedSign(Mph(50)), JustSign),
            arrayOf(
                StringMapEntryModify("maxspeed:type", "sign", "sign"),
                StringMapEntryModify("maxspeed", "40 mph", "50 mph")
            )
        )
    }

    @Test fun `change implicit to signed maxspeed`() {
        verifyAnswer(
            mapOf("maxspeed:type" to "DE:urban"),
            maxspeedBothDirections(MaxSpeedSign(Kmh(20)), JustSign),
            arrayOf(
                StringMapEntryAdd("maxspeed", "20"),
                StringMapEntryModify("maxspeed:type", "DE:urban", "sign")
            )
        )
        verifyAnswer(
            mapOf("maxspeed:type" to "GB:nsl_single"),
            maxspeedBothDirections(MaxSpeedSign(Mph(50)), JustSign),
            arrayOf(
                StringMapEntryAdd("maxspeed", "50 mph"),
                StringMapEntryModify("maxspeed:type", "GB:nsl_single", "sign")
            )
        )
    }

    @Test fun `change zone to signed maxspeed`() {
        verifyAnswer(
            mapOf("maxspeed:type" to "DE:zone30"),
            maxspeedBothDirections(MaxSpeedSign(Kmh(20)), JustSign),
            arrayOf(
                StringMapEntryAdd("maxspeed", "20"),
                StringMapEntryModify("maxspeed:type", "DE:zone30", "sign")
            )
        )
        verifyAnswer(
            mapOf(
                "maxspeed:type" to "DE:zone30",
                "maxspeed" to "30"
            ),
            maxspeedBothDirections(MaxSpeedSign(Kmh(20)), JustSign),
            arrayOf(
                StringMapEntryModify("maxspeed", "30", "20"),
                StringMapEntryModify("maxspeed:type", "DE:zone30", "sign")
            )
        )
        verifyAnswer(
            mapOf("maxspeed:type" to "GB:zone20"),
            maxspeedBothDirections(MaxSpeedSign(Mph(30)), JustSign),
            arrayOf(
                StringMapEntryAdd("maxspeed", "30 mph"),
                StringMapEntryModify("maxspeed:type", "GB:zone20", "sign")
            )
        )
        verifyAnswer(
            mapOf(
                "maxspeed:type" to "GB:zone20",
                "maxspeed" to "20"
            ),
            maxspeedBothDirections(MaxSpeedSign(Mph(30)), JustSign),
            arrayOf(
                StringMapEntryModify("maxspeed", "20", "30 mph"),
                StringMapEntryModify("maxspeed:type", "GB:zone20", "sign")
            )
        )
    }

    /* ----------------------------------- change to living street ------------------------------ */

    /* Unless an explicit speed limit is provided, changing to a living street removes all maxspeed
    *  tagging because we are in the context of speed limits. So the user was shown the current
    *  speed limit/type and answered that in fact it is a living street, thereby saying that what
    *  was tagged before was wrong.
    *  If the user also provides an explicit maxspeed then we tag the type as "xx:living_street"
    *  to make it clear. */
    @Test fun `changing to living street removes any previous maxspeed and type tagging`() {
        verifyAnswer(
            mapOf(
                "highway" to "residential",
                "maxspeed" to "50",
                "source:maxspeed" to "DE:urban"
            ),
            maxspeedBothDirections(null, LivingStreet("DE")),
            arrayOf(
                StringMapEntryModify("highway", "residential", "living_street"),
                StringMapEntryDelete("maxspeed", "50"),
                StringMapEntryDelete("source:maxspeed", "DE:urban")
            )
        )
    }

    // This would not parse as a living street, so the user would not see that it was living street
    // speed limit before
    @Test fun `living street with other maxspeed tagging to living street type removes previous maxspeed and type tagging`() {
        verifyAnswer(
            mapOf(
                "highway" to "living_street",
                "maxspeed" to "50",
                "source:maxspeed" to "DE:urban"
            ),
            maxspeedBothDirections(null, LivingStreet("DE")),
            arrayOf(
                StringMapEntryDelete("maxspeed", "50"),
                StringMapEntryDelete("source:maxspeed", "DE:urban")
            )
        )
        verifyAnswer(
            mapOf(
                "highway" to "service",
                "living_street" to "yes",
                "maxspeed" to "50",
                "maxspeed:type" to "sign",
                "source:maxspeed" to "DE:urban"
            ),
            maxspeedBothDirections(null, LivingStreet("DE")),
            arrayOf(
                StringMapEntryDelete("maxspeed", "50"),
                StringMapEntryDelete("maxspeed:type", "sign"),
                StringMapEntryDelete("source:maxspeed", "DE:urban")
            )
        )
    }

    @Test fun `living street with explicit speed limit tags living_street as type`() {
        verifyAnswer(
            mapOf(
                "highway" to "living_street",
                "maxspeed" to "50"
            ),
            maxspeedBothDirections(WalkMaxSpeed, LivingStreet("DE")),
            arrayOf(
                StringMapEntryModify("maxspeed", "50", "walk"),
                StringMapEntryAdd("maxspeed:type", "DE:living_street")
            )
        )
    }

    @Test fun `mark school zone living street as living street speed limit`() {
        verifyAnswer(
            mapOf(
                "highway" to "living_street",
                "hazard" to "school_zone"
            ),
            maxspeedBothDirections(null, LivingStreet("DE")),
            arrayOf(
                StringMapEntryAdd("maxspeed:type", "DE:living_street")
            )
        )
    }

    /* ----------------------------------- change to school zone -------------------------------- */

    /* Unless an explicit speed limit is provided, changing to a  school zone removes all maxspeed
    *  tagging because we are in the context of speed limits. So the user was shown the current
    *  speed limit/type and answered that in fact it is a school zone thereby saying that what was
    *  tagged before was wrong.
    *  If the user also provides an explicit maxspeed then "hazard=school_zone" suffices for the
    *  type as there is no accepted type tag for school zones */
    @Test fun `changing to school zone removes any previous maxspeed and type tagging`() {
        verifyAnswer(
            mapOf(
                "maxspeed" to "50",
                "maxspeed:type" to "sign",
                "source:maxspeed" to "DE:urban"
            ),
            maxspeedBothDirections(null, IsSchoolZone),
            arrayOf(
                StringMapEntryAdd("hazard", "school_zone"),
                StringMapEntryDelete("maxspeed", "50"),
                StringMapEntryDelete("maxspeed:type", "sign"),
                StringMapEntryDelete("source:maxspeed", "DE:urban")
            )
        )
    }

    @Test fun `changing to school zone does not remove special source_maxspeed`() {
        verifyAnswer(
            mapOf(
                "source:maxspeed" to "25 unless otherwise signed"
            ),
            maxspeedBothDirections(null, IsSchoolZone),
            arrayOf(
                StringMapEntryAdd("hazard", "school_zone"),
            )
        )
    }

    @Test fun `tag school zone also with an explicit speed limit`() {
        verifyAnswer(
            mapOf("maxspeed" to "50"),
            maxspeedBothDirections(MaxSpeedSign(Kmh(20)), IsSchoolZone),
            arrayOf(
                StringMapEntryModify("maxspeed", "50", "20"),
                StringMapEntryAdd("hazard", "school_zone")
            )
        )
    }

    @Test fun `remove school_zone tag if user was shown that it was a school zone and selected something else`() {
        verifyAnswer(
            mapOf("hazard" to "school_zone"),
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            arrayOf(
                StringMapEntryDelete("hazard", "school_zone"),
                StringMapEntryAdd("maxspeed:type", "DE:urban")
            )
        )
        verifyAnswer(
            mapOf(
                "hazard" to "school_zone",
                "maxspeed" to "50"
            ),
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            arrayOf(
                StringMapEntryDelete("hazard", "school_zone"),
                StringMapEntryDelete("maxspeed", "50"),
                StringMapEntryAdd("maxspeed:type", "DE:urban")
            )
        )
    }

    /* ----------------------------------- different type tags  --------------------------------- */

    @Test fun `respect previously used type tag`() {
        verifyAnswer(
            mapOf("source:maxspeed" to "sign"),
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            arrayOf(StringMapEntryModify("source:maxspeed", "sign", "DE:urban"))
        )
        verifyAnswer(
            mapOf("zone:maxspeed" to "DE:rural"),
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            arrayOf(StringMapEntryModify("zone:maxspeed", "DE:rural", "DE:urban"))
        )
        verifyAnswer(
            mapOf("zone:traffic" to "DE:rural"),
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            arrayOf(StringMapEntryModify("zone:traffic", "DE:rural", "DE:urban"))
        )
    }

    @Test fun `respect previously used type tag when source_maxspeed is a special value`() {
        verifyAnswer(
            mapOf(
                "zone:maxspeed" to "DE:rural",
                "source:maxspeed" to "25 unless otherwise signed"
            ),
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            arrayOf(StringMapEntryModify("zone:maxspeed", "DE:rural", "DE:urban"))
        )
        verifyAnswer(
            mapOf(
                "zone:traffic" to "DE:rural",
                "source:maxspeed" to "25 unless otherwise signed"
            ),
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            arrayOf(StringMapEntryModify("zone:traffic", "DE:rural", "DE:urban"))
        )
    }

    @Test fun `do not use source_maxspeed if previous value was not a maxspeed type, but preserve special values`() {
        verifyAnswer(
            mapOf(
                "source:maxspeed" to "25 unless otherwise posted",
                "maxspeed" to "60"
            ),
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            arrayOf(
                StringMapEntryDelete("maxspeed", "60"),
                StringMapEntryAdd("maxspeed:type", "DE:urban")
            )
        )
    }

    @Test fun `remove source_maxspeed if it is actual source value`() {
        verifyAnswer(
            mapOf(
                "source:maxspeed" to "survey",
                "maxspeed" to "60"
            ),
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            arrayOf(
                StringMapEntryDelete("source:maxspeed", "survey"),
                StringMapEntryDelete("maxspeed", "60"),
                StringMapEntryAdd("maxspeed:type", "DE:urban")
            )
        )
    }

    @Test fun `do not use previously used type tag if changing to sign where sign is not valid for that type`() {
        verifyAnswer(
            mapOf("zone:maxspeed" to "DE:urban"),
            maxspeedBothDirections(MaxSpeedSign(Kmh(30)), JustSign),
            arrayOf(
                StringMapEntryDelete("zone:maxspeed", "DE:urban"),
                StringMapEntryAdd("maxspeed:type", "sign"),
                StringMapEntryAdd("maxspeed", "30")
            )
        )
        verifyAnswer(
            mapOf("zone:traffic" to "DE:urban"),
            maxspeedBothDirections(MaxSpeedSign(Kmh(30)), JustSign),
            arrayOf(
                StringMapEntryDelete("zone:traffic", "DE:urban"),
                StringMapEntryAdd("maxspeed:type", "sign"),
                StringMapEntryAdd("maxspeed", "30")
            )
        )
    }

    @Test fun `use maxspeed for type if it was used before`() {
        verifyAnswer(
            mapOf("maxspeed" to "RU:urban"),
            maxspeedBothDirections(null, ImplicitMaxSpeed("RU", RURAL, null)),
            arrayOf(
                StringMapEntryModify("maxspeed", "RU:urban", "RU:rural")
            )
        )
    }

    @Test fun `use maxspeed for type if it was used before, even if tagged as a zone that is signed`() {
        verifyAnswer(
            mapOf(
                "maxspeed" to "RU:zone30",
                "maxspeed:type" to "sign"
            ),
            maxspeedBothDirections(null, ImplicitMaxSpeed("RU", RURAL, null)),
            arrayOf(
                StringMapEntryModify("maxspeed", "RU:zone30", "RU:rural"),
                StringMapEntryDelete("maxspeed:type", "sign")
            )
        )
        verifyAnswer(
            mapOf(
                "maxspeed" to "RU:zone30",
                "maxspeed:type" to "sign"
            ),
            maxspeedBothDirections(MaxSpeedZone(Kmh(20), "RU", "zone20"), JustSign),
            arrayOf(
                StringMapEntryModify("maxspeed:type", "sign", "sign"),
                StringMapEntryModify("maxspeed", "RU:zone30", "RU:zone20"))
        )
        verifyAnswer(
            mapOf(
                "maxspeed" to "RU:zone30",
                "source:maxspeed" to "sign"
            ),
            maxspeedBothDirections(MaxSpeedZone(Kmh(20), "RU", "zone20"), JustSign),
            arrayOf(
                StringMapEntryModify("maxspeed", "RU:zone30", "RU:zone20"),
                StringMapEntryModify("source:maxspeed", "sign", "sign"),
            )
        )
    }

    @Test fun `do not keep using maxspeed key for type if adding both explicit value and type`() {
        verifyAnswer(
            mapOf("maxspeed" to "RU:urban"),
            maxspeedBothDirections(MaxSpeedSign(Kmh(50)), ImplicitMaxSpeed("RU", RURAL, null)),
            arrayOf(
                StringMapEntryModify("maxspeed", "RU:urban", "50"),
                StringMapEntryAdd("maxspeed:type", "RU:rural")
            )
        )
    }

    @Test fun `do not change type tags when changing speed if type is not changed`() {
        verifyAnswer(
            mapOf(
                "maxspeed" to "100",
                "source:maxspeed" to "DE:motorway"
            ),
            maxspeedBothDirections(MaxSpeedSign(Kmh(110)), null),
            arrayOf(
                StringMapEntryModify("maxspeed", "100", "110"),
                StringMapEntryModify("source:maxspeed", "DE:motorway", "DE:motorway")
            )
        )
    }

    @Test fun `always delete survey type values of source_maxspeed when something changes`() {
        verifyAnswer(
            mapOf(
                "maxspeed" to "100",
                "source:maxspeed" to "survey",
                "maxspeed:type" to "DE:motorway"
            ),
            maxspeedBothDirections(MaxSpeedSign(Kmh(110)), null),
            arrayOf(
                StringMapEntryModify("maxspeed", "100", "110"),
                StringMapEntryDelete("source:maxspeed", "survey"),
                StringMapEntryModify("maxspeed:type", "DE:motorway", "DE:motorway")
            )
        )
        verifyAnswer(
            mapOf(
                "maxspeed" to "100",
                "source:maxspeed" to "survey",
                "maxspeed:type" to "DE:motorway"
            ),
            maxspeedBothDirections(MaxSpeedSign(Kmh(110)), ImplicitMaxSpeed("DE", MOTORWAY, null)),
            arrayOf(
                StringMapEntryModify("maxspeed", "100", "110"),
                StringMapEntryDelete("source:maxspeed", "survey"),
                StringMapEntryModify("maxspeed:type", "DE:motorway", "DE:motorway")
            )
        )
    }

    @Test fun `apply answer to a mixture of type tags`() {
        verifyAnswer(
            mapOf(
                "maxspeed:type:forward" to "DE:urban",
                "source:maxspeed:backward" to "DE:urban"
            ),
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", RURAL, null)),
            arrayOf(
                StringMapEntryDelete("maxspeed:type:forward", "DE:urban"),
                StringMapEntryDelete("source:maxspeed:backward", "DE:urban"),
                StringMapEntryAdd("maxspeed:type", "DE:rural")
            )
        )
        verifyAnswer(
            mapOf(
                "zone:maxspeed:forward" to "DE:urban",
                "source:maxspeed:backward" to "DE:urban"
            ),
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", RURAL, null)),
            arrayOf(
                StringMapEntryDelete("zone:maxspeed:forward", "DE:urban"),
                StringMapEntryDelete("source:maxspeed:backward", "DE:urban"),
                StringMapEntryAdd("source:maxspeed", "DE:rural")
            )
        )
        verifyAnswer(
            mapOf(
                "zone:maxspeed:forward" to "DE:urban",
                "source:maxspeed:backward" to "DE:urban",
                "maxspeed:type:hgv" to "sign",
                "zone:traffic:bus" to "DE:urban"
            ),
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", RURAL, null)),
            arrayOf(
                StringMapEntryDelete("zone:maxspeed:forward", "DE:urban"),
                StringMapEntryDelete("source:maxspeed:backward", "DE:urban"),
                StringMapEntryDelete("maxspeed:type:hgv", "sign"),
                StringMapEntryDelete("zone:traffic:bus", "DE:urban"),
                StringMapEntryAdd("maxspeed:type", "DE:rural")
            )
        )
    }

    @Test fun `apply sign answer to a mixture of type tags where sign is not valid`() {
        verifyAnswer(
            mapOf(
                "zone:maxspeed:forward" to "DE:urban",
                "zone:traffic:backward" to "DE:urban"
            ),
            maxspeedBothDirections(MaxSpeedSign(Kmh(50)), JustSign),
            arrayOf(
                StringMapEntryDelete("zone:maxspeed:forward", "DE:urban"),
                StringMapEntryDelete("zone:traffic:backward", "DE:urban"),
                StringMapEntryAdd("maxspeed:type", "sign"),
                StringMapEntryAdd("maxspeed", "50")
            )
        )
    }

    /* ----------------------------------- clean old tagging ------------------------------------ */

    @Test fun `clean up previous maxspeed tagging when changing speed`() {
        verifyAnswer(
            mapOf(
                "maxspeed" to "50",
                "maxspeed:hgv" to "40",
                "maxspeed:conditional" to "20 @ (10:00-11:00)",
                "maxspeed:trailer:conditional" to "20 @ (10:00-11:00)",
                "maxspeed:goods:backward" to "25",
                "maxspeed:hazmat:lanes" to "10||",
                "maxspeed:taxi:forward:lanes:conditional" to "70|| @ (10:00-11:00)"
            ),
            maxspeedBothDirections(MaxSpeedSign(Kmh(30)), null),
            arrayOf(
                StringMapEntryModify("maxspeed", "50", "30"),
                StringMapEntryDelete("maxspeed:hgv", "40"),
                StringMapEntryDelete("maxspeed:conditional", "20 @ (10:00-11:00)"),
                StringMapEntryDelete("maxspeed:trailer:conditional", "20 @ (10:00-11:00)"),
                StringMapEntryDelete("maxspeed:goods:backward", "25"),
                StringMapEntryDelete("maxspeed:hazmat:lanes", "10||"),
                StringMapEntryDelete("maxspeed:taxi:forward:lanes:conditional", "70|| @ (10:00-11:00)")
            )
        )
    }

    @Test fun `clean up previous maxspeed tagging when removing speed`() {
        verifyAnswer(
            mapOf(
                "maxspeed" to "100",
                "maxspeed:caravan" to "80",
                "maxspeed:conditional" to "90 @ (10:00-11:00)",
                "maxspeed:agricultural:conditional" to "20 @ (10:00-11:00)",
                "maxspeed:bdouble:backward" to "75",
                "maxspeed:hgv_articulated:lanes" to "60||",
                "maxspeed:coach:forward:lanes:conditional" to "50|| @ (10:00-11:00)"
            ),
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            arrayOf(
                StringMapEntryAdd("maxspeed:type", "DE:urban"),
                StringMapEntryDelete("maxspeed", "100"),
                StringMapEntryDelete("maxspeed:caravan", "80"),
                StringMapEntryDelete("maxspeed:conditional", "90 @ (10:00-11:00)"),
                StringMapEntryDelete("maxspeed:agricultural:conditional", "20 @ (10:00-11:00)"),
                StringMapEntryDelete("maxspeed:bdouble:backward", "75"),
                StringMapEntryDelete("maxspeed:hgv_articulated:lanes", "60||"),
                StringMapEntryDelete("maxspeed:coach:forward:lanes:conditional", "50|| @ (10:00-11:00)")
            )
        )
    }

    @Test fun `clean up previous type tagging when changing type`() {
        verifyAnswer(
            mapOf(
                "maxspeed:type" to "DE:urban",
                "zone:traffic" to "DE:urban",
                "zone:maxspeed" to "DE:urban"
            ),
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", RURAL, null)),
            arrayOf(
                StringMapEntryModify("maxspeed:type", "DE:urban", "DE:rural"),
                StringMapEntryDelete("zone:traffic", "DE:urban"),
                StringMapEntryDelete("zone:maxspeed", "DE:urban"),
            )
        )
        verifyAnswer(
            mapOf(
                "source:maxspeed" to "DE:motorway",
                "maxspeed:type" to "DE:urban",
                "zone:traffic" to "DE:rural",
                "zone:maxspeed" to "DE:30"
            ),
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", RURAL, null)),
            arrayOf(
                StringMapEntryModify("maxspeed:type", "DE:urban", "DE:rural"),
                StringMapEntryDelete("source:maxspeed", "DE:motorway"),
                StringMapEntryDelete("zone:traffic", "DE:rural"),
                StringMapEntryDelete("zone:maxspeed", "DE:30"),
            )
        )
        verifyAnswer(
            mapOf(
                "maxspeed:type" to "DE:urban",
                "maxspeed:type:forward" to "DE:urban",
                "maxspeed:type:backward" to "DE:rural"
            ),
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", RURAL, null)),
            arrayOf(
                StringMapEntryModify("maxspeed:type", "DE:urban", "DE:rural"),
                StringMapEntryDelete("maxspeed:type:forward", "DE:urban"),
                StringMapEntryDelete("maxspeed:type:backward", "DE:rural"),
            )
        )
    }

    /* ------------------------------------------------------------------------------------------ */
    /* ------------------------------ different directions -------------------------------------- */
    /* ------------------------------------------------------------------------------------------ */

    /* ---------------------------------- apply explicit maxspeed ------------------------------- */

    @Test fun `apply plain maxspeed in one direction`() {
        verifyAnswer(
            mapOf(),
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(MaxSpeedSign(Kmh(20)), null),
                null
            ),
            arrayOf(StringMapEntryAdd("maxspeed:forward", "20"))
        )
        verifyAnswer(
            mapOf(),
            ForwardAndBackwardMaxspeedAndType(
                null,
                MaxspeedAndType(MaxSpeedSign(Kmh(20)), null)
            ),
            arrayOf(StringMapEntryAdd("maxspeed:backward", "20"))
        )
        verifyAnswer(
            mapOf(),
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(MaxSpeedSign(Mph(30)), null),
                null
            ),
            arrayOf(StringMapEntryAdd("maxspeed:forward", "30 mph"))
        )
        verifyAnswer(
            mapOf(),
            ForwardAndBackwardMaxspeedAndType(
                null,
                MaxspeedAndType(MaxSpeedSign(Mph(30)), null)
            ),
            arrayOf(StringMapEntryAdd("maxspeed:backward", "30 mph"))
        )
    }

    @Test fun `apply common non-numeric maxspeed in one direction`() {
        verifyAnswer(
            mapOf(),
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(WalkMaxSpeed, null),
                null
            ),
            arrayOf(StringMapEntryAdd("maxspeed:forward", "walk"))
        )
        verifyAnswer(
            mapOf(),
            ForwardAndBackwardMaxspeedAndType(
                null,
                MaxspeedAndType(WalkMaxSpeed, null),
            ),
            arrayOf(StringMapEntryAdd("maxspeed:backward", "walk"))
        )
    }

    @Test fun `apply different plain maxspeed in each direction`() {
        verifyAnswer(
            mapOf(),
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(MaxSpeedSign(Kmh(20)), null),
                MaxspeedAndType(MaxSpeedSign(Kmh(30)), null)
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:forward", "20"),
                StringMapEntryAdd("maxspeed:backward", "30")
            )
        )
        verifyAnswer(
            mapOf(),
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(MaxSpeedSign(Mph(30)), null),
                MaxspeedAndType(MaxSpeedSign(Mph(40)), null)
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:forward", "30 mph"),
                StringMapEntryAdd("maxspeed:backward", "40 mph")
            )
        )
    }

    @Test fun `apply signed maxspeed in one direction`() {
        verifyAnswer(
            mapOf(),
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(MaxSpeedSign(Kmh(20)), JustSign),
                null
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:forward", "20"),
                StringMapEntryAdd("maxspeed:type:forward", "sign")
            )
        )
        verifyAnswer(
            mapOf(),
            ForwardAndBackwardMaxspeedAndType(
                null,
                MaxspeedAndType(MaxSpeedSign(Kmh(20)), JustSign)
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:backward", "20"),
                StringMapEntryAdd("maxspeed:type:backward", "sign")
            )
        )
        verifyAnswer(
            mapOf(),
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(MaxSpeedSign(Mph(30)), JustSign),
                null
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:forward", "30 mph"),
                StringMapEntryAdd("maxspeed:type:forward", "sign")
            )
        )
        verifyAnswer(
            mapOf(),
            ForwardAndBackwardMaxspeedAndType(
                null,
                MaxspeedAndType(MaxSpeedSign(Mph(30)), JustSign)
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:backward", "30 mph"),
                StringMapEntryAdd("maxspeed:type:backward", "sign")
            )
        )
    }

    /* ---------------------------------- apply implicit maxspeed ------------------------------- */

    @Test fun `apply implicit maxspeed in one direction`() {
        verifyAnswer(
            mapOf(),
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(null, ImplicitMaxSpeed("DE", URBAN, null)),
                null
            ),
            arrayOf(StringMapEntryAdd("maxspeed:type:forward", "DE:urban"))
        )
        verifyAnswer(
            mapOf(),
            ForwardAndBackwardMaxspeedAndType(
                null,
                MaxspeedAndType( null, ImplicitMaxSpeed("DE", RURAL, null))
            ),
            arrayOf(StringMapEntryAdd("maxspeed:type:backward", "DE:rural"))
        )
    }

    // Maybe this should not be allowed in the UI
    @Test fun `apply different implicit maxspeed in each direction`() {
        verifyAnswer(
            mapOf(),
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(null, ImplicitMaxSpeed("GB", NSL_SINGLE, null)),
                MaxspeedAndType(null, ImplicitMaxSpeed("GB", NSL_RESTRICTED, null))
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:type:forward", "GB:nsl_single"),
                StringMapEntryAdd("maxspeed:type:backward", "GB:nsl_restricted")
            )
        )
    }

    @Test fun `apply different answers to each direction which both have the same type`() {
        verifyAnswer(
            mapOf(),
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(MaxSpeedSign(Kmh(40)), JustSign),
                MaxspeedAndType(MaxSpeedSign(Kmh(50)), JustSign)
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:type:forward", "sign"),
                StringMapEntryAdd("maxspeed:type:backward", "sign"),
                StringMapEntryAdd("maxspeed:forward", "40"),
                StringMapEntryAdd("maxspeed:backward", "50")
            )
        )
    }

    /* ---------------------------------- apply mixed types ------------------------------------- */

    @Test fun `apply signed maxspeed in one direction and implicit in the other`() {
        verifyAnswer(
            mapOf(),
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(MaxSpeedSign(Kmh(40)), JustSign),
                MaxspeedAndType(null, ImplicitMaxSpeed("DE", URBAN, null))
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:forward", "40"),
                StringMapEntryAdd("maxspeed:type:forward", "sign"),
                StringMapEntryAdd("maxspeed:type:backward", "DE:urban")
            )
        )
        verifyAnswer(
            mapOf(),
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(null, ImplicitMaxSpeed("DE", URBAN, null)),
                MaxspeedAndType(MaxSpeedSign(Kmh(60)), JustSign)
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:type:forward", "DE:urban"),
                StringMapEntryAdd("maxspeed:backward", "60"),
                StringMapEntryAdd("maxspeed:type:backward", "sign")
            )
        )
        verifyAnswer(
            mapOf(),
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(MaxSpeedSign(Mph(30)), JustSign),
                MaxspeedAndType(null, ImplicitMaxSpeed("GB", NSL_SINGLE, null))
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:forward", "30 mph"),
                StringMapEntryAdd("maxspeed:type:forward", "sign"),
                StringMapEntryAdd("maxspeed:type:backward", "GB:nsl_single")
            )
        )
        verifyAnswer(
            mapOf(),
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(null, ImplicitMaxSpeed("GB", NSL_SINGLE, null)),
                MaxspeedAndType(MaxSpeedSign(Mph(30)), JustSign)
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:type:forward", "GB:nsl_single"),
                StringMapEntryAdd("maxspeed:backward", "30 mph"),
                StringMapEntryAdd("maxspeed:type:backward", "sign")
            )
        )
    }

    /* ----------------------- apply different sides to one existing value ---------------------- */

    @Test fun `change one maxspeed to different per direction`() {
        verifyAnswer(
            mapOf("maxspeed" to "50"),
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(MaxSpeedSign(Kmh(20)), null),
                MaxspeedAndType(MaxSpeedSign(Kmh(30)), null)
            ),
            arrayOf(
                StringMapEntryDelete("maxspeed", "50"),
                StringMapEntryAdd("maxspeed:forward", "20"),
                StringMapEntryAdd("maxspeed:backward", "30")
            )
        )
        verifyAnswer(
            mapOf("maxspeed" to "50 mph"),
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(MaxSpeedSign(Mph(20)), null),
                MaxspeedAndType(MaxSpeedSign(Mph(30)), null)
            ),
            arrayOf(
                StringMapEntryDelete("maxspeed", "50 mph"),
                StringMapEntryAdd("maxspeed:forward", "20 mph"),
                StringMapEntryAdd("maxspeed:backward", "30 mph")
            )
        )
    }

    @Test fun `change one implicit type to different per direction`() {
        verifyAnswer(
            mapOf("maxspeed:type" to "DE:bicycle_road"),
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(null, ImplicitMaxSpeed("DE", URBAN, null)),
                MaxspeedAndType(null, ImplicitMaxSpeed("DE", RURAL, null))
            ),
            arrayOf(
                StringMapEntryDelete("maxspeed:type", "DE:bicycle_road"),
                StringMapEntryAdd("maxspeed:type:forward", "DE:urban"),
                StringMapEntryAdd("maxspeed:type:backward", "DE:rural")
            )
        )
    }

    /* ----------------------- apply one value to existing different sides ---------------------- */

    @Test fun `change different maxspeed per direction to same in both directions`() {
        verifyAnswer(
            mapOf(
                "maxspeed:forward" to "50",
                "maxspeed:backward" to "60"
            ),
            maxspeedBothDirections(MaxSpeedSign(Kmh(40)), null),
            arrayOf(
                StringMapEntryDelete("maxspeed:forward", "50"),
                StringMapEntryDelete("maxspeed:backward", "60"),
                StringMapEntryAdd("maxspeed", "40")
            )
        )
        verifyAnswer(
            mapOf(
                "maxspeed:forward" to "50",
                "maxspeed:backward" to "60"
            ),
            maxspeedBothDirections(MaxSpeedSign(Kmh(50)), null),
            arrayOf(
                StringMapEntryDelete("maxspeed:forward", "50"),
                StringMapEntryDelete("maxspeed:backward", "60"),
                StringMapEntryAdd("maxspeed", "50")
            )
        )
        verifyAnswer(
            mapOf(
                "maxspeed:forward" to "50 mph",
                "maxspeed:backward" to "60 mph"
            ),
            maxspeedBothDirections(MaxSpeedSign(Mph(40)), null),
            arrayOf(
                StringMapEntryDelete("maxspeed:forward", "50 mph"),
                StringMapEntryDelete("maxspeed:backward", "60 mph"),
                StringMapEntryAdd("maxspeed", "40 mph")
            )
        )
        verifyAnswer(
            mapOf(
                "maxspeed:forward" to "50 mph",
                "maxspeed:backward" to "60 mph"
            ),
            maxspeedBothDirections(MaxSpeedSign(Mph(50)), null),
            arrayOf(
                StringMapEntryDelete("maxspeed:forward", "50 mph"),
                StringMapEntryDelete("maxspeed:backward", "60 mph"),
                StringMapEntryAdd("maxspeed", "50 mph")
            )
        )
    }

    @Test fun `change different type per direction to same in both directions`() {
        verifyAnswer(
            mapOf(
                "maxspeed:type:forward" to "DE:urban",
                "maxspeed:type:backward" to "DE:rural"
            ),
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            arrayOf(
                StringMapEntryDelete("maxspeed:type:forward", "DE:urban"),
                StringMapEntryDelete("maxspeed:type:backward", "DE:rural"),
                StringMapEntryAdd("maxspeed:type", "DE:urban")
            )
        )
        verifyAnswer(
            mapOf(
                "maxspeed:type:forward" to "DE:urban",
                "maxspeed:type:backward" to "DE:rural"
            ),
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", BICYCLE_ROAD, null)),
            arrayOf(
                StringMapEntryDelete("maxspeed:type:forward", "DE:urban"),
                StringMapEntryDelete("maxspeed:type:backward", "DE:rural"),
                StringMapEntryAdd("maxspeed:type", "DE:bicycle_road")
            )
        )
    }

    @Test fun `change different type per direction to signed value in both directions`() {
        verifyAnswer(
            mapOf(
                "maxspeed:type:forward" to "DE:urban",
                "maxspeed:type:backward" to "DE:rural"
            ),
            maxspeedBothDirections(MaxSpeedSign(Kmh(50)), JustSign),
            arrayOf(
                StringMapEntryDelete("maxspeed:type:forward", "DE:urban"),
                StringMapEntryDelete("maxspeed:type:backward", "DE:rural"),
                StringMapEntryAdd("maxspeed:type", "sign"),
                StringMapEntryAdd("maxspeed", "50")
            )
        )
        verifyAnswer(
            mapOf(
                "maxspeed:type:forward" to "GB:nsl_restricted",
                "maxspeed:type:backward" to "GB:nsl_single"
            ),
            maxspeedBothDirections(MaxSpeedSign(Mph(50)), JustSign),
            arrayOf(
                StringMapEntryDelete("maxspeed:type:forward", "GB:nsl_restricted"),
                StringMapEntryDelete("maxspeed:type:backward", "GB:nsl_single"),
                StringMapEntryAdd("maxspeed:type", "sign"),
                StringMapEntryAdd("maxspeed", "50 mph")
            )
        )
    }

    /* --------------------------------------- different type tags ------------------------------ */

    @Test fun `apply answer to different type tag in each direction`() {
        verifyAnswer(
            mapOf(
                "maxspeed:type:forward" to "DE:urban",
                "source:maxspeed:backward" to "DE:rural"
            ),
            maxspeedBothDirections(null, ImplicitMaxSpeed("DE", BICYCLE_ROAD, null)),
            arrayOf(
                StringMapEntryDelete("maxspeed:type:forward", "DE:urban"),
                StringMapEntryDelete("source:maxspeed:backward", "DE:rural"),
                StringMapEntryAdd("maxspeed:type", "DE:bicycle_road")
            )
        )
        verifyAnswer(
            mapOf(
                "maxspeed:type:forward" to "DE:urban",
                "source:maxspeed:backward" to "DE:rural"
            ),
            maxspeedBothDirections(MaxSpeedSign(Kmh(40)), JustSign),
            arrayOf(
                StringMapEntryDelete("maxspeed:type:forward", "DE:urban"),
                StringMapEntryDelete("source:maxspeed:backward", "DE:rural"),
                StringMapEntryAdd("maxspeed:type", "sign"),
                StringMapEntryAdd("maxspeed", "40")
            )
        )
        verifyAnswer(
            mapOf(
                "maxspeed:type:forward" to "DE:urban",
                "source:maxspeed:backward" to "DE:rural"
            ),
            ForwardAndBackwardMaxspeedAndType(
                MaxspeedAndType(MaxSpeedSign(Kmh(40)), JustSign),
                MaxspeedAndType(MaxSpeedSign(Kmh(50)), JustSign)
            ),
            arrayOf(
                StringMapEntryModify("maxspeed:type:forward", "DE:urban", "sign"),
                StringMapEntryDelete("source:maxspeed:backward", "DE:rural"),
                StringMapEntryAdd("maxspeed:type:backward", "sign"),
                StringMapEntryAdd("maxspeed:forward", "40"),
                StringMapEntryAdd("maxspeed:backward", "50")
            )
        )
    }
}

private fun verifyAnswer(tags: Map<String, String>, answer: ForwardAndBackwardMaxspeedAndType, expectedChanges: Array<StringMapEntryChange>) {
    val cb = StringMapChangesBuilder(tags)
    answer.applyTo(cb, null)
    val changes = cb.create().changes
    Assertions.assertThat(changes).containsExactlyInAnyOrder(*expectedChanges)
}

private fun maxspeedBothDirections(explicit: MaxSpeedAnswer?, type: MaxSpeedAnswer?) =
    ForwardAndBackwardMaxspeedAndType(
        MaxspeedAndType(explicit, type),
        MaxspeedAndType(explicit, type)
    )
