# Trial Chambers

**Trial Chambers** are underground Overworld expeditions built from Tuff and Copper, with connected corridors, varied combat rooms, supplies and reward blocks. Plan to establish a return route, tackle individual [Trial Spawners](../blocks/TrialSpawner.md), then spend the matching keys at [Vaults](../blocks/Vault.md). Finding the structure does not guarantee a particular room, key or rare prize. [Structure definition][structure] · [Starting templates][start-pool] · [Corridor choices][corridor-pool]

For a first visit, bring your own food and equipment and begin with ordinary encounters. For a [Mace](../items/Mace.md#obtaining), the two relevant goals are **Breeze Rods from player-attributed Breeze kills** and a **possible Heavy Core from an ominous Vault**. Neither discovering the chamber nor clearing an ordinary spawner completes that route. See [Breeze drops](../mobs/Breeze.md#drops) and [Vault reward differences](../blocks/Vault.md#normal-versus-ominous-rewards).

## Finding a chamber

### Search in the normal Overworld

The bundled normal-world route allows Trial Chambers under many Overworld biomes, including plains, forests, deserts, mountains, oceans, Lush Caves and Dripstone Caves. **Deep Dark is absent from the allowed biome tag.** Biome eligibility is checked at the structure's generation point, so the surface biome alone does not establish that a candidate below it succeeds. The normal preset's Nether, End and Primordial Caves biome sources do not provide the allowed biomes for this structure. [Allowed biomes][biomes] · [Normal preset][preset] · [Generation-point biome check][structure-code] · [Structure-set filtering][set-filter]

The sampled **starting height is Y −40 to −20**. That describes the start used to assemble the structure, not the full vertical extent, a guaranteed entrance level or a safe teleport height. The placement set uses **34-chunk spacing and 12-chunk separation** to choose candidates; those values are not a promise of one chamber every fixed number of blocks. [Structure settings][structure] · [Placement set][structure-set] · [Candidate calculation][spread] · [Start and piece placement][jigsaw]

Look for constructed Tuff-brick walls and Copper work while exploring underground, or use the map route below. Once you have a destination, dig a controlled staircase or inspect an existing cave approach. Mark the surface entry and each junction; a hole opened into a ceiling can put you above an arena rather than at its doorway. The checked templates have multiple levels, stairs, grates and separate room connections. [Corridor end][end-two] · [Entrance example][entrance-template] · [Assembly room][assembly]

**New structure starts require structure generation to be enabled.** The loaded structure sets, biome checks and jigsaw placement must also succeed. These are bundled-generation rules, not a retrofit promise for terrain already generated in an older world. Data packs, custom presets and saved template overrides can change the result; search newly generated terrain when checking the current defaults. [Generation/load distinction][generation-stage] · [Candidate generation][generator] · [Template lookup][template-manager]

### Get a Trial Explorer Map

A **Journeyman, level-3 Cartographer** can offer a **Trial Explorer Map**. The regular offer has a **base price of 12 Emeralds plus 1 Compass**. The Cartographer uses a [Cartography Table](../blocks/CartographyTable.md); follow [Trading](../trading/Trading.md#offers-and-trading-levels) to level one up and check the displayed price before buying. [Current level-3 pool][map-trade] · [Map construction and payment][map-offer] · [Map and level names][names]

The map is one candidate in that level's pool, not guaranteed on every Cartographer. Offers are selected when that level's trades are added, and this particular offer is absent if its destination search fails. Its search starts from the villager in the villager's current dimension and uses the Trial Chambers structure tag. A missing offer is not proof that no chamber exists anywhere in your world. [Offer selection][offer-selection] · [Trade dispatch][trade-dispatch] · [Destination tag][map-tag] · [Search gates][map-search]

Use the structure marker to reach the destination region, then investigate underground. It is a **scale-2 filled map**, with a terrain preview and destination marker; it does not show chamber corridors or the entrance depth. The search skips structures already referenced by that lookup system, rather than checking whether every chest or Vault is untouched. Do not treat the map as a reservation of fresh loot. [Map creation][map-offer] · [Structure reference check][map-reference] · [Maps](../items/Map.md)

### Operator search

With permission level 2, run this in the **Overworld**:

```text
/locate structure minecraft:trial_chambers
```

The command searches the current dimension. It does not place a chamber or move you to another dimension. Its result reports X and Z with `~` for Y; the clickable suggestion keeps your current height and is **not a safe arrival point**. Read the [general locate cautions](Structures.md#finding-a-structure) before using it. [Command permission and search][locate] · [Coordinate output][locate-output]

## Pack for a controlled expedition

- **Usable armor, a melee weapon and a Shield.** Check [durability and repair](../mechanics/Durability.md) before descending. A ranged weapon is useful against some enemies, but a [Breeze deflects ordinary incoming projectiles](../mobs/Breeze.md#deflection-and-immunities)
- **Enough food and healing for several fights.** [Supply chests](#choose-a-reward-route) can help, but their contents are random; do not depend on finding the next meal after a wave
- **A Pickaxe, spare blocks and lighting.** Use them to make a staircase, bridge a gap and mark the exit. Lighting helps you inspect the room; [Torches do not generally disable Trial Spawners](../blocks/TrialSpawner.md#wave-size-and-spawning)
- **A Water Bucket and a plan for falls.** Carrying a bucket is not protection by itself. Keep combat away from exposed edges, especially around Breezes
- **Milk if you want control over effects.** It clears beneficial effects too. Remove Bad Omen before approaching a trial if you want an ordinary encounter; [Milk cannot undo an already ominous spawner](../effects/OmenEffects.md#clearing-effects-and-cancellation)
- **Inventory space and a place to unload.** Spawners and Vaults eject loose items. Keep room for keys, Rods, equipment and the reward stacks you actually want

For group visits, agree who is entering each fight and how to divide loose rewards. Added participants can increase a spawner's encounter quota; simply standing behind a wall is not reliable protection from joining an already active encounter. See [Trial Spawner participants](../blocks/TrialSpawner.md#activation-and-participants).

## Read the rooms before advancing

The chamber is assembled from template pools, with rotated pieces and separately selected additions. The source includes entrance and atrium pieces, corridors, intersections, hallways and several chamber shapes. **These are layout possibilities, not a fixed floor plan or a checklist of rooms every structure must contain.** Connections, random choices, available space, depth and world-height limits affect assembly. [Corridor pool][corridor-pool] · [Hallway pool][hallway-pool] · [End-room pool][end-room-pool] · [Assembly rules][jigsaw]

| What you find | What to do before moving on |
| --- | --- |
| Corridors, intersections and stairways | Mark the way back and inspect both higher and lower passages; avoid opening several unexplored branches during a fight |
| A Trial Spawner with a displayed mob | Identify the encounter and nearby escape space before approaching; another spawner elsewhere can have a different configuration |
| A Vault cycling an item display | Note its position and whether you have the matching key; the display is a preview, not a promised next prize |
| A Chest, Barrel or Decorated Pot | Secure the approach, leave inventory space, and distinguish ordinary container loot from a key-operated Vault |
| Dispensers, buttons or pressure plates | Inspect the firing direction and controls before pressing or stepping on them |
| Magma, Powder Snow, water or open drops | Choose a route around the hazard or alter the approach before combat can push you into it |

The hazard examples come from actual connected templates. Eruption-room pieces contain **Magma Blocks**; `chamber_2` contains **Powder Snow**; other pieces contain water and dispenser mechanisms. For Powder Snow crossings, review [Leather Boots and freezing](../blocks/Snow.md); for hot floors and fluids, see [Magma](../blocks/SoulSandSoilAndMagma.md) and [Water and Lava](../blocks/WaterAndLava.md). The names of these saved pieces are source labels, not signs you must find in the world. [Eruption][eruption] · [Powder Snow room][snow-room] · [Water-containing end piece][end-two]

Generated dispenser examples pair the device with **buttons or a pressure plate**. Their chamber loot table can supply arrows, Fire Charges, water buckets and splash or lingering potions, among other entries. Different rolls can therefore produce different hazards. A powered dispenser dispatches the selected item's behavior; it is not harmless decoration. See [Dispenser and Dropper](../blocks/DispenserAndDropper.md) for inspection and operation. [Device templates][dispenser-template] [floor-dispenser] · [Contents][dispenser-loot] · [Powered operation][dispenser-code] · [Item behaviors][dispense-behaviors]

A Breeze can reposition by jumping and push you with wind shots. Those shots can also trigger supported nearby mechanisms when the relevant rule permits. Stay aware of the floor and firing lines, not just the mob's health. A Breeze launch does not grant the player's Wind Charge fall allowance; use the [Breeze combat guide](../mobs/Breeze.md#wind-shots-and-nearby-mechanisms) for the exact limits.

## Clear one encounter at a time

1. **Prepare the room edge.** Locate the spawner, nearby devices, other hostile mobs and the route back. Let the group finish eating and arranging equipment before closing in
2. **Commit deliberately.** Eligible nearby players activate the waiting spawner. The encounter can include more participants as they approach, so do not assume only the first player counts
3. **Follow the actual wave.** The source chooses among melee, small-melee and ranged configurations, with a separate Breeze route. Possible enemies include Zombies, Husks, Spiders, Slimes, Cave Spiders, Silverfish, baby Zombies, Skeletons, Strays, Bogged and Breezes; this is not a promise of every kind in one chamber. [Pool aliases][structure] · [Breeze configuration][breeze-config] · [Bogged configuration][bogged-config]
4. **Wait for the spawner's reward sequence.** Defeating one mob is not the same as finishing its encounter. The block tracks its own spawned wave, then ejects rewards for registered participants; the items are available for pickup in the world
5. **Collect, recover and choose the next room.** Check health, food, durability and inventory space before advancing. A reward shutter opening does not certify that other spawners, lingering hazards or wandering mobs are gone

[Trial Spawner](../blocks/TrialSpawner.md#clearing-rewards-and-cooldown) owns the exact detection, counts, completion conditions, reward selection and cooldown. Keep the block intact for return visits: breaking it does not recover a Trial Spawner item, even with Silk Touch.

If a waiting spawner never starts, check the [activation and spawning gates](../blocks/TrialSpawner.md#activation-and-participants): the ordinary route excludes Creative/Spectator participants and needs non-Peaceful difficulty, `doMobSpawning` and MattMC's `spawnerBlocksEnabled` rule enabled. A room existing does not bypass those gates.

The structure's ordinary spawn overrides contain empty lists inside its **recorded piece bounds**. That suppresses those natural-spawn lists there; it does not stop Trial Spawners, erase existing creatures or seal off surrounding caves. Treat the room you have inspected as your working area, rather than assuming the entire underground complex is safe. [Spawn overrides][structure] · [Piece-bound override dispatch][spawn-overrides]

## Decide when to make trials ominous

**Ordinary trials are the simpler starting route.** They can award Trial Keys for normal Vaults, and Breeze encounters can supply Rods without requiring an ominous conversion. If your goal is a Heavy Core or other ominous-only Vault rewards, prepare for the additional encounter hazards first.

Drinking an [Ominous Bottle](../items/OminousBottle.md) gives **Bad Omen**. Detection by an eligible Trial Spawner can convert it to **Trial Omen** and make that spawner ominous. Merely carrying a bottle or key does not do this. Decide before approaching, especially in a group where another affected participant can trigger conversion. [Trial Omen](../effects/OmenEffects.md#trial-omen) explains the effect and Milk timing.

Conversion can replace an active ordinary encounter or turn a normal spawner ominous during its cooldown. It is not limited to a fresh, untouched room. Ominous configurations can add equipment or change mob counts, and overhead item spawners can drop projectiles or potion hazards. Plan for attacks from above and [lingering triggered effects](../effects/TriggeredEffects.md#ominous-trials), then use [Trial Spawner: becoming ominous](../blocks/TrialSpawner.md#becoming-ominous) for the detailed rules.

Removing Trial Omen prevents you supplying it to later scans, but **does not revert a spawner already changed**. Likewise, a Vault does not become ominous because you bring an omen nearby. Naturally placed normal and ominous Vaults already have their own key/reward configurations. A found Ominous Trial Key can be used at the matching eligible Vault without first drinking another bottle. [Omen removal](../effects/OmenEffects.md#clearing-effects-and-cancellation) · [Ominous Vaults](../blocks/Vault.md#ominous-vaults)

## Choose a reward route

For building materials, see [salvaging Trial Chambers copper](../blocks/CopperConstruction.md#salvaging-trial-chambers-copper) for selected placed-block finds, the required pickaxe and uses that keep their waxed finish.

| Goal | Route and limitation |
| --- | --- |
| Supplies and ordinary container treasure | Search generated Chests and Barrels. Assigned tables differ: entrance, corridor, intersection and supply loot are not one shared contents list |
| A Trial Key | Possible ordinary spawner completion reward, entrance-chest roll or corridor-pot roll; no particular fight, chest or pot guarantees one |
| Normal Vault rewards | Spend one matching [Trial Key](../items/TrialKey.md) at a Vault that can still reward you |
| An Ominous Trial Key | Possible ominous spawner completion reward; clearing the harder encounter still does not guarantee a key |
| Ominous Vault rewards | Spend one [Ominous Trial Key](../items/OminousTrialKey.md) at an eligible ominous Vault; this is the Heavy Core route |
| Breeze Rods | Defeat a Breeze with player attribution under the [mob's loot rules](../mobs/Breeze.md#drops); these are separate from the spawner's completion reward |
| Flow, Guster or Scrape Pottery Sherds | Find pots decorated with those ingredients and use the [shattering method](../blocks/DecoratedPot.md#breaking-keep-the-pot-or-recover-ingredients) to recover them |

Supply-chest possibilities include food, arrows, building materials, Torches, potions and Milk. Corridor pots can roll valuables, a Trial Key or the **Creator (Music Box)** disc; their stored loot is separate from their face decorations. None of those named rolled items is guaranteed. [Supply template][supply-template] · [Supply loot][supply-loot] · [Decor choices][decor-pool] · [Decorated pot example][flow-pot] · [Pot loot][pot-loot]

Not every ordinary chest is merely a supply box: the checked `entrance_1` template has a chest assigned the **normal reward table**, as well as two entrance-table chests. It opens as a chest, without a Trial Key or the Vault's per-player history. This is a template-specific possibility, not a guarantee of three such chests in every chamber. Ordinary randomizable containers fill their assigned loot once; returning after a trial cooldown does not refill them. [Entrance chest data][entrance-template] · [Container access][container-access] · [One-time loot fill][container-loot]

At a Vault, wait for it to become active and use the matching unmodified key without Sneak/Crouch. Stay to collect every ejected stack. In ordinary play, treat each placed Vault as **one opening per player**; another player's eligibility is separate, and the loose output is not protected from other players picking it up. Waiting for a Trial Spawner's cooldown does not renew your Vault eligibility. The [Vault guide](../blocks/Vault.md#player-history-and-persistence) covers the saved record and its finite history limit.

Choose the right reward family before spending keys. Normal rewards include possible Bolt templates, a Guster Banner Pattern, Precipice and a Trident; ominous rewards include possible Flow templates, a Flow Banner Pattern, Creator and a Heavy Core, with Wind Burst books in its rare table. These are **possible rolls**, and the cycling display does not select your prize. Follow [normal versus ominous rewards](../blocks/Vault.md#normal-versus-ominous-rewards) for the tables and probabilities.

## Leave a useful return route

- Mark a continuous path from your cleared area to the surface, including the way down from any ledge you built up to
- Note which spawners you want to revisit, and which Vaults have already rewarded you; those are separate kinds of progress
- Collect loose rewards before leaving and unload valuable Rods, keys and equipment somewhere you can reliably return to
- Clear an unwanted omen before a later ordinary visit; removing it now does not cancel already ominous blocks
- Preserve spawners and Vaults. Mining either block does not recover it as a normal Survival item

Spawner encounters can be repeated after their cooldown under the applicable spawning and ticking rules. Container loot and Vault reward history work differently. If your remaining objective needs another Vault opening, explore other eligible Vaults instead of assuming a completed room's cooldown resets everything. [Spawner cooldown](../blocks/TrialSpawner.md#clearing-rewards-and-cooldown) · [Vault persistence](../blocks/Vault.md#player-history-and-persistence)

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. The active registry/generation route, structure set, biome eligibility, map/locate dispatch, template pools, decoded NBT contents and connected candidate routes were checked. This is an expedition guide; the linked block, effect, mob and item pages own their detailed mechanics.

The loaded `minecraft:jigsaw` structure type assembles pool elements, whose placement loads the saved block-entity data. That connects the resource definitions to actual spawners, Vaults and randomizable containers. Candidate connector compatibility is not a simulation of a seeded world or proof that every piece fits. [Registry loading][registry] · [Registered jigsaw type][structure-types] · [Piece placement][pool-piece] · [Template placement][single-pool] · [Block-entity data loading][template-place]

**No in-game search, world generation, navigation, combat, multiplayer, loot-frequency or timing test was run.** Preparation advice follows the inspected rules. No fixed layout, safe arena, guaranteed rare reward or farm rate is claimed.

Related: [Structures](Structures.md) · [Trial Spawner](../blocks/TrialSpawner.md) · [Vault](../blocks/Vault.md) · [Breeze](../mobs/Breeze.md) · [Omen effects](../effects/OmenEffects.md) · [Mace](../items/Mace.md)

[structure]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/structure/trial_chambers.json
[start-pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/trial_chambers/chamber/end.json
[corridor-pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/trial_chambers/corridor.json
[biomes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/trial_chambers.json
[preset]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[structure-code]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/Structure.java#L131-L142
[set-filter]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/chunk/ChunkGeneratorStructureState.java#L50-L64
[structure-set]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/structure_set/trial_chambers.json
[spread]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/placement/RandomSpreadStructurePlacement.java#L69-L85
[jigsaw]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/pools/JigsawPlacement.java
[end-two]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/trial_chambers/corridor/end_2.nbt
[entrance-template]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/trial_chambers/corridor/entrance_1.nbt
[assembly]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/trial_chambers/chamber/assembly.nbt
[generation-stage]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/chunk/status/ChunkStatusTasks.java#L40-L65
[generator]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L453-L579
[template-manager]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager.java#L66-L129
[map-trade]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L401-L409
[map-offer]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1542-L1586
[names]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/assets/minecraft/lang/en_us.json
[offer-selection]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L233
[trade-dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/Villager.java#L819-L840
[map-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/worldgen/structure/on_trial_chambers_maps.json
[map-search]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerLevel.java#L1328-L1344
[map-reference]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L277-L306
[locate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/commands/LocateCommand.java#L55-L107
[locate-output]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/commands/LocateCommand.java#L162-L185
[hallway-pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/trial_chambers/hallway.json
[end-room-pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/trial_chambers/chambers/end.json
[eruption]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/trial_chambers/chamber/eruption.nbt
[snow-room]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/trial_chambers/chamber/chamber_2.nbt
[dispenser-template]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/trial_chambers/dispensers/chamber.nbt
[floor-dispenser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/trial_chambers/dispensers/floor_dispenser.nbt
[dispenser-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/dispensers/trial_chambers/chamber.json
[dispenser-code]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/DispenserBlock.java#L82-L136
[dispense-behaviors]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java
[breeze-config]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/trial_spawner/trial_chamber/breeze/normal.json
[bogged-config]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/trial_spawner/trial_chamber/ranged/poison_skeleton/normal.json
[spawn-overrides]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L451
[supply-template]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/trial_chambers/chests/supply.nbt
[supply-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/supply.json
[decor-pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/trial_chambers/decor.json
[flow-pot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/trial_chambers/decor/flow_pot.nbt
[pot-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/pots/trial_chambers/corridor.json
[container-access]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/RandomizableContainerBlockEntity.java#L49-L89
[container-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/RandomizableContainer.java#L72-L89
[registry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/resources/RegistryDataLoader.java
[structure-types]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/StructureType.java#L23-L44
[pool-piece]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/PoolElementStructurePiece.java#L91-L127
[single-pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/pools/SinglePoolElement.java#L137-L181
[template-place]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java#L292-L310
