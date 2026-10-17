# Decorated Pot

A **Decorated Pot** (`minecraft:decorated_pot`) displays four chosen faces and stores **one item stack**. Its decorations are set when crafting; using an item on a placed pot inserts it into storage. There is no inventory menu or empty-hand removal action. For displaying plants, use the separate [Flower Pot](FlowerPot.md) guide. [Recipe][recipe] · [Placed interaction][insert] · [Single slot][single-slot]

## Crafting and choosing faces

Use a 3 × 3 Crafting Table grid with exactly four accepted ingredients in a cross. Leave the center and corners empty:

```text
.     Back  .
Left  .     Right
.     Front .
```

Each position accepts **one loose Brick or one accepted Pottery Sherd**. Mix or repeat them freely: four Bricks make a plain pot, while four copies of a sherd put its named decoration on all four sides when that pattern is mapped. This uses `minecraft:brick`, not the Bricks building block. The result is one pot. A separate simple shaped recipe also makes the plain four-Brick version. [Special recipe][recipe] [special-recipe] · [Ingredient tag][ingredients] · [Plain recipe][plain-recipe]

The recipe records top=back, middle-left=left, middle-right=right and bottom=front. With ordinary placement, the **bottom ingredient's front face points back toward you**; the top ingredient is on the opposite face. Left/right correspond to those sides as you look at the front. This follows the recipe, placement direction and renderer rotation; it has not been checked through an in-game placement test. [Stored side order][decorations] · [Placement direction][placement] · [Side geometry][render-sides] · [Renderer rotation][render-facing]

### Accepted sherds and current patterns

The current tag accepts **25 sherd items**, but the current renderer maps **23 named patterns**. The table compares checked identities and routes, without reproducing pattern artwork. Individual item pages remain the owners of those materials. [Accepted set][sherds] · [Pattern mapping][patterns]

