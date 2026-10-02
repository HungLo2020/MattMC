# Coverage plan and checkpoint

## Branch and source checkpoint

- Working branch: `docs/wiki-expansion`
- Source default branch: `master`
- Latest non-wiki default-branch snapshot integrated: `cf8cd5371bd1de61411ae5e7e144aabe3edb1e54`; older gameplay citations remain pinned to their reviewed snapshots where the relevant behavior is unchanged.
- Latest source sync: fast-forwarded from `beaa5747b36af51b001a13ce8b6648319ba6faf5` to `cf8cd5371bd1de61411ae5e7e144aabe3edb1e54`, preserving all eighteen pending docs and all 23 incoming ore-target/Enderman fix paths. User-merged PRs #792 and #793 are integrated; PR #791 remained unmerged at this checkpoint. Instructions and workflows were unchanged.
- Previous source sync: fast-forwarded again from `b6b5f733b316cef6852866924e2f11f12b0c4f5c` to `7af3a1594956f41ed57c3bf67d11ce006e61530f` after the publication guard detected a concurrent packed-voxel rotation migration. All 23 pending docs and all 20 incoming paths were preserved; instructions and workflows were unchanged.
- Previous source sync: fast-forwarded from promoted cutoff `e87cde38c872d30ae86139bbee181937603af769` to master `b6b5f733b316cef6852866924e2f11f12b0c4f5c`, preserving all 23 pending wiki paths byte-for-byte and all twelve incoming Crow, Kangaroo and Platypus fix paths in the Git index. Incoming tests remain outside this checkout’s sparse working tree. README, AGENTS and workflows were unchanged.
- Previous source sync: fast-forward from promoted cutoff `c87803e75d339e5d643ca812efc70a6def06a401` to master `fb7d6979fb8d9773cfe05f084c6085f35feb885c`, preserving all 39 pending documentation paths byte-for-byte and all twelve incoming merged-fix paths. PRs #787, #786 and #785 landed Rhinoceros, Ambersol and Grizzly corrections plus tests and guides; README, AGENTS and workflows were unchanged.
- Previous source sync: fast-forward from promoted cutoff `84e9628b0fbd2fe8ea1769278feaa4ce82259fd2` to master `2d4b7646eac8561a23f41f873e261d86f0cff5a2`, preserving the two incoming prompt-document reorganizations and all eight pending Cat/Ocelot documents in place. No source code or workflows changed in that incoming commit.
- Previous source sync: fast-forward from promoted cutoff `959ad4e7ec6e1347ee00ef5799ae99cf4a478db8` to master `6fe3f1e877707e45ee3159929bb9cd8769d6bda7`, preserving all 23 incoming voxel-box/skill paths and all eight pending Oceans documents in place. The new repository sync-and-push skill was read; README, AGENTS, and workflows were unchanged.
- Previous source sync: real two-parent merge `9fc7874be6328503aabf794c757584c4c0d11514`, integrating all 93 incoming rendering/shader-control paths exactly from master without conflicts. Incoming README, AGENTS, and workflows were unchanged.
- Previous source sync: real two-parent merge `226ad2d5499924128d0e21c0390c69820320d8f2`, preserving published wiki history and integrating palette resize/unpacking and voxel-join migrations without conflicts. Incoming workflow definitions were unchanged.
- Previous source sync: real two-parent merge `0b73e6fe0303fcdf2b86ce5c58ebf1436ba4d71a` integrated renderer cleanup/documentation; Java gameplay and bundled game data were unchanged then.
- Previous source sync: real two-parent merge `c1c36ba1c0dc353b6a4ecc99b36229ee526c09f4` integrated palette packing/histograms without conflicts.
- Source sync: fast-forwarded from `fffe4a073f0b8d867902b067a6dd022cda31926f` to the newer render-bridge refactor while preserving all wiki edits; inspected changes do not touch this batch's gameplay sources
- Initial branch created from `fffe4a073f0b8d867902b067a6dd022cda31926f` on 2026-10-01
- One-off promotion: on 2026-10-02 UTC, an explicit instruction authorized fast-forwarding master from `3e85592c4c78ebb420302360667a6c230dc0318d` to `239a8cb570ae75443f9d7865d3caaa1b239a4300`, including batches 1–72. The 379 changed paths were all documentation Markdown; final check/build/whitespace passed on 2,176 pages and 36 indexes.
- Standing publication instruction issued on 2026-10-02 UTC supersedes the earlier branch-only restriction: after each completed validated wiki batch, including three-hour reviews, promote the preserved documentation history to master and verify the expected Wiki Pages deployment. Author on the wiki branch, preserve concurrent source changes, use no force-push, and coordinate one deployment at a time.
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

## Sixty-ninth batch: repeaters, comparators, and observers

- Added three canonical device guides and expanded matching item pages with verified recipes, support/mining, placement direction, direct versus side inputs, ordinary/initial timing, locking, compare/subtract math, and Observer update/pulse limits.
- Explained actual container stack-size fullness and blocked-Chest reads; small circuits are explicitly source-derived and untested. Connected Hopper and Dust guidance to the new pages.
- Updated Blocks, Redstone, Content Guide, this checkpoint, and October log. Source remains integrated master `3e85592c4c78ebb420302360667a6c230dc0318d`.
- Validation: checker and strict build passed on 2,172 pages / 36 indexes; source targets, local links/anchors and whitespace checked. No circuit, timing, locking, container, observer, or crafting gameplay test.

## Seventieth batch: ordinary, stained, and tinted glass

- Added one shared Glass and Glass Panes guide and expanded all 35 ordinary, stained-color, pane, and tinted item pages with audited recipes and collection rules.
- Covered pane connections/support, waterlogging, light blocking, beacon coloring, and Tinted Glass differences; checked every color rather than assuming Wool recoloring applies.
- Updated Blocks, Content Guide, this checkpoint, and October log. Source remains integrated master `3e85592c4c78ebb420302360667a6c230dc0318d`.
- Validation: checker and strict build passed on 2,173 pages / 36 indexes; source paths, local links/anchors, and whitespace checked. No crafting, mining, lighting, waterlogging, renderer, or beacon gameplay test.

## Seventy-first batch: portable Shulker Box storage

- Added canonical Shulker Box behavior and expanded the uncolored/all 16 colored item pages with exact recipes, component preservation, collection, lid clearance, washing, and automation.
- Distinguished Survival/Creative washing, block-break preservation, damage-induced item contents release, and ordinary expiry; normal loot copies specified components rather than arbitrary added data.
- Connected Shell and Hopper guidance, Blocks, Content Guide, this checkpoint, and October log. Source remains integrated master `3e85592c4c78ebb420302360667a6c230dc0318d`.
- Validation: checker and strict build passed on 2,174 pages / 36 indexes; source paths, local links/anchors, and whitespace checked. No storage, coloring, washing, breaking, automation, damage, or expiry gameplay test.

