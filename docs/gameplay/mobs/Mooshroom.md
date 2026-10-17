# Mooshroom

A Mooshroom (`minecraft:mooshroom`) is a mushroom-covered animal with **10 health points (5 hearts)**. Keep one alive for Milk and repeated Bowl servings; shearing changes it into a normal [Cow](Cow.md). [Registered entity][moosh-type] · [Registered health][moosh-attributes] [Shared cow behavior][cow]

## Obtaining

Search **[Mushroom Fields](../biomes/TemperateForests.md#mushroom-fields)** in the bundled normal Overworld. Its creature table selects Mooshrooms in configured groups of **4–8**. The ground tag currently contains **Mycelium**, and the spawn predicate requires raw brightness **above 8**, alongside placement and space checks. These are eligibility rules, not a guaranteed herd or measured spawn rate. [Biome table][mushroom-biome] · [Spawn registration][moosh-placement] · [Ground and brightness][moosh-spawn] [Mycelium tag][moosh-ground] [Brightness helper][animal-spawn] · [Natural placement checks][natural-check]

The normal preset uses the Overworld biome parameter source, whose selector includes Mushroom Fields. Chunk generation and later natural spawning both call the active creature-selection paths. [Normal preset][normal] · [Parameters][parameters] · [Provider][biome-provider] · [Mushroom Fields selection][biome-fungal] · [Generation caller][worldgen-caller] [Creature population][worldgen-spawn] · [Natural selection and creation][natural-select] [Natural spawn creation][natural-finalize]

The ordinary listed [Mooshroom Spawn Egg](../items/MooshroomSpawnEgg.md) also has a [Creative inventory-browser](../mechanics/InventoryBrowser.md) route. That is separate from natural spawning. Ordinary newly created Mooshrooms start **red**; brown is a separate variant, not a different mob ID. [Egg entry][moosh-egg] · [Variant default][moosh-breed]

## Behavior

### Feeding and breeding

**Wheat** attracts Mooshrooms and is their breeding food. Feed two eligible adults and allow them to approach; Wheat also speeds a calf's growth. Mooshrooms mate with other Mooshrooms; an ordinary Cow is not a breeding partner. Parents receive the shared **6,000-tick breeding cooldown** after producing a calf. [Food tag and cow-family goals][cow-food] [Shared cow behavior][cow] · [Feeding][animal-interact] · [Active breeding goal][breed-goal] · [Mate check and cooldown][animal-mate]

For calf age and routine pen care, see [Cow farming](Cow.md#farming-and-breeding). Mooshrooms inherit the animal rule against ordinary distance-based despawning, but still need an enclosure or Lead to prevent wandering. [Retention][animal-persistence] · [Leash eligibility][leash]

### Bowls, Milk and flower servings

Use an empty **Bucket on an adult** for a [Milk Bucket](../items/MilkBucket.md). Use a **Bowl on an adult** for [Mushroom Stew](../items/MushroomStew.md), unless that animal has a stored suspicious-stew effect. Neither interaction converts the animal or sets a collection cooldown. Calves fail the adult checks. [Inherited milking][cow-milk] · [Bowl interaction][moosh-bowl]

A **brown** Mooshroom accepts a flower that supplies suspicious-stew effects when it has no effect already stored. Its next adult Bowl serving becomes [Suspicious Stew](../items/SuspiciousStew.md) with that effect, then clears the stored serving. An additional flower while a serving is stored does not replace it. Use the existing [flower effect table](../blocks/Flowers.md#small-flower-variants) to choose among the reviewed flowers; flower feeding is separate from Wheat breeding. [Flower acceptance][moosh-flower] · [Effect lookup and save][moosh-save] · [Serving and clearing][moosh-bowl]

### Red and brown variants

A real lightning strike **toggles red ↔ brown**, once per distinct lightning bolt. The active lightning tick invokes that callback; visual-only lightning does not. The color-change callback does not clear an already stored stew effect. Plan a safe space before attempting lightning-based conversion, since the wider lightning event can affect the surroundings. [Lightning dispatch][lightning] · [Variant toggle][moosh-spawn] · [Stored effect handling][moosh-save]

When both breeding parents have the same color, the calf has a **1 in 1,024 chance** to be the other color. Otherwise it chooses a parent's color at random; a mixed red/brown pair therefore gives either color with equal probability. Finding a brown animal through this rule is possible, not a promise after a fixed number of calves. [Offspring selection][moosh-breed]

### Shearing

Use **unbroken Shears on an adult** to obtain **five mushrooms matching its color** and convert it into an ordinary Cow. The Shears take one durability damage. This is a one-time conversion, not a wool-like regrowth cycle; preserve a brown breeding animal before choosing to shear it. The [Mushrooms guide](../blocks/Mushrooms.md) owns growing and collecting the resulting mushroom blocks/items. [Interaction][moosh-shear] · [Conversion and adult check][moosh-convert] · [Variant-selecting table][shear-root] · [Red yield][shear-red] · [Brown yield][shear-brown] · [Loaded shearing-loot call][shear-dispatch]

## Drops

With ordinary entity loot enabled, an adult's bundled death table gives **0–2 Leather and 1–3 Beef before Looting**. Each pool has a Looting increase, and the Beef uses the table's fire/enchantment smelting conditions. Death loot is separate from the five-mushroom shearing reward. The ordinary gate excludes babies. [Death table][moosh-loot] · [Loot gate][loot-rule] · [Death dispatch][death]

## Notes

Related: [Cow](Cow.md) · [Mushroom Stew](../items/MushroomStew.md) · [Suspicious Stew](../items/SuspiciousStew.md) · [Mobs](Mobs.md)

## Sources and verification

Source-reviewed at `cf8cd5371bd1de61411ae5e7e144aabe3edb1e54` on 2026-10-02. Checked loaded biome selection and active spawn callers, cow-family AI/food, Mooshroom interactions, inherited milking, lightning, offspring variants, shearing tables, retained state and death loot. Mob interactions and goals run through the shared dispatch. [Interaction caller][interaction-call] · [AI caller][mob-ai] · [Loaded entity-loot call][loot-load]

No spawn search, breeding, milking, Bowl use, lightning conversion, shearing, loot or retention gameplay test was performed. Data packs and world settings can change the listed conditions.

[moosh-type]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/EntityType.java#L913-L916
[moosh-attributes]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L198
[cow]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/AbstractCow.java#L37-L56
[mushroom-biome]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/biome/mushroom_fields.json#L1-L118
[moosh-placement]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L131
[moosh-spawn]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/MushroomCow.java#L71-L91
[moosh-ground]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/block/mooshrooms_spawnable_on.json#L1-L5
[animal-spawn]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Animal.java#L105-L114
[natural-check]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L227-L265
[normal]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/world_preset/normal.json#L1-L84
[parameters]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/multi_noise_biome_source_parameter_list/overworld.json#L1-L3
[biome-provider]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L73-L109
[biome-fungal]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java#L187-L195
[worldgen-caller]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/NoiseBasedChunkGenerator.java#L442-L450
[worldgen-spawn]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L360-L418
[natural-select]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L290-L326
[natural-finalize]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L193-L210
[moosh-egg]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2047
[moosh-breed]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/MushroomCow.java#L242-L269
[cow-food]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/item/cow_food.json#L1-L5
[animal-interact]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Animal.java#L133-L158
[breed-goal]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/goal/BreedGoal.java#L32-L80
[animal-mate]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Animal.java#L197-L228
[animal-persistence]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Animal.java#L121-L124
[leash]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L1217-L1220
[cow-milk]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/AbstractCow.java#L83-L94
[moosh-bowl]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/MushroomCow.java#L93-L118
[moosh-flower]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/MushroomCow.java#L127-L169
[moosh-save]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/MushroomCow.java#L191-L207
[lightning]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LightningBolt.java#L127-L145
[moosh-shear]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/MushroomCow.java#L119-L126
[moosh-convert]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/MushroomCow.java#L173-L188
[shear-root]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/loot_table/shearing/mooshroom.json#L1-L47
[shear-red]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/loot_table/shearing/mooshroom/red.json#L1-L16
[shear-brown]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/loot_table/shearing/mooshroom/brown.json#L1-L16
[shear-dispatch]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1555-L1579
[moosh-loot]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/loot_table/entities/mooshroom.json#L1-L102
[loot-rule]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L565-L567
[death]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1475
[interaction-call]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L1053-L1075
[mob-ai]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L634-L661
[loot-load]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1502-L1527
