package com.emfitsolutions.gopreach.ui.screens.planner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.data.model.PipelineStage
import com.emfitsolutions.gopreach.domain.WeekBounds
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** My Planner → Week (Dashboard/My Planner integration spec §4-§7) — a
 * "Weekly Report" summary, an "Hour Goal for This Week" stepper (Remaining
 * per spec §4/§13 also subtracts Credit Hours, unlike Day/Month/Year — see
 * [PlannerWeekUiState.remainingMinutes]), a Monday..Sunday breakdown with
 * the current day marked, then the week's individual records. Every day row
 * and every record row is tappable. */
@Composable
internal fun PlannerWeekContent(
    currentPersonId: String,
    viewModel: PlannerWeekViewModel,
    onOpenDay: (Long) -> Unit,
    onOpenPerson: (PipelineStage, String) -> Unit,
    onOpenPersonList: (PipelineStage) -> Unit,
) {
    val weekStart by viewModel.weekStart.collectAsStateWithLifecycle()
    // See PlannerDayContent's identical remember(currentPersonId) note —
    // stateFor() builds a fresh combine()+stateIn() pipeline each call.
    val state by remember(currentPersonId) { viewModel.stateFor(currentPersonId) }.collectAsStateWithLifecycle()
    val weekFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val weekEnd = remember(weekStart) { Calendar.getInstance().apply { timeInMillis = weekStart; add(Calendar.DAY_OF_MONTH, 6) }.timeInMillis }
    val weekLabel = "${weekFormat.format(Date(weekStart))} – ${weekFormat.format(Date(weekEnd))}"

    val creditViewModel: CreditHourEntryViewModel = hiltViewModel()
    val creditDialogs = remember { CreditHourDialogState() }
    val categoryName = rememberCategoryNamer(creditViewModel)
    val expansion = rememberPlannerExpansionState(PlannerSectionKey.DAYS)

    Column(modifier = Modifier.fillMaxWidth()) {
        PlannerDateNavHeader(label = weekLabel, onPrevious = viewModel::goToPreviousWeek, onNext = viewModel::goToNextWeek)
        PlannerLoadingBar(state.isLoading)

        Column(modifier = Modifier.padding(PlannerBodyPadding), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PlannerActivitySummary(
                stats = periodStats(state.totalMinutes, state.creditHoursMinutes, state.returnVisitCount, state.bibleStudyCount, LocalPlannerVisibility.current),
                onStatClick = expansion::expand,
            )
            PlannerGoalRow(
                label = "Week goal",
                goalHours = state.goalHours,
                remainingMinutes = state.remainingMinutes,
                surplusMinutes = state.surplusMinutes,
                onSetGoal = { viewModel.setGoalHours(currentPersonId, it) },
            )

            PlannerExpandableSection(
                title = "Days",
                expanded = expansion.isExpanded(PlannerSectionKey.DAYS),
                onToggle = { expansion.toggle(PlannerSectionKey.DAYS) },
            ) {
                state.dayRows.forEach { row -> WeekDayRow(row, onClick = { onOpenDay(row.dayStart) }) }
            }

            val bounds = remember(weekStart) { WeekBounds.of(weekStart) }
            PlannerRecordSections(
                records = state.records,
                expansion = expansion,
                categoryName = categoryName,
                onOpenCredit = { creditDialogs.detail = it },
                onAddCredit = { creditDialogs.addingForDay = defaultEntryDay(bounds, weekStart) },
                onOpenPerson = onOpenPerson,
                onOpenPersonList = onOpenPersonList,
                onOpenDay = onOpenDay,
            )
        }
    }

    CreditHourDialogsHost(creditDialogs, currentPersonId, creditViewModel)
}

/** One line per weekday: name + date, that day's RV/BS/Credit on the
 * second line, ministry time on the right. Tapping opens the Day view. */
@Composable
private fun WeekDayRow(row: PlannerWeekDayRow, onClick: () -> Unit) {
    val visibility = LocalPlannerVisibility.current
    val details = buildList {
        if (visibility.creditHours && row.creditMinutes > 0) add("Credit ${formatHoursMinutes(row.creditMinutes)}")
        if (visibility.returnVisits && row.returnVisitCount > 0) add("RV ${row.returnVisitCount}")
        if (visibility.bibleStudies && row.bibleStudyCount > 0) add("BS ${row.bibleStudyCount}")
    }.joinToString(" · ")
    PlannerRecordRow(
        title = SimpleDateFormat("EEE d", Locale.getDefault()).format(Date(row.dayStart)) + if (row.isToday) " · Today" else "",
        subtitle = details.ifBlank { null },
        trailing = if (visibility.hours) formatHoursMinutes(row.totalMinutes) else null,
        onClickLabel = "Open this day",
        emphasize = row.isToday,
        onClick = onClick,
    )
}
