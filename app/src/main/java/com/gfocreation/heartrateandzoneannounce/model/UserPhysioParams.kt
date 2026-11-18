package com.gfocreation.heartrateandzoneannounce.model

import android.content.Context
import com.gfocreation.heartrateandzoneannounce.misc.USER_DEFAULT_ENRG_U
import com.gfocreation.heartrateandzoneannounce.misc.USER_DEFAULT_NIVEAU
import com.gfocreation.heartrateandzoneannounce.misc.USER_DEFAULT_POIDS_KG
import com.gfocreation.heartrateandzoneannounce.misc.USER_DEFAULT_SEXE
import com.gfocreation.heartrateandzoneannounce.misc.USER_DEFAULT_SPORT_TYPE
import com.gfocreation.heartrateandzoneannounce.misc.loadUserPhysioParams
import com.gfocreation.heartrateandzoneannounce.misc.saveUserPhysioParams


data class UserPhysioParams(
    var poidsKg: Float,
    var sexe: Sexe,
    var sportType: SportType,
    var niveau: Niveau,
    var energyUnit: EnergyUnit
) {

    enum class Sexe {
        HOMME,
        FEMME
    }

    enum class SportType {
        DEFAULT,
        VELO,
        COURSE,
        RAMEUR
    }

    enum class Niveau {
        DEBUTANT,
        ENTRAINE
    }

    enum class EnergyUnit {
        KCAL,
        KJ
    }

    fun save(ctx: Context) {
        saveUserPhysioParams(ctx, this)
    }

    companion object {

        fun load(ctx: Context): UserPhysioParams {
            return loadUserPhysioParams(ctx)
        }

        fun default(): UserPhysioParams {
            return UserPhysioParams(
                poidsKg = USER_DEFAULT_POIDS_KG,
                sexe = USER_DEFAULT_SEXE,
                sportType = USER_DEFAULT_SPORT_TYPE,
                niveau = USER_DEFAULT_NIVEAU,
                energyUnit = USER_DEFAULT_ENRG_U
            )
        }
    }

}
