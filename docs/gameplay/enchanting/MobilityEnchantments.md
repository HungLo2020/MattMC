# Depth Strider, Frost Walker, Soul Speed and Swift Sneak

Choose **Depth Strider** for movement through water, **Frost Walker** for temporary footing over eligible water, **Soul Speed** for Soul Sand and Soul Soil routes, and **Swift Sneak** for faster crouching or crawling. Depth Strider and Frost Walker exclude each other; Soul Speed can accompany either boot choice, while Swift Sneak belongs on Leggings. [Depth Strider definition] · [Frost Walker definition] · [Soul Speed definition] · [Swift Sneak definition] · [Boot exclusions]

## Levels, equipment and compatibility

| Enchantment and exact ID | Normal levels | Supported equipment | Active slot | Enchanting Table pool |
| --- | --- | --- | --- | --- |
| Depth Strider (`minecraft:depth_strider`) | I–III | Boots | Feet | Yes |
| Frost Walker (`minecraft:frost_walker`) | I–II | Boots | Feet | No |
| Soul Speed (`minecraft:soul_speed`) | I–III | Boots | Feet | No |
| Swift Sneak (`minecraft:swift_sneak`) | I–III | Leggings | Legs | No |

Here **Boots** and **Leggings** each mean the seven tagged material variants: **Leather, Copper, Chainmail, Gold, Iron, Diamond and Netherite**. Imported armor is not automatically included. All four definitions omit a separate primary-item list, so their primary equipment set is the supported set; that does not put a treasure enchantment into the table pool. [Foot support] · [Boot items] · [Leg support] · [Legging items] · [Primary-item rule] · [Table pool] · [Non-treasure members] · [Live table pool]

Wear the item in its matching slot. A book or armor piece carried in ordinary inventory does not supply these equipment effects. Use a suitable [Enchanted Book](../items/EnchantedBook.md) and [Anvil](../blocks/Anvil.md) to apply an acquired enchantment. The Anvil checks supported items and compatibility; the two-boot exclusion also applies to table selection, including Books. See [Enchanting](Enchanting.md) for offers and costs, and [Armor](../mechanics/Armor.md) for material defenses. [Slot iteration] · [Anvil application] · [Compatibility rule] · [Table selection]

## Depth Strider: water movement

Depth Strider adds approximately **L / 3** to water-movement efficiency at level L: **1/3, 2/3, or 1**. The actual data uses 0.33333334 per level, and the attribute is capped at 1. The water-travel calculation **halves that efficiency while off the ground**, so level III contributes 0.5 in that condition. It does not require a source-water block; it applies in the water-travel branch. [Depth Strider definition] · [Attribute limits] · [Water movement] · [Travel selection]

The efficiency blends two movement terms: horizontal slowdown moves toward **0.54600006**, and input acceleration moves from **0.02 toward the player's movement-speed attribute**. These are calculation terms, not a fixed blocks-per-second promise. Sprinting, contact, other attributes, input and fluid motion still matter. [Water movement] · [Player speed]

