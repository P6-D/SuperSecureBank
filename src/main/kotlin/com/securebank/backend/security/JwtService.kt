package com.securebank.backend.security

import com.securebank.backend.config.SecureBankProperties
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class TokenType { ACCESS, REFRESH, MFA, QR }

data class DecodedToken(
    val userId: UUID,
    val type: TokenType,
    val jti: String,
    val expiresAt: Instant,
    val extra: Map<String, Any?> = emptyMap()
)

/**
 * FR-BE-003/005/006: RS256 JWT issuance, refresh-token rotation, and access
 * token deny-list (in-memory stand-in for the Redis deny-list described in
 * FR-BE-006, acceptable for the single-node local testbed).
 */
@Service
class JwtService(
    private val keyProvider: JwtKeyProvider,
    private val props: SecureBankProperties
) {
    private val deniedJti = ConcurrentHashMap.newKeySet<String>()
    private val revokedRefreshJti = ConcurrentHashMap.newKeySet<String>()

    fun issueAccessToken(userId: UUID): String =
        buildToken(userId, TokenType.ACCESS, props.jwt.accessTokenTtlSeconds)

    fun issueRefreshToken(userId: UUID): String =
        buildToken(userId, TokenType.REFRESH, props.jwt.refreshTokenTtlSeconds)

    fun issueMfaToken(userId: UUID): String =
        buildToken(userId, TokenType.MFA, props.jwt.mfaTokenTtlSeconds)

    fun issueQrToken(userId: UUID, accountId: UUID, amount: String, description: String?): String =
        buildToken(
            userId, TokenType.QR, props.security.qrTokenTtlSeconds,
            extraClaims = mapOf("account_id" to accountId.toString(), "amount" to amount, "description" to description)
        )

    private fun buildToken(
        userId: UUID,
        type: TokenType,
        ttlSeconds: Long,
        extraClaims: Map<String, Any?> = emptyMap()
    ): String {
        val now = Instant.now()
        val jti = UUID.randomUUID().toString()
        val builder = Jwts.builder()
            .subject(userId.toString())
            .id(jti)
            .claim("type", type.name)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(ttlSeconds)))
            .signWith(keyProvider.privateKey, Jwts.SIG.RS256)
        extraClaims.forEach { (k, v) -> if (v != null) builder.claim(k, v) }
        return builder.compact()
    }

    fun decode(token: String, expectedType: TokenType): DecodedToken {
        val claims: Claims = try {
            Jwts.parser().verifyWith(keyProvider.publicKey).build()
                .parseSignedClaims(token).payload
        } catch (e: ExpiredJwtException) {
            throw JwtException("Token expired")
        } catch (e: Exception) {
            throw JwtException("Invalid token")
        }
        val jti = claims.id ?: throw JwtException("Missing jti")
        val type = claims["type"] as? String ?: throw JwtException("Missing token type")
        if (TokenType.valueOf(type) != expectedType) throw JwtException("Unexpected token type")
        if (expectedType == TokenType.ACCESS && jti in deniedJti) throw JwtException("Token revoked")
        if (expectedType == TokenType.REFRESH && jti in revokedRefreshJti) throw JwtException("Refresh token revoked")
        return DecodedToken(
            userId = UUID.fromString(claims.subject),
            type = expectedType,
            jti = jti,
            expiresAt = claims.expiration.toInstant(),
            extra = claims.filterKeys { it !in setOf("type", "sub", "jti", "iat", "exp") }
        )
    }

    /** FR-BE-006: deny-list access token until natural expiry. */
    fun revokeAccessToken(jti: String) {
        deniedJti.add(jti)
    }

    /** FR-BE-005: rotate — old refresh token invalidated immediately. */
    fun revokeRefreshToken(jti: String) {
        revokedRefreshJti.add(jti)
    }
}
