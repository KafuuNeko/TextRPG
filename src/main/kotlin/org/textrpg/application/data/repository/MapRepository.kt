package org.textrpg.application.data.repository

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.textrpg.application.data.database.MapAttributes
import org.textrpg.application.data.database.MapPlayerAttributes
import org.textrpg.application.data.database.MapPlayers
import org.textrpg.application.domain.model.MapAttribute
import org.textrpg.application.domain.model.MapPlayerAttribute
import org.textrpg.application.domain.model.MapPlayerRelation

/**
 * 地图仓储
 *
 * 负责地图相关动态数据持久化：
 * - 玩家当前所在地图关系
 * - 地图动态属性（EAV）
 */
class MapRepository(
    private val mDatabase: Database
) {

    /**
     * 获取玩家当前所在地图。
     */
    fun findPlayerRelation(playerId: Long): MapPlayerRelation? = transaction(mDatabase) {
        MapPlayers.selectAll()
            .where { MapPlayers.playerId eq playerId }
            .singleOrNull()
            ?.toMapPlayerRelation()
    }

    /**
     * 获取指定地图中的所有玩家关系。
     */
    fun findPlayersByMapId(mapId: String): List<MapPlayerRelation> = transaction(mDatabase) {
        MapPlayers.selectAll()
            .where { MapPlayers.mapId eq mapId }
            .map { it.toMapPlayerRelation() }
    }

    /**
     * 保存或更新玩家所在地图关系。
     *
     * 同一玩家只允许存在一条所在地图记录。
     */
    fun savePlayerRelation(relation: MapPlayerRelation): MapPlayerRelation = transaction(mDatabase) {
        val exists = MapPlayers.selectAll()
            .where { MapPlayers.playerId eq relation.playerId }
            .count() > 0

        if (exists) {
            MapPlayers.update({ MapPlayers.playerId eq relation.playerId }) {
                it[mapId] = relation.mapId
            }
        } else {
            MapPlayers.insert {
                it[mapId] = relation.mapId
                it[playerId] = relation.playerId
            }
        }

        relation
    }

    /**
     * 删除玩家所在地图关系。
     */
    fun deletePlayerRelation(playerId: Long): Boolean = transaction(mDatabase) {
        MapPlayers.deleteWhere { MapPlayers.playerId eq playerId } > 0
    }

    /**
     * 获取玩家在指定地图中的某个持久属性。
     */
    fun getPlayerAttribute(mapId: String, playerId: Long, attributeName: String): MapPlayerAttribute? = transaction(mDatabase) {
        MapPlayerAttributes.selectAll()
            .where {
                (MapPlayerAttributes.mapId eq mapId) and
                    (MapPlayerAttributes.playerId eq playerId) and
                    (MapPlayerAttributes.name eq attributeName)
            }
            .singleOrNull()
            ?.toMapPlayerAttribute()
    }

    /**
     * 获取玩家在指定地图中的全部持久属性。
     */
    fun getAllPlayerAttributes(mapId: String, playerId: Long): List<MapPlayerAttribute> = transaction(mDatabase) {
        MapPlayerAttributes.selectAll()
            .where { (MapPlayerAttributes.mapId eq mapId) and (MapPlayerAttributes.playerId eq playerId) }
            .map { it.toMapPlayerAttribute() }
    }

    /**
     * 保存或更新玩家在指定地图中的持久属性。
     */
    fun savePlayerAttribute(attribute: MapPlayerAttribute): MapPlayerAttribute = transaction(mDatabase) {
        val exists = MapPlayerAttributes.selectAll()
            .where {
                (MapPlayerAttributes.mapId eq attribute.mapId) and
                    (MapPlayerAttributes.playerId eq attribute.playerId) and
                    (MapPlayerAttributes.name eq attribute.name)
            }
            .count() > 0

        if (exists) {
            MapPlayerAttributes.update({
                (MapPlayerAttributes.mapId eq attribute.mapId) and
                    (MapPlayerAttributes.playerId eq attribute.playerId) and
                    (MapPlayerAttributes.name eq attribute.name)
            }) {
                it[value] = attribute.value
            }
        } else {
            MapPlayerAttributes.insert {
                it[mapId] = attribute.mapId
                it[playerId] = attribute.playerId
                it[name] = attribute.name
                it[value] = attribute.value
            }
        }

        attribute
    }

    /**
     * 删除玩家在指定地图中的持久属性。
     */
    fun deletePlayerAttribute(mapId: String, playerId: Long, attributeName: String): Boolean = transaction(mDatabase) {
        MapPlayerAttributes.deleteWhere {
            (MapPlayerAttributes.mapId eq mapId) and
                (MapPlayerAttributes.playerId eq playerId) and
                (MapPlayerAttributes.name eq attributeName)
        } > 0
    }

    /**
     * 获取地图指定动态属性。
     */
    fun getAttribute(mapId: String, attributeName: String): MapAttribute? = transaction(mDatabase) {
        MapAttributes.selectAll()
            .where { (MapAttributes.mapId eq mapId) and (MapAttributes.name eq attributeName) }
            .singleOrNull()
            ?.toMapAttribute()
    }

    /**
     * 获取地图全部动态属性。
     */
    fun getAllAttributes(mapId: String): List<MapAttribute> = transaction(mDatabase) {
        MapAttributes.selectAll()
            .where { MapAttributes.mapId eq mapId }
            .map { it.toMapAttribute() }
    }

    /**
     * 保存或更新地图动态属性。
     */
    fun saveAttribute(attribute: MapAttribute): MapAttribute = transaction(mDatabase) {
        val exists = MapAttributes.selectAll()
            .where { (MapAttributes.mapId eq attribute.mapId) and (MapAttributes.name eq attribute.name) }
            .count() > 0

        if (exists) {
            MapAttributes.update({ (MapAttributes.mapId eq attribute.mapId) and (MapAttributes.name eq attribute.name) }) {
                it[value] = attribute.value
            }
        } else {
            MapAttributes.insert {
                it[mapId] = attribute.mapId
                it[name] = attribute.name
                it[value] = attribute.value
            }
        }

        attribute
    }

    /**
     * 删除地图动态属性。
     */
    fun deleteAttribute(mapId: String, attributeName: String): Boolean = transaction(mDatabase) {
        MapAttributes.deleteWhere {
            (MapAttributes.mapId eq mapId) and (MapAttributes.name eq attributeName)
        } > 0
    }

    private fun org.jetbrains.exposed.sql.ResultRow.toMapPlayerRelation() = MapPlayerRelation(
        mapId = this[MapPlayers.mapId],
        playerId = this[MapPlayers.playerId]
    )

    private fun org.jetbrains.exposed.sql.ResultRow.toMapPlayerAttribute() = MapPlayerAttribute(
        mapId = this[MapPlayerAttributes.mapId],
        playerId = this[MapPlayerAttributes.playerId],
        name = this[MapPlayerAttributes.name],
        value = this[MapPlayerAttributes.value]
    )

    private fun org.jetbrains.exposed.sql.ResultRow.toMapAttribute() = MapAttribute(
        mapId = this[MapAttributes.mapId],
        name = this[MapAttributes.name],
        value = this[MapAttributes.value]
    )
}