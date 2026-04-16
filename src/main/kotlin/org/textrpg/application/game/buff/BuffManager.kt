package org.textrpg.application.game.buff

import io.github.oshai.kotlinlogging.KotlinLogging
import org.textrpg.application.domain.model.*
import org.textrpg.application.game.attribute.AttributeContainer
import org.textrpg.application.game.effect.EffectContext
import org.textrpg.application.game.effect.EffectEngine
import org.textrpg.application.utils.script.KotlinScriptRunner
import java.io.File

private val log = KotlinLogging.logger {}

/**
 * Buff 管理器（per-entity）
 *
 * 管理单个游戏实体身上所有活跃的 Buff。负责 Buff 的施加、叠加策略处理、
 * 修正器挂载/回滚、周期效果执行和过期移除。
 *
 * 每个实体（玩家/怪物/NPC）拥有独立的 BuffManager 实例。
 * EntityAccessor 实现类将 `hasBuff`/`addBuff`/`removeBuff` 委托给 BuffManager。
 *
 * **tick 由外部驱动**：调用方（回合结束逻辑 / 战斗系统）调用 [tick] 方法触发周期效果。
 *
 * 修正器来源标识规则：
 * - REFRESH / STACK：`"buff:{buffId}"`
 * - INDEPENDENT：`"buff:{buffId}:{instanceIndex}"`
 *
 * STACK 策略修正器处理：叠层时移除旧修正器，按新层数（value * stacks）重新挂载。
 *
 * @param buffDefinitions Buff 定义映射（从 [BuffConfigLoader] 加载）
 * @param attributeContainer 所属实体的属性容器（修正器挂载目标）
 * @param scriptRunner Kotlin 脚本执行器（可选，用于 onApply/onRemove/onStack 钩子）
 */
