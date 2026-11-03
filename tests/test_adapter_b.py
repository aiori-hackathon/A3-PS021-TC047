from concurrent.futures import ThreadPoolExecutor
import os
from pathlib import Path
import socket
import subprocess
import sys
import time
from urllib.error import URLError
from urllib.request import Request, urlopen

import pytest
from fastapi.testclient import TestClient

from main import app
from models import RequestModel
from worker import stub
from core import (
    _claim_or_replay,
    _complete_request,
    _fail_request,
    _payload_hash,
    process_request,
)


def _count_effects(body: dict) -> int:
    from db import open_pool, transaction

    open_pool()
    with transaction() as connection:
        return connection.execute(
            "SELECT count(*) FROM adapter_b.effects WHERE dialog_id = %s AND request_id = %s",
            (body["dialog_id"], body["request_id"]),
        ).fetchone()[0]


def _free_port() -> int:
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def _post_to_server(port: int, body: dict):
    encoded = __import__("json").dumps(body).encode()
    request = Request(
        f"http://127.0.0.1:{port}/v1/requests",
        data=encoded,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    return urlopen(request, timeout=10)


def _start_server(port: int) -> subprocess.Popen:
    adapter_path = Path(__file__).parents[1] / "adapter-b-fastapi"
    environment = os.environ.copy()
    environment["DATABASE_URL"] = "postgresql://postgres:3421@localhost:5432/agents_db"
    return subprocess.Popen(
        [sys.executable, "-m", "uvicorn", "main:app", "--port", str(port)],
        cwd=adapter_path,
        env=environment,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )


def _wait_for_server(port: int) -> None:
    deadline = time.monotonic() + 15
    while time.monotonic() < deadline:
        try:
            with urlopen(f"http://127.0.0.1:{port}/healthz", timeout=1) as response:
                if response.status == 200:
                    return
        except (URLError, OSError):
            time.sleep(0.1)
    raise AssertionError("uvicorn did not start")


def test_duplicate_no_worker_call(request_body):
    with TestClient(app) as client:
        first = client.post("/v1/requests", json=request_body)
        second = client.post("/v1/requests", json=request_body)

    assert first.status_code == 200
    assert second.status_code == 200
    assert second.json()["duplicate"] is True
    assert second.json()["result"] == first.json()["result"]
    assert stub.call_count == 1
    assert _count_effects(request_body) == 1


def test_payload_mismatch(request_body):
    with TestClient(app) as client:
        first = client.post("/v1/requests", json=request_body)
        changed = {**request_body, "payload": {"input": "changed"}}
        second = client.post("/v1/requests", json=changed)

    assert first.status_code == 200
    assert second.status_code == 409
    assert second.json()["detail"]["message"] == "payload_mismatch"
    assert stub.call_count == 1
    assert _count_effects(request_body) == 1


def test_concurrent_identical(request_body):
    with TestClient(app) as client:
        with ThreadPoolExecutor(max_workers=10) as executor:
            responses = list(
                executor.map(
                    lambda _: client.post("/v1/requests", json=request_body),
                    range(10),
                )
            )

    assert all(response.status_code == 200 for response in responses)
    assert stub.call_count == 1
    assert _count_effects(request_body) == 1


def test_scenario_4_restart_then_retry(request_body):
    port = _free_port()
    server = _start_server(port)
    try:
        _wait_for_server(port)
        first = _post_to_server(port, request_body)
        first_body = first.read()
        assert first.status == 200
    finally:
        server.terminate()
        server.wait(timeout=10)

    restarted = _start_server(port)
    try:
        _wait_for_server(port)
        retry = _post_to_server(port, request_body)
        retry_body = retry.read()
        assert retry.status == 200
    finally:
        restarted.terminate()
        restarted.wait(timeout=10)

    import json

    assert json.loads(retry_body)["duplicate"] is True
    assert json.loads(retry_body)["result"] == json.loads(first_body)["result"]
    assert _count_effects(request_body) == 1


def test_scenario_3_response_lost(request_body):
    with TestClient(app) as client:
        import os

        old_value = os.environ.get("FAULT_INJECTION")
        os.environ["FAULT_INJECTION"] = "true"
        try:
            lost = client.post(
                "/v1/requests",
                json=request_body,
                headers={"X-Fault": "drop-response"},
            )
        finally:
            if old_value is None:
                os.environ.pop("FAULT_INJECTION", None)
            else:
                os.environ["FAULT_INJECTION"] = old_value
        retry = client.post("/v1/requests", json=request_body)

    assert lost.status_code == 503
    assert retry.status_code == 200
    assert retry.json()["duplicate"] is True
    assert stub.call_count == 1
    assert _count_effects(request_body) == 1


def test_stale_claim_recovered_on_retry(request_body, monkeypatch):
    from db import open_pool, transaction

    monkeypatch.setenv("STALE_CLAIM_SECONDS", "30")
    open_pool()
    with transaction() as connection:
        connection.execute(
            """
            INSERT INTO adapter_b.requests
                (dialog_id, request_id, task_id, payload_hash, status, claimed_at)
            VALUES (%s, %s, %s, %s, 'PROCESSING', now() - interval '60 seconds')
            """,
            (
                request_body["dialog_id"],
                request_body["request_id"],
                request_body["task_id"],
                _payload_hash(request_body["payload"]),
            ),
        )

    with TestClient(app) as client:
        response = client.post("/v1/requests", json=request_body)

    assert response.status_code == 200
    assert response.json()["status"] == "COMPLETED"
    assert stub.call_count == 1
    open_pool()
    with transaction() as connection:
        attempt_count = connection.execute(
            """
            SELECT attempt_count FROM adapter_b.requests
            WHERE dialog_id = %s AND request_id = %s
            """,
            (request_body["dialog_id"], request_body["request_id"]),
        ).fetchone()[0]
        effect_count = connection.execute(
            """
            SELECT count(*) FROM adapter_b.effects
            WHERE dialog_id = %s AND request_id = %s
            """,
            (request_body["dialog_id"], request_body["request_id"]),
        ).fetchone()[0]
        recovery_events = connection.execute(
            """
            SELECT count(*) FROM adapter_b.dialog_events
            WHERE dialog_id = %s
              AND request_id = %s
              AND event_type = 'STALE_CLAIM_DETECTED'
            """,
            (request_body["dialog_id"], request_body["request_id"]),
        ).fetchone()[0]

    assert attempt_count == 2
    assert effect_count == 1
    assert recovery_events == 1


def test_late_fail_after_takeover(request_body):
    from db import open_pool, transaction

    import os

    old_stale_seconds = os.environ.get("STALE_CLAIM_SECONDS")
    os.environ["STALE_CLAIM_SECONDS"] = "30"
    open_pool()
    try:
        with transaction() as connection:
            connection.execute(
                """
                INSERT INTO adapter_b.requests
                    (dialog_id, request_id, task_id, payload_hash, status)
                VALUES (%s, %s, %s, %s, 'PROCESSING')
                """,
                (
                    request_body["dialog_id"],
                    request_body["request_id"],
                    request_body["task_id"],
                    _payload_hash(request_body["payload"]),
                ),
            )
            connection.execute(
                """
                UPDATE adapter_b.requests
                SET claimed_at = now() - interval '60 seconds'
                WHERE dialog_id = %s AND request_id = %s
                """,
                (request_body["dialog_id"], request_body["request_id"]),
            )

        request = RequestModel.model_validate(request_body)
        claimed, attempt, replay = _claim_or_replay(
            request,
            _payload_hash(request_body["payload"]),
        )
        assert claimed is True
        assert attempt == 2
        assert replay is None

        assert _complete_request(request, {"worker": "replacement"}, attempt) is True
        assert _fail_request(request, "late failure", attempt) is False
        with transaction() as connection:
            status = connection.execute(
                """
                SELECT status FROM adapter_b.requests
                WHERE dialog_id = %s AND request_id = %s
                """,
                (request_body["dialog_id"], request_body["request_id"]),
            ).fetchone()[0]
            effect_count = connection.execute(
                """
                SELECT count(*) FROM adapter_b.effects
                WHERE dialog_id = %s AND request_id = %s
                """,
                (request_body["dialog_id"], request_body["request_id"]),
            ).fetchone()[0]

        assert status == "COMPLETED"
        assert effect_count == 1
    finally:
        if old_stale_seconds is None:
            os.environ.pop("STALE_CLAIM_SECONDS", None)
        else:
            os.environ["STALE_CLAIM_SECONDS"] = old_stale_seconds


def test_fenced_old_attempt_cannot_fail_new_attempt(request_body, monkeypatch):
    from db import open_pool, transaction

    monkeypatch.setenv("STALE_CLAIM_SECONDS", "30")
    request = RequestModel.model_validate(request_body)
    open_pool()
    claimed, attempt, replay = _claim_or_replay(
        request,
        _payload_hash(request.payload),
    )
    assert claimed is True
    assert attempt == 1
    assert replay is None

    with transaction() as connection:
        connection.execute(
            """
            UPDATE adapter_b.requests
            SET claimed_at = now() - interval '60 seconds'
            WHERE dialog_id = %s AND request_id = %s
            """,
            (request.dialog_id, request.request_id),
        )

    claimed, new_attempt, replay = _claim_or_replay(
        request,
        _payload_hash(request.payload),
    )
    assert claimed is True
    assert new_attempt == 2
    assert replay is None

    assert _fail_request(request, "late failure", attempt) is False
    with transaction() as connection:
        state = connection.execute(
            """
            SELECT status, attempt_count
            FROM adapter_b.requests
            WHERE dialog_id = %s AND request_id = %s
            """,
            (request.dialog_id, request.request_id),
        ).fetchone()
        failed_events = connection.execute(
            """
            SELECT count(*)
            FROM adapter_b.dialog_events
            WHERE dialog_id = %s
              AND request_id = %s
              AND event_type = 'FAILED'
            """,
            (request.dialog_id, request.request_id),
        ).fetchone()[0]

    assert state == ("PROCESSING", 2)
    assert failed_events == 0
    assert _complete_request(request, {"worker": "replacement"}, new_attempt) is True


def test_fenced_old_attempt_cannot_complete(request_body, monkeypatch):
    from db import open_pool, transaction

    monkeypatch.setenv("STALE_CLAIM_SECONDS", "30")
    request = RequestModel.model_validate(request_body)
    open_pool()
    claimed, attempt, replay = _claim_or_replay(
        request,
        _payload_hash(request.payload),
    )
    assert claimed is True
    assert attempt == 1
    assert replay is None

    with transaction() as connection:
        connection.execute(
            """
            UPDATE adapter_b.requests
            SET claimed_at = now() - interval '60 seconds'
            WHERE dialog_id = %s AND request_id = %s
            """,
            (request.dialog_id, request.request_id),
        )

    claimed, new_attempt, replay = _claim_or_replay(
        request,
        _payload_hash(request.payload),
    )
    assert claimed is True
    assert new_attempt == 2
    assert replay is None

    assert _complete_request(request, {"worker": "old"}, attempt) is False
    with transaction() as connection:
        state = connection.execute(
            """
            SELECT status, attempt_count
            FROM adapter_b.requests
            WHERE dialog_id = %s AND request_id = %s
            """,
            (request.dialog_id, request.request_id),
        ).fetchone()
        effect_count = connection.execute(
            """
            SELECT count(*)
            FROM adapter_b.effects
            WHERE dialog_id = %s AND request_id = %s
            """,
            (request.dialog_id, request.request_id),
        ).fetchone()[0]

    assert state == ("PROCESSING", 2)
    assert effect_count == 0


def test_slow_worker_takeover(request_body, monkeypatch):
    monkeypatch.setenv("STALE_CLAIM_SECONDS", "1")
    request_body["payload"] = {"delay_ms": 3000}

    with TestClient(app) as client:
        with ThreadPoolExecutor(max_workers=1) as executor:
            first_future = executor.submit(
                client.post,
                "/v1/requests",
                json=request_body,
            )
            time.sleep(1.5)
            retry = client.post("/v1/requests", json=request_body)
            first = first_future.result(timeout=10)

    assert first.status_code == 200
    assert retry.status_code == 200
    assert first.json()["status"] in {"COMPLETED", "IN_PROGRESS"}
    assert retry.json()["status"] == "COMPLETED"
    assert stub.call_count == 2
    assert _count_effects(request_body) == 1
    from db import open_pool, transaction

    open_pool()
    with transaction() as connection:
        state = connection.execute(
            """
            SELECT status, error_message FROM adapter_b.requests
            WHERE dialog_id = %s AND request_id = %s
            """,
            (request_body["dialog_id"], request_body["request_id"]),
        ).fetchone()
        failed_effects = connection.execute(
            """
            SELECT count(*)
            FROM adapter_b.requests r
            JOIN adapter_b.effects e
              ON e.dialog_id = r.dialog_id AND e.request_id = r.request_id
            WHERE r.dialog_id = %s
              AND r.request_id = %s
              AND r.status = 'FAILED'
            """,
            (request_body["dialog_id"], request_body["request_id"]),
        ).fetchone()[0]

    assert state[0] == "COMPLETED"
    assert failed_effects == 0


def test_invalid_agent_mode_is_configuration_error(request_body, monkeypatch):
    from db import open_pool, transaction

    monkeypatch.setenv("AGENT_MODE", "invalid")
    open_pool()
    request = RequestModel.model_validate(request_body)

    with pytest.raises(ValueError, match="Unknown AGENT_MODE"):
        process_request(request)

    with transaction() as connection:
        state = connection.execute(
            """
            SELECT status FROM adapter_b.requests
            WHERE dialog_id = %s AND request_id = %s
            """,
            (request_body["dialog_id"], request_body["request_id"]),
        ).fetchone()[0]

    assert state == "PROCESSING"
