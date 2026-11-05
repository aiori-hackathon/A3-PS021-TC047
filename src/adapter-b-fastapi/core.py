"""Durable request processing for Adapter B."""

import hashlib
import json
import logging
import os
import time
from typing import Any

from fastapi import HTTPException
from psycopg.errors import UniqueViolation
from psycopg.types.json import Json

from db import transaction
from faults import CRASH_BEFORE, CRASH_DURING, DROP_RESPONSE, DELAY_PREFIX, Fault
from models import RequestModel, ResponseModel, StatusEnum
from worker.factory import get_worker

LOGGER = logging.getLogger(__name__)
STALE_CLAIM_SECONDS = "STALE_CLAIM_SECONDS"
DEFAULT_STALE_CLAIM_SECONDS = 90.0

RequestRow = tuple[Any, ...]


class DropResponse(Exception):
    """Signals that durable processing completed but the HTTP response is lost."""


def _payload_hash(payload: dict[str, Any]) -> str:
    """Hash canonical JSON so equivalent payloads have the same identity."""
    canonical = json.dumps(
        payload,
        sort_keys=True,
        separators=(",", ":"),
        ensure_ascii=False,
    ).encode("utf-8")
    return hashlib.sha256(canonical).hexdigest()


def _stale_claim_seconds() -> float:
    value = os.environ.get(STALE_CLAIM_SECONDS)
    if value is None:
        return DEFAULT_STALE_CLAIM_SECONDS
    try:
        seconds = float(value)
    except ValueError as exc:
        raise RuntimeError("STALE_CLAIM_SECONDS must be numeric") from exc
    if seconds < 0:
        raise RuntimeError("STALE_CLAIM_SECONDS must not be negative")
    return seconds


def _response(
    request: RequestModel,
    status: StatusEnum,
    *,
    duplicate: bool,
    result: dict[str, Any] | None = None,
    message: str | None = None,
) -> ResponseModel:
    return ResponseModel(
        dialog_id=request.dialog_id,
        task_id=request.task_id,
        request_id=request.request_id,
        status=status,
        result=result,
        duplicate=duplicate,
        message=message,
    )


def _log_event(
    connection: Any,
    request: RequestModel,
    event_type: str,
    message: str | None = None,
) -> None:
    connection.execute(
        """
        INSERT INTO adapter_b.dialog_events
            (dialog_id, request_id, event_type, message)
        VALUES (%s, %s, %s, %s)
        """,
        (request.dialog_id, request.request_id, event_type, message),
    )


def _claim_or_replay(
    request: RequestModel,
    payload_hash: str,
) -> tuple[bool, int | None, ResponseModel | None]:
    """Atomically claim new/stale work or return the durable existing outcome."""
    payload_mismatch = False
    with transaction() as connection:
        claimed = connection.execute(
            """
            INSERT INTO adapter_b.requests
                (dialog_id, request_id, task_id, payload_hash, status)
            VALUES (%s, %s, %s, %s, 'PROCESSING')
            ON CONFLICT (dialog_id, request_id) DO NOTHING
            RETURNING dialog_id, request_id, task_id, payload_hash, status,
                      result_data, error_message, attempt_count, claimed_at
            """,
            (
                request.dialog_id,
                request.request_id,
                request.task_id,
                payload_hash,
            ),
        ).fetchone()
        if claimed is not None:
            _log_event(connection, request, "CLAIMED")
            return True, claimed[7], None

        existing: RequestRow | None = connection.execute(
            """
            SELECT dialog_id, request_id, task_id, payload_hash, status,
                   result_data, error_message, attempt_count, claimed_at
            FROM adapter_b.requests
            WHERE dialog_id = %s AND request_id = %s
            """,
            (request.dialog_id, request.request_id),
        ).fetchone()
        if existing is None:
            raise RuntimeError("request disappeared after conflict")

        existing_hash = existing[3]
        existing_status = existing[4]
        if existing_hash != payload_hash:
            _log_event(connection, request, "PAYLOAD_MISMATCH")
            payload_mismatch = True
        elif existing_status == "COMPLETED":
            return False, None, _response(
                    request,
                    StatusEnum.COMPLETED,
                    duplicate=True,
                    result=existing[5],
                    message="Request already completed.",
                )
        elif existing_status == "FAILED":
            return False, None, _response(
                    request,
                    StatusEnum.FAILED,
                    duplicate=True,
                    message=existing[6],
                )
        else:
            takeover = connection.execute(
                """
                UPDATE adapter_b.requests
                SET claimed_at = now(),
                    attempt_count = attempt_count + 1,
                    updated_at = now()
                WHERE dialog_id = %s
                  AND request_id = %s
                  AND status = 'PROCESSING'
                  AND claimed_at < now() - make_interval(secs => %s)
                RETURNING attempt_count
                """,
                (request.dialog_id, request.request_id, _stale_claim_seconds()),
            ).fetchone()
            if takeover is not None:
                _log_event(connection, request, "CLAIM_TAKEN_OVER")
                return True, takeover[0], None

            _log_event(connection, request, "IN_PROGRESS")
            return False, None, _response(
                request,
                StatusEnum.IN_PROGRESS,
                duplicate=False,
                message="Request is already being processed.",
            )

    if payload_mismatch:
        raise HTTPException(
            status_code=409,
            detail={"status": "FAILED", "message": "payload_mismatch"},
        )
    raise RuntimeError("unhandled request state")


