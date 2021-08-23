package de.westnordost.streetcomplete.quests.show_poi

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.osmquests.OsmFilterQuestType
import de.westnordost.streetcomplete.quests.NoAnswerFragment

class ShowTrafficStuff : OsmFilterQuestType<Boolean>() {
    override val elementFilter = """
        nodes, ways, relations with
         barrier and barrier !~ wall|fence|retaining_wall|hedge
         or traffic_calming
         or crossing
         or entrance
         or highway = crossing
         or railway = crossing
         or footway = crossing
         or cycleway = crossing
         or amenity = taxi
         or amenity = parking
         or public_transport
         or amenity = motorcycle_parking
         """

    override val commitMessage = "I hope this does not get committed"
    override val wikiLink = "nope"
    override val icon = R.drawable.ic_quest_railway // replace later, but need own icon...
    override val dotColor = "deepskyblue"

    override fun getTitle(tags: Map<String, String>) =
        R.string.quest_thisIsOther_title

    override fun getTitleArgs(tags: Map<String, String>, featureName: Lazy<String?>): Array<String> {
        val name = if (!tags["crossing"].isNullOrBlank() && !tags["traffic_calming"].isNullOrBlank())
            tags.entries
        else
            featureName.value ?: tags.entries
        return arrayOf(name.toString())
    }

    override fun createForm() = NoAnswerFragment()

    override fun applyAnswerTo(answer: Boolean, changes: StringMapChangesBuilder) {
    }
}