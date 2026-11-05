# Adapter B Runtime Configuration

`STALE_CLAIM_SECONDS` defaults to `90`. Configure it to exceed the worker
timeout (`HF_TIMEOUT_SECONDS`) by a clear margin. A stale claim must not be
released while the worker may still be running, because an early takeover can
allow the worker to execute twice. The effect uniqueness constraint still
deduplicates the database effect, but the worker itself may run twice.
