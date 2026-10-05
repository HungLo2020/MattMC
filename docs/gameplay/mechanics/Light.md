# Light

Light helps crops grow and changes which mobs can spawn, but the value at the **affected block** matters more than how bright the scene looks. Choose a light source, leave a path for its light to reach the target, and check that mechanic's actual light requirement. For fixture recipes and emission levels, use [Torches](../blocks/Torch.md), [full lighting blocks](../blocks/LuminousBlocks.md) and [Copper lighting](../blocks/CopperLighting.md).

## Which light value matters?

The world stores separate **block light** and **skylight** channels, with levels from **0 to 15**. They are not added together. [Separate engines][engines] · [Maximum level][light-engine]

| Term | Meaning for play |
| --- | --- |
| **Emission** | The light a source block supplies at its own position. An ordinary Torch emits 14; nearby blocks need not receive 14 |
| **Block light** | Light reaching a position from emitting blocks, after propagation through the surroundings |
| **Skylight** | Light supplied through sky-exposed columns and into neighboring spaces; available only when the dimension type enables it |
| **Raw brightness with no sky subtraction** | The greater of block light and stored skylight at the position |
| **Sky-darkened local brightness** | The greater of block light and skylight after a specified sky-darkening amount is subtracted |

[Ordinary Torch][torch] · [Block-light propagation][block-propagation] · [Sky sources][sky-sources] · [Dimension-selected engine][server-engine] · [Combined brightness][raw] · [Local-brightness query][local]

Different mechanics ask for different values. For example, Wheat's ordinary growth check uses **raw brightness with no sky subtraction**, while the standard monster darkness check also uses sky-darkened local brightness. A single “light level” copied from another mechanic can therefore be misleading. [Crop growth][crop-growth] · [Monster darkness][monster]

## Inspect nearby light

The current default debug profile does **not** include the light readout. To enable it, open **Debug Options with F3 + F6**, find the **`light_levels`** entry, choose **In F3**, and use **Done**. Toggle the debug display with **F3** when needed. A reduced-debug-info setting can make this entry unavailable. [Shortcut][debug-open] · [English controls][debug-labels] · [Entry registration][debug-profile] · [Default profile][debug-default] · [Controls][debug-options] · [Done][debug-done] · [F3 toggle][debug-toggle] · [Reduced-info gate][debug-permission]

The **Client Light** line shows the combined raw value with **zero sky subtraction**, followed by separate **sky** and **block** values. It samples the **camera entity's block position**, normally your position, rather than the block under the crosshair. Looking at a distant crop or wall does not measure light there. This is client-side information, not a guarantee that the server has the same value during a pending update. [Readout and sampled position][debug-light] · [Active display caller][debug-display]

## How block light spreads

Block light moves between neighboring block positions in the six directions: up, down, north, south, east and west. In unobstructed air it loses **one level per step**. A stronger blocking value causes a larger loss, and an occluding boundary can stop that path completely. A diagonal is reached through several such steps; it is not a one-step diagonal jump. [Propagation][block-propagation] · [Minimum loss and shape checks][opacity]

A source-derived example with an ordinary Torch and an unobstructed straight path:

| Steps from the Torch's block position | 0 | 1 | 2 | 3 | 4 | 5 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Block light from that Torch | 14 | 13 | 12 | 11 | 10 | 9 |

Vertical separation consumes steps too. A wall can block the short route while light takes a longer route around an opening. This table assumes ordinary air and settled light updates; it is not a measured room layout or a universal lighting radius. [Torch emission][torch] · [Propagation][block-propagation] · [Update processing][updates]

**Lights overlap by the strongest value reaching the position, not by addition.** If two sources each deliver level 8 to the same position, they do not combine into 16. Another lamp helps when it supplies a stronger route or reaches a previously dark space. [Stronger-value propagation][block-propagation] · [Source insertion][source-insertion]

## Skylight, roofs and night

An unobstructed sky-source column can retain **skylight 15 down its open vertical path**. It does not lose one level for every block of open air below the sky. When a blocking boundary ends that source column, neighboring light can still spread into the shaded area with attenuation. A roof can remove the direct route without making every space beneath it completely dark, especially beside an open edge. [Column boundaries][sky-boundaries] · [Source levels][sky-sources] · [Shaded propagation][sky-propagation]

The current source checks both blocking values and opposing block faces when finding those column boundaries. Its active native source scan uses those same block-state properties; a block's visible texture alone does not determine its lighting behavior. [Source scan caller][sky-fill] · [Native property/face table][native-properties] · [Native scan][native-scan]

