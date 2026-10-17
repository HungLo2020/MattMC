# Toucan

A **Toucan** is a small flying animal that breeds with fruit and berries and can turn selected Apples into planted **Oak Saplings**. It has **6 health points (3 hearts)**. Feeding one does not tame it: its active goals provide breeding, temptation, fruit collection and planting, with no owner-follow or sit command. [Registered attributes][toucan-attrs] · [Health][toucan-stats] · [Installed goals][toucan-goals] · [Fruit mapping][toucan-config] · [Planting][toucan-plant]

## Obtaining

### Current acquisition routes

Use a **[Toucan Spawn Egg](../items/ToucanSpawnEgg.md)** to establish your first birds, then breed a pair. The ordinary [inventory item browser](../mechanics/InventoryBrowser.md) makes listed items available in **Creative**. Normal egg placement directly creates and finalizes the entity; it does not go through natural biome spawning. [Egg registration][egg-register] · [Egg caller][egg-create] · [Entity creation][type-create] · [Offspring factory][toucan-variants]

**Do not plan a Jungle expedition around finding wild Toucans in this build.** None of the 68 bundled biome definitions contains a Toucan spawn entry. The checked natural spawning route selects biome/structure candidates, while the Toucan's own spawn-rule call uses a default of **zero spawn rolls**; that helper returns false for ordinary natural and chunk-generation reasons. The unused `toucanSpawnWeight` setting alone does not populate a biome. These are source findings for the bundled configuration, not a claim about every custom server. [Candidate selection][spawn-list] · [Chunk-generation selection][chunk-list] · [Toucan rule][toucan-spawn] · [Default configuration][toucan-config] · [Spawn-roll helper][spawn-roll]

Its separate obstruction check requires clear, liquid-free space **at or above sea level**, over Grass Block or Leaves. Those checks matter to callers that request spawn validation; they do not establish a loaded wild-spawn route. [Obstruction predicate][toucan-spawn]

## Behavior

### Food for breeding and growth

Hold **[Apple](../items/Apple.md), [Sweet Berries](../items/SweetBerries.md) or [Glow Berries](../items/GlowBerries.md)** to tempt a Toucan, and feed two ready adults to breed them. The bundled breeding tag contains exactly those three items. Golden Apples are handled by the separate planting interaction, not this breeding tag. [Registered temptation and food predicate][toucan-goals] · [Breeding tag][toucan-breed-food] · [Tag namespace][am-tags] · [Namespace helper][am-tag-names]

The active breeding goal brings a pair together and creates one chick. Parents receive a **6,000-tick cooldown** (about **5 minutes**); a chick normally grows over **24,000 ticks** (about **20 minutes**) while ticking. Feeding a chick a breeding food advances roughly **10% of its remaining growth**, rounded to seconds. Chicks cannot enter the Toucan's flying state. [Installed breeding goal][toucan-goals] · [Active goal caller][breed-goal] · [Animal feeding][animal-use] · [Breeding completion][animal-breed] · [Growth][age] · [Baby flight restriction][toucan-fly]

**Use berries when you only want breeding or growth.** Apple is also a planting input. The Toucan runs its inherited breeding/growth interaction first, then checks the remaining held stack for a planting gift. With enough Apples in the stack and an empty beak, one interaction can therefore consume an Apple for breeding or growth **and another for planting**. [Interaction order][toucan-use] · [Inherited food handling][animal-use] · [One-item consumption][consume] · [Apple mapping][toucan-config]

### Giving fruit for saplings

The default planting inputs are:

| Item eaten from its beak | Planting result |
| --- | --- |
| Apple | Remembers Oak Sapling, normally cleared after one successful planting |
| Golden Apple | Remembers Oak Sapling and grants **12,000 ticks** of golden state, about **10 minutes** while ticking |
| Enchanted Golden Apple | Remembers Oak Sapling and grants a saved enchanted state that allows repeated successful planting without clearing the remembered sapling |

[Default map][toucan-config] · [Fruit lookup][toucan-lookup] · [Consumption and effects][toucan-eat] · [Golden countdown][toucan-timer] · [Successful planting][toucan-plant] · [Saved state][toucan-save]

