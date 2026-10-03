# Rain Frog

The **Rain Frog** is a small passive mob with **6 health points (3 hearts)**. It can burrow into sand, and using a Shovel on it provides a species-specific way to prevent distance despawning. Its ordinary food and weather-control routes have integration limits in this snapshot. [Attributes and goals][frog-goals] · [Active attribute registration][attributes] · [Shovel interaction][shovel]

## Obtaining

Request a [Rain Frog Spawn Egg](../items/RainFrogSpawnEgg.md) through the [inventory item browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item), in **Creative**, then use it on a block with suitable space. The egg is registered and listed in the ordinary Spawn Eggs category. See [Spawn eggs](../items/SpawnEggs.md) for placement, consumption, Dispensers and spawner configuration. [Egg registration][egg] · [Category entry][category] · [Browser list][browser] · [Server request handling][browser-server]

**No natural Rain Frog encounter route was found in the bundled biome, structure or spawner data.** The source contains a rain/thunder-and-ground spawn helper, but no active caller or spawn-placement registration was found for it; its ground tag is also absent from the bundled data. Waiting for rain in a particular biome is therefore not an established acquisition method. Its active per-mob spawn-rule override returns true, which does not add it to a biome's spawn list. [Bundled data][data] · [Spawn helper and override][frog-spawn] · [Spawn placements][placements] · [Natural selection][natural]

An egg-configured [Monster Spawner](../blocks/MonsterSpawner.md) is a separate deliberate setup route. Its normal collision, nearby-mob and activation checks still apply, but the unused Rain Frog helper does not impose a rain requirement on that path. [Spawner checks][spawner] · [Placement defaults][placement-defaults] · [Rain Frog override][frog-spawn]

## Behavior

Rain Frogs wander and look at nearby players; their registered goals contain no combat or retaliation attack. Damage dealt directly by a living creature makes one leave its burrow and enter a short defensive stance. This response does **not** mark it as disturbed for persistence. [Goals][frog-goals] · [Damage response][hurt]

Provide accessible [Sand](../items/Sand.md), [Red Sand](../items/RedSand.md) or [Suspicious Sand](../items/SuspiciousSand.md) if you want to support burrowing. The frog periodically searches nearby sand-tagged blocks, walks to one and stops moving while burrowed. It leaves when that goal ends or the supporting block stops matching. Burrowing changes the frog's state, without removing the sand block, and has no rain requirement. [Burrow goal][burrow] · [Sand tag][sand] · [Movement][travel]

It is immune to **in-wall suffocation damage**, not to all hazards. Keep an enclosure's water escapable: Rain Frog is absent from the bundled underwater-breathing tag and inherits the normal air/drowning checks. Adult, untamed [Caimans](Caiman.md) also include Rain Frog among their prey. [Immunity][immunity] · [Breathing tag][breathing-tag] · [Air checks][air] · [Caiman targeting][caiman] · [Prey tag][caiman-tag]

## Keeping a Rain Frog

**Use a Shovel on a frog that has not yet been disturbed, or on any burrowed Rain Frog.** On the server, this makes it emerge, plays the sand-breaking sound, temporarily delays burrowing and saves its disturbed state. Outside Creative, a damageable Shovel has its damage value increased by one. This interaction does not tame it or give it owner commands. [Interaction][shovel] · [Saved state][save]

A disturbed frog is exempt from ordinary distance despawning, and the flag survives saving and loading. A fresh, undisturbed egg-placed frog is not automatically persistent: without another persistence condition it can despawn beyond **128 blocks**, or randomly beyond **32 blocks** after its inactivity counter exceeds 600 ticks. A used [Name Tag](../items/NameTag.md) is another persistence route. Hitting the frog or merely finding it burrowed does not replace the Shovel interaction. [Species persistence][persistence] · [Despawn checks][despawn] · [Distances][distances] · [Name Tag][name-tag] · [Damage response][hurt]

## Feeding and breeding