**Stored skylight and night-time darkness are separate.** Time of day, rain and thunder change the world's sky-darkening value. A local-brightness query can subtract that value from skylight while leaving block light unchanged. Under ideal clear Overworld conditions, skylight 15 with no block light gives local brightness 15 at noon and 4 at midnight, because the subtraction changes from 0 to 11. Those are formula examples, not a promise about every dimension or weather state. [Sky darkening][sky-darken] · [Local query][local] · [Combination][raw]

A mechanic that requests **zero** sky subtraction still sees the undarkened value. This is why the Wheat growth light check alone does not stop an open-sky crop at night. Growth still needs actual random ticks and its other conditions. [Crop check][crop-growth] · [Random-tick dispatch][random-dispatch] · [Wheat registration][wheat-register]

Dimension settings matter. The bundled Nether disables skylight, while the current End type enables it and uses fixed time. Follow [Daylight Detector dimension limits](../blocks/DaylightDetector.md#dimension-limits) for those differences rather than judging them from the sky's appearance. [Nether type][nether] · [End type][end] · [Engine selection][server-engine]

## Opaque, transparent and shaped blocks

- **Ordinary opaque full blocks** can block the direct path completely. The default full opaque state has light blocking 15. Light may still reach the far side through a different opening. [Default blocking][block-properties] · [Propagation][block-propagation]
- **Ordinary and stained Glass** transmit light; **Tinted Glass** blocks it despite being see-through. Glass transmission does not remove block light's ordinary distance loss. Use [Glass light and transparency](../blocks/GlassAndPanes.md#light-and-transparency) for panes, waterlogging and dye-color details. [Ordinary Glass registration][glass-register] · [Transparent behavior][transparent] · [Tinted override][tinted] · [Minimum loss][opacity]
- **Water** interrupts the unobstructed full-strength skylight-column route. Light can still propagate through it, so underwater lighting is not the same as an opaque wall or a dry open-sky column. [Water registration][water-register] · [Fluid-block shape and sky behavior][liquid] · [Blocking calculation][block-properties] · [Sky boundary][sky-boundaries]
- **Partial blocks and adjoining faces** need their actual shape rules checked. A visible gap, transparent texture, or collision shape is not sufficient by itself to establish a light path. The engine uses the states' light-occlusion faces as well as their blocking values. [Face selection and occlusion][opacity] · [State initialization][state-init]

## Apply the right requirement

| Goal | Relevant light rule |
| --- | --- |
| Grow [Wheat](../blocks/Wheat.md) | Raw brightness with zero sky subtraction must be **at least 9** for its random growth check; the separate survival light threshold is **8** |
| Prevent the ordinary [Overworld Zombie](../mobs/Zombie.md#where-to-find-it) darkness check | Its bundled dimension requires **block light 0** for the standard monster predicate, so positive block light at a candidate position rejects that check |
| Preserve ordinary [Ice](../blocks/Ice.md#ordinary-ice-melting-and-breaking) or [Snow layers](../blocks/Snow.md#snowfall-light-and-game-rules) | Their melting checks read **block light**, not skylight; consult those guides for thresholds, ticking and differences between variants |
| Operate a [Daylight Detector](../blocks/DaylightDetector.md) | It reads **skylight** and sky conditions; a Torch does not replace missing skylight |

[Crop growth and survival][crop-growth] [crop-survival][] · [Zombie predicate binding][zombie-binding] · [Monster checks][monster] · [Overworld limits][overworld] · [Ice melting][ice] · [Snow melting][snow] · [Detector calculation][detector]

For an enclosed Wheat plot with no skylight, a crop receiving block light 9 satisfies its **light** requirement. It still needs Farmland, room and ticking growth opportunities. The straight-path Torch example explains one source of that 9; walls and height differences can change the result. [Crop requirements][crop-growth] [crop-survival][] · [Propagation][block-propagation]

There is **no single spawn-proof light level for every mob, dimension and creation route**. Species predicates, spawners, events and special spawn reasons differ. Lighting does not remove an existing mob or stop it walking into the area. Use [Natural spawning](NaturalSpawning.md#how-a-natural-encounter-is-chosen) and the relevant species/device guide before calling a room safe. [Shared spawn checks and light exceptions][monster]

## Bright appearance is not a light source

**Night Vision and the graphics Brightness setting affect presentation.** They enter the client lightmap calculation, not the block-light propagation or server crop/spawn checks described above. A bright-looking cave can still have no placed block light. See [vision effects](../effects/VisionEffects.md#night-vision) and [graphics settings](GraphicsAndPacks.md) for display controls. [Client lightmap inputs][lightmap] · [World-light engine][engines] · [Gameplay consumers][crop-growth] [monster][]

When a result is unexpected, check the exact target position, its light path, the channel the mechanic uses, and the dimension's rules. Then check that the plant, machine or spawn route has its other requirements. More lamps cannot fix missing water, invalid ground or inactive terrain.

## Sources and verification

Source-reviewed on **2026-10-05** at `5218ac875eda9f2c4151ff1a97795f20f5f356cf`. The review followed the server-selected light engines, normal block-change update path, block/skylight propagation, Java and native skylight-source boundaries, consumer-facing brightness queries, selected registered crop/spawn/melting consumers, the configurable debug-light readout and client lightmap inputs. Normal changes to lighting properties queue a light recheck. [Block-change dispatch][change-dispatch] · [Server processing][updates]

No in-game lighting layout, propagation timing, crop growth, spawning, debug-screen navigation, graphics or native-versus-Java runtime test was run. Examples are derived from the inspected rules. Data packs can change dimension settings and other gameplay inputs; light alone does not establish a successful farm or a safe enclosure.

Related: [Mechanics](Mechanics.md) · [Torches](../blocks/Torch.md) · [Lighting blocks](../blocks/LuminousBlocks.md) · [Glass](../blocks/GlassAndPanes.md) · [Natural spawning](NaturalSpawning.md) · [Time, weather and sleep](TimeWeatherAndSleep.md)

[debug-open]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/KeyboardHandler.java#L324-L335
[debug-profile]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/components/debug/DebugScreenEntries.java#L19-L31
[debug-default]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/components/debug/DebugScreenEntries.java#L91-L124
[debug-options]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/debug/DebugOptionsScreen.java#L184-L212
[debug-done]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/debug/DebugOptionsScreen.java#L78-L83
[debug-toggle]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/KeyboardHandler.java#L525-L532
[debug-permission]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/components/debug/DebugScreenEntry.java#L10-L15
[debug-light]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/components/debug/DebugEntryLight.java#L21-L47
[debug-display]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/components/DebugScreenOverlay.java#L175-L181
[debug-labels]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/assets/minecraft/lang/en_us.json#L3376-L3400
[engines]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/lighting/LevelLightEngine.java#L21-L25
[light-engine]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/lighting/LightEngine.java#L20-L26
[raw]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/lighting/LevelLightEngine.java#L148-L152
[local]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/LevelReader.java#L169-L176
[block-propagation]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/lighting/BlockLightEngine.java#L45-L75
[opacity]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/lighting/LightEngine.java#L50-L85
[source-insertion]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/lighting/LightEngine.java#L156-L170
[sky-boundaries]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/lighting/ChunkSkyLightSources.java#L83-L145
[sky-sources]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/lighting/SkyLightEngine.java#L107-L169
[sky-propagation]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/lighting/SkyLightEngine.java#L136-L169
[sky-fill]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/lighting/ChunkSkyLightSources.java#L33-L50
[native-properties]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/chunk/NativeSkyLightSources.java#L58-L89
[native-scan]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/rust/world/level/lighting/skylight_sources/scan.rs#L40-L89
[server-engine]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/level/ChunkMap.java#L184-L191
[updates]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/level/ThreadedLevelLightEngine.java#L184-L215
[change-dispatch]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java#L292-L309
[sky-darken]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/Level.java#L639-L644
[crop-growth]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/CropBlock.java#L83-L94
[crop-survival]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/CropBlock.java#L149-L156
[monster]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/monster/Monster.java#L86-L118
[zombie-binding]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L154-L160
[wheat-register]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/Blocks.java#L1270-L1279
[random-dispatch]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/level/ServerLevel.java#L475-L512
[block-properties]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L296-L301
[state-init]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L507-L523
[glass-register]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/Blocks.java#L620-L632
[water-register]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/Blocks.java#L285-L297
[liquid]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/LiquidBlock.java#L101-L134
[transparent]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/TransparentBlock.java#L29-L37
[tinted]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/TintedGlassBlock.java#L19-L27
[torch]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/Blocks.java#L1193-L1202
[ice]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/IceBlock.java#L50-L63
[snow]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/SnowLayerBlock.java#L105-L111
[lightmap]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/renderer/LightTexture.java#L197-L246
[detector]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/DaylightDetectorBlock.java#L59-L76
[overworld]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/data/minecraft/dimension_type/overworld.json#L1-L24
[nether]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/data/minecraft/dimension_type/the_nether.json#L1-L20
[end]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/data/minecraft/dimension_type/the_end.json#L1-L20
