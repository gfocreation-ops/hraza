package com.gfocreation.heartrateandzoneannounce.model

data class Zone(
    val id: Int,
    val key: String,
    val label: String,
    val minBpm: Int,
    val maxBpm: Int,
    val minPercent: Int,
    val maxPercent: Int,
    val description: String
)
