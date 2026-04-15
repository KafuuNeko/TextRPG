package org.textrpg.application.data.config

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
object InventoryConfigLoader : AbstractYamlLoader<InventoryConfig>() {
    override val defaultPath = "src/main/resources/config/inventory.yaml"
    override val default = InventoryConfig()
    override val configName = "Inventory"

    override fun parse(raw: Map<String, Any>): InventoryConfig {
        @Suppress("UNCHECKED_CAST")
        val invRaw = raw["inventory"] as? Map<String, Any> ?: return InventoryConfig()
        return InventoryConfig(
            capacityMode = invRaw["capacityMode"] as? String ?: "slots",
            maxSlots = (invRaw["maxSlots"] as? Number)?.toInt() ?: 30,
            maxWeight = RequiresParser.toDouble(invRaw["maxWeight"]) ?: 100.0
        )
    }
}
