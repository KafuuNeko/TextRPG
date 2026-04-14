package org.textrpg.application.game.inventory

import org.textrpg.application.data.config.InventoryConfig
import org.textrpg.application.data.repository.ItemRepository
import org.textrpg.application.data.repository.PlayerInventoryItem
import org.textrpg.application.domain.model.EffectDefinition
import org.textrpg.application.domain.model.EffectResult
import org.textrpg.application.domain.model.ItemType
import org.textrpg.application.domain.model.SlotType
import org.textrpg.application.game.effect.EffectContext
import org.textrpg.application.game.effect.EffectEngine
import org.joda.time.DateTime

/**
 * 背包操作结果
 *
 * @property success 操作是否成功
 * @property message 失败原因或操作反馈
 * @property inventoryItemId 新增/更新的背包条目 ID（成功时可用）
 */
data class InventoryResult(
    val success: Boolean,
    val message: String? = null,
    val inventoryItemId: Long? = null
) {
    companion object {
        fun success(message: String? = null, itemId: Long? = null) =
            InventoryResult(true, message, itemId)
        fun failed(message: String) = InventoryResult(false, message)
    }
}

/**
 * 背包服务
 *
 * 封装背包的全部业务操作：添加/移除/使用/拆分/转移物品。
 * 作为单例服务使用，所有操作通过 playerId 参数指定目标玩家。
 *
 * 容量检查根据 [InventoryConfig.capacityMode] 切换模式：
 * - `"slots"`：按格子数限制
 * - `"weight"`：按负重限制（预留，暂用 price 代替 weight）
 * - `"unlimited"`：无限制
 *
 * @param itemRepository 物品仓储（提供三层物品数据操作）
 * @param config 背包容量配置
 */
