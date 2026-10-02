# Trapped Chest

A Trapped Chest (`minecraft:trapped_chest`) stores **27 slots**, or **54 slots** when joined to another Trapped Chest. It also supplies redstone power while counted users have it open. Its inventory-fullness Comparator output is separate from that opening signal. [Registered block][registration] · [Single inventory][capacity] · [Double menu][double-menu] · [Opening signal][opener-signal] · [Comparator hook][analog]

## Crafting and obtaining

Combine **one ordinary [Chest](Chest.md) and one [Tripwire Hook](Tripwire.md)** in any arrangement in a crafting grid to make **one Trapped Chest**. These are exact item ingredients: a Copper Chest, Ender Chest, or another Trapped Chest does not replace the ordinary Chest. The item is also listed in the Creative menu's Redstone Blocks tab. [Complete recipe][recipe] · [Creative tab][creative-tab] · [Creative entry][creative-item]

A possible **first-floor secret room in a Woodland Mansion** contains a Trapped Chest next to **two TNT blocks**. The bundled room template supplies two Ender Pearls in that chest; this is fixed template inventory. The generator can select this room, but it is not guaranteed in every mansion. Inspect and remove connected explosives before opening unfamiliar storage: the opening signal can prime adjacent TNT when the TNT-explodes game rule allows it. [Room template, binary NBT][mansion-template] · [First-floor room selection][secret-selector] · [Secret-room placement][secret-placement] · [Active mansion generator][mansion-generation] · [TNT signal response][tnt] · [TNT game-rule gate][tnt-rule]

## Placement and double chests

Place two compatible single Trapped Chests side by side, facing the same horizontal direction, to form a double chest. Automatic joining checks the two sides of the new chest, not its front or back. **Trapped Chests join only other Trapped Chests**; ordinary and Copper Chests remain separate neighbors. A third chest cannot extend an already formed pair. [Placement and single-partner checks][placement] · [Same-block connection rule][same-block] · [Pair validation][combine]

Secondary-use placement lets you control the connection. Placing against a top or bottom face skips automatic joining. Placing against the side of a compatible single chest can deliberately join it when that side is perpendicular to the existing chest's facing. Normal placement faces opposite the player's horizontal direction. The block can also be waterlogged; placement in source Water sets its waterlogged state. [Exact placement controls and water state][placement]

## Opening and obstruction

Keep the space immediately above **both halves** clear. Opening fails when the above block is a redstone conductor or a sitting Cat occupies the one-block space above either half. The check is not simply whether the block above looks solid. Each half of a double chest must also pass its menu-access check, including any data-defined lock. [Opening lookup][lookup] · [Block and Cat tests][obstruction] · [Both-half obstruction check][combine] · [Both-half menu access][double-menu]

A blocked opening does not create a chest menu or add a user to the opening count. Once a menu has opened, the normal menu constructor and close handler start and stop the container's open-use tracking. Spectators do not increment this count. [Opening dispatch][opening] · [Menu opens][menu-open] · [Menu closes][menu-close] · [Spectator exclusion][open-close]

## Opening signal and counted users

The opening signal equals the chest's current user count, limited to **0–15**: no users gives 0, one gives 1, two gives 2, and fifteen or more gives 15. Stored items do not set this signal; an empty open chest can power a circuit. [Count-to-signal conversion][opener-signal] · [Count lookup][count-lookup]

- **Weak power:** the opening strength is available in every direction
- **Strong power:** the same strength is delivered downward, to the block immediately beneath the chest
- **Double chest:** opening the combined menu counts that user once in each half, so one user gives strength 1 at both halves, not strength 2

The downward direction follows the actual receiving-block query: a block reads the chest above it using the `UP` direction accepted by the strong-power hook. Opener changes notify neighbors around the chest and the block below it. The counter also rechecks nearby qualifying users every five game ticks while in use. [Signal directions][opener-signal] · [Receiving-block direction convention][direction] · [Both-half open/close calls][compound-open] · [Neighbor updates][updates] · [Counter and scheduled rechecks][counter]

**Copper Golems can trigger it too.** Their active sorting behavior accepts Trapped Chests as destinations and calls the same opening and closing hooks when inspecting them, including an inspection that does not deposit an item. The golem's open-chest tracking also recognizes the connected half of a double chest. See [Copper Golem sorting](CopperChests.md#copper-golem-sorting) for destination selection and storage layout. [Destination predicate][golem-target] · [Installed sorting behavior][golem-active] · [Inspection callbacks][golem-opens] · [Golem counted on either half][golem-counted]

