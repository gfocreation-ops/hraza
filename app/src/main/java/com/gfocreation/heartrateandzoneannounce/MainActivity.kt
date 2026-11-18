package com.gfocreation.heartrateandzoneannounce

import android.Manifest
import android.app.Activity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.Color
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.gfocreation.heartrateandzoneannounce.ble.HeartRateGattServer
import com.gfocreation.heartrateandzoneannounce.misc.CAPTEUR_VIRTUEL_NOM
import com.gfocreation.heartrateandzoneannounce.misc.PermissionHelper
import com.gfocreation.heartrateandzoneannounce.misc.SensorManager
import com.gfocreation.heartrateandzoneannounce.misc.TrainingBrightnessMode
import com.gfocreation.heartrateandzoneannounce.misc.ZoneManager
import com.gfocreation.heartrateandzoneannounce.misc.applyTrainingBrightness
import com.gfocreation.heartrateandzoneannounce.misc.computeZonesFromFcMax
import com.gfocreation.heartrateandzoneannounce.misc.computeZonesFromManual
import com.gfocreation.heartrateandzoneannounce.misc.computeZonesKarvonen
import com.gfocreation.heartrateandzoneannounce.misc.getLuminositeEntrainemente
import com.gfocreation.heartrateandzoneannounce.misc.loadDisclaimerAccepted
import com.gfocreation.heartrateandzoneannounce.misc.loadStoredFcMax
import com.gfocreation.heartrateandzoneannounce.misc.loadStoredFcMin
import com.gfocreation.heartrateandzoneannounce.misc.loadStoredManualCsv
import com.gfocreation.heartrateandzoneannounce.misc.loadZonedMode
import com.gfocreation.heartrateandzoneannounce.misc.saveDisclaimerAccepted
import com.gfocreation.heartrateandzoneannounce.misc.setLuminositeEntrainemente
import com.gfocreation.heartrateandzoneannounce.misc.storedFcMaxExists
import com.gfocreation.heartrateandzoneannounce.model.Announcer
import com.gfocreation.heartrateandzoneannounce.model.HeartRateProvider
import com.gfocreation.heartrateandzoneannounce.model.HeartRateSample
import com.gfocreation.heartrateandzoneannounce.model.SeanceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers


