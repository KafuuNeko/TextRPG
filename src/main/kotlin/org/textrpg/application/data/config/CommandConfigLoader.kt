package org.textrpg.application.data.config

import org.textrpg.application.domain.model.CommandDefinition
import org.textrpg.application.domain.model.ParamDefinition
import org.textrpg.application.domain.model.RequiresDefinition
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * 指令配置加载结果
 *
 * @property commandPrefix 指令前缀（默认 "/"）
 * @property commands 指令定义映射（key -> CommandDefinition）
 */
data class CommandConfig(
    val commandPrefix: String = "/",
    val commands: Map<String, CommandDefinition> = emptyMap()
)

/**
 * 指令配置加载器
 *
 * 从 YAML 文件加载指令定义，解析为不可变的 [CommandDefinition] 领域模型。
 *
 * YAML 格式示例：
 * ```yaml
 * commandPrefix: "/"
 * commands:
 *   status:
 *     displayName: "查看状态"
 *     aliases: ["状态", "st"]
 *     handler: "builtin:status"
 *     requires:
 *       registered: true
 * ```
 *
 * @see CommandDefinition
 */
object CommandConfigLoader {
    private val yaml = Yaml()
    private const val DEFAULT_PATH = "src/main/resources/config/commands.yaml"

    /**
     * 从指定路径加载指令配置
     *
     * @param path YAML 配置文件路径
     * @return [CommandConfig] 实例，加载失败时返回默认配置
     */
    fun load(path: String = DEFAULT_PATH): CommandConfig {
        val file = File(path)
        if (!file.exists()) {
            println("Warning: Command config not found at $path, using empty definitions")
            return CommandConfig()
        }
        return try {
            val raw = yaml.load<Map<String, Any>>(file.readText()) ?: return CommandConfig()
            val prefix = raw["commandPrefix"] as? String ?: "/"
            @Suppress("UNCHECKED_CAST")
            val commandsRaw = raw["commands"] as? Map<String, Map<String, Any>> ?: emptyMap()
            val commands = commandsRaw.mapValues { (key, props) -> parseCommand(key, props) }
            CommandConfig(commandPrefix = prefix, commands = commands)
        } catch (e: Exception) {
            println("Warning: Failed to load command config from $path: ${e.message}")
            CommandConfig()
        }
    }

    /**
     * 解析单个指令定义
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseCommand(key: String, props: Map<String, Any>): CommandDefinition {
        return CommandDefinition(
            key = key,
            displayName = props["displayName"] as? String ?: key,
            aliases = (props["aliases"] as? List<*>)?.map { it.toString() } ?: emptyList(),
            description = props["description"] as? String ?: "",
            params = parseParams(props["params"]),
            handler = props["handler"] as? String ?: "builtin:noop",
            requires = parseRequires(props["requires"]),
            cost = parseCost(props["cost"])
        )
    }

    /**
     * 解析参数定义列表
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseParams(raw: Any?): List<ParamDefinition> {
        val list = raw as? List<Map<String, Any>> ?: return emptyList()
        return list.map { p ->
            ParamDefinition(
                name = p["name"] as? String ?: "arg",
                type = p["type"] as? String ?: "string",
                required = p["required"] as? Boolean ?: false
            )
        }
    }

    /**
     * 解析前置条件
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseRequires(raw: Any?): RequiresDefinition {
        val map = raw as? Map<String, Any> ?: return RequiresDefinition()
        return RequiresDefinition(
            registered = map["registered"] as? Boolean,
            minAttribute = (map["minAttribute"] as? Map<String, Any>)
                ?.mapValues { (_, v) -> toDouble(v) ?: 0.0 },
            inSession = map["inSession"] as? String,
            notInSession = map["notInSession"] as? Boolean,
            atNodeTag = map["atNodeTag"] as? String,
            hasItem = (map["hasItem"] as? Map<String, Any>)
                ?.mapValues { (_, v) -> (v as? Number)?.toInt() ?: 1 },
            hasBuff = map["hasBuff"] as? String,
            notHasBuff = map["notHasBuff"] as? String
        )
    }

    /**
     * 解析消耗配置（属性 key -> 消耗量）
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseCost(raw: Any?): Map<String, Double> {
        val map = raw as? Map<String, Any> ?: return emptyMap()
        return map.mapValues { (_, v) -> toDouble(v) ?: 0.0 }
    }

    private fun toDouble(value: Any?): Double? = when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }
}
