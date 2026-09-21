package com.securebank.backend.cache

import com.securebank.backend.config.SecureBankProperties
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory stand-ins for the Redis-backed stores described in the FRD
 * (OTP store FR-BE-001/002, refresh/access deny-lists, QR single-use
 * consumption FR-BE-016/017). Scope for this backend is explicitly H2 +
 * in-memory cache, no external Redis dependency — acceptable for the
 * single-node local testbed; entries expire lazily on access.
 */
@Component
class OtpStore(private val props: SecureBankProperties) {
    private data class Entry(val otp: String, val expiresAt: Instant)
    private val store = ConcurrentHashMap<String, Entry>()

    fun put(key: String, otp: String) {
        store[key.lowercase()] = Entry(otp, Instant.now().plusSeconds(props.otp.ttlSeconds))
    }

    fun verify(key: String, otp: String): Boolean {
        val entry = store[key.lowercase()] ?: return false
        if (Instant.now().isAfter(entry.expiresAt)) {
            store.remove(key.lowercase())
            return false
        }
        val matches = entry.otp == otp
        if (matches) store.remove(key.lowercase()) // FR-BE-002: OTP consumed and deleted after use
        return matches
    }
}

/**
 * FR-BE-016/017: single-use QR token tracking. The signed JWT already carries
 * amount/account/expiry; this store only needs to remember which jti values
 * have been consumed (ATK-011 QR token replay defense).
 */
@Component
class QrTokenConsumptionStore {
    private val consumed = ConcurrentHashMap.newKeySet<String>()

    /** Returns true if this is the first time the token is being consumed. */
    fun tryConsume(jti: String): Boolean = consumed.add(jti)
}

/**
 * FR-BE-012 in-flight lock keyed by idempotency key, to close the narrow race
 * window between "check DB for existing key" and "insert new transaction"
 * (ATK-007 Race Condition / Double Spend defense) ahead of the DB unique
 * constraint on `idempotency_key` acting as the final backstop.
 */
@Component
class IdempotencyLockStore {
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    fun tryLock(key: String): Boolean = inFlight.add(key)
    fun unlock(key: String) {
        inFlight.remove(key)
    }
}

/**
 * FR-BE-003: tracks failed login attempts per account for brute-force lockout,
 * independent from the persisted `User.failedLoginAttempts` counter used for
 * the actual lock decision (kept here only for fast per-IP/device velocity
 * checks referenced by MOD-003 Brute Force Protection / MOD-013 Velocity
 * Check).
 */
@Component
class LoginAttemptStore {
    private data class Attempts(var count: Int, var windowStart: Instant)
    private val perIp = ConcurrentHashMap<String, Attempts>()

    fun recordAttempt(ip: String): Int {
        val now = Instant.now()
        val attempts = perIp.compute(ip) { _, existing ->
            if (existing == null || now.isAfter(existing.windowStart.plusSeconds(3600))) {
                Attempts(1, now)
            } else {
                existing.count += 1
                existing
            }
        }
        return attempts?.count ?: 1
    }
}


