# Bell

A **Bell** (`minecraft:bell`) can be rung by using it, striking an appropriate side with a projectile, or applying a fresh redstone signal. It serves as a Villager meeting point and can reveal nearby Raider-tagged mobs with a brief glowing outline. Its mounting direction affects which sides respond to manual and projectile hits. [Registration][bell-reg] · [Ringing controls][ring] · [Meeting point][poi] · [Raider response][bell-entity]

## Obtaining and collecting

Find Bells in village meeting areas. A verified example is the Bell in the Taiga meeting-point-1 template, selected by the Taiga town-center pool. The village structure set and normal Overworld preset make this an active generation route when structures are enabled. [Actual Bell template][town-template] · [Pool and village][town-pool] [taiga-village] · [Structure set and biome][villages] [village-biome] · [Normal preset and active selection][normal] [biomes] [taiga] [structure-filter]

Bells have **no bundled crafting recipe**. At base prices, these trades can supply one Bell for **36 Emeralds**:

| Trading rules | Bell-selling level |
| --- | --- |
| Ordinary Armorer | Apprentice, level 2 |
| Toolsmith or Weaponsmith | Apprentice, level 2 |
| Armorer with the optional trade-rebalance feature | Journeyman, level 3 |

The entries have 12 uses before restocking, and offers are selected from their level's pool. Prices can change through trading mechanics. See [Trading](../trading/Trading.md) and [Villager](../mobs/Villager.md) for professions, selection, and restocking. [Ordinary pools][trades] · [Rebalanced Armorer][rebalance] · [Feature selection and offer selection][trade-dispatch] · [Price/stock constructor][trade-prices]

The [Bell item](../items/Bell.md) is also in Creative. Mining normally returns **one Bell**, with no Silk Touch requirement or Fortune bonus. A pickaxe is the efficient tool, but this MattMC registration does **not** require a correct tool for drops: hand harvesting can recover it too. Hardness is **5**. The loot has an explosion-survival condition and follows ordinary block-drop rules. [Item/Creative entries][bell-item] [creative] · [Registration][bell-reg] · [Pickaxe tag][pickaxe] · [Harvest gate][gate] [harvest] · [Loot][bell-loot] [loot-rule]

## Mounting and support

The same item makes all four attachment forms:

| Attachment | How to place it | Required support |
| --- | --- | --- |
| Floor | Use the top of a support block | A sturdy full top face below |
| Ceiling | Use the underside of a support | A downward face above that supports the center |
| Single wall | Use a horizontal face | A sturdy face on the supporting wall |
| Double wall | Place between opposite sturdy wall faces on the placement axis | Opposite wall supports; losing one converts it to single-wall attachment |

For floor or ceiling placement, the Bell's facing follows the player's horizontal facing. On a wall it points toward its supporting block. A horizontal placement that cannot use the wall can try a floor/ceiling fallback, so check the actual attachment before assuming how to ring it. Its collision shape changes with the attachment and is not an empty decorative shape. [Placement and attachment selection][bell-support] · [Sturdy-face support][support] · [Collision shapes][bell]

Losing the required floor, ceiling, or last wall support removes the Bell, using ordinary block-drop handling. Adding a suitable opposite wall can convert a single-wall Bell to double-wall; removing one wall of a double-wall Bell points it toward the remaining support. There is no waterlogged state. [Support updates][bell-support] · [Shape-change removal][support-drop] · [Bell loot][bell-loot] · [Registered states][bell]

## Ringing controls

For manual use or a projectile, aim at a **horizontal side of the lower Bell body**, no higher than approximately **0.8124 of the block's height**. Top/bottom hits and hits on the upper support fail the ringing-face check. The allowed horizontal side depends on the attachment:

- **Floor:** a side on the same axis as its facing; a north/south-facing Bell accepts its north or south side
- **Wall or double wall:** a side perpendicular to its facing/support axis
- **Ceiling:** any horizontal side

[Hit position and face rules][ring]

Use the block to ring it; mining is a different action. Ordinary main-hand use can reach the Bell's empty-hand fallback even while holding an item. Sneak/Crouch with a held item bypasses that block interaction. Projectiles run the same proper-hit check, whether or not their owner is a player. [Use/projectile callbacks][ring] · [Interaction dispatch][use]

A transition from **unpowered to powered** rings the Bell through its redstone callback. Holding a constant signal does not make it ring repeatedly; remove power before the next pulse. This path does not need a projectile-style hit face. A block-triggering explosion can also call the ring handler. [Power and explosion callbacks][bell] · [Neighbor signal lookup][signals]

### Ringing versus the search cache

There is **no 60-tick lockout on ringing** in the Bell handler. A new accepted ring restarts the shaking animation and queues the Bell event; the event resets the resonance counter. The 60-tick value controls how often that event rebuilds its cached list of nearby living entities: it rebuilds on the first ring, or on a later ring when more than 60 game ticks have passed since the previous refresh. [Ring handler and event][ring-ticks] · [Entity cache][hearing] · [Active server event dispatch][event-dispatch] [ring-event]

This distinction matters when looking for a mob that just entered the area. Rapidly ringing can still reuse the older list, and repeated rings can delay the uninterrupted resonance sequence described below. A single ring's visible shake lasts **50 game ticks**, about 2.5 seconds at normal tick speed. [Shake and resonance counters][ring-ticks]

