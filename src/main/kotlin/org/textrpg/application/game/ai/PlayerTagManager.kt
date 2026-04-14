package org.textrpg.application.game.ai

import java.util.concurrent.ConcurrentHashMap

/**
 * 玩家 Tag 管理器
 *
 * 维护玩家的轻量级标签系统，替代完整聊天记录传输。
 * 与 AI 交互时，只传输 Tag 列表作为前置设定，实现低成本的长线记忆。
 *
 * Tag 示例：
 * - "killed_dragon_boss"
 * - "allied_with_merchant_li"
 * - "explored_dark_forest"
 * - "level_10_warrior"
 *
 * Tag 由游戏事件自动生成（战斗结算、任务完成、NPC 对话等），
 * 也可由脚本手动添加/移除。
 */
class PlayerTagManager {

    /** 玩家标签映射（playerId → 标签集合） */
    private val playerTags = ConcurrentHashMap<String, MutableSet<String>>()

    /**
     * 添加标签
     */
    fun addTag(playerId: String, tag: String) {
        playerTags.getOrPut(playerId) { ConcurrentHashMap.newKeySet() }.add(tag)
    }

    /**
     * 移除标签
     */
    fun removeTag(playerId: String, tag: String) {
        playerTags[playerId]?.remove(tag)
    }

    /**
     * 获取玩家的所有标签
     */
    fun getTags(playerId: String): Set<String> {
        return playerTags[playerId]?.toSet() ?: emptySet()
    }

    /**
     * 检查是否拥有指定标签
     */
    fun hasTag(playerId: String, tag: String): Boolean {
        return playerTags[playerId]?.contains(tag) == true
    }

    /**
     * 清除玩家的所有标签
     */
    fun clearTags(playerId: String) {
        playerTags.remove(playerId)
    }

    /**
     * 构建上下文 Prompt 片段
     *
     * 将玩家的 Tag 列表格式化为可嵌入 AI Prompt 的文本。
     *
     * @param playerId 玩家 ID
     * @return Prompt 片段，无标签时返回空字符串
     */
    fun buildContextPrompt(playerId: String): String {
        val tags = getTags(playerId)
        if (tags.isEmpty()) return ""

        return buildString {
            appendLine("[PLAYER CONTEXT]")
            appendLine("Player traits and history:")
            tags.forEach { tag ->
                appendLine("- $tag")
            }
            append("[END PLAYER CONTEXT]")
        }
    }
}
