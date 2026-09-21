package com.securebank.backend.common

/**
 * FR-BE-001: "Validate input (email format, password strength, phone format)".
 * README-ANDROID §6: client-side policy is min 12 chars + upper/lower/digit/symbol;
 * backend must independently re-validate, not trust the client.
 */
object PasswordPolicy {
    private const val MIN_LENGTH = 12
    private val UPPER = Regex("[A-Z]")
    private val LOWER = Regex("[a-z]")
    private val DIGIT = Regex("[0-9]")
    private val SYMBOL = Regex("[^A-Za-z0-9]")

    fun validate(password: String) {
        val problems = mutableListOf<String>()
        if (password.length < MIN_LENGTH) problems.add("at least $MIN_LENGTH characters")
        if (!UPPER.containsMatchIn(password)) problems.add("an uppercase letter")
        if (!LOWER.containsMatchIn(password)) problems.add("a lowercase letter")
        if (!DIGIT.containsMatchIn(password)) problems.add("a digit")
        if (!SYMBOL.containsMatchIn(password)) problems.add("a symbol")
        if (problems.isNotEmpty()) {
            badRequest("Password must contain " + problems.joinToString(", "))
        }
    }
}

object EmailValidator {
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    fun isValid(email: String): Boolean = EMAIL_REGEX.matches(email)
}

object PhoneValidator {
    private val PHONE_REGEX = Regex("^\\+?[0-9]{8,15}$")
    fun isValid(phone: String): Boolean = PHONE_REGEX.matches(phone)
}