def _complete_request(
    request: RequestModel,
    result: dict[str, Any],
    attempt: int,
) -> bool:
    """Persist the effect and terminal status in one transaction."""
    try:
        with transaction() as connection:
            completed = connection.execute(
                """
                UPDATE adapter_b.requests
                SET status = 'COMPLETED',
                    result_data = %s,
                    updated_at = now()
                WHERE dialog_id = %s
                  AND request_id = %s
                  AND status = 'PROCESSING'
                  AND attempt_count = %s
                RETURNING 1
                """,
                (Json(result), request.dialog_id, request.request_id, attempt),
            ).fetchone()
            if completed is None:
                _log_event(connection, request, "CLAIM_LOST")
                return False
            connection.execute(
                """
                INSERT INTO adapter_b.effects
                    (dialog_id, request_id, effect_type, effect_data)
                VALUES (%s, %s, 'mock_write', %s)
                """,
                (request.dialog_id, request.request_id, Json(result)),
            )
            _log_event(connection, request, "COMPLETED")
            return True
    except UniqueViolation:
        return False


def _fail_request(request: RequestModel, error_message: str, attempt: int) -> bool:
    with transaction() as connection:
        failed = connection.execute(
            """
            UPDATE adapter_b.requests
            SET status = 'FAILED',
                error_message = %s,
                updated_at = now()
            WHERE dialog_id = %s
              AND request_id = %s
              AND status = 'PROCESSING'
              AND attempt_count = %s
            RETURNING 1
            """,
            (error_message, request.dialog_id, request.request_id, attempt),
        ).fetchone()
        if failed is None:
            _log_event(connection, request, "CLAIM_LOST")
            return False
        _log_event(connection, request, "FAILED", error_message)
        return True


def _stored_response(request: RequestModel) -> ResponseModel:
    """Re-read the durable row after losing ownership of a processing attempt."""
    with transaction() as connection:
        row = connection.execute(
            """
            SELECT status, result_data, error_message
            FROM adapter_b.requests
            WHERE dialog_id = %s AND request_id = %s
            """,
            (request.dialog_id, request.request_id),
        ).fetchone()
    if row is None:
        raise RuntimeError("request disappeared while resolving ownership loss")
    status, result, error_message = row
    if status == "COMPLETED":
        return _response(
            request,
            StatusEnum.COMPLETED,
            duplicate=True,
            result=result,
            message="Request already completed.",
        )
    if status == "PROCESSING":
        return _response(
            request,
            StatusEnum.IN_PROGRESS,
            duplicate=False,
            message="Request is already being processed.",
        )
    return _response(
        request,
        StatusEnum.FAILED,
        duplicate=True,
        message=error_message,
    )


def process_request(
    request: RequestModel,
    fault: Fault | None = None,
) -> ResponseModel:
    """Claim durable work before running the worker and recording its effect."""
    payload_hash = _payload_hash(request.payload)
    is_winner, attempt, replay = _claim_or_replay(request, payload_hash)
    if not is_winner:
        if replay is None:
            raise RuntimeError("claim decision returned no response")
        return replay
    if attempt is None:
        raise RuntimeError("winning claim returned no attempt")

    if fault is not None and fault.action == CRASH_BEFORE:
        os._exit(1)
    if fault is not None and fault.action == DELAY_PREFIX:
        time.sleep(fault.delay_ms / 1000)

    worker = get_worker()
    try:
        if fault is not None and fault.action == CRASH_DURING:
            os._exit(1)
        result = worker.run(request.payload)
    except Exception as exc:
        error_message = str(exc)
        if not _fail_request(request, error_message, attempt):
            return _stored_response(request)
        LOGGER.exception(
            "Worker failed for dialog_id=%s request_id=%s",
            request.dialog_id,
            request.request_id,
        )
        return _response(
            request,
            StatusEnum.FAILED,
            duplicate=False,
            message=error_message,
        )

    if not _complete_request(request, result, attempt):
        return _stored_response(request)
    if fault is not None and fault.action == DROP_RESPONSE:
        raise DropResponse
    return _response(
        request,
        StatusEnum.COMPLETED,
        duplicate=False,
        result=result,
        message="Request processed successfully.",
    )
