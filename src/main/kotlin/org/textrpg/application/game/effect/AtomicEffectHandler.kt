package org.textrpg.application.game.effect

import org.textrpg.application.domain.model.EffectDefinition
import org.textrpg.application.domain.model.EffectResult

/**
 * 原子操作处理器接口
 *
 * 特效引擎为每种效果类型注册一个处理器。处理器接收效果定义、目标实体和执行上下文，
 * 执行对应的原子操作并返回结果。
 *
 * 内置处理器参见 [BuiltinEffectHandlers]，脚本可通过 [EffectEngine.registerHandler] 注册自定义处理器。
 *
 * @see BuiltinEffectHandlers
 * @see EffectEngine
 */
fun interface AtomicEffectHandler {

    /**
     * 执行原子操作
     *
     * @param effect 效果定义（包含操作所需的参数）
     * @param target 目标实体
     * @param context 执行上下文（提供施法者信息、公式求值等）
     * @return 执行结果
     */
    suspend fun execute(effect: EffectDefinition, target: EntityAccessor, context: EffectContext): EffectResult
}
