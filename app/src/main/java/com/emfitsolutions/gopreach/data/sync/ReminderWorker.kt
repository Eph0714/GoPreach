package com.emfitsolutions.gopreach.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.emfitsolutions.gopreach.data.model.PublisherCategory
import com.emfitsolutions.gopreach.data.model.RecordStatus
import com.emfitsolutions.gopreach.data.model.ReportStatus
import com.emfitsolutions.gopreach.data.model.RoleType
import com.emfitsolutions.gopreach.data.repository.MonthlyReportRepository
import com.emfitsolutions.gopreach.data.repository.PreachingTimeRecordRepository
import com.emfitsolutions.gopreach.data.repository.VisitRepository
import com.emfitsolutions.gopreach.domain.UserSession
import com.emfitsolutions.gopreach.notifications.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.Calendar

private const val NOTIFICATION_ID_PARTICIPATION = 9001
private const val NOTIFICATION_ID_SUBMIT_REPORT = 9002

/**
 * "Submitting of report" spec items 1-2 — checked once a day (see
 * [ReminderScheduler]) against whichever Publisher role the currently
 * signed-in session (if any) holds:
 * 1. 5 days before month end, if no preaching activity has been recorded
 *    yet this month (Pioneer: any [com.emfitsolutions.gopreach.data.model
 *    .PreachingTimeRecord] hours; Regular/Unbaptized: any preaching
 *    [com.emfitsolutions.gopreach.data.model.Visit] logged — this app's
 *    closest proxy for "participated in ministry" outside a submitted
 *    report, which by definition can't exist yet mid-month) — nudge to go
 *    preach.
 * 2. Once inside the last-2-days submission window (see
 *    [com.emfitsolutions.gopreach.ui.screens.monthlyreport
 *    .MonthlyReportUiState.canSubmitWindow]), if this month's report hasn't
 *    been submitted yet — nudge to submit.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val userSession: UserSession,
    private val monthlyReportRepository: MonthlyReportRepository,
    private val preachingTimeRecordRepository: PreachingTimeRecordRepository,
    private val visitRepository: VisitRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        NotificationHelper.ensureChannel(applicationContext)
        val session = userSession.state.value
        val person = session.person ?: return Result.success()
        val publisherCategory = session.roleAssignments
            .firstNotNullOfOrNull { (it.resolvedRoleTypeOrNull() as? RoleType.Publisher)?.category }
            ?: return Result.success()

        val cal = Calendar.getInstance()
        val daysUntilMonthEnd = cal.getActualMaximum(Calendar.DAY_OF_MONTH) - cal.get(Calendar.DAY_OF_MONTH)
        val periodMonthStart = (cal.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val isPioneer = publisherCategory == PublisherCategory.REGULAR_PIONEER || publisherCategory == PublisherCategory.AUXILIARY_PIONEER

        if (daysUntilMonthEnd == 5) {
            val hasParticipated = if (isPioneer) {
                preachingTimeRecordRepository.observeForPublisher(person.id).first()
                    .any { it.status == RecordStatus.ACTIVE && it.date >= periodMonthStart && it.hoursConsumed > 0 }
            } else {
                visitRepository.observeAllForPublisher(person.id).first().any { it.visitDate >= periodMonthStart }
            }
            if (!hasParticipated) {
                NotificationHelper.notify(
                    applicationContext,
                    NOTIFICATION_ID_PARTICIPATION,
                    "Preaching Activity Reminder",
                    "You haven't recorded any preaching activity yet this month.",
                )
            }
        }

        if (daysUntilMonthEnd <= 2) {
            val existing = monthlyReportRepository.observeAll().first()
                .firstOrNull { it.publisherPersonId == person.id && it.periodMonth == periodMonthStart }
            if (existing == null || existing.status != ReportStatus.SUBMITTED) {
                NotificationHelper.notify(
                    applicationContext,
                    NOTIFICATION_ID_SUBMIT_REPORT,
                    "Monthly Report Due",
                    "Don't forget to submit your Monthly Report before the month ends.",
                )
            }
        }

        return Result.success()
    }
}
