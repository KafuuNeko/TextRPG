package org.textrpg.application.data.config

import org.textrpg.application.domain.model.MapNodeDefinition
import org.textrpg.application.domain.model.NodeConnection
import org.textrpg.application.domain.model.NodeEntities
import org.yaml.snakeyaml.Yaml
import java.io.File

data class MapConfig(val nodes: Map<String, MapNodeDefinition> = emptyMap())

/**
 * 地图配置加载器
 *
 * 从 YAML 文件加载地图节点定义。
 */
object MapConfigLoader {
    private val yaml = Yaml()
    private const val DEFAULT_PATH = "src/main/resources/config/nodes.yaml"

    fun load(path: String = DEFAULT_PATH): MapConfig {
        val file = File(path)
        if (!file.exists()) {
            println("Warning: Map config not found at $path, using empty definitions")
            return MapConfig()
        }
        return try {
            val raw = yaml.load<Map<String, Any>>(file.readText()) ?: return MapConfig()
            @Suppress("UNCHECKED_CAST")
            val nodesRaw = raw["nodes"] as? Map<String, Map<String, Any>> ?: emptyMap()
            val nodes = nodesRaw.mapValues { (key, props) -> parseNode(key, props) }
            MapConfig(nodes = nodes)
        } catch (e: Exception) {
            println("Warning: Failed to load map config from $path: ${e.message}")
            MapConfig()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseNode(key: String, props: Map<String, Any>): MapNodeDefinition {
        return MapNodeDefinition(
            key = key,
            displayName = props["displayName"] as? String ?: key,
            description = props["description"] as? String ?: "",
            tags = (props["tags"] as? List<*>)?.map { it.toString() }?.toSet() ?: emptySet(),
            connections = parseConnections(props["connections"]),
            entities = parseEntities(props["entities"]),
            isBoundary = props["isBoundary"] as? Boolean ?: false
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseConnections(raw: Any?): List<NodeConnection> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { item ->
            val props = item as? Map<String, Any> ?: return@mapNotNull null
            val target = props["target"] as? String ?: return@mapNotNull null
            NodeConnection(
                target = target,
                display = props["display"] as? String ?: "→ $target",
                requires = RequiresParser.parseRequires(props["requires"])
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseEntities(raw: Any?): NodeEntities {
        val map = raw as? Map<String, Any> ?: return NodeEntities()
        return NodeEntities(
            npcs = (map["npcs"] as? List<*>)?.map { it.toString() } ?: emptyList(),
            enemies = (map["enemies"] as? List<*>)?.map { it.toString() } ?: emptyList()
        )
    }
}
