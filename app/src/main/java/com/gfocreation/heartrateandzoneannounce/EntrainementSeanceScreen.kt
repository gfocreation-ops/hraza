package com.gfocreation.heartrateandzoneannounce

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import android.os.SystemClock
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.fromColorLong
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gfocreation.heartrateandzoneannounce.model.*
import kotlin.math.max
import com.gfocreation.heartrateandzoneannounce.misc.SensorManager
import com.gfocreation.heartrateandzoneannounce.misc.applyTrainingBrightness
import com.gfocreation.heartrateandzoneannounce.misc.computeZonesFromFcMax
import com.gfocreation.heartrateandzoneannounce.misc.computeZonesFromManual
import com.gfocreation.heartrateandzoneannounce.misc.computeZonesKarvonen
import com.gfocreation.heartrateandzoneannounce.misc.energieToDisplay
import com.gfocreation.heartrateandzoneannounce.misc.energieValeurAff
import com.gfocreation.heartrateandzoneannounce.misc.formatMsToHms
import com.gfocreation.heartrateandzoneannounce.misc.getLuminositeEntrainemente
import com.gfocreation.heartrateandzoneannounce.misc.loadStat
import com.gfocreation.heartrateandzoneannounce.misc.loadStoredFcMax
import com.gfocreation.heartrateandzoneannounce.misc.loadStoredFcMin
import com.gfocreation.heartrateandzoneannounce.misc.loadStoredManualCsv
import com.gfocreation.heartrateandzoneannounce.misc.loadZonedMode
import com.gfocreation.heartrateandzoneannounce.misc.saveStat
import com.gfocreation.heartrateandzoneannounce.ui.EtapeRun
import com.gfocreation.heartrateandzoneannounce.ui.flattenBlocksWithLabels
import com.gfocreation.heartrateandzoneannounce.misc.resetScreenBrightness
import kotlinx.coroutines.isActive
import kotlin.math.abs



enum class RunState { NotStarted, Running, Paused }

// --- Insert this helper near the top of the file (above the Composable) ---
/**
 * Retourne Pair(indexEtape, elapsedMsDansCetteEtape) en partant d'un elapsedTotalMs
 */
