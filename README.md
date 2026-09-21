# SecureBank Backend

Spring Boot (Kotlin) implementation of `FRD.md` FR-BE-001 through FR-BE-027,
matching the Android client contract documented in `README-ANDROID.md` §5
exactly (envelope shape, snake_case fields, decimal-as-string amounts, enum
value sets).

## Scope / stack

- Kotlin + Spring Boot 3.3 (Web, Security, Data JPA, Validation, Actuator)
- H2 in-memory database (no Postgres) — data resets on every restart
- In-memory caches (no Redis) for OTP store, JWT deny-lists, QR single-use
  tracking, idempotency locks — see `cache/InMemoryStores.kt`
- RS256 JWT (keypair generated fresh at process start — restarting the
  server invalidates all outstanding tokens; acceptable for this local
  testbed)
- bcrypt (cost 12) password hashing, AES-256-GCM for MFA secret encryption
  at rest (`security/AesEncryptor.kt`)
- TOTP MFA via `com.warrenstrange:googleauth`

## Requirements

- JDK 17 or 21. This machine only had a JDK 25 install (`Zulu`) which the
  Kotlin 2.0.21 compiler cannot parse; the bundled Android Studio JBR (JDK
  21, `C:\Program Files\Android\Android Studio\jbr`) is used instead. Set
  `JAVA_HOME` to a compatible JDK before running Gradle:
  ```
  $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
  ```

## Run

```
cd backend
.\gradlew.bat bootRun
```

Server listens on `http://localhost:8080`, base path `/api/v1` — matches
the Android `BuildConfig.API_BASE_URL` debug value
`http://10.0.2.2:8080/api/v1/` (emulator loopback to host).

On first boot, `DataSeeder` creates two accounts (H2 is in-memory, so this
re-seeds every restart):

| Role  | Email                      | Password       | Notes                         |
|-------|----------------------------|----------------|--------------------------------|
| ADMIN | admin@securebank.testbed   | Admin@12345!   | Access to `/admin/**`, `/security/**` |
| USER  | demo@securebank.testbed    | Demo@12345!    | Seeded CHECKING account, $5000 balance |

## Notable behavior / deviations from a full production design

- **Request signing (FR-BE-012 / §9.1 HMAC-SHA256):** validator is fully
  implemented (`security/RequestSignatureValidator.kt`,
  `RequestSignatureFilter.kt`) but disabled by default
  (`securebank.security.enforce-request-signing=false`) because the Android
  `RequestSigner` is not yet wired into the OkHttp interceptor (see
  README-ANDROID §6) and its key material is device-local — there is no
  shared secret the server could validate against yet. Flip the property
  once client/server agree on a real key-exchange design.
- **Step-up MFA (FR-BE-012):** enforced server-side independent of the
  client. Transfers ≥ `securebank.security.step-up-transfer-threshold`
  (default $1000) require a valid TOTP code in the `X-Step-Up-Code` header;
  the user must have MFA enrolled (`POST /auth/mfa/enroll`) first.
- **Security Event Stream (FR-BE-026):** FRD specifies SSE/WebSocket;
  `GET /security/events` instead returns a polled snapshot of the most
  recent audit events (no message-broker/emitter infra in this
  H2 + in-memory-cache-only scope).
- **Vulnerability Scanner Hook (FR-BE-027):** records the trigger request to
  the audit log and returns `QUEUED`; no real OWASP ZAP/Burp integration.
- **Notification delivery (FR-BE-018):** in-app only (DB-persisted,
  returned via `GET /notifications`); no FCM push integration configured.
- Admin/Security Control Panel endpoints (`/admin/**`, `/security/**`) have
  no Android client — gated by `hasRole('ADMIN')`, exercised via curl/Postman
  or future internal tooling per FRD §2.1.
