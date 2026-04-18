package org.textrpg.application.data.manager

import org.textrpg.application.data.repository.PlayerRepository
import org.textrpg.application.data.repository.PlayerAttributeRepository
import org.textrpg.application.data.config.PlayerAttributeConfig
import org.textrpg.application.domain.model.Player
import org.textrpg.application.domain.model.PlayerAttribute
import org.textrpg.application.domain.service.AttributeEvaluator

/**
 * 玩家管理器——玩家模块的统一数据操作入口
 *
 * 封装 [PlayerRepository]，对外提供完整的玩家管理接口。
 * 外部模块操作玩家数据时，应通过此管理器而非直接调用 Repository。
 *
 * @param mPlayerRepository 玩家仓储（数据库操作）
 * @param mPlayerAttributeRepository 玩家属性仓储
 * @param mPlayerAttributeConfig 玩家属性配置
 */
class PlayerManager(
    private val mPlayerRepository: PlayerRepository,
    private val mPlayerAttributeRepository: PlayerAttributeRepository,
    private val mPlayerAttributeConfig: PlayerAttributeConfig
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

    /**
     * 获取玩家所有的属性（包含基础属性和拓展属性的计算结果）
     * 
     * @param playerId 玩家ID
     * @param cache 用于在当前查询上下文中缓存计算结果
     * @return 包含所有已定义属性的列表
     */
    fun getAllAttributes(
        playerId: Long,
        cache: MutableMap<String, PlayerAttribute> = mutableMapOf()
    ): List<PlayerAttribute> {
        val result = mutableListOf<PlayerAttribute>()
        // 获取所有的键（基于配置文件）
        val allKeys = mPlayerAttributeConfig.basicAttributes.keys + mPlayerAttributeConfig.extendedAttributes.keys
        for (key in allKeys) {
            result.add(getAttribute(playerId, key, cache))
        }
        return result
    }

    /**
     * 获取玩家某个属性值
     *
     * 如果是拓展属性，需要自动计算后返回值。计算过程中会利用 cache 避免重复求值。
     * 否则优先从 repository 查找，没有则从 yaml 中取默认值。
     *
     * @param playerId 玩家ID
     * @param attributeName 属性名
     * @param cache 用于在当前查询上下文中缓存计算结果
     * @return 对应属性的值（类型为 PlayerAttribute），若未定义则返回默认值 0.0 所构造的实体
     */
    fun getAttribute(
        playerId: Long, 
        attributeName: String, 
        cache: MutableMap<String, PlayerAttribute> = mutableMapOf()
    ): PlayerAttribute {
        cache[attributeName]?.let { return it }

        val extendedAttr = mPlayerAttributeConfig.extendedAttributes[attributeName]
        if (extendedAttr != null) {
            // 自动计算，需要递归提供数值
            val calcValue = AttributeEvaluator.evaluate(extendedAttr.expression) { varName ->
                getAttribute(playerId, varName, cache).value
            }
            val result = PlayerAttribute(id = playerId, name = attributeName, value = calcValue)
            cache[attributeName] = result
            return result
        }

        val dbValue = mPlayerAttributeRepository.getAttribute(playerId, attributeName)
        if (dbValue != null) {
            cache[attributeName] = dbValue
            return dbValue
        }

        val basicAttr = mPlayerAttributeConfig.basicAttributes[attributeName]
        if (basicAttr != null) {
            val result = PlayerAttribute(id = playerId, name = attributeName, value = basicAttr.defaultValue)
            cache[attributeName] = result
            return result
        }

        val emptyResult = PlayerAttribute(id = playerId, name = attributeName, value = 0.0)
        cache[attributeName] = emptyResult
        return emptyResult
    }

    /**
     * 更新玩家某个属性值
     *
     * 仅支持基础属性的持久化更新。
     *
     * @param attribute 要更新的属性实例
     * @throws IllegalArgumentException 如果尝试更新的属性不是基础属性
     */
    fun updateAttribute(attribute: PlayerAttribute) {
        if (!mPlayerAttributeConfig.basicAttributes.containsKey(attribute.name)) {
            throw IllegalArgumentException("Only basic attributes can be updated. Attribute ${attribute.name} is not a valid basic attribute.")
        }
        mPlayerAttributeRepository.saveAttribute(attribute)
    }

    /**
     * 删除玩家属性
     *
     * @param attribute 要删除的属性实例
     */
    fun deleteAttribute(attribute: PlayerAttribute): Boolean {
        return mPlayerAttributeRepository.deleteAttribute(attribute)
    }
}