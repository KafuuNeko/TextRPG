package org.textrpg.application.game.quest

import org.textrpg.application.domain.model.ObjectiveType
import org.textrpg.application.domain.model.QuestDefinition
import org.textrpg.application.game.command.CommandContext
import org.textrpg.application.game.command.RequiresChecker
import org.textrpg.application.game.effect.EntityAccessor
import java.util.concurrent.ConcurrentHashMap

/**
 * 任务操作结果
 */
data class QuestResult(
    val success: Boolean,
    val message: String? = null
) {
    companion object {
        fun success(message: String) = QuestResult(true, message)
        fun failed(message: String) = QuestResult(false, message)
    }
}

/**
 * 任务管理器
 *
 * 管理所有玩家的任务进度：接取、追踪、事件驱动更新、交付奖励。
 * 内置 5 种目标类型的进度匹配逻辑，脚本类型由外部校验。
 *
 * 外部系统通过 [onEvent] 报告事件，任务引擎自动匹配活跃任务并更新进度。
 *
 * @param questDefinitions 任务定义映射（从 YAML 加载）
 */
class QuestManager(
    private val questDefinitions: Map<String, QuestDefinition>
) {

    /** 玩家任务进度映射（playerId → 任务进度列表） */
    private val playerQuests = ConcurrentHashMap<Long, MutableList<QuestProgress>>()

    /**
     * 接取任务
     *
     * @param playerId 玩家 ID
     * @param questId 任务标识符
     * @param context 指令上下文（用于 Requires 校验）
     * @return 操作结果
     */
    fun acceptQuest(playerId: Long, questId: String, context: CommandContext): QuestResult {
        val definition = questDefinitions[questId]
            ?: return QuestResult.failed("任务不存在：$questId")

        // 检查是否已接取
        val existing = getQuestProgress(playerId, questId)
        if (existing != null && existing.status != QuestStatus.TURNED_IN) {
            return QuestResult.failed("任务已在进行中")
        }

        // Requires 校验
        val requireResult = RequiresChecker.check(definition.requires, context)
        if (!requireResult.passed) {
            return QuestResult.failed(requireResult.message ?: "条件不满足")
        }

        // 创建进度
        val objectives = definition.objectives.mapIndexed { index, obj ->
            ObjectiveProgress(
                objectiveIndex = index,
                required = obj.quantity
            )
        }.toMutableList()

        val progress = QuestProgress(questId = questId, objectives = objectives)
        playerQuests.getOrPut(playerId) { mutableListOf() }.add(progress)

        return QuestResult.success("接取任务：${definition.displayName}")
    }

    /**
     * 获取玩家所有活跃任务
     */
    fun getActiveQuests(playerId: Long): List<QuestProgress> {
        return playerQuests[playerId]
            ?.filter { it.status == QuestStatus.ACTIVE || it.status == QuestStatus.COMPLETED }
            ?: emptyList()
    }

    /**
     * 获取指定任务的状态
     */
    fun getQuestStatus(playerId: Long, questId: String): QuestStatus? {
        return getQuestProgress(playerId, questId)?.status
    }

    /**
     * 检查任务是否已完成（所有目标达成）
     */
    fun isQuestCompleted(playerId: Long, questId: String): Boolean {
        return getQuestProgress(playerId, questId)?.status == QuestStatus.COMPLETED
    }

    /**
     * 处理任务事件
     *
     * 遍历玩家所有活跃任务，匹配事件类型和目标，更新进度。
     * 所有目标完成时自动将任务状态更新为 [QuestStatus.COMPLETED]。
     *
     * @param event 外部系统触发的事件
     */
    fun onEvent(event: QuestEvent) {
        val quests = playerQuests[event.playerId] ?: return

        for (progress in quests) {
            if (progress.status != QuestStatus.ACTIVE) continue

            val definition = questDefinitions[progress.questId] ?: continue

            for ((index, objective) in definition.objectives.withIndex()) {
                if (index >= progress.objectives.size) break
                val objProgress = progress.objectives[index]
                if (objProgress.completed) continue

                val matched = matchEvent(event, objective.type, objective.target)
                if (matched) {
                    val amount = when (event) {
                        is QuestEvent.ItemCollected -> event.quantity
                        else -> 1
                    }
                    objProgress.advance(amount)
                }
            }

            // 检查是否所有目标完成
            if (progress.allObjectivesCompleted && progress.status == QuestStatus.ACTIVE) {
                progress.status = QuestStatus.COMPLETED
            }
        }
    }

    /**
     * 交付任务并发放奖励
     *
     * @param playerId 玩家 ID
     * @param questId 任务标识符
     * @param entityAccessor 玩家实体访问器（用于发放奖励）
     * @return 操作结果
     */
    fun turnInQuest(playerId: Long, questId: String, entityAccessor: EntityAccessor): QuestResult {
        val progress = getQuestProgress(playerId, questId)
            ?: return QuestResult.failed("未接取该任务")

        if (progress.status != QuestStatus.COMPLETED) {
            return QuestResult.failed("任务尚未完成")
        }

        val definition = questDefinitions[questId]
            ?: return QuestResult.failed("任务定义不存在")

        // 发放奖励
        val rewards = definition.rewards
        val rewardMessages = mutableListOf<String>()

        // 经验
        if (rewards.exp > 0) {
            entityAccessor.modifyAttribute("exp", rewards.exp)
            rewardMessages.add("经验 +${rewards.exp.toInt()}")
        }

        // 物品
        for (item in rewards.items) {
            entityAccessor.giveItem(item.item, item.quantity)
            rewardMessages.add("${item.item} x${item.quantity}")
        }

        // 属性变化
        for ((attrKey, delta) in rewards.attributeChanges) {
            entityAccessor.modifyAttribute(attrKey, delta)
            rewardMessages.add("$attrKey ${if (delta >= 0) "+" else ""}${delta.toInt()}")
        }

        // 更新状态
        progress.status = QuestStatus.TURNED_IN

        val rewardText = if (rewardMessages.isNotEmpty()) {
            "奖励：${rewardMessages.joinToString("、")}"
        } else ""

        return QuestResult.success("任务完成：${definition.displayName}！$rewardText")
    }

    /**
     * 动态注册任务定义
     *
     * 供 Step 7 AI 动态生成任务时使用。
     * 注意：questDefinitions 是构造时传入的不可变 Map，
     * 动态任务需要一个额外的可变注册表。
     */
    private val dynamicQuests = ConcurrentHashMap<String, QuestDefinition>()

    fun registerQuest(definition: QuestDefinition) {
        dynamicQuests[definition.key] = definition
    }

    // ======================== 内部辅助 ========================

    private fun getQuestProgress(playerId: Long, questId: String): QuestProgress? {
        return playerQuests[playerId]?.find { it.questId == questId }
    }

    /**
     * 查找任务定义（先查静态，再查动态）
     */
    private fun findDefinition(questId: String): QuestDefinition? {
        return questDefinitions[questId] ?: dynamicQuests[questId]
    }

    /**
     * 匹配事件与目标类型
     */
    private fun matchEvent(event: QuestEvent, objectiveType: ObjectiveType, target: String): Boolean {
        return when (event) {
            is QuestEvent.ItemCollected ->
                objectiveType == ObjectiveType.COLLECT_ITEM && event.itemId == target
            is QuestEvent.EnemyKilled ->
                objectiveType == ObjectiveType.KILL_ENEMY && event.enemyId == target
            is QuestEvent.NodeReached ->
                objectiveType == ObjectiveType.REACH_NODE && event.nodeId == target
            is QuestEvent.NpcTalked ->
                objectiveType == ObjectiveType.TALK_TO_NPC && event.npcId == target
        }
    }
}
