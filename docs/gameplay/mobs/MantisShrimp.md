# Mantis Shrimp

The **Mantis Shrimp** (`minecraft:mantis_shrimp`) is a tameable, fish-fed animal that can follow its owner, carry a sample item and break matching blocks. Keep it supplied with water or rain, and separate it from the animals it hunts. Its registered attributes include **20 health points** and **8 armor points**. [Entity][shrimp-entity] · [Bound attributes][attributes] · [Attributes][shrimp-stats] · [Goals][shrimp-goals]

## Obtaining

Use the [Mantis Shrimp Spawn Egg](../items/MantisShrimpSpawnEgg.md). It is listed in the ordinary [Inventory Browser](../mechanics/InventoryBrowser.md), available in **Creative**. Egg placement creates the actual Mantis Shrimp class. [Egg registration][shrimp-egg] · [Category entry][egg-list] · [Browser list][browser] · [Server access][browser-server] · [Egg use][egg-use]

**A natural spawning route is not established in the reviewed active source.** The class has a sea-level-related static spawn predicate, but that predicate has no active caller or placement registration, and no bundled biome spawn list names this species. `WATER_CREATURE` classification alone does not add it to a biome. Do not use an upstream ocean-biome claim as a finding location in this version. [Species predicates][shrimp-spawn] · [Placement registrations][placements] · [Spawn-list selection][natural-list] · [Bundled biomes][biomes]

There is **no registered Mantis Shrimp bucket item**, and this entity does not implement bucket capture. Its normal offspring path is described below. No species death-loot table or raw/cooked shrimp item was found in the reviewed registrations and bundled loot. Equipment and other generic death handling are separate from a species loot table. [Items][registered-items] · [Interaction path][shrimp-hand] · [Bundled loot][loot] · [Default loot key][loot-key] · [Missing-table fallback][loot-fallback]

## Behavior

### Fish, taming and breeding

The accepted fish tag contains **Raw Cod, Cooked Cod, Raw Salmon, Cooked Salmon, Pufferfish and Tropical Fish**. Holding one can attract the shrimp through its temptation goal. Untamed shrimp consume these fish for taming; tamed shrimp also accept them as breeding/growth food and, while injured, healing food. [Exact fish tag][fish-tag] · [Temptation][shrimp-goals] · [Breeding predicate][shrimp-food] · [Feeding branches][shrimp-feeding]

The custom taming counter does not succeed during the first ten feedings. Starting with feeding 11 it checks a **one-in-six** chance, and a counter above 30 guarantees success in that branch. This counter is not saved, so a reload can reset unfinished progress. These are source conditions, not a tested feeding schedule. [Taming counter][shrimp-feeding] · [Species save fields][shrimp-save]

For tamed shrimp, **breeding or baby growth runs before custom healing**. An eligible adult may consume one fish to enter love mode; a baby may consume one for growth. If fish remain in the same held stack and the shrimp is injured, the custom branch can consume another fish and heal up to **5 health points**. If the first branch used the last fish, that later heal does not run. Do not assume one click always means one fish and one effect. [Shared feeding order][shared-feed] · [Custom feeding][shrimp-feeding] · [Consumption][consume-feed] · [Empty-stack identity][empty-stack]

**Recheck owner controls after feeding the last fish to an eligible adult.** The adult breeding result can fall through to later owner interaction: a normal use can also advance the command, including into Break Blocks, and a sneak-use can reach held-item exchange. Baby feeding uses a different success result and does not have this same fallthrough. [Adult/baby results][shared-feed] · [Distinct success values][interaction-results] · [Later owner gate][shrimp-hand]

Two compatible adults in love can breed. The offspring factory creates a baby shrimp through the shared breeding flow and does not copy an owner; tame the offspring separately. [Breeding goal][breed-goal] · [Shared birth flow][shared-breed] · [Shrimp offspring][shrimp-child]

### Owner commands and held items

With an empty hand, an owner can use the tamed shrimp to cycle **Wander → Follow → Sit → Break Blocks → Wander**. These are the source's command names. Follow is command 1, and combat can interrupt it. Sit uses the shrimp's own movement stop; it is not a promise that all targeting or damage stops. [Command interaction][shrimp-hand] · [Displayed names][commands-text] · [Follow checks][shrimp-follow] · [Sitting travel][shrimp-travel]