## Comparators and Hoppers

A [Comparator](RedstoneComparator.md) reading the chest measures **inventory fullness**, not its number of users. The checked formula is 0 for an empty container; otherwise it is `floor(14 × average slot fullness) + 1`, up to 15. Each slot's fullness uses that stack's allowed maximum, and a double chest is measured across all 54 slots. The Comparator's input path chooses this analog reading even while the chest is emitting its opening signal. [Fullness hook][analog] · [Slot averaging][fullness] · [Rounding][rounding] · [Comparator input selection][comparator]

**Obstruction matters to that reading in this build.** The analog hook asks for the chest container with obstruction checks enabled. If either half of a double chest is blocked, the lookup returns no container and its evaluated fullness signal is 0. This describes the computed reading; it does not establish an immediate Comparator update for every way a Cat or obstacle can move. [Obstruction-aware lookup][lookup] · [Pair obstruction][combine] · [No-container result][fullness]

A [Hopper](Hopper.md) can insert into a chest it points toward and extract from one immediately above it. Its chest lookup combines both halves and **bypasses the opening-obstruction check**, so an obstructed lid alone does not stop hopper access. These inventory operations do not open a chest menu or add an opener. [Hopper container lookup][hopper-lookup] · [Insertion][hopper-insert] · [Extraction][hopper-extract]

However, an opening Trapped Chest powers neighboring Hoppers. A Hopper immediately underneath therefore stops **its own transfer cycle** while the chest's opening signal is nonzero. Closing the chest permits that cycle again if no other source keeps the Hopper powered, subject to its normal cooldown and available space/items. Use ordinary Chests when opening storage should not interrupt adjacent Hopper operation. [Neighbor power checks][neighbor-signal] · [Hopper enable/disable rule][hopper-power] · [Transfer and cooldown gates][hopper-transfer]

## Saving contents and breaking the block

Each half keeps its own 27-slot inventory in the world save. Joining exposes both through one menu; it does not move the items into a new 54-slot block entity. If a chest has an unopened loot table, its loot-table identity and seed are saved instead of ordinary slot data until it is unpacked. Opening or accessing its inventory can generate that loot, so a player menu is not the only access that can materialize it. [Slot save/load][saved-slots] · [Combined menu][double-menu] · [Loot-table persistence and generation][saved-loot] · [Inventory-access generation][inventory-access] · [Chunk save caller][chunk-save] · [Chunk load caller][chunk-load]

In ordinary Survival mining with block drops enabled, the block drops **one Trapped Chest**. An axe is its tagged mining tool, but **no correct tool or minimum tier is required** for the block's own drop. Silk Touch is unnecessary and Fortune adds no chest items. The complete loot table copies only the custom name; its explosion-survival condition means an explosion does not guarantee the chest item. [Registration without a tool requirement][registration] · [Axe tag][axe-tag] · [Correct-tool gate][tool-gate] · [Survival drop caller][survival-break] · [Block-drops game rule][block-drops-rule] · [Complete loot table][loot] · [Explosion condition][explosion]

**Mining is not a way to carry filled storage.** Normal removal spills that half's contents as separate item entities, while the chest item retains the custom name rather than its inventory or loot table. Breaking one half leaves the other half and its own stored items in place. Empty it before moving it, or use a [Shulker Box](ShulkerBox.md#breaking-and-carrying-contents) for portable filled storage. [Removal caller][removal] · [Container spill hook][spill] · [Per-container item drops][spill-items] · [Remaining-half state update][same-block] · [Chest-item loot][loot]

## Piglins nearby

Opening access invokes nearby idle Piglin anger with a visibility check; breaking the block uses the guarded-block path without that opening visibility filter. A lid blocked before a menu provider is found returns before the opening callback. As with ordinary Chests, check nearby Piglins before using or breaking it. [Opening callback and order][opening] · [Guarded-block tag][guarded] · [Breaking callback][break-anger] · [Nearby Piglin filters][piglins]

## Related pages

