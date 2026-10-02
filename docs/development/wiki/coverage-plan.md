# Coverage plan and checkpoint

## Branch and source checkpoint

- Working branch: `docs/wiki-expansion`
- Source default branch: `master`
- Last source snapshot integrated: `3e85592c4c78ebb420302360667a6c230dc0318d`
- Latest source sync: real two-parent merge `226ad2d5499924128d0e21c0390c69820320d8f2`, preserving published wiki history and integrating palette resize/unpacking and voxel-join migrations without conflicts. Incoming workflow definitions were unchanged.
- Previous source sync: real two-parent merge `0b73e6fe0303fcdf2b86ce5c58ebf1436ba4d71a` integrated renderer cleanup/documentation; Java gameplay and bundled game data were unchanged then.
- Previous source sync: real two-parent merge `c1c36ba1c0dc353b6a4ecc99b36229ee526c09f4` integrated palette packing/histograms without conflicts.
- Source sync: fast-forwarded from `fffe4a073f0b8d867902b067a6dd022cda31926f` to the newer render-bridge refactor while preserving all wiki edits; inspected changes do not touch this batch's gameplay sources
- Initial branch created from `fffe4a073f0b8d867902b067a6dd022cda31926f` on 2026-10-01
- No merge back to the default branch or deployment is authorized
- Continue using [safe synchronization](continuation.md) before each batch

## Initial coverage inventory

At the source snapshot, `docs/` contains **2,090 Markdown pages** and **32 directory indexes**. These are file counts, not a completeness score.

| Area | Existing article pages, excluding index | Assessment |
| --- | ---: | --- |
| Items | 1,872 | Broad name coverage; many brief entries need source-grounded acquisition and use |
| Mobs | 158 | Many registry-based starting entries; spawning, interactions and drops need verification |
| Blocks | 0 | Only a category heading existed; dedicated placed-block articles are needed |

The existing gameplay hierarchy has 17 categories: biomes, blocks, brewing, commands, crafting, dimensions, effects, enchanting, gamemodes, items, mobs, mechanics, redstone, smelting, smithing, structures, and trading. Preserve this taxonomy and grow meaningful subcategories only when enough substantive pages justify them.

## First coherent batch

Expanded existing pages:

- [Grizzly Bear](../../gameplay/mobs/GrizzlyBear.md), [Trilocaris](../../gameplay/mobs/Trilocaris.md), [Cow](../../gameplay/mobs/Cow.md)
- [Ambersol item](../../gameplay/items/Ambersol.md), [Trilocaris Tail](../../gameplay/items/TrilocarisTail.md), [Cooked Trilocaris Tail](../../gameplay/items/CookedTrilocarisTail.md)

New player articles:

- [Ambersol block](../../gameplay/blocks/Ambersol.md)
- [Stone block](../../gameplay/blocks/Stone.md)
- [Content guide](../../gameplay/ContentGuide.md)

Navigation updated: Gameplay, Blocks, Items, Mobs, and Development indexes. New maintenance directory: this checkpoint, its index, [page templates](page-templates.md), and [continuation procedure](continuation.md).

## Second batch: Amber, Grizzly support, and Subterranodon

- Added [Amber block](../../gameplay/blocks/Amber.md); rewrote the existing Amber item to link to its canonical placed-block behavior.
- Expanded [Grizzly Bear Spawn Egg](../../gameplay/items/GrizzlyBearSpawnEgg.md), including the source-defined polar-bear offspring difference when used on a bear.
- Expanded [Raw Cod](../../gameplay/items/RawCod.md), [Cooked Cod](../../gameplay/items/CookedCod.md), and [Honeycomb](../../gameplay/items/Honeycomb.md) with verified drops, food values, cooking, harvesting, crafting, and interactions.
- Updated Blocks and Content Guide navigation. Source remains `9bd57e1d0057903f6a9196e592d5e2a087c9248a`; no source merge was needed at batch start.
- Expanded Subterranodon, Subterranodon Egg item, and Subterranodon Spawn Egg; added a canonical Subterranodon Egg block article. Flight-key wiring and egg-laying limitations are explicit.
- Pewen/Limestone and Trilocaris bucket remain next work; research findings are not completed articles.
- Validation: required checker and strict MkDocs build passed on the final 2,099-page tree with 33 indexes; whitespace and source-path checks passed. No gameplay test.

## Third batch: Limestone and Pewen families

- Added canonical [Limestone](../../gameplay/blocks/Limestone.md) and [Pewen](../../gameplay/blocks/Pewen.md) building-family pages.
- Replaced generic Limestone, Pewen Log, Pewen Planks, and Pewen Sapling item guidance with practical source-grounded content and family links.
- Documented mining-tag, axe-stripping, generic-plank-tag and recipe-input incompatibilities without claiming a successful runtime reproduction. Pewen sapling-to-feature wiring is established; natural biome placement remains unverified.
- Updated Blocks and Content Guide navigation. Source remains `9bd57e1d0057903f6a9196e592d5e2a087c9248a`; no source sync was required at batch start.
- Validation: required checker and strict build passed on the final 2,101-page tree with 33 indexes; source paths and whitespace checked. No gameplay test.

## Fourth batch: workstations and player guides

- Added Crafting Table, Furnace, and Chest block articles with recipes, ingredient-tag conditions, operation, fuel/capacity details, and troubleshooting.
- Rewrote the three corresponding item pages to point to their canonical block guides.
- Expanded the formerly heading-only Crafting and Smelting category pages into practical introductions with verified recipes and device distinctions.
- Updated Blocks and Content Guide navigation. Source remains `9bd57e1d0057903f6a9196e592d5e2a087c9248a`.
- Validation: required checker and strict build passed on the final 2,104-page tree with 33 indexes; source targets and whitespace checked. No gameplay test.

## Fifth batch: aquatic care and Roadrunner

- Expanded Blobfish and Roadrunner with active-source attributes, interactions, care, breeding/resource mechanics, and natural-spawning verification limits.
- Expanded Blobfish food, Blobfish bucket, Trilocaris bucket, Roadrunner Feather, and the three mobs' spawn eggs.
- Explicitly documented Blobfish's Poison food effect, air-handler mismatch, and mismatched custom bucket-data component instead of promising upstream slime protection or full custom-state preservation.
- Updated Content Guide and Mobs navigation. Source remains `9bd57e1d0057903f6a9196e592d5e2a087c9248a`.
- Validation: checker and strict build passed on 2,104 pages / 33 indexes; whitespace and source targets checked. No in-game test.

## Sixth batch: modes and food mechanics

- Replaced empty Creative and Survival pages with practical mode guidance; added Adventure and Spectator.
- Expanded the Game modes index with a comparison and permission-aware command examples.
- Added Hunger, Saturation, and Healing, including source-verified food values, sprint threshold, regeneration, and starvation conditions; expanded the Mechanics index.
- Updated Content Guide and this checkpoint. Source remains `9bd57e1d0057903f6a9196e592d5e2a087c9248a`.
- Validation: checker and strict build passed on 2,107 pages / 33 indexes; source targets and whitespace checked. No gameplay test.

