package org.textrpg.application.utils.script

import javax.script.ScriptEngineManager

/**
 * Kotlin 脚本执行器
 *
 * 使用 JSR223 规范执行 Kotlin 脚本，
 * 支持运行时动态执行代码，实现游戏热更新。
 *
 */
class KotlinScriptRunner {

    /** JSR223 脚本引擎 */
    private val mScriptEngine = ScriptEngineManager().getEngineByName("kotlin")

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
     * 脚本中可以访问 context 中注入的变量
     *
     * @param script Kotlin 脚本代码
     * @param context 变量上下文（键值对）
     * @return 执行结果
     *
     */
    fun executeScript(script: String, context: Map<String, Any?>): ScriptResult {
        return try {
            // 创建脚本绑定
            val bindings = mScriptEngine.createBindings()

            // 注入上下文变量
            context.forEach { (key, value) -> bindings[key] = value }

            // 执行脚本
            val evalResult = mScriptEngine.eval(script, bindings)

            ScriptResult(
                success = true,
                message = evalResult?.toString() ?: "Script executed",
                value = (evalResult as? Number)?.toInt() ?: 0
            )
        } catch (e: Exception) {
            ScriptResult(
                success = false,
                message = "Script error: ${e.message}",
                value = 0
            )
        }
    }

    /**
     * 验证脚本语法
     *
     * 不执行脚本，只检查语法是否正确
     *
     * @param script Kotlin 脚本代码
     * @return 语法是否正确
     */
    fun validateScript(script: String): Boolean {
        return try {
            mScriptEngine.eval(script)
            true
        } catch (e: Exception) {
            false
        }
    }
}
