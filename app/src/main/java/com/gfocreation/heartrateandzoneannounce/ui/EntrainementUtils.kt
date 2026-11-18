package com.gfocreation.heartrateandzoneannounce.ui

import com.gfocreation.heartrateandzoneannounce.model.*
import kotlin.random.Random


/**
 * Retourne la liste "dépliée" (flattened) d'étapes simples (Etape)
 * en évaluant les GroupRepeats (répétés N fois).
 */
fun flattenBlocks(blocks: List<BlocSeance>): List<Etape> {
    val result = mutableListOf<Etape>()

    fun expand(b: BlocSeance) {
        when (b) {
            is Etape -> result += b
            is GroupRepeats -> {
                val r = b.repeats.coerceAtLeast(0)
                repeat(r) {
                    b.children.forEach { expand(it) }
                }
            }
        }
    }

    blocks.forEach { expand(it) }
    return result
}

//data class EtapeRun(
//    val etape: Etape,
//    val label: String   // ex: "Bloc 1 · répétition 2/4"
//)
data class EtapeRun(
    val etape: Etape,          // référence à l’étape originale
    val repeatIndex: Int,      // 1-based
    val repeatCount: Int,      // total
    val label: String? = null  // "Répétition 2/4"
)

//fun flattenBlocksWithLabels(
//    blocks: List<BlocSeance>
//): List<EtapeRun> {
//
//    val result = mutableListOf<EtapeRun>()
//    // var blocCounter = 1
//
//    fun walk(
//        bloc: BlocSeance,
//        currentLabelPrefix: String? = null,
//        repeatIndex: Int = 1,
//        repeatCount: Int = 1
//    ) {
//        when (bloc) {
//            is Etape -> {
//                val label = currentLabelPrefix ?: ""
//                result += EtapeRun(
//                    etape = bloc,
//                    repeatIndex = repeatIndex,
//                    repeatCount = repeatCount,
//                    label = label
//                )
//            }
//
//            is GroupRepeats -> {
//                for (i in 1..bloc.repeats) {
//                    bloc.children.forEach { child ->
//                        walk(
//                            bloc = child,
//                            currentLabelPrefix = "x$i/${bloc.repeats}",       // "Répétition $i/${bloc.repeats}",
//                            repeatIndex = i,
//                            repeatCount = bloc.repeats
//                        )
//                    }
//                }
//
//            }
//        }
//    }
//
//    blocks.forEach { walk(it) }
//    return result
//}


fun flattenBlocksWithLabels(
    blocks: List<BlocSeance>
): List<EtapeRun> {

    val result = mutableListOf<EtapeRun>()

    fun walk(
        bloc: BlocSeance,
        repeatStack: List<Pair<Int, Int>> = emptyList()
    ) {
        when (bloc) {

            is Etape -> {
                val label = if (repeatStack.isNotEmpty()) {
                    repeatStack.joinToString(" ") { (i, total) ->
                        "x$i/$total"
                    }
                } else null

                // Pour compatibilité avec le reste du code :
                // on prend le niveau le plus interne pour repeatIndex / repeatCount
                val (repeatIndex, repeatCount) =
                    repeatStack.lastOrNull() ?: (1 to 1)

                result += EtapeRun(
                    etape = bloc,
                    repeatIndex = repeatIndex,
                    repeatCount = repeatCount,
                    label = label
                )
            }

//            is GroupRepeats -> {
//                for (i in 1..bloc.repeats) {
//                    val newStack = repeatStack + (i to bloc.repeats)
//                    bloc.children.forEach { child ->
//                        walk(
//                            bloc = child,
//                            repeatStack = newStack
//                        )
//                    }
//                }
//            }
            is GroupRepeats -> {
                for (i in 1..bloc.repeats) {

                    val isLastRepeat = i == bloc.repeats

                    val childrenToWalk =
                        if (
                            isLastRepeat &&
                            bloc.ignoreLastStepOnLastRepeat &&
                            bloc.children.lastOrNull() is Etape
                        ) {
                            bloc.children.dropLast(1)
                        } else {
                            bloc.children
                        }

                    val newStack = repeatStack + (i to bloc.repeats)

                    childrenToWalk.forEach { child ->
                        walk(
                            bloc = child,
                            repeatStack = newStack
                        )
                    }
                }
            }

        }
    }

    blocks.forEach { walk(it) }
    return result
}


fun newId(): Int = Random.nextInt(1, Int.MAX_VALUE)