import faults

VALID_PAYLOAD = {
    "action": "record_order",
    "parameters": {"item": "widget", "quantity": 3},
}


def test_same_request_id_with_different_payload_is_rejected_without_retry():
    raise NotImplementedError(
        "TODO(confirm with Person 2): expose a way to submit a caller-selected "
        "request_id and verify Adapter B rejection without retry"
    )


def test_a_restart_mid_task_resumes_same_ids(
    create_task_helper,
    wait_for_state_helper,
    stop_a_helper,
    start_a_helper,
):
    stop_a_helper()
    task_id = create_task_helper(VALID_PAYLOAD)
    start_a_helper()
    task = wait_for_state_helper(task_id, {"COMPLETED"})
    assert task["task_id"] == task_id


def test_planner_not_invoked_on_retry():
    raise NotImplementedError(
        "TODO(confirm with Person 2): expose planner invocation counter or "
        "sufficient dialog_events evidence"
    )