| Face ingredient | Current rendered face | Checked source route |
| --- | --- | --- |
| [Brick](../items/Brick.md) | Plain face | [Clay and Bricks](ClayAndBricks.md#crafting-and-smelting) |
| [Arms Up](../items/ArmsUpPotterySherd.md), [Brewer](../items/BrewerPotterySherd.md) | Named pattern for each sherd | [Desert Well archaeology table][well-loot] |
| [Archer](../items/ArcherPotterySherd.md), [Miner](../items/MinerPotterySherd.md), [Prize](../items/PrizePotterySherd.md), [Skull](../items/SkullPotterySherd.md) | Named pattern for each sherd | [Desert Pyramid archaeology table][pyramid-loot] |
| [Angler](../items/AnglerPotterySherd.md), [Shelter](../items/ShelterPotterySherd.md), [Snort](../items/SnortPotterySherd.md) | Named pattern for each sherd | [Warm Ocean Ruin archaeology table][warm-loot] |
| [Blade](../items/BladePotterySherd.md), [Explorer](../items/ExplorerPotterySherd.md), [Mourner](../items/MournerPotterySherd.md), [Plenty](../items/PlentyPotterySherd.md) | Named pattern for each sherd | [Cold Ocean Ruin archaeology table][cold-loot] |
| [Burn](../items/BurnPotterySherd.md), [Danger](../items/DangerPotterySherd.md), [Friend](../items/FriendPotterySherd.md), [Heart](../items/HeartPotterySherd.md), [Heartbreak](../items/HeartbreakPotterySherd.md), [Howl](../items/HowlPotterySherd.md), [Sheaf](../items/SheafPotterySherd.md) | Named pattern for each sherd | [Trail Ruins rare archaeology table][ruins-loot] |
| [Flow](../items/FlowPotterySherd.md), [Guster](../items/GusterPotterySherd.md), [Scrape](../items/ScrapePotterySherd.md) | Named pattern for each sherd | [Checked generated Trial Chamber pots][pot-pool] |
| [Dinosaur](../items/DinosaurPotterySherd.md), [Footprint](../items/FootprintPotterySherd.md) | Accepted and stored, but currently use the plain-face fallback | [Creative entries][custom-creative]; no archaeology loot route established by this audit |

Dinosaur and Footprint are missing from the item-to-pattern map. Their ingredient identities are still retained, so shattering such a face returns the original sherd rather than a Brick; the visual fallback does not rewrite the decoration data. This is a source finding, not a rendered-image test. [Map and missing entries][patterns] · [Null material lookup][material-lookup] · [Plain-face fallback][pattern-fallback] · [Ordered drops][decorations]

Use a [Brush](../items/Brush.md#archaeology-basics) on the appropriate Suspicious Sand or Suspicious Gravel for archaeology rewards. The assigned table can give other items, so brushing does not guarantee a sherd. For Flow, Guster and Scrape, the checked Trial Chamber route places already decorated pots; shattering those pots recovers their decoration ingredients, including the matching sherd. Holding a sherd against a placed pot does **not** repaint a side. To rearrange a design, recover its ingredients and craft the desired order again. [Archaeology tables][well-loot] [pyramid-loot][] [warm-loot][] [cold-loot][] [ruins-loot][] · [Generated pots][flow-pot] [guster-pot][] [scrape-pot][] · [Insertion handler][insert]

## Storage and player interaction

A pot holds **one matching stack**, limited by that item's normal stack size: for example, up to 64 ordinary Bricks or one non-stackable item. Item type and components must match, so differently named or otherwise different stacks do not combine just because they look similar. Its four face ingredients do not occupy this storage slot. [Slot model][single-slot] · [Insertion check][insert] · [Container limits][container-rules]

Each successful normal use inserts **one held item**. Survival consumes that item; Creative's infinite-material handling can supply it without shrinking the held stack. The pot wobbles, emits particles and plays a sound whose pitch increases with fullness. A full pot, mismatched item or empty-hand use gives failure feedback without taking items out. [Transaction and feedback][insert] · [Item consumption][consume]

Retrieve stored items with a Hopper underneath, or break the pot using the method you want below. There is no menu to shift-click the whole stack, and an empty-hand use does not withdraw it. Secondary use with a held item can bypass the pot interaction, following the normal block-use dispatch. [Interaction handlers][insert] · [Hopper extraction][hopper-pull] · [Use dispatch][use-dispatch]

## Hoppers, Droppers and comparators

The block entity is a real one-slot Container without sided restrictions. A [Hopper](Hopper.md) aimed into it can insert compatible items; one underneath can pull them out. A [Dropper](DispenserAndDropper.md) pointing at it uses the same container-insertion route. Decorations do not filter the contents, and powering the pot itself does not lock its slot; Hopper power/cooldown rules belong to the Hopper. [World-container lookup][hopper-find] · [Transfer and merge checks][hopper-transfer] · [Dropper insertion][dropper] · [Container permissions][container-rules]

A [Comparator](RedstoneComparator.md) with its rear against the pot reads **fullness**, from 0 to 15. Empty gives 0; otherwise the result is `1 + floor(14 × stored count / item stack limit)`. A non-stackable item therefore fills the slot and gives 15. [Pot analog handler][comparator] · [Fullness calculation][fullness] · [Rounding helper][rounding]

| Ordinary 64-stack item count | Comparator output |
| ---: | ---: |
| 0 | 0 |
| 1 | 1 |
| 32 | 8 |
| 64 | 15 |

These are source-derived arithmetic examples. For a small storage indicator, place a Comparator directly behind a pot, put 32 ordinary Bricks into it, and expect rear input 8 when there are no competing signal/side inputs. An enabled Hopper underneath drains it and lowers that reading. This layout has not been tested in game. Player and successful Hopper changes notify neighboring comparator output checks. [Change notification][changed] · [Hopper success path][hopper-transfer]

## Breaking: keep the pot or recover ingredients

The block has hardness 0 and requires no particular tool tier for ordinary drops. **Breaking with an empty hand keeps the decorated pot item**. Holding an item in the breaking-tools tag changes the result unless its enchantments prevent shattering. [Registration][registration] · [Player break hook][shatter]

| Break method | Pot/decorations drop |
| --- | --- |
| Empty hand, or an item outside the breaking-tools tag | One pot with its four decorations retained |
| Tagged breaking item without a protecting enchantment | Four face ingredients: a sherd for each sherd face, a Brick for each plain face |
| Tagged breaking item carrying Silk Touch | One decorated pot; the current protection tag contains Silk Touch |
| Eligible projectile impact, with interaction/break checks allowed | Cracked pot shatters into its four face ingredients |

The breaking tag includes the current sword, axe, pickaxe, shovel and hoe groups, plus Trident and Mace. The choice is based on tags and enchantments, not a requirement to use a stronger material tier. The normal loot table has no Fortune multiplier. [Breaking tag][break-tools] · [Protection tag][silk] · [Cracked/intact loot branches][loot]

**Stored contents spill separately in either ordinary break outcome.** The intact pot loot copies only its decoration component, not its storage contents. A pot with 32 Bricks inside therefore yields its decorated pot item plus the loose stored Bricks when broken by hand; it is not a sealed portable stack of those contents. The block-removal path drops its Container separately. [Loot component filter][loot] · [Removal dispatch][remove-dispatch] · [Container removal][remove-contents] · [Item spill][spill]

Projectile shattering requires membership in the impact-projectiles tag and `projectilesCanBreakBlocks`, plus the projectile's interaction permission. Examples in the tag include arrows, Snowballs, Eggs, Tridents and Wind Charges; this is not a promise that every projectile breaks pots. The held-tool Silk Touch check does not appear in the projectile branch. [Impact tag][impact-projectiles] · [Projectile permissions][projectile-permission] · [Pot hit handler][projectile-hit]

## Water and placement states

The same block ID has horizontal facing, waterlogged and cracked states. Ordinary placement sets cracked=false, aligns its stored facing with the player's horizontal direction, and waterlogs it when placed into a **Water source**. The pot is one block high and 14/16 of a block wide. Its decorations and stored stack are block-entity data rather than separate block IDs. [Shape/state implementation][block] · [Placement][placement]

Waterlogging is separate from storing a Water Bucket item. A normal use with a bucket can insert that bucket into an empty pot. The current bucket code also redirects sneaking water placement toward an adjacent block, so do not assume crouch-using a Water Bucket fills the pot itself. Placing the pot into a Water source is the directly verified setup route; this advice is source-derived, not a gameplay test. [Generic insertion][insert] · [Placement water test][wet-state] · [Bucket placement handling][bucket]

A waterlogged pot exposes a Water source and schedules water updates. The inherited bucket-pickup route can remove that source; bypass the pot's insertion interaction when using an empty bucket. Waterlogging itself does not replace the item slot or consume the stored stack. See [Water and Lava](WaterAndLava.md) for the broader fluid rules. [Waterlogged behavior][water] · [Source fluid][water-source] · [Bucket pickup][pickup-bucket] · [Use dispatch][use-dispatch]

## Generated pots and saved contents

The checked Trial Chamber decor pool reaches **Flow, Guster, Scrape and undecorated pot templates**. Each of the three decorated examples has one matching sherd and three Brick faces; the undecorated one has no sherd field. All four assign the corridor pot loot table to their storage. The route is verified through the active structure/pool/template definitions, not through a generated-world test. [Decor pool][pot-pool] · [Flow][flow-pot] · [Guster][guster-pot] · [Scrape][scrape-pot] · [Undecorated][plain-pot]

That table makes one weighted contents roll, with possibilities including Emeralds, Arrows, Iron Ingots, a Trial Key, the Creator (Music Box) disc, Diamonds and gem blocks. These stored contents are separate from the four face ingredients recovered by shattering. A pot you craft and place yourself does not receive this generated loot table. [Generated contents table][pot-loot] · [Default item components][item]

Generated contents are unpacked when the Container is accessed, including item insertion checks, Hopper access or a comparator read. The pot saves either its pending loot assignment or its realized stored item, along with the decorations. Custom supplied pot items can carry components that the placement path applies, but ordinary breaking does not package the stored stack into the dropped pot. [Lazy access][container-access] · [Loot unpacking][lazy-loot] · [Save/load][save] · [Component application][components] [apply] · [Ordinary loot][loot]

## Sources and verification

Source-reviewed on 2026-10-02 at `8d065c943710a8d4e3d609b0406dda95ed62cc63`. Checked recipe and decoration order, accepted ingredients/pattern mapping, actual player and container transactions, comparator arithmetic, water handling, break/loot/components and representative archaeology/Trial Chamber resource routes. No gameplay, rendered-pattern, circuit, projectile, bucket, natural-generation or save/reload test was run. Custom items, data packs and server rules can change individual outcomes.

Related: [Decorated Pot item](../items/DecoratedPot.md) · [Flower Pot](FlowerPot.md) · [Brush](../items/Brush.md) · [Hopper](Hopper.md) · [Blocks](Blocks.md)

[recipe]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/crafting/DecoratedPotRecipe.java#L15-L46
[insert]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/DecoratedPotBlock.java#L95-L145
[single-slot]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/ticks/ContainerSingleItem.java#L8-L62
[ingredients]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/item/decorated_pot_ingredients.json
[plain-recipe]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/recipe/crafting/decorated_pot_simple.json
[decorations]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/entity/PotDecorations.java#L25-L66
[placement]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/DecoratedPotBlock.java#L86-L93
[render-sides]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/client/renderer/blockentity/DecoratedPotRenderer.java#L101-L109
[render-facing]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/client/renderer/blockentity/DecoratedPotRenderer.java#L156-L163
[sherds]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/item/decorated_pot_sherds.json
[patterns]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/entity/DecoratedPotPatterns.java
[well-loot]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/loot_table/archaeology/desert_well.json
[pyramid-loot]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/loot_table/archaeology/desert_pyramid.json
[warm-loot]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/loot_table/archaeology/ocean_ruin_warm.json
[cold-loot]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/loot_table/archaeology/ocean_ruin_cold.json
[ruins-loot]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/loot_table/archaeology/trail_ruins_rare.json
[pot-pool]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/worldgen/template_pool/trial_chambers/decor.json
[custom-creative]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1929-L1930
[material-lookup]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/client/renderer/Sheets.java#L200-L203
[pattern-fallback]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/client/renderer/blockentity/DecoratedPotRenderer.java#L112-L121
[flow-pot]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/structure/trial_chambers/decor/flow_pot.nbt
[container-rules]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/Container.java#L31-L58
[consume]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1086
[hopper-pull]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L218-L231
[use-dispatch]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L358-L388
[hopper-find]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L375-L388
[hopper-transfer]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L246-L341
[dropper]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/DropperBlock.java#L58-L75
[comparator]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/DecoratedPotBlock.java#L228-L236
[fullness]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java#L748-L767
[rounding]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/util/Mth.java#L524-L527
[changed]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/entity/BlockEntity.java#L208-L219
[registration]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/Blocks.java#L6769-L6773
[shatter]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/DecoratedPotBlock.java#L173-L197
[break-tools]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/item/breaks_decorated_pots.json
[silk]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/enchantment/prevents_decorated_pot_shattering.json
[loot]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/loot_table/blocks/decorated_pot.json
[remove-dispatch]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java#L311-L326
[remove-contents]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/entity/BlockEntity.java#L250-L254
[spill]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/Containers.java#L21-L46
[impact-projectiles]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/entity_type/impact_projectiles.json
[projectile-permission]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/entity/projectile/Projectile.java#L336-L346
[projectile-hit]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/DecoratedPotBlock.java#L209-L216
[block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/DecoratedPotBlock.java
[wet-state]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/DecoratedPotBlock.java#L68-L93
[bucket]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/BucketItem.java#L100-L130
[water]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java#L19-L55
[water-source]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/DecoratedPotBlock.java#L199-L202
[pickup-bucket]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/BucketItem.java#L41-L83
[guster-pot]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/structure/trial_chambers/decor/guster_pot.nbt
[scrape-pot]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/structure/trial_chambers/decor/scrape_pot.nbt
[plain-pot]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/structure/trial_chambers/decor/undecorated_pot.nbt
[pot-loot]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/loot_table/pots/trial_chambers/corridor.json
[item]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/Items.java#L458-L461
[container-access]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/entity/DecoratedPotBlockEntity.java#L131-L152
[lazy-loot]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/RandomizableContainer.java#L71-L90
[save]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/entity/DecoratedPotBlockEntity.java#L43-L64
[components]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/entity/DecoratedPotBlockEntity.java#L110-L128

[special-recipe]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/recipe/crafting/decorated_pot.json
[apply]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/BlockItem.java#L70-L103
