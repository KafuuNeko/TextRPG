package org.textrpg.application.data.config

import org.textrpg.application.domain.model.*
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * 属性配置加载器
 *
 * 从 YAML 文件加载属性定义，解析为不可变的 [AttributeDefinition] 领域模型。
 * YAML 中的属性 key 即为属性的唯一标识符，在整个引擎中作为属性的引用名。
 *
 * YAML 格式示例：
 * ```yaml
 * attributes:
 *   strength:
 *     tier: 1
 *     displayName: "力量"
 *     defaultValue: 10
 *     min: 0
 *     max: 9999
 * ```
 *
 * @see AttributeDefinition
 */
object AttributeConfigLoader {
    private val yaml = Yaml()
    private const val DEFAULT_PATH = "src/main/resources/config/attributes.yaml"

    /**
     * 从指定路径加载属性配置
     *
     * 解析 YAML 文件中 `attributes` 节点下的所有属性定义，
     * 每个 key 作为属性标识符，对应的 value 映射为 [AttributeDefinition]。
     *
     * @param path YAML 配置文件路径（默认 [DEFAULT_PATH]）
     * @return 属性定义映射（key -> [AttributeDefinition]），加载失败或文件不存在时返回空 Map
     */
    fun load(path: String = DEFAULT_PATH): Map<String, AttributeDefinition> {
        val file = File(path)
        if (!file.exists()) {
            println("Warning: Attribute config not found at $path, using empty definitions")
            return emptyMap()
        }
        return try {
            val raw = yaml.load<Map<String, Any>>(file.readText()) ?: return emptyMap()
            @Suppress("UNCHECKED_CAST")
            val attributes = raw["attributes"] as? Map<String, Map<String, Any>> ?: return emptyMap()
            attributes.mapValues { (key, props) -> parseDefinition(key, props) }
        } catch (e: Exception) {
            println("Warning: Failed to load attribute config from $path: ${e.message}")
            emptyMap()
        }
    }

    /**
     * 解析单个属性定义
     *
     * @param key 属性标识符
     * @param props YAML 解析后的属性字段映射
     * @return 不可变的 [AttributeDefinition] 实例
     */
    private fun parseDefinition(key: String, props: Map<String, Any>): AttributeDefinition {
        return AttributeDefinition(
            key = key,
            tier = (props["tier"] as? Int)
                ?.let { AttributeTier.fromValue(it) }
                ?: AttributeTier.PRIMARY,
            displayName = props["displayName"] as? String ?: key,
            description = props["description"] as? String ?: "",
            defaultValue = toDouble(props["defaultValue"]) ?: 0.0,
            min = toDouble(props["min"]) ?: 0.0,
            max = toDouble(props["max"]) ?: 9999.0,
            formula = props["formula"] as? String,
            boundMax = props["boundMax"] as? String,
            triggers = parseTriggers(props["triggers"]),
            regen = parseRegen(props["regen"])
        )
    }

    /**
     * 解析触发器配置
     *
     * 支持两种 YAML 写法：
     * - 简写：`onDeplete: "death"`（仅 action）
     * - 完整：`onDeplete: { action: "death", value: "max_hp * 0.2" }`
     *
     * @param raw YAML 解析后的触发器原始数据
     * @return 触发器类型 -> 触发器定义的映射
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseTriggers(raw: Any?): Map<TriggerType, TriggerDefinition> {
        val map = raw as? Map<String, Any> ?: return emptyMap()
        return map.mapNotNull { (typeStr, config) ->
            val triggerType = TriggerType.entries.find { it.value == typeStr }
                ?: return@mapNotNull null
            val triggerDef = when (config) {
                // 简写形式：直接写 action 字符串
                is String -> TriggerDefinition(action = config)
                // 完整形式：包含 action 和可选的 value
                is Map<*, *> -> TriggerDefinition(
                    action = config["action"] as? String ?: return@mapNotNull null,
                    value = config["value"]?.toString()
                )
                else -> return@mapNotNull null
            }
            triggerType to triggerDef
        }.toMap()
    }

    /**
     * 解析自动回复配置
     *
     * @param raw YAML 解析后的回复配置原始数据
     * @return [RegenDefinition] 实例，解析失败返回 null
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseRegen(raw: Any?): RegenDefinition? {
        val map = raw as? Map<String, Any> ?: return null
        return RegenDefinition(
            amount = toDouble(map["amount"]) ?: return null,
            interval = map["interval"] as? String ?: return null
        )
    }

    /**
     * 安全地将 YAML 数值（可能是 Int / Double / String）转换为 Double
     *
     * @param value YAML 解析后的原始值
     * @return 转换后的 Double 值，无法转换时返回 null
     */
    private fun toDouble(value: Any?): Double? = when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }
}
