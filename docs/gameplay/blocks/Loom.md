# Loom

A Loom (`minecraft:loom`) adds a dyed pattern layer to a Banner. Its menu uses a Banner, one Dye, and optionally a reusable Banner Pattern item. It is also the Shepherd's registered job-site block. See [Banners](Banners.md) for the 16 base colors, placement, duplication, washing, map markers, and Shield decoration. [Block and menu opening][loom-block] · [Menu registration][menu-type] · [Shepherd POI][poi] · [Profession][profession]

## Crafting and placing

Craft **two String in a row above two plank-tag items** to make **one Loom**. This 2 × 2 recipe fits the inventory crafting grid. Its two plank slots can use different members of the tag. The bundled tag includes Oak, Spruce, Birch, Jungle, Acacia, Dark Oak, Pale Oak, Crimson, Warped, Mangrove, Bamboo, and Cherry Planks; Pewen Planks and Bamboo Mosaic are absent. [Recipe][recipe-loom] · [Plank tag][planks]

The placed Loom faces toward its placer. It has hardness and blast resistance **2.5**, full-block collision, and no correct-tool requirement for its ordinary drop. Hand mining returns one Loom; an unbroken axe has the normal mining-speed bonus. Silk Touch and Fortune are not needed. Its one-item loot is subject to normal block-drop rules and an explosion-survival condition. [Properties][loom-registration] · [Facing][loom-block] · [Axe tag][axe-tag] · [Harvest gate][harvest] · [Loot][loot-loom] · [Default collision and drops][behaviour] [block]

## Adding a pattern

1. Open the Loom and put a **Banner** in the banner slot.
2. Put a **Dye** in the dye slot. Its color applies to the new layer, without changing the base color or earlier layers.
3. Leave the pattern-item slot empty for the ordinary designs, or insert one of the templates below.
4. Select a design, check the result preview, and **take the output** to make the change.

Taking one result uses **one Banner and one Dye** and produces one decorated Banner. The template slot is not consumed. There is no fuel slot or timed processing cycle; ingredients are removed when the result is taken. The menu's result copies the input Banner and appends the selected pattern/color, keeping its existing components. [Slot types, result, and consumption][loom-menu]

This consumption code removes items directly from the input slots, including when using the ordinary Loom menu in Creative; it is not the infinite-materials bucket-style exchange. Remove a template to return to the ordinary pattern selector. Each currently registered template tag contains one pattern, which the menu selects automatically when the required inputs are present. [Result-slot callback and selection][loom-menu]

The input slots belong to the open menu, not a persistent inventory inside the block. Closing the menu returns remaining Banner, Dye, and template inputs to the player, or drops them through the normal return helper when necessary. An untaken output is a preview, not a separately stored fourth item. The menu also requires the Loom to remain valid and within the normal interaction range. [Menu lifetime][loom-menu] · [Returning ingredients][clear-menu]

## Pattern limits

The normal Loom interface permits up to **six added pattern layers**. Once the input has six or more, the screen hides its pattern choices and the ordinary slot-update path clears the output. Each layer counts even if its color makes it hard to see. The cloth's base color is separate and does not count toward six. [Menu limit][loom-menu] · [Visible interface limit][loom-screen] · [Base and layers][banner-entity]

Six is the normal editing limit, **not a universal limit on stored banner data**. The component codec stores a list without a six-layer cap; the renderer draws at most the first **16** layers, and the ordinary tooltip lists at most the first six. The Ominous Banner builder supplies eight layers. Ordinary crafting duplication accepts only banners with 1–6 layers, so those limits should not be interchanged. [Component list and tooltip][layers] · [Render loop][renderer] · [Eight-layer banner][ominous] · [Duplication rule][duplicate]

If the Loom shows no useful output, check that the inputs are a Banner and a Dye, the banner is below the normal limit, and the selected template supplies a loaded pattern tag. The slot accepts an item by its `provides_banner_patterns` component, not just because its name contains “Banner Pattern.” [Input and tag lookup][loom-menu] · [Registered component][components]