## Seventh batch: beds, lighting, and fuel

- Added canonical Bed and Torch block guides, with dimension hazards, sleep/respawn conditions, torch support and source-defined light values.
- Expanded White Bed, Torch, Coal, and Charcoal items with exact current recipes and fuel/tag distinctions.
- Updated Blocks, Content Guide, and this checkpoint. Source remains `9bd57e1d0057903f6a9196e592d5e2a087c9248a`.
- Validation: checker and strict build passed on 2,109 pages / 33 indexes; source targets and whitespace checked. No gameplay test.

## Eighth batch: Emu eggs and Gazelle herds

- Expanded Emu and Gazelle with practical behavior, reproduction/resource routes, active attributes, and availability caveats.
- Expanded Emu Egg, Boiled Emu Egg, and both mobs' spawn eggs; distinguished periodic eggs, probabilistic projectile hatching, and unverified boiled-egg recipe.
- Moved Emu from the passive to the neutral browsing group to reflect its retaliation and hostile-target goals. Updated Content Guide and this checkpoint.
- Documented Gazelle's absent bundled breeding-food tag instead of guessing an accepted food. Source remains `9bd57e1d0057903f6a9196e592d5e2a087c9248a`.
- Validation: checker and strict build passed on 2,109 pages / 33 indexes; source targets and whitespace checked. No gameplay test.

## Ninth batch: farming and tool progression

- Added Farmland and Wheat crop guides, explicitly documenting MattMC's empty-hand harvest/reset and same-crop 3 × 3 hoe harvest with durability cost.
- Expanded Wheat and Wheat Seeds; added Mining tools and drops with baseline material stats, Copper tier, and correct-tool/loot distinctions.
- Updated Blocks, Mechanics, Content Guide, and this checkpoint. Source remains `9bd57e1d0057903f6a9196e592d5e2a087c9248a`.
- Validation: checker and strict build passed on 2,112 pages / 33 indexes; source targets and whitespace checked. No gameplay test.

## Tenth batch: redstone foundations

- Added Lever, Buttons, and Redstone Dust/wire block guides; expanded the formerly heading-only Redstone category.
- Expanded Lever, Stone Button, Oak Button, and Redstone Dust item pages with exact recipes/acquisition and canonical behavior links.
- Distinguished game-tick pulse duration, signal strength, arrow-class activation, support, and ordinary wire attenuation; flagged evaluator-dependent behavior.
- Updated Blocks, Content Guide, and this checkpoint. Source remains `9bd57e1d0057903f6a9196e592d5e2a087c9248a`.
- Validation: checker and strict build passed on 2,115 pages / 33 indexes; source targets and whitespace checked. No circuit gameplay test.

## Eleventh batch: hostile encounters and resources

- Expanded Creeper and Skeleton with actual spawning/light rules, combat tactics, loot and conditional special rewards. Included manual ignition, charged creepers, skeleton equipment, seasonal headgear, and powder-snow conversion limits.
- Expanded Gunpowder, Bone, and Arrow with verified recipes, drop conditions, and selected uses.
- Updated Mobs, Content Guide, and this checkpoint. Source remains `9bd57e1d0057903f6a9196e592d5e2a087c9248a`.
- Validation: checker and strict build passed on 2,115 pages / 33 indexes; source targets and whitespace checked. No combat or farming test.

## Twelfth batch: brewing and Nether Wart

- Expanded Brewing into a practical potion-chain guide and added Brewing Stand block behavior; rewrote its item page.
- Added Nether Wart crop behavior and expanded Nether Wart, Blaze Powder, and Glass Bottle items with source-grounded acquisition and use.
- Covered 20-use fuel, 400-tick three-bottle operations, cancellation, base/modifier/container recipes, and the stand's actual tool requirement. Distinguished Nether Wart growth from ordinary crop harvesting.
- Updated Blocks, Content Guide, and this checkpoint. Source remains `9bd57e1d0057903f6a9196e592d5e2a087c9248a`.
- Validation: checker and strict build passed on 2,117 pages / 33 indexes; source targets and whitespace checked. No brewing/growth test.

## Thirteenth batch: smithing and Netherite progression

- Expanded Smithing with verified three-input upgrades, decorative trims, consumption, and saved-component/damage preservation; added Smithing Table block guide and rewrote its item page.
- Expanded Netherite Scrap, Netherite Ingot, and Netherite Upgrade template with exact processing/crafting recipes, bastion loot pools, and duplication.
- Updated Blocks, Content Guide, and this checkpoint. Source remains `9bd57e1d0057903f6a9196e592d5e2a087c9248a`.
- Validation: checker and strict build passed on 2,118 pages / 33 indexes; source targets and whitespace checked. No smithing/loot test.

## Fourteenth batch: enchanting foundations

- Expanded Enchanting and added Enchanting Table block guide with checked 15-shelf layout, actual transmitter-tag gaps, eligibility, requirements versus payment, compatibility, and seed behavior.
- Rewrote the table item and expanded Book, Bookshelf, Enchanted Book, and Lapis Lazuli with exact recipes, drops, and selected uses.
- Updated Blocks, Content Guide, and this checkpoint. Source remains `9bd57e1d0057903f6a9196e592d5e2a087c9248a`.
- Validation: checker and strict build passed on 2,119 pages / 33 indexes; source targets and whitespace checked. No enchanting/anvil test.

## Fifteenth batch: effects and remedies

- Expanded Effects and added Poison, Wither, and Regeneration with source-defined intervals, health conditions, verified sources, and removal distinctions.
- Rewrote Milk Bucket and Honey Bottle with accurate stack sizes, consumption/remainders, food values, collection and crafting routes.
- Distinguished all-effects Milk removal from Poison-only Honey removal; no universal immunity or damage guarantees.
- Updated Content Guide and this checkpoint. Source remains `9bd57e1d0057903f6a9196e592d5e2a087c9248a`.
- Validation: checker and strict build passed on 2,122 pages / 33 indexes; source targets and whitespace checked. No effect/consumption gameplay test.

## Sixteenth batch: dimensions and Primordial access

- Expanded Dimensions and added Nether, End, and Primordial Caves with active preset/portal/respawn wiring, hazards, scaling, and runtime limits.
- Expanded Pitcher Pod with Sniffer-digging/crop acquisition and the custom portal conversion that consumes the entire dropped item stack.
- Confirmed Primordial Caves is wired beyond a type JSON; Normal and fallback biome selection differ. Scoped biome checks did not establish natural spawning/tree placement for earlier cautious custom-content pages.
- Updated Content Guide and this checkpoint. Source remains `9bd57e1d0057903f6a9196e592d5e2a087c9248a`.
- Validation: checker and strict build passed on 2,125 pages / 33 indexes; source targets and whitespace checked. No portal/generation/respawn gameplay test.

## Seventeenth batch: command orientation

