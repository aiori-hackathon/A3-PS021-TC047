CREATE TABLE tasks(
                         task_id          uuid PRIMARY KEY,
                         dialog_id        uuid UNIQUE NOT NULL,
                         payload          jsonb NOT NULL,
                         attempt_count    integer NOT NULL DEFAULT 0,
                         last_error       text,
                         state            text NOT NULL check ( state in ('CREATED', 'INTERRUPTED', 'IN_PROGRESS', 'COMPLETED', 'FAILED')),
                         created_at       timestamptz NOT NULL DEFAULT now(),
                         updated_at       timestamptz NOT NULL DEFAULT now(),
                         version          integer NOT NULL DEFAULT 0,
                         next_retry_at    timestamptz,
                         request_id       uuid UNIQUE NOT NULL
);

CREATE TABLE dialog_events(
                            event_id         bigint generated always as identity PRIMARY KEY,
                            task_id          uuid NOT NULL REFERENCES tasks(task_id),
                            event_type       text NOT NULL,
                            message          text,
                            attempt          integer NOT NULL,
                            request_id       uuid NOT NULL,
                            created_at       timestamptz NOT NULL DEFAULT now()
);