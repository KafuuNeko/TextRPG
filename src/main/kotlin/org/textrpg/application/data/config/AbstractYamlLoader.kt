package org.textrpg.application.data.config

import io.github.oshai.kotlinlogging.KotlinLogging
import org.yaml.snakeyaml.Yaml
import java.io.File

private val log = KotlinLogging.logger {}

/**
 * YAML 配置加载器基类
 *
 * 封装各 ConfigLoader 共享的"文件存在性校验 + YAML 读取 + 失败回退 + 日志告警"样板，
 * 子类仅需实现 [parse] 把原始 `Map<String, Any>` 映射为具体领域模型。
 *
 * **典型子类**：
 * ```kotlin
 * object AIConfigLoader : AbstractYamlLoader<AIConfig>() {
 *     override val defaultPath = "src/main/resources/config/ai.yaml"
 *     override val default = AIConfig()
 *     override val configName = "AI"
 *     override fun parse(raw: Map<String, Any>): AIConfig { ... }
 * }
 * ```
 *
 * **失败语义**：
 * - 文件不存在 → 返回 [default]，WARN 日志
 * - YAML 解析异常或 [parse] 抛异常 → 返回 [default]，WARN 日志（含堆栈）
 *
 * @param T 加载结果类型
 */
abstract class AbstractYamlLoader<T> {

    /** 共用的 SnakeYAML 实例（线程安全用法：每次 load 调用新建 Yaml 也可，但共享足够） */
    protected val yaml: Yaml = Yaml()

    /** 配置文件默认路径（子类约定） */
    protected abstract val defaultPath: String

    /** 加载失败时的回退值（也作为空文件的返回） */
    protected abstract val default: T

    /** 日志用的配置名，如 `"AI"` / `"Combat"`，用于日志可读性 */
    protected abstract val configName: String

    /**
     * 把 YAML 顶层 Map 解析为具体配置对象
     *
     * @param raw 已成功读入的 YAML 顶层 Map（非 null，可能为空）
     */
    protected abstract fun parse(raw: Map<String, Any>): T

    /**
     * 从指定路径加载配置
     *
     * @param path YAML 文件路径，默认为 [defaultPath]
     * @return 解析结果；任何失败均回退为 [default]
     */
    fun load(path: String = defaultPath): T {
        val file = File(path)
        if (!file.exists()) {
            log.warn { "$configName config not found at $path, using defaults" }
            return default
        }
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            val raw = yaml.load<Map<String, Any>>(file.readText()) ?: return@runCatching default
            parse(raw)
        }.getOrElse { e ->
            log.warn(e) { "Failed to load $configName config from $path" }
            default
        }
    }
}
