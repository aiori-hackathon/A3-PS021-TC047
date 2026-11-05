package com.adapteraspring.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dialog_events")
public class DialogEvents {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "message")
    private String message;

    @Column(name = "attempt", nullable = false)
    private Integer attempt;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "transport")
    private String transport;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DialogEvents() {
    }

    public static DialogEvents taskCreated(UUID taskId, UUID requestId) {
        DialogEvents event = new DialogEvents();
        event.taskId = taskId;
        event.requestId = requestId;
        event.eventType = "task_created";
        event.attempt = 0;
        return event;
    }

    public static DialogEvents of(UUID taskId, UUID requestId, String type, String message, int attempt) {
        DialogEvents event = new DialogEvents();
        event.taskId = taskId;
        event.requestId = requestId;
        event.eventType = type;
        event.message = message;
        event.attempt = attempt;
        event.transport = "rest";
        return event;
    }
}
