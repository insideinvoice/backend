# Public Invoice Sharing — Design, Rollout & Residual Risks

Status: implemented, unit/integration tested (79/79 green), E2E-smoked against a real
local server. Not yet deployed — deployment requires explicit authorization.

## 1. What shipped

### Database (V19__add_invoice_sharing.sql)
- `invoices.share_token VARCHAR(64)` — opaque share token, NULL = never shared
- `invoices.share_enabled BOOLEAN NOT NULL DEFAULT false`
- `invoices.share_created_at`, `invoices.share_revoked_at TIMESTAMP`
- Partial unique index `uq_invoices_share_token` — two invoices can never share a token
- Purely additive; no existing rows are touched. Safe to apply to a live database.

### Authenticated endpoints (JWT required, scoped by `businessId`)
| Method | Path | Behaviour |
|---|---|---|
| POST | `/api/invoices/{id}/share` | Idempotent create-or-retrieve. While a link is active, repeated calls return the same token (stable URL). After revoke, the next call mints a new token. |
| DELETE | `/api/invoices/{id}/share` | Revoke: token set to NULL, `share_enabled=false`, `share_revoked_at=now()`. The old token can never resolve again. |
| POST | `/api/invoices/{id}/share/regenerate` | Always mints a new token and invalidates the previous one. |
| POST | `/api/invoices/{id}/email` | Emails the invoice (branded HTML + GST breakup + share link) to the customer on file. Creates the link on demand; 400 if the customer has no email or the origin is invalid, 503 when mail is unconfigured, 502 on provider failure. |

- Ownership is enforced in SQL: `findByIdAndBusinessIdForUpdate` (PESSIMISTIC_WRITE), so
  concurrent creates serialize and converge on a single token; cross-tenant attempts get
  the same uniform 404 as unknown invoices (ID existence is never confirmed).
- Token: 32 bytes from `SecureRandom`, base64url, unpadded (43 chars).
- Token collisions: pre-check + unique index; a race surfaces as a generic 400 and the
  transaction rolls back (no partial writes).

### Anonymous endpoints (share token is the only credential)
| Path | Behaviour |
|---|---|
| `GET /api/public/invoices/{token}` | Allowlisted JSON (see §3). |
| `GET /api/public/invoices/{token}/pdf` | On-demand server-rendered A4 PDF, `application/pdf`, `Content-Disposition: inline`. |

- Every request (detail and PDF) re-validates the token against the database; the browser
  page is never trusted.
- Invalid, malformed, unknown and revoked tokens all return the byte-identical message
  `"Invoice not found"` (404) — a revoked link is indistinguishable from a wrong guess.
- Privacy headers on every `/api/public/**` response (200, 404, 429): `Cache-Control:
  no-store`, `Pragma: no-cache`, `Referrer-Policy: no-referrer`,
  `X-Content-Type-Options: nosniff`.
- Rate limiting (process-local): per-IP and per-token sliding 60s windows, defaults
  120/min and 60/min, configurable via `app.public.rate-limit.*`. 429 + `Retry-After: 60`
  with a generic body that never echoes the token. Key tables are size-capped with an
  overflow fallback window, so memory stays bounded under spoofed-IP churn.

### Security fixes on existing surfaces
1. **BOLA in payments (fixed)**: `GET /api/payments/by-invoice/{invoiceId}` never used
   `businessId` — any authenticated user could read another tenant's payment history by
   guessing invoice IDs. Now the invoice is ownership-checked (404) and payments are
   additionally filtered by `businessId`. Regression-tested.
2. **CORS narrowed**: `allowedOriginPatterns("*")` + `allowCredentials(true)` replaced by
   an explicit origin list (property `app.cors.allowed-origins`, defaults: insideinvoice.in,
   www.insideinvoice.in, insideinvoice.netlify.app, localhost:5173/4173). If the frontend is
   ever served from another origin, extend the property on Railway — do NOT go back to `*`.
3. **Actuator**: `/actuator/**` was fully anonymous; now only `/actuator/health/**` and
   `/actuator/info` are. `/actuator/metrics` and `/actuator/env` require authentication
   (verified: 401).
4. Invoice CRUD already used `findByIdAndBusinessId` everywhere → cross-tenant reads were
   uniform 404s; now covered by tests so they stay that way.

