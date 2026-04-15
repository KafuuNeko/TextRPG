package org.textrpg.application.domain.entity

import org.textrpg.application.domain.model.EquipmentSlot

/**
 * 玩家装备栏
 *
 * 记录玩家 8 个装备槽位上的物品实例 ID，null 表示该槽位为空。
 * 通过 [getSlot] 和 [withSlot] 方法按 [EquipmentSlot] 枚举访问，避免反射。
 *
 * 遵循规范 §4.2：纯 Kotlin 数据类，无框架注解；数据库映射由
 * [org.textrpg.application.data.dao.PlayerEquipmentEntity] 承担。
 *
 * @property id 记录主键，新建时传 0
 * @property playerId 所属玩家 ID
 * @property slotHead 头部槽位（物品实例 ID）
 * @property slotChest 胸甲槽位
 * @property slotWeapon 武器槽位
 * @property slotOffhand 副手槽位
 * @property slotRing 戒指槽位
 * @property slotAmulet 项链槽位
 * @property slotBoots 鞋子槽位
 * @property slotGloves 手套槽位
 */
data class PlayerEquipment(
    val id: Long = 0,
    val playerId: Long,
    val slotHead: Long? = null,
    val slotChest: Long? = null,
    val slotWeapon: Long? = null,
    val slotOffhand: Long? = null,
    val slotRing: Long? = null,
    val slotAmulet: Long? = null,
    val slotBoots: Long? = null,
    val slotGloves: Long? = null
) {
    /**
     * 按装备槽位枚举获取对应的物品实例 ID
     */
    fun getSlot(slot: EquipmentSlot): Long? = when (slot) {
        EquipmentSlot.HEAD -> slotHead
        EquipmentSlot.CHEST -> slotChest
        EquipmentSlot.WEAPON -> slotWeapon
        EquipmentSlot.OFFHAND -> slotOffhand
        EquipmentSlot.RING -> slotRing
        EquipmentSlot.AMULET -> slotAmulet
        EquipmentSlot.BOOTS -> slotBoots
        EquipmentSlot.GLOVES -> slotGloves
    }

    /**
     * 返回指定槽位设置为新值后的副本（不可变更新）
     */
    fun withSlot(slot: EquipmentSlot, instanceId: Long?): PlayerEquipment = when (slot) {
        EquipmentSlot.HEAD -> copy(slotHead = instanceId)
        EquipmentSlot.CHEST -> copy(slotChest = instanceId)
        EquipmentSlot.WEAPON -> copy(slotWeapon = instanceId)
        EquipmentSlot.OFFHAND -> copy(slotOffhand = instanceId)
        EquipmentSlot.RING -> copy(slotRing = instanceId)
        EquipmentSlot.AMULET -> copy(slotAmulet = instanceId)
        EquipmentSlot.BOOTS -> copy(slotBoots = instanceId)
        EquipmentSlot.GLOVES -> copy(slotGloves = instanceId)
    }

    /**
     * 获取所有已装备的物品实例 ID 列表
     */
    fun allEquippedInstanceIds(): List<Long> = listOfNotNull(
        slotHead, slotChest, slotWeapon, slotOffhand,
        slotRing, slotAmulet, slotBoots, slotGloves
    )
}
