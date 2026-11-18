package com.gfocreation.heartrateandzoneannounce

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.res.stringResource

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState

import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row

import androidx.compose.material3.MenuAnchorType
import com.gfocreation.heartrateandzoneannounce.misc.computeZonesFromFcMax
import com.gfocreation.heartrateandzoneannounce.misc.computeZonesFromManual
import com.gfocreation.heartrateandzoneannounce.misc.computeZonesKarvonen
import com.gfocreation.heartrateandzoneannounce.misc.loadStoredFcMax
import com.gfocreation.heartrateandzoneannounce.misc.loadStoredFcMin
import com.gfocreation.heartrateandzoneannounce.misc.loadStoredManualCsv
import com.gfocreation.heartrateandzoneannounce.misc.loadZonedMode
import com.gfocreation.heartrateandzoneannounce.misc.saveZoneMode
import com.gfocreation.heartrateandzoneannounce.model.CANONICAL_ZONES

enum class ZonesModeUi { SIMPLIFIED, KARVONEN, MANUAL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZonesConfigScreen(onBack: () -> Unit) {

    val ctx = LocalContext.current

    // lire via les helpers centraux (Prefs.kt)
    var zone_type by remember { mutableStateOf(loadZonedMode(ctx)) }
    var fcMax by remember { mutableStateOf(loadStoredFcMax(ctx)) }
    var fcMin by remember { mutableStateOf(loadStoredFcMin(ctx)) }
    var manualCsv by remember { mutableStateOf(loadStoredManualCsv(ctx)) }


    // helper lambda local pour sauvegarder et fermer — permet d'appeler depuis le bouton "Enregistré" en topBar
    val saveAndClose: () -> Unit = {
        saveZoneMode(ctx, zone_type, fcMax, fcMin, manualCsv)
        onBack()
    }

    val manualValues = remember(manualCsv) {
        manualCsv.split(",").mapNotNull { it.trim().toIntOrNull() }
    }