/***** Versions ****************
 * * GFo 10/2025
 *
 * - 0.2.0_o  - 14/10/2025 12:31 : Zones ok ; Capteur ok ; Libre : pas encore
 * - 0.2.0_o2 - 14/10/2025 19:21 : avec DEBUG_MODE, DEBUG_SIMUL_LIBRE et DEBUG_ALL_DEVICE
 * - 0.2.0_q  - 14/10/2025 23:42
 * - 0.2.0_r  - 15/10/2025 01:22
 * - 0.2.0_s  - 15/10/2025 01:52
 * - 0.2.0_t  - 15/10/2025 09:52
 * - 0.2.0_u  - 15/10/2025 10:54 : Capteur réel ko (depuis o2 ?) ; Libre avec coeur+bpm tps réel et animations
 * - 0.2.0_v  - 15/10/2025 12:25 : Zones ok ; Capteur ok ; Libre avec coeur+bpm tps réel et animations ; sans DEBUG_SIMUL_LIBRE
 * - 0.2.0_w  - 15/10/2025 17:45 : - réactivation de DEBUG_SIMUL_LIBRE pour go direct choix du capteur
 *                                 - Class StatLibre avec persitance et affichage minimal dans Libre
 * - 0.2.0_x  - 15/10/2025 17:54 : - intégration de StatLibre dans Libre
 * - 0.2.0_y  - 15/10/2025 20:06 : - Bloc stat dans Libre (ok mais à valider)
 * - 0.2.0_z  - 16/10/2025 11:36 : - Améliotations visuelles du bloc Stat
 *                                 - nouvelle class Announcer : la dernière annonce interrompt la précédente si encore en cours
 *                                 - Libre annonce (persitent) : soit tout (chg zone + new bpm) ; soit chg zone (défaut) ; soit mute
 *                                 - Ajout de DEBUG_SIMUL_LINEAIRE
 *                                 - /!\ non corrigé : dans Libre du mauvais positionnement coeur+bpm en zone 0 (18% fixe)
 * - 0.2.0_aa - 16/10/2025 17:35 : - correction ko du coeur+bpm en zone 0 (Libre) => problème d'affichage de FcMin dans Libre (sauf Karvonen)
 *                                 - création des objets Seance... et de la page SeancesScreen (juste le titre)
 *                                 - création des pages (juste le titre) et navigation pour SeanceEdit et EntrSeanceScreen
 * - 0.2.0_ab - 16/10/2025 18:54 : - correction des zones karvonen, simple (déjà ok)
 *                                 - correction dans Libre de la FcMin en fonction du type de zones
 * - 0.3.0_a  - 17/10/2025 09:47 : - page de création d'une séance
 *                                 - nettoyages dans Libre
 * - 0.3.0_b - 17/10/205 11:20   : - liste des séances
 * - 0.3.0_c - 17/10/205 16:27   : - liste des séances amélioration
 *                                 - armonisation de tous les titres et boutons avec
 * - 0.3.0_d - 17/10/205 18:11   : - correction déplacement des étapes d'une séance
 *                                 - améliorations visuelles de la liste des séances
 * - 0.3.0_e - 17/10/205 18:54   : - ajout de la vérification de l'activation du Bluetooth
 * - 0.3.0_f - 17/10/205 19:13   : - création de la page de la séance
 * - 0.4.a  : - liste des étapes d'une séance plus compact
 *            - création de Utils (pour les fonctions communes) avec parseDuration et formatDuration
 *            - séance ne peut pas être lancée si moins de 1 sec de durée totale
 *            - début page d'entrainement de la séance
 * - 0.4.b  : - changement SeanceEtape de targetZones: List<String> à zoneCibleNum: Int
 *            - entrainement de la séance : les 3 barres avec textes haut et bas ; déroulé de la séance avec go/pause/reprise
 * - 0.4.c :  - entrainement de la séance : ajout coeur+bpm
 *            - séances : play conditionné à un capteur connecté
 * - 0.4.d :  - séances : message si pas de capteur connecté, sans perte de place avec la liste des séances
 *            - dupliquer une étape avec double-tap
 * - 0.5.a :  - réorganisation des annonces dans Libre et Séance
 *            - Stats de fin de séance
 * - 1.0.0 :  - Possibilité de reprise de séance
 * - 1.1.0 :  - garder l'écran allumé sur les entrainements
 *            - entrainement séance avec timerDelay variable
 *            - annonces -10 sec, -5 sec et bips sur les 4 dernières secondes (4,3, 2 et 1)
 *            - rendre les stat de l'entrainement séance persitantes
 *            - ajout du coeur à suivre pendant une séance
 *            - Type de capteur devient mode démo
 *            - annonces bpm stoppable et pas les autres
 *            - problème sur duréee de la séance et sur les stats
 *            - ajout du temps restant pendant une séance
 *            - gestion dans SeanceCard (seances) de seance et/ou etapes nulles et étape durée nulle ou total < 3 sec
 *            - correction des beep de fin d'étape
 *            - ajout beep fin étape
 * - 1.2.0 :  - annonce de changement de zone lissée sur 3 secondes (paramètre)
 *            - choix de la durée du lissage et pas en capteur simulé
 * - 1.3.0_e :  - répétition (récursive) d'étapes : ok création et modification ; reste l'entrainement
 * - 1.3.0_f : - EntrainementSeanceScreen avec blocs
 * - 1.4.0_a : - EntrainementSeanceScreen titre avec x2/3 x6/10 etc en cas de groupes de groupe
 *        _b : - numéro de version associé aux sauvegardes des séances
 *             - correction des permissions accordées mais indiquées manquantes au premier lancement de l'application
 *             - indiquer niveau de batterie pour le capteur connecté (pas possible avant de connecter)
 *        _c   - stat reprise, si en cours, quand retour moins de 30 sec après quitter
 *             - déplacement des constantes (fonctionnelles) dans le fichier Constants
 *            - aller directement dans zones si pas de zone enregistrée en prefs
 *            - amélioration de l'implémentation de DEBUG_SIMUL_LIBRE et DEBUG_SIMUL_SEANCES
 *        _d  - dupliquer groupe, avec tout son contenu (trop petit pour une étape)
 *            - choix dernière étape d'un groupe lors de la dernière répétition
 * - 1.4.0 publiée
 * - 1.5.0_a  - utilisation de séance sans capteur cardio : préparation
 *        _b  - utilisation de séance sans capteur cardio
 *            - annonce : possibilité de n'avoir que les beep
 *            - bips dans annoncer
 *            - gestion du bouton du son dans annoncer
 *            - annonce all n'annonce pas les chg de zone
 *            - redirection auto pour définir zone cardio : que si capteur connecté
 *        _c  - rappel chaque x minutes en libre
 * - 1.5.0 publiée
 * - 1.6.0    - multiples corrections dans EntrainementLibreScreen
 * - 1.6.0 publiée
 * - 1.7.0_a  - pas de notification des permissions sur MainActivity (utilisation sans cardio)
 *            - ne pas couvrir la barre système/notification du haut
 *        _b  - annonces pendant séance : pas les zones sur pas capteur et ajouter nom de l'étape
 *            - dupliquer une séance
 *            - skip étape
 * - 1.7.0 publiée
 * - 1.8.0  - barre du haut des notifications système illisible
 * - 2.0.0_a  - stat FC avec % des zones cardio
 *            - statistiques avec énergie consommée (lipides/glucides et apports glucide nécessaires)
 *            - amélioration energie settings et choix de l'unité
 *            - bug : partie énergie perdue à la reprise => en cours
 *            - données énergie validées
 * - 2.0.0_b
 *            - désactiver capteur démo quand on le déconnecte
 *            - Stat énergie : ajout choix du niveau (débutant / entrainé)
 *            - Mode Debug d'arrêt auto des stats pour validation des données
 * - 2.0.0_c
 *            - entrainements libre et séance : changement de luminosité de l'écran par simple clic
 *            - forcer mode portrait
 *            - stats en pause si pas de nouveau bpm (bpm > 0) depuis au moins 8 sec (BPM_TIMEOUT_MS)
 * - 2.0.0_d
 *            - correction perte de capteur si trop loin
 *            - créer séance type si n'existe pas
 *            - pas de faible luminosité sur les écrans autres que les entrainements
 *            - ajouter séance type (si aucune ; ouversion suivante ?)
 * - 2.0.0_e
 *            - persistance du choix de luminosité
 *            - correction durée des stats quand bpm à 0
 * - 2.0.0_f
 *         f1 - vérifier/corriger stat durée augmente quand bpm à 0
 *         f2 - correction zone 5 pour calcul énergie et amélioration par lissage (ENERGIE_CALCUL_AVEC_LISSAGE)
 * - 2.0.0_f publiée
 * - 2.0.0_g  - ajout du type de sport
 * ==> publiée
 * - 2.0.0_h
 *            - page d'accueil : bouton déconnecter si capteur connecté
 *            - ajout du niveau de batterie
 * ==> publiée
 * - 2.0.0_i
 *          - adaptation de la partie stat/énergie pour les séances => en cours (manques lipides/glucides et libellé énergie
 *          - correction durée réel/total de la séance
 * ==> publiée
 * - 2.0.0_j
 *          - nettoyage de EntrainementLibreScreen
 *          - nettoyage de EntrainementSeanceScreen
 * - 2.0.0 => publiée
 * - 3.0.0 ; 03/02/2026 : capteur et stat en service/tâche de fond (en particulier pour entrainement libre)
 * ==> publiée (05/02/2026) : stats non alimentées par le service (application quittée)
 * - 3.1.0 : correction du service des stats (entrainement libre)
 * ==> publiée (06/02/2026)
 * - 3.1.0_a ; 10/02/2026 ; analyse pour correction et finalisation de l'évolution des stats de l'entrainement libre en service
 *        _b ; 10/02/2026 ; début de correction : service connecté à sensorManager (à la place de heartRateManager ; ajout de sensorManager dans AppContainer)
 *        => le service ne reçoit ni du capteur simulé ni du capteur réel (régression)
 *        _c ; 10/02/206 ; nouvelle correction (instance unique de sensorManager (AppContainer) pour tous)
 *        => service ok mais scan capteur réel ne trouve plus rien
 *        _d ; 10/02/2026 ; suppression du doublon ajoutBpm() dans l'entrainement libre ; initialisation de realHeartRateManager dans SensorManager
 *        => service ok et trouve le capteur réel
 *        => reset stat n'arrête pas le service
 *        -e : correction reset stat arrête aussi le service
 * ==> publiée 3.1.1 (11/02/2026)
 * - branche 3.2.x abandonnée :  bugs sur service stat et relais cardio BLE
 * - 3.3.0_a ; 11/02/2026 ; Relais cardio BLE
 *        _b ; 12/02/2026 ; corrections pour compilation
 * ==> publiée 12/02/2026 => plante quand on coche la case RCBle (Relais cardio BLE)
 *        _c ; 13/02/2026 ; correction plantage case Relais cardio BLE (permission BLUETOOTH_ADVERTISE)
 *        => ne plante plus sur la case RCBle, mais rien ne se passe
 *        _d ; 13/02/2026 ; ok
 * ==> publiée 3.3.0 (13/02/2026)
 * - 3.3.1 ; 13/02/2026 ; RCBle ok mais (Rouvy) pas de réception de bpm du capteur virtuel
 *        _a _b
 *        _c ; 14/02/2026 ; correction pb RCBle d'émission de bpm (GattServer appelé à la cnx du capteur entrant)
 * ==> publiée 3.3.2 (14/02/2026)
 * - 3.4.0 ; 14/02/2026 ; afficher le nom de l'appareil connecté à RCBle => en cours
 *           15/02/2026 ; amélioration visuelle accueil partie capteur et RCBle
 * ==> publiée
 *        _b ; 16/02/2026 ; nettoyage + correction nom appareil connecté à "Cardio Hraza"
 *        _c ; 17-18/02/2026 ;
 *             - modification GattServer pour comprendre et corriger les "-" ponctuel dans Rouvy (à la place des bpm)
 *             - rendre le nom du capteur virtuel ("Cardio Hraza") unique par l'ajout d'un aléa
 *        _d ; 18/02/2026 ; suite correction fiabilisation RCBle (bpm pontuellement à "-" coté Rouvy)
 *        => toujours le problème dans Rouvy et le nom du capteur cardio virtuel ne change pas (trop tard) dans Rouvy)
 *        _e ; 19/02/2026 ; correction nom avec aléa du RCBle
 *        => en cours : correction sur "-" Rouvy avec chat Firefox
 *        _f ; 10/03/2026 ; correction des bpm "-" => encore le problème
 *        _g ; 11/03/2026 ; (suite) suppression de la gestion de "queue" des bpm
 *- 3.5.0 ; 11/03/2026
 *        _a ; 11-12/03/2026
 *             - ajout de Cardiac Strain Balance (CSB) (équiv. W balance) dans les stats Entrainement libre
 *             - clic sur notification RCBle pour retour à l'appli => à valider
 *             - stats : séparation fenêtre modale information et paramètres en 2
 *        _c et _d ; 12-13/03/2026 ; branche abandonnée : mauvaises corrections du bpm "-"
 *        _e ; 14-15/03/2026 ;
 *            - remplacer StateFlow<Int> par Flow<HeartRateSample>
 *            - correction CSB sur reset
 *        _f ; 15/03/2026 ; amélioration du CSB
 *        => plus de bpm capteur réel
 *- 3.5.1 ; 16/03/2026 ;
 *            - correction bug bpm 0 capteur réel
 *            - repositionnement du bloc des stats
 *
 *
 *
 ***********/


