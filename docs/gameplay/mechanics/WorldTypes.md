# World types and customization

**World Type** chooses the generation preset for a new world. It is separate from the player abilities chosen through **Game Mode**: Superflat and Skyblock are world types, while Survival and Creative are player modes. Use [Local worlds](LocalWorlds.md#create-a-local-world) for the complete creation workflow and [Game modes](../gamemodes/Gamemodes.md) for player abilities. [World Type control][chooser] · [Preset selection][selection]

## Choose a world type

Open **Create New World → World → World Type**. The bundled normal list has six choices. Hold **Alt** while cycling this control to use the extended list, which also includes **Debug Mode**. **Customize** is available for **Superflat** and **Single Biome**. [Normal list][normal-list] · [Extended list][extended-list] · [Alt control][alt-key] · [Chooser lists][chooser-lists] · [Editors][editors]

| World Type | Overworld setup | Customize |
| --- | --- | --- |
| **Default** | Varied Overworld biomes with ordinary Overworld terrain settings | No |
| **Superflat** | Flat layers in Plains; initially 1 Bedrock, 2 Dirt and 1 Grass Block, bottom to top | Layers and flat presets |
| **Large Biomes** | Overworld biome selection with the separate Large Biomes generation settings | No |
| **AMPLIFIED** | Overworld biome selection with the separate amplified terrain settings | No |
| **Single Biome** | One fixed biome, initially Plains, with ordinary Overworld terrain settings | Biome selection |
| **Skyblock** | The Skyblock generator with Overworld biome selection; see [its limits below](#skyblock) | No |
| **Debug Mode** — extended list | A display of block states using the debug generator; see [Debug Mode](#debug-mode) | No |

Preset definitions: [Default][normal] · [Superflat][flat] · [Large Biomes][large] · [AMPLIFIED][amplified] · [Single Biome][single] · [Skyblock][skyblock-preset] · [Debug Mode][debug-preset]. Large Biomes and AMPLIFIED use distinct generation settings; this guide does not assign a measured biome-size multiplier, terrain-height guarantee, performance requirement, or matching terrain across presets with the same seed. [Large Biomes settings][large-settings] · [AMPLIFIED settings][amplified-settings]

The choices come from the loaded preset registry, so [world data packs](WorldDataPacks.md) can affect the list and definitions. The English labels here are the bundled labels. [Loaded lists][loaded-lists] · [World-type labels][type-labels]

### Other dimensions

These preset files retain ordinary Nether and End generators; selecting Superflat or Skyblock does not select that generator for every dimension. World creation combines the selected preset's dimensions with loaded dimension definitions, and a loaded definition takes priority when both provide the same dimension. The bundled **Primordial Caves** definition therefore matters even for presets that do not list it themselves. See [Dimensions](../dimensions/Dimensions.md) for destinations and travel, and [Primordial Caves](../dimensions/PrimordialCaves.md) for its environment and the distinction between preset and loaded definitions. [Dimension loading][dimension-load] · [Creation step][creation-bake] · [Combination and precedence][dimension-bake] · [Bundled Primordial Caves][primordial]

## Customize Superflat

1. Select **Superflat**, then **Customize**
2. Review the layers; the screen shows the top layer first. Select a layer and choose **Remove Layer** to delete it
3. To start from a bundled setup, open **Presets**, select an entry, then choose **Use Preset**
4. Review the resulting layers and choose **Done** to apply the flat configuration

The editor and preset screen are separate steps: **Use Preset** returns a configuration to the layer screen, and **Done** applies that configuration to world creation. [Layer controls][flat-controls] · [Display and deletion order][flat-layers] · [Use Preset][use-preset] · [Apply to world creation][editors]

**Do not rely on Cancel to undo layer deletions.** The current layer editor receives the existing flat settings object, and **Remove Layer** changes its layer list immediately. **Cancel** closes the editor and refreshes those layers without restoring a saved copy. Review the configuration before creating the world. This is a source-derived limitation, not a live UI test. [Shared settings][editors] · [Retained settings object][flat-shared] · [Removal][flat-layers] · [Cancel callback][flat-controls]

### Nine bundled flat presets

The visible flat-preset tag contains these nine entries. The screen filters out entries whose layer blocks are disabled by the active feature flags. In this table, layers run **bottom to top**, and each number is a layer thickness in blocks. [Visible list][flat-visible] · [List filtering][flat-filter] · [Preset labels][flat-labels]

| Preset | Biome | Layers, bottom → top |
| --- | --- | --- |
| [Classic Flat][classic] | Plains | 1 Bedrock → 2 Dirt → 1 Grass Block |
| [Tunnelers' Dream][tunnelers] | Windswept Hills | 1 Bedrock → 230 Stone → 5 Dirt → 1 Grass Block |
| [Water World][water] | Deep Ocean | 1 Bedrock → 64 Deepslate → 5 Stone → 5 Dirt → 5 Gravel → 90 Water |
| [Overworld][flat-overworld] | Plains | 1 Bedrock → 59 Stone → 3 Dirt → 1 Grass Block |
| [Snowy Kingdom][snowy] | Snowy Plains | 1 Bedrock → 59 Stone → 3 Dirt → 1 Grass Block → 1 Snow |
| [Bottomless Pit][bottomless] | Plains | 2 Cobblestone → 3 Dirt → 1 Grass Block |
| [Desert][desert] | Desert | 1 Bedrock → 3 Stone → 52 Sandstone → 8 Sand |
| [Redstone Ready][redstone] | Desert | 1 Bedrock → 3 Stone → 116 Sandstone |
| [The Void][void] | The Void | 1 Air |

The same presets also select feature, lake and structure-set settings:

| Preset | Features | Lakes | Structure sets allowed by the preset |
| --- | --- | --- | --- |
| [Classic Flat][classic] | Off | Off | Villages |
| [Tunnelers' Dream][tunnelers] | On | Off | Mineshafts, Strongholds |
| [Water World][water] | Off | Off | Ocean Ruins, Shipwrecks, Ocean Monuments |
| [Overworld][flat-overworld] | On | On | Villages, Mineshafts, Pillager Outposts, Ruined Portals, Strongholds |
| [Snowy Kingdom][snowy] | Off | Off | Villages, Igloos |
| [Bottomless Pit][bottomless] | Off | Off | Villages |
| [Desert][desert] | On | Off | Villages, Desert Pyramids, Mineshafts, Strongholds |
| [Redstone Ready][redstone] | Off | Off | None: explicit empty list |
| [The Void][void] | On | Off | None: explicit empty list |

**Initial Superflat and Classic Flat differ.** Their layers and Plains biome match, and both disable features and lakes, but initial Superflat allows **Strongholds and Villages**, while Classic Flat allows **Villages only**. [Initial Superflat][flat] · [Classic Flat][classic]

Allowed structure sets are candidates, not promised structures. **Generate Structures** is a separate creation control; biome features and lakes use separate generation settings. An explicit empty structure list allows no sets through this flat-generator path, whereas an absent override would use the available registry entries. Features still depend on the selected biome, terrain and placement conditions. Read [Structures](../structures/Structures.md) for structure families, [Biomes](../biomes/Biomes.md) for biome content, and [The Void](../biomes/SpecialBiomes.md#the-void) for that preset's special feature behavior. [Structure-set selection][flat-structures] · [Feature and lake settings][flat-features] · [Creation controls][chooser]

### Share or edit the preset text

The text box in **Presets** records the layer list and biome. Layers are comma-separated, with a thickness written as `count*block_id`; the biome follows a semicolon. For example, `minecraft:bedrock,2*minecraft:dirt,minecraft:grass_block;minecraft:plains` describes the familiar four-block-deep Plains layout. Layers are listed bottom to top. [Text parser and export][flat-string]

This text is **not a complete copy of the preset**. A successfully parsed layer/biome string keeps the structure choices, feature setting and lake setting from the currently selected settings. To reproduce a bundled setup, select that preset first, then edit the text if needed and choose **Use Preset**. Pasting another preset's text alone does not transfer all of its settings. Invalid or empty layer data falls back to default flat settings; an invalid biome falls back to Plains. Always review the resulting layers. [Parsing and fallback][flat-string] · [Selected preset settings][preset-select] · [Preserved feature and lake settings][flat-copy]

## Customize Single Biome

Select **Single Biome → Customize**, choose a biome, then use **Done**. The list contains loaded biomes, including entries outside the ordinary Overworld biome selection; an entry without a translated name displays its resource ID. **Cancel** returns without applying the newly selected biome. [Selection, Done and Cancel][biome-editor] · [Biome names][biome-names]

The result uses one fixed biome with ordinary Overworld terrain settings. Choosing a Nether, End or custom biome does not itself give the Overworld that destination's dimension type or environmental rules. For biome content, start with [Biomes](../biomes/Biomes.md); for destination rules, use [Dimensions](../dimensions/Dimensions.md). The selector's availability alone does not establish how well every loaded biome generates in this setup. [Single Biome configuration][single-config] · [Preserved dimension type][replace-generator]

## Skyblock

The current Skyblock generator writes a **3 × 3 platform centered on X/Z 0**, with Dirt at **Y64–65** and Grass Block at **Y66**. Its decoration step attempts an Oak tree at **0, 67, 0** in chunk **0, 0**. This is an attempt, not a guaranteed tree or a guarantee about the player's arrival position. [Platform and tree code][skyblock-generator]

Its custom decoration replaces the ordinary biome-decoration call, and its surface, carver and original-generation-mob methods do no work. Those generation-stage facts do not mean that all later natural spawning is absent. This guide does not establish a complete Survival progression or full-scene reproducibility from a seed. For the conditional extra loot from advancement completions, read [Skyblock advancement rewards](AdvancementsAndStatistics.md#extra-rewards-in-skyblock); the reward check concerns the generator of the dimension you are currently in. [Generator methods][skyblock-generator] · [Generation-stage dispatch][generation-dispatch]

## Debug Mode

Hold **Alt** while cycling **World Type** to reach **Debug Mode**. Its generator selects registered block states for a display at **Y70**, over a Barrier plane at **Y60**. This describes the display code; it does not certify that every registered state is visibly represented in a running world. [Alt control][alt-key] · [Extended chooser][chooser-lists] · [Display generation][debug-generator]

New Debug worlds use **Spectator**, **Peaceful**, commands allowed, and the daylight cycle disabled. Server setup locks Peaceful, clears rain and thunder, and sets the time to **6000**. The creation UI disables **Generate Structures**, **Bonus Chest** and **Customize**. Despite its label, Debug Mode is selected as a generation preset, separate from the general [player modes](../gamemodes/Gamemodes.md). [Debug world settings][debug-settings] · [Server setup][debug-server] · [Disabled controls][chooser]

## Sources and verification

Source-reviewed on **2026-10-05** at `5218ac875eda9f2c4151ff1a97795f20f5f356cf`. The review followed the active chooser, loaded preset tags and definitions, both customization editors, flat settings, dimension combination, and the Skyblock and Debug generator paths. English world-type and flat-preset labels were checked against the bundled language file; other language or resource packs can change displayed text.

No game, world-generation or live UI test was performed. These source-defined settings do not establish runtime generation parity, successful feature placement, safe initial player placement, or generation performance. Existing-save handling remains in [Local worlds](LocalWorlds.md).

Related: [Mechanics](Mechanics.md) · [World data packs](WorldDataPacks.md) · [Game modes](../gamemodes/Gamemodes.md) · [Biomes](../biomes/Biomes.md) · [Dimensions](../dimensions/Dimensions.md) · [Gameplay](../Gameplay.md)

[chooser]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/worldselection/CreateWorldScreen.java#L789-L850
[selection]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/worldselection/WorldCreationUiState.java#L219-L238
[normal-list]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/data/minecraft/tags/worldgen/world_preset/normal.json
[extended-list]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/data/minecraft/tags/worldgen/world_preset/extended.json
[alt-key]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/components/CycleButton.java#L21-L24
[chooser-lists]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/worldselection/CreateWorldScreen.java#L860-L873
[editors]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/worldselection/PresetEditor.java#L31-L58
[normal]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[flat]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/data/minecraft/worldgen/world_preset/flat.json
[large]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/data/minecraft/worldgen/world_preset/large_biomes.json
[amplified]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/data/minecraft/worldgen/world_preset/amplified.json
[single]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/data/minecraft/worldgen/world_preset/single_biome_surface.json
[skyblock-preset]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/data/minecraft/worldgen/world_preset/skyblock.json
[debug-preset]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/data/minecraft/worldgen/world_preset/debug_all_block_states.json
[large-settings]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/data/minecraft/worldgen/noise_settings/large_biomes.json#L24-L320
[amplified-settings]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/data/minecraft/worldgen/noise_settings/amplified.json#L24-L300
[loaded-lists]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/worldselection/WorldCreationUiState.java#L248-L278
[type-labels]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/assets/minecraft/lang/en_us.json#L4433-L4440
[dimension-load]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/WorldLoader.java#L35-L52
[creation-bake]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/worldselection/CreateWorldScreen.java#L291-L303
[dimension-bake]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/levelgen/WorldDimensions.java#L168-L193
[primordial]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/data/minecraft/dimension/primordial_caves.json
[flat-controls]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/CreateFlatWorldScreen.java#L70-L99
[flat-layers]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/CreateFlatWorldScreen.java#L135-L168
[use-preset]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/PresetFlatWorldScreen.java#L184-L218
[flat-shared]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/CreateFlatWorldScreen.java#L48-L67
[flat-visible]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/data/minecraft/tags/worldgen/flat_level_generator_preset/visible.json
[flat-filter]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/PresetFlatWorldScreen.java#L253-L275
[flat-labels]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/assets/minecraft/lang/en_us.json#L4312-L4320
[flat-structures]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/levelgen/FlatLevelSource.java#L28-L46
[flat-features]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/levelgen/flat/FlatLevelGeneratorSettings.java#L138-L177
[flat-string]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/PresetFlatWorldScreen.java#L73-L181
[preset-select]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/PresetFlatWorldScreen.java#L319-L325
[flat-copy]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/levelgen/flat/FlatLevelGeneratorSettings.java#L111-L127
[biome-editor]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/CreateBuffetWorldScreen.java#L42-L105
[biome-names]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/CreateBuffetWorldScreen.java#L113-L122
[single-config]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/worldselection/PresetEditor.java#L61-L69
[replace-generator]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/levelgen/WorldDimensions.java#L64-L90
[skyblock-generator]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/levelgen/SkyblockChunkGenerator.java#L33-L153
[generation-dispatch]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/chunk/status/ChunkStatusTasks.java#L87-L156
[debug-generator]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/levelgen/DebugLevelSource.java#L31-L109
[debug-settings]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/worldselection/CreateWorldScreen.java#L329-L345
[debug-server]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/MinecraftServer.java#L621-L630
[classic]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/data/minecraft/worldgen/flat_level_generator_preset/classic_flat.json
[tunnelers]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/data/minecraft/worldgen/flat_level_generator_preset/tunnelers_dream.json
[water]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/data/minecraft/worldgen/flat_level_generator_preset/water_world.json
[flat-overworld]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/data/minecraft/worldgen/flat_level_generator_preset/overworld.json
[snowy]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/data/minecraft/worldgen/flat_level_generator_preset/snowy_kingdom.json
[bottomless]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/data/minecraft/worldgen/flat_level_generator_preset/bottomless_pit.json
[desert]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/data/minecraft/worldgen/flat_level_generator_preset/desert.json
[redstone]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/data/minecraft/worldgen/flat_level_generator_preset/redstone_ready.json
[void]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/data/minecraft/worldgen/flat_level_generator_preset/the_void.json
