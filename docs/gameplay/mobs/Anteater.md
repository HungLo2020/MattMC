# Anteater

The **Anteater** is a neutral animal with **20 health points (10 hearts)** and **6 base attack damage**. You can breed it with Honey Bottles or Honeycomb, and it can swallow live [Leafcutter Ants](LeafcutterAnt.md). It can produce Sugar while digging Dirt. Its extra food-based healing has a bundled-data limitation. [Attributes][attributes] · [Active attribute registration][attribute-registration] · [Breeding food][breedables] · [Ant swallowing][swallow]

## Obtaining

For a confirmed setup route, request an [Anteater Spawn Egg](../items/AnteaterSpawnEgg.md) from the [inventory item browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item) in **Creative**, then use it on a block with room for the animal. The egg is registered and listed in the Spawn Eggs category. The catalog is also visible in Survival, but ordinary Survival insertion requests do not pass the server's mode gate. See [Spawn eggs](../items/SpawnEggs.md) for placement, consumption, Dispensers and spawner configuration. [Egg registration][egg] · [Category entry][egg-list] · [Egg placement][egg-placement]

**No natural Anteater acquisition route was verified in this snapshot.** No Anteater biome spawn entry or spawn-placement registration was found in the inspected source and bundled data. The class contains a brightness-above-8 helper and a spawn-roll check, but neither supplies a biome encounter route. Do not choose a biome or wait for daylight expecting those helpers alone to make Anteaters appear. [Spawn helpers][spawn-helpers] · [Spawn placements][placements] · [Biome sources][biomes] · [Bundled data][data]

## Behavior

Anteaters wander, look at players and retaliate when attacked. An injured baby can alert nearby adults, while the baby itself does not run the melee attack goal. Adults use claw slashes against other combat targets; **6 is the base damage attribute**, not a guaranteed amount after defenses and damage scaling. [Registered goals][goals] · [Baby retaliation][retaliation] · [Adult attacks][melee] · [Slash damage][slash]

### Live ants and healing

An adult's ant-pursuit goal selects **non-queen Leafcutter Ants** while the Anteater is not angry, standing or already carrying an ant on its tongue. Once close, it extends its tongue. Swallowing an ant heals **6 health points (3 hearts)**. This route uses live entities and remains separate from the broken insect-item tag below. [Pursuit selection][ant-target] · [Approach][melee] · [Capture and healing][swallow]

**Keep all ants you want to preserve away from Anteaters.** The separate tongue-capture check does not repeat the pursuit goal's queen exclusion or adult-Anteater requirement: a queen, or proximity to a baby Anteater, is not a reliable safeguard. A successful capture removes the ant. The capture search uses an area around the Anteater, so this review does not establish a safe pen spacing. [Capture check][swallow]

### Digging Dirt

Adults can approach **Dirt or Coarse Dirt** and perform repeated digging or tongue actions. Each successful action on one of those blocks drops **1–2 Sugar** and leaves the target block in place. This is a living mob's production behavior, not death loot; the amount is per action, not per visit. A raid ends with a randomized cooldown, so these values are not a tested farm output rate. [Goal and cooldown][raid-start] · [Actions][raid-actions] · [Sugar output][sugar] · [Cooldown reset][cooldown]

The tongue action also provokes nearby Leafcutter Ants and enters the swallowing state. That state can heal **6 health points (3 hearts)** even without capturing a live ant. It does **not** need or harvest an actual [Leafcutter Anthill or Chamber](../blocks/LeafcutterNests.md). Those nest blocks are absent from the raid's target check, and this simplified action does not complete their missing colony-storage or fungus-production behavior. [Tongue action and targets][raid-targets] · [Swallowing recovery][swallow]

## Feeding, breeding and keeping

Use **Honey Bottles or Honeycomb on two ready adults** to put them in love. Their independent breeding-food tag contains both registered items and has no insect-tag dependency. The active breeding goal and offspring factory create a baby Anteater. Feeding either item to a baby accelerates its growth. [Breeding tag][breedables] · [Honey items][honey-items] · [Food check][food-check] · [Animal food interaction][animal-food] · [Breeding goal][breed-goal] · [Offspring][offspring]

After breeding, each parent has a **6,000-tick cooldown**, about **5 minutes at 20 TPS**. A new baby grows over **24,000 loaded entity ticks**, about **20 minutes at 20 TPS**, before feeding adjustments. A matching spawn egg used on a living Anteater can also create a baby; that helper does not award ordinary breeding XP. [Breeding and reward][breeding] · [Growth][growth] · [Egg interaction][egg-dispatch] · [Egg baby creation][egg-baby]