private fun computeStepFromElapsed(elapsedTotalMs: Long, steps: List<EtapeRun>): Pair<Int, Long> {
    if (steps.isEmpty()) return 0 to 0L
    var acc = 0L
    for ((idx, s) in steps.withIndex()) {
        val dur = max(1L, s.etape.durationMillis)
        val next = acc + dur
        if (elapsedTotalMs < next) {
            val elapsedInStep = (elapsedTotalMs - acc).coerceAtLeast(0L)
            return idx to elapsedInStep
        }
        acc = next
    }
    // si on dépasse la durée totale -> dernière étape finie
    val lastIndex = steps.lastIndex
    val lastElapsed = steps.lastOrNull()?.etape?.durationMillis ?: 0L
    return lastIndex to lastElapsed
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntrainementSeanceScreen(
    onBack: () -> Unit,
    sensorManager: SensorManager,
    announcer: Announcer?,
    seanceId: Int,
    avecCardio: Boolean) {

    val ctx = LocalContext.current

    // --- Gestion lifecycle : reset luminosité ---
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                val activity = ctx as? Activity
                activity?.resetScreenBrightness()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val view = LocalView.current
    // Empêche la mise en veille de l’écran
    SideEffect {
        view.keepScreenOn = true
    }

    // Luminosité de l'écran
    LaunchedEffect(Unit) {
        val activity = ctx as? Activity
        val mode = getLuminositeEntrainemente(ctx)
        activity?.applyTrainingBrightness(mode)
    }

    // Chargement des zones
    val storedMode = remember { loadZonedMode(ctx) }
    val storedFcMax = loadStoredFcMax(ctx)
    val storedFcMin = if (storedMode == ZonesModeUi.KARVONEN) loadStoredFcMin(ctx) else 0
    val zones = remember(storedMode, storedFcMax, storedFcMin) {
        when (storedMode) {
            ZonesModeUi.KARVONEN -> computeZonesKarvonen(storedFcMin, storedFcMax)
            ZonesModeUi.MANUAL -> computeZonesFromManual(
                loadStoredManualCsv(ctx).split(",").mapNotNull { it.trim().toIntOrNull() }
            )
            else -> computeZonesFromFcMax(storedFcMax)
        }
    }

    val userPhysioParams = remember {
        UserPhysioParams.load(ctx)
    }

    // Statistiques locales pour la séance en cours (non-persistant par défaut)
    var statEntr by remember { mutableStateOf(loadStat(ctx, seanceId)) }
    LaunchedEffect(
        avecCardio,
        zones,
        userPhysioParams
    ) {
        if (avecCardio) {
            statEntr.initAddsIfNeeded(
                zones = zones,
                poidsKg = userPhysioParams.poidsKg,
                sexe = userPhysioParams.sexe,
                sportType = userPhysioParams.sportType,
                niveau = userPhysioParams.niveau,
                energyUnit = userPhysioParams.energyUnit
            )
        } else {
            statEntr.reset()
        }
    }


    var flattenedEtapeRuns by remember {
        mutableStateOf<List<EtapeRun>>(emptyList())
    }


    // contrôle du modal de stats à la fin
    var showStatsDialog by remember { mutableStateOf(false) }

    // état local pour refléter immédiatement le mode renvoyé par announcer.nextMode()
    var currentNiveau by remember { mutableStateOf(announcer?.getNiveau(avecCardio) ?: Announcer.AnnouncementNiveau.DEFAULT) }

    val repo = remember { SeanceRepository(ctx) }

    var seance by remember { mutableStateOf<Seance?>(null) }

    val totalSeance by remember {
        derivedStateOf { max(1L, flattenedEtapeRuns.sumOf { it.etape.durationMillis }) }
    }

    var currentEtapeRunIndex by remember { mutableStateOf(0) }
    var elapsedEtape by remember { mutableStateOf(0L) }
    var elapsedTotal by remember { mutableStateOf(0L) }
    var sessionStartElapsed by remember { mutableStateOf<Long?>(null) }
    var pausedAccumulated by remember { mutableStateOf(0L) }

    val sample by if (avecCardio) {
        sensorManager.bpmFlow.collectAsState(initial = HeartRateSample(0, 0))
    } else {
        remember { mutableStateOf(HeartRateSample(0, 0)) }
    }

    val bpmReel = sample.bpm

    // état d'exécution de la séance
    var runState by remember { mutableStateOf(RunState.NotStarted) }
    var isSessionFinished by remember { mutableStateOf(false) }

    // style texte partagé (même taille + gras)
    val textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)

    // Charge la séance
    LaunchedEffect(seanceId) {

        val s = repo.getAllSeance().find { it.id == seanceId }
        if (s == null) {
            Toast.makeText(ctx, "Séance introuvable", Toast.LENGTH_SHORT).show()
            onBack()
        } else {
            seance = s

            // Nouvelle ligne : on aplatit les blocs
            flattenedEtapeRuns = flattenBlocksWithLabels(s.blocks)

            if (flattenedEtapeRuns.isEmpty()) {
                Toast.makeText(ctx, "Séance vide ou invalide", Toast.LENGTH_SHORT).show()
                onBack()
            }

            // Charger les stats
            statEntr = loadStat(ctx, seanceId)

            if (s.dureeRealMillis > 0L) {
                elapsedTotal = s.dureeRealMillis
                sessionStartElapsed = SystemClock.elapsedRealtime() - elapsedTotal
                pausedAccumulated = 0L

                val (idx, elapsedInStep) = computeStepFromElapsed(elapsedTotal, flattenedEtapeRuns)
                currentEtapeRunIndex = idx
                elapsedEtape = elapsedInStep
                runState = RunState.Paused
            } else {
                currentEtapeRunIndex = 0
                elapsedEtape = 0L
                elapsedTotal = 0L
                runState = RunState.NotStarted
            }
        }

    }


    // Durée par défaut du timer (adapté par la suite si nécessaire)
    var timerDelay = 500L

    LaunchedEffect(runState) {

        when (runState) {
            RunState.Running -> {
                // début de la capture
                if (avecCardio) {
                    statEntr.start(ctx)
                }
            }
            RunState.Paused -> {
                if (avecCardio) {
                    statEntr.stop(ctx)
                }

            }
            RunState.NotStarted -> {
                // si on revient à NotStarted (ex : reset), on stoppe et reset éventuel
                if (avecCardio) {
                    statEntr.stop(ctx)
                }

            }
        }

        // sauvegarde automatique à la transition Paused / NotStarted
        if (runState == RunState.Paused || runState == RunState.NotStarted) {
            seance?.let { s ->
                val updated = s.copy(dureeRealMillis = elapsedTotal)
                try { repo.updateSeance(updated) } catch (_: Throwable) {}
                seance = updated
            }
        }

        if (runState == RunState.Running && seance != null) {

            val etapeRuns = flattenedEtapeRuns        // seance!!.etapes
            // Annonce du démarrage si première étape
            if (elapsedTotal == 0L && currentEtapeRunIndex == 0) {
                announcer?.announceTexte("Début de la séance ${seance!!.name}, étape une")
            }

            // --- flags locaux par étape (initialisés pour l'étape courante)
            var currentEtape = etapeRuns.getOrNull(currentEtapeRunIndex)?.etape
            val announceThresholds = mutableMapOf(
                10_000L to { announcer?.announceTexte("Encore 10 secondes") },
                5_000L  to { announcer?.announceTexte("5 secondes") }
            )
            val beepThresholds = mutableListOf(4000L, 3000L, 2000L, 1000L)

            // boucle principale
            while (isActive && runState == RunState.Running && !isSessionFinished) {

                // 1) si on a atteint la durée totale globale -> fin de séance
                if (elapsedTotal >= totalSeance) {
                    // ---- Fin de séance ----
                    isSessionFinished = true
                    elapsedTotal = totalSeance

                    if (avecCardio) {
                        statEntr.stop(ctx)
                    }

                    announcer?.announceTexte("Séance terminée")
                    showStatsDialog = true

                    seance?.let { s ->
                        // val updated = s.copy(dureeRealMillis = 0L)
                        val updated = s.copy(dureeRealMillis = totalSeance)
                        try { repo.updateSeance(updated) } catch (_: Throwable) {}
                        seance = updated
                    }

                    // reset local
                    sessionStartElapsed = null
                    pausedAccumulated = 0L

                    runState = RunState.NotStarted

                    break
                }

                // 2) (re)obtenir étape courante (peut avoir changé)
                val currentEtapeRun = etapeRuns.getOrNull(currentEtapeRunIndex) ?: break

                // recalcul remaining après incrément
                var remainingMs =
                    (currentEtapeRun.etape.durationMillis - elapsedEtape).coerceAtLeast(0L)

                // Ajuste timer selon le temps restant
                val timerDelay = when {
                    remainingMs <= 0L    -> 500L
                    remainingMs < 5_000L -> 100L
                    remainingMs < 15_000L -> 250L
                    else -> 500L
                }

                // attente
                delay(timerDelay)

                // incrément temporel
                val now = SystemClock.elapsedRealtime()
                elapsedTotal = (now - (sessionStartElapsed ?: now) - pausedAccumulated)
                    .coerceAtLeast(0L)

                val (idx, elapsedInStep) =
                    computeStepFromElapsed(elapsedTotal, flattenedEtapeRuns)

                currentEtapeRunIndex = idx
                elapsedEtape = elapsedInStep

                remainingMs =
                    (currentEtapeRun.etape.durationMillis - elapsedEtape).coerceAtLeast(0L)

                // Annonces à 5 et 10 sec
                val triggeredAnnounces = announceThresholds
                    .filterKeys { remainingMs <= it }

                for (action in triggeredAnnounces.values) {
                    action()
                }
                announceThresholds.keys.removeAll(triggeredAnnounces.keys)

                // Bips des 4 dernières secondes
                val triggeredBeeps = beepThresholds.filter { remainingMs <= it }
                for (threshold in triggeredBeeps) {
                    announcer?.beepCourtInterm()
                }
                beepThresholds.removeAll(triggeredBeeps)


                // 3) Si l'étape est terminée -> avancer ou finir la session
                if (elapsedEtape >= currentEtapeRun.etape.durationMillis) {
                    if (currentEtapeRunIndex < etapeRuns.lastIndex) {

                        announcer?.beepCourtFin()

                        // Passage à l'étape suivante : réinitialiser les flags pour la prochaine étape
                        currentEtapeRunIndex++

                        // Reset des annonces 5, 10 sec et des bips 4 dernières secondes
                        beepThresholds.clear()
                        beepThresholds.addAll(listOf(4000L, 3000L, 2000L, 1000L))
                        announceThresholds.clear()
                        announceThresholds[10_000L] = { announcer?.announceTexte("Encore 10 secondes") }
                        announceThresholds[5_000L]  = { announcer?.announceTexte("5 secondes") }

                        currentEtape = etapeRuns.getOrNull(currentEtapeRunIndex)?.etape

                        // Délai entre annonces/beep de fin d'étape et début suivante
                        // mais impact la comptabilisation de la durée totale de la séance
                        // delay(500)

                        // Message à énoncer
                        var message = "Étape ${currentEtapeRunIndex + 1}"
                        if (avecCardio) {
                            // val zoneNom = zones.getOrNull(etapeRuns.getOrNull(currentEtapeRunIndex)?.etape?.zoneId ?: 0)?.key
                            val zoneNom = zones.getOrNull(currentEtape?.zoneId ?: 0)?.key
                            if (!zoneNom.isNullOrEmpty()) {
                                val zoneNom = zones.getOrNull(currentEtape?.zoneId ?: 0)?.key
                                message += ", zone $zoneNom"
                            }
                        }
                        if (!currentEtape?.description.isNullOrEmpty()) {
                            message += ", ${currentEtape?.description}"
                        }

                        announcer?.announceTexte(message)

                    } else {
                        // dernière étape terminée => finalisation
                        isSessionFinished = true
                        if (avecCardio) {
                            statEntr.stop(ctx)
                        }

                        announcer?.announceTexte("Séance terminée")
                        showStatsDialog = true

                        seance?.let { s ->
                            val updated = s.copy(dureeRealMillis = 0L)
                            try { repo.updateSeance(updated) } catch (_: Throwable) {}
                            seance = updated
                        }

                        elapsedTotal = 0L
                        currentEtapeRunIndex = 0
                        runState = RunState.NotStarted

                        break
                    }
                }

            }
        }

    }


    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = seance?.name ?: "Entraînement",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        // sauvegarde de la progression actuelle avant de quitter
                        seance?.let { s ->
                            val updated = s.copy(dureeRealMillis = elapsedTotal)
                            try { repo.updateSeance(updated) } catch (_: Throwable) {}
                            seance = updated
                        }
                        announcer?.stop()
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                },

                actions = {
                    // Bouton Go / Pause / Reprise à droite du titre
                    val label = when (runState) {
                        RunState.NotStarted -> "Go"
                        RunState.Running -> "Pause"
                        RunState.Paused -> "Reprise"
                    }
                    val coul = when (runState) {
                        RunState.NotStarted -> Color(0xFF4CAF50)
                        RunState.Running -> Color.Red
                        RunState.Paused -> Color.fromColorLong( 0xFF4CAF50)
                    }

                    Button(
                        onClick = { runState = when (runState) {
                            RunState.NotStarted -> {
                                // Début d'une nouvelle séance : s'assurer que tout est remis à zéro
                                sessionStartElapsed = SystemClock.elapsedRealtime()
                                pausedAccumulated = 0L
                                elapsedTotal = 0L
                                elapsedEtape = 0L
                                currentEtapeRunIndex = 0
                                isSessionFinished = false
                                statEntr.reset()
                                RunState.Running

                            }
                            RunState.Running -> {
                                // Pause : arrêter la capture de stats
                                if (avecCardio) {
                                    statEntr.stop(ctx)
                                }
                                RunState.Paused
                            }
                            RunState.Paused -> {
                                // Reprise : redémarrer la capture
                                statEntr.start(ctx)
                                RunState.Running
                            }
                        }
                                  },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = coul,
                            contentColor = Color.White
                        )
                    ) {
                        Text(label)
                    }

                }
            )
        }
    ) {
        innerPadding ->
        seance?.let { seance ->

            val totalEtapes = flattenedEtapeRuns.size
            // récupérer l'étape courante et si nulle => sortir du let (rien à afficher)
            // val currentEtape = flattenedEtapes.getOrNull(currentEtapeIndex) ?: return@let
            val currentRun = flattenedEtapeRuns.getOrNull(currentEtapeRunIndex) ?: return@let
            val currentEtape = currentRun.etape
            val repetitionLabel = currentRun.label


            // zone spec et autres usages peuvent maintenant être non-null
            val currentZoneSpec = CANONICAL_ZONES.get(currentEtape.zoneId)
            val coulEtape = currentZoneSpec.color

            val currentZone = zones.getOrNull(currentEtape.zoneId) ?: zones.first()
            val minBpm = currentZone.minBpm
            val maxBpm = currentZone.maxBpm

            // description (String non-null)
            val description = currentEtape.description.takeIf { it.isNotBlank() }
            val topText = buildString {
                if (!repetitionLabel.isNullOrBlank()) {
                    append("$repetitionLabel - ")
                }
                append("${currentEtapeRunIndex + 1}/$totalEtapes")

                if (description != null) {
                    append(" : $description")
                }

             }


            // calculs pour les barres (éviter division par zéro)
            val durEtape = max(1L, currentEtape.durationMillis)
            val totalSeance = max(1L, flattenedEtapeRuns.sumOf { it.etape?.durationMillis ?: 0L })
            val progressEtape = (elapsedEtape.toFloat() / durEtape.toFloat()).coerceIn(0f, 1f)
            val progressTotal = (elapsedTotal.toFloat() / totalSeance.toFloat()).coerceIn(0f, 1f)


            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxSize()
            ) {

                // Pour action sur la luminosité
                val activity = LocalContext.current as MainActivity
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    activity.toggleScreenBrightness()
                                }
                            )
                        }
                ) {  }

                IconButton(
                    onClick = {
                        val currentEtape =
                            flattenedEtapeRuns
                                .getOrNull(currentEtapeRunIndex)
                                ?.etape
                                ?: return@IconButton

                        val remaining = currentEtape.durationMillis - elapsedEtape
                        if (remaining > 0) {
                            pausedAccumulated -= remaining
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .size(48.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .border(3.dp, Color(0xFF369be3), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(R.drawable.skip_next_24dp),
                            contentDescription = "Passer l'étape",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                IconButton(
                    onClick = {
                        val newNiveau = announcer?.nextNiveau(avecCardio) ?: return@IconButton
                        currentNiveau = newNiveau
                        Toast.makeText(ctx, newNiveau.label, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(48.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .border(3.dp, currentNiveau.borderColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(currentNiveau.iconRes),
                            contentDescription = currentNiveau.contentDescription,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }


                // --- Column inchangée — occupe tout l'espace et n'est pas affectée par le bouton ---
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    // Ligne du haut (centrée)
                    Text(
                        text = topText,
                        style = textStyle,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Spacer(Modifier.height(8.dp))

                    // --- Trois colonnes, même hauteur, sans scroll ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // --- Colonne GAUCHE ---
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Durée totale de l'étape (haut)
                            Text(
                                text = formatMsToHms(currentEtape?.durationMillis ?: 0L),
                                style = textStyle
                            )

                            // Rectangle couleur zone + progression (rempli du bas vers le haut)
                            // -> BoxWithConstraints pour positionner le label "temps restant" sur la limite haute du remplissage
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .background(Color.Transparent),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                val density = LocalDensity.current
                                val boxHeightPx = constraints.maxHeight.toFloat()

                                // hauteur remplie (px)
                                val filledHeightPx = (progressEtape * boxHeightPx).coerceIn(0f, boxHeightPx)

                                // position Y (px) de la limite haute du remplissage (0 = top)
                                val topOfFilledPx = (boxHeightPx - filledHeightPx).coerceIn(0f, boxHeightPx)

                                // on veut centrer le texte sur cette limite -> on utilisera la moitié de la hauteur du textBox pour l'ajustement
                                val textBoxHeightDp = 20.dp
                                val textYOffset = with(density) {
                                    // y offset depuis le top de la box : topOfFilledPx - (textHeight/2)
                                    (topOfFilledPx - textBoxHeightDp.toPx() / 2f).toDp().coerceAtLeast(0.dp)
                                }

                                // fond : plein couleur de la zone
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(coulEtape)
                                )

                                // progression (rempli du bas vers le haut) — on fixe la hauteur via .height(...)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(with(density) { filledHeightPx.toDp() })
                                        .align(Alignment.BottomCenter)
                                        .border(2.dp, Color.DarkGray)
                                )

                                // Texte : temps restant de l'étape, centré horizontalement et positionné sur la limite haute de la progression
                                val remainingEtape = (durEtape - elapsedEtape).coerceAtLeast(0L)
                                Text(
                                    text = formatMsToHms(remainingEtape, true),
                                    style = textStyle,
                                    // style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .offset(y = textYOffset),
                                    textAlign = TextAlign.Center
                                )
                            }


                            // Temps passé sur cette étape (bas)
                            Text(text = formatMsToHms(elapsedEtape), style = textStyle)
                        }

                        if (avecCardio) {
                            // --- Colonne CENTRALE ---
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Fc max de la zone (haut)
                                Text(text = "${maxBpm} bpm", style = textStyle)

                                BoxWithConstraints(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .background(coulEtape),
                                ) {
                                    val boxHeightPx = constraints.maxHeight.toFloat()
                                    val density = LocalDensity.current

                                    // taille visuelle du coeur
                                    val heartSizeDp = 56.dp
                                    val heartSizePx = with(density) { heartSizeDp.toPx() }

                                    // --- Calcul position verticale du centre du coeur selon bpmReel ---
                                    fun bpmToCenterYPx(bpm: Float): Float {
                                        // cas limites globaux : on mappe min->bottom, max->top
                                        if (bpm >= maxBpm) return 0f
                                        if (bpm <= minBpm) return boxHeightPx

                                        // fraction 0..1 où 0 => minBpm, 1 => maxBpm
                                        val fracFromMin =
                                            (bpm - minBpm).toFloat() / (maxBpm - minBpm).toFloat()
                                        // nous voulons: min -> bottom (y = boxHeightPx), max -> top (y = 0)
                                        // donc centreY = boxHeightPx * (1 - fracFromMin)
                                        return (1f - fracFromMin) * boxHeightPx
                                    }

                                    // target center Y (en px), puis clamp pour que le centre reste entièrement visible
                                    val rawTargetCenterYPx = bpmToCenterYPx(bpmReel.toFloat())
                                    val clampedTargetCenterYPx =
                                        rawTargetCenterYPx.coerceIn(0f, boxHeightPx)

                                    // animation de la position (on anime la position du centre en px)
                                    val animatedCenterYPx by animateFloatAsState(
                                        targetValue = clampedTargetCenterYPx,
                                        animationSpec = tween(
                                            durationMillis = 600,
                                            easing = LinearOutSlowInEasing
                                        ),
                                        label = "HeartYAnimation"
                                    )

                                    // conversion centrePx -> offsetDp pour placer le TOP du composant coeur :
                                    // top = centre - halfSize
                                    val yDpForHeart =
                                        with(density) { (animatedCenterYPx - heartSizePx / 2f).toDp() }

                                    // couleur du texte BPM
                                    val bpmColor = when {
                                        bpmReel == 0 -> Color.Gray
                                        bpmReel > maxBpm -> Color(0xFFB71C1C)
                                        bpmReel < minBpm -> Color(0xFF0D47A1)
                                        else -> Color.Black
                                    }

                                    // Stat et animation du coeur
                                    val pulseScale = remember { Animatable(1f) }
                                    LaunchedEffect(sample.timestamp) {       // bpmReel) {

                                        if (statEntr.isRunning) {
                                            statEntr.ajouterBpm(bpmReel, ctx)
                                        }

                                        // Annonces et animation du coeur
                                        if (bpmReel > 0) {
                                            announcer?.announceBpmEtZone(bpmReel, zones)
                                            pulseScale.stop()
                                            pulseScale.animateTo(
                                                1.07f,
                                                tween(120, easing = LinearOutSlowInEasing)
                                            )
                                            pulseScale.animateTo(
                                                1f,
                                                tween(180, easing = LinearOutSlowInEasing)
                                            )
                                        }

                                    }


                                    // Cœur idéal (aplati et semi-transparent)
                                    if (runState == RunState.Running) {

                                        val idealBpmFloat = computeIdealBpmForEtape(
                                            currentEtapeRunIndex,
                                            elapsedEtape,
                                            flattenedEtapeRuns,     // seance.etapes,
                                            zones
                                        )
                                        // Log.w("computeIdealBpmForEtape", "($currentEtapeIndex ; $elapsedEtape) => $idealBpm")

                                        // Positionnement selon le bpm idéal (même échelle que le cœur réel)
                                        val idealCenterYPx = bpmToCenterYPx(idealBpmFloat)
                                        val yDpForIdealHeart =
                                            with(density) { (idealCenterYPx - heartSizePx / 2f).toDp() }

                                        // Transparence selon la proximité du bpm réel
                                        val bpmEcart = kotlin.math.abs(bpmReel - idealBpmFloat)
                                        val alpha = when {
                                            bpmEcart >= 5f -> 1f
                                            else -> bpmEcart * 0.2f
                                        }

                                        kotlin.math.abs(bpmReel - idealBpmFloat)

                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopCenter)
                                                .offset(y = yDpForIdealHeart)
                                                .graphicsLayer(
                                                    scaleX = 1.8f,
                                                    scaleY = 0.5f, // aplati verticalement
                                                    alpha = alpha
                                                )
                                                .size(heartSizeDp)
                                                .zIndex(5f),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Image(
                                                painter = painterResource(id = R.drawable.heart_contour),
                                                contentDescription = "Heart ideal",
                                                // colorFilter = ColorFilter.tint(Color.Gray.copy(alpha = 0.5f)),
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }


                                    // --- Cœur + BPM : aligné horizontalement puis offset verticalement ---
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopCenter) // centre horizontalement la Box
                                            .offset(y = yDpForHeart)    // décale verticalement le top pour que le centre corresponde
                                            .graphicsLayer(
                                                scaleX = pulseScale.value,
                                                scaleY = pulseScale.value
                                            )
                                            .size(heartSizeDp)
                                            .zIndex(10f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.heart_contour),
                                            contentDescription = "Heart",
                                            modifier = Modifier.fillMaxSize().scale(1.4f)
                                        )
                                        Text(
                                            text = bpmReel.toString(),
                                            color = bpmColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 22.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }

                                    // le reste (labels, separators...) continue comme avant, en utilisant boxHeightPx / weights etc.
                                }

                                // Fc min de la zone (bas)
                                Text(text = "${minBpm} bpm", style = textStyle)
                            }
                        }


                        // --- Colonne DROITE ---
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Durée totale de la séance (haut)
                            Text(text = formatMsToHms(totalSeance), style = textStyle)

                            // Empilement des rectangles proportionnels (première étape en bas)
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                val density = LocalDensity.current
                                val boxHeightPx = constraints.maxHeight.toFloat()

                                // progression totale
                                val filledTotalPx = (progressTotal * boxHeightPx).coerceIn(0f, boxHeightPx)
                                val topOfFilledTotalPx = (boxHeightPx - filledTotalPx).coerceIn(0f, boxHeightPx)

                                val textBoxHeightDp = 20.dp
                                val textYOffsetTotal = with(density) {
                                    (topOfFilledTotalPx - textBoxHeightDp.toPx() / 2f).toDp().coerceAtLeast(0.dp)
                                }

                                // On reconstruit la colonne bottom-up comme avant pour les segments
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Bottom
                                ) {
                                    val totalDurationF = totalSeance.toFloat()
                                    val etapeRuns = flattenedEtapeRuns        // seance.etapes
                                    etapeRuns.reversed().forEach { etapeRun ->
                                        val frac = (etapeRun.etape.durationMillis.toFloat() / totalDurationF).coerceIn(0f, 1f)
                                        val stepZoneSpec = CANONICAL_ZONES.get(etapeRun.etape.zoneId)
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(frac)
                                                .background(stepZoneSpec.color)
                                        )
                                    }
                                }

                                // overlay progression totale (no rounding), rempli du bas vers le haut
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(progressTotal)
                                        .align(Alignment.BottomCenter)
                                        .background(Color.Black.copy(alpha = 0.18f))
                                )

                                // Texte : temps restant total de la séance, placé sur la limite haute du remplissage total
                                val remainingTotal = (totalSeance - elapsedTotal).coerceAtLeast(0L)
                                Text(
                                    text = formatMsToHms(remainingTotal, true),
                                    style = textStyle,
                                    // style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .offset(y = textYOffsetTotal),
                                    textAlign = TextAlign.Center
                                )
                            }


                            // Durée depuis le début (bas)
                            Text(text = formatMsToHms(elapsedTotal), style = textStyle)
                        }
                    } // end Row

                }

            } // end Column
        } // end seance?.let

        if (showStatsDialog) {
            if (avecCardio) {
                AlertDialog(
                    onDismissRequest = { showStatsDialog = false },
                    title = { Text(text = "Statistiques de la séance") },
                    text = {
                        Column {

                            // Text("Durée : ${}", fontWeight = FontWeight.Bold)
                            // StatLine("Durée :", statEntr.dureeAff)
                            val dureeForce = (totalSeance != statEntr.dureeMs && abs(totalSeance - statEntr.dureeMs) < 1_500L)
                            val dureeMs2Aff = if (dureeForce) { totalSeance } else { statEntr.dureeMs }
                            StatLine("Durée :", formatMsToHms(dureeMs2Aff))
                            if (DEBUG_MODE) {     //  && dureeForce) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("-- DEBUG --", fontWeight = FontWeight.Bold)
                                StatLine("Durée affichée :", dureeMs2Aff.toString())
                                StatLine("Durée totale :", totalSeance.toString())
                                StatLine("Durée elapsed :", elapsedTotal.toString())
                                StatLine("Durée stat :", statEntr.dureeMs.toString())
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(R.string.stat_fc_titre), fontWeight = FontWeight.Bold)

                            StatLine("Max :", "${statEntr.bpmMax} bpm")
                            StatLine("Moyen :", "${statEntr.bpmMoyen} bpm")
                            StatLine("Min :", "${statEntr.bpmMin} bpm")
                            if (DEBUG_MODE) {
                                StatLine("Nombre de valeurs :", statEntr.valeurNb.toString())
                            }

                            // --- Énergie (si calculée, donc poids > 0) ---
                            if (statEntr.kcalTotal > 0.0) {

                                // Energie
                                val energTxt = energieToDisplay(
                                    statEntr.kcalTotal,
                                    userPhysioParams.energyUnit
                                ).toString()
                                val energUniteTxt = when (userPhysioParams.energyUnit) {
                                    UserPhysioParams.EnergyUnit.KCAL ->
                                        stringResource(R.string.stat_e_kcal_u)
                                    UserPhysioParams.EnergyUnit.KJ ->
                                        stringResource(R.string.stat_e_kj_u)
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(stringResource(R.string.stat_energie_titre), fontWeight = FontWeight.Bold)
                                StatLine(
                                    label = stringResource(R.string.stat_e_kcal),
                                    value = "$energTxt $energUniteTxt"
                                )
                                StatLine(
                                    label = stringResource(R.string.stat_e_lipide),
                                    value = "${energieValeurAff(statEntr.lipidesTotalG)} g"
                                )
                                StatLine(
                                    label = stringResource(R.string.stat_e_glucide),
                                    value = "${energieValeurAff(statEntr.glucidesTotalG)} g"
                                )

                            }

                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            // option: sauvegarder dans prefs via statLibre.save(ctx)
                            // statLibre.save(ctx)
                            // Todo : reset la séance
                            showStatsDialog = false
                        }) {
                            Text("Fermer")
                        }
                    }
                )
            } else {
                AlertDialog(
                    onDismissRequest = { showStatsDialog = false },
                    title = { Text(text = "Séance terminée") },
                    text = {
                        Column {
                            Text("Bravo ! Vous avez terminé votre séance.")
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showStatsDialog = false
                        }) {
                            Text("Fermer")
                        }
                    }
                )
            }
        }

        // --- Sauvegarde de sécurité à la destruction du Composable ---
        DisposableEffect(Unit) {
            onDispose {
                try {
                    seance?.let { s ->
                        val updated = s.copy(dureeRealMillis = elapsedTotal)
                        try { repo.updateSeance(updated) } catch (_: Throwable) {}
                    }
                } catch (_: Throwable) {}
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                if (avecCardio) {
                    saveStat(ctx, statEntr)
                }
            }
        }

    } // end Scaffold content
}


