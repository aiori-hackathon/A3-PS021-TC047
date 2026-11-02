"""Fault-injection header constants and parsing."""

from dataclasses import dataclass
import re

from fastapi import HTTPException

FAULT_HEADER = "X-Fault"
DROP_RESPONSE = "drop-response"
CRASH_BEFORE = "crash-before"
CRASH_DURING = "crash-during"
DELAY_PREFIX = "delay="
_DELAY_PATTERN = re.compile(r"^delay=(\d+)$")


@dataclass(frozen=True)
class Fault:
    """Validated fault instruction passed from the HTTP door to the core."""

    action: str
    delay_ms: int = 0


def parse_fault(value: str | None, enabled: bool) -> Fault | None:
    """Parse the fault header only when fault injection is explicitly enabled."""
    if not enabled or value is None:
        return None
    if value in {DROP_RESPONSE, CRASH_BEFORE, CRASH_DURING}:
        return Fault(action=value)
    match = _DELAY_PATTERN.fullmatch(value)
    if match is not None:
        return Fault(action=DELAY_PREFIX, delay_ms=int(match.group(1)))
    raise HTTPException(
        status_code=400,
        detail=f"invalid {FAULT_HEADER} value",
    )
