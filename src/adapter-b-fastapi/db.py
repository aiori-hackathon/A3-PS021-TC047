"""PostgreSQL connection and schema lifecycle for Adapter B."""

from contextlib import contextmanager
import logging
import os
from pathlib import Path
from typing import Iterator
from dotenv import load_dotenv

from psycopg import Connection
from psycopg_pool import ConnectionPool

load_dotenv(Path(__file__).parent / ".env")

LOGGER = logging.getLogger(__name__)
SCHEMA_PATH = Path(__file__).parent / "sql" / "schema.sql"

_pool: ConnectionPool[Connection] | None = None

FAULT_INJECTION = os.environ.get("FAULT_INJECTION", "false").lower() == "true"
STALE_CLAIM_SECONDS = float(os.environ.get("STALE_CLAIM_SECONDS", "10"))


def _database_url() -> str:
    """Return the configured database URL, failing explicitly when absent."""
    database_url = os.environ.get("DATABASE_URL")
    if not database_url:
        raise RuntimeError("DATABASE_URL must be configured")
    return database_url


def open_pool() -> None:
    """Open the shared pool before requests can access durable state."""
    global _pool
    if _pool is not None:
        return
    _pool = ConnectionPool(
        conninfo=_database_url(),
        min_size=1,
        max_size=10,
        open=False,
    )
    _pool.open()
    _pool.wait()
    LOGGER.info("Adapter B database pool opened")


def close_pool() -> None:
    """Close all pooled connections during application shutdown."""
    global _pool
    if _pool is None:
        return
    _pool.close()
    _pool = None
    LOGGER.info("Adapter B database pool closed")


def _get_pool() -> ConnectionPool[Connection]:
    if _pool is None:
        raise RuntimeError("Database pool is not open")
    return _pool


@contextmanager
def transaction() -> Iterator[Connection]:
    """Yield a connection whose transaction commits or rolls back atomically."""
    pool = _get_pool()
    with pool.connection() as connection:
        with connection.transaction():
            yield connection


def init_schema() -> None:
    """Create Adapter B's tables idempotently in one transaction."""
    schema_sql = SCHEMA_PATH.read_text(encoding="utf-8")
    with transaction() as connection:
        connection.execute(schema_sql)
    LOGGER.info("Adapter B schema initialized")


def recover_stale_claims(stale_claim_seconds: float) -> int:
    """Audit stale processing rows without executing or taking them over."""
    with transaction() as connection:
        rows = connection.execute(
            """
            SELECT dialog_id, request_id
            FROM adapter_b.requests
            WHERE status = 'PROCESSING'
              AND claimed_at < now() - make_interval(secs => %s)
            """,
            (stale_claim_seconds,),
        ).fetchall()
        for dialog_id, request_id in rows:
            connection.execute(
                """
                INSERT INTO adapter_b.dialog_events
                    (dialog_id, request_id, event_type, message)
                VALUES (%s, %s, 'STALE_CLAIM_DETECTED', %s)
                """,
                (
                    dialog_id,
                    request_id,
                    "Stale PROCESSING claim detected during startup",
                ),
            )
    LOGGER.info("Audited %d stale processing claims", len(rows))
    return len(rows)


def initialize() -> None:
    """Open the pool and initialize the schema for application startup."""
    open_pool()
    init_schema()


if __name__ == "__main__":
    import sys

    if len(sys.argv) != 2 or sys.argv[1] != "init":
        raise SystemExit("usage: python -m db init")
    open_pool()
    try:
        init_schema()
    finally:
        close_pool()
