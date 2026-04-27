package org.textrpg.application.data.manager

import org.textrpg.application.data.registry.MapConnectionRegistry
import org.textrpg.application.data.registry.MapTemplateRegistry
import org.textrpg.application.data.repository.MapRepository
import org.textrpg.application.domain.model.MapAttribute
import org.textrpg.application.domain.model.MapPlayerAttribute
import org.textrpg.application.domain.model.MapPlayerRelation
import org.textrpg.application.domain.model.MapRoute
import org.textrpg.application.domain.model.MapTemplate

/**
 * 地图管理器
 *
 * 对外提供地图模板与地图连接关系的统一访问入口。
 */
class MapManager(
    private val mMapRepository: MapRepository,
    private val mMapTemplateRegistry: MapTemplateRegistry,
    private val mMapConnectionRegistry: MapConnectionRegistry
) {

    /**
     * 根据地图 ID 查询地图模板。
     */
    fun findTemplateById(mapId: String): MapTemplate? {
        return mMapTemplateRegistry.findById(mapId)
    }

    /**
     * 获取全部地图 ID。
     */
    fun listAllMapIds(): List<String> {
        return mMapConnectionRegistry.listAllMapIds()
    }

    /**
     * 获取全部地图模板。
     */
    fun findAllTemplates(): List<MapTemplate> {
        return mMapTemplateRegistry.findAll()
    }

    /**
     * 获取指定地图的直连地图 ID 列表。
     */
    fun listConnectedMapIds(mapId: String): List<String> {
        return mMapConnectionRegistry.findConnectedMapIds(mapId)
    }

    /**
     * 获取指定地图的直连路径列表。
     */
    fun findConnectedRoutes(mapId: String): List<MapRoute> {
        return mMapConnectionRegistry.findConnectedRoutes(mapId)
    }

    /**
     * 获取全部地图连接表。
     */
    fun findAllConnections(): Map<String, List<MapRoute>> {
        return mMapConnectionRegistry.findAllConnections()
    }

    /**
     * 获取指定起点到终点的路径配置。
     */
    fun findRoute(sourceMapId: String, targetMapId: String): MapRoute? {
        return mMapConnectionRegistry.findRoute(sourceMapId, targetMapId)
    }

    /**
     * 获取指定地图的直连地图模板列表。
     */
    fun findConnectedTemplates(mapId: String): List<MapTemplate> {
        return findConnectedRoutes(mapId).mapNotNull { route ->
            mMapTemplateRegistry.findById(route.targetMapId)
        }
    }

    /**
     * 判断两个地图是否直接相连。
     */
    fun isConnected(sourceMapId: String, targetMapId: String): Boolean {
        return mMapConnectionRegistry.isConnected(sourceMapId, targetMapId)
    }

    /**
     * 获取玩家当前所在地图关系。
     */
    fun findPlayerRelation(playerId: Long): MapPlayerRelation? {
        return mMapRepository.findPlayerRelation(playerId)
    }

    /**
     * 获取指定地图中的所有玩家关系。
     */
    fun findPlayersInMap(mapId: String): List<MapPlayerRelation> {
        return mMapRepository.findPlayersByMapId(mapId)
    }

    /**
     * 获取指定地图中的所有玩家 ID。
     */
    fun listPlayerIdsInMap(mapId: String): List<Long> {
        return findPlayersInMap(mapId).map { it.playerId }
    }

    /**
     * 保存或更新玩家所在地图关系。
     */
    fun savePlayerRelation(relation: MapPlayerRelation): MapPlayerRelation {
        require(mMapTemplateRegistry.findById(relation.mapId) != null) {
            "Map template not found: ${relation.mapId}"
        }
        return mMapRepository.savePlayerRelation(relation)
    }

    /**
     * 删除玩家所在地图关系。
     */
    fun deletePlayerRelation(playerId: Long): Boolean {
        return mMapRepository.deletePlayerRelation(playerId)
    }

    /**
     * 获取玩家在指定地图中的某个持久属性。
     */
    fun getPlayerAttribute(mapId: String, playerId: Long, attributeName: String): MapPlayerAttribute? {
        return mMapRepository.getPlayerAttribute(mapId, playerId, attributeName)
    }

    /**
     * 获取玩家在指定地图中的全部持久属性。
     */
    fun getAllPlayerAttributes(mapId: String, playerId: Long): List<MapPlayerAttribute> {
        return mMapRepository.getAllPlayerAttributes(mapId, playerId)
    }

    /**
     * 保存或更新玩家在指定地图中的持久属性。
     */
    fun savePlayerAttribute(attribute: MapPlayerAttribute): MapPlayerAttribute {
        require(mMapTemplateRegistry.findById(attribute.mapId) != null) {
            "Map template not found: ${attribute.mapId}"
        }
        return mMapRepository.savePlayerAttribute(attribute)
    }

    /**
     * 删除玩家在指定地图中的持久属性。
     */
    fun deletePlayerAttribute(mapId: String, playerId: Long, attributeName: String): Boolean {
        return mMapRepository.deletePlayerAttribute(mapId, playerId, attributeName)
    }

    /**
     * 获取地图指定动态属性。
     */
    fun getAttribute(mapId: String, attributeName: String): MapAttribute? {
        return mMapRepository.getAttribute(mapId, attributeName)
    }

    /**
     * 获取地图全部动态属性。
     */
    fun getAllAttributes(mapId: String): List<MapAttribute> {
        return mMapRepository.getAllAttributes(mapId)
    }

    /**
     * 保存或更新地图动态属性。
     */
    fun saveAttribute(attribute: MapAttribute): MapAttribute {
        require(mMapTemplateRegistry.findById(attribute.mapId) != null) {
            "Map template not found: ${attribute.mapId}"
        }
        return mMapRepository.saveAttribute(attribute)
    }

    /**
     * 删除地图动态属性。
     */
    fun deleteAttribute(mapId: String, attributeName: String): Boolean {
        return mMapRepository.deleteAttribute(mapId, attributeName)
    }

    /**
     * 刷新地图静态数据缓存。
     */
    fun invalidateAll() {
        mMapTemplateRegistry.invalidateAll()
    }
}