# Invisibility, Glowing, and Nausea

**Invisibility changes detection and body rendering; Glowing supplies an outline; Nausea has a current rendering limitation.** These are separate statuses: Glowing does not remove Invisibility, and a presentation setting does not cure Nausea. This guide covers the checked MattMC behavior, including the active Java-to-Rust renderer, rather than assuming every inherited visual effect is implemented. [Effect state][effect-map] · [Invisible state][invisible-state] · [Glowing state][glow-state] · [Current render entry][active-entry]

Times use **20 game ticks per second**. Level I means amplifier `0`. Source factors and timings describe the checked branches, not measured detection distances, guaranteed hit results, or a tested final image on every model and shader pack. [Effect constructors][effect-constructors]

## Invisibility

Effect ID: `minecraft:invisibility`.

### Detection and equipment

Invisibility reduces the distance used by AI checks that test an entity's visibility. It does **not** make an entity universally untargetable. The checked targeting helper multiplies its configured range by the target's visibility factor, then uses a **minimum range of 2 blocks**; line-of-sight and other target rules still apply. Some brain sensors skip Invisibility testing for an already remembered attack target. A [Warden](../mobs/Warden.md#vibrations-smell-and-touch) has separate sensing rules. [Visibility factor][visibility-factor] · [Range and sight checks][target-range] · [Remembered targets][target-memory]

For a non-sneaking target without a relevant mob-head disguise, the Invisibility factor is **0.7 × armor-slot coverage**, with coverage clamped to at least **0.1**. The four counted slots are head, chest, legs, and feet; the code counts occupied slots, not armor points or material quality. [Coverage calculation][armor-coverage] · [Slot types][armor-slots] · [Visibility calculation][visibility-factor]

| Occupied armor slots | Invisibility factor before the target helper's minimum range |
| --- | --- |
| None | 0.07 |
| One | 0.175 |
| Two | 0.35 |
| Three | 0.525 |
| Four | 0.70 |

These are range multipliers for that helper, not chances to be spotted. Sneaking contributes a further **0.8** factor, and certain matching mob-head disguises add their own factor. Held items are not counted by this armor-coverage calculation. Higher Invisibility levels do not improve its presence-based AI reduction. [Complete visibility calculation][visibility-factor] · [Sneak gate][sneak-gate] · [Coverage][armor-coverage]

Invisibility I also reduces the ordinary waypoint-transmit attribute to zero. The server untracks a living entity's locator waypoint when this attribute is nonpositive. This concerns the built-in locator system, not a promise to hide every modded map or marker. [Effect modifier][invisibility-register] · [Modifier application][attribute-install] · [Waypoint update][waypoint-update] · [Transmit gate][waypoint-gate]

### Body rendering and its limits

For the ordinary living-body renderer, an entity invisible to the viewer supplies no normal base-body draw unless Glowing selects its separate outline route. Spectator viewers and teammates allowed to see friendly invisible entities instead select a translucent body. These choices reach the active semantic model collector and Rust mesh transport; they are not merely unused render-type registrations. [Viewer exceptions][invisible-viewer] · [Body selection][body-select] · [Render-type choice][body-material] · [Current entity traversal][entity-traversal] · [Semantic collector][model-collector]

**Do not rely on the potion to hide equipped armor.** The player renderer installs an armor layer and continues its layers for nonspectators even when the base body is invisible. The armor layer submits matching equipped assets separately through the current native model route. Individual layers and models still have their own eligibility and asset requirements; this is not verification of every mob, cosmetic, held item, or shader-pack appearance. [Player layers][player-layers] · [Separate layer dispatch][layer-dispatch] · [Armor slots and submission][armor-layer] · [Armor material producer][armor-producer] · [Native mesh producer][model-producer] · [Frame transport][frame-transport] · [Rust decoding][mesh-decode]

With **Glowing and Invisibility together**, a body invisible to the viewer is copied for the outline mask only. Rust excludes that outline-only mesh from ordinary color batches, while the dedicated outline pass can still use it. This does not cancel Invisibility's AI factor. The rule depends on a successfully extracted, submitted model, not just the status icon being present. [Invisible-outline choice][body-select] · [Active semantic branch][outline-semantic] · [Outline collector][outline-collector] · [Outline-only producer][outline-producer] · [Rust color exclusion][outline-exclusion] · [Outline consumer][outline-consumer] · [Separate AI calculation][visibility-factor]

### Invisibility sources

- **Brewing:** make [Night Vision](VisionEffects.md#night-vision) from Awkward Potion and Golden Carrot, then add [Fermented Spider Eye](../items/FermentedSpiderEye.md). The ordinary result is **Invisibility I for 3,600 ticks / 3 minutes**. Add Redstone for **9,600 ticks / 8 minutes**, or convert extended Night Vision directly. No Glowstone-strengthened Invisibility mixture is registered. [Mixes][invisibility-brew] · [Durations][invisibility-potions] · [Complete brewing list][brew-all]
- **Selected Wandering Trader offer:** a trader can sell one extended Invisibility potion for **5 Emeralds**. It is a selected offer, not a guaranteed trade from every trader. See [Wandering Trader](../mobs/WanderingTrader.md#trading) for its full offer pools. [Offer][invisibility-trade] · [Trade selection][trader-selection] · [Random offer choice][trade-random]
- **Mob encounters:** an [Illusioner](../mobs/Illusioner.md#invisibility-and-blindness) can cast **Invisibility I for 1,200 ticks / 60 seconds** on itself. A qualifying Hard-difficulty [Spider spawn group](../mobs/Spider.md#riders-and-special-effects) can receive Invisibility I with infinite duration. A [Wandering Trader](../mobs/WanderingTrader.md#behavior) drinks the ordinary three-minute potion when its outside-light condition is dark and drinks Milk when bright. These are effects on the mobs, not player rewards. [Illusioner spell][illusioner-invisibility] · [Spider application][spider-apply] · [Spider choices][spider-choices] · [Trader goals][trader-goals] · [Use-item goal][use-item]

## Glowing

Effect ID: `minecraft:glowing`.

Glowing supplies an entity outline using its **scoreboard team color**, or **white** when no team color is available. The effect's registration color is not the chosen outline color. Higher effect levels do not change the inspected presence-based outline selection. [Status synchronization][glow-sync] · [Client flag][entity-glow] · [Client selection][glow-selection] · [Color extraction][outline-color] · [Team fallback][team-color]

For submitted entity meshes, the active native renderer collects their outline colors, draws a mask with **depth testing disabled**, and composites the processed outline into the world image. Ordinary world depth therefore does not occlude this mask, providing the checked through-obstruction outline route. The effect does not make unloaded entities appear: client entity selection, distance/culling, copied geometry and asset admission still precede that pass. No in-game outline or shader comparison was performed. [Active traversal][entity-traversal] · [Entity selection][entity-selection] · [Wire encoding][mesh-encode] · [Rust decoding][mesh-decode] · [Mask planning][outline-consumer] · [Mask pipeline][outline-pipeline] · [Depth policy][depth-policy] · [Vulkan state][vulkan-depth] · [Built-in composition][outline-compose] · [Shader-source composition][outline-source-compose]

A glow can also come from the separate entity `Glowing` flag or the spectator player-outline control. Removing the **status effect** does not remove those other causes. This matters when Milk or `/effect clear` has removed the status but an outline remains. [Persistent flag][entity-glow] · [Living-entity combination][glow-state] · [Spectator control][glow-selection]

### Glowing sources

- **Spectral Arrow:** the ordinary projectile requests **Glowing I for 200 ticks / 10 seconds** in its post-hurt callback. That callback follows an accepted arrow hit and is not a guarantee that every collision affects every entity. Craft **two Spectral Arrows** from one Arrow surrounded orthogonally by **four Glowstone Dust**. [Crafting][spectral-recipe] · [Item projectile][spectral-item] · [Accepted-hit caller][arrow-hit] · [Effect and default duration][spectral-effect]
- **Bell resonance:** eligible cached Raiders receive **Glowing I for 60 ticks / 3 seconds** after the resonance completes. This is not an immediate effect on everything near a ring; see [Bell](../blocks/Bell.md#revealing-nearby-raiders) for trigger, range and timing. [Server action][bell-server] · [Raider filter and grant][bell-glow]
- **Blue Jay song:** when a song starts on the server, the bird applies **Glowing I for 1,200 ticks / 60 seconds** to loaded living entities in its checked monster category and scan box. The application is a startup scan, not a continuous detector. See [Blue Jay](../mobs/BlueJay.md#seeds-following-and-song) for seeds, the scan area and following behavior. Its ordinary Glowing grant does not assign a special blue team color. [Song trigger][jay-song] · [Category][jay-filter] · [Scan and grant][jay-glow] · [Outline color rule][team-color]

There is **no ordinary Glowing potion or brewing recipe** in the checked registry. Spectral Arrows use their own projectile grant rather than the ordinary tipped-arrow duration scale. [Potion registry][potions-all] · [Mix list][brew-all] · [Spectral grant][spectral-effect]

## Nausea

Effect ID: `minecraft:nausea`.

Nausea's inspected visual input depends on its duration blend, not its effect level. It builds toward full strength at **1/150 per client tick**, begins fading when **60 ticks or less remain**, and fades toward zero at **1/20 per tick**. Thus a fresh short application can start fading before it reaches full intensity. These are presentation parameters, separate from the remaining status duration. [Configuration][nausea-registration] · [Blend calculation][nausea-blend]

### Nausea sources

| Source | Nausea grant | What to expect |
| --- | --- | --- |
| Eating [Pufferfish](../items/Pufferfish.md) | **I, 300 ticks / 15 seconds** | The food also grants Poison II and Hunger III; the living fish's sting is a separate effect source |
| [Closed Eyeblossom Suspicious Stew](../blocks/Eyeblossoms.md#forms-and-exact-ids) | **I, 140 ticks / 7 seconds** | Eat the prepared stew, rather than the flower |
| An active [Skunk spray](../blocks/SkunkSpray.md#effects-active-spray-versus-coating) | **I, 300 ticks / 15 seconds per grant** | The mob's attack applies Nausea; a leftover surface coating is not a Nausea contact effect |

[Pufferfish binding][nausea-food-binding] · [Food effect list][nausea-pufferfish] · [Stew recipe][nausea-stew] · [Stew consumption][stew-eat] · [Level-I stew][stew-level] · [Skunk attack][nausea-skunk]

The resulting mob also receives **Nausea I for 200 ticks / 10 seconds** when a [Zombie Villager](../mobs/ZombieVillager.md) is cured, a [Hoglin](../mobs/Hoglin.md) becomes a Zoglin, or a [Piglin](../mobs/Piglin.md) becomes a Zombified Piglin. These grants do not give a nearby player Nausea. There is **no ordinary Nausea potion or brewing recipe** in the checked registry. [Villager conversion][nausea-villager] · [Hoglin conversion][nausea-hoglin] · [Piglin conversion][nausea-piglin] · [Potion registry][potions-all] · [Brewing list][brew-all]

### Distortion Effects setting

The English Accessibility option is **Distortion Effects**, with a default of **100%**. Below 100%, the current HUD requests a green Nausea overlay, scaled by the Nausea blend multiplied by the unused portion of the setting. Lowering the setting therefore strengthens this overlay; **Off** uses the full current blend. A portal overlay takes precedence, and hiding the HUD also hides the Nausea overlay. Changing this option does not remove the status. [Option and default][nausea-option] · [Menu][nausea-menu] · [English label][nausea-label] · [Overlay conditions][nausea-overlay] · [HUD gate][nausea-hud]

The green overlay reaches the active Rust renderer as a tinted image with additive blending. However, **the usual rotating world-view distortion is not wired into the inspected active rendering path**: the old spinning timer is updated but has no rendering consumer, and the active camera matrices omit that transform. Do not treat the inherited tooltip as proof of a working screen warp, especially at 100%, where this green overlay is not requested. This is a source-path limitation, not a visual gameplay test or a claim about every custom shader pack. [Image producer][nausea-overlay-draw] · [Java transport][nausea-overlay-transport] · [Whole-frame submission][frame-transport] · [Rust blend selection][nausea-rust-blend] · [Active camera matrices][nausea-camera] · [Unused timer][nausea-timer]

## Delivery, clearing, and commands

The server initializes and executes the Invisibility brewing chain above. Drinkable, splash, lingering and tipped-arrow delivery are separate: use [shared potion delivery](MovementEffects.md#delivery-changes-duration) for splash distance and shorter cloud/arrow duration scales. The registered ordinary Invisibility variants are also listed in MattMC's [inventory item browser](../mechanics/InventoryBrowser.md), available in Survival and Creative separately from gathering ingredients. Glowing and Nausea registrations alone do not add ordinary bottles to it. [Server brewing][brew-server] · [Stand processing][brew-stand] · [Browser variants][browser-potions] · [Enabled types][browser-types] · [Potion registry][potions-all]

**Milk removes all three statuses**, including beneficial Invisibility. Honey removes Poison only, so it is not a cure for these effects; after eating Pufferfish, clearing only Poison can leave Hunger and Nausea. A qualifying Totem activation clears existing statuses before adding its replacement effects. A nearby source can apply a fresh effect after clearing. [Milk consumption][milk] · [All-effect callback][milk-clear] · [Removal][effect-clear] · [Honey][honey] · [Totem sequence][totem]

Different effects coexist; repeated applications of the same effect do not simply add levels and durations. Stronger or longer applications and hidden weaker effects follow the [shared replacement rules](MovementEffects.md#refreshing-clearing-and-limits). Finite durations count down in game ticks; infinite duration does not expire normally. [Replacement][effect-update] · [Infinite duration][effect-infinite]

With command permission level 2, for example:

```mcfunction
/effect give @s minecraft:invisibility 60 0
/effect give @s minecraft:glowing 10 0
/effect clear @s minecraft:nausea
```

The duration arguments above are seconds; amplifier `0` means level I. These commands remain separate from ordinary item-browser access. Clearing Glowing by command removes the status, not an entity's separate `Glowing` flag. [Permission and arguments][command-args] · [Seconds conversion][command-seconds] · [Specific removal][command-clear] · [Separate flag][entity-glow]

## Related pages

- [Status effects](Effects.md), [Vision effects](VisionEffects.md), and [Movement effects](MovementEffects.md)
- [Brewing](../brewing/Brewing.md), [Pufferfish](../items/Pufferfish.md), and [Suspicious Stew](../items/SuspiciousStew.md)
- [Bell](../blocks/Bell.md), [Blue Jay](../mobs/BlueJay.md), and [Skunk Spray](../blocks/SkunkSpray.md)

## Sources and verification

Source-reviewed on **2026-10-03** at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`. Checked effect application/removal, the described sources, AI visibility, selected living-body/equipment producers, Java-to-Rust outline and Nausea-overlay transport, native consumers, and the accessibility setting. **No in-game detection, rendering, shader-pack comparison, combat, brewing, feeding, trade, command, or timing test was run.** Custom data, entity overrides, model support, camera choice and server timing can change outcomes. The Nausea distortion limitation describes the inspected active source path, not a measured symptom on every installation.

[active-entry]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/Minecraft.java#L1355-L1370
[armor-coverage]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2128-L2143
[armor-layer]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/entity/layers/HumanoidArmorLayer.java#L38-L75
[armor-producer]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/entity/layers/EquipmentLayerRenderer.java#L74-L99
[armor-slots]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/EquipmentSlot.java#L12-L20
[arrow-hit]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/AbstractArrow.java#L415-L436
[attribute-install]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffect.java#L168-L175
[bell-glow]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/BellBlockEntity.java#L161-L169
[bell-server]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/BellBlockEntity.java#L87-L88
[body-material]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java#L1093-L1110
[body-select]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java#L230-L248
[brew-all]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L133-L190
[brew-server]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/MinecraftServer.java#L336-L340
[brew-stand]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/BrewingStandBlockEntity.java#L170-L183
[browser-potions]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1756-L1770
[browser-types]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2207-L2213
[command-args]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/commands/EffectCommands.java#L33-L90
[command-clear]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/commands/EffectCommands.java#L226-L232
[command-seconds]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/commands/EffectCommands.java#L153-L176
[depth-policy]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/rust/render/worldrender/passes/pipelines.rs#L109-L115
[effect-clear]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L943-L953
[effect-constructors]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L49-L75
[effect-infinite]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L168-L188
[effect-map]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L982-L1000
[effect-update]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L123-L170
[entity-glow]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Entity.java#L2593-L2604
[entity-selection]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/LevelRenderer.java#L466-L498
[entity-traversal]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/LevelRenderer.java#L625-L668
[frame-transport]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/vulkanic/gui/RustGalFrameCoordinator.java#L933-L995
[glow-selection]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/Minecraft.java#L2692-L2694
[glow-state]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3637-L3639
[glow-sync]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L842-L885
[honey]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumables.java#L16-L20
[illusioner-invisibility]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/Illusioner.java#L248-L267
[invisibility-brew]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L146-L150
[invisibility-potions]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/Potions.java#L15-L22
[invisibility-register]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffects.java#L62-L71
[invisibility-trade]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L730-L748
[invisible-state]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L842-L885
[invisible-viewer]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Entity.java#L2610-L2617
[jay-filter]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityBlueJay.java#L67-L69
[jay-glow]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityBlueJay.java#L299-L305
[jay-song]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityBlueJay.java#L276-L290
[layer-dispatch]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java#L1065-L1084
[mesh-decode]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/rust/render/bridge/world/whole_frame.rs#L1071-L1094
[mesh-encode]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/vulkanic/bridge/VulkanicGalBridge.java#L2631-L2657
[milk]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumables.java#L64
[milk-clear]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/consume_effects/ClearAllStatusEffectsConsumeEffect.java#L20-L22
[model-collector]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java#L262-L302
[model-producer]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L9643-L9725
[nausea-blend]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L371-L397
[nausea-camera]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/GameRenderer.java#L784-L817
[nausea-food-binding]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1672
[nausea-hoglin]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/hoglin/Hoglin.java#L267-L269
[nausea-hud]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/gui/Gui.java#L236-L247
[nausea-label]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/assets/minecraft/lang/en_us.json#L6198-L6199
[nausea-menu]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/gui/screens/options/AccessibilityOptionsScreen.java#L24-L39
[nausea-option]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/Options.java#L654-L662
[nausea-overlay]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/gui/Gui.java#L343-L354
[nausea-overlay-draw]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/gui/Gui.java#L1415-L1427
[nausea-overlay-transport]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/vulkanic/gui/RustGalGuiRenderer.java#L1724-L1760
[nausea-piglin]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/piglin/AbstractPiglin.java#L101-L107
[nausea-pufferfish]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumables.java#L49-L57
[nausea-registration]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffects.java#L57
[nausea-rust-blend]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/rust/render/guirender/frontend/resources/groups.rs#L46-L85
[nausea-skunk]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySkunk.java#L267-L304
[nausea-stew]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_closed_eyeblossom.json#L1-L23
[nausea-timer]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/GameRenderer.java#L326-L343
[nausea-villager]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/ZombieVillager.java#L220-L234
[outline-collector]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java#L227-L244
[outline-color]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/entity/EntityRenderer.java#L268-L271
[outline-compose]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/rust/render/worldrender/vanilla/recording.rs#L2526-L2563
[outline-consumer]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/rust/render/worldrender/features/outline/mod.rs#L1224-L1271
[outline-exclusion]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/rust/render/worldrender/geometry/batching.rs#L954-L965
[outline-pipeline]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/rust/render/worldrender/vanilla/resources/outline.rs#L293-L316
[outline-producer]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L9353-L9370
[outline-semantic]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java#L1008-L1015
[outline-source-compose]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/rust/render/worldrender/source/submit.rs#L1384-L1418
[player-layers]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/entity/player/AvatarRenderer.java#L49-L70
[potions-all]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/Potions.java#L10-L80
[spectral-effect]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/SpectralArrow.java#L16-L50
[spectral-item]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/SpectralArrowItem.java#L17-L25
[spectral-recipe]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/spectral_arrow.json#L1-L17
[spider-apply]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/Spider.java#L158-L168
[spider-choices]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/Spider.java#L202-L217
[stew-eat]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/SuspiciousStewEffects.java#L38-L42
[stew-level]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/SuspiciousStewEffects.java#L58-L72
[target-memory]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/sensing/Sensor.java#L64-L83
[target-range]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/targeting/TargetingConditions.java#L60-L93
[team-color]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Entity.java#L323-L325
[totem]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/DeathProtection.java#L25-L40
[trader-goals]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/WanderingTrader.java#L60-L80
[trader-selection]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/WanderingTrader.java#L133-L140
[use-item]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/goal/UseItemGoal.java#L25-L44
[visibility-factor]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L887-L915
[vulkan-depth]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/rust/render/vulkanic/backends/vulkan/resources.rs#L2523-L2530
[waypoint-gate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3717-L3727
[waypoint-update]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1117-L1125
[sneak-gate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Entity.java#L2557-L2559
[trade-random]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L232