private const val APP_VERSION = "3.6.0"
const val APP_SEANCE_VERSION = 1


/*************************************
 * Abandonné :
 * - déplacer une étape en la prenant => pas assez ergonomique
 *
 * Todo :
 * - après affichage résumé séance, la reset
 * - enregistrer activité (globale puis détaillée) avec voir et détails
 * - voir évolutions séances
 * - vérifier zones cardio en manuel
 * - stat en service pour continuer quand activity quittée
 * - option de création de capteur en re-émission
 * -
 **************************************/




// Constantes de debug et tests
const val DEBUG_MODE = false
const val DEBUG_CAPTEUR_DEMO_AVEC_ZERO = DEBUG_MODE && false    // Capteur simulé avec bpm à 0
const val DEBUG_CAPTEUR_DEMO_FIXE = DEBUG_MODE && true         // Capteur simulé fixe (prioritaire sur linéaire)
const val DEBUG_CAPTEUR_DEMO_VALEUR_INIT = 155                  // Valeur fixe pour le capteur simulé
const val DEBUG_CAPTEUR_DEMO_LINEAIRE = DEBUG_MODE && false     // Capteur simulé parcours toutes les valeurs linérairement
const val DEBUG_STATS_AVEC_NB = DEBUG_MODE && true             // Bloc des stats (entrainement libre) avec nombre de valeurs de bpm reçu
const val DEBUG_SIMUL_LIBRE = DEBUG_MODE && true                // Active le capteur simulé et navige sur entrainement libre
const val DEBUG_SIMUL_SEANCES = DEBUG_MODE && false             // Active le capteur simulé et navige sur seances
const val DEBUG_ALL_DEVICE = DEBUG_MODE && false                // Au scan, affiche tous des device bleutooth, y compris ceux n'émettant pas de FC
const val DEBUG_STAT_AUTO_STOP = DEBUG_MODE && false            // Arrêt automatique des stat au bout de x seconde (pour validation des valeurs)
const val DEBUG_STAT_AUTO_STOP_DUREE = 10 * 60_000L - 250L      // x * ... avec x le nombre de minutes