## Available designs and templates

Without a template, the bundled `no_item_required` tag exposes **32 designs**: corner squares, stripes, crosses, triangles, diagonals, circles/rhombuses, halves, a border, and gradients. The pattern registry also contains the base pattern and ten template designs. In this revision, all **ten registered template items** point to existing, nonempty tags whose pattern IDs resolve in the loaded registry. [Ordinary selector][basic-patterns] · [Template components][templates] · [Tag keys][pattern-tags] · [Active registry loading][pattern-registry]

| Template item | Unlocked pattern ID | Source-reviewed way to obtain the template |
| --- | --- | --- |
| Flower | `flower` | Paper + Oxeye Daisy, shapeless; [recipe][recipe-flower-banner-pattern] · [tag][tag-flower] |
| Creeper | `creeper` | Paper + Creeper Head, shapeless; [recipe][recipe-creeper-banner-pattern] · [tag][tag-creeper] |
| Skull | `skull` | Paper + Wither Skeleton Skull, shapeless; [recipe][recipe-skull-banner-pattern] · [tag][tag-skull] |
| Thing / Mojang | `mojang` | Paper + **Enchanted** Golden Apple, shapeless; [recipe][recipe-mojang-banner-pattern] · [tag][tag-mojang] |
| Field Masoned | `bricks` | Paper + **Bricks block**, shapeless; [recipe][recipe-field-masoned-banner-pattern] · [tag][tag-field-masoned] |
| Bordure Indented | `curly_border` | Paper + Vine, shapeless; [recipe][recipe-bordure-indented-banner-pattern] · [tag][tag-bordure-indented] |
| Globe | `globe` | Level-5 Cartographer trade, base cost 8 Emeralds; [offer][trade] · [tag][tag-globe] |
| Snout / Piglin | `piglin` | Chance in the `bastion_other` chest loot; [loot][piglin-loot] · [tag][tag-piglin] |
| Flow | `flow` | Chance in the Ominous Trial Chambers reward chain; [reward pool][flow-pool] · [unique loot][flow-loot] · [tag][tag-flow] |
| Guster | `guster` | Chance in the normal Trial Chambers reward chain; [reward pool][guster-pool] · [unique loot][guster-loot] · [tag][tag-guster] |

The pattern IDs in this table use the `minecraft:` namespace. In particular, **Field Masoned and Bordure Indented are implemented here** as template-gated `bricks` and `curly_border` patterns; they are not extra ordinary-selector options. Each template stacks to one and remains available for repeated Loom use. [Registered templates][templates] · [Template selection and retention][loom-menu]

The six crafting recipes each produce one template and consume their two ingredients. The Thing recipe specifically spends an Enchanted Golden Apple; a plain Golden Apple is not the named input. Field Masoned uses the Bricks **block**, not the small Brick item. See [Field Masoned Banner Pattern](../items/FieldMasonedBannerPattern.md) for that distinction.

For the non-crafting entries, the checked Cartographer update path uses the current profession/level offer list. The stated Globe price is its base offer, not a promise about every adjusted trade price. Piglin-pattern loot is connected to bundled Bastion chest data. Guster is in the normal reward chain used by the default Vault configuration; the bundled Ominous Vault explicitly selects the Ominous reward chain containing Flow. These are chance-based loot entries, not a guarantee that each chest or reward contains a template. This review does not prescribe structure-finding coordinates or a complete Vault progression route. [Trade dispatch][trade-dispatch] · [Bastion chest template][piglin-template] · [Vault default][vault-default] · [Ominous Vault configuration][ominous-template] · [Active reward lookup][vault-reward]

## Planning and reusing a design

