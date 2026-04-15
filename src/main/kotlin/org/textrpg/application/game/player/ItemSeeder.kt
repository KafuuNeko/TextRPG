package org.textrpg.application.game.player

import org.textrpg.application.data.repository.ItemRepository
import org.textrpg.application.data.repository.ItemTemplate
import org.textrpg.application.domain.model.ItemRarity
import org.textrpg.application.domain.model.ItemSubType
import org.textrpg.application.domain.model.ItemType

/**
 * 范例层物品模板播种器
 *
 * 在首次启动时向数据库写入预定义的物品模板。
 * 幂等设计：已存在模板时跳过，不重复插入。
 */
object ItemSeeder {

    /** 预定义的物品模板列表 */
    private val templates = listOf(
        // ===== 武器 =====
        ItemTemplate(
            name = "木剑",
            type = ItemType.EQUIPMENT,
            subType = ItemSubType.WEAPON,
            rarity = ItemRarity.WHITE,
            stackable = false,
            baseStats = """{"physical_attack": 5}""",
            price = 20,
            description = "新手冒险者的第一把剑，虽然简陋但聊胜于无。"
        ),
        ItemTemplate(
            name = "铁剑",
            type = ItemType.EQUIPMENT,
            subType = ItemSubType.WEAPON,
            rarity = ItemRarity.GREEN,
            stackable = false,
            baseStats = """{"physical_attack": 15, "strength": 3}""",
            price = 150,
            description = "结实的铁制长剑，铁匠的标准作品。"
        ),
        ItemTemplate(
            name = "精铁剑",
            type = ItemType.EQUIPMENT,
            subType = ItemSubType.WEAPON,
            rarity = ItemRarity.BLUE,
            stackable = false,
            baseStats = """{"physical_attack": 25, "strength": 5}""",
            levelReq = 3,
            price = 500,
            description = "精心锻造的铁剑，散发着淡淡的寒光。"
        ),

        // ===== 防具 =====
        ItemTemplate(
            name = "皮甲",
            type = ItemType.EQUIPMENT,
            subType = ItemSubType.ARMOR,
            rarity = ItemRarity.WHITE,
            stackable = false,
            baseStats = """{"defense": 5}""",
            price = 30,
            description = "简单的皮制护甲，提供基本的防护。"
        ),
        ItemTemplate(
            name = "铁甲",
            type = ItemType.EQUIPMENT,
            subType = ItemSubType.ARMOR,
            rarity = ItemRarity.GREEN,
            stackable = false,
            baseStats = """{"defense": 12, "constitution": 2}""",
            price = 200,
            description = "坚固的铁制铠甲，能抵御大部分攻击。"
        ),

        // ===== 消耗品 =====
        ItemTemplate(
            name = "治疗药水",
            type = ItemType.CONSUMABLE,
            stackable = true,
            price = 25,
            description = "恢复 50 点生命值。"
        ),
        ItemTemplate(
            name = "魔力药水",
            type = ItemType.CONSUMABLE,
            stackable = true,
            price = 30,
            description = "恢复 30 点魔力值。"
        ),

        // ===== 材料 =====
        ItemTemplate(
            name = "史莱姆凝胶",
            type = ItemType.MATERIAL,
            stackable = true,
            price = 5,
            description = "从史莱姆身上获得的黏糊糊的凝胶。"
        ),
        ItemTemplate(
            name = "哥布林之耳",
            type = ItemType.MATERIAL,
            stackable = true,
            price = 10,
            description = "哥布林的耳朵，可作为讨伐证明。"
        ),
        ItemTemplate(
            name = "狼皮",
            type = ItemType.MATERIAL,
            stackable = true,
            price = 15,
            description = "野狼的毛皮，可用于制作皮甲。"
        ),

        // ===== 任务物品 =====
        ItemTemplate(
            name = "野草药",
            type = ItemType.QUEST,
            stackable = true,
            price = 0,
            description = "药师需要的野生草药。"
        )
    )

    /**
     * 执行播种
     *
     * 检查数据库是否已有物品模板，如果为空则插入预定义模板。
     * 如果已存在则跳过，保证幂等。
     *
     * @param itemRepository 物品仓储
     * @return 插入的模板数量（0 表示已跳过）
     */
    fun seed(itemRepository: ItemRepository): Int {
        val existing = itemRepository.findAllTemplates()
        if (existing.isNotEmpty()) {
            return 0
        }

        var count = 0
        for (template in templates) {
            itemRepository.saveTemplate(template)
            count++
        }
        return count
    }

    /**
     * 按名称查找模板 ID
     *
     * 在播种完成后使用，用于注册 handler 时按名称查找模板。
     *
     * @param name 物品名称
     * @param itemRepository 物品仓储
     * @return 模板 ID，未找到返回 null
     */
    fun findTemplateIdByName(name: String, itemRepository: ItemRepository): Int? {
        return itemRepository.findAllTemplates().find { it.name == name }?.id
    }
}
