package com.emfitsolutions.gopreach.ui.screens.elders

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ManageCoordinatorEldersScreen(
    fixedCongregationId: String?,
    currentPersonId: String,
    canPermanentlyDelete: Boolean,
    readOnly: Boolean = false,
    onBack: () -> Unit,
    onAddNew: () -> Unit,
    viewModel: ManageCoordinatorEldersViewModel = hiltViewModel(),
) {
    val rowsFlow = remember(fixedCongregationId) { viewModel.rowsFor(fixedCongregationId) }
    val rows by rowsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    ElderListScreen(
        title = "Coordinator Elders",
        scopeLabel = "Congregation",
        rows = rows,
        canPermanentlyDelete = canPermanentlyDelete,
        readOnly = readOnly,
        onBack = onBack,
        onAddNew = onAddNew,
        onSetActive = { row, active -> viewModel.setActive(row.assignment, active, currentPersonId) },
        onEdit = { _, updated -> viewModel.updatePerson(updated) },
        onPermanentlyDelete = { row -> viewModel.permanentlyDelete(row, currentPersonId) },
    )
}
