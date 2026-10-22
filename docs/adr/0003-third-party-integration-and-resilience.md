# ADR 0003: Third-party integration and resilience

- **Status:** Accepted
- **Date:** 2026-10-22

## Context

Issuing a policy now depends on two external partners:

| Partner | Used for | If it is down… |
|---|---|---|
| **Vehicle registry** (national registry API) | Confirm the vehicle exists, is not scrapped, and the declared model year is right | Quoting must keep working. Agents in a branch cannot tell a customer "come back later". |
| **Payment gateway** (virtual POS) | Collect the premium before the policy is issued | No policy may be issued without payment, and **no customer may be charged twice**. |

Partners fail in different ways: slow responses, 5xx, connection resets, contract changes. A slow
partner is the most dangerous, because it ties up our request threads and database connections.

## Decision

**Ports and adapters.** The domain depends on the `VehicleInquiry` and `PaymentGateway` interfaces.
`VehicleRegistryClient` and `PaymentGatewayClient` (Adapter pattern) own the HTTP details and the
partners' wire formats, so a partner's JSON never leaks into the domain and switching providers stays local.

**Error translation in the adapter.**

| Partner response | Exception | Retried | Counted by circuit breaker | API answer |
|---|---|---|---|---|
| timeout, I/O error, 5xx, 429 | `ThirdPartyUnavailableException` | yes | yes | fallback, or `503` with `Retry-After` |
| unexpected 4xx, unknown enum, empty body | `ThirdPartyIntegrationException` | no | no | `502` (contract/configuration problem that needs a human) |
| vehicle `404` | not an error: "unknown plate" | n/a | n/a | quote marked `UNVERIFIED` |
| payment `DECLINED` | not an error: a business outcome | n/a | n/a | `422`, quote stays open |

**Resilience4j**, configured per partner:
- *Timeouts* on every client (registry: 2 s read; payment: 10 s, since card authorisation is legitimately slower).
- *Retry*: 3 attempts with exponential backoff, only for transient failures.
- *Circuit breaker*: opens at a 50 % failure rate over the last 20 calls (min. 10), stays open for 30 s,
  and also treats sustained slow calls as failures. Retry wraps the circuit breaker, so an open
  circuit stops retries immediately.
- An open circuit is reported by `/actuator/health` but does **not** make the app DOWN
  (`allow-health-indicator-to-fail: false`). Otherwise the orchestrator would restart healthy pods
  because a partner is down.

**Vehicle registry fallback.** After retries, or when the circuit is open, the lookup is answered
from the copy loaded nightly by `vehicleImportJob` (ADR 0002). Scrapped vehicles are therefore still
rejected during an outage. Each quote records how the vehicle was verified (`VERIFIED_ONLINE`,
`VERIFIED_OFFLINE`, `UNVERIFIED`) for underwriting and audit.

**Payment: no fallback, idempotent retries, no transaction around the HTTP call.**
1. In a short read-only transaction, check the quote can still be issued, so we never charge for
   an expired or already converted quote.
2. Charge **outside any transaction**, with `Idempotency-Key: policy-issue-<quoteNumber>`. Retries
   (ours, or a client re-submitting after a timeout) reuse the key, so the provider returns the
   original payment instead of charging again.
3. In a second transaction, convert the quote and store the policy with the provider's payment id.

If step 3 fails after a successful charge, re-submitting the same request is safe: the charge is
deduplicated and the conversion runs again. Card data never reaches us; the API accepts a single-use
`paymentToken` from the provider's hosted card form.

## Consequences

- Quoting survives a registry outage with day-old data, and the response shows the data was day-old.
- Payment outages surface as `503` + `Retry-After`; nothing is issued, and a retry cannot double charge.
- The JDK HTTP client is pinned to HTTP/1.1. Its h2c upgrade on plain-HTTP POSTs was reset by the
  gateway stub, and the same happens with many real gateways and proxies.
- Not covered yet: a reconciliation job for the rare "charged but policy step failed and the client
  never retried" case, which should refund or complete it. Rate limiting and bulkheads per partner
  can be added through the same Resilience4j configuration when load requires them.
