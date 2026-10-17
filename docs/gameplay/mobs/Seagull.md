# Seagull

A **Seagull** is an untamed bird that can breed with raw fish, pick up dropped food, and steal food from a nearby player's hotbar. Protect valuable food and retrieve anything it grabs promptly. Its treasure-map behavior is only partly connected in the checked MattMC source. [Active goals][seagull-goals] · [Theft][steal] · [Retrieval][retrieve] · [Map handling][map-check]

## Obtaining

Use the [Seagull Spawn Egg](../items/SeagullSpawnEgg.md) for the verified ordinary creation route. It is listed in the item browser, which can supply ordinary listed items in **Creative**; follow [Inventory item browser](../mechanics/InventoryBrowser.md). This is separate from natural spawning. [Egg registration][seagull-egg] · [Listing][seagull-list] · [Egg placement][egg-place]

The checked bundle has **no Seagull entries in its active biome spawn lists**, and no separate natural creation caller was found. Its additional spawn-roll setting is zero; the helper therefore rejects ordinary natural and chunk-generation checks. A configured spawn weight or a standalone bright-ground predicate does not establish a working wild population. Do not assume a beach visit will produce Seagulls in this build. [Biome-list dispatch][natural-list] · [Chunk-generation selection][chunk-list] · [Natural check][natural-rules] · [Chunk-generation check][chunk-rules] · [Species rules][seagull-spawn] · [Default configuration][seagull-config] · [Roll helper][spawn-roll]

Once you have two adults, normal breeding works. A matching egg used on a Seagull can also create a baby through the species' offspring factory; see [Spawn eggs](../items/SpawnEggs.md#using-an-egg-on-an-existing-mob) for the shared interaction. [Egg dispatch][egg-dispatch] · [Baby creation][egg-baby] · [Offspring factory][seagull-baby] · [Registered-type alias][seagull-alias]

## Feeding and breeding

Different ways of supplying food have different results:

| What you do | Current result |
| --- | --- |
| Hold Raw Cod or Raw Salmon | Lures a Seagull when its item-related activity is not taking priority |
| Interact with an eligible adult using Raw Cod or Raw Salmon | Consumes one fish in ordinary Survival and starts breeding love mode |
| Feed either raw fish to a baby | Advances growth by about 10% of the remaining childhood |
| Hold Tropical Fish, Cooked Cod, or Cooked Salmon | Can lure it; these are offering items, not the bundled breeding foods |
| Drop an edible item where it can reach it | It can pick up one item, carry it, then eat it and heal |

The raw-fish and offering lists are separate bundled tags. Hand-feeding uses the ordinary animal breeding/growth interaction; it does **not** perform the bird's carried-food healing action. [Breeding foods][raw-fish] · [Offering foods][offering-fish] · [Lure and food checks][seagull-goals] · [Animal feeding][animal-feed] · [Growth amount][age] · [Carried-food healing][food-hold]

Feed two nearby adults that are outside their breeding cooldown, then allow them to approach. The baby is a Seagull, and each parent receives **6,000 ticks**, about **5 minutes**, of cooldown. A baby normally grows in **24,000 ticking game ticks**, about **20 minutes**, and cannot enter the flying state before adulthood. Feeding does not tame either parent or create an owner relationship. [Breeding approach][breed-goal] · [Birth and cooldown][birth] · [Offspring][seagull-baby] · [Age progression][age] · [Baby flight restriction][seagull-fly]

A carried food item is normally eaten after the holding counter passes **200 ticks**, about **10 seconds** at the normal rate, while the bird is not sitting. Eating consumes one item and restores **4 health points, or 2 hearts**, up to its maximum. This path recognizes the item's food component and directly heals the bird; it does not run the player's normal food-effect or empty-container return action. Drop inexpensive food if your purpose is healing. [Food eligibility and consumption][food-hold] · [One-item pickup][seagull-pickup] · [Ground-stack decrement][item-pickup]

## Behavior

### Protecting and recovering food

