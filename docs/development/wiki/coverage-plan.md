# Coverage plan and checkpoint

## Branch and source checkpoint

- Working branch: `docs/wiki-expansion`
- Source default branch: `master`
- Last source snapshot integrated: `9bd57e1d0057903f6a9196e592d5e2a087c9248a`
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

## Next batches, in priority order

1. Continue animal resource clusters beyond completed Grizzly/cod/honeycomb, Trilocaris bucket, Subterranodon/egg, Blobfish and Roadrunner coverage. Prioritize practical player interactions and active availability.
2. Extend the completed Limestone/Pewen/Amber family guides with substantive branch/pines/sap behavior and remaining item cross-links. Do not claim the documented integration gaps are repaired.
3. Continue vanilla essentials beyond the completed crafting table/furnace/chest and crafting/smelting guides: wood/ore tool requirements, food, beds, torches, and basic exploration.
4. Work through Alex's Mobs by related biome or interaction, pairing mobs with their drops, breeding items, buckets, and equipment. Use registry IDs and active spawn/config sources to track completeness.
5. Work through integrated Alex's Caves by actually registered biome, mob, block and item clusters; do not assume all upstream biomes exist.
6. Broaden all 17 gameplay categories, including world/dimension discovery, effects/enchantments, structures, redstone, trading, and commands. Select content from the current registries/data, not an external wiki page list.
7. Periodically reconcile registry IDs against page coverage and prioritize missing mechanics or incorrect stubs over raw article count. Record semantic coverage separately from file count.

## Evidence gaps to carry forward

- Source review does not establish in-game/runtime correctness.
- Ambersol has a drop entry and a correct-tool requirement, but no matching mining-tool tag was found; Survival harvesting and natural-generation wiring remain unverified.
- Verify natural spawning through actual biome/config registration rather than treating standalone spawn predicates as proof.
- Existing untouched item/mob stubs are not newly verified by this batch.

## Validation record

- Baseline: `python3 DevUtils/RunWiki.py check` passed on 2,090 pages and 32 indexes.
- Final expanded-tree check: passed on 2,097 pages and 33 indexes.
- Strict MkDocs build: passed; existing pages omitted from explicit navigation are informational, not build failures.
- Final validation was repeated after the source fast-forward and all batch edits.
- Gameplay tests: not run; this batch changes documentation only.

## Mutation ledger policy

Every batch must record its source sync, exact changed paths, published commit, test outcomes, and remaining gaps in the requesting conversation. The commit diff is the authoritative file ledger. Update this checkpoint within the same batch; record the resulting commit link in the conversation (it cannot reference its own future hash here).
