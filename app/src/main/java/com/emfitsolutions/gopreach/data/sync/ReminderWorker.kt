package com.emfitsolutions.gopreach.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.emfitsolutions.gopreach.data.model.AdminRole
import com.emfitsolutions.gopreach.data.model.PublisherCategory
import com.emfitsolutions.gopreach.data.model.RecordStatus
import com.emfitsolutions.gopreach.data.model.ReportStatus
import com.emfitsolutions.gopreach.data.model.RoleAssignment
import com.emfitsolutions.gopreach.data.model.RoleAssignmentStatus
import com.emfitsolutions.gopreach.data.model.RoleType
import com.emfitsolutions.gopreach.data.repository.AuditLogRepository
import com.emfitsolutions.gopreach.data.repository.MonthlyReportRepository
import com.emfitsolutions.gopreach.data.repository.PreachingTimeRecordRepository
import com.emfitsolutions.gopreach.data.repository.RoleAssignmentRepository
import com.emfitsolutions.gopreach.data.repository.VisitRepository
import com.emfitsolutions.gopreach.domain.PermissionChecker
import com.emfitsolutions.gopreach.domain.PublisherAutoStatus
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
    private val roleAssignmentRepository: RoleAssignmentRepository,
    private val auditLogRepository: AuditLogRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        NotificationHelper.ensureChannel(applicationContext)
        val session = userSession.state.value
        val person = session.person ?: return Result.success()

        // "CREATING PUBLISHER" spec's auto-status rules — runs once a day
        // alongside the reminder checks below, off whichever session happens
        // to be signed in on this device (see ReminderScheduler's doc
        // comment on why this app's checks are all client-side, no push/
        // Cloud Functions backend). An Admin-track session sweeps every
        // Publisher in their own visible scope, not just themselves, since
        // an admin's device is the one most likely to be opened regularly
        // enough to keep every publisher's status current.
        runAutoStatusSweep(session.roleAssignments, person.id)

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

    /** See [PublisherAutoStatus] for the actual rule. [scopeAssignments] is
     * the signed-in session's *own* RoleAssignments, used only to decide the
     * sweep's scope (an Admin-track role broadens it to their whole
     * congregation/all congregations; a plain Publisher narrows it to just
     * themselves) — never mutated directly, unlike the assignments fetched
     * from [roleAssignmentRepository] below, which are. */
    private suspend fun runAutoStatusSweep(scopeAssignments: List<RoleAssignment>, signedInPersonId: String) {
        val adminRole = PermissionChecker.highestAdminRole(scopeAssignments)
        val scopeCongregationId = scopeAssignments.firstOrNull { assignment ->
            val role = (assignment.resolvedRoleTypeOrNull() as? RoleType.Admin)?.role
            role == AdminRole.ADMIN_PER_CONGREGATION || role == AdminRole.COORDINATOR_ELDER || role == AdminRole.SERVICE_OVERSEER
        }?.congregationId

        val allAssignments = roleAssignmentRepository.observeAll().first()
        val targets = when {
            adminRole == AdminRole.SUPER_ADMIN -> allAssignments.filter { it.resolvedRoleTypeOrNull() is RoleType.Publisher }
            scopeCongregationId != null -> allAssignments.filter {
                it.resolvedRoleTypeOrNull() is RoleType.Publisher && it.congregationId == scopeCongregationId
            }
            // Not an Admin-track session at all — only re-evaluate the
            // signed-in Publisher's own assignment(s), same as the reminder
            // checks below already scope themselves to `person`.
            else -> allAssignments.filter { it.personId == signedInPersonId && it.resolvedRoleTypeOrNull() is RoleType.Publisher }
        }.filter { it.status == RoleAssignmentStatus.ACTIVE }

        if (targets.isEmpty()) return
        val allReports = monthlyReportRepository.observeAll().first()
        val periodMonthStart = (Calendar.getInstance().clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        targets.forEach { assignment ->
            val category = (assignment.resolvedRoleTypeOrNull() as? RoleType.Publisher)?.category ?: return@forEach
            val reports = allReports.filter { it.publisherPersonId == assignment.personId }
            val newCategory = PublisherAutoStatus.evaluate(category, reports, periodMonthStart) ?: return@forEach
            roleAssignmentRepository.save(assignment.copy(roleType = RoleType.serialize(RoleType.Publisher(newCategory))))
            auditLogRepository.log(
                actorPersonId = signedInPersonId,
                action = "AUTO_CHANGE_PUBLISHER_STATUS",
                targetType = "Person",
                targetId = assignment.personId,
                congregationId = assignment.congregationId,
                details = "status: $category -> $newCategory (no consistent report in the last 6 months)",
            )
        }
    }
}
