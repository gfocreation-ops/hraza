package com.gfocreation.heartrateandzoneannounce.model

data class ZoneConfig(
    val mode: String = "simpl",
    val fcMax: Int = 190,
    val fcMin: Int = 60,
    val manualCsv: String = "50,60,70,80,90,190"
)