- [Trapped Chest item](../items/TrappedChest.md) and [TNT](TNT.md#priming-routes)
- [Chest](Chest.md), [Copper Chests](CopperChests.md), and [Shulker Box](ShulkerBox.md)
- [Tripwire and Tripwire Hook](Tripwire.md), [Hopper](Hopper.md), and [Redstone Comparator](RedstoneComparator.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `cf1c134b3f9ff634490e448fe26c335b90f82227` on 2026-10-02. Reviewed the registered block/item, complete recipe and loot table, relevant tags, active placement/menu/opener paths, Copper Golem callbacks, signal consumers, inventory persistence/removal, and mansion template selection. The binary room template was decoded to inspect its palette, placed blocks, and inventory. No gameplay, circuit-timing, multiplayer, save/reload, generated-world, or explosion test was run. Data packs and later builds can change these results.

[registration]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Blocks.java#L2834-L2838
[capacity]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/entity/ChestBlockEntity.java#L28-L75
[double-menu]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/ChestBlock.java#L90-L118
[recipe]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/recipe/crafting/trapped_chest.json#L1-L12
[creative-tab]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1283-L1288
[creative-item]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1325-L1334
[mansion-template]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/structure/woodland_mansion/1x2_s2.nbt
[secret-selector]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/levelgen/structure/structures/WoodlandMansionPieces.java#L47-L71
[secret-placement]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/levelgen/structure/structures/WoodlandMansionPieces.java#L1009-L1019
[mansion-generation]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/levelgen/structure/structures/WoodlandMansionStructure.java#L28-L43
[tnt]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/TntBlock.java#L47-L61
[tnt-rule]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/TntBlock.java#L82-L92
[same-block]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/ChestBlock.java#L164-L181
[placement]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/ChestBlock.java#L201-L240
[combine]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/DoubleBlockCombiner.java#L24-L54
[obstruction]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/ChestBlock.java#L328-L350
[lookup]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/ChestBlock.java#L269-L299
[opener-signal]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/TrappedChestBlock.java#L41-L54
[open-close]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/entity/ChestBlockEntity.java#L129-L142
[count-lookup]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/entity/ChestBlockEntity.java#L164-L174
[menu-open]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/inventory/ChestMenu.java#L41-L59
[menu-close]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/inventory/ChestMenu.java#L99-L103
[compound-open]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/CompoundContainer.java#L72-L82
[direction]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/SignalGetter.java#L13-L39
[neighbor-signal]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/SignalGetter.java#L65-L82
[updates]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/entity/TrappedChestBlockEntity.java#L17-L27
[counter]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/entity/ContainerOpenersCounter.java#L28-L101
[golem-target]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/entity/animal/coppergolem/CopperGolemAi.java#L41-L46
[golem-active]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/entity/animal/coppergolem/CopperGolemAi.java#L99-L108
[golem-opens]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/entity/animal/coppergolem/CopperGolemAi.java#L121-L158
[golem-counted]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/entity/animal/coppergolem/CopperGolem.java#L411-L421
[analog]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/ChestBlock.java#L352-L360
[fullness]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java#L752-L767
[rounding]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/util/Mth.java#L524-L527
[comparator]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/ComparatorBlock.java#L97-L118
[hopper-lookup]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L344-L387
[hopper-transfer]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L97-L130
[hopper-insert]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L143-L159
[hopper-extract]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L218-L229
[hopper-power]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/HopperBlock.java#L117-L127
[saved-slots]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/entity/ChestBlockEntity.java#L82-L97
[saved-loot]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/RandomizableContainer.java#L50-L89
[inventory-access]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/entity/RandomizableContainerBlockEntity.java#L49-L76
[chunk-save]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java#L465-L482
[chunk-load]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/chunk/storage/SerializableChunkData.java#L498-L522
[loot]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/loot_table/blocks/trapped_chest.json#L1-L30
[axe-tag]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/tags/block/mineable/axe.json#L25-L42
[tool-gate]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[survival-break]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L292
[block-drops-rule]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Block.java#L410-L415
[explosion]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/storage/loot/predicates/ExplosionCondition.java#L27-L35
[removal]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java#L311-L323
[spill]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/entity/BlockEntity.java#L250-L254
[spill-items]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/Containers.java#L13-L25
[guarded]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/tags/block/guarded_by_piglins.json#L1-L14
[opening]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/ChestBlock.java#L247-L259
[break-anger]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Block.java#L480-L487
[piglins]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L524-L533
