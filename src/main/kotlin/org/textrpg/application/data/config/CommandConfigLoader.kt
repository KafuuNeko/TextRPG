package org.textrpg.application.data.config

import org.textrpg.application.domain.model.CommandDefinition
import org.textrpg.application.domain.model.ParamDefinition
import org.textrpg.application.domain.model.RequiresDefinition

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
object CommandConfigLoader : AbstractYamlLoader<CommandConfig>() {
    override val defaultPath = "src/main/resources/config/commands.yaml"
    override val default = CommandConfig()
    override val configName = "Command"

    override fun parse(raw: Map<String, Any>): CommandConfig {
        val prefix = raw["commandPrefix"] as? String ?: "/"
        @Suppress("UNCHECKED_CAST")
        val commandsRaw = raw["commands"] as? Map<String, Map<String, Any>> ?: emptyMap()
        val commands = commandsRaw.mapValues { (key, props) -> parseCommand(key, props) }
        return CommandConfig(commandPrefix = prefix, commands = commands)
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
     * 解析前置条件（委托给 [RequiresParser]）
     */
    private fun parseRequires(raw: Any?): RequiresDefinition = RequiresParser.parseRequires(raw)

    /**
     * 解析消耗配置（委托给 [RequiresParser]）
     */
    private fun parseCost(raw: Any?): Map<String, Double> = RequiresParser.parseCost(raw)
}
