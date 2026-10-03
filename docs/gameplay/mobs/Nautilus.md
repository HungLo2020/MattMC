# Nautilus

A **Nautilus** is a tameable aquatic companion that its owner can saddle and ride. Keep it in water, use loose Pufferfish for taming, and bring your own underwater breathing protection: the current mount effect does not provide the air protection its name suggests. Its ID is `minecraft:nautilus`. [Registration][nautilus-type] · [Interactions][nautilus] · [Breathing check][breathing]

## Where to find one

**The bundled biome spawn tables do not currently supply natural Nautilus spawns.** The mob, attributes and water-spawn predicate are registered, but the game's loaded biome JSON contains no Nautilus entry. Ocean additions in the Java biome-generation builders are not merged into those loaded tables. Do not plan an ocean search around those unused builder entries. [Attribute wiring][attributes-nautilus] · [Predicate wiring][placements] · [World loading][world-loader] · [Ocean data][biome-json] · [Generation builder][builders]

The [Nautilus Spawn Egg](../items/NautilusSpawnEgg.md) is listed in an ordinary inventory category, so the verified [inventory item browser](../mechanics/InventoryBrowser.md) can supply it in Creative. This is a separate route from finding a naturally spawned animal. Use it in water, then tame the resulting Nautilus. Data packs can change spawning. [Egg listing][egg-list-nautilus] · [Egg placement][egg-use]

The active spawn chooser reads the loaded biome spawn list (or a structure override); the resource registries are created fresh from JSON. Neither the checked biome files nor structure spawn overrides add these mobs. [Registry kinds][loaded-registries] · [Fresh registry creation][fresh-registries] · [JSON loading][json-loading] · [Natural spawn lookup][natural-spawns] · [Biome/structure selection][biome-spawn-choice]

## Taming and feeding

Use **Pufferfish** or a **Bucket of Pufferfish** on an untamed Nautilus. Each attempt has a **one-in-three** success chance; failed attempts still consume one item outside instant-build mode. Successful taming assigns the player as owner. Taming does not increase the animal's maximum health. [Taming interaction][nautilus] · [Taming items][taming-food] · [Ownership][owner]

Once tamed, the following foods are accepted:

- Raw Cod, Cooked Cod, Raw Salmon, Cooked Salmon, Tropical Fish and Pufferfish
- Buckets of Cod, Salmon, Tropical Fish and Pufferfish

These follow the bundled tags, so a custom tag can change the list. **Loose fish are the practical choice:** the checked feeding paths consume the fish-bucket item without a bucket-return conversion. [Food tag][food] · [Fish members][fish] · [Bucket members][bucket-food] · [Bucket properties][fish-buckets] · [Consumption][item-use]

An owner's food interaction first heals an injured tame Nautilus by **4 health points, or 2 hearts**, up to its maximum. Healing takes priority over mounting or breeding. Other players do not get this owner-specific healing interaction. [Healing and interaction order][nautilus]

## Breeding and babies

Keep two tame adults together in water and feed them accepted food. Heal injured animals first. If a saddled adult tries to carry you instead, **hold Sneak while feeding** to bypass mounting and reach the breeding interaction. Both animals need to be ready to breed; after a successful birth, each has a **6,000-tick cooldown**, five minutes at 20 ticks per second. [Interaction order][nautilus] · [Food and love state][animal] · [Breeding goal][breed-goal] · [Cooldown][breeding]

The child is a baby Nautilus. A tame parent creating the child passes on its owner and tame state; using two parents owned by you avoids ambiguity about which parent's ownership is inherited. Babies normally mature in **24,000 ticks**, 20 minutes. Feeding a baby speeds growth by roughly one tenth of its remaining growth time; taming and owner-healing checks still take priority when applicable. Babies cannot use the saddle or armor slots. [Offspring][offspring] · [Growth][age] · [Slot restrictions][nautilus]

## Riding and equipment

1. Use a [Saddle](../items/Saddle.md) on a living, **adult, tamed** Nautilus with an empty saddle slot
2. As its owner, interact without Sneak to mount; an empty hand avoids competing item interactions
3. Use normal movement inputs to steer. The riding path uses Jump for upward input. **Sneak is also the player's server-side dismount control**, so do not rely on it as a sustained descent control

The saddle is required for player control. Only the owner gets the ordinary mounting interaction. The reviewed implementation has no working dash control to document; dash sound methods alone do not provide one. [Saddle interaction][saddle] · [Equipment checks][equip-target] · [Riding][nautilus] · [Movement][movement] · [Client input][input] · [Sneak packet][input-server] · [Dismount key][dismount-key] · [Server dismount][dismount]

