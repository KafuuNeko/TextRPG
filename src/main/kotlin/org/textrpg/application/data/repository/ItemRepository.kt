package org.textrpg.application.data.repository

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.textrpg.application.data.database.ItemInstances
import org.textrpg.application.data.database.ItemTemplates
import org.textrpg.application.data.database.PlayerEquipments
import org.textrpg.application.data.database.PlayerInventories
import org.textrpg.application.domain.entity.ItemInstanceEntity
import org.textrpg.application.domain.entity.ItemTemplateEntity
import org.textrpg.application.domain.entity.PlayerEquipmentEntity
import org.textrpg.application.domain.entity.PlayerInventoryEntity
import org.textrpg.application.domain.model.EquipmentSlot
import org.textrpg.application.domain.model.ItemRarity
import org.textrpg.application.domain.model.ItemSubType
import org.textrpg.application.domain.model.ItemType
import org.textrpg.application.domain.model.SlotType

/**
 * 物品模板——静态配置
 *
 * 定义游戏中所有物品的公共属性，在服务器启动时通常载入内存缓存。
 * 一个模板可生成多个背包条目（堆叠物品）或多个实例（装备）。
 *
 * @property id 模板主键（自增），新建时传 0
 * @property name 物品名称
 * @property type 物品类型，参见 [ItemType]
 * @property subType 物品子类型，仅对装备类型有效，参见 [ItemSubType]
 * @property rarity 物品品质，参见 [ItemRarity]
 * @property stackable 是否可堆叠。true=消耗品/材料（共享一行背包），false=装备（每件独立一行）
 * @property baseStats 基础属性 JSON，如 `{"atk": 10, "def": 5}`
 * @property levelReq 使用等级限制
 * @property price 商店基础售价（金币）
 * @property description 物品描述文本
 */
data class ItemTemplate(
    val id: Int = 0,
    val name: String,
    val type: ItemType,
    val subType: ItemSubType = ItemSubType.WEAPON,
    val rarity: ItemRarity = ItemRarity.WHITE,
    val stackable: Boolean = true,
    val baseStats: String = "{}",
    val levelReq: Int = 0,
    val price: Int = 0,
    val description: String = ""
)

/**
 * 物品实例——仅需要唯一属性的物品
 *
 * 只有装备等拥有个性化属性的物品（强化等级、耐久度、随机词条、宝石孔等）才会生成实例。
 * 堆叠物品（药水、材料）不生成实例，背包中 `instance_id` 为 NULL。
 *
 * @property id 实例主键（自增），新建时由数据库生成
 * @property templateId 关联的模板 ID
 * @property level 当前强化等级
 * @property exp 装备当前经验值（用于升级）
 * @property durability 当前耐久度
 * @property randomStats 洗练/随机生成的附加属性 JSON，如 `[{"key":"crit_rate","value":0.05}]`
 * @property sockets 镶嵌信息 JSON，如 `[2001, 2002, null]`，null 表示空孔
 * @property creatorId 制造者玩家 ID（用于展示"XX制造"）
 * @property createdAt 创建时间
 */
data class ItemInstance(
    val id: Long = 0,
    val templateId: Int,
    val level: Int = 0,
    val exp: Long = 0L,
    val durability: Int = 100,
    val randomStats: String = "[]",
    val sockets: String = "[]",
    val creatorId: Long? = null,
    val createdAt: DateTime? = null
)

/**
 * 玩家背包条目——玩家持有的物品记录
 *
 * 是玩家与物品的关联核心。通过 [instanceId] 是否为 NULL 区分：
 * - 非 NULL：实例物品（装备），quantity 固定为 1
 * - NULL：堆叠物品（药水/材料），quantity 表示持有数量
 *
 * @property id 背包条目主键（自增），新建时传 0
 * @property playerId 所属玩家 ID
 * @property templateId 物品模板 ID
 * @property instanceId 实例 ID。堆叠物品为 NULL；装备为具体实例 ID
 * @property quantity 持有数量。实例物品固定为 1
 * @property slotType 位置类型，参见 [SlotType]
 * @property slotIndex 在格子数组中的索引（用于前端排序）
 * @property isBound 是否绑定（绑定后不可交易）
 * @property createdAt 创建时间
 */
data class PlayerInventoryItem(
    val id: Long = 0,
    val playerId: Long,
    val templateId: Int,
    val instanceId: Long? = null,
    val quantity: Int = 1,
    val slotType: SlotType = SlotType.INVENTORY,
    val slotIndex: Int = 0,
    val isBound: Boolean = false,
    val createdAt: DateTime? = null
) {
    val isStackable: Boolean get() = instanceId == null
}

