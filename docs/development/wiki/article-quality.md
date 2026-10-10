# Article coverage and quality

Use this page to choose useful documentation work. The [coverage checkpoint](coverage-plan.md)
preserves source integrations and historical batch records; its early inventories
and completed-batch counts describe their dates, not today's article quality.

## Current coverage boundary

The [alphabetical Blocks directory](../../gameplay/blocks/Blocks.md) currently
routes all **1,235 source-inventoried block IDs** to a related guide. The
[registration method](../../gameplay/blocks/catalog/index.md#registration-method-correction)
includes 1,211 direct declarations and 24 helper-created Copper forms. This is
navigation coverage, not proof that every variant's acquisition, state transitions,
loot, interactions or rendering has been reviewed. Category pages supplement
the alphabetical directory; they do not replace it.

Do not recreate missing-page lists from the initial October 1 inventory. Inspect
the linked owner first. A brief item page can be useful when it points directly
to a strong block or family article; a long page can still omit the answer a
player needs. File counts, word counts, links and successful builds are not
completeness measures.

## Questions a useful block article answers

- **Getting it:** Where is it actually generated, crafted or dropped? What tool,
  enchantment or permission is required? Distinguish the inventory item from
  the placed block and explain irreversible conversions
- **Using it:** What supports placement, which states matter, and what triggers
  an interaction, growth, power, light or other effect? Give practical layouts
  and troubleshooting where the active implementation supports them
- **Collecting or replacing it:** What is returned, what is consumed, and which
  contents, state or custom data survive? Explain replanting or rebuilding costs
- **MattMC specifics:** Follow active server dispatch and native definitions as
  well as Java compatibility code. Separate implemented behavior from unused
  callbacks, proposed migrations and source-predicted defects
- **Evidence:** Cite the inspected revision and active resources, give exact
  units, and say which runtime checks were actually performed. An untested
  edge case stays an explicit limit, rather than an inferred guarantee

Not every question needs a separate heading. Write for the reader's task and
link common mechanics instead of repeating them.

## Keep one authoritative explanation

Placed behavior belongs in the block/family guide. The corresponding item page
should answer item-specific acquisition/use questions and link to that owner.
Shared crafting, farming, loot or effect rules belong in their existing topic
pages. Use precise family anchors for meaningful variants; preserve old page
URLs and heading anchors when consolidating.

When two pages disagree, trace the active source and correct both routes in the
same change. Do not simply copy the longer paragraph. For migrated properties,
check native definitions, profiles and policies alongside Java construction and
world callbacks. For recipes and drops, inspect active registry paths and
required tags rather than assuming upstream behavior.

## Current focused work

The October 9 depth review expanded [Farmland](../../gameplay/blocks/Farmland.md)
and [Nether Wart](../../gameplay/blocks/NetherWart.md), then examined
[Lever controls](../../gameplay/blocks/Lever.md) and
[shared redstone power](../../gameplay/redstone/Redstone.md). The selected gaps
were practical collection, environmental behavior, exact harvest rules and
support-side power. Companion item pages keep acquisition summaries and link
to those placed-behavior owners. The already detailed Button and Pressure Plate
family tables did not need duplicate rewrites. These are bounded reviews of
selected core articles, not an exhaustive audit of blocks, items, mobs or
technical mechanics.

Continue by inspecting real missing answers in existing core articles and
contradictory duplicate descriptions. Record the question and source needed
before writing; avoid producing new generic pages solely to increase counts.
The latest source-driven architecture and verification reconciliation belongs
in the relevant development guides, with measured benchmarks distinguished
from ordinary gameplay observations.

## Verification before publication

Use the [documentation maintenance checks](../DOCUMENTATION.md#check-and-preview),
[page evidence rules](page-templates.md) and [safe publication procedure](continuation.md).
Review facts and rendered source destinations as well as local links. Preserve
historical changelog entries and evidence; label superseded conclusions rather
than erasing them. A successful documentation build verifies structure, not
runtime behavior or universal completeness.
