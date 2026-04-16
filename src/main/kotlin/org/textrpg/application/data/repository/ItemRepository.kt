package org.textrpg.application.data.repository

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.textrpg.application.data.dao.ItemInstanceEntity
import org.textrpg.application.data.dao.ItemTemplateEntity
import org.textrpg.application.data.dao.PlayerEquipmentEntity
import org.textrpg.application.data.dao.PlayerInventoryEntity
import org.textrpg.application.data.database.PlayerEquipments
import org.textrpg.application.data.database.PlayerInventories
import org.textrpg.application.domain.entity.ItemInstance
import org.textrpg.application.domain.entity.ItemTemplate
import org.textrpg.application.domain.entity.PlayerEquipment
import org.textrpg.application.domain.entity.PlayerInventoryItem
import org.textrpg.application.domain.model.ItemRarity
import org.textrpg.application.domain.model.ItemSubType
import org.textrpg.application.domain.model.ItemType
import org.textrpg.application.domain.model.SlotType

/**
 * 物品仓储实现
 *
 * 实现了"模板 - 实例 - 背包"三层物品系统：
 * - [ItemTemplate] 存储静态配置（所有物品共享）
 * - [ItemInstance] 仅存储需要唯一属性的物品（装备强化/耐久等）
 * - [PlayerInventoryItem] 存储玩家持有的物品条目（关联模板 + 可选实例）
 *
 * 堆叠物品（如药水）不在 [ItemInstances] 表生成记录，通过背包条目的 [PlayerInventoryItem.quantity] 表示。
 * 装备等唯一物品先在 [ItemInstances] 创建实例，再将实例 ID 存入背包条目。
 *
 * @param database Exposed Database 实例
 */
