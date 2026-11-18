package com.gfocreation.heartrateandzoneannounce.model

import android.content.Context
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import com.gfocreation.heartrateandzoneannounce.misc.SMOOTHING_DELAY_MS_DEFAULT
import com.gfocreation.heartrateandzoneannounce.misc.bpm2ZoneId
import com.gfocreation.heartrateandzoneannounce.misc.loadAnnouncementNiveau
import com.gfocreation.heartrateandzoneannounce.misc.loadZoneSmoothing
import com.gfocreation.heartrateandzoneannounce.misc.saveAnnouncementNiveau
import kotlinx.coroutines.*
import java.util.*
import android.media.AudioManager
import androidx.compose.ui.graphics.Color
import com.gfocreation.heartrateandzoneannounce.R


/**
 * Announcer : Text-to-Speech simple (sans vibration).
 *
 * Comportement :
 * - Annuler toute annonce précédente dès qu'une nouvelle arrive.
 * - Appeler tts.stop() puis tts.speak(... QUEUE_FLUSH ...) pour interrompre et lancer la nouvelle annonce immédiatement.
 * - Si TTS n'est pas ready, on attend court (max 2s) puis on abandonne si toujours pas ready.
 */
class Announcer(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val toneGenerator by lazy {
        ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    }


    private var ready = false

    private var stoppable = false

    // Lissage du changement de zone (anti-rebond)
    private var pendingZoneId: Int? = null
    private var zoneChangeJob: Job? = null
    private var smoothingDelayMs = SMOOTHING_DELAY_MS_DEFAULT


//    enum class AnnouncementMode { ALL, CHG_ZONE, ANNONCE_ONLY, BEEP_ONLY, MUTE }
//    private var announcementMode: AnnouncementMode = AnnouncementMode.MUTE
enum class AnnouncementNiveau(val valeur: Int) {
    ALL(9),
    CHG_ZONE(7),
    ANNONCE_ONLY(5),
    BEEP_ONLY(3),
    MUTE(0);

    companion object {
        val DEFAULT = ALL
    }

    val borderColor: Color
        get() = when (this) {
            ALL -> Color(0xFF4CAF50)
            CHG_ZONE -> Color(0xFFFF0000)
            ANNONCE_ONLY -> Color(0xFFFFA000)
            BEEP_ONLY -> Color(0xFF03A9F4)
            MUTE -> Color.Gray
        }

    val iconRes: Int
        get() = when (this) {
            MUTE -> R.drawable.ic_voice_off
            BEEP_ONLY -> R.drawable.notifications_active_24dp
            else -> R.drawable.ic_voice
        }

    val label: String
        get() = when (this) {
            ALL -> "Annonces : tout (zones + bpm)"
            CHG_ZONE -> "Annonces : changements de zone"
            ANNONCE_ONLY -> "Annonces : manuelles uniquement"
            BEEP_ONLY -> "Bips uniquement"
            MUTE -> "Son coupé"
        }

    val contentDescription: String
        get() = label

}
    private var announcementNiveau: AnnouncementNiveau = AnnouncementNiveau.DEFAULT

    val isMute: Boolean
        get() = announcementNiveau == AnnouncementNiveau.MUTE
    val isNoAnnonce: Boolean
        get() = announcementNiveau.valeur <= AnnouncementNiveau.BEEP_ONLY.valeur

    // Job gérant l'annonce en cours (annulable)
    private var announceJob: Job? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
        announcementNiveau = loadAnnouncementNiveau(context)
        smoothingDelayMs = loadZoneSmoothing(context)
    }


    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            try {
                tts?.language = Locale.getDefault()
                tts?.setPitch(1.0f)
                tts?.setSpeechRate(1.0f)
                // Log.d("Announcer", "TTS ready")
            } catch (t: Throwable) {
                // Log.w("Announcer", "TTS language/pitch error", t)
            }
        } else {
            // Log.w("Announcer", "TTS initialization failed status=$status")
        }
    }


    fun close() {
        try {
            announceJob?.cancel()
            scope.cancel()
        } catch (_: Throwable) {}
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (t: Throwable) {
            // Log.w("Announcer", "TTS shutdown failed", t)
        } finally {
            tts = null
        }
        toneGenerator.release()
    }


    /**
     * Lance immédiatement la nouvelle annonce en interrompant toute annonce en cours.
     * - Si muted -> retourne.
     * - Si TTS non prêt : on attend court (max 2s), sinon abandon.
     */
    private fun announce(text: String, isStoppable: Boolean = false) {

        val oldStoppable = stoppable
        stoppable = isStoppable

//        if (muted) return

        // annule la job précédente (si existante)
        announceJob?.cancel()

        announceJob = scope.launch {
            try {
                // attendre que le TTS soit prêt (timeout 2s)
                val start = System.currentTimeMillis()
                while (!ready && System.currentTimeMillis() - start < 2000L) {
                    delay(50L)
                }
                if (!ready) {
                    // Log.w("Announcer", "announce aborted: TTS not ready")
                    return@launch
                }

                if (oldStoppable)
                    stop()

                // speak avec QUEUE_FLUSH pour s'assurer qu'on démarre directement
                try {
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "announce-${System.currentTimeMillis()}")
                    // Log.w("Announcer", "TTS ok : $text")
                } catch (t: Throwable) {
                    // Log.w("Announcer", "TTS speak failed", t)
                }
            } catch (ce: CancellationException) {
                // job cancelled (nouvelle annonce a remplacé) : on ne log pas comme erreur
            } catch (t: Throwable) {
                // Log.w("Announcer", "announce error", t)
            }
        }
    }

    fun stop() {
        // stop toute énonciation en cours (interrompt immédiatement)
        try {
            tts?.stop()
        } catch (t: Throwable) {
            // ignore
        }
    }


    // Gestion des modes : zone+bpm ; changement de zone ; annonce ; mute


    fun getNiveau(avecCardio: Boolean): AnnouncementNiveau {
        return getOrNextNiveau(false, avecCardio)
    }

    fun nextNiveau(avecCardio: Boolean): AnnouncementNiveau {
        return getOrNextNiveau(true, avecCardio)
    }

    private fun getOrNextNiveau(avecNext: Boolean, avecCardio: Boolean): AnnouncementNiveau {

        val oldValue = announcementNiveau

        if (avecNext) {
            announcementNiveau = when (announcementNiveau) {
                AnnouncementNiveau.ALL -> AnnouncementNiveau.CHG_ZONE
                AnnouncementNiveau.CHG_ZONE -> AnnouncementNiveau.ANNONCE_ONLY
                AnnouncementNiveau.ANNONCE_ONLY -> AnnouncementNiveau.BEEP_ONLY
                AnnouncementNiveau.BEEP_ONLY -> AnnouncementNiveau.MUTE
                AnnouncementNiveau.MUTE -> AnnouncementNiveau.ALL
            }
        }

        if (!avecCardio && announcementNiveau.valeur > AnnouncementNiveau.ANNONCE_ONLY.valeur) {
            announcementNiveau = AnnouncementNiveau.ANNONCE_ONLY
        }

        if (avecNext && isNoAnnonce) {
            stop()
        }

        if (oldValue != announcementNiveau) {
            saveAnnouncementNiveau(context, announcementNiveau)
        }

        return announcementNiveau
    }




    // Bips

    private fun beepCourt(toneGenerator: ToneGenerator, toneType: Int) {
        if (isMute) {
            return
        }
        toneGenerator.startTone(toneType, 150)
    }
    fun beepCourtInterm() {
        beepCourt(toneGenerator, ToneGenerator.TONE_PROP_BEEP)
    }
    fun beepCourtRappel() {
        beepCourt(toneGenerator, ToneGenerator.TONE_CDMA_MED_SLS)
    }
    fun beepCourtFin() {
        beepCourt(toneGenerator, ToneGenerator.TONE_CDMA_ONE_MIN_BEEP)
    }


    // Annonces

    private var zoneIdIndexLast: Int = -1



    fun announceBpmEtZone(bpm: Int, zones: List<Zone>) {

        // Cas où on n’annonce rien par rapport à FC et zones
        if (announcementNiveau.valeur <= AnnouncementNiveau.ANNONCE_ONLY.valeur) return

        val zone = bpm2ZoneId(bpm, zones)
        val newZoneId = zone.id
        val oldZoneId = zoneIdIndexLast

        // Mise à jour immédiate : la zone courante détectée
        zoneIdIndexLast = newZoneId

        // Mode changement de zone → on ajoute un lissage pour éviter les rebonds
        if (announcementNiveau.valeur >= AnnouncementNiveau.CHG_ZONE.valeur && oldZoneId != newZoneId) {

            // Cancel l’ancien timer
            zoneChangeJob?.cancel()

            // Nouveau changement de zone "en attente"
            pendingZoneId = newZoneId

            zoneChangeJob = scope.launch {
                delay(smoothingDelayMs)

                // Si la zone détectée après x sec est la même → annonce
                if (pendingZoneId == newZoneId) {
                    val message = "Zone ${zone.key}, $bpm bpm"
                    announce(message, false)
                }
            }

        } else if (announcementNiveau == AnnouncementNiveau.ALL) {
            announce(bpm.toString(), true)
        }

    }

    fun announceTexte(message: String) {
        if (announcementNiveau.valeur >= AnnouncementNiveau.ANNONCE_ONLY.valeur) {
            // Log.w("Announcer", "Message libre $message")
            announce(message, false)
        }
    }


}
