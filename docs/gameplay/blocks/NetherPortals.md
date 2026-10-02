# Nether and Primordial Caves portals

Nether portals and MattMC's Primordial Caves portals use the same ordinary Obsidian frame when first created, but their destinations and ongoing support checks differ. These are placed travel blocks, with **no matching inventory items**. The [Nether](../dimensions/Nether.md) and [Primordial Caves](../dimensions/PrimordialCaves.md) guides cover preparation, environmental hazards, and respawning. [Block registration][blocks] · [Item registry][items]

## Nether portal

**Block ID:** `minecraft:nether_portal`. Its `axis` state is `x` or `z`, describing the horizontal direction across the upright portal; the default is `x`. The portal has no solid collision, emits **light level 11**, and cannot be mined in ordinary Survival or moved by pistons. Its bundled block loot table has no drops. Collect the [Obsidian frame](Obsidian.md#mining-and-block-properties), not the portal cells. [Properties][blocks] · [Axis and shape][nether-state] · [Mining rule][mining] · [Piston rule][pistons] · [Loot][nether-loot]

### Building and activating

Build an upright rectangle with an interior **2–21 blocks wide and 3–21 blocks high**. Ordinary Obsidian must line the bottom, top, and both sides; the four outside corner blocks are not required by the shape check. Crying Obsidian is not accepted. Keep the interior clear: the validator accepts air, fire-tagged blocks, and existing Nether portal cells, not arbitrary decorations. [Frame and interior validation][frame]

For initial activation, place fire inside the frame, for example with [Flint and Steel](../items/FlintAndSteel.md). Fire checks an empty valid frame and fills its interior with Nether portal blocks. This ignition route is enabled in the **Overworld and Nether only**. An Obsidian rectangle in Primordial Caves or the End does not become a Nether portal just because it is lit. [Ignition item][ignition] · [Fire activation and dimension check][fire]

The normal portal rechecks its complete frame and filled interior on relevant neighbor-shape updates. Removing a required frame block or interrupting its active interior can therefore collapse the portal. Repair the frame and clear the interior before lighting it again. Outside corners are not required supports. [Support updates][nether-support] · [Complete-shape check][complete]

The ordinary portal's random-tick routine can also create a [Zombified Piglin](../mobs/ZombifiedPiglin.md) in a dimension marked natural, when monster spawning, difficulty, a nearby player, and a valid spawning surface permit it. This is a conditional random spawn, not a guaranteed result of using a portal. The Primordial Caves portal does not inherit this routine. [Nether portal random tick][portal-spawn] · [Separate Primordial implementation][primordial-all]

### Destination and linking

A Nether portal entered **in the Nether targets the Overworld**; entered anywhere else, it targets the Nether. The fire restriction above is about ordinary creation, not the destination of an operator-placed portal. The target dimension must be loaded, and entry into the Nether is subject to the [server control below](#entry-and-cooldown-controls). [Destination dispatch][nether-destination]

X and Z are multiplied by the source dimension's coordinate scale divided by the destination's scale; Y is not scaled. With the bundled Overworld scale **1** and Nether scale **8**, Overworld X=800, Z=−400 produces a Nether search target of X=100, Z=−50. This is a search target, not a promised landing position. [Scale calculation][scale] · [Overworld type][overworld-type] · [Nether type][nether-type]

The target is clamped to the destination world border. The portal search uses a horizontal square radius of **16 blocks when arriving in the Nether**, or **128 otherwise**, then chooses the candidate portal cell nearest in three-dimensional distance, with lower Y breaking a tie. These are search radii, not guaranteed one-to-one portal pairing rules. [Target and exit selection][nether-destination] · [Portal search][nether-search]

If no suitable portal is found, the game tries to construct an ordinary Obsidian frame with a **2×3 portal interior** near the target. The fallback can replace terrain with Obsidian and air; construction can also fail. Arrival uses the entry position within the portal and the exit's orientation, then attempts a limited collision adjustment. That adjustment is skipped for entities wider or taller than four blocks and can return the original position if no free position is found. It does not check every nearby hazard. Record the actual exits, and protect both approaches before treating the route as safe. [Construction][nether-create] · [Arrival calculation][nether-arrival] · [Collision-adjustment limits][collision]

## Primordial Caves portal

**Block ID:** `minecraft:primordial_caves_portal`. It also has `axis=x` or `axis=z`, defaults to `x`, has no solid collision, emits **light level 11**, and is not an ordinary Survival mining drop or piston-movable block. No Primordial Caves portal item is registered. [Properties][blocks] · [State][primordial-state] · [Item registry][items] · [Mining rule][mining] · [Piston rule][pistons]

### Converting an active portal

Drop **one [Pitcher Pod](../items/PitcherPod.md), separated from the rest of the stack**, into a complete active Nether portal. MattMC checks the existing Nether portal's frame and completeness, then replaces connected portal cells with Primordial Caves portal cells while retaining the entry portal's axis. This changes the destination of the placed portal. [Conversion][conversion]

**The whole dropped item entity is discarded, even if the completeness check prevents conversion.** A stack of pods is not reduced by just one. Keep spare pods and a separate ordinary Nether route elsewhere; the [Pitcher Pod guide](../items/PitcherPod.md#obtaining) owns acquisition and planting details. [Item interception and removal][pod-discard]

### Support after conversion

The converted portal **does not keep the Nether portal's full-frame validation**. On the relevant neighbor update, a cell checks for at least one directly adjacent Primordial Caves portal cell or ordinary Obsidian block. The check includes all six directions. Connected portal cells can therefore support one another even after frame pieces are removed; breaking one frame block is not a dependable way to switch this portal off. No pod interaction in the converted block changes it back to a Nether portal. [Support and interaction implementation][primordial-support]

### Destination and return

From outside Primordial Caves, the portal targets `minecraft:primordial_caves`. From inside that dimension it targets the **Overworld**, regardless of the original departure dimension. In particular, converting a Nether-side portal does not establish a return to that Nether location. [Destination dispatch][primordial-destination]

The same source/destination scale ratio is applied to X/Z. Bundled Overworld ↔ Primordial Caves targets are **1:1**. A departure from the bundled Nether instead multiplies X/Z by **8** when targeting Primordial Caves. Y remains unscaled, and the destination border clamps the target. [Destination calculation][primordial-destination] · [Scale ratio][scale] · [Dimension types][overworld-type] [Nether type][nether-type] [Primordial type][primordial-type]

This portal searches its **own portal type**, with a **128-block horizontal square radius**, choosing the closest cell by three-dimensional distance and then lower Y. If none is found, it attempts a 2×3 Primordial Caves portal in an Obsidian frame. Its creation code explicitly permits replacing Water and Lava as well as replaceable blocks, and its fallback writes Obsidian and air into the arrival area. Keep valuables clear of potential exits and inspect the landing area. Its arrival-position calculation differs from the ordinary Nether portal's and uses the same limited collision helper; matching search coordinates do not guarantee a safe or exactly paired exit. [Distinct portal registration][poi] · [Search and creation][primordial-create] · [Replacement and arrival][primordial-arrival] · [Collision limits][collision]

The bundled Normal preset includes this dimension, and server startup attempts to add it when missing. That fallback can fail, and it uses a different biome-selection path from the Normal preset. This establishes active destination wiring, not a promise about a particular seed, portal location, or every inherited mod feature. Follow [Primordial Caves](../dimensions/PrimordialCaves.md#what-currently-generates) for the bounded generation review. [Normal preset][normal] · [Server creation and fallback][server-levels] · [Fallback biome preset][fallback-biome]

## Entry and cooldown controls

For both portals on this page, player wait time comes from the **source world's** game rules:

- `playersNetherPortalDefaultDelay`: defaults to **80 ticks**, about four seconds at 20 TPS
- `playersNetherPortalCreativeDelay`: defaults to **0 ticks**, selected when the player's invulnerable ability is set, including ordinary Creative mode
- Other eligible entities have no configured wait in these two block implementations

These are thresholds used by the portal processor, not wall-clock promises. Time accumulated while touching a portal decays by four per processing tick after leaving it. [Block delay selection][nether-delay] [Primordial delay][primordial-delay] · [Defaults][delay-rules] · [Portal processor][processor]

Ordinary portal contact requires an entity to be **alive and not riding as a passenger**. Dropped items can qualify too; this is not restricted to mobs and players. Sleeping living entities are excluded, and individual entity types can reject portal use. A vehicle is not excluded merely because it carries passengers, so do not assume older blanket riding restrictions. [Base eligibility][eligibility] · [Sleeping restriction][sleeping] · [Example boss restriction][dragon-eligibility]

The shared processor sets an entity cooldown before asking for a destination. A player's cooldown is **10 ticks**; the base entity delay is **300 ticks**, or its first player's delay when carrying that player as first passenger. Contact during cooldown resets it, so step fully out before trying a return crossing. A failed destination attempt can still start that cooldown. [Cooldown and dispatch][dispatch] · [Player cooldown][player-cooldown]

Before ordinary processed travel, the server checks the proposed destination. The gamerule **`allowEnteringNetherUsingPortals`** defaults to true and can block travel **into the Nether**. It is not a general switch for every portal or for travel out of the Nether. Missing destination worlds or failed exit creation also prevent travel. [Server gate][server-gate] · [Rule name and default][nether-rule] · [Active teleport dispatch][dispatch]

## Survival, operator access, and verification

Survival routes create these portal blocks through ignition, pod conversion, or destination generation. They do not award portable portal items. Operators with the relevant command permission can use block-placement commands for these registered block IDs; that is separate from Survival access and does not prove that an arbitrary unsupported or malformed placement will function. [Item registry][items] · [Block command permission][setblock]

Source-reviewed on **2026-10-02** at `b823010659d7b5095ed021b1c99cf85627e2082a`. Checked creation, support, state, registry/item distinction, active entity/server teleport dispatch, bundled scale data, and server/preset wiring. No in-game portal construction, conversion, round trip, safe-arrival, or timing test was run. Data packs, game rules, saved worlds, and later source changes can alter results.

Related: [End portals and gateways](EndPortals.md) · [Obsidian and Crying Obsidian](Obsidian.md) · [Dimensions](../dimensions/Dimensions.md) · [Blocks](Blocks.md)

[items]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java
[mining]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L347-L357
[pistons]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java#L226-L259
[setblock]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/commands/SetBlockCommand.java#L22-L40
[dispatch]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/Entity.java#L2460-L2501
[eligibility]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/Entity.java#L3065-L3079
[sleeping]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3443-L3446
[normal]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[blocks]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Blocks.java#L2063-L2084
[nether-state]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/NetherPortalBlock.java#L47-L66
[nether-loot]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/loot_table/blocks/nether_portal.json
[frame]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/portal/PortalShape.java#L26-L179
[ignition]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/FlintAndSteelItem.java#L26-L46
[fire]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/BaseFireBlock.java#L156-L175
[nether-support]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/NetherPortalBlock.java#L91-L108
[complete]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/portal/PortalShape.java#L182-L184
[nether-destination]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/NetherPortalBlock.java#L186-L232
[scale]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/dimension/DimensionType.java#L126-L130
[overworld-type]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/dimension_type/overworld.json
[nether-type]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/dimension_type/the_nether.json
[primordial-type]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/dimension_type/primordial_caves.json
[nether-search]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/portal/PortalForcer.java#L41-L50
[nether-create]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/portal/PortalForcer.java#L52-L172
[nether-arrival]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/NetherPortalBlock.java#L234-L280
[collision]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/portal/PortalShape.java#L211-L225
[primordial-state]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/PrimordialCavesPortalBlock.java#L48-L67
[conversion]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/NetherPortalBlock.java#L133-L172
[pod-discard]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/NetherPortalBlock.java#L110-L125
[primordial-support]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/PrimordialCavesPortalBlock.java#L69-L113
[primordial-destination]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/PrimordialCavesPortalBlock.java#L126-L141
[poi]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiTypes.java#L138-L140
[primordial-create]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/PrimordialCavesPortalBlock.java#L143-L279
[primordial-arrival]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/PrimordialCavesPortalBlock.java#L282-L350
[server-levels]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/MinecraftServer.java#L406-L563
[fallback-biome]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L81-L93
[nether-delay]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/NetherPortalBlock.java#L175-L184
[primordial-delay]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/PrimordialCavesPortalBlock.java#L115-L124
[delay-rules]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/GameRules.java#L146-L151
[processor]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/PortalProcessor.java#L20-L45
[dragon-eligibility]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/boss/enderdragon/EnderDragon.java#L828-L831
[player-cooldown]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/player/Player.java#L408-L411
[server-gate]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/MinecraftServer.java#L1316-L1318
[nether-rule]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/GameRules.java#L230-L232
[portal-spawn]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/NetherPortalBlock.java#L68-L89
[primordial-all]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/PrimordialCavesPortalBlock.java
