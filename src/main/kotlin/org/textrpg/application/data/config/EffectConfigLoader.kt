package org.textrpg.application.data.config

import org.textrpg.application.domain.model.EffectDefinition

/**
 * 特效配置解析器
 *
 * 从 YAML 原始数据中解析 [EffectDefinition] 列表。
 * 作为共享解析工具，供 [SkillConfigLoader] 和未来的 BuffConfigLoader 等复用。
 *
 * YAML 格式示例：
 * ```yaml
 * effects:
 *   - type: "modify_attribute"
 *     target: "target"
 *     attribute: "current_hp"
 *     amount: "-(physical_attack * 1.5 + 30)"
 *   - type: "add_buff"
 *     target: "target"
 *     buffId: "burn_dot"
 *     duration: 3
 * ```
 *
 * @see EffectDefinition
 */
object EffectConfigLoader {

    /**
     * 解析效果列表
     *
     * 将 YAML 中 `effects` 节点的原始 List 解析为 [EffectDefinition] 列表。
     * 每个元素期望为 Map<String, Any>，解析失败的条目被跳过。
     *
     * @param raw YAML 解析后的原始数据（期望为 List<Map<String, Any>>）
     * @return 不可变的 [EffectDefinition] 列表
     */
    @Suppress("UNCHECKED_CAST")
    fun parseEffectList(raw: Any?): List<EffectDefinition> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { item ->
            val props = item as? Map<String, Any> ?: return@mapNotNull null
            parseEffect(props)
        }
    }

    /**
     * 解析单个效果定义
     *
     * @param props YAML 解析后的效果字段映射
     * @return [EffectDefinition] 实例，type 字段缺失时返回 null
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseEffect(props: Map<String, Any>): EffectDefinition? {
        val type = props["type"] as? String ?: return null
        return EffectDefinition(
            type = type,
            target = props["target"] as? String ?: "self",
            attribute = props["attribute"] as? String,
            amount = props["amount"]?.toString(),
            value = props["value"]?.toString(),
            source = props["source"] as? String,
            modifierType = props["modifierType"] as? String,
            modifierValue = RequiresParser.toDouble(props["modifierValue"]),
            modifierPriority = (props["modifierPriority"] as? Number)?.toInt() ?: 0,
            buffId = props["buffId"] as? String,
            stacks = (props["stacks"] as? Number)?.toInt() ?: 1,
            duration = (props["duration"] as? Number)?.toInt(),
            nodeId = props["nodeId"] as? String,
            message = props["message"] as? String,
            itemTemplateId = props["itemTemplateId"] as? String,
            quantity = (props["quantity"] as? Number)?.toInt() ?: 1,
            sessionType = props["sessionType"] as? String,
            params = (props["params"] as? Map<String, Any>)
                ?.mapValues { (_, v) -> v.toString() }
                ?: emptyMap(),
            scriptPath = props["scriptPath"] as? String
        )
    }
}
