package org.textrpg.application.game.equipment

import org.textrpg.application.data.repository.ItemRepository
import org.textrpg.application.data.repository.PlayerEquipment
import org.textrpg.application.data.repository.PlayerInventoryItem
import org.textrpg.application.domain.model.*
import org.textrpg.application.game.attribute.AttributeContainer
import org.textrpg.application.game.inventory.InventoryService
import org.textrpg.application.game.inventory.ItemStatsParser

/**
 * 装备操作结果
 *
 * @property success 操作是否成功
 * @property message 失败原因或操作反馈
 */
data class EquipmentResult(
    val success: Boolean,
    val message: String? = null
) {
    companion object {
        fun success(message: String? = null) = EquipmentResult(true, message)
        fun failed(message: String) = EquipmentResult(false, message)
    }
}

/**
 * 装备服务
 *
 * 处理装备穿脱逻辑：槽位校验、等级检查、修正器自动挂载/回滚。
 * 穿戴装备时自动读取 [ItemTemplate.baseStats] 并挂载为 flat 类型修正器，
 * 脱下时按来源自动回滚属性。
 *
 * 修正器来源格式：`"equipment:{EquipmentSlot.value}"`（如 `"equipment:slot_weapon"`）
 *
 * @param itemRepository 物品仓储
 * @param inventoryService 背包服务（用于容量检查和物品添加/移除）
 */