Give the fruit directly while its beak is empty, or drop it nearby. The dedicated item goal seeks eligible loose fruit and takes **one item**, then the Toucan eats it after holding it for more than ten ticks. Eating through this beak path heals **4 health points**, up to its maximum. Berries fed for breeding do not run that healing path. Do not leave valuable Golden Apples loose near a Toucan you do not intend to feed. [Direct gift][toucan-use] · [Eligible mapped items][toucan-fruit-target] · [Installed pickup goal][toucan-goals] · [Flying item goal][toucan-item-goal] · [Item selection][fruit-find] · [One-item pickup][fruit-pickup] · [Held item][toucan-hold] · [Healing and consumption][toucan-eat] · [Health cap][heal-cap]

The bundled mapping does **not** include Cocoa Beans, Sweet Berries or Glow Berries as planting gifts. Grow [Cocoa](../blocks/Cocoa.md) with its ordinary log-supported planting route. The source's fruit-to-block map is a separate mechanism from its breeding-food tag. [Mapping initialization][toucan-map] · [Default mappings][toucan-config] · [Breeding foods][toucan-breed-food]

### Providing planting space

Leave suitable ground, overhead air and room for the bird to reach a site. Its planting goal samples nearby positions, checks the remembered sapling's survival rule and line of sight, circles or approaches, then pecks. Placement still requires the destination to be replaceable when it finishes. It places a **sapling**, not a grown tree; use the [tree sapling guide](../blocks/SaplingsAndAzaleas.md) for later growth. A food gift therefore does not promise an immediate plant or a fixed planting rate. [Search and approach][toucan-plant] · [Candidate checks][toucan-plant-finish]

A normal or temporarily golden Toucan clears its remembered sapling after a successful placement. An enchanted one keeps it and can continue planting. Its planted-block memory can be replaced by a later mapped fruit gift once its beak is empty. Golden state shortens some approach timing; the enchanted branch does not use the same shortened retry delay, so it should not be described as an identical-speed permanent upgrade. [Consumption updates memory][toucan-eat] · [Gift acceptance][toucan-use] · [Golden and enchanted predicates][toucan-golden] · [Retry, approach and memory clearing][toucan-plant]

The dedicated loose-fruit pickup and sapling-placement paths contain **no `mobGriefing` check**. Do not rely on that rule alone to protect a garden from these actions. Confining the birds and controlling the fruit supply are practical precautions inferred from those paths. [Pickup caller][fruit-pickup] · [Planting caller][toucan-plant]

### Keeping birds and choosing a look

Use an enclosure with a roof if you want adults to stay in one area; their idle behavior can fly, and feeding does not establish ownership. A **Lead** is accepted through the shared non-enemy mob rule. Toucans inherit the animal rule against ordinary distance despawning, but their small health pool still needs protection from hazards. Their fall handler skips ordinary fall-damage processing. [Goals][toucan-goals] · [Flight choices][toucan-idle] · [Lead eligibility][mob-lead] · [Persistence][animal-persist] · [Fall handling][toucan-fly]

Finalized spawns choose one of **four ordinary variants**. A bred chick copies the variant of the parent whose offspring method creates it; it does not inherit that parent's golden timer, enchanted flag or planting memory. Those special states start empty on a newly constructed bird. [Spawn and offspring][toucan-variants] · [Initial data][toucan-data]

A name containing **“sam”**, ignoring letter case and formatting, selects the special Sam appearance; it takes visual priority over the golden texture. This changes the selected appearance, not the saved ordinary variant or breeding-food list. Golden/enchanted state and the ordinary variant survive save/load. [Name check][toucan-sam] · [Texture selection][toucan-render] · [Save/load][toucan-save]

## Notes

- Entity ID: **`minecraft:toucan`**, despite its bundled Alex's Mobs implementation; registered size **0.5×0.6 blocks** [Registration][toucan-reg]
- There is **no bundled active `minecraft:entities/toucan` death loot table** in the reviewed resources. The default entity loot key resolves to an empty table when absent, so this guide does not promise Feathers or fruit from killing Toucans. Items held as equipment use a separate death-drop path [Default key][loot-default] · [Loaded-table caller][loot-load] · [Missing-table fallback][loot-missing] · [Equipment drops][equipment-drops]

