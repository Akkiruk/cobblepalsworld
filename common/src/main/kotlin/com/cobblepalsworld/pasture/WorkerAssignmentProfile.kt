package com.cobblepalsworld.pasture

enum class WorkerAssignmentMode(val label: String) {
    GENERAL("General"),
    PREFERRED("Preferred"),
    RESERVED("Reserved");

    companion object {
        fun fromOrdinal(value: Int): WorkerAssignmentMode = entries.getOrElse(value) { GENERAL }
    }
}

data class WorkerAssignmentProfile(
    val mode: WorkerAssignmentMode = WorkerAssignmentMode.GENERAL,
    val allowFallback: Boolean = true
) {
    fun isDefault(): Boolean = mode == WorkerAssignmentMode.GENERAL && allowFallback

    fun label(): String = when {
        mode == WorkerAssignmentMode.RESERVED -> "Reserved"
        mode == WorkerAssignmentMode.PREFERRED && !allowFallback -> "Restricted"
        mode == WorkerAssignmentMode.PREFERRED -> "Preferred"
        !allowFallback -> "Strict"
        else -> "General"
    }

    fun detail(): String = when {
        mode == WorkerAssignmentMode.RESERVED -> "Held out of general pasture labor until released"
        mode == WorkerAssignmentMode.PREFERRED && !allowFallback -> "Preferred for this role and blocks general fallback while available"
        mode == WorkerAssignmentMode.PREFERRED -> "Preferred for this role but still allows general fallback"
        !allowFallback -> "Keeps fallback disabled for this assignment"
        else -> "Works as part of the general crew pool"
    }
}