package org.textrpg.application.utils.script

import java.util.concurrent.ConcurrentHashMap
import javax.script.Compilable
import javax.script.CompiledScript
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager

/**
 * Kotlin 脚本执行器
 *
 * 使用 JSR223 规范执行 Kotlin 脚本，
 * 支持运行时动态执行代码，实现游戏热更新。
 *
 * **构造期校验**：找不到 `kotlin` 引擎时立即抛异常，避免运行期 NPE。
 *
 * **编译缓存**：相同脚本字符串只编译一次（编译是 Kotlin 脚本最大的开销，
 * 通常 100ms~数秒）。缓存以脚本内容作为 key——内容变化（含外部文件被修改后重读）
 * 会自动失效，符合"热更新"语义。编译失败的脚本不进入缓存。
 */
class KotlinScriptRunner {

    /** JSR223 脚本引擎（构造期解析，确保非空） */
    private val scriptEngine: ScriptEngine = ScriptEngineManager().getEngineByName("kotlin")
        ?: error("JSR223 engine 'kotlin' not available; please ensure kotlin-scripting-jsr223 is on the classpath")

    /** 引擎是否支持编译（Kotlin JSR223 默认实现 [Compilable]，否则降级为每次 eval） */
    private val compilable: Compilable? = scriptEngine as? Compilable

    /** 编译产物缓存：脚本内容 → 已编译脚本 */
    private val compileCache = ConcurrentHashMap<String, CompiledScript>()

    /**
     * 脚本执行结果
     *
     * @property success 是否执行成功
     * @property message 执行结果描述或错误信息
     * @property value 数值返回值（用于数值计算效果）
     */
    data class ScriptResult(
        val success: Boolean,
        val message: String,
        val value: Int = 0
    )

    /**
     * 执行 Kotlin 脚本
     *
     * 脚本中可以访问 context 中注入的变量。重复执行同一脚本字符串时会复用
     * 已缓存的编译产物（见类级 KDoc 中的"编译缓存"说明）。
     *
     * @param script Kotlin 脚本代码
     * @param context 变量上下文（键值对）
     * @return 执行结果
     */
    fun executeScript(script: String, context: Map<String, Any?>): ScriptResult {
        val bindings = scriptEngine.createBindings()
        context.forEach { (key, value) -> bindings[key] = value }

        return runCatching { evalScript(script, bindings) }
            .fold(
                onSuccess = { evalResult ->
                    ScriptResult(
                        success = true,
                        message = evalResult?.toString() ?: "Script executed",
                        value = (evalResult as? Number)?.toInt() ?: 0
                    )
                },
                onFailure = { e ->
                    ScriptResult(
                        success = false,
                        message = "Script error: ${e.message}",
                        value = 0
                    )
                }
            )
    }

    /**
     * 编译并执行脚本
     *
     * 引擎支持编译时走 [compileCache]——`computeIfAbsent` 在编译异常时不会落键，
     * 因此失败的脚本不会污染缓存。引擎不支持编译时降级为每次 [ScriptEngine.eval]。
     */
    private fun evalScript(script: String, bindings: javax.script.Bindings): Any? {
        // 把 nullable property 本地化，避免 lambda 内 smart cast 失效
        val c = compilable ?: return scriptEngine.eval(script, bindings)
        val compiled = compileCache.computeIfAbsent(script) { c.compile(it) }
        return compiled.eval(bindings)
    }

    /**
     * 验证脚本语法
     *
     * 仅做编译期语法检查，**不执行**脚本（不会触发任何副作用）。
     * 验证通过的编译产物会被复用（写入 [compileCache]），后续 [executeScript]
     * 同一脚本时直接走缓存，省一次编译。
     *
     * 实现细节：通过 [Compilable] 接口编译脚本——若 JSR223 引擎不实现 [Compilable]
     * （Kotlin JSR223 默认实现该接口），返回 `true` 表示"无法判断，假定有效"。
     *
     * @param script Kotlin 脚本代码
     * @return 语法是否正确（引擎不支持编译时返回 `true` 跳过校验）
     */
    fun validateScript(script: String): Boolean {
        val c = compilable ?: return true
        return runCatching { compileCache.computeIfAbsent(script) { c.compile(it) } }.isSuccess
    }
}
