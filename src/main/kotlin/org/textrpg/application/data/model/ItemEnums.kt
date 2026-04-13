package org.textrpg.application.data.model

/**
 * 物品类型枚举
 */
enum class ItemType(val value: Int) {
    EQUIPMENT(1),
    CONSUMABLE(2),
    MATERIAL(3),
    QUEST(4);

    companion object {
        fun fromValue(value: Int): ItemType = entries.first { it.value == value }
    }
}

/**
 * 物品子类型枚举（仅对装备类型有效）
 */
enum class ItemSubType(val value: Int) {
    WEAPON(1),
    ARMOR(2),
    JEWELRY(3);

    companion object {
        fun fromValue(value: Int): ItemSubType = entries.first { it.value == value }
    }
}

/**
 * 物品品质枚举
 */
enum class ItemRarity(val value: Int) {
    WHITE(1),
    GREEN(2),
    BLUE(3),
    PURPLE(4),
    ORANGE(5);

    companion object {
        fun fromValue(value: Int): ItemRarity = entries.first { it.value == value }
    }
}

/**
 * 背包格子类型枚举
 */
enum class SlotType(val value: Int) {
    INVENTORY(1),
    WAREHOUSE(2),
    MAIL(3);

    companion object {
        fun fromValue(value: Int): SlotType = entries.first { it.value == value }
    }
}

/**
 * 装备位枚举
 */
enum class EquipmentSlot(val value: String) {
    HEAD("slot_head"),
    CHEST("slot_chest"),
    WEAPON("slot_weapon"),
    OFFHAND("slot_offhand"),
    RING("slot_ring"),
    AMULET("slot_amulet"),
    BOOTS("slot_boots"),
    GLOVES("slot_gloves");

    companion object {
        fun fromValue(value: String): EquipmentSlot = entries.first { it.value == value }
    }
}