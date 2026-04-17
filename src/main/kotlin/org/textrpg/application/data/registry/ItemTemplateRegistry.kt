package org.textrpg.application.data.registry

import com.google.gson.Gson
import org.textrpg.application.domain.model.ItemRarity
import org.textrpg.application.domain.model.ItemSubType
import org.textrpg.application.domain.model.ItemTemplate
import org.textrpg.application.domain.model.ItemType
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * 物品模板注册表——静态数据懒加载器
 *
 * 使用 LRU 缓存策略按需加载 `static/item/` 目录下的 YAML 文件。
 * 文件名（去掉后缀）即为物品模板 ID，YAML 中不存储 id 字段。
 *
 * - 首次访问某个模板时从磁盘读取并缓存
 * - 缓存满时自动淘汰最早访问的数据（LRU）
 * - [findAll] 返回当前目录下所有可用的模板 ID 列表（不加载全部到内存）
 *
 */
class ItemTemplateRegistry(
    private val mYaml: Yaml,
    private val mGson: Gson
) {
    companion object {
        private const val BASE_PATH: String = "resources/static/item"
        private const val MAX_CACHE_SIZE: Int = 128
    }

    private val mCache = object : LinkedHashMap<String, ItemTemplate>(MAX_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ItemTemplate>): Boolean {
            return size > MAX_CACHE_SIZE
        }
    }



    /**
     * 根据 ID 查询物品模板（懒加载）
     *
     * 优先从缓存获取，缓存未命中时从磁盘加载。
     * 缓存超过 [MAX_CACHE_SIZE] 时，最久未访问的条目会被自动淘汰。
     *
     * @param id 模板 ID（即 YAML 文件名，不含后缀）
     * @return 模板实例，文件不存在或解析失败则返回 null
     */
    fun findById(id: String): ItemTemplate? {
        mCache[id]?.let { return it }

        val template = loadFromDisk(id) ?: return null
        mCache[id] = template
        return template
    }

    /**
     * 获取所有可用的模板 ID 列表
     *
     * 仅扫描目录文件名，不加载文件内容到内存。
     *
     * @return 所有可用模板 ID 的列表
     */
    fun listAllIds(): List<String> {
        val dir = File(BASE_PATH)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.extension == "yaml" || f.extension == "yml" }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()
    }

    /**
     * 获取所有物品模板
     *
     * 注意：此方法会加载所有模板到内存（受 LRU 缓存大小限制，
     * 超出部分会淘汰最早的条目）。适合在模板总量较少时使用。
     *
     * @return 所有模板的列表
     */
    fun findAll(): List<ItemTemplate> {
        return listAllIds().mapNotNull { findById(it) }
    }

    /**
     * 使指定模板的缓存失效
     *
     * 下次访问该模板时将重新从磁盘加载。
     *
     * @param id 模板 ID
     */
    fun invalidate(id: String) {
        mCache.remove(id)
    }

    /**
     * 清空全部缓存
     *
     * 所有模板在下次访问时将重新从磁盘加载。
     */
    fun invalidateAll() {
        mCache.clear()
    }

    /**
     * 当前缓存中的模板数量
     */
    val cacheSize: Int get() = mCache.size

    /**
     * 从磁盘加载单个模板
     */
    private fun loadFromDisk(id: String): ItemTemplate? {
        val dir = File(BASE_PATH)
        val file = dir.resolve("$id.yaml").takeIf { it.exists() }
            ?: dir.resolve("$id.yml").takeIf { it.exists() }
            ?: return null

        return try {
            parseTemplate(id, file)
        } catch (e: Exception) {
            println("Warning: Failed to load item template '$id': ${e.message}")
            null
        }
    }

    /**
     * 解析单个 YAML 文件为 ItemTemplate
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseTemplate(id: String, file: File): ItemTemplate {
        val data = mYaml.load<Map<String, Any>>(file.readText())
            ?: throw IllegalArgumentException("Empty YAML file")

        val baseStats = data["baseStats"]
        val baseStatsJson = when (baseStats) {
            is Map<*, *> -> mGson.toJson(baseStats)
            is String -> baseStats
            null -> "{}"
            else -> mGson.toJson(baseStats)
        }

        return ItemTemplate(
            id = id,
            name = data["name"] as? String ?: throw IllegalArgumentException("Missing 'name'"),
            type = ItemType.fromName(data["type"] as? String ?: throw IllegalArgumentException("Missing 'type'")),
            subType = (data["subType"] as? String)?.let { ItemSubType.fromName(it) } ?: ItemSubType.WEAPON,
            rarity = (data["rarity"] as? String)?.let { ItemRarity.fromName(it) } ?: ItemRarity.WHITE,
            stackable = data["stackable"] as? Boolean ?: true,
            baseStats = baseStatsJson,
            levelReq = (data["levelReq"] as? Number)?.toInt() ?: 0,
            price = (data["price"] as? Number)?.toInt() ?: 0,
            description = data["description"] as? String ?: ""
        )
    }
}
