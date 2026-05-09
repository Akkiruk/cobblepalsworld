package com.cobblepalsworld.tag

import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtList
import net.minecraft.nbt.NbtString
import net.minecraft.util.math.BlockPos

enum class RedstoneControlMode(val id: String) {
    ALWAYS("always"),
    HIGH("high"),
    LOW("low"),
    NEVER("never"),
    PULSE("pulse");

    companion object {
        fun fromId(id: String): RedstoneControlMode = entries.firstOrNull { it.id == id } ?: ALWAYS
    }
}

enum class TargetStrategy(val id: String) {
    ROUND_ROBIN("round_robin"),
    NEAREST_FIRST("nearest_first"),
    FURTHEST_FIRST("furthest_first"),
    RANDOM("random");

    companion object {
        fun fromId(id: String): TargetStrategy = entries.firstOrNull { it.id == id } ?: ROUND_ROBIN
    }
}

enum class ActivatorActionMode(val id: String) {
    INTERACT_ONLY("interact_only"),
    ATTACK_ONLY("attack_only"),
    INTERACT_THEN_ATTACK("interact_then_attack");

    companion object {
        fun fromId(id: String): ActivatorActionMode = entries.firstOrNull { it.id == id } ?: INTERACT_ONLY
    }
}

data class TagTarget(
    val dimensionId: String,
    val pos: BlockPos
)

data class TagSettings(
    val extraTargets: List<TagTarget> = emptyList(),
    val targetStrategy: TargetStrategy = TargetStrategy.ROUND_ROBIN,
    val redstoneMode: RedstoneControlMode = RedstoneControlMode.ALWAYS,
    val activatorMode: ActivatorActionMode = ActivatorActionMode.INTERACT_ONLY,
    val regulatorAmount: Int = 64,
    val terminateAfterSuccess: Boolean = false
) {
    companion object {
        val EMPTY = TagSettings()
    }
}

object TagSettingsSerializer {
    private const val KEY_EXTRA_TARGETS = "ExtraTargets"
    private const val KEY_TARGET_STRATEGY = "TargetStrategy"
    private const val KEY_REDSTONE_MODE = "RedstoneMode"
    private const val KEY_ACTIVATOR_MODE = "ActivatorMode"
    private const val KEY_REGULATOR_AMOUNT = "RegulatorAmount"
    private const val KEY_TERMINATE = "TerminateAfterSuccess"

    fun toNbt(settings: TagSettings): NbtCompound {
        val nbt = NbtCompound()
        nbt.putString(KEY_TARGET_STRATEGY, settings.targetStrategy.id)
        nbt.putString(KEY_REDSTONE_MODE, settings.redstoneMode.id)
        nbt.putString(KEY_ACTIVATOR_MODE, settings.activatorMode.id)
        nbt.putInt(KEY_REGULATOR_AMOUNT, settings.regulatorAmount)
        nbt.putBoolean(KEY_TERMINATE, settings.terminateAfterSuccess)

        if (settings.extraTargets.isNotEmpty()) {
            val list = NbtList()
            settings.extraTargets.forEach { target ->
                val entry = NbtCompound()
                entry.putString("Dimension", target.dimensionId)
                entry.putInt("X", target.pos.x)
                entry.putInt("Y", target.pos.y)
                entry.putInt("Z", target.pos.z)
                list.add(entry)
            }
            nbt.put(KEY_EXTRA_TARGETS, list)
        }

        return nbt
    }

    fun fromNbt(nbt: NbtCompound): TagSettings {
        val extraTargets = mutableListOf<TagTarget>()
        if (nbt.contains(KEY_EXTRA_TARGETS)) {
            val list = nbt.getList(KEY_EXTRA_TARGETS, 10)
            for (index in 0 until list.size) {
                val entry = list.getCompound(index)
                if (!entry.contains("Dimension")) continue
                extraTargets += TagTarget(
                    dimensionId = entry.getString("Dimension"),
                    pos = BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z"))
                )
            }
        }

        return TagSettings(
            extraTargets = extraTargets,
            targetStrategy = TargetStrategy.fromId(nbt.getString(KEY_TARGET_STRATEGY)),
            redstoneMode = RedstoneControlMode.fromId(nbt.getString(KEY_REDSTONE_MODE)),
            activatorMode = ActivatorActionMode.fromId(nbt.getString(KEY_ACTIVATOR_MODE)),
            regulatorAmount = if (nbt.contains(KEY_REGULATOR_AMOUNT)) nbt.getInt(KEY_REGULATOR_AMOUNT).coerceIn(1, 64) else 64,
            terminateAfterSuccess = nbt.getBoolean(KEY_TERMINATE)
        )
    }
}