Babies can approach and ride an available adult Anteater, then dismount when grown. This does not make the adult a player-controlled mount. Anteaters inherit the animal rule that prevents ordinary distance despawning; feeding is not required for that protection and does not establish an owner or sitting command. **Holding food does not lure them**: the food-following goal is commented out. Use an enclosure rather than relying on a Honeycomb trail. [Ride-parent goal][ride] · [Dismount][dismount] · [Animal persistence][persistence] · [Class and goals][goals] · [Interaction][interaction]

### Why ordinary healing food does not work

**Honey breeding works, but the extra hand-fed healing/calming and loose-insect feeding routes fail with the bundled tags.** The insect tag requires Maggot and Mosquito Larva, which are not registered items, alongside the valid [Leafcutter Ant Pupa](../items/LeafcutterAntPupa.md). Required missing entries reject the whole tag. The separate healing-food tag includes that tag as a required dependency, so it also fails as a whole, including its Honey Bottle and Honeycomb entries. This is tracked in [issue #804](https://github.com/HungLo2020/MattMC/issues/804). [Insect tag][insects] · [Item registry][items] · [Healing-food tag][foodstuffs] · [Required-entry rules][tag-entry] · [Whole-tag rejection][tag-loader]

Do not spend Pupa or honey expecting the default extra **4-health-point (2-heart) heal and anger clearing**. Those effects belong to the tag-dependent hand interaction or held-insect consumption. A data pack that repairs the tags can change those routes; ordinary honey breeding and live-ant healing do not need that repair. No active food lure is supplied by merely repairing a tag. [Hand interaction][interaction] · [Held-insect consumption][insect-eating] · [Insect targeting][insect-target] · [Goals][goals]

Do not rely on an exact automatic calming time after provoking an Anteater. Its imported anger updater uses an older method signature and does not override the current server-AI hook. Ordinary combat target-loss and clearing paths still exist; this mismatch is not proof of permanent aggression. [Imported updater][anger-hook] · [Active server hook][server-hook] · [Animal hook][animal-hook] · [Target loss][target-loss] · [Anger clearing][anger-clear]

## Drops and experience

No bundled `minecraft:entities/anteater` loot table or Anteater-specific death-drop override was found. Its default loot lookup therefore reaches the empty-table fallback: there is **no defined species item drop or Looting bonus** in these defaults. Sugar comes from the Dirt actions above. Custom data packs or equipment are separate possible sources of drops. [Default loot key][loot-key] · [Bundled data][data] · [Empty-table fallback][loot-fallback] · [Anteater class][anteater] · [Death handling][death]

Adults inherit **1–3 base XP** when ordinary death-XP conditions are met: recent player or tamed-Wolf kill credit, `doMobLoot` enabled and experience not already consumed. Babies do not drop death XP. Successful ordinary breeding awards **1–7 XP** with `doMobLoot` enabled. [Animal XP][animal-xp] · [Kill credit][credit] · [Death-XP gates][death-xp] · [Baby gate][baby-xp] · [Breeding reward][breeding]

## Appearance and integration

Naming an Anteater **Peter** does not select the alternate skin in this snapshot. The class recognizes names containing `peter`, `petr` or `zot`, ignoring case, but the active renderer always chooses the ordinary texture. The bundled alternate image is not evidence of a working name-based appearance change. [Name helper][name] · [Texture selection][renderer]

The inspected native rendering path also shares the Citadel model-transport gap tracked in [issue #803](https://github.com/HungLo2020/MattMC/issues/803). For an admitted visible ordinary-body submission with a readable texture and valid transform, the native extractor receives an empty model-part tree and can reach its empty-model exception. **This is a source-identified limitation, not a reproduced crash or invisibility report.** It does not establish the outcome of every frame or every imported mob. [Registered renderer][renderer-registration] · [Anteater model][model] · [Model inheritance][advanced-model] · [Citadel root][citadel] · [Native admission][render-submit] · [Extraction][extraction] · [Traversal][traversal]

## Notes

* The mob is registered as `minecraft:anteater`, uses `MobCategory.CREATURE`, and has registered adult dimensions of **1.2 blocks wide by 0.9 blocks high**. These are entity dimensions, not a tested enclosure minimum. [Registration][registration]
* Its spawn egg is `minecraft:anteater_spawn_egg`. [Egg registration][egg]
* Its entity class is `EntityAnteater`, from bundled Alex's Mobs content integrated into MattMC. [Entity implementation][anteater]

## Sources and verification

Source-reviewed at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6` on 2026-10-03. Checked active registration, egg acquisition, spawn references, attribute binding, AI calls, food-tag dependencies, inherited breeding/persistence/loot behavior and the species' renderer chain. No in-game spawning, combat, feeding, breeding, ant capture, Dirt production, drops, timing or rendering test was performed. Data packs and custom entity data can change these defaults.

Related: [Anteater Spawn Egg](../items/AnteaterSpawnEgg.md) · [Leafcutter Ant](LeafcutterAnt.md) · [Leafcutter ant nests](../blocks/LeafcutterNests.md) · [Leafcutter Ant Pupa](../items/LeafcutterAntPupa.md) · [Mobs](Mobs.md)

[attributes]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityAnteater.java#L76-L82
[attribute-registration]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L226
[breedables]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/anteater_breedables.json#L1-L7
[swallow]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityAnteater.java#L272-L302
[egg]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1944
[egg-list]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2075
[egg-placement]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L76-L103
[spawn-helpers]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityAnteater.java#L84-L90
[placements]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[biomes]: https://github.com/HungLo2020/MattMC/tree/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/biome
[data]: https://github.com/HungLo2020/MattMC/tree/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data
[goals]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityAnteater.java#L46-L106
[retaliation]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/ai/AnimalAIHurtByTargetNotBaby.java#L8-L31
[melee]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityAnteater.java#L448-L494
[slash]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityAnteater.java#L318-L338
[ant-target]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityAnteater.java#L410-L445
[raid-start]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/ai/AnteaterAIRaidNest.java#L57-L76
[raid-actions]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/ai/AnteaterAIRaidNest.java#L96-L117
[sugar]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/ai/AnteaterAIRaidNest.java#L35-L54
[cooldown]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityAnteater.java#L343-L346
[raid-targets]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/ai/AnteaterAIRaidNest.java#L129-L158
[honey-items]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L2462-L2482
[food-check]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityAnteater.java#L128-L130
[animal-food]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/animal/Animal.java#L133-L158
[breed-goal]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/goal/BreedGoal.java#L20-L80
[offspring]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityAnteater.java#L361-L365
[breeding]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/animal/Animal.java#L205-L228
[growth]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/AgeableMob.java#L129-L167
[egg-dispatch]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Mob.java#L1088-L1103
[egg-baby]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L184
[ride]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/ai/AnimalAIRideParent.java#L19-L87
[dismount]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityAnteater.java#L262-L270
[persistence]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/animal/Animal.java#L121-L124
[interaction]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityAnteater.java#L205-L222
[insects]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/insect_items.json#L1-L8
[items]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java
[foodstuffs]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/anteater_foodstuffs.json#L1-L8
[tag-entry]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/tags/TagEntry.java#L13-L79
[tag-loader]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/tags/TagLoader.java#L94-L135
[insect-eating]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityAnteater.java#L303-L316
[insect-target]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityAnteater.java#L387-L403
[anger-hook]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityAnteater.java#L199-L203
[server-hook]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Mob.java#L657-L675
[animal-hook]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/animal/Animal.java#L53-L60
[target-loss]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/goal/target/TargetGoal.java#L37-L88
[anger-clear]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/NeutralMob.java#L79-L101
[loot-key]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-fallback]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[anteater]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityAnteater.java
[death]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1491
[animal-xp]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/animal/Animal.java#L126-L129
[credit]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1324-L1338
[death-xp]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1480-L1487
[baby-xp]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L566
[name]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityAnteater.java#L414-L421
[renderer]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/client/render/RenderAnteater.java#L11-L54
[renderer-registration]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/entity/EntityRenderers.java#L254
[model]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/client/model/ModelAnteater.java#L14-L40
[advanced-model]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/citadel/client/model/AdvancedEntityModel.java#L17-L26
[citadel]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/citadel/client/model/basic/BasicEntityModel.java#L13-L56
[render-submit]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java#L283-L302
[extraction]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L9643-L9689
[traversal]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/model/geom/ModelPart.java#L150-L169
[registration]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/EntityType.java#L1143-L1145
