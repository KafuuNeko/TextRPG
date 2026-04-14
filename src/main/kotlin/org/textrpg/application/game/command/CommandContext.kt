package org.textrpg.application.game.command

/**
 * 指令执行上下文接口
 *
 * 为指令的 Requires 校验和 Handler 执行提供游戏状态访问。
 * 由游戏层实现，将玩家状态、属性、会话等信息暴露给指令系统。
 *
 * 上下文与单个玩家绑定，每次指令处理创建或复用对应玩家的上下文。
 *
 * 使用示例：
 * ```kotlin
 * class PlayerCommandContext(
 *     override val playerId: String,
 *     private val attributes: AttributeContainer,
 *     private val sessionManager: SessionManager
 * ) : CommandContext {
 *     override val isRegistered: Boolean get() = attributes.contains("level")
 *     override fun getAttributeValue(key: String) = attributes.getValue(key)
 *     // ...
 * }
 * ```
 */
interface CommandContext {

    /** 玩家标识符（如 QQ 号） */
    val playerId: String

    /** 玩家是否已注册角色 */
    val isRegistered: Boolean

    /**
     * 获取玩家属性值
     *
     * @param key 属性标识符
     * @return 属性有效值，属性不存在时返回 null
     */
    fun getAttributeValue(key: String): Double?

    /**
     * 修改玩家属性值
     *
     * 用于指令执行时扣除消耗（Cost）等操作。
     *
     * @param key 属性标识符
     * @param delta 变化量（负数为扣除）
     */
    fun modifyAttribute(key: String, delta: Double)

    /** 当前会话类型（如 "combat"、"shop"），不在会话中时为 null */
    val currentSessionType: String?

    /** 当前地图节点的标签集合 */
    fun getCurrentNodeTags(): Set<String>

    /**
     * 检查是否拥有指定物品
     *
     * @param itemId 物品模板 ID
     * @param quantity 所需数量
     */
    fun hasItem(itemId: String, quantity: Int = 1): Boolean

    /**
     * 检查是否拥有指定 Buff
     *
     * @param buffId Buff 标识符
     */
    fun hasBuff(buffId: String): Boolean

    /**
     * 向玩家发送消息
     *
     * @param message 消息文本
     */
    suspend fun reply(message: String)
}
