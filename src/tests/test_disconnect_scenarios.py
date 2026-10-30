import faults


VALID_PAYLOAD = {
    "action": "record_order",
    "parameters": {"item": "widget", "quantity": 3},
}


def test_b_down_before_send(
    create_task_helper,
    wait_for_state_helper,
    count_effects_helper,
    stop_b_helper,
    start_b_helper,
):
    stop_b_helper()
    task_id = create_task_helper(VALID_PAYLOAD)
    start_b_helper()
    task = wait_for_state_helper(task_id, {"COMPLETED"})
    assert task["task_id"] == task_id
    assert count_effects_helper(task["dialog_id"], task["request_id"]) == 1


def test_b_killed_while_processing(
    create_task_helper,
    wait_for_state_helper,
    count_effects_helper,
    start_b_helper,
    stop_b_helper,
):
    start_b_helper()
    task_id = create_task_helper(VALID_PAYLOAD)
    faults.crash_during_processing()
    stop_b_helper()
    start_b_helper()
    task = wait_for_state_helper(task_id, {"COMPLETED"})
    assert count_effects_helper(task["dialog_id"], task["request_id"]) == 1


def test_work_completes_but_response_is_lost(
    create_task_helper,
    wait_for_state_helper,
    count_effects_helper,
):
    faults.drop_response()
    task_id = create_task_helper(VALID_PAYLOAD)
    task = wait_for_state_helper(task_id, {"COMPLETED"})
    assert count_effects_helper(task["dialog_id"], task["request_id"]) == 1


def test_b_restart_after_completion(
    create_task_helper,
    wait_for_state_helper,
    count_effects_helper,
    stop_b_helper,
    start_b_helper,
):
    start_b_helper()
    task_id = create_task_helper(VALID_PAYLOAD)
    task = wait_for_state_helper(task_id, {"COMPLETED"})
    stop_b_helper()
    start_b_helper()
    resumed = wait_for_state_helper(task_id, {"COMPLETED"})
    assert resumed["task_id"] == task["task_id"]
    assert count_effects_helper(task["dialog_id"], task["request_id"]) == 1


def test_retry_during_recovery_does_not_duplicate_execution(
    create_task_helper,
    wait_for_state_helper,
    count_effects_helper,
):
    faults.delay_processing(5)
    task_id = create_task_helper(VALID_PAYLOAD)
    faults.crash_before_processing()
    task = wait_for_state_helper(task_id, {"COMPLETED"})
    assert count_effects_helper(task["dialog_id"], task["request_id"]) == 1
