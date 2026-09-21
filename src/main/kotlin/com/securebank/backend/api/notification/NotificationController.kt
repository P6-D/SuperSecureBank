package com.securebank.backend.api.notification

import com.securebank.backend.common.ApiResponse
import com.securebank.backend.common.PaginatedData
import com.securebank.backend.domain.entity.NotificationType
import com.securebank.backend.security.UserPrincipal
import com.securebank.backend.service.NotificationResponseData
import com.securebank.backend.service.NotificationService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class SendNotificationRequest(
    @field:NotBlank val userId: String,
    @field:NotBlank val title: String,
    @field:NotBlank val message: String,
    val type: String = "SYSTEM",
    val referenceId: String? = null
)

@RestController
@RequestMapping("\${securebank.api.base-path}/notifications")
class NotificationController(private val notificationService: NotificationService) {

    @GetMapping
    fun list(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int,
        @RequestParam(defaultValue = "false") unreadOnly: Boolean
    ): ApiResponse<PaginatedData<NotificationResponseData>> {
        return ApiResponse.success(notificationService.list(principal.id, page, pageSize, unreadOnly))
    }

    @PatchMapping("/{notificationId}/read")
    fun markRead(@AuthenticationPrincipal principal: UserPrincipal, @PathVariable notificationId: UUID): ApiResponse<Unit> {
        notificationService.markRead(principal.id, notificationId)
        return ApiResponse.success(Unit, 200, "Notification marked as read")
    }

    /** FR-BE-018: "internal service-to-service only" — restricted to ADMIN role as the closest RBAC equivalent. */
    @PostMapping("/send")
    @PreAuthorize("hasRole('ADMIN')")
    fun send(@Valid @RequestBody request: SendNotificationRequest): ApiResponse<Unit> {
        val type = try {
            NotificationType.valueOf(request.type.uppercase())
        } catch (e: IllegalArgumentException) {
            NotificationType.SYSTEM
        }
        notificationService.notify(UUID.fromString(request.userId), request.title, request.message, type, request.referenceId)
        return ApiResponse.success(Unit, 201, "Notification queued")
    }
}