### Nautilus armor

**Use a dispenser facing the Nautilus to equip body armor.** It must be alive, tamed, adult and have an empty body slot. Load one armor piece and activate the dispenser while the animal is in the block space in front. Ordinary right-click armor application is not enabled by the current item's equipment properties. [Armor properties][armor-properties] · [Interaction default][equip-defaults] · [Item interaction][item-interact] · [Dispenser selection][dispenser-default] · [Dispenser checks][dispenser-check] · [Equipping][dispenser]

| Armor | Added armor points | Added toughness | Added knockback resistance |
| --- | ---: | ---: | ---: |
| [Copper](../items/CopperNautilusArmor.md) | 4 | 0 | 0 |
| [Iron](../items/IronNautilusArmor.md) | 5 | 0 | 0 |
| [Golden](../items/GoldenNautilusArmor.md) | 7 | 0 | 0 |
| [Diamond](../items/DiamondNautilusArmor.md) | 11 | 2 | 0 |
| [Netherite](../items/NetheriteNautilusArmor.md) | 11 | 3 | 0.1 |

Armor points are combat attributes, not a fixed percentage of damage stopped. These items stack to one, have no normal durability component, and are configured not to take damage from the wearer's injuries. [Material values][armor-materials] · [Attribute application][armor-attributes] · [Item properties][armor-properties]

Dismount before using [Shears](../items/Shears.md) without Sneak to remove equipment. One use removes one eligible piece and drops it beside the animal; leash connections are removed before equipment if present. [Shears interaction][shears] · [Rider restriction][shear-rider] · [Recovery][shear-result]

Although the armor's allowed-entity tag also names Zombie Nautilus, its normal player interactions cannot tame or equip that mob. See the [Zombie Nautilus restrictions](ZombieNautilus.md#player-interactions). [Allowed types][armor-tag] · [Zombie restrictions][zombie]

## Keeping it alive and nearby

A Nautilus breathes underwater. Out of water it flops and seeks water; its **300-tick air reserve** drains, with the first **2-point drying hit** after about **16 seconds** from a full reserve and another every second while it remains dry. Entering water restores the reserve. Keep both its enclosure and any travel route wet. Poison cannot affect it, but that does not protect it from other hazards. [Water breathing and flopping][movement] · [Active drying tick][care] · [Poison rejection][nautilus-other]

It can retaliate against attackers and defend its owner. Its follow goal begins at roughly 10 blocks away and tries to close within 2 blocks using water navigation; this goal does not teleport it to you. A leash or an unavailable path can prevent ordinary following, and there is no player sit/stay interaction in the checked mob implementation. [Goals][nautilus] · [Follow behavior][follow] · [Follow exclusions][follow-gate]

**Taming alone is not a reliable persistence safeguard in this revision.** Nautilus explicitly allows distance despawning, and its taming path does not set the ordinary persistence flag. Use a renamed [Name Tag](../items/NameTag.md) for a companion you intend to keep; successful dispenser equipping also sets persistence. This protects against ordinary distance despawning, not death. [Distance rule][nautilus-other] · [Despawn checks][despawn] · [Taming flags][taming] · [Name Tag][name-tag] · [Dispenser persistence][dispenser]

### Underwater breathing limitation

A ridden Nautilus applies **Breath of the Nautilus** for 60 ticks and refreshes it while carrying the player. However, the effect is registered as a plain effect with no air-related modifier or ticking behavior. The player's underwater-air path recognizes **Water Breathing and Conduit Power**, and its oxygen decrement does not consult Breath of the Nautilus. **Bring Water Breathing or stay within a working Conduit's coverage; do not treat the mount effect as drowning protection.** This is a source-reviewed integration limitation, not an in-game reproduction. [Application][movement] · [Effect registration][breath-registration] · [Base effect behavior][base-effect] · [Air gate][air-gate] · [Air change][air-change] · [Recognized breathing effects][breathing]

## Health and drops

- **Health:** 15 points, or 7½ hearts; **base melee damage:** 3 points before applicable modifiers
- **Shell:** an eligible adult player-credit kill has a 5% chance to drop one [Nautilus Shell](../items/NautilusShell.md), rising to 6%, 7% and 8% with Looting I, II and III
- **Experience:** an eligible adult player-credit kill gives 1–3 experience with mob loot enabled
- Babies do not supply the normal death-table items or death experience. Equipped gear is handled separately from the shell table

