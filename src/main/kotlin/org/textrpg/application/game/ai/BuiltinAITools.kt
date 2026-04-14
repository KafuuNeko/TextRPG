package org.textrpg.application.game.ai

import org.textrpg.application.adapter.llm.model.ToolDefinition
import org.textrpg.application.adapter.llm.model.ToolParameter
import org.textrpg.application.domain.model.MapNodeDefinition
import org.textrpg.application.domain.model.NodeConnection
import org.textrpg.application.domain.model.NpcDefinition
import org.textrpg.application.domain.model.QuestDefinition
import org.textrpg.application.game.map.MapManager
import org.textrpg.application.game.npc.NpcManager
import org.textrpg.application.game.quest.QuestManager

/**
 * 内置 AI 工具注册
 *
 * 将框架的各个管理器功能暴露为 AI 可调用的工具。
 * 每个工具有定义（描述给 LLM）和执行逻辑（调用管理器方法）。
 *
 * 内置工具列表：
 * - `send_message` — 发送消息（NPC 对话用）
 * - `cast_skill` — 使用技能（Boss AI 用）
 * - `create_node` — 创建地图节点（世界生成用）
 * - `create_npc` — 创建 NPC（世界生成用）
 * - `create_quest` — 创建任务（动态任务用）
 */
object BuiltinAITools {

    /**
     * 注册所有内置 AI 工具到注册表
     *
     * @param registry 工具注册表
     * @param mapManager 地图管理器（可选，不提供则相关工具不注册）
     * @param npcManager NPC 管理器（可选）
     * @param questManager 任务管理器（可选）
     */
    fun registerAll(
        registry: AIToolRegistry,
        mapManager: MapManager? = null,
        npcManager: NpcManager? = null,
        questManager: QuestManager? = null
    ) {
        // send_message — 通用消息发送
        registry.register("send_message", AIToolHandler(
            definition = ToolDefinition(
                name = "send_message",
                description = "Send a message or dialogue text to the player",
                parameters = mapOf(
                    "message" to ToolParameter(type = "string", description = "The message text", required = true)
                )
            ),
            handler = { args ->
                val message = args["message"]?.toString() ?: ""
                // 实际发送由场景管理器处理，这里返回文本
                message
            }
        ))

        // cast_skill — Boss AI 使用技能
        registry.register("cast_skill", AIToolHandler(
            definition = ToolDefinition(
                name = "cast_skill",
                description = "Use a combat skill. Choose from available skills.",
                parameters = mapOf(
                    "skill_id" to ToolParameter(type = "string", description = "The skill ID to use", required = true),
                    "speech" to ToolParameter(type = "string", description = "Optional battle cry or dialogue")
                )
            ),
            handler = { args ->
                val skillId = args["skill_id"]?.toString() ?: ""
                val speech = args["speech"]?.toString() ?: ""
                "skill:$skillId${if (speech.isNotBlank()) "|speech:$speech" else ""}"
            }
        ))

        // create_node — 创建地图节点
        if (mapManager != null) {
            registry.register("create_node", AIToolHandler(
                definition = ToolDefinition(
                    name = "create_node",
                    description = "Create a new map node (location/area)",
                    parameters = mapOf(
                        "key" to ToolParameter(type = "string", description = "Unique node identifier", required = true),
                        "display_name" to ToolParameter(type = "string", description = "Display name", required = true),
                        "description" to ToolParameter(type = "string", description = "Location description", required = true),
                        "tags" to ToolParameter(type = "string", description = "Comma-separated tags (e.g. 'safe_zone,shop')"),
                        "connect_to" to ToolParameter(type = "string", description = "Node key to connect to (bidirectional)")
                    )
                ),
                handler = { args ->
                    val key = args["key"]?.toString() ?: return@AIToolHandler "Error: missing key"
                    val displayName = args["display_name"]?.toString() ?: key
                    val description = args["description"]?.toString() ?: ""
                    val tags = args["tags"]?.toString()?.split(",")?.map { it.trim() }?.toSet() ?: emptySet()
                    val connectTo = args["connect_to"]?.toString()

                    val connections = if (connectTo != null) {
                        listOf(NodeConnection(target = connectTo, display = "→ 前往$connectTo"))
                    } else emptyList()

                    val node = MapNodeDefinition(
                        key = key,
                        displayName = displayName,
                        description = description,
                        tags = tags,
                        connections = connections
                    )
                    mapManager.addNode(node)
                    "Node created: $key ($displayName)"
                }
            ))
        }

        // create_npc — 创建 NPC
        if (npcManager != null) {
            registry.register("create_npc", AIToolHandler(
                definition = ToolDefinition(
                    name = "create_npc",
                    description = "Create a new NPC character",
                    parameters = mapOf(
                        "key" to ToolParameter(type = "string", description = "Unique NPC identifier", required = true),
                        "display_name" to ToolParameter(type = "string", description = "NPC display name", required = true),
                        "description" to ToolParameter(type = "string", description = "NPC description", required = true),
                        "location" to ToolParameter(type = "string", description = "Map node key where NPC is located", required = true),
                        "prompt" to ToolParameter(type = "string", description = "AI dialogue role prompt")
                    )
                ),
                handler = { args ->
                    val key = args["key"]?.toString() ?: return@AIToolHandler "Error: missing key"
                    val npc = NpcDefinition(
                        key = key,
                        displayName = args["display_name"]?.toString() ?: key,
                        description = args["description"]?.toString() ?: "",
                        location = args["location"]?.toString() ?: "",
                        prompt = args["prompt"]?.toString() ?: ""
                    )
                    npcManager.addNpc(npc)
                    "NPC created: $key (${npc.displayName})"
                }
            ))
        }

        // create_quest — 创建任务
        if (questManager != null) {
            registry.register("create_quest", AIToolHandler(
                definition = ToolDefinition(
                    name = "create_quest",
                    description = "Create a new quest/mission",
                    parameters = mapOf(
                        "key" to ToolParameter(type = "string", description = "Unique quest identifier", required = true),
                        "display_name" to ToolParameter(type = "string", description = "Quest display name", required = true),
                        "description" to ToolParameter(type = "string", description = "Quest description", required = true),
                        "giver" to ToolParameter(type = "string", description = "NPC key who gives this quest")
                    )
                ),
                handler = { args ->
                    val key = args["key"]?.toString() ?: return@AIToolHandler "Error: missing key"
                    val quest = QuestDefinition(
                        key = key,
                        displayName = args["display_name"]?.toString() ?: key,
                        description = args["description"]?.toString() ?: "",
                        giver = args["giver"]?.toString() ?: ""
                    )
                    questManager.registerQuest(quest)
                    "Quest created: $key (${quest.displayName})"
                }
            ))
        }
    }
}
