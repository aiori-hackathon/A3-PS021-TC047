import os
import sys
from pathlib import Path

import pytest

ADAPTER_B = Path(__file__).parents[1] / "adapter-b-fastapi"
sys.path.insert(0, str(ADAPTER_B))
os.environ.setdefault(
    "DATABASE_URL",
    "postgresql://postgres:3421@localhost:5432/agents_db",
)

import db
from worker import stub


@pytest.fixture(autouse=True)
def clean_database():
    stub.reset_call_count()
    db.open_pool()
    db.init_schema()
    with db.transaction() as connection:
        connection.execute("TRUNCATE adapter_b.dialog_events, adapter_b.effects, adapter_b.requests")
    yield
    db.open_pool()
    with db.transaction() as connection:
        connection.execute("TRUNCATE adapter_b.dialog_events, adapter_b.effects, adapter_b.requests")
    db.close_pool()


@pytest.fixture
def request_body():
    return {
        "dialog_id": "test-dialog",
        "task_id": "test-task",
        "request_id": "test-request",
        "payload": {"input": "hello"},
    }
