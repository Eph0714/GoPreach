package com.emfitsolutions.gopreach.data.model

/** Administration track — hierarchical, congregation/group-scoped (spec §2.1). */
enum class AdminRole {
    SUPER_ADMIN,
    ADMIN_PER_CONGREGATION,
    COORDINATOR_ELDER,
    REGULAR_ELDER,
}

/** Publisher track — categories, not a hierarchy (spec §2.2). */
enum class PublisherCategory {
    REGULAR_PIONEER,
    AUXILIARY_PIONEER,
    REGULAR_PUBLISHER,
    UNBAPTIZED_PUBLISHER,
    INACTIVE_PUBLISHER,
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

enum class Gender { MALE, FEMALE }

/** Status of a householder at a preaching visit (spec §6.3). Congregations may need to
 * extend this list — kept as a plain enum for now per the spec's fixed field name;
 * revisit as a lookup table (like [ElderTitleEntity]) if that need shows up in practice. */
enum class HouseholderStatus {
    INTERESTED,
    NOT_INTERESTED,
    NOT_AT_HOME,
    MOVED,
    DO_NOT_CALL,
    RETURN_VISIT_SCHEDULED,
}

/** Local-only sync state for offline-first CRUD (spec §6.5), stored alongside cached rows. */
enum class SyncState { SYNCED, PENDING, FAILED }

enum class SyncOperationType { CREATE, UPDATE, DELETE }
