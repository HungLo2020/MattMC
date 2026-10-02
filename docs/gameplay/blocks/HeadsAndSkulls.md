# Heads and Skulls

Heads and skulls are collectible decorations that can stand on a surface or attach to a wall. Use standing mob heads above a [Note Block](NoteBlock.md) for mob sounds, power Dragon or Piglin Heads for animation, or save Wither Skeleton Skulls for the [Wither](../mobs/Wither.md#summoning). Every kind also has a wearable inventory item. [Registered blocks][blocks] · [Items][items]

## Registered forms and inventory items

All IDs below use the `minecraft:` namespace. Each row has **two distinct placed blocks but one inventory item**: the item ID matches the standing ID. Breaking either form uses that row's standing-block loot table; wall forms are not separate collectibles. All fourteen forms support the skull block entity. [Item pairing][paired] · [Shared wall loot][wall-properties] · [Block-entity registration][entity-type]

| Kind | Standing block and item | Wall block | Ordinary block loot |
| --- | --- | --- | --- |
| [Skeleton Skull](#skeleton-skulls) | `skeleton_skull` | `skeleton_wall_skull` | [One skull][loot-skeleton_skull] |
| [Wither Skeleton Skull](#wither-skeleton-skulls) | `wither_skeleton_skull` | `wither_skeleton_wall_skull` | [One skull][loot-wither_skeleton_skull] |
| [Zombie Head](#zombie-heads) | `zombie_head` | `zombie_wall_head` | [One head][loot-zombie_head] |
| [Creeper Head](#creeper-heads) | `creeper_head` | `creeper_wall_head` | [One head][loot-creeper_head] |
| [Piglin Head](#piglin-heads) | `piglin_head` | `piglin_wall_head` | [One head][loot-piglin_head] |
| [Dragon Head](#dragon-heads) | `dragon_head` | `dragon_wall_head` | [One head][loot-dragon_head] |
| [Player Head](#player-heads) | `player_head` | `player_wall_head` | [One head with retained data][loot-player_head] |

## Obtaining the different heads

### Skeleton skulls

A charged [Creeper](../mobs/Creeper.md) can produce a [Skeleton Skull](../items/SkeletonSkull.md) by killing an ordinary [Skeleton](../mobs/Skeleton.md), under the [special-drop conditions](#charged-creeper-conditions) below. Strays, Bogged, and Skeleton Horses are not alternative victims in this table. [Victim selection][charged-root] · [Skull reward][charged-skeleton]

There is also a generated-block route: Ancient City **barracks** and **medium-pillar** templates each contain one Skeleton Skull. Both pieces belong to the city's structures pool; the city uses the jigsaw generator and is eligible in the Deep Dark. The pieces are optional, so finding a city does not guarantee either skull. These are decorations to break and collect, not chest-loot rolls. [Barracks template][ancient-barracks] · [Pillar template][ancient-pillar] · [Piece pool][ancient-pool] · [Connecting wall template][ancient-wall-connector] · [City definition][ancient-definition] · [Jigsaw generation][jigsaw] · [Placement set][ancient-placement] · [Eligible biome][ancient-biomes]

### Wither skeleton skulls

A [Wither Skeleton](../mobs/WitherSkeleton.md) has a separate skull roll on a qualifying player-attributed kill: **2.5% without Looting**, then **3.5%, 4.5%, or 5.5%** with Looting I–III. The [item guide](../items/WitherSkeletonSkull.md#obtaining) owns the exact attribution and Looting restrictions. A charged Creeper can supply a skull through its separate special-drop route. [Ordinary loot][wither-loot] · [Special reward][charged-wither_skeleton]

Both `wither_skeleton_skull` and `wither_skeleton_wall_skull` can complete a Wither construction. **Do not finish a skull display on a valid Soul Sand/Soul Soil base accidentally.** Follow the canonical [Wither summoning guide](../mobs/Wither.md#summoning) for the layout, difficulty check, consumed blocks, and dangerous arrival. [Standing trigger and accepted blocks][wither] · [Wall trigger][wither-wall]

### Zombie heads

The verified mob route is a charged Creeper killing an ordinary [Zombie](../mobs/Zombie.md). The special table checks the exact zombie type; Husks, Drowned, and Zombie Villagers are not listed substitutes. The same [Zombie Head item](../items/ZombieHead.md) places `zombie_head` or `zombie_wall_head`. [Selection][charged-root] · [Reward][charged-zombie] · [Pairing][items]

### Creeper heads

A charged Creeper must kill **another Creeper** to produce a [Creeper Head](../items/CreeperHead.md). A Creeper's own explosion is not this victim-drop route. Keep the one-special-head limit in mind before gathering several eligible victims together. [Killer callback][creeper] · [Victim selection][charged-root] · [Reward][charged-creeper]

One Creeper Head plus one [Paper](../items/Paper.md), shapeless, makes one [Creeper Banner Pattern](../items/CreeperBannerPattern.md). A Wither Skeleton Skull similarly makes a [Skull Banner Pattern](../items/SkullBannerPattern.md) with Paper. Both recipes consume the head/skull. [Creeper pattern recipe][creeper-pattern] · [Skull pattern recipe][skull-pattern]

### Piglin heads

The special-drop table accepts an ordinary [Piglin](../mobs/Piglin.md) killed by a charged Creeper. It does not accept a Piglin Brute or Zombified Piglin. One [Piglin Head item](../items/PiglinHead.md) supplies the standing and wall forms; both can animate when powered. [Victim selection][charged-root] · [Reward][charged-piglin] · [Animated forms][power]

### Dragon heads

Look for the head at the front of an **End Ship** in an [End City](../structures/EndCity.md#towers-bridges-and-ships). The bundled ship template contains **one `dragon_wall_head`**, which drops the ordinary [Dragon Head item](../items/DragonHead.md). End-city generation can select that ship template, but a city is not guaranteed to have a ship. This is a placed structure reward, separate from killing the Ender Dragon. [Ship template][ship] · [Ship selection][ship-route] · [Template loading][ship-loader] · [Block loot][loot-dragon_head]

### Player heads

[Player Heads](../items/PlayerHead.md) can carry player-profile data for their appearance. The item is available in Creative, but the reviewed built-in recipes, entity/chest loot, and structure templates provide **no ordinary Survival acquisition route**. Player death is not established here as a source. A custom head supplied by commands, a map, or server content is a separate route with its own item data. [Creative entry][creative] · [Profile handling][player-name] · [Profile rendering][render]

### Charged-Creeper conditions

The charged Creeper's actual kill callback invokes a special loot table when mob loot is enabled and that Creeper has not already produced a special head. The first eligible output marks that Creeper as used. **One explosion killing several eligible mobs therefore does not award one special head for each victim.** The root table accepts only the five types listed above; its child tables each award one corresponding head/skull without a player-kill condition or percentage roll. Ordinary death loot is separate, including a Wither Skeleton's possible normal skull roll. See [Creeper](../mobs/Creeper.md) for charging and explosion behavior. [Callback and limit][creeper] · [Mob-loot gate][mob-loot] · [Eligible victims][charged-root] · [Death dispatch][death-dispatch]

## Placement, support, and water

Use a head item on top of a block for its standing form or aim against a side for its wall form. Standing forms have **16 horizontal rotations**; wall forms have **four horizontal facings**. The placement selector uses the player's view direction and rejects an obstructed result. There is no ceiling-hanging head variant. [Standing placement][standing] · [Wall placement][wall] · [Item selection][paired]

A wall head's initial placement looks for a **non-replaceable block behind it**; it does not demand a full sturdy wall face. After placement, neither standing nor wall heads have a support-loss breaking rule: removing the block underneath or behind them does not by itself make them fall or break. This follows the active skull classes and their inherited always-valid survival check. [Wall check][wall] · [Standing class][standing] · [Inherited survival][survival] · [Inherited neighbor-shape update][shape-update]

Heads are **not waterloggable**. Flowing water that reaches their space can replace them and request their ordinary block drops. Water can carry the resulting item away, so keep a collection space below an exposed display. Their small collision shapes fall below the motion-blocking threshold used by the fluid check. This is source-derived behavior, not an in-game flood test. [Standing shape][standing] · [Wall shape][wall] · [Piglin wall shape][piglin-wall] · [Solidity calculation][solidity] · [Fluid admission][fluid] · [Replacement][fluid-spread] · [Water drops][water-drops]

## Breaking and retained data

All fourteen forms have hardness **1.0** and do not require a particular tool for drops. They are absent from the bundled axe, pickaxe, shovel, and hoe mining tags. Breaking one in Survival can therefore return its item even by hand; **Silk Touch is not required**. The seven tables each roll one item without Fortune, Silk Touch, tool, or explosion-survival conditions. Block-drop game rules and later destruction of the loose item still matter. [Registration][blocks] · [Default tool requirement][base-properties] · [Player harvest test][harvest] · [Drop dispatch and game rule][drops]

The placed form does not survive as a separate wall item: both forms return the standing form's inventory item. The ordinary six mob-head tables copy **custom name** data. Player Head loot additionally copies **profile** and **Note Block sound** data from the block entity. Position, facing, rotation, and current power are not among the copied components. Placement transfers the supplied item components into the skull block entity, allowing a customized Player Head to keep its profile when placed and recovered. [Wall loot alias][wall-properties] · [Player loot][loot-player_head] · [Example mob-head loot][loot-dragon_head] · [Placement transfer][block-item] · [Stored components][entity]

Pistons treat these blocks as **destroyable**, rather than moving a placed head intact. Take down a valuable display before running a piston through it. [Piston reaction][blocks] · [Destruction selection][piston-selection] · [Drop and removal][piston-drops]

## Powered animation

Redstone power animates the **Dragon Head's jaw** and the **Piglin Head's ears**, including their wall forms. The client animation advances while the head's powered state is true and stops advancing when power is removed. Skeleton, Wither Skeleton, Zombie, Creeper, and Player Heads do not receive that animation ticker. Powering a head by itself does not play a mob sound; use a Note Block for sound. [Power changes and animated kinds][power] · [Animation counter][entity] · [Renderer][render] · [Dragon jaw][dragon-animation] · [Piglin ears][piglin-animation]

## Note Block sounds

Place a **standing head directly above** a [Note Block](NoteBlock.md), then play or power the Note Block. The top-instrument item tag lets the head-placement interaction pass through when used on the Note Block's upper face. A newly powered Note Block plays once on its unpowered-to-powered transition; it does not continuously repeat while power stays on. [Placement interaction][note-use] · [Accepted items][note-tag] · [Selection and power][note-selection]

| Standing head above the Note Block | Selected sound |
| --- | --- |
| Skeleton Skull | Skeleton imitation |
| Wither Skeleton Skull | Wither Skeleton imitation |
| Zombie Head | Zombie imitation |
| Creeper Head | Creeper imitation |
| Dragon Head | Ender Dragon imitation |
| Piglin Head | Piglin imitation |
| Player Head | Its supplied custom Note Block sound, if present |

These head instruments play at a fixed pitch; cycling the Note Block's stored note does not transpose the mob sound. A plain Player Head with no supplied Note Block sound produces **no sound** through the custom-head playback path. Changing only its profile or display name does not supply a sound. [Instrument kinds][instruments] · [Playback and missing-sound check][note-sound] · [Separate stored components][entity]

**Wall-form limitation in this source snapshot:** the seven wall registrations inherit the loot table and display name but do not copy the standing head's instrument. They retain the default base instrument. If a wall head occupies the block directly above a Note Block, instrument selection falls back to the block below; the non-air wall head then fails the normal clear-space playback check. Use the standing form above the Note Block. This is traced source behavior, not a verified in-game sound result. [Wall properties][wall-properties] · [Default instrument][base-properties] · [Registrations][blocks] · [Selection and playback gate][note-selection]

## Sources and verification

Source-reviewed on **2026-10-02** at `fc47f0654e74d1e5bd3b7f3522cc1e8117694418`. Reviewed all fourteen block registrations, seven item aliases and block-loot tables, relevant tags/recipes, charged-Creeper dispatch, head/profile/rendering paths, and compressed structure templates with their generation wiring. No in-game acquisition, placement, water, breaking, retained-profile, piston, sound, animation, or summoning test was run. Data packs, supplied item data, game rules, world-generation settings, and already looted structures can change the available result.

Related: [Blocks](Blocks.md) · [Creeper](../mobs/Creeper.md) · [Wither](../mobs/Wither.md) · [End City](../structures/EndCity.md) · [Items](../items/Items.md)

[blocks]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/Blocks.java#L2740-L2803
[items]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/Items.java#L2063-L2097
[paired]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/StandingAndWallBlockItem.java#L23-L52
[wall-properties]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/Blocks.java#L7264-L7272
[entity-type]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/entity/BlockEntityType.java#L110-L127
[loot-skeleton_skull]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/blocks/skeleton_skull.json
[loot-wither_skeleton_skull]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/blocks/wither_skeleton_skull.json
[loot-zombie_head]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/blocks/zombie_head.json
[loot-creeper_head]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/blocks/creeper_head.json
[loot-piglin_head]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/blocks/piglin_head.json
[loot-dragon_head]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/blocks/dragon_head.json
[loot-player_head]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/blocks/player_head.json
[charged-root]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/charged_creeper/root.json#L1-L82
[charged-skeleton]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/charged_creeper/skeleton.json#L1-L16
[ancient-barracks]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/structure/ancient_city/structures/barracks.nbt
[ancient-pillar]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/structure/ancient_city/structures/medium_pillar_1.nbt
[ancient-pool]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/worldgen/template_pool/ancient_city/structures.json
[ancient-definition]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/worldgen/structure/ancient_city.json#L40-L48
[jigsaw]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/levelgen/structure/structures/JigsawStructure.java#L135-L152
[ancient-placement]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/worldgen/structure_set/ancient_cities.json#L1-L14
[ancient-biomes]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/ancient_city.json#L1-L5
[wither-loot]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/entities/wither_skeleton.json#L64-L88
[charged-wither_skeleton]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/charged_creeper/wither_skeleton.json#L1-L16
[wither]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/WitherSkullBlock.java#L42-L104
[wither-wall]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/WitherWallSkullBlock.java#L24-L27
[charged-zombie]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/charged_creeper/zombie.json#L1-L16
[creeper]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/entity/monster/Creeper.java#L170-L179
[charged-creeper]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/charged_creeper/creeper.json#L1-L16
[creeper-pattern]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/recipe/crafting/creeper_banner_pattern.json#L1-L12
[skull-pattern]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/recipe/crafting/skull_banner_pattern.json#L1-L12
[charged-piglin]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/charged_creeper/piglin.json#L1-L16
[power]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/AbstractSkullBlock.java#L38-L80
[ship]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/structure/end_city/ship.nbt
[ship-route]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/levelgen/structure/structures/EndCityPieces.java#L198-L209
[ship-loader]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/levelgen/structure/structures/EndCityPieces.java#L334-L360
[creative]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1252-L1258
[player-name]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/PlayerHeadItem.java#L9-L20
[render]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/client/renderer/blockentity/SkullBlockRenderer.java#L80-L103
[mob-loot]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/entity/LivingEntity.java#L565-L567
[standing]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/SkullBlock.java#L26-L71
[wall]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/WallSkullBlock.java#L23-L75
[survival]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L311
[piglin-wall]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/PiglinWallSkullBlock.java#L14-L30
[solidity]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L482-L542
[fluid]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L401-L428
[fluid-spread]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L268-L277
[water-drops]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/material/WaterFluid.java#L87-L91
[base-properties]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L992-L1022
[harvest]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[drops]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/Block.java#L364-L419
[block-item]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/BlockItem.java#L64-L103
[entity]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/entity/SkullBlockEntity.java#L38-L107
[dragon-animation]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/client/model/dragon/DragonHeadModel.java#L47-L51
[piglin-animation]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/client/model/PiglinHeadModel.java#L28-L35
[note-use]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/NoteBlock.java#L106-L132
[note-tag]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/tags/item/noteblock_top_instruments.json#L1-L11
[note-selection]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/NoteBlock.java#L54-L104
[instruments]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/state/properties/NoteBlockInstrument.java#L25-L61
[note-sound]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/NoteBlock.java#L139-L172
[shape-update]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L150-L160
[piston-selection]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/piston/PistonStructureResolver.java#L37-L49
[piston-drops]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java#L282-L298
[ancient-wall-connector]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/structure/ancient_city/walls/intact_horizontal_wall_1.nbt
[death-dispatch]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1413-L1435