[Dolphin's Grace](../effects/MovementEffects.md#dolphins-grace) then replaces the horizontal slowdown term with **0.96**, while the Depth Strider acceleration calculation remains. Depth Strider does not supply breathing protection or the underwater-mining bonus: use [Water Breathing and Conduit Power](../effects/WaterAndFireEffects.md) and the [mining guide](../mechanics/Mining.md) for those separate needs. [Water movement] · [Air handling]

## Frost Walker: temporary water crossings

With Frost Walker Boots equipped, eligible location updates create Frosted Ice **one block below the wearer's block position**, within a horizontal radius of **3 at level I** or **4 at level II**. The wearer must be **on the ground and not riding another entity**. Block centers must fall strictly inside the radius, so this is a disk around the current position, not a guaranteed square of ice. [Frost Walker definition] · [Disk replacement] · [Location dispatch]

Each target needs **air above, an actual Water block containing source-water fluid, and an unobstructed full-block replacement**. Flowing water, waterlogged building blocks and covered water do not meet those checks. The freeze effect has no biome-temperature or light requirement. The ordinary server path checks changes of block position, landing, and equipment changes; simply owning the Boots does not freeze every nearby body of water continuously. [Frost Walker definition] · [Fluid predicate] · [Fluid identities] · [Unobstructed predicate] · [Movement and landing triggers] · [Changed-position trigger] · [Equipment updates]

**Plan a return route.** Frosted Ice is temporary and cannot be collected, even with Silk Touch. Its placement starts a scheduled update after **60–120 game ticks**; later aging, light and neighboring ice determine when it disappears. Taking off the Boots stops future equipment effects but does not remove the ice already placed. The [Ice guide](../blocks/Ice.md#frosted-ice-and-frost-walker) owns the full aging, melting and collection rules. [Frosted Ice scheduling] · [Server timer dispatch] · [Scheduled block callback] · [Location cleanup]

Frost Walker also rejects damage tagged **`burn_from_stepping`**, provided it is not tagged `bypasses_invulnerability`. The bundled stepping tag contains **Campfire and Hot Floor** damage. This protects against the matching [Magma floor](../blocks/SoulSandSoilAndMagma.md#magma-floor-safety) and campfire contacts; it is not general Lava or fire immunity. That separate damage check does not require the water-freezing conditions. [Frost Walker definition] · [Stepping damage tag] · [Immunity dispatch] · [Living immunity call] · [Active damage gate]

## Soul Speed: terrain boost and wear

Soul Speed's terrain tag contains exactly **Soul Sand and Soul Soil**. Its location effect adds movement speed and **+1 movement efficiency** when its conditions match. The efficiency contribution brings the ordinary Soul Sand slowdown factor to 1; [Soul Sand and Soul Soil](../blocks/SoulSandSoilAndMagma.md#walking-and-soul-speed) owns the block comparison. [Soul terrain] · [Soul Speed definition] · [Movement-efficiency consumer]

| Soul Speed level | Added movement-speed attribute |
| --- | ---: |
| I | +0.0405 |
| II | +0.0510 |
| III | +0.0615 |

The formula is **0.0405 + 0.0105 × (L − 1)**. For the ordinary player's base movement-speed attribute of 0.1, these are additions before other modifiers and movement processing, not measured travel-speed percentages. [Soul Speed definition] · [Player attributes] · [Player speed] · [Ground movement] · [Ground acceleration]

The initial boost requires the movement-affecting block below the wearer to be in the terrain tag, **no riding**, and **no Creative flight or Elytra gliding**. Once active, it may remain through an airborne jump even when the block below is no longer tagged; the next applicable location check removes it when its conditions fail, such as landing on other ground. The terrain predicate samples the movement-affecting block, not every block touching the entity. [Soul Speed definition] · [Movement-block predicate] · [Movement-block position] · [Flight predicate] · [Location activation]

A separate effect has a **4% chance per qualifying location-effect evaluation** to request **1 point of boot damage** while on the ground with tagged terrain below. This is not a 4%-per-tick or fixed-distance charge, and it is separate from the boost's flying/riding test. It goes through ordinary durability processing, including Unbreaking and the infinite-materials exemption. See [Unbreaking and Mending](DurabilityEnchantments.md) for wear prevention and repairs. [Soul wear condition] · [Wear application] · [Durability processing]

## Swift Sneak: crouching and crawling

Swift Sneak adds **0.15 × L** to the sneaking-speed attribute. Starting from its ordinary **0.3** base gives **0.45 at I, 0.60 at II, and 0.75 at III**. The local player's active movement-input path uses that factor when crouching **or visually crawling**. The attribute is capped at 1. [Swift Sneak definition] · [Attribute limits] · [Slow-movement input]

For otherwise identical straight-line input, those factors correspond to 45%, 60%, or 75% of the ordinary non-slowed input at that stage. Item-use slowdown, the common input adjustment, diagonal handling, terrain and other effects remain separate. Swift Sneak does not change the main movement-speed attribute or make ordinary standing movement faster. [Slow-movement input]

## Changing equipment and retained broken gear

Removing or replacing equipped armor clears its ordinary attribute modifiers and active location effects. Depth Strider and Swift Sneak are equipment-attribute effects: **their bonuses stop when the item breaks**, and normal equipment updates do not apply them from a retained broken stack. Repairs and enchantment-preserving methods are covered by [Durability and repair](../mechanics/Durability.md); Grindstone removal is separate from taking armor off. [Modifier dispatch] · [Equipment updates] · [Break cleanup]

**Frost Walker and Soul Speed need a different broken-item caveat in this snapshot.** Breakage immediately clears active location effects too, but the later location-change equipment iterator has no broken-item exclusion. An equipped broken pair can therefore trigger Frost Walker again, or reactivate Soul Speed when its location conditions match. Frost Walker's separate damage-immunity iterator also still reads broken equipped Boots. Further wear requests on an already-broken item do nothing. [Retained break] · [Break cleanup] · [Slot iteration] · [Location dispatch] · [Location activation] · [Attribute activation] · [Immunity dispatch] · [Durability processing]

This describes the current source, not a promised passive-effect design. The intended retained-gear contract remains under [#800 review](https://github.com/HungLo2020/MattMC/issues/800#issuecomment-5961208983). Do not infer that every enchantment either always survives or always stops at breakage; use the specific effect and caller.

## Getting the enchantments

The following routes use the **ordinary bundled data**, without the optional Trade Rebalance pack:

| Enchantment | Checked acquisition routes |
| --- | --- |
| Depth Strider | Enchanting Table offers on eligible Boots or Books; ordinary Librarian book offers; the fishing treasure Book's random-enchantment pool |
| Frost Walker | Ordinary Librarian book offers; the fishing treasure Book's random-enchantment pool; absent from the table |
| Soul Speed | Adult Piglin bartering can give a Book or Iron Boots with Soul Speed I–III; the general Bastion chest table can give a Book or Golden Boots with Soul Speed I–III |
| Swift Sneak | The ordinary Ancient City chest table has a Swift Sneak Book entry, choosing I–III |

Depth Strider and Frost Walker are in **`tradeable`** and **`on_random_loot`**; Soul Speed and Swift Sneak are in neither. Actual Librarian offers select from the tradeable pool and are random. Fishing must reach the treasure branch, which requires the bobber's open-water condition, and then select its Book entry and enchantments. A pool membership is not a guarantee from every villager, catch or chest. [Trade pool] · [Random-loot pool] · [Librarian listings] · [Book-offer selection] · [Fishing loot call] · [Fishing branches] · [Treasure Book] · [Level-based loot enchantment]

Piglin bartering loads the stated table through the adult barter-completion path; see [Piglin](../mobs/Piglin.md). The [Bastion guide](../structures/BastionRemnant.md) distinguishes general-table chests from other Bastion rewards. Ancient City's structure pool includes chest-bearing templates using `chests/ancient_city`; their saved loot-table names enter the normal container-loot path. These are possible rewards, not guaranteed books from a structure visit. [Barter completion] · [Barter loot call] · [Random level selection] · [Barter entries] · [Bastion entries] · [Ancient City entry] · [Ancient City structure pool] · [Ancient City chest template] · [Template placement] · [Chest loot]

**MattMC's inventory browser is another route.** The category entries generate a maximum-level Enchanted Book for every registered enchantment, so these four appear as **Depth Strider III, Frost Walker II, Soul Speed III and Swift Sneak III**. The panel uses category display entries, not the separate all-level search-only list. It allows ordinary listed-item insertion in Creative; follow the [inventory browser guide](../mechanics/InventoryBrowser.md) for cursor and inventory-space requirements, then apply the book with an Anvil. [Category books] · [Book enumeration] · [Browser list] · [Browser request] · [Server insertion] · [Server new-item placement]

**Optional Trade Rebalance is separate.** When its feature flag is enabled, Villagers use the experimental trade listings. Its common Librarian pools place Depth Strider in the Swamp set and Frost Walker in the Snow set. Those biome-specific rules are not prerequisites for the ordinary tradeable pool above. [Feature pack] · [Trade switch] · [Experimental book mapping] · [Swamp pool] · [Snow pool]

## Sources and verification

Source-reviewed on **2026-10-03** at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`. Checked the loaded enchantment definitions, expanded support/exclusion/pool tags, equipment effects, player movement-input and water/ground consumers, location and damage dispatch, frost placement and scheduled-block path, wear/break cleanup and the listed acquisition routes. The active consumers for these mechanics are Java at this revision; the movement claims follow the player calls into those consumers. [Registry loading] · [Player travel] · [Slow-movement input]

No game, timed-movement, freezing, damage, durability, fishing, barter, trade or chest-opening test was run. Attribute values and possible loot outcomes are source-derived, not measured performance or drop rates. The Ancient City chest template was decoded from NBT. Data packs, modified item components and later builds can change these results. Existing block, movement-effect, armor and repair guides retain their detailed mechanics.

[Depth Strider definition]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/enchantment/depth_strider.json
[Frost Walker definition]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/enchantment/frost_walker.json
[Soul Speed definition]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/enchantment/soul_speed.json
[Swift Sneak definition]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/enchantment/swift_sneak.json
[Boot exclusions]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/enchantment/exclusive_set/boots.json
[Foot support]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/enchantable/foot_armor.json
[Boot items]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/foot_armor.json
[Leg support]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/enchantable/leg_armor.json
[Legging items]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/leg_armor.json
[Primary-item rule]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L120-L130
[Live table pool]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/inventory/EnchantmentMenu.java#L184-L198
[Table pool]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/enchantment/in_enchanting_table.json
[Non-treasure members]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/enchantment/non_treasure.json
[Slot iteration]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L135-L158
[Anvil application]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L168-L216
[Compatibility rule]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L160-L162
[Table selection]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L542-L600
[Attribute limits]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L63-L96
[Water movement]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2313-L2348
[Travel selection]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2257-L2266
[Player speed]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L1337-L1340
[Air handling]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L424-L442
[Disk replacement]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/effects/ReplaceDisk.java#L43-L60
[Location dispatch]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L251-L274
[Fluid predicate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/levelgen/blockpredicates/MatchingFluidsPredicate.java#L12-L28
[Fluid identities]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/material/Fluids.java#L7-L11
[Unobstructed predicate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/levelgen/blockpredicates/UnobstructedPredicate.java#L12-L24
[Movement and landing triggers]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L351-L359
[Equipment updates]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2650-L2685
[Frosted Ice scheduling]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/FrostedIceBlock.java#L39-L91
[Server timer dispatch]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/level/ServerLevel.java#L354-L362
[Scheduled block callback]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/level/ServerLevel.java#L766-L771
[Location cleanup]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L478-L488
[Stepping damage tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/damage_type/burn_from_stepping.json
[Immunity dispatch]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L159-L170
[Active damage gate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1146-L1153
[Living immunity call]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3699-L3701
[Soul terrain]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/block/soul_speed_blocks.json
[Movement-efficiency consumer]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L495-L498
[Player attributes]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L220-L234
[Ground acceleration]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2502-L2507
[Ground movement]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2458-L2467
[Movement-block predicate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/advancements/critereon/EntityPredicate.java#L128-L133
[Movement-block position]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Entity.java#L946-L970
[Flight predicate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/advancements/critereon/EntityFlagsPredicate.java#L32-L51
[Location activation]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L440-L475
[Soul wear condition]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/enchantment/soul_speed.json#L121-L151
[Wear application]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/effects/ChangeItemDamage.java#L23-L32
[Durability processing]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/ItemStack.java#L457-L475
[Slow-movement input]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/player/LocalPlayer.java#L570-L618
[Modifier dispatch]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L393-L399
[Break cleanup]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3540-L3553
[Retained break]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/ItemStack.java#L449-L486
[Attribute activation]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/effects/EnchantmentAttributeEffect.java#L38-L49
[Trade pool]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/enchantment/tradeable.json
[Random-loot pool]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/enchantment/on_random_loot.json
[Librarian listings]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L300-L340
[Book-offer selection]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1296-L1324
[Fishing loot call]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/FishingHook.java#L435-L467
[Fishing branches]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/gameplay/fishing.json
[Treasure Book]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/gameplay/fishing/treasure.json#L54-L65
[Level-based loot enchantment]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantWithLevelsFunction.java#L56-L61
[Barter completion]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L373-L384
[Barter loot call]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L440-L447
[Random level selection]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantRandomlyFunction.java#L55-L81
[Barter entries]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/gameplay/piglin_bartering.json#L1-L28
[Bastion entries]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/chests/bastion_other.json#L146-L217
[Ancient City entry]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/chests/ancient_city.json#L154-L166
[Ancient City structure pool]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/worldgen/template_pool/ancient_city/structures.json#L9-L17
[Ancient City chest template]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/structure/ancient_city/structures/barracks.nbt
[Template placement]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java#L299-L311
[Chest loot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/RandomizableContainer.java#L50-L88
[Category books]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1953-L1956
[Book enumeration]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2216-L2235
[Browser list]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L90-L107
[Browser request]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[Server new-item placement]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2006-L2020
[Server insertion]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1906
[Feature pack]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/datapacks/trade_rebalance/pack.mcmeta
[Trade switch]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/Villager.java#L819-L836
[Experimental book mapping]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1134-L1145
[Swamp pool]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/datapacks/trade_rebalance/data/minecraft/tags/enchantment/trades/swamp_common.json
[Snow pool]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/datapacks/trade_rebalance/data/minecraft/tags/enchantment/trades/snow_common.json
[Registry loading]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L124-L129
[Player travel]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L1295-L1317
[Changed-position trigger]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L445-L450
