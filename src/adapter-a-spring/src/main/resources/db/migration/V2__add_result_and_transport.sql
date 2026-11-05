ALTER TABLE tasks
    ADD COLUMN result jsonb;

ALTER TABLE dialog_events
    ADD COLUMN transport text;
