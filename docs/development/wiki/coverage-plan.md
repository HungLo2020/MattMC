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

## Next batches, in priority order

1. Finish the first connected cluster: Grizzly Bear's food/taming pages and spawn egg; Trilocaris bucket/spawn egg; cross-links to existing item pages. Audit runtime availability before claiming Survival access.
2. Expand placed-block coverage for Limestone and Pewen families, Amber, and associated Alex's Caves recipes/world-generation wiring. Use one canonical page per meaningful behavior or family; preserve item-page URLs.
3. Add source-reviewed vanilla essentials: crafting table, furnace, chest, wood/ore tool requirements and food. Upgrade related crafting/smelting category guides with actual recipes.
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
