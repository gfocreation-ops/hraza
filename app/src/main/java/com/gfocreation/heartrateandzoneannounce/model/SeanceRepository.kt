package com.gfocreation.heartrateandzoneannounce.model

import android.content.Context
import android.content.SharedPreferences
import com.gfocreation.heartrateandzoneannounce.APP_SEANCE_VERSION
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import com.gfocreation.heartrateandzoneannounce.misc.RuntimeTypeAdapterFactory
import com.gfocreation.heartrateandzoneannounce.ui.newId
import java.lang.reflect.Type



class SeanceRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Polymorphism for BlocSeance
    private val blocFactory = RuntimeTypeAdapterFactory.of(BlocSeance::class.java, "type")
        .registerSubtype(Etape::class.java, "etape")
        .registerSubtype(GroupRepeats::class.java, "group")



    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(BlocSeance::class.java, BlocSeanceDeserializer())
        .create()


    private val seanceListType: Type = object : TypeToken<List<Seance>>() {}.type


    companion object {
        private const val PREFS_NAME = "seances_prefs"
        private const val KEY_SEANCES = "seances"
    }


fun getAllSeance(): List<Seance> {
    val json = prefs.getString(KEY_SEANCES, null) ?: return emptyList()

    return try {
        val root = JsonParser.parseString(json)

        // -------- NOUVEAU FORMAT VERSIONNÉ --------
        if (root.isJsonObject && root.asJsonObject.has("version")) {
            val obj = root.asJsonObject

            val version = obj["version"]?.asInt
            val seancesJson = obj["seances"]

            if (version != null && seancesJson != null && seancesJson.isJsonArray) {
                gson.fromJson(seancesJson, seanceListType)
            } else {
                emptyList()
            }

        } else {
            // -------- ANCIEN FORMAT (liste brute) --------
            gson.fromJson(json, seanceListType)
        }

    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}


fun saveAllSeance(seances: List<Seance>) {
    val store = SeancesStore(
        version = APP_SEANCE_VERSION,
        seances = seances
    )

    val json = gson.toJson(store)
    prefs.edit().putString(KEY_SEANCES, json).apply()
}

    fun addSeance(seance: Seance) {
        val seances = getAllSeance().toMutableList()
        seances.add(seance)
        saveAllSeance(seances)
    }

    fun updateSeance(updated: Seance) {
        val seances = getAllSeance().map {
            if (it.id == updated.id) updated else it
        }
        saveAllSeance(seances)
    }

    fun deleteSeance(id: Int) {
        val seances = getAllSeance().filterNot { it.id == id }
        saveAllSeance(seances)
    }

    fun duplicateSeance(original: Seance): Seance {
        val seances = getAllSeance()

        val newId = (seances.maxOfOrNull { it.id } ?: 0) + 1

        val copy = original.copy(
            id = newId,
            name = "${original.name} (copie)"
        )

        saveAllSeance(seances + copy)
        return copy
    }


    fun ensureDefaultSeancesIfNeeded() {

//        // Pour test : attention, ça écrase toutes les autres séances
//        createDefaultSeances()
//        prefs.edit()
//            .putInt("SEANCE_INIT_VERSION", APP_SEANCE_VERSION)
//            .apply()
//        return

        val initVersion = prefs.getInt("SEANCE_INIT_VERSION", 0)

        // déjà initialisé pour cette version
        if (initVersion >= APP_SEANCE_VERSION) return

        val seances = getAllSeance()
        if (seances.isEmpty()) {
            createDefaultSeances()   // (2) — création réelle
        }

        prefs.edit()
            .putInt("SEANCE_INIT_VERSION", APP_SEANCE_VERSION)
            .apply()
    }

    private fun createDefaultSeances() {

        val seances = mutableListOf<Seance>()

        // ==================================================
        // Séance 1 : HIIT Cardio
        // ==================================================

        val hiitBlocks = listOf<BlocSeance>(

            // 10 min Z2 – Échauffement
            Etape(
                id = newId(),
                durationMillis = 10 * 60_000L,
                description = "Échauffement",
                zoneId = 2
            ),

            // 10 x (1 min Z5a + 1 min Z2)
            GroupRepeats(
                id = newId(),
                repeats = 10,
                ignoreLastStepOnLastRepeat = true,
                children = listOf(
                    Etape(
                        id = newId(),
                        durationMillis = 60_000L,
                        description = "Travail",
                        zoneId = 5
                    ),
                    Etape(
                        id = newId(),
                        durationMillis = 60_000L,
                        description = "Récupération",
                        zoneId = 2
                    )
                )
            ),

            // 3 min Z2 – Récupération
            Etape(
                id = newId(),
                durationMillis = 3 * 60_000L,
                description = "Récupération",
                zoneId = 2
            ),

            // 6 x (30s Z5b + 1m30 Z2)
            GroupRepeats(
                id = newId(),
                repeats = 6,
                ignoreLastStepOnLastRepeat = true,
                children = listOf(
                    Etape(
                        id = newId(),
                        durationMillis = 30_000L,
                        description = "Sprint",
                        zoneId = 5
                    ),
                    Etape(
                        id = newId(),
                        durationMillis = 90_000L,
                        description = "Récup",
                        zoneId = 2
                    )
                )
            ),

            // 7 min Z2 – Retour au calme
            Etape(
                id = newId(),
                durationMillis = 7 * 60_000L,
                description = "Retour au calme",
                zoneId = 2
            )
        )

        val hiitSeance = Seance(
            id = 1,
            name = "Exemple HIIT Cardio",
            description = "Séance fractionnée haute intensité",
            dureeRealMillis = 0L,
            blocks = hiitBlocks
        )

        seances += hiitSeance

        // ==================================================
        // Séance 2 : Endurance Vélo / HomeTrainer
        // ==================================================

        val enduranceBlocks = listOf<BlocSeance>(

            // 10 min Z2 – Mise en route
            Etape(
                id = newId(),
                durationMillis = 10 * 60_000L,
                description = "Mise en route",
                zoneId = 2
            ),

            // 3 x (12 min Z3 + 3 min Z2)
            GroupRepeats(
                id = newId(),
                repeats = 3,
                ignoreLastStepOnLastRepeat = true,
                children = listOf(
                    Etape(
                        id = newId(),
                        durationMillis = 12 * 60_000L,
                        description = "Tempo",
                        zoneId = 3
                    ),
                    Etape(
                        id = newId(),
                        durationMillis = 3 * 60_000L,
                        description = "Récupération",
                        zoneId = 2
                    )
                )
            ),

            // 8 min Z2 – Retour au calme
            Etape(
                id = newId(),
                durationMillis = 8 * 60_000L,
                description = "Retour au calme",
                zoneId = 2
            )
        )

        val enduranceSeance = Seance(
            id = 2,
            name = "Exemple séance endurance",
            description = "Travail d’endurance et tempo",
            dureeRealMillis = 0L,
            blocks = enduranceBlocks
        )

        seances += enduranceSeance

        // ==================================================
        // Sauvegarde
        // ==================================================

        saveAllSeance(seances)
    }



}