Sneak-use it to manage the item in its claws. If its hand is empty, it takes **one item from your held stack**; this direct subtraction also applies in Creative. If it already holds something, sneak-use drops that item into the world and clears its hand. Use an empty hand to retrieve the held item, and remember the adult last-fish fallthrough described above. [Held-item exchange][shrimp-hand]

Giving it a **Water Bucket** keeps its moisture refreshed and makes its water-leaving goal eligible. The held bucket is not spent by that moisture check. This is an item carried by the shrimp, not a bucket containing the animal. [Moisture refresh][shrimp-moisture] · [Water-seeking rules][shrimp-water]

### Moisture and keeping it safe

Water, rain or a held Water Bucket resets its moisture reserve to **60,000 ticks**. Otherwise its active tick code reduces the reserve. After it runs out, the shrimp cancels its command/sitting state and begins periodic dry-out damage attempts. Provide reachable water instead of treating sitting on dry land as safe indefinite storage. Its own damage check rejects drowning and in-wall damage, but that does not make it immune to other hazards. [Moisture handling][shrimp-moisture] · [Immunities][shrimp-travel] · [Water goals][shrimp-water]

The prey tag includes **Mimic Octopus, Lobster, Shulker, Squid, Guardian, Elder Guardian, Tropical Fish, Catfish and Flying Fish**. Sit and Break Blocks prevent the tagged-prey goal from starting, but they do not clear an existing target; owner-defense and retaliation goals remain registered separately. Taming therefore does not make a shared aquarium universally safe. [Exact targets][shrimp-targets] · [Tag predicate][target-tag] · [Target goals][shrimp-goals] · [Target continuation][target-continuation]

When the shrimp receives kill credit for a **Shulker**, its active kill callback adds **one Shulker Shell**. It also reduces incoming damage whose attributed source entity is a Shulker or Shulker Bullet. This is separate from ordinary Shulker loot and does not establish a fixed farm yield. See [Shulker Shell](../items/ShulkerShell.md) for the shell's uses. [Shrimp callbacks][shrimp-shell] · [Active kill-credit caller][kill-credit]

The normal melee goal calls the current two-argument attack method. The shrimp's old one-argument punch starter is a different method, so its animation-only knockback, ignition and fish-discard code should not be treated as guaranteed effects of every ordinary attack. Other registered goals can still trigger a punch animation. [Melee caller][melee-caller] · [Current melee damage][melee-damage] · [Old punch starter][shrimp-punch] · [Animation effects][shrimp-punch-tick]

### Break Blocks and the Wheat interaction

For **Break Blocks**, give an adult shrimp a sample block item and select that command. With no living combat target, it searches nearby for blocks whose item form matches the held sample, checks line of sight, approaches and requests their destruction. If the shrimp is underwater, its search also requires the target to touch water. The sample is not consumed by this goal. [Start conditions][break-start] · [Search][break-search] · [Matching and destruction][break-action]

Keep this command away from builds you want to preserve. The goal contains no `mobGriefing` check. It requests drops through the generic block-destruction path with an **empty tool context**, so do not assume a player tool's harvesting, Silk Touch or Fortune behavior. The block's actual loot rules still determine the result. [Break action][break-action] · [Generic caller][break-caller] · [Loot context][break-loot]

A shrimp holding **Wheat** can approach a furnace or campfire through a separate goal, animate punches and change a furnace's lit state. Its food-output code is commented out. This does **not** establish fried-rice production or a new cooking recipe. [Active Wheat goal and omitted output][wheat-goal]

### Save and reload limits

Do not rely on the shrimp's custom command, sitting flag, moisture or appearance being restored after reloading the entity. Its species save/load methods use narrower argument types than the active entity save/load contract, so the normal callbacks do not reach those methods. The inherited tame-owner and equipment paths are separate and remain wired. Recheck its command and water arrangements after a reload. [Species overloads][shrimp-save] · [Save call][entity-save-call] · [Load call][entity-load-call] · [Active contract][entity-save-contract] · [Owner persistence][tame-save] · [Equipment save][equipment-save] · [Equipment load][equipment-load]

## Notes

