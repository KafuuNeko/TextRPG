package org.textrpg.application.data.database

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.jodatime.datetime

object ItemInstances : LongIdTable("item_instances") {
    val templateId = varchar("template_id", 128)
    val attribute = text("attribute").default("{}")
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

object Players : LongIdTable("players") {
    val name = varchar("name", 64).uniqueIndex()
    val bindAccount = varchar("bind_account", 128)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
}