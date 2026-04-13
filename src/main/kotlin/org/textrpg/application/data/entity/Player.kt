package org.textrpg.application.data.entity

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.textrpg.application.data.database.Players

/**
 * 玩家 Exposed DAO 实体
 *
 * 对应数据库 `players` 表，仅包含玩家基础信息。
 * 游戏数值（等级、经验、生命值等）由独立的数值服务管理，不在此实体中。
 *
 * @param id EntityID<Long>，数据库自增主键
 */
class PlayerEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<PlayerEntity>(Players)

    var name by Players.name
    var bindAccount by Players.bindAccount
    var createdAt by Players.createdAt
    var updatedAt by Players.updatedAt
}