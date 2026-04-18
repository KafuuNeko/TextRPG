package org.textrpg.application.data.repository

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.textrpg.application.data.database.PlayerAttributes
import org.textrpg.application.domain.model.PlayerAttribute

/**
 * 玩家属性仓储
 * 
 * 专门处理玩家属性（EAV 表结构）的持久化。
 *
 * @param mDatabase Exposed Database 实例
 */
class PlayerAttributeRepository(private val mDatabase: Database) {

    /**
     * 获取玩家指定名称的属性值
     */
    fun getAttribute(playerId: Long, attributeName: String): PlayerAttribute? = transaction(mDatabase) {
        val row = PlayerAttributes.selectAll()
            .where { (PlayerAttributes.id eq playerId) and (PlayerAttributes.name eq attributeName) }
            .singleOrNull()
        row?.let {
            PlayerAttribute(playerId, attributeName, it[PlayerAttributes.value])
        }
    }

    /**
     * 获取玩家所有的属性
     */
    fun getAllAttributes(playerId: Long): List<PlayerAttribute> = transaction(mDatabase) {
        PlayerAttributes.selectAll()
            .where { PlayerAttributes.id eq playerId }
            .map {
                PlayerAttribute(
                    id = it[PlayerAttributes.id],
                    name = it[PlayerAttributes.name],
                    value = it[PlayerAttributes.value]
                )
            }
    }

    /**
     * 保存或更新属性
     */
    fun saveAttribute(attribute: PlayerAttribute) = transaction(mDatabase) {
        val exists = PlayerAttributes.selectAll()
            .where { (PlayerAttributes.id eq attribute.id) and (PlayerAttributes.name eq attribute.name) }
            .count() > 0

        if (exists) {
            PlayerAttributes.update({ (PlayerAttributes.id eq attribute.id) and (PlayerAttributes.name eq attribute.name) }) {
                it[PlayerAttributes.value] = attribute.value
            }
        } else {
            PlayerAttributes.insert {
                it[PlayerAttributes.id] = attribute.id
                it[PlayerAttributes.name] = attribute.name
                it[PlayerAttributes.value] = attribute.value
            }
        }
    }

    /**
     * 删除属性
     */
    fun deleteAttribute(attribute: PlayerAttribute): Boolean = transaction(mDatabase) {
        PlayerAttributes
            .deleteWhere { (PlayerAttributes.id eq attribute.id) and (PlayerAttributes.name eq attribute.name) } > 0
    }
}
