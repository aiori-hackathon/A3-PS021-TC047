def drop_response() -> None:
    raise NotImplementedError(
        "TODO(confirm with Person 2): define Adapter B response-drop fault hook"
    )


def delay_processing(seconds: float) -> None:
    raise NotImplementedError(
        "TODO(confirm with Person 2): define Adapter B processing-delay fault hook"
    )


def crash_before_processing() -> None:
    raise NotImplementedError(
        "TODO(confirm with Person 2): define Adapter B pre-processing crash hook"
    )


def crash_during_processing() -> None:
    raise NotImplementedError(
        "TODO(confirm with Person 2): define Adapter B mid-processing crash hook"
    )