## Related pages

- [Apple](../items/Apple.md)
- [Sweet Berry Bush](../blocks/SweetBerryBush.md) and [Cave Vines](../blocks/Vines.md#cave-vines-and-glow-berries)
- [Tree Saplings](../blocks/SaplingsAndAzaleas.md)
- [Cocoa](../blocks/Cocoa.md)
- [Toucan Spawn Egg](../items/ToucanSpawnEgg.md)
- [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `cf8cd5371bd1de61411ae5e7e144aabe3edb1e54`. Checked the active registrations, loaded biome and item data, relevant AI and interaction callers, shared breeding/retention rules, and loot resolution. No in-game spawning, feeding, breeding, transport, planting, combat, sound or drop test was run. Tick timings assume a ticking server at 20 ticks per second; data packs, settings and custom entity data can change the result. The biome inventory covered all 68 bundled biome definitions.

[toucan-attrs]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L245-L245
[toucan-stats]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/alexsmobs/entity/EntityToucan.java#L109-L111
[toucan-goals]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/alexsmobs/entity/EntityToucan.java#L169-L197
[toucan-config]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/alexsmobs/config/AMConfig.java#L41-L48
[toucan-plant]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/alexsmobs/entity/EntityToucan.java#L690-L752
[egg-register]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/Items.java#L1987-L1987
[egg-create]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L90-L109
[type-create]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/EntityType.java#L1731-L1775
[toucan-variants]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/alexsmobs/entity/EntityToucan.java#L471-L485
[spawn-list]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L290-L329
[chunk-list]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L360-L414
[toucan-spawn]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/alexsmobs/entity/EntityToucan.java#L125-L139
[spawn-roll]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/alexsmobs/entity/AMEntityRegistry.java#L62-L72
[toucan-breed-food]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/item/toucan_breedables.json#L1-L8
[am-tags]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/alexsmobs/misc/AMTagRegistry.java#L87-L89
[am-tag-names]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/alexsmobs/misc/AMTagRegistry.java#L250-L252
[breed-goal]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/goal/BreedGoal.java#L32-L81
[animal-use]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Animal.java#L133-L158
[animal-breed]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Animal.java#L163-L227
[age]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/AgeableMob.java#L129-L167
[toucan-fly]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/alexsmobs/entity/EntityToucan.java#L294-L310
[toucan-use]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/alexsmobs/entity/EntityToucan.java#L154-L167
[consume]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L1113-L1120
[toucan-lookup]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/alexsmobs/entity/EntityToucan.java#L141-L152
[toucan-eat]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/alexsmobs/entity/EntityToucan.java#L263-L283
[toucan-timer]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/alexsmobs/entity/EntityToucan.java#L216-L222
[toucan-save]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/alexsmobs/entity/EntityToucan.java#L393-L419
[toucan-fruit-target]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/alexsmobs/entity/EntityToucan.java#L508-L511
[toucan-item-goal]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/alexsmobs/entity/ai/FlyingAITargetDroppedItems.java#L6-L23
[fruit-find]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/alexsmobs/entity/ai/CreatureAITargetItems.java#L53-L106
[fruit-pickup]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/alexsmobs/entity/ai/CreatureAITargetItems.java#L130-L147
[toucan-hold]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/alexsmobs/entity/EntityToucan.java#L548-L558
[heal-cap]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1127-L1144
[toucan-map]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/alexsmobs/entity/EntityToucan.java#L94-L107
[toucan-plant-finish]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/alexsmobs/entity/EntityToucan.java#L766-L792
[toucan-golden]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/alexsmobs/entity/EntityToucan.java#L434-L459
[toucan-idle]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/alexsmobs/entity/EntityToucan.java#L580-L672
[mob-lead]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L1217-L1220
[animal-persist]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Animal.java#L121-L129
[toucan-data]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/alexsmobs/entity/EntityToucan.java#L199-L209
[toucan-sam]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/alexsmobs/entity/EntityToucan.java#L421-L424
[toucan-render]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/alexsmobs/client/render/RenderToucan.java#L32-L64
[toucan-reg]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/EntityType.java#L1439-L1445
[loot-default]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-load]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1502-L1527
[loot-missing]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[equipment-drops]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L813-L839
