# PolicyHub

[![CI](https://github.com/emircanozdag/policyhub/actions/workflows/ci.yml/badge.svg)](https://github.com/emircanozdag/policyhub/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-brightgreen)
![Oracle](https://img.shields.io/badge/Oracle-23ai%20Free-red)
![Spring Batch](https://img.shields.io/badge/Spring%20Batch-6-6db33f)

A motor insurance platform for **Kasko** (comprehensive) and **Traffic** (compulsory liability)
products. It covers quoting, policy issuance, nightly batch processing, claims with AI-assisted
triage, and integration with third-party services. The goal is to model the problems a real
insurance back office deals with, built with production-grade engineering practices.

```
 Branch / Digital ──► REST API ──► Quote ──► Policy ──► Claim (AI triage)
                                     │          ▲
                     Premium strategies    Spring Batch: renewals,
                     (Kasko, Traffic, …)   mainframe file import
                                     │
                               Oracle Database
```

## Quick start

Requires only Docker.

```bash
docker compose up --build
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

```bash
# 1. Get a quote
curl -s -X POST localhost:8080/api/v1/quotes -H 'Content-Type: application/json' -d '{
  "productType": "KASKO", "plateNumber": "34 ABC 123", "vehicleModelYear": 2023,
  "vehicleValue": 1250000, "driverAge": 32, "claimCountLast3Years": 0 }'

# 2. Issue a policy from it
curl -s -X POST localhost:8080/api/v1/policies -H 'Content-Type: application/json' \
  -d '{"quoteNumber": "Q-20261021-123456"}'
```

## API

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/quotes` | Price a Kasko or Traffic quote (valid for 15 days) |
| `GET` | `/api/v1/quotes/{quoteNumber}` | Get a quote |
| `POST` | `/api/v1/policies` | Issue a one-year policy from an active quote |
| `GET` | `/api/v1/policies/{policyNumber}` | Get a policy |
| `POST` | `/api/v1/policies/{policyNumber}/cancel` | Cancel an active policy |
| `GET` | `/api/v1/vehicles/{plateNumber}` | Vehicle registry entry and current kasko value |
| `POST` | `/api/v1/batch/vehicle-import` | Import a mainframe extract from the inbound directory |
| `POST` | `/api/v1/batch/policy-lifecycle?businessDate=` | Run the nightly lifecycle job on demand |

Errors follow [RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457): `400` for
validation, `404` for unknown resources, `422` for business rule violations (expired quote,
quote already converted, …), `409` when a batch job for the same file or business date already ran.

## Batch jobs

Both jobs keep their run history (read / write / skip counts, status, parameters) in the Spring Batch
tables on Oracle. Design notes: [ADR 0002](docs/adr/0002-batch-processing-and-mainframe-file-import.md).

### `vehicleImportJob`: mainframe vehicle registry extract

The vehicle registry and kasko value list arrives from the mainframe as a fixed-width file described
by the COBOL copybook [`VEHREC.cpy`](docs/mainframe/VEHREC.cpy) (`LRECL=100`, header / details / trailer):

```
H20261021MFVEHREG
D34ABC123  NMTBA3BE10R123456TOYOTA         COROLLA 1.5 VISION  20230000125000000A
D41GH5678  TMBJJ7NE1L0123456SKODA          OCTAVIA 1.5 TSI     2022000009X500000A   <- corrupt, skipped
T000000006
```

1. **Validate:** header and trailer present, `LRECL` respected, and the trailer control total matches
   the detail count. Otherwise the whole file is rejected before anything is loaded.
2. **Load:** chunked read, `PIC 9(11)V99` decoded as implied decimals, record validation, Oracle `MERGE`
   upsert. Invalid records are skipped and logged with line number and reason.

```bash
curl -s -X POST localhost:8080/api/v1/batch/vehicle-import -H 'Content-Type: application/json' \
  -d '{"fileName": "VEHREG.20261021.DAT"}'
```

### `policyLifecycleJob`: nightly at 02:00 (Europe/Istanbul)

1. Expire active quotes past their validity date.
2. Expire active policies past their end date.
3. Create a **renewal quote** for every policy ending in the next 30 days. The risk is aged by one
   year and re-priced with the latest kasko value from the registry. Scrapped vehicles are not
   renewed, and a unique constraint guarantees at most one renewal per policy.

```bash
curl -s -X POST 'localhost:8080/api/v1/batch/policy-lifecycle?businessDate=2026-10-21'
```

## Engineering highlights

| Area | How it is addressed | Status |
|---|---|---|
| Java / Spring Boot / REST | Spring Boot 4, layered by feature (`quote`, `policy`, `premium`), OpenAPI docs | ✅ |
| Oracle Database | Oracle 23ai Free, schema versioned with Flyway, `ddl-auto=validate` | ✅ |
| Design patterns / OOP | **Strategy** for premium calculation ([ADR 0001](docs/adr/0001-strategy-pattern-for-premium-calculation.md)), rich domain entities guarding their own state transitions, sealed record hierarchy for file records | ✅ |
| Testing (JUnit) | JUnit 5 + Mockito unit tests, `@WebMvcTest` slices, **Testcontainers** integration tests (API flow and both batch jobs) against real Oracle, JaCoCo coverage | ✅ |
| CI | GitHub Actions running unit + integration tests | ✅ |
| Spring Batch | Nightly lifecycle job (expiry and renewal quotes), restartable and idempotent ([ADR 0002](docs/adr/0002-batch-processing-and-mainframe-file-import.md)) | ✅ |
| Mainframe integration | Import of **fixed-width files described by a COBOL copybook**, with control totals, implied decimals, skip-and-log and Oracle `MERGE` | ✅ |
| 3rd-party integration | Vehicle registry, payment gateway, claim history adapters; Resilience4j circuit breaker / retry; WireMock stubs | 🔜 |
| Jenkins + SonarQube | `Jenkinsfile`, SonarQube quality gate via Docker Compose | 🔜 |
| Monitoring | Structured JSON logs → ELK (Kibana dashboards), OpenTelemetry tracing (Dynatrace-compatible), Micrometer metrics | 🔜 |
| AI | Claim triage: LLM classifies damage type, summarises the report, flags fraud risk (Spring AI) | 🔜 |
| Frontend | React agent/branch screen for quotes, policies and claims | 🔜 |

## Project structure

```
src/main/java/com/policyhub
├── premium/   # PremiumCalculator strategies + registry (pure domain, no framework coupling)
├── quote/     # Quote aggregate, service, REST controller
├── policy/    # Policy aggregate, service, REST controller
├── vehicle/   # Vehicle registry read model + REST controller
├── batch/
│   ├── vehicleimport/   # Mainframe extract: copybook layout, validator, processor, job config
│   └── lifecycle/       # Expiry + renewal quote job
└── common/    # Exceptions, Problem Details handler, plate numbers, reference numbers
src/main/resources/db/migration   # Flyway (Oracle) migrations, incl. Spring Batch metadata
docs/adr                          # Architecture Decision Records
docs/mainframe                    # COBOL copybooks of inbound files
samples/mainframe                 # Sample extract, mounted as the inbound directory in Docker Compose
```

## Running tests locally

```bash
./mvnw test     # unit + web slice tests (no Docker needed)
./mvnw verify   # + Oracle integration tests via Testcontainers (Docker needed)
```

## Roadmap

1. ~~Quote & policy API on Oracle with premium strategies~~
2. ~~Spring Batch: renewals + mainframe fixed-width import~~
3. Third-party integrations with resilience patterns
4. Jenkins pipeline + SonarQube quality gate
5. Observability: ELK, OpenTelemetry, dashboards & runbook
6. Claims module with AI triage
7. React frontend

> Premium rates in this project are illustrative and do not reflect any real insurer's tariffs.
