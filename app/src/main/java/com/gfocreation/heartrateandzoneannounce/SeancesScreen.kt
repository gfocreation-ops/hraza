package com.gfocreation.heartrateandzoneannounce

import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gfocreation.heartrateandzoneannounce.misc.SensorManager
import com.gfocreation.heartrateandzoneannounce.misc.formatMsToHms
import com.gfocreation.heartrateandzoneannounce.model.Seance
import com.gfocreation.heartrateandzoneannounce.model.SeanceRepository
import com.gfocreation.heartrateandzoneannounce.ui.*


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeancesScreen(
    onBack: () -> Unit,
    sensorManager: SensorManager,
    onOpenSeanceEdit: (Seance?) -> Unit,
    onOpenEntrSeance: (Seance, Boolean) -> Unit
) {

    val ctx = LocalContext.current
    val repository = remember { SeanceRepository(ctx) }


    var seances by remember { mutableStateOf<List<Seance>>(emptyList()) }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                seances = repository.getAllSeance()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }


    val sensorState by sensorManager.sensorState.collectAsState()

    Scaffold(
        topBar = {
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = onBack) {
                        Text(text = stringResource(id = R.string.but_back))
                    }

                    Text(
                        text = stringResource(id = R.string.seances_titre),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(2f),
                        textAlign = TextAlign.Center
                    )

                    Button(
                        onClick = { onOpenSeanceEdit(null) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50),
                            contentColor = Color.White
                        )
                    ) {
                        Text(stringResource(R.string.but_add))
                    }
                }
            }
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            if (!sensorState.connected) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.seances_avertissement),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = Color.Magenta,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }


            val sortedSeances = remember(seances) {
                seances.sortedBy { it.name.lowercase() }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {

                items(
                    items = sortedSeances,
                    key = { it.id }
                ) { seance ->

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 2.dp,
                        shadowElevation = 3.dp,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .border(
                                width = 1.dp,
                                color = if (seance.dureeRealMillis > 0) Color.Green
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {

                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            SeanceCard(
                                sensorState = sensorState,
                                seance = seance,
                                onEdit = { onOpenSeanceEdit(seance) },
                                onDuplicate = {
                                    val newSeance = repository.duplicateSeance(seance)
                                    seances = repository.getAllSeance()
                                    onOpenSeanceEdit(newSeance)
                                },
                                onDelete = {
                                    repository.deleteSeance(seance.id)
                                    seances = repository.getAllSeance().toMutableList()
                                    Toast.makeText(ctx, "Séance supprimée", Toast.LENGTH_SHORT).show()
                                },
                                onStart = { avecCardio ->
                                    onOpenEntrSeance(seance, avecCardio)
                                }
                            )
                        }

                    }

                }
            }
        }
    }
}

@Composable
private fun SeanceCard(
    sensorState: SensorManager.SensorState,
    seance: Seance,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onStart: (Boolean) -> Unit
) {


    val etapes = flattenBlocks(seance.blocks)
    val etapesNb = etapes.size
    val hasInvalidDuration = etapesNb == 0 || etapes.any { it.durationMillis == 0L }

    val dureeTotal = if (hasInvalidDuration) 0L else etapes.sumOf { it.durationMillis }
    val peutJouer = dureeTotal >= 3000L

    val etapesTxt =
        if (etapesNb == 0) "aucune étape"
        else "$etapesNb étape${if (etapesNb > 1) "s" else ""}"

    val dureeTxt =
        if (hasInvalidDuration) "--:--:--"
        else formatMsToHms(dureeTotal)

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {

        // ─── Ligne 1 : Titre plein largeur ───
        Text(
            text = seance.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(4.dp))

        // ─── Ligne 2–3 : gauche = infos / droite = actions ───
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // Infos (gauche)
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = dureeTxt,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = etapesTxt,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Actions (droite)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                IconButton(
                    onClick = { onStart(sensorState.connected) },
                    enabled = peutJouer,        // sensorState.connected &&
                    modifier = Modifier.alpha(if (peutJouer) 1f else 0f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Lancer")
                }

                IconButton(onClick = onDuplicate) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Dupliquer"
                    )
                }

                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Modifier")
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Supprimer",
                        tint = Color(0xFF8F4646)
                    )
                }

            }
        }
    }


//
//
//    Row(
//        modifier = Modifier.fillMaxWidth(),
//        verticalAlignment = Alignment.CenterVertically
//    ) {
//
//        Column(modifier = Modifier.weight(1f)) {
//
//
//            Text(text = "${etapesTxt}\n${dureeTxt}",
//                style = MaterialTheme.typography.bodySmall
//            )
//
//        }
//     }




}
