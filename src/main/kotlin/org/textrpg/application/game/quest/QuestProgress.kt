package org.textrpg.application.game.quest

/**
 * 任务状态枚举
 */
enum class QuestStatus {
    /** 可接取（满足前置条件） */
    AVAILABLE,
    /** 已接取，进行中 */
    ACTIVE,
    /** 所有目标已完成，待交付 */
    COMPLETED,
    /** 已交付奖励 */
    TURNED_IN
}

/**
 * 单个目标的进度
 *
 * @property objectiveIndex 在任务目标列表中的索引
 * @property current 当前进度
 * @property required 所需总量
 * @property completed 是否已完成
 */
data class ObjectiveProgress(
    val objectiveIndex: Int,
    var current: Int = 0,
    val required: Int = 1,
    var completed: Boolean = false
) {
    /** 推进进度 */
    fun advance(amount: Int = 1) {
        current = (current + amount).coerceAtMost(required)
        if (current >= required) {
            completed = true
        }
    }
}

/**
 * 任务进度
 *
 * 跟踪玩家对单个任务的完成情况。
 *
 * @property questId 任务标识符
 * @property objectives 各目标的进度列表
 * @property status 当前任务状态
 */
data class QuestProgress(
    val questId: String,
    val objectives: MutableList<ObjectiveProgress>,
    var status: QuestStatus = QuestStatus.ACTIVE
) {
    /** 所有目标是否都已完成 */
    val allObjectivesCompleted: Boolean
        get() = objectives.all { it.completed }
}