class MainActivity : ComponentActivity() {

    private lateinit var zoneManager: ZoneManager
    private lateinit var sensorManager: SensorManager

    private var announcer: Announcer? = null
    private var realProviderAvailable: Boolean = false

//    private val appContainer by lazy { AppContainer(this) }
//    private val clientName by appContainer.heartRateGattServer.connectedClientName.collectAsState()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        // Création de séance type si n'existe pas
        SeanceRepository(applicationContext).ensureDefaultSeancesIfNeeded()

        // --- création des managers et liaison au lifecycle (conserver) ---
        zoneManager = ZoneManager(applicationContext, lifecycleScope)

        val app = applicationContext as Hraza
        sensorManager = app.appContainer.sensorManager

        val heartRateGattServer = app.appContainer.heartRateGattServer

        // If RealHeartRateManager is available, register it into the SensorManager (fail-safe)
        try {

            realProviderAvailable = true
        } catch (_: Throwable) {
            realProviderAvailable = false
        }

        // Provide adapter to ZoneManager so it consumes sensorManager's bpm/connected flows
        val sensorProviderAdapter = object : HeartRateProvider {
            override val bpmFlow = sensorManager.bpmFlow
            override val connected = sensorManager.connected
            override val isReconnecting = sensorManager.isReconnecting

            override fun start() { /* no-op */ }
            override fun stop() { /* no-op */ }
            override fun connect(address: String?) { sensorManager.connectToDevice(address) }
            override fun disconnect() { sensorManager.disconnectDevice() }
            override fun dispose() {  }
        }

