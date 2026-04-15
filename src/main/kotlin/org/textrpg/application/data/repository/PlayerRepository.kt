package org.textrpg.application.data.repository

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.textrpg.application.data.database.Players
import org.textrpg.application.domain.entity.PlayerEntity

/**
 * 玩家领域模型
 *
 * 包含玩家基础信息和属性数据快照。
 *
 * @property id 数据库主键，新建时传 0
 * @property name 玩家名称（全局唯一）
 * @property bindAccount 绑定的社交平台账号 ID（用于将 QQ/微信等平台用户关联到游戏角色）
 * @property attributeData 属性基础值 JSON 快照（如 {"strength": 15, "current_hp": 80}）
 * @property createdAt 创建时间
 * @property updatedAt 更新时间
 */
data class Player(
    val id: Long = 0,
    val name: String,
    val bindAccount: String,
    val attributeData: String = "{}",
    val createdAt: DateTime? = null,
    val updatedAt: DateTime? = null
)

/**
 * 玩家仓储实现
 *
 * 负责玩家基础信息的持久化，支持按名称和绑定账号查询。
 * 游戏数值相关的操作不在此仓储中，由专门的数值服务处理。
 *
 * @param database Exposed Database 实例
 */
class PlayerRepository(private val database: Database) : IRepository<Player, Long> {

    /**
     * 根据 ID 查询玩家
     *
     * @param id 玩家主键
     * @return 玩家实例，不存在则返回 null
     */
    override fun findById(id: Long): Player? = transaction(database) {
        PlayerEntity.findById(id)?.toPlayer()
    }

    /**
     * 查询所有玩家
     *
     * @return 所有玩家的列表
     */
    override fun findAll(): List<Player> = transaction(database) {
        PlayerEntity.all().map { it.toPlayer() }
    }

    /**
     * 根据名称查询玩家
     *
     * @param name 玩家名称（精确匹配）
     * @return 玩家实例，不存在则返回 null
     */
    fun findByName(name: String): Player? = transaction(database) {
        PlayerEntity.find { Players.name eq name }.firstOrNull()?.toPlayer()
    }

    /**
     * 根据绑定账号查询玩家
     *
     * 用于将社交平台用户（如 QQ 号）映射到游戏角色。
     *
     * @param bindAccount 绑定的社交平台账号 ID
     * @return 玩家实例，不存在则返回 null
     */
    fun findByBindAccount(bindAccount: String): Player? = transaction(database) {
        PlayerEntity.find { Players.bindAccount eq bindAccount }.firstOrNull()?.toPlayer()
    }

    /**
     * 保存玩家（新增或更新）
     *
     * @param entity 要保存的玩家实体
     * @return 保存后的实体（含数据库生成的主键和 timestamp）
     */
    override fun save(entity: Player): Player = transaction(database) {
        if (entity.id == 0L) {
            val now = DateTime.now()
            val id = Players.insertAndGetId {
                it[name] = entity.name
                it[bindAccount] = entity.bindAccount
                it[attributeData] = entity.attributeData
                it[createdAt] = now
                it[updatedAt] = now
            }
            entity.copy(id = id.value, createdAt = now, updatedAt = now)
        } else {
            val existing = PlayerEntity.findById(entity.id)
                ?: error("Player not found: ${entity.id}")
            existing.apply {
                name = entity.name
                bindAccount = entity.bindAccount
                attributeData = entity.attributeData
                updatedAt = DateTime.now()
            }.toPlayer()
        }
    }

    /**
     * 删除玩家
     *
     * @param id 要删除的玩家主键
     * @return 是否删除成功
     */
    override fun delete(id: Long): Boolean = transaction(database) {
        val entity = PlayerEntity.findById(id) ?: return@transaction false
        entity.delete()
        true
    }

    /**
     * 保存玩家属性数据快照
     *
     * 轻量级更新：仅写入 attributeData 和 updatedAt，不触碰其他字段。
     * 适用于战斗结算、地图切换等安全节点的异步落盘。
     *
     * @param playerId 玩家主键
     * @param attributeData 属性基础值 JSON
     */
    fun saveAttributeData(playerId: Long, attributeData: String) = transaction(database) {
        val existing = PlayerEntity.findById(playerId)
            ?: error("Player not found: $playerId")
        existing.attributeData = attributeData
        existing.updatedAt = DateTime.now()
    }

    /**
     * 读取玩家属性数据快照
     *
     * @param playerId 玩家主键
     * @return 属性基础值 JSON，玩家不存在时返回 null
     */
    fun loadAttributeData(playerId: Long): String? = transaction(database) {
        PlayerEntity.findById(playerId)?.attributeData
    }

    private fun PlayerEntity.toPlayer() = Player(
        id = id.value,
        name = name,
        bindAccount = bindAccount,
        attributeData = attributeData,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
