# ADR 0002: Batch processing and mainframe file import

- **Status:** Accepted
- **Date:** 2026-10-21

## Context

Two kinds of work do not fit a request/response API:

1. **Vehicle registry / kasko value list.** It is produced daily on the mainframe as a fixed-width
   extract (`RECFM=FB, LRECL=100`, layout in [`VEHREC.cpy`](../mainframe/VEHREC.cpy)) with a
   header, detail records and a trailer carrying a control total. It drives renewal pricing.
2. **Policy lifecycle.** Every night, stale quotes and ended policies must be expired, and policies
   close to their end date need a re-priced renewal offer.

Both must be restartable, auditable (what ran, when, how many records) and safe to re-run.

## Decision

Use **Spring Batch 6** with its job repository in the same Oracle schema (tables created by Flyway, not
by Spring Boot, so all DDL is versioned in one place).

**`vehicleImportJob`**
- *Validate first, load second.* A tasklet checks the envelope (header first, trailer last, known record
  types, `LRECL`) and that the trailer count equals the number of detail records. A truncated or
  mismatched file loads nothing, so there is never a half-imported file to clean up.
- The load step maps the copybook to Java with `FixedLengthTokenizer` ranges and decodes
  `PIC 9(11)V99` as an implied-decimal `BigDecimal`. Values are never parsed through `double`.
- Bad detail records are **skipped and logged** with line number and reason (up to a limit) rather
  than failing the run: one corrupt vehicle should not block thousands of valid ones.
- Writes are an Oracle `MERGE` (upsert), so a later file updates vehicles already known.
- The file path is the identifying job parameter: the same file cannot be imported twice by accident.
- The processor is stateless because fault-tolerant steps may process an item again after a rollback.

**`policyLifecycleJob`**
- The business date is the identifying parameter: one run per date, and a failed run is restarted
  rather than run again.
- Expiry steps are set-based `UPDATE` statements. Doing them row by row would add nothing.
- Renewal candidates are read with a **cursor** reader. A paging reader would skip rows, because
  writing a renewal removes that policy from the `NOT EXISTS` result set and shifts the page offsets.
- A unique constraint on `quote.renewal_of_policy_id` guarantees at most one renewal per policy,
  even across overlapping or restarted runs.

Jobs are started by a cron scheduler (02:00 Europe/Istanbul) or on demand through `/api/v1/batch/*`.
The import endpoint only accepts a bare file name resolved inside the configured inbound directory,
never a path.

## Consequences

- Operations can see the read, write, skip and filter counts of every run in the `BATCH_*` tables,
  and those counts come back in the API response.
- The file is assumed to arrive through a text-mode transfer (EBCDIC converted to the configured
  encoding). Binary transfers with packed decimal (`COMP-3`) fields would need a custom record
  reader. That is a known gap and is not needed yet.
- Running several application instances would need the scheduler to be coordinated (for example
  with ShedLock). Today the job repository only prevents two runs of the same business date.
