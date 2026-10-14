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
- Scans (the harness has no scan worker): `POST /transactions/upload-url` with `documentType:"scan"` accepts images and csv/xls/xlsx (no PDF, 10 MiB) and returns the local `/__s3` form. `POST /scans` validates like the backend (1-60 documents, `kind` image|spreadsheet, `pdfGroupId` only on images) and returns 202; scans live in memory per account. `GET /scans/{id}` reports `processing` (progress advancing) on the first two reads and `ready` on the third; only `GET /scans/{id}` advances a scan, `GET /scans` never does. A single image whose `filename` contains "receipt" becomes a `receipt` scan with one item; anything else is a `list` scan with 12 deterministic rows over about two months (4x "Blue Bottle Coffee", a duplicate, a "PAYMENT – THANK YOU" payment, a transfer, income, a pending row, an undated row, and "NETFLIX.COM"). Your `source:"user"` rules apply at finalize: a skip rule matching Netflix gives `skipReason:"rule"` + `ruleId`; a categorize rule (for example "Blue Bottle") sets `categoryId` + `ruleId`. `GET /scans?status=` (default processing,ready), `POST /scans/{id}/complete` (idempotent; 409 `ScanNotReady` while processing). `POST /transactions/bulk` with `source:"scan"` and a sidecar `scanId` marks the scan `saved` and forwards the rest to the harness without `scanId` (unknown scan: 404 `ScanNotFound`; if the harness rejects `source:"scan"` it retries as `manual`). Other bulk requests are untouched.
- Scan credits: the sidecar reports no quota (the Pro account is never charged). `POST /__sidecar/scan-quota {"exhausted":true}` makes `POST /scans` return the flat 403 `QuotaExhausted` (`quotaType:"scanCredits"`) for the non-Pro demo account only; send `false` to clear it.
- Rules (`/settings/merchant-mappings`, in memory per harness identity): `action` categorize|skip, `source:"user"`, upsert by normalized `rawPattern` (201 created, 200 updated), partial `PUT` (409 `RuleExists` on a renamed-onto pattern), `DELETE` 204, `GET .../match-count?merchant=&categoryId=` (counts the harness user's last 24 months of transactions whose description contains the merchant, not already in the category, same income/expense side, no template; a fixed 3 if the harness cannot list transactions) and `POST .../{id}/apply` (recategorizes those through the harness `PUT /transactions/{id}`; 409 `SkipRuleCannotApply` for skip rules). The list also contains one `ai` mapping so clients can verify they show user rules only.
- `GET /user/export`: synthetic, un-enveloped `spenditos-export` JSON (rules come from the store above) with `Content-Disposition`, `Cache-Control: no-store`, and gzip (`Content-Encoding: gzip`) when `Accept-Encoding` contains gzip. `POST /__sidecar/export-fail {"fail":true}` makes it return 500 `ExportFailed`.
- Admin: `/__sidecar/reset` clears all in-memory state, including scans, rules, and the scan-quota and export-fail flags.
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