## Seventy-second batch: block movement and item devices

- Added canonical Pistons and Dispenser/Dropper guides plus four connected item pages with exact recipes, mining/drop requirements, power response, load limits, random-slot selection, sided transfer, and active item actions.
- Distinguished queued Piston events from scheduled item-device actions, ordinary container spills from portable storage, and source-derived examples from untested gameplay.
- Updated Blocks, Redstone, Hopper, Content Guide, this checkpoint, and October log. Source remains integrated master `3e85592c4c78ebb420302360667a6c230dc0318d`.
- Validation: checker and strict build passed on 2,176 pages / 36 indexes; source paths, local links/anchors, and whitespace checked. No motion, dispensing, transfer, timing, or save/reload gameplay test.

## Seventy-third batch: Concrete and Powder colors

- Added canonical Concrete/Powder behavior and expanded all 32 color/form item pages with exact nine-ingredient recipes, matching conversion targets, falling and water-contact rules, and mining/drop requirements.
- Distinguished ordinary Sand from excluded Red Sand, adjacent water from water below, and hand-collectible powder from correct-tool Concrete drops.
- Updated Blocks, Content Guide, this checkpoint, and October log. Master and wiki share the authorized promotion cutoff `239a8cb570ae75443f9d7865d3caaa1b239a4300`; gameplay source remains `3e85592c4c78ebb420302360667a6c230dc0318d`. This batch remains wiki-branch-only.
- Validation: checker and strict build passed on 2,177 pages / 36 indexes; source paths, local links/anchors, and whitespace checked. No crafting, water conversion, falling, mining, or farm gameplay test.

## Seventy-fourth batch: occupancy and crossing sensors

- Added Pressure Plates and Tripwire guides, expanded five sensor item pages, and added one guide link while preserving String's existing source-grounded content.
- Covered living/all-entity filters, count-based weighted signals, recheck intervals, support, hook spacing, line attachment, and usable-Shears disarming through active callbacks.
- Updated Blocks, Redstone, Content Guide, this checkpoint, and October log. Gameplay source remains `3e85592c4c78ebb420302360667a6c230dc0318d`; this batch is subsequent wiki-branch work after the authorized master cutoff.
- Validation: checker and strict build passed on 2,179 pages / 36 indexes; source paths, local links/anchors, and whitespace checked. No entity-detection, count, circuit, attachment, disarming, or timing gameplay test.

## Seventy-fifth batch: Orcas and Hammerhead Sharks

- Expanded two custom predator guides and both spawn-egg pages with verified current access, health/combat, player interaction, air/water care, and loot limits.
- Distinguished registered mobs from absent bundled natural-spawn wiring; documented Orca swimming benefits and missing mating AI/legacy melee selection, plus Hammerhead injured-target acquisition versus continuation.
- Updated Mobs, Content Guide, this checkpoint, and October log. Gameplay source remains `3e85592c4c78ebb420302360667a6c230dc0318d`; these are wiki-branch additions after the authorized master cutoff.
- Validation: checker and strict build passed on 2,179 pages / 36 indexes; source paths, local links/anchors, and whitespace checked. No spawning, swimming, breeding, combat, enclosure, or drop gameplay test.

## Seventy-sixth batch: placed rails and cart activation

- Added Rails and expanded its four item forms while preserving Transport/Minecart's established recipe and route guidance.
- Covered support, curves/slopes, same-type power relays, detector digital/analog differences, per-cart Activator effects, and default/experimental movement distinctions without untested speed or spacing claims.
- Updated Blocks, Redstone, Transport, Content Guide, this checkpoint, and October log. Gameplay source remains `3e85592c4c78ebb420302360667a6c230dc0318d`; this is wiki-branch work after the authorized master cutoff.
- Validation: checker and strict build passed on 2,180 pages / 36 indexes; source paths, local links/anchors, and whitespace checked. No track, junction, vehicle, timing, speed, or stopping-distance gameplay test.

## Seventy-seventh batch: Frog lifecycle and variants

- Expanded Frog and Tadpole care, added canonical Frogspawn, and rewrote Frogspawn/Bucket of Tadpole item guidance with shoreline breeding, timed hatch/growth, age-preserving transport, and maturation-biome variants.
- Traced actual biome spawns, source-water support, empty egg loot, and active breeding/laying/conversion callbacks; linked existing Slime/Magma Cube rewards without duplicating their canonical tables.
- Updated Blocks, Mobs, Content Guide, this checkpoint, and October log. Gameplay source remains `3e85592c4c78ebb420302360667a6c230dc0318d`; this is subsequent wiki-branch work.
- Validation: checker and strict build passed on 2,181 pages / 36 indexes; source paths, local links/anchors, and whitespace checked. No breeding, hatch, growth, bucket, variant, or farm gameplay test.

## Seventy-eighth batch: ordinary Monster Spawners

- Added canonical ordinary-spawner behavior and replaced the generic item stub with actual Creative access, empty loot, configuration, and no-default-Pig guidance.
- Traced current player activation, spawn attempts/collision/mob rules, exact-class local counts, conditional delay resets, mining XP gates, and Creative egg-count restoration; Trial Spawners remain a separate system.
- Updated Blocks, Content Guide, this checkpoint, and October log. Gameplay source remains `3e85592c4c78ebb420302360667a6c230dc0318d`; this is subsequent wiki-branch work.
- Validation: independent source review, checker and strict build passed on 2,182 pages / 36 indexes; source paths, local links/anchors, and whitespace checked. No spawning, lighting, mining, egg configuration, redstone, or farm gameplay test.

## Seventy-ninth batch: Gorilla, Gelada, and Dead Bush

- Expanded Gorilla/Gelada behavior and spawn eggs; added canonical Dead Bush and expanded its item with traced biome/loot supply, Shears collection, support, pots, and Gelada breeding use.
- Separated absent Gorilla food/foraging tags from active Gelada breeding/clearing; documented source-only combat-state, old-callback, and mob-griefing limits without claiming fixes.
- Updated Blocks, Mobs, Content Guide, this checkpoint, and October log. Gameplay source remains `3e85592c4c78ebb420302360667a6c230dc0318d`; this is subsequent wiki-branch work.
- Validation: checker and strict build passed on 2,183 pages / 36 indexes; source paths, local links/anchors, and whitespace checked. No feeding, breeding, clearing, grooming, combat, generation, or harvesting gameplay test.

