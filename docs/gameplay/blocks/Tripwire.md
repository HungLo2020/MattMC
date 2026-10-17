# Tripwire and Tripwire Hooks

A tripwire line detects an eligible entity crossing String between two facing hooks. The **hooks** provide the redstone output; the String is the sensing line, not ordinary redstone dust.

The block IDs are `minecraft:tripwire_hook` and `minecraft:tripwire`. Place [String](../items/String.md), registered as `minecraft:string`, to create the wire segments.

## Obtaining and collecting

Craft **two Tripwire Hooks** in a Crafting Table with one Iron Ingot above one Stick above one accepted Planks, all in one column. The plank must match `minecraft:planks`; Oak is accepted, while Pewen Planks are absent from the bundled tag.

See the [String guide](../items/String.md#obtaining) for verified spider, cobweb, and placed-tripwire sources. No separate Tripwire inventory item is needed.

Ordinary Survival mining returns one Hook from a Hook and one String from a wire segment. Neither block requires a particular harvesting tool for that drop. **Tool choice still matters for the signal caused by breaking a wire**; see [disarming](#breaking-and-disarming).

## Build a complete line

Hooks must attach to sturdy **horizontal wall faces**, with their fronts pointing at each other at the same height. Run an uninterrupted straight line of String between them.

The checked scan accepts **1–40 String segments** between the hooks. Equivalently, the hooks occupy positions **2–41 blocks apart** along the line. Two adjacent hooks with no String between them do not form an attached line. A gap, wrong-facing hook, or excessive distance prevents attachment.

String itself has no requirement for a supporting block underneath in this implementation, so a line can be suspended. The hooks must retain their wall support. String can show side connections, but a visual branch or corner is not a replacement for a straight pair of facing hooks.

## Triggering and output

An eligible entity crossing an attached wire segment can power **both hooks at strength 15**. Each powered Hook supplies power to surrounding components and direct power into the wall block supporting it. Read the output at a Hook or its powered support, rather than expecting String to behave like a powered dust line.

Detection follows the wire's contact shape. Players, mobs, and other eligible entities can trigger it; it is not a players-only sensor. Entities that ignore block triggers are skipped, and spectators are excluded from the ordinary contact/recheck path. Simply standing near a line without intersecting it is not enough.

A triggered wire segment rechecks occupancy every **10 game ticks**: five conventional redstone ticks, or 0.5 seconds at 20 game ticks per second. It can remain powered across repeated checks while an eligible entity stays in the sensing area. After the entity leaves, release waits for a recheck; this is not a fixed half-second pulse for every crossing.

Hook state changes caused by an affected wire segment also schedule a ten-game-tick line recheck. No measured end-to-end trap latency or compatibility with every imported circuit is claimed.

## Breaking and disarming

Breaking an **armed, attached** segment ordinarily reports that segment as powered during removal. This can briefly activate the connected hooks before their recheck notices the gap. Breaking a wire is therefore not equivalent to quietly turning the detector off.

To suppress that removal-triggered pulse, hold **Shears in the main hand** and mine a wire segment. The server marks it disarmed before removal, so the hook calculation does not treat the cut segment as an armed trigger. This is a break action, not a use/right-click action. [Disarming callback][disarming-current] · [Hook calculation][hook-current] · [Server removal order][mining-current]

**Retained broken Shears also reach this disarming path.** The current client and server allow block destruction with a broken held stack, and the wire handler tests Shears identity without a broken-item check. A broken tool loses its special mining speed and fails correct-tool drop checks on blocks that require them; those limits do not prohibit breaking Tripwire, whose ordinary loot has no tool requirement. This is source-reviewed behavior, not an in-game broken-tool test. [Client destruction][client-current] · [Broken-stack permission][broken-current] · [Speed/drop guards][guards-current] · [Player drop gate][drop-gate-current] · [Wire loot][wire-loot-current]

Disarming does not promise that every attached machine remains motionless: an already powered output can turn off, and circuits can react to detachment or other nearby changes. Removing Hook support also breaks the Hook and alters the line. Replacing the missing String can form a fresh attached line again.

## Small example: a crossing indicator

This layout is source-derived and has **not been tested in game**.

1. On a flat floor, place two solid support blocks with **five empty block positions between them**.
2. Attach a Hook to each inward-facing wall. This occupies the two end positions in the gap and leaves three positions between the hooks.
3. Place one String in each of those three positions, making a straight continuous line at the hooks' height.
4. Place a Redstone Lamp beside one Hook, off to the side of the row so it does not interrupt the String.
5. Cross the middle of the line from the side. The Lamp should light; move clear and allow the occupancy recheck to release it.

If it does not work, check Hook facing and wall support, every String position, attachment, and whether the entity actually crosses the wire's height. The Lamp has its own off delay, so its visible light duration is not an exact tripwire timing measurement.

## Related pages

- [Tripwire Hook item](../items/TripwireHook.md) and [String](../items/String.md)
- [Pressure plates](PressurePlates.md), [Redstone Dust](RedstoneDust.md), and [Redstone basics](../redstone/Redstone.md)
- [Shears](../items/Shears.md)
- [Blocks](Blocks.md)

## Sources and verification

The disarming and broken-tool statements were rechecked at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716` on **2026-10-03**, including client/server destruction permission, pre-removal order, tool speed and harvest eligibility. The remaining guide was source-reviewed at `3e85592c4c78ebb420302360667a6c230dc0318d` on 2026-10-02. Registration, recipes, loot, wall support, line-length scan, current contact/removal callbacks, the actual shears-before-removal path, and scheduled-tick dispatch were checked. No in-game attachment, crossing, disarming, or timing test was run. Active data packs and connected circuits can affect the result.

- [Hook and wire registration](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java)
- [Hook item and String block-item mapping](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java)
- [Hook recipe](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/tripwire_hook.json)
- [Accepted planks](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/item/planks.json)
- [Hook loot](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/tripwire_hook.json)
- [String drop from Tripwire](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/tripwire.json)
- [Wall support, facing, attachment scan, output, and rechecks](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/TripWireHookBlock.java)
- [Contact, line updates, break-trigger behavior, and disarming](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/TripWireBlock.java)
- [Server mining and pre-removal hook](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java)
- [Removed-state side effects during block replacement](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java)
- [Held-tool block-destruction permission](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/ItemStack.java)
- [Entity contact dispatch](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Entity.java)
- [Spectator movement state](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/player/Player.java)
- [Recheck entity-query filter](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/EntityGetter.java)
- [Game-time scheduling](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/LevelAccessor.java)
- [Scheduled tick dispatch](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/level/ServerLevel.java)
- [Lamp timing](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/RedstoneLampBlock.java)

[disarming-current]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/TripWireBlock.java#L109-L123
[hook-current]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/TripWireHookBlock.java#L109-L151
[mining-current]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L256-L295
[client-current]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L115-L195
[broken-current]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/ItemStack.java#L1111-L1116
[guards-current]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L604
[drop-gate-current]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[wire-loot-current]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/tripwire.json
