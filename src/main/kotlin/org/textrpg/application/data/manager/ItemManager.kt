package org.textrpg.application.data.manager

import org.textrpg.application.data.registry.ItemTemplateRegistry
import org.textrpg.application.data.repository.ItemRepository
import org.textrpg.application.domain.model.ItemInstance
import org.textrpg.application.domain.model.ItemTemplate
import org.textrpg.application.domain.model.PlayerItem

/**
 * 物品管理器——物品模块的统一数据操作入口
 *
 * 协调 [ItemRepository]（数据库层）和 [ItemTemplateRegistry]（静态数据层），
 * 对外提供完整的物品操作接口：模板查询、实例管理、玩家物品操作。
 *
 * 外部模块（如命令处理器、战斗系统）操作物品时，应通过此管理器而非直接调用 Repository/Registry。
 *
 * @param mItemRepository 物品仓储（数据库操作：实例和玩家物品条目）
 * @param mItemTemplateRegistry 物品模板注册表（YAML 静态数据加载）
 */
class ItemManager(
    private val mItemRepository: ItemRepository,
    private val mItemTemplateRegistry: ItemTemplateRegistry
) {

    // ==================== 模板查询 ====================

    /**
     * 根据模板 ID 查询物品模板
     *
     * @param templateId 模板 ID（即 YAML 文件名，不含后缀）
     * @return 模板实例，不存在则返回 null
     */
    fun findTemplateById(templateId: String): ItemTemplate? {
        return mItemTemplateRegistry.findById(templateId)
    }

    /**
     * 获取所有可用的模板 ID 列表
     *
     * @return 所有可用模板 ID 的列表
     */
    fun listAllTemplateIds(): List<String> {
        return mItemTemplateRegistry.listAllIds()
    }

    // ==================== 物品实例 ====================

    /**
     * 根据实例 ID 查询物品实例
     *
     * @param instanceId 实例主键
     * @return 实例，不存在则返回 null
     */
    fun findInstanceById(instanceId: Long): ItemInstance? {
        return mItemRepository.findInstanceById(instanceId)
    }

    /**
     * 更新物品实例属性（强化、修复耐久度、镶嵌宝石等）
     *
     * @param instanceId 实例主键
     * @param attribute 新的属性 JSON 字符串
     * @return 更新后的实例
     * @throws IllegalStateException 实例不存在时抛出
     */
    fun updateInstanceAttribute(instanceId: Long, attribute: String): ItemInstance {
        val existing = mItemRepository.findInstanceById(instanceId)
            ?: error("Item instance not found: $instanceId")
        return mItemRepository.updateInstance(existing.copy(attribute = attribute))
    }

    // ==================== 玩家物品操作 ====================

    /**
     * 给玩家添加物品
     *
     * 自动根据模板的 [ItemTemplate.stackable] 属性决定行为：
     * - **可堆叠物品**：查找已有的可堆叠格子，有则增加数量，无则新建记录
     * - **不可堆叠物品**：先创建 [ItemInstance]，再新建关联记录（quantity=1）
     *
     * @param playerId 目标玩家 ID
     * @param templateId 物品模板 ID
     * @param quantity 添加数量（仅对可堆叠物品有效，不可堆叠物品固定为 1）
     * @param slotType 指定物品存放的位置类型，默认为 "inventory"
     * @return 新增/更新后的玩家物品条目
     * @throws IllegalArgumentException 模板不存在或数量无效时抛出
     */
    fun addItemToPlayer(playerId: Long, templateId: String, quantity: Int = 1, slotType: String = "inventory"): PlayerItem {
        require(quantity > 0) { "Quantity must be positive, got: $quantity" }

        val template = mItemTemplateRegistry.findById(templateId)
            ?: throw IllegalArgumentException("Item template not found: $templateId")

        return if (template.stackable) {
            addStackableItem(playerId, templateId, quantity, slotType)
        } else {
            addUniqueItem(playerId, templateId, slotType)
        }
    }

    /**
     * 从玩家物品记录中移除物品
     *
     * - 如果移除数量 >= 当前持有量：删除整条记录（装备同时删除关联实例）
     * - 如果移除数量 < 当前持有量：仅减少数量
     *
     * @param playerItemId 玩家物品条目主键
     * @param quantity 移除数量（默认 1）
     * @return 是否操作成功（条目不存在时返回 false）
     */
    fun removeItemFromPlayer(playerItemId: Long, quantity: Int = 1): Boolean {
        require(quantity > 0) { "Quantity must be positive, got: $quantity" }

        val item = mItemRepository.findById(playerItemId) ?: return false

        return if (quantity >= item.quantity) {
            // 完全移除：删除记录 + 关联实例
            item.instanceId?.let { mItemRepository.deleteInstance(it) }
            mItemRepository.delete(playerItemId)
        } else {
            // 部分移除：减少数量
            mItemRepository.save(item.copy(quantity = item.quantity - quantity))
            true
        }
    }

    /**
     * 查询玩家所有物品条目
     *
     * @param playerId 玩家主键
     * @return 该玩家的所有物品条目列表
     */
    fun getPlayerItems(playerId: Long): List<PlayerItem> {
        return mItemRepository.findByPlayerId(playerId)
    }

    /**
     * 查询指定物品条目
     *
     * @param playerItemId 条目主键
     * @return 玩家物品条目，不存在则返回 null
     */
    fun findPlayerItem(playerItemId: Long): PlayerItem? {
        return mItemRepository.findById(playerItemId)
    }

    // ==================== 私有方法 ====================

    /**
     * 添加可堆叠物品（药水、材料等）
     *
     * 优先合并到已有的同模板同位置的格子，无则新建。
     */
    private fun addStackableItem(playerId: Long, templateId: String, quantity: Int, slotType: String): PlayerItem {
        val existingSlot = mItemRepository.findStackableSlot(playerId, templateId, slotType)

        return if (existingSlot != null) {
            mItemRepository.save(existingSlot.copy(quantity = existingSlot.quantity + quantity))
        } else {
            mItemRepository.save(
                PlayerItem(
                    playerId = playerId,
                    templateId = templateId,
                    quantity = quantity,
                    slotType = slotType
                )
            )
        }
    }

    /**
     * 添加不可堆叠物品（装备等）
     *
     * 先创建物品实例，再创建物品条目关联到该实例。
     */
    private fun addUniqueItem(playerId: Long, templateId: String, slotType: String): PlayerItem {
        val instance = mItemRepository.createInstance(templateId)
        return mItemRepository.save(
            PlayerItem(
                playerId = playerId,
                templateId = templateId,
                instanceId = instance.id,
                quantity = 1,
                slotType = slotType
            )
        )
    }
}