package com.gfocreation.heartrateandzoneannounce.misc

import android.content.Context
import com.gfocreation.heartrateandzoneannounce.model.Announcer
import com.gfocreation.heartrateandzoneannounce.ZonesModeUi
import com.gfocreation.heartrateandzoneannounce.model.RappelParam
import com.gfocreation.heartrateandzoneannounce.model.StatEntrainement
import com.gfocreation.heartrateandzoneannounce.model.UserPhysioParams


// nom du fichier prefs et clés exposées pour usage global
private const val APP_PREFS = "hr_zones_prefs"
private const val KEY_DISCLAIMER = "disclaimer_accepted"
private const val KEY_ZONE_TYPE = "zone_type"

private const val KEY_ZT_SIMPLE = "simplified"
private const val KEY_ZT_KARVONEN = "karvonen"
private const val KEY_ZT_MANUAL = "manual"

private const val KEY_FC_MAX = "fc_max"
private const val KEY_FC_MIN = "fc_min"
private const val KEY_MANUAL_CSV = "manual_csv"


private const val KEY_RAPPEL_DUREE = "rappel_duree"
private const val KEY_RAPPEL_LIBELLE = "rappel_libelle"


// Paramètres physiques de l'utilisateur (UserPhysioParams)
private const val KEY_USER_POIDS_KG = "user_poids"
private const val KEY_USER_SEXE = "user_sexe"
private const val KEY_USER_SPORT_TYPE = "user_sport_type"
private const val KEY_USER_NIVEAU = "user_niveau"
private const val KEY_USER_ENRG_U = "user_energyUnit"

private const val KEY_STAT_PREFIX_ = "STAT_"
//private const val KEY_STAT_PREFIX_LIBRE = "STAT_LIBRE_"
//private const val KEY_STAT_PREFIX_SEANCE = "STAT_SEANCE_"
private const val KEY_TYPE = "type"
private const val KEY_DUREE_MS = "DUREE_MS"
private const val KEY_BPM_MAX = "BPM_MAX"
private const val KEY_BPM_MIN = "BPM_MIN"
private const val KEY_BPM_TOTAL = "BPM_TOTAL"
private const val KEY_VALEUR_NB = "VALEUR_NB"
private const val KEY_LAST_START = "LAST_START"
private const val KEY_KCAL_TOTAL = "KCAL_TOTAL"
private const val KEY_GLUICIDE_TOTAL_G = "GLUCIDE_TOTAL_G"
private const val KEY_GLUCIDE_DEPUIS_RESET_G = "GLUCIDE_DEPUIS_RESET_G"
private const val KEY_LIPIDE_TOTAL_G = "LIPIDE_TOTAL_G"
private const val KEY_CSB_PRC = "CSB_PRC"
private const val KEY_CSB_DUREE_RESTANTE = "CSB_DUREE_RESTANTE"



// private const val KEY_ANNOUNCE_MODE = "ANNOUNCE_MODE"
private const val KEY_ANNOUNCE_NIVEAU = "ANNOUNCE_NIVEAU"

// Demande des droits
private const val KEY_ASKED_ONCE = "ble_perm_asked"

fun markBlePermissionsAsked(ctx: Context) {
    ctx.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_ASKED_ONCE, true)
        .apply()
}

fun wasBlePermissionsAsked(ctx: Context): Boolean {
    return ctx.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_ASKED_ONCE, false)
}


// Lissage des annonces de changement de zones
private const val KEY_ZONE_SMOOTHING = "zone_smoothing_duration_msec"

fun saveZoneSmoothing(ctx: Context, msec: Long) {
    val prefs = ctx.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
    prefs.edit().putLong(KEY_ZONE_SMOOTHING, msec).apply()
}

fun loadZoneSmoothing(ctx: Context): Long {
    val prefs = ctx.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
    return prefs.getLong(KEY_ZONE_SMOOTHING, SMOOTHING_DELAY_MS_DEFAULT)
}


// Luminosité

private const val KEY_LUMINOSITE_ENTRAINEMENT = "luminosite_entr"

fun getLuminositeEntrainemente(ctx: Context): TrainingBrightnessMode {
    val prefs = ctx.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
    return TrainingBrightnessMode.valueOf(prefs.getString(KEY_LUMINOSITE_ENTRAINEMENT, TrainingBrightnessMode.NORMAL.name)!!)
}

fun setLuminositeEntrainemente(ctx: Context, mode: TrainingBrightnessMode) {
    val prefs = ctx.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_LUMINOSITE_ENTRAINEMENT, mode.name)
        .apply()
}


// Rappel

fun saveRappelParam(ctx: Context, rappelParam: RappelParam) {
    val prefs = ctx.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
    prefs.edit().putInt(KEY_RAPPEL_DUREE, rappelParam.minutes)
        .putString(KEY_RAPPEL_LIBELLE, rappelParam.label)
        .apply()
}

