package com.gfocreation.heartrateandzoneannounce.model

data class SeancesStore(
    val version: Int,
    val seances: List<Seance>
)