from pydantic import BaseModel, Field, ConfigDict
from typing import Optional, Any
import enum


class StatusEnum(str, enum.Enum):
    COMPLETED = "COMPLETED"
    IN_PROGRESS = "IN_PROGRESS"
    FAILED = "FAILED"

class RequestModel(BaseModel):
    dialog_id:str = Field(min_length=1)
    task_id:str = Field(min_length=1)
    request_id:str = Field(min_length=1)
    payload: dict[str, Any] = Field(min_length=1)
    metadata: Optional[dict] = None
    model_config = ConfigDict(extra="forbid")

class ResponseModel(BaseModel):
    dialog_id: str = Field(min_length=1)
    task_id: str = Field(min_length=1)
    request_id: str = Field(min_length=1)
    status: StatusEnum
    result: Optional[dict] = None
    duplicate: bool
    message: Optional[str] = None
    model_config = ConfigDict(extra="forbid")

# NOTES:
# ID type: str vs UUID, must match what A generates
# metadata: required or optional
# payload: non-empty rule
# extra="forbid" on the request
# the payload-mismatch status, not in the enum yet

