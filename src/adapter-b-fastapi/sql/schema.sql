CREATE SCHEMA IF NOT EXISTS adapter_b;

CREATE TABLE IF NOT EXISTS adapter_b.requests (
    dialog_id TEXT NOT NULL,
    request_id TEXT NOT NULL,
    task_id TEXT NOT NULL,
    payload_hash TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED')),
    result_data JSONB,
    error_message TEXT,
    attempt_count INT NOT NULL DEFAULT 1,
    claimed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (dialog_id, request_id)
);

CREATE TABLE IF NOT EXISTS adapter_b.effects (
    effect_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dialog_id TEXT NOT NULL,
    request_id TEXT NOT NULL,
    effect_type TEXT NOT NULL,
    effect_data JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (dialog_id, request_id),
    FOREIGN KEY (dialog_id, request_id)
        REFERENCES adapter_b.requests(dialog_id, request_id)
);

CREATE TABLE IF NOT EXISTS adapter_b.dialog_events (
    event_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dialog_id TEXT NOT NULL,
    request_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