/**
 * 玩家装备栏
 *
 * 记录玩家 8 个装备槽位上的物品实例 ID，null 表示该槽位为空。
 * 通过 [getSlot] 和 [withSlot] 方法按 [EquipmentSlot] 枚举访问，避免反射。
 *
 * @property id 记录主键，新建时传 0
 * @property playerId 所属玩家 ID
 * @property slotHead 头部槽位（物品实例 ID）
 * @property slotChest 胸甲槽位
 * @property slotWeapon 武器槽位
 * @property slotOffhand 副手槽位
 * @property slotRing 戒指槽位
 * @property slotAmulet 项链槽位
 * @property slotBoots 鞋子槽位
 * @property slotGloves 手套槽位
 */
data class PlayerEquipment(
    val id: Long = 0,
    val playerId: Long,
    val slotHead: Long? = null,
    val slotChest: Long? = null,
    val slotWeapon: Long? = null,
    val slotOffhand: Long? = null,
    val slotRing: Long? = null,
    val slotAmulet: Long? = null,
    val slotBoots: Long? = null,
    val slotGloves: Long? = null
) {
    /**
     * 按装备槽位枚举获取对应的物品实例 ID
     */
    fun getSlot(slot: EquipmentSlot): Long? = when (slot) {
        EquipmentSlot.HEAD -> slotHead
        EquipmentSlot.CHEST -> slotChest
        EquipmentSlot.WEAPON -> slotWeapon
        EquipmentSlot.OFFHAND -> slotOffhand
        EquipmentSlot.RING -> slotRing
        EquipmentSlot.AMULET -> slotAmulet
        EquipmentSlot.BOOTS -> slotBoots
        EquipmentSlot.GLOVES -> slotGloves
    }

    /**
     * 返回指定槽位设置为新值后的副本（不可变更新）
     */
    fun withSlot(slot: EquipmentSlot, instanceId: Long?): PlayerEquipment = when (slot) {
        EquipmentSlot.HEAD -> copy(slotHead = instanceId)
        EquipmentSlot.CHEST -> copy(slotChest = instanceId)
        EquipmentSlot.WEAPON -> copy(slotWeapon = instanceId)
        EquipmentSlot.OFFHAND -> copy(slotOffhand = instanceId)
        EquipmentSlot.RING -> copy(slotRing = instanceId)
        EquipmentSlot.AMULET -> copy(slotAmulet = instanceId)
        EquipmentSlot.BOOTS -> copy(slotBoots = instanceId)
        EquipmentSlot.GLOVES -> copy(slotGloves = instanceId)
    }

    /**
     * 获取所有已装备的物品实例 ID 列表
     */
    fun allEquippedInstanceIds(): List<Long> = listOfNotNull(
        slotHead, slotChest, slotWeapon, slotOffhand,
        slotRing, slotAmulet, slotBoots, slotGloves
    )
}

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
            val id = ItemTemplates.insertAndGetId {
                it[name] = template.name
                it[type] = template.type.value
                it[subType] = template.subType.value
                it[rarity] = template.rarity.value
                it[stackable] = template.stackable
                it[baseStats] = template.baseStats
                it[levelReq] = template.levelReq
                it[price] = template.price
                it[description] = template.description
            }
            template.copy(id = id.value)
        } else {
            val existing = ItemTemplateEntity.findById(template.id)
                ?: error("Template not found: ${template.id}")
            existing.apply {
                name = template.name
                type = template.type.value
                subType = template.subType.value
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
        val now = DateTime.now()
        val id = ItemInstances.insertAndGetId {
            it[ItemInstances.templateId] = templateId
            it[ItemInstances.creatorId] = creatorId
            it[ItemInstances.createdAt] = now
        }
        ItemInstance(id = id.value, templateId = templateId, creatorId = creatorId, createdAt = now)
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
            val now = DateTime.now()
            val id = PlayerInventories.insertAndGetId {
                it[playerId] = entity.playerId
                it[templateId] = entity.templateId
                it[instanceId] = entity.instanceId
                it[quantity] = entity.quantity
                it[slotType] = entity.slotType.value
                it[slotIndex] = entity.slotIndex
                it[isBound] = entity.isBound
                it[createdAt] = now
            }
            entity.copy(id = id.value, createdAt = now)
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
            val id = PlayerEquipments.insertAndGetId {
                it[playerId] = equipment.playerId
                it[slotHead] = equipment.slotHead
                it[slotChest] = equipment.slotChest
                it[slotWeapon] = equipment.slotWeapon
                it[slotOffhand] = equipment.slotOffhand
                it[slotRing] = equipment.slotRing
                it[slotAmulet] = equipment.slotAmulet
                it[slotBoots] = equipment.slotBoots
                it[slotGloves] = equipment.slotGloves
            }
            equipment.copy(id = id.value)
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
        subType = ItemSubType.fromValue(subType),
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
