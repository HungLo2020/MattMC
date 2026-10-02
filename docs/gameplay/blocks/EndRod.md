# End Rod

An **End Rod** (`minecraft:end_rod`) is a slim, steady **light-level-14** block that can face in any of six directions. It has a narrow collision shape and stays in place after its original support is removed, making it useful for projecting lights and small decorative rails. Keep it dry: this source does not waterlog End Rods. [Registration][reg] · [Placement][rod] · [Shape][shape] · [Default support][defaults]

## Crafting and collecting

Place **1 Blaze Rod directly above 1 Popped Chorus Fruit** to craft **4 End Rods**. The two-row recipe fits the inventory crafting grid. Popped Chorus Fruit is made by smelting Chorus Fruit; ordinary unpopped Chorus Fruit does not satisfy the rod recipe. [Crafting recipe][recipe] · [Smelting recipe][smelting]

You can also collect placed rods from [End Cities](../structures/EndCity.md#towers-bridges-and-ships). The bundled Wide Tower Top contains **4**, while the End Ship contains **6**. These are counts in those individual templates, not a fixed total for every city or a guarantee that a city has a ship. The active city assembly selects these templates. [Tower template][tower-template] · [Ship template][ship-template] · [City assembly][pieces]

End Rods break instantly and ordinarily drop **1 End Rod**, including by hand. No correct-tool tier or Silk Touch is needed; Fortune does not increase the self-drop. The loot includes an explosion-survival condition, and ordinary block drops must be enabled. [Properties][reg] · [Loot][loot] · [Tool gate][harvest] · [Harvest dispatch][harvest-call] · [Drop rule][drops]

## Facing, support and collision

A newly placed rod normally points outward from the face you click: upward on a floor, downward under a ceiling, or horizontally from a wall. When that supporting block is another End Rod already pointing in that direction, the new rod reverses direction, so the two tips meet. The block has one `facing` state; it does not acquire a different light level for each orientation. [Placement and states][rod] · [Constant light][reg]

Its collision is a **4/16-block-wide square rod running the full block length along its axis**. It can obstruct movement even though its silhouette is narrow. There is no continuing support check or gravity behavior: removing the mounting block does not make the rod fall or break. This differs from [Torch support](Torch.md#placement). [Rod shape][shape] · [Collision and survival defaults][defaults]

## Water and lighting

End Rods have **no waterlogged state**. Their registration explicitly treats them as non-solid for the shared fluid-admission check. Water that advances into the rod's position replaces it and calls the normal item-drop path; it does not leave a submerged, waterlogged rod. Use a full light block such as a [Sea Lantern](LuminousBlocks.md#sea-lantern) for an exposed underwater fitting. [Registration][reg] · [State definition][rod] · [Fluid admission][fluid-entry] · [Replacement][flow] · [Water drops][water-drops]

The light is always 14 and does not need redstone, fuel, or an on/off interaction. The rod emits decorative End Rod particles. Its block light can melt nearby ordinary Ice or Snow layers if enough light reaches them; see [Ice](Ice.md#ordinary-ice-melting-and-breaking) and [Snow](Snow.md). The number 14 is not a guaranteed safe radius for every mob-spawn rule. [Light][reg] · [Particles][rod] · [Ice check][ice] · [Snow check][snow]

## Related pages

- [End Rod item](../items/EndRod.md), [Blaze Rod](../items/BlazeRod.md), and [Popped Chorus Fruit](../items/PoppedChorusFruit.md)
- [End Cities](../structures/EndCity.md), [Torch](Torch.md), and [Lanterns](Lanterns.md)
- [Mining](../mechanics/Mining.md) and [Blocks](Blocks.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `cf1c134b3f9ff634490e448fe26c335b90f82227`. Registration, all block states, placement/default support, collision, shared fluid admission/replacement, recipe, self-loot, tool tags, and End City assembly/templates were checked. No in-game generation, placement, fluid, movement, lighting, or mining test was run. Template overrides, data packs, and game rules can change the results.

[reg]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Blocks.java#L4212-L4214
[rod]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/EndRodBlock.java
[shape]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/RodBlock.java
[recipe]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/recipe/crafting/end_rod.json
[smelting]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/recipe/smelting/popped_chorus_fruit.json
[loot]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/loot_table/blocks/end_rod.json
[tower-template]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/structure/end_city/fat_tower_top.nbt
[ship-template]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/structure/end_city/ship.nbt
[pieces]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/levelgen/structure/structures/EndCityPieces.java
[defaults]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java
[flow]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L268-L278
[fluid-entry]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L400-L427
[water-drops]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/material/WaterFluid.java#L88-L91
[harvest]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[harvest-call]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L278-L294
[drops]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Block.java#L364-L420
[ice]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/IceBlock.java#L50-L63
[snow]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/SnowLayerBlock.java#L105-L111
