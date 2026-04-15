package org.textrpg.application.game.combat

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import org.textrpg.application.domain.model.CombatConfig
import org.textrpg.application.domain.model.EnemyDefinition
import org.textrpg.application.game.attribute.FormulaEngine
import org.textrpg.application.game.command.Session
import org.textrpg.application.game.command.SessionManager
import org.textrpg.application.game.effect.EffectEngine
import org.textrpg.application.game.skill.SkillEngine
import kotlin.random.Random

private val log = KotlinLogging.logger {}

/**
 * 战斗结局
 */
enum class CombatOutcome {
    /** 战斗继续 */
    CONTINUE,
    /** 玩家胜利 */
    PLAYER_WIN,
    /** 玩家失败 */
    PLAYER_LOSE,
    /** 玩家逃跑 */
    PLAYER_FLEE
}

/**
 * 战斗会话
 *
 * 实现 [Session] 接口的纯回合制战斗系统。内部启动协程运行战斗循环，
 * 需要玩家输入时 suspend 在 [CompletableDeferred] 上，
 * [handleMessage] 被调用时 complete 它来恢复循环。
 *
 * 战斗消息通过 [CombatEntity.sendMessage] 直接发送，[handleMessage] 返回 null。
 *
 * 回合流程：
 * ```
 * 回合开始 → 玩家行动（suspend 等待输入）→ 胜负检查
 *          → 敌人行动（AI 决策）→ 胜负检查
 *          → 回合结束（Buff tick + CD tick）→ 胜负检查
 * ```
 *
 * @param playerId 玩家 ID
 * @param playerEntity 玩家参战实体
 * @param enemyEntity 敌人参战实体
 * @param enemyDefinition 敌人定义（含 AI 规则和奖励）
 * @param combatConfig 战斗全局配置
 * @param skillEngine 技能引擎
 * @param effectEngine 特效引擎
 * @param enemyAI AI 决策引擎
 * @param sessionManager 会话管理器（战斗结束时注销）
 * @param coroutineScope 协程作用域
 */
