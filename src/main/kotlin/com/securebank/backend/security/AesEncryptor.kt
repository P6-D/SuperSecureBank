package com.securebank.backend.security

import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * FRD 11.2 "Data Encryption at Rest: AES-256-GCM". Used for MFA TOTP secret
 * encryption (FR-BE-004: "TOTP secret stored encrypted (AES-256) in DB") and
 * available for other PII-at-rest fields (e.g. national_id per §10.1).
 *
 * The AES key is generated once per process start (in-memory, testbed
 * scope — matches the RSA JWT keypair approach in JwtKeyProvider). Restarting
 * the server invalidates previously-encrypted values, acceptable for this
 * local testbed.
 */
@Component
class AesEncryptor {
    private val secretKey: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val random = SecureRandom()

    fun encrypt(plainText: String): String {
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = iv + cipherText
        return Base64.getEncoder().encodeToString(combined)
    }

    fun decrypt(encoded: String): String {
        val combined = Base64.getDecoder().decode(encoded)
        val iv = combined.copyOfRange(0, 12)
        val cipherText = combined.copyOfRange(12, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }
}
