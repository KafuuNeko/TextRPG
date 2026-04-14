package org.textrpg.application.data.config

import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * 背包配置
 *
 * @property capacityMode 容量限制模式："slots"（格子数）/ "weight"（负重）/ "unlimited"（无限）
 * @property maxSlots 格子数模式下的上限
 * @property maxWeight 负重模式下的上限
 */
data class InventoryConfig(
    val capacityMode: String = "slots",
    val maxSlots: Int = 30,
    val maxWeight: Double = 100.0
)

/**
 * 背包配置加载器
 *
 * 从 YAML 文件加载背包容量配置。
 *
 * YAML 格式示例：
 * ```yaml
 * inventory:
 *   capacityMode: "slots"
 *   maxSlots: 30
 *   maxWeight: 100.0
 * ```
 */
object InventoryConfigLoader {
    private val yaml = Yaml()
    private const val DEFAULT_PATH = "src/main/resources/config/inventory.yaml"

    /**
     * 从指定路径加载背包配置
     *
     * @param path YAML 配置文件路径（默认 [DEFAULT_PATH]）
     * @return [InventoryConfig] 实例，加载失败时返回默认配置
     */
    fun load(path: String = DEFAULT_PATH): InventoryConfig {
        val file = File(path)
        if (!file.exists()) {
            println("Warning: Inventory config not found at $path, using defaults")
            return InventoryConfig()
        }
        return try {
            val raw = yaml.load<Map<String, Any>>(file.readText()) ?: return InventoryConfig()
            @Suppress("UNCHECKED_CAST")
            val invRaw = raw["inventory"] as? Map<String, Any> ?: return InventoryConfig()
            InventoryConfig(
                capacityMode = invRaw["capacityMode"] as? String ?: "slots",
                maxSlots = (invRaw["maxSlots"] as? Number)?.toInt() ?: 30,
                maxWeight = RequiresParser.toDouble(invRaw["maxWeight"]) ?: 100.0
            )
        } catch (e: Exception) {
            println("Warning: Failed to load inventory config from $path: ${e.message}")
            InventoryConfig()
        }
    }
}
