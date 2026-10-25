package com.adapteraspring.entity;

import com.adapteraspring.enumerates.StateEnum;
import jakarta.persistence.*;
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
    private UUID task_id;

    @Column(name = "dialog_id", nullable = false)
    private UUID dialog_id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    private JsonNode payload;

    @Column(name = "attempt_count", nullable = false)
    private Integer attempt_count = 0;

    @Column(name= "last_error")
    private String last_error;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private StateEnum state;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant created_at;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updated_at;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "next_retry_at")
    private Instant next_retry_at;

    @Column(name = "request_id", nullable = false)
    private UUID request_id;


}
