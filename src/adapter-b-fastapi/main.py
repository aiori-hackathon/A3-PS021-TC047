from contextlib import asynccontextmanager
from collections.abc import AsyncIterator
from fastapi import Header

import os

from fastapi import FastAPI, Request
from fastapi.responses import Response

from core import DropResponse, process_request
from core import _stale_claim_seconds
from db import close_pool, initialize, recover_stale_claims
from faults import FAULT_HEADER, parse_fault
from models import ResponseModel, RequestModel, StatusEnum


@asynccontextmanager
async def lifespan(_: FastAPI) -> AsyncIterator[None]:
    try:
        initialize()
        recover_stale_claims(_stale_claim_seconds())
        yield
    finally:
        close_pool()


app = FastAPI(lifespan=lifespan)

@app.get("/")
def read_root():
    return {"Hello": "World"}

@app.get("/healthz")
def health_check():
    return {"status": "healthy"}



@app.post("/v1/requests", response_model=ResponseModel)
def handle_request(request: RequestModel, x_fault: str | None = Header(default=None)):
    fault = parse_fault(
        x_fault,
        os.environ.get("FAULT_INJECTION", "").lower() == "true",
    )
    try:
        return process_request(request, fault)
    except DropResponse:
        return Response(status_code=503, content=b"")