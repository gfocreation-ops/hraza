package com.gfocreation.heartrateandzoneannounce

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.ui.zIndex
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.delay
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.gfocreation.heartrateandzoneannounce.ble.HeartRateGattServer
import com.gfocreation.heartrateandzoneannounce.misc.PermissionHelper
import com.gfocreation.heartrateandzoneannounce.misc.RAPPEL_ALERT_AFF_DUREE
import com.gfocreation.heartrateandzoneannounce.misc.RAPPEL_DUREE_DEFAUT
import com.gfocreation.heartrateandzoneannounce.misc.SensorManager
import com.gfocreation.heartrateandzoneannounce.misc.applyTrainingBrightness
import com.gfocreation.heartrateandzoneannounce.misc.bpmToPercentFcZonesAff
import com.gfocreation.heartrateandzoneannounce.misc.computeZonesFromFcMax
import com.gfocreation.heartrateandzoneannounce.misc.computeZonesFromManual
import com.gfocreation.heartrateandzoneannounce.misc.computeZonesKarvonen
import com.gfocreation.heartrateandzoneannounce.misc.energieToDisplay
import com.gfocreation.heartrateandzoneannounce.misc.energieValeurAff
import com.gfocreation.heartrateandzoneannounce.misc.formatSecToHms
import com.gfocreation.heartrateandzoneannounce.misc.getLuminositeEntrainemente
import com.gfocreation.heartrateandzoneannounce.misc.loadRappelParam
import com.gfocreation.heartrateandzoneannounce.misc.loadStoredFcMax
import com.gfocreation.heartrateandzoneannounce.misc.loadStoredFcMin
import com.gfocreation.heartrateandzoneannounce.misc.loadStoredManualCsv
import com.gfocreation.heartrateandzoneannounce.misc.loadZonedMode
import com.gfocreation.heartrateandzoneannounce.misc.saveStat
import com.gfocreation.heartrateandzoneannounce.model.Announcer
import com.gfocreation.heartrateandzoneannounce.model.CANONICAL_ZONES
import com.gfocreation.heartrateandzoneannounce.model.PeriodicReminder
import com.gfocreation.heartrateandzoneannounce.model.UserPhysioParams
import com.gfocreation.heartrateandzoneannounce.misc.resetScreenBrightness
import com.gfocreation.heartrateandzoneannounce.model.HeartRateSample
import com.gfocreation.heartrateandzoneannounce.service.TrainingForegroundService
import kotlin.math.roundToInt


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntrainementLibreScreen(
    onBack: () -> Unit,
    sensorManager: SensorManager,
    heartRateGattServer: HeartRateGattServer,
    announcer: Announcer?
) {
    val ctx = LocalContext.current
    val activity = ctx as Activity
    val app = ctx.applicationContext as Hraza

    val statLibre = app.appContainer.statEntrainement
    // 3.1.0_b  val heartRateManager = app.appContainer.heartRateManager

    val view = LocalView.current
    // Empêche la mise en veille de l’écran
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Luminosité de l'écran
    LaunchedEffect(Unit) {
        // val activity = ctx as? Activity
        val mode = getLuminositeEntrainemente(ctx)
        activity.applyTrainingBrightness(mode)
    }

    // Reminder
    val rappelTitre = stringResource(R.string.reminder_titre)
    val reminderScope = rememberCoroutineScope()
    val rappelParam = remember { loadRappelParam(ctx) }
    val reminder = remember {
        PeriodicReminder(reminderScope).apply {
            minutes = rappelParam.minutes
            label = rappelParam.label
            reset()
        }
    }
    val isRappelRunning by reminder.isRunningFlow.collectAsState()
    var showReminderPopup by remember { mutableStateOf(false) }
    var showReminderSettings by remember { mutableStateOf(false) }
    LaunchedEffect(reminder) { // , announcer) {
        reminder.onTrigger = {
            showReminderPopup = true
            reminder.start()
            announcer?.beepCourtRappel()
            val mess = reminder.label.takeIf { it.isNotBlank() } ?: rappelTitre
            announcer?.announceTexte(mess)
        }
    }

    // États capteurs
    val sensorState by sensorManager.sensorState.collectAsState()
    val avecCardio = sensorState.connected
    // val bpmReel by sensorManager.bpmFlow.collectAsState(initial = 0)
    val sample by sensorManager.bpmFlow.collectAsState(
        initial = HeartRateSample(0, 0)
    )
    val bpmReel = sample.bpm
    val isReconnecting by sensorManager.isReconnecting.collectAsState()

    // état local pour refléter immédiatement le mode renvoyé par announcer.nextMode()
    var currentNiveau by remember { mutableStateOf(announcer?.getNiveau(avecCardio) ?: Announcer.AnnouncementNiveau.DEFAULT) }

    // Chargement des zones
    val storedMode = remember { loadZonedMode(ctx) }
    val storedFcMax = loadStoredFcMax(ctx)
    val storedFcMin = if (storedMode == ZonesModeUi.KARVONEN) loadStoredFcMin(ctx) else 0
    val zones = when (storedMode) {
        ZonesModeUi.KARVONEN -> computeZonesKarvonen(storedFcMin, storedFcMax)
        ZonesModeUi.MANUAL -> computeZonesFromManual(
            loadStoredManualCsv(ctx).split(",").mapNotNull { it.trim().toIntOrNull() }
        )
        else -> computeZonesFromFcMax(storedFcMax)
    }

    // Données physique de l'utilisateur pour les calculs de l'énergie dans les stats
    // Paramètres physiologiques utilisateur
    val userPhysioParams = remember {
        UserPhysioParams.load(ctx)
    }

    // Statistiques
    LaunchedEffect(Unit) {
        statLibre.initAddsIfNeeded(
            zones = zones,
            poidsKg = userPhysioParams.poidsKg,
            sexe = userPhysioParams.sexe,
            sportType = userPhysioParams.sportType,
            niveau = userPhysioParams.niveau,
            energyUnit = userPhysioParams.energyUnit
        )
    }

    // Pour l'actualisation du bloc stat
    var statTick by remember { mutableStateOf(0L) }
    fun forceStatRefresh() {        // Pour actualisation lors des actions utilisateur
        statTick = System.currentTimeMillis()
    }
    LaunchedEffect(sample.timestamp) {      // bpmReel) {       // sensorManager.bpmFlow) {
            if (statLibre.isRunning) {
                // 3.1.0_d      statLibre.ajouterBpm(bpmReel, ctx)      // bpm, ctx)
                statTick = System.currentTimeMillis()
            }
    }


    var showStatSettings by remember { mutableStateOf(false) }
    var showStatInfo by remember { mutableStateOf(false) }

    var tmpPoids by remember(showStatSettings) {
        mutableStateOf(
            userPhysioParams.poidsKg.takeIf { it > 0 }?.toString() ?: ""
        )
    }

    var tmpSexe by remember(showStatSettings) {
        mutableStateOf(userPhysioParams.sexe)
    }

    var tmpSportType by remember(showStatSettings) {
        mutableStateOf(userPhysioParams.sportType)
    }

    var tmpNiveau by remember(showStatSettings) {
        mutableStateOf(userPhysioParams.niveau)
    }

    var tmpEnergyUnit by remember(showStatSettings) {
        mutableStateOf(userPhysioParams.energyUnit)
    }

    BackHandler {

        // arrêt volontaire
        statLibre.stop(ctx)
        saveStat(ctx, statLibre)

        ctx.stopService(
            Intent(ctx, TrainingForegroundService::class.java)
        )

        // reset luminosité
        activity.resetScreenBrightness()

        // retour écran précédent
        onBack()        // activity.finish()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    // APP PASSE EN ARRIÈRE-PLAN
                    activity.resetScreenBrightness()
                }

                Lifecycle.Event.ON_START -> {
                    if (!PermissionHelper.hasNotificationPermission(ctx)) {
                        val perms = PermissionHelper.requiredNotificationPermissions(ctx)
                        if (perms.isNotEmpty()) {
                            ActivityCompat.requestPermissions(activity, perms, 2001)
                        }
                    }

                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()              // <-- assure le décalage sous la barre système
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Retour ou annuler
                    // Button(onClick = onBack) {
                    Button(onClick = {

                        statLibre.stop(ctx)
                        saveStat(ctx, statLibre)

                        ctx.stopService(
                            Intent(ctx, TrainingForegroundService::class.java)
                        )

                        activity.resetScreenBrightness()

                        // retour écran précédent
                        onBack()        // activity.finish()

                    }) {
                        Text(text = stringResource(id = R.string.but_back))
                    }
                    // Titre
                    Text(
                        text = stringResource(id = R.string.entrainement_libre_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(2f),
                        textAlign = TextAlign.Center
                    )

                }
            }
        },
        content = { innerPadding ->
            val density = LocalDensity.current
            val zonesReversed = zones.reversed()
            val barWidth = 60.dp
            val separatorOverflow = 8.dp
            val separatorStrokeDp = 1.dp
            val middleThirdOverlayColor = Color(0xFFF5F5F5)
            val textDarkGray = Color(0xFF444444)
            val zoneDeclDroite = 40.dp
            val blocsMargeGauche = 12.dp
            val blocGaucheLarg = 140.dp

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    // .padding(16.dp)
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
//                                    if (DEBUG_MODE) {
//                                        // val ctx = LocalContext.current
//                                        Toast.makeText(ctx, "Luminosité change", Toast.LENGTH_SHORT).show()
//                                    }
                                    activity.toggleScreenBrightness()       // ctx)
                                }
                            )
                        }
                ) {  }

                // Bannière de reconnexion automatique au capteur
                if (isReconnecting) {
                    ReconnectBanner(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .zIndex(100f)
                    )
                }

                // Micro toggle button (en haut à droite)
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


                // --- Contenu principal (barre + labels)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = zoneDeclDroite),    // Décalage vers la droite de toute la barre principale (et associés)
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // FC Max (haut)
                    Text(
                        text = storedFcMax.toString(),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = textDarkGray
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        val boxHeightPx = constraints.maxHeight.toFloat()
                        val boxWidthPx = constraints.maxWidth.toFloat()
                        val fixedZoneWeight = 0.18f
                        val fixedZoneIndex =
                            zonesReversed.indexOfFirst { it.id == 0 }.takeIf { it >= 0 }
                                ?: zonesReversed.lastIndex
                        val otherZonesWeight = 1f - fixedZoneWeight

                        val rawSizes = zonesReversed.mapIndexed { index, z ->
                            if (index == fixedZoneIndex) 0f else {
                                val spec = CANONICAL_ZONES.getOrNull(z.id)
                                val span =
                                    (spec?.pctRange?.second ?: 0) - (spec?.pctRange?.first ?: 0)
                                span.coerceAtLeast(1).toFloat()
                            }
                        }
                        val sumRawSizes = rawSizes.sum().takeIf { it > 0f } ?: 1f
                        val weights = rawSizes.mapIndexed { index, size ->
                            if (index == fixedZoneIndex) fixedZoneWeight
                            else (size / sumRawSizes) * otherZonesWeight
                        }

                        val spansPxBox = weights.map { it * boxHeightPx }
                        val cumulativeTopsBox = MutableList(spansPxBox.size) { 0f }
                        run {
                            var acc = 0f
                            for (i in spansPxBox.indices) {
                                cumulativeTopsBox[i] = acc
                                acc += spansPxBox[i]
                            }
                        }

                        val barPx = with(density) { barWidth.toPx() }
                        val overflowPx = with(density) { separatorOverflow.toPx() }
                        val strokePx = with(density) { separatorStrokeDp.toPx() }

                        // Colonne des zones colorées (la barre)
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(barWidth),
                            verticalArrangement = Arrangement.Top,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            zonesReversed.forEachIndexed { idx, z ->
                                val weight = weights.getOrNull(idx) ?: 0f
                                val color = CANONICAL_ZONES.getOrNull(z.id)?.color
                                    ?: Color(0xFF0B7A3A)
                                Box(
                                    modifier = Modifier
                                        .weight(weight)
                                        .fillMaxWidth()
                                        .background(color)
                                )
                            }
                        }

                        // Canvas : separators + middle-third zone0
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val centerX = size.width / 2f
                            val startX = centerX + barPx / 2f
                            val endX = startX + overflowPx
                            val spansPxCanvas = weights.map { it * size.height }

                            // middle third of zone 0
                            val idxZone0 = zonesReversed.indexOfFirst { it.id == 0 }
                            if (idxZone0 >= 0) {
                                var topAcc = 0f
                                for (i in 0 until idxZone0) topAcc += spansPxCanvas.getOrNull(i)
                                    ?: 0f
                                val zoneHeightPx = spansPxCanvas.getOrNull(idxZone0) ?: 0f
                                if (zoneHeightPx > 2f) {
                                    val third = zoneHeightPx / 3f
                                    drawRect(
                                        color = middleThirdOverlayColor,
                                        topLeft = Offset(centerX - barPx / 2f, topAcc + third),
                                        size = androidx.compose.ui.geometry.Size(
                                            width = barPx,
                                            height = third
                                        )
                                    )
                                }
                            }

                            var cum = 0f
                            spansPxCanvas.dropLast(1).forEach { span ->
                                cum += span
                                drawLine(
                                    color = Color.Black,
                                    start = Offset(x = startX, y = cum),
                                    end = Offset(x = endX, y = cum),
                                    strokeWidth = strokePx
                                )
                            }
                        }

                        // --- overlay unifié (coeur + labels + stats)
                        Box(modifier = Modifier.fillMaxSize()) {
                            val spansPxOverlay = spansPxBox
                            val cumulativeTopsOverlay = cumulativeTopsBox

                            fun bpmToYPx(bpm: Int): Float {
                                val globalMax = zonesReversed.firstOrNull()?.maxBpm ?: bpm
                                val globalMin = zonesReversed.lastOrNull()?.minBpm ?: bpm
                                if (bpm >= globalMax) return 0f
                                if (bpm <= globalMin) return boxHeightPx
                                val idx =
                                    zonesReversed.indexOfFirst { z -> bpm in z.minBpm..z.maxBpm }
                                if (idx >= 0) {
                                    val z = zonesReversed[idx]
                                    val zoneTop = cumulativeTopsOverlay[idx]
                                    val zoneHeight = spansPxOverlay[idx]
                                    val spanBpm = (z.maxBpm - z.minBpm).coerceAtLeast(1)
                                    val frac = 1f - ((bpm - z.minBpm).toFloat() / spanBpm.toFloat())
                                    return zoneTop + frac * zoneHeight
                                }
                                val span = (globalMax - globalMin).coerceAtLeast(1)
                                val frac = (globalMax - bpm).toFloat() / span.toFloat()
                                return frac * boxHeightPx
                            }

                            val heartSizeDp = barWidth + 28.dp
                            val heartSizePx = with(density) { heartSizeDp.toPx() }
                            val targetYPx = bpmToYPx(bpmReel).coerceIn(0f, boxHeightPx)
                            val animatedYPx by animateFloatAsState(
                                targetValue = targetYPx,
                                animationSpec = tween(600, easing = LinearOutSlowInEasing),
                                label = "HeartAnim"
                            )
                            val yDp = with(density) { (animatedYPx - heartSizePx / 2).toDp() }
                            val xDp = with(density) { (boxWidthPx / 2 - heartSizePx / 2).toDp() }

                            val bpmColor = when {
                                bpmReel == 0 -> Color.Gray
                                bpmReel > storedFcMax -> Color(0xFFB71C1C)
                                bpmReel < storedFcMin -> Color(0xFF0D47A1)
                                else -> Color.Black
                            }

                            val pulseScale = remember { Animatable(1f) }
                            LaunchedEffect(sample.timestamp) {      // bpmReel) {
                                pulseScale.animateTo(
                                    1.07f,
                                    tween(120, easing = LinearOutSlowInEasing)
                                )
                                pulseScale.animateTo(1f, tween(180, easing = LinearOutSlowInEasing))
                                announcer?.announceBpmEtZone(bpmReel, zones)
                            }

                            Box(
                                modifier = Modifier
                                    .offset(x = xDp, y = yDp)
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
                                    modifier = Modifier.fillMaxSize()
                                )
                                Text(
                                    text = bpmReel.toString(),
                                    color = bpmColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp,
                                    textAlign = TextAlign.Center
                                )
                            }

                            // --- BPM markers and percent labels
                            var cumY = 0f
                            val textBoxHeightDp = 24.dp
                            val textBoxHeightPx = with(density) { textBoxHeightDp.toPx() }
                            val barRightXPx = boxWidthPx / 2 + barPx / 2
                            val labelXOffset = 12.dp

                            spansPxOverlay.dropLast(1).forEachIndexed { idx, span ->
                                cumY += span
                                val topDp = with(density) { (cumY - textBoxHeightPx / 2).toDp() }
                                val upperZone = zonesReversed.getOrNull(idx + 1)
                                val bpmText = upperZone?.maxBpm?.toString() ?: ""
                                Box(
                                    modifier = Modifier
                                        .offset(y = topDp)
                                        .height(textBoxHeightDp)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = bpmText,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp
                                        ),
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                val pctText = CANONICAL_ZONES
                                    .getOrNull(upperZone?.id ?: -1)
                                    ?.pctRange?.second?.toString()?.plus("%") ?: ""

                                if (pctText.isNotEmpty()) {
                                    val xPx =
                                        (barRightXPx + with(density) { labelXOffset.toPx() }).toInt()
                                    val yPx = (cumY - textBoxHeightPx / 2).toInt()
                                    Box(
                                        modifier = Modifier
                                            .offset { IntOffset(xPx, yPx) }
                                            .height(textBoxHeightDp)
                                            .width(with(density) { 40.dp }),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = pctText,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
                                            color = textDarkGray,
                                            textAlign = TextAlign.Start
                                        )
                                    }
                                }
                            }

                            // short labels
                            var cumYLabel = 0f
                            zonesReversed.forEachIndexed { idx, z ->
                                val weight = weights[idx]
                                val spec = CANONICAL_ZONES.getOrNull(z.id)
                                val label = spec?.shortLabel ?: ""
                                val topInt = (cumYLabel * boxHeightPx).toInt()
                                val heightDp = with(density) { (weight * boxHeightPx).toDp() }
                                cumYLabel += weight
                                val xPx =
                                    (barRightXPx + with(density) { labelXOffset.toPx() }).toInt()
                                Box(
                                    modifier = Modifier
                                        .offset { IntOffset(xPx, topInt) }
                                        .height(heightDp)
                                        .widthIn(max = with(density) { (boxWidthPx - barRightXPx - 16.dp.toPx()).toDp() })
                                        .padding(end = 8.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        ),
                                        color = textDarkGray,
                                        textAlign = TextAlign.Start,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                        } // fin overlay Box
                    } // fin BoxWithConstraints

                    Spacer(modifier = Modifier.height(4.dp))

                    // FC Min (bas)
                    Text(
                        text = storedFcMin.toString(),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = textDarkGray,
                        modifier = Modifier.wrapContentHeight()
                    )

                } // fin Column principale


                // Notification bpm pas réçu par le device connecté
                val notifySuccess by heartRateGattServer.lastNotifySuccess.collectAsState()
                if (notifySuccess == false) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = blocsMargeGauche)
                            .border(1.dp, Color(0xFFCCCCCC))
                            .width(blocGaucheLarg)
                    ) {
                        Text(
                            text = "BLE SEND ERROR",
                            color = Color.Red
                        )
                    }
                }


                // Bloc rappel
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = blocsMargeGauche, y = 28.dp)     // 12.dp)
                        .border(1.dp, Color(0xFFCCCCCC))
                        .width(blocGaucheLarg)      // 116.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally // centrer horizontalement l'ensemble
                    ) {

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isRappelRunning) Color(0xFF369be3) else Color(
                                        0xFF606060
                                    )
                                )
                                .padding(horizontal = 6.dp, vertical = 6.dp)
                        ) {

                            Text(
                                text = stringResource(R.string.reminder_titre),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.align(
                                    if (isRappelRunning) Alignment.Center else Alignment.CenterStart
                                )
                            )

                            if (!isRappelRunning) {
                                IconButton(
                                    onClick = { showReminderSettings = true },
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .size(32.dp)
                                        .background(
                                            Color.White.copy(alpha = 0.67f),
                                            RoundedCornerShape(6.dp)
                                        )
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.add_alert_24dp),
                                        contentDescription = "Réglages rappel",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }


                        Column(
                            modifier = Modifier
                                .padding(8.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally // centrer horizontalement l'ensemble
                        ) {

                            Spacer(modifier = Modifier.height(8.dp))

                            // Durée restante (centrée)
                            val remainingSeconds by reminder.remainingSecondsFlow.collectAsState()
//                            val min = remainingSeconds / 60
//                            val sec = remainingSeconds % 60
//                            val txt = String.format("%02d:%02d", min, sec)
                            val txt = formatSecToHms(remainingSeconds)

                            Text(
                                text = txt,         // if (isRunning) txt else "${reminder.minutes}:00",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Bouton On/Off
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {

                                // Bouton reset
                                Button(
                                    onClick = { reminder.reset() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text(stringResource(R.string.but_reset),
                                        fontSize = 12.sp)
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Bouton on/off
                                Button(
                                    onClick = {
                                        if (isRappelRunning) reminder.pause()
                                        else reminder.resume()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text(if (isRappelRunning) stringResource(R.string.but_off) else stringResource(R.string.but_on), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }


                // Bloc Stat (placé ici comme enfant direct du Box parent, bottom-left)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = blocsMargeGauche, y = -28.dp)
                        .border(1.dp, Color(0xFFCCCCCC))
                        .width(blocGaucheLarg)      // 116.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally // centrer horizontalement l'ensemble
                    ) {

                        val tick = statTick
                        val isStatRunning = statLibre.isRunning

                        // Entête
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isStatRunning) Color(0xFF4CAF50) else Color(
                                        0xFF606060
                                    )
                                )
                                .padding(horizontal = 6.dp, vertical = 6.dp)
                            , contentAlignment = Alignment.Center
                        ) {

                            if (isStatRunning) {
                                Text(
                                    text = stringResource(R.string.stat_titre),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            } else {
                                Text(
                                    text = "Stats",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.align(Alignment.CenterStart).offset(x = -4.dp)
                                )
                            }



                            if (!isStatRunning) {

                                IconButton( onClick = { showStatInfo = true }
                                    , modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .size(32.dp)
                                        .offset(x = -50.dp)
                                        .background( Color.White.copy(alpha = 0.67f)
                                            , RoundedCornerShape(6.dp) )
                                ) {
                                    Icon( imageVector = Icons.Default.Info
                                        , contentDescription = "Informations sur les statistiques"
                                        , modifier = Modifier.size(20.dp)
                                    ) }

                                IconButton( onClick = { showStatSettings = true }
                                    , modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .size(32.dp)
                                        .background( Color.White.copy(alpha = 0.67f)
                                        , RoundedCornerShape(6.dp) )
                                ) {
                                    Icon( imageVector = Icons.Default.Settings
                                        , contentDescription = "Réglage données utilisateur"
                                        , modifier = Modifier.size(20.dp)
                                    ) }
                            }

                        }


                        Column(
                            modifier = Modifier
                                .padding(8.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally // centrer horizontalement l'ensemble
                        ) {

                            Spacer(modifier = Modifier.height(4.dp))

                            // Durée (centrée)
                            Text(
                                text = statLibre.dureeAff,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(4.dp))

//                            // titre FC
//                            StatSousTitre(stringResource(R.string.stat_fc_titre))

                            // Lignes Max / Avg / Min (2 colonnes: label gauche, valeur droite)
                            fun valueOrDash(value: Int, hasValue: Boolean): String = if (hasValue) value.toString() else "-"

                            val hasAny = statLibre.valeurNb > 0

                            // FC max
                            StatLine(
                                label = stringResource(R.string.stat_max),
                                hasAny,
                                statLibre.bpmMax,
                                subText = if (hasAny) { bpmToPercentFcZonesAff(statLibre.bpmMax, zones) } else null
                            )

                            // FC moyenne
                            StatLine(
                                label = stringResource(R.string.stat_moy),
                                hasAny,
                                statLibre.bpmMoyen,
                                subText = if (hasAny) { bpmToPercentFcZonesAff(statLibre.bpmMoyen, zones) } else null
                            )

                            // FC min
                            StatLine(
                                label = stringResource(R.string.stat_min),
                                hasAny,
                                statLibre.bpmMin,
                                subText = if (hasAny) { bpmToPercentFcZonesAff(statLibre.bpmMin, zones) } else null
                            )

                            if (DEBUG_STATS_AVEC_NB) {
                                // Affichage du nombre de valeurs de bpm reçu
                                StatLine(
                                    label = "Nombres :",
                                    hasAny,
                                    statLibre.valeurNb,
                                    subText = null
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // CSB
                            StrainBar(
                                percent = statLibre.csbPercent,
                                tteSec = statLibre.csbTteSec
                            )


                            Spacer(modifier = Modifier.height(4.dp))

//                            // titre énergie
//                            StatSousTitre(stringResource(R.string.stat_energie_titre))

                            // Energie kcal
                            StatLine(
                                label = stringResource(R.string.stat_e_kcal),
                                hasAny,
                                energieToDisplay(statLibre.kcalTotal, userPhysioParams.energyUnit),
                                subText = when (userPhysioParams.energyUnit) {
                                    UserPhysioParams.EnergyUnit.KCAL -> stringResource(R.string.stat_e_kcal_u)
                                    UserPhysioParams.EnergyUnit.KJ -> stringResource(R.string.stat_e_kj_u)
                                }
                            )

                            // Energie lipide
                            StatLine(
                                label = stringResource(R.string.stat_e_lipide),
                                hasAny,
                                statLibre.lipidesTotalG,
                                subText = stringResource(R.string.stat_e_energie_gr)
                            )

                            // Energie glucide
                            StatLine(
                                label = stringResource(R.string.stat_e_glucide),
                                hasAny,
                                statLibre.glucidesTotalG,
                                subText = stringResource(R.string.stat_e_energie_gr)
                            )

                            // Energie apport
                            StatLine(
                                label = stringResource(R.string.stat_e_apport),
                                hasAny,
                                statLibre.glucidesDepuisResetG,
                                subText = stringResource(R.string.stat_e_energie_gr)
                            )

                            // Bouton reset apport
                            Button(
                                onClick = {
                                    statLibre.resetGlucides(ctx)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.reset_apport),
                                    fontSize = 12.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Bouton reset ("---")
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Button(
                                    onClick = {
                                        ctx.stopService(
                                            Intent(ctx, TrainingForegroundService::class.java)
                                        )
                                        statLibre.reset()
                                        saveStat(ctx, statLibre)
                                        forceStatRefresh()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text(stringResource(R.string.but_reset),
                                        fontSize = 12.sp)
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Bouton on/off
                                Button(
                                    onClick = {
                                        if (!isStatRunning) {

                                            // Pas de stat si poids nul
                                            if (userPhysioParams.poidsKg <= 0f) {
                                                showStatSettings = true
                                                return@Button
                                            }

                                            statLibre.start(ctx)
                                            ContextCompat.startForegroundService(
                                                ctx,
                                                Intent(ctx, TrainingForegroundService::class.java)
                                            )

                                        } else {
                                            ctx.stopService(
                                                Intent(ctx, TrainingForegroundService::class.java)
                                            )
                                            statLibre.stop(ctx)
                                        }
                                        forceStatRefresh()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text(if (isStatRunning) stringResource(R.string.but_off) else stringResource(R.string.but_on), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }


                if (showReminderPopup) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f))
                            .zIndex(50f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .background(Color.White, shape = MaterialTheme.shapes.medium)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Rappel", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(reminder.label, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { showReminderPopup = false }) { Text("Fermer") }
                        }
                    }

                    LaunchedEffect(showReminderPopup) {
                        if (showReminderPopup) {
                            delay(RAPPEL_ALERT_AFF_DUREE)
                            showReminderPopup = false
                        }
                    }

                }


            } // <-- fin Box principale
        } // <-- fin content
    ) // <-- fin Scaffold


    // Configuration reminder

    var tmpMinutes by remember(showReminderSettings) {
        mutableStateOf(reminder.minutes.toString())
    }
    var tmpLabel by remember(showReminderSettings) {
        mutableStateOf(reminder.label)
    }

    if (showReminderSettings) {
        AlertDialog(
            onDismissRequest = { showReminderSettings = false },
            confirmButton = {
                TextButton(onClick = {
                    reminder.applyConfig(
                        ctx,
                        newMinutes = tmpMinutes.toIntOrNull() ?: RAPPEL_DUREE_DEFAUT,
                        newLabel = tmpLabel
                        // newEnabled = true
                    )
                    showReminderSettings = false
                }) {
                    Text("OK")
                }

            },
            dismissButton = {
                TextButton(onClick = { showReminderSettings = false }) {
                    Text("Annuler")
                }
            },
            title = { Text("Rappel") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    OutlinedTextField(
                        value = tmpMinutes,
                        onValueChange = { tmpMinutes = it.filter { c -> c.isDigit() } },
                        label = { Text("Durée (minutes)") },
                        singleLine = true,
                        modifier = Modifier.width(140.dp)
                    )

                    OutlinedTextField(
                        value = tmpLabel,
                        onValueChange = { tmpLabel = it },
                        label = { Text("Libellé") },
                        singleLine = true
                    )
                }
            }
        )
    }


    // Informations sur les statistiques
    if (showStatInfo) {
        AlertDialog(
            onDismissRequest = { showStatInfo = false },

            confirmButton = {
                TextButton(onClick = {
                    showStatInfo = false
                }) {
                    Text(stringResource(R.string.but_back))
                }
            },

            title = { Text( stringResource(R.string.stat_titre) ) },

            text = {
                Column() {      // verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    Text(
                        text = stringResource(R.string.stat_e_params_information),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        )
    }


    // Paramètres utilisateur pour le calcul de l'énergie dans les stats
    if (showStatSettings) {
        AlertDialog(
            onDismissRequest = { showStatSettings = false },

            confirmButton = {
                TextButton(onClick = {
                    val poids = tmpPoids.toFloatOrNull() ?: 0f

                    userPhysioParams.poidsKg = poids
                    userPhysioParams.sexe = tmpSexe
                    userPhysioParams.sportType = tmpSportType
                    userPhysioParams.niveau = tmpNiveau
                    userPhysioParams.energyUnit = tmpEnergyUnit
                    userPhysioParams.save(ctx)

                    // propager aux stats
                    statLibre.metabolicAccumulator?.updatePhysio(userPhysioParams)

                    showStatSettings = false
                }) {
                    Text(stringResource(R.string.but_save))
                }
            },

            dismissButton = {
                TextButton(onClick = { showStatSettings = false }) {
                    Text(stringResource(R.string.but_cancel))
                }
            },

            title = { Text( stringResource(R.string.stat_e_params_titre)) },

            text = {
                Column() {      // verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    Text(
                        text = stringResource(R.string.stat_e_params_explication),
                        style = MaterialTheme.typography.bodySmall
                    )

                    HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(top = 6.dp))

                    // Choix du poids
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 0.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Poids", modifier = Modifier.width(60.dp))

                        SimpleOutlinedInput(
                            value = tmpPoids,
                            onValueChange = {
                                tmpPoids = it.replace(',', '.')
                                    .filter { c -> c.isDigit() || c == '.' }
                            }
                            , width = 60.dp
                        )

                        Text("kg")
                    }

                    HorizontalDivider()

                    // Choix du sexe
                   Row(modifier = Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
                        Text("Sexe")

                        RadioButton(
                            selected = tmpSexe == UserPhysioParams.Sexe.HOMME,
                            onClick = { tmpSexe = UserPhysioParams.Sexe.HOMME }
                        )
                        Text("Homme")

                        Spacer(Modifier.width(16.dp))

                        RadioButton(
                            selected = tmpSexe == UserPhysioParams.Sexe.FEMME,
                            onClick = { tmpSexe = UserPhysioParams.Sexe.FEMME }
                        )
                        Text("Femme")
                    }

                    HorizontalDivider()

                    // Choix du sport
                    Row(modifier = Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.stat_type_sport))

                        Column() {

                            Row(modifier = Modifier.fillMaxWidth().height(36.dp),verticalAlignment = Alignment.CenterVertically) {

                                RadioButton(
                                    selected = tmpSportType == UserPhysioParams.SportType.DEFAULT,
                                    onClick = { tmpSportType = UserPhysioParams.SportType.DEFAULT }
                                )
                                Text(stringResource(R.string.stat_type_defaut))

                                RadioButton(
                                    selected = tmpSportType == UserPhysioParams.SportType.VELO,
                                    onClick = { tmpSportType = UserPhysioParams.SportType.VELO }
                                )
                                Text(stringResource(R.string.stat_type_velo))

                            }

                            Row(modifier = Modifier.fillMaxWidth().height(36.dp),verticalAlignment = Alignment.CenterVertically) {

                                RadioButton(
                                    selected = tmpSportType == UserPhysioParams.SportType.COURSE,
                                    onClick = { tmpSportType = UserPhysioParams.SportType.COURSE }
                                )
                                Text(stringResource(R.string.stat_type_course))

                                RadioButton(
                                    selected = tmpSportType == UserPhysioParams.SportType.RAMEUR,
                                    onClick = { tmpSportType = UserPhysioParams.SportType.RAMEUR }
                                )
                                Text(stringResource(R.string.stat_type_rameur))

                            }
                        }

                    }

                    HorizontalDivider()

                    // Choix du niveau
                    Row(modifier = Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.stat_niveau))

                        RadioButton(
                            selected = tmpNiveau == UserPhysioParams.Niveau.DEBUTANT,
                            onClick = { tmpNiveau = UserPhysioParams.Niveau.DEBUTANT }
                        )
                        Text(stringResource(R.string.stat_niveau_deb))

                        Spacer(Modifier.width(16.dp))

                        RadioButton(
                            selected = tmpNiveau == UserPhysioParams.Niveau.ENTRAINE,
                            onClick = { tmpNiveau = UserPhysioParams.Niveau.ENTRAINE }
                        )
                        Text(stringResource(R.string.stat_niveau_entr))
                    }

                    HorizontalDivider()

                    // Choix de l'unité de l'énergie
                    Row(modifier = Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.stat_energie))

                        RadioButton(
                            selected = tmpEnergyUnit == UserPhysioParams.EnergyUnit.KCAL,
                            onClick = { tmpEnergyUnit = UserPhysioParams.EnergyUnit.KCAL }
                        )
                        Text(stringResource(R.string.stat_e_kcal_u))

                        Spacer(Modifier.width(16.dp))

                        RadioButton(
                            selected = tmpEnergyUnit == UserPhysioParams.EnergyUnit.KJ,
                            onClick = { tmpEnergyUnit = UserPhysioParams.EnergyUnit.KJ }
                        )
                        Text(stringResource(R.string.stat_e_kj_u))
                    }

                }
            }
        )
    }


} // <-- fin composable

