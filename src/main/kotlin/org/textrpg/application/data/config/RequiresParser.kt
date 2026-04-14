package org.textrpg.application.data.config

import org.textrpg.application.domain.model.RequiresDefinition

/**
 * Requires / Cost 解析工具
 *
 * 从 YAML 原始数据中解析 [RequiresDefinition] 和消耗映射。
 * 指令配置和技能配置共用此解析器，确保解析逻辑一致。
 *
 * @see RequiresDefinition
 */
object RequiresParser {

    /**
     * 解析前置条件
     *
     * 将 YAML 中 `requires` 节点的原始 Map 解析为 [RequiresDefinition]。
     * 所有字段均为可选，缺失或类型不匹配时使用默认值（null / 空）。
     *
     * @param raw YAML 解析后的原始数据（期望为 Map<String, Any>）
     * @return 不可变的 [RequiresDefinition] 实例
     */
    @Suppress("UNCHECKED_CAST")
    fun parseRequires(raw: Any?): RequiresDefinition {
        val map = raw as? Map<String, Any> ?: return RequiresDefinition()
        return RequiresDefinition(
            registered = map["registered"] as? Boolean,
            minAttribute = (map["minAttribute"] as? Map<String, Any>)
                ?.mapValues { (_, v) -> toDouble(v) ?: 0.0 },
            inSession = map["inSession"] as? String,
            notInSession = map["notInSession"] as? Boolean,
            atNodeTag = map["atNodeTag"] as? String,
            hasItem = (map["hasItem"] as? Map<String, Any>)
                ?.mapValues { (_, v) -> (v as? Number)?.toInt() ?: 1 },
            hasBuff = map["hasBuff"] as? String,
            notHasBuff = map["notHasBuff"] as? String
        )
    }

    /**
     * 解析消耗配置
     *
     * 将 YAML 中 `cost` 节点的原始 Map 解析为属性消耗映射（attribute_key -> 消耗量）。
     *
     * @param raw YAML 解析后的原始数据（期望为 Map<String, Number/String>）
     * @return 属性消耗映射，解析失败时返回空 Map
     */
    @Suppress("UNCHECKED_CAST")
    fun parseCost(raw: Any?): Map<String, Double> {
        val map = raw as? Map<String, Any> ?: return emptyMap()
        return map.mapValues { (_, v) -> toDouble(v) ?: 0.0 }
    }

    /**
     * 安全地将 YAML 数值（可能是 Int / Double / String）转换为 Double
     */
    fun toDouble(value: Any?): Double? = when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }
}
