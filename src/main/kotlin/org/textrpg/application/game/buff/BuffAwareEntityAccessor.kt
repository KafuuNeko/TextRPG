package org.textrpg.application.game.buff

import org.textrpg.application.domain.model.Modifier
import org.textrpg.application.game.attribute.AttributeContainer
import org.textrpg.application.game.effect.EntityAccessor

/**
 * 支持 Buff 的实体访问器基类
 *
 * 为后续 Step 的实体实现提供标准基类，将属性操作委托给 [AttributeContainer]，
 * Buff 操作委托给 [BuffManager]。子类只需实现物品和消息相关的方法。
 *
 * 这是组合模式的体现——实体的各项能力通过组合不同的管理器实现：
 * - 属性管理 → [AttributeContainer]
 * - Buff 管理 → [BuffManager]
 * - 物品管理 → 由具体实现提供（Step 4 背包服务）
 * - 消息发送 → 由具体实现提供（依赖消息平台适配器）
 *
 * @param attributeContainer 属性容器（提供属性查询/修改/修正器操作）
 * @param buffManager Buff 管理器（提供 Buff 施加/移除/查询）
 */
abstract class BuffAwareEntityAccessor(
    protected val attributeContainer: AttributeContainer,
    protected val buffManager: BuffManager
) : EntityAccessor {

    // ======================== 属性操作（委托 AttributeContainer） ========================

    override fun getAttributeValue(key: String): Double = attributeContainer.getValue(key)

    override fun modifyAttribute(key: String, delta: Double) {
        attributeContainer.modifyBaseValue(key, delta)
    }

    override fun setAttribute(key: String, value: Double) {
        attributeContainer.setBaseValue(key, value)
    }

    override fun addModifier(attributeKey: String, modifier: Modifier) {
        attributeContainer.addModifier(attributeKey, modifier)
    }

    override fun removeModifiersBySource(attributeKey: String, source: String) {
        attributeContainer.removeModifiersBySource(attributeKey, source)
    }

    // ======================== Buff 操作（委托 BuffManager） ========================

    override fun hasBuff(buffId: String): Boolean = buffManager.hasBuff(buffId)

    override fun addBuff(buffId: String, stacks: Int, duration: Int?) {
        buffManager.applyBuff(buffId, stacks, duration)
    }

    override fun removeBuff(buffId: String) {
        buffManager.removeBuff(buffId)
    }

    // ======================== 物品 / 消息（由子类实现） ========================

    // hasItem, giveItem, removeItem, sendMessage 保持 abstract
    // 由具体的实体实现提供（如 PlayerEntityAccessor）
}
