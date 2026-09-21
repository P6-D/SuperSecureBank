package com.securebank.backend.service

import com.securebank.backend.common.ApiErrorCode
import com.securebank.backend.common.ApiException
import com.securebank.backend.common.PaginatedData
import com.securebank.backend.domain.entity.Notification
import com.securebank.backend.domain.entity.NotificationType
import com.securebank.backend.repository.NotificationRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.format.DateTimeFormatter
import java.util.UUID

data class NotificationResponseData(
    val id: String,
    val title: String,
    val message: String,
    val type: String,
    val isRead: Boolean,
    val referenceId: String?,
    val createdAt: String
) {
    companion object {
        fun from(n: Notification) = NotificationResponseData(
            id = n.id.toString(), title = n.title, message = n.message, type = n.type.name,
            isRead = n.isRead, referenceId = n.referenceId,
            createdAt = DateTimeFormatter.ISO_INSTANT.format(n.createdAt)
        )
    }
}

/**
 * FR-BE-018/019/020. `notify()` is the internal service-to-service hook (§7.4
 * FR-BE-018): callers throughout the app (Auth/Transaction/Card services)
 * push in-app notifications directly rather than via a queued FCM call,
 * since this backend has no external push provider configured (testbed
 * scope). FR-BE-018's "never include full account/card number or full
 * amount in payload" is honoured by every call site.
 */
@Service
class NotificationService(private val notificationRepository: NotificationRepository) {

    fun notify(userId: UUID, title: String, message: String, type: NotificationType, referenceId: String?) {
        notificationRepository.save(
            Notification(userId = userId, title = title, message = message, type = type, referenceId = referenceId)
        )
    }

    fun list(userId: UUID, page: Int, pageSize: Int, unreadOnly: Boolean): PaginatedData<NotificationResponseData> {
        val pageable = PageRequest.of((page - 1).coerceAtLeast(0), pageSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        val result = if (unreadOnly) {
            notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId, pageable)
        } else {
            notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
        }
        return PaginatedData(
            items = result.content.map { NotificationResponseData.from(it) },
            page = page, pageSize = pageSize,
            totalCount = result.totalElements, totalPages = result.totalPages
        )
    }

    @Transactional
    fun markRead(userId: UUID, notificationId: UUID) {
        val notification = notificationRepository.findById(notificationId)
            .orElseThrow { ApiException(ApiErrorCode.NOT_FOUND, "Notification not found") }
        if (notification.userId != userId) throw ApiException(ApiErrorCode.FORBIDDEN, "Not your notification")
        notification.isRead = true
        notificationRepository.save(notification)
    }
}
