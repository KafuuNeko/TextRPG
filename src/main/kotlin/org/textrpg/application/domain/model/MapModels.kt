package org.textrpg.application.domain.model

/**
 * 地图节点连接（不可变领域模型）
 *
 * 有向图的一条边，从当前节点指向 [target] 节点。
 * 连接可附带前置条件，不满足时玩家无法通行。
 *
 * @property target 目标节点 key
 * @property display 连接显示文本（如 "→ 前往集市大街"）
 * @property requires 通行前置条件（可选）
 */
data class NodeConnection(
    val target: String,
    val display: String = "",
    val requires: RequiresDefinition = RequiresDefinition()
)

/**
 * 节点上的实体列表
 *
 * @property npcs NPC ID 列表
 * @property enemies 敌人 ID 列表
 */
data class NodeEntities(
    val npcs: List<String> = emptyList(),
    val enemies: List<String> = emptyList()
)

/**
 * 地图节点定义（不可变领域模型）
 *
 * 有向图的一个节点。通过 [connections] 与其他节点连接。
 * 节点可以是 YAML 预设的，也可以由 AI 动态生成（Step 7）。
 *
 * @property key 节点唯一标识符
 * @property displayName 显示名称
 * @property description 节点描述文本
 * @property tags 功能标签集合（如 "safe_zone"、"shop"、"gatherable"）
 * @property connections 出方向连接列表
 * @property entities 当前节点上的实体
 * @property isBoundary 是否为边界节点（可触发 AI 动态生成新区域）
 */
data class MapNodeDefinition(
    val key: String,
    val displayName: String = "",
    val description: String = "",
    val tags: Set<String> = emptySet(),
    val connections: List<NodeConnection> = emptyList(),
    val entities: NodeEntities = NodeEntities(),
    val isBoundary: Boolean = false
)