- Expanded the formerly heading-only Commands page with verified help, permissions, selectors, give/summon/mode/teleport/locate examples and query-versus-change distinctions.
- Documented the actual shared scopes: time changes iterate loaded levels, weather targets the Overworld. No commands executed.
- Updated Content Guide and this checkpoint. Source remains `9bd57e1d0057903f6a9196e592d5e2a087c9248a`.
- Validation: checker and strict build passed on 2,125 pages / 33 indexes; source targets and whitespace checked.

## Eighteenth batch: monthly changelog integration

- Inspected all existing monthly logs and preserved their paths/history; added October 2026 with concise verified master outcomes and a separate unmerged wiki-work section.
- Linked the month from both changelog indexes and added the durable monthly convention to the continuation guide, including actual issue links, completion evidence, duplicate avoidance, and single-writer ownership.
- No issue association was established for the inspected October source commits, so commit links were used without inventing issue relationships or labeling issue creation as a fix.
- Validation: checker and strict build passed on 2,126 pages / 33 indexes; whitespace and local links checked. No master branch mutation.

## Historical changelog backlog

Reconcile missing August and September 2026 coverage against actual history before creating summaries. Also verify the January 2026 file's December heading before changing historical dating. These are review tasks, not completed or invented changelog entries.

## Nineteenth batch: custom biome reference

- Expanded Biomes and added Primordial Plains and Dry Midlands with actual surface/features, candidate spawn weights/groups, and explicit light/sky/placement restrictions.
- Documented missing Dry Midlands replacement tags separately from geode generation; no gameplay fix or successful generation test is claimed.
- Linked the guides from Primordial Caves and Content Guide and appended one concise October branch-work changelog entry.
- Validation: checker and strict build passed on 2,131 pages / 34 indexes after merging source `b81c01943c9f3254e713c365a1dd633392929cb2`; source targets and whitespace checked. Reviewed gameplay sources are unchanged from the pinned `9bd57e1d0057903f6a9196e592d5e2a087c9248a` snapshot.

## Twentieth batch: villagers and trading

- Expanded Trading, Villager, and Emerald with employment, job-site ownership, offer selection, leveling, restocking, changing prices, and verified acquisition/use routes.
- Distinguished optional trade-rebalance pools from default behavior and selected Farmer offers from guaranteed stock.
- Updated Content Guide, this checkpoint, the monthly branch-work log, and the coordinated-run handoff convention. Source remains `b81c01943c9f3254e713c365a1dd633392929cb2`; reviewed gameplay sources are unchanged from the pinned `9bd57e1d0057903f6a9196e592d5e2a087c9248a` snapshot.
- Validation: required checker and strict build passed on 2,131 pages / 34 indexes; source targets and whitespace checked. No employment/trading gameplay test.

## Twenty-first batch: Pewen foliage and resources

- Expanded Pewen Branch, Pewen Pines, Pine Nuts, and Pewen Sap with support/waterlogging, tool-dependent harvesting, food values, and explicit missing sap-use evidence.
- Distinguished single-roll resource-pool selection from independent guaranteed drops; no natural forest or tree-tapping system is assumed.
- Updated the canonical family guide, Content Guide, this checkpoint, and October branch-work log. Source remains `b81c01943c9f3254e713c365a1dd633392929cb2`; no source sync required.
- Validation: checker and strict build passed on 2,131 pages / 34 indexes; source targets and whitespace checked. No gameplay test.

## Twenty-second batch: structure expeditions

- Expanded Structures and added Nether Fortress and Stronghold guides with verified placement eligibility, locating, optional rooms, chest tables, hazards, and End portal preparation.
- Distinguished placement weights and candidate counts from guaranteed structures, locating coordinates from safe arrivals, and chest-specific guarantees from guaranteed rooms.
- Updated Content Guide, this checkpoint, and October branch-work log. Source remains `b81c01943c9f3254e713c365a1dd633392929cb2`; no source sync required.
- Validation: checker and strict build passed on 2,133 pages / 34 indexes; source targets and whitespace checked. No generation, combat, loot, or portal gameplay test.

## Twenty-third batch: raccoons and capuchins

- Expanded Raccoon, Capuchin Monkey, and Raccoon Tail with active attributes/interactions, owner controls, retaliation, missing food tags, and acquisition/loot limits.
- Moved both mobs from passive to neutral browsing categories based on active retaliation goals; no gameplay classification code was changed.
- Updated Content Guide, this checkpoint, and October branch-work log. Source remains `b81c01943c9f3254e713c365a1dd633392929cb2`; no source sync required.
- Validation: checker and strict build passed on 2,133 pages / 34 indexes; source targets and whitespace checked. No spawning, taming, combat, or loot gameplay test.

## Twenty-fourth batch: End expedition supplies

- Expanded Ender Pearl, Eye of Ender, and Blaze Rod with acquisition, shared pearl cooldown, teleport damage/eligibility, eye search versus frame use, crafting, and furnace fuel.
- Separated base damage from protections/game rules and random loot from guaranteed supplies; linked the structure expedition guides.
- Updated Content Guide, this checkpoint, and October branch-work log. Source remains `b81c01943c9f3254e713c365a1dd633392929cb2`; no source sync required.
- Validation: checker and strict build passed on 2,133 pages / 34 indexes; source targets and whitespace checked. No teleport, combat, barter, portal, or fuel gameplay test.

## Twenty-fifth batch: elephants and kangaroos

- Expanded Elephant and Kangaroo with actively registered attributes, owner controls, storage, taming/breeding, combat, spawning, and loot distinctions.
- Warned that Kangaroo pouch items are absent from save/load serialization, with parent-equipment distinction; no runtime loss test claimed. Elephant storage has explicit persistence but opening is not owner-locked.
- Moved Elephant to neutral based on active retaliation; updated Content Guide, this checkpoint, and October branch-work log. Source remains `b81c01943c9f3254e713c365a1dd633392929cb2`; no source sync required.
- Validation: checker and strict build passed on 2,133 pages / 34 indexes; source targets and whitespace checked. No taming, inventory, save/reload, riding, combat, or spawning gameplay test.

## Twenty-sixth batch: anvils and repair costs

- Added Anvil block and Anvil Mechanics guides and expanded the existing Anvil item with crafting/mining, wear/falling, repair tags, enchantment combining, names, prior work, and retained components.
- Documented MattMC's actual 40-level payment cap without vanilla's Too Expensive rejection, and Creative input consumption despite waived XP/use wear.
- Updated Blocks, Mechanics, Content Guide, this checkpoint, and October branch-work log. Source remains `b81c01943c9f3254e713c365a1dd633392929cb2`; no source sync required.
- Validation: checker and strict build passed on 2,135 pages / 34 indexes; source targets and whitespace checked. No repair, enchanting, wear, or falling gameplay test.

## Twenty-seventh batch: grindstone repair and disenchantment

- Added Grindstone block guide and expanded its item page with exact recipe, placement/mining, five-percent repair bonus, curse/component retention, experience calculation, and prior-work reset.
- Distinguished removing all useful enchantments from selective upgrades and experience points from Anvil levels.
- Updated Blocks, Content Guide, this checkpoint, and October branch-work log. Source remains `b81c01943c9f3254e713c365a1dd633392929cb2`; no source sync required.
- Validation: checker and strict build passed on 2,136 pages / 34 indexes; source targets and whitespace checked. No repair, disenchantment, mining, or experience gameplay test.