## Eightieth batch: Terracotta and glazed colors

- Added canonical Terracotta and expanded uncolored, all 16 dyed, and all 16 glazed item pages with exact recipes, loot, mining tags, facing, and piston rules.
- Distinguished uncolored-only dye inputs, matching-color glazing, no bundled recolor/unglaze recipes, and direct pushing versus Slime/Honey adhesion.
- Updated Blocks, Pistons, Content Guide, this checkpoint, and October log. Gameplay source remains `3e85592c4c78ebb420302360667a6c230dc0318d`; this is subsequent wiki-branch work.
- Validation: checker and strict build passed on 2,184 pages / 36 indexes; source paths, local links/anchors, and whitespace checked. No crafting, smelting, pattern placement, mining, piston, or sticky-block gameplay test.

## Eighty-first batch: built golem defenders

- Expanded Iron/Snow Golem guides and Carved Pumpkin, Snow Block, and Iron Block support items while preserving existing Pumpkin carving and Iron Ingot conversion ownership.
- Traced exact construction patterns, player-created targeting, conditional villager/Outpost routes, repair, Snow Golem water/biome damage, snow trails, shearing, and death loot.
- Updated Mobs, Content Guide, this checkpoint, and October log. Gameplay source remains `3e85592c4c78ebb420302360667a6c230dc0318d`; this is subsequent wiki-branch work.
- Validation: checker and strict build passed on 2,184 pages / 36 indexes; source paths, local links/anchors, and whitespace checked. No construction, village summoning, combat, repair, weather, shearing, or farm gameplay test.

## Eighty-second batch: Rattlesnake and Jerboa

- Expanded two desert-animal guides and both spawn eggs with active food, target, persistence, effect, and baby-generation paths; preserved existing Roadrunner and remedy pages.
- Distinguished Jerboa befriending/Speed from an invalid breeding-item tag, and Rattlesnake active melee/immunity from its legacy venom-animation callback. Natural-spawn and unique-drop limits remain explicit.
- Updated Content Guide, simplified the growing Mobs route summary, this checkpoint, and October log. Gameplay source remains `3e85592c4c78ebb420302360667a6c230dc0318d`; this is subsequent wiki-branch work.
- Validation: checker and strict build passed on 2,184 pages / 36 indexes; source paths, local links/anchors, and whitespace checked. No spawning, warnings, combat, Poison, feeding, breeding, reward, or drop gameplay test.

## Eighty-third batch: Redstone Torch and Lamp

- Added canonical Torch/Lamp guides and expanded matching item pages with recipes, collection, standing/wall support, physical input/output direction, light values, and switching behavior.
- Independently checked burnout off-transition counting, retained-history pruning, pending-tick deduplication, restart checks, and Lamp delayed-off behavior; examples remain explicitly untested.
- Updated Blocks, Redstone, Content Guide, this checkpoint, and October log. Gameplay source remains `3e85592c4c78ebb420302360667a6c230dc0318d`; this is subsequent wiki-branch work.
- Validation: checker and strict build passed on 2,186 pages / 36 indexes; source paths, local links/anchors, and whitespace checked. No crafting, placement, circuit, pulse, burnout, or lighting gameplay test.

## Eighty-fourth batch: Clay, Bricks, and Flower Pots

- Added Clay/Bricks and Flower Pot guides plus five material/item rewrites with actual Clay drops, smelting/fuel, masonry recipes, Mud conversion, pot interactions, and all 40 registered filled forms.
- Verified the separate support block in the dripstone layout, pot support behavior, integrated plant/tag differences, variant loot exceptions, and potted Eyeblossom time changes; preserved Terracotta and Dead Bush recipe ownership.
- Updated Blocks, Dead Bush cross-link, Content Guide, this checkpoint, and October log. Gameplay source remains `3e85592c4c78ebb420302360667a6c230dc0318d`; this is subsequent wiki-branch work.
- Validation: checker and strict build passed on 2,188 pages / 36 indexes; source paths, local links/anchors, and whitespace checked. No mining, smelting, dripstone, potting, lighting, or growth gameplay test.

## Eighty-fifth batch: Stonecutter workstation

- Added canonical Stonecutter and rewrote its item with exact crafting/mining, single-input consumption, example yields, temporary-menu cleanup, Hopper isolation, and Mason job-site binding.
- Independent review verified current menu/return callers, 9/16 collision and no saw-damage callback, plus all 254 stonecutting recipes for the scoped Limestone-input absence claim.
- Updated Blocks, Clay/Bricks cross-link, Content Guide, this checkpoint, and October log. Gameplay source remains `3e85592c4c78ebb420302360667a6c230dc0318d`; this is subsequent wiki-branch work.
- Validation: checker and strict build passed on 2,189 pages / 36 indexes; source paths, local links/anchors, and whitespace checked. No crafting, cutting, collection, Hopper, collision, or villager gameplay test.

## Eighty-sixth batch: large ocean animals and crystals

- Expanded Cachalot Whale/Giant Squid and their spawn eggs; corrected Prismarine Crystals from a block stub to an actual material/reward guide with checked Guardian loot and Sea Lantern crafting.
- Traced current whale rescue attribution/reward persistence, echo/charge and block breaking, air handling, variant selection, and disabled squid capture/multipart paths without claiming upstream rewards or natural spawning.
- Updated Content Guide, this checkpoint, and October log. Gameplay source remains `3e85592c4c78ebb420302360667a6c230dc0318d`; this is subsequent wiki-branch work.
- Validation: checker and strict build passed on 2,189 pages / 36 indexes; source paths, local links/anchors, and whitespace checked. No spawning, rescue, combat, block damage, breathing, or loot gameplay test.

## Eighty-seventh batch: Horses, Donkeys, and Mules

- Expanded three equine guides and three spawn eggs with actual biome/offspring routes, taming/temper, feeding versus lures, current equipment, riding, cargo, breeding/inheritance, and persistence/drop recovery.
- Preserved Saddle, Transport, and Armor ownership while documenting current Carrot food, 15-slot chested transport, bred-Donkey variation, and Mule infertility despite possible love particles.
- Updated Content Guide, Transport links, this checkpoint, and October log. Gameplay source remains `3e85592c4c78ebb420302360667a6c230dc0318d`; this is subsequent wiki-branch work.
- Validation: checker and strict build passed on 2,189 pages / 36 indexes; independent source review, source paths, local links/anchors, and whitespace checked. No spawning, taming, feeding, breeding, cargo, equipment, or travel gameplay test.

## Eighty-eighth batch: Cactus, Bamboo, and Scaffolding

