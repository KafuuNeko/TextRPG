package org.textrpg.application.data.config

import io.github.oshai.kotlinlogging.KotlinLogging
import org.textrpg.application.domain.model.BuffDefinition
import org.textrpg.application.domain.model.BuffModifierDefinition
import org.textrpg.application.domain.model.ModifierType
import org.textrpg.application.domain.model.StackPolicy

private val log = KotlinLogging.logger {}

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
object BuffConfigLoader : AbstractYamlLoader<BuffConfig>() {
    override val defaultPath = "src/main/resources/config/buffs.yaml"
    override val default = BuffConfig()
    override val configName = "Buff"

    override fun parse(raw: Map<String, Any>): BuffConfig {
        @Suppress("UNCHECKED_CAST")
        val buffsRaw = raw["buffs"] as? Map<String, Map<String, Any>> ?: return BuffConfig()
        return BuffConfig(buffs = buffsRaw.mapValues { (key, props) -> parseBuff(key, props) })
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
            val typeStr = props["type"] as? String ?: return@mapNotNull null
            val type = runCatching { ModifierType.fromValue(typeStr) }.getOrElse {
                log.warn { "Buff modifier: unknown type '$typeStr' (expected 'flat' or 'percent'), skipping" }
                return@mapNotNull null
            }
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
