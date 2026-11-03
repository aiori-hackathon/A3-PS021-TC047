package com.adapteraspring.service;

import com.adapteraspring.b.*;
import com.adapteraspring.config.RetryProperties;
import com.adapteraspring.entity.Tasks;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class TaskDispatcher {
    private final TaskWriter taskWriter;
    private final TaskService taskService;
    private final BTransport transport;
    private final RetryProperties retryProperties;

    public TaskDispatcher(TaskWriter taskWriter, TaskService taskService, BTransport transport,
                          RetryProperties retryProperties) {
        this.taskWriter = taskWriter;
        this.taskService = taskService;
        this.transport = transport;
        this.retryProperties = retryProperties;
    }

    public void attempt(UUID taskId) {
        Tasks task = taskWriter.beginAttempt(taskId);
        BRequest request = BRequest.of(task.getDialogId(), task.getTaskId(), task.getRequestId(), task.getPayload());
        try {
            BResponse response = transport.send(request, taskService.takeFault(taskId));
            handleResponse(task, response);
        } catch (BRejectedException exception) {
            taskWriter.applyFailed(taskId, exception.getStatusCode() == 409
                    ? "payload_mismatch" : "contract violation: " + exception.getMessage());
        } catch (BTransientException exception) {
            interruptOrFail(task, exception.getMessage());
        }
    }

    private void handleResponse(Tasks task, BResponse response) {
        if ("COMPLETED".equals(response.status())) {
            taskWriter.applyCompleted(task.getTaskId(), response.result(), response.duplicate());
        } else if ("FAILED".equals(response.status())) {
            taskWriter.applyFailed(task.getTaskId(), response.message());
        } else if ("IN_PROGRESS".equals(response.status())) {
            taskWriter.applyInterrupted(task.getTaskId(), response.message(),
                    Instant.now().plusMillis(retryProperties.getBackoffMs()), "b_in_progress");
        } else {
            interruptOrFail(task, "unparseable B status: " + response.status());
        }
    }

    private void interruptOrFail(Tasks task, String message) {
        if (task.getAttemptCount() >= retryProperties.getMaxAttempts()) {
            taskWriter.applyFailed(task.getTaskId(), "max attempts exceeded");
        } else {
            taskWriter.applyInterrupted(task.getTaskId(), message,
                    Instant.now().plusMillis(retryProperties.getBackoffMs()), "transport_error");
        }
    }
}
