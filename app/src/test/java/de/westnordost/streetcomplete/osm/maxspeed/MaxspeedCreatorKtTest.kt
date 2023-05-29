package de.westnordost.streetcomplete.osm.maxspeed

import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryChange
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryDelete
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryModify
import de.westnordost.streetcomplete.osm.lit.LitStatus.*
import de.westnordost.streetcomplete.osm.maxspeed.Inequality.*
import de.westnordost.streetcomplete.osm.maxspeed.RoadType.*
import de.westnordost.streetcomplete.osm.nowAsCheckDateString
import de.westnordost.streetcomplete.osm.weight.ImperialPounds
import de.westnordost.streetcomplete.osm.weight.Kilograms
import de.westnordost.streetcomplete.osm.weight.MetricTons
import de.westnordost.streetcomplete.osm.weight.ShortTons
import de.westnordost.streetcomplete.quests.max_speed.Kmh
import de.westnordost.streetcomplete.quests.max_speed.Mph
import org.assertj.core.api.Assertions
import org.junit.Test

class MaxspeedCreatorKtTest {
    @Test fun `apply nothing applies nothing`() {
        verifyAnswer(
            mapOf(),
            ForwardAndBackwardAllSpeedInformation(null, null, null),
            arrayOf()
        )
    }

    @Test fun `add check date if nothing change`() {
        verifyAnswer(
            mapOf("maxspeed" to "20"),
            bareMaxspeedBothDirections(MaxSpeedSign(Kmh(20)), null, null),
            arrayOf(
                StringMapEntryModify("maxspeed", "20", "20"),
                StringMapEntryAdd("check_date:maxspeed", nowAsCheckDateString())
            )
        )
    }

    /* ------------------------------------------------------------------------------------------ */
    /* ------------------------------ same in both directions ----------------------------------- */
    /* ------------------------------------------------------------------------------------------ */

    /* ---------------------------------- apply plain values ------------------------------------ */

    @Test fun `apply plain maxspeed`() {
        verifyAnswer(
            mapOf(),
            bareMaxspeedBothDirections(MaxSpeedSign(Kmh(20)), null),
            arrayOf(StringMapEntryAdd("maxspeed", "20"))
        )
        verifyAnswer(
            mapOf(),
            bareMaxspeedBothDirections(MaxSpeedSign(Mph(20)), null),
            arrayOf(StringMapEntryAdd("maxspeed", "20 mph"))
        )
    }

    @Test fun `apply common non-numeric maxspeed`() {
        verifyAnswer(
            mapOf(),
            bareMaxspeedBothDirections(WalkMaxSpeed, null),
            arrayOf(StringMapEntryAdd("maxspeed", "walk"))
        )
        verifyAnswer(
            mapOf(),
            bareMaxspeedBothDirections(MaxSpeedIsNone, null),
            arrayOf(StringMapEntryAdd("maxspeed", "none"))
        )
    }

    @Test fun `apply plain maxspeed type`() {
        verifyAnswer(
            mapOf(),
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            arrayOf(StringMapEntryAdd("maxspeed:type", "DE:urban"))
        )
        verifyAnswer(
            mapOf(),
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", RURAL, null)),
            arrayOf(StringMapEntryAdd("maxspeed:type", "DE:rural"))
        )
    }

    @Test fun `apply unsigned answer`() {
        verifyAnswer(
            mapOf(),
            bareMaxspeedBothDirections(null, NoSign),
            arrayOf(StringMapEntryAdd("maxspeed:signed", "no"))
        )
    }

