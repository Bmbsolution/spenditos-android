# Android E2E sidecar

A local Node 20+ server for Android end-to-end testing. It has no npm dependencies and keeps sidecar data in memory. The only sample data is synthetic. It binds to `127.0.0.1` only.

## Run

Start the local backend harness in another terminal:

```sh
pnpm --filter @spenditos/functions run serve:e2e
```

Then start the sidecar from the Android repository root:

```sh
node tools/e2e-sidecar/server.mjs
```

The sidecar listens on `http://127.0.0.1:4311`; it forwards routes it does not implement to `http://127.0.0.1:4310`.

Environment variables:

| Variable | Default | Use |
| --- | --- | --- |
| `SIDECAR_PORT` | `4311` | Sidecar listening port. |
| `HARNESS_URL` | `http://127.0.0.1:4310` | Local E2E harness URL. |
| `TOKEN_TTL_SECONDS` | `3600` | Access and ID token lifetime. Set to `20` to exercise refresh. |
| `QUOTA_EXHAUSTED` | unset | Set to `1` to make voice capture requests return the backend `QuotaExhausted` 403 response. Typed capture is unaffected. |

For a USB-connected Android device or emulator, forward its loopback port to the host:

```sh
adb reverse tcp:4311 tcp:4311
```

Set the app's debug API base URL to `http://127.0.0.1:4311/`. No other address is accepted by this server.

## Synthetic accounts

| Email | Password | Verification | Harness identity | Premium |
| --- | --- | --- | --- | --- |
| `demo@spenditos.test` | `Passw0rd!demo` | Already verified | `22222222-2222-4222-8222-222222222222` | Inactive |
| `pro@spenditos.test` | `Passw0rd!pro1` | Already verified | `22222222-2222-4222-8222-222222222222` | Active |

Both accounts proxy as the harness Home user. The sidecar keeps them distinct by the email claim in its own local JWT and patches `premium.active` in settings responses. New registrations receive verification code `123456`; forgot-password codes are also `123456`.

## Implemented behavior

- Auth: `POST /auth/login`, `/register`, `/confirm-registration`, `/resend-code`, `/forgot-password`, `/confirm-password`, `/refresh`, and `/logout`; plus `POST /user/change-password`. Successful responses use the API's `data` envelope. Common validation and auth failures retain the backend status and error codes.
- User settings: registered sidecar users receive 404 from `GET /user/settings` until a settings update is saved. Seeded users proxy settings to the harness, then receive the account-specific premium flag. Sidecar-created settings and categories are in memory.
- Categories: sidecar users get a small synthetic expense and income set. Seeded users proxy reads and receive in-memory additions, edits, and deletions overlaid on the harness list. Category suggestions from statement preview are names; import maps a matching name or ID to an existing category and creates no categories.
- Proxy: all other authenticated requests pass to the harness with `Authorization: Bearer e2e-<sub>`. The proxy streams request and response bytes, status, and headers through. The sidecar log records only method, path, and status.
- Uploads: `POST /transactions/upload-url` provides local form fields and `POST /__s3` stores uploaded bytes in memory. Submit every returned form field before the `file` part. `/__s3` is unauthenticated; an Authorization header is ignored and warned about.
- Statements: CSV analysis supports quoted RFC4180 fields, escaped quotes, CRLF, and UTF-8 BOM. Preview uses the selected mapping and calendar-date parsing without timezone conversion. It preserves signed amounts like the backend preview (expenses are negative, income positive); import sends absolute amounts to `/transactions/bulk`, along with `source: "import"` and stable `row-<index>` identifiers. An `Idempotency-Key` deduplicates replayed rows in sidecar memory and is forwarded to the harness. The current bulk handler schema rejects `source: "import"` (it accepts `manual`, `voice`, or `text`), so the sidecar retries a 400 validation response as `source: "manual"` to keep the imported rows visible in harness-backed transaction lists. XLSX is explicitly unsupported.
- Capture and receipts: capture uses deterministic canned transcription or a small typed-transcript parser. Analysis polling reports `processing` once, then `success`. Receipt analysis returns a synthetic receipt result.
- Admin: `GET /__sidecar/health`, `GET /__sidecar/state`, and `POST /__sidecar/reset` are local-only. Reset clears uploads, settings, categories, analyses, and import replay state, then restores the two seeded accounts.

The local upload store, users, verification codes, refresh tokens, categories, settings, analysis results, and import replay keys disappear when the server stops. This tool is not an authentication service and should only be used with synthetic test data on loopback.

## Self-test

Run the built-in Node test runner; it starts a tiny fake harness and the sidecar, then exercises both with `fetch`:

```sh
node --test tools/e2e-sidecar/test.mjs
```

The fake harness covers only `GET /categories`, `GET /user/settings`, and `POST /transactions/bulk`. It does not require DynamoDB Local or the functions workspace.
