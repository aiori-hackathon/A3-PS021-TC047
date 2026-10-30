import time
from typing import Any, Callable, Iterable

import pytest
import requests

A_BASE_URL = "http://localhost:8080"
B_BASE_URL = "http://localhost:8000"
POLL_TIMEOUT_SECONDS = 30
POLL_INTERVAL_SECONDS = 1


def create_task(payload: dict[str, Any]) -> str:
    response = requests.post(
        f"{A_BASE_URL}/tasks",
        json={"payload": payload},
        timeout=10,
    )
    response.raise_for_status()
    return response.json()["task_id"]


def get_task(task_id: str) -> dict[str, Any]:
    response = requests.get(f"{A_BASE_URL}/tasks/{task_id}", timeout=10)
    response.raise_for_status()
    return response.json()


def wait_for_state(
    task_id: str,
    states: Iterable[str],
    timeout: float = POLL_TIMEOUT_SECONDS,
) -> dict[str, Any]:
    expected_states = set(states)
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        task = get_task(task_id)
        if task["state"] in expected_states:
            return task
        time.sleep(POLL_INTERVAL_SECONDS)
    raise AssertionError(f"Task {task_id} did not reach {expected_states}")


def count_effects_on_b(dialog_id: str, request_id: str) -> int:
    raise NotImplementedError(
        "TODO(confirm with Person 2): define Adapter B effects-count endpoint or query"
    )


def stop_b() -> None:
    raise NotImplementedError(
        "TODO(confirm with Person 2): define how tests stop Adapter B"
    )


def start_b() -> None:
    raise NotImplementedError(
        "TODO(confirm with Person 2): define how tests start Adapter B"
    )


def stop_a() -> None:
    raise NotImplementedError(
        "TODO(confirm with Person 2): define how tests stop Adapter A"
    )


def start_a() -> None:
    raise NotImplementedError(
        "TODO(confirm with Person 2): define how tests start Adapter A"
    )


@pytest.fixture
def create_task_helper() -> Callable[[dict[str, Any]], str]:
    return create_task


@pytest.fixture
def get_task_helper() -> Callable[[str], dict[str, Any]]:
    return get_task


@pytest.fixture
def wait_for_state_helper() -> Callable[..., dict[str, Any]]:
    return wait_for_state


@pytest.fixture
def count_effects_helper() -> Callable[[str, str], int]:
    return count_effects_on_b


@pytest.fixture
def stop_b_helper() -> Callable[[], None]:
    return stop_b


@pytest.fixture
def start_b_helper() -> Callable[[], None]:
    return start_b


@pytest.fixture
def stop_a_helper() -> Callable[[], None]:
    return stop_a


@pytest.fixture
def start_a_helper() -> Callable[[], None]:
    return start_a