fun loadRappelParam(ctx: Context): RappelParam {
    val prefs = ctx.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
    val rappelParam = RappelParam(
        minutes = prefs.getInt(KEY_RAPPEL_DUREE, RAPPEL_DUREE_DEFAUT),
        label = prefs.getString(KEY_RAPPEL_LIBELLE, RAPPEL_LIBELLE_DEFAUT) ?: ""
    )
    return rappelParam
}



// Paramètres physiques de l'utilisateur (UserPhysioParams)

fun saveUserPhysioParams(ctx: Context, userPhysioParams: UserPhysioParams) {
    val prefs = ctx.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
    prefs.edit()
        .putFloat(KEY_USER_POIDS_KG, userPhysioParams.poidsKg)
        .putString(KEY_USER_SEXE, userPhysioParams.sexe.name)
        .putString(KEY_USER_SPORT_TYPE, userPhysioParams.sportType.name)
        .putString(KEY_USER_NIVEAU, userPhysioParams.niveau.name)
        .putString(KEY_USER_ENRG_U, userPhysioParams.energyUnit.name)
        .apply()
}


fun loadUserPhysioParams(ctx: Context): UserPhysioParams {
    val prefs = ctx.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)

    val poids = prefs.getFloat(KEY_USER_POIDS_KG, USER_DEFAULT_POIDS_KG)

    val sexeStr = prefs.getString(KEY_USER_SEXE, USER_DEFAULT_SEXE.name)
    val sexe = runCatching {
        UserPhysioParams.Sexe.valueOf(sexeStr ?: USER_DEFAULT_SEXE.name)
    }.getOrDefault(USER_DEFAULT_SEXE)

    val sportTypeStr = prefs.getString(KEY_USER_SPORT_TYPE, USER_DEFAULT_SPORT_TYPE.name)
    val sportType = runCatching {
        UserPhysioParams.SportType.valueOf(sportTypeStr ?: USER_DEFAULT_SPORT_TYPE.name)
    }.getOrDefault(USER_DEFAULT_SPORT_TYPE)

    val niveauStr = prefs.getString(KEY_USER_NIVEAU, USER_DEFAULT_NIVEAU.name)
    val niveau = runCatching {
        UserPhysioParams.Niveau.valueOf(niveauStr ?: USER_DEFAULT_NIVEAU.name)
    }.getOrDefault(USER_DEFAULT_NIVEAU)

    val energyUnitStr = prefs.getString(KEY_USER_ENRG_U, USER_DEFAULT_ENRG_U.name)
    val energyUnit = runCatching {
        UserPhysioParams.EnergyUnit.valueOf(energyUnitStr ?: USER_DEFAULT_ENRG_U.name)
    }.getOrDefault(USER_DEFAULT_ENRG_U)

    return UserPhysioParams(
        poidsKg = poids,
        sexe = sexe,
        sportType = sportType,
        niveau = niveau,
        energyUnit = energyUnit

    )
}





// Détails des annonces

fun loadDisclaimerAccepted(ctx: Context): Boolean {
    val prefs = ctx.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
    return prefs.getBoolean(KEY_DISCLAIMER, false)
}

fun saveDisclaimerAccepted(ctx: Context, accepted: Boolean) {
    val prefs = ctx.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
    prefs.edit().putBoolean(KEY_DISCLAIMER, accepted).apply()
}


fun loadAnnouncementNiveau(ctx: Context): Announcer.AnnouncementNiveau {
    val prefs = ctx.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
    val stored = prefs.getInt(KEY_ANNOUNCE_NIVEAU, Announcer.AnnouncementNiveau.DEFAULT.valeur)
    return Announcer.AnnouncementNiveau.values()
        .firstOrNull { it.valeur == stored }
        ?: Announcer.AnnouncementNiveau.DEFAULT
}

fun saveAnnouncementNiveau(ctx: Context, niveau: Announcer.AnnouncementNiveau) {
    val prefs = ctx.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
    prefs.edit()
        .putInt(KEY_ANNOUNCE_NIVEAU, niveau.valeur)
        .apply()
}



/** Retourne un ZonesModeUi — évite les conversions String ↔ enum ailleurs */
fun loadZonedMode(ctx: Context): ZonesModeUi {
    val prefs = ctx.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
    return when (prefs.getString(KEY_ZONE_TYPE, KEY_ZT_SIMPLE)) {
        KEY_ZT_KARVONEN -> ZonesModeUi.KARVONEN
        KEY_ZT_MANUAL -> ZonesModeUi.MANUAL
        else -> ZonesModeUi.SIMPLIFIED
    }
}

