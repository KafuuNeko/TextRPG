package org.textrpg.application.data.repository

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.textrpg.application.data.database.PlayerItems
import org.textrpg.application.domain.entity.ItemInstanceEntity
import org.textrpg.application.domain.entity.PlayerItemEntity
import org.textrpg.application.domain.model.ItemInstance
import org.textrpg.application.domain.model.PlayerItem

/**
 * 物品仓储实现
 *
 * "模板 - 实例 - 玩家物品"三层物品系统：
 * - [ItemTemplate] 存储静态配置（由 YAML 文件加载，通过 ItemTemplateRegistry 查询）
 * - [ItemInstance] 仅存储需要唯一属性的物品（装备强化/耐久等）
 * - [PlayerItem] 存储玩家持有的物品条目（关联模板 + 可选实例）
 *
 * 堆叠物品（如药水）不在 [ItemInstances] 表生成记录，通过玩家物品条目的 [PlayerItem.quantity] 表示。
 * 装备等唯一物品先在 [ItemInstances] 创建实例，再将实例 ID 存入玩家物品条目。
 *
 * @param mDatabase Exposed Database 实例
 */
class ItemRepository(private val mDatabase: Database) :
    IRepository<PlayerItem, Long> {

    // ==================== 物品实例 ====================

    /**
     * 根据 ID 查询物品实例
     *
     * @param id 实例主键
     * @return 实例，不存在则返回 null
     */
    fun findInstanceById(id: Long): ItemInstance? = transaction(mDatabase) {
        ItemInstanceEntity.findById(id)?.toInstance()
    }

    /**
     * 创建物品实例
     *
     * 仅用于装备等需要唯一属性的物品。堆叠物品不生成实例。
     *
     * @param templateId 关联的模板 ID
     * @param creatorId 制造者玩家 ID（可选，用于展示"XX制造"）
     * @return 创建后的实例（含数据库生成的主键）
     */
    fun createInstance(templateId: String, creatorId: Long? = null): ItemInstance = transaction(mDatabase) {
        ItemInstanceEntity.new {
            this.templateId = templateId
            this.creatorId = creatorId
            createdAt = DateTime.now()
        }.toInstance()
    }

    /**
     * 更新物品实例属性
     *
     * 用于强化、修复耐久度、镶嵌宝石等操作。
     *
     * @param instance 要更新的实例（含 id）
     * @return 更新后的实例
     */
    fun updateInstance(instance: ItemInstance): ItemInstance = transaction(mDatabase) {
        val existing = ItemInstanceEntity.findById(instance.id)
            ?: error("Instance not found: ${instance.id}")
        existing.apply {
            attribute = instance.attribute
        }.toInstance()
    }

    // ==================== 玩家物品操作 ====================

    /**
     * 根据 ID 查询玩家物品条目
     *
     * @param id 玩家物品条目主键
     * @return 玩家物品条目，不存在则返回 null
     */
    override fun findById(id: Long): PlayerItem? = transaction(mDatabase) {
        PlayerItemEntity.findById(id)?.toPlayerItem()
    }

    /**
     * 查询所有玩家物品条目
     *
     * @return 所有玩家物品条目的列表
     */
    override fun findAll(): List<PlayerItem> = transaction(mDatabase) {
        PlayerItemEntity.all().map { it.toPlayerItem() }
    }

    /**
     * 查询指定玩家的所有物品条目
     *
     * @param playerId 玩家主键
     * @return 该玩家的所有物品条目列表
     */
    fun findByPlayerId(playerId: Long): List<PlayerItem> = transaction(mDatabase) {
        PlayerItemEntity.find { PlayerItems.playerId eq playerId }
            .map { it.toPlayerItem() }
    }

    /**
     * 查找可堆叠的玩家物品空格
     *
     * 用于堆叠物品（如药水）获取时，先检查是否已有未满的同一位置(slotType)同模板格子，
     * 有则合并，无则新增一行。
     *
     * @param playerId 玩家主键
     * @param templateId 物品模板 ID
     * @param slotType 物品存储位置（默认 "inventory"）
     * @return 可堆叠的条目，若不存在则返回 null
     */
    fun findStackableSlot(playerId: Long, templateId: String, slotType: String = "inventory"): PlayerItem? = transaction(mDatabase) {
        PlayerItemEntity.find {
            (PlayerItems.playerId eq playerId) and
            (PlayerItems.templateId eq templateId) and
            (PlayerItems.slotType eq slotType) and
            (PlayerItems.instanceId.isNull())
        }.firstOrNull()?.toPlayerItem()
    }

    /**
     * 保存玩家物品条目（新增或更新）
     *
     * @param entity 要保存的条目，id == 0 时为新增
     * @return 保存后的条目（含数据库生成的主键）
     */
    override fun save(entity: PlayerItem): PlayerItem = transaction(mDatabase) {
        if (entity.id == 0L) {
            PlayerItemEntity.new {
                playerId = entity.playerId
                templateId = entity.templateId
                instanceId = entity.instanceId
                quantity = entity.quantity
                slotType = entity.slotType
                slotIndex = entity.slotIndex
                isBound = entity.isBound
                createdAt = DateTime.now()
            }.toPlayerItem()
        } else {
            val existing = PlayerItemEntity.findById(entity.id)
                ?: error("PlayerItem not found: ${entity.id}")
            existing.apply {
                quantity = entity.quantity
                slotType = entity.slotType
                slotIndex = entity.slotIndex
                isBound = entity.isBound
            }.toPlayerItem()
        }
    }

    /**
     * 删除玩家物品条目
     *
     * @param id 要删除的条目主键
     * @return 是否删除成功
     */
    override fun delete(id: Long): Boolean = transaction(mDatabase) {
        val entity = PlayerItemEntity.findById(id) ?: return@transaction false
        entity.delete()
        true
    }

    /**
     * 删除物品实例
     *
     * 用于装备从物品栏移除时，同步清理 item_instances 表的记录。
     *
     * @param id 实例主键
     * @return 是否删除成功
     */
    fun deleteInstance(id: Long): Boolean = transaction(mDatabase) {
        val entity = ItemInstanceEntity.findById(id) ?: return@transaction false
        entity.delete()
        true
    }

    // ==================== 辅助方法 ====================

    private fun ItemInstanceEntity.toInstance() = ItemInstance(
        id = id.value,
        templateId = templateId,
        attribute = attribute,
        creatorId = creatorId,
        createdAt = createdAt
    )

    private fun PlayerItemEntity.toPlayerItem() = PlayerItem(
        id = id.value,
        playerId = playerId,
        templateId = templateId,
        instanceId = instanceId,
        quantity = quantity,
        slotType = slotType,
        slotIndex = slotIndex,
        isBound = isBound,
        createdAt = createdAt
    )
}
