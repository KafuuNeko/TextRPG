package org.textrpg.application.data.config

import org.textrpg.application.domain.model.BasicAttributeInfo
import org.textrpg.application.domain.model.ExtendedAttributeInfo
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * 玩家属性配置类
 *
 * 封装并管理所有的基础属性和拓展属性配置。
 */
class PlayerAttributeConfig {
    val basicAttributes = mutableMapOf<String, BasicAttributeInfo>()
    val extendedAttributes = mutableMapOf<String, ExtendedAttributeInfo>()
}

/**
 * 玩家属性配置加载器
 *
 * 负责从 YAML 文件加载玩家属性定义。
 */
data class PlayerAttributeConfigLoader(
    private val mYaml: Yaml
) {
    /**
     * 从指定路径加载属性配置
     *
     * @param path 配置文件路径（默认 `resources/config/player_attribute.yaml`）
     * @return 解析后的 PlayerAttributeConfig 实例
     */
    @Suppress("UNCHECKED_CAST")
    fun loadOrDefault(path: String = "resources/config/player_attribute.yaml"): PlayerAttributeConfig {
        val config = PlayerAttributeConfig()
        val file = File(path)

        if (!file.exists()) {
            println("Warning: Player attribute config file not found at $path")
            return config
        }

        try {
            val content = file.readText()
            val data = mYaml.load<Map<String, Map<String, Any>>>(content) ?: return config

            // 解析基础属性 (basic)
            val basicMap = data["basic"] as? Map<String, Map<String, Any>>
            basicMap?.forEach { (key, valueMap) ->
                val name = valueMap["name"] as? String ?: key
                // SnakeYaml 把 default 解析为 Double 或 Integer
                val defaultVal = (valueMap["default"] as? Number)?.toDouble() ?: 0.0
                config.basicAttributes[key] = BasicAttributeInfo(name, defaultVal)
            }

            // 解析拓展属性 (extended)
            val extendedMap = data["extended"] as? Map<String, Map<String, Any>>
            extendedMap?.forEach { (key, valueMap) ->
                val name = valueMap["name"] as? String ?: key
                val expression = valueMap["expression"] as? String ?: "0.0"
                config.extendedAttributes[key] = ExtendedAttributeInfo(name, expression)
            }
        } catch (e: Exception) {
            println("Error parsing player attribute config: ${e.message}")
            e.printStackTrace()
        }

        return config
    }
}
