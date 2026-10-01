# Page templates and evidence rules

These are authoring templates, not claims about unfinished content. Follow [documentation maintenance](../DOCUMENTATION.md) and preserve existing filenames and category indexes.

## Common rules

1. Lead with what the player can do and a short description.
2. Verify the live registry ID, implementation, recipes, loot, tags, configuration, and any required world-generation or spawn wiring. `frnsrc/` is upstream reference material, not proof of active MattMC behavior.
3. Write original prose. Do not copy external wiki text or artwork. Link outside references sparingly and record attribution/license if using an authorized asset.
4. Separate code-reviewed facts from in-game verification. Add dated source links pinned to the inspected commit. A source file existing is not evidence that it is wired into play.
5. Omit invented values. State specific unknowns that affect player decisions. A Creative entry is not a Survival acquisition route.
6. Keep each topic canonical: placed behavior on the block page, item-only behavior on the item page, with reciprocal links. Shared recipes should live on one page and be linked elsewhere.
7. Every new directory gets one `index.md`; preserve existing indexes such as `Items.md`, `Mobs.md`, and `Gameplay.md`. Link every immediate page and child index.

## Mob article

- Title and practical description
- Where to find it: confirmed spawn wiring, dimension/biome/light conditions and exclusions; otherwise mark unverified
- Behavior: triggers, targets, player interactions, breeding/taming/riding where implemented
- Drops: loot counts, conditions, Looting, fire handling, and other production
- Stats only when verified, with health points distinguished from hearts
- Known MattMC differences or incomplete integration
- Related mob/item/category pages
- Dated source snapshot and exact supporting paths

## Block article

- Title, role, ID
- Obtaining: generation, recipes, mining tool/tag and drop conditions
- Placement and use: state changes, interactions, power/light behavior
- Verified hardness/resistance/light where useful
- Limitations, related item/category pages, source snapshot

## Item article

- Title and role, ID when useful
- Obtaining: exact recipe/loot/interactions and restrictions
- Use: effects, durability or nutrition only when verified
- Link placed behavior to its block article; shared cooking details to one canonical page
- Related pages and source snapshot

## Review checklist

- All new claims traced to active sources, not just the old stub
- Counts/ticks/units correct; recipe XP not confused with device XP delivery
- No asserted natural spawn solely from an entity spawn predicate
- Loot table availability distinguished from tool conditions and runtime data loading
- Category indexes and incoming links updated in the same batch
- `python3 DevUtils/RunWiki.py check` and `python3 DevUtils/RunWiki.py build` results recorded honestly
