package org.textrpg.application.data.registry

import com.google.gson.Gson
import org.textrpg.application.domain.model.MapTemplate
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * 地图模板注册表
 *
 * 按需加载 `static/map` 目录下的单地图 YAML 配置文件。
 * 文件名（去掉后缀）即地图 ID，地图的连接关系由 [MapConnectionRegistry] 负责管理。
 */
class MapTemplateRegistry(
    private val mYaml: Yaml,
    private val mGson: Gson,
    private val mMapConnectionRegistry: MapConnectionRegistry
) {
    companion object {
        private const val BASE_PATH: String = "resources/static/map"
        private const val MAX_CACHE_SIZE: Int = 128
    }

    private val mCache = object : LinkedHashMap<String, MapTemplate>(MAX_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MapTemplate>): Boolean {
            return size > MAX_CACHE_SIZE
        }
    }

    /**
     * 根据 ID 查询地图模板。
     */
    fun findById(id: String): MapTemplate? {
        mCache[id]?.let { return it }

        val template = loadFromDisk(id) ?: return null
        mCache[id] = template
        return template
    }

    /**
     * 获取所有地图 ID。
     */
    fun listAllIds(): List<String> {
        return mMapConnectionRegistry.listAllMapIds()
    }

    /**
     * 获取所有地图模板。
     */
    fun findAll(): List<MapTemplate> {
        return listAllIds().mapNotNull { findById(it) }
    }

    /**
     * 使指定地图缓存失效。
     */
    fun invalidate(id: String) {
        mCache.remove(id)
    }

    /**
     * 清空全部缓存。
     */
    fun invalidateAll() {
        mCache.clear()
    }

    val cacheSize: Int get() = mCache.size

    private fun loadFromDisk(id: String): MapTemplate? {
        val dir = File(BASE_PATH)
        val file = dir.resolve("$id.yaml").takeIf { it.exists() }
            ?: dir.resolve("$id.yml").takeIf { it.exists() }
            ?: return null

        return try {
            parseTemplate(id, file)
        } catch (e: Exception) {
            println("Warning: Failed to load map template '$id': ${e.message}")
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseTemplate(id: String, file: File): MapTemplate {
        val data = mYaml.load<Map<String, Any>>(file.readText())
            ?: throw IllegalArgumentException("Empty YAML file")

        val attribute = data["attribute"]
        val attributeJson = when (attribute) {
            is Map<*, *> -> mGson.toJson(stringifyAttributeMap(attribute))
            is List<*> -> mGson.toJson(attribute.map { stringifyAttributeValue(it) })
            is String -> attribute
            null -> "{}"
            else -> mGson.toJson(stringifyAttributeValue(attribute))
        }

        return MapTemplate(
            id = id,
            name = data["name"] as? String ?: throw IllegalArgumentException("Missing 'name'"),
            description = data["description"] as? String ?: "",
            attribute = attributeJson
        )
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