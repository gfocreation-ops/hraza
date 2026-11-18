package com.gfocreation.heartrateandzoneannounce.model


import com.gfocreation.heartrateandzoneannounce.misc.ENERGIE_CALCUL_AVEC_LISSAGE
import com.gfocreation.heartrateandzoneannounce.misc.ENERGIE_CALCUL_ZONE_TRANSITION_PCT
import com.gfocreation.heartrateandzoneannounce.misc.USER_DEFAULT_ENRG_U
import com.gfocreation.heartrateandzoneannounce.misc.USER_DEFAULT_NIVEAU
import com.gfocreation.heartrateandzoneannounce.misc.USER_DEFAULT_POIDS_KG
import com.gfocreation.heartrateandzoneannounce.misc.USER_DEFAULT_SEXE
import com.gfocreation.heartrateandzoneannounce.misc.USER_DEFAULT_SPORT_TYPE
import com.gfocreation.heartrateandzoneannounce.misc.bpm2ZoneIdPrc
import com.gfocreation.heartrateandzoneannounce.model.UserPhysioParams.EnergyUnit


class MetabolicStatsAccumulator(
    private val zones: List<Zone>
    //, private var poidsKg: Float
) {
    var kcalTotal = 0.0
        private set

    var glucidesGrammesTotal = 0.0
        private set

    var glucidesGrammesDepuisReset = 0.0
        private set

    var lipidesGrammesTotal = 0.0
        private set

    var poidsKg: Float = USER_DEFAULT_POIDS_KG
    var sexe: UserPhysioParams.Sexe = USER_DEFAULT_SEXE
    var sportType: UserPhysioParams.SportType = USER_DEFAULT_SPORT_TYPE
    var niveau: UserPhysioParams.Niveau = USER_DEFAULT_NIVEAU
    var energyUnit: UserPhysioParams.EnergyUnit = USER_DEFAULT_ENRG_U


    private val apportRatio = 0.70


    fun onSample(bpm: Int, deltaMs: Long) {
        if (bpm <= 0 || deltaMs <= 0) return
        if (poidsKg <= 0f) return

        val zonePos = bpm2ZoneIdPrc(bpm, zones)
        val durationH = deltaMs / 3_600_000.0


        val zoneContribs = smoothZoneContribution(
            zonePos.zone.id,
            zonePos.percentInZone,
            zones
        )


        // MET
        // --- MET lissé
//        var met = zoneContribs.sumOf { (zoneId, weight) ->
//            zoneToMet(zoneId, zonePos.percentInZone) * weight
//        }
        var met = zoneContribs.sumOf { (zId, weight) ->
            val pct = when (zId) {
                zonePos.zone.id -> zonePos.percentInZone
                zonePos.zone.id + 1 -> 0f
                zonePos.zone.id - 1 -> 1f
                else -> zonePos.percentInZone
            }
            zoneToMet(zId, pct) * weight
        }
        // Prise en compte du type de sport
        val sportMetCoeff = when (sportType) {
            UserPhysioParams.SportType.DEFAULT -> 1.0
            UserPhysioParams.SportType.VELO -> 0.95
            UserPhysioParams.SportType.COURSE -> 1.05
            UserPhysioParams.SportType.RAMEUR -> 1.10
        }
        met *= sportMetCoeff

        // Glucides
        // --- Ratio glucides lissé
//        var glucideRatio = zoneContribs.sumOf { (zoneId, weight) ->
//            zoneToGlucideRatio(zoneId, zonePos.percentInZone) * weight
//        }
        var glucideRatio = zoneContribs.sumOf { (zId, weight) ->
            val pct = when (zId) {
                zonePos.zone.id -> zonePos.percentInZone
                zonePos.zone.id + 1 -> 0f
                zonePos.zone.id - 1 -> 1f
                else -> zonePos.percentInZone
            }
            zoneToGlucideRatio(zId, pct) * weight
        }
        // Prise en compte du type de sport
        val sportGlucideCoeff = when (sportType) {
            UserPhysioParams.SportType.DEFAULT -> 1.0
            UserPhysioParams.SportType.VELO -> 0.90
            UserPhysioParams.SportType.COURSE -> 1.05
            UserPhysioParams.SportType.RAMEUR -> 1.10
        }
        glucideRatio *= sportGlucideCoeff
        glucideRatio = glucideRatio.coerceIn(0.0, 1.0)


        // val kcal = met * poidsKg * durationH
        val rendement = when (niveau) {
            UserPhysioParams.Niveau.DEBUTANT -> 1.00
            UserPhysioParams.Niveau.ENTRAINE -> 0.95
        }
        val kcal = met * poidsKg * durationH * rendement

        // Cumul d'énergie
        kcalTotal += kcal

        // Ajustement simple selon sexe (réaliste)
        if (sexe == UserPhysioParams.Sexe.FEMME) {
            glucideRatio *= 0.9
        }

        // Ajustement selon le niveau
        when (niveau) {
            UserPhysioParams.Niveau.DEBUTANT -> {
                // rien (référence)
            }
            UserPhysioParams.Niveau.ENTRAINE -> {
                glucideRatio *= 0.85
            }
        }

        // Garantir de rester entre les bornes
        glucideRatio = glucideRatio.coerceIn(0.0, 1.0)

        val kcalGlucides = kcal * glucideRatio
        val kcalLipides = kcal - kcalGlucides

        val glucidesG = kcalGlucides / 4.0
        val lipidesG = kcalLipides / 9.0

        glucidesGrammesTotal += glucidesG
        lipidesGrammesTotal += lipidesG

        // --- Apport conseillé (et NON dépense)
        glucidesGrammesDepuisReset += glucidesG * apportRatio

    }


    fun updatePhysio(params: UserPhysioParams) {
        poidsKg = params.poidsKg
        sexe = params.sexe
        niveau = params.niveau
        energyUnit = params.energyUnit
    }

    fun restoreFromStat(
        kcalTotal: Double,
        glucidesTotalG: Double,
        glucidesDepuisResetG: Double,
        lipidesTotalG: Double
    ) {
        this.kcalTotal = kcalTotal
        this.glucidesGrammesTotal = glucidesTotalG
        this.glucidesGrammesDepuisReset = glucidesDepuisResetG
        this.lipidesGrammesTotal = lipidesTotalG
    }


    fun resetGlucides() {
        glucidesGrammesDepuisReset = 0.0
    }

    fun resetAll() {
        kcalTotal = 0.0
        glucidesGrammesTotal = 0.0
        glucidesGrammesDepuisReset = 0.0
        lipidesGrammesTotal = 0.0
    }


    private fun lerp(min: Double, max: Double, t: Float): Double {
        return min + (max - min) * t
    }


    /*************************************************
     * Entre Z2 max et Z3 min (par exemple) le changement de filière énergétique est brutal
     * - ce n’est pas une interpolation continue
     * - le corps bascule vers plus de glucides
     *
     * Ce « trou » n’est pas un vide, c’est une transition physiologique abrupte
     *
     * Attention : les zones sont (id 0, zone 0), (id 2, zone 2), ... (id 4, zone 4), (id 5, zone 5a) et (id 6, zone 5b)
     *
     */
    private val zoneMetRanges = mapOf(
        1 to (3.0 to 4.0),
        2 to (5.0 to 6.0),
        3 to (7.0 to 8.0),
        4 to (9.0 to 11.0),
        // 5 to (12.0 to 16.0), // Zone 5 (5a et 5b)
        5 to (12.0 to 14.0),   // Zone 5a
        6 to (14.0 to 17.0)    // Zone 5b
    )

    private val zoneGlucideRanges = mapOf(
        1 to (0.10 to 0.20),
        2 to (0.30 to 0.40),
        3 to (0.50 to 0.60),
        4 to (0.70 to 0.80),
        // 5 to (0.85 to 0.98), // Zone 5 (5a et 5b)
        5 to (0.85 to 0.95),   // Zone 5a
        6 to (0.95 to 0.99)    // Zone 5b
    )

    fun zoneToMet(zoneId: Int, percentInZone: Float): Double {
        val (min, max) = zoneMetRanges[zoneId] ?: return 0.0
        return lerp(min, max, percentInZone)
    }

    fun zoneToGlucideRatio(zoneId: Int, percentInZone: Float): Double {
        val (min, max) = zoneGlucideRanges[zoneId] ?: return 0.0
        return lerp(min, max, percentInZone)
    }


    private fun smoothStep(t: Float): Float {
        return t * t * (3f - 2f * t)
    }

    private fun smoothZoneContribution(
        zoneId: Int,
        percentInZone: Float,
        zones: List<Zone>
    ): List<Pair<Int, Float>> {

        if (!ENERGIE_CALCUL_AVEC_LISSAGE) {
            return listOf(zoneId to 1f)
        }

        val zoneIds = zones.map { it.id }.toSet()

        val pct = percentInZone.coerceIn(0f, 1f)

        // Transition vers zone supérieure
        // if (pct > 1f - ENERGIE_CALCUL_ZONE_TRANSITION_PCT && zoneId < zones.last().id) {
        if (pct > 1f - ENERGIE_CALCUL_ZONE_TRANSITION_PCT && zoneIds.contains(zoneId + 1)) {
        val t = (pct - (1f - ENERGIE_CALCUL_ZONE_TRANSITION_PCT)) / ENERGIE_CALCUL_ZONE_TRANSITION_PCT
            val alpha = smoothStep(t)
            return listOf(
                zoneId to (1f - alpha),
                (zoneId + 1) to alpha
            )
        }

        // Transition depuis zone inférieure
        // if (pct < ENERGIE_CALCUL_ZONE_TRANSITION_PCT && zoneId > zones.first().id) {
        if (pct < ENERGIE_CALCUL_ZONE_TRANSITION_PCT && zoneIds.contains(zoneId - 1)) {
            val t = pct / ENERGIE_CALCUL_ZONE_TRANSITION_PCT
            val alpha = smoothStep(t)
            return listOf(
                (zoneId - 1) to (1f - alpha),
                zoneId to alpha
            )
        }

        return listOf(zoneId to 1f)
    }

}