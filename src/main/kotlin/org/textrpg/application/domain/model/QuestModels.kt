package org.textrpg.application.domain.model

/**
 * 任务目标类型枚举
 *
 * 引擎内置常见目标类型，脚本可通过 [SCRIPT] 类型扩展自定义判断逻辑。
 */
enum class ObjectiveType(val value: String) {
    /** 收集指定物品 */
    COLLECT_ITEM("collect_item"),
    /** 击杀指定敌人 N 次 */
    KILL_ENEMY("kill_enemy"),
    /** 到达指定地图节点 */
    REACH_NODE("reach_node"),
    /** 与指定 NPC 对话 */
    TALK_TO_NPC("talk_to_npc"),
    /** 自定义脚本判断 */
    SCRIPT("script");

    companion object {
        fun fromValue(value: String): ObjectiveType =
            entries.find { it.value == value } ?: SCRIPT
    }
}

/**
 * 任务目标定义
 *
 * @property type 目标类型
 * @property target 目标标识（物品 ID / 敌人 ID / 节点 ID / NPC ID / 脚本路径）
 * @property quantity 所需数量（默认 1）
 * @property scriptPath 脚本路径（仅 [ObjectiveType.SCRIPT] 使用）
 */
data class QuestObjective(
    val type: ObjectiveType,
    val target: String = "",
    val quantity: Int = 1,
    val scriptPath: String? = null
)

/**
 * 任务奖励物品
 */
data class QuestRewardItem(
    val item: String,
    val quantity: Int = 1
)

/**
 * 任务奖励配置
 *
 * @property exp 经验值奖励
 * @property items 物品奖励列表
 * @property attributeChanges 属性变化映射（如好感度）
 */
data class QuestRewards(
    val exp: Double = 0.0,
    val items: List<QuestRewardItem> = emptyList(),
    val attributeChanges: Map<String, Double> = emptyMap()
)

/**
 * 任务解锁配置
 *
 * @property quests 完成后解锁的任务 ID 列表
 */
data class QuestUnlocks(
    val quests: List<String> = emptyList()
)

/**
 * 任务定义（不可变领域模型）
 *
 * 框架提供任务引擎（进度跟踪 + 目标检查 + 奖励发放），
 * 任务内容可以是 YAML 预设的，也可以由 AI 动态生成。
 *
 * @property key 任务唯一标识符
 * @property displayName 显示名称
 * @property description 任务描述
 * @property giver 任务发放 NPC 的 key
 * @property objectives 目标列表（全部完成才算任务完成）
 * @property rewards 奖励配置
 * @property requires 接取前置条件
 * @property unlocks 完成后解锁
 */
data class QuestDefinition(
    val key: String,
    val displayName: String = "",
    val description: String = "",
    val giver: String = "",
    val objectives: List<QuestObjective> = emptyList(),
    val rewards: QuestRewards = QuestRewards(),
    val requires: RequiresDefinition = RequiresDefinition(),
    val unlocks: QuestUnlocks = QuestUnlocks()
)