    Scaffold(

        topBar = {
            // Custom single-line header:
            // - centered title "Choix du capteur"
            // - "Retour" button aligned to the right (blue background), ergonomique main droite
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
                    Button(onClick = onBack) {
                        Text(text = stringResource(id = R.string.but_back))
                    }
                    // Titre
                    Text(
                        text = stringResource(id = R.string.zone_cardio_titre),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(2f),
                        textAlign = TextAlign.Center
                    )
                    // Bouton Enregistrer
                    Button(
                        onClick = { saveAndClose() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text(text = stringResource(R.string.but_save))
                    }

                }
            }
    }) { padding ->
        Column(modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(padding)
            .padding(16.dp)) {

// Dropdown for the zone types (Simplified / Karvonen / Manual)
            var expanded by remember { mutableStateOf(false) }

            val selectedLabel = when (zone_type) {
//                ZonesModeUi.SIMPLIFIED -> stringResource(R.string.zones_simple)
                ZonesModeUi.KARVONEN -> stringResource(R.string.zones_karvonen)
                ZonesModeUi.MANUAL -> stringResource(R.string.zones_manual)
                else -> stringResource(R.string.zones_simple)
            }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
//                OutlinedTextField(
//                    value = selectedLabel,
//                    onValueChange = { /* read-only */ },
//                    readOnly = true,
//                    modifier = Modifier
//                        .menuAnchor()  // important to properly anchor the menu
//                        .fillMaxWidth(),
//                    label = { Text(stringResource(R.string.mode_choise)) },
//                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
//                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
//                )
                OutlinedTextField(
                    value = selectedLabel,
                    onValueChange = { /* read-only */ },
                    readOnly = true,
                    modifier = Modifier
                        // nouveau overload : choisir un MenuAnchorType adapté :
                        // PrimaryNotEditable -> champ non éditable principal, bon choix pour un OutlinedTextField readOnly
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                        .fillMaxWidth(),
                    label = { Text(stringResource(R.string.mode_choise)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.zones_simple)) }, onClick = {
                        zone_type = ZonesModeUi.SIMPLIFIED
                        expanded = false
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.zones_karvonen)) }, onClick = {
                        zone_type = ZonesModeUi.KARVONEN
                        expanded = false
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.zones_manual)) }, onClick = {
                        zone_type = ZonesModeUi.MANUAL
                        expanded = false
                    })
                }
            }

            Spacer(Modifier.height(12.dp))

            when (zone_type) {
                ZonesModeUi.SIMPLIFIED -> {
                    NumberField(label = stringResource(R.string.fc_max), value = fcMax, onChange = { fcMax = it })
                    val zones = computeZonesFromFcMax(fcMax)
                    Spacer(Modifier.height(10.dp))
                    ZonesPreview(zones, false)
                }
                ZonesModeUi.KARVONEN -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(
                            label = stringResource(R.string.fc_min),
                            value = fcMin,
                            onChange = { fcMin = it },
                            modifier = Modifier.weight(1f)
                        )
                        NumberField(
                            label = stringResource(R.string.fc_max),
                            value = fcMax,
                            onChange = { fcMax = it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    val zones = computeZonesKarvonen(fcMin, fcMax)
                    Spacer(Modifier.height(10.dp))
                    ZonesPreview(zones, false)
                }
                ZonesModeUi.MANUAL -> {
                    OutlinedTextField(
                        value = manualCsv,
                        onValueChange = { manualCsv = it },
                        label = { Text(stringResource(R.string.zones_karvonen_csv)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val manualVals = manualCsv.split(",").mapNotNull { it.trim().toIntOrNull() }
                    val zones = computeZonesFromManual(manualVals)
                    Spacer(Modifier.height(10.dp))
                    ZonesPreview(zones, false)
                }

            }

            // Spacer(Modifier.height(20.dp))

        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier.width(180.dp) // default preserves previous layout
) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = {
            val cleaned = it.filter { ch -> ch.isDigit() }
            val newVal = cleaned.toIntOrNull() ?: value
            onChange(newVal)
        },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier
    )
}


@Composable
fun ZonesPreview(zones: List<com.gfocreation.heartrateandzoneannounce.model.Zone>,
                 avecDescription: Boolean = false
) {
    Column {

//        Text("Aperçu des zones", style = MaterialTheme.typography.titleSmall)
//        Spacer(Modifier.height(8.dp))

        val globalMin = zones.minOfOrNull { it.minBpm } ?: 0
        val globalMax = zones.maxOfOrNull { it.maxBpm } ?: 200
        val totalRange = (globalMax - globalMin).coerceAtLeast(1)

        Column(modifier = Modifier.fillMaxWidth()) {
            // display highest zones at top
            zones.asReversed().forEach { z ->
                val specIndex = z.id.coerceIn(0, CANONICAL_ZONES.lastIndex)
                val spec = CANONICAL_ZONES.get(specIndex)

                val span = (z.maxBpm - z.minBpm + 1).coerceAtLeast(1)
                val pctRange = spec.pctRange ?: Pair(0, 0)
                val pctText = "${pctRange.first}% - ${pctRange.second}%"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .padding(vertical = 4.dp)
                        .background(spec.color, shape = RoundedCornerShape(6.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    // Remplace l'intérieur du Row par cette Column pour avoir 2 lignes (ligne1 + ligne2)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        // Zone et nom

                        Text(
                            text = "Zone ${spec.idKey} - ${spec.shortLabel}",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.wrapContentWidth()
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // BPM & % appended after — même taille de texte pour uniformité
                        Text(
                            text = "${z.minPercent}–${z.maxPercent} % ❤ ${z.minBpm}–${z.maxBpm} bpm",
                            color = Color.White.copy(alpha = 0.95f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            modifier = Modifier.wrapContentWidth(),
                            overflow = TextOverflow.Ellipsis
                        )

                        if (avecDescription) {

                            Spacer(modifier = Modifier.height(6.dp))

                            // description
                            Text(
                                text = spec.description,
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                // maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                        }
                    }

                }
            }
        }
    }
}
