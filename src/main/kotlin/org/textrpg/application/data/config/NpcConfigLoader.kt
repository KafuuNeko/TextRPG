package org.textrpg.application.data.config

import org.textrpg.application.domain.model.NpcDefinition
import org.textrpg.application.domain.model.NpcFunction

data class NpcConfig(val npcs: Map<String, NpcDefinition> = emptyMap())

/**
 * NPC 配置加载器
 *
 * 从 YAML 文件加载 NPC 定义。
 */
object NpcConfigLoader : AbstractYamlLoader<NpcConfig>() {
    override val defaultPath = "src/main/resources/config/npcs.yaml"
    override val default = NpcConfig()
    override val configName = "NPC"

    override fun parse(raw: Map<String, Any>): NpcConfig {
        @Suppress("UNCHECKED_CAST")
        val npcsRaw = raw["npcs"] as? Map<String, Map<String, Any>> ?: return NpcConfig()
        return NpcConfig(npcs = npcsRaw.mapValues { (key, props) -> parseNpc(key, props) })
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseNpc(key: String, props: Map<String, Any>): NpcDefinition {
        return NpcDefinition(
            key = key,
            displayName = props["displayName"] as? String ?: key,
            description = props["description"] as? String ?: "",
            location = props["location"] as? String ?: "",
            prompt = props["prompt"] as? String ?: "",
            functions = parseFunctions(props["functions"]),
            allowedTools = (props["allowedTools"] as? List<*>)?.map { it.toString() } ?: emptyList()
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseFunctions(raw: Any?): List<NpcFunction> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { item ->
            val props = item as? Map<String, Any> ?: return@mapNotNull null
            val type = props["type"] as? String ?: return@mapNotNull null
            val params = props.filterKeys { it != "type" }.mapValues { (_, v) -> v.toString() }
            NpcFunction(type = type, params = params)
        }
    }
}
