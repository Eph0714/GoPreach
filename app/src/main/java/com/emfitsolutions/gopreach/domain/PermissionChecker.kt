package com.emfitsolutions.gopreach.domain

import com.emfitsolutions.gopreach.data.model.AdminRole
import com.emfitsolutions.gopreach.data.model.RoleAssignment
import com.emfitsolutions.gopreach.data.model.RoleAssignmentStatus
import com.emfitsolutions.gopreach.data.model.RoleType

/**
 * Every permission check in the app reduces to one question (spec §3): "does this
 * Person have an ACTIVE RoleAssignment matching role X in scope Y" — never a lookup
 * on the Person record, since a person's admin role and publisher category are
 * separate RoleAssignment rows that can both be true at once.
 */
object PermissionChecker {

    /** True if any of [assignments] is an active Admin RoleAssignment of [role],
     * scoped to [congregationId] (Super-Admin is congregation-agnostic and matches
     * any congregation) and, when [role] is REGULAR_ELDER, to [groupId]. */
    fun hasAdminRole(
        assignments: List<RoleAssignment>,
        role: AdminRole,
        congregationId: String? = null,
        groupId: String? = null,
    ): Boolean = assignments.any { a ->
        a.status == RoleAssignmentStatus.ACTIVE &&
            (a.resolvedRoleType() as? RoleType.Admin)?.role == role &&
            (role == AdminRole.SUPER_ADMIN || a.congregationId == congregationId) &&
            (role != AdminRole.REGULAR_ELDER || a.groupId == groupId)
    }

    /** True if any of [assignments] is an active Publisher RoleAssignment. Category
     * is intentionally not part of most checks — most publisher-facing capabilities
     * (submit a report, log a visit) apply to any active publisher category. */
    fun isActivePublisher(assignments: List<RoleAssignment>): Boolean = assignments.any { a ->
        a.status == RoleAssignmentStatus.ACTIVE && a.resolvedRoleType() is RoleType.Publisher
    }

    /** The most senior Admin-track role among [assignments], if any — for deciding
     * which Control Panel / management screens to surface after login. */
    fun highestAdminRole(assignments: List<RoleAssignment>): AdminRole? {
        val activeAdminRoles = assignments
            .filter { it.status == RoleAssignmentStatus.ACTIVE }
            .mapNotNull { (it.resolvedRoleType() as? RoleType.Admin)?.role }
            .toSet()
        return listOf(
            AdminRole.SUPER_ADMIN,
            AdminRole.ADMIN_PER_CONGREGATION,
            AdminRole.COORDINATOR_ELDER,
            AdminRole.REGULAR_ELDER,
        ).firstOrNull { it in activeAdminRoles }
    }
}