class EquipmentService(
    private val itemRepository: ItemRepository,
    private val inventoryService: InventoryService
) {

    companion object {
        /**
         * 装备槽位兼容映射
         *
         * 定义每个装备槽位可接受的物品子类型。
         */
        val SLOT_COMPATIBILITY: Map<EquipmentSlot, Set<ItemSubType>> = mapOf(
            EquipmentSlot.HEAD to setOf(ItemSubType.ARMOR),
            EquipmentSlot.CHEST to setOf(ItemSubType.ARMOR),
            EquipmentSlot.WEAPON to setOf(ItemSubType.WEAPON),
            EquipmentSlot.OFFHAND to setOf(ItemSubType.WEAPON, ItemSubType.ARMOR),
            EquipmentSlot.RING to setOf(ItemSubType.JEWELRY),
            EquipmentSlot.AMULET to setOf(ItemSubType.JEWELRY),
            EquipmentSlot.BOOTS to setOf(ItemSubType.ARMOR),
            EquipmentSlot.GLOVES to setOf(ItemSubType.ARMOR)
        )
    }

    /**
     * 穿戴装备
     *
     * 完整流程：
     * 1. 校验物品所有权和类型
     * 2. 校验槽位兼容性和等级要求
     * 3. 如果目标槽位已有装备，先脱下旧装备
     * 4. 从背包移除物品，放入装备位
     * 5. 解析 baseStats，挂载属性修正器
     *
     * @param playerId 玩家 ID
     * @param inventoryItemId 要穿戴的背包条目 ID
     * @param slot 目标装备槽位
     * @param attributeContainer 玩家属性容器（用于挂载修正器）
     * @return 操作结果
     */
    fun equip(
        playerId: Long,
        inventoryItemId: Long,
        slot: EquipmentSlot,
        attributeContainer: AttributeContainer
    ): EquipmentResult {
        // 1. 加载并校验背包条目
        val invItem = itemRepository.findById(inventoryItemId)
            ?: return EquipmentResult.failed("背包条目不存在")
        if (invItem.playerId != playerId) {
            return EquipmentResult.failed("权限不足")
        }
        if (invItem.instanceId == null) {
            return EquipmentResult.failed("该物品不是装备")
        }

        // 2. 加载模板，校验类型
        val template = itemRepository.findTemplateById(invItem.templateId)
            ?: return EquipmentResult.failed("物品模板不存在")
        if (template.type != ItemType.EQUIPMENT) {
            return EquipmentResult.failed("${template.name} 不是装备")
        }

        // 3. 校验槽位兼容性
        val compatible = SLOT_COMPATIBILITY[slot] ?: emptySet()
        if (template.subType !in compatible) {
            return EquipmentResult.failed("${template.name} 无法装备到 ${slot.name} 槽位")
        }

        // 4. 校验等级要求
        if (template.levelReq > 0) {
            val playerLevel = attributeContainer.getValue("level")
            if (playerLevel < template.levelReq) {
                return EquipmentResult.failed("等级不足（需要 ${template.levelReq}，当前 ${playerLevel.toInt()}）")
            }
        }

        // 5. 加载装备栏（不存在则新建空记录）
        var equipment = itemRepository.findEquipmentByPlayerId(playerId)
            ?: PlayerEquipment(playerId = playerId)

        // 6. 如果槽位已有装备，先脱下
        val existingInstanceId = equipment.getSlot(slot)
        if (existingInstanceId != null) {
            val unequipResult = unequip(playerId, slot, attributeContainer)
            if (!unequipResult.success) {
                return EquipmentResult.failed("脱下旧装备失败：${unequipResult.message}")
            }
            // 重新加载装备栏（unequip 已更新）
            equipment = itemRepository.findEquipmentByPlayerId(playerId)
                ?: PlayerEquipment(playerId = playerId)
        }

        // 7. 从背包移除
        itemRepository.delete(inventoryItemId)

        // 8. 放入装备位
        val updatedEquipment = equipment.withSlot(slot, invItem.instanceId)
        itemRepository.saveEquipment(updatedEquipment)

        // 9. 挂载属性修正器
        val stats = ItemStatsParser.parseBaseStats(template.baseStats)
        val modSource = "equipment:${slot.value}"
        for ((attrKey, value) in stats) {
            if (attributeContainer.contains(attrKey)) {
                attributeContainer.addModifier(
                    attrKey,
                    Modifier(source = modSource, type = ModifierType.FLAT, value = value)
                )
            }
        }

        return EquipmentResult.success("${template.name} 已装备到 ${slot.name}")
    }

    /**
     * 脱下装备
     *
     * 完整流程：
     * 1. 校验槽位是否有装备
     * 2. 检查背包是否有空间
     * 3. 从装备位移除，放回背包
     * 4. 按来源移除属性修正器（自动回滚属性）
     *
     * @param playerId 玩家 ID
     * @param slot 装备槽位
     * @param attributeContainer 玩家属性容器（用于回滚修正器）
     * @return 操作结果
     */
    fun unequip(
        playerId: Long,
        slot: EquipmentSlot,
        attributeContainer: AttributeContainer
    ): EquipmentResult {
        // 1. 加载装备栏
        val equipment = itemRepository.findEquipmentByPlayerId(playerId)
            ?: return EquipmentResult.failed("装备栏不存在")

        val instanceId = equipment.getSlot(slot)
            ?: return EquipmentResult.failed("${slot.name} 槽位没有装备")

        // 2. 检查背包空间
        if (!inventoryService.hasSpace(playerId)) {
            return EquipmentResult.failed("背包已满，无法脱下")
        }

        // 3. 加载实例信息
        val instance = itemRepository.findInstanceById(instanceId)
            ?: return EquipmentResult.failed("装备实例不存在")

        // 4. 清空装备位
        val updatedEquipment = equipment.withSlot(slot, null)
        itemRepository.saveEquipment(updatedEquipment)

        // 5. 放回背包
        val invItem = PlayerInventoryItem(
            playerId = playerId,
            templateId = instance.templateId,
            instanceId = instanceId,
            quantity = 1,
            slotType = SlotType.INVENTORY
        )
        itemRepository.save(invItem)

        // 6. 移除属性修正器
        val modSource = "equipment:${slot.value}"
        attributeContainer.removeAllModifiersBySource(modSource)

        return EquipmentResult.success("已脱下装备")
    }

    /**
     * 获取玩家装备栏
     *
     * @param playerId 玩家 ID
     * @return 装备栏记录，不存在返回 null
     */
    fun getEquipment(playerId: Long): PlayerEquipment? {
        return itemRepository.findEquipmentByPlayerId(playerId)
    }
}
