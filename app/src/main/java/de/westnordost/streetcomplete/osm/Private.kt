package de.westnordost.streetcomplete.osm

import de.westnordost.streetcomplete.data.elementfilter.toElementFilterExpression
import de.westnordost.streetcomplete.data.osm.mapdata.Element

private val isPrivateOnFootFilter by lazy { """
    nodes, ways, relations with
      access ~ private|no
      and (!foot or foot ~ private|no)
""".toElementFilterExpression() }

fun isPrivateOnFoot(element: Element): Boolean = isPrivateOnFootFilter.matches(element)

private val isPrivateForVehicle by lazy { """
    nodes, ways, relations with
      access ~ private|no
      and (!vehicle or vehicle ~ private|no)
""".toElementFilterExpression() }

fun isPrivateForVehicle(element: Element): Boolean = isPrivateForVehicle.matches(element)

private val isPrivateForMotorVehicle by lazy { """
    nodes, ways, relations with
      access ~ private|no
      and (!motor_vehicle or motor_vehicle ~ private|no)
""".toElementFilterExpression() }

fun isPrivateForMotorVehicle(element: Element): Boolean = isPrivateForMotorVehicle.matches(element)
