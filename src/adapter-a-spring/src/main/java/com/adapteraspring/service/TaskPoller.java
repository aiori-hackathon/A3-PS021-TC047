package com.adapteraspring.service;

import com.adapteraspring.config.RetryProperties;
import com.adapteraspring.enumerates.StateEnum;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

@Component
public class TaskPoller {
    private static final Logger log = LoggerFactory.getLogger(TaskPoller.class);
    private final com.adapteraspring.repository.TaskRepository taskRepository;
    private final TaskDispatcher dispatcher;

    public TaskPoller(com.adapteraspring.repository.TaskRepository taskRepository, TaskDispatcher dispatcher,
                      RetryProperties retryProperties) {
        this.taskRepository = taskRepository;
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${retry.poll-interval-ms:1000}")
    public void poll() {
        taskRepository.findEligible(List.of(StateEnum.CREATED, StateEnum.INTERRUPTED), Instant.now())
                .forEach(task -> {
                    try {
                        dispatcher.attempt(task.getTaskId());
                    } catch (ObjectOptimisticLockingFailureException exception) {
                        log.warn("Skipping task {} after optimistic locking conflict", task.getTaskId());
                    } catch (RuntimeException exception) {
                        log.error("Task {} attempt failed before outcome was recorded",
                                task.getTaskId(), exception);
                    }
                });
    }
}
