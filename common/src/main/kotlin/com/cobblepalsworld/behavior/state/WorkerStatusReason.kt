package com.cobblepalsworld.behavior.state

enum class WorkerStatusKind {
    ACTIVE,
    READY,
    WAITING,
    BLOCKED,
    STANDBY
}

enum class WorkerStatusReason(val label: String, val kind: WorkerStatusKind) {
    READY("Ready", WorkerStatusKind.READY),
    NAVIGATING("Moving", WorkerStatusKind.ACTIVE),
    ARRIVING("Arriving", WorkerStatusKind.ACTIVE),
    WORKING("Working", WorkerStatusKind.ACTIVE),
    DEPOSITING("Returning", WorkerStatusKind.ACTIVE),
    COOLDOWN("Cooldown", WorkerStatusKind.WAITING),
    SEARCH_DELAY("Awaiting", WorkerStatusKind.WAITING),
    ECO_IDLE("Eco Idle", WorkerStatusKind.WAITING),
    NO_TARGET("No Work", WorkerStatusKind.WAITING),
    TARGET_BUSY("Contested", WorkerStatusKind.WAITING),
    PATH_BUDGET("Queued", WorkerStatusKind.WAITING),
    REDSTONE_OFF("Redstone", WorkerStatusKind.BLOCKED),
    TAG_DISABLED("Disabled", WorkerStatusKind.BLOCKED),
    PATHING_STALLED("Pathing", WorkerStatusKind.BLOCKED),
    NO_DEPOSIT("No Deposit", WorkerStatusKind.BLOCKED),
    WORKER_CAP("Standby", WorkerStatusKind.STANDBY),
    ROLE_LOCKED("Held", WorkerStatusKind.STANDBY),
    RESERVED_DUTY("Reserved", WorkerStatusKind.STANDBY)
}