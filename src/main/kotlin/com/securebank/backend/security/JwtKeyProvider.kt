package com.securebank.backend.security

import org.springframework.stereotype.Component
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

/**
 * FR-BE-003 / FRD 11.2: Token Algorithm RS256 (asymmetric JWT signing).
 * For the local/testbed runtime a fresh RSA keypair is generated at application
 * startup (in-memory, not persisted) — sufficient for the H2/no-external-deps
 * scope of this backend. Restarting the server invalidates all outstanding
 * tokens, which is acceptable for a local testbed.
 */
@Component
class JwtKeyProvider {
    private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    val privateKey: RSAPrivateKey get() = keyPair.private as RSAPrivateKey
    val publicKey: RSAPublicKey get() = keyPair.public as RSAPublicKey
}
