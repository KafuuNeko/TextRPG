package org.textrpg.application.data.repository

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.textrpg.application.data.database.PlayerBuffs
import org.textrpg.application.domain.entity.PlayerBuffEntity

/**
 * 持久性 Buff 数据模型
 *
 * 存储需要跨会话保留的 Buff 状态。
 *
 * @property id 数据库主键，新建时传 0
 * @property playerId 玩家数据库 ID
 * @property buffId Buff 定义标识符
 * @property stacks 当前叠加层数
 * @property remainingDuration 剩余持续回合数（负数表示永久）
 * @property createdAt 施加时间
 */
data class PersistentBuff(
    val id: Long = 0,
    val playerId: Long,
    val buffId: String,
    val stacks: Int = 1,
    val remainingDuration: Int = -1,
    val createdAt: DateTime? = null
)

/**
 * Buff 持久化仓储
 *
 * 管理玩家持久性 Buff 的存储与读取。
 * 仅存储需要跨会话保留的 Buff（如中毒、祝福等），
 * 战斗中的临时 Buff 由 [BuffManager] 内存管理，不在此处存储。
 *
 * @param database Exposed Database 实例
 */
class BuffRepository(private val database: Database) {

    /**
     * 查询指定玩家的所有持久性 Buff
     *
     * @param playerId 玩家数据库 ID
     * @return 持久性 Buff 列表
     */
    fun findByPlayerId(playerId: Long): List<PersistentBuff> = transaction(database) {
        PlayerBuffEntity.find { PlayerBuffs.playerId eq playerId }
            .map { it.toPersistentBuff() }
    }

    /**
     * 保存持久性 Buff（新增或更新）
     *
     * @param buff 要保存的 Buff 数据
     * @return 保存后的 Buff（含数据库生成的主键）
     */
    fun save(buff: PersistentBuff): PersistentBuff = transaction(database) {
        if (buff.id == 0L) {
            PlayerBuffEntity.new {
                playerId = buff.playerId
                buffId = buff.buffId
                stacks = buff.stacks
                remainingDuration = buff.remainingDuration
                createdAt = DateTime.now()
            }.toPersistentBuff()
        } else {
            val existing = PlayerBuffEntity.findById(buff.id)
                ?: error("PersistentBuff not found: ${buff.id}")
            existing.apply {
                stacks = buff.stacks
                remainingDuration = buff.remainingDuration
            }.toPersistentBuff()
        }
    }

    /**
     * 删除指定 Buff 记录
     *
     * @param id Buff 记录主键
     * @return 是否删除成功
     */
    fun delete(id: Long): Boolean = transaction(database) {
        val entity = PlayerBuffEntity.findById(id) ?: return@transaction false
        entity.delete()
        true
    }

    /**
     * 删除指定玩家的指定 Buff
     *
     * @param playerId 玩家数据库 ID
     * @param buffId Buff 定义标识符
     * @return 删除的记录数
     */
    fun deleteByPlayerAndBuff(playerId: Long, buffId: String): Int = transaction(database) {
        val entities = PlayerBuffEntity.find {
            (PlayerBuffs.playerId eq playerId) and (PlayerBuffs.buffId eq buffId)
        }
        val count = entities.count().toInt()
        entities.forEach { it.delete() }
        count
    }

    /**
     * 清除指定玩家的所有持久性 Buff
     *
     * @param playerId 玩家数据库 ID
     * @return 删除的记录数
     */
    fun deleteAllByPlayer(playerId: Long): Int = transaction(database) {
        val entities = PlayerBuffEntity.find { PlayerBuffs.playerId eq playerId }
        val count = entities.count().toInt()
        entities.forEach { it.delete() }
        count
    }

    private fun PlayerBuffEntity.toPersistentBuff() = PersistentBuff(
        id = id.value,
        playerId = playerId,
        buffId = buffId,
        stacks = stacks,
        remainingDuration = remainingDuration,
        createdAt = createdAt
    )
}
