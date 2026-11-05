import os
import time

call_count = 0


def reset_call_count() -> None:
    global call_count
    call_count = 0


def run(payload: dict) -> dict:
    global call_count
    call_count += 1
    delay_ms = payload.get("delay_ms",0)  # read "delay_ms" from payload, default 0
    if delay_ms > 0:
        time.sleep(delay_ms/1000)            # wait that long (seconds, not ms)
    if payload.get("fail") is True :            # payload asks for failure
        raise RuntimeError("simulated failure")
    return {"worker": "stub", "echo": payload}

def pick_greeting():
    mode = os.environ.get("GREET_MODE", "polite")
    if mode == "polite":
        return "Good day"
    elif mode == "casual":
        return "Hey"
    else:
        raise ValueError(f"Unknown GREET_MODE: {mode}")