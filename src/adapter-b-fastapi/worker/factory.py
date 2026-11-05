import os
from worker import stub


def get_worker():
    mode = os.environ.get("AGENT_MODE", "stub")
    if mode == "stub":
        return stub
    else:
        raise ValueError(f"Unknown AGENT_MODE: {mode}")