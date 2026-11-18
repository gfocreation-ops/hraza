package com.gfocreation.heartrateandzoneannounce.model

sealed class ZoneEvent {
    data class Entered(val zoneId: Int, val bpm: Int) : ZoneEvent()
    data class Exited(val zoneId: Int, val bpm: Int) : ZoneEvent()
}