    @Test fun `tag lit status when given`() {
        verifyAnswer(
            mapOf(),
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("GB", NSL_SINGLE, NO)),
            arrayOf(
                StringMapEntryAdd("maxspeed:type", "GB:nsl_single"),
                StringMapEntryAdd("lit", "no")
            )
        )
        verifyAnswer(
            mapOf(),
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("GB", NSL_RESTRICTED, YES)),
            arrayOf(
                StringMapEntryAdd("maxspeed:type", "GB:nsl_restricted"),
                StringMapEntryAdd("lit", "yes")
            )
        )
    }

    @Test fun `do not modify lit tag if not given`() {
        verifyAnswer(
            mapOf("lit" to "yes"),
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            arrayOf(StringMapEntryAdd("maxspeed:type", "DE:urban"))
        )
        verifyAnswer(
            mapOf("lit" to "unknown"),
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", RURAL, null)),
            arrayOf(StringMapEntryAdd("maxspeed:type", "DE:rural"))
        )
    }

    @Test fun `apply zone maxspeed`() {
        verifyAnswer(
            mapOf(),
            bareMaxspeedBothDirections(null, MaxSpeedZone(Kmh(20), "DE", "zone20")),
            arrayOf(
                StringMapEntryAdd("maxspeed:type", "DE:zone20"),
                StringMapEntryAdd("maxspeed", "20")
            )
        )
        verifyAnswer(
            mapOf(),
            bareMaxspeedBothDirections(null, MaxSpeedZone(Mph(20), "GB", "zone20")),
            arrayOf(
                StringMapEntryAdd("maxspeed:type", "GB:zone20"),
                StringMapEntryAdd("maxspeed", "20 mph")
            )
        )
    }

    @Test fun `apply signed maxspeed`() {
        verifyAnswer(
            mapOf(),
            bareMaxspeedBothDirections(MaxSpeedSign(Kmh(20)), JustSign),
            arrayOf(
                StringMapEntryAdd("maxspeed", "20"),
                StringMapEntryAdd("maxspeed:type", "sign")
            )
        )
        verifyAnswer(
            mapOf(),
            bareMaxspeedBothDirections(MaxSpeedSign(Mph(20)), JustSign),
            arrayOf(
                StringMapEntryAdd("maxspeed", "20 mph"),
                StringMapEntryAdd("maxspeed:type", "sign")
            )
        )
    }

    @Test fun `change to living street`() {
        verifyAnswer(
            mapOf("highway" to "residential"),
            bareMaxspeedBothDirections(null, null, LivingStreet("DE")),
            arrayOf(
                StringMapEntryModify("highway", "residential", "living_street")
            )
        )
    }

    @Test fun `mark as school zone`() {
        verifyAnswer(
            mapOf(),
            ForwardAndBackwardAllSpeedInformation(null, null, IsSchoolZone),
            arrayOf(
                StringMapEntryAdd("hazard", "school_zone")
            )
        )
    }

    /* ----------------------------- change 'maxspeed' to another type -------------------------- */

    @Test fun `change plain maxspeed to different value`() {
        verifyAnswer(
            mapOf("maxspeed" to "40"),
            bareMaxspeedBothDirections(MaxSpeedSign(Kmh(60)), null),
            arrayOf(StringMapEntryModify("maxspeed", "40", "60"))
        )
        verifyAnswer(
            mapOf("maxspeed" to "30 mph"),
            bareMaxspeedBothDirections(MaxSpeedSign(Mph(40)), null),
            arrayOf(StringMapEntryModify("maxspeed", "30 mph", "40 mph"))
        )
    }

    /* ----------------------------------- change to implicit  ---------------------------------- */

    @Test fun `change maxspeed type from one implicit value to another`() {
        verifyAnswer(
            mapOf("maxspeed:type" to "DE:urban"),
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", RURAL, null)),
            arrayOf(StringMapEntryModify("maxspeed:type", "DE:urban", "DE:rural"))
        )
        verifyAnswer(
            mapOf("maxspeed:type" to "DE:motorway"),
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", RURAL, null)),
            arrayOf(StringMapEntryModify("maxspeed:type", "DE:motorway", "DE:rural"))
        )
    }

    @Test fun `change signed maxspeed to implicit maxspeed`() {
        verifyAnswer(
            mapOf(
                "maxspeed" to "40",
                "maxspeed:type" to "sign"
            ),
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            arrayOf(
                StringMapEntryDelete("maxspeed", "40"),
                StringMapEntryModify("maxspeed:type", "sign", "DE:urban")
            )
        )
    }

    @Test fun `change zone maxspeed to implicit maxspeed`() {
        verifyAnswer(
            mapOf("maxspeed:type" to "DE:zone30"),
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            arrayOf(
                StringMapEntryModify("maxspeed:type", "DE:zone30", "DE:urban")
            )
        )
        verifyAnswer(
            mapOf(
                "maxspeed:type" to "DE:zone30",
                "maxspeed" to "30"
            ),
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
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
            bareMaxspeedBothDirections(null, MaxSpeedZone(Kmh(20), "DE", "zone20")),
            arrayOf(
                StringMapEntryModify("maxspeed:type", "DE:zone30", "DE:zone20"),
                StringMapEntryAdd("maxspeed", "20")
            )
        )
        verifyAnswer(
            mapOf("maxspeed:type" to "GB:zone20"),
            bareMaxspeedBothDirections(null, MaxSpeedZone(Mph(30), "GB", "zone30")),
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
            bareMaxspeedBothDirections(null, MaxSpeedZone(Kmh(20), "DE", "zone20")),
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
            bareMaxspeedBothDirections(null, MaxSpeedZone(Mph(30), "GB", "zone30")),
            arrayOf(
                StringMapEntryModify("maxspeed:type", "GB:zone20", "GB:zone30"),
                StringMapEntryModify("maxspeed", "20 mph", "30 mph")
            )
        )
    }

    @Test fun `change explicit maxspeed to zone maxspeed`() {
        verifyAnswer(
            mapOf("maxspeed" to "40"),
            bareMaxspeedBothDirections(null, MaxSpeedZone(Kmh(30), "DE", "zone30")),
            arrayOf(
                StringMapEntryAdd("maxspeed:type", "DE:zone30"),
                StringMapEntryModify("maxspeed", "40", "30")
            )
        )
        verifyAnswer(
            mapOf("maxspeed" to "30 mph"),
            bareMaxspeedBothDirections(null, MaxSpeedZone(Mph(20), "GB", "zone20")),
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
            bareMaxspeedBothDirections(null, MaxSpeedZone(Kmh(30), "DE", "zone30")),
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
            bareMaxspeedBothDirections(null, MaxSpeedZone(Mph(20), "GB", "zone20")),
            arrayOf(
                StringMapEntryModify("maxspeed", "30 mph", "20 mph"),
                StringMapEntryModify("maxspeed:type", "sign", "GB:zone20")
            )
        )
    }

    @Test fun `change implicit maxspeed to zone maxspeed`() {
        verifyAnswer(
            mapOf("maxspeed:type" to "DE:urban"),
            bareMaxspeedBothDirections(null, MaxSpeedZone(Kmh(30), "DE", "zone30")),
            arrayOf(
                StringMapEntryModify("maxspeed:type", "DE:urban", "DE:zone30"),
                StringMapEntryAdd("maxspeed", "30")
            )
        )
        verifyAnswer(
            mapOf("maxspeed:type" to "GB:nsl_restricted"),
            bareMaxspeedBothDirections(null, MaxSpeedZone(Mph(20), "GB", "zone20")),
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
            bareMaxspeedBothDirections(MaxSpeedSign(Kmh(40)), JustSign),
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
            bareMaxspeedBothDirections(MaxSpeedSign(Mph(50)), JustSign),
            arrayOf(
                StringMapEntryModify("maxspeed:type", "sign", "sign"),
                StringMapEntryModify("maxspeed", "40 mph", "50 mph")
            )
        )
    }

    @Test fun `change implicit to signed maxspeed`() {
        verifyAnswer(
            mapOf("maxspeed:type" to "DE:urban"),
            bareMaxspeedBothDirections(MaxSpeedSign(Kmh(20)), JustSign),
            arrayOf(
                StringMapEntryAdd("maxspeed", "20"),
                StringMapEntryModify("maxspeed:type", "DE:urban", "sign")
            )
        )
        verifyAnswer(
            mapOf("maxspeed:type" to "GB:nsl_single"),
            bareMaxspeedBothDirections(MaxSpeedSign(Mph(50)), JustSign),
            arrayOf(
                StringMapEntryAdd("maxspeed", "50 mph"),
                StringMapEntryModify("maxspeed:type", "GB:nsl_single", "sign")
            )
        )
    }

    @Test fun `change zone to signed maxspeed`() {
        verifyAnswer(
            mapOf("maxspeed:type" to "DE:zone30"),
            bareMaxspeedBothDirections(MaxSpeedSign(Kmh(20)), JustSign),
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
            bareMaxspeedBothDirections(MaxSpeedSign(Kmh(20)), JustSign),
            arrayOf(
                StringMapEntryModify("maxspeed", "30", "20"),
                StringMapEntryModify("maxspeed:type", "DE:zone30", "sign")
            )
        )
        verifyAnswer(
            mapOf("maxspeed:type" to "GB:zone20"),
            bareMaxspeedBothDirections(MaxSpeedSign(Mph(30)), JustSign),
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
            bareMaxspeedBothDirections(MaxSpeedSign(Mph(30)), JustSign),
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
            ForwardAndBackwardAllSpeedInformation(null, null, LivingStreet("DE")),
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
            ForwardAndBackwardAllSpeedInformation(null, null, LivingStreet("DE")),
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
            ForwardAndBackwardAllSpeedInformation(null, null, LivingStreet("DE")),
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
            bareMaxspeedBothDirections(WalkMaxSpeed, null, LivingStreet("DE")),
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
            ForwardAndBackwardAllSpeedInformation(null, null, LivingStreet("DE")),
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
            ForwardAndBackwardAllSpeedInformation(null, null, IsSchoolZone),
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
            mapOf("source:maxspeed" to "25 unless otherwise signed"),
            ForwardAndBackwardAllSpeedInformation(null, null, IsSchoolZone),
            arrayOf(StringMapEntryAdd("hazard", "school_zone"))
        )
    }

    @Test fun `tag school zone also with an explicit speed limit`() {
        verifyAnswer(
            mapOf("maxspeed" to "50"),
            bareMaxspeedBothDirections(MaxSpeedSign(Kmh(20)), null, IsSchoolZone),
            arrayOf(
                StringMapEntryModify("maxspeed", "50", "20"),
                StringMapEntryAdd("hazard", "school_zone")
            )
        )
    }

    @Test fun `remove school_zone tag if user was shown that it was a school zone and selected something else`() {
        verifyAnswer(
            mapOf("hazard" to "school_zone"),
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
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
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
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
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            arrayOf(StringMapEntryModify("source:maxspeed", "sign", "DE:urban"))
        )
        verifyAnswer(
            mapOf("zone:maxspeed" to "DE:rural"),
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            arrayOf(StringMapEntryModify("zone:maxspeed", "DE:rural", "DE:urban"))
        )
        verifyAnswer(
            mapOf("zone:traffic" to "DE:rural"),
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            arrayOf(StringMapEntryModify("zone:traffic", "DE:rural", "DE:urban"))
        )
    }

    @Test fun `respect previously used type tag when source_maxspeed is a special value`() {
        verifyAnswer(
            mapOf(
                "zone:maxspeed" to "DE:rural",
                "source:maxspeed" to "25 unless otherwise signed"
            ),
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            arrayOf(StringMapEntryModify("zone:maxspeed", "DE:rural", "DE:urban"))
        )
        verifyAnswer(
            mapOf(
                "zone:traffic" to "DE:rural",
                "source:maxspeed" to "25 unless otherwise signed"
            ),
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            arrayOf(StringMapEntryModify("zone:traffic", "DE:rural", "DE:urban"))
        )
    }

    @Test fun `do not use source_maxspeed if previous value was not a maxspeed type, but preserve special values`() {
        verifyAnswer(
            mapOf(
                "source:maxspeed" to "25 unless otherwise posted",
                "maxspeed" to "60"
            ),
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
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
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
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
            bareMaxspeedBothDirections(MaxSpeedSign(Kmh(30)), JustSign),
            arrayOf(
                StringMapEntryDelete("zone:maxspeed", "DE:urban"),
                StringMapEntryAdd("maxspeed:type", "sign"),
                StringMapEntryAdd("maxspeed", "30")
            )
        )
        verifyAnswer(
            mapOf("zone:traffic" to "DE:urban"),
            bareMaxspeedBothDirections(MaxSpeedSign(Kmh(30)), JustSign),
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
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("RU", RURAL, null)),
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
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("RU", RURAL, null)),
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
            bareMaxspeedBothDirections(MaxSpeedZone(Kmh(20), "RU", "zone20"), JustSign),
            arrayOf(
                StringMapEntryModify("maxspeed:type", "sign", "sign"),
                StringMapEntryModify("maxspeed", "RU:zone30", "RU:zone20"))
        )
        verifyAnswer(
            mapOf(
                "maxspeed" to "RU:zone30",
                "source:maxspeed" to "sign"
            ),
            bareMaxspeedBothDirections(MaxSpeedZone(Kmh(20), "RU", "zone20"), JustSign),
            arrayOf(
                StringMapEntryModify("maxspeed", "RU:zone30", "RU:zone20"),
                StringMapEntryModify("source:maxspeed", "sign", "sign"),
            )
        )
    }

    @Test fun `do not keep using maxspeed key for type if adding both explicit value and type`() {
        verifyAnswer(
            mapOf("maxspeed" to "RU:urban"),
            bareMaxspeedBothDirections(MaxSpeedSign(Kmh(50)), ImplicitMaxSpeed("RU", RURAL, null)),
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
            bareMaxspeedBothDirections(MaxSpeedSign(Kmh(110)), null),
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
            bareMaxspeedBothDirections(MaxSpeedSign(Kmh(110)), null),
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
            bareMaxspeedBothDirections(MaxSpeedSign(Kmh(110)), ImplicitMaxSpeed("DE", MOTORWAY, null)),
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
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", RURAL, null)),
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
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", RURAL, null)),
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
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", RURAL, null)),
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
            bareMaxspeedBothDirections(MaxSpeedSign(Kmh(50)), JustSign),
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
            ),
            bareMaxspeedBothDirections(MaxSpeedSign(Kmh(30)), null),
            arrayOf(
                StringMapEntryModify("maxspeed", "50", "30"),
                StringMapEntryDelete("maxspeed:hgv", "40"),
                StringMapEntryDelete("maxspeed:conditional", "20 @ (10:00-11:00)"),
                StringMapEntryDelete("maxspeed:trailer:conditional", "20 @ (10:00-11:00)"),
                StringMapEntryDelete("maxspeed:goods:backward", "25"),
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
                "maxspeed:bdouble:backward" to "75"
            ),
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
            arrayOf(
                StringMapEntryAdd("maxspeed:type", "DE:urban"),
                StringMapEntryDelete("maxspeed", "100"),
                StringMapEntryDelete("maxspeed:caravan", "80"),
                StringMapEntryDelete("maxspeed:conditional", "90 @ (10:00-11:00)"),
                StringMapEntryDelete("maxspeed:agricultural:conditional", "20 @ (10:00-11:00)"),
                StringMapEntryDelete("maxspeed:bdouble:backward", "75")
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
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", RURAL, null)),
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
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", RURAL, null)),
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
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", RURAL, null)),
            arrayOf(
                StringMapEntryModify("maxspeed:type", "DE:urban", "DE:rural"),
                StringMapEntryDelete("maxspeed:type:forward", "DE:urban"),
                StringMapEntryDelete("maxspeed:type:backward", "DE:rural"),
            )
        )
    }

    /* ------------------------------------------------------------------------------------------ */
    /* ------------------------------ advisory maxspeed ----------------------------------------- */
    /* ------------------------------------------------------------------------------------------ */

    @Test fun `apply advisory maxspeed`() {
        verifyAnswer(
            mapOf(),
            maxspeedBothDirections(null, AdvisorySpeedSign(Kmh(40)), null),
            arrayOf(
                StringMapEntryAdd("maxspeed:advisory", "40"),
                StringMapEntryAdd("maxspeed:type:advisory", "sign")
            )
        )
        verifyAnswer(
            mapOf(),
            maxspeedBothDirections(null, AdvisorySpeedSign(Mph(40)), null),
            arrayOf(
                StringMapEntryAdd("maxspeed:advisory", "40 mph"),
                StringMapEntryAdd("maxspeed:type:advisory", "sign")
            )
        )
    }

    @Test fun `change advisory maxspeed`() {
        verifyAnswer(
            mapOf("maxspeed:advisory" to "30"),
            maxspeedBothDirections(null, AdvisorySpeedSign(Kmh(40)), null),
            arrayOf(
                StringMapEntryModify("maxspeed:advisory", "30", "40"),
                StringMapEntryAdd("maxspeed:type:advisory", "sign")
            )
        )
        verifyAnswer(
            mapOf("maxspeed:advisory" to "30 mph"),
            maxspeedBothDirections(null, AdvisorySpeedSign(Mph(40)), null),
            arrayOf(
                StringMapEntryModify("maxspeed:advisory", "30 mph", "40 mph"),
                StringMapEntryAdd("maxspeed:type:advisory", "sign")
            )
        )
    }

    @Test fun `respect previous type tag when adding advisory maxspeed`() {
        verifyAnswer(
            mapOf(
                "maxspeed:advisory" to "30",
                "source:maxspeed:advisory" to "sign"
            ),
            maxspeedBothDirections(null, AdvisorySpeedSign(Kmh(40)), null),
            arrayOf(
                StringMapEntryModify("maxspeed:advisory", "30", "40"),
                StringMapEntryModify("source:maxspeed:advisory", "sign", "sign")
            )
        )
    }

    @Test fun `update advisory maxspeed key when changing value`() {
        verifyAnswer(
            mapOf("maxspeed:advised" to "30"),
            maxspeedBothDirections(null, AdvisorySpeedSign(Kmh(40)), null),
            arrayOf(
                StringMapEntryDelete("maxspeed:advised", "30"),
                StringMapEntryAdd("maxspeed:advisory", "40"),
                StringMapEntryAdd("maxspeed:type:advisory", "sign")
            )
        )
    }

    @Test fun `apply advisory maxspeed in only one direction`() {
        verifyAnswer(
            mapOf(),
            ForwardAndBackwardAllSpeedInformation(
                AllSpeedInformation(null, AdvisorySpeedSign(Kmh(40)), null),
                null,
                null
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:advisory:forward", "40"),
                StringMapEntryAdd("maxspeed:type:advisory:forward", "sign")
            )
        )
        verifyAnswer(
            mapOf(),
            ForwardAndBackwardAllSpeedInformation(
                null,
                AllSpeedInformation(null, AdvisorySpeedSign(Kmh(40)), null),
                null
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:advisory:backward", "40"),
                StringMapEntryAdd("maxspeed:type:advisory:backward", "sign")
            )
        )
    }

    @Test fun `apply different advisory maxspeed in each direction`() {
        verifyAnswer(
            mapOf(),
            ForwardAndBackwardAllSpeedInformation(
                AllSpeedInformation(null, AdvisorySpeedSign(Kmh(40)), null),
                AllSpeedInformation(null, AdvisorySpeedSign(Kmh(50)), null),
                null
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:advisory:forward", "40"),
                StringMapEntryAdd("maxspeed:type:advisory:forward", "sign"),
                StringMapEntryAdd("maxspeed:advisory:backward", "50"),
                StringMapEntryAdd("maxspeed:type:advisory:backward", "sign")
            )
        )
    }

    /* ------------------------------------------------------------------------------------------ */
    /* ------------------------------ variable maxspeed ----------------------------------------- */
    /* ------------------------------------------------------------------------------------------ */

    @Test fun `apply variable maxspeed`() {
        verifyAnswer(
            mapOf(),
            maxspeedBothDirections(null, null, true),
            arrayOf(StringMapEntryAdd("maxspeed:variable", "yes"))
        )
    }

    @Test fun `apply variable maxspeed in only one direction`() {
        verifyAnswer(
            mapOf(),
            ForwardAndBackwardAllSpeedInformation(
                AllSpeedInformation(null, null, true),
                null,
                null
            ),
            arrayOf(StringMapEntryAdd("maxspeed:variable:forward", "yes"))
        )
        verifyAnswer(
            mapOf(),
            ForwardAndBackwardAllSpeedInformation(
                null,
                AllSpeedInformation(null, null, true),
                null
            ),
            arrayOf(StringMapEntryAdd("maxspeed:variable:backward", "yes"))
        )
    }

    @Test fun `remove variable maxspeed when answering that there is no variable limit now`() {
        verifyAnswer(
            mapOf("maxspeed:variable" to "obstruction"),
            maxspeedBothDirections(null, null, false),
            arrayOf(StringMapEntryModify("maxspeed:variable", "obstruction", "no"))
        )
        verifyAnswer(
            mapOf("maxspeed" to "signals"),
            maxspeedBothDirections(null, null, false),
            arrayOf(
                StringMapEntryDelete("maxspeed", "signals"),
                StringMapEntryAdd("maxspeed:variable", "no")
            )
        )
    }

    @Test fun `do not change previous variable value when answering that the limit is still variable`() {
        verifyAnswer(
            mapOf("maxspeed:variable" to "obstruction"),
            maxspeedBothDirections(null, null, true),
            arrayOf(
                StringMapEntryModify("maxspeed:variable", "obstruction", "obstruction"),
                StringMapEntryAdd("check_date:maxspeed", nowAsCheckDateString())
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
            bareMaxspeedAndType(
                MaxspeedAndType(MaxSpeedSign(Kmh(20)), null),
                null
            ),
            arrayOf(StringMapEntryAdd("maxspeed:forward", "20"))
        )
        verifyAnswer(
            mapOf(),
            bareMaxspeedAndType(
                null,
                MaxspeedAndType(MaxSpeedSign(Kmh(20)), null)
            ),
            arrayOf(StringMapEntryAdd("maxspeed:backward", "20"))
        )
        verifyAnswer(
            mapOf(),
            bareMaxspeedAndType(
                MaxspeedAndType(MaxSpeedSign(Mph(30)), null),
                null
            ),
            arrayOf(StringMapEntryAdd("maxspeed:forward", "30 mph"))
        )
        verifyAnswer(
            mapOf(),
            bareMaxspeedAndType(
                null,
                MaxspeedAndType(MaxSpeedSign(Mph(30)), null)
            ),
            arrayOf(StringMapEntryAdd("maxspeed:backward", "30 mph"))
        )
    }

    @Test fun `apply common non-numeric maxspeed in one direction`() {
        verifyAnswer(
            mapOf(),
            bareMaxspeedAndType(
                MaxspeedAndType(WalkMaxSpeed, null),
                null
            ),
            arrayOf(StringMapEntryAdd("maxspeed:forward", "walk"))
        )
        verifyAnswer(
            mapOf(),
            bareMaxspeedAndType(
                null,
                MaxspeedAndType(WalkMaxSpeed, null),
            ),
            arrayOf(StringMapEntryAdd("maxspeed:backward", "walk"))
        )
    }

    @Test fun `apply different plain maxspeed in each direction`() {
        verifyAnswer(
            mapOf(),
            bareMaxspeedAndType(
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
            bareMaxspeedAndType(
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
            bareMaxspeedAndType(
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
            bareMaxspeedAndType(
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
            bareMaxspeedAndType(
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
            bareMaxspeedAndType(
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
            bareMaxspeedAndType(
                MaxspeedAndType(null, ImplicitMaxSpeed("DE", URBAN, null)),
                null
            ),
            arrayOf(StringMapEntryAdd("maxspeed:type:forward", "DE:urban"))
        )
        verifyAnswer(
            mapOf(),
            bareMaxspeedAndType(
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
            bareMaxspeedAndType(
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
            bareMaxspeedAndType(
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
            bareMaxspeedAndType(
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
            bareMaxspeedAndType(
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
            bareMaxspeedAndType(
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
            bareMaxspeedAndType(
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
            bareMaxspeedAndType(
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
            bareMaxspeedAndType(
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
            bareMaxspeedAndType(
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
            bareMaxspeedBothDirections(MaxSpeedSign(Kmh(40)), null),
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
            bareMaxspeedBothDirections(MaxSpeedSign(Kmh(50)), null),
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
            bareMaxspeedBothDirections(MaxSpeedSign(Mph(40)), null),
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
            bareMaxspeedBothDirections(MaxSpeedSign(Mph(50)), null),
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
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", URBAN, null)),
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
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", BICYCLE_ROAD, null)),
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
            bareMaxspeedBothDirections(MaxSpeedSign(Kmh(50)), JustSign),
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
            bareMaxspeedBothDirections(MaxSpeedSign(Mph(50)), JustSign),
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
            bareMaxspeedBothDirections(null, ImplicitMaxSpeed("DE", BICYCLE_ROAD, null)),
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
            bareMaxspeedBothDirections(MaxSpeedSign(Kmh(40)), JustSign),
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
            bareMaxspeedAndType(
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

    /* ------------------------------------------------------------------------------------------ */
    /* ------------------------------ different vehicles ---------------------------------------- */
    /* ------------------------------------------------------------------------------------------ */

    /* ------------------------------------ apply maxspeed -------------------------------------- */

    @Test fun `apply maxspeed answer for a vehicle`() {
        verifyAnswer(
            mapOf(),
            maxspeedBothDirections(
                mapOf(
                    "hgv" to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(20)), null))
                )
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:hgv", "20")
            )
        )
        verifyAnswer(
            mapOf(),
            maxspeedBothDirections(
                mapOf(
                    "coach" to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(20)), null))
                )
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:coach", "20")
            )
        )
        verifyAnswer(
            mapOf(),
            maxspeedBothDirections(
                mapOf(
                    "hgv" to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Mph(20)), null))
                )
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:hgv", "20 mph")
            )
        )
    }

    @Test fun `apply answer for multiple vehicles`() {
        verifyAnswer(
            mapOf(),
            maxspeedBothDirections(
                mapOf(
                    null to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(90)), null)),
                    "hgv" to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(80)), null)),
                    "coach" to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(70)), null))
                )
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed", "90"),
                StringMapEntryAdd("maxspeed:hgv", "80"),
                StringMapEntryAdd("maxspeed:coach", "70")
            )
        )
    }

    @Test fun `apply answer for a vehicle in one direction`() {
        verifyAnswer(
            mapOf(),
            ForwardAndBackwardAllSpeedInformation(
                AllSpeedInformation(
                    mapOf(
                        "hgv" to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(20)), null))
                    ),
                    null, null
                ),
                null, null
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:hgv:forward", "20")
            )
        )
        verifyAnswer(
            mapOf(),
            ForwardAndBackwardAllSpeedInformation(
                null,
                AllSpeedInformation(
                    mapOf(
                        "hgv" to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(20)), null))
                    ),
                    null, null
                ),
                null
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:hgv:backward", "20")
            )
        )
    }

    @Test fun `change maxspeed for a vehicle`() {
        verifyAnswer(
            mapOf("maxspeed:hgv" to "50"),
            maxspeedBothDirections(
                mapOf(
                    "hgv" to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null))
                )
            ),
            arrayOf(
                StringMapEntryModify("maxspeed:hgv", "50", "40")
            )
        )
    }

    /* ------------------------------------ apply type ------------------------------------------ */

    @Test fun `apply explicit speed and type answer for a vehicle`() {
        verifyAnswer(
            mapOf(),
            maxspeedBothDirections(
                mapOf(
                    "trailer" to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(50)), JustSign))
                )
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:trailer", "50"),
                StringMapEntryAdd("maxspeed:type:trailer", "sign")
            )
        )
    }

    /* ------------------------------ different directions -------------------------------------- */

    @Test fun `apply different maxspeed for a vehicle in each direction`() {
        verifyAnswer(
            mapOf(),
            ForwardAndBackwardAllSpeedInformation(
                AllSpeedInformation(
                    mapOf(
                        "hgv" to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(20)), null))
                    ),
                    null, null
                ),
                AllSpeedInformation(
                    mapOf(
                        "hgv" to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(30)), null))
                    ),
                    null, null
                ),
                null
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:hgv:forward", "20"),
                StringMapEntryAdd("maxspeed:hgv:backward", "30")
            )
        )
    }

    @Test fun `change single maxspeed for a vehicle to different in each direction`() {
        verifyAnswer(
            mapOf("maxspeed:hgv" to "50"),
            ForwardAndBackwardAllSpeedInformation(
                AllSpeedInformation(
                    mapOf(
                        "hgv" to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(20)), null))
                    ),
                    null, null
                ),
                AllSpeedInformation(
                    mapOf(
                        "hgv" to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(30)), null))
                    ),
                    null, null
                ),
                null
            ),
            arrayOf(
                StringMapEntryDelete("maxspeed:hgv", "50"),
                StringMapEntryAdd("maxspeed:hgv:forward", "20"),
                StringMapEntryAdd("maxspeed:hgv:backward", "30")
            )
        )
    }

    @Test fun `change different maxspeed in each direction for a vehicle to single maxspeed`() {
        verifyAnswer(
            mapOf(
                "maxspeed:hgv:forward" to "50",
                "maxspeed:hgv:backward" to "40"
            ),
            maxspeedBothDirections(
                mapOf(
                    "hgv" to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(30)), null))
                )
            ),
            arrayOf(
                StringMapEntryDelete("maxspeed:hgv:forward", "50"),
                StringMapEntryDelete("maxspeed:hgv:backward", "40"),
                StringMapEntryAdd("maxspeed:hgv", "30")
            )
        )
    }

    /* ------------------------------ different types ------------------------------------------- */

    @Test fun `apply answer to different type tags for different vehicles`() {
        verifyAnswer(
            mapOf(
                "maxspeed:type" to "DE:rural",
                "source:maxspeed:hgv" to "DE:rural"
            ),
            maxspeedBothDirections(
                mapOf(
                    null to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(50)), JustSign)),
                    "hgv" to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(30)), JustSign))
                )
            ),
            arrayOf(
                StringMapEntryDelete("source:maxspeed:hgv", "DE:rural"),
                StringMapEntryAdd("maxspeed", "50"),
                StringMapEntryModify("maxspeed:type", "DE:rural", "sign"),
                StringMapEntryAdd("maxspeed:hgv", "30"),
                StringMapEntryAdd("maxspeed:type:hgv", "sign"),
            )
        )
        verifyAnswer(
            mapOf(
                "zone:maxspeed" to "DE:rural",
                "source:maxspeed:hgv" to "DE:rural",
                "zone:traffic:coach" to "DE:rural"
            ),
            maxspeedBothDirections(
                mapOf(
                    null to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(50)), JustSign)),
                    "hgv" to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(30)), JustSign)),
                    "coach" to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(20)), JustSign)),
                )
            ),
            arrayOf(
                StringMapEntryDelete("zone:maxspeed", "DE:rural"),
                StringMapEntryDelete("zone:traffic:coach", "DE:rural"),
                StringMapEntryAdd("maxspeed", "50"),
                StringMapEntryAdd("source:maxspeed", "sign"),
                StringMapEntryAdd("maxspeed:hgv", "30"),
                StringMapEntryModify("source:maxspeed:hgv", "DE:rural", "sign"),
                StringMapEntryAdd("maxspeed:coach", "20"),
                StringMapEntryAdd("source:maxspeed:coach", "sign"),
            )
        )
    }

    /* ------------------------------------------------------------------------------------------ */
    /* ------------------------------ conditional maxspeed -------------------------------------- */
    /* ------------------------------------------------------------------------------------------ */

    /* ------------------------------ apply conditional ----------------------------------------- */

    @Test fun `apply conditional maxspeed`() {
        verifyAnswer(
            mapOf(),
            maxspeedBothDirections(
                mapOf(null to mapOf(Wet to MaxspeedAndType(MaxSpeedSign(Kmh(50)), null)))
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:conditional", "50 @ wet")
            )
        )
        verifyAnswer(
            mapOf(),
            maxspeedBothDirections(
                mapOf(null to mapOf(Wet to MaxspeedAndType(MaxSpeedSign(Mph(50)), null)))
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:conditional", "50 mph @ wet")
            )
        )
        verifyAnswer(
            mapOf(),
            maxspeedBothDirections(
                mapOf(null to mapOf(Snow to MaxspeedAndType(MaxSpeedSign(Kmh(50)), null)))
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:conditional", "50 @ snow")
            )
        )
        verifyAnswer(
            mapOf(),
            maxspeedBothDirections(
                mapOf(null to mapOf(Winter to MaxspeedAndType(MaxSpeedSign(Kmh(50)), null)))
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:conditional", "50 @ winter")
            )
        )
        verifyAnswer(
            mapOf(),
            maxspeedBothDirections(
                mapOf(null to mapOf(Flashing to MaxspeedAndType(MaxSpeedSign(Kmh(50)), null)))
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:conditional", "50 @ flashing")
            )
        )
    }

    @Test fun `apply conditional maxspeed for weight`() {
        verifyAnswer(
            mapOf(),
            maxspeedBothDirections(
                mapOf(null to mapOf(
                    WeightAndComparison(MetricTons(10.0), LESS_THAN) to
                        MaxspeedAndType(MaxSpeedSign(Kmh(50)), null))
                )
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:conditional", "50 @ weight<10")
            )
        )
        verifyAnswer(
            mapOf(),
            maxspeedBothDirections(
                mapOf(null to mapOf(
                    WeightAndComparison(MetricTons(3.5), MORE_THAN) to
                        MaxspeedAndType(MaxSpeedSign(Kmh(50)), null))
                )
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:conditional", "50 @ weight>3.5")
            )
        )
        verifyAnswer(
            mapOf(),
            maxspeedBothDirections(
                mapOf(null to mapOf(
                    WeightAndComparison(MetricTons(20.0), LESS_THAN_OR_EQUAL) to
                        MaxspeedAndType(MaxSpeedSign(Kmh(50)), null))
                )
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:conditional", "50 @ weight<=20")
            )
        )
        verifyAnswer(
            mapOf(),
            maxspeedBothDirections(
                mapOf(null to mapOf(
                    WeightAndComparison(MetricTons(7.5), MORE_THAN_OR_EQUAL) to
                        MaxspeedAndType(MaxSpeedSign(Kmh(50)), null))
                )
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:conditional", "50 @ weight>=7.5")
            )
        )
    }

    @Test fun `apply conditional maxspeed for weight with units`() {
        verifyAnswer(
            mapOf(),
            maxspeedBothDirections(
                mapOf(null to mapOf(
                    WeightAndComparison(ShortTons(7.5), LESS_THAN) to
                        MaxspeedAndType(MaxSpeedSign(Kmh(50)), null))
                )
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:conditional", "50 @ weight<7.5 st")
            )
        )
        verifyAnswer(
            mapOf(),
            maxspeedBothDirections(
                mapOf(null to mapOf(
                    WeightAndComparison(ImperialPounds(4000), LESS_THAN) to
                        MaxspeedAndType(MaxSpeedSign(Kmh(50)), null))
                )
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:conditional", "50 @ weight<4000 lbs")
            )
        )
        verifyAnswer(
            mapOf(),
            maxspeedBothDirections(
                mapOf(null to mapOf(
                    WeightAndComparison(Kilograms(750), LESS_THAN) to
                        MaxspeedAndType(MaxSpeedSign(Kmh(50)), null))
                )
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:conditional", "50 @ weight<750 kg")
            )
        )
    }

    @Test fun `apply maxspeed with multiple conditions`() {
        verifyAnswer(
            mapOf(),
            maxspeedBothDirections(
                mapOf(null to mapOf(
                    Wet to MaxspeedAndType(MaxSpeedSign(Kmh(50)), null),
                    Snow to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null)
                ))
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:conditional", "50 @ wet; 40 @ snow")
            )
        )
        verifyAnswer(
            mapOf(),
            maxspeedBothDirections(
                mapOf(null to mapOf(
                    WeightAndComparison(MetricTons(10.0), LESS_THAN) to
                        MaxspeedAndType(MaxSpeedSign(Kmh(60)), null),
                    Wet to MaxspeedAndType(MaxSpeedSign(Kmh(50)), null),
                    Snow to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null),
                    Winter to MaxspeedAndType(MaxSpeedSign(Kmh(30)), null),
                ))
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:conditional", "60 @ weight<10; 50 @ wet; 40 @ snow; 30 @ winter")
            )
        )
    }

    @Test fun `apply conditional maxspeed in one direction`() {
        verifyAnswer(
            mapOf(),
            ForwardAndBackwardAllSpeedInformation(
                AllSpeedInformation(
                    mapOf(null to mapOf(Wet to MaxspeedAndType(MaxSpeedSign(Kmh(50)), null))),
                    null, null
                ),
                null, null
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:forward:conditional", "50 @ wet")
            )
        )
        verifyAnswer(
            mapOf(),
            ForwardAndBackwardAllSpeedInformation(
                null,
                AllSpeedInformation(
                    mapOf(null to mapOf(Wet to MaxspeedAndType(MaxSpeedSign(Kmh(50)), null))),
                    null, null
                ),
                null
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:backward:conditional", "50 @ wet")
            )
        )
    }

    @Test fun `apply conditional maxspeed different in each direction`() {
        verifyAnswer(
            mapOf(),
            ForwardAndBackwardAllSpeedInformation(
                AllSpeedInformation(
                    mapOf(null to mapOf(Wet to MaxspeedAndType(MaxSpeedSign(Kmh(50)), null))),
                    null, null
                ),
                AllSpeedInformation(
                    mapOf(null to mapOf(Snow to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null))),
                    null, null
                ),
                null
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:forward:conditional", "50 @ wet"),
                StringMapEntryAdd("maxspeed:backward:conditional", "40 @ snow")
            )
        )
    }

    @Test fun `apply conditional maxspeed for a vehicle`() {
        verifyAnswer(
            mapOf(),
            maxspeedBothDirections(
                mapOf("hgv" to mapOf(Wet to MaxspeedAndType(MaxSpeedSign(Kmh(50)), null)))
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:hgv:conditional", "50 @ wet")
            )
        )
        verifyAnswer(
            mapOf(),
            ForwardAndBackwardAllSpeedInformation(
                AllSpeedInformation(
                    mapOf("hgv" to mapOf(Wet to MaxspeedAndType(MaxSpeedSign(Kmh(50)), null))),
                    null, null
                ),
                AllSpeedInformation(
                    mapOf("coach" to mapOf(Snow to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null))),
                    null, null
                ),
                null
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:hgv:forward:conditional", "50 @ wet"),
                StringMapEntryAdd("maxspeed:coach:backward:conditional", "40 @ snow")
            )
        )
    }

    /* ------------------------------ change conditional ---------------------------------------- */

    @Test fun `change conditional maxspeed`() {
        verifyAnswer(
            mapOf("maxspeed:conditional" to "70 @ wet"),
            maxspeedBothDirections(
                mapOf(null to mapOf(Wet to MaxspeedAndType(MaxSpeedSign(Kmh(50)), null)))
            ),
            arrayOf(
                StringMapEntryModify("maxspeed:conditional", "70 @ wet", "50 @ wet")
            )
        )
        verifyAnswer(
            mapOf("maxspeed:conditional" to "70 @ snow"),
            maxspeedBothDirections(
                mapOf(null to mapOf(
                    WeightAndComparison(MetricTons(10.0), MORE_THAN) to
                        MaxspeedAndType(MaxSpeedSign(Kmh(50)), null))
                )
            ),
            arrayOf(
                StringMapEntryModify("maxspeed:conditional", "70 @ snow", "50 @ weight>10")
            )
        )
    }

    @Test fun `applying the same conditions does not change conditional value`() {
        verifyAnswer(
            mapOf("maxspeed:conditional" to "30 @ weight >= 10"),
            maxspeedBothDirections(
                mapOf(null to mapOf(
                    WeightAndComparison(MetricTons(10.0), MORE_THAN_OR_EQUAL) to
                        MaxspeedAndType(MaxSpeedSign(Kmh(30)), null)
                ))
            ),
            arrayOf(
                StringMapEntryModify("maxspeed:conditional", "30 @ weight >= 10", "30 @ weight >= 10"),
                StringMapEntryAdd("check_date:maxspeed", nowAsCheckDateString())
            )
        )
        verifyAnswer(
            mapOf("maxspeed:conditional" to "60 @ weight<10; 50 @ wet; 40 @ snow; 30 @ winter"),
            maxspeedBothDirections(
                mapOf(null to mapOf(
                    WeightAndComparison(MetricTons(10.0), LESS_THAN) to
                        MaxspeedAndType(MaxSpeedSign(Kmh(60)), null),
                    Wet to MaxspeedAndType(MaxSpeedSign(Kmh(50)), null),
                    Snow to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null),
                    Winter to MaxspeedAndType(MaxSpeedSign(Kmh(30)), null),
                ))
            ),
            arrayOf(
                StringMapEntryModify(
                    "maxspeed:conditional",
                    "60 @ weight<10; 50 @ wet; 40 @ snow; 30 @ winter",
                    "60 @ weight<10; 50 @ wet; 40 @ snow; 30 @ winter"),
                StringMapEntryAdd("check_date:maxspeed", nowAsCheckDateString())
            )
        )
        verifyAnswer(
            mapOf("maxspeed:conditional" to "40 @ snow; 30 @ winter; 60 @ weight < 10; 50 @ wet"),
            maxspeedBothDirections(
                mapOf(null to mapOf(
                    WeightAndComparison(MetricTons(10.0), LESS_THAN) to
                        MaxspeedAndType(MaxSpeedSign(Kmh(60)), null),
                    Wet to MaxspeedAndType(MaxSpeedSign(Kmh(50)), null),
                    Snow to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null),
                    Winter to MaxspeedAndType(MaxSpeedSign(Kmh(30)), null),
                ))
            ),
            arrayOf(
                StringMapEntryModify(
                    "maxspeed:conditional",
                    "40 @ snow; 30 @ winter; 60 @ weight < 10; 50 @ wet",
                    "40 @ snow; 30 @ winter; 60 @ weight < 10; 50 @ wet"),
                StringMapEntryAdd("check_date:maxspeed", nowAsCheckDateString())
            )
        )
    }

    /* ------------------------------------------------------------------------------------------ */
    /* ------------------------------ bit of everything ----------------------------------------- */
    /* ------------------------------------------------------------------------------------------ */

    @Test fun `bit of everything`() {
        verifyAnswer(
            mapOf(),
            ForwardAndBackwardAllSpeedInformation(
                AllSpeedInformation(
                    mapOf(
                        null to mapOf(
                            NoCondition to MaxspeedAndType(MaxSpeedIsNone, JustSign),
                            Wet to MaxspeedAndType(MaxSpeedSign(Kmh(100)), null)
                        ),
                        "hgv" to mapOf(
                            WeightAndComparison(MetricTons(20.0), MORE_THAN_OR_EQUAL) to
                                MaxspeedAndType(MaxSpeedSign(Kmh(80)), null)
                        ),
                        "trailer" to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(40)), null))
                    ),
                    AdvisorySpeedSign(Kmh(120)),
                    true
                ),
                AllSpeedInformation(
                    mapOf(
                        null to mapOf(
                            NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(130)), JustSign),
                            Wet to MaxspeedAndType(MaxSpeedSign(Kmh(100)), null)
                        ),
                        "hgv" to mapOf(
                            WeightAndComparison(MetricTons(3.5), MORE_THAN) to
                                MaxspeedAndType(MaxSpeedSign(Kmh(70)), null)
                        ),
                        "coach" to mapOf(NoCondition to MaxspeedAndType(MaxSpeedSign(Kmh(50)), null))
                    ),
                    AdvisorySpeedSign(Kmh(120)),
                    null
                ),
                null
            ),
            arrayOf(
                StringMapEntryAdd("maxspeed:forward", "none"),
                StringMapEntryAdd("maxspeed:type:forward", "sign"),
                StringMapEntryAdd("maxspeed:backward", "130"),
                StringMapEntryAdd("maxspeed:type:backward", "sign"),
                StringMapEntryAdd("maxspeed:conditional", "100 @ wet"),
                StringMapEntryAdd("maxspeed:hgv:forward:conditional", "80 @ weight>=20"),
                StringMapEntryAdd("maxspeed:hgv:backward:conditional", "70 @ weight>3.5"),
                StringMapEntryAdd("maxspeed:trailer:forward", "40"),
                StringMapEntryAdd("maxspeed:coach:backward", "50"),
                StringMapEntryAdd("maxspeed:advisory", "120"),
                StringMapEntryAdd("maxspeed:type:advisory", "sign"),
                StringMapEntryAdd("maxspeed:variable:forward", "yes")
            )
        )
    }
}

