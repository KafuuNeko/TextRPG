package org.textrpg.application.data.registry

import com.google.gson.Gson
import org.textrpg.application.domain.model.MapConnectionIndex
import org.textrpg.application.domain.model.MapRoute
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * 地图连接注册表
 *
 * 负责读取 `resources/static/map/` 目录下的地图索引文件，提供地图 ID 与静态路径查询。
 */
class MapConnectionRegistry(
    private val mYaml: Yaml,
    private val mGson: Gson
) {
    companion object {
        private const val BASE_PATH: String = "resources/static/map"
    }

    fun listAllMapIds(): List<String> {
        return loadIndex().mapIds
    }

    fun findConnectedMapIds(mapId: String): List<String> {
        return findConnectedRoutes(mapId).map { it.targetMapId }
    }

    fun findConnectedRoutes(mapId: String): List<MapRoute> {
        return loadIndex().connections[mapId].orEmpty()
    }

    fun findAllConnections(): Map<String, List<MapRoute>> {
        return loadIndex().connections
    }

    fun findRoute(sourceMapId: String, targetMapId: String): MapRoute? {
        return findConnectedRoutes(sourceMapId).firstOrNull { it.targetMapId == targetMapId }
    }

    fun isConnected(sourceMapId: String, targetMapId: String): Boolean {
        return findRoute(sourceMapId, targetMapId) != null
    }

    private fun loadIndex(): MapConnectionIndex {
        return loadIndexFromDisk()
    }

    private fun loadIndexFromDisk(): MapConnectionIndex {
        val dir = File(BASE_PATH)
        if (!dir.exists() || !dir.isDirectory) {
            return MapConnectionIndex()
        }

        val indexFile = dir.listFiles { file -> file.isFile && (file.extension == "yaml" || file.extension == "yml") }
            ?.firstOrNull { file -> isIndexFile(file) }
            ?: return MapConnectionIndex()

        return try {
            parseIndex(indexFile)
        } catch (e: Exception) {
            println("Warning: Failed to load map index '${indexFile.name}': ${e.message}")
            MapConnectionIndex()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun isIndexFile(file: File): Boolean {
        val data = mYaml.load<Map<String, Any>>(file.readText()) ?: return false
        return data.containsKey("mapIds") || data.containsKey("connections")
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseIndex(file: File): MapConnectionIndex {
        val data = mYaml.load<Map<String, Any>>(file.readText())
            ?: throw IllegalArgumentException("Empty YAML file")

        val rawMapIds = data["mapIds"] as? List<*> ?: emptyList<Any>()
        val mapIds = rawMapIds.map { it.toString() }.distinct()

        val rawConnections = data["connections"] as? Map<*, *> ?: emptyMap<Any, Any>()
        val normalizedConnections = mutableMapOf<String, MutableList<MapRoute>>()

        mapIds.forEach { normalizedConnections.getOrPut(it) { mutableListOf() } }

        rawConnections.forEach { (rawSourceId, rawTargets) ->
            val sourceId = rawSourceId?.toString().orEmpty()
            if (sourceId.isBlank() || sourceId !in mapIds) {
                return@forEach
            }

            val sourceRoutes = normalizedConnections.getOrPut(sourceId) { mutableListOf() }
            parseRoutes(rawTargets)
                .filter { route -> route.targetMapId in mapIds && route.targetMapId != sourceId }
                .forEach { route ->
                    if (sourceRoutes.none { it.targetMapId == route.targetMapId }) {
                        sourceRoutes += route
                    }
                }
        }

        return MapConnectionIndex(
            mapIds = mapIds,
            connections = normalizedConnections.mapValues { (_, value) -> value.toList() }
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseRoutes(rawTargets: Any?): List<MapRoute> {
        return when (rawTargets) {
            is List<*> -> rawTargets.mapNotNull { parseRoute(it) }
            null -> emptyList()
            else -> listOfNotNull(parseRoute(rawTargets))
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseRoute(rawRoute: Any?): MapRoute? {
        return when (rawRoute) {
            null -> null
            is String -> MapRoute(targetMapId = rawRoute)
            is Map<*, *> -> {
                val targetMapId = listOf("targetMapId", "mapId", "id")
                    .firstNotNullOfOrNull { key -> rawRoute[key]?.toString()?.takeIf { it.isNotBlank() } }
                    ?: return null

                val attributeJson = when (val attribute = rawRoute["attribute"]) {
                    is Map<*, *> -> mGson.toJson(stringifyAttributeMap(attribute))
                    is List<*> -> mGson.toJson(attribute.map { stringifyAttributeValue(it) })
                    is String -> attribute
                    null -> "{}"
                    else -> mGson.toJson(stringifyAttributeValue(attribute))
                }

                MapRoute(targetMapId = targetMapId, attribute = attributeJson)
            }
            else -> MapRoute(targetMapId = rawRoute.toString())
        }
    }

    private fun stringifyAttributeMap(attribute: Map<*, *>): Map<String, Any?> {
        return attribute.entries.associate { (key, value) ->
            key.toString() to stringifyAttributeValue(value)
        }
    }

    private fun stringifyAttributeValue(value: Any?): Any? {
        return when (value) {
            null -> null
            is Map<*, *> -> stringifyAttributeMap(value)
            is List<*> -> value.map { stringifyAttributeValue(it) }
            else -> value.toString()
        }
    }
}