## Twenty-eighth batch: root crops and foods

- Added Root Crops and expanded Carrot, Potato, and Beetroot with planting items, growth, bone meal, mature/immature loot, Fortune, food, recipes, and cooking.
- Documented inherited MattMC empty-hand and same-crop hoe harvest/reset behavior, distinct Beetroot growth, and Carrot's confirmed Kangaroo taming connection.
- Updated Blocks, Content Guide, this checkpoint, and October branch-work log. Source remains `b81c01943c9f3254e713c365a1dd633392929cb2`; no source sync required.
- Validation: checker and strict build passed on 2,137 pages / 34 indexes; source targets and whitespace checked. No growing, harvesting, cooking, or feeding gameplay test.

## Twenty-ninth batch: coordinated issue and changelog links

- Linked the existing Kangaroo persistence warning to verified open issue [#780](https://github.com/HungLo2020/MattMC/issues/780) and Dry Midlands stone/deepslate ore-target caveat to [#781](https://github.com/HungLo2020/MattMC/issues/781).
- Updated the existing October documentation entries rather than duplicating outcomes or labeling issue creation as a fix. Linked the already-landed palette entry to verified partial-progress comments on #774 and #776; both remain open.
- Source remains `b81c01943c9f3254e713c365a1dd633392929cb2`; no source sync required and no tracker mutation in this docs batch.
- Validation: checker and strict build passed on 2,137 pages / 34 indexes; issue/comment targets read and local links/whitespace checked. No gameplay or migration test rerun.

## Thirtieth batch: large Primordial dinosaurs and eggs

- Expanded Relicheirus, Tremorsaurus, Dinosaur Nugget, and both species-egg items; added a canonical placed-egg guide with actual Creative listing, Silk Touch loot, hatch conditions, proximity ownership, and trampling rules.
- Distinguished Turtle Egg breeding placeholders from species eggs; documented uncalled soup/taming hooks and unverified rider combat input instead of upstream assumptions.
- Updated Blocks, Content Guide, this checkpoint, and October branch-work log. Source remains `b81c01943c9f3254e713c365a1dd633392929cb2`; no source sync required.
- Validation: checker and strict build passed on 2,138 pages / 34 indexes; source targets and whitespace checked. No taming, hatching, breeding, combat, or riding gameplay test.

## Thirty-first batch: Primordial plant propagation

- Added Primordial Plants and expanded Fiddlehead, Cycad, and Archaic Vine items with active inherited growth, support, harvesting, and availability checks.
- Distinguished Fiddlehead neighbor spreading, Cycad's four-block bone-meal limit, and Archaic Vine's disabled natural growth/non-climbable tag status.
- Updated Blocks, Content Guide, this checkpoint, and October branch-work log. Source remains `b81c01943c9f3254e713c365a1dd633392929cb2`; no source sync required.
- Validation: checker and strict build passed on 2,139 pages / 34 indexes; source targets and whitespace checked. No propagation, harvest, placement, or climbing gameplay test.

## Thirty-second batch: pigs, riding, and porkchops

- Expanded Pig, Saddle, Carrot on a Stick, Raw Porkchop, and Cooked Porkchop with verified spawn/food routes, breeding, riding controls, boost durability, crafting, equipment removal, drops, and cooking.
- Documented the actual Saddle recipe and shear-removal path, plus the boost implementation rather than unused duration constants.
- Updated Content Guide, this checkpoint, and October branch-work log. Source remains `b81c01943c9f3254e713c365a1dd633392929cb2`; no source sync required.
- Validation: checker and strict build passed on 2,139 pages / 34 indexes; source targets and whitespace checked. No breeding, riding, shearing, loot, or cooking gameplay test.

## Thirty-third batch: ore and material progression

- Added Ore Resources and expanded Raw Iron, Iron Ingot, Raw Copper, Copper Ingot, and Diamond with correct pickaxes, base drops, Fortune/Silk Touch, processing, storage, and selected recipes.
- Distinguished Copper ore versus raw-piece yield and source-verified processing from unreviewed world-distribution claims, retaining the Dry Midlands issue caveat.
- Updated Blocks, Content Guide, this checkpoint, and October branch-work log. Source remains `b81c01943c9f3254e713c365a1dd633392929cb2`; no source sync required.
- Validation: checker and strict build passed on 2,140 pages / 34 indexes; source targets and whitespace checked. No mining, processing, crafting, or generation gameplay test.

## Thirty-fourth batch: shields and death protection

- Expanded Shield and Totem of Undying and added Defensive Items with active-use delay, angle, damage bypass, durability/axe cooldown, decoration, hand-only death protection, and effect sequence.
- Distinguished source-confirmed protection from general immunity; no old random axe-disable rule or automatic inventory refill is assumed.
- Updated Mechanics, Content Guide, this checkpoint, and October branch-work log. Source remains `b81c01943c9f3254e713c365a1dd633392929cb2`; no source sync required.
- Validation: checker and strict build passed on 2,141 pages / 34 indexes; source targets and whitespace checked. No combat, decoration, loot, or death-protection gameplay test.

## Thirty-fifth batch: hopper transfers and furnace automation

- Added Hopper block guide and expanded its item with placement, five-slot storage, transfers, cooldowns, dropped-item pickup, redstone locking, and fullness output.
- Added Furnace sided-inventory automation guidance; distinguished a Hopper's own disabled transfer loop from external access to its inventory.
- Updated Blocks, Redstone, Content Guide, this checkpoint, and October branch-work log. Source remains `b81c01943c9f3254e713c365a1dd633392929cb2`; no source sync required.
- Validation: checker and strict build passed on 2,142 pages / 34 indexes; source targets and whitespace checked. No transfer, redstone, pickup, or furnace-automation gameplay test.

## Thirty-sixth batch: armor progression

- Added Armor mechanics and expanded Copper, Iron, and Diamond Chestplates with active armor/toughness/durability, crafting, repair tags, and upgrade distinctions.
- Used current Copper-inclusive material values and explained damage-dependent armor reduction rather than a fixed percentage for every hit.
- Updated Mechanics, Content Guide, this checkpoint, and October branch-work log. Source remains `b81c01943c9f3254e713c365a1dd633392929cb2`; no source sync required.
- Validation: checker and strict build passed on 2,143 pages / 34 indexes; source targets and whitespace checked. No equipping, damage, crafting, repair, or smithing gameplay test.

## Thirty-seventh batch: zombies and spiders

- Expanded Zombie and Spider with active spawning/light rules, combat behavior, daylight, doors, conversion, riders/effects, loot, and equipment distinctions.
- Verified hand-openable Copper doors in Zombie door-breaking scope and continued drowned conversion once started; kept Spider brightness targeting separate from retaliation.
- Updated Mobs, Content Guide, this checkpoint, and October branch-work log. Source remains `b81c01943c9f3254e713c365a1dd633392929cb2`; no source sync required.
- Validation: checker and strict build passed on 2,143 pages / 34 indexes; source targets and whitespace checked. No spawning, combat, conversion, door, or loot gameplay test.

## Thirty-eighth batch: wetland animals and bucket care

- Expanded Caiman, Platypus, and Bucket of Platypus with active attributes, food, egg/ownership behavior, care, breeding/digging gaps, and capture/release rules.
- Warned about Platypus age omission and CUSTOM_DATA versus BUCKET_ENTITY_DATA mismatch, without a runtime state-loss claim; separated placed egg functionality from disabled laying.
- Updated Content Guide, this checkpoint, and October branch-work log. Source remains `b81c01943c9f3254e713c365a1dd633392929cb2`; no source sync required.
- Validation: checker and strict build passed on 2,143 pages / 34 indexes; source targets and whitespace checked. No spawning, breeding, hatching, digging, poison, or bucket gameplay test.

## Thirty-ninth batch: Vallumraptor and Grottoceratops

- Expanded both mobs and Grottoceratops Egg; added the previously missing Vallumraptor Egg item page and its Items index entry; extended Dinosaur Eggs for one-to-four Vallumraptor clusters and species-specific ownership, and connected Dinosaur Nugget food guidance.
- Documented active theft/flower-grazing risks, disconnected salad taming, and Dragon/Turtle Egg breeding placeholders without inventing Survival egg routes.
- Updated Blocks, Content Guide, this checkpoint, and October branch-work log. Source remains `b81c01943c9f3254e713c365a1dd633392929cb2`; no source sync required.
- Validation: the first checker caught the newly added Vallumraptor Egg missing from the Items index; added its entry, then checker and strict build passed on 2,144 pages / 34 indexes. Source targets and whitespace checked. No hatching, breeding, theft, grazing, combat, or taming gameplay test.

## Fortieth batch: monster resources and callback audit

- Expanded Rotten Flesh, String, Spider Eye, and Fermented Spider Eye with selected loot, actual consumable effects, wolf healing, current recipes, brewing transformations, and merchant offers.
- Verified five-String/no-Slimeball Leads, 432-tick Poison II, and the Wandering Trader's selected fermented-eye purchase offer rather than importing older expectations.
- Corrected the earlier Dinosaur Eggs and Subterranodon Egg fall-trample claims: the imported float overload does not override the current double landing callback. Active stepping behavior remains documented. Also corrected the Carrot on a Stick Fishing Rod return claim: its helper tests an empty stack, but current durability retains broken stacks. Added durable callback/state-transition review checks.
- Updated Content Guide, continuation guidance, this checkpoint, and October branch-work log. Source remains `b81c01943c9f3254e713c365a1dd633392929cb2`; no source sync required.
- Validation: checker and strict build passed on 2,144 pages / 34 indexes; source targets and whitespace checked. No loot, eating, wolf, crafting, brewing, trade, or landing gameplay test.

## Forty-first batch: Atlatitan and temporary riding

- Expanded Atlatitan and Serene Salad with the active 12,000-tick mounting interaction, ordinary steering dispatch, food values, combat, and availability distinctions.
- Separated working direct salad interaction from an unused feeding hook; documented unconnected stomp input, unsaved mounting timer, null egg placement, and active inherited XP rather than the legacy overload.
- Updated Content Guide, this checkpoint, and October branch-work log. Source remains `b81c01943c9f3254e713c365a1dd633392929cb2`; no source sync required.
- Validation: checker and strict build passed on 2,144 pages / 34 indexes; source targets and whitespace checked. No mounting, steering, feeding, breeding, combat, or XP gameplay test.

## Forty-second batch: wolves and armor care

- Expanded Wolf and Wolf Armor with actual natural populations/variants, Bone taming, owner care, broad food tags, breeding, armor recipe/scute routes, repairs, removal, and dyeing.
- Documented the retained-broken-armor absorption path precisely and clarified ordinary humanoid broken-armor attribute handling; no disappearance assumption, runtime reproduction, or indefinite-protection guarantee.
- Updated Content Guide, this checkpoint, and October branch-work log. Source remains `b81c01943c9f3254e713c365a1dd633392929cb2`; no source sync required.
- Validation: checker and strict build passed on 2,144 pages / 34 indexes; source targets and whitespace checked. No taming, breeding, armor, repair, shearing, dye, or combat gameplay test.

## Forty-third batch: Endermen and cave spiders

- Expanded Enderman and Cave Spider with actual spawning routes, gaze/anger and teleport checks, ordinary/trial-spawner distinctions, difficulty-specific Poison, and loot.
- Moved Cave Spider to the neutral browsing group based on its inherited brightness-dependent targeting; special spawner availability does not change that target-goal behavior.
- Updated Mobs, Content Guide, this checkpoint, and October branch-work log. Source remains `b81c01943c9f3254e713c365a1dd633392929cb2`; no source sync required.
- Validation: checker and strict build passed on 2,144 pages / 34 indexes; source targets and whitespace checked. No spawning, gaze, teleport, combat, poison, or loot gameplay test.

## Forty-fourth batch: retained broken items and repair choices

- Added Durability mechanics with current retained-stack behavior, active function guards, equipment effects, and Anvil/Grindstone/crafting-grid repair distinctions.
- Traced the crafting repair recipe's fresh result, curse retention, and lost custom components; linked the previously documented steering-tool and Wolf Armor special cases.
- Updated Mechanics, Mining, Armor, Anvil Mechanics, Crafting, Content Guide, this checkpoint, and October branch-work log. Source remains `b81c01943c9f3254e713c365a1dd633392929cb2`; no source sync required.
- Validation: checker and strict build passed on 2,145 pages / 34 indexes; source targets and whitespace checked. No wear-out, repair, data-retention, mining, or equipment gameplay test.

## Forty-fifth batch: Wither and Nether Star

- Expanded Wither and Nether Star with active summon pattern/gates, charge/explosion, combat phases, skull effects, terrain risk, custom death drop, XP, and Beacon recipe.
- Distinguished arrow-family/Wind Charge immunity from all projectiles, power from blast radius, and the fresh boss drop's extended lifetime from later dropped stacks.
- Updated Mobs, Content Guide, this checkpoint, and October branch-work log. Integrated master `4285adff2e35307c277a3a5bf54ebd064aa5e64b` through the real merge above; cited Java/data sources remain unchanged from `b81c01943c9f3254e713c365a1dd633392929cb2`. Also recorded the verified renderer refactor in October's landed-master section.
- Validation: checker and strict build passed on 2,150 pages / 35 indexes; source targets and whitespace checked. No summoning, combat, containment, loot, despawn, or crafting gameplay test.

## Forty-sixth batch: prehistoric food blocks and magma

- Added canonical Dinosaur Chop and Primal Magma/Fissure block guides, with active eating/cooking, mining gates, collision, step damage, scheduled state changes, and legacy callback limits.
- Replaced generic Primal Magma, Dinosaur Chop, and Cooked Dinosaur Chop item stubs with verified acquisition/use guidance; cross-linked Blocks and Content Guide and recorded the October outcome.
- Source remains integrated master `4285adff2e35307c277a3a5bf54ebd064aa5e64b`; cited gameplay source is unchanged from `b81c01943c9f3254e713c365a1dd633392929cb2`.
- Validation: checker and strict build passed on 2,152 pages / 35 indexes; source targets and whitespace checked. No eating, cooking, collision, fire, mining, waterlogging, comparator, or piston gameplay test.

## Forty-seventh batch: Armadillo, scutes, and Brush

- Expanded active Armadillo spawning, feeding, breeding, fear/damage reduction, adult brushing, and natural shedding, plus Scute crafting/repair and Brush archaeology/repair.
- Distinguished normal durability costs from missing broken-Brush checks in adult interaction and dispenser paths; linked the general Durability guide without claiming runtime reproduction.
- Updated Mobs, Content Guide, this checkpoint, and October log. Source remains integrated master `4285adff2e35307c277a3a5bf54ebd064aa5e64b`; cited Java/data sources are unchanged from `b81c01943c9f3254e713c365a1dd633392929cb2`.
- Validation: checker and strict build passed on 2,152 pages / 35 indexes; source targets and whitespace checked. No spawning, brushing, archaeology, dispenser, repair, or broken-tool gameplay test.

## Forty-eighth batch: fishing and rod maintenance

- Added Fishing mechanics and expanded Fishing Rod with recipe, lifecycle, bite timing, weather, Lure/Luck effects, exact open-water layer rules, conditional loot weights, entity pulling, and retrieval wear.
- Linked Mechanics and Content Guide and recorded the October outcome. Source remains integrated master `4285adff2e35307c277a3a5bf54ebd064aa5e64b`.
- Validation: checker and strict build passed on 2,153 pages / 35 indexes; source targets and whitespace checked. No casting, open-water, loot-distribution, entity-pulling, or repair gameplay test.

## Forty-ninth batch: oak and beginner wood resources

- Added canonical Oak behavior and expanded Oak Log, Oak Planks, Oak Sapling, and Stick with harvesting, leaf/Fortune rolls, planting/stages/Bone Meal, bee-capable tree selection, stripping, recipes, and fuel comparisons.
- Updated Blocks, Content Guide, this checkpoint, and October log. Source remains integrated master `4285adff2e35307c277a3a5bf54ebd064aa5e64b`.
- Validation: checker and strict build passed on 2,154 pages / 35 indexes; source targets and whitespace checked. No tree-growth, leaf-drop, bee-nest, crafting, stripping, or furnace gameplay test.

## Fiftieth batch: Rhinoceros and Komodo resources

- Expanded Rhinoceros trust, breeding, potion coating, attacks and availability; Komodo predation, missing food tags, owner controls, breeding, and living-adult spit production.
- Expanded Komodo Spit and replaced the misleading generic Bottle of Komodo Spit consumable text with verified plain-item/recipe limitations.
- Updated Mobs, Content Guide, this checkpoint, and October log. Source remains integrated master `4285adff2e35307c277a3a5bf54ebd064aa5e64b`; cited Java/data sources are unchanged from `b81c01943c9f3254e713c365a1dd633392929cb2`.
- Validation: checker and strict build passed on 2,154 pages / 35 indexes; source targets and whitespace checked. No trust, coating, taming, breeding, riding, combat, or production gameplay test.

## Fifty-first batch: boat and rail transport

- Added Transport mechanics and expanded Oak Boat, Oak Boat with Chest, and Minecart, covering controls, capacities, persisted cargo, recovery, dispensers, rail construction, launch/braking, detector/activator roles, and optional minecart-experiment differences.
- Used MattMC's actual Sneak/Crouch binding and kept source speed limits separate from measured travel performance; no universal boost spacing claimed.
- Updated Mechanics, Content Guide, this checkpoint, and October log. Source remains integrated master `4285adff2e35307c277a3a5bf54ebd064aa5e64b`; cited Java/data sources are unchanged from `b81c01943c9f3254e713c365a1dd633392929cb2`.
- Validation: checker and strict build passed on 2,155 pages / 35 indexes; source targets and whitespace checked. No crafting, vehicle control, cargo, rail, station, speed, or dispenser gameplay test.

## Fifty-second batch: bows and crossbows

- Expanded Bow and Crossbow with active recipes, draw/load/release behavior, ammunition selection, Infinity constraints, Quick Charge, Multishot/Piercing, firework hazards, and retained-broken-item guards.
- Distinguished per-projectile wear and conditional target damage from universal shot totals; added Content Guide and October entries.
- Source remains integrated master `4285adff2e35307c277a3a5bf54ebd064aa5e64b`.
- Validation: checker and strict build passed on 2,155 pages / 35 indexes; source targets and whitespace checked. No weapon, ammunition, damage, enchantment, wear, or repair gameplay test.

## Fifty-third batch: sheep, chickens, wool, and eggs

- Expanded Sheep and Chicken with verified spawn examples, foods/breeding, growth, biome variants/colors, shearing/regrowth/dyeing, adult laying, jockey exclusions, drops and XP.
- Expanded White Wool and Egg with recipes, fuel, thrown-hatching probabilities versus spawn checks, and variant inheritance; linked existing canonical String/Bed details.
- Updated Mobs, Content Guide, this checkpoint, and October log. Source remains integrated master `4285adff2e35307c277a3a5bf54ebd064aa5e64b`.
- Validation: checker and strict build passed on 2,155 pages / 35 indexes; source targets and whitespace checked. No breeding, shearing, grazing, laying, hatching, variant, loot, or crafting gameplay test.

## Fifty-fourth batch: Blue Jay and Crow

- Expanded Blue Jay temporary feeding/following/song and conditional raccoon partnership; expanded Crow dropped-seed taming, commands, shoulder riding, marked-container gathering, perch behavior, breeding and crop protection.
- Clearly marked missing natural spawn wiring, Blue Jay countdown persistence, Crow interaction/perch concerns, and lack of a registered unique resource rather than adding speculative item stubs.
- Updated Mobs, Content Guide, this checkpoint, and October log. Source remains integrated master `4285adff2e35307c277a3a5bf54ebd064aa5e64b`; cited Java/data sources are unchanged from `b81c01943c9f3254e713c365a1dd633392929cb2`.
- Validation: checker and strict build passed on 2,155 pages / 35 indexes; source targets and whitespace checked. No taming, song, gathering, breeding, shoulder-riding, crop, or reload gameplay test.

## Fifty-fifth batch: bees and housing

- Added canonical Bee housing and expanded Bee, Beehive, and Bee Nest with occupied-tree/sapling routes, food/pollination, dangerous flowers, nectar/release timing, colony capacity/save data, smoke/dispenser behavior, and Silk Touch relocation.
- Refined Honeycomb's durability statement and linked the active block-side broken-Shears exception; linked Honey Bottle and Durability to the canonical behavior.
- Updated Blocks, Mobs, Content Guide, this checkpoint, and October log. Source remains integrated master `4285adff2e35307c277a3a5bf54ebd064aa5e64b`; cited Java/data sources are unchanged from `b81c01943c9f3254e713c365a1dd633392929cb2`.
- Validation: checker and strict build passed on 2,156 pages / 35 indexes; source targets and whitespace checked. No colony, pollination, honey, smoke, dispenser, moving-hive, or broken-Shears gameplay test.

## Fifty-sixth batch: fortress mobs and skulls

- Expanded Blaze and Wither Skeleton with active fortress/spawner routes, dimension-dependent darkness versus Blaze any-light rules, attacks, defenses, drops and XP.
- Expanded Wither Skeleton Skull with player-credit versus Looting conditions, charged-Creeper limits, placement/wearing, banner recipe and canonical Wither summon links.
- Updated Nether Fortress, Mobs, Content Guide, this checkpoint, and October log. Source remains integrated master `4285adff2e35307c277a3a5bf54ebd064aa5e64b`.
- Validation: checker and strict build passed on 2,156 pages / 35 indexes; 55 distinct source targets, 19 JSON files, local links/anchors and whitespace checked. No spawn-lighting, combat, skull-farming, equipment, or summoning gameplay test.

## Fifty-seventh batch: Tiger and Tasmanian Devil

- Expanded Tiger dropped-food healing/blessing, targeting/combat, missing breeding foods, white inheritance and legacy anger/collision limits.
- Expanded Tasmanian Devil direct versus dropped-food behavior, monster-disrupting howl, Bone-to-Bone-Meal conversion, breeding, and obsolete attack/kill-reward callbacks.
- Updated Mobs, Content Guide, this checkpoint, and October log. Source remains integrated master `4285adff2e35307c277a3a5bf54ebd064aa5e64b`; cited Java/data sources are unchanged from `b81c01943c9f3254e713c365a1dd633392929cb2`.
- Validation: checker and strict build passed on 2,156 pages / 35 indexes; source targets, local links/anchors and whitespace checked. No blessing, howl, breeding, conversion, combat, or movement gameplay test.

## Fifty-eighth batch: buckets and fluids

- Expanded Bucket, Water Bucket, and Lava Bucket with recipe/stacking, source versus flow collection, cauldrons, animal-bucket distinctions, waterlogging, ultrawarm evaporation, source-conversion rules, and lava fuel/remainder handling.
- Kept source-derived pool construction separate from runtime validation and bounded water/lava mixing claims to the checked branch.
- Updated Content Guide, this checkpoint, and October log. Source remains integrated master `4285adff2e35307c277a3a5bf54ebd064aa5e64b`.
- Validation: checker and strict build passed on 2,156 pages / 35 indexes; source targets and whitespace checked. No fluid, cauldron, source-pool, waterlogging, dispenser, or furnace gameplay test.

## Fifty-ninth batch: aquatic animals and bucket transfer

- Expanded Axolotl and Dolphin with active natural-spawn routes, food/breeding distinctions, variants, air/moisture care, player assistance, prey, and structure-guidance limits.
- Expanded Bucket of Axolotl with current component save/load, hand/dispenser release, persistent state, and the ultrawarm release hazard; separated unused Primordial Ocean data from active world generation.
- Updated Mobs, Content Guide, this checkpoint, and October log. Source remains integrated master `4285adff2e35307c277a3a5bf54ebd064aa5e64b`.
- Validation: checker and strict build passed on 2,156 pages / 35 indexes; source targets, local links/anchors and whitespace checked. No spawning, breeding, assistance, guidance, moisture, or bucket-transfer gameplay test.

## Sixtieth batch: Sugar Cane and fruit crops

- Added canonical Sugar Cane and Pumpkin/Melon farming, including active biome features, support/water/light, random growth, Bone Meal, fruit space, drops, stem preservation, and distinct MattMC harvest controls.
- Expanded six item pages for Sugar Cane, Pumpkin, Melon, Melon Slice, Pumpkin Seeds, and Melon Seeds; linked the existing Pumpkin Pie recipe rather than duplicating it.
- Clarified Pumpkin carving's source-reachable broken-Shears exception and normal wear. Updated Blocks, Content Guide, this checkpoint, and October log.
- Source remains integrated master `4285adff2e35307c277a3a5bf54ebd064aa5e64b`.
- Validation: checker and strict build passed on 2,158 pages / 35 indexes; source targets, local links/anchors and whitespace checked. No crop growth, harvesting, Bone Meal, carving, loot, or crafting gameplay test.

## Sixty-first batch: slimes, cubes, and resource navigation

- Expanded Slime/Magma Cube spawn chains and spawners, size/health/armor/contact damage, death splitting, size-dependent drops, frog rewards and XP.
- Expanded Slimeball/Magma Cream recipes, trader/Panda/chest routes, brewing distinctions and Fire Resistance limits; preserved the actual five-String Lead recipe.
- Organized the existing Content Guide into practical play topics and replaced accumulating Mobs introductory paragraphs with concise featured routes, retaining the complete alphabetical category directory.
- Updated this checkpoint and October log. Integrated master `3e85592c4c78ebb420302360667a6c230dc0318d` through the real merge above. All cited Slime/Magma Cube article source paths remain unchanged from the reviewed `4285adff2e35307c277a3a5bf54ebd064aa5e64b` snapshot; recorded the landed palette/voxel work in October.
- Validation: checker and strict build passed on 2,162 pages / 36 indexes; source targets, local links/anchors and whitespace checked. No spawning, splitting, loot-distribution, frog, spawner, brewing, or crafting gameplay test.

## Sixty-second batch: coordinated tracker crosslinks

- Linked existing Platypus bucket component-state, Rhinoceros Water Bottle clearing, and Crow repeated-feeding/result warnings to verified issues #782, #783, and #784. Retained separate age/perch limitations and source-only verification notes; no fix claimed.
- Linked October's already-recorded palette/voxel migration row to the verified partial-progress comments on #774 and #770; no subsystem completion or gameplay-fix claim.
- Source remains integrated master `3e85592c4c78ebb420302360667a6c230dc0318d`. This batch changes documentation crosslinks only.
- Validation: checker and strict build passed on 2,162 pages / 36 indexes; issue/comment destinations were read directly and whitespace checked. No gameplay tests.

## Sixty-third batch: ocean expeditions and treasure maps

- Added Shipwreck, Ocean Ruins, and Buried Treasure guides with active generation/templates, chest layouts/loot, archaeology, map odds and failed/repeated lookup limits, excavation and Heart of the Sea use.
- Distinguished source table guarantees from unopened-world rewards and avoided claiming unassigned buried-treasure potions are Water Breathing.
- Updated Structures, Content Guide, this checkpoint, and October log. Source remains integrated master `3e85592c4c78ebb420302360667a6c230dc0318d`; the cited structure/data paths are unchanged from reviewed `4285adff2e35307c277a3a5bf54ebd064aa5e64b`.
- Validation: checker and strict build passed on 2,165 pages / 36 indexes; source targets, local links/anchors and whitespace checked. No world generation, dive, archaeology, map, excavation, or chest-opening gameplay test.

## Sixty-fourth batch: dragon fight, crystals, and trophy

- Expanded Ender Dragon and End Crystal with active tracked-fight lifecycle, healing/damage gates, perching, breath collection, first/repeat rewards, exact crystal clearance and respawn positions, and destructive pillar rebuilding.
- Added canonical Dragon Egg block behavior and expanded its item page with teleport/fall/piston collection; retained the separate Vallumraptor placeholder rather than asserting world-wide uniqueness.
- Updated Blocks, Mobs, Content Guide, End, this checkpoint, and October log. Source remains integrated master `3e85592c4c78ebb420302360667a6c230dc0318d`.
- Validation: checker and strict build passed on 2,166 pages / 36 indexes; source targets, local links/anchors and whitespace checked. No fight, damage, breath, respawn, egg-teleport, or collection gameplay test.

## Sixty-fifth batch: maps, cartography, and compasses

- Added canonical Cartography Table and expanded Map, Empty Map, Compass, and the table item with current recipes, held surveying, grid scales, shared map IDs, fresh scaled data, locking and menu cleanup.
- Distinguished the table's locked-map guard from the crafting extension's missing check, and global/default spawn targeting from Lodestone binding and dimension validity; verified the current Iron-based Lodestone recipe.
- Updated Blocks, Content Guide, this checkpoint, and October log. Source remains integrated master `3e85592c4c78ebb420302360667a6c230dc0318d`.
- Validation: checker and strict build passed on 2,167 pages / 36 indexes; source targets, local links/anchors and whitespace checked. No mapping, copying, locking, Compass, binding, or crafting gameplay test.

## Sixty-sixth batch: Crocodile integration and scute

- Expanded Crocodile with active registration, fish food, custom mating/laying, Turtle Egg and Turtle Scute placeholders, existing-owner controls, and exact legacy versus current melee dispatch.
- Replaced the erroneous generic Crocodile Scute block description with its plain-item registration, Creative access, and unestablished Survival/recipe uses.
- Updated Mobs, Content Guide, this checkpoint, and October log. Source remains integrated master `3e85592c4c78ebb420302360667a6c230dc0318d`.
- Validation: checker and strict build passed on 2,167 pages / 36 indexes; source targets, local links/anchors and whitespace checked. No feeding, breeding, growth, ownership, combat, or scute gameplay test.

## Sixty-seventh batch: all-color Wool and Carpet family

- Added canonical Wool and Carpet behavior and expanded/connected all 32 existing color item pages, preserving White Wool's earlier facts and recipe ownership.
- Audited all 16 registry/color mappings, 48 dye/carpet recipes, 32 block loot tables, 32 sheep color tables, shared tags, support/mining/fire/fuel, and current vibration dispatch. Carpet recoloring is one item per dye; fuel is 67 default ticks; carpet dampening does not substitute for full wool signal occlusion.
- Added reciprocal Sheep navigation and updated Blocks, Content Guide, this checkpoint, and October log. The external audit matrix is not published as a wiki page.
- Source remains integrated master `3e85592c4c78ebb420302360667a6c230dc0318d`.
- Validation: checker and strict build passed on 2,168 pages / 36 indexes; source targets, local links/anchors and whitespace checked. No sheep, dyeing, placement, mining, fire, furnace, or vibration gameplay test.

## Sixty-eighth batch: End Cities, Shulkers, and flight

- Added End City with active biome/height/template generation, conditional ships, chest loot, Elytra frame rewards, and exploration hazards.
- Expanded Shulker, Shulker Shell, and Elytra with projectile/teleport/duplication behavior, exact shell chances, Levitation flight restriction, 431-of-432 exhaustion, repair/Mending and star-free rocket boosts.
- Kept initial closed-shell armor uncertainty bounded by describing the actual closing handler, not claiming every initial spawn already has its modifier.
- Updated Structures, Mobs, Content Guide, End, this checkpoint, and October log. Source remains integrated master `3e85592c4c78ebb420302360667a6c230dc0318d`.
- Validation: checker and strict build passed on 2,169 pages / 36 indexes; source targets, local links/anchors and whitespace checked. No city generation, combat, loot, duplication, gliding, repair, or boost gameplay test.

## Next batches, in priority order

1. Continue animal resource clusters beyond completed Grizzly/cod/honeycomb, Trilocaris bucket, Subterranodon/egg, Blobfish and Roadrunner coverage. Prioritize practical player interactions and active availability.
2. Extend the completed Limestone/Pewen/Amber and Pewen foliage/resource guides with remaining substantive item cross-links. Do not claim the documented integration gaps are repaired.
3. Continue vanilla essentials beyond the completed workstations, food mechanics, beds, torches, fuels, and modes: ore/tool tiers, farming, basic exploration, and redstone.
4. Work through Alex's Mobs by related biome or interaction, pairing mobs with their drops, breeding items, buckets, and equipment. Use registry IDs and active spawn/config sources to track completeness.
5. Work through integrated Alex's Caves by actually registered biome, mob, block and item clusters; do not assume all upstream biomes exist.
6. Broaden all 17 gameplay categories, including world/dimension discovery, effects/enchantments, structures, redstone, trading, and commands. Select content from the current registries/data, not an external wiki page list.
7. Periodically reconcile registry IDs against page coverage and prioritize missing mechanics or incorrect stubs over raw article count. Record semantic coverage separately from file count.

## Evidence gaps to carry forward

- Luxtructosaurus is excluded at `b81c01943c9f3254e713c365a1dd633392929cb2`: no active entity/attribute/item registration or implementation was found. [Sauropod base comments](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/living/SauropodBaseEntity.java#L49) explicitly describe it as not added; the [tag constant](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/misc/ACTagRegistry.java#L60) and stub world-data flags do not establish a boss or summoning route. Reassess only if active registration/runtime implementation changes; do not create an upstream-derived placeholder.

- Source review does not establish in-game/runtime correctness.
- Ambersol has a drop entry and a correct-tool requirement, but no matching mining-tool tag was found; Survival harvesting and natural-generation wiring remain unverified.
- Verify natural spawning through actual biome/config registration rather than treating standalone spawn predicates as proof.
- Existing untouched item/mob stubs are not newly verified by this batch.

## Initial batch validation record

- Baseline: `python3 DevUtils/RunWiki.py check` passed on 2,090 pages and 32 indexes.
- Final expanded-tree check: passed on 2,097 pages and 33 indexes.
- Strict MkDocs build: passed; existing pages omitted from explicit navigation are informational, not build failures.
- Final validation was repeated after the source fast-forward and all batch edits.
- Gameplay tests: not run; this batch changes documentation only.

## Mutation ledger policy

Every batch must record its source sync, exact changed paths, published commit, test outcomes, and remaining gaps in the requesting conversation. The commit diff is the authoritative file ledger. Update this checkpoint within the same batch; record the resulting commit link in the conversation (it cannot reference its own future hash here).
