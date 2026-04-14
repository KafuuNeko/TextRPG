package org.textrpg.application.game.command

import org.textrpg.application.domain.model.RequiresDefinition

/**
 * Requires 校验结果
 *
 * @property passed 是否通过所有校验
 * @property message 失败时的提示文本（通过时为 null）
 */
data class RequireResult(
    val passed: Boolean,
    val message: String? = null
) {
    companion object {
        val PASSED = RequireResult(passed = true)
        fun failed(message: String) = RequireResult(passed = false, message = message)
    }
}

/**
 * 统一前置条件校验器
 *
 * 处理 [RequiresDefinition] 中定义的所有内置条件检查。
 * 指令和技能共用此校验器，保证校验逻辑一致。
 *
 * 校验顺序按定义字段从上到下执行，遇到第一个不满足的条件立即返回失败。
 * 脚本扩展的自定义条件通过覆盖此类或注册额外检查器实现（后续版本支持）。
 */
object RequiresChecker {

    /**
     * 执行前置条件校验
     *
     * @param requires 前置条件定义
     * @param context 执行上下文（提供玩家游戏状态）
     * @return 校验结果，包含是否通过及失败原因
     */
    fun check(requires: RequiresDefinition, context: CommandContext): RequireResult {
        // 注册检查
        if (requires.registered == true && !context.isRegistered) {
            return RequireResult.failed("你还没有注册角色，请先使用注册指令。")
        }

        // 属性最低值检查
        requires.minAttribute?.forEach { (attrKey, minValue) ->
            val current = context.getAttributeValue(attrKey)
            if (current == null || current < minValue) {
                return RequireResult.failed("${attrKey} 不足（需要 $minValue，当前 ${current ?: 0.0}）。")
            }
        }

        // 会话状态检查
        if (requires.inSession != null && context.currentSessionType != requires.inSession) {
            return RequireResult.failed("此指令需要在 ${requires.inSession} 中使用。")
        }
        if (requires.notInSession == true && context.currentSessionType != null) {
            return RequireResult.failed("当前处于 ${context.currentSessionType} 中，无法使用此指令。")
        }

        // 地图节点标签检查
        if (requires.atNodeTag != null) {
            if (requires.atNodeTag !in context.getCurrentNodeTags()) {
                return RequireResult.failed("当前位置不满足条件。")
            }
        }

        // 物品持有检查
        requires.hasItem?.forEach { (itemId, quantity) ->
            if (!context.hasItem(itemId, quantity)) {
                return RequireResult.failed("缺少物品：$itemId x$quantity。")
            }
        }

        // Buff 状态检查
        if (requires.hasBuff != null && !context.hasBuff(requires.hasBuff)) {
            return RequireResult.failed("需要状态：${requires.hasBuff}。")
        }
        if (requires.notHasBuff != null && context.hasBuff(requires.notHasBuff)) {
            return RequireResult.failed("当前状态 ${requires.notHasBuff} 阻止了此操作。")
        }

        return RequireResult.PASSED
    }
}