**No working ordinary breeding food is supplied by the bundled data.** Both held-item attraction and food interaction require `minecraft:rain_frog_breedables`, whose tag file is absent. The registered breeding goal and offspring factory do not by themselves make an obtainable item valid food. [Food and offspring][food] · [Goals][frog-goals] · [Tag names][tag-names] · [Bundled data][data] · [Animal food interaction][animal-food]

Dropped-insect healing has a different problem. Its tag lists Maggot, Mosquito Larva and [Leafcutter Ant Pupa](../items/LeafcutterAntPupa.md), but **Maggot and Mosquito Larva are not registered items** in the checked source. These are required entries, so the tag loader rejects the entire bundled insect tag; Pupa's valid registration alone does not make it usable here. Do not spend Pupa expecting this default feeding route to work. [Insect tag][insects] · [Item registrations][items] · [Required entries][tag-entry] · [Whole-tag loading][tag-loader]

If a data pack supplies a valid insect tag, the existing dropped-item goal can approach a matching loose item, consume **one item** and heal **2 health points (1 heart)**. Finding that target also interrupts burrowing. This is ground-item feeding, not a hand-fed breeding or taming action. It does not set the disturbed flag. [Frog item response][eating] · [Target selection and consumption][item-goal]

For a baby in the checked defaults, use a **matching Rain Frog Spawn Egg on a living Rain Frog**. The common interaction calls its working offspring factory: the baby copies the clicked frog's variant and starts disturbed, so it has the species' persistence protection. It grows over **24,000 loaded entity ticks**, about **20 minutes at 20 TPS**. No Tadpole or Frogspawn stage is created. A data pack that adds valid breeding food can also reach this offspring factory through ordinary animal breeding. [Egg interaction][egg-dispatch] · [Baby creation][egg-baby] · [Offspring][food] · [Growth][growth] · [Animal breeding][animal-breeding]

## Jukebox and weather limit

The client receives nearby [Jukebox](../blocks/Jukebox.md) playback events and can set Rain Frog dance state. However, the only ordinary playback caller found is client-side, while changing world weather requires the frog's server-side dance countdown. No server caller was found to start that countdown from Jukebox playback. **Do not rely on a Rain Frog as a working rain or clear-weather switch in this snapshot.** [Playback notification][jukebox] · [Dance setup][dance] · [Server weather gate][weather-gate]

The source defines dancing and defensive/burrowing poses, but their visible appearance is subject to the rendering limit below. These are source-defined states, not verified in-game animations. [Render state][renderer] · [Model poses][model-poses]

## Appearance and integration

Ordinary spawn initialization chooses one of **three variant values**, and the renderer selects one of three matching textures. The variant is saved; egg-created babies copy the clicked frog's value. [Initialization and saving][save] · [Texture selection][renderer] · [Offspring][food]

The Rain Frog's actual model follows the Citadel geometry path covered by [shared rendering issue #803](https://github.com/HungLo2020/MattMC/issues/803). For its selected ordinary body on the native whole-frame route, the inherited model root has no geometry for the active extraction visitor to copy. Successful body rendering, textures and poses are therefore **unverified here**; this is a source-traced compatibility limit, not a reproduced crash or invisibility report. [Renderer registration][renderer-registration] · [Model inheritance][model] · [Citadel root][citadel] · [Direct-texture route][render-submit] · [Native extraction][extraction] · [Geometry traversal][traversal]

## Drops and experience

No bundled `minecraft:entities/rain_frog` loot table or Rain Frog-specific death-drop override was found. The default entity loot key therefore resolves to the empty-table fallback: there is **no defined species item drop**, including a Looting bonus, in these defaults. Custom data packs or equipment can change the broader drop result. [Default loot key][loot-key] · [Bundled data][data] · [Empty-table fallback][loot-fallback] · [Death handling][death]

Adults inherit **1–3 base XP** when ordinary experience-drop conditions are met: recent player or tamed-Wolf kill credit, `doMobLoot` enabled, and experience not already consumed. Babies do not drop death XP. Successful ordinary breeding would award **1–7 XP** with `doMobLoot` enabled, but requires the missing breeding-food route to be supplied; the egg-on-mob helper does not run that breeding reward. [Animal XP][animal-xp] · [Kill credit][credit] · [Death XP gates][death-xp] · [Baby gate][baby-xp] · [Breeding reward][animal-breeding] · [Egg helper][egg-baby]

