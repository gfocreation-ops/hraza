package com.gfocreation.heartrateandzoneannounce.misc

import com.gfocreation.heartrateandzoneannounce.model.CANONICAL_ZONES
import com.gfocreation.heartrateandzoneannounce.model.Zone
import kotlin.math.roundToInt

/**
 * Construit la liste de Zone (model.Zone) à partir des CANONICAL_ZONES (ZoneSpec).
 *
 * On suppose que CANONICAL_ZONES contient des éléments avec au moins :
 *  - idIndex: Int
 *  - idKey: String
 *  - shortLabel: String
 *  - pctRange: Pair<Int,Int>
 *  - description: String
 *
 * Les fonctions renvoient une List<Zone> adaptée à la data class Zone de ton projet.
 */

/** Compute zones from fcMax using CANONICAL_ZONES (0..6) */
fun computeZonesFromFcMax(fcMax: Int): List<Zone> {
    return CANONICAL_ZONES.map { spec ->
        val (minPct, maxPct) = spec.pctRange
        val minBpm = if (minPct <= 0) 0 else ((minPct / 100.0) * fcMax).roundToInt().coerceAtLeast(0)
        val maxBpm = ((maxPct / 100.0) * fcMax).roundToInt().coerceAtMost(250)
        Zone(
            id = spec.idIndex,
            key = spec.idKey,
            label = spec.shortLabel,
            minBpm = minBpm,
            maxBpm = maxBpm,
            minPercent = minPct,
            maxPercent = maxPct,
            description = spec.description ?: ""
        )
    }
}

/** Karvonen-like mapping (fcMin used as lower clamp) */
fun computeZonesKarvonen(fcMin: Int, fcMax: Int): List<Zone> {
    val reserve = (fcMax - fcMin).coerceAtLeast(0)
    return CANONICAL_ZONES.map { spec ->
        val (minPct, maxPct) = spec.pctRange
        // calcule sur la réserve + ajout du fcMin
        val minBpm = fcMin + ((minPct / 100.0) * reserve).roundToInt()
        val maxBpm = fcMin + ((maxPct / 100.0) * reserve).roundToInt()
        Zone(
            id = spec.idIndex,
            key = spec.idKey,
            label = spec.shortLabel,
            minBpm = minBpm.coerceAtLeast(0),
            maxBpm = maxBpm.coerceAtMost(250),
            minPercent = minPct,
            maxPercent = maxPct,
            description = spec.description ?: ""
        )
    }
}


/**
 * Manual CSV:
 * - if values.size == 6 -> expected [z1, z1-2, z2-3, z3-4, z4-5a, fc_max]
 *      -> builds zones 0..6 with last zone6 being fcmax..fcmax (fallback)
 * - if values.size >=7 -> expected [z1, z1-2, z2-3, z3-4, z4-5a, z5b, fc_max]
 *      -> builds zones 0..6 using the provided boundaries
 */