The shell is not a guaranteed drop, and killing Nautiluses is not a verified natural-shell-farming route while bundled natural spawns are absent. [Attributes][nautilus] · [Shell table][nautilus-loot] · [Animal experience][animal] · [Age and mob-loot gates][loot-age] · [Death processing][loot-xp] · [Equipment drops][equipment-loot]

Related: [Zombie Nautilus](ZombieNautilus.md) · [Nautilus Spawn Egg](../items/NautilusSpawnEgg.md) · [Conduit](../blocks/Conduit.md) · [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f`. Checked active entity/attribute/predicate wiring, loaded biome data, food and equipment interactions, breeding, movement, oxygen handling, persistence and loot. No in-game taming, riding, breeding, spawning, damage, despawn or dispenser test was run. Timing assumes 20 ticks per second; data packs can change tags, recipes, loot and biome entries.

[nautilus-type]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/EntityType.java#L946-L953
[nautilus]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/animal/nautilus/AbstractNautilus.java#L71-L179
[breathing]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/effect/MobEffectUtil.java#L43-L45
[attributes-nautilus]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L119-L119
[placements]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L89-L97
[world-loader]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/server/WorldLoader.java#L38-L51
[biome-json]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/resources/data/minecraft/worldgen/biome/ocean.json
[builders]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/data/worldgen/BiomeDefaultFeatures.java#L447-L463
[egg-list-nautilus]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2052-L2052
[egg-use]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L49-L127
[taming-food]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/resources/data/minecraft/tags/item/nautilus_taming_items.json
[owner]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/TamableAnimal.java#L158-L169
[food]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/resources/data/minecraft/tags/item/nautilus_food.json
[fish]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/resources/data/minecraft/tags/item/fishes.json
[bucket-food]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/resources/data/minecraft/tags/item/nautilus_bucket_food.json
[fish-buckets]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/Items.java#L1534-L1573
[item-use]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/Mob.java#L1113-L1120
[animal]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/animal/Animal.java#L126-L174
[breed-goal]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/ai/goal/BreedGoal.java#L32-L80
[breeding]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/animal/Animal.java#L197-L227
[offspring]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/animal/nautilus/Nautilus.java#L19-L34
[age]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/AgeableMob.java#L129-L168
[saddle]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/equipment/Equippable.java#L99-L108
[equip-target]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/equipment/Equippable.java#L159-L175
[movement]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/animal/nautilus/AbstractNautilus.java#L191-L250
[input]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/client/player/LocalPlayer.java#L575-L580
[input-server]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L409-L416
[dismount-key]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/player/Player.java#L308-L314
[dismount]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/player/Player.java#L451-L458
[armor-properties]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/Item.java#L504-L518
[equip-defaults]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/equipment/Equippable.java#L178-L189
[item-interact]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/ItemStack.java#L591-L604
[dispenser-default]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/level/block/DispenserBlock.java#L102-L112
[dispenser-check]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3555-L3562
[dispenser]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/core/dispenser/EquipmentDispenseItemBehavior.java#L20-L36
[armor-materials]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/equipment/ArmorMaterials.java#L12-L38
[armor-attributes]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/equipment/ArmorMaterial.java#L25-L40
[shears]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/Entity.java#L2134-L2144
[shear-rider]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/Mob.java#L512-L514
[shear-result]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/Entity.java#L2211-L2228
[armor-tag]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/resources/data/minecraft/tags/entity_type/can_wear_nautilus_armor.json
[zombie]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/animal/nautilus/ZombieNautilus.java#L54-L92
[care]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/animal/nautilus/Nautilus.java#L84-L106
[nautilus-other]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/animal/nautilus/AbstractNautilus.java#L265-L325
[follow]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/animal/nautilus/AbstractNautilus.java#L327-L388
[follow-gate]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/TamableAnimal.java#L282-L284
[despawn]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/Mob.java#L602-L629
[taming]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/TamableAnimal.java#L115-L129
[name-tag]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/NameTagItem.java#L17-L29
[breath-registration]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/effect/MobEffects.java#L127-L127
[base-effect]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/effect/MobEffect.java#L55-L101
[air-gate]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/LivingEntity.java#L424-L443
[air-change]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/LivingEntity.java#L569-L582
[nautilus-loot]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/resources/data/minecraft/loot_table/entities/nautilus.json
[loot-age]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L567
[loot-xp]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1486
[equipment-loot]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/Mob.java#L813-L835
[loaded-registries]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L125
[fresh-registries]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L401-L409
[json-loading]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L277-L334
[natural-spawns]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L290-L325
[biome-spawn-choice]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450
