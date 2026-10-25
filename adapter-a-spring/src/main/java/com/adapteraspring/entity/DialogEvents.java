package com.adapteraspring.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dialog_events")
public class DialogEvents {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long event_id;

    @Column(name = "task_id", nullable = false)
    private UUID task_id;

    @Column(name = "event_type", nullable = false)
    private String event_type;

    @Column(name = "message")
    private String message;

    @Column(name = "attempt", nullable = false)
    private Integer attempt;

    @Column(name = "request_id", nullable = false)
    private UUID request_id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant created_at;




}