fun computeZonesFromManual(values: List<Int>): List<Zone> {
    if (values.size < 6) return computeZonesFromFcMax(190)

    // helper to safely fetch pctRange/description from canonical spec by index
    fun specAt(i: Int) = CANONICAL_ZONES.getOrNull(i)

    return if (values.size == 6) {
        val z1 = values[0]
        val z12 = values[1]
        val z23 = values[2]
        val z34 = values[3]
        val z45a = values[4]
        val fcmax = values[5]

        val list = mutableListOf<Zone>()
        // zone 0 : < z1
        val s0 = specAt(0)
        list.add(
            Zone(
                id = s0?.idIndex ?: 0,
                key = s0?.idKey ?: "0",
                label = s0?.shortLabel ?: "Repos",
                minBpm = 0,
                maxBpm = (z1 - 1).coerceAtLeast(0),
                minPercent = s0?.pctRange?.first ?: 0,
                maxPercent = s0?.pctRange?.second ?: 49,
                description = s0?.description ?: ""
            )
        )
        // zone 1..5 (5 merges 5a/5b)
        val specs = (1..5).map { specAt(it) }
        list.add(
            Zone(
                id = specs[0]?.idIndex ?: 1,
                key = specs[0]?.idKey ?: "1",
                label = specs[0]?.shortLabel ?: "Zone 1",
                minBpm = z1,
                maxBpm = (z12 - 1).coerceAtLeast(z1),
                minPercent = specs[0]?.pctRange?.first ?: 50,
                maxPercent = specs[0]?.pctRange?.second ?: 60,
                description = specs[0]?.description ?: ""
            )
        )
        list.add(
            Zone(
                id = specs[1]?.idIndex ?: 2,
                key = specs[1]?.idKey ?: "2",
                label = specs[1]?.shortLabel ?: "Zone 2",
                minBpm = z12,
                maxBpm = (z23 - 1).coerceAtLeast(z12),
                minPercent = specs[1]?.pctRange?.first ?: 60,
                maxPercent = specs[1]?.pctRange?.second ?: 70,
                description = specs[1]?.description ?: ""
            )
        )
        list.add(
            Zone(
                id = specs[2]?.idIndex ?: 3,
                key = specs[2]?.idKey ?: "3",
                label = specs[2]?.shortLabel ?: "Zone 3",
                minBpm = z23,
                maxBpm = (z34 - 1).coerceAtLeast(z23),
                minPercent = specs[2]?.pctRange?.first ?: 70,
                maxPercent = specs[2]?.pctRange?.second ?: 80,
                description = specs[2]?.description ?: ""
            )
        )
        list.add(
            Zone(
                id = specs[3]?.idIndex ?: 4,
                key = specs[3]?.idKey ?: "4",
                label = specs[3]?.shortLabel ?: "Zone 4",
                minBpm = z34,
                maxBpm = (z45a - 1).coerceAtLeast(z34),
                minPercent = specs[3]?.pctRange?.first ?: 80,
                maxPercent = specs[3]?.pctRange?.second ?: 90,
                description = specs[3]?.description ?: ""
            )
        )
        // zone 5 (from z45a to fcmax)
        val s5 = specAt(5)
        list.add(
            Zone(
                id = s5?.idIndex ?: 5,
                key = s5?.idKey ?: "5",
                label = s5?.shortLabel ?: "Zone 5",
                minBpm = z45a,
                maxBpm = fcmax.coerceAtLeast(z45a),
                minPercent = s5?.pctRange?.first ?: 90,
                maxPercent = s5?.pctRange?.second ?: 100,
                description = s5?.description ?: ""
            )
        )
        // zone 6 (fallback single-point at fcmax)
        val s6 = specAt(6)
        list.add(
            Zone(
                id = s6?.idIndex ?: 6,
                key = s6?.idKey ?: "6",
                label = s6?.shortLabel ?: "Zone 5b",
                minBpm = fcmax,
                maxBpm = fcmax,
                minPercent = s6?.pctRange?.first ?: 95,
                maxPercent = s6?.pctRange?.second ?: 100,
                description = s6?.description ?: ""
            )
        )

        list
    } else {
        // 7 or more values provided: z1, z12, z23, z34, z45a, z5b, fcmax
        val z1 = values[0]
        val z12 = values[1]
        val z23 = values[2]
        val z34 = values[3]
        val z45a = values[4]
        val z5b = values[5]
        val fcmax = values[6]

        val list = mutableListOf<Zone>()
        // zone 0
        val s0 = specAt(0)
        list.add(
            Zone(
                id = s0?.idIndex ?: 0,
                key = s0?.idKey ?: "0",
                label = s0?.shortLabel ?: "Repos",
                minBpm = 0,
                maxBpm = (z1 - 1).coerceAtLeast(0),
                minPercent = s0?.pctRange?.first ?: 0,
                maxPercent = s0?.pctRange?.second ?: 49,
                description = s0?.description ?: ""
            )
        )
        // zone1
        val s1 = specAt(1)
        list.add(
            Zone(
                id = s1?.idIndex ?: 1,
                key = s1?.idKey ?: "1",
                label = s1?.shortLabel ?: "Zone 1",
                minBpm = z1,
                maxBpm = (z12 - 1).coerceAtLeast(z1),
                minPercent = s1?.pctRange?.first ?: 50,
                maxPercent = s1?.pctRange?.second ?: 60,
                description = s1?.description ?: ""
            )
        )
        // zone2
        val s2 = specAt(2)
        list.add(
            Zone(
                id = s2?.idIndex ?: 2,
                key = s2?.idKey ?: "2",
                label = s2?.shortLabel ?: "Zone 2",
                minBpm = z12,
                maxBpm = (z23 - 1).coerceAtLeast(z12),
                minPercent = s2?.pctRange?.first ?: 60,
                maxPercent = s2?.pctRange?.second ?: 70,
                description = s2?.description ?: ""
            )
        )
        // zone3
        val s3 = specAt(3)
        list.add(
            Zone(
                id = s3?.idIndex ?: 3,
                key = s3?.idKey ?: "3",
                label = s3?.shortLabel ?: "Zone 3",
                minBpm = z23,
                maxBpm = (z34 - 1).coerceAtLeast(z23),
                minPercent = s3?.pctRange?.first ?: 70,
                maxPercent = s3?.pctRange?.second ?: 80,
                description = s3?.description ?: ""
            )
        )
        // zone4
        val s4 = specAt(4)
        list.add(
            Zone(
                id = s4?.idIndex ?: 4,
                key = s4?.idKey ?: "4",
                label = s4?.shortLabel ?: "Zone 4",
                minBpm = z34,
                maxBpm = (z45a - 1).coerceAtLeast(z34),
                minPercent = s4?.pctRange?.first ?: 80,
                maxPercent = s4?.pctRange?.second ?: 90,
                description = s4?.description ?: ""
            )
        )
        // zone5 (5a)
        val s5 = specAt(5)
        list.add(
            Zone(
                id = s5?.idIndex ?: 5,
                key = s5?.idKey ?: "5",
                label = s5?.shortLabel ?: "Zone 5a",
                minBpm = z45a,
                maxBpm = (z5b - 1).coerceAtLeast(z45a),
                minPercent = s5?.pctRange?.first ?: 90,
                maxPercent = s5?.pctRange?.second ?: 95,
                description = s5?.description ?: ""
            )
        )
        // zone6 (5b)
        val s6 = specAt(6)
        list.add(
            Zone(
                id = s6?.idIndex ?: 6,
                key = s6?.idKey ?: "6",
                label = s6?.shortLabel ?: "Zone 5b",
                minBpm = z5b,
                maxBpm = fcmax.coerceAtLeast(z5b),
                minPercent = s6?.pctRange?.first ?: 95,
                maxPercent = s6?.pctRange?.second ?: 100,
                description = s6?.description ?: ""
            )
        )

        list
    }
}