        // Give ZoneManager the adapter as its BPM provider
        zoneManager.setProvider(sensorProviderAdapter)

        announcer = Announcer(applicationContext)

        WindowCompat.setDecorFitsSystemWindows(window, true)

        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
        }

        setContent {
            AppContent(
                zoneManager = zoneManager
                , sensorManager = sensorManager
                // , virtualDeviceName = app.appContainer.virtualDeviceName
                , heartRateGattServer = heartRateGattServer
                , announcer = announcer
                , realProviderAvailable = realProviderAvailable
            )
        }
    }


    override fun onDestroy() {
        try { announcer?.close() } catch (_: Throwable) {}
        try { zoneManager.dispose() } catch (_: Throwable) {}
        try { sensorManager.disconnectDevice() } catch (_: Throwable) {}
        try { sensorManager.dispose() } catch (_: Throwable) {}
        super.onDestroy()
    }

    // Luminosité de l'écran
    fun toggleScreenBrightness() {
        val mode = getLuminositeEntrainemente(applicationContext)
        val newMode =
            if (mode == TrainingBrightnessMode.NORMAL)
            {
                TrainingBrightnessMode.LOW
            }
            else
            {
                TrainingBrightnessMode.NORMAL
            }
        setLuminositeEntrainemente(applicationContext, newMode)
        applyTrainingBrightness(newMode)
    }

}

