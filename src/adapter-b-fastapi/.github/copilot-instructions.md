# Repo rules for Copilot (Adapter B, FastAPI) — PS-021, team 404 Crew

Source of truth: PROJECT_PLAN.md (v3: real agents and MCP required for the demo, REST kept, ONE shared core).
## Non-negotiable design rules (plan section 7)
1. Dedup key is (dialog_id, request_id), enforced by a DB PRIMARY KEY/UNIQUE. The claim is ONE atomic statement
   (INSERT ... ON CONFLICT DO NOTHING RETURNING), never check-then-insert.
2. Store a payload hash (SHA-256 of canonical JSON: sort_keys=True, separators=(",",":"), ensure_ascii=False).
   Same request_id + different payload => reject, never execute.
3. The effect row and the COMPLETED status update happen in ONE transaction.
4. The worker (stub or LLM) runs AFTER the claim commits and BEFORE the effect transaction. Never call a worker or
   any LLM inside an open DB transaction.
5. A duplicate of a COMPLETED request returns the STORED result with duplicate=true and must NOT call the worker.
6. Only this adapter touches schema adapter_b. Wire format is snake_case. IDs are str.
7. Append every meaningful step to adapter_b.dialog_events.
8. Never claim "exactly-once". Say "effectively-once for our mock effect (a DB write) under the tested failures".
9. ONE core function `process_request`. The REST route and the MCP tool are thin wrappers; no claim/dedup logic
   is duplicated in either door.
10. The `hf` worker is called only after a winning claim, never for a duplicate, never inside a DB transaction.
    Wire the same worker interface: run(payload: dict) -> dict. Worker errors raise; core maps them to FAILED.
## Code quality
- Python 3.11, type hints everywhere, Pydantic v2 models already in models.py (do not change field names/types
  without flagging it; extra="forbid" stays).
- psycopg 3 (sync) + psycopg_pool. Parameterised SQL only. No f-string SQL. No ORM.
- Config only from environment variables (DATABASE_URL, AGENT_MODE, FAULT_INJECTION, STALE_CLAIM_SECONDS).
  Secrets are never committed or logged.
- Small functions, one responsibility each, docstrings that state the invariant being protected.
- Route handlers must be plain `def` (not `async def`) because the core is blocking (DB + worker sleep),
  or use run_in_threadpool.
- Use the logging module, not print.
- Do not add dependencies, abstractions or frameworks without a concrete need. Use `uv add`.

## Verification rule
Never say something works unless you ran it. Show the exact command and the real output.
If you could not run it, say "NOT RUN".