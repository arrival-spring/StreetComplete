package de.westnordost.streetcomplete.osm.maxspeed

import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryChange
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryDelete
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryModify
import de.westnordost.streetcomplete.quests.max_speed.Kmh
import de.westnordost.streetcomplete.quests.max_speed.Mph
import org.assertj.core.api.Assertions
import org.junit.Test

class MaxspeedCreatorKtTests {
    @Test fun `apply nothing applies nothing`() {
        verifyAnswer(
            mapOf(),
            MaxspeedAndType(null, null),
            arrayOf()
        )
    }

    @Test fun `apply plain maxspeed`() {
        verifyAnswer(
            mapOf(),
            MaxspeedAndType(MaxSpeedSign(Kmh(20)), null),
            arrayOf(StringMapEntryAdd("maxspeed", "20"))
        )
        verifyAnswer(
            mapOf(),
            MaxspeedAndType(MaxSpeedSign(Mph(20)), null),
            arrayOf(StringMapEntryAdd("maxspeed", "20 mph"))
        )
    }

    @Test fun `apply plain maxspeed type`() {
        verifyAnswer(
            mapOf(),
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", "urban", null)),
            arrayOf(StringMapEntryAdd("maxspeed:type", "DE:urban"))
        )
        verifyAnswer(
            mapOf(),
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", "rural", null)),
            arrayOf(StringMapEntryAdd("maxspeed:type", "DE:rural"))
        )
    }

    @Test fun `apply zone maxspeed`() {
        verifyAnswer(
            mapOf(),
            MaxspeedAndType(null, MaxSpeedZone(Kmh(20), "DE", "zone20")),
            arrayOf(
                StringMapEntryAdd("maxspeed:type", "DE:zone20"),
                StringMapEntryAdd("maxspeed", "20")
            )
        )
        verifyAnswer(
            mapOf(),
            MaxspeedAndType(null, MaxSpeedZone(Mph(20), "GB", "zone20")),
            arrayOf(
                StringMapEntryAdd("maxspeed:type", "GB:zone20"),
                StringMapEntryAdd("maxspeed", "20 mph")
            )
        )
    }

    @Test fun `apply signed maxspeed`() {
        verifyAnswer(
            mapOf(),
            MaxspeedAndType(MaxSpeedSign(Kmh(20)), JustSign),
            arrayOf(
                StringMapEntryAdd("maxspeed", "20"),
                StringMapEntryAdd("maxspeed:type", "sign")
            )
        )
        verifyAnswer(
            mapOf(),
            MaxspeedAndType(MaxSpeedSign(Mph(20)), JustSign),
            arrayOf(
                StringMapEntryAdd("maxspeed", "20 mph"),
                StringMapEntryAdd("maxspeed:type", "sign")
            )
        )
    }

    @Test fun `respect previously used type tag`() {
        verifyAnswer(
            mapOf("source:maxspeed" to "sign"),
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", "urban", null)),
            arrayOf(StringMapEntryModify("source:maxspeed", "sign", "DE:urban"))
        )
        verifyAnswer(
            mapOf("zone:maxspeed" to "DE:rural"),
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", "urban", null)),
            arrayOf(StringMapEntryModify("zone:maxspeed", "DE:rural", "DE:urban"))
        )
        verifyAnswer(
            mapOf("zone:traffic" to "DE:rural"),
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", "urban", null)),
            arrayOf(StringMapEntryModify("zone:traffic", "DE:rural", "DE:urban"))
        )
        verifyAnswer(
            mapOf("source:maxspeed" to "sign"),
            MaxspeedAndType(null, MaxSpeedZone(Kmh(20), "DE", "zone20")),
            arrayOf(
                StringMapEntryModify("source:maxspeed", "sign", "DE:zone20"),
                StringMapEntryAdd("maxspeed", "20")
            )
        )
    }

    @Test fun `do not use source_maxspeed if previous value was not a maxspeed type`() {
        verifyAnswer(
            mapOf(
                "source:maxspeed" to "survey",
                "maxspeed" to "60"
            ),
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", "urban", null)),
            arrayOf(
                StringMapEntryDelete("maxspeed", "60"),
                StringMapEntryAdd("maxspeed:type", "DE:urban")
            )
        )
        verifyAnswer(
            mapOf(
                "source:maxspeed" to "survey",
                "zone:maxspeed" to "DE:30",
                "maxspeed" to "30"
            ),
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", "urban", null)),
            arrayOf(
                StringMapEntryDelete("maxspeed", "30"),
                StringMapEntryModify("zone:maxspeed", "DE:30", "DE:urban")
            )
        )
        verifyAnswer(
            mapOf(
                "source:maxspeed" to "survey",
                "zone:traffic" to "DE:rural",
                "maxspeed" to "60"
            ),
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", "urban", null)),
            arrayOf(
                StringMapEntryDelete("maxspeed", "60"),
                StringMapEntryModify("zone:traffic", "DE:rural", "DE:urban")
            )
        )
    }

    @Test fun `do not use previously used type tag if changing to sign where sign is not valid for that type`() {
        verifyAnswer(
            mapOf("zone:maxspeed" to "DE:urban"),
            MaxspeedAndType(MaxSpeedSign(Kmh(30)), JustSign),
            arrayOf(
                StringMapEntryDelete("zone:maxspeed", "DE:urban"),
                StringMapEntryAdd("maxspeed:type", "sign"),
                StringMapEntryAdd("maxspeed", "30")
            )
        )
        verifyAnswer(
            mapOf("zone:traffic" to "DE:urban"),
            MaxspeedAndType(MaxSpeedSign(Kmh(30)), JustSign),
            arrayOf(
                StringMapEntryDelete("zone:traffic", "DE:urban"),
                StringMapEntryAdd("maxspeed:type", "sign"),
                StringMapEntryAdd("maxspeed", "30")
            )
        )
    }

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
            MaxspeedAndType(MaxSpeedSign(Kmh(30)), null),
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
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", "urban", null)),
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
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", "rural", null)),
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
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", "rural", null)),
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
            MaxspeedAndType(null, ImplicitMaxSpeed("DE", "rural", null)),
            arrayOf(
                StringMapEntryModify("maxspeed:type", "DE:urban", "DE:rural"),
                StringMapEntryDelete("maxspeed:type:forward", "DE:urban"),
                StringMapEntryDelete("maxspeed:type:backward", "DE:rural"),
            )
        )
    }

    @Test fun `do not change type tags when changing speed if type is not changed`() {
        verifyAnswer(
            mapOf(
                "maxspeed" to "100",
                "source:maxspeed" to "DE:motorway"
            ),
            MaxspeedAndType(MaxSpeedSign(Kmh(110)), null),
            arrayOf(
                StringMapEntryModify("maxspeed", "100", "110"),
            )
        )
        verifyAnswer(
            mapOf(
                "maxspeed" to "100",
                "source:maxspeed" to "survey",
                "maxspeed:type" to "DE:motorway"
            ),
            MaxspeedAndType(MaxSpeedSign(Kmh(110)), null),
            arrayOf(
                StringMapEntryModify("maxspeed", "100", "110"),
            )
        )
    }
}

private fun verifyAnswer(tags: Map<String, String>, answer: MaxspeedAndType, expectedChanges: Array<StringMapEntryChange>) {
    val cb = StringMapChangesBuilder(tags)
    answer.applyTo(cb)
    val changes = cb.create().changes
    Assertions.assertThat(changes).containsExactlyInAnyOrder(*expectedChanges)
}
