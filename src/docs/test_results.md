# Adapter B Test Results

Command run from the repository root:

```text
uv run --project adapter-b-fastapi pytest -q tests
......                                                                   [100%]
6 passed in 5.87s
```

| Test | Starting state | Injected failure | Retry behaviour | Final state | Identity preserved? | Duplicate prevented? | Result |
|---|---|---|---|---|---|---|---|
| `duplicate_no_worker_call` | No request row | None | Same IDs and payload | `COMPLETED`, one effect | Yes | Yes; worker called once | Passed |
| `payload_mismatch` | Completed request | Different payload with same IDs | Rejected with HTTP 409 | `COMPLETED`, one effect | Yes | Yes; mismatch did not call worker | Passed |
| `concurrent_identical` | No request row | Ten parallel identical requests | Fresh duplicates return `IN_PROGRESS` or stored result | `COMPLETED`, one effect | Yes | Yes; worker called once | Passed |
| `scenario_3_response_lost` | No request row | `X-Fault: drop-response` with `FAULT_INJECTION=true` | Retry after lost response | `COMPLETED`, one effect | Yes | Yes; stored result replayed | Passed |
| `scenario_4_restart_then_retry` | No request row | Uvicorn process terminated after completion | Restart and resend same IDs | `COMPLETED`, one effect | Yes | Yes; stored result replayed | Passed |
| `stale_claim_recovered_on_retry` | Stale `PROCESSING` row, no effect | Startup audit only; no auto-execution | Next retry atomically takes over | `COMPLETED`, attempt count 2, one effect | Yes | Yes; worker called once | Passed |