### Frontend
- Route `/i/:shareToken` (public, outside `PrivateRoute`) → `PublicInvoicePage.jsx`:
  loading / 404 ("link unavailable") / 429 / retry states, View PDF (browser viewer),
  Download (blob download), Print (blob → hidden iframe → print), template rendering via
  the existing `InvoiceTemplateRenderer` with an explicit field mapping (§3).
- `src/api/public.js`: dedicated axios instance — never attaches the JWT, has no
  force-logout interceptor, uses `referrerPolicy: "no-referrer"`.
- `InvoiceView`: Share Link panel — Create (also copies), Copy, New (regenerate), Revoke.
  Invoices are unshared by default; sharing only happens on explicit user action.
- `InvoicesList`: per-row "copy share link" action (idempotent create + copy).
- WhatsApp message gains a `View invoice online: <url>` line only when a link is active.
- `<meta name="referrer" content="no-referrer">` in `index.html`.
- `src/config/api.js` now reads `VITE_API_BASE_URL`; committed `.env.production` pins the
  Railway URL so production builds keep working while local dev falls back to
  `http://localhost:8080/api`.

### Invoice email delivery

- Endpoint: `POST /api/invoices/{id}/email` (JWT required, ownership via `businessId`).
  Body optional: `{"frontendOrigin": "https://..."}` — the frontend passes its own
  origin so the share link inside the email points at the site that issued it; the
  server falls back to `app.frontend-base-url`. Only bare http(s) hosts are accepted
  and the origin is validated **before any side effect** (a bad request never mints a link).
- The link is **created on demand** (`InvoiceShareService.createOrRetrieve`): a revoked
  or never-shared invoice still emails a working link; the link commits before the send,
  so a provider failure never rolls it back.
- Template: `InvoiceEmailTemplate` (subject + table-based HTML + plain-text fallback).
  The heading brands the sender's business ("Acme Enterprises"), the footer attributes
  Inside Invoice. Carries the item breakup (qty / rate / taxable / GST% / amount), totals
  with a CGST+SGST split when the place of supply matches the business state (IGST
  otherwise, generic GST when state data is missing), amount in words, and the share-link
  CTA. All user-supplied strings are HTML-escaped; totals come from the invoice record
  (server truth, never the client).
- Error contract: customer has no email → 400; mail not configured → 503; provider
  failure → 502 (`EmailDeliveryException`, handled centrally). The success message
  echoes the recipient: "Invoice email sent to `<addr>`".
- Config: `app.mail.resend-api-key` (base64-encoded), `app.mail.from`,
  `app.mail.contact-email`, `app.frontend-base-url`. **Fix included:** these keys
  previously lived under `spring.mail.*` in `application.yml` while `EmailService`
  reads `${app.mail.*}` — every send (OTP, contact) silently skipped. The block now
  lives under `app:`. `application-local.yaml` pins the key to `""` so local dev never
  sends real mail (override with env `APP_MAIL_RESEND_API_KEY`); production (base yml)
  sends via Resend. Provider calls now have 10s/20s timeouts instead of none.
- Frontend: `InvoiceForm` — "Email" toggle below Discount (create **and** update);
  sends right after a successful save and unchecks itself after a successful send.
  `InvoiceView` — the Share Link panel row is half "Copy link" / half "Send Email"
  (re-trigger; creates the link first if missing).
- Heads-up: with the config mismatch fixed, **OTP and contact-form emails will start
  actually delivering in production once deployed** (they were silently skipped before
  unless `APP_MAIL_RESEND_API_KEY` was set in the environment).

## 2. Rollout order

1. Backend first (Railway): Flyway applies V19 automatically on boot. Additive only.
   - Optional config: `app.cors.allowed-origins` if the site origin differs from the
     defaults listed above; `app.public.rate-limit.*` to tune throttling;
     `app.security.trust-forwarded-for=false` if the app is ever exposed directly instead
     of behind a proxy.
2. Frontend (Netlify): `vite build` picks up `.env.production` automatically. Existing
   `public/_redirects` (`/*  /index.html  200`) already routes `/i/:token` to the SPA —
   no redirect changes were needed. **Deployment target is Netlify** (netlify.toml +
   custom-domain redirect); no Cloudflare Pages configuration exists in the repo.
