package com.securebank.backend.common

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant
import java.util.UUID

/**
 * FRD Section 9.2: Global Response Format.
 * Field names are serialized snake_case via the application-wide Jackson
 * SNAKE_CASE naming strategy (see application.yml).
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
data class ApiResponse<T>(
    val status: String,
    val code: Int,
    val message: String,
    val data: T? = null,
    val metadata: ResponseMetadata? = ResponseMetadata()
) {
    companion object {
        fun <T> success(data: T?, code: Int = 200, message: String = "OK"): ApiResponse<T> =
            ApiResponse(status = "success", code = code, message = message, data = data)

        fun <T> error(code: Int, message: String): ApiResponse<T> =
            ApiResponse(status = "error", code = code, message = message, data = null)
    }
}

data class ResponseMetadata(
    val requestId: String = UUID.randomUUID().toString(),
    val timestamp: String = Instant.now().toString(),
    val version: String = "1.0.0"
)

/**
 * Section 5.1 (README) / paginated `data` envelope used by list endpoints.
 */
data class PaginatedData<T>(
    val items: List<T>,
    val page: Int,
    val pageSize: Int,
    val totalCount: Long,
    val totalPages: Int
)
