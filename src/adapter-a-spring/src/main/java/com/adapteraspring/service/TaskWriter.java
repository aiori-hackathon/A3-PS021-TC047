package com.adapteraspring.service;

import com.adapteraspring.entity.DialogEvents;
import com.adapteraspring.entity.Tasks;
import com.adapteraspring.repository.DialogEventRepository;
import com.adapteraspring.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.UUID;
import com.adapteraspring.enumerates.StateEnum;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TaskWriter {
    private final TaskRepository taskRepository;
    private final DialogEventRepository dialogEventRepository;

    public TaskWriter(TaskRepository taskRepository, DialogEventRepository dialogEventRepository) {
        this.taskRepository = taskRepository;
        this.dialogEventRepository = dialogEventRepository;
    }

    @Transactional
    public Tasks saveNew(UUID taskId, UUID dialogId, UUID requestId, JsonNode payload) {
        Tasks task = new Tasks(taskId, dialogId, requestId, payload,
                com.adapteraspring.enumerates.StateEnum.CREATED);
        Tasks saved = taskRepository.save(task);
        dialogEventRepository.save(DialogEvents.taskCreated(taskId, requestId));
        return saved;
    }

    public Tasks find(UUID taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "task not found"));
    }

    @Transactional
    public Tasks beginAttempt(UUID taskId) {
        Tasks task = find(taskId);
        task.markInProgress();
        dialogEventRepository.save(DialogEvents.of(task.getTaskId(), task.getRequestId(),
                "sending", null, task.getAttemptCount()));
        return task;
    }

    @Transactional
    public void applyCompleted(UUID taskId, JsonNode result, boolean duplicate) {
        Tasks task = find(taskId);
        task.complete(result);
        dialogEventRepository.save(DialogEvents.of(taskId, task.getRequestId(), "completed",
                "duplicate=" + duplicate, task.getAttemptCount()));
    }

    @Transactional
    public void applyFailed(UUID taskId, String message) {
        Tasks task = find(taskId);
        task.fail(message);
        dialogEventRepository.save(DialogEvents.of(taskId, task.getRequestId(), "failed",
                message, task.getAttemptCount()));
    }

    @Transactional
    public void applyInterrupted(UUID taskId, String message, java.time.Instant retryAt, String eventType) {
        Tasks task = find(taskId);
        task.interrupt(message, retryAt);
        dialogEventRepository.save(DialogEvents.of(taskId, task.getRequestId(), eventType,
                message, task.getAttemptCount()));
    }

    @Transactional
    public void recoverInProgress() {
        for (Tasks task : taskRepository.findByState(StateEnum.IN_PROGRESS)) {
            task.interrupt("recovered after restart", java.time.Instant.now());
            dialogEventRepository.save(DialogEvents.of(task.getTaskId(), task.getRequestId(),
                    "recovered_after_restart", "recovered after restart", task.getAttemptCount()));
        }
    }
}
