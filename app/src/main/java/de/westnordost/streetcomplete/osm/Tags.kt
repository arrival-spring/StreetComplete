package de.westnordost.streetcomplete.osm

import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder

typealias Tags = StringMapChangesBuilder

fun Tags.expandSides(key: String, postfix: String? = null, includeBareTag: Boolean = true) {
    val post = if (postfix != null) ":$postfix" else ""
    val both = get("$key:both$post") ?: (if (includeBareTag) get("$key$post") else null)
    if (both != null) {
        // *:left/right is seen as more specific/correct in case the two contradict each other
        if (!containsKey("$key:left$post")) set("$key:left$post", both)
        if (!containsKey("$key:right$post")) set("$key:right$post", both)
    }
    remove("$key:both$post")
    if (includeBareTag) remove("$key$post")
}

fun Tags.mergeSides(key: String, postfix: String? = null) {
    val post = if (postfix != null) ":$postfix" else ""
    val left = get("$key:left$post")
    val right = get("$key:right$post")
    if (left != null && left == right) {
        set("$key:both$post", left)
        remove("$key:left$post")
        remove("$key:right$post")
    }
}

fun Tags.expandDirections(key: String, postfix: String? = null) {
    val post = if (postfix != null) ":$postfix" else ""
    val both = get("$key$post")
    if (both != null) {
        // *:forward/backward is seen as more specific/correct in case the two contradict each other
        if (!containsKey("$key:forward$post")) set("$key:forward$post", both)
        if (!containsKey("$key:backward$post")) set("$key:backward$post", both)
    }
    remove("$key$post")
}

fun Tags.mergeDirections(key: String, postfix: String? = null) {
    val post = if (postfix != null) ":$postfix" else ""
    val forward = get("$key:forward$post")
    val backward = get("$key:backward$post")
    if (forward != null && forward == backward) {
        set("$key$post", forward)
        remove("$key:forward$post")
        remove("$key:backward$post")
    }
}
