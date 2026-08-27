package com.emfitsolutions.gopreach.data.model

/** Administration track — hierarchical, congregation/group-scoped (spec §2.1). */
enum class AdminRole {
    SUPER_ADMIN,
    ADMIN_PER_CONGREGATION,
    COORDINATOR_ELDER,
    /** One per congregation (spec: "there must be 1 Service Overseer in
     * every congregation") — enforced as "at most one active" at enrollment
     * time (see ServiceOverseerEnrollmentViewModel), not a hard app-wide
     * invariant, since an admin may not have created one yet. Sees the
     * Consolidated Monthly Report for their own congregation; Coordinator
     * Elder/Admin see the same for their congregation, Super-Admin for every
     * congregation (see ConsolidatedReportViewModel). */
    SERVICE_OVERSEER,
    REGULAR_ELDER,
    /** Not an Elder — a distinct appointed position (spec: "MINISTERIAL
     * ACCOUNT"), enrollable by Super-Admin, Admin (own congregation), and
     * Coordinator Elder, unlike multiple per congregation are allowed (no
     * uniqueness constraint, unlike [SERVICE_OVERSEER]'s "at most one").
     * Its own "Select Role" checkboxes reuse [RegularElderRole.GROUP_SERVANT]/
     * [RegularElderRole.GROUP_ASSISTANT] via an additional, simultaneous
     * `Admin(REGULAR_ELDER)` RoleAssignment — the same "extra role on top of
     * the primary one" pattern already used for Coordinator Elder/Service
     * Overseer's own Group Overseer checkbox (see
     * MinisterialServantEnrollmentViewModel) — since a Group's Servant/
     * Assistant slot is filled the same way regardless of whether the person
     * is technically an Elder or a Ministerial Servant. */
    MINISTERIAL_SERVANT,

    /**
     * A restricted, externally-facing role (e.g. a real Circuit Overseer, or any
     * other one-off account a Super-Admin needs to create) that carries **no**
     * implicit permissions of its own — unlike the four roles above, whose access
     * is a fixed, built-in set. Every [AdminRole.CIRCUIT_OVERSEER] account's
     * actual capabilities live entirely in its [UserAccessGrant]: what it may do
     * ([UserAccessGrant.permissions]) and where ([UserAccessGrant.scopeType] +
     * scope lists). A RoleAssignment of this role with no matching grant grants
     * nothing at all — see [com.emfitsolutions.gopreach.domain.PermissionChecker.hasPermission].
     */
    CIRCUIT_OVERSEER,
}

/**
 * WHAT a restricted ([AdminRole.CIRCUIT_OVERSEER]) user may do — see
 * [UserAccessGrant]. Deliberately a flat enum rather than hard-coded booleans
 * scattered through the app, so a future permission is one new case plus
 * whatever screen checks it — not a schema change (spec §12/§15).
 */
enum class Permission {
    VIEW_CONGREGATIONS, ADD_CONGREGATIONS, EDIT_CONGREGATIONS, DELETE_CONGREGATIONS,
    VIEW_ELDERS, MANAGE_ELDERS,
    VIEW_GROUPS, MANAGE_GROUPS,
    VIEW_PUBLISHERS, MANAGE_PUBLISHERS,
    VIEW_PUBLISHER_REPORTS, VIEW_GROUP_REPORTS, VIEW_CONGREGATION_REPORTS,
    PRINT_REPORTS, EXPORT_REPORTS,
    MANAGE_USERS,
}

/** WHERE a [Permission] applies for a restricted user (spec §6) — kept entirely
 * separate from [Permission] itself so "can view reports" and "for which
 * congregations" are independently configurable. */
enum class ScopeType {
    ALL_CONGREGATIONS,
    SELECTED_CONGREGATIONS,
    SELECTED_GROUPS,
}

/** Account-level (not role-level) lifecycle switch (spec §9) — distinct from
 * [RoleAssignmentStatus], which tracks one specific role/report-access grant.
 * A deactivated/suspended account can never sign in, full stop, regardless of
 * how many active RoleAssignments it still holds; nothing about its historical
 * records is touched. */
enum class AccountStatus { ACTIVE, INACTIVE, SUSPENDED }

/** Publisher track — categories, not a hierarchy (spec §2.2). */
enum class PublisherCategory {
    REGULAR_PIONEER,
    AUXILIARY_PIONEER,
    REGULAR_PUBLISHER,
    UNBAPTIZED_PUBLISHER,
    /** "CREATING PUBLISHER" spec's STATUS list — not regular about reporting
     * (some months with no report during a 6-month window); the spec's
     * auto-status note assigns this automatically once that check is wired
     * up, same as [INACTIVE_PUBLISHER]'s "6 months consecutive" trigger —
     * also manually selectable here at enrollment/edit in the meantime. */
    IRREGULAR_PUBLISHER,
    INACTIVE_PUBLISHER,
    /** "CREATING PUBLISHER" spec's STATUS list — a Publisher under reproof;
     * distinct from [REMOVED_PUBLISHER], which additionally blocks the
     * account from signing in at all (see ManagePublishersViewModel). */
    REPROOF_PUBLISHER,
    REMOVED_PUBLISHER,
}

