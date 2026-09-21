package com.securebank.backend.security

import com.securebank.backend.config.SecureBankProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper

/**
 * FR-BE-012: enforces HMAC-signed bodies (X-Signature/X-Timestamp) for
 * mutating requests once `securebank.security.enforce-request-signing=true`.
 * Disabled by default — see RequestSignatureValidator kdoc.
 */
@Component
class RequestSignatureFilter(
    private val props: SecureBankProperties,
    private val validator: RequestSignatureValidator
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !props.security.enforceRequestSigning || request.method !in MUTATING_METHODS

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val wrapped = ContentCachingRequestWrapper(request)
        // Force body caching by reading it before downstream handlers do.
        wrapped.inputStream.readBytes()
        val body = String(wrapped.contentAsByteArray, Charsets.UTF_8)
        val timestamp = request.getHeader("X-Timestamp")
        val signature = request.getHeader("X-Signature")

        if (!validator.isValid(body, timestamp, signature)) {
            response.status = 400
            response.contentType = "application/json"
            response.writer.write(
                """{"status":"error","code":400,"message":"Invalid or missing request signature","data":null}"""
            )
            return
        }
        filterChain.doFilter(wrapped, response)
    }

    companion object {
        private val MUTATING_METHODS = setOf("POST", "PUT", "PATCH", "DELETE")
    }
}
