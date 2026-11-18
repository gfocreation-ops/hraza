package com.gfocreation.heartrateandzoneannounce.misc

import com.gfocreation.heartrateandzoneannounce.model.UserPhysioParams


const val INT_NULL = -1

// BLE et cardio
const val UUI_VALUE_HEART_RATE_SERVICE = "0000180D-0000-1000-8000-00805f9b34fb"
const val UUI_VALUE_HEART_RATE_MEASUREMENT = "00002A37-0000-1000-8000-00805f9b34fb"
const val UUID_VALUE_CCCD = "00002902-0000-1000-8000-00805f9b34fb"
const val UUID_VALUE_BATTERY_SERVICE = "0000180F-0000-1000-8000-00805f9b34fb"
const val UUID_VALUE_BATTERY_LEVEL = "00002A19-0000-1000-8000-00805f9b34fb"

const val CAPTEUR_VIRTUEL_NOM = "Cardio Hraza"
// const val CHARS_LETTRES_ET_NUMS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
const val CHARS_LETTRES_ET_NUMS = "ABCDEFGHJKLMNPQRTWXYZ3456789"

const val ENERGIE_CALCUL_AVEC_LISSAGE = true        // Affine les calculs de dépense énergétique
// Largeur de la zone de transition (8 % de la zone)
const val ENERGIE_CALCUL_ZONE_TRANSITION_PCT = 0.08f


// Cardiac Strain Balance
const val CSB_PRC_INIT = 100


const val SMOOTHING_DELAY_MS_DEFAULT = 3000L

const val USER_DEFAULT_POIDS_KG = 0f    // Signifie rien de saisie
val USER_DEFAULT_SEXE = UserPhysioParams.Sexe.HOMME
var USER_DEFAULT_SPORT_TYPE = UserPhysioParams.SportType.DEFAULT
var USER_DEFAULT_NIVEAU = UserPhysioParams.Niveau.DEBUTANT
val USER_DEFAULT_ENRG_U = UserPhysioParams.EnergyUnit.KCAL


const val BPM_TIMEOUT_MS = 8000L        // Stat en pause si pas de bpm > 0 au bout de 8 sec
const val MAX_RECONNECT_DURATION_MS = 30_000L
const val INITIAL_RECONNECT_DELAY_MS = 1_000L
const val MAX_RECONNECT_DELAY_MS = 5_000L


const val LIBRE_STAT_AUTO_RESUME_DELAI = 30_000L       // 30 secondes
const val DEFAULT_FC_MAX = 170
const val BPM_MIN_DEFAULT = 1000

const val RAPPEL_DUREE_DEFAUT = 20       // 20 minutes
const val RAPPEL_LIBELLE_DEFAUT = "Hydratation"
const val RAPPEL_ALERT_AFF_DUREE = 4000L       // Durée max d'affichage de l'alerte
