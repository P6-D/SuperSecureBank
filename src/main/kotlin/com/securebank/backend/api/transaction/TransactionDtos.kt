package com.securebank.backend.api.transaction

import com.securebank.backend.common.AmountFormat
import com.securebank.backend.domain.entity.Transaction
import jakarta.validation.constraints.NotBlank
import java.time.format.DateTimeFormatter

data class TransferRequest(
    @field:NotBlank val sourceAccountId: String,
    val destinationAccountId: String? = null,
    val destinationAccountNumber: String? = null,
    @field:NotBlank val amount: String,
    val currency: String = "USD",
    val description: String? = null,
    @field:NotBlank val type: String,
    @field:NotBlank val idempotencyKey: String,
    val bankCode: String? = null
)

data class QrGenerateRequest(
    @field:NotBlank val accountId: String,
    @field:NotBlank val amount: String,
    val description: String? = null
)

data class QrProcessRequest(
    @field:NotBlank val qrToken: String,
    @field:NotBlank val sourceAccountId: String
)

data class FraudReportRequest(
    @field:NotBlank val transactionId: String,
    @field:NotBlank val fraudType: String,
    @field:NotBlank val description: String
)

data class QrTokenResponseData(
    val qrToken: String,
    val expiresAt: String,
    val amount: String
)

data class TransactionResponseData(
    val id: String,
    val idempotencyKey: String,
    val sourceAccountId: String,
    val destinationAccountId: String?,
    val destinationAccountNumber: String?,
    val type: String,
    val amount: String,
    val currency: String,
    val status: String,
    val reference: String,
    val description: String?,
    val anomalyScore: Float?,
    val initiatedBy: String,
    val createdAt: String,
    val settledAt: String?,
    val cancelledAt: String?
) {
    companion object {
        fun from(t: Transaction) = TransactionResponseData(
            id = t.id.toString(),
            idempotencyKey = t.idempotencyKey,
            sourceAccountId = t.sourceAccountId.toString(),
            destinationAccountId = t.destinationAccountId?.toString(),
            destinationAccountNumber = t.destinationAccountNumber,
            type = t.type.name,
            amount = AmountFormat.toWire(t.amount),
            currency = t.currency,
            status = t.status.name,
            reference = t.reference,
            description = t.description,
            anomalyScore = t.anomalyScore,
            initiatedBy = t.initiatedBy.name,
            createdAt = DateTimeFormatter.ISO_INSTANT.format(t.createdAt),
            settledAt = t.settledAt?.let { DateTimeFormatter.ISO_INSTANT.format(it) },
            cancelledAt = t.cancelledAt?.let { DateTimeFormatter.ISO_INSTANT.format(it) }
        )
    }
}
