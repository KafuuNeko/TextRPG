package org.textrpg.application.data.database

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.jodatime.datetime

object ItemInstances : LongIdTable("item_instances") {
    val templateId = varchar("template_id", 128)
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
    val templateId = varchar("template_id", 128)
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
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
}