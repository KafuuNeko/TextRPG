package org.textrpg.application.data.config

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import org.yaml.snakeyaml.Yaml
import java.io.File

private val log = KotlinLogging.logger {}

/**
 * 应用配置数据类
 */
@Serializable
data class AppConfig(
    var bot: OneBotConfig = OneBotConfig(),
    var llm: LLMConfig = LLMConfig(),
    var database: DatabaseConfig = DatabaseConfig(),
)

/**
 * 数据库连接配置
 *
 * 支持 SQLite（默认）或任意 JDBC 兼容数据库。
 *
 * @property url JDBC 连接字符串，例如 `jdbc:sqlite:textrpg.db` 或 `jdbc:h2:mem:test`
 * @property driver JDBC 驱动类名
 */
@Serializable
data class DatabaseConfig(
    var url: String = "jdbc:sqlite:textrpg.db",
    var driver: String = "org.sqlite.JDBC"
)

/**
 * OneBot WebSocket 配置
 *
 * OneBot 是一个机器人平台标准，本框架通过 WebSocket 反向连接
 * 接收来自 QQ/微信等平台的消息
 *
 * @property enabled 是否启用 OneBot 连接
 * @property websocketUrl OneBot WebSocket 服务器地址
 * @property accessToken 访问令牌（可选）
 */
@Serializable
data class OneBotConfig(
    var enabled: Boolean = true,
    var websocketUrl: String = "ws://127.0.0.1:8080",
    var httpUrl: String = "http://127.0.0.1:8080",
    var accessToken: String = ""
)

/**
 * LLM（大型语言模型）API 配置
 *
 * 用于连接本地或远程 LLM 服务，为游戏提供 AI 生成能力：
 * - NPC 对话生成
 * - 剧情事件描述
 * - 战斗动作描述
 *
 * @property enabled 是否启用 LLM
 * @property apiUrl LLM API 地址（支持 Ollama、OpenAI 兼容接口）
 * @property model 模型名称
 * @property timeout 请求超时时间（毫秒）
 */
@Serializable
data class LLMConfig(
    var enabled: Boolean = true,
    var apiUrl: String = "http://127.0.0.1:11434/api/generate",
    var apiKey: String = "",
    var model: String = "llama3",
    var timeout: Int = 60000
)

/**
 * 配置加载器
 *
 * 负责从 YAML 文件加载应用配置，
 * 支持默认值回退（当文件不存在或解析失败时）
 *
 * @see AppConfig
 *
 * @example
 * ```kotlin
 * val config = ConfigLoader.loadOrDefault()
 * println("LLM API: ${config.llm.apiUrl}")
 * ```
 */
object ConfigLoader {

    /**
     * 从指定路径加载配置
     *
     * 每次调用创建新的 [Yaml] 实例——SnakeYAML 的 Yaml 类非线程安全。
     *
     * @param path 配置文件路径（默认 `src/main/resources/config/app.yaml`）
     * @return 解析后的 AppConfig 实例
     * @throws Exception 当文件不存在或 YAML 解析失败时
     */
    fun load(path: String = "src/main/resources/config/app.yaml"): AppConfig {
        val file = File(path)
        if (!file.exists()) {
            return AppConfig()
        }
        val content = file.readText()
        return Yaml().loadAs(content, AppConfig::class.java)
    }

    /**
     * 加载配置，带默认值回退
     *
     * 推荐使用此方法，即使配置文件不存在或解析失败，
     * 也会返回默认配置而非抛出异常
     *
     * @param path 配置文件路径
     * @return AppConfig 实例（解析失败时返回默认配置）
     */
    fun loadOrDefault(path: String = "src/main/resources/config/app.yaml"): AppConfig {
        return runCatching { load(path) }
            .onFailure { log.warn(it) { "Failed to load config from $path, using defaults" } }
            .getOrDefault(AppConfig())
    }
}
