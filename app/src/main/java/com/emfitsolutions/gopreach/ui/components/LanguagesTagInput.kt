package com.emfitsolutions.gopreach.ui.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * "Language(s) Used" — Congregation enrollment/edit, multiple free-form
 * entries (e.g. "Iloko", "Ibanag") rather than a fixed dropdown, since a
 * congregation isn't limited to a predefined language list. A text field +
 * Add builds up chips; each chip has its own remove (X). [languages] is the
 * current list, owned by the caller — this composable holds no state of its
 * own beyond the in-progress text field.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LanguagesTagInput(
    languages: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingText by remember { mutableStateOf("") }

    fun commit() {
        val trimmed = pendingText.trim()
        if (trimmed.isNotEmpty() && languages.none { it.equals(trimmed, ignoreCase = true) }) {
            onAdd(trimmed)
        }
        pendingText = ""
    }

    Column(modifier = modifier) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                value = pendingText,
                onValueChange = { pendingText = it },
                label = { Text("Language Used") },
                placeholder = { Text("e.g. Iloko") },
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { commit() }),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { commit() }) {
                Icon(Icons.Rounded.Add, contentDescription = "Add language")
            }
        }
        if (languages.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                languages.forEach { language ->
                    AssistChip(
                        onClick = { onRemove(language) },
                        label = { Text(language) },
                        trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = "Remove $language") },
                        colors = AssistChipDefaults.assistChipColors(),
                    )
                }
            }
        }
    }
}
