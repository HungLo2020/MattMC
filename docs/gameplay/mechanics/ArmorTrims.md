# Armor trims

Decorate armor at a [Smithing Table](../blocks/SmithingTable.md) by combining a **pattern template**, **an armor piece**, and **a trim material**. The bundled game provides **18 patterns and 11 materials**. A trim changes decoration without increasing protection, durability, or the armor's material tier.

Use this guide to find pattern templates and make copies. The [Smithing guide](../smithing/Smithing.md) covers the workstation and equipment upgrades; a [Netherite Upgrade template](../items/SmithingTemplateNetheriteUpgrade.md) performs a separate operation.

## Apply or replace a trim

1. Open a placed Smithing Table
2. Put the pattern's template in the left input, your armor in the middle, and one accepted trim-material item in the right input
3. Take the output armor to finish

Each completed application consumes **one template, one armor input, and one addition item**, returning **one copy of that armor with its trim changed**. The template is not returned. There is no fuel or experience-level charge. The armor keeps its damage, enchantments, custom name, dye, and other components; trimming does not repair it.

For example, **Sentry template + Diamond Chestplate + Redstone Dust** gives that chestplate the Sentry pattern in redstone material. A Netherite Ingot used in the addition slot gives a netherite-material trim; it does not turn a Diamond Chestplate into Netherite armor.

Armor has one trim at a time. Applying a different pattern or material **replaces** the previous trim without refunding its template or material. If both pattern and material already match, the recipe produces **no output** and no inputs are consumed.

### Eligible armor

The bundled trimmable tag includes helmets, chestplates, leggings, and boots made from **Leather, Copper, Chainmail, Gold, Iron, Diamond, and Netherite**, plus the **Turtle Shell helmet**. These are 29 item types. Horse armor, Wolf Armor, Elytra, and integrated armor outside that tag are not accepted by the bundled trim recipes. An item being wearable does not itself make it trimmable.

### Addition materials

Use one item from this list. Blocks, nuggets, ores, scraps, and similarly named items do not automatically substitute for it.

| Addition item | Trim material |
| --- | --- |
| Amethyst Shard | Amethyst |
| Copper Ingot | Copper |
| Diamond | Diamond |
| Emerald | Emerald |
| Gold Ingot | Gold |
| Iron Ingot | Iron |
| Lapis Lazuli | Lapis |
| Netherite Ingot | Netherite |
| Nether Quartz | Quartz |
| Redstone Dust | Redstone |
| Resin Brick | Resin |

Resin **Brick**, not Resin Clump, is the accepted resin addition. The recipe checks the trim-material tag and then resolves the item's supplied trim material. Changing only a tag in a data pack does not guarantee a usable new material.

## Find your first template

The table gives the source of the first template and the material needed to copy it. **Every copy recipe also needs one existing matching template and seven Diamonds**; the last column is the extra ingredient, not the entire recipe.

Chances below are for a single matching chest's loot generation, death-loot roll, archaeology block's rare-table roll, or vault reward as stated. They are not the probability of finding a structure, finding its special room, or completing an expedition. Loot is not rerolled simply by reopening a chest. These are the bundled source rules; world settings, data packs, and prior exploration can alter availability.

| Pattern | First-template source | Templates and chance per stated roll | Copying ingredient (one) |
| --- | --- | --- | --- |
| [Sentry](../items/SmithingTemplateSentryArmorTrim.md) | Pillager Outpost chest | 2; 25% | Cobblestone |
| [Dune](../items/SmithingTemplateDuneArmorTrim.md) | Desert Pyramid chest | 2; 1/7 (about 14.3%) | Sandstone |
| [Coast](../items/SmithingTemplateCoastArmorTrim.md) | Shipwreck map, supply, or treasure chest | 2; 1/6 (about 16.7%) | Cobblestone |
| [Wild](../items/SmithingTemplateWildArmorTrim.md) | Jungle Temple chest | 2; 1/3 (about 33.3%) | Mossy Cobblestone |
| [Ward](../items/SmithingTemplateWardArmorTrim.md) | Ancient City ordinary chest | 1; 5% | Cobbled Deepslate |
| [Eye](../items/SmithingTemplateEyeArmorTrim.md) | Stronghold library or corridor chest | 1; library 100%, corridor 10% | End Stone |
| [Vex](../items/SmithingTemplateVexArmorTrim.md) | Woodland Mansion chest | 1; 50% | Cobblestone |
| [Tide](../items/SmithingTemplateTideArmorTrim.md) | Elder Guardian death loot | 1; 20% | Prismarine |
| [Snout](../items/SmithingTemplateSnoutArmorTrim.md) | Bastion bridge, stable, treasure, or other chest | 1; 1/12 (about 8.3%) | Blackstone |
| [Rib](../items/SmithingTemplateRibArmorTrim.md) | Nether Fortress chest | 1; 1/15 (about 6.7%) | Netherrack |
| [Spire](../items/SmithingTemplateSpireArmorTrim.md) | End City treasure chest | 1; 1/15 (about 6.7%) | Purpur Block |
| [Wayfinder](../items/SmithingTemplateWayfinderArmorTrim.md) | Trail Ruins rare suspicious gravel | 1; 1/12 of rare-table rolls | Terracotta |
| [Shaper](../items/SmithingTemplateShaperArmorTrim.md) | Trail Ruins rare suspicious gravel | 1; 1/12 of rare-table rolls | Terracotta |
| [Silence](../items/SmithingTemplateSilenceArmorTrim.md) | Ancient City ordinary chest | 1; 1.25% | Cobbled Deepslate |
| [Raiser](../items/SmithingTemplateRaiserArmorTrim.md) | Trail Ruins rare suspicious gravel | 1; 1/12 of rare-table rolls | Terracotta |
| [Host](../items/SmithingTemplateHostArmorTrim.md) | Trail Ruins rare suspicious gravel | 1; 1/12 of rare-table rolls | Terracotta |
| [Flow](../items/SmithingTemplateFlowArmorTrim.md) | Trial Chambers ominous vault | 1; 22.5% per reward | Breeze Rod |
| [Bolt](../items/SmithingTemplateBoltArmorTrim.md) | Trial Chambers regular vault or entrance reward chest | 1; 6.25% per reward | Copper Block or Waxed Copper Block |

