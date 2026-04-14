package org.textrpg.application.data.config

import org.textrpg.application.domain.model.NpcDefinition
import org.textrpg.application.domain.model.NpcFunction
import org.yaml.snakeyaml.Yaml
import java.io.File

data class NpcConfig(val npcs: Map<String, NpcDefinition> = emptyMap())

/**
 * NPC 配置加载器
 *
 * 从 YAML 文件加载 NPC 定义。
 */
object NpcConfigLoader {
    private val yaml = Yaml()
    private const val DEFAULT_PATH = "src/main/resources/config/npcs.yaml"

    fun load(path: String = DEFAULT_PATH): NpcConfig {
        val file = File(path)
        if (!file.exists()) {
            println("Warning: NPC config not found at $path, using empty definitions")
            return NpcConfig()
        }
        return try {
            val raw = yaml.load<Map<String, Any>>(file.readText()) ?: return NpcConfig()
            @Suppress("UNCHECKED_CAST")
            val npcsRaw = raw["npcs"] as? Map<String, Map<String, Any>> ?: emptyMap()
            val npcs = npcsRaw.mapValues { (key, props) -> parseNpc(key, props) }
            NpcConfig(npcs = npcs)
        } catch (e: Exception) {
            println("Warning: Failed to load NPC config from $path: ${e.message}")
            NpcConfig()
        }
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
