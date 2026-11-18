package com.gfocreation.heartrateandzoneannounce.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gfocreation.heartrateandzoneannounce.model.*
import com.gfocreation.heartrateandzoneannounce.misc.formatMsToHms
import com.gfocreation.heartrateandzoneannounce.misc.convertHms2Ms
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.gfocreation.heartrateandzoneannounce.R


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EtapeEditor(
    etape: Etape,
    onChange: (Etape) -> Unit,
    onMoveUp: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {

    // Etats locaux affichage / édition
    var durText by remember(etape.id) { mutableStateOf(formatMsToHms(etape.durationMillis)) }
    var zoneId by remember(etape.id) { mutableStateOf(etape.zoneId) }
    var desc by remember(etape.id) { mutableStateOf(etape.description) }

    // Gestion de la liste des zones
    var expanded by remember { mutableStateOf(false) }
    val zoneSpec = CANONICAL_ZONES.getOrNull(zoneId)
    val zoneColor = zoneSpec?.color ?: Color.Gray


    Card(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(top = 4.dp)
            .border(2.dp, zoneColor, RoundedCornerShape(12.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            // ---------- Colonne de gauche ----------
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 80.dp)
            ) {
                // Ligne : durée (80.dp) + select zone (reste)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Durée compact
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                    ) {
                        if (durText.isEmpty()) {
                            Text(
                                text = "Durée",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                            )
                        }
                        BasicTextField(
                            value = durText,
                            onValueChange = {
                                durText = it
                                val ms = convertHms2Ms(it)
                                onChange(etape.copy(durationMillis = ms))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            singleLine = true,
                            maxLines = 1,
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Spacer(Modifier.width(6.dp))

                    // Zone selector compact (one-line)
                    val zoneLabel = "Zone ${zoneSpec?.idKey ?: zoneId} - ${zoneSpec?.shortLabel ?: ""}"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                    ) {
                        // anchor clickable
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                .clickable { expanded = !expanded }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = zoneLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (expanded) "Fermer" else "Ouvrir"
                                )
                            }
                        }

                        // Dropdown : zones triées décroissant (zone max en haut)
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            CANONICAL_ZONES
                                .sortedByDescending { it.idKey }
                                .forEach { z ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                // swatch circulaire
                                                Box(
                                                    modifier = Modifier
                                                        .size(12.dp)
                                                        .clip(CircleShape)
                                                        .background(z.color)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = "Zone ${z.idKey} — ${z.shortLabel}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        },
                                        onClick = {
                                            zoneId = z.idIndex
                                            expanded = false
                                            onChange(etape.copy( zoneId = zoneId))
                                        },
                                        modifier = Modifier.background(Color.Transparent)
                                    )
                                }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Description (entière largeur colonne)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                ) {
                    if (desc.isEmpty()) {
                        Text(
                            text = "Description",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(start = 8.dp, top = 6.dp)
                        )
                    }
                    BasicTextField(
                        value = desc,
                        onValueChange = {
                            desc = it
                            onChange(etape.copy( description = desc))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        singleLine = true,
                        maxLines = 1,
                        decorationBox = { inner ->
                            Box(modifier = Modifier.fillMaxWidth()) { inner() }
                        }
                    )
                }
            }

            // ---------- Colonne de droite : Up / Delete / Down ----------
            Column(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .height(80.dp),            // conserve la hauteur totale
                verticalArrangement = Arrangement.SpaceBetween, // compaction correcte
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Monter",
                    tint = if (onMoveUp != null) MaterialTheme.colorScheme.primary else Color.Gray,
                    modifier = Modifier
                        .size(22.dp)           // taille VISUELLE fixée
                        .clickable(enabled = onMoveUp != null) { onMoveUp?.invoke()}        // onMoveUp() }
                )


                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Supprimer",
                    tint = Color(0xFF8F4646),
                    modifier = Modifier
                        .size(22.dp)
                        .clickable { onDelete?.invoke() }       // onDelete() }
                )

                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Descendre",
                    tint = if (onMoveDown != null) MaterialTheme.colorScheme.primary else Color.Gray,
                    modifier = Modifier
                        .size(22.dp)           // taille VISUELLE fixée
                        .clickable(enabled = onMoveDown != null) { onMoveDown?.invoke() }        // onMoveDown() }
                )

            }
        }
    }
}