@Composable
fun ReconnectBanner(
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFFFFC107), // amber
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.BluetoothDisabled,
                contentDescription = null,
                tint = Color.Black
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "Connexion au capteur perdue — tentative de reconnexion…",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = Color.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


@Composable
fun StatSousTitre(label: String)
{
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,     // .copy(fontWeight = FontWeight.Bold),
        maxLines = 1,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
}


@Composable
fun StatLine(
    label: String,
    hasAny: Boolean,
    value: Double,
    subText: String? = null,
    valueStyle: TextStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
    subStyle: TextStyle = MaterialTheme.typography.bodySmall
) {
    val valueText = energieValeurAff(value)     //  if (value < 5) String.format("%.1f", value) else value.roundToInt().toString()
    StatLine(label, hasAny, valueText, subText, valueStyle, subStyle)
}

@Composable
fun StatLine(
    label: String,
    hasAny: Boolean,
    value: Int,
    subText: String? = null,
    valueStyle: TextStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
    subStyle: TextStyle = MaterialTheme.typography.bodySmall
) {
    StatLine(label, hasAny, value.toString(), subText, valueStyle, subStyle)
}

@Composable
fun StatLine(
    label: String,
    hasAny: Boolean,
    valueText: String,
    subText: String? = null,
    valueStyle: TextStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
    subStyle: TextStyle = MaterialTheme.typography.bodySmall
) {
    val valueTxt = if (hasAny) valueText else "-"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            textAlign = TextAlign.End,
            style = valueStyle,
            text = buildAnnotatedString {
                append(valueTxt)
                if (subText != null) {
                    withStyle(subStyle.toSpanStyle()) {
                        append(" $subText")
                    }
                }
            }
        )
    }
}

@Composable
fun SimpleOutlinedInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 100.dp
) {
    Surface(
        modifier = modifier
            .width(width)
            .height(36.dp), // 🔹 HAUTEUR FIXE, MAÎTRISÉE
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline
        ),
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(), // OK car la hauteur est fixée
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 14.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth()
            )
        }
    }
}


@Composable
fun StrainBar(percent: Int, tteSec: Int?) {

    val color = when {
        percent > 60 -> Color(0xFF4CAF50)
        percent > 30 -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }

    Column {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .background(Color.LightGray)
        ) {

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(percent / 100f)
                    .background(color)
            )

            Text(
                text = "$percent %",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                // fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)        // centre horizontal + vertical
                    .fillMaxWidth()
            )
        }

        val tpsRestant = tteSec?.let { "Restant : ${formatSecToHms(it)}" } ?: "-"
        Text(tpsRestant
            , style = MaterialTheme.typography.bodyMedium
            , textAlign = TextAlign.Center
            , modifier = Modifier.fillMaxWidth()
        )

    }

}