/**
 * A [RoleAssignment.roleType] is either one of the [AdminRole]s or "the person is a
 * publisher in this [PublisherCategory]" — a Person can hold several RoleAssignments
 * at once (spec §3: a Coordinator Elder who is also a Regular Pioneer).
 */
sealed class RoleType {
    data class Admin(val role: AdminRole) : RoleType()
    data class Publisher(val category: PublisherCategory) : RoleType()

    companion object {
        private const val ADMIN_PREFIX = "ADMIN:"
        private const val PUBLISHER_PREFIX = "PUBLISHER:"

        /** Firestore/Room store role type as a flat string; this round-trips it. */
        fun serialize(type: RoleType): String = when (type) {
            is Admin -> "$ADMIN_PREFIX${type.role.name}"
            is Publisher -> "$PUBLISHER_PREFIX${type.category.name}"
        }

        fun deserialize(raw: String): RoleType = when {
            raw.startsWith(ADMIN_PREFIX) -> Admin(AdminRole.valueOf(raw.removePrefix(ADMIN_PREFIX)))
            raw.startsWith(PUBLISHER_PREFIX) -> Publisher(PublisherCategory.valueOf(raw.removePrefix(PUBLISHER_PREFIX)))
            else -> error("Unknown RoleType: $raw")
        }
    }
}

enum class RoleAssignmentStatus { ACTIVE, INACTIVE }

/**
 * Lifecycle status for a master record that isn't Person/RoleAssignment-based
 * (Congregation, Group, InterestedPerson) — the "Admin Record Deletion and
 * Inactive Status" spec's "Move to Inactive" outcome for those record types.
 * Person-linked records (Admins, Elders, Publisher categories, restricted
 * Users) already had their own equivalent before this spec
 * ([RoleAssignmentStatus], [PublisherCategory.REMOVED_PUBLISHER], and
 * [AccountStatus] respectively) and keep using those, rather than gaining a
 * second, redundant status field.
 */
enum class RecordStatus { ACTIVE, INACTIVE }

/** A Regular Elder's structural role within their assigned Group — distinct from
 * [ElderTitleEntity] (a free-form, admin-editable "specific title" label): this is
 * a fixed 3-way split that drives Group-completeness validation and which of a
 * Group's three Regular Elder slots this person fills. Every Group needs exactly
 * one of each to be considered fully assigned. */
enum class RegularElderRole { GROUP_OVERSEER, GROUP_SERVANT, GROUP_ASSISTANT }

enum class Gender { MALE, FEMALE }

/** Outcome of one preaching visit logged against a Return Visit or Bible
 * Study pipeline record ("Manage Returned Visit/Bible Study Module" spec's
 * Status dropdown: NH/B/CA/MO/NT). Replaces the old, differently-scoped
 * `HouseholderStatus` — this app's only user of that enum was [Visit.outcome]
 * itself, so this is a rename-in-place to the spec's exact vocabulary, not a
 * parallel field. */
enum class VisitOutcome {
    /** NH */ NOT_AT_HOME,
    /** B */ BUSY,
    /** CA */ CALL_AGAIN,
    /** MO */ MOVED_OUT,
    /** NT — ready for the next study topic. */ NEXT_TOPIC,
}

/** Where one [InterestedPerson] currently sits in the Searching → Return
 * Visit → Bible Study pipeline (spec's three-module redesign). Distinct from
 * [RecordStatus] (active/inactive soft-delete) — a record can be inactive at
 * any stage. Advancing a stage is a one-way action from the Searching/Return
 * Visit module's own screen (see PipelineViewModel.advanceStage); there is no
 * spec'd path backward. */
enum class PipelineStage { SEARCHING, RETURN_VISIT, BIBLE_STUDY }

/** Lifecycle of one cross-congregation [ForwardRequest] ("Forward to Other
 * Congregation" spec flow). [InterestedPerson.pendingForwardRequestId] points
 * at the most recent one for that person (if any), regardless of which of
 * these three states it's currently in — that's how the sending publisher's
 * own screen shows a live "Forward status: ..." without a separate lookup. */
enum class ForwardRequestStatus { PENDING, ACCEPTED, DECLINED }

/** Local-only sync state for offline-first CRUD (spec §6.5), stored alongside cached rows. */
enum class SyncState { SYNCED, PENDING, FAILED }

enum class SyncOperationType { CREATE, UPDATE, DELETE }
