package org.textrpg.application.data.config

import org.textrpg.application.domain.model.BuffDefinition
import org.textrpg.application.domain.model.BuffModifierDefinition
import org.textrpg.application.domain.model.StackPolicy
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Buff 配置加载结果
 *
 * @property buffs Buff 定义映射（buff_key -> [BuffDefinition]）
 */
data class BuffConfig(
    val buffs: Map<String, BuffDefinition> = emptyMap()
)

/**
 * Buff 配置加载器
 *
 * 从 YAML 文件加载 Buff 定义，解析为不可变的 [BuffDefinition] 领域模型。
 * 复用 [EffectConfigLoader] 解析 tickEffects，复用 [RequiresParser] 解析数值。
 *
 * YAML 格式示例：
 * ```yaml
 * buffs:
 *   burn_dot:
 *     displayName: "灼烧"
 *     maxStacks: 5
 *     duration: 3
 *     stackPolicy: "stack"
 *     modifiers:
 *       - attribute: "fire_resist"
 *         type: "flat"
 *         value: -10
 *     tickEffects:
 *       - type: "modify_attribute"
 *         target: "self"
 *         attribute: "current_hp"
 *         amount: "-10 * stacks"
 *     onApply: "scripts/burn_apply.kts"
 * ```
 *
 * @see BuffDefinition
 */
object BuffConfigLoader {
    private val yaml = Yaml()
    private const val DEFAULT_PATH = "src/main/resources/config/buffs.yaml"

    /**
     * 从指定路径加载 Buff 配置
     *
     * @param path YAML 配置文件路径（默认 [DEFAULT_PATH]）
     * @return [BuffConfig] 实例，加载失败时返回默认空配置
     */
    fun load(path: String = DEFAULT_PATH): BuffConfig {
        val file = File(path)
        if (!file.exists()) {
            println("Warning: Buff config not found at $path, using empty definitions")
            return BuffConfig()
        }
        return try {
            val raw = yaml.load<Map<String, Any>>(file.readText()) ?: return BuffConfig()
            @Suppress("UNCHECKED_CAST")
            val buffsRaw = raw["buffs"] as? Map<String, Map<String, Any>> ?: emptyMap()
            val buffs = buffsRaw.mapValues { (key, props) -> parseBuff(key, props) }
            BuffConfig(buffs = buffs)
        } catch (e: Exception) {
            println("Warning: Failed to load buff config from $path: ${e.message}")
            BuffConfig()
        }
    }

    /**
     * 解析单个 Buff 定义
     */
    private fun parseBuff(key: String, props: Map<String, Any>): BuffDefinition {
        return BuffDefinition(
            key = key,
            displayName = props["displayName"] as? String ?: key,
            description = props["description"] as? String ?: "",
            maxStacks = (props["maxStacks"] as? Number)?.toInt() ?: 1,
            duration = (props["duration"] as? Number)?.toInt() ?: -1,
            stackPolicy = (props["stackPolicy"] as? String)
                ?.let { StackPolicy.fromValue(it) }
                ?: StackPolicy.REFRESH,
            modifiers = parseModifiers(props["modifiers"]),
            tickEffects = EffectConfigLoader.parseEffectList(props["tickEffects"]),
            onApply = props["onApply"] as? String,
            onRemove = props["onRemove"] as? String,
            onStack = props["onStack"] as? String
        )
    }

    /**
     * 解析 Buff 修正器定义列表
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseModifiers(raw: Any?): List<BuffModifierDefinition> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { item ->
            val props = item as? Map<String, Any> ?: return@mapNotNull null
            val attribute = props["attribute"] as? String ?: return@mapNotNull null
            val type = props["type"] as? String ?: return@mapNotNull null
            val value = RequiresParser.toDouble(props["value"]) ?: return@mapNotNull null
            BuffModifierDefinition(
                attribute = attribute,
                type = type,
                value = value,
                priority = (props["priority"] as? Number)?.toInt() ?: 0
            )
        }
    }
}
