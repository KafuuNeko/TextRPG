package org.textrpg.application.domain.model

/**
 * NPC 功能定义
 *
 * 描述 NPC 提供的一种功能（商店、对话、任务派发等）。
 *
 * @property type 功能类型（"shop" / "dialogue" / "quest_giver" 等）
 * @property params 功能参数映射（如 shop_id、quest_ids 等）
 */
data class NpcFunction(
    val type: String,
    val params: Map<String, String> = emptyMap()
)

/**
 * NPC 定义（不可变领域模型）
 *
 * 从 YAML 配置加载的 NPC 模板。框架层提供实体定义和对话接口，
 * AI 驱动的对话内容由 Step 7 的 LLM 集成支撑。
 *
 * @property key NPC 唯一标识符
 * @property displayName 显示名称
 * @property description NPC 描述
 * @property location 所在地图节点 key
 * @property prompt AI 对话时的角色 Prompt
 * @property functions NPC 提供的功能列表
 * @property allowedTools AI 对话时可调用的工具白名单（权限隔离）
 */
data class NpcDefinition(
    val key: String,
    val displayName: String = "",
    val description: String = "",
    val location: String = "",
    val prompt: String = "",
    val functions: List<NpcFunction> = emptyList(),
    val allowedTools: List<String> = emptyList()
)
