package com.emfitsolutions.gopreach.ui.screens.elders

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ManageRegularEldersScreen(
    fixedCongregationId: String?,
    onBack: () -> Unit,
    onAddNew: () -> Unit,
    viewModel: ManageRegularEldersViewModel = hiltViewModel(),
) {
    val rowsFlow = remember(fixedCongregationId) { viewModel.rowsFor(fixedCongregationId) }
    val rows by rowsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    ElderListScreen(
        title = "Regular Elders",
        scopeLabel = "Group",
        rows = rows,
        onBack = onBack,
        onAddNew = onAddNew,
        onSetActive = { row, active -> viewModel.setActive(row.assignment, active) },
    )
}
