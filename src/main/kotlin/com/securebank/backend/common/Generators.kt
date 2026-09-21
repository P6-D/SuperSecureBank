package com.securebank.backend.common

import java.math.BigDecimal
import java.math.RoundingMode
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

object AmountFormat {
    /** README §5: amounts serialized as decimal strings with 4-digit scale (matches decimal(18,4) columns). */
    fun toWire(amount: BigDecimal): String = amount.setScale(4, RoundingMode.HALF_UP).toPlainString()

    fun parse(value: String): BigDecimal = try {
        BigDecimal(value)
    } catch (e: NumberFormatException) {
        badRequest("Invalid amount format: $value")
    }
}

object AccountNumberGenerator {
    private val random = SecureRandom()
    fun generate(): String {
        val digits = (1..12).joinToString("") { random.nextInt(10).toString() }
        return "SB$digits"
    }
}

object CardNumberGenerator {
    private val random = SecureRandom()
    fun generate(): String {
        val digits = (1..16).joinToString("") { random.nextInt(10).toString() }
        return digits
    }

    fun generateCvv(): String = (100 + random.nextInt(900)).toString()
}

object OtpGenerator {
    private val random = SecureRandom()
    fun generate(length: Int): String = (1..length).joinToString("") { random.nextInt(10).toString() }
}

object ReferenceGenerator {
    fun generate(prefix: String = "TXN"): String =
        "$prefix-${Instant.now().epochSecond}-${UUID.randomUUID().toString().take(8).uppercase()}"
}
