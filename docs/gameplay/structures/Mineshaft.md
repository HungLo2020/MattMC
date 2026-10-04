# Mineshaft

Explore a **Mineshaft** for reusable track, building materials, possible chest-minecart supplies, and a cave-spider spawner worth preserving if you want a later project. Establish a clear exit before collecting anything: passages branch, change height, and can open above deep caves. A Mineshaft does not guarantee a chest cart, spawner, or particular reward. [Layout][pieces] · [Cart and spawner placement][encounters]

## Where to search

Both bundled variants use the same procedural room, corridor, crossing and stair system:

| Variant and structure ID | Eligible biomes in the bundled normal world | Recognition and height |
| --- | --- | --- |
| Normal, `minecraft:mineshaft` | 50 Overworld biomes: the ordinary Overworld set except the three Badlands biomes and Deep Dark; includes Lush Caves and Dripstone Caves | Oak planks, fences and log supports; the layout is shifted below sea level |
| Mesa, `minecraft:mineshaft_mesa` | Badlands, Wooded Badlands and Eroded Badlands | Dark Oak materials; its layout center is shifted to a height between sea level and the sampled surface, or sea level when that surface is lower |

These are biome eligibility and placement rules, not a guarantee of exposed entrances or a fixed tunnel Y level. For an above-ground search, inspect [Badlands](../biomes/DesertsBadlandsAndSavannas.md#badlands) slopes and openings for Dark Oak supports; ordinary cave exploration can reveal Oak corridors. [Normal definition][normal] · [Normal eligibility][normal-biomes] · [Overworld inventory][overworld-biomes] · [Mesa definition][mesa] · [Mesa eligibility][mesa-biomes] · [Badlands tag][badlands] · [Materials and vertical placement][structure] · [Below-sea-level adjustment][height]

Search the **Overworld** in the bundled normal setup. The Nether, End and loaded [Primordial Caves](../dimensions/PrimordialCaves.md#what-currently-generates) biome selections do not intersect these eligibility tags. This follows the loaded biome sources and structure filtering, rather than an unconditional dimension-name ban; data packs and custom worlds can differ. New starts also require structure generation to be enabled. [Normal preset][preset] · [Primordial definition][primordial] · [Biome-source filtering][filter] · [Generation setting][generation-gate]

The shared structure set uses spacing **1 chunk**, separation **0**, and a **0.004 frequency gate**. That is a 0.4% candidate gate before structure/biome validity, not one Mineshaft per 250 chunks or a distance you can rely on. Biome acceptance uses the generated start position, including its height. Individual pieces can subsequently be skipped by liquid-boundary checks or the **Deep Dark blocking tag**. [Structure set][set] · [Candidate placement][spread] · [Frequency test][frequency] · [Start-biome check][valid-biome] · [Piece exclusions][piece-exclusions] · [Blocking tag][blocking]

### Locating with commands

With permission level 2, use either command in the dimension you want to search:

```text
/locate structure minecraft:mineshaft
/locate structure minecraft:mineshaft_mesa
```

Each command selects that exact variant. The result gives **X and Z with `~` for Y**, and the suggested teleport preserves your current height. It does not identify a chest, spawner, entrance, or safe landing. Approach through a controlled descent, and check dimension, ID and world settings before treating a failed search as evidence of absence. [Locate permission and search][locate] · [Result coordinates][locate-result]

## Pack for the passage, then clear a route

Bring food, armor, a usable weapon and pickaxe, plenty of lighting, spare solid blocks, and **[Shears](../items/Shears.md)** if you want intact webs. Leave inventory space for rails and supplies. Check durability: MattMC retains fully worn tools as broken items, and a broken tool fails the correct-tool drop check. [Broken-item state][broken] · [Drop qualification][usable-tool]

1. **Mark the entrance and junctions.** Work one branch at a time, including upper openings in crossings. Generation assembles a starting room with optional corridors, crossings and descending stair passages; branches can shorten or stop when pieces would collide or expansion reaches its limits. There is no required final treasure room. [Piece selection and limits][pieces] · [Corridor sizing][corridor-size] · [Crossings][crossings] · [Stairs][stairs]
2. **Check the floor before advancing or harvesting.** Plank walkways can cross cave gaps, supported by logs below or Iron Chains above. Add your own safe standing area and edge protection before removing planks, rails or supports. The generator's liquid checks do not survey every surrounding cave: inspect adjacent drops and any exposed lava before widening a passage. Lava is an ambient underground hazard, not a required Mineshaft trap. [Floor and track placement][floors] · [Supports][supports] · [Liquid checks][piece-exclusions] · [Terrain fluid policy][fluids]
3. **Clear webs from the retreat route before approaching a cage.** Cobwebs slow you without forming a solid wall; Cave Spiders inherit the Spider's exemption from that slowing and can climb walls. Their 0.7-block-wide, 0.5-block-tall body makes a one-block opening an unreliable barrier. Use enclosed barriers and an exit you can actually traverse. See [Cobweb](../blocks/Cobweb.md) and [Cave Spider](../mobs/CaveSpider.md). [Web contact][web] · [Inherited movement][spider] · [Cave Spider size][spider-size]

Bring [Milk](../items/MilkBucket.md) or a [Honey Bottle](../items/HoneyBottle.md) for Poison, and retreat before drinking. A successful Cave Spider bite applies **Poison I for 7 seconds on Normal or 15 seconds on Hard**, assuming 20 TPS; Easy bites do not add Poison. Milk clears all effects, including useful ones; Honey removes Poison specifically. Another bite can apply it again. Poison only deals its own damage while health is above 1 point (half a heart), but a bite or fall can still finish a weakened player. [Attack callback][bite] · [Poison damage][poison] · [Drink effects][remedies]

## Handling a cave-spider corridor

A newly selected corridor has a one-in-three rail flag. Only corridors without that flag get the separate **one-in-23 spider-corridor selection**. A selected spider corridor attempts dense webs and at most one ordinary Cave Spider spawner, subject to placement/interior checks. Other corridors can still have scattered webs, so a web alone does not prove a spawner is ahead; a Mineshaft can also have more than one selected spider corridor. [Corridor flags][corridor-size] · [Webs and spawner][encounters]

Treat the cage as active while approaching. The ordinary default requires a living, nonspectator player **strictly within 16 blocks** of its center, plus enabled spawners. Its spawn positions must pass collision, obstruction and the registered monster checks. In the bundled Overworld these include **block light 0**, so light the possible spawning spaces, including adjoining dark recesses. Lighting one face of the cage is not proof that all candidate spaces are covered, and lighting does not instantly end an existing fight. [Spawner tick][spawner-tick] · [Player filter][near-player] · [Spawn binding][spawn-binding] · [Darkness checks][darkness] · [Overworld light setting][dimension] · [Existing-target behavior][spider-target]

Decide whether to preserve it before mining. **Breaking the cage never returns a spawner item, even with Silk Touch.** A successful Survival break with a usable correct pickaxe can award **15–43 XP** when block drops are enabled. Keep it if you want a later spawn project; use the [Monster Spawner guide](../blocks/MonsterSpawner.md) for timing, nearby-mob limits and troubleshooting. Its default attempts are not a tested farm-output rate. [Empty block loot][spawner-loot] · [Pickaxe tag][pickaxe-tag] · [Mining gate][mining] · [Spawner XP][spawner-block] · [XP game rule][block-xp]

## Chest minecarts and their loot

Look for a **[Minecart with Chest](../items/MinecartWithChest.md)** sitting on a short rail segment. Each five-block corridor section makes two independent one-in-100 cart-placement attempts; the chosen position must be air with a non-air block below. This does not guarantee a cart in a corridor or Mineshaft. Successful placement assigns `minecraft:chests/abandoned_mineshaft` to the cart. [Placement attempts][encounters] · [Entity and loot assignment][cart-placement]

Interact to open the cart's **27-slot inventory**. The bundled table has three pools:

| Pool | Rolls per generated cart | Possible result per selected entry |
| --- | --- | --- |
| Special item | 1 | 1 Golden Apple, Enchanted Golden Apple, Name Tag, randomly enchanted Book, Iron Pickaxe, **or nothing** |
| Supplies | 2–4 | Iron Ingots 1–5; Gold Ingots 1–3; Redstone Dust or Lapis Lazuli 4–9; Diamonds 1–2; Coal 3–8; Bread 1–3; Glow Berries 3–6; Melon, Pumpkin or Beetroot Seeds 2–4 |
| Track and lighting | 3 | Ordinary Rails 4–8; Powered, Detector or Activator Rails 1–4; Torches 1–16 |

Entries have different weights. These are **per-roll amounts**, not per-cart totals, and repeated selections are possible. The special pool can be empty; neither its rare items nor a particular supply/rail type is guaranteed. The book uses the loaded random-loot enchantment selection. [Loot table][loot] · [Weighted pool execution][loot-pools] · [Book enchantment][book] · [Inventory and interaction][cart]

Secure the area and unload the inventory before recovering the cart. Ordinary Survival damage that destroys it drops **one combined Minecart with Chest item** when entity drops are enabled; it is not a packed container, and cargo spills separately. Follow the [cart guide](../items/MinecartWithChest.md#behavior) for recovery and [Transport](../mechanics/Transport.md#building-a-basic-rail-route) for a working route. Generated rail fragments need repairs, support and propulsion before they become a useful railway. [Cart drop][cart] · [Vehicle destruction][vehicle] · [Cargo removal][cargo]

## Recover materials without losing the route

With block drops enabled and ordinary Survival mining:

| Material | Collection choice and limit |
| --- | --- |
| Placed ordinary Rails | Return the Rail item; no correct-tool requirement for this block. Keep the walkway intact while collecting track |
| Cobwebs | Usable Shears return **1 Cobweb**; an ordinary usable sword returns **1 String**. Hand mining or an unsuitable tool returns no block loot. Silk Touch does not bypass the correct-tool gate |
| Iron Chains | Use a usable correct pickaxe to recover the matching Iron Chain item |
| Oak or Dark Oak planks, logs and fences | Return their matching block items under normal harvesting. Clear or replace your route before stripping the wood |

[Rail properties][rail-block] · [Rail loot][rail-loot] · [Web loot][web-loot] · [Shears rule][shears] · [Sword rule][sword] · [Player tool gate][tool-gate] · [Chain properties][chain-block] · [Chain tag][chains] · [Chain loot][chain-loot] · [Oak plank loot][wood-loot] · [Oak log loot][oak-log-loot] · [Oak fence loot][oak-fence-loot] · [Dark Oak plank loot][dark-planks-loot] · [Dark Oak log loot][dark-log-loot] · [Dark Oak fence loot][dark-fence-loot]

The [Rails](../blocks/Rails.md), [Cobweb](../blocks/Cobweb.md#collecting-cobwebs), [Iron Chain](../blocks/IronFixtures.md#iron-chain), [tree logs](../blocks/TreeLogsAndRoots.md) and [wood construction](../blocks/WoodConstruction.md) guides own their detailed handling. You can craft replacement rails and carts using the recipes in [Transport](../mechanics/Transport.md#building-a-basic-rail-route) and [Minecart with Chest](../items/MinecartWithChest.md#obtaining); finding a Mineshaft is an alternative supply route.

## Returning later

Revisiting can make sense for unexplored branches, stored supplies or a preserved spawner. **Opening the same generated cart again does not reroll its loot:** the pending loot-table reference is cleared when the contents are generated, and the inventory is saved thereafter. Mined track, wood and destroyed cages are not restocked by a revisit; these are structure-generation placements. Search another Mineshaft for more unopened generated carts. A surviving spawner can continue attempting mobs when its activation and spawn conditions allow, so a cleared visit is not a permanent guarantee of safety. [One-time cart loot and saving][cart-loot-runtime] · [Structure placement dispatch][piece-dispatch] · [Spawner conditions][spawner-tick]

Related: [Structures](Structures.md) · [Cave biomes](../biomes/CaveBiomes.md) · [Mining](../mechanics/Mining.md) · [Commands](../commands/Commands.md)

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked loaded structure definitions and recursively expanded biome tags, procedural piece generation, live placement and selected entity/block callbacks, loot loading, recovery gates and linked recipe data. Mineshaft layouts here come from the registered procedural generator, rather than a fixed NBT room-template inventory. [Structure registry loading][registry] · [Mineshaft type binding][type] · [Start generation][generation] · [Decoration caller][decoration] · [Piece dispatch][piece-dispatch] · [Loot loading][loot-loading]

No in-game structure search, generation, combat, lighting, harvesting, cart-opening, railway or farm test was run. Existing chunks, data packs, custom spawner data and server/world settings can differ. Preparation and route-clearing steps are source-informed advice, not a tested safe-path or yield guarantee.

[pieces]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/MineshaftPieces.java#L49-L97
[encounters]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/MineshaftPieces.java#L370-L413
[normal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/structure/mineshaft.json#L1-L7
[normal-biomes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/mineshaft.json#L1-L26
[overworld-biomes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/worldgen/biome/is_overworld.json#L1-L58
[mesa]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/structure/mineshaft_mesa.json#L1-L7
[mesa-biomes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/mineshaft_mesa.json#L1-L5
[badlands]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/worldgen/biome/is_badlands.json#L1-L7
[structure]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/MineshaftStructure.java#L38-L79
[height]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/pieces/StructurePiecesBuilder.java#L32-L44
[preset]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[primordial]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/dimension/primordial_caves.json
[filter]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/chunk/ChunkGeneratorStructureState.java#L45-L64
[generation-gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/chunk/status/ChunkStatusTasks.java#L40-L59
[set]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/structure_set/mineshafts.json#L1-L20
[spread]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/placement/RandomSpreadStructurePlacement.java#L69-L83
[frequency]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/placement/StructurePlacement.java#L78-L110
[valid-biome]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/Structure.java#L131-L140
[piece-exclusions]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/MineshaftPieces.java#L976-L1025
[blocking]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/worldgen/biome/mineshaft_blocking.json#L1-L5
[locate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/commands/LocateCommand.java#L55-L107
[locate-result]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/commands/LocateCommand.java#L162-L181
[broken]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L449-L494
[usable-tool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[corridor-size]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/MineshaftPieces.java#L122-L151
[crossings]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/MineshaftPieces.java#L573-L611
[stairs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/MineshaftPieces.java#L1253-L1323
[floors]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/MineshaftPieces.java#L416-L439
[supports]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/MineshaftPieces.java#L443-L522
[fluids]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/AquiferFluidPicker.java#L1-L17
[web]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/WebBlock.java#L26-L36
[spider]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/Spider.java#L53-L124
[spider-size]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/EntityType.java#L370-L372
[bite]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/CaveSpider.java#L29-L48
[poison]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/effect/PoisonMobEffect.java#L14-L27
[remedies]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumables.java#L16-L64
[spawner-tick]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/BaseSpawner.java#L31-L185
[near-player]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/EntityGetter.java#L103-L114
[spawn-binding]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L112-L112
[darkness]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/Monster.java#L86-L112
[dimension]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/dimension_type/overworld.json#L1-L24
[spider-target]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/Spider.java#L180-L199
[spawner-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/spawner.json
[pickaxe-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[mining]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[spawner-block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/SpawnerBlock.java#L33-L49
[block-xp]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L418-L422
[cart-placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/MineshaftPieces.java#L339-L358
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/abandoned_mineshaft.json
[loot-pools]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/LootPool.java#L60-L102
[book]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantRandomlyFunction.java#L54-L83
[cart]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/vehicle/MinecartChest.java#L28-L72
[vehicle]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/vehicle/VehicleEntity.java#L34-L75
[cargo]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/vehicle/AbstractMinecartContainer.java#L34-L80
[rail-block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L1416-L1416
[rail-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/rail.json
[web-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/cobweb.json#L1-L58
[shears]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ShearsItem.java#L32-L44
[sword]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ToolMaterial.java#L62-L79
[chain-block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L2317-L2321
[chains]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/chains.json
[chain-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/iron_chain.json
[wood-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/oak_planks.json
[oak-log-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/oak_log.json
[oak-fence-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/oak_fence.json
[dark-planks-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/dark_oak_planks.json
[dark-log-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/dark_oak_log.json
[dark-fence-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/dark_oak_fence.json
[cart-loot-runtime]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/vehicle/ContainerEntity.java#L61-L124
[piece-dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/StructureStart.java#L81-L105
[registry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L111
[generation]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L542-L577
[decoration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L333-L350
[loot-loading]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L42-L69
[tool-gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[type]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/StructureType.java#L31-L31
