package org.textrpg.application.game.buff

import org.textrpg.application.domain.model.BuffDefinition
import org.textrpg.application.domain.model.StackPolicy

/**
 * Buff 运行时实例
 *
 * 表示一个实体身上活跃的 Buff。包含当前层数、剩余持续时间等可变运行时状态。
 * 每个实例由 [BuffManager] 管理，不应在外部直接修改。
 *
 * 修正器来源标识规则：
 * - REFRESH / STACK 策略：`"buff:{buffId}"`
 * - INDEPENDENT 策略：`"buff:{buffId}:{instanceIndex}"`（每个独立实例有唯一 index）
 *
 * @property definition 关联的 Buff 定义（不可变模板）
 * @property instanceIndex 实例唯一索引（由 BuffManager 分配，用于 INDEPENDENT 策略的来源标识）
 * @property stacks 当前叠加层数
 * @property remainingDuration 剩余持续回合数（负数表示永久）
 */
class BuffInstance(
    val definition: BuffDefinition,
    val instanceIndex: Int,
    var stacks: Int = 1,
    var remainingDuration: Int = definition.duration
) {

    /**
     * 修正器来源标识
     *
     * 用于通过 [AttributeContainer.removeAllModifiersBySource] 批量回滚修正器。
     */
    val modifierSource: String
        get() = if (definition.stackPolicy == StackPolicy.INDEPENDENT) {
            "buff:${definition.key}:$instanceIndex"
        } else {
            "buff:${definition.key}"
        }

    /** 是否已过期（剩余回合数降至 0） */
    val isExpired: Boolean
        get() = remainingDuration == 0

    /** 是否为永久 Buff（剩余回合数为负数） */
    val isPermanent: Boolean
        get() = remainingDuration < 0
}