private fun verifyAnswer(tags: Map<String, String>, answer: ForwardAndBackwardAllSpeedInformation, expectedChanges: Array<StringMapEntryChange>) {
    val cb = StringMapChangesBuilder(tags)
    answer.applyTo(cb)
    val changes = cb.create().changes
    Assertions.assertThat(changes).containsExactlyInAnyOrder(*expectedChanges)
}

private fun bareMaxspeedBothDirections(explicit: MaxSpeedAnswer?, type: MaxSpeedAnswer?, wholeRoadType: MaxSpeedAnswer? = null) =
    bareMaxspeedAndType(
        MaxspeedAndType(explicit, type),
        MaxspeedAndType(explicit, type),
        wholeRoadType
    )

private fun bareMaxspeedAndType(forward: MaxspeedAndType?, backward: MaxspeedAndType?, wholeRoadType: MaxSpeedAnswer? = null) =
    ForwardAndBackwardAllSpeedInformation(
        AllSpeedInformation(mapOf(null to mapOf(NoCondition to forward)), null, null),
        AllSpeedInformation(mapOf(null to mapOf(NoCondition to backward)), null, null),
        wholeRoadType
    )

private fun maxspeedBothDirections(
    vehicles: Map<String?, Map<Condition, MaxspeedAndType?>?>?,
    advisory: AdvisorySpeedSign? = null,
    variable: Boolean? = null,
    wholeRoadType: MaxSpeedAnswer? = null
) =
    ForwardAndBackwardAllSpeedInformation(
        AllSpeedInformation(vehicles, advisory, variable),
        AllSpeedInformation(vehicles, advisory, variable),
        wholeRoadType
    )
