package org.textrpg.application.data.manager

import org.textrpg.application.data.repository.PlayerRepository
import org.textrpg.application.domain.model.Player

/**
 * 玩家管理器——玩家模块的统一数据操作入口
 *
 * 封装 [PlayerRepository]，对外提供完整的玩家管理接口。
 * 外部模块操作玩家数据时，应通过此管理器而非直接调用 Repository。
 *
 * @param mPlayerRepository 玩家仓储（数据库操作）
 */
class PlayerManager(
    private val mPlayerRepository: PlayerRepository
) {

    /**
     * 创建新玩家
     *
     * @param name 玩家名称（需全局唯一）
     * @param bindAccount 绑定的社交平台账号 ID
     * @return 创建后的玩家（含数据库生成的主键和时间戳）
     */
    fun createPlayer(name: String, bindAccount: String): Player {
        return mPlayerRepository.save(Player(name = name, bindAccount = bindAccount))
    }

    /**
     * 根据 ID 查询玩家
     *
     * @param id 玩家主键
     * @return 玩家实例，不存在则返回 null
     */
    fun findById(id: Long): Player? {
        return mPlayerRepository.findById(id)
    }

    /**
     * 根据名称查询玩家
     *
     * @param name 玩家名称（精确匹配）
     * @return 玩家实例，不存在则返回 null
     */
    fun findByName(name: String): Player? {
        return mPlayerRepository.findByName(name)
    }

    /**
     * 根据绑定账号查询玩家
     *
     * 用于将社交平台用户（如 QQ 号）映射到游戏角色。
     *
     * @param bindAccount 绑定的社交平台账号 ID
     * @return 玩家实例，不存在则返回 null
     */
    fun findByBindAccount(bindAccount: String): Player? {
        return mPlayerRepository.findByBindAccount(bindAccount)
    }

    /**
     * 更新玩家信息
     *
     * @param player 要更新的玩家（需包含有效的 id）
     * @return 更新后的玩家
     * @throws IllegalStateException 玩家不存在时抛出
     */
    fun updatePlayer(player: Player): Player {
        return mPlayerRepository.save(player)
    }

    /**
     * 删除玩家
     *
     * @param id 要删除的玩家主键
     * @return 是否删除成功（玩家不存在时返回 false）
     */
    fun deletePlayer(id: Long): Boolean {
        return mPlayerRepository.delete(id)
    }
}