fun saveZoneMode(ctx: Context, mode: ZonesModeUi, fcMax: Int, fcMin: Int, manualCsv: String) {
    val prefs = ctx.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
    prefs.edit().apply {
        putString(KEY_ZONE_TYPE, when (mode) {
            ZonesModeUi.SIMPLIFIED -> KEY_ZT_SIMPLE
            ZonesModeUi.KARVONEN -> KEY_ZT_KARVONEN
            ZonesModeUi.MANUAL -> KEY_ZT_MANUAL
        })
        putInt(KEY_FC_MAX, fcMax)
        putInt(KEY_FC_MIN, fcMin)
        putString(KEY_MANUAL_CSV, manualCsv)
        apply()
    }
}

fun storedFcMaxExists(ctx: Context): Boolean {
    val prefs = ctx.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
    return prefs.contains(KEY_FC_MAX)
}
fun loadStoredFcMax(ctx: Context): Int {
    val prefs = ctx.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
    return prefs.getInt(KEY_FC_MAX, DEFAULT_FC_MAX)
}

fun loadStoredFcMin(ctx: Context): Int {
    val prefs = ctx.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
    return prefs.getInt(KEY_FC_MIN, 60)
}

fun loadStoredManualCsv(ctx: Context): String {
    val prefs = ctx.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
    // Default example: 6 or 7 boundary numbers depending on UI
    return prefs.getString(KEY_MANUAL_CSV, "") ?: ""
}


// Objet StatLibre


fun loadStat(ctx: Context, seanceId: Int): StatEntrainement {
    val prefix: String = StatType2Prefix(seanceId)
    val prefs = ctx.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
    // Log.w("StatPrefs", "loadStat $prefix")
    val csbTteSec = prefs.getInt(prefix + KEY_CSB_DUREE_RESTANTE, INT_NULL)
    return StatEntrainement(
        seanceId = seanceId,
        dureeMs = prefs.getLong(prefix + KEY_DUREE_MS, 0L),
        bpmMax = prefs.getInt(prefix + KEY_BPM_MAX, 0),
        bpmMin = prefs.getInt(prefix + KEY_BPM_MIN, 300), // default 300 si absent
        bpmTotal = prefs.getLong(prefix + KEY_BPM_TOTAL, 0L),
        valeurNb = prefs.getInt(prefix + KEY_VALEUR_NB, 0),
        lastStartTimeMs = prefs.getLong(prefix + KEY_LAST_START, 0L),
        kcalTotal = prefs.getFloat(prefix + KEY_KCAL_TOTAL, 0.0f).toDouble(),
        glucidesTotalG = prefs.getFloat(prefix + KEY_GLUICIDE_TOTAL_G, 0.0f).toDouble(),
        glucidesDepuisResetG = prefs.getFloat(prefix + KEY_GLUCIDE_DEPUIS_RESET_G, 0.0f).toDouble(),
        lipidesTotalG = prefs.getFloat(prefix + KEY_LIPIDE_TOTAL_G, 0.0f).toDouble(),
        csbPercent = prefs.getInt(prefix + KEY_CSB_PRC, CSB_PRC_INIT),
        csbTteSec = if (csbTteSec == INT_NULL) null else csbTteSec
    )
}

private fun StatType2Prefix(seanceId: Int): String {
    return KEY_STAT_PREFIX_ + seanceId.toString() + "_"
}

fun saveStat(ctx: Context, statEntr: StatEntrainement) {
    val prefixStatPref: String = StatType2Prefix(statEntr.seanceId)
    val prefs = ctx.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
    prefs.edit()
        .putString(prefixStatPref + KEY_TYPE, prefixStatPref)
        .putLong(prefixStatPref + KEY_DUREE_MS, statEntr.dureeMs)
        .putInt(prefixStatPref + KEY_BPM_MAX, statEntr.bpmMax)
        .putInt(prefixStatPref + KEY_BPM_MIN, statEntr.bpmMin)
        .putLong(prefixStatPref + KEY_BPM_TOTAL, statEntr.bpmTotal)
        .putInt(prefixStatPref + KEY_VALEUR_NB, statEntr.valeurNb)
        .putLong(prefixStatPref + KEY_LAST_START, statEntr.lastStartTimeMs)
        .putFloat(prefixStatPref + KEY_KCAL_TOTAL, statEntr.kcalTotal.toFloat())
        .putFloat(prefixStatPref + KEY_GLUICIDE_TOTAL_G, statEntr.glucidesTotalG.toFloat())
        .putFloat(prefixStatPref + KEY_GLUCIDE_DEPUIS_RESET_G, statEntr.glucidesDepuisResetG.toFloat())
        .putFloat(prefixStatPref + KEY_LIPIDE_TOTAL_G, statEntr.lipidesTotalG.toFloat())
        .putInt(prefixStatPref + KEY_CSB_PRC, statEntr.csbPercent)
        .putInt(prefixStatPref + KEY_CSB_DUREE_RESTANTE, statEntr.csbTteSec ?: INT_NULL)
        .apply()
    // Log.w("StatPrefs", "saveStat $prefix")
}