Stealing is **enabled by default**. An eligible Seagull with an empty beak can approach a nearby non-Creative, non-Spectator player, take **one** edible item from the **nine hotbar slots**, and fly away with it. The selected hotbar slot need not be the item currently in your hand. The current blacklist check rejects nothing, so valuable edible items are eligible too. [Default setting][seagull-config] · [Starting conditions][steal-start] · [Theft and flight][steal] · [Player and hotbar checks][hotbar]

Move food into the main inventory outside the hotbar or into storage while working near one. That prevents this hotbar-stealing path from selecting it; do not leave replacement food on the ground, because the separate item-pickup goal searches for dropped edible items. These two goals do not check `mobGriefing`. [Hotbar-only selection][hotbar] · [Dropped-item search][item-search] · [Pickup callback][item-pickup]

**Interact with an empty hand to make it drop what it is carrying**, then collect the loose item before it eats it or another bird reaches it. This sets a theft cooldown of **1,500–2,999 ticks**, about **75–150 seconds** at normal ticking speed. Successful damage also makes it drop its held item, but the empty-hand interaction retrieves it without injuring the bird. The cooldown concerns theft from players; it does not disable all dropped-food pickup. [Retrieval and cooldown][retrieve] · [Damage response][damage-drop] · [Separate pickup goal][seagull-items]

### Offerings and treasure maps

**Do not rely on feeding a Seagull to locate Buried Treasure in this build.** When it picks up a player-thrown Tropical Fish, Cooked Cod, or Cooked Salmon, the offering callback checks the thrower's hands for a map with a nonempty map-decoration component. It can record the feeder and set a short timer, but it does **not** read a destination into the bird's treasure position. [Offering callback][seagull-pickup] · [Map check][map-check]

The treasure-following goal requires a nonempty treasure position, which starts empty. The checked normal map interaction never supplies it; only the saved-coordinate loading path restores such a position. An offer or decorated map is therefore not proof of a working guide. Use the [Buried Treasure guide](../structures/BuriedTreasure.md) and a working [Map](../items/Map.md) for the actual navigation route. [Empty initial position][treasure-default] · [Goal requirement][treasure-goal] · [Saved coordinates][treasure-save]

### Movement and appearance

Seagulls have **8 health points, or 4 hearts**. Adults switch between walking and flight, can circle or seek landing positions, and scatter from nearby non-Creative players when higher-priority food activities are not keeping them occupied. The theft peck removes food; its active callback does not damage the player. This is not a trained combat or shoulder companion. [Registered attributes][seagull-attribute] · [Health and goals][seagull-goals] · [Wandering flight][seagull-wander] · [Scattering][seagull-scatter] · [Fleeing movement][scatter-flight] · [Theft callback][steal]

Use an enclosed space with a roof if you want to keep a flying adult nearby. A [Lead](../items/Lead.md) is also available through the shared mob leash interaction, but feeding provides no sit, follow-owner, or teleport command. Ordinary movement does not apply landing fall damage because the bird overrides that callback. [Leash eligibility][lead-rule] · [Lead interaction][lead-use] · [Active goals][seagull-goals] · [Movement fall caller][fall-caller] · [Bird override][seagull-fall]

Naming one **`wingull`**, ignoring capitalization, selects its alternate texture. This is an appearance change on the same registered Seagull, not a separate species or a taming method. [Name check][wingull-name] · [Active texture selection][wingull-render] · [Name Tag interaction][name-tag]

## Persistence and drops

Seagulls inherit the animal rule that disables ordinary distance-based despawning. Their age, equipment, flying/sitting state, and theft cooldown are saved. The temporary feeder reference is not an ownership system and is not included in the Seagull's saved fields. Persistence does not stop an unconfined bird flying away. [Animal retention][no-distance] · [Saved age][age-save] · [Saved equipment][living-save] · [Species saved state][treasure-save]

With normal mob loot enabled, an adult's base death table supplies **0–2 Feathers**, with a Looting count bonus. Babies do not produce that base loot. Food in its beak is separate from the Feather table; use the empty-hand retrieval interaction rather than relying on generic equipment loot. [Feather table][seagull-loot] · [Adult and mob-loot gate][loot-gate] · [Death dispatch][loot-dispatch] · [Loaded table][loot-load] · [Equipment rules][equipment-loot] · [Retrieval][retrieve]