3. Smoke: create a link from the invoice view, open it in a private browser window,
   View/Download/Print, then revoke and confirm the link dies.

## 3. What the public DTO deliberately excludes

Included: invoice metadata and totals, line items (as displayed), seller profile +
payment instructions, buyer (billing) details, invoice `notes` (they are the document's
terms and appear on the PDF).

Excluded by explicit field-mapping (never entity serialization): all entity `id`s,
`businessId`, `customerId`, `createdBy`, `shareToken`, audit timestamps. The frontend
mapping in `PublicInvoicePage.jsx` mirrors this — adding a backend field does not leak
until both sides allow it.

## 4. Honest residual risks (documented, not hidden)

- **No claim of absolute security.** Tests verify the checks that exist; they cannot
  prove absence of unknown issues.
- **Rate limiting is process-local** (one JVM's memory). Railway runs a single instance
  today, so it sees all traffic; if scaled horizontally it must move to a shared store.
  The knobs are already externalized for that.
- **Forwarded-IP trust**: per-IP throttling uses the last `X-Forwarded-For` hop only while
  `app.security.trust-forwarded-for=true` (default). Correct only if Railway's ingress
  always appends to that header and the app port is not directly reachable. It is used
  for throttling decisions only, never for authorization.
- **Link secrecy = access.** Anyone who obtains a live link can read that one invoice
  until it is revoked. Tokens are unguessable (256 bits) and never logged, but they live
  in browser history, referrers are suppressed, chat apps may still store them, and there
  is no expiry. Revocation is immediate and tested.
- **Plaintext secrets in `application.yml`** (Neon credentials, Resend key, Google OAuth
  client secret + refresh token, JWT secret) are committed to the repository — a leak
  there exposes everything. Out of scope for this change; move to environment/secret
  manager before treating the repo as public.
- **`POST /api/email/inbound` is anonymous** (webhook by design) — unverified caller;
  ensure it only stores data and never trusts payload-supplied business IDs.
- **Label creation stores caller-supplied `invoiceId` without ownership validation**
  (low severity: labels are own-tenant data, but an attacker can point them at a foreign
  invoice ID). Worth fixing in a follow-up.
- **Server-rendered PDF is a deviation from "reuse the existing PDF service"**: no backend
  invoice PDF existed (all PDFs were client-side jsPDF/html2canvas). A minimal PDFBox
  renderer was added for the public flow only; private flows are untouched. It mirrors the
  frontend's number formatting and amount-in-words, but may differ pixel-wise from
  client-rendered PDFs.
- **Frontend has no test runner** — no vitest/jest is configured in the repo. The public
  page and share UI were verified by production build + lint (0 new errors) + API-contract
  E2E, not by component tests.

## 5. Running the backend tests

Requires local PostgreSQL on `localhost:5433` (user `insideinvoice` / password
`invoiceinside`). The suite **recreates** the scratch database `inside_invoice_test` and
runs the real migrations (including V19). It is hard-wired to localhost (double-guarded
by profile YAML and `@DynamicPropertySource`) and can never reach the Neon database.

```
mvn clean test        # 95 tests: 64 pre-existing + 15 sharing/security + 16 email
```

Attack scenarios covered: anonymous access (401), cross-tenant invoice/payment reads
(404, incl. the BOLA regression), cross-tenant share management (404), share lifecycle
(create/revoke/re-create rotation, regenerate, concurrency), uniform 404s for
invalid/unknown/revoked tokens (detail + PDF), DTO allowlist (no IDs/secrets/echo),
privacy headers on 200 and 404, real PDF bytes + media type, CORS allow/deny, admin BFLA
(403), actuator restriction (401), rate-limit 429 (per-token and per-IP, generic body,
headers intact). Email coverage: happy-path capture of recipient/subject/HTML (link,
business branding, GST breakup, amount in words), configured-origin fallback, revoked-link
recreation, 400 (no customer email, invalid origin — with no link minted), 404 cross-tenant,
401 anonymous, blank-key 503, From-name sanitisation, Indian amount grouping, CGST/SGST vs
IGST selection, and HTML-escaping of user-supplied names.
