# Spyglass

A **Spyglass** narrows your first-person view for looking at distant terrain and mobs. It is reusable, has no normal durability cost, and takes one inventory slot per item. [Use behavior][use] · [Registration][registration]

## Obtaining

Craft one at a [Crafting Table](../blocks/CraftingTable.md) using the [Spyglass recipe in the Amethyst guide](../blocks/Amethyst.md#shard-recipes-and-other-uses). The guide gives the ingredient arrangement and explains how to obtain the Shards. The bundled shaped recipe produces one Spyglass. [Recipe][recipe]

The Spyglass also has a Creative category entry. For MattMC's inventory panel, follow the [inventory browser's mode and permission limits](../mechanics/InventoryBrowser.md#mode-and-permission-limits); seeing it in a Survival catalog does not provide a Survival acquisition route. [Creative entry][creative]

## Usage

With the default hold controls, hold **Use** while carrying the Spyglass, then release Use to stop. You do not need to select a distant mob or block to begin looking. If a nearby chest or other interaction takes the action first, aim away from it. The item can be used from either hand; main-hand and targeted interactions are tried before the off hand. [Use behavior][use] · [Interaction order][input] · [Release control][release-control] · [Server item use][server-use]

A single use lasts up to **1,200 game ticks**, nominally **60 seconds at 20 ticks per second**. Finishing that use returns the same Spyglass; it does not consume it. Releasing Use ends the session early, and changing the used hand's item to a different item also stops it. The 60 seconds describes the use timer, not a durability limit. [Duration and finish][use] · [Item-change check][item-change] · [Use countdown][countdown] · [Completion and release][completion]

## Behavior

- **First-person zoom:** the Spyglass sets the field-of-view modifier to **0.1**, with the camera easing toward it. A configured 70-degree FOV therefore approaches 7 degrees when no other FOV adjustment applies. This is an angular FOV multiplier, not a measured tenfold image magnification. The third-person camera does not take this Spyglass zoom branch. [FOV selection][fov] · [Camera calculation][camera]
- **Slower aiming:** in first person, with Smooth Camera disabled, mouse-turn input uses one eighth of its ordinary multiplier. Smooth Camera has its own earlier input branch. [Mouse input][mouse]
- **Movement while looking:** while you are using the item and are not riding, ordinary movement input receives the shared 0.2 use-item multiplier. [Movement input][movement]
- **Viewing distance:** zoom changes the projection; the current renderer still takes its render distance from the normal render-distance setting. It does not load distant terrain merely because the Spyglass is in use. [Active camera path][native-camera]

## Notes

The item is registered as `minecraft:spyglass`, has a maximum stack size of **one**, and is registered without durability data. Normal looking does not spend materials or require repairs. [Registration][registration] · [Default item components][components] · [Use behavior][use]

The current Rust/Vulkan rendering route receives the Spyglass FOV calculation and calls the first-person scope-overlay path when the HUD is visible. Its hand-rendering path also suppresses ordinary hands while scoping. These paths were traced in source; **the scope border, held-item appearance, and visual transitions have not been checked in game**. [Active camera path][native-camera] · [Active HUD path][native-hud] · [HUD visibility][hud] · [Scope overlay][overlay] · [Hand path][hands]

Related: [Amethyst Shard](AmethystShard.md) · [Copper Ingot](CopperIngot.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-04** at MattMC commit `f5473e41dc4af8ced756db517fada27288df07a3`. Registration, the bundled recipe and resource overrides, item-use dispatch and lifecycle, camera/input calculations, and the selected rendering route were inspected. No in-game crafting, scoping, multiplayer, or visual test was run.

[use]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/SpyglassItem.java#L11-L50
[registration]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/Items.java#L1665
[recipe]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/recipe/crafting/spyglass.json
[creative]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1484
[input]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/client/Minecraft.java#L1770-L1835
[release-control]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/client/Minecraft.java#L2102-L2105
[server-use]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L300-L335
[item-change]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3124-L3132
[countdown]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3168-L3172
[completion]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3255-L3311
[fov]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/client/player/AbstractClientPlayer.java#L116-L137
[camera]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/client/renderer/GameRenderer.java#L459-L503
[mouse]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/client/MouseHandler.java#L351-L376
[movement]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/client/player/LocalPlayer.java#L590-L597
[native-camera]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/client/renderer/GameRenderer.java#L750-L837
[components]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
[native-hud]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/client/renderer/GameRenderer.java#L968-L983
[overlay]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/client/gui/Gui.java#L320-L337
[hud]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/client/gui/Gui.java#L236-L247
[hands]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/client/renderer/ItemInHandRenderer.java#L421-L503
