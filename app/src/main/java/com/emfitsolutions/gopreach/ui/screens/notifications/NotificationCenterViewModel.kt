package com.emfitsolutions.gopreach.ui.screens.notifications

import androidx.lifecycle.ViewModel
import com.emfitsolutions.gopreach.data.model.Announcement
import com.emfitsolutions.gopreach.data.model.ForwardRequest
import com.emfitsolutions.gopreach.data.model.ForwardRequestStatus
import com.emfitsolutions.gopreach.data.model.MonthlyReport
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.PublisherForwardRequest
import com.emfitsolutions.gopreach.data.model.ReportStatus
import com.emfitsolutions.gopreach.data.model.Schedule
import com.emfitsolutions.gopreach.data.model.ScheduleKind
import com.emfitsolutions.gopreach.data.repository.AnnouncementRepository
import com.emfitsolutions.gopreach.data.repository.AnnouncementSeenStore
import com.emfitsolutions.gopreach.data.repository.ForwardRequestRepository
import com.emfitsolutions.gopreach.data.repository.MonthlyReportRepository
import com.emfitsolutions.gopreach.data.repository.NotificationCategory
import com.emfitsolutions.gopreach.data.repository.NotificationSeenStore
import com.emfitsolutions.gopreach.data.repository.PersonRepository
import com.emfitsolutions.gopreach.data.repository.PublisherForwardRequestRepository
import com.emfitsolutions.gopreach.data.repository.ScheduleRepository
import com.emfitsolutions.gopreach.ui.components.formatRecordTimestamp
import com.emfitsolutions.gopreach.ui.navigation.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** One row in the unified notification balloon. [route] is where tapping it
 * navigates — always an existing, already-scoped screen (Forward Requests,
 * Manage Publisher Reports, Manage/Publisher Announcements, Calendar) rather
 * than a new dedicated notification-detail screen. */
data class NotificationItem(
    val category: NotificationCategory,
    val title: String,
    val subtitle: String,
    val timestamp: Long,
    val route: String,
)

private data class AdminBundle(
    val forwards: List<ForwardRequest>,
    val publisherForwards: List<PublisherForwardRequest>,
    val reports: List<MonthlyReport>,
    val announcements: List<Announcement>,
    val schedules: List<Schedule>,
)

/**
 * Backs the unified notification balloon shown to every role (spec: "Add a
 * notification balloon for Service Overseer, Elders, Admin, publisher and
 * super admin"), merging four independent event sources into one
 * chronological list:
 *  1. Incoming approval requests for a transfer (cross-congregation
 *     [ForwardRequest], same-congregation [PublisherForwardRequest]) — every
 *     role sees whichever of the two it can actually act on/view (see
 *     [com.emfitsolutions.gopreach.ui.screens.pipeline.ForwardRequestsScreen]/
 *     [com.emfitsolutions.gopreach.ui.screens.pipeline
 *     .PublisherForwardRequestsScreen]).
 *  2. Incoming Publisher Monthly Reports — not shown to a Publisher (spec).
 *  3. New Announcements.
 *  4. New Calendar Schedule entries (excludes Chat Schedule/personal notes —
 *     the spec names "Calendar Schedule" specifically).
 *
 * Congregation scoping (spec's closing note) is the caller's job, exactly
 * like every other congregation-scoped screen in this app: pass `null` for
 * [itemsForAdmin]'s `congregationIds` only for Super-Admin ("can see all
 * congregation notification"), and the exact single congregationId for
 * [itemsForPublisher] — never resolved in here.
 *
 * "Seen" tracking reuses the pre-existing per-category-cheap local approach
 * (see [NotificationSeenStore]/[AnnouncementSeenStore]) — the bell's badge is
 * an unseen *count*, not a persisted read/unread flag per item, so opening
 * the balloon (see [markAllSeen]) is what resets it, the same way opening the
 * Announcements screen already does for that one category alone.
 */
