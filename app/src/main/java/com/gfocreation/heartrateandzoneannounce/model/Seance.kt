package com.gfocreation.heartrateandzoneannounce.model

import com.gfocreation.heartrateandzoneannounce.ui.newId


sealed class BlocSeance {
    abstract val id: Int
    abstract val blocType: BlocType
}

enum class BlocType {
    ETAPE,
    GROUP
}

data class Etape(
    override val id: Int,
    val durationMillis: Long,
    val description: String,
    val zoneId: Int,
    override val blocType: BlocType = BlocType.ETAPE
) : BlocSeance()

data class GroupRepeats(
    override val id: Int,
    val repeats: Int,
    val ignoreLastStepOnLastRepeat: Boolean = false,
    val children: List<BlocSeance>,
    override val blocType: BlocType = BlocType.GROUP
) : BlocSeance()


data class Seance(
    val id: Int,
    val name: String,
    val description: String? = null,
    val dureeRealMillis: Long,
    val blocks: List<BlocSeance> = emptyList()
)


fun GroupRepeats.withChildren(newChildren: List<BlocSeance>): GroupRepeats {
    val canIgnoreLast =
        newChildren.lastOrNull() is Etape

    return copy(
        children = newChildren,
        ignoreLastStepOnLastRepeat =
            if (canIgnoreLast) ignoreLastStepOnLastRepeat else false
    )
}

fun BlocSeance.deepCopy(): BlocSeance =
    when (this) {
        is Etape -> copy(
            id = newId()
        )

        is GroupRepeats -> copy(
            id = newId(),
            children = children.map { it.deepCopy() }
        )
    }