@Composable
fun CompactStepper(
    value: Int,
    onValueChange: (Int) -> Unit
) {
    var text by remember(value) { mutableStateOf(value.toString()) }

    fun commit(v: Int) {
        val clamped = v.coerceIn(1, 99)
        text = clamped.toString()
        onValueChange(clamped)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {

        // --- bouton "-" ---
        Box(
            modifier = Modifier
                .size(28.dp)
                .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                .clickable { commit(value - 1) },
            contentAlignment = Alignment.Center
        ) {
            Text("-", style = MaterialTheme.typography.bodyMedium)
        }

        // --- champ compact SANS padding ---
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(28.dp)
                .border(1.dp, Color.Gray, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            BasicTextField(
                value = text,
                onValueChange = { input ->
                    val filtered = input.filter { it.isDigit() }.take(2)
                    text = filtered
                    filtered.toIntOrNull()?.let { commit(it) }
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier
                    .fillMaxWidth()
            )
        }

        // --- bouton "+" ---
        Box(
            modifier = Modifier
                .size(28.dp)
                .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                .clickable { commit(value + 1) },
            contentAlignment = Alignment.Center
        ) {
            Text("+", style = MaterialTheme.typography.bodyMedium)
        }
    }
}


@Composable
fun GroupEditor(
    group: GroupRepeats,
    onChange: (GroupRepeats) -> Unit,
    onDelete: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onDuplicate: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // local copy pour affichage/édition (on renvoie via onChange)
    var repeatsText by remember(group.id) { mutableStateOf(group.repeats.toString()) }

    // helper : clamp repeats entre 1 et 99
    fun commitRepeatsFromText() {
        val v = repeatsText.filter { it.isDigit() }.toIntOrNull() ?: 1
        val clamped = v.coerceIn(1, 99)
        if (clamped != group.repeats) {
            onChange(group.copy(repeats = clamped))
        }
        repeatsText = clamped.toString()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color.Gray, RoundedCornerShape(8.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // --- Header single line: "Répéter x" + numeric field + up/delete/down icons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {

                // "Répéter" label + champ compact
                Text(
                    text = "Répéter",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 6.dp)
                )

                CompactStepper(
                    value = group.repeats,
                    onValueChange = { v ->
                        onChange(group.copy(repeats = v))
                    }
                )

                // Commit on focus loss / or when changing via icon? We'll commit on change of text when user leaves,
                // but also provide immediate commit when pressing up/down on the group controls below.
                LaunchedEffect(repeatsText) {
                    // if user input becomes empty we don't immediately commit; wait for explicit commit
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {

                    // Dupliquer
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Dupliquer",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable(enabled = onDuplicate != null) {
                                commitRepeatsFromText()
                                onDuplicate?.invoke()
                            }
                            .padding(2.dp)
                    )

                    // Move Up button for group
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Monter",
                        tint = if (onMoveUp != null) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier
                            .clickable(enabled = onMoveUp != null) {
                                commitRepeatsFromText()
                                onMoveUp?.invoke()
                            }
                            .padding(2.dp)
                    )

                    // Move Down button for group
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Descendre",
                        tint = if (onMoveDown != null) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier
                            .clickable(enabled = onMoveDown != null) {
                                commitRepeatsFromText()
                                onMoveDown?.invoke()
                            }
                            .padding(2.dp)
                    )

                    // Delete group button
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Supprimer",
                        tint = Color(0xFF8F4646),
                        modifier = Modifier
                            .clickable() {
                                commitRepeatsFromText()
                                onDelete?.invoke()
                            }
                            .padding(2.dp)
                    )
                }

            }

            Spacer(Modifier.height(8.dp))

            val lastIsEtape = group.children.lastOrNull() is Etape

            if (lastIsEtape) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .padding(start = 4.dp)
                ) {
                    Checkbox(
                        checked = group.ignoreLastStepOnLastRepeat,
                        onCheckedChange = { checked ->
                            onChange(group.copy(ignoreLastStepOnLastRepeat = checked))
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.seance_bloc_ignore),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(Modifier.height(8.dp))
            }

            // --- Children list (0..n)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val children = group.children
                children.forEachIndexed { idx, child ->

                    // wrapper to show move up/down/delete for each child, and the child editor itself
                    when (child) {
                        is Etape -> {
                            EtapeEditor(
                                etape = child,
                                onChange = { updatedEt ->
                                    val newChildren = group.children.toMutableList()
                                    newChildren[idx] = updatedEt
                                    // onChange(group.copy(children = newChildren))
                                    onChange(group.withChildren(newChildren))
                                },
                                onDelete = {
                                    val newChildren = group.children.toMutableList()
                                    newChildren.removeAt(idx)
                                    onChange(group.withChildren(newChildren))       // group.copy(children = newChildren))
                                },
                                onMoveUp = if (idx > 0) {
                                    {
                                        val newChildren = group.children.toMutableList()
                                        val moved = newChildren.removeAt(idx)
                                        newChildren.add(idx - 1, moved)
                                        onChange(group.withChildren(newChildren))       // group.copy(children = newChildren))
                                    }
                                } else null,
                                onMoveDown = if (idx < group.children.lastIndex) {
                                    {
                                        val newChildren = group.children.toMutableList()
                                        val moved = newChildren.removeAt(idx)
                                        newChildren.add(idx + 1, moved)
                                        onChange(group.withChildren(newChildren))       // group.copy(children = newChildren))
                                    }
                                } else null,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        is GroupRepeats -> {
                            GroupEditor(
                                group = child,
                                onChange = { updatedGroup ->
                                    val newChildren = group.children.toMutableList()
                                    newChildren[idx] = updatedGroup
                                    onChange(group.withChildren(newChildren))       // group.copy(children = newChildren))
                                },
                                onDelete = {
                                    val newChildren = group.children.toMutableList()
                                    newChildren.removeAt(idx)
                                    onChange(group.withChildren(newChildren))       // group.copy(children = newChildren))
                                },
                                onMoveUp = if (idx > 0) {
                                    {
                                        val newChildren = group.children.toMutableList()
                                        val moved = newChildren.removeAt(idx)
                                        newChildren.add(idx - 1, moved)
                                        onChange(group.withChildren(newChildren))       // group.copy(children = newChildren))
                                    }
                                } else null,
                                onMoveDown = if (idx < group.children.lastIndex) {
                                    {
                                        val newChildren = group.children.toMutableList()
                                        val moved = newChildren.removeAt(idx)
                                        newChildren.add(idx + 1, moved)
                                        onChange(group.withChildren(newChildren))       // group.copy(children = newChildren))
                                    }
                                } else null,
                                onDuplicate = {
                                    val newChildren = group.children.toMutableList()
                                    newChildren.add(idx + 1, child.deepCopy())
                                    onChange(group.withChildren(newChildren))       // group.copy(children = newChildren))
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                }
            }

            Spacer(Modifier.height(8.dp))

            // --- Add buttons row (one-line) : [+ Étape]  [+ Groupe]
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = {
                    // add an Etape at end
                    val newEt = Etape(id = newId(), durationMillis = 0L, description = "", zoneId = 1)
                    val newChildren = group.children + newEt
                    // onChange(group.copy(children = newChildren))
                    onChange(group.withChildren(newChildren))
                }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Étape", style = MaterialTheme.typography.bodyMedium)
                }

                Button(onClick = {
                    val newGroup = GroupRepeats(id = newId(), repeats = 1, children = listOf(
                        Etape(id = newId(), durationMillis = 0L, description = "", zoneId = 1)
                    ))
                    val newChildren = group.children + newGroup
                    // onChange(group.copy(children = newChildren))
                    onChange(group.withChildren(newChildren))
                }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Groupe", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