Apply layers in the order you want them drawn: new layers are appended after the old ones, and the renderer submits them in that order. Try a high-contrast base and Dye combination so the preview remains readable. Once the design is finished, use [banner duplication](Banners.md#patterns-and-duplication) to copy it onto matching blank banners instead of spending Dye on every copy. [Result construction][loom-menu] · [Layer render order][renderer]

To undo the most recent layer, use [Water Cauldron washing](Cauldrons.md#washing-equipment-banners-and-shulker-boxes), which costs one water level and removes only the last layer. It does not recolor the base. The [Shield guide](../items/Shield.md#banner-decoration) covers carrying the design onto a Shield. [Banner washing][washing]

For a source-based, **untested** first design, craft a White Banner and a Loom. Insert the banner and Red Dye, choose a center stripe, and take the output. Put it back with Black Dye and choose the ordinary border. No template is required for either layer. The [Banner guide's matching-design example](Banners.md#a-small-matching-design) continues with duplication and placement.

## Sources and verification

Reviewed at `3c39e8cc456e3c76eac84ae45b12020c4b08a7a7` on 2026-10-02. Source and bundled-data review only; no in-game placement, weaving, duplication, washing, map-marker, shield-decoration, or loot test.

[loom-block]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/LoomBlock.java
[menu-type]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/inventory/MenuType.java#L30
[poi]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiTypes.java#L131
[profession]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/entity/npc/VillagerProfession.java#L121
[recipe-loom]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/loom.json
[planks]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/tags/item/planks.json
[loom-registration]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/Blocks.java#L5306-L5310
[axe-tag]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[harvest]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[loot-loom]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/loom.json
[behaviour]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java
[block]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/Block.java
[loom-menu]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/inventory/LoomMenu.java
[clear-menu]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java#L594-L613
[loom-screen]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/client/gui/screens/inventory/LoomScreen.java
[banner-entity]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/entity/BannerBlockEntity.java
[layers]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/entity/BannerPatternLayers.java
[renderer]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/client/renderer/blockentity/BannerRenderer.java
[ominous]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/entity/raid/Raid.java#L611-L629
[duplicate]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/crafting/BannerDuplicateRecipe.java
[components]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/core/component/DataComponents.java
[basic-patterns]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/tags/banner_pattern/no_item_required.json
[templates]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/Items.java#L2387-L2424
[pattern-tags]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/tags/BannerPatternTags.java
[pattern-registry]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/resources/RegistryDataLoader.java
[recipe-flower-banner-pattern]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/flower_banner_pattern.json
[tag-flower]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/tags/banner_pattern/pattern_item/flower.json
[recipe-creeper-banner-pattern]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/creeper_banner_pattern.json
[tag-creeper]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/tags/banner_pattern/pattern_item/creeper.json
[recipe-skull-banner-pattern]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/skull_banner_pattern.json
[tag-skull]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/tags/banner_pattern/pattern_item/skull.json
[recipe-mojang-banner-pattern]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/mojang_banner_pattern.json
[tag-mojang]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/tags/banner_pattern/pattern_item/mojang.json
[recipe-field-masoned-banner-pattern]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/field_masoned_banner_pattern.json
[tag-field-masoned]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/tags/banner_pattern/pattern_item/field_masoned.json
[recipe-bordure-indented-banner-pattern]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/bordure_indented_banner_pattern.json
[tag-bordure-indented]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/tags/banner_pattern/pattern_item/bordure_indented.json
[trade]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L450-L457
[tag-globe]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/tags/banner_pattern/pattern_item/globe.json
[piglin-loot]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/chests/bastion_other.json
[tag-piglin]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/tags/banner_pattern/pattern_item/piglin.json
[flow-pool]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward_ominous.json
[flow-loot]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward_ominous_unique.json
[tag-flow]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/tags/banner_pattern/pattern_item/flow.json
[guster-pool]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward.json
[guster-loot]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward_unique.json
[tag-guster]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/tags/banner_pattern/pattern_item/guster.json
[trade-dispatch]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/entity/npc/Villager.java#L819-L842
[piglin-template]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/structure/bastion/bridge/ramparts/rampart_1.nbt
[vault-default]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/entity/vault/VaultConfig.java
[ominous-template]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/structure/trial_chambers/reward/ominous_vault.nbt
[vault-reward]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/entity/vault/VaultBlockEntity.java#L321-L329
[washing]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/core/cauldron/CauldronInteraction.java