## Villagers and raids

All Bell block states are registered as **meeting points**, separate from profession job sites. Villagers can acquire a meeting point and use it for their meeting activities. Placing a Bell is not a way to assign a trading profession; use the [Villager job-site guide](../mobs/Villager.md#professions-and-job-sites) for that. [Meeting-point registration][poi] · [Meeting-point acquisition and activities][goals] · [Active Villager brain wiring][villager]

A processed ring writes a heard-bell memory to cached, living entities less than **32 blocks** from the Bell's center. Villagers' active core behavior responds by requesting the **hide activity when there is no Raid at their position**. That activity seeks a home/hiding point and uses normal navigation; it does not teleport villagers into safety. Available homes and routes still matter. [Hearing memory][hearing] · [No-raid reaction][reaction] · [Hiding target selection][hide] · [Active goal packages][goals] [villager]

During an existing Raid, the separate raid/pre-raid activities control their response. The pre-raid package can have a Villager approach its meeting point and ring the Bell; the active raid package has its own hiding behavior. The Bell's ring handler itself does not start a Raid. [Pre-raid and raid behaviors][goals] · [Villager ringing][ring-ai] · [Bell handler][ring]

## Revealing nearby Raiders

For an uninterrupted ring, the server starts resonance once the ringing counter reaches **5 ticks**, provided the cached list contains a living **Raider-tagged entity within 32 blocks**. After the resonance counter has advanced for **40 ticks**, the server applies **Glowing for 60 ticks**, or three seconds, to qualifying cached Raiders within **48 blocks**. This normally places the outline at roughly **45 game ticks after the processed ring event**, about 2.25 seconds, while the block is actively ticking. [Resonance timing and server action][ring-ticks] · [32-block trigger][hearing] · [48-block target and 60-tick effect][outline] · [Active ticking][bell] [level-tick] [chunk-tick]

The current Raider tag includes **Evokers, Pillagers, Ravagers, Vindicators, Illusioners, and Witches**. The check uses entity type and distance, not membership in an active Raid, and does not raycast for a clear view. Thus the outline can help locate a qualifying mob behind a wall, subject to the cached-list and range limits. A Raider between 32 and 48 blocks does not by itself start resonance, though it can be outlined when a closer Raider starts it. [Raider tag][raiders] · [Trigger and target predicates][hearing] [outline]

## Small signal setup

As an untested source-derived example, place a floor Bell on a solid support block, put a [Lever](Lever.md) on an exposed face of that support, and toggle it on to ring the Bell. Toggle off to reset the powered state before ringing again. For a Raider search, let one ring finish its resonance rather than continually restarting it. [Lever signaling][lever] · [Neighbor power][signals] · [Bell power transition][ring] · [Resonance][ring-ticks]

## Verification scope

Source-reviewed at `8d065c943710a8d4e3d609b0406dda95ed62cc63` on 2026-10-02. Registration, loot and tool gate, village/trade acquisition, support, proper-hit tests, server event dispatch, active Villager behavior, Raider tag/ranges, resonance, and ticker wiring were inspected. No in-game ringing, support, trade, village, redstone, or Raid test was run.

[bell-reg]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/Blocks.java#L5380-L5384
[ring]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/BellBlock.java#L68-L145
[poi]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiTypes.java#L135
[bell-entity]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/entity/BellBlockEntity.java
[town-template]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/structure/village/taiga/town_centers/taiga_meeting_point_1.nbt
[town-pool]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/worldgen/template_pool/village/taiga/town_centers.json
[taiga-village]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/worldgen/structure/village_taiga.json
[villages]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/worldgen/structure_set/villages.json
[village-biome]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/village_taiga.json
[normal]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[biomes]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java
[taiga]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java#L75-L80
[structure-filter]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/chunk/ChunkGeneratorStructureState.java#L50-L64
[trades]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L490-L570
[rebalance]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L920-L930
[trade-dispatch]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/entity/npc/Villager.java#L819-L842
[trade-prices]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1422-L1483
[bell-item]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/Items.java#L2445
[creative]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1087-L1092
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[gate]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[harvest]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L297
[bell-loot]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/loot_table/blocks/bell.json
[loot-rule]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/Block.java#L411-L416
[bell-support]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/BellBlock.java#L170-L266
[support]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/FaceAttachedHorizontalDirectionalBlock.java#L32-L35
[bell]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/BellBlock.java
[support-drop]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/Block.java#L213-L227
[use]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L340-L400
[signals]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/SignalGetter.java#L60-L83
[ring-ticks]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/entity/BellBlockEntity.java#L42-L101
[hearing]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/entity/BellBlockEntity.java#L104-L136
[event-dispatch]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/server/level/ServerLevel.java#L1183-L1217
[ring-event]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/BaseEntityBlock.java#L23-L26
[goals]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/entity/ai/behavior/VillagerGoalPackages.java
[villager]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/entity/npc/Villager.java#L223-L249
[reaction]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/entity/ai/behavior/ReactToBell.java
[hide]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/entity/ai/behavior/LocateHidingPlace.java
[ring-ai]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/entity/ai/behavior/RingBell.java
[outline]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/entity/BellBlockEntity.java#L161-L169
[level-tick]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/Level.java#L440-L459
[chunk-tick]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java#L710-L783
[raiders]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/entity_type/raiders.json
[lever]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/LeverBlock.java
