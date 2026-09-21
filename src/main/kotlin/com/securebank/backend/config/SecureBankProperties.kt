package com.securebank.backend.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.math.BigDecimal

@ConfigurationProperties(prefix = "securebank")
data class SecureBankProperties(
    val jwt: Jwt = Jwt(),
    val otp: Otp = Otp(),
    val security: Security = Security(),
    val api: Api = Api()
) {
    data class Jwt(
        val accessTokenTtlSeconds: Long = 900,
        val refreshTokenTtlSeconds: Long = 604800,
        val mfaTokenTtlSeconds: Long = 300
    )

    data class Otp(
        val ttlSeconds: Long = 600,
        val length: Int = 6
    )

    data class Security(
        val bcryptStrength: Int = 12,
        val maxFailedLoginAttempts: Int = 5,
        val requestSigningSecret: String = "",
        /**
         * FR-BE-012 / Section 9.1 requires HMAC-signed request bodies, but the
         * Android RequestSigner is not yet wired into the OkHttp interceptor
         * (see README §6 "HMAC request signing"). Signature validation logic
         * is fully implemented server-side (RequestSignatureValidator) but
         * enforcement is OFF by default so the app keeps working out-of-box;
         * flip to true once the client sends X-Signature on every request.
         */
        val enforceRequestSigning: Boolean = false,
        val requestSignatureToleranceSeconds: Long = 300,
        val stepUpTransferThreshold: BigDecimal = BigDecimal("1000.00"),
        val statementMaxRangeMonths: Long = 12,
        val cancelWindowMinutes: Long = 10,
        val qrTokenTtlSeconds: Long = 60
    )

    data class Api(
        val version: String = "1.0.0",
        val basePath: String = "/api/v1"
    )
}
