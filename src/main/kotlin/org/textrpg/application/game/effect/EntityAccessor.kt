package org.textrpg.application.game.effect

import org.textrpg.application.domain.model.Modifier

/**
 * 实体访问器接口
 *
 * 为特效引擎提供对游戏实体（玩家、怪物、NPC）的统一操作接口。
 * 引擎不关心实体的具体实现，只通过此接口进行属性操作、Buff 管理、物品交互和消息发送。
 *
 * 与 [CommandContext] 的区别：CommandContext 面向单玩家指令场景，包含会话、注册状态等指令相关的上下文；
 * EntityAccessor 是实体操作的最小接口，面向任意游戏实体，专注于特效引擎需要的原子操作。
 *
 * 实现注意事项：
 * - 属性操作应委托给 [AttributeContainer]，确保触发器和依赖链的自动处理
 * - 尚未实现的系统（如 Buff、物品）可先提供空实现或 stub
 * - 所有操作应当是线程安全的（单个实体可能被多个效果并发操作）
 *
 * @see org.textrpg.application.game.command.CommandContext
 */
interface EntityAccessor {

    /** 实体唯一标识符（玩家为数据库 ID 的字符串形式，怪物为 "enemy:{key}"） */
    val entityId: String

    /**
     * 获取属性当前有效值（含修正器计算后的最终值）
     *
     * @param key 属性标识符
     * @return 属性有效值
     */
    fun getAttributeValue(key: String): Double

    /**
     * 修改属性基础值（加减）
     *
     * @param key 属性标识符
     * @param delta 变化量（正数增加，负数减少）
     */
    fun modifyAttribute(key: String, delta: Double)

    /**
     * 直接设置属性基础值
     *
     * @param key 属性标识符
     * @param value 新的基础值
     */
    fun setAttribute(key: String, value: Double)

    /**
     * 挂载属性修正器
     *
     * 修正器通过 [Modifier.source] 标识来源，后续可按来源批量移除。
     *
     * @param attributeKey 目标属性标识符
     * @param modifier 修正器实例
     */
    fun addModifier(attributeKey: String, modifier: Modifier)

    /**
     * 按来源移除指定属性的修正器
     *
     * @param attributeKey 目标属性标识符
     * @param source 修正器来源标识
     */
    fun removeModifiersBySource(attributeKey: String, source: String)

    /**
     * 检查是否拥有指定 Buff
     *
     * @param buffId Buff 标识符
     * @return 是否拥有该 Buff
     */
    fun hasBuff(buffId: String): Boolean

    /**
     * 添加 Buff
     *
     * 具体的叠加策略（Refresh / Stack / Independent）由 Buff 系统处理。
     * 在 Buff 系统实现前（Step 3），此方法可为空实现。
     *
     * @param buffId Buff 标识符
     * @param stacks 叠加层数
     * @param duration 持续回合数（null 表示永久）
     */
    fun addBuff(buffId: String, stacks: Int = 1, duration: Int? = null)

    /**
     * 移除 Buff
     *
     * @param buffId Buff 标识符
     */
    fun removeBuff(buffId: String)

    /**
     * 检查是否拥有指定物品
     *
     * @param itemId 物品模板 ID
     * @param quantity 所需数量
     * @return 是否拥有足够数量的该物品
     */
    fun hasItem(itemId: String, quantity: Int = 1): Boolean

    /**
     * 给予物品
     *
     * @param itemId 物品模板 ID
     * @param quantity 数量
     */
    fun giveItem(itemId: String, quantity: Int = 1)

    /**
     * 移除物品
     *
     * @param itemId 物品模板 ID
     * @param quantity 数量
     */
    fun removeItem(itemId: String, quantity: Int = 1)

    /**
     * 向实体发送消息
     *
     * @param message 消息文本
     */
    suspend fun sendMessage(message: String)
}
