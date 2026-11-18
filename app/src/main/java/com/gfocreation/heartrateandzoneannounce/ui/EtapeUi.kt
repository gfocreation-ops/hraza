package com.gfocreation.heartrateandzoneannounce.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gfocreation.heartrateandzoneannounce.model.*
import com.gfocreation.heartrateandzoneannounce.misc.*
import kotlin.math.max

@Composable
fun EtapeCardSimple(
    index: Int,
    etape: Etape,
    onUpdate: (Etape) -> Unit,
    onDelete: () -> Unit
) {
    var durationText by remember(etape.id) { mutableStateOf(formatMsToHms(etape.durationMillis)) }
    var desc by remember(etape.id) { mutableStateOf(etape.description) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .border(1.dp, Color.LightGray),
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = durationText,
                    onValueChange = {
                        durationText = it
                        onUpdate(etape.copy(durationMillis = convertHms2Ms(it)))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                BasicTextField(
                    value = desc,
                    onValueChange = {
                        desc = it
                        onUpdate(etape.copy(description = it))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Supprimer")
            }
        }
    }
}

@Composable
fun GroupCardEditor(
    group: GroupRepeats,
    onUpdate: (GroupRepeats) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(6.dp)
            .border(2.dp, Color(0xFF90CAF9)),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Groupe (répéter):", modifier = Modifier.weight(1f))
                OutlinedTextField(
                    value = group.repeats.toString(),
                    onValueChange = {
                        val v = it.toIntOrNull() ?: 0
                        onUpdate(group.copy(repeats = v))
                    },
                    modifier = Modifier.width(80.dp)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Supprimer groupe")
                }
            }

            Spacer(Modifier.height(8.dp))

            // children
            Column {
                group.children.forEachIndexed { idx, child ->
                    when (child) {
                        is Etape -> EtapeCardSimple(
                            index = idx,
                            etape = child,
                            onUpdate = { updated ->
                                val newChildren = group.children.toMutableList()
                                newChildren[idx] = updated
                                onUpdate(group.copy(children = newChildren))
                            },
                            onDelete = {
                                val newChildren = group.children.toMutableList()
                                newChildren.removeAt(idx)
                                onUpdate(group.copy(children = newChildren))
                            }
                        )
                        is GroupRepeats -> GroupCardEditor(
                            group = child,
                            onUpdate = { updated ->
                                val newChildren = group.children.toMutableList()
                                newChildren[idx] = updated
                                onUpdate(group.copy(children = newChildren))
                            },
                            onDelete = {
                                val newChildren = group.children.toMutableList()
                                newChildren.removeAt(idx)
                                onUpdate(group.copy(children = newChildren))
                            }
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                Row {
                    Button(onClick = {
                        val newEt = Etape(id = newId(), durationMillis = 0L, description = "", zoneId = 1)
                        onUpdate(group.copy(children = group.children + newEt))
                    }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Ajouter étape")
                    }

                    Spacer(Modifier.width(8.dp))

                    Button(onClick = {
                        val newGroup = GroupRepeats(id = newId(), repeats = 1, children = listOf(
                            Etape(id = newId(), durationMillis = 0L, description = "", zoneId = 1)
                        ))
                        onUpdate(group.copy(children = group.children + newGroup))
                    }) {
                        Text("Ajouter sous-groupe")
                    }
                }
            }
        }
    }
}
