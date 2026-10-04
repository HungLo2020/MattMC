# Archer Pottery Sherd

The **Archer Pottery Sherd** is a [Desert Pyramid](../structures/DesertPyramid.md#archaeology-cellar) archaeology reward that gives one face of a [Decorated Pot](../blocks/DecoratedPot.md) the archer pattern. Each copy supplies one decorated face, so save additional copies if you want the same design on several sides. [Archaeology reward][pyramid-table] · [Pattern mapping][patterns] · [Face ingredients][recipe]

## Obtaining

### Search the archaeology cellar

Look for the sand-covered staircase near a corner of a Desert Pyramid's upper floor. It leads into the archaeology cellar and its collapsed sandy roof. The active placement pass assigns the pyramid archaeology table to selected Suspicious Sand positions there, after building the piece. Follow the [pyramid expedition guide](../structures/DesertPyramid.md#where-to-search) for finding the structure and avoiding its separate, deep TNT chamber. [Cellar entrance][pyramid-piece] · [Suspicious Sand placement][placement] · [Placement order][dispatch]

A block assigned the bundled pyramid archaeology table makes **one roll among eight equally weighted items**. Archer has weight 1 of 8: **12.5% per table roll**, yielding one Archer Pottery Sherd when selected. The other possible results are the other three pyramid sherds, Diamond, TNT, Gunpowder and Emerald. These are source-table probabilities, not a guarantee of this design from a pyramid or a measured excavation yield. The pyramid's separate chest table does not contain this sherd. [Archaeology table][pyramid-table] · [Default weights][weights] · [Weighted selection][roll] · [Chest table][chest-table]

### Excavate without losing the find

Keep each suspicious block supported, expose its top or side, then **aim at it and hold use with a serviceable Brush**. Finish exposed upper layers before removing their supports to reach lower ones. Ordinary mining, Silk Touch and allowing Suspicious Sand to fall do not provide the brushing reward; use the [suspicious-block handling guide](../blocks/SoilSandAndGravel.md#suspicious-sand-and-suspicious-gravel). A fully broken Brush cannot start this block-use route. [Brushing controls][brush] · [Broken-item guard][broken] · [Falling behavior][fall] · [Empty block-loot table][sand-loot]

Successful completion releases the stored item and leaves ordinary Sand. The loot assignment is resolved once on the first successful brush pulse, so stopping and resuming does not reroll it. See [Brush archaeology basics](Brush.md#archaeology-basics) for progress, timing and interruptions. [Loot resolution and completion][stored-loot] · [Sand replacement][sand-registration]

### Creative access

This item appears in the **Ingredients** creative category. Follow the [Inventory item browser's mode and permission limits](../mechanics/InventoryBrowser.md#mode-and-permission-limits): seeing the catalog in ordinary Survival does not allow server-accepted item insertion, including in integrated single-player. [Creative entry][creative] · [Server packet gate][browser-gate]

## Usage

Use this sherd in the [Decorated Pot's four-ingredient crafting recipe](../blocks/DecoratedPot.md#crafting-and-choosing-faces). One Archer sherd plus three loose [Bricks](Brick.md) makes one pot with one archer face and three plain faces. Repeat the sherd for more archer faces, or combine it with [other mapped sherds](../blocks/DecoratedPot.md#accepted-sherds-and-current-patterns). The full grid and face orientation belong to the pot guide. [Active recipe][recipe-data] · [Recipe checks][recipe] · [Ingredient tag][ingredients] · [Accepted sherds][sherds]

This sherd is mapped to **`minecraft:archer`**. It is an accepted ingredient with a named pattern, rather than an accepted but unmapped sherd that uses the plain-face fallback. The mapping is source-reviewed; no artwork comparison was performed in game. [Pattern map][patterns]

## Behavior

Pottery sherds are crafting ingredients. Using one on an already placed pot can insert it into the storage slot when compatible space is available; it does **not** repaint a face. To change a design, recover the face ingredients and craft the desired arrangement again. [Placed-pot interaction][insert] · [Crafted faces][recipe]

For normal Survival recovery with block drops enabled, **shatter the pot with a tagged breaking tool, such as a pickaxe without Silk Touch**. This returns one Archer sherd for each Archer face, along with the other face ingredients. Empty-hand breaking keeps the decorated pot item instead. Stored items spill separately in either ordinary outcome. See [pot breaking and recovery](../blocks/DecoratedPot.md#breaking-keep-the-pot-or-recover-ingredients) for the complete rules. [Shatter decision and ingredient drops][shatter] · [Breaking tag][break-tools] · [Silk Touch protection][silk] · [Loot branches][pot-loot] · [Storage spill][spill]

## Notes

- Registered as `minecraft:archer_pottery_sherd` with **Uncommon** item rarity; that label is separate from its 12.5% archaeology-table chance. [Registration][item]
- It appears in the Ingredients creative category; visibility and permission to obtain browser items are separate
- Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked the active pyramid placement and assigned loot, Brush controls, pot recipe/pattern/recovery and browser permission chain. No in-game generation, excavation, crafting, rendering, breaking or browser test was run. Data packs and server changes can alter these defaults

Related: [Miner Pottery Sherd](MinerPotterySherd.md) · [Prize Pottery Sherd](PrizePotterySherd.md) · [Skull Pottery Sherd](SkullPotterySherd.md) · [Brush](Brush.md) · [Decorated Pot item](DecoratedPot.md) · [Items](Items.md)

[pyramid-table]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/archaeology/desert_pyramid.json#L1-L44
[pyramid-piece]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/DesertPyramidPiece.java#L311-L343
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/DesertPyramidStructure.java#L25-L71
[dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/StructureStart.java#L81-L102
[chest-table]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/desert_pyramid.json
[weights]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/entries/LootPoolSingletonContainer.java#L45-L55
[roll]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/LootPool.java#L60-L101
[brush]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BrushItem.java#L37-L98
[broken]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L370
[stored-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/BrushableBlockEntity.java#L60-L147
[fall]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/BrushableBlock.java#L82-L99
[sand-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/suspicious_sand.json
[sand-registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L318-L329
[recipe-data]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/decorated_pot.json
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/DecoratedPotRecipe.java#L15-L46
[ingredients]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/decorated_pot_ingredients.json
[sherds]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/decorated_pot_sherds.json
[patterns]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/DecoratedPotPatterns.java#L13-L101
[insert]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/DecoratedPotBlock.java#L95-L145
[shatter]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/DecoratedPotBlock.java#L173-L197
[break-tools]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/breaks_decorated_pots.json
[silk]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/enchantment/prevents_decorated_pot_shattering.json
[pot-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/decorated_pot.json
[spill]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/BlockEntity.java#L250-L254
[browser-gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/network/protocol/game/GameProtocols.java#L40-L58
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2586
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1907