class BuffManager(
    private val buffDefinitions: Map<String, BuffDefinition>,
    private val attributeContainer: AttributeContainer,
    private val scriptRunner: KotlinScriptRunner? = null
) {

    /** 活跃的 Buff 实例列表 */
    private val activeBuffs: MutableList<BuffInstance> = mutableListOf()

    /** 实例索引计数器（INDEPENDENT 策略用于生成唯一来源标识） */
    private var instanceCounter: Int = 0

    /**
     * 检查是否拥有指定 Buff
     */
    fun hasBuff(buffId: String): Boolean {
        return activeBuffs.any { it.definition.key == buffId }
    }

    /**
     * 获取指定 Buff 的当前层数
     *
     * INDEPENDENT 策略下返回所有独立实例的层数之和。
     *
     * @return 层数总和，不存在时返回 0
     */
    fun getBuffStacks(buffId: String): Int {
        return activeBuffs.filter { it.definition.key == buffId }.sumOf { it.stacks }
    }

    /**
     * 获取所有活跃 Buff 的快照
     *
     * 返回副本列表，外部修改不影响内部状态。
     */
    fun getActiveBuffs(): List<BuffInstance> = activeBuffs.toList()

    /**
     * 施加 Buff
     *
     * 根据 Buff 定义的叠加策略处理重复施加：
     * - **REFRESH**：刷新持续时间（取较长者），不增加层数
     * - **STACK**：增加层数（上限 maxStacks），重新挂载修正器（按新层数缩放）
     * - **INDEPENDENT**：创建新的独立实例
     *
     * @param buffId Buff 标识符
     * @param stacks 施加的层数（默认 1）
     * @param durationOverride 自定义持续时间（null 则使用定义中的 duration）
     */
    fun applyBuff(buffId: String, stacks: Int = 1, durationOverride: Int? = null) {
        val definition = buffDefinitions[buffId]
        if (definition == null) {
            log.warn { "Buff definition not found: $buffId" }
            return
        }

        val duration = durationOverride ?: definition.duration

        when (definition.stackPolicy) {
            StackPolicy.REFRESH -> applyRefresh(definition, duration)
            StackPolicy.STACK -> applyStack(definition, stacks, duration)
            StackPolicy.INDEPENDENT -> applyIndependent(definition, stacks, duration)
        }
    }

    /**
     * REFRESH 策略：刷新持续时间
     */
    private fun applyRefresh(definition: BuffDefinition, duration: Int) {
        val existing = activeBuffs.find { it.definition.key == definition.key }
        if (existing != null) {
            // 取较长的持续时间
            if (!existing.isPermanent && duration > existing.remainingDuration) {
                existing.remainingDuration = duration
            }
        } else {
            // 新施加
            val instance = createInstance(definition, 1, duration)
            activeBuffs.add(instance)
            attachModifiers(instance)
            executeHookScript(definition.onApply, definition.key, 1)
        }
    }

    /**
     * STACK 策略：增加层数
     */
    private fun applyStack(definition: BuffDefinition, addStacks: Int, duration: Int) {
        val existing = activeBuffs.find { it.definition.key == definition.key }
        if (existing != null) {
            val oldStacks = existing.stacks
            existing.stacks = (existing.stacks + addStacks).coerceAtMost(definition.maxStacks)

            // 刷新持续时间（取较长者）
            if (!existing.isPermanent && duration > existing.remainingDuration) {
                existing.remainingDuration = duration
            }

            // 重新挂载修正器（按新层数缩放）
            if (existing.stacks != oldStacks) {
                detachModifiers(existing)
                attachModifiers(existing)
                executeHookScript(definition.onStack, definition.key, existing.stacks)
            }
        } else {
            val instance = createInstance(definition, addStacks.coerceAtMost(definition.maxStacks), duration)
            activeBuffs.add(instance)
            attachModifiers(instance)
            executeHookScript(definition.onApply, definition.key, instance.stacks)
        }
    }

    /**
     * INDEPENDENT 策略：创建独立实例
     */
    private fun applyIndependent(definition: BuffDefinition, stacks: Int, duration: Int) {
        // 检查同 buffId 的独立实例数是否已达上限
        val existingCount = activeBuffs.count { it.definition.key == definition.key }
        if (existingCount >= definition.maxStacks) {
            return // 达到上限，忽略
        }

        val instance = createInstance(definition, stacks, duration)
        activeBuffs.add(instance)
        attachModifiers(instance)
        executeHookScript(definition.onApply, definition.key, stacks)
    }

    /**
     * 移除指定 Buff 的所有实例
     *
     * @param buffId Buff 标识符
     */
    fun removeBuff(buffId: String) {
        val toRemove = activeBuffs.filter { it.definition.key == buffId }
        toRemove.forEach { instance ->
            detachModifiers(instance)
            executeHookScript(instance.definition.onRemove, buffId, instance.stacks)
        }
        activeBuffs.removeAll(toRemove)
    }

    /**
     * 执行 Buff 周期 tick
     *
     * 每回合结束时由外部调用。遍历所有活跃 Buff，执行 tick_effects，
     * 递减持续时间，移除过期 Buff。
     *
     * 迭代使用快照列表，防止 tick_effects 中修改 Buff 列表导致并发异常。
     *
     * @param context 特效执行上下文（提供施法者/目标等信息）
     * @param effectEngine 特效引擎（执行 tick_effects）
     * @return 所有 tick_effects 的执行结果
     */
    suspend fun tick(context: EffectContext, effectEngine: EffectEngine): List<EffectResult> {
        val results = mutableListOf<EffectResult>()
        val snapshot = activeBuffs.toList()

        for (instance in snapshot) {
            // 执行 tick_effects
            if (instance.definition.tickEffects.isNotEmpty()) {
                val tickContext = BuffTickEffectContext(context, instance)
                val tickResults = effectEngine.executeEffects(instance.definition.tickEffects, tickContext)
                results.addAll(tickResults)
            }

            // 递减持续时间（永久 Buff 跳过）
            if (!instance.isPermanent) {
                instance.remainingDuration--
            }
        }

        // 移除过期 Buff
        val expired = activeBuffs.filter { it.isExpired }
        expired.forEach { instance ->
            detachModifiers(instance)
            executeHookScript(instance.definition.onRemove, instance.definition.key, instance.stacks)
        }
        activeBuffs.removeAll(expired)

        return results
    }

    /**
     * 移除所有 Buff
     *
     * 清理所有修正器和实例。战斗结束或特殊效果时使用。
     */
    fun removeAllBuffs() {
        activeBuffs.forEach { detachModifiers(it) }
        activeBuffs.clear()
    }

    // ======================== 内部辅助方法 ========================

    /**
     * 创建 Buff 实例
     */
    private fun createInstance(definition: BuffDefinition, stacks: Int, duration: Int): BuffInstance {
        return BuffInstance(
            definition = definition,
            instanceIndex = instanceCounter++,
            stacks = stacks,
            remainingDuration = duration
        )
    }

    /**
     * 挂载 Buff 修正器到属性容器
     *
     * STACK 策略下修正器值按层数缩放（value * stacks）。
     */
    private fun attachModifiers(instance: BuffInstance) {
        for (modDef in instance.definition.modifiers) {
            val scaledValue = modDef.value * instance.stacks
            val modifier = Modifier(
                source = instance.modifierSource,
                type = modDef.type,
                value = scaledValue,
                priority = modDef.priority
            )
            if (attributeContainer.contains(modDef.attribute)) {
                attributeContainer.addModifier(modDef.attribute, modifier)
            }
        }
    }

    /**
     * 从属性容器移除 Buff 修正器
     */
    private fun detachModifiers(instance: BuffInstance) {
        attributeContainer.removeAllModifiersBySource(instance.modifierSource)
    }

    /**
     * 执行生命周期钩子脚本
     *
     * @param scriptPath 脚本路径（如 "scripts/burn_apply.kts"），null 则跳过
     * @param buffId Buff 标识符（注入脚本上下文）
     * @param stacks 当前层数（注入脚本上下文）
     */
    private fun executeHookScript(scriptPath: String?, buffId: String, stacks: Int) {
        if (scriptPath == null || scriptRunner == null) return

        val file = File(scriptPath)
        if (!file.exists()) {
            log.warn { "Buff hook script not found: $scriptPath" }
            return
        }

        val context = mapOf<String, Any?>(
            "buffId" to buffId,
            "stacks" to stacks,
            "attributeContainer" to attributeContainer
        )

        val result = scriptRunner.executeScript(file.readText(), context)
        if (!result.success) {
            log.warn { "Buff hook script failed ($scriptPath): ${result.message}" }
        }
    }
}