class ItemRepository(private val database: Database) :
    IRepository<PlayerInventoryItem, Long> {

    // ==================== 物品模板 ====================

    /**
     * 根据 ID 查询物品模板
     *
     * @param id 模板主键
     * @return 模板实例，不存在则返回 null
     */
    fun findTemplateById(id: Int): ItemTemplate? = transaction(database) {
        ItemTemplateEntity.findById(id)?.toTemplate()
    }

    /**
     * 查询所有物品模板
     *
     * @return 所有模板的列表
     */
    fun findAllTemplates(): List<ItemTemplate> = transaction(database) {
        ItemTemplateEntity.all().map { it.toTemplate() }
    }

    /**
     * 保存物品模板（新增或更新）
     *
     * @param template 要保存的模板实体，id == 0 时为新增
     * @return 保存后的模板（含数据库生成的主键）
     */
    fun saveTemplate(template: ItemTemplate): ItemTemplate = transaction(database) {
        if (template.id == 0) {
            ItemTemplateEntity.new {
                name = template.name
                type = template.type.value
                subType = template.subType?.value ?: 0
                rarity = template.rarity.value
                stackable = template.stackable
                baseStats = template.baseStats
                levelReq = template.levelReq
                price = template.price
                description = template.description
            }.toTemplate()
        } else {
            val existing = ItemTemplateEntity.findById(template.id)
                ?: error("Template not found: ${template.id}")
            existing.apply {
                name = template.name
                type = template.type.value
                subType = template.subType?.value ?: 0
                rarity = template.rarity.value
                stackable = template.stackable
                baseStats = template.baseStats
                levelReq = template.levelReq
                price = template.price
                description = template.description
            }.toTemplate()
        }
    }

    /**
     * 删除物品模板
     *
     * @param id 模板主键
     * @return 是否删除成功
     */
    fun deleteTemplate(id: Int): Boolean = transaction(database) {
        val entity = ItemTemplateEntity.findById(id) ?: return@transaction false
        entity.delete()
        true
    }

    // ==================== 物品实例 ====================

    /**
     * 根据 ID 查询物品实例
     *
     * @param id 实例主键
     * @return 实例，不存在则返回 null
     */
    fun findInstanceById(id: Long): ItemInstance? = transaction(database) {
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
    fun createInstance(templateId: Int, creatorId: Long? = null): ItemInstance = transaction(database) {
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
    fun updateInstance(instance: ItemInstance): ItemInstance = transaction(database) {
        val existing = ItemInstanceEntity.findById(instance.id)
            ?: error("Instance not found: ${instance.id}")
        existing.apply {
            level = instance.level
            exp = instance.exp
            durability = instance.durability
            randomStats = instance.randomStats
            sockets = instance.sockets
        }.toInstance()
    }

    /**
     * 删除物品实例
     *
     * @param id 实例主键
     * @return 是否删除成功
     */
    fun deleteInstance(id: Long): Boolean = transaction(database) {
        val entity = ItemInstanceEntity.findById(id) ?: return@transaction false
        entity.delete()
        true
    }

    // ==================== 背包 ====================

    /**
     * 根据 ID 查询背包条目
     *
     * @param id 背包条目主键
     * @return 背包条目，不存在则返回 null
     */
    override fun findById(id: Long): PlayerInventoryItem? = transaction(database) {
        PlayerInventoryEntity.findById(id)?.toInventory()
    }

    /**
     * 查询所有背包条目
     *
     * @return 所有背包条目的列表
     */
    override fun findAll(): List<PlayerInventoryItem> = transaction(database) {
        PlayerInventoryEntity.all().map { it.toInventory() }
    }

    /**
     * 查询指定玩家的所有背包条目
     *
     * @param playerId 玩家主键
     * @return 该玩家的所有背包条目列表
     */
    fun findByPlayerId(playerId: Long): List<PlayerInventoryItem> = transaction(database) {
        PlayerInventoryEntity.find { PlayerInventories.playerId eq playerId }
            .map { it.toInventory() }
    }

    /**
     * 查找可堆叠的背包空格
     *
     * 用于堆叠物品（如药水）获取时，先检查是否已有未满的同模板格子，
     * 有则合并，无则新增一行。
     *
     * @param playerId 玩家主键
     * @param templateId 物品模板 ID
     * @return 可堆叠的空格，若不存在则返回 null
     */
    fun findStackableSlot(playerId: Long, templateId: Int): PlayerInventoryItem? = transaction(database) {
        PlayerInventoryEntity.find {
            (PlayerInventories.playerId eq playerId) and
            (PlayerInventories.templateId eq templateId) and
            (PlayerInventories.instanceId.isNull())
        }.firstOrNull()?.toInventory()
    }

    /**
     * 保存背包条目（新增或更新）
     *
     * @param entity 要保存的背包条目，id == 0 时为新增
     * @return 保存后的条目（含数据库生成的主键）
     */
    override fun save(entity: PlayerInventoryItem): PlayerInventoryItem = transaction(database) {
        if (entity.id == 0L) {
            PlayerInventoryEntity.new {
                playerId = entity.playerId
                templateId = entity.templateId
                instanceId = entity.instanceId
                quantity = entity.quantity
                slotType = entity.slotType.value
                slotIndex = entity.slotIndex
                isBound = entity.isBound
                createdAt = DateTime.now()
            }.toInventory()
        } else {
            val existing = PlayerInventoryEntity.findById(entity.id)
                ?: error("Inventory item not found: ${entity.id}")
            existing.apply {
                quantity = entity.quantity
                slotType = entity.slotType.value
                slotIndex = entity.slotIndex
                isBound = entity.isBound
            }.toInventory()
        }
    }

    /**
     * 删除背包条目
     *
     * @param id 要删除的背包条目主键
     * @return 是否删除成功
     */
    override fun delete(id: Long): Boolean = transaction(database) {
        val entity = PlayerInventoryEntity.findById(id) ?: return@transaction false
        entity.delete()
        true
    }

    // ==================== 装备 ====================

    /**
     * 查询玩家装备栏
     *
     * @param playerId 玩家主键
     * @return 装备栏记录，不存在则返回 null
     */
    fun findEquipmentByPlayerId(playerId: Long): PlayerEquipment? = transaction(database) {
        PlayerEquipmentEntity.find { PlayerEquipments.playerId eq playerId }
            .firstOrNull()?.toEquipment()
    }

    /**
     * 保存装备栏（新增或更新）
     *
     * @param equipment 要保存的装备栏记录，id == 0 时为新增
     * @return 保存后的装备栏
     */
    fun saveEquipment(equipment: PlayerEquipment): PlayerEquipment = transaction(database) {
        if (equipment.id == 0L) {
            PlayerEquipmentEntity.new {
                playerId = equipment.playerId
                slotHead = equipment.slotHead
                slotChest = equipment.slotChest
                slotWeapon = equipment.slotWeapon
                slotOffhand = equipment.slotOffhand
                slotRing = equipment.slotRing
                slotAmulet = equipment.slotAmulet
                slotBoots = equipment.slotBoots
                slotGloves = equipment.slotGloves
            }.toEquipment()
        } else {
            val existing = PlayerEquipmentEntity.findById(equipment.id)
                ?: error("Equipment record not found: ${equipment.id}")
            existing.apply {
                slotHead = equipment.slotHead
                slotChest = equipment.slotChest
                slotWeapon = equipment.slotWeapon
                slotOffhand = equipment.slotOffhand
                slotRing = equipment.slotRing
                slotAmulet = equipment.slotAmulet
                slotBoots = equipment.slotBoots
                slotGloves = equipment.slotGloves
            }.toEquipment()
        }
    }

    /**
     * 删除玩家装备栏记录
     *
     * @param playerId 玩家主键
     * @return 是否删除成功
     */
    fun deleteEquipment(playerId: Long): Boolean = transaction(database) {
        val entity = PlayerEquipmentEntity.find { PlayerEquipments.playerId eq playerId }
            .firstOrNull() ?: return@transaction false
        entity.delete()
        true
    }

    /**
     * 统计玩家指定容器的背包条目数
     *
     * 用于容量检查，轻量级计数查询。
     *
     * @param playerId 玩家主键
     * @param slotType 容器类型（默认背包）
     * @return 条目数量
     */
    fun countInventorySlots(playerId: Long, slotType: SlotType = SlotType.INVENTORY): Int = transaction(database) {
        PlayerInventoryEntity.find {
            (PlayerInventories.playerId eq playerId) and
            (PlayerInventories.slotType eq slotType.value)
        }.count().toInt()
    }

    // ==================== 辅助方法 ====================

    private fun ItemTemplateEntity.toTemplate() = ItemTemplate(
        id = id.value,
        name = name,
        type = ItemType.fromValue(type),
        subType = if (subType != 0) ItemSubType.fromValue(subType) else null,
        rarity = ItemRarity.fromValue(rarity),
        stackable = stackable,
        baseStats = baseStats,
        levelReq = levelReq,
        price = price,
        description = description
    )

    private fun ItemInstanceEntity.toInstance() = ItemInstance(
        id = id.value,
        templateId = templateId,
        level = level,
        exp = exp,
        durability = durability,
        randomStats = randomStats,
        sockets = sockets,
        creatorId = creatorId,
        createdAt = createdAt
    )

    private fun PlayerEquipmentEntity.toEquipment() = PlayerEquipment(
        id = id.value,
        playerId = playerId,
        slotHead = slotHead,
        slotChest = slotChest,
        slotWeapon = slotWeapon,
        slotOffhand = slotOffhand,
        slotRing = slotRing,
        slotAmulet = slotAmulet,
        slotBoots = slotBoots,
        slotGloves = slotGloves
    )

    private fun PlayerInventoryEntity.toInventory() = PlayerInventoryItem(
        id = id.value,
        playerId = playerId,
        templateId = templateId,
        instanceId = instanceId,
        quantity = quantity,
        slotType = SlotType.fromValue(slotType),
        slotIndex = slotIndex,
        isBound = isBound,
        createdAt = createdAt
    )
}
