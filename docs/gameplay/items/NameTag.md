# Name Tag

A **Name Tag** gives a living entity a custom name. Rename the tag in an [Anvil](../mechanics/AnvilMechanics.md#naming-and-prior-work), then use it on the target. Naming a mob also protects it from ordinary distance-based despawning. The item ID is `minecraft:name_tag`; ordinary tags stack to **64**, with matching names and other item data required to combine stacks. [Naming][naming] · [Registration][registration] · [Default components][defaults] · [Stack matching][stack-matching]

## Obtaining

There is **no bundled crafting recipe** for Name Tags. The Survival routes in this source snapshot are chest loot, fishing, and librarian trading. [Bundled recipes][recipes]

### Chest loot

These are calculated chances for the **default bundled loot tables** of **at least one Name Tag per container**, not chances that a structure generates. Each successful Name Tag roll produces one tag; the total can be zero.

| Container | Possible total | Chance of at least one |
| --- | --- | --- |
| Abandoned mineshaft chest minecart | 0–1 | **42.3%** |
| Monster-room dungeon chest | 0–3 | **25.3%** |
| Woodland mansion loot chest | 0–3 | **28.3%** |
| Ancient city main-loot chest | 0–10 | **16.1%** |

The mineshaft table has one roll with Name Tag weight 30 out of 71. The dungeon and mansion tables have 1–3 rolls, with weights 20/144 and 20/127 respectively. The ancient city table has 5–10 rolls at weight 2/86. Ancient city ice-box chests use a different table and are not included. The optional built-in **Trade Rebalance** data pack replaces the mineshaft and ancient-city tables, but retains the same Name Tag chances and counts shown above. [Mineshaft table][mineshaft-loot] · [Dungeon table][dungeon-loot] · [Mansion table][mansion-loot] · [City table][city-loot] · [Ice-box table][icebox-loot] · [Rebalanced mineshaft][rebalance-mine] · [Rebalanced city][rebalance-city] · [Optional pack feature][rebalance-pack]

These tables are connected to chest-minecart generation, dungeon and mansion chest placement, and ancient-city structure templates. [Mineshaft placement][mineshaft-placement] · [Dungeon placement][dungeon-placement] · [Mansion placement][mansion-placement] · [City template pool][city-pool] · [City chest template][city-template]

### Fishing and trading

- **Fishing:** Name Tags are one of six equally weighted treasure results. At zero combined fishing luck, an eligible catch has a **5% treasure chance**, giving a **1/120 chance, about 0.83%, of one Name Tag**. Treasure requires the bobber's open-water condition; Luck of the Sea and the player's Luck change the category weights. See [Fishing](../mechanics/Fishing.md#open-water-for-treasure) for the pool requirements. [Fishing table][fishing-loot] · [Treasure table][treasure-loot] · [Active catch handling][fishing-handler]
- **Trading:** A **Master, level-5 librarian** sells **one Name Tag for a base price of 20 Emeralds**, with **12 uses before restocking**. Demand and player-specific price adjustments can change the displayed price. The offer exists in both ordinary trades and trades with the optional Trade Rebalance feature enabled. [Ordinary offer][librarian] · [Rebalanced offer][rebalance-librarian] · [Offer quantities and uses][trade-details] · [Trade selection][trade-selection] · [Price adjustment][trade-price]

Creative players can take Name Tags from the Creative inventory. See the [inventory browser's mode and permission limits](../mechanics/InventoryBrowser.md#mode-and-permission-limits) for acquisition through that interface. [Creative entry][creative]

## Usage

1. Put the Name Tag or stack of tags in the **left Anvil slot**, leaving the right slot empty
2. Enter the desired name and take the output
3. Hold a renamed tag and use it on a living target within interaction range

A fresh tag or ordinary stack with no prior-work penalty costs **1 experience level** to rename. The whole stack receives the name for that one operation. A tag with prior anvil work can cost more; MattMC caps the completed operation at **40 levels**. Creative waives the experience charge. See [Anvil naming and prior work](../mechanics/AnvilMechanics.md#naming-and-prior-work) for the full rules. [Anvil result and naming][anvil] · [Anvil payment][anvil-payment] · [40-level cap][anvil-cap]

An unrenamed tag does not name anything. Clearing a tag's custom name in the Anvil makes it unusable for naming again until another name is applied. Applying a new tag replaces an entity's previous name; even applying the same name again uses a tag in Survival. [Name check and application][naming] · [Clearing an item name][anvil]

## Behavior

A successful server-side use consumes **one tag in Survival or Adventure** and sets the target's custom name. **Creative keeps the tag** through the player's interaction handling. Spectators cannot use this naming interaction. For mobs, the shared Name Tag check runs **before** their normal mob-specific use actions, such as trading or owner controls; sneaking is not required by this check. [Mob interaction priority][mob-interaction] · [Player modes and consumption][player-interaction] · [Server interaction checks][server-interaction]

The item requires a **living entity whose type can be saved**, and the target must still be alive when the server applies the name. This includes ordinary Armor Stands, whose equipment interaction explicitly passes Name Tags through. It excludes players, ordinary non-living objects such as boats and minecarts, and normal direct use on the Ender Dragon: its selectable body parts are non-living entities, while the dragon itself is not selectable. [Naming eligibility][naming] · [Armor Stand interaction][armor-stand] · [Player type][player-type] · [Dragon targeting][dragon] · [Dragon parts][dragon-part]

For mobs, naming also sets a saved persistence flag that bypasses the usual distance/inactivity despawn checks. **It does not make a mob invulnerable or permanent in every circumstance:** removal on Peaceful still runs first, and a Wandering Trader still has its separate despawn timer. Custom names and the mob persistence flag are saved with entity data. [Despawn checks][despawn] · [Trader timer][trader-timer] · [Name saving][name-save] · [Name loading][name-load] · [Persistence saving and loading][persistence-save]

### Special names

The following exact, case-sensitive names have source-wired effects:

| Name | Target and effect |
| --- | --- |
| `Dinnerbone` or `Grumm` | The shared living-entity renderer turns the model upside down. This is a visual effect; particular renderers or poses can have their own rotation rules. [Name check][upside-down-name] · [Rotation][upside-down-rotation] · [Active pose and texture][native-pose] · [Native submission][native-submission] · [Copied transform][native-transport] |
| `jeb_` | A Sheep's wool cycles through colors visually. Its stored wool color, including the color selected by shearing loot, is unchanged. [Sheep render state][sheep-renderer] · [Wool color][sheep-color] · [Shearing table][shearing-loot] · [Native wool-color submission][native-wool] |
| `Toast` | A Rabbit uses the special Toast texture. Its underlying rabbit variant is retained. [Rabbit renderer][rabbit-renderer] · [Native Toast admission][native-rabbit] |
| `jeb_` | For a [Raccoon](../mobs/Raccoon.md) that already has a colored bandana, the Java layer selects cycling bandana colors; naming supplies no bandana. **Visible output is not established on the current native path:** the raccoon shares the conditional Citadel model-transport limitation in [#803](https://github.com/HungLo2020/MattMC/issues/803). [Bandana selection][raccoon-renderer] · [Raccoon model][raccoon-model] · [Model inheritance][raccoon-inheritance] · [Native root][raccoon-root] · [Native admission][raccoon-admission] · [Native extraction][raccoon-extraction] · [Part traversal][raccoon-traversal] |
| `Johnny` | A Vindicator gains a target-selection goal for other attackable living entities. Normal combat, alliance, visibility, and range checks still apply. **Giving it another name does not turn this flag off.** [Johnny flag][johnny-flag] · [Johnny targeting][johnny-goal] · [Target filters][target-filters] |

The Raccoon limitation above is a source-identified case for a visible body admitted to native rendering with a readable texture and valid transform. It has not been reproduced in-game as a crash or invisibility report.

## Notes

Source-reviewed on **2026-10-03** against MattMC commit `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. No in-game acquisition, naming, persistence, or visual tests were run. Chest percentages are calculated from the bundled tables and rounded to one decimal place; custom data packs can change recipes and loot, and modified item components can change the ordinary item behavior described here.

Related pages: [Items](Items.md), [Anvil mechanics](../mechanics/AnvilMechanics.md), [Fishing](../mechanics/Fishing.md), and [Mobs](../mobs/Mobs.md).

[naming]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/NameTagItem.java#L16-L32
[registration]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L2141
[defaults]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
[stack-matching]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/ItemStack.java#L666-L672
[recipes]: https://github.com/HungLo2020/MattMC/tree/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe
[mineshaft-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/chests/abandoned_mineshaft.json#L1-L43
[dungeon-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/chests/simple_dungeon.json#L1-L90
[mansion-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/chests/woodland_mansion.json#L1-L69
[city-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/chests/ancient_city.json
[icebox-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/chests/ancient_city_ice_box.json
[rebalance-mine]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/datapacks/trade_rebalance/data/minecraft/loot_table/chests/abandoned_mineshaft.json#L1-L43
[rebalance-city]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/datapacks/trade_rebalance/data/minecraft/loot_table/chests/ancient_city.json
[mineshaft-placement]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/levelgen/structure/structures/MineshaftPieces.java#L340-L401
[dungeon-placement]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/levelgen/feature/MonsterRoomFeature.java#L100-L109
[mansion-placement]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/levelgen/structure/structures/WoodlandMansionPieces.java#L1218-L1231
[city-pool]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/worldgen/template_pool/ancient_city/structures.json#L9-L17
[city-template]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/structure/ancient_city/structures/barracks.nbt
[fishing-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/gameplay/fishing.json
[treasure-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/gameplay/fishing/treasure.json
[fishing-handler]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/projectile/FishingHook.java#L434-L468
[librarian]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L299-L339
[rebalance-librarian]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L831-L864
[trade-details]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1434-L1478
[trade-selection]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/npc/Villager.java#L819-L841
[trade-price]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/trading/MerchantOffer.java#L89-L96
[creative]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1455-L1464
[anvil]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L119-L276
[anvil-payment]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L71-L116
[mob-interaction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1053-L1088
[player-interaction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/player/Player.java#L854-L892
[server-interaction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1703-L1741
[armor-stand]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/decoration/ArmorStand.java#L184-L191
[player-type]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L1596-L1606
[dragon]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/boss/enderdragon/EnderDragon.java#L734-L737
[dragon-part]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/boss/EnderDragonPart.java#L18-L46
[despawn]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L607-L633
[trader-timer]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/npc/WanderingTrader.java#L209-L221
[name-save]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L1933-L1936
[name-load]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L2019-L2020
[persistence-save]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L350-L383
[upside-down-name]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java#L1209-L1216
[upside-down-rotation]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java#L1132-L1162
[sheep-renderer]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/entity/SheepRenderer.java#L31-L38
[sheep-color]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/entity/state/SheepRenderState.java#L13-L18
[shearing-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/shearing/sheep.json
[rabbit-renderer]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/entity/RabbitRenderer.java#L26-L52
[raccoon-renderer]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/client/render/RenderRaccoon.java#L36-L101
[johnny-flag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/monster/Vindicator.java#L144-L150
[johnny-goal]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/monster/Vindicator.java#L209-L223
[target-filters]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/targeting/TargetingConditions.java#L65-L96
[rebalance-pack]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/datapacks/trade_rebalance/pack.mcmeta#L1-L14
[raccoon-model]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/client/model/ModelRaccoon.java#L17-L39
[raccoon-inheritance]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/citadel/client/model/AdvancedEntityModel.java#L17-L26
[raccoon-root]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/citadel/client/model/basic/BasicEntityModel.java#L13-L40
[raccoon-admission]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java#L283-L302
[raccoon-extraction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L9705-L9728
[raccoon-traversal]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/model/geom/ModelPart.java#L151-L169
[anvil-cap]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L24-L43
[native-pose]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java#L222-L237
[native-submission]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java#L1009-L1018
[native-transport]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L9745-L9769
[native-wool]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/entity/layers/SheepWoolLayer.java#L46-L63
[native-rabbit]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L7763-L7799
