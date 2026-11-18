package com.gfocreation.heartrateandzoneannounce.model

import androidx.compose.ui.graphics.Color

data class ZoneSpec(
    val idIndex: Int,
    val idKey: String,
    val shortLabel: String,
    val pctRange: Pair<Int, Int>,
    val description: String,
    val color: Color
)

val CANONICAL_ZONES: List<ZoneSpec> = listOf(
    ZoneSpec(0, "0", "Repos", Pair(0,50), "", Color(0xFFDCDCDC)),
    ZoneSpec(1, "1", "Récupération active", Pair(50,60), "Échauffement, récupération, amélioration de la circulation, échauffement et retour au calme.", Color(0xFF369be3)),
    ZoneSpec(2, "2", "Endurance fondamentale", Pair(60,70), "Construction de la base aérobie, optimisation de la filière lipidique, volume d’entraînement principal.", Color(0xFF29a054)),
    ZoneSpec(3, "3", "Endurance modérée", Pair(70,80), "Aérobie soutenue\nDéveloppement de la capacité aérobie et de la tolérance à l’effort prolongé ; rythme de course longue.", Color(0xFFf2bb00)),
    ZoneSpec(4, "4", "Tempo soutenu", Pair(80,90), "Endurance active\nEndurance musculaire, efficacité énergétique, travail soutenu sur moyennes durées (10–40 min).", Color(0xFFff4500)),
    ZoneSpec(5, "5a", "Anaérobie", Pair(90,95), "Résistance intense\nTravail au seuil, capacité à maintenir un effort proche du maximum sur plusieurs minutes, amélioration de la FTP.", Color(0xFFB22222)),
    ZoneSpec(6, "5b", "VO2 Max", Pair(95,100), "Puissance maximale aérobie\nEfforts très courts et intenses (30 s à 3 min) : développement de la VO2 Max, de la tolérance à l’acide lactique et de la capacité anaérobie.", Color(0xFF991D1D))
)