class CombatSession(
    override val playerId: Long,
    private val playerEntity: CombatEntity,
    private val enemyEntity: CombatEntity,
    private val enemyDefinition: EnemyDefinition,
    private val combatConfig: CombatConfig,
    private val skillEngine: SkillEngine,
    private val effectEngine: EffectEngine,
    private val enemyAI: EnemyAI,
    private val sessionManager: SessionManager,
    private val coroutineScope: CoroutineScope
) : Session {

    override val type: String = "combat"

    private var active = true
    private var inputDeferred: CompletableDeferred<String>? = null
    private var combatJob: Job? = null

    /** 玩家操作菜单（按索引对应） */
    private var currentMenu: List<MenuEntry> = emptyList()

    /**
     * 启动战斗循环
     *
     * 在协程中运行完整的战斗流程。战斗结束后自动注销会话。
     */
    fun start() {
        combatJob = coroutineScope.launch {
            try {
                runCombatLoop()
            } catch (e: CancellationException) {
                throw e // 不吞掉取消异常
            } catch (e: Exception) {
                playerEntity.sendMessage("战斗异常终止：${e.message}")
            } finally {
                active = false
                sessionManager.endSession(playerId)
            }
        }
    }

    override fun isActive(): Boolean = active

    override suspend fun handleMessage(message: String): String? {
        inputDeferred?.complete(message)
        return null // 战斗消息由协程内部通过 sendMessage 发送
    }

    override suspend fun onTimeout() {
        inputDeferred?.complete("__timeout__")
    }

    // ======================== 战斗主循环 ========================

    private suspend fun runCombatLoop() {
        playerEntity.sendMessage("⚔ 战斗开始！对手：${enemyEntity.displayName}")
        playerEntity.sendMessage(formatEnemyStatus())

        var roundNumber = 1
        while (true) {
            val outcome = runRound(roundNumber++)
            when (outcome) {
                CombatOutcome.CONTINUE -> continue
                CombatOutcome.PLAYER_WIN -> {
                    playerEntity.sendMessage("🏆 战斗胜利！击败了 ${enemyEntity.displayName}！")
                    settlement()
                    break
                }
                CombatOutcome.PLAYER_LOSE -> {
                    playerEntity.sendMessage("💀 战斗失败……")
                    break
                }
                CombatOutcome.PLAYER_FLEE -> {
                    playerEntity.sendMessage("🏃 成功逃离了战斗！")
                    break
                }
            }
        }
    }

    /**
     * 执行单回合
     */
    private suspend fun runRound(roundNumber: Int): CombatOutcome {
        playerEntity.sendMessage("\n--- 第 ${roundNumber} 回合 ---")

        // 玩家回合（玩家可能选择逃跑等终结战斗的行动，优先于胜负判定返回）
        val playerOutcome = playerTurn()
        if (playerOutcome != CombatOutcome.CONTINUE) return playerOutcome

        val check1 = checkWinLose()
        if (check1 != CombatOutcome.CONTINUE) return check1

        // 敌人回合
        enemyTurn(roundNumber)
        val check2 = checkWinLose()
        if (check2 != CombatOutcome.CONTINUE) return check2

        // 回合结束：Buff tick + CD tick
        roundEndPhase()
        return checkWinLose()
    }

    // ======================== 玩家回合 ========================

    /**
     * 处理玩家回合
     *
     * @return [CombatOutcome.CONTINUE] 表示战斗继续；其他值（如 [CombatOutcome.PLAYER_FLEE]）
     *         表示玩家行动直接终结战斗
     */
    private suspend fun playerTurn(): CombatOutcome {
        // 构建菜单
        val menu = buildPlayerMenu()
        currentMenu = menu

        val menuText = buildString {
            appendLine("你的回合，请选择行动：")
            menu.forEachIndexed { index, entry ->
                appendLine("  ${index + 1}. ${entry.display}")
            }
        }
        playerEntity.sendMessage(menuText)

        // 等待输入
        val input = awaitPlayerInput(combatConfig.defaultTimeoutSeconds * 1000)

        if (input == "__timeout__") {
            playerEntity.sendMessage("⏰ 超时，自动防御。")
            return CombatOutcome.CONTINUE
        }

        // 解析输入
        val action = parsePlayerInput(input)
        if (action == null) {
            playerEntity.sendMessage("无效输入，本回合跳过。")
            return CombatOutcome.CONTINUE
        }

        // 执行行动
        return executePlayerAction(action)
    }

    private fun buildPlayerMenu(): List<MenuEntry> {
        val menu = mutableListOf<MenuEntry>()

        // 普通攻击（始终可用）
        menu.add(MenuEntry("攻击", MenuActionType.ATTACK))

        // 可用技能
        for (skillId in playerEntity.skillIds) {
            val skill = skillEngine.getSkill(skillId) ?: continue
            val onCd = playerEntity.cooldownManager.isOnCooldown(playerEntity.entityId, skillId)
            if (!onCd) {
                menu.add(MenuEntry(skill.displayName.ifEmpty { skillId }, MenuActionType.SKILL, skillId))
            } else {
                val remaining = playerEntity.cooldownManager.getCooldown(playerEntity.entityId, skillId)
                menu.add(MenuEntry("${skill.displayName.ifEmpty { skillId }}（冷却${remaining}回合）", MenuActionType.DISABLED))
            }
        }

        // 防御 & 逃跑
        menu.add(MenuEntry("防御", MenuActionType.DEFEND))
        if (combatConfig.fleeCheck != null) {
            menu.add(MenuEntry("逃跑", MenuActionType.FLEE))
        }

        return menu
    }

    private fun parsePlayerInput(input: String): MenuEntry? {
        // 尝试按数字索引
        val index = input.trim().toIntOrNull()
        if (index != null && index in 1..currentMenu.size) {
            val entry = currentMenu[index - 1]
            if (entry.actionType == MenuActionType.DISABLED) return null
            return entry
        }

        // 尝试按名称匹配
        return currentMenu.find {
            it.actionType != MenuActionType.DISABLED && it.display.contains(input.trim(), ignoreCase = true)
        }
    }

    /**
     * 执行玩家选定的行动
     *
     * @return [CombatOutcome.CONTINUE] 战斗继续；[CombatOutcome.PLAYER_FLEE] 玩家成功逃跑
     */
    private suspend fun executePlayerAction(action: MenuEntry): CombatOutcome {
        when (action.actionType) {
            MenuActionType.ATTACK -> {
                // 普通攻击：使用战斗公式直接计算
                val damage = calculateDamage(playerEntity, enemyEntity)
                enemyEntity.modifyAttribute(combatConfig.attributeKeys.currentHp, -damage)
                playerEntity.sendMessage("你发起攻击，造成 ${damage.toInt()} 点伤害！")
                playerEntity.sendMessage(formatEnemyStatus())
            }
            MenuActionType.SKILL -> {
                val skillId = action.data ?: return CombatOutcome.CONTINUE
                val context = buildCombatContext(playerEntity, enemyEntity)
                val result = skillEngine.useSkill(skillId, context, sessionType = "combat")
                if (result.success) {
                    playerEntity.sendMessage("${result.message}")
                    playerEntity.sendMessage(formatEnemyStatus())
                } else {
                    playerEntity.sendMessage("技能失败：${result.message}")
                }
            }
            MenuActionType.DEFEND -> {
                playerEntity.sendMessage("你采取防御姿态。")
                // 防御效果由 Buff 或临时修正器实现（简化版直接跳过）
            }
            MenuActionType.FLEE -> {
                return tryFlee()
            }
            MenuActionType.DISABLED -> { /* 不应到达 */ }
        }
        return CombatOutcome.CONTINUE
    }

    /**
     * 求值 [CombatConfig.fleeCheck] 公式判定逃跑结果
     *
     * 公式 > 0 视为成功（约定与暴击判定方向相反但语义自洽：成功条件由公式直观表达）。
     * 公式异常或未配置 fleeCheck 时视为失败，本回合作废但战斗继续。
     */
    private suspend fun tryFlee(): CombatOutcome {
        val fleeFormula = combatConfig.fleeCheck
        if (fleeFormula == null) {
            playerEntity.sendMessage("此战斗不允许逃跑。")
            return CombatOutcome.CONTINUE
        }
        val fleeCtx = CombatEffectContext(
            source = playerEntity,
            primaryTarget = enemyEntity,
            allies = listOf(playerEntity),
            enemies = listOf(enemyEntity),
            combatConfig = combatConfig
        )
        val result = runCatching {
            fleeCtx.resolveFormula(fleeCtx.preprocessCombatFormula(fleeFormula), playerEntity)
        }.getOrElse {
            playerEntity.sendMessage("逃跑公式异常，本次尝试失败。")
            return CombatOutcome.CONTINUE
        }
        return if (result > 0) {
            CombatOutcome.PLAYER_FLEE
        } else {
            playerEntity.sendMessage("逃跑失败！")
            CombatOutcome.CONTINUE
        }
    }

    // ======================== 敌人回合 ========================

    private suspend fun enemyTurn(roundNumber: Int) {
        val state = CombatState(roundNumber, playerEntity, enemyEntity)
        val decision = enemyAI.decide(enemyEntity, enemyDefinition, state)

        when (decision.actionType) {
            AIActionType.SKILL -> {
                val skillId = decision.skillId
                if (skillId != null) {
                    val skill = skillEngine.getSkill(skillId)
                    val context = buildCombatContext(enemyEntity, playerEntity)
                    val result = skillEngine.useSkill(skillId, context, sessionType = "combat")
                    val skillName = skill?.displayName?.ifEmpty { skillId } ?: skillId
                    if (result.success) {
                        playerEntity.sendMessage("${enemyEntity.displayName} 使用了 $skillName！")
                    } else {
                        // 技能失败时执行普通攻击
                        val damage = calculateDamage(enemyEntity, playerEntity)
                        playerEntity.modifyAttribute(combatConfig.attributeKeys.currentHp, -damage)
                        playerEntity.sendMessage("${enemyEntity.displayName} 发起攻击，造成 ${damage.toInt()} 点伤害！")
                    }
                }
            }
            AIActionType.DEFEND -> {
                playerEntity.sendMessage("${enemyEntity.displayName} 采取防御姿态。")
            }
            AIActionType.FLEE -> {
                playerEntity.sendMessage("${enemyEntity.displayName} 试图逃跑……但失败了。")
            }
        }

        playerEntity.sendMessage(formatPlayerStatus())
    }

    // ======================== 回合结束 ========================

    private suspend fun roundEndPhase() {
        // Buff tick
        val playerCtx = buildCombatContext(playerEntity, playerEntity)
        playerEntity.buffs.tick(playerCtx, effectEngine)

        val enemyCtx = buildCombatContext(enemyEntity, enemyEntity)
        enemyEntity.buffs.tick(enemyCtx, effectEngine)

        // CD tick
        playerEntity.cooldownManager.tickCooldowns(playerEntity.entityId)
        enemyEntity.cooldownManager.tickCooldowns(enemyEntity.entityId)
    }

    // ======================== 伤害计算 ========================

    /**
     * 使用全局伤害公式计算伤害
     *
     * 预处理 `attacker.` / `defender.` 前缀和 `random(N)` 后交给 FormulaEngine。
     */
    private fun calculateDamage(attacker: CombatEntity, defender: CombatEntity): Double {
        val ctx = CombatEffectContext(attacker, defender, emptyList(), emptyList(), combatConfig)

        // 基础伤害
        val formula = ctx.preprocessCombatFormula(combatConfig.damageFormula)
        var damage = ctx.resolveFormula(formula, attacker)

        // 暴击判定（critCheck 设计为 "random(100) - crit_rate"，<= 0 表示暴击）
        // 公式异常时静默跳过暴击，避免因为暴击公式破坏战斗主流程
        runCatching {
            val critFormula = ctx.preprocessCombatFormula(combatConfig.critCheck)
            ctx.resolveFormula(critFormula, attacker)
        }.onSuccess { critResult ->
            if (critResult <= 0) damage *= combatConfig.critMultiplier
        }

        // 最低伤害
        if (damage < combatConfig.minDamage) {
            damage = combatConfig.minDamage
        }

        return damage
    }

    // ======================== 胜负判定 ========================

    private fun checkWinLose(): CombatOutcome {
        if (!enemyEntity.isAlive) return CombatOutcome.PLAYER_WIN
        if (!playerEntity.isAlive) return CombatOutcome.PLAYER_LOSE
        return CombatOutcome.CONTINUE
    }

    // ======================== 战斗结算 ========================

    private suspend fun settlement() {
        val rewards = enemyDefinition.rewards ?: return

        // 经验结算（公式异常时跳过经验奖励，避免破坏掉落结算流程）
        rewards.expFormula?.let { expFormula ->
            runCatching {
                val formula = expFormula
                    .replace("enemy.", "enemy_")
                    .replace("player.", "player_")
                FormulaEngine.evaluate(formula) { key ->
                    when {
                        key.startsWith("enemy_") -> enemyEntity.getAttributeValue(key.removePrefix("enemy_"))
                        key.startsWith("player_") -> playerEntity.getAttributeValue(key.removePrefix("player_"))
                        else -> enemyEntity.getAttributeValue(key)
                    }
                }
            }.fold(
                onSuccess = { exp ->
                    playerEntity.modifyAttribute(combatConfig.attributeKeys.exp, exp)
                    playerEntity.sendMessage("获得 ${exp.toInt()} 经验值")
                },
                onFailure = { log.warn(it) { "Exp formula evaluation failed" } }
            )
        }

        // 掉落结算
        val obtainedItems = mutableListOf<String>()
        for (drop in rewards.drops) {
            if (Random.nextDouble() < drop.chance) {
                val qty = if (drop.quantityMin == drop.quantityMax) drop.quantityMin
                else Random.nextInt(drop.quantityMin, drop.quantityMax + 1)
                playerEntity.giveItem(drop.item, qty)
                obtainedItems.add("${drop.item} x$qty")
            }
        }
        if (obtainedItems.isNotEmpty()) {
            playerEntity.sendMessage("获得物品：${obtainedItems.joinToString("、")}")
        }
    }

    // ======================== 输入等待 ========================

    private suspend fun awaitPlayerInput(timeoutMs: Long): String {
        val deferred = CompletableDeferred<String>()
        inputDeferred = deferred
        return try {
            withTimeoutOrNull(timeoutMs) { deferred.await() } ?: "__timeout__"
        } finally {
            inputDeferred = null
        }
    }

    // ======================== 辅助方法 ========================

    private fun buildCombatContext(source: CombatEntity, target: CombatEntity): CombatEffectContext {
        val allies = if (source.isPlayer) listOf(playerEntity) else listOf(enemyEntity)
        val enemies = if (source.isPlayer) listOf(enemyEntity) else listOf(playerEntity)
        return CombatEffectContext(
            source = source,
            primaryTarget = target,
            allies = allies,
            enemies = enemies,
            combatConfig = combatConfig
        )
    }

    private fun formatEnemyStatus(): String {
        val hp = enemyEntity.getAttributeValue(combatConfig.attributeKeys.currentHp).toInt()
        val maxHp = enemyEntity.getAttributeValue(combatConfig.attributeKeys.maxHp).toInt()
        return "[${enemyEntity.displayName}] HP: $hp / $maxHp"
    }

    private fun formatPlayerStatus(): String {
        val hp = playerEntity.getAttributeValue(combatConfig.attributeKeys.currentHp).toInt()
        val maxHp = playerEntity.getAttributeValue(combatConfig.attributeKeys.maxHp).toInt()
        return "[你] HP: $hp / $maxHp"
    }
}

/**
 * 菜单动作类型
 */
enum class MenuActionType {
    ATTACK, SKILL, DEFEND, FLEE, DISABLED
}

/**
 * 菜单条目
 */
data class MenuEntry(
    val display: String,
    val actionType: MenuActionType,
    val data: String? = null
)
