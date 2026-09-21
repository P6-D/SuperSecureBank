package com.securebank.backend.common

import org.springframework.http.HttpStatus

/**
 * FRD Section 9.3: Error Codes.
 */
enum class ApiErrorCode(val httpStatus: HttpStatus) {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    CONFLICT(HttpStatus.CONFLICT),
    UNPROCESSABLE(HttpStatus.UNPROCESSABLE_ENTITY),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    SECURITY_BLOCKED(HttpStatus.valueOf(451)),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE)
}

class ApiException(
    val errorCode: ApiErrorCode,
    override val message: String
) : RuntimeException(message)

fun notFound(message: String): Nothing = throw ApiException(ApiErrorCode.NOT_FOUND, message)
fun badRequest(message: String): Nothing = throw ApiException(ApiErrorCode.INVALID_REQUEST, message)
fun unauthorized(message: String): Nothing = throw ApiException(ApiErrorCode.UNAUTHORIZED, message)
fun forbidden(message: String): Nothing = throw ApiException(ApiErrorCode.FORBIDDEN, message)
fun conflict(message: String): Nothing = throw ApiException(ApiErrorCode.CONFLICT, message)
fun unprocessable(message: String): Nothing = throw ApiException(ApiErrorCode.UNPROCESSABLE, message)
fun rateLimited(message: String): Nothing = throw ApiException(ApiErrorCode.RATE_LIMITED, message)
fun securityBlocked(message: String): Nothing = throw ApiException(ApiErrorCode.SECURITY_BLOCKED, message)