@HiltViewModel
class NotificationCenterViewModel @Inject constructor(
    private val forwardRequestRepository: ForwardRequestRepository,
    private val publisherForwardRequestRepository: PublisherForwardRequestRepository,
    private val monthlyReportRepository: MonthlyReportRepository,
    private val announcementRepository: AnnouncementRepository,
    private val scheduleRepository: ScheduleRepository,
    private val personRepository: PersonRepository,
    private val notificationSeenStore: NotificationSeenStore,
    private val announcementSeenStore: AnnouncementSeenStore,
) : ViewModel() {

    private val periodFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    /** Admin-track roles (Super-Admin/Admin/Coordinator Elder/Regular
     * Elder/Service Overseer/Ministerial Servant). [congregationIds] is
     * `null` only for Super-Admin; [includeMonthlyReports] is `false` only
     * when the caller has no Monthly Report visibility of its own (this
     * balloon never grants access beyond what the role already has). */
    fun itemsForAdmin(congregationIds: Set<String>?, includeMonthlyReports: Boolean): Flow<List<NotificationItem>> =
        combine(
            combine(
                forwardRequestRepository.observeAll(),
                publisherForwardRequestRepository.observeAll(),
                monthlyReportRepository.observeAll(),
                announcementRepository.observeAll(),
                scheduleRepository.observeAll(),
            ) { forwards, publisherForwards, reports, announcements, schedules ->
                AdminBundle(forwards, publisherForwards, reports, announcements, schedules)
            },
            personRepository.observeAll(),
        ) { bundle, people ->
            buildAdminItems(bundle, people, congregationIds, includeMonthlyReports)
        }

    private fun buildAdminItems(
        bundle: AdminBundle,
        people: List<Person>,
        congregationIds: Set<String>?,
        includeMonthlyReports: Boolean,
    ): List<NotificationItem> {
        val items = mutableListOf<NotificationItem>()

        bundle.forwards
            .filter { it.status == ForwardRequestStatus.PENDING && (congregationIds == null || it.toCongregationId in congregationIds) }
            .forEach { r ->
                items += NotificationItem(
                    category = NotificationCategory.TRANSFER_REQUEST,
                    title = "Transfer Request: ${r.personNameSnapshot}",
                    subtitle = "From ${r.fromPublisherNameSnapshot} · ${r.fromCongregationNameSnapshot}",
                    timestamp = r.requestedAt,
                    route = Destinations.FORWARD_REQUESTS,
                )
            }

        bundle.publisherForwards
            .filter { it.status == ForwardRequestStatus.PENDING && (congregationIds == null || it.congregationId in congregationIds) }
            .forEach { r ->
                items += NotificationItem(
                    category = NotificationCategory.TRANSFER_REQUEST,
                    title = "Transfer Request: ${r.personNameSnapshot}",
                    subtitle = "${r.fromPublisherNameSnapshot} → ${r.toPublisherNameSnapshot}",
                    timestamp = r.requestedAt,
                    route = Destinations.FORWARD_REQUESTS,
                )
            }

        if (includeMonthlyReports) {
            bundle.reports
                .filter { it.status == ReportStatus.SUBMITTED && (congregationIds == null || it.congregationId in congregationIds) }
                .forEach { report ->
                    val publisherName = people.firstOrNull { it.id == report.publisherPersonId }?.fullName ?: "A publisher"
                    items += NotificationItem(
                        category = NotificationCategory.MONTHLY_REPORT,
                        title = "Monthly Report: $publisherName",
                        subtitle = periodFormat.format(Date(report.periodMonth)),
                        timestamp = report.submittedAt ?: report.periodMonth,
                        // "If [a] report from [a] Publisher will be open[ed],
                        // open the exact month, not the default month of the
                        // module" — the plain MANAGE_PUBLISHER_REPORTS route
                        // always defaults to "This Month," which would show
                        // nothing for a report from any other period.
                        route = Destinations.manageReportsForMonth(report.periodMonth),
                    )
                }
        }

        bundle.announcements
            .filter { congregationIds == null || it.congregationId in congregationIds }
            .forEach { a ->
                items += NotificationItem(
                    category = NotificationCategory.ANNOUNCEMENT,
                    title = "New Announcement: ${a.title}",
                    subtitle = a.details,
                    timestamp = a.createdAt,
                    route = Destinations.MANAGE_ANNOUNCEMENTS,
                )
            }

        bundle.schedules
            .filter { it.kind == ScheduleKind.CALENDAR_EVENT && (congregationIds == null || it.congregationId in congregationIds) }
            .forEach { s ->
                items += NotificationItem(
                    category = NotificationCategory.CALENDAR_SCHEDULE,
                    title = "New Calendar Event: ${s.title}",
                    subtitle = formatRecordTimestamp(s.startTime),
                    timestamp = s.createdAt,
                    route = Destinations.CALENDAR,
                )
            }

        return items.sortedByDescending { it.timestamp }.take(50)
    }

    /** A Publisher's own balloon — no Monthly Report category (spec: "Not
     * for Publisher"), and "transfer" here means only the same-congregation
     * hand-offs targeted *at them* ([PublisherForwardRequest]) — a Publisher
     * never sees the cross-congregation Service Overseer queue. */
    fun itemsForPublisher(currentPersonId: String, congregationId: String?): Flow<List<NotificationItem>> =
        combine(
            publisherForwardRequestRepository.observeAll(),
            announcementRepository.observeAll(),
            scheduleRepository.observeAll(),
        ) { publisherForwards, announcements, schedules ->
            val items = mutableListOf<NotificationItem>()

            publisherForwards
                .filter { it.toPublisherPersonId == currentPersonId && it.status == ForwardRequestStatus.PENDING }
                .forEach { r ->
                    items += NotificationItem(
                        category = NotificationCategory.TRANSFER_REQUEST,
                        title = "Transfer Request: ${r.personNameSnapshot}",
                        subtitle = "From ${r.fromPublisherNameSnapshot}",
                        timestamp = r.requestedAt,
                        route = Destinations.PUBLISHER_FORWARD_REQUESTS,
                    )
                }

            announcements
                .filter { it.congregationId == congregationId }
                .forEach { a ->
                    items += NotificationItem(
                        category = NotificationCategory.ANNOUNCEMENT,
                        title = "New Announcement: ${a.title}",
                        subtitle = a.details,
                        timestamp = a.createdAt,
                        route = Destinations.PUBLISHER_ANNOUNCEMENTS,
                    )
                }

            schedules
                .filter { it.kind == ScheduleKind.CALENDAR_EVENT && it.congregationId == congregationId }
                .forEach { s ->
                    items += NotificationItem(
                        category = NotificationCategory.CALENDAR_SCHEDULE,
                        title = "New Calendar Event: ${s.title}",
                        subtitle = formatRecordTimestamp(s.startTime),
                        timestamp = s.createdAt,
                        route = Destinations.CALENDAR,
                    )
                }

            items.sortedByDescending { it.timestamp }.take(50)
        }

    private fun lastSeenAt(category: NotificationCategory, personId: String): Long =
        if (category == NotificationCategory.ANNOUNCEMENT) announcementSeenStore.lastSeenAt(personId)
        else notificationSeenStore.lastSeenAt(category, personId)

    /** Live unseen count for the bell's badge — recomputes whenever [items]
     * or either seen-store changes, same "combine against the seen-store's
     * own StateFlow" trick [com.emfitsolutions.gopreach.ui.screens
     * .announcements.ManageAnnouncementsViewModel.unseenCountFor] already
     * uses for the single-category case. */
    fun unseenCountFor(items: Flow<List<NotificationItem>>, currentPersonId: String): Flow<Int> =
        combine(items, notificationSeenStore.lastSeenAt, announcementSeenStore.lastSeenAtByPerson) { list, _, _ ->
            list.count { it.timestamp > lastSeenAt(it.category, currentPersonId) }
        }

    /** Opening the balloon marks every category seen "now" — the row list
     * itself is unaffected (it's not filtered by seen status, same as the
     * Announcements screen), only the badge count resets. */
    fun markAllSeen(currentPersonId: String) {
        NotificationCategory.entries.forEach { category ->
            if (category == NotificationCategory.ANNOUNCEMENT) announcementSeenStore.markSeenNow(currentPersonId)
            else notificationSeenStore.markSeenNow(category, currentPersonId)
        }
    }
}