/* ---------------- App content and screens ---------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContent(zoneManager: ZoneManager, sensorManager: SensorManager
               // , virtualDeviceName: String
               , heartRateGattServer: HeartRateGattServer, announcer: Announcer?, realProviderAvailable: Boolean) {
    val ctx = LocalContext.current
    val navController = rememberNavController()

    val hasAutoNavigatedToZones = rememberSaveable { mutableStateOf(false) }

    // disclaimer
    var disclaimerAccepted by remember { mutableStateOf(loadDisclaimerAccepted(ctx)) }

    // Pour teste --- Debug : naviguer une fois vers "libre" si demandé ---
    // flag mémoire pour ne naviguer qu'une fois
    val _navigatedToLibre = remember { mutableStateOf(false) }
    LaunchedEffect(disclaimerAccepted) {
        if (!disclaimerAccepted) return@LaunchedEffect

        if (!_navigatedToLibre.value) {
            if (DEBUG_MODE) {
                Toast.makeText(ctx, "Mode DEBUG !", Toast.LENGTH_LONG).show()
            }
            when {
                DEBUG_SIMUL_LIBRE -> {
                    navController.navigate("entr_libre")
                    _navigatedToLibre.value = true
                }
                DEBUG_SIMUL_SEANCES -> {
                    navController.navigate("seances")
                    _navigatedToLibre.value = true
                }
            }
        }
    }


    val sensorState by sensorManager.sensorState.collectAsState()
    val avecCardio = sensorState.connected

    LaunchedEffect(disclaimerAccepted) {
        if (!disclaimerAccepted) return@LaunchedEffect
        if (!hasAutoNavigatedToZones.value && avecCardio) {
            val hasFcMax = storedFcMaxExists(ctx)

            if (!hasFcMax) {
                hasAutoNavigatedToZones.value = true
                navController.navigate("zones") {
                    popUpTo("home") { inclusive = false }
                    launchSingleTop = true
                }
            }
        }
    }


    // permissions handling
    val requiredPerms = remember { PermissionHelper.requiredBlePermissions(ctx).toList() }

    val activity = LocalContext.current as Activity

    // initial permission map
    var permissionsStatus by remember {
        mutableStateOf(requiredPerms.associateWith { p -> ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED })
    }
    var permanentlyDenied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(RequestMultiplePermissions()) { result ->
        permissionsStatus = requiredPerms.associateWith { p -> result[p] == true }
        permanentlyDenied = requiredPerms.any { p ->
            val granted = result[p] == true
            val shouldShow = activity.shouldShowRequestPermissionRationale(p)
            (!granted && !shouldShow)
        }
    }

    val allPermsGranted by derivedStateOf { PermissionHelper.hasAllBlePermissions(ctx) }

    // emulator detection -> auto-simulate
    fun isEmulator(): Boolean {
        val f = Build.FINGERPRINT ?: ""
        val m = Build.MODEL ?: ""
        val manu = Build.MANUFACTURER ?: ""
        return f.startsWith("generic") || f.startsWith("unknown") ||
                m.contains("Emulator") || m.contains("google_sdk") || manu.contains("Genymotion")
    }

    // simulated flag: default true on emulator or if no real provider available
    var simulated by rememberSaveable {
        mutableStateOf(
            DEBUG_SIMUL_LIBRE ||
                    DEBUG_SIMUL_SEANCES ||
                    !realProviderAvailable ||
                    isEmulator()
        )
    }

    LaunchedEffect(simulated) { sensorManager.setSimulated(simulated) }

    MaterialTheme {

        val currentBackStackEntry = navController.currentBackStackEntryFlow.collectAsState(initial = navController.currentBackStackEntry)

        Scaffold(
            topBar = {

                // hide the global top bar when on the sensors screen so SensorSelectionScreen can use full top area
                val currentRoute = currentBackStackEntry.value?.destination?.route

                if (currentRoute == "home") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(vertical = 8.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.wrapContentWidth(),
                        )
                    }

                } else {
                    // no topBar when on sensors screen
                    Spacer(modifier = Modifier.height(0.dp))
                }
            },


        ) { padding ->
            Box(modifier = Modifier
                .padding(padding)
                .fillMaxSize()) {
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreenReordered(
                            sensorManager = sensorManager,
                            // virtualDeviceName = virtualDeviceName,
                            heartRateGattServer = heartRateGattServer,
                            onOpenEntrLibre = { navController.navigate("entr_libre") },
                            onOpenZones = { navController.navigate("zones") },
                            onOpenSensors = { navController.navigate("sensors") },
                            onOpenSeances = { navController.navigate("seances") },
                        )
                    }
                    composable("zones") {
                        ZonesConfigScreen(onBack = { navController.popBackStack() })
                    }
                    composable("sensors") {
                        SensorSelectionScreen(
                            onBack = { navController.popBackStack() },
                            sensorManager = sensorManager
                        )
                    }
                    composable("entr_libre") {
                        EntrainementLibreScreen(
                            onBack = { navController.popBackStack() },
                            sensorManager = sensorManager,
                            heartRateGattServer= heartRateGattServer,
                            announcer = announcer
                        )
                    }
                    composable("seances") {
                        SeancesScreen(
                            onBack = { navController.popBackStack() },
                            sensorManager = sensorManager,
                            onOpenSeanceEdit = { seance ->
                                if (seance == null) navController.navigate("seance_edit") else navController.navigate("seance_edit/${seance.id}")
                            },
                            onOpenEntrSeance = { seance, avecCardio ->
                                navController.navigate(
                                    "entr_seance/${seance.id}?avecCardio=$avecCardio"
                                )
                            }


                        )

                    }
                    // Route pour création sans id
                    composable("seance_edit") {
                        SeanceEditV2Screen(onBack = { navController.popBackStack() }, seanceId = null)
                    }
                    // Route pour édition avec id
                    composable(
                        route = "seance_edit/{seanceId}",
                        arguments = listOf(navArgument("seanceId") { type = NavType.IntType })
                    ) { backstackEntry ->
                        val id = backstackEntry.arguments?.getInt("seanceId")
                        SeanceEditV2Screen(onBack = { navController.popBackStack() }, seanceId = id)
                    }
                    // Route pour lancer une séance : entr_seance/{seanceId}
                    composable(
                        route = "entr_seance/{seanceId}?avecCardio={avecCardio}",
                        arguments = listOf(
                            navArgument("seanceId") { type = NavType.IntType },
                            navArgument("avecCardio") {
                                type = NavType.BoolType
                                defaultValue = true
                            }
                        )
                    ) { backStackEntry ->
                        val id = backStackEntry.arguments?.getInt("seanceId") ?: run {
                            navController.popBackStack()
                            return@composable
                        }

                        val avecCardio = backStackEntry.arguments?.getBoolean("avecCardio") ?: true

                        EntrainementSeanceScreen(
                            onBack = { navController.popBackStack() },
                            sensorManager = sensorManager,
                            announcer = announcer,
                            seanceId = id,
                            avecCardio = avecCardio
                        )
                    }

                }

                if (!disclaimerAccepted) {
                    DisclaimerDialog(
                        defaultChecked = false,
                        onAccept = {
                            saveDisclaimerAccepted(ctx, true)
                            disclaimerAccepted = true
                        },
                        onQuit = { (ctx as? Activity)?.finish() }
                    )
                }
            }
        }
    }
}

/* -------------- Home screen with the requested order -------------- */

