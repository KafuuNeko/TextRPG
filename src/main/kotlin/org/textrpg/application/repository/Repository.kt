package org.textrpg.application.repository

/**
 * 通用仓储接口，定义基础 CRUD 操作
 *
 * @param T 实体类型
 * @param ID 实体主键类型
 */
interface Repository<T, ID> {
    /**
     * 根据 ID 查询单个实体
     *
     * @param id 实体主键
     * @return 实体实例，若不存在则返回 null
     */
    fun findById(id: ID): T?

    /**
     * 查询所有实体
     *
     * @return 所有实体的列表
     */
    fun findAll(): List<T>

    /**
     * 保存实体（新增或更新）
     *
     * @param entity 要保存的实体，id == 0 时为新增，否则为更新
     * @return 保存后的实体（含数据库生成的主键）
     */
    fun save(entity: T): T

    /**
     * 根据 ID 删除实体
     *
     * @param id 要删除的实体主键
     * @return 是否删除成功（实体不存在时返回 false）
     */
    fun delete(id: ID): Boolean
}