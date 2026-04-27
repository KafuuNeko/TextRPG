package org.textrpg.application.data.database

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.jodatime.datetime

object ItemInstances : LongIdTable("item_instances") {
    val templateId = varchar("template_id", 128)
    val attribute = text("attribute").default("{}")
    val creatorId = long("creator_id").nullable()
    val createdAt = datetime("created_at")
}

object PlayerItems : LongIdTable("player_items") {
    val playerId = long("player_id").index()
    val templateId = varchar("template_id", 128)
    val instanceId = long("instance_id").nullable().index()
    val quantity = integer("quantity").default(1)
    val slotType = varchar("slot_type", 32).default("inventory")
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

object MapPlayers : org.jetbrains.exposed.sql.Table("map_players") {
    val mapId = varchar("map_id", 128)
    val playerId = long("player_id").references(Players.id).uniqueIndex()

    override val primaryKey = PrimaryKey(mapId, playerId)
}

object MapPlayerAttributes : org.jetbrains.exposed.sql.Table("map_player_attributes") {
    val mapId = varchar("map_id", 128)
    val playerId = long("player_id").references(Players.id)
    val name = varchar("name", 64)
    val value = text("value")

    override val primaryKey = PrimaryKey(mapId, playerId, name)
}

object MapAttributes : org.jetbrains.exposed.sql.Table("map_attributes") {
    val mapId = varchar("map_id", 128)
    val name = varchar("name", 64)
    val value = text("value")

    override val primaryKey = PrimaryKey(mapId, name)
}

object PlayerAttributes : org.jetbrains.exposed.sql.Table("player_attributes") {
    val id = long("id").references(Players.id)
    val name = varchar("name", 64)
    val value = double("value")

    override val primaryKey = PrimaryKey(id, name)
}