## Notes

* The mob is registered as `minecraft:rain_frog`, uses `MobCategory.CREATURE`, and has registered adult dimensions of **0.4 blocks wide by 0.4 blocks high**. These are entity dimensions, not a tested enclosure minimum. [Registration][registration]
* Its spawn egg is `minecraft:rain_frog_spawn_egg`. [Egg registration][egg]
* Its entity class is `EntityRainFrog`, from bundled Alex's Mobs content integrated into MattMC. [Entity implementation][frog]

## Sources and verification

Source-reviewed at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6` on 2026-10-03. Checked active registration, browser/egg and spawner paths, biome/structure data, food-tag loading, inherited interaction/offspring/despawn/loot behavior, Jukebox callers and this species' renderer chain. No in-game spawning, burrowing, Shovel use, feeding, breeding, weather, drops or rendering test was performed. Data packs and custom entity data can alter these defaults.

Related: [Rain Frog Spawn Egg](../items/RainFrogSpawnEgg.md) · [Spawn eggs](../items/SpawnEggs.md) · [Frog](Frog.md) · [Mobs](Mobs.md)

[attributes]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L219
[frog]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityRainFrog.java
[frog-goals]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityRainFrog.java#L70-L83
[shovel]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityRainFrog.java#L292-L310
[egg]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1937
[category]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2068
[browser]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[browser-server]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1906
[data]: https://github.com/HungLo2020/MattMC/tree/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data
[frog-spawn]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityRainFrog.java#L85-L92
[placements]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[natural]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L290-L325
[spawner]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/BaseSpawner.java#L84-L174
[placement-defaults]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L64-L84
[hurt]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityRainFrog.java#L238-L247
[burrow]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityRainFrog.java#L405-L463
[sand]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/block/sand.json
[travel]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityRainFrog.java#L312-L332
[immunity]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityRainFrog.java#L257-L264
[breathing-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/entity_type/can_breathe_under_water.json
[air]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L384-L443
[caiman]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityCaiman.java#L132-L136
[caiman-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/entity_type/caiman_targets.json
[save]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityRainFrog.java#L272-L290
[persistence]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityRainFrog.java#L249-L255
[despawn]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Mob.java#L598-L630
[distances]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/MobCategory.java#L6-L29
[name-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/NameTagItem.java#L13-L33
[food]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityRainFrog.java#L143-L156
[tag-names]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/misc/AMTagRegistry.java#L63-L65
[animal-food]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/animal/Animal.java#L133-L158
[insects]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/insect_items.json
[items]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java
[tag-entry]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/tags/TagEntry.java#L13-L79
[tag-loader]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/tags/TagLoader.java#L94-L135
[eating]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityRainFrog.java#L334-L349
[item-goal]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/ai/CreatureAITargetItems.java#L53-L156
[egg-dispatch]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Mob.java#L1054-L1107
[egg-baby]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L184
[growth]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/AgeableMob.java#L129-L167
[animal-breeding]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/animal/Animal.java#L205-L228
[jukebox]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/LevelEventHandler.java#L661-L687
[dance]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityRainFrog.java#L367-L386
[weather-gate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityRainFrog.java#L210-L235
[renderer]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/client/render/RenderRainFrog.java#L10-L40
[model-poses]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/client/model/ModelRainFrog.java#L84-L155
[renderer-registration]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/entity/EntityRenderers.java#L247
[model]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/client/model/ModelRainFrog.java#L11-L81
[citadel]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/citadel/client/model/basic/BasicEntityModel.java#L13-L56
[render-submit]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java#L283-L302
[extraction]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L9643-L9689
[traversal]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/model/geom/ModelPart.java#L150-L169
[loot-key]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-fallback]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[death]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1491
[animal-xp]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/animal/Animal.java#L126-L129
[credit]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1324-L1338
[death-xp]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1480-L1487
[baby-xp]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L566
[registration]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/EntityType.java#L1100-L1105