## Notes

- Entity ID: `minecraft:seagull`
- Spawn Egg ID: `minecraft:seagull_spawn_egg`
- Registered as a creature with a **0.5 × 0.6-block** adult size. [Entity registration][seagull-id] · [Egg registration][seagull-egg]

## Related pages

- [Seagull Spawn Egg](../items/SeagullSpawnEgg.md)
- [Raw Cod](../items/RawCod.md) and [Raw Salmon](../items/RawSalmon.md)
- [Buried Treasure](../structures/BuriedTreasure.md)
- [Map](../items/Map.md)
- [Potoo](Potoo.md)
- [Mobs](Mobs.md)

## Sources and verification

Source-reviewed at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6` on 2026-10-02. Checked registered creation and attribute callers, active biome resources, spawn-roll dispatch, food tags, breeding, theft and item callbacks, map destination assignment, movement, saved state, and active loot. No in-game spawn, feeding, theft, treasure search, flight, breeding, or drop test was run. The missing natural and treasure routes describe the checked bundle; added data or code can change them.

[seagull-goals]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySeagull.java#L137-L157
[steal]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/ai/SeagullAIStealFromPlayers.java#L61-L101
[retrieve]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySeagull.java#L524-L537
[map-check]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySeagull.java#L390-L405
[seagull-egg]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1946
[seagull-list]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2077
[egg-place]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L89-L103
[natural-list]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L290-L325
[chunk-list]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L360-L369
[natural-rules]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L284-L287
[chunk-rules]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L410-L417
[seagull-spawn]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySeagull.java#L160-L169
[seagull-config]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/config/AMConfig.java#L31-L33
[spawn-roll]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/AMEntityRegistry.java#L62-L72
[egg-dispatch]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Mob.java#L1079-L1102
[egg-baby]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L181
[seagull-baby]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySeagull.java#L541-L545
[seagull-alias]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/AMEntityRegistry.java#L31
[raw-fish]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/seagull_breedables.json#L1-L7
[offering-fish]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/seagull_offerings.json#L1-L8
[animal-feed]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/animal/Animal.java#L133-L158
[age]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/AgeableMob.java#L129-L167
[food-hold]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySeagull.java#L341-L371
[breed-goal]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/goal/BreedGoal.java#L32-L80
[birth]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/animal/Animal.java#L197-L226
[seagull-fly]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySeagull.java#L205-L209
[seagull-pickup]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySeagull.java#L424-L443
[item-pickup]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/ai/CreatureAITargetItems.java#L130-L146
[steal-start]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/ai/SeagullAIStealFromPlayers.java#L30-L46
[hotbar]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/ai/SeagullAIStealFromPlayers.java#L104-L143
[item-search]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/ai/CreatureAITargetItems.java#L53-L106
[damage-drop]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySeagull.java#L237-L252
[seagull-items]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySeagull.java#L822-L875
[treasure-default]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySeagull.java#L192-L198
[treasure-goal]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/ai/SeagullAIRevealTreasure.java#L20-L52
[treasure-save]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySeagull.java#L110-L135
[seagull-attribute]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L228
[seagull-wander]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySeagull.java#L669-L706
[seagull-scatter]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySeagull.java#L560-L592
[scatter-flight]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySeagull.java#L607-L640
[lead-rule]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Mob.java#L1217-L1219
[lead-use]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Entity.java#L2162-L2174
[fall-caller]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Entity.java#L738-L746
[seagull-fall]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySeagull.java#L172-L177
[wingull-name]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySeagull.java#L418-L421
[wingull-render]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/client/render/RenderSeagull.java#L24-L38
[name-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/NameTagItem.java#L16-L29
[no-distance]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/animal/Animal.java#L121-L124
[age-save]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/AgeableMob.java#L106-L118
[living-save]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L730-L756
[seagull-loot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/entities/seagull.json#L1-L36
[loot-gate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L565-L567
[loot-dispatch]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1474
[loot-load]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1502-L1527
[equipment-loot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Mob.java#L813-L838
[seagull-id]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/EntityType.java#L1149-L1151
