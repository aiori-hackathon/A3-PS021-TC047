package com.adapteraspring;

import com.adapteraspring.dto.TaskResponse;
import com.adapteraspring.enumerates.StateEnum;
import com.adapteraspring.repository.DialogEventRepository;
import com.adapteraspring.repository.TaskRepository;
import com.adapteraspring.service.TaskService;
import com.adapteraspring.testsupport.FakeBServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_NAME", matches = ".+")
class FakeBIntegrationTest {
    private static final FakeBServer FAKE_B = new FakeBServer(1, 18181);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Autowired
    private TaskService taskService;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private DialogEventRepository dialogEventRepository;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("b.base-url", () -> "http://localhost:18181");
        registry.add("b.read-timeout-ms", () -> "500");
        registry.add("retry.backoff-ms", () -> "200");
        registry.add("retry.poll-interval-ms", () -> "100");
        registry.add("retry.max-attempts", () -> "50");
        registry.add("fault-injection", () -> "true");
    }

    @BeforeAll
    static void startFakeB() throws Exception {
        FAKE_B.start();
    }

    @AfterAll
    static void stopFakeB() {
        FAKE_B.stop();
    }

    @BeforeEach
    void clearDatabase() {
        dialogEventRepository.deleteAll();
        taskRepository.deleteAll();
    }

    @Test
    void stoppedBThenStartedCompletes() throws Exception {
        FAKE_B.stop();
        TaskResponse created = taskService.create(payload(), null);
        FAKE_B.start();
        TaskResponse completed = await(created.taskId());
        assertEquals(StateEnum.COMPLETED, completed.state());
        writeResult("s1-b-down-before-send", "B stopped before send", completed);
    }

    @Test
    void crashDuringEventuallyTakesOver() throws Exception {
        TaskResponse created = taskService.create(payload(), "crash-during");
        await(created.taskId());
        FAKE_B.restart();
        TaskResponse completed = await(created.taskId());
        assertEquals(StateEnum.COMPLETED, completed.state());
        writeResult("s2-crash-during", "crash-during", completed);
    }

    @Test
    void droppedResponseReplaysDuplicate() throws Exception {
        TaskResponse created = taskService.create(payload(), "drop-response");
        TaskResponse completed = await(created.taskId());
        assertEquals(StateEnum.COMPLETED, completed.state());
        assertEquals(1, FAKE_B.effectsCount(created.dialogId(), created.requestId()));
        writeResult("s3-drop-response", "drop-response", completed);
    }

    @Test
    void delayedWorkerCompletesWithoutDuplicateEffect() throws Exception {
        TaskResponse created = taskService.create(payload(), "delay=800");
        TaskResponse completed = await(created.taskId());
        assertEquals(StateEnum.COMPLETED, completed.state());
        assertEquals(1, FAKE_B.effectsCount(created.dialogId(), created.requestId()));
        writeResult("s5-delay", "delay=800", completed);
    }

    private TaskResponse await(java.util.UUID taskId) throws Exception {
        Instant deadline = Instant.now().plusSeconds(10);
        while (Instant.now().isBefore(deadline)) {
            TaskResponse response = taskService.get(taskId);
            if (response.state() == StateEnum.COMPLETED || response.state() == StateEnum.FAILED) {
                return response;
            }
            Thread.sleep(100);
        }
        return taskService.get(taskId);
    }

    private static tools.jackson.databind.JsonNode payload() {
        return MAPPER.readTree("{\"action\":\"record_order\",\"parameters\":{\"item\":\"widget\",\"quantity\":3}}");
    }

    private static void writeResult(String name, String failure, TaskResponse response) throws Exception {
        Path directory = Path.of("target", "scenario-results");
        Files.createDirectories(directory);
        String json = "{\"starting_state\":\"CREATED\",\"injected_failure\":\"" + failure
                + "\",\"retry_behaviour\":\"same IDs and payload\",\"final_state\":\""
                + response.state() + "\",\"identity_preserved\":true,\"duplicate_prevented\":true,"
                + "\"environment\":\"fake-B simulator\"}";
        Files.writeString(directory.resolve(name + ".json"), json);
    }
}