fun computeIdealBpmForEtape(
    currentEtapeRunIndex: Int,
    elapsedEtapeMs: Long,
    etapeRuns: List<EtapeRun>,
    zones: List<Zone>
): Float {

    if (currentEtapeRunIndex !in etapeRuns.indices) return 0f

    // Etape et zone en cours
    val currentEtape = etapeRuns[currentEtapeRunIndex].etape
    val dureeEtape = currentEtape.durationMillis
    val currentZone = zones.getOrNull(currentEtape.zoneId) ?: return 0f

    // FC min, max et milieu de la zone
    val fcMin = currentZone.minBpm.toFloat()
    val fcMax = currentZone.maxBpm.toFloat()
    val fcCentre = (fcMin + fcMax) / 2f

    // Si étape de moins de 3 minutes alors on centre
    val dureeMaxEtapeCourte = if (DEBUG_MODE) 10_000L else 3 * 60_000L
    if (dureeEtape <= dureeMaxEtapeCourte)
        return fcCentre

    // Etape : transition début (max 5 minutes + milieu (si total > 10 minutes) + transition fin (même durée que début)
    val dureeMaxTransition= if (DEBUG_MODE) 10_000L else 5 * 60_000L
    val dureeTransition = (dureeEtape / 2F).toLong().coerceAtMost(dureeMaxTransition)
    // Partie : -1 début ; 0 milieu ; 1 fin
    val partieEtape = when {
        elapsedEtapeMs <= dureeTransition -> -1
        elapsedEtapeMs>= dureeEtape - dureeTransition -> 1
        else -> 0
    }
    val dureeMilieu = (dureeEtape - 2 * dureeTransition).coerceAtLeast(0L)

    when (partieEtape) {
        -1 -> {
            // Transition de début d'étape : de min ou max (fonction zone précédente) vers milieu
            val fcDebut = when {
                currentEtapeRunIndex == 0 -> fcMin
                else -> {
                    val prevZoneId = etapeRuns[currentEtapeRunIndex - 1].etape.zoneId
                    if (prevZoneId  < currentEtape.zoneId) fcMin else fcMax
                }
            }
            return (elapsedEtapeMs.toFloat()/dureeTransition*(fcCentre-fcDebut)+fcDebut)
        }
        1 -> {
            // Transition de fin d'étape : milieu à min ou max (fonction zone précédente)
            if (currentEtapeRunIndex == etapeRuns.lastIndex)
                return fcCentre
            val nextZoneId = etapeRuns[currentEtapeRunIndex + 1].etape.zoneId
            val fcFin = if (nextZoneId  < currentEtape.zoneId) fcMin else fcMax
            return ((dureeEtape - elapsedEtapeMs)/dureeTransition.toFloat()*(fcCentre-fcFin)+fcFin)
        }
        else -> return fcCentre
    }

}

@Composable
fun StatLine(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            fontWeight = FontWeight.Medium
        )
    }
}
