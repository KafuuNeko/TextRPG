package org.textrpg.application.game.attribute

import org.textrpg.application.domain.model.TriggerDefinition
import org.textrpg.application.domain.model.TriggerType

/**
 * 属性触发事件
 *
 * 当属性值变化满足触发条件时，由 [AttributeContainer] 构建并分发给 [TriggerHandler]。
 *
 * @property attributeKey 触发事件的属性标识符
 * @property triggerType 触发器类型
 * @property definition 触发器定义（含 action 和可选的阈值表达式）
 * @property oldValue 变化前的有效值
 * @property newValue 变化后的有效值
 */
data class TriggerEvent(
    val attributeKey: String,
    val triggerType: TriggerType,
    val definition: TriggerDefinition,
    val oldValue: Double,
    val newValue: Double
)

/**
 * 属性触发器处理器接口
 *
 * 由游戏层实现，处理属性值变化触发的事件。
 * 使用 `fun interface` 支持 lambda 简写：
 *
 * ```kotlin
 * container.triggerHandler = TriggerHandler { event ->
 *     when (event.definition.action) {
 *         "death" -> handlePlayerDeath(event.attributeKey)
 *         else -> if (event.definition.action.startsWith("script:")) {
 *             scriptRunner.execute(event.definition.action.removePrefix("script:"))
 *         }
 *     }
 * }
 * ```
 */
fun interface TriggerHandler {
    /**
     * 处理触发事件
     *
     * @param event 触发事件数据
     */
    fun onTrigger(event: TriggerEvent)
}
