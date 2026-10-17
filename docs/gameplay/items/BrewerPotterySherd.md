# Brewer Pottery Sherd

The **Brewer Pottery Sherd** is a desert-well archaeology find used to give one side of a [Decorated Pot](../blocks/DecoratedPot.md#crafting-and-choosing-faces) the brewer pattern. Bring a working [Brush](Brush.md#archaeology-basics) and excavate carefully: ordinary mining does not release the sherd hidden in Suspicious Sand. [Well assignment][well-positions] · [Loot table][well-table] · [Pattern map][patterns] · [Suspicious Sand loot][sand-loot]

## Obtaining

### Search desert wells

Look for a well in the [Desert biome](../biomes/DesertsBadlandsAndSavannas.md#desert). The active well generator places its archaeology blocks below the five-block water cross: one placement is **one block below** a randomly chosen water position, and another is **two blocks below** an independently chosen position. Both choices can use the same column, but their different depths keep the positions distinct. These generation steps do not guarantee two surviving finds, or a Brewer sherd, in every well you encounter. The biome guide owns well rarity and terrain restrictions. [Water and sand layers][well-feature] · [Selected positions and loot assignment][well-positions]

Each successfully brushed block with the bundled desert-well table makes **one item roll**. Brewer has weight 2 out of 8, a **25% table chance**. [Arms Up Pottery Sherd](ArmsUpPotterySherd.md) has the other weight-2 entry; Brick, Emerald, Stick and Suspicious Stew each have weight 1 (12.5%). These are per-roll source probabilities, not measured find rates or a promise of a sherd from a well. [Well table][well-table] · [Default weights][weights] · [Weighted roll][roll]

A [Desert Pyramid](../structures/DesertPyramid.md) uses a different archaeology table: its sherd entries are Archer, Miner, Prize and Skull. That table does not contain Brewer or Arms Up. Being in the same biome does not make pyramid Suspicious Sand a substitute for the well route. [Pyramid archaeology table][pyramid-table]

### Brush without losing the find

1. Inspect the Sand beneath the water before removing it. Expose a suspicious block's top or side while keeping the block directly underneath intact; **water is not support**. Follow the [fragile-block handling guide](../blocks/SoilSandAndGravel.md#suspicious-sand-and-suspicious-gravel). [Well layers][well-feature] · [Support test][support] · [Suspicious-block falling][fall]
2. Aim at the exposed suspicious block and hold use with a serviceable Brush. Its targeting ignores fluids, so water above an otherwise reachable block does not itself block brushing. Keep a clear line to the block and stay in reach. The [Brush guide](Brush.md#archaeology-basics) covers the ten successful pulses and progress decay if you stop. [Brush targeting][brush] · [Fluid handling][water-target] · [Progress][progress] · [Decay][decay]
3. Finish an upper suspicious block before excavating a lower one in the same column. Successful brushing releases its assigned item and leaves ordinary Sand, which can then be removed to expose the lower layer. Preserve support beneath that lower find too. Do not try to collect Suspicious Sand by mining it or letting it fall. [Completion][brushed-loot] · [Sand replacement][sand-registration] · [Falling behavior][fall] · [Block loot][sand-loot]

### Creative access

This sherd appears in the **Ingredients** creative category, which feeds MattMC's [Inventory item browser](../mechanics/InventoryBrowser.md#which-items-appear). It is an ordinary category entry, with no operator-only listing gate. You can browse it in Survival, but ordinary Survival cannot obtain it by clicking the browser: insertion requires the server player's infinite-materials ability, normally Creative. Follow the browser guide for search, cursor and inventory-space requirements. [Category][category] · [Entry][creative] · [Browser assembly][browser] · [Packet gate][browser-gate] · [Server ability][server-ability] · [Ability value][ability] · [Game modes][mode]

## Usage

Use a 3 × 3 Crafting Table grid with **four loose Bricks and/or accepted Pottery Sherds in a cross**, leaving the center and all corners empty. One Brewer sherd supplies one brewer face; fill the other three positions with Bricks for plain faces, [other sherds with mapped patterns](../blocks/DecoratedPot.md#accepted-sherds-and-current-patterns) for mixed designs, or more Brewer sherds to repeat it. Four copies decorate all four faces. The result is one pot. [Active recipe][recipe-data] · [Recipe checks][recipe] · [Ingredients][ingredients] · [Accepted sherds][sherds]

The recipe assigns **top=back, middle-left=left, middle-right=right and bottom=front**. The registered mapping for this sherd is `minecraft:brewer`; its identity is kept with that face in the pot's decorations. See [choosing pot faces](../blocks/DecoratedPot.md#crafting-and-choosing-faces) for the placement-facing explanation. This is source-checked mapping, not an in-game image comparison. [Face selection][recipe] · [Stored identities][stored-faces] · [Named pattern][patterns]

## Behavior

Pottery sherds are crafting ingredients. A sherd sets a face when the pot is crafted; using it on an already placed pot can insert it into the pot's item storage if that stack is accepted and has room. It does not change the decoration. Recover the face ingredients and craft again to rearrange a design. [Crafting][recipe] · [Placed-pot interaction][insert]

To recover a Brewer sherd already used as a face, **shatter the pot in Survival with a tagged breaking tool without Silk Touch**, such as an ordinary pickaxe. The current breaking tag includes swords, axes, pickaxes, shovels, hoes, Trident and Mace; Silk Touch is the current shattering-protection enchantment. The cracked-pot loot returns all four face ingredients, including one Brewer sherd for each Brewer face and one Brick for each Brick face. [Break decision and ingredient drops][shatter] · [Breaking items][break-tools] · [Protection][silk] · [Loot branches][pot-loot]

Breaking by empty hand keeps the decorated pot item and its face choices instead. Any items stored inside spill separately; they are separate from the four face ingredients. Use the [Decorated Pot breaking guide](../blocks/DecoratedPot.md#breaking-keep-the-pot-or-recover-ingredients) for the full recovery options. [Intact-pot loot][pot-loot] · [Storage spill][spill]

## Notes

- This item is registered as `minecraft:brewer_pottery_sherd` with **Uncommon** item rarity; that label is separate from its archaeology-table chance. [Registration][items]
- It appears in the Ingredients creative category. Browser visibility and permission to insert items are separate, as explained above
- Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked the well feature and assigned loot, brushing/support rules, pot recipe/pattern/recovery paths and current browser category/permission chain. No in-game generation, excavation, crafting, rendering, breaking or browser test was run. Data packs and server changes can alter these defaults

Related: [Arms Up Pottery Sherd](ArmsUpPotterySherd.md) · [Brush](Brush.md) · [Desert](../biomes/DesertsBadlandsAndSavannas.md#desert) · [Decorated Pot item](DecoratedPot.md) · [Items](Items.md)

[well-feature]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/DesertWellFeature.java#L57-L68
[well-positions]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/DesertWellFeature.java#L100-L112
[well-table]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/archaeology/desert_well.json
[pyramid-table]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/archaeology/desert_pyramid.json
[weights]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/entries/LootPoolSingletonContainer.java#L45-L55
[roll]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/LootPool.java#L60-L101
[brush]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BrushItem.java#L37-L98
[progress]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/BrushableBlockEntity.java#L60-L87
[brushed-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/BrushableBlockEntity.java#L89-L147
[decay]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/BrushableBlockEntity.java#L149-L169
[water-target]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/ProjectileUtil.java#L38-L57
[fall]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/BrushableBlock.java#L82-L99
[support]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FallingBlock.java#L63-L65
[sand-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/suspicious_sand.json
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/DecoratedPotRecipe.java#L15-L46
[recipe-data]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/decorated_pot.json
[ingredients]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/decorated_pot_ingredients.json
[sherds]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/decorated_pot_sherds.json
[patterns]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/DecoratedPotPatterns.java#L37-L85
[stored-faces]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/PotDecorations.java#L25-L49
[insert]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/DecoratedPotBlock.java#L95-L145
[shatter]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/DecoratedPotBlock.java#L173-L197
[break-tools]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/breaks_decorated_pots.json
[silk]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/enchantment/prevents_decorated_pot_shattering.json
[pot-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/decorated_pot.json
[spill]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/BlockEntity.java#L250-L254
[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2585-L2591
[category]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1775-L1783
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1906-L1911
[browser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[browser-gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/network/protocol/game/GameProtocols.java#L40-L58
[server-ability]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2320-L2323
[ability]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L1212-L1215
[mode]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/GameType.java#L62-L79
[sand-registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L318-L327
