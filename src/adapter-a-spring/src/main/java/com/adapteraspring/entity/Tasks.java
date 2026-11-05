package com.adapteraspring.entity;

import com.adapteraspring.enumerates.StateEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tasks")
public class Tasks {
    @Id
    @Column(name = "task_id")
    private UUID taskId;

    @Column(name = "dialog_id", nullable = false)
    private UUID dialogId;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    private JsonNode payload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result", columnDefinition = "jsonb")
    private JsonNode result;

    @Column(name = "state", nullable = false)
    @Enumerated(EnumType.STRING)
    private StateEnum state;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Tasks() {
    }

    public Tasks(UUID taskId, UUID dialogId, UUID requestId, JsonNode payload, StateEnum state) {
        this.taskId = taskId;
        this.dialogId = dialogId;
        this.requestId = requestId;
        this.payload = payload;
        this.state = state;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public UUID getDialogId() {
        return dialogId;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public JsonNode getResult() {
        return result;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public String getLastError() {
        return lastError;
    }

    public StateEnum getState() {
        return state;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Integer getVersion() {
        return version;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public void markInProgress() { state = StateEnum.IN_PROGRESS; attemptCount++; nextRetryAt = null; }
    public void complete(JsonNode result) { state = StateEnum.COMPLETED; this.result = result; lastError = null; nextRetryAt = null; }
    public void fail(String error) { state = StateEnum.FAILED; lastError = error; nextRetryAt = null; }
    public void interrupt(String error, Instant retryAt) { state = StateEnum.INTERRUPTED; lastError = error; nextRetryAt = retryAt; }
}