- Added three canonical block guides and expanded four item pages with actual support, growth/flower and Bone Meal behavior, hazards, recipes, smelting/fuel, and Scaffolding placement/climbing/waterlogging.
- Distinguished generated cactus height from its random-growth cap, Bamboo finished stages from manual stacking, and Scaffolding extension/falling from loss of existing support; preserved String/Stick/Flower Pot/Furnace ownership.
- Updated Blocks, Content Guide, this checkpoint, and October log. Gameplay source remains `3e85592c4c78ebb420302360667a6c230dc0318d`; this is subsequent wiki-branch work.
- Validation: checker and strict build passed on 2,192 pages / 36 indexes; source paths, local links/anchors, and whitespace checked. No farming, placement, support removal, collision, climbing, damage, or fuel gameplay test.

## Eighty-ninth batch: Llama caravans and Camels

- Expanded three transport-animal guides and three spawn eggs with exact food/temper, strength-based cargo, carpet equipment, ten-animal single-anchor caravan bound, Trader Llama timer/mixed breeding, and Camel controls/retention.
- Traced Camel Desert/Dry Midlands and village routes, two-player mounting, dash cooldown, fixed baby-feeding gains, and current merchant-leash/despawn interactions; preserved existing transport/equipment recipe ownership.
- Updated Mobs, Transport, Content Guide, this checkpoint, and October log. Gameplay source remains `3e85592c4c78ebb420302360667a6c230dc0318d`; this is subsequent wiki-branch work.
- Validation: checker and strict build passed on 2,192 pages / 36 indexes; source paths, local links/anchors, and whitespace checked. No taming, feeding, breeding, caravan, merchant retention, dash, or travel gameplay test.

## Ninetieth batch: Hummingbird integration guide

- Expanded Hummingbird and its spawn egg with active flight/variant/offspring paths, persistence, and source-confirmed access and resource limits.
- Independently checked absent bundled food/pollination tags, absent feeder registration and POI stub, all 68 actual biome definitions, and default loot fallback. No upstream crop-farm or feeder claims were imported.
- Updated Content Guide, this checkpoint, and October log. Gameplay source remains `3e85592c4c78ebb420302360667a6c230dc0318d`; this is subsequent wiki-branch work.
- Validation: checker and strict build passed on 2,192 pages / 36 indexes; source paths, local links/anchors, and whitespace checked. No spawning, flight, feeding, breeding, pollination, or drop gameplay test.

### Coverage measurement at the preceding published cutoff

At `987ab0a72e971d40dbcdfc6d4ee5ae52c2a95564`, 513 gameplay paths differ from the underlying gameplay source snapshot. Of those, 467 non-index articles contain pinned/source links and at least 150 whitespace-separated words: 316 items, 66 mobs, 59 blocks, 8 mechanics, 6 structures, 4 game modes, 3 dimensions, 3 effects, and 2 biomes. This is a rough substantial-article proxy, not a claim that every article is complete or runtime-tested. Variant families share canonical behavior guides; remaining untouched stubs and sparse biome/effect/structure coverage remain work.

## Ninety-first batch: underwater plants and dried Kelp

- Added Kelp, Seagrass, and Sea Pickle block guides plus five item rewrites with actual water/support predicates, growth/harvest/Shears, Bone Meal, light, cooking, food, packing, and fuel rules.
- Distinguished age-limited Kelp from a fixed height cap, amount-8 water from source-type placement, tall Seagrass harvest pairing, living-Coral-only Sea Pickle multiplication, and the 4,001-tick Dried Kelp Block fuel value.
- Updated Blocks, Content Guide, this checkpoint, and October log. Gameplay source remains `3e85592c4c78ebb420302360667a6c230dc0318d`; this is subsequent wiki-branch work.
- Validation: checker and strict build passed on 2,195 pages / 36 indexes; source paths, local links/anchors, and whitespace checked. No planting, harvesting, Bone Meal, lighting, cooking, eating, or fuel gameplay test.

## Standing promotion policy and rendering source synchronization

- The later explicit instruction authorizes routine promotion of completed validated wiki work back into master, superseding the initial one-off-only publication boundary. The [continuation procedure](continuation.md) now requires fresh source synchronization, a documentation-only authored difference, guarded nonforced promotion, remote readback, and deployment verification before reporting live publication.
- Source merge `9fc7874be6328503aabf794c757584c4c0d11514` preserves both wiki history and master `4efea14485328238a3a4981162c8a7e8400914b4`. All incoming entries match master; no gameplay or renderer edits were authored by the wiki workflow.
- Preserved and restored the pending underwater-plant batch with exact file hashes. The incoming rendering changes do not alter those reviewed plant, recipe, loot, or fuel rules. Source-author code-test claims are separate from the documentation checks run here.
- Reconciled October status wording and added the verified rendering commit as landed work without inventing issue associations. Completed documentation outcomes are recorded once; deployment status remains separately verified.

## Ninety-second batch: nine Overworld ocean biomes

- Added one comprehensive ocean-family guide covering nine actual biome selections, selected features and aquatic/Drowned candidates, nested structure eligibility, Monument overrides, and Primordial Ocean selection limits.
- Connected the biome index, underwater plant guides, and Content Guide while separating generation entries from guaranteed finds and fish categories from structure monster overrides.
- Integrated source is now `6fe3f1e877707e45ee3159929bb9cd8769d6bda7` after preserving the newer voxel-box and repository-skill work; unchanged evidence stays pinned to its reviewed `3e85592c4c78ebb420302360667a6c230dc0318d` snapshot. This completed batch follows the standing promotion procedure.
- Validation: independent source review, checker and strict build passed on 2,197 pages / 36 indexes; source paths, local links/anchors, and whitespace checked. No generation survey, spawn-rate, resource, structure-search, or diving gameplay test.

## Ninety-third batch: Cats and Ocelots

- Expanded two companion guides and both spawn eggs with actual village/hut and biome routes, coat selectors, taming versus trust, feeding/breeding, owner controls, safe teleport checks, gifts, predators, and persistence.
- Documented current Cat replenishment variant-ordering and Ocelot monster-list dependencies without claiming they were fixed; preserved Raw Cod and other existing food guidance.
- Latest integrated default tip is `2d4b7646eac8561a23f41f873e261d86f0cff5a2`; gameplay source remains `6fe3f1e877707e45ee3159929bb9cd8769d6bda7`; unchanged evidence stays pinned to its reviewed snapshot. This completed batch follows standing promotion and deployment verification.
- Validation: checker and strict build passed on 2,197 pages / 36 indexes; source paths, local links/anchors, and whitespace checked. No spawning, taming, trust, breeding, teleport, gift, or predator gameplay test.

## Ninety-fourth batch: Pickaxes and Shovels

- Added a shared tool guide and expanded all seven Pickaxe/seven Shovel variants, including Copper, with exact recipes, material values/repair, upgrades, tool eligibility, path/campfire actions, wear, and recycling.
- Preserved Mining, Durability, and Smithing ownership; documented retained broken tools, last-use loot ordering, identity-based recycling/fuel, and Netherite item fire resistance without universal-loss immunity claims.
- Latest integrated default source is `2d4b7646eac8561a23f41f873e261d86f0cff5a2`; unchanged material/tool evidence remains pinned to its reviewed `4efea14485328238a3a4981162c8a7e8400914b4` snapshot. This completed batch follows standing promotion and deployment verification.
- Validation: checker and strict build passed on 2,198 pages / 36 indexes; source paths, local links/anchors, and whitespace checked. No crafting, mining, repair, item-use, recycling, or fire gameplay test.

## Ninety-fifth batch: Axes and Hoes

- Added shared Axe/Hoe behavior and all fourteen material variants with exact recipes, combat values, mining eligibility, repairs/upgrades, axe conversions, hoe tilling, crop harvesting, and recycling.
- Corrected shared crop/durability guidance for the retained broken-hoe area-harvest exception, while keeping tilling guarded; documented offhand blocking-item interception and Rooted Dirt's special click/cover rules.
- Latest integrated default source remains `2d4b7646eac8561a23f41f873e261d86f0cff5a2`; unchanged evidence is pinned to gameplay source `6fe3f1e877707e45ee3159929bb9cd8769d6bda7`. This completed batch follows standing promotion and deployment verification.
- Validation: checker and strict build passed on 2,199 pages / 36 indexes; source paths, local links/anchors, and whitespace checked. No crafting, combat, conversions, tilling, crop harvesting, repairs, or recycling gameplay test.

## Ninety-sixth batch: Rabbits, Foxes, and resources

- Expanded Rabbit/Fox and four Rabbit resource/food pages with natural/custom-biome conditions, coats, breeding/trust, crop/berry interactions, carried items, persistence, loot, cooking, crafting, and brewing links.
- Distinguished the Foot's Looting-adjusted chance from count, Fox mouth drops from ordinary loot gates, and fresh-offspring prey-goal initialization from later loaded behavior; preserved Brewing recipe ownership.
- Latest integrated default source remains `2d4b7646eac8561a23f41f873e261d86f0cff5a2`; unchanged evidence is pinned to gameplay source `6fe3f1e877707e45ee3159929bb9cd8769d6bda7`. This completed batch follows standing promotion and deployment verification.
- Validation: checker and strict build passed on 2,199 pages / 36 indexes; source paths, local links/anchors, and whitespace checked. No spawning, breeding, trust, crops, berries, held items, cooking, or loot gameplay test.

## Ninety-seventh batch: alphabetical Blocks and missing core behavior

- Replaced the incomplete Blocks main list with all 1,211 active built-in registrations in displayed-name alphabetical order, retaining exact IDs and related-guide scope labels. Added twelve separate material/use category lists and one category index; every ID appears exactly once in each inventory view.
- The registry inventory is complete at `c87803e75d339e5d643ca812efc70a6def06a401`; placed-block behavior coverage remains substantially incomplete. No item stubs are used as substitutes, no empty variant articles were generated, and no word/file-count completeness score is claimed.
- Added source-grounded core block guidance and corrected the Crafter item's false boat template. Repaired missing source-reference definitions in six Rabbit/Fox articles without changing their behavior claims.
- Updated continuation rules and priority order for the requested alphabetical main directory, separate categories, and registry-based article expansion. Linked the coordinated source/issue review while keeping draft fixes explicitly unmerged.
- Source advanced during validation: fast-forwarded from `c87803e7` to `fb7d6979fb8d9773cfe05f084c6085f35feb885c`, preserving the user-merged Grizzly, Rhino and Ambersol fixes. Updated current checkpoints and remaining Grizzly egg/mining caveats; historical citations remain pinned where unchanged. Final validation and publication follow the standing procedure.
- Validation: required checker and strict build passed after the source fast-forward on 2,214 pages / 37 directory indexes; all 1,211 IDs, alphabetical order, category membership, source line starts, article links/anchors and reference definitions checked. English labels match bundled localization where present; 25 fallback labels are identified. No gameplay test.

## Ninety-eighth batch: stone, deepslate and wood construction

- Expanded Stone and added the Deepslate and wood-construction family guides, with exact variant-to-section navigation rather than duplicate stub articles. Stone/Deepslate covers 51 registered forms, including seven infested forms and Reinforced Deepslate; the wood guide covers 87 forms across twelve materials plus Bamboo Mosaic.
- Verified individual recipes and loot for the covered shapes, shared placed behavior, tool gates, support, waterlogging, fire/fuel and power distinctions. Corrected six core stone/deepslate item pages, including Reinforced Deepslate's misleading food template. Preserved existing Stone URL anchors.
- Kept the main Blocks list alphabetical by displayed name, updated precise family anchors, and sorted each separate category table by displayed name too. Related-guide routes describe scope, not a claim that all block articles are complete.
- Source starts from deployed `2f6c6d4689df9796912eea87cf9def80fc320ee1`, preserving the user-merged fixes. Historical stone evidence is pinned to `6fe3f1e8`; the only changed cited tag files add Ambersol and leave every covered stone-family entry unchanged. Wood evidence is pinned to `fb7d6979` and matches the current source.
- Validation: required checker and strict build passed on 2,216 pages / 37 directory indexes; 1,367 local links/anchors, 573 explicit/shortcut citation uses, all 1,211 directory IDs, and exact 51/87 family maps checked. The directory now offers 493 related-guide routes and leaves 718 explicit article gaps; those are navigation counts, not a completeness score. No gameplay test.

## Ninety-ninth batch: copper construction, decorative stone and ores

- Added structural Copper guidance for 64 exact oxidation/wax variants, 139 recipes, 64 loot tables, 32 wax pairs and 24 oxidation steps. Documented current neighbor-aging, double-slab waxing, interactive-door controls, door tool exception and lightning-cleaning behavior.
- Added Decorative Stone and Tuff guides for 36 exact forms, including distinct polished/chiseled variants, 83 recipes, 36 loot tables and source-wired generation examples. Corrected five core item pages.
- Expanded Ores and Ancient Debris to all nineteen registered ore/debris blocks and replaced their nineteen item stubs with exact collection/processing guidance. Verified tool tags, Fortune formulas, base XP, all loot, 38 ore processing recipes, raw-metal recipes and 21 generation feature routes; preserved prior guide anchors.
- Added precise alphabetical/category links and removed unregistered Copper bars/chains from the category summary. Source remains deployed `1879e5f54378351fd773b9a2c10839eb9259c504`, with unchanged gameplay evidence pinned to `2f6c6d46`. No gameplay tests were run.
- Validation: required checker and strict build passed on 2,219 pages / 37 directory indexes; 1,545 local links/anchors, 675 explicit/shortcut citation uses, all inventory IDs and the exact 64/36/19 family maps checked. Copper evidence covers 249 source files, decorative stone 163 source/resources, and ores 100 cited source files plus independently checked tag/recipe/generation matrices. The directory has 606 related-guide routes and 605 explicit article gaps, not a completion score. No gameplay test.

## Hundredth batch: copper devices, Nether masonry and sandstone

- Added five Copper device/resource guides for the remaining 35 copper-catalog IDs: Bulbs/Torches, Chests/sorting, Lightning Rods, Golem Statues and Raw Copper storage. The exact 101-ID copper catalog now reconciles 64 structural forms, these 35 forms and two ores, with specific guide routes for every entry.
- Added Blackstone/Basalt and Nether Brick guides for 29 masonry forms, including Gilded Blackstone's loot/provocation, active Basalt conversion, barter and structure examples. Corrected six core item pages.
- Added Sandstone guidance for all twenty ordinary/red forms, 38 recipes and twenty loot tables; corrected two item pages and created the previously missing Cut Sandstone Slab item page with an alphabetical Items index link. Distinguished slab strength from full-block strength.
- Added source-verified Piglin interaction notes for Copper Chests and Gold ores. Preserved source-only verification limits, current missing-article labels, and reserved bug-fix pages.
- Source starts at deployed `688be47ebeaa38beb06130efddf19484a8b81580`; relevant gameplay sources remain unchanged from cited `1879e5f5`. No gameplay tests were run.
- Validation: required checker and strict build passed on 2,228 pages / 37 directory indexes; 3,539 local links/anchors and 712 explicit/shortcut reference uses checked, with all 1,211 directory IDs preserved. Copper devices checked 28 recipes/34 unique loot tables, Nether masonry 58 recipes/29 loot tables, and Sandstone 38 recipes/20 loot tables. The directory has 690 related-guide routes and 521 explicit article gaps; navigation scope is not completion. No gameplay test.

## Hundred-and-first batch: tree materials, Quartz/End masonry and fluids

- Added Tree Logs/Roots and Tree Leaves guides covering 58 exact IDs, their complete loot matrix, timber recipes, stripping/axis/fuel differences, leaf drops/decay and Mangrove Propagule harvesting. Corrected four mangrove item pages while preserving Oak/Pewen growth ownership.
- Added Quartz and End Stone/Purpur guides for eighteen exact forms, including mixed crafting inputs versus literal stonecutting inputs, pillar orientation, End/Chorus/End City acquisition examples and Dragon block-immunity distinctions. Corrected five core item pages, including Popped Chorus Fruit's false food description.
- Added Water/Lava and Bubble Column guides for three exact block IDs: source collection/conversion, dimension and flow distinctions, active fluid effects, column creation/support/motion and eye-position breathing rules. Source-based pool/elevator examples remain explicitly untested.
- Updated alphabetical/category family anchors and curated navigation. Source starts at deployed `e87cde38c872d30ae86139bbee181937603af769`; relevant historical evidence at `688be47` is unchanged. No gameplay tests were run.
- Validation: required checker and strict build passed on 2,235 pages / 37 directory indexes; 1,784 local links/anchors, 444 explicit/shortcut references, all 1,211 inventory IDs and exact 58/18/3 family maps checked. Tree sources include 58 loot tables and 23 production recipes; Quartz/End includes 35 recipes and 18 loot tables. Fluid dispatch, source rules and source-line ranges were checked. The directory has 764 related-guide routes and 447 explicit article gaps, not a completion score. No gameplay test.

## Current block coverage audit and priority

The 2026-10-02 audit at `c87803e75d339e5d643ca812efc70a6def06a401` found **1,211 built-in block registrations**, resolving six pumpkin/melon ResourceKey names. The registry has one active write site and is bootstrapped through `Blocks.AIR`; this is a source inventory, not an in-game registry dump. The earlier 62 placed-block guides provide candidate related-guide routes for 344 IDs, leaving 867 without an identified placed-block guide. Those figures describe navigation scope, **not completed behavior coverage**. Family edge cases and partially covered articles still need review.

The user requested an alphabetical Blocks main page like Items, with separate category pages. Keep this as the default layout. Do not use word counts or article-file totals as a completion measure. Each block family needs practical obtaining/mining, placement/use, variants, recipes/loot, and important MattMC caveats before calling its player guidance complete.

## Batch 102: snow, ice, tree growth and imported plants

- Added six canonical guides: Snow/Powder Snow, Ice families, Saplings/Azaleas, Crimson/Warped Fungi, Ancient plants, and Flood Basalt/Fern Thatch. Corrected thirteen existing item articles, including Snowball's former generic building description.
- Reviewed seven snow/ice IDs, thirteen growth-plant IDs, and seven imported-family IDs (Potted Ancient Sapling keeps the existing Flower Pot guide). Exact recipes, loot, tool gates, selected growth configurations, support, weather, melting/freezing and active callbacks were checked. Natural starter gaps and inactive giant Ancient-tree wiring are explicit.
- Preserved the alphabetical main directory and separate category lists. Related-guide routes describe navigation scope, not completed behavior coverage; broad catch-up remains incomplete.
- Reconciled 201 distinct cited source paths against the integrated master snapshot; all remain unchanged from their pins. Incoming mob fixes and the voxel rotation migration are preserved. No gameplay tests were run.
- Validation: required checker and strict build passed on 2,241 pages and 37 indexes. Checked 1,930 local links/anchors, 533 citation uses, all 1,211 alphabetical IDs, seven snow/ice loot tables, thirteen growth-plant loot tables and seven imported-family loot tables. The directory now offers 788 related-guide routes with 423 explicit article gaps; these are navigation counts, not a completeness score.

## Batch 103: Beacon, Conduit, Coral and Nether utility blocks

- Added four canonical block guides: Beacon, Conduit, Coral, and Soul Sand/Soul Soil/Magma. Corrected nine existing item pages with specific acquisition/use and links to the canonical block behavior.
- Reviewed Beacon tiers/payment/effects, Conduit water/frame/range/targeting, all forty living/dead Coral forms and thirty shared/direct loot tables, and three Nether utility blocks' harvesting, movement, conversion, column and damage rules. Geometry and source-defined conditions are distinguished from runtime verification.
- Kept existing Nether Star crafting, glass beam-color, Bubble Column, Nether Wart, Wither and Basalt article ownership. All forty Coral IDs route to actual species anchors; wall fans retain their shared item/loot distinction.
- No source advance was needed from published `be4ac3081f175ffb862938ab118b20e6457110d8` at batch start. The authored diff is documentation only; no gameplay tests were run.
- Validation: required checker and strict build passed on 2,245 pages / 37 indexes. Checked 1,789 local links/anchors, 307 citation uses, 164 pinned source paths unchanged against the integrated head, all 1,211 alphabetical IDs, and exact 2/40/3 block-family maps. The directory has 833 related-guide routes and 378 explicit article gaps, not a completion score.

## Batch 104: all buttons/plates and Amethyst

- Expanded the existing Buttons and Pressure Plates guides to all fifteen buttons and seventeen plates, preserving prior anchors. Checked exact recipes/loot, occupancy and arrow/Wind Charge paths, strength/recheck timing, weighted entity counts, water/piston/fuel differences and Pewen integration limits.
- Added the canonical six-block Amethyst guide with growth, nonrecoverable budding blocks, exact Silk Touch/Fortune yields, support/water states, piston destruction, geode generation, recipes, vibration and active Shard uses. Corrected eight existing item pages across both families.
- Reconciled 151 pinned source references against published head `beaa5747b36af51b001a13ce8b6648319ba6faf5`. Master advanced during publication to `cf8cd5371bd1de61411ae5e7e144aabe3edb1e54`; preserved incoming #781/#767 fixes and guides, then updated the related Biomes/Ore Resources caveats and monthly log. Final validation was repeated after sync. All authored edits are documentation; no gameplay tests were run.
- Validation: required check and strict build passed on 2,246 pages / 37 indexes; 1,796 local links/anchors, 391 citations, all 1,211 alphabetical IDs, all thirty-two control recipe/loot mappings and six Amethyst loot tables were checked. Existing control anchors are retained. The directory has 863 related-guide routes and 348 explicit article gaps, not a completion score.

## Batch 105: Signs, Cake/Candles and cooking-device families

- Added canonical Signs/Hanging Signs, Cake and Candles guides; expanded Furnace with Blast Furnace/Smoker construction, recipes, fuel consumption, placement, collection, automation, comparator and employment differences. Corrected nine existing item pages.
- Reviewed all fifty-two sign forms, thirty-five Cake/candle forms and three cooking devices. Pewen signs are explicitly flagged for the source-predicted block-entity placement failure, unresolved chain recipe and missing wall-hanging loot; they are not advertised as functioning labels.
- Preserved existing food/recipe, Honeycomb, glass, Hopper and Furnace anchors/ownership. Checked exact sign text-face/wax persistence, support differences, Cake eating/candle recovery and current water/ignition paths rather than assuming upstream parity.
- Source checkpoint remains published `60699a119c4728a7bcaf15196f3c839cfcfd69dc` at batch start. All authored paths are documentation; no gameplay tests were run.
- Validation: required check and strict build passed on 2,249 pages / 37 indexes; 1,917 local links/anchors, 447 citation uses, all 1,211 alphabetical IDs and exact 52/35/3 family maps checked. Reconciled 220 newly introduced pinned source references; audited all 113 cooking recipes and the sign/candle recipe/loot matrices. Directory: 948 related-guide routes and 263 explicit article gaps, not a completeness score.

## Batch 106: storage, Cauldrons and Obsidian/respawn utilities

- Added five canonical guides for Barrel, Ender Chest, Cauldrons, Obsidian/Crying Obsidian and Respawn Anchor; corrected six existing item pages. Exact maps cover two storage IDs, four cauldron contents IDs and three Obsidian/anchor IDs.
- Traced shared versus personal inventory ownership, death/dimension persistence, opening/automation/loot, cauldron transactions and dripstone, Obsidian acquisition and the active anchor selection/charge/respawn/fallback/explosion paths. Wet-anchor resistance has an explicit source-qualified anomaly; no water-containment guarantee is made.
- Preserved existing Eye of Ender recipe, Nether/End/Bed, Snow/Water/Bucket and Shulker ownership. Thirteen ruined-portal templates were decoded to verify acquisition evidence; no runtime generation or gameplay test was run.
- Published source checkpoint at batch start: `3c39e8cc456e3c76eac84ae45b12020c4b08a7a7`. All authored paths are documentation.
- Validation: required check and strict build passed on 2,254 pages / 37 indexes; 1,679 local links/anchors, 337 citation uses, 131 pinned source references unchanged, all 1,211 alphabetical IDs and exact 2/4/3 block maps checked. Directory: 957 related-guide routes and 254 explicit article gaps, not a completion score. Source, recipe, loot, persistence and active dispatch audits are complete for this bounded batch; no gameplay tests.

## Batch 107: Banners/Loom, flowers and book furniture

- Added five canonical guides: Banners, Loom, small/tall Flowers, Bookshelves and Lectern. Corrected ten existing item pages. Exact maps cover thirty-three banner/Loom IDs, seventeen flowers and three book-furniture blocks.
- Checked pattern components, preservation/duplication/washing/map behavior, all ten template items and the normal Loom interface limit; flower support, dyes/stew effects, propagation and biome examples; bookshelf storage/removal, enchanting contribution, Lectern reading/page signals and profession assignment.
- Kept existing Shield, Cauldron, Flower Pot, Bee, Enchanting and trading ownership. The guides correct the Chiseled Bookshelf portable-content assumption and distinguish comparator slot/page readings from inventory fullness. No gameplay tests were run.
- Published source checkpoint at batch start: `88d85ee594bc770fa362129690eadb127272dc20`. All authored paths are documentation.
- Validation: required check and strict build passed on 2,259 pages / 37 indexes; 2,034 local links/anchors, 305 citation uses, 239 pinned source references unchanged, all 1,211 alphabetical IDs and exact 33/17/3 family maps checked. All selected recipes, loot, pattern/flower/book tags and active callbacks were audited. Directory: 1010 related-guide routes and 201 explicit article gaps, not a completion score.

## Batch 108: coordinated review, 2026-10-02 05:45 UTC

- Linked the current Pewen sign constructor/placement limitation to verified open [issue #795](https://github.com/HungLo2020/MattMC/issues/795). The ticket does not fix gameplay or subsume the separate recipe, tag and loot caveats.
- Recorded [rotation progress on #770](https://github.com/HungLo2020/MattMC/issues/770#issuecomment-5946344425): eligible packed rotations/reflections moved to Rust, while Java retains coordinates, ownership and compatibility paths. The review inspected source and recorded acceptance; it did not rerun tests or establish complete subsystem migration.
- [Held-light #765 / draft PR #791](https://github.com/HungLo2020/MattMC/issues/765#issuecomment-5946345106) and [Building Wand #720 / draft PR #794](https://github.com/HungLo2020/MattMC/issues/720#issuecomment-5946345950) remain unmerged at this checkpoint. Their proposed behavior is not promoted into the current-master player guides.
- Narrowed the earlier wet-anchor observation: the self-comparison exists, but an ordinary power-5 ray starts at no more than 6.5 and the first Water-resistance deduction is about 30.09. It stops before reaching an off-center query, so the checked shadowing does not establish a different ordinary terrain outcome. Removed the player-facing implication of an active terrain defect; no issue or gameplay fix is claimed. This is static source reasoning, not a runtime containment test. [Anchor calculator](https://github.com/HungLo2020/MattMC/blob/fa2e6ba3e894e8de0b3e432346185d36b5dd4fcc/src/main/java/net/minecraft/world/level/block/RespawnAnchorBlock.java#L144-L159) · [Water resistance](https://github.com/HungLo2020/MattMC/blob/fa2e6ba3e894e8de0b3e432346185d36b5dd4fcc/src/main/java/net/minecraft/world/level/block/Blocks.java#L285-L297) · [Ray budget and order](https://github.com/HungLo2020/MattMC/blob/fa2e6ba3e894e8de0b3e432346185d36b5dd4fcc/src/main/java/net/minecraft/world/level/ServerExplosion.java#L135-L159)
- Source checkpoint: `fa2e6ba3e894e8de0b3e432346185d36b5dd4fcc`; no source merge or gameplay test was needed. Broader vine/moss drafts remain separate from this bounded review.
- Validation: required checker and strict build passed on 2,259 pages / 37 indexes; all four authored paths are documentation, with 184 local links/anchors and 199 citation uses checked. The full alphabetical registry inventory remains unchanged.

## Batch 109: vines, lichen, moss and ceiling plants

- Added four canonical guides: Vines/Glow Berries, Glow Lichen, Moss/Pale Moss, and Hanging Roots/Spore Blossom. Corrected seven existing item pages; exact maps cover eight vine/lichen IDs and seven moss/ceiling-plant IDs.
- Checked tip/body and shared-item distinctions, growth/age/trimming, Bone Meal, tool-dependent drops and detached-segment losses, berry food/light, climbing, water, moss conversion limits and pale-carpet extensions. Source-backed starter examples distinguish natural features from player-grown tree configurations.
- Preserved existing Stone recipe, Bee/Fox/Hunger, Rooted Dirt/Azalea and imported Archaic Vine ownership. The source checkpoint remains published `ae92d4575f1af7752d0f461bbf7bf0843c3cddf7` at batch start. All authored paths are documentation; no gameplay tests were run.

- Validation: documentation check and strict build passed on 2,263 pages and 37 directory indexes; all 1,961 local links/anchors and 150 citation uses in this 17-path batch resolve. The alphabetical directory retains all 1,211 exact registered IDs, with 1,025 related-guide routes and 186 explicit article gaps. These route counts are navigation coverage, not completed article counts. All 132 unique pinned source references were compared unchanged with the current source.

## Next batches, in priority order

1. Maintain the complete alphabetical Blocks directory and category catalogs as source changes; expand genuine article coverage from the now-visible gaps. The initial directory, twelve-terrain guide, Crafter correction, and Rabbit/Fox citation repair are recorded in batch 97.
2. Expand stone/cobblestone, deepslate, tuff, sandstone, Nether/End masonry and broad mineral/ore families with meaningful placed behavior and variant routes.
3. Expand non-Oak/Pewen woods and their actual doors, trapdoors, fences, signs, shelves, and other building behavior; do not assume every imported wood has vanilla recipe/tool wiring.
4. Cover Copper oxidation/waxing and its many building/functional variants, then Beacon/Conduit, Trial Spawner/Vault and remaining functional families.
5. Preserve ready Swords and undead drafts for later; finish them after the immediate Blocks correction. Continue remaining mobs, biomes, effects, structures and gameplay systems without claiming broad catch-up is complete.

## Coordinated source and issue review, 2026-10-02 02:45 UTC

- [Voxel-box checkpoint on #770](https://github.com/HungLo2020/MattMC/issues/770#issuecomment-5944675511) and [renderer/resource/shader checkpoint on #747](https://github.com/HungLo2020/MattMC/issues/747#issuecomment-5944676593) distinguish inspected source and author-recorded tests from independent reruns.
- Draft fixes [#785](https://github.com/HungLo2020/MattMC/pull/785), [#786](https://github.com/HungLo2020/MattMC/pull/786), and [#787](https://github.com/HungLo2020/MattMC/pull/787) address [#778](https://github.com/HungLo2020/MattMC/issues/778#issuecomment-5944683569), [#779](https://github.com/HungLo2020/MattMC/issues/779#issuecomment-5944684597), and [#783](https://github.com/HungLo2020/MattMC/issues/783#issuecomment-5944685794), respectively. They were unmerged at the 02:45 review, then the user merged #787 at 02:56:51 UTC, #786 at 02:57:13, and #785 at 02:57:27. Their twelve incoming paths are now preserved from master `fb7d6979fb8d9773cfe05f084c6085f35feb885c`; issues #783, #779 and #778 are closed. This later merge status supersedes the earlier draft checkpoint. Subsequently, the user merged Crow PR #788 at 04:16:28 UTC, Kangaroo PR #790 at 04:16:54, and Platypus PR #789 at 04:17:21. Master `b6b5f733b316cef6852866924e2f11f12b0c4f5c` contains these fixes and their updated guides; their earlier unmerged status is historical. Live gameplay limits remain as documented in those guides.

## Evidence gaps to carry forward

- Luxtructosaurus is excluded at `b81c01943c9f3254e713c365a1dd633392929cb2`: no active entity/attribute/item registration or implementation was found. [Sauropod base comments](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/living/SauropodBaseEntity.java#L49) explicitly describe it as not added; the [tag constant](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/misc/ACTagRegistry.java#L60) and stub world-data flags do not establish a boss or summoning route. Reassess only if active registration/runtime implementation changes; do not create an upstream-derived placeholder.

- Source review does not establish in-game/runtime correctness.
- Ambersol’s mining-tag omission was fixed on master through #779 / PR #786. Use its updated block guide for the stone-or-better pickaxe requirement. Natural-generation wiring and live gameplay collection remain separate verification gaps.
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
