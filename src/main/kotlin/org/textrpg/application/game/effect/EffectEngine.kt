package org.textrpg.application.game.effect

import org.textrpg.application.domain.model.EffectDefinition
import org.textrpg.application.domain.model.EffectResult
import org.textrpg.application.utils.script.KotlinScriptRunner
import java.io.File

/**
 * 特效引擎
 *
 * 框架的"动词系统"——属性引擎管"名词"（实体有什么），特效引擎管"动词"（对实体做什么）。
 * 所有游戏行为（技能、消耗品、Buff 周期效果、地图事件……）最终都归结为一组 Effect 的执行。
 *
 * 引擎为每种效果类型注册一个 [AtomicEffectHandler]，运行时通过效果类型字符串查找并执行。
 * 内置 12 种原子操作（参见 [BuiltinEffectHandlers]），可通过 [registerHandler] 注册自定义类型。
 *
 * 执行流程：
 * ```
 * 遍历 Effects 列表
 *   → 解析 target（目标选择器 → 实体列表）
 *   → 查找 handler（按效果类型字符串）
 *   → 对每个目标执行 handler
 *   → 收集结果（单个失败不影响后续执行）
 * ```
 *
 * @param scriptRunner Kotlin 脚本执行器（可选），用于 run_script 效果和 customScript 后置脚本
 *
 * @see AtomicEffectHandler
 * @see BuiltinEffectHandlers
 * @see EffectContext
 */
class EffectEngine(private val scriptRunner: KotlinScriptRunner? = null) {

    /** 效果类型 → 处理器的注册表 */
    private val handlers: MutableMap<String, AtomicEffectHandler> =
        BuiltinEffectHandlers.createAll(scriptRunner).toMutableMap()

    /**
     * 注册自定义效果处理器
     *
     * 可覆盖内置处理器，也可注册全新的效果类型。
     * 脚本系统可通过此方法在运行时扩展引擎能力。
     *
     * @param type 效果类型标识字符串
     * @param handler 处理器实现
     */
    fun registerHandler(type: String, handler: AtomicEffectHandler) {
        handlers[type] = handler
    }

    /**
     * 执行效果列表
     *
     * 按顺序遍历效果列表，对每个效果解析目标选择器，然后对所有目标依次执行。
     * 单个效果失败时跳过并继续执行剩余效果，不回滚已执行的效果。
     *
     * @param effects 效果定义列表
     * @param context 执行上下文（提供施法者、目标、公式求值）
     * @return 所有效果的执行结果列表
     */
    suspend fun executeEffects(
        effects: List<EffectDefinition>,
        context: EffectContext
    ): List<EffectResult> {
        val results = mutableListOf<EffectResult>()

        for (effect in effects) {
            // 解析目标
            val targets = context.resolveTargets(effect.target)
            if (targets.isEmpty()) {
                results.add(EffectResult.failed("${effect.type}: no targets resolved for selector '${effect.target}'"))
                continue
            }

            // 查找处理器
            val handler = handlers[effect.type]
            if (handler == null) {
                results.add(EffectResult.failed("Unknown effect type: '${effect.type}'"))
                continue
            }

            // 对每个目标执行
            for (target in targets) {
                try {
                    val result = handler.execute(effect, target, context)
                    results.add(result)
                } catch (e: Exception) {
                    results.add(EffectResult.failed("${effect.type}: exception: ${e.message}"))
                }
            }
        }

        return results
    }

    /**
     * 执行效果列表并运行后置脚本
     *
     * 先执行所有效果，然后如果指定了 [customScript]，加载并执行该脚本。
     * 脚本中可通过注入的变量访问施法者、目标和效果执行结果。
     *
     * @param effects 效果定义列表
     * @param context 执行上下文
     * @param customScript 后置脚本路径（可选）
     * @return 所有效果的执行结果列表（包含脚本执行结果）
     */
    suspend fun executeWithScript(
        effects: List<EffectDefinition>,
        context: EffectContext,
        customScript: String? = null
    ): List<EffectResult> {
        val results = executeEffects(effects, context).toMutableList()

        // 执行后置脚本
        if (customScript != null && scriptRunner != null) {
            val scriptFile = File(customScript)
            if (scriptFile.exists()) {
                val scriptCode = scriptFile.readText()
                val scriptContext = mapOf<String, Any?>(
                    "source" to context.source,
                    "target" to context.primaryTarget,
                    "context" to context,
                    "results" to results.toList()
                )
                val scriptResult = scriptRunner.executeScript(scriptCode, scriptContext)
                results.add(
                    if (scriptResult.success) {
                        EffectResult.success("customScript: ${scriptResult.message}")
                    } else {
                        EffectResult.failed("customScript: ${scriptResult.message}")
                    }
                )
            } else {
                results.add(EffectResult.failed("customScript: file not found: $customScript"))
            }
        }

        return results
    }
}