@Composable
fun HomeScreenReordered(
    sensorManager: SensorManager,
    // virtualDeviceName: String,
    heartRateGattServer: HeartRateGattServer,
    onOpenEntrLibre: () -> Unit,
    onOpenZones: () -> Unit,
    onOpenSensors: () -> Unit,
    onOpenSeances: () -> Unit,
) {
    val ctx = LocalContext.current

// storedMode : relit les prefs au retour à l'écran (ON_RESUME), évitant le décalage
    var storedMode by remember { mutableStateOf(loadZonedMode(ctx)) }

    val lifecycleOwner = rememberUpdatedState(LocalLifecycleOwner.current)
    DisposableEffect(lifecycleOwner.value) {
        val owner = lifecycleOwner.value
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // relire la préférence à chaque resume
                storedMode = loadZonedMode(ctx)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

// Lire les autres prefs (lecture directe ici acceptable)
    val storedFcMax = loadStoredFcMax(ctx)
    val storedFcMin = loadStoredFcMin(ctx)
    val storedManualValues = loadStoredManualCsv(ctx).split(",").mapNotNull { it.trim().toIntOrNull() }

// UTILISER storedMode (variable d'état rafraîchie) pour l'affichage et le calcul des zones
    val zonesToShow = when (storedMode) {
        ZonesModeUi.KARVONEN -> computeZonesKarvonen(storedFcMin, storedFcMax)
        ZonesModeUi.MANUAL -> computeZonesFromManual(storedManualValues)
        else -> computeZonesFromFcMax(storedFcMax)
    }


    // flows from sensorManager (authoritative for device state) and zoneManager (for zone calc state)
    val sensorState by sensorManager.sensorState.collectAsState()
    val avecCardio = sensorState.connected
    val displayNameFromSensor = sensorState.deviceName
    // val bpm by sensorManager.bpmFlow.collectAsState(initial = 0)
    val sample by sensorManager.bpmFlow.collectAsState(
        initial = HeartRateSample(0, 0)
    )
    val bpm = sample.bpm

    // RCBle
    val clientName by heartRateGattServer.connectedClientName.collectAsState()
    val notifySuccess by heartRateGattServer.lastNotifySuccess.collectAsState()

    // Colonne principale
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {

        // Choix du capteur et éventuellement capteur connecté (+ relais cardio BLE et déconnexion)
        Column() {

            val ctx = LocalContext.current
            val hraza = ctx.applicationContext as Hraza
            val preferenceStore = hraza.relayPreferenceStore
            val activity = ctx as Activity

            // Déconnexion et choix du capteur
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                // Si capteur connecter : bouton de déconnexion
                if (avecCardio) {
                    Button(
                        onClick = {
                            try {
                                sensorManager.disconnectDevice()
                                // Si on était en mode simulé, on le désactive aussi
                                if (sensorState.modeSimulated) {
                                    sensorManager.setSimulated(false)
                                }
                                CoroutineScope(Dispatchers.Main).launch {
                                    preferenceStore.setRelayEnabled(false)
                                }
                            } catch (_: Throwable) {
                            }
                        },
                        modifier = Modifier.defaultMinSize(minWidth = 96.dp)
                    ) {
                        Text(text = stringResource(id = R.string.disconnect))
                    }
                } else {
                    Text(text = stringResource(id = R.string.no_sensor_connected), style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(modifier = Modifier.width(12.dp))

                // aller au choix du capteur
                Button(onClick = onOpenSensors) {
                    Text(text = stringResource(id = R.string.choose_sensor_button))
                }

            }

            // Tout le reste seulement si un capteur est connecté (nom du capteur et case RCBle)
            if (avecCardio) {

                Spacer(modifier = Modifier.height(6.dp))

                // Information sur le capteur connecté
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val displayName = displayNameFromSensor?.takeIf { it.isNotBlank() } ?: stringResource(R.string.device_unknown)
                    Text(text = "Connecté à ", style = MaterialTheme.typography.bodySmall)
                    Text(text = "$displayName : $bpm BPM", style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Relais cardio BLE
                val relayEnabled by preferenceStore.relayEnabledFlow.collectAsState(initial = false)

                // Case à cocher pour activer le Relais Cardio BLE (RCBle)
                // Nom du capteur cardio virtuel
                val virtualDeviceName = hraza.appContainer.virtualDeviceName
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(modifier = Modifier.size(18.dp),
                        checked = relayEnabled,
                        onCheckedChange = { checked ->
                            if (checked) {

                                if (ContextCompat.checkSelfPermission(
                                        ctx,
                                        Manifest.permission.BLUETOOTH_ADVERTISE
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {

                                    ActivityCompat.requestPermissions(
                                        activity,
                                        arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE),
                                        1001
                                    )

                                } else {
                                    hraza.appContainer.refreshVirtualDeviceName()
                                    CoroutineScope(Dispatchers.Main).launch {
                                        preferenceStore.setRelayEnabled(true)
                                    }
                                }

                            } else {
                                CoroutineScope(Dispatchers.Main).launch {
                                    preferenceStore.setRelayEnabled(false)
                                }
                            }

                        }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(if (relayEnabled) "\"$virtualDeviceName\" actif" else "Démarrer le capteur virtuel")
                }


                // Texte d'information
                Text(text = "(cocher cette case pour connecter un autre appareil au capteur virtuel \"$CAPTEUR_VIRTUEL_NOM xxx\" et y recevoir par rebond votre FC)", style = MaterialTheme.typography.bodySmall)

                // Appareil connecté au Relais cardio BLE
                if (relayEnabled && clientName != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("$clientName est connecté à $virtualDeviceName")
                    if (notifySuccess == false) {
                        Text(
                            text = "BLE SEND ERROR",
                            color = Color.Red
                        )
                    }

                }


            }

        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))

        // Ligne d'accès rapide : Libre (gauche) + Séances (droite)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Libre : activable seulement si un capteur est connecté
            Button(
                onClick = { if (sensorState.connected) onOpenEntrLibre() else {} },
                enabled = sensorState.connected,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = stringResource(id = R.string.but_libre))
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Séances : liste des séances
            Button(
                onClick = { onOpenSeances() },
                modifier = Modifier.weight(1f)
            ) {
                Text(text = stringResource(id = R.string.but_seances))
            }

        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))

        // ------------- Zones section (kept as before, scrollable)
        // 7. Mode zones + button to open config (mode on left, button on right)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.zones_mode, when (storedMode) {
//                    ZonesModeUi.SIMPLIFIED -> stringResource(R.string.zone_simple)
                    ZonesModeUi.KARVONEN -> stringResource(R.string.zone_karvonen)
                    ZonesModeUi.MANUAL -> stringResource(R.string.zone_manual)
                    else -> stringResource(R.string.zone_simple)
                }),
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onOpenZones) { Text(stringResource(R.string.zones)) }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 8. Zones preview (kept unchanged)
        ZonesPreview(zonesToShow, true)

        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.app_version, APP_VERSION),
                fontSize = 12.sp,
            )
        }

    }

}


/* ---------------- helpers ---------------- */

fun openAppSettings(ctx: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", ctx.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    ctx.startActivity(intent)
}
