package org.textrpg.application.game.inventory

import org.textrpg.application.game.attribute.AttributeContainer
import org.textrpg.application.game.buff.BuffAwareEntityAccessor
import org.textrpg.application.game.buff.BuffManager

/**
 * 支持背包操作的实体访问器基类
 *
 * 在 [BuffAwareEntityAccessor]（属性 + Buff）的基础上，补全物品操作的实现。
 * 层层叠加的组合模式：
 *
 * ```
 * EntityAccessor
 *   └─ BuffAwareEntityAccessor（属性 + Buff）
 *       └─ InventoryAwareEntityAccessor（+ 物品）
 * ```
 *
 * 子类只需实现 [sendMessage] 即可获得完整的实体操作能力。
 *
 * 注意：物品操作通过 [InventoryService] 委托，需要将 [entityId]（String）
 * 转换为 playerId（Long）。非玩家实体的物品操作为空操作。
 *
 * @param attributeContainer 属性容器
 * @param buffManager Buff 管理器
 * @param inventoryService 背包服务
 */
abstract class InventoryAwareEntityAccessor(
    attributeContainer: AttributeContainer,
    buffManager: BuffManager,
    protected val inventoryService: InventoryService
) : BuffAwareEntityAccessor(attributeContainer, buffManager) {

    /**
     * 将 entityId 转换为 playerId
     *
     * 非玩家实体（怪物等）返回 null，物品操作将静默失败。
     */
    private val playerId: Long?
        get() = entityId.toLongOrNull()

    /**
     * 将 itemId（模板 ID 字符串）转换为 templateId
     */
    private fun parseTemplateId(itemId: String): Int? = itemId.toIntOrNull()

    override fun hasItem(itemId: String, quantity: Int): Boolean {
        val pid = playerId ?: return false
        val tid = parseTemplateId(itemId) ?: return false
        return inventoryService.hasItem(pid, tid, quantity)
    }

    override fun giveItem(itemId: String, quantity: Int) {
        val pid = playerId ?: return
        val tid = parseTemplateId(itemId) ?: return
        inventoryService.addItem(pid, tid, quantity)
    }

    override fun removeItem(itemId: String, quantity: Int) {
        val pid = playerId ?: return
        val tid = parseTemplateId(itemId) ?: return
        inventoryService.removeItem(pid, tid, quantity)
    }
}
