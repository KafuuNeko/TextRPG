package org.textrpg.application.data.database

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.jodatime.datetime

object ItemTemplates : IntIdTable("item_templates") {
    val name = varchar("name", 128)
    val type = integer("type")
    val subType = integer("sub_type").default(0)
    val rarity = integer("rarity").default(1)
    val stackable = bool("stackable").default(true)
    val baseStats = text("base_stats").default("{}")
    val levelReq = integer("level_req").default(0)
    val price = integer("price").default(0)
    val description = text("description").default("")
}

object ItemInstances : LongIdTable("item_instances") {
    val templateId = integer("template_id").references(ItemTemplates.id)
    val level = integer("level").default(0)
    val exp = long("exp").default(0L)
    val durability = integer("durability").default(100)
    val randomStats = text("random_stats").default("[]")
    val sockets = text("sockets").default("[]")
    val creatorId = long("creator_id").nullable()
    val createdAt = datetime("created_at")
}

object PlayerInventories : LongIdTable("player_inventories") {
    val playerId = long("player_id").index()
    val templateId = integer("template_id").references(ItemTemplates.id)
    val instanceId = long("instance_id").nullable().index()
    val quantity = integer("quantity").default(1)
    val slotType = integer("slot_type").default(1)
    val slotIndex = integer("slot_index").default(0)
    val isBound = bool("is_bound").default(false)
    val createdAt = datetime("created_at")
}

object PlayerEquipments : LongIdTable("player_equipments") {
    val playerId = long("player_id").uniqueIndex()
    val slotHead = long("slot_head").nullable()
    val slotChest = long("slot_chest").nullable()
    val slotWeapon = long("slot_weapon").nullable()
    val slotOffhand = long("slot_offhand").nullable()
    val slotRing = long("slot_ring").nullable()
    val slotAmulet = long("slot_amulet").nullable()
    val slotBoots = long("slot_boots").nullable()
    val slotGloves = long("slot_gloves").nullable()
}

object Players : LongIdTable("players") {
    val name = varchar("name", 64).uniqueIndex()
    val bindAccount = varchar("bind_account", 128)
    /** 属性基础值 JSON 快照，格式：{"strength": 15, "current_hp": 80, ...} */
    val attributeData = text("attribute_data").default("{}")
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
}

/**
 * 玩家持久性 Buff 表
 *
 * 存储需要跨会话保留的 Buff 状态（如中毒、祝福等非战斗临时 Buff）。
 * 战斗中的临时 Buff 不在此表存储。
 */
object PlayerBuffs : LongIdTable("player_buffs") {
    val playerId = long("player_id").index()
    val buffId = varchar("buff_id", 128)
    val stacks = integer("stacks").default(1)
    val remainingDuration = integer("remaining_duration").default(-1)
    val createdAt = datetime("created_at")
}