package org.textrpg.application.game.map

import org.textrpg.application.data.config.MapConfig
import org.textrpg.application.domain.model.MapNodeDefinition
import org.textrpg.application.domain.model.NodeConnection
import org.textrpg.application.game.command.CommandContext
import org.textrpg.application.game.command.RequiresChecker
import java.util.concurrent.ConcurrentHashMap

/**
 * 移动结果
 *
 * @property success 移动是否成功
 * @property message 失败原因或新位置描述
 * @property newNodeId 新位置节点 key（成功时有效）
 */
data class MoveResult(
    val success: Boolean,
    val message: String? = null,
    val newNodeId: String? = null
) {
    companion object {
        fun success(message: String, nodeId: String) = MoveResult(true, message, nodeId)
        fun failed(message: String) = MoveResult(false, message)
    }
}

/**
 * 地图管理器
 *
 * 管理有向图结构的地图系统：节点查询、玩家位置跟踪、移动逻辑。
 * 支持动态添加节点（Step 7 AI 生成用）。
 *
 * 玩家位置以内存 Map 存储，持久化由外部负责。
 *
 * @param mapConfig 地图配置（从 YAML 加载的节点定义）
 */
class MapManager(mapConfig: MapConfig) {

    /** 节点注册表（支持动态添加） */
    private val nodes: MutableMap<String, MapNodeDefinition> = mapConfig.nodes.toMutableMap()

    /** 玩家位置映射（playerId → nodeId） */
    private val playerLocations = ConcurrentHashMap<String, String>()

    /**
     * 获取节点定义
     */
    fun getNode(nodeId: String): MapNodeDefinition? = nodes[nodeId]

    /**
     * 获取所有节点 key
     */
    fun getAllNodeIds(): Set<String> = nodes.keys.toSet()

    /**
     * 获取玩家当前位置
     */
    fun getPlayerLocation(playerId: String): String? = playerLocations[playerId]

    /**
     * 设置玩家位置（不做移动校验）
     */
    fun setPlayerLocation(playerId: String, nodeId: String) {
        playerLocations[playerId] = nodeId
    }

    /**
     * 获取玩家在当前节点可用的连接（已过滤 Requires）
     *
     * @param playerId 玩家 ID
     * @param context 指令上下文（用于 Requires 校验）
     * @return 满足条件的连接列表
     */
    fun getAvailableConnections(playerId: String, context: CommandContext): List<NodeConnection> {
        val nodeId = playerLocations[playerId] ?: return emptyList()
        val node = nodes[nodeId] ?: return emptyList()
        return node.connections.filter { conn ->
            RequiresChecker.check(conn.requires, context).passed
        }
    }

    /**
     * 执行移动
     *
     * 校验流程：目标节点存在 → 当前节点有到目标的连接 → 连接 Requires 满足。
     *
     * @param playerId 玩家 ID
     * @param targetNodeId 目标节点 key
     * @param context 指令上下文
     * @return 移动结果
     */
    fun move(playerId: String, targetNodeId: String, context: CommandContext): MoveResult {
        val currentNodeId = playerLocations[playerId]
            ?: return MoveResult.failed("玩家位置未初始化")

        val currentNode = nodes[currentNodeId]
            ?: return MoveResult.failed("当前节点不存在：$currentNodeId")

        // 查找连接
        val connection = currentNode.connections.find { it.target == targetNodeId }
            ?: return MoveResult.failed("无法从当前位置前往 $targetNodeId")

        // 检查连接的 Requires
        val requireResult = RequiresChecker.check(connection.requires, context)
        if (!requireResult.passed) {
            return MoveResult.failed(requireResult.message ?: "条件不满足")
        }

        // 目标节点存在性检查
        if (!nodes.containsKey(targetNodeId)) {
            return MoveResult.failed("目标节点不存在：$targetNodeId")
        }

        // 执行移动
        playerLocations[playerId] = targetNodeId

        return MoveResult.success(
            message = getNodeDescription(targetNodeId),
            nodeId = targetNodeId
        )
    }

    /**
     * 格式化节点描述
     *
     * 包含节点名称、描述文本和可用方向。
     */
    fun getNodeDescription(nodeId: String): String {
        val node = nodes[nodeId] ?: return "未知区域"
        return buildString {
            appendLine("【${node.displayName}】")
            if (node.description.isNotBlank()) {
                appendLine(node.description)
            }
            if (node.connections.isNotEmpty()) {
                appendLine("可前往：")
                node.connections.forEach { conn ->
                    appendLine("  ${conn.display.ifEmpty { "→ ${conn.target}" }}")
                }
            }
            if (node.isBoundary) {
                appendLine("（这里是边界区域，可以尝试探索未知领域）")
            }
        }.trimEnd()
    }

    /**
     * 动态添加节点
     *
     * 供 Step 7 AI 动态生成新区域时使用。
     *
     * @param node 新节点定义
     */
    fun addNode(node: MapNodeDefinition) {
        nodes[node.key] = node
    }

    /**
     * 获取当前节点的标签集合
     *
     * 供 CommandContext 实现使用。
     */
    fun getNodeTags(playerId: String): Set<String> {
        val nodeId = playerLocations[playerId] ?: return emptySet()
        return nodes[nodeId]?.tags ?: emptySet()
    }
}
