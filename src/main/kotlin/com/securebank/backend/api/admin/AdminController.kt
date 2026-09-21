package com.securebank.backend.api.admin

import com.securebank.backend.api.auth.UserResponseData
import com.securebank.backend.common.ApiResponse
import com.securebank.backend.common.PaginatedData
import com.securebank.backend.security.UserPrincipal
import com.securebank.backend.service.AdminService
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

/** FR-BE-021, FR-BE-022, FR-BE-023 — Admin-only, per FRD §7.5 / README §5.7 ("no Retrofit interface, Web UI / internal tooling"). */
@RestController
@RequestMapping("\${securebank.api.base-path}/admin")
@PreAuthorize("hasRole('ADMIN')")
class AdminController(private val adminService: AdminService) {

    @GetMapping("/users")
    fun listUsers(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int
    ): ApiResponse<PaginatedData<UserResponseData>> {
        return ApiResponse.success(adminService.listUsers(page, pageSize))
    }

    @GetMapping("/users/{userId}")
    fun getUser(@PathVariable userId: UUID): ApiResponse<UserResponseData> {
        return ApiResponse.success(adminService.getUser(userId))
    }

    @PatchMapping("/users/{userId}/status")
    fun updateUserStatus(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable userId: UUID,
        @Valid @RequestBody request: UpdateUserStatusRequest
    ): ApiResponse<UserResponseData> {
        return ApiResponse.success(adminService.updateUserStatus(principal.id, userId, request), 200, "User status updated")
    }

    @DeleteMapping("/users/{userId}")
    fun deleteUser(@AuthenticationPrincipal principal: UserPrincipal, @PathVariable userId: UUID): ApiResponse<Unit> {
        adminService.softDeleteUser(principal.id, userId)
        return ApiResponse.success(Unit, 200, "User soft-deleted")
    }

    @GetMapping("/audit-logs")
    fun getAuditLogs(
        @RequestParam(required = false) userId: UUID?,
        @RequestParam(required = false) eventType: String?,
        @RequestParam(required = false) startDate: String?,
        @RequestParam(required = false) endDate: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int
    ): ApiResponse<PaginatedData<AuditLogResponseData>> {
        return ApiResponse.success(adminService.getAuditLogs(userId, eventType, startDate, endDate, page, pageSize))
    }

    @GetMapping("/fraud-cases")
    fun listFraudCases(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int
    ): ApiResponse<PaginatedData<FraudCaseResponseData>> {
        return ApiResponse.success(adminService.listFraudCases(page, pageSize))
    }

    @PatchMapping("/fraud-cases/{caseId}")
    fun updateFraudCase(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable caseId: UUID,
        @Valid @RequestBody request: UpdateFraudCaseRequest
    ): ApiResponse<FraudCaseResponseData> {
        return ApiResponse.success(adminService.updateFraudCase(principal.id, caseId, request), 200, "Fraud case updated")
    }

    @PostMapping("/fraud-cases/{caseId}/resolve")
    fun resolveFraudCase(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable caseId: UUID,
        @RequestBody(required = false) request: ResolveFraudCaseRequest?
    ): ApiResponse<FraudCaseResponseData> {
        return ApiResponse.success(
            adminService.resolveFraudCase(principal.id, caseId, request ?: ResolveFraudCaseRequest()),
            200, "Fraud case resolved"
        )
    }
}