- Entity ID: `minecraft:mantis_shrimp`; egg ID: `minecraft:mantis_shrimp_spawn_egg`.
- Registered category: `WATER_CREATURE`; size: 0.8 × 0.5 blocks. [Registration][shrimp-entity]
- Related: [Mudskipper](Mudskipper.md), [Water Bucket](../items/WaterBucket.md), and [Inventory Browser](../mechanics/InventoryBrowser.md).

Source-reviewed on **2026-10-02** at `eaeeffdeb9220de7d839af7c84a047693249c2f7`. Checked active registrations, food/target tags, callers, breeding, owner controls, block-changing goals, moisture, kill rewards and persistence signatures. No game, browser, taming, breeding, combat, block-breaking, reload or production-rate test was run.

[shrimp-entity]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/EntityType.java#L900-L902
[attributes]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L196-L200
[shrimp-stats]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/EntityMantisShrimp.java#L137-L143
[shrimp-goals]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/EntityMantisShrimp.java#L149-L170
[shrimp-egg]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/item/Items.java#L1911-L1911
[egg-list]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2045-L2049
[browser]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[browser-server]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1919
[egg-use]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L50-L101
[shrimp-spawn]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/EntityMantisShrimp.java#L128-L147
[placements]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[natural-list]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450
[biomes]: https://github.com/HungLo2020/MattMC/tree/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/resources/data/minecraft/worldgen/biome
[registered-items]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/item/Items.java
[shrimp-hand]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/EntityMantisShrimp.java#L332-L368
[loot]: https://github.com/HungLo2020/MattMC/tree/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/resources/data/minecraft/loot_table
[loot-key]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-fallback]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[fish-tag]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/resources/data/minecraft/tags/item/fishes.json
[shrimp-food]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/EntityMantisShrimp.java#L221-L224
[shrimp-feeding]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/EntityMantisShrimp.java#L305-L331
[shrimp-save]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/EntityMantisShrimp.java#L371-L385
[shared-feed]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/animal/Animal.java#L134-L158
[consume-feed]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/Mob.java#L1113-L1120
[empty-stack]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/item/ItemStack.java#L322-L332
[interaction-results]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/InteractionResult.java#L11-L15
[breed-goal]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/ai/goal/BreedGoal.java#L33-L80
[shared-breed]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/animal/Animal.java#L197-L227
[shrimp-child]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/EntityMantisShrimp.java#L541-L561
[commands-text]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/resources/assets/minecraft/lang/en_us.json#L3985-L3988
[shrimp-follow]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/EntityMantisShrimp.java#L618-L645
[shrimp-travel]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/EntityMantisShrimp.java#L184-L205
[shrimp-moisture]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/EntityMantisShrimp.java#L283-L303
[shrimp-water]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/EntityMantisShrimp.java#L563-L585
[shrimp-targets]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/resources/data/minecraft/tags/entity_type/mantis_shrimp_targets.json
[target-tag]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/AMEntityRegistry.java#L79-L81
[shrimp-shell]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/EntityMantisShrimp.java#L105-L125
[kill-credit]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1412-L1435
[melee-caller]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/ai/goal/MeleeAttackGoal.java#L127-L144
[melee-damage]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/Mob.java#L1292-L1320
[shrimp-punch]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/EntityMantisShrimp.java#L226-L233
[shrimp-punch-tick]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/EntityMantisShrimp.java#L417-L450
[break-start]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/ai/MantisShrimpAIBreakBlocks.java#L46-L61
[break-search]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/ai/MantisShrimpAIBreakBlocks.java#L101-L129
[break-action]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/ai/MantisShrimpAIBreakBlocks.java#L140-L160
[break-caller]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/level/LevelWriter.java#L17-L25
[break-loot]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/level/Level.java#L263-L280
[wheat-goal]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/ai/MantisShrimpAIFryRice.java#L39-L89
[entity-save-call]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/Entity.java#L1963-L1967
[entity-load-call]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/Entity.java#L2026-L2029
[entity-save-contract]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/Entity.java#L2055-L2057
[tame-save]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/TamableAnimal.java#L54-L79
[equipment-save]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/LivingEntity.java#L751-L756
[equipment-load]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/LivingEntity.java#L817-L821
[target-continuation]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/ai/goal/target/TargetGoal.java#L37-L70
