package com.adapteraspring.controller;

import com.adapteraspring.dto.CreateTaskRequest;
import com.adapteraspring.dto.TaskResponse;
import com.adapteraspring.service.TaskService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

@RestController
@RequestMapping("/tasks")
@Tag(name = "Tasks", description = "Submit tasks and retrieve their processing status")
public class TaskController {
    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a task", description = "Submits a task for asynchronous processing.")
    public TaskResponse create(@Valid @RequestBody CreateTaskRequest request,
                               @RequestHeader(value = "X-Fault", required = false) String faultHeader) {
        return taskService.create(request.payload(), faultHeader);
    }

    @GetMapping("/{taskId}")
    @Operation(summary = "Get task status", description = "Returns the current state and result of a task.")
    public TaskResponse get(@PathVariable UUID taskId) {
        return taskService.get(taskId);
    }
}
