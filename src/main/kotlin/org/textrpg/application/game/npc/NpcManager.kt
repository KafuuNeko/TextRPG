package org.textrpg.application.game.npc

import org.textrpg.application.data.config.NpcConfig
import org.textrpg.application.domain.model.NpcDefinition
import org.textrpg.application.domain.model.NpcFunction
import java.util.concurrent.ConcurrentHashMap

/**
 * NPC 管理器
 *
 * 管理 NPC 的查询和对话。框架层提供实体管理和对话接口框架，
 * AI 驱动的对话内容由 Step 7 的 LLM 集成支撑——本阶段对话方法返回占位文本。
 *
 * @param npcConfig NPC 配置（从 YAML 加载）
 */
class NpcManager(npcConfig: NpcConfig) {

    /** NPC 定义注册表（支持动态添加，ConcurrentHashMap 保证 AI 线程并发安全） */
    private val npcs: MutableMap<String, NpcDefinition> = ConcurrentHashMap(npcConfig.npcs)

    /**
     * 获取 NPC 定义
     */
    fun getNpc(npcId: String): NpcDefinition? = npcs[npcId]

    /**
     * 获取指定节点上的所有 NPC
     *
     * @param nodeId 地图节点 key
     * @return 该节点上的 NPC 定义列表
     */
    fun getNpcsAtNode(nodeId: String): List<NpcDefinition> {
        return npcs.values.filter { it.location == nodeId }
    }

    /**
     * 开始对话
     *
     * 返回 NPC 的开场白。当前为占位实现，Step 7 接入 LLM 后替换。
     *
     * @param playerId 玩家 ID
     * @param npcId NPC 标识符
     * @return NPC 的开场白文本
     */
    @Suppress("UNUSED_PARAMETER") // playerId 预留给 Step 7 LLM 对话上下文
    fun startDialogue(playerId: Long, npcId: String): String {
        val npc = npcs[npcId] ?: return "找不到该 NPC。"

        val functionsDesc = if (npc.functions.isNotEmpty()) {
            val funcList = npc.functions.joinToString("、") { describeFunctionType(it.type) }
            "\n可用功能：$funcList"
        } else ""

        return "【${npc.displayName}】${npc.description}$functionsDesc"
    }

    /**
     * 处理对话消息
     *
     * 当前为占位实现，返回提示文本。Step 7 接入 LLM 后替换为 AI 生成的回复。
     *
     * @param playerId 玩家 ID
     * @param npcId NPC 标识符
     * @param message 玩家发送的消息
     * @return NPC 的回复文本
     */
    @Suppress("UNUSED_PARAMETER") // playerId, message 预留给 Step 7 LLM 对话
    fun processDialogue(playerId: Long, npcId: String, message: String): String {
        val npc = npcs[npcId] ?: return "找不到该 NPC。"
        // 占位：Step 7 接入 LLM 后，此处构建 AI prompt 并调用 LLM
        return "【${npc.displayName}】（AI 对话功能将在 Step 7 接入）"
    }

    /**
     * 获取 NPC 提供的功能列表
     */
    fun getNpcFunctions(npcId: String): List<NpcFunction> {
        return npcs[npcId]?.functions ?: emptyList()
    }

    /**
     * 动态添加 NPC
     *
     * 供 Step 7 AI 动态生成 NPC 时使用。
     */
    fun addNpc(npc: NpcDefinition) {
        npcs[npc.key] = npc
    }

    private fun describeFunctionType(type: String): String = when (type) {
        "shop" -> "商店"
        "dialogue" -> "对话"
        "quest_giver" -> "任务"
        else -> type
    }
}
