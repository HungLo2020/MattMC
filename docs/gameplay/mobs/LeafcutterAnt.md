# Leafcutter Ant

The **Leafcutter Ant** is a neutral animal with leaf-carrying workers and larger queens. **Oak Leaves can lure it**, and using a leaf item can calm nearby ants. Workers can cut foliage, but the nest system does not support a working colony or fungus farm in this snapshot. [Goals and lure][goals] · [Leaf interaction][feeding] · [Nest implementation][nest]

## Obtaining

For a confirmed setup route, request a [Leafcutter Ant Spawn Egg](../items/LeafcutterAntSpawnEgg.md) through the [inventory item browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item) in **Creative**, then use it on a block with room for the ant. The ordinary egg creates a worker; it does not randomly select a queen. The egg is registered and listed in the Spawn Eggs category. Seeing the item browser in Survival does not grant ordinary Survival insertion. [Egg registration][egg] · [Category entry][egg-list] · [Egg placement][egg-placement] · [Default queen state and spawn finalization][spawn-state]

**No natural ant or populated-anthill acquisition route was verified.** No Leafcutter Ant biome spawn entry, spawn-placement registration, or natural nest-generation reference was found in the inspected source and bundled data. An upstream jungle association or an existing nest block does not establish a wild encounter here. [Spawn placements][placements] · [Biome sources][biomes] · [World generation][worldgen] · [Bundled data][data]

