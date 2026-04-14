package org.textrpg.application.game.combat

import org.textrpg.application.domain.model.*
import org.textrpg.application.game.attribute.AttributeContainer
import org.textrpg.application.game.buff.BuffAwareEntityAccessor
import org.textrpg.application.game.buff.BuffManager
import org.textrpg.application.game.skill.CooldownManager

/**
 * 参战实体
 *
 * 在战斗中代表一个参战者（玩家或敌人）的统一包装。
 * 继承 [BuffAwareEntityAccessor]，可直接用作 [EntityAccessor] 传入 EffectEngine / SkillEngine。
 *
 * 设计说明：
 * - **玩家**：使用已有的 AttributeContainer + BuffManager（不克隆，战斗伤害是真实的）
 * - **敌人**：从 [EnemyDefinition] 创建全新的 AttributeContainer + BuffManager
 * - **物品操作**：玩家由外部 InventoryService 处理；敌人为 no-op
 * - **消息发送**：通过 [messageSink] lambda 委托，玩家发到聊天平台，敌人可忽略
 *
 * @param entityId 实体唯一标识
 * @param displayName 显示名称
 * @param isPlayer 是否为玩家
 * @param attributeContainer 属性容器
 * @param buffManager Buff 管理器
 * @param skillIds 可用技能 ID 列表
 * @param cooldownManager 冷却管理器
 * @param messageSink 消息发送回调
 */
class CombatEntity(
    override val entityId: String,
    val displayName: String,
    val isPlayer: Boolean,
    attributeContainer: AttributeContainer,
    buffManager: BuffManager,
    val skillIds: List<String>,
    val cooldownManager: CooldownManager,
    private val messageSink: suspend (String) -> Unit = {}
) : BuffAwareEntityAccessor(attributeContainer, buffManager) {

    /** 是否存活（current_hp > 0） */
    val isAlive: Boolean
        get() = try { getAttributeValue("current_hp") > 0 } catch (_: Exception) { false }

    /** 公开的 Buff 管理器引用（战斗系统需要在回合结束时调用 tick） */
    val buffs: org.textrpg.application.game.buff.BuffManager get() = buffManager

    /** 公开的属性容器引用（战斗系统需要在工厂中传递给新实体） */
    val attributes: org.textrpg.application.game.attribute.AttributeContainer get() = attributeContainer

    // ======================== 物品操作（敌人 no-op） ========================

    override fun hasItem(itemId: String, quantity: Int): Boolean = false
    override fun giveItem(itemId: String, quantity: Int) { /* no-op for combat entities */ }
    override fun removeItem(itemId: String, quantity: Int) { /* no-op for combat entities */ }

    // ======================== 消息发送 ========================

    override suspend fun sendMessage(message: String) {
        messageSink(message)
    }

    companion object {

        /**
         * 从敌人定义创建参战实体
         *
         * 创建全新的 AttributeContainer 和 BuffManager。
         * 用 [EnemyDefinition.attributes] 初始化属性基础值。
         *
         * @param def 敌人定义
         * @param attributeDefinitions 属性定义（从 attributes.yaml 加载）
         * @param buffDefinitions Buff 定义（从 buffs.yaml 加载）
         * @return 新建的敌人参战实体
         */
        fun fromEnemyDefinition(
            def: EnemyDefinition,
            attributeDefinitions: Map<String, AttributeDefinition>,
            buffDefinitions: Map<String, BuffDefinition> = emptyMap()
        ): CombatEntity {
            // 创建属性容器并设置初始值
            val attrContainer = AttributeContainer(attributeDefinitions)
            for ((key, value) in def.attributes) {
                if (attrContainer.contains(key)) {
                    attrContainer.setBaseValue(key, value)
                }
            }

            val buffMgr = BuffManager(buffDefinitions, attrContainer)
            val cdManager = CooldownManager()

            return CombatEntity(
                entityId = "enemy:${def.key}",
                displayName = def.displayName.ifEmpty { def.key },
                isPlayer = false,
                attributeContainer = attrContainer,
                buffManager = buffMgr,
                skillIds = def.skills,
                cooldownManager = cdManager
            )
        }
    }
}
