package com.adapteraspring.service;

import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Component;

@Component
public class TaskRecovery {
    private final TaskWriter taskWriter;

    public TaskRecovery(TaskWriter taskWriter) {
        this.taskWriter = taskWriter;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverAfterRestart() {
        taskWriter.recoverInProgress();
    }
}
