package org.textrpg.application.domain.model

/**
 * 地图静态配置
 *
 * @property id 地图 ID，对应 YAML 文件名
 * @property name 地图名称
 * @property description 地图描述
 * @property attribute 地图属性 JSON，所有叶子节点值均按字符串保存
 */
data class MapTemplate(
    val id: String,
    val name: String,
    val description: String = "",
    val attribute: String = "{}"
)

/**
 * 地图路径配置
 *
 * @property targetMapId 路径可到达的目标地图 ID
 * @property attribute 路径属性 JSON，所有叶子节点值均按字符串保存
 */
data class MapRoute(
    val targetMapId: String,
    val attribute: String = "{}"
)

/**
 * 地图连接索引
 *
 * @property mapIds 所有地图 ID 列表
 * @property connections 地图连接表，key 为地图 ID，value 为该地图可出发的有向路径列表
 */
data class MapConnectionIndex(
    val mapIds: List<String> = emptyList(),
    val connections: Map<String, List<MapRoute>> = emptyMap()
)

/**
 * 玩家所在地图关系
 *
 * @property mapId 玩家当前所在地图 ID
 * @property playerId 玩家 ID
 */
data class MapPlayerRelation(
    val mapId: String,
    val playerId: Long
)

/**
 * 地图动态属性
 *
 * @property mapId 地图 ID
 * @property name 属性名
 * @property value 属性值
 */
data class MapAttribute(
    val mapId: String,
    val name: String,
    val value: String
)

/**
 * 玩家在指定地图中的持久属性
 *
 * @property mapId 地图 ID
 * @property playerId 玩家 ID
 * @property name 属性名
 * @property value 属性值
 */
data class MapPlayerAttribute(
    val mapId: String,
    val playerId: Long,
    val name: String,
    val value: String
)