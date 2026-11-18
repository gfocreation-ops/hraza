package com.gfocreation.heartrateandzoneannounce

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gfocreation.heartrateandzoneannounce.model.*
import com.gfocreation.heartrateandzoneannounce.ui.* // EtapeEditor, GroupEditor, newId
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeanceEditV2Screen(
    onBack: () -> Unit,
    seanceId: Int? = null
) {
    val ctx = LocalContext.current
    val repo = remember { SeanceRepository(ctx) }

    var seanceName by remember { mutableStateOf(TextFieldValue("")) }

    // Liste observable des blocs racine (Étapes + Groupes)
    val blocks = remember { mutableStateListOf<BlocSeance>() }

    // Charger une séance existante
    LaunchedEffect(seanceId) {
        if (seanceId != null) {
            val s = repo.getAllSeance().find { it.id == seanceId }
            if (s != null) {
                seanceName = TextFieldValue(s.name)
                blocks.clear()
                blocks.addAll(s.blocks)
            } else {
                Toast.makeText(ctx, "Séance introuvable", Toast.LENGTH_SHORT).show()
                onBack()
            }
        }
    }

    // Fonction pour déplacer un bloc racine
    fun moveBlock(from: Int, to: Int) {
        if (to in blocks.indices) {
            val moved = blocks.removeAt(from)
            blocks.add(to, moved)
        }
    }

    Scaffold(
        topBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = onBack) {
                        Text("Annuler")
                    }

                    Text(
                        text = if (seanceId == null) "Nouvelle séance" else "Éditer séance",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(2f),
                        textAlign = TextAlign.Center
                    )

                    Button(
                        onClick = {
                            when {
                                seanceName.text.isBlank() ->
                                    Toast.makeText(ctx, "Nom obligatoire", Toast.LENGTH_SHORT).show()
                                blocks.isEmpty() ->
                                    Toast.makeText(ctx, "Ajoutez au moins un bloc", Toast.LENGTH_SHORT).show()
                                else -> {
                                    val id = seanceId
                                        ?: ((repo.getAllSeance().maxOfOrNull { it.id } ?: 0) + 1)

                                    val s = Seance(
                                        id = id,
                                        name = seanceName.text,
                                        dureeRealMillis = 0L,
                                        blocks = blocks.toList()
                                    )
                                    if (seanceId == null) repo.addSeance(s)
                                    else repo.updateSeance(s)

                                    Toast.makeText(ctx, "Séance enregistrée", Toast.LENGTH_SHORT).show()
                                    onBack()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50),
                            contentColor = Color.White
                        )
                    ) {
                        Text("Enregistrer")
                    }
                }
            }
        }
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // Nom de la séance
            item {
                OutlinedTextField(
                    value = seanceName,
                    onValueChange = { seanceName = it },
                    label = { Text("Nom de la séance") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
            }

            // Liste des blocs racine
            itemsIndexed(blocks, key = { _, b -> b.id }) { index, block ->

                when (block) {

                    is Etape -> {
                        EtapeEditor(
                            etape = block,
                            onChange = { updated ->
                                blocks[index] = updated
                            },
                            onDelete = { blocks.removeAt(index) },
                            onMoveUp = { moveBlock(index, index - 1) },
                            onMoveDown = { moveBlock(index, index + 1) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    is GroupRepeats -> {
                        GroupEditor(
                            group = block,
                            onChange = { updated -> blocks[index] = updated },
                            onDelete = { blocks.removeAt(index) },
                            onMoveUp = { moveBlock(index, index - 1) },
                            onMoveDown = { moveBlock(index, index + 1) },
                            onDuplicate = { blocks.add(index + 1, block.deepCopy()) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Boutons d’ajout
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            blocks.add(
                                Etape(
                                    id = newId(),
                                    durationMillis = 0L,
                                    zoneId = 1,
                                    description = ""
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Étape")
                    }

                    Button(
                        onClick = {
                            blocks.add(
                                GroupRepeats(
                                    id = newId(),
                                    repeats = 1,
                                    children = listOf(
                                        Etape(
                                            id = newId(),
                                            durationMillis = 0L,
                                            zoneId = 1,
                                            description = ""
                                        )
                                    )
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Groupe")
                    }
                }
            }
        }
    }
}
