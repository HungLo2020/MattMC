# Skunk Spray

**Skunk Spray** (`minecraft:skunk_spray`) is a temporary, non-colliding coating created by a spraying [Skunk](../mobs/Skunk.md). Use a Glass Bottle on a coated face to collect a [Stink Bottle](../items/StinkBottle.md), or break the coating to clear it. The placed coating and the skunk's active spray attack have different effects. [Registration][registration] · [Mob placement and effects][spray] · [Bottling][bottling]

## Creation and availability

An adult skunk builds harassment while fleeing feared creatures or panicking. Once that counter exceeds 200 and its spray cooldown is zero, it starts a spray lasting **60–119 ticks**, with a **200–399-tick** cooldown. These are tick counters, not promises about how long any individual approach will take. The bundled fear tag includes Players, Grizzly Bears, and Polar Bears; the avoidance goal excludes Creative and Spectator targets. Babies cannot start the spray through this trigger. [Avoidance and panic][goals] · [Trigger and cooldown][trigger] · [Fear tag][fears]

After its spraying animation builds up and the spray goal has run long enough, each eligible attempt has a 50% chance to cast toward a surface up to **five blocks** away. A coating is placed only if the target cell is air or replaceable and the attachment check accepts the hit surface. That placement branch has no `mobGriefing` game-rule check. It does not replace an arbitrary solid wall: the coating occupies the neighboring cell. [Placement gates][spray]

There is **no registered Skunk Spray inventory item** in the checked item registration, so this block has no Creative-category or [inventory-browser](../mechanics/InventoryBrowser.md) stack to request. No bundled recipe, block loot table, or world-generation placement was found. Its implemented creation route is the skunk behavior above; this review does not establish where a naturally spawning skunk can be found. See the [Skunk Spawn Egg](../items/SkunkSpawnEgg.md) for the separate entity item. [Item registration][items] · [Missing block-item fallback][item-fallback] · [Bundled data][data]

## Attachment and state

The block stores six directional face flags, `waterlogged`, and `age` from **0 to 3**, initially 0. Each occupied face needs a full face in the neighboring block's support shape or collision shape. Losing support removes that face; losing the last face removes the coating. Valid faces can be on floors, walls, or ceilings. [State defaults][state] · [Face properties][face-state] · [Support loss][support-loss] · [Attachment check][support]

The shared placement helper can create a waterlogged state in source water, and the spray block preserves the water state and schedules water updates. The skunk still applies its own air-or-replaceable target gate. Existing spray is not marked replaceable by its registration, so repeated spraying should not be assumed to accumulate extra faces on an existing coating. [Placement helper][placement] · [Water updates][water] · [Registration][registration] · [Mob target gate][spray] · [Replaceability flag][replaceable]

The coating breaks instantly and has no entity collision or correct-tool requirement. No bundled loot table returns a spray item, including with Shears or Silk Touch. Bottling is a separate use interaction. [Registration][registration] · [Loot lookup][loot] · [Missing-table fallback][missing-loot]

## Effects: active spray versus coating

During an active spray attempt, nearby living entities in the spray path, except skunks, receive **300 ticks of [Nausea](../effects/VisibilityEffects.md#nausea)** and copies of the skunk's current status effects. Nausea is applied by the mob's attack code; the placed block has no contact-damage or contact-effect handler. Walking across a remaining stain is not an implemented way to reapply those effects. [Active effects][spray] · [Complete coating class][block]

At the end of the spray, a skunk with active status effects also creates a shrinking area-effect cloud containing copies of those effects. A skunk with no active effects skips that cloud. The coating does not store potion effects for later use. The spray advancement call is a no-op in the checked trigger registry, so it does not establish a working advancement reward. [Conditional cloud][cloud] · [Advancement stub][advancement]

## Collecting and clearing spray

Use a **Glass Bottle on the exposed coated face**. The interaction checks the face opposite the hit direction. When that face is present, it removes that one face, grants one Stink Bottle, and consumes one Glass Bottle outside Creative. The filled bottle goes into the inventory or drops beside the player if it cannot fit. Removing the final face replaces the coating with air. A click without a matching occupied face does not collect a bottle. [Bottling and last-face removal][bottling]

The resulting Stink Bottle is a real item with stack limit **one**, but it is registered as a plain item. No throwing, drinking, spray placement, or status-effect use is implemented for it, and it has no bundled category-tab entry. Its Glass Bottle crafting-remainder property is not itself a recipe or an emptying action. [Stink Bottle registration][bottle-item] · [Basic item use][item-use] · [Category lists][categories]

Alternatively, break the coating, remove its support, or let it age away. Random ticks call its decay routine. Each call has a **1-in-8** aging branch: ages 0, 1, and 2 increase by one; an aging step at age 3 removes the block. That branch also ages adjacent spray blocks. Other calls schedule another attempt **50–100 ticks** later, as do adjacent patches that age without disappearing. There is no fixed lifetime in seconds, and decay grants no Stink Bottle. [Decay and scheduling][decay] · [Age increment and removal][age] · [Random-tick registration][registration]

## Related pages

- [Skunk](../mobs/Skunk.md)
- [Stink Bottle](../items/StinkBottle.md)
- [Glass Bottle](../items/GlassBottle.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `b823010659d7b5095ed021b1c99cf85627e2082a` on 2026-10-02. Traced the mob's trigger, placement/effect branches, all spray-block callbacks, inherited face support/water states, bottling, item registration, and category lists. Searched bundled recipes, loot, tags, and world-generation references. No in-game spraying, collection, decay-timing, water, or multiplayer test was run; the timings and gates above describe checked code rather than measured play.

[registration]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Blocks.java#L2404-L2415
[goals]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/alexsmobs/entity/EntitySkunk.java#L73-L105
[trigger]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/alexsmobs/entity/EntitySkunk.java#L143-L180
[fears]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/tags/entity_type/skunk_fears.json#L1-L9
[spray]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/alexsmobs/entity/EntitySkunk.java#L260-L309
[cloud]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/alexsmobs/entity/EntitySkunk.java#L184-L202
[block]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/alexsmobs/block/BlockSkunkSpray.java#L34-L158
[state]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/alexsmobs/block/BlockSkunkSpray.java#L42-L48
[face-state]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/MultifaceBlock.java#L109-L122
[support-loss]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/MultifaceBlock.java#L124-L145
[support]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/MultifaceBlock.java#L251-L265
[placement]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/MultifaceBlock.java#L193-L217
[water]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/alexsmobs/block/BlockSkunkSpray.java#L50-L65
[replaceable]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L831-L833
[bottling]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/alexsmobs/block/BlockSkunkSpray.java#L93-L115
[decay]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/alexsmobs/block/BlockSkunkSpray.java#L68-L86
[age]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/alexsmobs/block/BlockSkunkSpray.java#L117-L125
[bottle-item]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java#L1766-L1769
[item-use]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Item.java#L164-L197
[advancement]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/alexsmobs/misc/AMAdvancementTriggerRegistry.java#L3-L21
[items]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java
[item-fallback]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Item.java#L117-L120
[categories]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[loot]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L269-L277
[missing-loot]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[data]: https://github.com/HungLo2020/MattMC/tree/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft
