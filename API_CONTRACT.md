# SecureBank Backend — API Contract

> **Base URL:** `/api/v1`  
> **Content-Type:** `application/json`  
> **Naming convention:** All JSON field names are **snake_case** (Jackson `SNAKE_CASE` strategy).  
> **Authentication:** Bearer JWT in `Authorization` header (unless marked 🔓 Public).

---

## Table of Contents

1. [Global Response Envelope](#1-global-response-envelope)
2. [Error Codes](#2-error-codes)
3. [Pagination Envelope](#3-pagination-envelope)
4. [Enum Reference](#4-enum-reference)
5. [Auth](#5-auth)
6. [Accounts](#6-accounts)
7. [Transactions](#7-transactions)
8. [Fraud Reports](#8-fraud-reports)
9. [Cards](#9-cards)
10. [Notifications](#10-notifications)
11. [Admin](#11-admin)
12. [Security Control Panel](#12-security-control-panel)
13. [Actuator (Health)](#13-actuator-health)

---

## 1. Global Response Envelope

Every response follows this shape:

```json
{
  "status": "success" | "error",
  "code": 200,
  "message": "OK",
  "data": { ... } | null,
  "metadata": {
    "request_id": "uuid",
    "timestamp": "2026-09-21T09:00:00Z",
    "version": "1.0.0"
  }
}
```

| Field       | Type                | Description                               |
|-------------|---------------------|-------------------------------------------|
| `status`    | `string`            | `"success"` or `"error"`                  |
| `code`      | `integer`           | HTTP status code                          |
| `message`   | `string`            | Human-readable message                    |
| `data`      | `object` / `null`   | Response payload (null on errors)         |
| `metadata`  | `object` / `null`   | Request correlation & versioning          |

---

## 2. Error Codes

| Code | Constant            | HTTP Status                |
|------|----------------------|----------------------------|
| 400  | `INVALID_REQUEST`    | Bad Request                |
| 401  | `UNAUTHORIZED`       | Unauthorized               |
| 403  | `FORBIDDEN`          | Forbidden                  |
| 404  | `NOT_FOUND`          | Not Found                  |
| 409  | `CONFLICT`           | Conflict                   |
| 422  | `UNPROCESSABLE`      | Unprocessable Entity       |
| 429  | `RATE_LIMITED`       | Too Many Requests          |
| 451  | `SECURITY_BLOCKED`   | Unavailable For Legal Reasons |
| 500  | `INTERNAL_ERROR`     | Internal Server Error      |
| 503  | `SERVICE_UNAVAILABLE`| Service Unavailable        |

**Validation errors** (422) join field messages with `; `, e.g. `"email: must not be blank; password: must not be blank"`.

---

## 3. Pagination Envelope

Used by list endpoints that return `PaginatedData<T>`:

```json
{
  "items": [ ... ],
  "page": 1,
  "page_size": 20,
  "total_count": 42,
  "total_pages": 3
}
```

---

## 4. Enum Reference

| Category            | Values                                                                 |
|---------------------|------------------------------------------------------------------------|
| User Status         | `PENDING_VERIFICATION` · `ACTIVE` · `LOCKED` · `SUSPENDED` · `CLOSED` |
| User Role           | `USER` · `ADMIN`                                                       |
| Account Type        | `CHECKING` · `SAVINGS` · `INVESTMENT`                                  |
| Account Status      | `ACTIVE` · `FROZEN` · `CLOSED`                                        |
| Transaction Type    | `TRANSFER_INTERNAL` · `TRANSFER_EXTERNAL` · `QR_PAYMENT` · `FEE` · `REVERSAL` |
| Transaction Status  | `PENDING` · `PROCESSING` · `COMPLETED` · `FAILED` · `CANCELLED` · `REVERSED`  |
| Initiated By        | `USER` · `SYSTEM` · `ADMIN`                                           |
| Card Type           | `DEBIT` · `CREDIT`                                                     |
| Card Status         | `ACTIVE` · `FROZEN` · `CANCELLED` · `EXPIRED`                         |
| Fraud Type          | `UNAUTHORIZED` · `WRONG_AMOUNT` · `DUPLICATE` · `PHISHING` · `OTHER`  |
| Fraud Case Status   | `OPEN` · `UNDER_REVIEW` · `RESOLVED` · `DISMISSED`                    |
| Notification Type   | `TRANSACTION` · `SECURITY` · `ACCOUNT` · `PROMOTION` · `SYSTEM`       |
| Audit Result        | `SUCCESS` · `FAILURE` · `BLOCKED`                                      |

---

## 5. Auth

All auth endpoints are 🔓 **Public** (no Bearer token required) unless noted.

### 5.1 Register
`POST /api/v1/auth/register`

| Field | Type | Required | Description |
|---|---|---|---|
| `full_name` | `string` | ✅ | |
| `email` | `string` | ✅ | |
| `phone` | `string` | ✅ | |
| `national_id` | `string` | ✅ | |
| `password` | `string` | ✅ | Min 12 chars, upper+lower+digit+symbol |
| `device_fingerprint` | `string` | ❌ | |

**Response** `201`: `{"user_id": "uuid", "verification_token": "string"}`

### 5.2 Verify OTP
`POST /api/v1/auth/verify`
- **Body**: `email` (req), `otp` (req, 6-digit)
- **Response** `200`: `data` is `null`

### 5.3 Login
`POST /api/v1/auth/login`

| Field | Type | Required | Description |
|---|---|---|---|
| `email` | `string` | ✅ | |
| `password` | `string` | ✅ | |
| `device_fingerprint` | `string` | ❌ | |
| `device_name` | `string` | ❌ | e.g. "Pixel 7" |
| `device_os` | `string` | ❌ | e.g. "Android" |
| `device_os_version` | `string` | ❌ | e.g. "14" |
| `app_version` | `string` | ❌ | e.g. "1.0.0" |

**Response** `200`:
```json
{
  "access_token": "jwt",
  "refresh_token": "jwt",
  "expires_in": 900,
  "mfa_required": false,
  "mfa_token": null,
  "user": { /* UserResponseData */ }
}
```
*(If `mfa_required` is true, access/refresh tokens are empty and you must use `mfa_token` with `/mfa/verify`)*

### 5.4 Refresh Token
`POST /api/v1/auth/refresh`
- **Body**: `refresh_token` (req)
- **Response** `200`: Same as Login response

### 5.5 Logout
`POST /api/v1/auth/logout` (🔒 **Authenticated**)
- **Body**: `refresh_token` (opt)
- **Response** `200`: `data` is `null`

### 5.6 Enroll MFA
`POST /api/v1/auth/mfa/enroll` (🔒 **Authenticated**)
- **Body**: `password` (req)
- **Response** `200`: `{"secret": "base32...", "qr_code_uri": "otpauth..."}`

### 5.7 Verify MFA
`POST /api/v1/auth/mfa/verify`
- **Body**: `token` (req, `mfa_token` from login), `mfa_code` (req, 6-digit TOTP)
- **Response** `200`: Same as Login response

### 5.8 Disable MFA
`POST /api/v1/auth/mfa/disable` (🔒 **Authenticated**)
- **Body**: `password` (req)
- **Response** `200`: `data` is `null`

### 5.9 List Device Sessions
`GET /api/v1/auth/devices` (🔒 **Authenticated**)
- **Response** `200`: Array of Device Session objects (id, device_fingerprint, is_trusted, etc.)

### 5.10 Revoke Device Session
`DELETE /api/v1/auth/devices/{deviceId}` (🔒 **Authenticated**)

---

## 6. Accounts

All endpoints are 🔒 **Authenticated**.

### 6.1 Create Account
`POST /api/v1/accounts` (🔒 **Admin only**)
- **Body**: `user_id` (req), `account_type` (req), `currency` (opt, def "USD"), `initial_balance` (opt, def "0.00")
- **Response** `201`: Account Response Data

### 6.2 List Accounts
`GET /api/v1/accounts`
- **Response** `200`: Array of Account Response Data

### 6.3 Get Account
`GET /api/v1/accounts/{accountId}`
- **Response** `200`: Account Response Data

### 6.4 Get Balance
`GET /api/v1/accounts/{accountId}/balance`
- **Response** `200`: Account Response Data

### 6.5 Get Statement (Transactions)
`GET /api/v1/accounts/{accountId}/transactions`
- **Query Params**: `start_date`, `end_date`, `page` (def 1), `page_size` (def 20)
- **Response** `200`: `PaginatedData<TransactionResponseData>`

> **Amounts**: Represented as decimal strings with 4-digit scale (e.g. `"10000.0000"`).

---

## 7. Transactions

All endpoints are 🔒 **Authenticated**.

### 7.1 Transfer
`POST /api/v1/transactions/transfer`
- **Headers**: `Idempotency-Key` (req), `X-Step-Up-Code` (opt, if amount > threshold)
- **Body**: `source_account_id` (req), `destination_account_id` (opt), `destination_account_number` (opt), `amount` (req), `type` (req), `idempotency_key` (req), `currency` (opt), `description` (opt)
- **Response** `201`: Transaction Response Data

### 7.2 Get Transaction
`GET /api/v1/transactions/{transactionId}`
- **Response** `200`: Transaction Response Data

### 7.3 Search Transactions
`GET /api/v1/transactions`
- **Query Params**: `account_id`, `type`, `status`, `start_date`, `end_date`, `page`, `page_size`
- **Response** `200`: `PaginatedData<TransactionResponseData>`

### 7.4 Cancel Transaction
`DELETE /api/v1/transactions/{transactionId}`
- **Response** `200`: Transaction Response Data (status `CANCELLED`)

### 7.5 Generate QR Token
`POST /api/v1/transactions/qr/generate`
- **Body**: `account_id` (req), `amount` (req), `description` (opt)
- **Response** `201`: `{"qr_token": "str", "expires_at": "timestamp", "amount": "str"}`

### 7.6 Process QR Payment
`POST /api/v1/transactions/qr/process`
- **Body**: `qr_token` (req), `source_account_id` (req)
- **Response** `201`: Transaction Response Data

---

## 8. Fraud Reports
`POST /api/v1/fraud-reports` (🔒 **Authenticated**)
- **Body**: `transaction_id` (req), `fraud_type` (req), `description` (req)
- **Response** `201`: `data` is `null`

- **Response** `200`: `data` is `null`



---

## 9. Cards

All endpoints are 🔒 **Authenticated**.

### 9.1 List Cards
`GET /api/v1/cards`
- **Query Params**: `account_id` (opt)
- **Response** `200`: Array of Card Response Data

### 9.2 Issue Virtual Card
`POST /api/v1/cards/virtual`
- **Body**: `account_id` (req), `card_type` (req)
- **Response** `201`: Card Response Data

### 9.3 Freeze Card
`PATCH /api/v1/cards/{cardId}/freeze`
- **Response** `200`: Card Response Data (status `FROZEN`)

### 9.4 Unfreeze Card
`PATCH /api/v1/cards/{cardId}/unfreeze`
- **Response** `200`: Card Response Data (status `ACTIVE`)

### 9.5 Update Limits
`PATCH /api/v1/cards/{cardId}/limits`
- **Body**: `daily_limit` (req), `monthly_limit` (req)
- **Response** `200`: Card Response Data

---

## 10. Notifications

All endpoints are 🔒 **Authenticated**.

### 10.1 List Notifications
`GET /api/v1/notifications`
- **Query Params**: `page` (def 1), `page_size` (def 20), `unread_only` (def false)
- **Response** `200`: `PaginatedData<NotificationResponseData>`

### 10.2 Mark Read
`PATCH /api/v1/notifications/{notificationId}/read`
- **Response** `200`: `data` is `null`

### 10.3 Send Notification (Admin)
`POST /api/v1/notifications/send` (🔒 **Admin only**)
- **Body**: `user_id` (req), `title` (req), `message` (req), `type` (opt), `reference_id` (opt)
- **Response** `201`: `data` is `null`

---

## 11. Admin

All endpoints are 🔒 **Admin only** (`ROLE_ADMIN`).

### 11.1 List Users
`GET /api/v1/admin/users`
- **Response** `200`: `PaginatedData<UserResponseData>`

### 11.2 Get User
`GET /api/v1/admin/users/{userId}`
- **Response** `200`: User Response Data

### 11.3 Update User Status
`PATCH /api/v1/admin/users/{userId}/status`
- **Body**: `status` (req)
- **Response** `200`: User Response Data

### 11.4 Delete User
`DELETE /api/v1/admin/users/{userId}`
- **Response** `200`: `data` is `null`

### 11.5 Get Audit Logs
`GET /api/v1/admin/audit-logs`
- **Query Params**: `user_id`, `event_type`, `start_date`, `end_date`, `page`, `page_size`
- **Response** `200`: `PaginatedData<AuditLogResponseData>`

### 11.6 List Fraud Cases
`GET /api/v1/admin/fraud-cases`
- **Response** `200`: `PaginatedData<FraudCaseResponseData>`

### 11.7 Update Fraud Case
`PATCH /api/v1/admin/fraud-cases/{caseId}`
- **Body**: `status` (req), `resolution_note` (opt)
- **Response** `200`: Fraud Case Response Data

### 11.8 Resolve Fraud Case
`POST /api/v1/admin/fraud-cases/{caseId}/resolve`
- **Body**: `resolution_note` (opt)
- **Response** `200`: Fraud Case Response Data

---

## 12. Security Control Panel

All endpoints are 🔒 **Admin only** (`ROLE_ADMIN`). Testbed-specific (Red Team / Blue Team dashboard).

### 12.1 List Security Modules
`GET /api/v1/security/modules`
- **Response** `200`: Array of Security Module Response Data

### 12.2 Toggle Security Module
`PATCH /api/v1/security/modules/{moduleName}`
- **Body**: `enabled` (req), `config` (opt, key-value map)
- **Response** `200`: Security Module Response Data

### 12.3 Attack Simulation
`POST /api/v1/security/simulate`
- **Body**: `attack_type` (req), `target` (req)
- **Response** `200`: 
```json
{
  "simulation_id": "uuid",
  "attack_type": "string",
  "target": "string",
  "status": "string",
  "detected": true,
  "response_time_ms": 42,
  "triggered_module": "string"
}
```

### 12.4 Security Events
`GET /api/v1/security/events`
- **Query Params**: `limit` (def 50)
- **Response** `200`: Array of Security Event Data

### 12.5 Trigger Vulnerability Scan
`POST /api/v1/security/scan/trigger`
- **Body**: `target_url` (opt)
- **Response** `202`: `{"scan_id": "uuid", "status": "INITIATED", "message": "string"}`

---

## 13. Actuator (Health)

🔓 **Public.** Note: Lives outside `/api/v1` namespace.

`GET /actuator/health`
- **Response** `200`: Uses Spring Boot Actuator envelope (`status`, `components`), **not** the standard `ApiResponse` wrapper.