A [Leafcutter Ant Pupa](../items/LeafcutterAntPupa.md) used on an Anthill creates a **baby worker**; see [Nests and Pupa use](#nests-and-pupa-use). A queen can be released from an anthill already supplied with saved queen data, but a freshly placed anthill has no queen and no active ordinary-play route to install one. Neither the normal egg nor Pupa use fills that gap. [Pupa action][pupa] · [Anthill use][anthill] · [Queen release and saved flag][nest]

## Behavior

### Workers and queens

A newly created worker has **8 maximum health points (4 hearts)** and **2 base attack damage**. There is a save/load inconsistency: loading a saved worker sets its maximum health to **6 points (3 hearts)**. Turning a newly created ant into a queen sets **36 maximum health points (18 hearts)** and **6 base attack damage**. These are source attribute values, not damage guaranteed after armor or difficulty scaling. [Starting attributes][attributes] · [Active attribute registration][attribute-binding] · [Queen setter and loading][queen-state] · [Active melee damage][melee-damage]

Workers have registered adult dimensions of **0.7 blocks wide by 0.5 blocks high**. Adult queens use **1.25 by 0.98 blocks**. Workers can attach to walls and ceilings; queens do not run that worker attachment update. Use a covered enclosure when keeping workers, and do not treat these dimensions as a tested escape-proof pen size. [Registration][entity] · [Queen size][dimensions] · [Adult size selection][dimension-select] · [Worker attachment][climbing]

### Foraging and carrying leaves

Adult workers without a carried leaf seek blocks in the **leaves block tag**. Babies and queens do not start this foraging goal. The cutting action removes the selected leaf block **without drops**, then restores it with approximately a **50% chance**. Its active cutting path has no `mobGriefing` check, so do not rely on that rule to protect decorative foliage. [Foraging conditions][forage-start] · [Cutting and target check][forage-cut] · [Leaves block tag][block-leaves]

The ant records a carried leaf and its source block. Carrying does not give the player a leaf item, produce fungus, or prove that a nest works. The checked return action reaches an empty storage method, and no ordinary leaf-deposit/reset route was found. A worker already carrying a leaf cannot start another foraging trip. [Carried state][forage-cut] · [Return arrival][return-arrival] · [Nest storage][nest]

Do not depend on a line of ants following one leader. The caravan-start test requires the same ant both to have a leaf and not to have a leaf, so that goal cannot start in these defaults. Holding Oak Leaves provides the separate player-lure route. [Caravan start][caravan] · [Leader test][offspring] · [Lure ingredient][lure] · [Goals][goals]

### Retaliation and calming

Ants can target an attacker and alert nearby ants; the alert checks the attacked ant's sight of that attacker. Creative players are excluded by the ant's target setter. Ordinary melee attacks use the current shared attack method, so the older bite-animation helper is not evidence of a fixed animation-based attack timing. [Retaliation][retaliation] · [Shared retaliation start][hurt-goal] · [Creative target exclusion][creative-target] · [Melee dispatch][melee-dispatch] · [Active damage][melee-damage] · [Older bite helper][bite-helper]

**Use leaves to calm ants rather than counting on an exact anger timeout.** The imported anger updater uses an older signature and is not called by the current server-AI hook. Target-loss and explicit clearing still exist; this mismatch does not establish permanent aggression. [Imported updater][anger-hook] · [Current server call][server-hook] · [Inherited animal hook][animal-hook] · [Target clearing][target-clear] · [Leaf calming][feeding]

## Nests and Pupa use

See [Leafcutter ant nests](../blocks/LeafcutterNests.md) for the Anthill, Chamber and Pupa interaction. The checked anthill implementation does not complete home discovery, ant storage or fungus production; placing nest blocks beside ants does not establish a working colony. Ants with a carried leaf, or queens, can try to return to an already saved home, but the search reuses that existing position and arrival stores nothing. [Home and search][return-search] · [Arrival][return-arrival] · [Nest implementation][nest]

Use a **Pupa on the Anthill block itself** to spawn one baby worker centered one block above it. The action consumes one Pupa outside Creative. It requires neither a Chamber nor an existing queen, and does not assign the new ant a home, store it or set the anthill's queen flag. Keep clear space above the block. Pupa use on a Chamber or unrelated block does not perform this action. [Pupa use][pupa] · [Initial home][lure] · [Default queen state][spawn-state] · [Queen flag][nest]

### Anteaters and Pupa feeding

**Keep ants you want to preserve away from Anteaters.** An adult Anteater's pursuit goal excludes queens, but its separate tongue-capture check does not repeat that queen exclusion or the adult requirement. Successful capture removes the ant. See the [Anteater guide](Anteater.md#live-ants-and-healing) for live-ant predation, including those different checks. Its [Dirt actions](Anteater.md#digging-dirt) are separate from nest behavior and do not complete the missing colony system. [Pursuit][anteater-target] · [Tongue capture][anteater-capture] · [Dirt raid targets][raid-targets]

Pupa's use as an Anteater food item has the bundled-tag problem tracked in [issue #804](https://github.com/HungLo2020/MattMC/issues/804): required, unregistered Maggot and Mosquito Larva entries reject the entire insect-item tag, and the healing-food tag depending on it also fails. That failure does **not** block Pupa-on-Anthill spawning, ant leaf care, the Anteater's independent honey breeding, or live-ant healing. See [Anteater feeding limits](Anteater.md#why-ordinary-healing-food-does-not-work). [Insect tag][insects] · [Item registry][items] · [Dependent food tag][anteater-food] · [Required entries][tag-entry] · [Whole-tag rejection][tag-loader] · [Independent honey tag][anteater-breeding] · [Pupa action][pupa] · [Leaf care][feeding] · [Live-ant healing][anteater-capture]

## Feeding, growth and keeping

Hold **Oak Leaves in either hand** to lure ants. This is narrower than hand-feeding: the active lure uses Oak Leaves specifically, while the hand interaction accepts the valid leaves item tag. Its eleven bundled entries include Jungle, Oak, Spruce, Pale Oak, Dark Oak, Acacia, Birch, Azalea, Flowering Azalea, Mangrove and Cherry Leaves. [Lure ingredient][lure] · [Registered goal][goals] · [Lure activation][tempt] · [Either-hand check][tempt-hands] · [Leaf interaction][feeding] · [Leaf tag][item-leaves] · [Registered leaf items][leaf-items]

Using one of those leaves on a **worker or a queen already on its baby cooldown** heals **3 health points (1½ hearts)** and clears anger in the fed ant and nearby ants. The clearing area extends the fed ant's bounding box by 20 blocks horizontally and 6 vertically. One leaf is consumed outside Creative even if the ant is already at full health. Feeding establishes no owner or sitting command. [Calming, healing and consumption][feeding]

A **queen whose baby cooldown is zero** instead consumes one leaf and creates **one baby worker**, while also calming nearby ants. No second parent or nest is required. This direct action sets the queen's separate cooldown to **24,000**, but that counter has no decrement in the checked implementation and is saved across reloads. **Waiting 20 minutes does not reset it**; do not plan a repeatable queen-breeding farm around this action. Subsequent leaf uses take the healing branch. [Queen feeding][feeding] · [Complete ant implementation][ant] · [Saved cooldown][save]

Baby workers from a Pupa or queen begin at **−24,000 age** and grow over **24,000 loaded entity ticks**, about **20 minutes at 20 TPS**. Feeding leaves heals them but does not accelerate that growth: the ant rejects ordinary animal breeding food. Pair breeding is unavailable, and using a matching egg directly on an existing ant also fails to create a baby because its offspring factory returns nothing. Use the egg on a block for an ordinary worker, or Pupa on an Anthill for a baby. The direct queen and Pupa actions do not award ordinary breeding XP. [Pupa][pupa] · [Queen action][feeding] · [Growth][growth] · [No ordinary food][no-food] · [Shared food behavior][animal-food] · [No offspring][offspring] · [Egg interaction dispatch][egg-dispatch] · [Egg baby factory][egg-baby] · [Ordinary breeding rewards][breeding-rewards]

Ants inherit the animal rule that prevents ordinary distance despawning; naming or feeding is not required for that protection. It does not protect them from damage or Anteater capture. [Animal persistence][persistence] · [Capture][anteater-capture]

## Drops and experience

**No species item drop, Pupa drop or Looting bonus is defined in the bundled defaults.** No Leafcutter Ant or queen loot table was found, and the queen loot identifier declared in the class is not selected by the active lookup. Both forms reach the entity type's default loot key and the missing-table fallback. Custom data packs and equipment are separate possibilities. [Declared queen key][no-food] · [Ant implementation][ant] · [Active lookup][loot-lookup] · [Default key][loot-key] · [Bundled data][data] · [Empty fallback][loot-fallback]

Adults inherit **1–3 base XP** when ordinary death-XP conditions are met: recent player or tamed-Wolf kill credit, `doMobLoot` enabled and experience not already consumed. Babies do not drop death XP. Anteater capture removes an ant directly; it does not run that ordinary death-drop action. [Animal XP][persistence] · [Kill credit][kill-credit] · [XP conditions][death-xp] · [Baby gate][baby-xp] · [Capture removal][anteater-capture]

## Appearance and integration

The renderer selects separate worker and queen models, with ordinary or angry textures, and a separate carried-leaf layer for workers. These selections do not certify a successful frame. [Renderer registration][render-registration] · [State and textures][renderer] · [Leaf layer][leaf-layer]

The selected native rendering path shares the Citadel model-transport gap tracked in [issue #803](https://github.com/HungLo2020/MattMC/issues/803). For an admitted visible body submission with a readable texture and finite transform, the worker and queen models expose an empty native model-part tree, so extraction can reach the empty-model exception. The carried-leaf layer uses the same worker model through the direct-texture route when that layer is reached. **This is a source-identified limitation, not a reproduced crash or invisibility report**, and does not establish the outcome of every frame. [Model inheritance][model] · [Queen model][queen-model] · [Advanced model base][advanced-model] · [Proxy root][proxy] · [Body submission][body-submit] · [Direct-texture admission][collector] · [Eligibility][eligibility] · [Native extraction and exception][extract] · [Extraction traversal][extract-traversal] · [Empty-root handling][traversal] · [Leaf submission][leaf-layer]

## Notes

* This mob is registered as `minecraft:leafcutter_ant`, uses `MobCategory.CREATURE`, and has registered worker size **0.7 by 0.5 blocks**. [Entity registration][entity]
* Its spawn egg is registered as `minecraft:leafcutter_ant_spawn_egg`. [Egg registration][egg]
* Its entity class is `EntityLeafcutterAnt`, from bundled Alex's Mobs content integrated into MattMC. [Entity implementation][ant]

## Sources and verification

Source-reviewed at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716` on 2026-10-03. Checked acquisition and generation references, active attributes and interaction/AI dispatch, queen and worker state, tag dependencies and loader behavior, nesting and Pupa paths, inherited growth/persistence/loot rules, and the selected body/leaf rendering chain. No in-game spawning, combat, feeding, growth, colony, drops, timing or rendering test was performed. Data packs, resource packs and custom entity data can change these defaults.

Related: [Leafcutter Ant Spawn Egg](../items/LeafcutterAntSpawnEgg.md) · [Leafcutter ant nests](../blocks/LeafcutterNests.md) · [Leafcutter Ant Pupa](../items/LeafcutterAntPupa.md) · [Anteater](Anteater.md) · [Tree leaves](../blocks/TreeLeaves.md) · [Mobs](Mobs.md)

[goals]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityLeafcutterAnt.java#L138-L150
[feeding]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityLeafcutterAnt.java#L193-L237
[nest]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/tileentity/TileEntityLeafcutterAnthill.java#L12-L60
[egg]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L1900-L1901
[egg-list]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2037-L2042
[egg-placement]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L76-L103
[spawn-state]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityLeafcutterAnt.java#L404-L421
[placements]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[biomes]: https://github.com/HungLo2020/MattMC/tree/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/biome
[worldgen]: https://github.com/HungLo2020/MattMC/tree/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/levelgen
[data]: https://github.com/HungLo2020/MattMC/tree/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data
[pupa]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/item/ItemLeafcutterPupa.java#L18-L36
[anthill]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/block/BlockLeafcutterAnthill.java#L30-L39
[attributes]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityLeafcutterAnt.java#L130-L132
[attribute-binding]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L190
[queen-state]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityLeafcutterAnt.java#L464-L493
[melee-damage]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1292-L1321
[entity]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L1273-L1279
[dimensions]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityLeafcutterAnt.java#L68-L70
[dimension-select]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityLeafcutterAnt.java#L152-L154
[climbing]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityLeafcutterAnt.java#L253-L323
[forage-start]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/ai/LeafcutterAntAIForageLeaves.java#L25-L38
[forage-cut]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/ai/LeafcutterAntAIForageLeaves.java#L123-L158
[block-leaves]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/block/leaves.json#L1-L15
[return-arrival]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityLeafcutterAnt.java#L684-L696
[caravan]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/ai/LeafcutterAntAIFollowCaravan.java#L24-L74
[offspring]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityLeafcutterAnt.java#L587-L595
[lure]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityLeafcutterAnt.java#L88-L96
[retaliation]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityLeafcutterAnt.java#L737-L755
[hurt-goal]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/goal/target/HurtByTargetGoal.java#L34-L76
[creative-target]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityLeafcutterAnt.java#L104-L109
[melee-dispatch]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/goal/MeleeAttackGoal.java#L127-L137
[bite-helper]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityLeafcutterAnt.java#L629-L632
[anger-hook]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityLeafcutterAnt.java#L378-L402
[server-hook]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L657-L675
[animal-hook]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Animal.java#L53-L60
[target-clear]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/goal/target/TargetGoal.java#L37-L88
[return-search]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityLeafcutterAnt.java#L634-L669
[anteater-target]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityAnteater.java#L430-L445
[anteater-capture]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityAnteater.java#L272-L302
[raid-targets]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/ai/AnteaterAIRaidNest.java#L129-L158
[insects]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/insect_items.json#L1-L8
[items]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java
[anteater-food]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/anteater_foodstuffs.json#L1-L8
[tag-entry]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/tags/TagEntry.java#L13-L79
[tag-loader]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/tags/TagLoader.java#L94-L135
[anteater-breeding]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/anteater_breedables.json#L1-L7
[tempt]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/ai/TameableAITempt.java#L18-L42
[tempt-hands]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/goal/TemptGoal.java#L42-L65
[item-leaves]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/leaves.json#L1-L15
[leaf-items]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L268-L278
[ant]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityLeafcutterAnt.java
[save]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityLeafcutterAnt.java#L481-L525
[growth]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/AgeableMob.java#L129-L167
[no-food]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityLeafcutterAnt.java#L60-L70
[animal-food]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Animal.java#L133-L158
[egg-dispatch]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1088-L1103
[egg-baby]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L184
[breeding-rewards]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Animal.java#L205-L228
[persistence]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Animal.java#L121-L129
[loot-lookup]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L3922-L3924
[loot-key]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-fallback]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[kill-credit]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1324-L1338
[death-xp]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1480-L1487
[baby-xp]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L566
[render-registration]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/entity/EntityRenderers.java#L200
[renderer]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/client/render/RenderLeafcutterAnt.java#L16-L116
[leaf-layer]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/client/render/layer/LayerLeafcutterAntLeaf.java#L18-L58
[model]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/client/model/ModelLeafcutterAnt.java#L19-L43
[queen-model]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/client/model/ModelLeafcutterAntQueen.java#L18-L43
[advanced-model]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/citadel/client/model/AdvancedEntityModel.java#L17-L26
[proxy]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/citadel/client/model/basic/BasicEntityModel.java#L13-L55
[body-submit]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java#L1044-L1072
[collector]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java#L248-L303
[eligibility]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L6879-L6944
[extract]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L9682-L9728
[extract-traversal]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L11943-L11961
[traversal]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/model/geom/ModelPart.java#L151-L168