class InventoryService(
    private val itemRepository: ItemRepository,
    private val config: InventoryConfig
) {

    /**
     * 添加物品到背包
     *
     * 堆叠物品自动合并到已有条目；不可堆叠物品（装备）为每件创建独立实例和条目。
     * 添加前检查容量是否充足。
     *
     * @param playerId 玩家 ID
     * @param templateId 物品模板 ID
     * @param quantity 数量
     * @return 操作结果
     */
    fun addItem(playerId: Long, templateId: Int, quantity: Int = 1): InventoryResult {
        val template = itemRepository.findTemplateById(templateId)
            ?: return InventoryResult.failed("物品模板不存在：$templateId")

        if (template.stackable) {
            // 堆叠物品：查找已有条目合并，或新建
            val existing = itemRepository.findStackableSlot(playerId, templateId)
            if (existing != null) {
                val updated = existing.copy(quantity = existing.quantity + quantity)
                val saved = itemRepository.save(updated)
                return InventoryResult.success("${template.name} x$quantity 已添加", saved.id)
            }

            // 新建条目
            if (!hasSpace(playerId)) {
                return InventoryResult.failed("背包已满")
            }
            val item = PlayerInventoryItem(
                playerId = playerId,
                templateId = templateId,
                quantity = quantity,
                slotType = SlotType.INVENTORY
            )
            val saved = itemRepository.save(item)
            return InventoryResult.success("${template.name} x$quantity 已添加", saved.id)
        } else {
            // 不可堆叠物品（装备）：每件创建实例 + 背包条目
            val createdIds = mutableListOf<Long>()
            for (i in 1..quantity) {
                if (!hasSpace(playerId)) {
                    return if (createdIds.isEmpty()) {
                        InventoryResult.failed("背包已满")
                    } else {
                        InventoryResult.success("背包已满，添加了 ${createdIds.size}/$quantity 件", createdIds.first())
                    }
                }
                val instance = itemRepository.createInstance(templateId)
                val item = PlayerInventoryItem(
                    playerId = playerId,
                    templateId = templateId,
                    instanceId = instance.id,
                    quantity = 1,
                    slotType = SlotType.INVENTORY
                )
                val saved = itemRepository.save(item)
                createdIds.add(saved.id)
            }
            return InventoryResult.success("${template.name} x$quantity 已添加", createdIds.firstOrNull())
        }
    }

    /**
     * 从背包移除物品
     *
     * 堆叠物品减少数量（归零时删除条目）；不可堆叠物品直接删除条目。
     *
     * @param playerId 玩家 ID
     * @param templateId 物品模板 ID
     * @param quantity 数量
     * @return 操作结果
     */
    fun removeItem(playerId: Long, templateId: Int, quantity: Int = 1): InventoryResult {
        val items = itemRepository.findByPlayerId(playerId)
            .filter { it.templateId == templateId && it.slotType == SlotType.INVENTORY }

        if (items.isEmpty()) {
            return InventoryResult.failed("未持有该物品")
        }

        var remaining = quantity

        for (item in items) {
            if (remaining <= 0) break

            if (item.isStackable) {
                if (item.quantity <= remaining) {
                    remaining -= item.quantity
                    itemRepository.delete(item.id)
                } else {
                    val updated = item.copy(quantity = item.quantity - remaining)
                    itemRepository.save(updated)
                    remaining = 0
                }
            } else {
                // 不可堆叠：删除单条
                itemRepository.delete(item.id)
                remaining--
            }
        }

        return if (remaining > 0) {
            InventoryResult.failed("物品不足（缺少 $remaining）")
        } else {
            InventoryResult.success("已移除 x$quantity")
        }
    }

    /**
     * 检查是否持有指定物品
     *
     * @param playerId 玩家 ID
     * @param templateId 物品模板 ID
     * @param quantity 所需数量
     * @return 是否持有足够数量
     */
    fun hasItem(playerId: Long, templateId: Int, quantity: Int = 1): Boolean {
        val total = itemRepository.findByPlayerId(playerId)
            .filter { it.templateId == templateId && it.slotType == SlotType.INVENTORY }
            .sumOf { it.quantity }
        return total >= quantity
    }

    /**
     * 使用消耗品
     *
     * 校验物品为消耗品类型，执行关联的效果列表（通过特效引擎），然后扣减 1 个数量。
     *
     * @param playerId 玩家 ID
     * @param templateId 物品模板 ID
     * @param effects 物品使用时触发的效果列表（由调用方提供）
     * @param context 特效执行上下文
     * @param effectEngine 特效引擎
     * @return 操作结果
     */
    suspend fun useItem(
        playerId: Long,
        templateId: Int,
        effects: List<EffectDefinition>,
        context: EffectContext,
        effectEngine: EffectEngine
    ): InventoryResult {
        val template = itemRepository.findTemplateById(templateId)
            ?: return InventoryResult.failed("物品模板不存在：$templateId")

        if (template.type != ItemType.CONSUMABLE) {
            return InventoryResult.failed("${template.name} 不是消耗品")
        }

        if (!hasItem(playerId, templateId)) {
            return InventoryResult.failed("未持有 ${template.name}")
        }

        // 执行效果
        effectEngine.executeEffects(effects, context)

        // 扣减数量
        val removeResult = removeItem(playerId, templateId, 1)
        if (!removeResult.success) {
            return InventoryResult.failed("效果已执行，但扣减物品失败：${removeResult.message}")
        }

        return InventoryResult.success("使用了 ${template.name}")
    }

    /**
     * 拆分堆叠
     *
     * 将一个堆叠条目拆分为两个：原条目数量减少，新建条目持有拆出的数量。
     *
     * @param playerId 玩家 ID
     * @param inventoryItemId 原背包条目 ID
     * @param splitQuantity 拆出的数量
     * @return 操作结果（返回新条目 ID）
     */
    fun splitStack(playerId: Long, inventoryItemId: Long, splitQuantity: Int): InventoryResult {
        val item = itemRepository.findById(inventoryItemId)
            ?: return InventoryResult.failed("背包条目不存在")

        if (item.playerId != playerId) {
            return InventoryResult.failed("权限不足")
        }
        if (!item.isStackable) {
            return InventoryResult.failed("该物品不可堆叠，无法拆分")
        }
        if (splitQuantity <= 0 || splitQuantity >= item.quantity) {
            return InventoryResult.failed("拆分数量无效")
        }
        if (!hasSpace(playerId)) {
            return InventoryResult.failed("背包已满，无法拆分")
        }

        // 减少原条目
        val updated = item.copy(quantity = item.quantity - splitQuantity)
        itemRepository.save(updated)

        // 新建拆出的条目
        val newItem = PlayerInventoryItem(
            playerId = playerId,
            templateId = item.templateId,
            quantity = splitQuantity,
            slotType = item.slotType
        )
        val saved = itemRepository.save(newItem)

        return InventoryResult.success("拆分成功", saved.id)
    }

    /**
     * 跨容器转移
     *
     * 将物品在不同容器间转移（背包 ↔ 仓库 / 邮件）。
     *
     * @param playerId 玩家 ID
     * @param inventoryItemId 背包条目 ID
     * @param targetSlotType 目标容器类型
     * @return 操作结果
     */
    fun transfer(playerId: Long, inventoryItemId: Long, targetSlotType: SlotType): InventoryResult {
        val item = itemRepository.findById(inventoryItemId)
            ?: return InventoryResult.failed("背包条目不存在")

        if (item.playerId != playerId) {
            return InventoryResult.failed("权限不足")
        }
        if (item.slotType == targetSlotType) {
            return InventoryResult.failed("物品已在目标容器中")
        }

        val updated = item.copy(slotType = targetSlotType)
        itemRepository.save(updated)

        return InventoryResult.success("已转移到 ${targetSlotType.name}")
    }

    /**
     * 检查背包是否有空间
     *
     * @param playerId 玩家 ID
     * @param slotType 容器类型（默认背包）
     * @return 是否还有空间
     */
    fun hasSpace(playerId: Long, slotType: SlotType = SlotType.INVENTORY): Boolean {
        return when (config.capacityMode) {
            "unlimited" -> true
            "slots" -> itemRepository.countInventorySlots(playerId, slotType) < config.maxSlots
            "weight" -> true // 负重模式暂未实现，默认允许
            else -> true
        }
    }
}
