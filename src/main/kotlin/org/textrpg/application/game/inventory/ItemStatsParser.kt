package org.textrpg.application.game.inventory

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.github.oshai.kotlinlogging.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * 物品属性解析器
 *
 * 将 [ItemTemplate.baseStats] 的 JSON 字符串解析为属性映射。
 * 装备穿戴时，引擎遍历解析结果，为每个属性挂载 flat 类型修正器。
 *
 * JSON 格式示例：`{"physical_attack": 15, "strength": 3}`
 *
 * @see org.textrpg.application.game.equipment.EquipmentService
 */
object ItemStatsParser {
    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, Double>>() {}.type

    /**
     * 解析 baseStats JSON 为属性映射
     *
     * @param json JSON 字符串，格式为 `{"attribute_key": value, ...}`
     * @return 属性 key → 数值的映射，解析失败返回空 Map
     */
    fun parseBaseStats(json: String): Map<String, Double> {
        if (json.isBlank() || json == "{}") return emptyMap()
        return runCatching { gson.fromJson<Map<String, Double>>(json, mapType) ?: emptyMap() }
            .getOrElse { e ->
                log.warn(e) { "Failed to parse baseStats" }
                emptyMap()
            }
    }
}
