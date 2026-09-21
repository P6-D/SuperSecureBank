package com.securebank.backend.security

import com.securebank.backend.config.SecureBankProperties
import org.springframework.stereotype.Component
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * FR-BE-012 / FRD Section 9.1: "Signed request body (HMAC-SHA256 from mobile
 * client)". Server-side counterpart of Android's `RequestSigner.sign()`
 * (`payload = "<body>|<timestamp>"`, HMAC-SHA256, lowercase hex digest).
 *
 * NOTE (see README-ANDROID §6 "HMAC request signing"): the Android
 * interceptor does not yet call `RequestSigner.sign()` / send `X-Signature`,
 * and its key material is device-local (Keystore-derived), not a value the
 * server could ever share — so today this validator can only check against
 * `securebank.security.request-signing-secret` as a shared-secret stand-in
 * for the eventual proper key-exchange design. Enforcement is therefore
 * gated behind `securebank.security.enforce-request-signing` (default
 * false) so the app keeps working out-of-box; flip it on once client/server
 * agree on a real shared signing key.
 */
@Component
class RequestSignatureValidator(private val props: SecureBankProperties) {

    fun isValid(rawBody: String, timestampHeader: String?, signatureHeader: String?): Boolean {
        if (timestampHeader.isNullOrBlank() || signatureHeader.isNullOrBlank()) return false
        val timestamp = timestampHeader.toLongOrNull() ?: return false
        val now = Instant.now().epochSecond
        val toleranceSeconds = props.security.requestSignatureToleranceSeconds
        if (Math.abs(now - timestamp / if (timestamp > 10_000_000_000L) 1000 else 1) > toleranceSeconds) return false

        val expected = sign(rawBody, timestampHeader)
        return constantTimeEquals(expected, signatureHeader.lowercase())
    }

    private fun sign(body: String, timestamp: String): String {
        val payload = "$body|$timestamp"
        val mac = Mac.getInstance("HmacSHA256")
        val keyBytes = props.security.requestSigningSecret.toByteArray(Charsets.UTF_8)
        mac.init(SecretKeySpec(keyBytes, "HmacSHA256"))
        return mac.doFinal(payload.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].code xor b[i].code)
        return result == 0
    }
}