### Acquisition details that matter

- **Trail Ruins:** use a Brush on suspicious gravel. Wayfinder, Shaper, Raiser, and Host are four separate entries in the 12-entry rare archaeology table. Each has a 1/12 chance only when that block uses the rare table; the common table supplies none of them. The house archaeology processor places the rare-table blocks. Breaking a block is not the documented brushing route
- **Ancient Cities:** Ward and Silence share one selection pool, with weights 4 and 1 against 75 empty outcomes. They are not two independent trim rolls. The ice-box chest has a different table
- **Eye:** the Stronghold library table guarantees one template when that chest's loot is generated; the corridor table gives one only 10% of the time
- **Tide:** the Elder Guardian template pool has a 20% chance, no player-credit condition, and no Looting bonus. Normal mob-loot rules still apply. See [Elder Guardian](../mobs/ElderGuardian.md#drops) and [Ocean Monument](../structures/OceanMonument.md) for the encounter
- **Vaults:** regular vaults use a Trial Key and the Bolt reward table; ominous vaults use an Ominous Trial Key and the Flow reward table. A successful Survival claim consumes one matching key, and an already-rewarded player cannot immediately claim that same vault again. The rotating display is not a selection button. Bolt's chance is 25% × 3/12 = **6.25%**; Flow's is 75% × 3/10 = **22.5%**
- **MattMC's additional Bolt chest:** the bundled Trial Chambers corridor entrance piece `entrance_1` also contains a chest assigned the regular reward table. It therefore has the same 6.25% Bolt roll, without using a key. This is a particular chest in a generated piece, not every chamber chest

The [inventory item browser](InventoryBrowser.md#mode-and-permission-limits) is a separate **Creative-only insertion** route. Seeing a template in its catalog does not establish Survival acquisition.

## Copy a template before using it

At a [Crafting Table](../blocks/CraftingTable.md), arrange the full 3×3 recipe as follows:

| Left | Middle | Right |
| --- | --- | --- |
| Diamond | Existing template | Diamond |
| Diamond | Pattern's copying ingredient | Diamond |
| Diamond | Diamond | Diamond |

The recipe consumes **one existing template, seven Diamonds, and one listed copying ingredient**, then produces **two templates of the same pattern**. Your starting template is part of the cost, so the net gain is **one template**, not two. Keep one copy in reserve if you want to make more later.

Use the exact copying ingredient. The four Trail Ruins patterns use plain Terracotta; Tide uses plain Prismarine; Ward and Silence use Cobbled Deepslate. Bolt accepts **Copper Block or Waxed Copper Block**, with no exposed, weathered, oxidized, or cut-copper substitutes. Flow uses a **Breeze Rod** rather than a building block.

For a four-piece matching outfit, four smithing applications need **four templates and four addition items**. Starting with one template, three duplication crafts cost **21 Diamonds and three copying ingredients** and leave four templates to spend. Make one additional copy if you also want to keep a template afterward.

## If no result appears

- Check all three inputs as a complete recipe. A slot accepting an item does not mean the other inputs match
- Use an armor-trim template, not a Netherite Upgrade template
- Confirm the armor belongs to the trimmable tag and the addition is one of the eleven accepted items
- If the armor already has that exact pattern and material, change one of them; identical reapplication intentionally gives no output
- On a custom server, check its loaded recipes, tags, and trim-material definitions

## Appearance and verification limits

The saved trim has a pattern and material, and the equipment renderer submits a trim overlay from those values. MattMC's active rendering path then passes the model and atlas data through the Rust/Vulkan renderer, with its own model, material, and texture eligibility checks. The presence of a trim component or an upstream texture is **not a guarantee that every armor, pattern, resource-pack, and shader combination has been visually verified**.

This page is a source review, not an in-game appearance test. It documents how to obtain and apply the trim and what the operation preserves; it does not certify color matching, overlay placement, glint, or rendering parity for every combination. If decoration looks wrong, retain the item and distinguish its saved trim from the appearance issue before spending another template.

## Related pages

- [Smithing Table](../blocks/SmithingTable.md) and [Smithing](../smithing/Smithing.md)
- [Armor and damage reduction](Armor.md)
- [Durability and repair](Durability.md)
- [Netherite Upgrade Smithing Template](../items/SmithingTemplateNetheriteUpgrade.md)
- [Mechanics](Mechanics.md)

## Sources and verification

Source-reviewed on **2026-10-03** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. All gameplay claims use active `src/main` code and bundled data, not `frnsrc/` reference material. Reviewed item/trim registration, all 18 trim and duplication recipes, expanded armor tags, loot pools and their placement/interaction consumers, smithing consumption and repeat handling, and the active trim-render submission route. No in-game world generation, loot, smithing, or visual test was run. Later builds and data packs can change these rules.

### Application, materials, and copying

- [Smithing input layout, result lookup, and consumption](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/inventory/SmithingMenu.java)
- [Trim component preservation and identical-trim rejection](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/crafting/SmithingTrimRecipe.java)
- [Trim pattern registry](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/equipment/trim/TrimPatterns.java)
- [Trim material registry and ingredient resolution](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/equipment/trim/TrimMaterials.java)
- [Material-provider and template item registrations](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java)
- [Trim registry loading](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/resources/RegistryDataLoader.java)
- [Accepted addition tag](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/trim_materials.json)
- [Trimmable armor tag](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/trimmable_armor.json)
- [head armor tag](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/head_armor.json)
- [chest armor tag](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/chest_armor.json)
- [leg armor tag](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/leg_armor.json)
- [foot armor tag](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/foot_armor.json)
- [Crafting input consumption](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/inventory/ResultSlot.java)
- [Crafting remainders](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/crafting/CraftingRecipe.java)
- [Loaded smithing recipes](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java)

The individual pattern pages above link their exact duplication and smithing recipes. All use the same copying layout, one-template smithing cost, and tag-based base/addition rules.

### Acquisition and active consumers

Each pattern page links its loot tables. The following sources establish how those tables reach chests, archaeology, entities, and vaults:

- [Server loot-table loading](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/ReloadableServerRegistries.java)
- [Structure-template block-entity data loading](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java)
- [Chest loot generation](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/RandomizableContainer.java)
- [Structure chest placement: Dune](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/levelgen/structure/structures/DesertPyramidPiece.java)
- [Structure chest placement: Coast](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/levelgen/structure/structures/ShipwreckPieces.java)
- [Structure chest placement: Wild](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/levelgen/structure/structures/JungleTemplePiece.java)
- [Structure chest placement: Eye](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/levelgen/structure/structures/StrongholdPieces.java)
- [Structure chest placement: Vex](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/levelgen/structure/structures/WoodlandMansionPieces.java)
- [Structure chest placement: Rib](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/levelgen/structure/structures/NetherFortressPieces.java)
- [Structure chest placement: Spire](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/levelgen/structure/structures/EndCityPieces.java)
- [Outpost tower pool](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/worldgen/template_pool/pillager_outpost/towers.json)
- [Outpost chest template](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/structure/pillager_outpost/watchtower.nbt)
- [Ancient City structures pool](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/worldgen/template_pool/ancient_city/structures.json)
- [Ancient City chest template](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/structure/ancient_city/structures/barracks.nbt)
- [Bastion starting pools](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/worldgen/template_pool/bastion/starts.json)
- [Trail Ruins structure pools](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/data/worldgen/TrailRuinsStructurePools.java)
- [Rare gravel placement processor](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/worldgen/processor_list/trail_ruins_houses_archaeology.json)
- [Brushing and archaeology loot](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/entity/BrushableBlockEntity.java)
- [Elder Guardian death-loot consumer](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java)
- [Trial Chambers structure and start pool](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/worldgen/structure/trial_chambers.json)
- [Trial Chambers corridor pool](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/worldgen/template_pool/trial_chambers/corridor.json)
- [Trial Chambers corridor start-piece connectors](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/structure/trial_chambers/corridor/end_1.nbt)
- [Trial Chambers entrance reward chest](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/structure/trial_chambers/corridor/entrance_1.nbt)
- [Trial Chambers copper-bulb-only degradation rules](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/worldgen/processor_list/trial_chambers_copper_bulb_degradation.json)
- [Regular vault template](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/structure/trial_chambers/reward/vault.nbt)
- [Ominous vault template](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/structure/trial_chambers/reward/ominous_vault.nbt)
- [Vault keys, rewards, and already-rewarded check](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/entity/vault/VaultBlockEntity.java)

### Appearance scope

- [Trim overlay submission](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/entity/layers/EquipmentLayerRenderer.java)
- [Active model submission and Rust route admission](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java)
- [Atlas/model eligibility and copied model submission](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java)
- [Trim component and asset selection](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/equipment/trim/ArmorTrim.java)
