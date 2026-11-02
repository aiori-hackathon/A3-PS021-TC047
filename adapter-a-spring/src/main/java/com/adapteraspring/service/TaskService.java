package com.adapteraspring.service;

import com.adapteraspring.dto.TaskResponse;
import com.adapteraspring.entity.Tasks;
import com.adapteraspring.planner.Planner;
import com.adapteraspring.validation.PayloadValidator;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

@Service
public class TaskService {
    private final Planner planner;
    private final PayloadValidator payloadValidator;
    private final TaskWriter taskWriter;
    private final boolean faultInjection;

    public TaskService(Planner planner, PayloadValidator payloadValidator, TaskWriter taskWriter,
                       @Value("${fault-injection:false}") boolean faultInjection) {
        this.planner = planner;
        this.payloadValidator = payloadValidator;
        this.taskWriter = taskWriter;
        this.faultInjection = faultInjection;
    }

    public TaskResponse create(JsonNode payload, String faultHeader) {
        JsonNode plannedPayload = planner.plan(payload);
        payloadValidator.validate(plannedPayload);
        Tasks task = taskWriter.saveNew(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), plannedPayload);
        if (faultInjection && faultHeader != null) {
            pendingFaults.put(task.getTaskId(), faultHeader);
        }
        return TaskResponse.from(task);
    }

    public TaskResponse get(UUID taskId) {
        return TaskResponse.from(taskWriter.find(taskId));
    }

    private final java.util.concurrent.ConcurrentMap<UUID, String> pendingFaults =
            new java.util.concurrent.ConcurrentHashMap<>();

    public String takeFault(UUID taskId) { return pendingFaults.remove(taskId); }
}
