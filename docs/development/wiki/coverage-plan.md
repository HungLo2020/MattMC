# Coverage plan and checkpoint

For current review priorities, start with [article coverage and quality](article-quality.md).
This page preserves historical inventories, source synchronizations and batch evidence.
Early missing-page lists and page counts are not a current completeness assessment.

## Current inventory-browser correction

At source `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`, the browser catalog is visible in Survival but the live server protocol skips ordinary Survival insertion requests before the permissive handler. Both dedicated and integrated transports use that gate. Earlier batch descriptions below that claimed Survival insertion are superseded by [the corrected shared guide](../../gameplay/mechanics/InventoryBrowser.md#mode-and-permission-limits). Historical work and links remain preserved; this is a documentation correction, not a gameplay fix. Batches 202–203 reconciled the audited item and non-item acquisition claims under one publisher. New and unreviewed pages still require the same active protocol-gate checks.


## Branch and source checkpoint

- Working branch: `docs/wiki-expansion`
- Source default branch: `master`
- Latest non-wiki default-branch snapshot integrated: `f86206767dadde696adfed4e04c5ee97cd0d0885`; older gameplay citations remain pinned to their reviewed snapshots where the relevant behavior is unchanged.
- Latest source sync: fast-forwarded from promoted wiki cutoff `f7d5f7dd24e75e4ca2e33e3962e305dfce526794` through terrain-light source `ee34f2ad99921848d8fc5d63da93eb6c583786c4` to test/tooling follow-up `f86206767dadde696adfed4e04c5ee97cd0d0885`, preserving two incoming commits and all 54 changed paths, including fourteen developer documents. Seven pending documents survived the first sync and twelve survived the second byte-for-byte before reconciliation. The relevant redstone gameplay source was unchanged; incoming native implementation, tests, build staging and author evidence remain intact. Repository instructions and deployment workflows were unchanged; required documentation validation uses the newer parallel checker.
- Previous source sync: recovered a fresh checkout and fast-forwarded from promoted wiki cutoff `ba8d4a9373d1562d97ef83f3c07347702f93098c` to `4246f4e7bfc1f3ab7862272ebba5f1f38aa16953`, preserving two source commits and all 57 changed paths, including thirteen developer-document paths. Native live light generations, GUI ABI 78 and the ordinary-gameplay performance harness remain intact. The renewed article expansion is separate from source runtime implementation and does not infer ordinary-play parity from settled benchmark gates. Instructions and workflows were unchanged.
- Previous source sync: recovered a fresh checkout and fast-forwarded from promoted wiki cutoff `b03eb9b257275159b44193edeb079b2eadf0c32b` to `642943247003d7d8d756a65180f0872b088c13f0`, preserving four incoming commits and all 97 changed paths, including twelve developer-document paths. The series adds native section counters and DH clouds, bounded column/debug/readiness fixes, and GUI/world/hand item inputs. Repository instructions and workflows were unchanged; source, incoming documentation and historical evidence remain intact.
- Previous source sync: recovered a fresh checkout after execution became available and fast-forwarded from the already synchronized wiki tip `9a301e274f932a273d842efedd308909e3876773` to `a908f78cd909200f5f4f4424b124072cef0a17f6`. Earlier source-only branch fast-forwards to `6324cd1d`, `c9e2a71d` and `9a301e27` preserved deployed default-branch history while authored corrections were held for required validation. Across the full interval since promoted wiki cutoff `2e62eed926124734bd4a9da157057d1be3da3c2e`, six source commits and all 200 changed paths, including 29 wiki-document paths, remain intact. Instructions and workflows were unchanged. The resumed reconciliation uses current stage/live/snapshot owners rather than replaying obsolete Java-storage wording.
- Previous source sync: fast-forwarded from promoted wiki cutoff `3e1b2a94cd40ce1eb7ef293e3f69002006d6fe16` to `87046367cdf0a4a427f10066a9010dd6d39fd422`, preserving three source commits and all eighty changed paths, including sixteen developer-document paths. The series adds native sound/offset and block-family configuration, a parent-retention repair and Frozen RunDev launch options. Instructions and workflows were unchanged; incoming source and author records remain intact.
- Previous source sync: the wiki branch first fast-forwarded from `7fdf1ebce59a83400e0ebdcd3882ad5c4bdb3505` to already-deployed master `df6c6dc77d0e382b13fa8fcd62317f512ebf0b94` while execution was unavailable; that source-only sync authored no commit or master update. After execution recovered, a fresh checkout fast-forwarded from that tip through `1b1837931a9b25fa12b655e440c6ff77cdd5887d` to `d0141162d81eee184fa99f0b7a9411d401c306b4`, preserving both pending maintenance files byte-for-byte during the second sync. Across the held interval, six source commits and all 243 changed paths, including fifteen developer-document paths, are preserved. The pending reconciliation was rebuilt against native block definitions and physical profiles rather than applying stale Java-ownership wording. Instructions and workflows were unchanged.
- Previous source sync: fast-forwarded from promoted wiki cutoff `1216f0604255972270a6a42740b856e741a42621` to `97e3092269ed29854c8175a480a819fb1896c311`, preserving one incoming commit and all fourteen changed paths, including five developer-document paths and the source-owned whole-project migration plan. The delta changes acceptance/retention tooling and documentation, not game runtime implementation. Instructions and workflows were unchanged; incoming milestone guidance, historical source records and navigation are preserved.
- Previous source sync: fast-forwarded from promoted wiki cutoff `bf8a557dd60f044148afed764fe1aa97758763cb` through `72b8cea2e63a7186830e6c647a657745aa771969` to `697b0a3c6200151830a565c73aaee88d323eb484`, preserving twelve incoming commits and all 36 changed paths, including four rendering-document owners. The series adds native terrain publication, batched DH lifecycle checks, lease repairs, shared geometry pages and a validation driver with updated author measurements. Repository instructions and workflows were unchanged; incoming source and evidence remain intact.
- Previous source sync: fast-forwarded from promoted wiki cutoff `f75eea5b1956643fe8165813749276d6f2c4430b` to `f13239e10d0f66d244c4311c091d0d60819fb391`, preserving eight incoming commits and all 47 changed paths, including four developer-document owners. The series adds native terrain-vertex staging, DH column/payload/visibility ownership and ABI 72 retained generic groups, with new author-recorded comparison windows. Instructions and workflows were unchanged. Incoming source and evidence remain intact; current guidance below distinguishes copying, compatibility, lifecycle and acceptance boundaries.
- Previous source sync: fast-forwarded from already-published proposal tip `5457b41ae29059c1f26b439d0d537476a439b9df` through `7a6009f84d966263293f864933f4da06b1823dfa` to `4740f8fabffd878286850083e2d86ff733c9121e`, preserving twenty-six incoming commits and all 199 changed paths, including 19 developer-document paths. The second fast-forward preserved the pending checkpoint bytes exactly. Shared block-registry consumers, native terrain bookkeeping/assembly, ABI 71 rigs, deferred resource retirement and lifecycle handling now require current implementation guidance alongside the remaining proposal. Instructions and workflows were unchanged; incoming source, launch/test settings and author evidence remain intact.
- Previous branch-only sync: advanced `docs/wiki-expansion` from `c85e1fa07eff208cc7b8f42b46060048a8b5f539` to `5457b41ae29059c1f26b439d0d537476a439b9df`, its direct descendant, after verifying that exact proposal commit's existing successful Wiki Pages build/deployment. No new authored commit or master write occurred. Factual proposal corrections were held while the execution environment was unavailable; they are reconciled against the newer implemented scope below rather than replayed as stale proposal-only prose.
- Previous source sync: fast-forwarded from promoted wiki cutoff `48aa01ebcee2842838054fbf356e8ef876fdf56f` to `20e157cab7962140b30b83f40374cdeb1e6a8b19`, preserving thirty-seven incoming commits and all 66 changed paths, including four rendering developer owners. The series changes GUI geometry reuse, terrain/block-entity selection, DH packing/batching, profiling/configuration caches and Frozen-baseline provisioning. Instructions, workflows and gameplay documents were unchanged. Incoming code and author results remain intact; current ownership and comparison limits are reconciled below.
- Previous source sync: fast-forwarded from promoted wiki cutoff `4601c9fe4fd5348f80ebff7b758dd996bf4f79a2` to `8db0fd82c52acd01cd6b787e5775d64fca8c3d25`, preserving all eight incoming paths, including the storage developer owner. The commit adds rejected-compaction cleanup, encoded tape-failure reporting and focused regressions. Instructions, workflows and gameplay documents were unchanged. The checkpoint below distinguishes these landed repairs from the wider unresolved save-future and integration acceptance scope.
- Previous source sync: fast-forwarded from promoted wiki cutoff `87dd68039d302ae28a337d7db8e1f52c3e2ae41e` to `313e7a8a82a34dc915c4924a78da77c720af2f7e`, preserving four incoming commits and all 59 paths, including six developer-document paths. The series adds camera-pass entity culling, native graph terrain/caster records, late direct-route DH particle composition, current-version section loading and eligible biome searches. Instructions, workflows and gameplay documents were unchanged; source-owned documentation and measurements are preserved with current/historical and acceptance qualifications below.
- Previous source sync: fast-forwarded from promoted wiki cutoff `4c6b0fbad521f520ffed9626423a1580281b153e` to `121ad13c84e45555c34814d54a8199194b37f39c`, preserving twenty incoming commits and all 71 changed paths, including eight developer-document paths. The series adds queued native frames with caller-side decoding, bounded rendering optimizations and native chunk-section NBT tape encoding. Repository instructions, workflows and gameplay documents were unchanged. Current owners distinguish eligible and compatibility routes, author-recorded timings and independently inspected evidence.
- Previous source sync: fast-forwarded from promoted wiki cutoff `048e840c5ab96abcb4c7ee9c0ca7cfeb2770c13f` to `5c02fd8215f4c1dde624dbe3d21a476d38b16708`, preserving all 55 incoming paths, including 11 developer-document paths. Rust gains block/sky propagation queues and sky seeding, eligible NOISE aquifer-material preparation and additional SURFACE condition answers. Repository instructions, workflows and gameplay documents were unchanged. The affected developer owners below preserve author evidence while clarifying compatibility and verification boundaries.
- Previous source sync: fast-forwarded from promoted wiki cutoff `04aade166d5d5ba3579b3a016e9f56e9b2c4a0dc` to `54611cfc25dbdf60ae4b11dc17557d2bec77469d`, preserving seven incoming commits and all 254 changed paths, including 27 developer-document paths. The series moves bounded worldgen stages and rendering/visibility work into Rust. Incoming source, tests, build tooling and author-recorded histories remain intact; repository instructions, workflows and gameplay documents were unchanged. Current ownership and acceptance qualifications are reconciled below without treating author reports as independently rerun gameplay evidence.
- Previous source sync: fast-forwarded from promoted wiki cutoff `0e8adc1be9d56dd568d69e0e11d0ce5fd363ac47` to `da1109de6fe84592bf75e32cffef0cb5506d2651`, preserving all twenty-two incoming paths, including four developer-document paths. The source-owned router fills eligible interpolation slices; Java retains graph compilation, cache/cell work, output copying and surrounding orchestration, with separate router and per-slice compatibility paths. Gameplay documents, repository instructions and workflows were unchanged in this source commit. The review below qualifies acceptance evidence without claiming runtime suites or benchmarks were rerun.
- Previous source sync: fast-forwarded from promoted wiki cutoff `204d482d5538d100ed51cf0113942ab6ccbf48e5` to `858476969d6500b2a9e26d10c5d8c5967222ccfe`, preserving all thirty-one incoming paths, including five developer-document paths. The source-owned native NOISE fill and random-factory migration is preserved without treating its author-reported tests or benchmarks as independently rerun. Pending player-guide evidence was compared with the new source; light initialization and section tick-counter installation received additional overlap review. No gameplay document changed in the incoming commit. The prior `5218ac875eda9f2c4151ff1a97795f20f5f356cf` loading/POI synchronization is recorded in batch 334 below.
- Previous source sync: fast-forwarded from promoted wiki cutoff `813ab4fb1b6908325e2eb306bcc0f2073ddb19fa` to `9bafc14d2e2943dcfe8a37e9e81dc001386b88bb`, preserving all thirteen staged batch 333 documents in place with their staged state and exact bytes. The incoming simulation-distance migration changes 22 logical paths (27 old/new paths when five renames are expanded), including five development-document paths and the new [simulation-distance owner](../world/chunk-loading/RUST-SIMULATION-DISTANCE.md). The native `player_distance` module becomes shared `chunk_distance`; incoming source, tests and authored documentation remain intact. All 79 gameplay/resource files cited by the eleven firearm guides were verified unchanged. This synchronization does not claim new Java/Rust tests, benchmarks or gameplay runs by the documentation review; post-sync documentation validation replaces the earlier aggregate result.
- Previous source sync: fast-forwarded from `126a96037f8bc997cbee0bd5469538cf6fee4d01` to `cc140840a21e5c6c932c23abf34124418d6506b0`, preserving 139 incoming paths: renderer follow-up `37817e1` (118), the source-owned prompt update `96659c1` (one), and player-distance migration `cc14084` (20). The batch 325 item/Firearms documentation was restored from saved bytes and subsequently [published at `dc2f87a`](https://github.com/HungLo2020/MattMC/commit/dc2f87a0a89d05d03e97cec2ddd1d92167985560); its game-item/projectile sources were unchanged by these commits. Source-authored rendering procedures, `PROGRESS.md`, historical failed-run verdicts and benchmark results remain preserved. Current rendering evidence is summarized in the [Goal 5 checkpoint](../rendering/GOAL-5-STATUS.md); player-distance ownership and evidence limits are in the [chunk-loading guide](../world/chunk-loading/RUST-PLAYER-DISTANCE.md). The tracker review independently passed 79 isolated Python rendering-tool tests at `37817e1`; the documentation reconciliation did not rerun Java/Rust/native tests, live clients, server workloads or benchmarks. Goal 5 remains incomplete.
- Previous source sync: fast-forwarded from promoted cutoff `112acbd1217aedca702f1c864840b91680132e2e` to `78e8e0423084f010bb47e36132550619b37644c2`, preserving all six incoming Rust GUI/source-frame implementation and test paths. The six pending wiki documents were restored from exact saved bytes, and all 41 frozen Villager evidence files remained unchanged. Workflows and repository instructions were unchanged. Added source-level regression coverage was inspected, not rerun; this bounded GUI-target repair does not establish general rendering parity.
- Previous source sync: fast-forwarded from promoted cutoff `02c7761f3509ff0ca3563d93247c2102cae71035` to `f5473e41dc4af8ced756db517fada27288df07a3`, preserving all 24 incoming priority-queue source, test and documentation paths, including the updated source-owned Rust migration prompt. All 27 pending wiki paths were restored from their saved candidate bytes and all 334 frozen Copper evidence files remained unchanged. Workflows were unchanged. The incoming queue tests and performance measurements were not rerun by the documentation task; no uniform caller speedup is asserted.
- Previous source sync: fast-forwarded the clean wiki checkout from `37d7bf58cd5abdc1d040c632be893618461269a1` to `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`, preserving all 254 incoming Goal 5 rendering, tooling, test and documentation paths. All 36 isolated TaCZ draft guards still matched; its cited source files were unchanged. Instructions and workflows were unchanged. Goal 5 is explicitly incomplete. The broader rendering-documentation reconciliation completed in batch 208 and is summarized in the [Goal 5 checkpoint](../rendering/GOAL-5-STATUS.md); incoming rendering tests and capture claims are not treated as independently rerun.
- Previous source sync: fast-forwarded from `5d497ab8b063b90e0e3dd68de5c83879b47913b5` to `cfa7057b6fe2b8dfa84e93f21932be2602eff749`, preserving all eleven pending Bow/crosslink documents byte-for-byte and all 61 incoming voxel-query, bulk long-array codec, test and developer-documentation paths. Instructions and workflows were unchanged; cited Bow/admission source files remain pinned to their reviewed unchanged content. The incoming gameplay/native tests were not rerun by the documentation task.
- Previous source sync: fast-forwarded from `beaa5747b36af51b001a13ce8b6648319ba6faf5` to `cf8cd5371bd1de61411ae5e7e144aabe3edb1e54`, preserving all eighteen pending docs and all 23 incoming ore-target/Enderman fix paths. User-merged PRs #792 and #793 are integrated; PR #791 remained unmerged at this checkpoint. Instructions and workflows were unchanged.
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

## Block inventory method correction

The current inspected built-in path has **1,235 unique registered block IDs: 1,211 direct static Block fields plus 24 WeatheringCopperBlocks helper forms**. Earlier 1,211-ID directory/validation counts below describe the direct-field subset, not the full effective source inventory. Copper Bars, Copper Chain and Copper Lantern each have eight registered oxidation/wax forms that already existed in the original `c87803e7` snapshot. Batch 147 supersedes the earlier completeness claim; it does not add game content. Existing 1,211 related-guide routes are preserved. Batch 148 adds source-reviewed Copper Bars, Chains and Lanterns family routes for the newly exposed 24 forms; route coverage is not complete variant-behavior certification. See [the registration method and source chain](../../gameplay/blocks/catalog/index.md#registration-method-correction).

## Initial coverage inventory

At the initial **October 1, 2026** source snapshot, `docs/` contained **2,090 Markdown pages** and **32 directory indexes**. These are file counts, not a completeness score.

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

The bounded reconciliation in batch 232 adds seven verified outcomes to [August](../../changelog/changelog/8.2026.md) and [September](../../changelog/changelog/9.2026.md), with both month indexes updated. January's title was verified against its introducing diff and first master landing, then corrected without moving its content or breaking its previous heading URL. This closes those missing-file and label gaps; it does not exhaustively summarize the two months. Remaining commits, including month-boundary cases, still require source and dating review.

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

- Replaced the incomplete Blocks main list with the then-inventoried 1,211 direct-field registrations in displayed-name alphabetical order, retaining exact IDs and related-guide scope labels. Added twelve separate material/use category lists and one category index; every ID appears exactly once in each inventory view.
- The original registry-completeness claim at `c87803e75d339e5d643ca812efc70a6def06a401` is superseded by batch 147: the direct-field scan missed 24 helper registrations already present there. Placed-block behavior coverage remains substantially incomplete. No item stubs are used as substitutes, no empty variant articles were generated, and no word/file-count completeness score is claimed.
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

The 2026-10-02 audit at `c87803e75d339e5d643ca812efc70a6def06a401` found **1,211 direct-field block registrations**, resolving six pumpkin/melon ResourceKey names; it missed 24 helper-created IDs, as corrected in batch 147. The registry has one active write site and is bootstrapped through `Blocks.AIR`; this is a source inventory, not an in-game registry dump. The earlier 62 placed-block guides provide candidate related-guide routes for 344 IDs, leaving 867 without an identified placed-block guide. Those figures describe navigation scope, **not completed behavior coverage**. Family edge cases and partially covered articles still need review.

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

## Batch 110: Nether ground and steady lighting

- Added three source-reviewed placed-block guides covering seven Nylium/root/sprout/cap IDs and nine full-light/Lantern IDs. Corrected seven related inventory articles. Preserved canonical fungus, crop, vine, Frog and brewing ownership.
- Nylium coverage distinguishes correct-tool Silk Touch recovery, cover conversion, targeted Netherrack renewal, weighted Bone Meal vegetation and active natural-surface routes. Wart Blocks are not reversible brewing-crop storage.
- Lighting coverage distinguishes Glowstone/Sea Lantern Fortune caps, three verified Frog outputs, Carmine Survival acquisition still unverified, and MattMC Lantern hand recovery versus pickaxe speed. No gameplay test or new acquisition route is inferred.
- Source checkpoint: published `1d7b3bbf88ccad2196a6de69317d934e7c3aeced`; all authored paths are documentation.

- Validation: required checker and strict build passed on 2,266 pages / 37 indexes. The 17 authored documentation paths resolve 2,106 local links/anchors and 158 citation uses. All 118 distinct pinned source references remain unchanged. The 1,211-ID alphabetical directory now has 1,041 related-guide routes and 170 article gaps; these are navigation counts, not completeness.

## Batch 111: Composter, Stonecutter and wooden Shelves

- Added a canonical Composter guide with all 115 explicitly accepted ingredients and five chance groups, first-item guarantee, scheduled maturation, level-dependent loot, automation, comparator and Farmer behavior. Expanded existing Stonecutter coverage while preserving anchors and material-recipe ownership; corrected both inventory guides.
- Reviewed two crafting recipes, two loot tables and all 254 bundled stonecutting recipes. Source audit distinguishes private menu input from placed storage and follows current server selection, consumption and cleanup paths.
- The Composter output-Hopper rollback trace predicts a potential lost batch only when extraction is attempted without compatible Bone Meal capacity. This is explicitly source-only, not a gameplay reproduction or fixed issue; the separate exact audit was forwarded for independent tracker triage.
- Source checkpoint: `96e5604a6abaec697de2004b1ba9775e303bfba7`; no source changes since the drafts were pinned. Authored changes are documentation only.

- Added the twelve-variant Shelf family with all twelve recipes and loot tables, three representative item corrections, front-only full-stack exchange, powered hotbar groups, rear occupancy encoding, automation and ordinary contents spilling versus data-copy items. Preserved Chiseled Bookshelf ownership; source-derived behavior is not gameplay-tested.
- Redirected three existing plant-guide composting links to the new canonical placed-block guide.

- Validation: required checker and strict build passed on 2,268 pages / 37 indexes. All 17 authored paths are documentation; 2,073 local links/anchors and 296 citation uses resolve. The eight new or rewritten article files have 110 pinned source references checked unchanged. The alphabetical directory retains 1,211 IDs, with 1,054 related-guide routes and 157 explicit article gaps, not a completed-content score.

## Batch 112: ordinary Mushrooms and map/Fletcher workstations

- Added the five-ID Mushroom family and Fletching Table placed guides, expanded existing Cartography Table guidance, and corrected five inventory pages. Preserved existing anchors and canonical Map, food, Flower Pot and Nether Fungus ownership.
- Mushroom review covers support/light, density-limited spreading, Bone Meal and current huge-growth clearance differences, exact cap/stem loot, face persistence, twenty recipes and selected active biome/Mooshroom acquisition chains.
- Workstation review distinguishes the active Cartography menu, map-ID operations and cleanup from Fletching Table's implemented job-site role and absent player menu. Existing source-qualified crafting/table map differences remain explicit; no untested duplication or loss is asserted.
- Source checkpoint: `7c92eedd610d1a7a6fdb5453731043b9ab323307`; all authored changes are documentation, with no gameplay testing claimed.

- Validation: required checker and strict build passed on 2,270 pages / 37 indexes. All 14 authored paths are documentation; 1,875 local links/anchors and 92 citation uses resolve. The eight article files have 100 pinned source references checked unchanged. The full 1,211-ID alphabetical directory has 1,060 related-guide routes and 151 explicit article gaps; these counts do not establish article completeness.

## Batch 113: platforms, Campfires and signal inputs

- Added five placed guides covering Dripleaves/Lily Pad, Campfires, Target and Daylight Detector, with seven inventory corrections and exact routes for eight registered IDs. Preserved Kelp/Seagrass, Bee housing, food, redstone-component and dimension ownership.
- Plant review distinguishes paired-half recovery, source-water/ground rules, the Pale Moss conversion pitfall, growth and collapse, fresh versus pending tilt timers, and projectile/power exceptions. Lily Pad covers boat breakage and verified starting supplies.
- Campfire review checks all twelve type-selected cooking recipes, four parallel slots and cooling, ordinary versus Silk Touch loot, smoke reach, damage and active lighting/water handlers. Recipe folder names are not treated as recipe types.
- Target review distinguishes live pulse strength from credited later hits. Daylight review traces local skylight, inversion, time/weather and actual dimension types, including the End skylight definition and manual updates without an automatic ticker. Numeric and circuit examples are source-derived, not gameplay measurements.
- Source checkpoint: `053cd852a8609f4002234ce0d445d3a345b551ae`; all authored changes are documentation and no gameplay tests were run.

- Validation: required checker and strict build passed on 2,275 pages / 37 indexes. All 19 authored paths are documentation; 1,940 local links/anchors and 261 citation uses resolve. All 153 distinct pinned source references in the twelve articles were checked unchanged. The 1,211-ID alphabetical directory now offers 1,068 related-guide routes and retains 143 explicit article gaps; family links are not completed-article claims.

## Batch 114: Trial encounters, Vaults and shrubs

- Added Trial Spawner, Vault and shrub family guides, with seven inventory corrections. Trial Spawner and Vault link directly to one another; ordinary Monster Spawner and Dead Bush retain their canonical ownership. Exact new routes cover six previously unlinked block IDs.
- Trial review follows activation, registered-player quotas, tracked mob clearance, one encounter-wide reward-table selection, per-ejection contents, cooldown and ominous transitions. Representative real chamber routes and all 28 bundled configurations were audited; no runtime encounter or loot-frequency test is claimed.
- Vault review distinguishes template key/reward configuration from appearance, component-sensitive key use, delayed loose-item ejection, preview versus reward rolls, and saved per-Vault player history with its 128-entry limit. The 7.5% Heavy Core example is derived from the checked ominous loot weights, not measured play or a guarantee.
- Shrub review preserves Dead Bush while covering four new plants, support/water, Bone Meal, harvesting, fuel/compost, and Firefly light/particles/sound. Corrected the two dry-grass item IDs and names, preserved old filenames/anchors, and moved their Items directory links into their correct S/T alphabetical positions.
- Source checkpoint: `8d065c943710a8d4e3d609b0406dda95ed62cc63`; all authored changes are documentation only.

- Validation: required checker and strict build passed on 2,278 pages / 37 indexes. All 18 authored paths are documentation; 3,797 local links/anchors and 256 citation uses resolve. All 145 distinct pinned article-source references match the integrated source. Both corrected item labels occur once in their S/T sections with old paths retained. The 1,211-ID alphabetical Blocks directory has 1,074 related-guide routes and 137 explicit article gaps, not a completeness score.

## Batch 115: resource storage, pottery and sound blocks

- Added four placed guides covering ten resource-block IDs, Decorated Pot, Bell and Jukebox; corrected ten related inventory pages. Existing OreResources and Vault now link the new canonical storage/pot guides.
- Resource review checks every harvest tier, full loot and pack/unpack recipe, default Coal fuel and relevant Beacon/golem/Piglin/Redstone paths. Copper and broader device mechanics retain their existing owners.
- Pottery review distinguishes four face ingredients from the stored stack, normal recovery from shattering and spilled contents, source-water placement from bucket insertion, and the current 25 accepted sherds versus 23 mapped patterns. Corrected unsupported Dinosaur/Footprint sherd promises; their ingredient identities remain retained despite plain-face fallback, and Survival archaeology acquisition remains unverified. No code fix is claimed.
- Bell review separates repeated ringing from the entity-search cache and traces actual Villager/Raider response. Jukebox review covers all 21 song records, padded logical playback, disc-specific comparator values, ordinary Hopper locking, saved timer versus audio restart and separate disc recovery.
- Source checkpoint: `fc47f0654e74d1e5bd3b7f3522cc1e8117694418`; all authored changes are documentation. No gameplay, listening, rendered-pattern or timing tests were run.

- Validation: required checker and strict build passed on 2,282 pages / 37 indexes. All 23 authored paths are documentation; 2,045 local links/anchors and 720 citation uses resolve. Integrated citation QA caught and corrected two undefined Pot shorthand references before publication. All 176 distinct pinned source references in the fourteen article files match the integrated source. The 1,211-ID alphabetical directory has 1,087 related-guide routes and 124 article gaps, not a completion score.

## Batch 116: source-citation rendering repair

- Repaired 205 context-reviewed citation collisions across 123 lines in 45 gameplay pages. Whitespace-separated shortcut citations were being interpreted as a single full reference, showing the first source label but linking the second source.
- The repair only appends 410 empty reference brackets, restoring 205 intended anchors while preserving prose, URL definitions, whitespace and legitimate full references. Each citation-only page transformation was compared byte-for-byte with the expected edit and its complete rendered HTML matched the independently audited result. The later coordinated-review update adds the separately verified #796 link to Composter.
- The source audit covered all 2,223 gameplay Markdown files at `a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd`, including 545 files with reference definitions. No undefined full/collapsed/shortcut citations were found at that snapshot; this repair addresses wrong/missing rendered targets rather than missing definitions.
- Continuing authoring rule: use explicit full or collapsed source references, or separate independent shortcuts clearly. Validate all three reference forms and actual rendered labels/targets; a strict MkDocs build alone does not detect every reference-syntax problem. This is documentation-only maintenance, with no gameplay changes or tests.

- Validation: all 45 citation-only transformations exactly match the independently verified rendered HTML; all 410 inserted collapsed references resolve to their own definitions, restoring exactly 205 anchors. Required checker, strict build and diff checks passed on 2,282 pages / 37 indexes; 930 local links/anchors were checked across the 47 authored documentation paths. Registry inventory, article routes and gameplay descriptions are unchanged.

## Coordinated source and issue review, 2026-10-02 08:45 UTC

- Source remains unchanged at the published documentation head `a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd`. The review added [Composter issue #796](https://github.com/HungLo2020/MattMC/issues/796) after independent source verification of failed-Hopper-transfer rollback; the existing player warning now links it. This is an open, source-predicted defect, not a runtime reproduction or implemented fix.
- Pewen sign issue #795 remains open; held-light PR #791 and Building Wand PR #794 remain unmerged drafts at the review checkpoint. No new runtime commits, migration completion or issue closures were recorded. Dinosaur/Footprint pattern and acquisition limitations remain documented, with separate integration triage deferred; no tested visual defect or fix is claimed.
- Batch116 publication was delayed by a stalled tree-object request. Read-only reconciliation confirmed both refs still at the prior head and no116 commit/ref request had been sent. The citation repair and this bounded review are validated together before promotion.

## Batch 117: Prismarine, heads, sponges and navigation markers

- Added four canonical guides and ten item corrections, with exact routes for ten Prismarine forms, fourteen head/skull forms and three Sponge/Lodestone blocks. Existing Conduit, Guardian, End City, Wither, Compass and fluid owners remain linked.
- Prismarine review checks all ten registrations/loot tables, full crafting/stonecutting conversions, actual ingredient distinctions, water/shape/piston behavior and active Monument supplies. Heads review distinguishes seven inventory items from fourteen placed forms, real charged-Creeper and structure routes, retained profile/sound data, animation and the current wall-head Note Block playback limitation.
- Sponge review counts the traversal origin separately from its maximum 64 admitted water positions, traces supported removal paths and tool-free plant loot, and distinguishes Furnace/bucket recovery from ultrawarm drying. Lodestone review verifies the Iron recipe, assigned chest-loot routes, tool gate and stored Compass coordinates/point-of-interest checks.
- Source checkpoint: `cf1c134b3f9ff634490e448fe26c335b90f82227`; all authored changes are documentation. These are source-derived mechanics, with no gameplay, listening or generated-world testing claimed.

- Validation: required checker and strict build passed on 2,286 pages / 37 indexes. All 21 authored paths are documentation; 2,164 local links/anchors and 453 tracked reference uses resolve. Rendered-prose/reference checks found no ambiguity or unresolved bracket candidates on the changed pages. All 171 distinct pinned article-source references match the integrated source. The alphabetical directory retains 1,211 IDs, with 1,114 related-guide routes and 97 explicit article gaps, not a completion score.

## Batch 118: Mud masonry and Dripstone

- Added two canonical block-family guides and corrected six item pages. Eight exact block IDs now route to reviewed family anchors; related navigation remains an inventory aid, not proof of complete variant coverage.
- Mud review distinguishes low collision/full support, Packed Mud’s hand drops, correct-tool masonry drops, eight crafting/cutting recipes, bottle/dispenser conversion, actual Mangrove and Trail Ruins supply routes, and a source-derived renewable soil chain. Clay and Bricks retains ownership of detailed Mud drying.
- Dripstone review separates placement support from growth and cauldron requirements, checks both damage paths and actual exclusive search limits, follows selected trades and generation/template routes, and distinguishes dry-tip cauldron behavior from the separate waterlogged-compatible Mud conversion path.
- Source checkpoint: `3cc0d7d93500be7a135c5577511b5565406d42e6`; all 126 distinct pinned article-source paths match this integrated source. These are documentation/source checks, with no mining, placement, generation, growth, damage or farm runtime tests claimed.

- Validation: required checker and strict build passed on 2,288 pages / 37 indexes; all 14 authored paths are documentation. The integrated tree resolves 2,051 local links/anchors and 313 tracked citation uses, with zero rendered citation ambiguity/unresolved-bracket candidates. The directory retains all 1,211 exact IDs, with 1,122 related-guide routes and 89 explicit article gaps; these counts do not measure article completeness.

## Batch 119: Note Block, Resin and remaining decorative lights

- Added five canonical block guides (Note Block, Resin, Creaking Heart, End Rod, Jack o’Lantern), expanded Torch for Soul forms, and corrected eight item pages. Thirteen exact registered IDs gain reviewed owner routes; the head/skull guide now links the canonical Note Block guide.
- Note Block review checks all 23 instruments and representative base materials, actual pitch/tuning/player and power callbacks, queue semantics, standing/wall head behavior and source-derived circuit examples. Audio and timing were not tested in game.
- Resin/Heart review distinguishes seven building forms from the Heart and ingredient-only Resin Brick, verifies recipes, mining/loot, actual tree/chest sources, aligned-log activation, spawning and day/night gates, player attribution, placement attempts/cooldown, protector removal, natural-only XP and comparator behavior.
- Lighting review preserves existing Torch anchors while checking Soul recipes/Piglin behavior, End Rod support and water replacement, Jack o’Lantern golem/dispenser distinctions, and wired structure/loot supply routes. None of these source checks establishes tested light radius, farm yield or runtime behavior.
- Source checkpoint: `85428ea17c51c31ddb67b6c1060b78f4c3a13515`; 157 distinct pinned draft-source paths match the integrated source. All authored changes are documentation.

- Validation: required checker and strict build passed on 2,293 pages / 37 indexes. All 23 authored paths are documentation; 2,491 local links/anchors and 512 tracked citation uses resolve, with zero rendered ambiguity or unresolved-bracket candidates. All 1,211 exact registered IDs remain alphabetical, with 1,135 related-guide routes and 76 explicit article gaps, not a completion score.

## Batch 120: TNT, Trapped Chest and iron fixtures

- Added four canonical block guides and corrected seven item pages, with seven exact registered IDs linked to their owners. Ordinary Chest now links the placed Trapped Chest guide; TNT and Trapped Chest link their shared trap/priming context.
- TNT review follows all active priming routes, normal versus explosion-chain fuse lengths, moving/fluid behavior, terrain versus entity effects, drop decay, permissions and actual exploration/loot routes. Disabling TNT explosions is not described as preserving blocks already consumed by fire or destructive blasts.
- Trapped Chest review verifies same-type double pairing, obstruction, counted users including Copper Golems, directional power, distinct Comparator/Hopper access, inventory persistence/removal, Piglins and the inspected Mansion template hazard.
- Iron fixtures/Ladder review checks exact recipes, Iron Door’s hand-drop exception, other iron forms’ correct-pickaxe gates, connections/shapes/support/water, hand versus power/Wind Charge controls, climbing exits, fuel and verified generation examples. No circuit, physics, mining, movement, save/reload or world-generation runtime test is claimed.
- Source checkpoint: `beb4335362d5983b867ef84d66a74ce668b6ef7d`; all 112 distinct pinned draft-source references match the integrated source. All authored changes are documentation.

- Validation: required checker and strict build passed on 2,297 pages / 37 indexes. All 18 authored paths are documentation; 1,978 local links/anchors and 317 tracked references resolve, with zero rendered reference ambiguity or unresolved bracket candidates. The directory preserves 1,211 alphabetical IDs, with 1,142 related-guide routes and 69 explicit article gaps. These are navigation counts, not article-completion scores.

## Batch 121: Cocoa, Sweet Berries and ancient crops

- Added four canonical block guides and corrected six item pages, preserving their 31 existing anchors, including Pitcher Pod portal conversion. Six registered crop/plant IDs now link to their owners, and Flowers links the separate ancient-crop lifecycles.
- Cocoa review checks the four accepted Jungle timber forms, growth and Bone Meal, break/replant harvesting, recipes and composting. Sweet Berry review separates ordinary picking from Fortune-sensitive breaking, actual soil/light rules, contact damage and Fox harvesting.
- Torchflower/Pitcher review follows active Sniffer state/tick/gift-loot acquisition, exact support and growth gates, their different mature-block transitions, seed/pod consumption and drops, decorative planting, dye/stew/compost uses and absence of ordinary mature-crop use/hoe controls. Torchflower’s level-7 light and Pitcher’s brightness-8 growth gate are checked MattMC properties.
- Source checkpoint: `20354edd390fadb09b43132d712facf4188c4119`; all 62 distinct pinned article-source paths match the integrated source. No crop growth, harvest, Sniffer, lighting, Bone Meal, contact or portal runtime test is claimed.

- Validation: required checker and strict build passed on 2,301 pages / 37 indexes. All 16 authored paths are documentation; 2,027 local links/anchors and 287 tracked reference uses resolve, with zero rendered citation ambiguity or unresolved-bracket candidates. The alphabetical directory retains 1,211 IDs, with 1,148 related-guide routes and 63 explicit article gaps. Broad article coverage remains incomplete.

## Batch 122: Workbenches, sticky blocks and remaining building materials

- Added six canonical block guides and corrected ten item pages, covering ten registered IDs. Bamboo and Pistons link the new detailed material owners; central alphabetical/category navigation retains honest incomplete-coverage language.
- TaCZ review traces all three registered workbenches through menus, screens, definition/recipe loading and server crafting; static data resolves 63 firearm, 29 ammunition and 99 attachment recipes. The guide documents direct player-inventory inputs, groups/output examples, no workbench storage/fuel, Creative ingredient requirements/overflow behavior and source-identified missing labels without claiming a live UI test.
- Slime/Honey review checks reversible recipes and bottle remainders, landing/crouch/slide callbacks, collision and movement factors, sticky-group exclusions/limits and moving-entity/projectile distinctions. The source-only ordinary on-foot Slime crouch path is qualified; no tested launcher or universal imported-entity behavior is claimed.
- Material review verifies Bamboo packing/stripping/fuel, Bone Meal storage and wired fossil routes, Netherrack terrain/processing/vegetation/fire uses, and Petrified Oak Slab’s separate mining/material rules. No ordinary fresh-Survival Petrified acquisition route was found in the checked recipes, loot, active references or 1,202 structure templates; legacy conversion remains distinct.
- Source checkpoint: `099b1184d1a7115915d880b436c8f58c1ed5a422`; all 135 distinct pinned draft-source paths match the integrated source. These are documentation/source checks, not runtime generation, crafting, UI, physics or native-player synchronization tests.

- Validation: required checker and strict build passed on 2,307 pages / 37 indexes. All 26 authored paths are documentation; 2,543 local links/anchors and 352 tracked references resolve, with zero rendered ambiguity/unresolved-bracket candidates. The full alphabetical directory retains 1,211 IDs, with 1,158 related-guide routes and 53 explicit article gaps. The route counts do not establish finished variant or broad wiki coverage.

## Batch 123: Ground cover and Eyeblossoms

- Added three canonical grouped guides and corrected nine item pages, preserving 45 existing item anchors. Nine registered IDs gain precise owner anchors; Flowers and Bee care link the new behavior owners.
- Grass/Fern review distinguishes short versus tall loot, Shears versus Fortune-sensitive seed routes, paired-half harvesting, Bone Meal multiplication, and actual village-chest tall-item acquisition. Source tables return two short plants from a sheared intact tall plant.
- Flowerbed/Leaf Litter review verifies segmented coverage and facing, different support rules, Bone Meal duplication versus leaf smelting, generation/trader routes, dye/compost/fuel and Bee tags. Eyeblossom review separates time-driven state changes, potted ownership, no emitted light, stew/dye forms and Bee-specific contact/feeding effects.
- Source checkpoint: `d1bff20a6235d5a7052eff0e7e2191da8fbb00c4`; all 104 distinct pinned draft-source paths match the integrated source. No generation, harvesting, propagation, timing, lighting or Bee runtime test is claimed.

- Validation: required checker and strict build passed on 2,310 pages / 37 indexes. All 19 authored paths are documentation and 2,132 local links/anchors resolve. The draft audit verified 118 unique pinned source links; integrated rendering of all 19 changed pages found zero citation ambiguity or unresolved-bracket candidates, including the spaced reference labels omitted by the older shortcut counter. The directory preserves 1,211 alphabetical IDs, with 1,167 related-guide routes and 44 explicit article gaps, not a completion score.

## Batch 124: Sculk systems and bounded item corrections

- Added three canonical guides and corrected six sculk item pages. All six sculk IDs now link to precise owners, with harvesting/XP, Catalyst growth, ordinary/calibrated detection, all 40 base frequency events, resonance, water/Wool/sneaking distinctions, and Shrieker warning/summoning gates traced through active callers.
- Natural and ordinary placed/catalyst-grown Shrieker states remain distinct. Direct step callbacks, waterlogged sensor events and an already-shrieking block’s removal response are documented without promising a safe cancellation method or guaranteed Warden spawn.
- Corrected the existing Soul Heart page: it is a plain lure item, not a placeable block; held-item/nearest-player checks and the verified ordinary-category inventory-browser route are now explicit, while recipes/natural loot remain unverified. Corrected only the false Acacia chest-boat cargo-retention claim: normal cargo spills separately and is not stored in the dropped boat item. The other ten chest-boat/raft variants were checked for that specific wording and did not repeat the claim.
- Added the central inventory-browser guide after tracing the complete user-interface/client/server path: ordinary category entries intentionally support Survival insertion, while operator entries require instant-build plus permission level 2. The guide distinguishes this route from recipes/natural loot and does not assume every registry item is listed. Creative/Survival owners now crosslink it.
- Source checkpoint: `b823010659d7b5095ed021b1c99cf85627e2082a`; all 86 distinct pinned sculk/correction/browser source pairs match the integrated source, and the new correction citations have valid bounds. No gameplay, signal timing, Warden spawning, lure or cargo-recovery runtime test is claimed.

- Validation: required checker and strict build passed on 2,314 pages / 37 indexes. All 21 authored paths are documentation; 2,034 local links/anchors and 217 tracked reference uses resolve, with zero rendered citation ambiguity or unresolved-bracket candidates. The inventory/browser source chain and all new correction citation bounds were checked independently. The alphabetical directory preserves 1,211 IDs, with 1,173 related-guide routes and 38 explicit article gaps; neither this nor the page count implies broad completion.

## Batch 125: Animal eggs and custom redstone

- Added three canonical block guides and corrected six item pages, covering seven exact IDs. Five existing mob guides gain narrow egg-owner crosslinks without rewriting their behavior or merged fixes.
- Egg review separates Caiman/Platypus/Terrapin/Turtle random-tick rules from Sniffer scheduled stages, actual baby types/ages, placement/stacking, Silk/drop and trample differences. Terrapin’s unregistered laying goal, absent item mapping and unestablished parent-trait transfer remain explicit; no fictional Terrapin Egg inventory page was created. Existing breeding/loot gaps remain qualified, with ordinary listed egg acquisition linked to the verified inventory browser.
- Elevator review distinguishes the two active upward input/search paths, basic server acceptance, ordinary listing and absent recipe/loot/tool data. Player prose focuses on controls and practical landing limits; separate server-validation analysis remains outside the wiki article. Randomizer review verifies directional signal mapping, sampled delay/pulse behavior, default states and model limitations without claiming measured randomness or circuit timing.
- Source checkpoint: `306bdd428eff0c9b05475b43735b798f71f130aa`; all 61 distinct pinned draft-source pairs match the integrated source. No hatch, breeding, collection, teleport, multiplayer or circuit runtime test is claimed.

- Validation: required checker and strict build passed on 2,317 pages / 37 indexes. All 20 authored paths are documentation; 2,056 local links/anchors and 334 tracked references resolve, with zero rendered citation ambiguity or unresolved-bracket candidates. The directory keeps all 1,211 IDs alphabetical, with 1,180 related-guide routes and 31 explicit article gaps. Five mob edits are crosslinks only, not newly completed species guides.

## Batch 126: Fire, Chorus and portals

- Added four canonical placed-block guides and corrected four real item pages, covering nine exact registered IDs. Existing dimension guides now link to the detailed portal owners.
- Fire review distinguishes ordinary spread, support, rain and rule gates from Soul Fire, and separates contact damage from block burning. Chorus review covers support, age, growth, collection and the actual fruit teleport attempt/cooldown behavior.
- Portal review covers frame geometry, activation, destination scaling/search, entry rules and cooldowns; Primordial portal support and return destination remain distinct. End rings, arrival-platform replacement and same-dimension gateway travel have explicit scope and safety limits. Ordinary listed frame/Chorus acquisition links to the verified inventory browser, separately from recipe and mining routes.
- Source checkpoint: `8897c74857515a7bad4b5f616f78e404ee9c32ec`; 115 distinct pinned draft-source pairs were checked unchanged against the current game source. No portal travel, fire spread, crop growth or fruit teleport runtime test is claimed.

- Validation: required checker and strict build passed on 2,321 pages / 37 indexes. All 17 authored paths are documentation; 1,896 local links/anchors and 351 tracked reference uses resolve, with zero rendered citation ambiguity or unresolved-bracket candidates. The alphabetical inventory retains 1,211 IDs, with 1,189 related-guide routes and 22 explicit article gaps; this does not establish complete variant coverage.

## Batch 127: Coordinated source and issue review, 2026-10-02 11:45 UTC

- Rechecked master at `b60d2986c5246e2eaade0dc8dd2bf5ff1cf0e3f0`; the latest game-source commit remains `cf8cd5371bd1de61411ae5e7e144aabe3edb1e54`. This review adds no gameplay changes or source-sync merge.
- Linked the existing Elevator travel/landing caveat to [#797](https://github.com/HungLo2020/MattMC/issues/797), and Terrapin egg laying, item-mapping and parent-data limitations to [#798](https://github.com/HungLo2020/MattMC/issues/798). Both are open source-reviewed bugs, not completed fixes or runtime reproductions.
- Pewen signs [#795](https://github.com/HungLo2020/MattMC/issues/795) and Composter rollback [#796](https://github.com/HungLo2020/MattMC/issues/796) remain open. Held-item light [PR #791](https://github.com/HungLo2020/MattMC/pull/791) and Building Wand [PR #794](https://github.com/HungLo2020/MattMC/pull/794) remain unmerged at this review; their proposed behavior is not promoted into current master guides.

## Batch 128: Operator, structural and empty-space blocks

- Added five canonical guides and corrected twelve registered item pages for fourteen exact block IDs: Air forms, Barrier, Light, Structure Void, Bedrock, Command Blocks, Structure/Jigsaw and Test Blocks.
- Verified actual inventory-category, placement, editor and removal gates rather than assuming all supplied items use Game Master restrictions. Bedrock's ordinary browser route remains separate from its absent mining recovery; AirItem remains an empty internal inventory form.
- Command guidance uses MattMC's commandBlocksEnabled gamerule and active chain/condition semantics. Structure and test guidance distinguishes preview from potentially immediate replacement, omission from saved Air, memory capture from disk save, and shared test/region effects. No commands, world edits or test runs were executed.
- Source checkpoint: `4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f`; all 56 distinct pinned source pairs match current source. Actual rendered-citation review repaired five missing draft definitions before integration, preserving prose and anchors.

- Validation: required checker and strict build passed on 2,326 pages / 37 indexes. All 24 authored paths are documentation; 1,752 local links/anchors and 190 tracked reference uses resolve, with no rendered citation candidates. The directory keeps 1,211 alphabetical IDs, 1,203 related-guide routes and eight explicit article gaps. Technical IDs and family routes remain distinct from obtainable items and completed behavior coverage.

## Batch 129: Remaining block-family entry points

- Added seven canonical guides and corrected nine item pages for the final eight explicit directory gaps: Leafcutter nests, Skunk Spray, Hay Bale, Honeycomb Block, Heavy Core, Cobweb and Dried Ghast. Leafcutter Ant and Skunk receive narrow owner crosslinks.
- Imported nature guidance distinguishes active Pupa spawning and spray bottling from absent colony storage, production, recipe/loot and item integrations. Storage/core guidance verifies exact recipes, recovery, Vault reward probability and the separate ordinary inventory-browser route.
- Cobweb coverage follows actual harvest gates, slowing, entity exceptions, Water replacement, Weaving and brewing. Dried Ghast coverage verifies recipe ID, barter/fossil callers, waterlogging, random-to-scheduled hydration checks and Happy Ghast hatch identity without promising an exact wall-clock timer.
- Source checkpoint: `d815d4429aac38e31ae553cbf42752e5248e24d7`; all 132 distinct pinned source pairs match current source. Independent rendering repaired nineteen missing draft definitions and five potential shortcut misbindings before integration. No game/runtime tests were run.
- All 1,211 IDs in the then-used direct-field inventory acquired a related placed-block route; the 24 omitted helper forms were exposed later in batch 147. This closes the directory's explicit link gaps only: shared family aliases, technical forms, article depth and broader player-wiki content remain review work. It is not a claim of complete block behavior coverage or broad catch-up completion.

- Validation: required checker and strict build passed on 2,333 pages / 37 indexes. All 25 authored paths are documentation; 1,993 local links/anchors and 398 tracked reference uses resolve, with zero actual rendered citation candidates. All 1,211 IDs remain alphabetical and occur once across the separate categories; 25 fallback labels remain disclosed. That direct-field subset has related-guide routes, pending ongoing semantic review; it was not the full registration set.

## Batch 130: Combat, recovery and the Warden encounter

- Added four shared mechanics owners for Combat, Health, Death/Respawn and Experience, and replaced the generic Warden page with a source-reviewed encounter guide. Existing equipment, food, Bed/Anchor and enchanting owners remain linked; their specialized formulas and setup are not replaced.
- Covers active charge/critical/sweep and defense stages; health/absorption and selective difficulty scaling; inventory/XP consequences, saved-point fallback and recovery; point-to-level costs, orb/Mending allocation and level spending. Hunger's saturation-cap sentence is now scoped to food additions, with Peaceful's separate setter linked.
- Warden coverage traces triggered/egg spawning, vibration/smell/contact, anger, melee versus sonic defenses, Darkness, persistence/burrowing and actual loot. Mobs navigation now lists it with aggressive encounters rather than claiming an implemented boss system.
- Source checkpoint: `c1adfb58c73bd6918cde87943be31afef6a2ccf4`; 78 distinct pinned source pairs match current source. No gameplay combat, death, healing, XP, spawn or escape test is claimed.

- Validation: required checker and strict build passed on 2,337 pages / 37 indexes. All 15 authored paths are documentation; 862 local links/anchors and 202 tracked reference uses resolve, with zero rendered citation candidates. Existing owner headings remain available. No raw page count is used to claim broad coverage completion.

## Batch 131: Semantic family-route corrections

- Expanded Pewen, Pistons and Dinosaur Chop where related-family links previously omitted player decisions. Four existing item pages now give actual acquisition, connection or recovery instructions instead of generic or overly broad availability claims.
- Pewen Fence explains missing bundled fence-tag membership, the resulting source-derived connection exceptions, and suitable sturdy-face/gate alternatives without promising tested pen containment. The family also links the already tracked Pewen sign limitation.
- Piston Head and Moving Piston now have explicit removal/collection guidance and precise directory anchors, including matching-base and moving-support conditions.
- Thin Bone now has an anchored remnant/placement/tool-gate section: its loot entry does not bypass the missing standard-tool assignment. Its ordinary inventory-browser route remains available. Dinosaur Chop availability wording likewise separates browser insertion from absent bundled raw crafting/natural loot.
- Source checkpoint: `b153e7232bbb43920a8694afbdb0053c2e219d77`; 23 newly cited pinned source pairs were checked unchanged. This was a bounded semantic audit, not a blanket certification of all family aliases or a gameplay test. No source defect was fixed or tracker issue created by this batch.

- Validation: required checker and strict build passed on 2,337 pages / 37 indexes. All 13 authored paths are documentation; 2,163 local links/anchors and 16 tracked shortcut uses resolve, with zero rendered citation candidates. Four registry aliases now reach precise subsections; the 1,211-ID alphabetical/category inventory remains unchanged.

## Batch 132: Nautilus care and undead encounters

- Added two missing mob owners for Nautilus and Zombie Nautilus, expanded Husk/Drowned/Zombie Villager, and corrected nine related item pages. Existing published anchors are preserved, including compatibility anchors for the older undead drafts.
- Nautilus coverage verifies loaded-biome spawn omissions, ordinary listed eggs, taming/feeding/breeding, dispenser armor, riding and persistence. The named mount effect remains explicitly insufficient as current air protection; Zombie Nautilus restrictions and conditional rider creation are distinct.
- Undead coverage follows active spawn data, water-conversion callers, equipment versus table loot, curing inputs/timers, saved trades and persistence. Shell acquisition connects fishing/trading/Drowned equipment with qualified Nautilus loot and the separate browser route; Golden Apple links exact food/cure behavior.
- Source checkpoint: `a8cfed894fbc4de04fbf938986736c539fe9549e`; 139 distinct pinned source pairs were rechecked unchanged. Ten hidden citation misbindings in held drafts were repaired before integration, and spawn-egg prose now includes the verified Survival browser route. No gameplay spawning, breeding, riding, oxygen, curing, loot or persistence test was run.

- Validation: required checker and strict build passed on 2,339 pages / 37 indexes. All 18 authored paths are documentation; 888 local links/anchors and 349 tracked reference uses resolve, with zero rendered citation candidates. Two new mob owners and three substantive replacements are distinct from the nine item corrections; broader mob coverage remains incomplete.

## Batch 133: Sword family and special weapons

- Added the Swords family owner and corrected ten material/special-weapon item pages, preserving published item anchors. Shared attack-charge/critical/sprint prose links the current Combat owner; sword-specific sweep, fixed mining rules, seven material recipes and repair remain together.
- Mace guidance verifies fall-distance bands, attack ordering, Density/Breach/Wind Burst, landing limits and retained-broken exceptions. Trident guidance separates Drowned equipment and normal Vault loot, thrown/return/Riptide controls, enchantment gates and the final-durability throw. Wind Charge guidance covers its exact recipe, reward entries, cooldown, block triggers and bounded fall allowance.
- All ordinary listed weapon browser routes remain distinct from recipe/loot acquisition. New linked source notes qualify broken-state exceptions instead of promising every enchantment disables uniformly.
- Source checkpoint: `1d6d726fd7ac529bef1649549bea8dde97819d92`; 138 distinct pinned source pairs match current source. Independent actual rendering found no special-weapon citation failures; held sword items received compatibility anchors before integration. No weapon, damage, fall, enchantment or repair gameplay tests were run.

- Validation: required checker and strict build passed on 2,340 pages / 37 indexes. All 17 authored paths are documentation; 752 local links/anchors and 403 tracked reference uses resolve, with zero rendered citation candidates. One new family guide, ten substantive item corrections and existing-owner crosslinks are recorded separately.

## Batch 134: Bastion and Monument expeditions

- Added two substantive structure guides and expanded four resident mob guides: Bastion Remnant/Piglin/Brute and Ocean Monument/Guardian/Elder Guardian. Existing source-reviewed material, breathing, brewing, map and trading owners remain linked.
- Bastion review traces Normal-preset eligibility, structure selection, actual connected templates and resident/chest dispatch, gold protection/theft/retaliation, the complete bundled barter table, conversion and equipment drops. No fixed layout or resident count is inferred from pool weights.
- Monument review traces loaded structure/spawn data, eligible deep-ocean selection, Cartographer map offer lookup, generation/room/resident callers, renewable Guardians, beam/spike attacks, Mining Fatigue eligibility and separate loot conditions. Looting, player credit and the three Elder template chances are kept distinct.
- Source checkpoint: `79f20bccc697135bd56a472c64f59981dce47fe0`; 115 distinct pinned source pairs match current source. Binary-template decoding and source checks are not in-game generation, combat or reward trials. Independent citation/anchor review passed all six incoming pages before integration.

- Validation: required checker and strict build passed on 2,342 pages / 37 indexes. All 17 authored paths are documentation; 964 local links/anchors and 508 tracked reference uses resolve, with zero rendered citation candidates. Two expedition owners and four substantive mob replacements are recorded separately from the narrow discovery links.

## Batch 135: Imported arthropod encounters and equipment

- Replaced two generic mob guides and six distinctive item pages for Cave Centipede and Tarantula Hawk. Existing Armor, Elytra and Durability owners remain canonical and gain narrow links to the imported exceptions.
- Centipede guidance traces active head/segment behavior, Poison, head-only leg loot, raw-chicken-style Hunger risk, seven-leg crafting and chainmail-based leggings. Missing normal armor/durability enchantment tag membership is distinct from repair material and armor attributes.
- Hawk guidance separates active sting/prey behavior from absent bundled feeding tags, unestablished offspring/wing production, and missing default Elytra equipment/glider/fragment-repair components. Neither imported animal is assumed to receive the default arthropod enchantment behavior solely from appearance.
- Source checkpoint: `bb9a8a060da02b64f23508b77794fb0f79307de4`; 47 distinct pinned source pairs match current source. Independent actual rendering verified all old anchors and references before integration. No gameplay encounter, taming, breeding, drop, crafting, flight or repair test was run, and no missing integration was claimed fixed.

- Validation: required checker and strict build passed on 2,342 pages / 37 indexes. All 14 authored paths are documentation; 853 local links/anchors and 196 tracked reference uses resolve, with zero rendered citation candidates and all replacement headings retained. The two mob and six item expansions do not certify other imported integrations.

## Batch 136: Nether/End biome choices and airborne imports

- Added two substantive biome-family owners with exact sections for all ten Nether/End IDs, preserving dimension travel/respawn and structure/material ownership. Updated the biome index's scope without claiming coverage of the remaining Overworld families.
- Replaced three imported mob stubs and corrected four related items for Spectre, Cosmaw and Cosmic Cod. Soul Heart remains the existing lure owner. Loaded natural-spawn absence, failed required Cosmaw food-item dependency, conditional companion behavior, water-sensitive bucket release and source-level leash limits are explicit.
- Biome guidance separates selected climate/noise categories, conditional surfaces, placed-feature attempts, spawn candidates and structure eligibility. Doubled Delta ore attempts are not described as doubled yield; End Midlands city eligibility is distinct from its empty feature list.
- Source checkpoint: `cfed1bb2ac5db4457ec7654a9418120f59bcf69d`; 113 distinct pinned source pairs match current source. Independent reference/anchor rendering passed all nine incoming pages. No generation, spawn-rate, feeding, transport, capture/release or resource-yield gameplay test was run.

- Validation: required checker and strict build passed on 2,344 pages / 37 indexes. All 16 authored paths are documentation; 909 local links/anchors and 266 tracked reference uses resolve, with zero rendered citation candidates and old replacement anchors retained. Ten biome IDs have precise family sections; this does not imply all Overworld biomes or imported systems are complete.

## Batch 137: Correct effective Primordial biome selection

- Corrected eight existing player owners after tracing dimension-resource loading and precedence through both fresh and saved client/server callers. Earlier preset-only wording that excluded Primordial Ocean from effective bundled selection was too broad and is superseded here.
- The literal Normal definition has two custom biomes; the loaded dimension resource includes Primordial Plains, Dry Midlands and Primordial Ocean candidates and takes precedence during baking. The missing-dimension fallback remains a separate Plains-only path. Candidate inclusion is not a measured generation rate, guaranteed encounter/portal landing, or retroactive change to existing terrain.
- Updated the dimension and biome owners plus Drowned, Dolphin and Coral wording, preserving their independent spawning and feature-placement gates. No gameplay code, resources, worlds or saved data changed.
- Source checkpoint: `eafe6b61edf55cefa8036ec540a3029da1143637`. The previously validated raid/outpost draft is preserved outside this branch working tree for the following batch; it was not published or discarded.

- Validation: required checker and strict build passed on 2,344 pages / 37 indexes. All ten authored paths are documentation; local links/anchors and actual rendered citations resolve. The correction follows fresh and saved-world caller inputs, not only the bake helper, and makes no runtime or old-terrain-change claim.

## Batch 138: Raids, outposts and raider behavior

- Added canonical Raid and Pillager Outpost guides, expanded Pillager/Ravager, and corrected Ominous Bottle and two egg pages. Trading/Villager and Trial Spawner/Vault retain their separate mechanics and gain narrow links.
- Traced drinking versus carrying, Bad Omen conversion, stored-position Raid Omen cancellation, occupied village points, wave composition versus additions/riders, active-raid persistence and victory/loss/stop outcomes. Captain bottle eligibility is stated at loot-evaluation time, not inferred solely from where a captain originated.
- Outpost review follows actual structure-set/jigsaw/NBT resident and chest paths, candidate placement controls and the continuing full-bounding-box Pillager override. Ravager guidance covers active blocking response, stun/roar timing, obstacles and separate Saddle/XP conditions.
- Source checkpoint: `384aa3dfa1473af7753759569de95012d5bdc46f`; 74 distinct pinned source pairs match current source. The direct-biome-spawn absence check covers 68 biome definitions, separately from biome-tag resources. No generation, captain-loot, raid, combat, cancellation or victory gameplay test was run.

- Validation: required checker and strict build passed on 2,346 pages / 37 indexes. All 15 authored paths are documentation; 907 local links/anchors and 191 tracked reference uses resolve, with zero rendered citation candidates and all existing replacement anchors retained. The two new owners and two mob expansions remain separate from the three narrow item corrections.

## Batch 139: Lava and aerial mounts

- Expanded Strider and Happy Ghast as separate source-grounded riding owners and added one Harnesses family guide covering all sixteen colors and thirty-two recipes. Existing color filenames now provide exact recipe and equipment routes; Warped Fungus on a Stick and both spawn eggs have corrected acquisition/use details.
- Traced Strider Lava spawning, temperature, adult saddle eligibility, steering/boosting and dismount limits; Happy Ghast hatching/growth, food/healing separation, four-seat control, temporary platform behavior and harness recovery. MattMC's current Left Ctrl crouch and Shift sprint defaults are preserved.
- Source checkpoint: `af6d2c60f3fe80c03eedd83996b9b6bd194d8775`; 92 immutable source files match current source. Independent actual rendering retained all existing anchors with no citation misbindings. No in-game flight, breeding, crafting, equipment, dismount or loot test was run.

- Validation: required checker and strict build passed on 2,347 pages / 37 indexes. All 29 authored paths are documentation; 2,711 local links/anchors and 464 tracked reference uses resolve, with zero rendered citation candidates. One shared Harness family owner and two substantive mob expansions are counted separately from exact color and support-item corrections.

## Batch 140: Spear family and its three attack paths

- Added one canonical Spears guide and corrected all seven material item pages with exact crafting/smithing, material values, repairs and links. Combat retains ordinary attack timing; the new guide owns held-contact and release-thrust differences, targeting, Lunge, equipment wear and enchantment eligibility.
- Traced active input/use dispatch and registered components, including the material-independent held-contact damage calculation, eight-tick release threshold, contact cooldown, multi-target limits and the registered tool's absence of Sword mining rules. Broken-entry guards are not presented as proof of mid-loop interruption.
- Source checkpoint: `53b55546fdf042588eea148e36f016234bedc0ed`; 55 distinct pinned source pairs match current source. Independent actual rendering retained all 35 existing item anchors with no citation misbindings. No in-game damage, timing, mounted combat, Lunge, enchantment or crafting test was run.

- Validation: required checker and strict build passed on 2,348 pages / 37 indexes. All 13 authored paths are documentation; 693 local links/anchors and 291 tracked reference uses resolve, with zero rendered citation candidates. One shared attack-family owner is counted separately from seven exact material-item corrections.

## Batch 141: Overworld and special biome comparisons

- Added nine substantive family guides covering the remaining 47 bundled biome resource IDs, alongside the previously reviewed 21 ocean, Nether, End and custom IDs. The Biomes index now provides all 68 exact IDs alphabetically with 65 verified English names, three disclosed custom-name fallbacks and precise family/section routes.
- Traced Normal selection and resource loading, selected surface/vegetation/ore chains, mob-specific spawning constraints and structure-start tags/sets. Primordial Ocean uses the corrected loaded-dimension precedence; The Void is explicitly a separate flat setup. Feature attempts, candidate weights and climate parameters are not presented as measured terrain, yields, encounter rates or geographic directions.
- Source checkpoint: `aaeea0b263d995334061e562cd5b71a853540f7f`; 363 immutable source pairs match current source. Independent rendered-reference checks and bounded semantic checks found no remaining concrete correction. No terrain survey, generation-rate, structure-search or gameplay test was run. This adds useful biome coverage without claiming all wiki systems or every biome detail are complete.

- Validation: required checker and strict build passed on 2,357 pages / 37 indexes. All 15 authored paths are documentation; 884 local links/anchors and 791 tracked reference uses resolve, with zero rendered citation candidates. The alphabetical biome directory has exactly 68 unique source IDs, 65 translated names and three disclosed fallback labels; every new family section is reachable.

## Batch 142: Ordinary fish, squid and aquatic resources

- Expanded six aquatic mob owners, four live-fish buckets, two ink resources and the loose Pufferfish/Tropical Fish items. Existing Oceans, Fishing, Signs, Nautilus and ordinary Cod/Salmon food owners remain linked for their distinct workflows.
- Traced actual biome candidates and water/height checks, schooling versus breeding, Salmon size weights, Tropical Fish appearance selection, Pufferfish inflation/sting, Squid baby/drying behavior, bucket state/retention/ultra-warm release and death-loot gates. Primordial Cod/Squid notes retain the corrected loaded-dimension precedence without measured encounter claims.
- Source checkpoint: `881a3ef4335dce000555e8584df9772b043887b6`; 84 immutable source pairs match current source. Independent rendered-reference and retained-anchor checks passed. No in-game spawning, aquarium, capture, damage, appearance or loot test was run.

- Validation: required checker and strict build passed on 2,357 pages / 37 indexes. All 18 authored paths are documentation; 925 local links/anchors and 377 tracked reference uses resolve, with zero rendered citation candidates and all prior replacement anchors retained. Six substantial mob replacements are counted separately from eight support-item corrections.

## Batch 143: Coordinated 14:45 UTC review

- Rechecked the published documentation cutoff at `4a0205977d3c1c10a9432c35b18092cac7d4ab5e`; game source remains `cf8cd5371bd1de61411ae5e7e144aabe3edb1e54`. Batches 128–142 have verified successful Wiki Pages build/deployment results. The Primordial selection correction in batch 137 supersedes the earlier preset-only exclusion inference without implying existing-chunk regeneration.
- Linked the existing Spectre crouch-release warning to newly verified [#799](https://github.com/HungLo2020/MattMC/issues/799), narrowly covering the cleared-holder read rather than redesigning its separate distance behavior.
- Linked the existing broken-Mace Breach warning to [#800](https://github.com/HungLo2020/MattMC/issues/800). That issue also covers Turtle Shell refresh with an explicit passive-effect policy decision; it does not establish a universal rule for all passive effects. The expanded Turtle Shell draft will carry the same link when integrated.
- These are source-reviewed limitations and new tracker records, not gameplay fixes or reproduced runtime failures. Existing issues #795–798 and draft fixes #791/#794 were unchanged at the coordinated review; other evidence leads remain queued rather than being labeled implemented or disproved.

- Validation: required checker and strict build passed on 2,357 pages / 37 indexes. The four authored paths are documentation; 215 local links/anchors and 70 tracked reference uses resolve, with zero rendered citation candidates. Both new issue bodies were independently read back before adding their links.

## Batch 144: Turtle and Terrapin care

- Expanded Turtle and Terrapin, plus Turtle Scute and Turtle Shell. Existing AnimalEggs/TurtleEgg owners retain placed-egg details; the new care guides cover home assignment, feeding, growth rewards, habitat, capture and species limitations.
- Traced Turtle baby-to-adult Scute loot separately from death loot; Terrapin air, spinning, persistence and unsaved bucket Age; and Turtle Shell armor, recipe, repair, brewing and separate Water Breathing refresh. The source-reviewed lifecycle limits remain linked to [#798](https://github.com/HungLo2020/MattMC/issues/798), and the broken-effect policy follow-up to [#800](https://github.com/HungLo2020/MattMC/issues/800). Neither issue is described as fixed.
- Source checkpoint: `a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1`; 57 immutable source pairs match current source. Independent rendering and old-anchor checks passed, and the narrow #800 addition was rerendered. No gameplay breeding, bucket round trip, wear-out, damage or diving test was run.

- Validation: required checker and strict build passed on 2,357 pages / 37 indexes. All eight authored paths are documentation; 842 local links/anchors and 155 tracked reference uses resolve, with zero rendered citation candidates and all prior replacement anchors preserved. Two substantive mob replacements are counted separately from two resource/equipment corrections.

## Batch 145: Mansion residents and Allay helpers

- Added Woodland Mansion as a structure owner and expanded Evoker, Vindicator, Vex and Allay, with three narrow illager egg corrections. Mansion review follows Dark Forest/Pale Garden eligibility, Cartographer map availability, actual room/NBT resident paths, loot-table versus fixed/empty chests and conditional Vex-template rewards.
- Traced illager targeting, fangs/summons, raid-gated door actions, persistent Johnny state and Vex lifetime damage. Allay owns sample/cargo filters, player versus Note Block delivery, Jukebox duplication, retention and recovery limitations. Existing Raid, Totem, Amethyst and shared transport owners retain their separate mechanics.
- Source checkpoint: `780a7733d1804088e86c248a1c8ec6957def4915`; 86 immutable source pairs match current source. All 73 mansion templates were decoded during research. Independent rendering caught five missing shortcut definitions in isolated Allay; all five were restored and the actual patched file passed before integration, with no prose or anchor rewrite.
- No in-game mansion search, loot sampling, spell combat, item collection, duplication or recovery test was run. Source-observed limitations are not implemented fixes or broad runtime parity claims.

- Validation: required checker and strict build passed on 2,358 pages / 37 indexes. All 15 authored paths are documentation; 931 local links/anchors and 273 tracked reference uses resolve, with zero rendered citation candidates and prior replacement anchors preserved. One new structure owner and four substantive mob replacements are distinct from three spawn-egg corrections and narrow discovery links.

## Batch 146: Spawn-egg family and acquisition corrections

- Added one shared Spawn Eggs owner grounded in all 158 registrations and 156 ordinary category listings. Ender Dragon/Wither eggs are explicitly unlisted and use the separately permission-gated command route; Cave Centipede binds to its head entity. Common hand/fluid/spawner/dispenser and existing-mob interactions retain their different gates and species exceptions.
- Corrected 117 generic egg pages with narrow acquisition/behavior paragraphs, exact registration/category evidence and verified species-guide links. Separately corrected the misleading Survival exclusions in Giant Squid and Mule without replacing their authored mechanics. The three newly expanded illager egg pages were preserved after exact baseline-drift checks; all other protected pages retain their substance.
- These are 119 targeted existing-page corrections plus one substantive shared owner, not 119 newly complete species articles. Natural spawning, food breeding, variants and care remain species-owned. Missing recipe/loot claims are kept distinct from the verified ordinary inventory-browser route.
- Source checkpoint: `ac333e7655e092e93f2423a5ab47b50b2b2a9d9a`; 34 cited source pairs used by the family and corrections match current source. Independent inventory, rendered-reference, species-link and old-anchor checks passed; the actual server slot-insertion range supplements its handler gates. No inventory request, spawn-egg use, spawner change, dispenser or gameplay test was run.

- Validation: required checker and strict build passed on 2,359 pages / 37 indexes. All 124 authored paths are documentation; 2,956 local links/anchors and 74 tracked reference uses resolve, with zero rendered citation candidates. The 119 section-level corrections retain existing headings and authored species notes; the three newer illager egg pages remain untouched.

## Batch 147: Correcting helper-created block inventory

- Reconciled the built-in declaration types and actual helper calls: 1,211 direct Block fields plus three WeatheringCopperBlocks families with eight explicit registration invocations each, deduplicated to 1,235 IDs. All three pass Blocks::register; their matching item helpers register eight inventory forms per family.
- Added the 24 Copper Bars/Chain/Lantern forms to the displayed-name alphabetical Blocks list and Copper category, with exact English labels and per-row helper evidence. All 1,211 existing guide routes and category memberships are retained. The 24 new inventory rows explicitly say Article needed; Iron recipes/loot or ordinary Lantern behavior are not asserted as full Copper coverage.
- Corrected the historical completeness wording, monthly entry and continuation method. This is an inventory-method correction: Blocks.java, Items.java and both helper sources are unchanged from the original `c87803e7` snapshot. The validated but unpushed animal-care batch is preserved separately for later integration.
- Source checkpoint: `f9e4a4c96fcf7fb744bce869c085cdfab189810e`. This is a source registration inventory, not a runtime dump or a claim about external mods, natural availability, item completeness or all variant behavior. The 25 existing localization fallback labels remain disclosed; all 24 helper names have exact English entries.

- Validation: required checker and strict build passed on 2,359 pages / 37 indexes. All 19 authored paths are documentation; 5,138 local links/anchors resolve and actual rendering has no unresolved citation candidates. Exact inventory checks cover 1,235 unique displayed-name-sorted IDs, one category membership each, all 1,211 preserved guide routes, precisely 24 Article needed rows and 25 disclosed fallback labels.

## Next batches, in priority order

1. Maintain the full alphabetical Blocks directory and separate categories as registrations or names change. Audit related-family routes for actual variant behavior; the absence of an article-needed marker is not a completion certificate.
2. Continue the remaining generic mob/item families and source-reviewed expedition drafts. Preserve current citation repairs and source-revalidate every incoming draft.
3. Replace remaining generic mob/item pages in coherent gameplay loops, and deepen practical expedition, biome, effect and enchantment references.
4. Continue checking acquisition through the actual inventory browser, recipe/loot, generation, permissions and active interaction callers separately. Keep incomplete imported systems explicit and link independently verified issues without claiming unmerged fixes.
5. Broad catch-up remains incomplete. The prior audit's template candidates are a triage list, not a completion metric; use player decisions, registry ownership and source-grounded substance to assess progress.

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

## Batch 148: Copper helper families

- Added two substantive placed-block/inventory owners for all 24 Copper Bars, Chain and Lantern forms, with exact anchors and alphabetical item aliases. The 1,235-ID catalog now routes those forms to their reviewed family guide.
- Verified 15 crafting recipes, 24 self-drop tables, tool/drop distinctions, support, waterlogging, light, oxidation, waxing, axe scraping and lightning state preservation against unchanged source. Copper Lanterns can drop without a correct-tool gate; Bars and Chain require an intact pickaxe.
- Removed obsolete recipe-book advice from the new draft: recipe awards are disabled, so mismatched reward IDs are an inactive data observation, not a manual-crafting defect. Axe wear is qualified for enchantments and infinite-material handling.
- Documentation check, strict build, local links, exact variant anchors and rendered citations are required before promotion. No game launch or crafting/mining/weathering runtime test is claimed; broad wiki catch-up remains incomplete.

## Batch 149: Fungal and mountain animal care

- Expanded Mooshroom, Goat and Polar Bear behavior and acquisition limits, plus Mushroom Stew and Goat Horn use, with three discovery/care crosslinks.
- Source review covers Mooshroom flower servings and shearing, Goat ramming/horn instruments and breeding variants, and Polar Bear cub protection and absent breeding food. Loot follows current MattMC data, including Goat Mutton; upstream assumptions are not substituted.
- All five owner drafts were independently reviewed, with 74 immutable source-file pairs checked against the integrated source. Final documentation check/build, rendered citations and local anchors are required before promotion. No live animal, item-use or breeding test is claimed.

## Batch 150: Recipe-book documentation correction

- Corrected the Knowledge Book owner against current item registration, category/browser construction, command permissions, client/server dispatch and consumption, loaded-recipe validation, inactive server recipe awards, and Chiseled Bookshelf storage. Empty or invalid recipe data can consume a book in Survival before failure; valid data does not unlock recipes in this implementation.
- Removed only the obsolete recipe-book-layout phrases from Stick, String and Oak Planks, preserving their existing authored recipes, source citations and anchors. The current crafting-table screen has no recipe-book interface.
- Source-reviewed at `13ff4feddc5b7b0ce0cdbfd912a9486d817400fd`. Preserved all old anchors; require the package's exact-diff, local-link, source-bound, reference-binding and actual MkDocs-render checks before promotion. No gameplay or multiplayer test is claimed, and this correction does not certify every other guide's recipe-book wording.

## Batch 151: Desert, jungle and swamp structures

- Added Desert Pyramid, Jungle Temple and Swamp Hut expedition owners, with biome, structure-index, Cat and content-guide routes.
- Reviewed actual trap layouts, finite Dispenser ammunition, chest and archaeology loot, intended suspicious-block counts, and the distinct generated-resident/natural/replenishment paths in huts. Generation source and pool weights are not runtime frequency guarantees.
- Independent review checked 175 references and 68 immutable source files; final check/build, local anchors and rendered citations are required before promotion. No live trap disarming, puzzle sequence, terrain-generation or loot survey is claimed.

## Batch 152: Food comparison and staple preparation

- Added a registered-food reference covering all 59 checked FOOD items, 19 MattMC additions, three separate nonfood drinks, and placed-food exclusions. Nutrition, saturation, use duration, stacking, remainder and effect distinctions follow actual registrations and active callers.
- Expanded Bread, Baked Potato and Cooked Mutton with exact recipes, bounded loot examples and the ordinary inventory-browser route; added index, content-guide and hunger discovery links.
- Independent review checked the complete registration inventory, nine recipes, four bounded loot claims, 218 citation uses and 36 source files. Final documentation check/build, local links, retained anchors and rendered citations are required before promotion. No eating, timing, production or loot runtime test is claimed.

## Batch 153: Imported water animals and food supplies

- Replaced eleven generic owners: Alligator Snapping Turtle, Catfish, Lobster, four filled buckets and four food items. Added care/acquisition discovery routes without importing upstream mechanics.
- Checked 68 biome definitions, 34 structure definitions and 1,202 templates separately from standalone predicates; no natural population route for these three species was found. Actual browser/egg routes, breeding, moss/dispenser harvest, cargo and swallowed-creature controls, persistence and food/device recipes are documented.
- Kept source-confirmed incomplete Catfish entity addition, Lobster bucket-color component mismatch and Snapping Turtle breathing limits explicit. These are implementation findings, not live reproduction or completed fixes. Common bucket health/name components and dispenser durability handling are precisely qualified.
- Independent rendering/source review and final documentation check/build/local-anchor validation are required before promotion. No creature release, storage-loss, drowning, breeding, farming or loot runtime test is claimed.

## Batch 154: Nether encounters and Ghast rewards

- Expanded Ghast, Hoglin, Zoglin and Zombified Piglin owners plus Ghast Tear and Tears music-disc items, linking biome/structure routes and relevant food/portal owners.
- Checked fireball ownership and exact disc-loot conditions, Hoglin feeding/repellents/conversion and retained Peaceful flag, monster-versus-animal baby loot gates, and Zombified Piglin anger/equipment handling. Hoglin pacification wording preserves the successful-hit and living-attacker guards.
- Independent review checked active source, 207 citation definitions, 268 rendered references, retained anchors, proposed crosslinks and decoded Bastion templates. Final documentation check/build and unchanged-source checks are required before promotion. No in-game encounter, reflection, breeding, conversion or drop test is claimed.

## Batch 155: Potion, special-arrow and wind encounters

- Expanded Witch, Bogged, Stray and Breeze owners with actual spawning/Trial configuration routes, combat controls, effects, shearing, immunities and conditional drops. Added four discovery/related-owner crosslinks.
- Independent review traced connected Trial templates, active potion and arrow handling, projectile collision/deflection, loot gates and damage exceptions. Bogged Shears wear is qualified before modifiers; Breeze burst mechanism triggers need mobGriefing, while an eligible direct Bell hit can still ring through its separate callback.
- Final unchanged-source, documentation check/build, local-anchor and actual rendered-citation checks are required before promotion. No runtime chamber, combat, potion-duration, shearing or mechanism-trigger test is claimed.

## Batch 156: Standard Sign inventory routes

- Replaced 22 generic standard Sign/Hanging Sign inventory entries with exact item IDs, standing/wall bindings, per-material recipe references, ordinary browser availability, blank-drop distinctions and scoped placement/editing owner links. No new article files were added.
- Existing authored Oak/Pewen entries and their different integration limits are preserved; already-substantive Wool/Glass pages were excluded from the candidate list. The current Signs owner remains the source-reviewed authority for shared behavior.
- Independent review and final source/check/build/render validation are required before promotion; all 110 existing page anchors are preserved. This is a bounded inventory-routing improvement, not 22 newly exhaustive block-behavior articles or a completed item backlog. No in-game placement, editing or harvesting test is claimed.

## Batch 157: Constructed protectors and ancient animal care

- Expanded Copper Golem, Creaking and Sniffer owners, preserving existing chest/statue, Heart, egg and ancient-plant behavior owners and adding two Copper backlinks.
- Traced construction/revival and sorting interactions, living-golem wax/lightning controls, Heart-bound versus unbound Creaking gaze/damage/removal, and Sniffer food, 48,000-tick hatchling growth, egg breeding and variable digging cycles.
- Independent source/render review and final documentation check/build/local-anchor verification are required before promotion. No live sorting, gaze, damage, revival, hatching, breeding or digging test is claimed; biome registration alone is not treated as a natural spawn route.

## Batch 158: Bison and Moose resource guides

- Replaced six generic owners for Bison, Moose, Bison Fur, Moose Antler, Raw Moose Ribs and Cooked Moose Ribs, preserving all 28 old anchors and the shared FoodReference owner.
- Distinguished ordinary browser/egg access from missing natural spawn-list wiring, missing entity death tables, and absent Fur/Antler/Rib recipes. Exact base-pack loader-path review covered 1,501 singular `recipe/` resources, 1,410 singular `loot_table/` resources and 68 biome definitions. Separate negative scans covered 1,459 recipe-related advancement entries, 191 plural `recipes/` resources outside the standard recipe loader path, and five nested `trade_rebalance` datapack loot resources, without assuming that optional pack is enabled. These are source-path counts, not runtime load counts.
- Traced Bison's active dispenser shearing, requested Shears wear, five-graze coat restoration and proximity aggression; Moose's adult-only probabilistic food acceptance, calf-growth limitation and saved Antler shedding; and the current melee-caller/legacy-animation-hook mismatch.
- Source-reviewed at `25319cecd6bee492767c5b15ee53b9213d1ec1ee`. Static/render checks preserve existing anchors, resolve all local links and citations, and pass an actual strict MkDocs build. No gameplay, natural-spawn, breeding, shearing, shedding, attack or production-rate test is claimed. Existing Headgear and other generic entries remain outside this bounded batch.

## Batch 159: Coordinated review, 2026-10-02 17:45 UTC

- Article cutoff is batch 158 at `2eb6ad396dd26dadffc33336088870d7068a9835`; game source remains `cf8cd5371bd1de61411ae5e7e144aabe3edb1e54`. The review interval contains documentation changes only, with no implemented gameplay fixes or new subsystem-completion claim.
- Linked the source-confirmed shared Catfish/Comb Jelly release limitation to [#801](https://github.com/HungLo2020/MattMC/issues/801) and Lobster bucket-color restoration to [#802](https://github.com/HungLo2020/MattMC/issues/802). Corrected the old Comb Jelly bucket claim that use successfully places its creature; state/position round-trip validation remains part of the issue, not a completed repair.
- These are source-only findings, not runtime reproductions. Ordinary dispenser item ejection is distinct from supported fluid-container release; no unsupported dispenser-emptying path is claimed.
- Reinforced exact loader-path resource counting after a prepublication research-manifest label correction. The six batch 158 player drafts were unchanged by that evidence repair. Full alphabetical block inventory still distinguishes 1,235 source IDs from complete variant-behavior coverage.
- Continue the isolated jungle-animal draft and remaining mob/item families after this bounded review. Final documentation check, strict build, links/anchors and rendered citations are required before promotion; broad catch-up remains incomplete.

## Batch 160: Jungle animal care

- Expanded Panda, Parrot and Toucan owners with actual acquisition, food/breeding or taming, care, variants and loot limits, plus four discovery links in three existing owners.
- Preserved fork-specific Panda one-stalk Bamboo breeding checks, Parrot Cookie/shoulder/imitation controls, and Toucan breeding-versus-planting inputs and current missing natural population route. A single Apple interaction can reach both inherited feeding and a separate planting gift when conditions permit.
- Independent review corrected Parrot release attempts to distinguish ignored early triggers from queued release, qualified Cookie consumption for ordinary Survival, and cited the active TamableAnimal Lead override. Source/render checks and final strict documentation build are required before promotion; no live feeding, breeding, shoulder, planting, sound or loot test is claimed.

## Batch 161: Standard Trapdoor inventory correction

- Replaced 12 legacy standard Trapdoor entries with precise single-block panel/top-bottom state descriptions, exact material recipes, browser access, matching ordinary hand-mining drops and scoped Wood Construction links. Ten had incorrectly described two-block doors; Acacia fire wording is now bounded by the material owner.
- Preserved all 60 existing anchors and 60 other inspected authored item pages. Crimson/Warped fuel exclusions remain explicit. These are existing inventory-page corrections, not 12 new exhaustive behavior articles.
- Clarified resource audit instructions: TaCZ actively reads some plural recipes paths through its own loader, so ordinary RecipeManager directory scope must not be confused with universal resource inactivity.
- Independent source/data/render checks and final documentation checker/strict build are required before promotion. No placement, power, waterlogging, mining, fire or recipe gameplay test is claimed.

## Batch 162: Small mobs and night encounters

- Expanded Bat, Phantom, Silverfish and Endermite with actual creation/spawn callers, care and combat limits, persistence and loot, plus seven discovery links. Phantom Membrane receives a conditional acquisition crosslink, not a completed item-owner claim.
- Documented default-off insomnia, conditional Phantom scheduling/rest checks and the size-zero damage-initialization caveat; Bat seasonal/local-ground conditions; Silverfish infestation, effect and configured spawner routes; and Endermite Pearl-position/lifetime/Enderman-target distinctions.
- Independent caller review and final unchanged-source/check/build/render validation are required before promotion. Spawn predicates are checked with their inherited and caller gates; no live spawn, combat, light-control, timing or loot test is claimed.

## Batch 163: Bundle inventory storage and colored routes

- Replaced the generic Bundle owner with source-grounded capacity, click/scroll controls, nesting restrictions, world unloading, component preservation and item-destruction limits. Added exact dye routes to the 16 existing color pages while retaining their old anchors and linking shared mechanics.
- Verified the String-over-Leather recipe, all 16 transmute recipes, eight village chest tables and their template references, and ordinary browser availability in Survival and Creative. Color recipes preserve contents and reject an unchanged same-color result.
- Reviewed exact base loader paths: 1,501 recipe resources and 1,410 loot-table resources. Advancement, plural recipe-directory and nested optional-pack resources were inventoried separately. Source reviewed at `1d7e3e91f2a2694339f78b8673993e98d496ca5f`.
- The configured strict MkDocs build, rendered links, original anchors, immutable source bounds and full/shortcut reference checks pass. No game or browser test is claimed.

## Batch 164: Six item classification corrections

- Corrected Copper Nugget, Glowstone Dust and Pumpkin Pie from false placed-block descriptions to actual ingredient/food behavior with exact selected recipes, harvesting/brewing or nutrition and ordinary browser routes.
- Corrected Copper, Oxidized Copper and Pewen Trapdoors from two-block-door descriptions to single-block panels, retaining actual hand/power/oxidation controls, Copper tool-tier requirements and Pewen recipe/tag limits. Other authored trapdoor variants and shared owners are preserved.
- These are six existing-page corrections with all 30 old anchors retained. Exact registration, recipe/loot, tool and food callers, source/render checks and final strict documentation build are required before promotion; no gameplay placement, mining, crafting, eating or brewing test is claimed.
- Before publication, fast-forwarded from batch 163 to concurrent master `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`, preserving all eight draft files and incoming source/development documentation. This source change adds native palette/world-generation work; its code tests were not rerun by the wiki pass. The six item-page source dependencies remain independently checked.

## Batch 165: Undead horse acquisition and care

- Replaced Skeleton Horse and Zombie Horse generic owners with actual weather/egg/operator routes, taming interaction limits, rider equipment, passive and potion healing, foal behavior, persistence and loot. Added shared Horse and Transport discovery links.
- Distinguished new lightning-trap creation from horse conversion, original and additional trap mounts, untamed ordinary eggs from explicitly tame command data, and underwater breathing from rider dismount/air rules. Preserved all eight original anchors.
- Independent source and configured-render review passes on both canonical articles; all 44 cited files and 68 biome definitions remain unchanged through the concurrent native world-generation source update. Final documentation check and strict build are required before promotion. No commands or gameplay travel, trap, taming, healing or loot tests were executed.

## Batch 166: Luminous block inventory routes

- Replaced generic Shroomlight, Ochre Froglight, Verdant Froglight and Pearlescent Froglight item entries with exact acquisition, ordinary recovery and shared placed-block links. These four inventory routes supplement the existing substantive Luminous Blocks owner; no new block article or exhaustive completion is claimed.
- Preserved all 20 original anchors and the already-authored Glowstone, Sea Lantern, Carmine Froglight and Luminous Blocks pages. Independent review corrected three mob-loot citations to the active inherited LivingEntity gate before publication; visible behavior claims were unchanged.
- Checked active huge-fungus growth and Frog prey/loot callers, exact ordinary and TaCZ recipe-loader scopes, tool/drop rules and configured rendering. Final documentation check and strict build are required before promotion. No live growth, mining, Frog, lighting or farm-rate test is claimed.

## Batch 167: Mantis Shrimp and Mudskipper care

- Expanded the two existing mob guides and the registered Bucket of Mudskipper item guide, preserving old anchors and linking the existing Bucket, Water Bucket, Inventory Browser and Shulker Shell owners.
- Verified species/egg/bucket registrations and bounded natural-spawn/loot absence; removed the unsupported Mudskipper Bucket Creative/browser claim and documented actual manual capture, release and Dispenser fallback.
- Traced diet tags, shared-first breeding/growth, later taming/healing and the adult last-fish owner-control fallthrough. Documented Mantis Shrimp moisture, prey, block-changing controls, kill reward and current save/attack signature limits; documented Mudskipper breathing, defense, display and incomplete bucket-state transfer.
- Source-reviewed at `eaeeffdeb9220de7d839af7c84a047693249c2f7`, using exact base recipe/loot/biome scopes and separate advancement/plural/nested-pack inventories. Configured render, old-anchor, link and immutable citation checks apply; no game or browser test is claimed.
- Independent review distinguished the tagged-prey goal's start gate from continuation: Sit/Break Blocks do not clear an existing Mantis Shrimp target. Corrected this care warning and added the active continuation citation before publication.

## Batch 168: Limestone variant behavior

- Deepened the existing Limestone owner across its ten registered forms, adding exact variant anchors and 20 corresponding links in the alphabetical Blocks directory and stone category. Registry IDs, names, ordering and the 1,235-ID inventory are unchanged; these are reviewed routes, not ten new articles.
- Distinguished ordinary inventory-browser supply from absent checked recipe/natural routes, standard-tool failure from conditional explosion loot, and one-item double-slab loot from normal two-slab placement. Explained stair corners, orientation, support and Water behavior.
- Traced missing walls-tag membership through actual connection/support-shape callbacks; absent or asymmetric joins are source-predicted, not a runtime appearance or containment test. Final independent source/render review, original anchors, documentation check and strict build are required before promotion. No game mining, explosion, placement, waterlogging or generation tests were run.

## Batch 169: Wandering Trader visits and offers

- Replaced the generic Wandering Trader owner with actual Overworld scheduler and placement gates, egg/operator distinctions, all buying listings, special stock and selected general supplies. Added one Trading discovery link; shared Llama/Trader Llama and inventory-browser owners remain authoritative.
- Distinguished nine initial offers from levels/restocking, full-container payment consumption from crafting remainders, and natural ticking lifetime from egg/command defaults. Documented threat/drinking behavior and conditional equipment drops without promising arrival or farming rates.
- Preserved all four old anchors; checked active offer constructors, payment/use callers, source ranges and configured rendering. Final independent source review, documentation check and strict build are required before promotion. No spawning, trading, command, timing or drop gameplay tests were run.

## Batch 170: Comb Jelly and Frilled Shark care

- Expanded two existing mob owners and Bucket of Frilled Shark, with discovery links and all 13 original anchors retained. Protected the existing Comb Jelly bucket warning linked to [#801](https://github.com/HungLo2020/MattMC/issues/801); the shark uses a different release class with actual entity insertion.
- Traced egg/browser access, unestablished natural routes, active aquatic air handling, prey/retaliation and the shark's directly called bite method. Separated current save signatures, pressure appearance, common bucket fields and custom-component loss; no lossless transport or aquarium-size guarantee is made.
- Exact source/data and configured-render checks cover active registrations/callers, bounded resource and structure scans, loot fallback and variant naming. Final independent review, documentation check and strict build are required before promotion. No game capture/release, combat, pressure, aquarium or multiplayer test was run.

## Batch 171: Dye recipes and applications

- Replaced 15 generic dye item entries with exact ingredient choices, output quantities, repeated-slot guidance and color-specific uses. Preserved their 75 existing anchors, including MkDocs-compatible BOM handling for White and Yellow Dye.
- Added one shared Dyes owner for the 16-color directory, ordinary Survival/Creative browser access, selected Wandering Trader stock and reviewed Sheep/collar/sign, Loom, material, portable-storage, equipment-color and Firework Star uses. Count this new owner separately from the 15 rewritten item entries.
- Independently verified all 43 dye-producing ordinary recipe resources: 41 shapeless and 2 smelting, with no tag ingredients. The rewritten entries cover 42; the existing substantive Green Dye owner covers the Cactus recipe and remains unchanged.
- Kept Flowers, Wool and Carpet, Signs, Sheep, Cat, Wolf, Loom, Bundle, Shulker Box, Cauldrons and Inventory Browser as existing subject owners. Added direct ContentGuide and Items-index discovery entries plus scoped links from all 15 color entries; the alphabetical item list is unchanged.
- Source-reviewed at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`, with 1,501 base recipe resources and 1,410 base loot tables counted separately from advancements, plural-path recipes and bundled-pack resources. Configured strict render, original-anchor, local-link, immutable-citation and reference-binding checks apply. No game or browser test is claimed.

## Batch 172: Seagull and Potoo care

- Replaced two generic mob owners with active egg/browser acquisition, absent natural-route limits, feeding and movement behavior, persistence and loot. Added Root Crops and Buried Treasure discovery links, retaining all eight old mob anchors.
- Distinguished Seagull raw-fish breeding from luring/offerings, carried-food healing from player consumable effects, hotbar theft from dropped-item pickup, and working retrieval from incomplete map-destination assignment.
- Separated Potoo's working Beetroot Seed lure from missing breeding/perch tags and inactive hunting commands. Exact active resource scopes, registered callers and configured-render/source checks are required alongside final independent review and strict documentation build. No game spawning, feeding, theft, treasure search, perching, flight or drop tests were run.

## Batch 173: Limestone inventory routes and sandstone compatibility

- Corrected ten existing Limestone inventory routes, preserving the already-authored base item's structure and all 49 original anchors. Exact ordinary BlockItem bindings and per-form owner anchors distinguish browser acquisition, missing recipe/natural supply, mining gates, shape placement and conditional recovery. These are inventory routes supplementing the existing family owner, not ten new full articles.
- Corrected the legacy CutStandstoneSlab page's invented item ID using the actual item-to-block registry binding. Kept its old URL and anchors, linked the protected canonical CutSandstoneSlab guide, and moved the duplicate false inventory row into an explicit compatibility section without disturbing alphabetical item ordering.
- The ordinary BlockItem audit identifies 483 route candidates for individual review; this is not a verdict that every candidate needs replacement or that short linked pages are incomplete. Special factories, aliases and useful authored routes remain separate. Final independent source/render review, documentation check and strict build are required before promotion; no gameplay mining, placement, crafting or water tests were run.

## Batch 174: Movement effects

- Added one shared practical owner for Speed, Slowness, Jump Boost, Slow Falling, Levitation and Dolphin's Grace, with exact effect anchors. Expanded the Effects index to nine reviewed effects alphabetically and linked Brewing; the prior three effect owners are preserved.
- Traced active attribute/motion/gravity/fall consumers, actual potion and mob/food/Beacon routes, duration scaling, refresh/hidden-effect rules, clearing and recipient limits. Attribute contributions are not represented as measured travel speeds or guaranteed jump heights.
- Moved the already-recorded native palette/world-generation source entry into the monthly landed-code section, without duplicating it or changing its factual scope. Final independent source/render review, required index routes, documentation check and strict build apply. No gameplay movement, brewing, command, timing or fall tests were run.

## Batch 175: Mining enchantments

- Added one practical owner for Efficiency, Fortune and Silk Touch, with required Enchanting-index and Mining/ContentGuide links. Preserved newer ContentGuide additions by applying only the exact scoped insertion, and kept existing tool/block owners intact.
- Traced supported and exclusive tags, the Shears Anvil-versus-table distinction, active main-hand speed and loot/XP consumers, correct-tool/drop gates and player/crop/explosion tool contexts. Broken-stack behavior is qualified by the actual caller rather than a global enchantment-disable assumption.
- Exact recipe/definition/source bounds and configured rendering accompany final independent review, actual overlaid hierarchy check and strict build. No timed mining, enchantment-roll, drop-rate, crop-harvest or explosion gameplay tests were run.

## Batch 176: Coordinated review, 2026-10-02 20:45 UTC

- Fixed article cutoff is batch175 at `9c1a3372e55fb7a6fb5c17fb22f5e3f5a2138d7a`; source advanced once in this interval to `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`. The real fast-forward preserved eight pending documentation files and the full incoming native palette/world-generation source change; authored changes remained documentation-only. Code-author acceptance records were inspected, not independently rerun by this wiki pass.
- Corrected the Allay full-inventory warning after tracing the complete ordinary return path. It requires an empty main hand, which maps to the free selected inventory slot; both insertion branches can use that slot for the one-item sample. Cargo is thrown with pickup delay. The original warning's ordinary-full-inventory premise was unsupported, so this is a documentation correction, not an implemented gameplay fix or a new bug ticket.
- Source/caller review, preserved anchors, configured rendering, the documentation checker and strict build are required before promotion. No live sample-retrieval, inventory-loss or other gameplay reproduction is claimed.
- Linked the bounded native-operation progress checkpoint [#775](https://github.com/HungLo2020/MattMC/issues/775#issuecomment-5961221994) to the existing source changelog entry, retaining the limits of Java-owned orchestration and tests not independently rerun. Linked additional armor-enchantment evidence to [#800](https://github.com/HungLo2020/MattMC/issues/800#issuecomment-5961208983), whose passive-effect policy remains unresolved; no universal disable rule or code fix is claimed. These were two tracker comments, with no new tickets or closures.

## Batch 177: Combat effects and potion-loot correction

- Added one shared owner for Strength, Weakness, Resistance, Absorption and Health Boost, with active attack/damage/health consumers, real acquisition/delivery routes and clearing/refresh limits. The Effects index now has 14 reviewed effects alphabetically; earlier Movement entries and the Armor policy paragraph are preserved.
- Corrected Buried Treasure's earlier empty-potion claim by following its pool-level set-potion function through actual loot execution. Eligible rolls produce zero to two ordinary Water Breathing I bottles; the ordinary stored duration is 3,600 ticks. This is a source-backed documentation correction, not a changed loot table or guaranteed chest result.
- Guarded text/index insertions preserve existing owners and anchors. Final independent source/render review, required index routes, documentation checker and strict build apply. No game damage, healing, brewing, chest-opening or consumption tests were run.

## Batch 178: Protection enchantments

- Added one shared owner for Protection, Fire Protection, Blast Protection and Projectile Protection, with exact I–IV contributions, damage-tag matching, the shared 20-point cap, the 29 supported armor items and table/Anvil/browser routes. Other enchantment definitions are not marked reviewed by this batch.
- Separated damage protection from armor/toughness and from Fire burning-time/Blast knockback attributes. Retained broken armor's passive contribution follows the active caller; the broader policy tracked in [#800](https://github.com/HungLo2020/MattMC/issues/800) remains unresolved, with no code change or blanket disable rule claimed.
- Added guarded Enchanting, Armor and ContentGuide discovery links and a link to the now-published Combat Effects owner, preserving newer prose. Final independent source/render equality review, actual documentation hierarchy check and strict build apply. No in-game combat, equipping, enchanting, inventory or repair tests were run.

## Batch 179: Tuff inventory routes

- Corrected thirteen existing ordinary BlockItem routes with exact raw/polished/brick/chiseled recipes and useful family-section links. Preserved the authored base Tuff item and substantive block owners; these are route improvements, not thirteen new articles.
- Reviewed all 38 family recipes (13 crafting and 25 Stonecutter), complete per-form loot, correct-tool gates and slab/stair/wall placement. Chiseled Tuff and Chiseled Tuff Bricks use different slab ingredients and different cutting inputs; ordinary Wood pickaxes qualify, and double slabs recover two matching slabs.
- Exact baseline/proposal hashes, 65 original anchors, source/render review, required hierarchy check and strict build apply. Catalog IDs, display ordering and existing shared owners remain preserved. No gameplay crafting, mining, placement or water tests were run.

## Batch 180: Candle-color inventory routes

- Corrected fifteen existing color routes with exact uncolored-Candle-plus-Dye recipes and links to each placed-candle and candle-cake variant. Preserved the authored Candle and White Candle items, shared block owners and catalog files; these are inventory-route improvements, not fifteen new articles.
- Reviewed all fifteen recipes, thirty complete standalone/cake loot tables and active grouping, lighting, water, support, Cake insertion and recovery callbacks. Already dyed candles are not recipe substitutes; same-color groups hold up to four, while a Cake holds one.
- Exact baseline/proposal hashes, 75 original anchors (including the old Yellow Candle heading), source/render review, hierarchy check and strict build apply. No gameplay crafting, lighting, mining, water, support-removal or eating tests were run.

## Batch 181: Skelewag Sword and Trial sherd corrections

- Corrected the existing Skelewag Sword page’s promised normal sword/tool behavior. Its active empty Item subclass lacks the configured weapon/tool components and sword attributes; ordinary held-item attacks/mining remain possible, while the configured 430 durability is not a promise of 430 sword attacks. Traced browser acquisition is distinguished from absent checked recipe/loot routes.
- Corrected three existing Flow, Guster and Scrape Pottery Sherd routes from generic archaeology prose to the verified Trial Chamber decorated-pot recovery path. Independently decoded template data and reachable jigsaw connectors lead to one matching sherd plus three Bricks per specified pot, with active loading and dynamic shatter drops traced. Existing Decorated Pot and other item owners remain unchanged.
- Four existing route corrections, not new full owners or gameplay fixes. Twenty old anchors, 53 source uses, exact baseline/proposal hashes, source/render review, hierarchy check and strict build apply. No game combat, mining, generation, brushing, shattering or crafting tests were run.

## Batch 182: Water and fire effects

- Added one practical owner for Fire Resistance, Water Breathing, Conduit Power and Breath of the Nautilus, with an eighteen-entry alphabetical Effects index and a narrow Conduit discovery link. Existing frame, mount, movement and combat owners retain their detailed mechanics.
- Traced real damage, air, mining, duration and current Java-to-Rust rendering consumers. Fire Resistance does not clear burning or cover every fiery-looking attack; Water Breathing and Conduit Power share air handling. Breath of the Nautilus is applied but lacks the passenger air consumer in this snapshot; higher levels do not supply that missing behavior.
- Reviewed actual brewing, item, barter and loot routes, including the already-corrected Buried Treasure pool function without applying that correction twice. Source/caller review, 119 citation bindings, exact guarded index/owner merges, hierarchy check and strict build apply. No gameplay damage, drowning, riding, visibility, potion or loot tests were run.

## Batch 183: Unbreaking and Mending

- Added one owner for two durability enchantments with guarded Enchanting, Durability and Experience discovery links. Existing repair-method and XP owners retain their practical prose, and Mining/Protection discovery links are preserved.
- Reviewed per-durability-point Unbreaking rolls, armor-tag-specific probabilities, the exact 83-item support set and seven supported items without direct table-enchanting components. Traced actual Mending equipped-item selection, retained broken-stack repair and integer XP remainder rather than assuming uniform lifetime gains or whole-XP rounding.
- Verified ordinary table, Librarian and category-book browser routes, with optional trade-rebalance and non-exhaustive acquisition scope explicit. Exact canonical/baseline hashes, source/render review, 22 old anchors, hierarchy check and strict build apply. No gameplay wear, XP pickup, repair, enchanting, inventory or trade tests were run.

## Batch 184: Vision effects

- Added one practical owner for Night Vision, Blindness and Darkness; the alphabetical Effects index now lists 21 reviewed effects. A guarded Warden link leads to the effect and presentation explanation while existing encounter, Shrieker, flower and brewing owners retain their scope.
- Traced current Java-to-Rust lightmap, fog, sky and HUD consumers separately from ordinary player sprint/critical gates. Night Vision does not cancel harmful fog or Blindness restrictions; Darkness Pulsing changes specified presentation inputs without removing the status or all fog/sky effects. Source parameters are not measured visibility claims.
- Verified distinct potion, crafted/traded stew, Illusioner, Warden and Shrieker routes and refresh/clearing limits. Exact canonical/index guards, 136 citation bindings, independent source/render review, hierarchy check and strict build apply. No gameplay visibility, shader, sprint, spell, combat, food, trade or timing tests were run.

## Batch 185: Melee damage enchantments

- Added one practical owner for Sharpness, Smite and Bane of Arthropods with two guarded Enchanting/Swords discovery insertions. Existing combat, weapon, repair and status-effect owners retain their mechanics.
- Reviewed additive formulas, ordinary player charge/critical/sweep ordering, active server dispatch, Bane’s direct-hit slowing and broken-weapon gates. Exact tag closures distinguish 14 Sharpness-supported items from 22 Smite/Bane-supported items, and fifteen Smite targets from five Bane targets; custom mob appearance does not establish membership.
- Separated narrower sword-only table selection from supported Anvil equipment, compatibility exclusions and the ordinary maximum-level browser-book route. Exact source/canonical guards, independent source/render review, actual hierarchy check and strict build apply. No game combat, slowing, enchanting, inventory or repair tests were run.

## Batch 186: Instant effects and lingering-cloud correction

- Added one shared owner for Instant Health and Instant Damage, with a 23-entry alphabetical Effects index and narrow Brewing discovery link. Ordinary I/II values, fifteen inverted recipient types, drinking/splash/cloud/arrow delivery and selected acquisition/mob sources are traced independently.
- Distinguished direct instant calls from stored arrow/status ticks, cloud half-strength from ordinary full strength, and physical-arrow damage order from recent-hit comparison. Displayed effect amounts are not guaranteed health loss; applicable source, recipient, difficulty and damage rules remain explicit.
- Corrected the existing Lingering Potion page’s unconditional cloud claim with guarded replacements and actual brewing/browser routes. Normal Water without custom effects uses a special branch; other effect-free contents also make no cloud, while a Water base with custom effects can reach cloud creation. This is a documentation correction, not a gameplay change.
- Exact canonical/index/old-text guards, independent source/render review, original anchors, actual hierarchy check and strict build apply. No gameplay healing, damage, brewing, throwing, cloud, arrow, command, trade or timing tests were run.

## Batch 187: Skelewag

- Replaced the generic existing mob owner with active spawn-egg/spawner routes, absent checked natural selection, installed combat/movement behavior, variants and Drowned rider attempts, persistence and loot/XP details. The separate Skelewag Sword route is linked without inventing a sword drop.
- Traced the complete active air/tick/damage path and nested breathing tags rather than relying on the unused legacy fluid method. Prolonged submersion damage is explicitly conditional on ordinary unprotected air handling and is source-predicted, not gameplay reproduced; naming prevents ordinary distance despawn, not drowning or Peaceful removal.
- The inspected native extraction path sees the legacy model proxy as an empty part tree and is written to throw. The guide states this source-level compatibility limitation beside egg access, with no reproduced runtime failure or blanket claim about other imported mobs.
- Exact baseline/canonical hashes, four preserved old anchors, current rendering caller review, independent source/render verification, actual hierarchy check and strict build apply. No spawning, combat, breathing, rider, rendering or multiplayer gameplay tests were run. A separate bounded tracker-review lead is evidence only; no new ticket or gameplay fix is claimed.

## Batch 188: Illusioner encounters

- Replaced the generic existing Illusioner owner with deliberate acquisition, command initialization, installed bow/spell goals, persistence and complete default loot/equipment/XP behavior. Two guarded discovery insertions preserve existing mansion/illager and content routes.
- Distinguished plain summons from NBT summons that skip initialization, absent generated patrol/raid-wave/mansion selection from an existing eligible raider joining a raid, and captain-banner equipment from the Pillager-only bottle pool. Spell conditions use actual local difficulty and remembered targets, not a blanket Hard-only assumption.
- Traced equipment dropping before conditional experience calculation; source-derived rewards are qualified for ordinary versus modified equipment. Source/canonical guards, preserved anchors, independent source/render review, actual hierarchy check and strict build apply. No spawn, spell, raid, combat, persistence, drop or appearance gameplay tests were run; no claim about rendered illusion-copy counts is made.

## Batch 189: Wooden-door inventory routes

- Corrected twelve existing vanilla wood-door routes, preserving all sixty original anchors and Acacia’s useful entrance/hinge guidance. Exact six-matching-planks recipes, DoubleHighBlockItem bindings and lower-half assembly loot replace generic or mismatched prose; the Warped Door vegetation template is removed.
- Routed shared support, power and water-state rules to the existing Wood Construction owner, with qualified Zombie/Vindicator behavior links for Acacia. Ten materials provide 200 default fuel ticks; Crimson and Warped doors remain excluded. No unsupported underwater-air promise is retained.
- Corrected an isolated evidence inventory before publication: ordinary recipes, recipe advancements and the separate active TaCZ recipe loader are disjoint scopes. This changed audit metadata only; the twelve article hashes were unchanged. Baseline/proposal guards, independent source/render review, actual hierarchy check and strict build apply. No gameplay crafting, mining, door, mob, water or fuel tests were run.

## Batch 190: Recovery Compass

- Replaced the generic existing item owner with the exact recipe, linked Compass ingredients, reachable Ancient City Barracks chest-table acquisition and separate inventory-browser route. Loot selections are not guaranteed chest yields.
- Traced player-owned death saving, ordinary respawn copying, login/respawn synchronization and normal inventory model selection. The compass reads the viewer’s applicable player record, not a stored item target; dimension/no-record and horizontal-direction limits are explicit, and dropped-item survival remains with Death and Respawn.
- Verified the active selected-model geometry/atlas transport into the Rust GUI draw route rather than assuming asset definitions establish displayed behavior. Exact canonical/baseline guards, five preserved heading IDs, independent source/render review, hierarchy check and strict build apply. No gameplay crafting, chest search, death, rejoin, dimension, needle or item-recovery tests were run.

## Batch 191: 23:45 coordinated review

- Linked the existing Skelewag rendering warning to [#803](https://github.com/HungLo2020/MattMC/issues/803), the verified shared Citadel geometry transport/admission issue. The visible ordinary-body, readable-texture and valid-transform scope is explicit; no runtime failure, universal imported-mob defect or gameplay fix is claimed. The separate breathing lead remains outside that ticket.
- Source remains `8b9173b399a629578a7bf0168e4d3ea32b10e8a6` at the batch-190 article cutoff. The review adds one tracked limitation; it does not close issues or represent new gameplay implementation. Existing monthly entries and their links are preserved.
- Documentation hierarchy, changed local links, rendered citations and strict build are checked. No gameplay or optional browser validation is claimed. Broader player-wiki catch-up remains incomplete.

## Batch 192: Mining and food status effects

- Added two shared practical owners covering Haste, Mining Fatigue, Hunger and Saturation. The alphabetical Effects index preserves its existing entries and now links 27 reviewed effects; narrow Mining, Hunger, Food Reference, Suspicious Stew and Husk discovery links retain existing owner content.
- Traced mining multipliers separately from attack recharge and swing timing, with Conduit interaction, Beacon and Elder Guardian sources and actual removal rules. Food effects are separate from item nutrition: Hunger uses player exhaustion, while stored Saturation duration can apply repeatedly; stew trade/loot durations and conditional Husk hits are qualified.
- Exact canonical hashes, guarded insertions, independent source and configured-render review, preserved old anchors, the integrated hierarchy check and strict build apply. No gameplay mining, attack, food, stew, loot, trade or timing tests were run. This adds four reviewed effects, not complete status-effect coverage.

## Batch 193: Mob-granted status effects

- Added five alphabetical effect routes using the existing Orca, Tiger, Tarantula Hawk and Sunbird owners; the Effects index now has 32 reviewed entries. Existing detailed mob content and all 34 prior heading anchors are preserved. This is focused effect coverage in four existing owners, not four completely re-audited mob articles.
- Documented grant areas, levels, refresh/expiry, clearing and ordinary brewing exclusions. Replaced Sunbird's generic natural-spawn promise with the verified egg/browser route and a bounded source absence finding.
- Qualified Tarantula Hawk and Sunbird motion formulas through the active server/client tick and packet paths. Damage and corrections can independently transmit motion; sustained client-controlled slowing or flight benefits from these effects alone are not established. Sunbird fall-distance reset and synchronized glide interruption remain separate active behaviors.
- Independent source/render and acquisition review, exact canonical/equality guards, 79 added citation uses, preserved index entries, hierarchy check and strict build apply. No gameplay spawning, movement, networking, flight, fall, effect or consumption tests were run.

## Batch 194: Ordinary armor item routes

- Replaced 25 generic armor inventory routes across Leather, Copper, Chainmail, Iron, Gold, Diamond and Netherite with per-piece defenses, durability, exact recipes or upgrades, selected acquisition routes and repair values. Preserved the already-substantive Copper, Iron and Diamond Chestplates, custom armor, Turtle Shell and shared mechanics owners. These are existing item-page expansions, not new items or an exhaustive loot survey.
- Verified ordinary versus optional-rebalanced trade selection, Chainmail gifts and recipe-output absence, Netherite component-preserving upgrades and dropped-item fire resistance, Leather dye/freezing/boots rules and Golden armor's bounded Piglin check. Active attributes lost on breakage are distinguished from still-checked passive tags and Protection contributions; the intended broader policy remains under #800 review.
- Exact baseline/canonical guards, all 125 previous heading IDs, configured citation/local-link review, independent source review, the actual integrated documentation check and strict build apply. No gameplay crafting, trade, equipment, damage, repair, smithing or inventory-browser tests were run.

## Batch 195: Invisibility, Glowing and Nausea

- Added one shared owner and three alphabetical effect routes, preserving the prior 32 entries and bringing the directory to 35 reviewed effects. Narrow links in Vision Effects, Blue Jay, Skunk Spray and Spectral Arrow preserve their current owners; Spectral Arrow is a discovery correction, not a complete item rewrite.
- Traced AI visibility factors and target exceptions separately from actual living-body, armor-layer and native outline transport. An invisible body uses outline-only geometry only when it is invisible to the viewer; permitted spectators/team viewers retain their translucent-body branch.
- Verified the active Nausea green-overlay path and Distortion Effects setting separately from the unconsumed old spinning timer and absent active camera warp. Source-derived appearance is qualified for admitted models and the inspected native route, with no gameplay or shader-pack visual comparison. Acquisition, duration, clearing and command permissions are independently checked.
- Final canonical/index guards, preserved heading IDs, configured source-link rendering, independent source review, hierarchy check and strict build apply. No gameplay detection, rendering, brewing, trade, combat, command or timing tests were run.

## Batch 196: Rain Frog care and default integration limits

- Expanded the existing Rain Frog owner with deliberate acquisition, sand burrowing, Shovel persistence, inherited despawn and egg-created offspring, conditional hazards and complete default loot/XP scope. Two narrow discovery links preserve the existing navigation owners.
- Traced the missing breeding tag and whole insect-tag rejection through the active static-registry loader and holder binding. A guarded Leafcutter Ant Pupa note now distinguishes being named in bundled JSON from active tag membership; its real Anthill interaction is preserved. Inspected packaged structures and archives provide no hidden default spawn/tag override.
- Qualified the client-only ordinary Jukebox dance trigger separately from the server weather countdown. The species-specific model path is linked to existing [#803](https://github.com/HungLo2020/MattMC/issues/803), with conditional admission and no reproduced runtime failure or claim about every imported mob. Food and weather findings are documented limitations, not gameplay fixes or assumed new tickets.
- Exact baseline/canonical guards, preserved anchors, independent source/render review, hierarchy check and strict build apply. No gameplay spawning, Shovel, feeding, breeding, weather, loot, XP or rendering tests were run.

## Batch 197: Luck, Unluck and loot context

- Added one shared owner and two alphabetical routes, preserving all 35 prior effect entries and bringing the directory to 37 reviewed effects. A narrow Fishing discovery link preserves its existing mechanics.
- Traced additive effect attributes, real loot-context forwarding, weighted selection and bonus-roll arithmetic separately from fishing enchantments. The complete inspected base and optional loot scopes use nonzero quality only for fishing categories; passing a player's luck to a chest, credited kill, brushing or Vault context does not itself establish a default reward bonus. Custom tables remain explicitly separate.
- Verified registered Luck potion/browser forms, absence of an ingredient route to Luck contents, and working generic container conversions once a Luck bottle exists. Unluck registration does not create an ordinary potion type. Delivery, timing, effect coexistence, permissions and clearing are source-qualified.
- Exact canonical/index guards, independent source/render review, separated citation bindings, preserved anchors, integrated hierarchy check and strict build apply. No gameplay potion, fishing-distribution, loot, trade, command or timing tests were run.

## Batch 198: Hurt- and death-triggered effects

- Added one shared owner for Wind Charged, Weaving, Oozing and Infested, with four alphabetical routes preserving all 37 prior entries. Five narrow discovery links retain the existing Wind Charge, Slime, Silverfish, Cobweb and Trial Spawner owners.
- Traced admitted hurt/Totem ordering, ordinary killed-removal timing and effect expiry, Creeper's explicit extra dispatch and non-guaranteed ordering of simultaneous death effects. Direct effect-created mobs, natural spawn gates, Peaceful removal, nearby-Slime counting and Weaving block-placement rules remain distinct.
- Verified the living-bearer wind burst's no-damage and trigger-block behavior without copying projectile-specific fall protection or Breeze griefing rules. Brewing, durations, delivery and ominous cached potion selection/timing are checked through active consumers.
- Exact canonical/index guards, independent source/render review, pinned citations, preserved owner anchors, actual hierarchy check and strict build apply. No gameplay brewing, damage, death, spawning, block-placement, cloud, farm-output or timing tests were run.

## Batch 199: Omens, Hero rewards and effect directory inventory

- Added one shared owner for Bad Omen, Raid Omen, Trial Omen and Hero of the Village, with guarded links in five existing owners. Preserved the prior 41 effect entries and Triggered's Trial Spawner discovery paragraph.
- Traced bottle and complete reward-table acquisition, village/raid and trial state transitions, stored-position timing, separate player/block cooldowns, permission and gamerule gates, actual trade rounding/clamps and eligible gift checks. Nearby active raids exclude the Pillager captain bottle route; gifts have no guaranteed wall-clock interval.
- Independently reconciled all 45 direct MobEffects.java registrations with distinct directory routes and corrected the current directory qualifier accordingly. This is checked source registration-route coverage, not every interaction, a runtime registry inventory or broad wiki completion.
- Exact frozen owner/navigation guards, source/render review, two comparison tables, preserved anchors and index rows, actual hierarchy check and strict build apply. No gameplay bottle, raid, trial, Milk, multiplayer, trade or gift tests were run.

## Batch 200: Mobility enchantments

- Added a shared Depth Strider, Frost Walker, Soul Speed and Swift Sneak owner with four guarded discovery links. Exact supported material/slot sets, ordinary levels, table-versus-treasure selection, exclusions and acquisition distinguish equipment choices without duplicating the linked movement/block owners.
- Traced active water and input attributes separately from location effects, Frosted Ice placement/timers, terrain predicates and Soul Speed wear. Attribute inputs are not measured travel-speed guarantees; optional trade-rebalance pools remain separate from ordinary routes.
- Verified immediate break cleanup and normal broken-equipment rejection alongside the later location iterator that can re-enter Frost Walker/Soul Speed. This is source-described behavior under the unresolved [#800 policy review](https://github.com/HungLo2020/MattMC/issues/800), not an implemented fix or a blanket passive-effect contract.
- Exact canonical/baseline guards, independent source/render review, preserved anchors, active caller and source-range checks, actual hierarchy check and strict build apply. No gameplay movement, frost, damage, wear, fishing, barter, trade or chest-opening tests were run.

## Batch 201: Browser protocol admission correction

- Corrected the shared inventory-browser owner and its mode/navigation guidance after tracing the actual client and server contexts, active codec modifier, both transports and skip handling. Visibility remains available in Survival; ordinary insertion requires infinite materials, normally Creative. No runtime failure or disconnect is claimed.
- Preserved real recipes, loot, normal item-use/consumption and operator-category permissions. Corrected replacement-supply advice in Death and Respawn and related equipment/mode summaries. Broader explicit per-item and per-mob acquisition claims are a separately reviewed continuation of this correction.
- Added durable authoring/continuation checks so a permissive downstream handler cannot again stand in for protocol admission. Existing historical monthly entries and batch descriptions are retained with the current correction clearly identified.
- Source proof was independently reproduced. Exact before/after guards, unchanged-source verification, preserved headings, rendered citations, the required documentation check and strict build apply. No game, browser-click or network runtime test was run.

## Batch 202: Item browser acquisition wording

- Applied 362 guarded paragraph replacements across 361 existing item pages: 292 acquisition-correction files and 69 clarification-only files. Ambiguous listing wording retains true Survival catalog visibility while distinguishing Creative insertion. Fifteen other inventory candidates remain unchanged.
- Preserved normal Survival egg use, consumption, recovery, recipe/loot routes and operator conditions. Knowledge Book and Mule wording were checked as mixed cases; Spawn Eggs' separate no-mode-change supply inference was corrected. No item registry, recipe, gameplay or packet code changed.
- Independent review checked all 98 distinct wording pairs and the remaining browser/mode contexts across 1,880 item pages. Exact baseline/output guards, all 1,992 original anchors and 8,682 outgoing links are preserved; configured local-link checks, actual hierarchy/citation checks and strict build apply. No runtime inventory test was performed.

## Batch 203: Remaining player-guide browser acquisition wording

- Corrected 107 guarded paragraphs across 100 existing guides: 68 mobs, 15 blocks, eight effects, three enchanting guides, three structures, two biomes and one dimension. The reviewed set contains 104 false acquisition assertions and three visibility/acquisition clarifications; these are paragraph counts, not newly completed subjects.
- Preserved valid Survival egg use, consumption, recovery, natural sources, recipes, loot and operator conditions. The derived Bedrock acquisition inference is corrected, and the shared Inventory Browser guide owns the server admission boundary. No gameplay, registry, recipe or network code changed.
- Reconstructed the exact proposal rules after workspace recovery and checked every scoped baseline and guarded replacement. The preceding item batch was recovered to its original full Git tree before publication. Required hierarchy, local-link, citation-rendering and strict-build checks apply to this final integrated tree; no runtime inventory or current live-browser test was performed.
- This repairs the audited browser-acquisition wording. It does not complete the wider wiki: generic item summaries, accurate family navigation, TaCZ acquisition/operation and potion-form entry points still need substantive source-grounded work.

## Batch 204: Bow enchantments and tracked insect-food limitation

- Added the shared [Power, Punch, Flame and Infinity guide](../../gameplay/enchanting/BowEnchantments.md), covering supported ammunition, damage/knockback/burning behavior, Infinity consumption and pickup, table limits, books, Anvil combinations and checked acquisition routes. Updated six discovery routes while preserving the Mobility guide and corrected Creative browser boundary.
- Recovered the previously reviewed unpublished Bow owner byte-for-byte, then applied the independently confirmed server-admission correction. The original gameplay prose and its cited source files remain unchanged; new admission references and all final links are rechecked. No source or enchantment data changed.
- Linked the existing Rain Frog and Leafcutter Ant Pupa warnings to [open issue #804](https://github.com/HungLo2020/MattMC/issues/804). This documents a bundled tag-dependency limitation; it is not a gameplay fix. Separate Anteater honey breeding and live-ant healing remain distinct from the failed tag-dependent routes.
- Validation includes exact baseline/output guards, preserved existing anchors, configured citation/local-link checks, the required hierarchy check and strict build. No in-game enchantment, damage, fire, acquisition or food-tag reproduction test was performed.

## Batch 205: Potion-form and tipped-arrow entry points

- Replaced generic [Potion](../../gameplay/items/Potion.md), [Splash Potion](../../gameplay/items/SplashPotion.md) and [Tipped Arrow](../../gameplay/items/TippedArrow.md) entries with source-reviewed acquisition and use. Existing Brewing, effects and weapon owners remain canonical for their detailed systems; all previous item anchors and the new Bow-enchantment discovery paragraph are preserved.
- Covered drink completion and bottle remainder, immediate splash/dispenser delivery, the distinct direct Water-on-soil interaction, eight-arrow crafting, contents-only copying, timed-effect scale, physical-hit ordering, selected Fletcher and Stray/Bogged drop routes, and conditional in-ground potion loss. Catalog visibility remains separate from Creative insertion and actual Survival recipes/loot.
- Independent source review corrected an overlong BottleItem citation before adoption. All 47 cited source files remain byte-identical to the reviewed `8b9173b399a629578a7bf0168e4d3ea32b10e8a6` snapshot after the `cfa7057b6fe2b8dfa84e93f21932be2602eff749` sync; this is not a whole-source or native-collision runtime claim. Exact guards, configured reference/local-link checks, hierarchy check and strict build apply to the integrated tree. No in-game brewing, consumption, throwing, crafting, trading, pickup or timing test was performed.

## Batch 206: Anteater care and active integration limits

- Rewrote [Anteater](../../gameplay/mobs/Anteater.md) with checked egg acquisition, unverified natural encounters, attributes, retaliation, distinct ant pursuit/capture filters, honey breeding, baby riding and animal persistence. Added four guarded discovery links without replacing existing Leafcutter Ant or nest explanations.
- Documented active Dirt/Coarse Dirt actions that leave the block in place and produce 1–2 Sugar per action. The tongue action can reach the same six-health-point recovery state without capturing a live ant; no farm-rate or runtime timing guarantee is made.
- Distinguished working honey breeding and live-ant/Dirt healing from tag-dependent hand/insect routes blocked by [#804](https://github.com/HungLo2020/MattMC/issues/804). Explained the old anger-hook signature without claiming permanent aggression, the inactive name-skin selection, default loot/XP, and the conditional source-identified renderer limitation [#803](https://github.com/HungLo2020/MattMC/issues/803). These are documentation findings, not gameplay fixes.
- Reconstructed this owner from active source after workspace recovery; byte identity to the older unpublished draft is not claimed. Independent review checked the final two prose refinements, 64 pinned references, 31 individually unchanged cited files and all legacy anchors. Required hierarchy, actual reference/local-link QA and strict build apply after integration. No in-game combat, feeding, breeding, spawning, production or rendering test was performed.

- Coordinated 2026-10-03 17:45 UTC review: source commit `cfa7057b6fe2b8dfa84e93f21932be2602eff749` is tracked by the verified [codec progress comment on #769](https://github.com/HungLo2020/MattMC/issues/769#issuecomment-5971784878) and [voxel-query progress comment on #770](https://github.com/HungLo2020/MattMC/issues/770#issuecomment-5971787080). These record bounded Rust kernels while caller policies and compatibility paths remain owned by existing code; neither tracker is closed. The commit author's 54 Java/27 Rust focused results are recorded evidence, not tests rerun by this documentation task; overlapping subsystem counts are not added together. Existing bugs, draft PRs and milestones had no new state changes in that review.

## Batch 207: TaCZ controls, ammunition and acquisition

- Added [TaCZ firearms](../../gameplay/mechanics/TaCZFirearms.md) as the shared player guide for active controls, magazine/reserve distinctions, reload completion, refitting, capacity-loss limits and damage interpretation. Existing workbench transaction guidance is reused.
- Expanded all 29 registered ammunition entries with exact dedicated-workbench recipes, quantities, output batches and groups, while preserving numeric Properties/Used by sections. Corrected the 58 old firing/reload paragraphs, including six ammo types without a declared gun consumer and the Minigun's separate zero-capacity gate. Preserved the Fury item's old URL/anchor while correcting its visible multiplication-sign label.
- Updated narrow Glock/Minigun guidance and four discovery/index routes. Clarified saved-key startup limits, keyboard-repeat behavior, default refit close behavior, ignored recipe attachment presets, and excess-round loss after reducing magazine capacity. These describe current source behavior, not code fixes or runtime guarantees.
- Independent review checked controls/server paths, all 29 recipe/compatibility rows with 290 comparisons, existing anchors, and exact discovery edits. It corrected an omitted capacity-loss warning and duplicated citation before final integration; a direct .308 zero-capacity source link was also added. Required hierarchy, actual citation/local-link checks and strict build apply to the combined source/docs tree. No in-game crafting, combat, refit, settings-persistence or multiplayer test was performed.

## Batch 208: Goal 5 rendering reconciliation

- Audited all 254 paths in source commit `2fff1ef19106350f806ddedd4fb3c3b4fbc44716` through the documentation impact map and coordinated shader/frame/tooling reviews. The eight rendering documentation paths (seven guides and their index) already document most mechanisms; accurate details and historical evidence remain preserved.
- Added the canonical [Goal 5 checkpoint](../rendering/GOAL-5-STATUS.md), connecting shader sources/inputs, ABI 68 culling and shadow roles, selected DH/terrain repairs, lifetime/cost work, verification tools and ten verified tracker progress comments. It distinguishes source implementation, scoped author Java/Rust/live results, 60 independently run Python tooling tests and unresolved acceptance.
- Corrected root PROGRESS.md's current publication state and selected-DH-loss remaining-work row without deleting historical failures or measured results. Clarified the final one-executable/no-Java target versus current Java/Rust boundaries, repaired two subshell/Gradle command sequences and the GPU-overlap crosslink, and added rendering-status navigation from the feature list.
- Preserved 35 historical references across eight player pages after checking their claims. The conditional Citadel limitation #803 remains applicable; entity culling does not add model geometry transport. First-frame Rust-vanilla fallback, bounded expression/material admission, failed MakeUp and DH-extension comparisons, general flicker, performance and resource-bound gaps remain explicit.
- All changes are documentation, including the explicitly requested narrow root progress correction. No gameplay, rendering code, workflow, settings, issue closure or milestone change is authored here. Final independent diff review, hierarchy/link/reference checks and strict build apply; no Java/Rust/gameplay/rendering acceptance rerun is claimed by this documentation pass.

## Batch 209: Bone Meal and Clock utility guides

- Replaced two generic item entries with substantive, source-grounded guides at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`; existing identities and headings remain preserved.
- Bone Meal covers exact acquisition recipes/loot/Composter output, accepted-use consumption versus visible growth, Creative player restoration, block-first interactions, a nonexhaustive growth-family guide, underwater support/water/tag conditions, and Dispenser/Dropper differences. The existing source-only Composter loss warning remains linked to issue #796; no fix or runtime reproduction is implied.
- Clock covers recipes, loot and trade selection, Overworld-only daytime versus other-dimension random display, owner/preview limits, frame controls/comparator semantics and the active selected-model path into Rust rendering. Source review does not establish in-game dial animation or visual acceptance.
- Independent review, pinned source/link/heading checks and the full documentation check/strict build apply. No gameplay, world-generation, automation, rendering or trade test is claimed. These two rewrites advance meaningful item coverage without treating directory presence as completion.

## Batch 210: Bamboo construction-item discovery

- Replaced ten generic Bamboo construction entries with precise discovery guides: planks, mosaic, both slab/stair pairs, fence, fence gate, button and pressure plate. Each retains its item identity and original headings and links the existing canonical placed-block guide.
- Checked exact recipe layouts/counts, raw-Bamboo and Mosaic exclusions, ordinary loot/tool conditions, slab/stair placement, fence/gate behavior and power, and button/pressure-plate triggering at source `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`.
- Separately corrected the already descriptive Acacia Button entry: sturdy-face support, eligible-arrow bounds and scheduled release replace broad opaque-block/projectile wording; its source-grounded discovery route now points to the full Buttons guide.
- Added an authoring reminder to use explicit/collapsed source references and inspect rendered destinations, following the citation collisions caught before batch209 publication. No gameplay or recipe behavior is changed. Independent source review plus the integrated documentation check, strict build and link/reference/anchor QA apply; no runtime crafting, mining, placement or redstone test is claimed.

## Batch 211: Armor trims and pattern templates

- Added the canonical [Armor Trims guide](../../gameplay/mechanics/ArmorTrims.md), replaced all 18 generic pattern-template entries with precise discovery guides, and connected the Mechanics, Smithing and Smithing Table pages. Existing item identities and headings remain preserved; Netherite upgrading remains a separate operation.
- Verified all 18 smithing/duplication recipe pairs, exact ingredients and net copying cost, 11 material providers and 29 tagged armor items. Application consumes inputs, copies existing components and replaces one trim; identical pattern/material produces no output. Visual decoration is distinguished from combat effects and in-game rendering acceptance.
- Acquisition probabilities are tied to their actual chest, archaeology, death-loot or vault selection pools rather than whole expeditions. Tide's no-player-credit/no-Looting pool and Ward/Silence shared selection remain explicit. Independently traced the additional Bolt chest through compatible Trial Chamber pool/template connectors and processors; all 80 decoded NBT evidence rows matched the pinned source objects.
- Source snapshot remains `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Independent source review, old-heading/local-link/pinned-source checks and integrated full documentation check/strict build apply. No natural-generation, loot-frequency, smithing, multiplayer or visual gameplay test is claimed, and registry coverage does not establish broad wiki completion.

## Batch 212: Cockroach and egg-variant guides

- Replaced the Cockroach mob template and three associated item stubs with source-grounded production, care, interactions and current integration limits. The adult ootheca timer, hand-thrown hatch range, growth, Creative consumption and Dispenser item-ejection distinction are explicit. Wings/fragments have no established bundled Survival source or recipe in the inspected snapshot.
- Traced current damage/persistence, dropped-food healing, Maraca priority/removal, dancing and shearing dispatch. Missing spawn wiring, food tags and loot are qualified to the bundled source. Missing-musician dance persistence is a source limitation, not a runtime reproduction or an assumed existing issue association.
- Replaced Blue Egg and Brown Egg templates with variant-specific discovery guides, correcting the ambiguity about edibility. Current cold/warm Chicken acquisition examples, laying restrictions, inherited hatch variants and egg-tag recipe substitution link the canonical Egg and Chicken guides.
- Restored all original section IDs before publication; preserved item identities and current documentation baselines. Source snapshot remains `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Independent source and rendered-reference reviews plus integrated documentation check, strict build and link/anchor checks apply; no spawning, feeding, hatching, dance or other gameplay test is claimed.
- Refreshed the current checkpoint wording for the already-published browser-claim correction and Goal 5 documentation reconciliation while preserving their historical entries. These completed bounded passes do not establish overall wiki completion.

## Batch 213: Player-carrying claim correction

- Corrected three published guides: Crow follow-mode shoulder boarding, Capuchin Monkey head-riding controls, and Movement Effects' Sugar Glider pickup/Slow Falling source. The active shared server riding method rejects Player vehicles because their entity type is not serializable; its rejection precedes the forced-mount and passenger-permission branches.
- Traced server interaction and AI through the actual Mob/Entity methods and per-species callers, including ignored false results. Conditional riding/dismount/effect code remains described as conditional rather than established playable behavior. Client-local attempts do not establish an authoritative server passenger relationship. This is a correction to previous documentation, not a gameplay change or a claimed new regression.
- Verified exclusions: Parrot shoulder storage uses a separate NBT save/discard/recreate route, and a player riding a serializable animal is the opposite passenger direction. Existing supported follow/sit controls, potion effects, other source-reviewed facts, old headings and historical provenance remain preserved.
- Source snapshot is `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Independent source/diff/hash/reference review and integrated full documentation check/strict build apply. No runtime pickup, riding, effect, persistence or multiplayer reproduction was performed. Added authoring reminders for shared dispatch gates/current signatures and published heading-ID preservation to prevent the same evidence mistakes in later expansions.

## Batch 214: Fireworks recipes and active use

- Added the canonical [Fireworks guide](../../gameplay/mechanics/Fireworks.md), replaced generic Firework Rocket/Star entries with useful discovery pages, and connected Mechanics, Elytra, Crossbow and Dyes navigation. Every original item heading ID is retained alongside the new descriptive subheadings.
- Verified the active special recipes, occupied-slot ingredient counts, output quantities, shape/color/trail/flicker/fade limits and component-copy rules. Crafted limits are distinct from customized component bounds; fading preserves the star's other data, while rocket assembly transfers explosion entries rather than the full star stack.
- Traced actual hand/block, Elytra, Crossbow and Dispenser routes, interaction gates, flight expiry timing, collisions, damage/cover conditions and save-state limitations. The current Rust particle and rocket-item routes are present; no in-game visual, combat, flight or persistence acceptance is implied by source review.
- Source snapshot remains `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Independent source/diff/hash/reference review and the integrated full documentation check, strict build, local-link/source-range and old-heading checks apply. No gameplay, workflow, settings, tracker state or incoming source history is changed by this documentation batch.

## Batch 215: Sugar Glider and Flying Fish

- Replaced two imported-mob templates with substantive owner guides, rewrote the Flying Fish bucket entry, and added a narrow Apple-to-Sugar-Glider discovery link. Original page IDs/headings and other Apple content remain preserved; the Apple crosslink is not counted as a full item rewrite.
- Sugar Glider guidance traces actual Apple interaction priority, last-item/result handling, untamed offspring, commands/following and gliding, conditional carrying and owner effects, direct leaf rewards, fallback loot-context rejection and the non-decrementing forage cooldown. The shared player-mount limitation agrees with batch 213. Legacy fall/trample helpers are not presented as proven normal injury/crop-damage defects because active fall handling and size gates provide separate protection.
- Flying Fish guidance separates active 300-air handling from its unused overload, preserves the three-variant distinction, and traces capture/release through the actual MobBucketItem. Release adds the fish and restores common data, while the captured species variant is not restored through the current component path. A matching random reroll is possible; no guarantee that every release visibly changes it is made. Dispensers eject the filled item rather than release its contents.
- Natural-access, food, loot and other implementation limits remain source-qualified at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Independent source reviews, exact hashes, rendered citations and integrated documentation check/strict build apply. No runtime taming, foraging, carrying, gliding, dry-air, bucket, spawning or multiplayer test is claimed. Deferred code leads are separate from documentation changes and are not assumed to belong to existing species-specific issues.

## Batch 216: Coordinated player-carry tracking checkpoint

- Linked the existing Crow, Capuchin Monkey, Sugar Glider and Movement Effects warnings to the verified shared [issue #805](https://github.com/HungLo2020/MattMC/issues/805). The ticket covers retained custom-mob carry/attachment integration, including source-conditional callers; it is not a completed gameplay repair or an assertion that every mount is broken.
- The verified source remains `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`; intervening branch/default changes are documentation. Parrot shoulder NBT and normal player-as-passenger mounts remain outside this limitation. No game code, workflow, settings, issue status or milestone is changed by this documentation checkpoint.
- Added one concise issue-linked monthly entry while preserving all earlier work. Existing source-backed warnings, old headings and gameplay facts are unchanged; their tracking connection is added. Replaced one unavailable legacy Crow feeding branch citation with the reviewed immutable source commit, preserving the recorded issue #784 correction. Full documentation check, strict build and link/reference/anchor QA apply. No runtime riding, persistence or multiplayer reproduction is claimed.

## Batch 217: Mungus and Endergrade owner guides

- Replaced two imported-mob templates with substantive source-grounded care/interaction guides, preserving every original heading ID and existing registered identity. Source snapshot remains `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`.
- Mungus covers accepted mushroom/fungus loading, the normal five-charge limit, matching-plant growth and conditional consumption, periodic Brown Mushroom production and Dispenser-only removal. Active plant placement is distinguished from inactive biome conversion and an ordinary damage explosion that is not called by the reviewed death-burst path. Missing bundled breeding/replacement data remains explicit.
- Endergrade covers active saddle/one-seat boarding with the player as passenger, conditional steering and feeding, flight/containment, retained held food, persistence and saddle recovery. The missing four tags, uncalled boost and actual Poison alias are documented without importing upstream promises; opposite-direction player-carry limitations do not invalidate this saddle mount.
- Independent active-dispatch/source reviews, exact frozen hashes, rendered references/local links and integrated full documentation check/strict build apply. No in-game spawning, riding, steering, shearing, mushroom growth, terrain/biome, damage, loot or save/reload test is claimed. Source leads remain separate from implemented fixes and tracker state.

## Batch 218: Bald Eagle and Mimic Octopus

- Replaced two imported-mob templates and expanded their real coupled item pages: Leather Horse Armor and Bucket of Mimic Octopus. Preserved original identities, section IDs and existing content baselines; no separate Falconry Glove item was invented.
- Bald Eagle guidance traces reachable prey AI, food/superclass ordering, ownership and command/hood interactions, item consumption and return-data limits. The real hand-item check uses Leather Horse Armor; issue #805 and the missing active launch/control callers remain distinct limitations. The armor page preserves its normal Horse role, exact acquisition, attributes and dye/wash behavior.
- Mimic Octopus guidance traces exact feeding/upgrading/command priorities, mimic forms and Sit limitations, moisture/healing versus active underwater air handling, and actual capture/release. The registered MobBucketItem adds a mob and restores common data, but does not preserve the pet-specific state; recapture requires taming. Its category-browser absence is established through active category assembly and helpers, not registry presence alone.
- Updated authoring rules to require resource-path/namespace and nested-reference checks for absent-data claims. Source snapshot remains `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Independent source and exact-hash reviews plus integrated full documentation check, strict build, source/reference/local-link and old-heading checks apply. No live spawning, feeding, riding, control, breathing, bucket, combat, equipment or rendering test is claimed; documentation limitations are not implemented fixes.

## Batch 219: Shoebill and Snow Leopard owner guides

- Replaced two imported-mob templates with source-grounded owner guides, preserving their registered identities and every original heading ID. Existing species-egg discovery links already reach these pages, so no redundant reciprocal edits were added.
- Shoebill covers active prey/retaliation selection, shared melee dispatch, disturbance and fishing. Fishing uses the current empty parameter context with luck: fish/junk remain possible, while the missing entity and origin reject treasure and Bamboo. The initial cooldown is not a guaranteed catch interval. Missing bundled food tags, offspring access, persistence and natural-spawn wiring remain qualified to the inspected source.
- Snow Leopard covers active meat breeding, dropped-food healing without taming, prey/retaliation, ordinary melee and pounce, normal fall handling and persistence. Its bundled breeding, prey and spawn-ground tags are present; their helper predicates alone do not establish natural-spawn wiring. Resource filename/namespace review corrected an unpublished draft before integration.
- Source snapshot remains `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Independent source and frozen-hash reviews plus integrated documentation check, strict build, rendered reference/local-link/source-range and old-heading checks apply. No live fishing, feeding, spawning, combat, fall, loot or save/reload test is claimed; these documentation changes are not gameplay fixes.

## Batch 220: Mimicube and Warped Toad owner guides

- Replaced two imported-mob templates with substantive source-grounded guides, preserving registered identities, every original heading ID and existing species-egg discovery links. Source snapshot remains `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`.
- Mimicube covers recent-attacker equipment copying, slot/count/component limits, broken copied equipment, active melee/ranged/Shield handling, food timing, persistence and actual shared equipment drops. The unused zero-drop and melee-animation helpers are distinguished from the active dispatch. Peaceful eligibility and conditional rendering remain explicit; no loot-farm or combat performance acceptance is implied.
- Warped Toad covers resolved taming/healing/breeding foods, shared feeding order and last-item command transitions, owner controls and following, offspring state, tongue combat and persistence. Swimming permissions are separated from active fire and air protection; care guidance reflects the inspected breathing, lava and water-exit paths. Normal movement fall handling is not generalized to every damage source.
- Both rendering caveats trace their actual species models through the shared conditional [issue #803](https://github.com/HungLo2020/MattMC/issues/803) path. Natural-acquisition and missing-loot claims are bounded to reviewed source/data routes. Resource review distinguishes root-pack loot definitions from optional bundled data-pack overrides.
- Independent source/frozen-hash reviews plus integrated full documentation check, strict build, rendered source/reference/local-link and old-heading checks apply. No live spawning, equipment copying, combat, feeding, taming, breeding, environmental damage, drops, save/reload or rendering test is claimed. Documentation limitations and issue links are not implemented gameplay fixes.

## Batch 221: Naming, leashing and registered-mob coverage

- Replaced the Name Tag and Lead templates with substantive utility guides, preserving all original section IDs and registered identities. Name Tag covers actual default chest chances, optional Trade Rebalance differences, fishing/trading, anvil naming, consumption, eligibility, persistence exceptions and source-wired special names. Its Raccoon visual case is qualified through the current conditional rendering limitation rather than assumed from a Java color helper alone.
- Lead covers the five-String recipe, actual loot/trader-animal supply, shared attachment/transfer/fence/Shears interactions, eligibility, movement, recovery and saved-holder/travel limits. Existing String and Slimeball recipe guidance was already consistent and was preserved. Neither naming nor leashing is described as unconditional permanent survival.
- An independent source census at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`, with navigation pinned to `67af924440caf1b8fee8867638025729fa5c28b1`, found **239 registered EntityTypes and 160 independent mobs**. Every independent mob has an existing owner page and Mobs-index route. This is registration/navigation evidence, not an executed registry dump or certification that all 160 articles are complete.
- The census traced all static registrations, factory helpers and class hierarchies through the active [entity registration helper](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L1636-L1646) and [registry bootstrap](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/core/registries/BuiltInRegistries.java#L166-L168). Boat/raft factories add no extra IDs; the three registered multipart components belong to their species owners. Armor Stand, Mannequin and Player are excluded from independent-mob counts, while the four actual mobs in the MISC category are included. A category or LivingEntity/Mob subclass count alone would be incorrect.
- At that documentation pin, Anaconda, Enderiophage, Bunfungus and Underminer remained template-dominant owners; Giant needed behavior expansion while preserving useful command-only access text. Leafcutter Ant and Skunk retained mixed sections that need careful expansion without replacing their useful nest/spray content. Mimicube and Warped Toad were pending at the audit pin and subsequently published in batch 220. Unmatched pages are not automatically complete, and this mob audit does not close item, block, mechanic, biome or other wiki gaps.
- Independent source/frozen-hash reviews and integrated full documentation check, strict build, rendered references, immutable source ranges, local links and original headings apply. No in-game acquisition, naming, leashing, travel, persistence or visual test is claimed. The census is a documentation planning check, not gameplay implementation or a runtime-content guarantee.

## Batch 222: Anaconda, Bunfungus, Giant and Shed Snake Skin

- Expanded three existing mob owners and the coupled Shed Snake Skin item. Original identities, headings and useful Giant command-access guidance are preserved. Source snapshot remains `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`.
- Anaconda covers active bites/constriction, food pacification, breeding/variant inheritance, multipart handling and persistence. Its narrower kill overload is disconnected from ordinary death dispatch, so the downstream swallowing/healing/shedding sequence is documented conditionally. Shed Snake Skin distinguishes registry/command access from actual category assembly, ordinary supply and uses; Creative availability is not inferred from registration.
- Bunfungus covers actual targeting, close-range combat and its unreachable slam/pursuit limits, food/conversion/breeding/resource gaps, sleep/state persistence and care. Its normal landing override is distinguished from an obsolete damage overload. Conditional Citadel transport and the separate missing sleeping texture are not conflated into a reproduced rendering failure.
- Giant replaces the aggressive-behavior implication with the active empty-goal reality: attack and movement attributes do not themselves supply targeting, pursuit or an attack loop. The guide preserves command-only access and explains naming/persistence, sleep interference, drops/experience and Peaceful limits. The selected semantic rendering path bypasses the older normal-submit equipment gate, so that gate does not establish disappearance in the current world route; submitted equipment appearance remains untested.
- Independent full-call-chain/source reviews, final frozen hashes and integrated documentation check, strict build, rendered source/reference/local-link and old-heading checks apply. No live spawning, biting, constriction, feeding, breeding, swallowing, shedding, combat, persistence, naming, loot or rendering test is claimed. These documentation changes and conditional issue links are not gameplay fixes.

## Batch 223: Enderiophage and Underminer owner guides

- Replaced two imported-mob templates with substantive source-grounded owner guides, retaining registered identities, original headings and existing discovery links. Source snapshot remains `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`.
- Enderiophage covers its actual Poison alias, eligible hosts, attachment and non-player bite lifecycle, cooldown/state handling, care and acquisition. The shared player-vehicle rejection tracked in [issue #805](https://github.com/HungLo2020/MattMC/issues/805) is distinguished from non-player hosts. Animal inheritance controls ordinary distance persistence and base experience despite its category and constructor field; ordinary landing suppression does not establish immunity to host-propagated fall damage.
- Underminer covers current interactions, missing default ore-tag consequences, pickup/held equipment, retaliation, persistence and drops. The Ghostly Pickaxe alias is the ordinary Diamond Pickaxe, and active two-argument pickup and DropChances dispatch take precedence over old one-argument/percentage helpers. Conditional custom-data behavior is not a default encounter or reward guarantee.
- Rendering descriptions follow actual transparency/direct-texture submissions: the Enderiophage and Underminer dwarf models fit the conditional shared [issue #803](https://github.com/HungLo2020/MattMC/issues/803) path, while the tall Underminer model has separate real geometry. An atlas whitelist alone is not treated as proof of selected transport, and no blanket species-visibility result is asserted.
- Independent source/frozen-hash reviews plus integrated full documentation check, strict build, rendered source/reference/local-link and old-heading checks apply. No in-game spawning, host attachment, biting, effect application, guidance, pickup, combat, drops, persistence or rendering test is claimed. These articles document source behavior and limitations rather than gameplay fixes or overall wiki completion.

## Batch 224: Skunk, Leafcutter Ant and cloud-lifetime correction

- Expanded two mixed-content mob owners while preserving their useful spray/cleanup and nest/Pupa/Anteater sections, identities and original heading IDs. Applied a narrow correction to the existing Skunk Spray block owner; it is not counted as a new or wholly rewritten block guide. Source snapshot remains `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`.
- Corrected the published suggestion that the Skunk's end-of-spray copied-potion cloud persists to apply effects. The current default duration is -1; halving it assigns 0, and server removal at the wait boundary precedes effect application. Direct spray Nausea/copied effects, coating behavior, collection and cleanup remain separate valid paths. The Creeper explicitly assigns duration 300 and is an independently checked negative control, so this is not generalized to every area-effect cloud.
- Skunk now covers actual acquisition/food limits, offspring routes, spray triggers, care, persistence and loot. Leafcutter Ant distinguishes Oak Leaves lure from the broader valid leaf-care tag, active queen baby creation from the non-decrementing saved cooldown, and active Pupa-on-Anthill use from null shared offspring and incomplete colony/caravan paths. Worker attributes after loading and current combat/anger handling are explicitly traced.
- Existing [issue #804](https://github.com/HungLo2020/MattMC/issues/804) remains limited to the dependent Anteater food-tag failure; it does not disable every ant, nest or Pupa action. Conditional [issue #803](https://github.com/HungLo2020/MattMC/issues/803) statements follow the actual selected direct-texture model routes and their resource/geometry conditions rather than asserting a reproduced visibility failure.
- Independent source/frozen-hash reviews plus integrated full documentation check, strict build, rendered source/reference/local-link and old-heading checks apply. No live spraying, cloud application, cleanup, ant feeding, colony behavior, combat, spawning, persistence or rendering test is claimed. These are documentation expansions and a corrected claim, not new gameplay fixes or overall wiki completion.

## Batch 225: Shears and Tripwire disarming correction

- Replaced the Shears item template with a substantive source-grounded guide and narrowly corrected the existing Tripwire block guide. Original identities, headings and valid circuit/disarming caveats remain preserved. Source snapshot remains `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`.
- Shears covers actual recipe, trade and connected snowy-shepherd-house loot, tool properties, plant harvesting/trimming, all nine active Shearable species, and separate hand, block, Dispenser, leash and equipment interaction routes. Repair/enchantment claims use loaded tags and data. Broken-stack, Creative and wear exceptions follow the actual dispatcher rather than one assumed universal guard; untraced client-cast leads are excluded from player-facing claims.
- Corrected the published statement that a fully broken held tool cannot mine Tripwire. The selected mining controllers permit the action, and the Tripwire pre-removal handler tests Shears identity without a broken-state check. Block destruction and correct-tool loot eligibility remain distinct. The originally cited `3e85592c4c78ebb420302360667a6c230dc0318d` ItemStack also allowed destruction, so this is a documentation correction rather than a newly introduced gameplay regression.
- Existing String, Slimeball, Mining, Durability and related tool-owner statements were checked for the same contradiction and preserved where already consistent. The Tripwire edit is counted as a narrow corrected claim, not a wholly new block owner. Neither ordinary disarming nor this correction guarantees that every connected circuit remains motionless.
- Independent source/frozen-hash review plus integrated full documentation check, strict build, rendered source/reference/local-link and old-heading checks apply. No live crafting, trade, chest acquisition, harvesting, shearing, cutting, disarming, repair, enchantment or circuit test is claimed. This batch changes documentation only.

## Batch 226: Wooden control item discovery

- Replaced generic discovery prose on exactly 18 existing item pages: Button and Pressure Plate pairs for Birch, Cherry, Crimson, Dark Oak, Jungle, Mangrove, Pale Oak, Spruce and Warped wood. Preserved every original identity and section ID; no new independent block-behavior owner is counted.
- Verified every individual matching-plank recipe, output and active resource ID, registered/localized identity, self-drop table, support/placement rules and Creative category entry. Item pages point to precise material and behavior sections in the existing Buttons and Pressure Plates owners rather than leaving readers at an unspecified block source.
- Shared behavior and source-qualified fuel distinctions were independently checked, including 100-tick Buttons and 300-tick Plates with Crimson/Warped excluded. Canonical circuit, arrow, water/piston and occupancy details remain owned by the block guides; these item entries preserve that navigation without claiming new mechanics.
- The old recipe-advancement reward IDs do not create a claimed unlock failure: current recipe-award tracking is inactive and grid crafting uses the loaded RecipeManager. That cross-file mismatch remains an audit observation rather than a repeated speculative warning in player pages. Resource checks include the three bundled optional packs.
- Source snapshot remains `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Independent per-item source/frozen-hash review plus integrated full documentation check, strict build, rendered source/reference/local-link and original-heading checks apply. No live crafting, mining, placement, fuel or circuit test is claimed, and these 18 improved discovery entries are not an overall item/block coverage score.

## Batch 227: Coordinated digestion and copied-cloud tracking checkpoint

- Connected the already documented Anaconda/Shed Snake Skin callback limitation to verified [issue #806](https://github.com/HungLo2020/MattMC/issues/806), and Skunk/Skunk Spray's optional copied-effect cloud lifetime to verified [issue #807](https://github.com/HungLo2020/MattMC/issues/807). These are four contextual tracking links, not four new behavior articles or completed gameplay repairs.
- Anaconda's active bite/constriction and food pacification/breeding remain distinct from the disconnected kill-to-digestion route. The issue requires full prey-removal and loot/experience policy verification; changing a method signature alone is not accepted proof of correct suppression or lifecycle behavior.
- The Skunk issue concerns only its optional copied-effect cloud. Direct spray effects, coating/bottling/cleanup and the explicitly positive-duration Creeper cloud remain outside that failing construction. The expiry guard runs before the invalid shrink value would reach the ordinary radius update, so no runtime crash claim is introduced.
- Current gameplay source remains `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`; article cutoff `9d68abff4e23f699804646dc86d6f227387136d1` adds documentation only. Both issue bodies were read back against their actual source-qualified scope. No issue status, milestone, game code, workflow or settings are changed by this checkpoint.
- Added one concise monthly tracking entry while preserving all previous entries and links. Exact-diff review, full documentation check, strict build and rendered source/local-link/original-heading QA apply. No in-game combat, digestion, shedding, spray/cloud or multiplayer reproduction is claimed. Broader wiki catch-up remains active.

## Batch 228: Copper Chest item discovery

- Replaced generic discovery prose on all eight existing Copper Chest item forms: four oxidation stages, each waxed or unwaxed. Preserved original identities and section IDs, and linked precise sections of the existing Copper Chests block owner. These are eight item-entry improvements, not eight new placed-block guides.
- Verified the one base recipe and four matching waxing recipes, exact self-drop tables and required-tool recovery, separately spilled inventory, placement/facing and state transitions. Weathered finishes are obtained through the actual aging/scraping routes rather than invented direct crafting recipes.
- Copper Golem construction maps the body stage to an unwaxed chest even when its full copper body block was waxed; waxed-item guidance includes the additional waxing step. Joining behavior can change the resulting finish, so the canonical storage and inventory-retention cautions remain linked.
- The selected facts in the canonical owner remain consistent: its 42 cited source files are unchanged at the current source snapshot. Independent optional-pack/structure/resource closure found no additional bundled chest-production route. Resource and route absence is source-qualified, not a claim about external data packs.
- Source snapshot remains `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Independent per-form source/frozen-hash review plus integrated full documentation check, strict build, actual Markdown reference/local-link and original-heading checks apply. No live crafting, construction, mining, storage, waxing, aging, lightning or circuit test is claimed; broader wiki coverage remains incomplete.

## Batch 229: Copper Golem Statue item discovery

- Replaced generic discovery prose on all eight existing Copper Golem Statue item forms, preserving exact identities and original headings. The pages link the established placed-statue owner for detailed poses, transitions and revival; these are eight item-entry improvements rather than new block-behavior owners.
- Verified actual formation from a living golem into an unwaxed Oxidized statue, subsequent stage/wax transformations and four exact shapeless waxing recipes. Earlier finishes are not assigned invented direct crafting or mob-conversion outputs.
- Item pages distinguish fresh crafted waxed stacks from in-place waxing: crafting does not retain the input's saved custom name or pose, while the checked placed-block transition preserves them. Exact per-form loot, mining, placement and revival conditions remain linked to the canonical rules.
- Independent source and bundled-resource checks covered all eight loot tables, recipe files and optional packs; no selected-claim contradiction was found in the canonical owner. External data packs and customized components remain outside the ordinary bundled behavior described.
- Source snapshot remains `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Independent per-form source/frozen-hash review plus integrated full documentation check, strict build, actual Markdown references/local links and old-heading checks apply. No live formation, crafting, waxing, mining, pose, naming, weathering or revival test is claimed; the broader wiki remains incomplete.

## Batch 230: Copper Torch and Bulb item discovery

- Replaced generic discovery prose on Copper Torch and all eight Copper Bulb item forms, retaining original identities and heading IDs. Precise links keep detailed placed-block and circuit behavior in the existing Copper Lighting owner; these are nine item entries rather than new block-behavior guides.
- Verified all 13 producing recipes and nine loot tables, including each stage's actual shaped Bulb recipe, the four additional Honeycomb recipes, and the distinct Copper Torch ingredient/output route. Item facts are bound to their individual resources rather than assumed from the Copper Chest recipe set.
- Corrected the Torch template's solid-block description with its actual instant-break, non-colliding floor/wall-supported behavior and Lantern ingredient use. Bulb guidance distinguishes lit brightness by oxidation stage, comparator output, power transitions, waxing and aging, with exact tool/recovery rules.
- Independent resource/tag/optional-pack checks found no relevant bundled overrides or selected-claim contradiction in the canonical owners. Registration, placement and current callback paths were inspected; no rendered appearance or measured circuit timing is inferred from assets alone.
- Source snapshot remains `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Independent per-form source/frozen-hash review plus integrated full documentation check, strict build, actual Markdown references/local links and original-heading checks apply. No live crafting, mining, placement, redstone, weathering, waxing or lighting test is claimed; broader wiki catch-up continues.

## Batch 231: Basic Copper item discovery and unpacking correction

- Expanded all 24 full, cut and chiseled Copper item forms across ordinary, exposed, weathered and oxidized stages, with their waxed counterparts. Original page identities and heading IDs remain; precise owner links keep shared construction and weathering mechanics accessible without counting item entries as new block guides.
- Reviewed exact producing recipes and per-form loot, including stonecutter material yields, crafting inputs, waxing and scrape transitions. The full-block entries distinguish valid Copper Golem construction forms from cut and chiseled pieces; resource presence alone is not treated as proof of natural generation.
- Corrected a pre-existing Copper Construction statement that excluded waxed full blocks from ingot recovery: both an ordinary Block of Copper and a Waxed Block of Copper have direct recipes yielding nine Copper Ingots. The waxed recipe was already present at that owner's previous source pin; this is a documentation correction, not a new gameplay change. Weathered, cut and chiseled forms do not gain a direct unpacking recipe from this correction.
- The resource audit covers the 53 producing recipes, 24 exact loot tables, source tool/interaction dispatch and optional bundled packs. Copper Ingot's existing text did not explicitly exclude the waxed recipe and was left intact. No source or tracker changes are part of this batch.
- Synchronized from master `f5473e41dc4af8ced756db517fada27288df07a3`, preserving its priority-queue code, tests and developer documentation. All 334 frozen Copper evidence files remain byte-identical; item citations retain their `2fff1ef19106350f806ddedd4fb3c3b4fbc44716` pin. Independent per-form source/frozen-hash review plus integrated full documentation check, strict build, actual Markdown references/local links and original-heading checks apply. No live crafting, stonecutting, mining, oxidation, waxing, scraping or Golem-construction test is claimed; broader wiki catch-up remains incomplete.

## Batch 232: Bounded monthly-history reconciliation

- Added three concise August and four September entries grounded in actual master history and selected implementation diffs. Entries link verified commits or merged PR 741; no non-PR issue association was established for these outcomes and none was invented.
- Both changelog indexes now include the missing months. January's December heading was a label error in its introducing commit; the corrected visible title preserves the existing heading URL and all body content. Existing monthly entries and links remain intact.
- September's rendering entry distinguishes the sole Rust execution route from retained Java semantic producers/configuration and remaining feature gaps. Historical progress is not presented as fresh runtime validation or as the final no-Java architecture target already being achieved.
- The seven selected outcomes have unambiguous month assignments under both recorded commit offsets and UTC. Other historical work, including September-evening/October-UTC boundaries, remains an evidence-based backlog rather than an invented comprehensive summary.
- Independent source/history and frozen-diff review plus integrated documentation/index checks, strict build, rendered links and original-heading validation apply. No gameplay source, issue, milestone, workflow or historical release was changed; no old benchmark or runtime test was rerun by this documentation batch.

## Batch 233: Fire-starting item reference

- Replaced the generic Flint and Steel and Fire Charge entries with source-grounded acquisition and use guidance. Exact crafting, chest/template bindings, Skyblock reward delivery, Piglin barter, Trial Chamber stock and Creative request rules are distinguished from mere icons or overhead trial projectiles.
- Traced selected block-before-item and entity interaction paths, direct lighting/adjacent-fire fallthrough, portal and game-rule gates, Creative count/wear protection, Dispenser behavior and Small Fireball impacts. Source values are not described as measured damage, safe distances or verified visual effects.
- Documented the narrow retained-broken-tool difference: ordinary held-item ignition and Creeper use reject broken Flint and Steel, while direct TNT selection and its registered Dispenser action can still execute. This is a source-derived behavior distinction, not an implemented fix or a blanket policy judgment; no runtime reproduction is claimed.
- Fire Charge's additional uses include the live Firework Star shape recipe and packaged TaCZ Incendiary Ammo attachment transaction, with its actual table group and carried-material gate. The shared Fire owner delegates exact ignition-item recipes to the expanded entries and retains its fire-physics coverage and existing anchors.
- Gameplay citations retain `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`; the relevant cited and audited sources remain unchanged after master source `f5473e41dc4af8ced756db517fada27288df07a3`. Independent source/resource/dispatch and frozen-hash review plus integrated full documentation check, strict build, actual Markdown/local links and original-heading checks apply. No live crafting, ignition, automation, projectile, damage, repair, portal or visual test was run; broad wiki catch-up continues.

## Batch 234: Spyglass reference

- Replaced the generic Spyglass entry with the canonical Amethyst recipe route, Creative-access limits, one-item stack, ordinary no-durability behavior, either-hand interaction order and the 1,200-tick single-use lifecycle. Existing heading IDs remain intact.
- Traced first-person FOV selection into the selected Rust/Vulkan whole-frame camera path, with separate ordinary mouse and movement-input modifiers. Angular FOV scaling is not presented as measured image magnification; zoom does not itself expand the renderer's normal terrain distance.
- Described the source-wired scope HUD and hand-suppression paths while explicitly leaving the border, item appearance and visual transitions unverified in game. Shared default controls and the third-person/Smooth Camera exceptions remain qualified.
- Audited the existing Brush entry and its current dispatch, loot and durability paths; it already provides substantive, source-correct coverage and was not rewritten. That read-only check is not counted as a new page or expansion.
- Evidence is pinned to source `f5473e41dc4af8ced756db517fada27288df07a3`. Independent source/native-route and frozen-hash review plus full documentation/index checks, strict build, configured Markdown references/local links and original-heading checks apply. No live crafting, scoping, multiplayer or visual test is claimed; broader wiki coverage remains incomplete.

## Batch 235: Copper Slab and Stair item discovery

- Expanded all 16 Cut Copper Slab and Cut Copper Stairs item forms across four oxidation stages and waxed/unwaxed states. Original identities and heading IDs remain; the existing Copper Construction owner still carries detailed shared block behavior.
- Verified 56 producing recipes, eight slab-to-Chiseled-Copper recipes and 16 exact loot tables. Per-form tables distinguish shaped crafting, full-block versus cut-block stonecutter yields, and additional Honeycomb recipes, with exact stage/wax inputs instead of interchangeable family labels.
- Documented same-variant slab merging, one/two-item slab recovery, waterlogging and compatible stair-corner links, correct unbroken pickaxe collection, placed waxing/scraping and random aging. No additional Copper Construction correction was required.
- Source dispatch and resource-loader checks distinguish actual recipe availability from old advancement recipe references; no unsupported recipe-book warning was added. The bundled optional-pack audit found no selected overrides, while external data-pack changes remain qualified.
- Evidence remains pinned to `f5473e41dc4af8ced756db517fada27288df07a3`. Independent per-form source/resource and frozen-hash review plus integrated full documentation/index checks, strict build, configured Markdown references/local links and old-heading checks apply. No live crafting, stonecutting, slab merging, stair placement, mining, weathering, waxing or scraping test is claimed; these item expansions do not establish broad wiki completion.

## Batch 236: Copper Door, Trapdoor and Grate item discovery

- Expanded all 24 Door, Trapdoor and Grate item forms across four oxidation stages and waxed/unwaxed states, preserving their original identities and heading IDs. Shared placed-block rules remain with the existing Copper Construction guide; these are inventory-item expansions rather than new block owners.
- Verified 30 direct production recipes and 24 exact loot tables. Grate entries distinguish full-block stonecutting yields from shaped crafting; Door and Trapdoor entries distinguish their basic ingot recipes from later finish conversions and Honeycomb recipes rather than inventing aged-stage crafting recipes.
- Preserved the actual harvesting distinction: all eight Copper Door forms lack a correct-tool drop requirement, while Grates and Trapdoors require a qualifying unbroken pickaxe. The pages link one-item whole-door recovery, controls, waterlogging, manual/redstone behavior and finish changes to their source-backed owner sections.
- Paired waxed/unwaxed item-display definitions reuse their named bundled model; this asset relationship is not treated as proof of live visual appearance or placed-block behavior. Resource-pack presentation and data-pack recipe/loot changes remain qualified. No canonical-owner contradiction was found in the selected claims.
- Evidence is pinned to `f5473e41dc4af8ced756db517fada27288df07a3`. Independent per-form source/resource and frozen-hash review plus integrated documentation/index checks, strict build, configured Markdown references/local links and original-heading validation apply. No live crafting, mining, aging, waxing, scraping, redstone, waterlogging or rendering test is claimed; broader wiki catch-up remains incomplete.

## Batch 237: Boat and Raft variant reference

- Expanded the 20 non-Oak normal/chest Boat and Raft item forms, including Bamboo and Pewen, with exact registered item/entity identities and named material recipes. Preserved all original heading IDs and linked common placement, rowing, passenger and storage controls to the existing Oak Boat and Transport owners.
- Distinguished two-seat boats from one-seat, 27-slot chest boats, crafting a chest form from attaching a Chest to a placed vehicle, item recovery from separate cargo drops, and ordinary damage from Creative discard. Fuel and Dispenser instructions follow actual tags and registrations rather than the wood appearance of a variant.
- Corrected the generic Pewen item acquisition claims: the ordinary recipe exists but its Pewen Planks input has a separate crafting limitation; the chest-boat definition uses incompatible legacy ingredient objects and is omitted by the active loader. The full loader/codec/error path and bundled override closure were checked; no Java recipe-load or gameplay reproduction is claimed.
- Both Pewen boat forms are absent from the bundled fuel and boat-placement Dispenser registrations; default dispensing ejects their items. These are source-derived limits, not asserted intentional design or implemented fixes. The existing Pewen block owner already documents its three legacy recipe definitions and remains unchanged.
- A serializer-aware inventory of all 1,501 bundled server recipe files found the same three legacy ingredient candidates: Pewen Planks, Button and Chest Boat. Result objects and the separate TaCZ workbench parser were not misclassified. This audit does not create a blanket rule against every object-valued recipe field.
- Evidence is pinned to `f5473e41dc4af8ced756db517fada27288df07a3`. Independent source/resource/dispatch and frozen-hash review plus integrated documentation/index checks, strict build, configured Markdown references/local links and original-heading validation apply. No live crafting, rowing, cargo, recovery, fuel, dispensing or appearance test is claimed; broader wiki catch-up remains incomplete.

## Batch 238: Villager breeding and population

- Filled the Villager owner's explicit breeding/population gap with a substantial new section, preserving its existing jobs/trading passages and all original heading IDs. Added discovery links from Mechanics, Mobs and the Content Guide's farming section; no separate duplicate population page was created.
- Traced the installed IDLE mating behavior, exact four-food point map, pickup and inventory-only sharing thresholds, readiness, reachable free HOME allocation, mating delay, food use before bed-search failure, birth and tick-based parent/baby ages.
- Distinguished the practical spare-bed arrangement from a universal three-bed minimum or a fixed room/headroom recipe. A bed's claim slot is separate from visible sleeping occupancy, and path reachability remains an actual requirement rather than a measured enclosure blueprint.
- Kept mobGriefing's dropped-item pickup/harvest gates separate from stored-Wheat bread-making and the breeding routines themselves. Source-derived failure guidance does not imply that disabling the rule prevents already-fed parents from breeding.
- Synced from master `78e8e0423084f010bb47e36132550619b37644c2` while preserving its six renderer implementation/test paths and the pending documents. All 41 Villager source files remain unchanged. New population evidence stays pinned to `f5473e41dc4af8ced756db517fada27288df07a3`; existing job/trading source history is retained. Independent current-dispatch/source and final-byte review plus integrated full documentation/index checks, strict build, configured Markdown references/local links and old-heading validation apply. No live feeding, pathfinding, breeding, village simulation or growth test is claimed; broader wiki catch-up remains incomplete.

## Batch 239: Shared natural spawning and despawning

- Added the missing shared Natural Spawning owner and linked it from Mechanics, Mobs, Content Guide, Slime and Zombie. Species-specific biome/light predicates, cages, trials, eggs, breeding and events remain with their existing guides; route discovery is not presented as proof of a naturally available species.
- Traced the live server/chunk call through dimension/biome/structure selection, placement and collision, global/local category budgets and active-terrain eligibility. Distinguished the 24-block position exclusions, horizontal chunk-center range and per-mob far-distance checks rather than proposing a universal farm radius.
- Explained category competition and cadence without a hard total-population or breeding cap claim. Distance-retained animals can still count toward natural capacity; required/custom persistence is a separate exclusion. Shared despawn ordering, inactivity and selected Name Tag/bucket/species exceptions remain qualified.
- Included a practical diagnostic sequence for quiet areas and disappearing mobs, without seed-specific coordinates, tested farm geometry, throughput or live timing promises. The new tables use checked category values and source formulas rather than remembered upstream values.
- Evidence remains pinned to `f5473e41dc4af8ced756db517fada27288df07a3`; all 34 supporting files were checked unchanged after source `78e8e0423084f010bb47e36132550619b37644c2`. Independent active-dispatch/source and frozen-hash review plus integrated full documentation/index checks, strict build, configured Markdown references/local links and original-heading preservation apply. No live spawning, despawning, multiplayer, simulation-distance or farm test was run; broader wiki catch-up remains incomplete.

## Batch 240: Rendering ownership and coordinated source review

- Updated Render Architecture and Render Verification for source `78e8e0423084f010bb47e36132550619b37644c2`: prebuilt GUI commands retain their recorded target declarations through route selection and armed submission; blur/custom private intermediates are declared without treating external bindings as GUI-owned targets. Undeclared targets and a second presenter remain rejected.
- Described two added GUI tests and one extended world-frame test as source-inspected coverage. Their mock/test-only routes do not execute the production mid-frame prepare/arm branch or establish a live menu/world-entry result; no dedicated item-raster entry case was added. The custom fixture does not exercise external/depth inputs. Copyable focused commands are provided without claiming they were executed here.
- Linked the existing October implementation entries to verified [native queue progress in #776](https://github.com/HungLo2020/MattMC/issues/776#issuecomment-5975915273) and [GUI target repair in #772](https://github.com/HungLo2020/MattMC/issues/772#issuecomment-5975915809). These are two scoped progress comments, not issue closures or whole-subsystem completion. No existing monthly entry or source link was removed.
- Queue test/benchmark results remain author-recorded, with mixed caller performance and Java graph authority retained. The source-only GUI commit had no exact-head workflow run at this review; the earlier queue Wiki CI is documentation evidence for its own commit, not a renderer-test result for the later fix.
- Independent source/frozen-diff and metadata review plus integrated full documentation/index checks, strict build, configured Markdown references/local links and old-heading preservation apply. No Rust/Java tests, live rendering, gameplay, image comparison or performance measurements were run by this documentation batch. Goal 5 and broader wiki catch-up remain incomplete; the final one-executable, at-most-one-loaded-library, no-Java target is unchanged.

## Batch 241: Gameplay navigation and topic ownership

- Reorganized 36 existing Content Guide bullets verbatim so ordinary/shared systems and TaCZ routes are no longer grouped as Alex's Caves additions. Existing article content remains with its canonical owners; this navigation repair is not counted as 36 newly written gameplay guides.
- Renamed the broad mixed-origin section to Shared gameplay guides while retaining its existing `vanilla-derived-gameplay` anchor. This is the sole authorized heading-text substitution; all prior body lines, links and heading URLs are preserved, including the newly added Villager and Natural Spawning routes.
- Added 11 already-substantial component owners to the Redstone directory and two existing fossil/Dried Ghast routes to Structures. The links point to the actual owner sections rather than creating duplicate guides or claiming unreviewed generated destinations.
- The three navigation candidates were rebased onto the published content tip and checked against the current source context `78e8e0423084f010bb47e36132550619b37644c2`. Independent owner-semantic, configured Markdown and exact preservation checks covered 515 old rendered links, 25 old IDs and 522 local-link occurrences before integration.
- Independent frozen-diff and metadata review plus the integrated documentation/index check, strict build, local/source references and original-heading checks apply. No gameplay source, workflow or site configuration was changed; no runtime test was run. Missing expedition and remaining item-entry work continue, and broader wiki catch-up remains incomplete.

## Batch 242: Trial Chambers expedition

- Added a substantive Trial Chambers owner guide and routes from Structures and the Content Guide. It covers normal-world eligibility and map/locate limits, preparation and room hazards, supply containers, encounter participation and distinct normal/ominous reward choices. Existing Trial Spawner, Vault, Omen, Breeze, Mace and trading pages retain detailed mechanics ownership.
- Source pin `78e8e0423084f010bb47e36132550619b37644c2`: the author and independent reviewer checked 336 source hashes, 191 decoded templates, 47 pools and 50 loot/config resources. Template connectivity is source evidence, not a seeded-generation or guaranteed-layout test. The ordinary reward Chest in an entrance template is explicitly template-specific.
- The guide separates starting height from the full structure extent, candidate spacing from guaranteed frequency, map offer/search eligibility from availability, and random rewards from promises. Existing terrain is not retroactively regenerated by these documentation changes.
- Frozen content and metadata review, integrated documentation/index checks, strict build, configured references/local links and source-range checks apply. No gameplay, world-generation, timing, loot-frequency or visual test was run. Broader wiki catch-up remains incomplete; other missing expedition owners remain in the queue.

## Batch 243: Ancient City expedition

- Added an Ancient City owner and two navigation routes, with source-grounded search/height limits, sculk and Warden precautions, optional layouts, lower-center circuitry, chest rewards and the separate Primordial portal path. Detailed Sculk, Warden and portal behavior remains with its canonical owners.
- Source pin `78e8e0423084f010bb47e36132550619b37644c2`: all 58 bundled Ancient City templates were decoded. Ordinary city and ice-box table assignments are distinct from the optional center template's fixed Golden Apple chest; no room, chest count or template-specific reward is guaranteed for every city.
- The guide qualifies the missing configured wall-stair template with the actual empty-template/no-connector fallback, without claiming whole-city generation failure. It derives center height from placement and anchor data rather than treating the configured start as the ground level, and does not certify a safe route or teleport height.
- Optional Trade Rebalance adds a dedicated Mending roll; ordinary random-enchanted books can already produce Mending, so that dedicated roll is not the total chest probability. Existing historical entries remain unchanged; no issue fix or runtime test is claimed.
- Frozen independent content/metadata review, full documentation/index check and strict build, configured links, pinned sources and preserved anchors apply. No world generation, loot sampling, redstone demonstration, Warden encounter or portal test was run. Broader wiki catch-up remains incomplete.

## Batch 244: Trail Ruins expedition

- Added a Trail Ruins expedition owner plus Structures and Content Guide routes. It covers the six eligible normal-Overworld biomes, search/height and visibility limits, buried-site identification, supported brushing and return expectations. Existing Brush and suspicious-block owners retain detailed mechanics.
- Source pin `78e8e0423084f010bb47e36132550619b37644c2`: independent review checked the 151 author-pinned sources plus four supplementary source files and decoded all 84 referenced templates. Seven Trail Ruins pools, processor assignment and active placement/brushing/loot loading were traced; no generated-world distribution was tested.
- Common and rare substitutions are caps per template-processing pass, not guaranteed counts or a universal 6:3 distribution. The guide gives table-conditional probabilities verified against the current item IDs, including the common table's Clay block. It separates normal material excavation from preserving one-time Suspicious Gravel rewards.
- Frozen content/metadata review, integrated documentation/index check and strict build, configured local/source links and original anchors apply. No in-game search, generation, visibility, excavation, brushing or loot-frequency test was run. Historical changelog entries remain intact; broader wiki catch-up remains incomplete.

## Batch 245: Utility minecart item guides

- Replaced five generic utility-cart entries with source-grounded Chest, Hopper, Furnace, TNT and Command Block guides, linked together from the Content Guide. Existing Transport, Rails, command-system and container owners retain their detailed shared mechanics.
- Source pin `78e8e0423084f010bb47e36132550619b37644c2`: four shapeless recipes and the Chest cart's Mineshaft route are distinguished from the command cart's authorized operator acquisition. Catalog visibility is not proof that inventory-browser insertion succeeds.
- The guides trace actual rail placement and dispenser fallback, variant recovery, Hopper activation, Furnace fuel and TNT gates. Command cart possession/placement, operator-restricted custom data, editor/save permission and command execution are separate checks; its ordinary recovery is a plain Minecart.
- Independent review checked all five frozen bodies, 25 original heading IDs, 81 local links and 59 distinct pinned source URLs/ranges. These are five substantive item-entry expansions, not five new transport systems.
- Frozen metadata/content review and integrated documentation/index checks, strict build, configured source/local links and original anchors apply. No in-game placement, dispensing, command edit/execution, cart transport or explosion test was run. Existing monthly history is preserved; broader wiki catch-up remains incomplete.

## Batch 246: Item Frame and Glow Item Frame

- Replaced two generic frame entries with substantive acquisition, support/placement, insertion, rotation, removal and drop guidance, and added a Content Guide route. Existing map, Comparator and related item owners remain linked.
- Source pin `78e8e0423084f010bb47e36132550619b37644c2`: traced the active client/server interaction dispatch for MattMC's occupied-frame Sneak/Crouch visibility toggle, including the need to refill an empty hidden frame before toggling it. Eight Comparator values are distinguished from four filled-map orientations.
- Glow-frame guidance follows the active item-mesh light path and bounds its claim to inspected rendering inputs; the separate map material route does not justify a verified brightness difference. No universal shader-pack appearance, world-light emission level or spawn-proofing effect is established.
- Independent review checked 67 pinned source files, 19 owner/input documents, all 10 original heading IDs, 40 source definitions and 47 authored local links. Both guides retain current inventory-browser permission/acquisition qualifications.
- Exact content/metadata review and final integrated documentation/index check, strict build, configured local/source links and old anchors apply. No game crafting, placement, hiding, lighting, brightness or circuit test was run. Monthly history remains intact and broader wiki catch-up remains incomplete.

## Batch 247: Mineshaft expedition

- Added a Mineshaft expedition owner and two navigation routes. It distinguishes normal and Badlands variants, search and generation eligibility, route preparation, cave-spider/spawner hazards, chest-cart rewards, material recovery and revisit limits. Existing Transport, Rails, Cave Spider, Cobweb and Monster Spawner owners remain linked.
- Source pin `78e8e0423084f010bb47e36132550619b37644c2`: the guide follows the registered procedural generator and loaded biome tags, not a fabricated fixed room-template inventory. The 50 normal and three mesa eligible biomes are distinct from later piece-level liquid and Deep Dark exclusions.
- Candidate frequency, corridor flags, spawner attempts and chest-cart attempts are not guaranteed world counts or travel distances. Loot quantities describe selected entries, and consumed cart loot does not reroll on revisiting.
- Cobweb and spawner recovery follows both block loot and the usable correct-tool gate. Silk Touch alone does not establish a drop with an unsuitable tool, and preserving a spawner is not a tested farm-output claim.
- Independent frozen content/metadata review, final documentation/index check and strict build, configured references/local links and preserved anchors apply. No generation, combat, lighting, harvesting, cart-loot, railway or farm test was run. Monthly history is retained and broader wiki catch-up remains incomplete.

## Batch 248: Igloo expedition

- Added an Igloo expedition owner and two navigation routes. It covers the three eligible snowy biomes, surface/shaft inspection, optional basement placement, infested masonry, finite supplies, curing prerequisites and a recoverable return route. Existing Zombie Villager, Villager, Brewing and Snow owners retain detailed shared mechanics.
- Source pin `78e8e0423084f010bb47e36132550619b37644c2`: independent review checked 55 source files, all three decoded templates and 26 content assertions. The 50% basement piece-selection branch does not guarantee a surviving, untouched basement in a particular world.
- The supplied Weakness splash is already prepared but the Brewing Stand has zero saved fuel. The Chest's fixed Golden Apple pool and other random selections are distinguished from supplies in every surface igloo; replacement brewing requires its own ingredients and fuel.
- Persistent residents can still be lost to damage or difficulty rules, including Peaceful removal of the Zombie Villager during conversion. A successful cure can provide a second surviving Villager, not a completed breeding or trading setup.
- Frozen independent content/metadata review, final documentation/index check and strict build, configured references/local links and old anchors apply. No structure search, terrain survey, cure, encounter or resident-transport test was run. Monthly history is preserved and broader wiki catch-up remains incomplete.

## Batch 249: Ruined Portal expedition

- Added a Ruined Portal owner and two navigation routes. The guide joins seven variant-specific search/placement rules, all-variant locate syntax, hazards, optional loot and salvage, ordinary frame repair and onward-travel preparation. Nether Portal, Primordial Caves and harvesting owners retain their detailed mechanics.
- Source pin `78e8e0423084f010bb47e36132550619b37644c2`: independent review checked 135 pinned files, all 13 decoded templates, seven resolved variant tags, the 25-entry main loot pool and separate optional Lodestone pool. Raw chest assignment does not guarantee an intact accessible chest after placement.
- Template selection, gold removal, Obsidian aging and fluid/terrain alterations are distinguished from guaranteed supplies. Crying Obsidian is not valid ordinary portal-frame material; salvage does not automatically produce a working or safely linked portal.
- The guide distinguishes an ordinary Nether portal repair from the existing Pitcher Pod conversion route, and honors the loaded three-biome Primordial dimension definition when assessing structure eligibility.
- Frozen independent content/metadata review, final documentation/index check and strict build, configured references/local links and preserved anchors apply. No world generation, chest sampling, mining, repair, ignition, travel or visual test was run. Existing monthly history remains intact; broader wiki catch-up remains incomplete.

## Batch 250: Paper and player-written books

- Expanded Paper, Book and Quill, and Written Book into practical acquisition and writing guides, with one Content Guide route. Existing Book, Sugar Cane, Lectern, Bookshelves, map/cartography and trading owners remain linked.
- Source pin `78e8e0423084f010bb47e36132550619b37644c2`: active editing, saving, signing, opening and copying routes are traced through UI, packet validation, server conversion and loaded recipes. Done saves the editable notebook; Escape follows close-only behavior. Normal signing-title limits are separated from wider data/packet limits.
- Copying preserves the source book and author, increments the generation and consumes qualifying unsigned notebooks even when they contain notes. A second signed source invalidates the recipe, and generation-two copies do not create another crafting generation.
- Acquisition follows real recipes, loot and current merchant-price calculation, including the writable-book payment stack clamp. The unsupported generic Written Book catalog-acquisition claim is replaced with the verified signing route and category/browser limits.
- Independent review verified 98 source files, 88 source definitions, 113 rendered source uses, 68 local links and all 15 original heading IDs. Frozen metadata/content review and final integrated documentation/index checks, strict build, configured references/local links and old anchors apply.
- No game editing, signing, copying, trading, reading or storage test was run. Existing monthly history remains intact and broader wiki catch-up remains incomplete.

## Batch 251: Monster Rooms and destination-index distinctions

- Added a Monster Room expedition owner plus navigation, covering all five ordinary/custom placement routes, cave approach, optional chests, the 26-entry reward table, cage choices, preservation, material recovery and return limits. Monster Spawner remains the detailed device-behavior owner.
- Source pin `78e8e0423084f010bb47e36132550619b37644c2`: independent review checked all 119 author-manifest source hashes, with 132 source files checked in total, and reproduced five placements and 116 biome/stage memberships. Candidate counts and geometry checks do not establish generated room frequency or guaranteed chests.
- Monster Rooms are active biome features, not registered structure starts: their decoration branch is separate from Generate Structures and they have no locate-structure target. The Structures index now states that boundary explicitly.
- Corrected the index's obsolete two-biome Primordial summary to the loaded dimension's Dry Midlands, Primordial Plains and Primordial Ocean, with current loading/override citations. Existing fortress/stronghold absence from those allowed-biome tags remains qualified; no old terrain is retroactively changed by documentation.
- Frozen independent content/metadata review, final documentation/index check and strict build, configured references/local links and old anchors apply. No world generation, room search, combat, cage operation, loot sampling or harvesting test was run. Existing monthly history is retained; broader wiki catch-up remains incomplete.

## Batch 252: Village expedition

- Added a Village destination owner and two navigation routes. It joins five-style biome search, real map/locate routes, arrival and resident protection, normal/zombie layouts, crop/material collection, house/workshop loot and return planning. Existing Villager breeding/jobs, Trading, Raid and Iron Golem owners retain their substantial system guidance.
- Source pin `78e8e0423084f010bb47e36132550619b37644c2`: all 649 frozen source files match the source checkpoint, and 478 conservatively reachable village templates were independently decoded. Pool reachability is an over-approximation; it does not certify connector fit, collision/depth acceptance or any generated population/building count.
- Map availability depends on the Cartographer's stored type, valid-offer selection and successful bounded search. A village destination or a style name does not guarantee a particular workshop, chest, resident or Camel.
- The guide distinguishes ordinary and zombie processor/resident routes, table-specific optional loot and non-refilling containers. Farm wording is bounded to pieces that actually contain Wheat; other templates already contain different crops or stems, so the substitution table is not an inventory of every farm.
- Frozen independent content/metadata review, final documentation/index check and strict build, configured sources/local links and preserved anchors apply. No generated-village survey, map purchase, resident behavior, harvest, chest-opening or travel test was run. Monthly history stays intact; this destination guide does not establish broad wiki completeness.

## Batch 253: Painting item workflow

- Expanded the Painting entry with recipe/trade acquisition, wall support and placement permissions, maximum-area random selection, preset placement sequence, recovery and practical size-control guidance. Added a Content Guide route while preserving existing display owners.
- Source pin `78e8e0423084f010bb47e36132550619b37644c2`: the bundled data contains 51 variants and 47 normally placeable choices in nine size groups. Four elemental variants are excluded from normal random placement and have permission-gated catalog presets; these counts are not limits on data packs.
- The Master Shepherd offer returns three Paintings for a base two Emeralds, with ordinary price modifiers still applicable. Recovering a placed painting returns an ordinary item without preserving its selected picture. Current browser listing/insertion qualifications remain.
- Active rendering inputs and submission are source-inspected without claiming verified appearance for every artwork, resource pack or shader. No third-party artwork was copied into the wiki.
- Frozen independent content/metadata review, final documentation/index check and strict build, configured local/source links and old anchors apply. No game crafting, trading, placement, reroll, recovery or visual test was run. Historical entries remain intact and broader wiki catch-up remains incomplete.

## Batch 254: Metal horse armor

- Expanded Copper, Iron, Golden, Diamond and Netherite Horse Armor entries with current acquisition, protection, equipment/removal and recovery facts, and added a Content Guide route. Horse, Saddle, Armor and Smithing remain the shared mechanic owners.
- Source pin `78e8e0423084f010bb47e36132550619b37644c2`: acquisition distinguishes actual chest-table and optional-pack routes, recycling and the valid loaded Netherite smithing recipe. The Netherite route was checked through the current Ingredient and TransmuteResult codecs and menu behavior rather than inferred from upstream conventions.
- Ordinary hand equipping follows the Horse-specific interaction path; component presence alone does not establish generic equip-on-interact behavior. Default durability/enchantability, supported tags, dye/trim limits and actual armor attributes are kept distinct.
- Golden armor's Piglin admiration behavior is not bartering, and Netherite dropped-item fire resistance is not a claim that the wearing Horse becomes fireproof. Current browser acquisition and permission qualifications remain.
- Frozen independent content/metadata review, final documentation/index check and strict build, configured local/source links and all original headings apply. No game looting, crafting/smithing, equipping, damage, fire, death-drop or trading test was run. Historical entries remain intact and broader wiki catch-up remains incomplete.

## Batch 255: TaCZ extended magazine acquisition and capacity

- Expanded the six Light/Heavy Extended Mag I/II/III entries with exact Attachment Table recipes, group/output information, refitting links and gun-specific capacities. Existing item identities, levels, properties and compatible-gun rows are preserved; canonical workbench/firearm owners remain authoritative.
- Source pin `78e8e0423084f010bb47e36132550619b37644c2`: the six pages cover 37 compatible guns and 111 gun/tier capacity results. The custom TaCZ recipe loader accepts its nested material schema; ordinary Minecraft Ingredient parsing is not substituted for that active loader.
- Accepted Light III on M1A1 Thompson and Heavy III on M1 Carbine select literal one-round capacities. The guide does not normalize these values or infer intent from conflicting raw imported allow-tags. Fitting preserves the current loaded count; a later ammo write can clamp it, so the source-derived examples distinguish installation from loss on the next successful shot.
- Loose attachment English tooltips use fixed 21/24/33-round labels that are not the actual capacity for every gun. The guides point readers to the gun-specific values and retain the current browser acquisition limits. No gameplay repair or runtime reproduction is claimed.
- Frozen independent content/metadata review, final documentation/index check and strict build, configured references/local links and all original anchors apply. No crafting, refit, reload, firing or UI test was run in-game. Monthly history remains intact and broader wiki catch-up remains incomplete.

## Batch 256: 05:45 coordinated issue checkpoint

- The review's gameplay source remains `78e8e0423084f010bb47e36132550619b37644c2`; completed documentation batches through 255 were promoted and deployed separately. No new gameplay implementation or migration completion is asserted by this checkpoint.
- Linked the Ancient City pool-reference limitation to [#808](https://github.com/HungLo2020/MattMC/issues/808). The missing single-template candidate has no attachment jigsaws under the default fallback; other shuffled candidates and external template overrides remain possible. This is not a claim of total city-generation failure or a runtime-observed missing room.
- Linked the two tier-III magazine exception guides to [#809](https://github.com/HungLo2020/MattMC/issues/809). The issue requires an explicit intended compatibility/capacity decision, safe loaded-ammunition behavior and matching descriptions; the wiki does not guess replacement values or broaden other guns' excluded attachment support.
- Both issue reports were read back and verified as newly tracked, open source-qualified limitations. The two issue creations are not implemented fixes, and neither the tracker review nor this documentation checkpoint ran gameplay tests. Related generic tooltip evidence stays with the capacity issue rather than a separate per-label ticket.
- Existing article facts, source pins, original headings and historical entries remain preserved apart from the three narrow issue-link additions and append-only checkpoint records. Independent exact-diff review, full documentation/index check and strict build, configured references/local links and old-anchor checks apply. Broader wiki catch-up remains incomplete; isolated compact-sight drafts continue after the report cutoff.

## Batch 257: TaCZ compact sights

- Expanded ACRO P-1, DeltaPoint and FastFire sight entries and their Rised variants with exact Attachment Table Scope recipes, active fitting lists, refit links and configured zoom. Existing headings, properties and compatibility rows remain preserved, with one family route added to the Content Guide.
- Source pin `78e8e0423084f010bb47e36132550619b37644c2`: each pistol variant accepts eight gun IDs and each Rised variant accepts 33 through the active Java checks. The four-versus-six Iron Ingot recipes each also use one Redstone Dust and return one attachment; the ordinary crafting-grid codec is not substituted for TaCZ's custom recipe loader.
- ACRO's configured 2x and DeltaPoint/FastFire's 1.5x values are traced through active first-person FOV and camera-recoil consumers. These are inspected source settings, not measured runtime magnification or guaranteed reticle/resource-pack appearance.
- Imported weight, ADS and accuracy/spread metadata is distinguished from consumed bonuses in this snapshot. The guides preserve current browser category/insertion qualifications and canonical workbench/firearm ownership, without promising universal gun compatibility.
- Frozen independent content/metadata review, final documentation/index check and strict build, configured references/local links and old anchors apply. No in-game crafting, fitting, aiming, recoil, reticle or visual test was run. Monthly history remains intact and broader wiki catch-up remains incomplete.

## Batch 258: Desert-well archaeology and sherds

- Expanded Arms Up and Brewer Pottery Sherd entries and added practical excavation/loot guidance to the existing Desert-well section, with a Content Guide route. Brush, suspicious-block and Decorated Pot owners retain their detailed behavior. Existing unrelated biome prose, references and headings remain intact.
- New well/item evidence is pinned to `78e8e0423084f010bb47e36132550619b37644c2`. The two selections independently choose among five water columns at different depths: all 25 combinations keep distinct block positions, including same-column choices. Placement positions do not guarantee two surviving recoverable finds in an encountered world.
- Guidance preserves support, works down through shared columns, and distinguishes water targeting from water support. The assigned well loot gives each requested sherd weight 2 of 8; these are source-table probabilities, not measured excavation yield or a per-well guarantee.
- Pot crafting/pattern and shattering guidance distinguishes accepted ingredients from mapped artwork and ingredient identity from plain fallback appearance. Arms Up/Brewer have mapped patterns; using other accepted but unmapped sherds is not described as producing another visible design or a Brick refund.
- The new well additions have their own review scope and current pin; unreviewed older biome sections were not silently repinned. Frozen independent content/metadata review, final documentation/index check and strict build, configured references/local links and old anchors apply. No game search, underwater excavation, crafting, rendering, breaking or browser test was run. Monthly history remains intact and broader wiki catch-up remains incomplete.

## Batch 259: Lightning Rod item variants

- Expanded all eight existing Lightning Rod item entries and added a Content Guide route. The placed Lightning Rod and Copper Construction guides retain their behavior ownership; item pages distinguish inventory recipes from placed-block aging, waxing and scraping.
- Evidence is pinned to `78e8e0423084f010bb47e36132550619b37644c2`: three Copper Ingots craft one base rod, and each of the four matching unwaxed rods plus one Honeycomb crafts one waxed rod. The full 1,501-recipe scan found no direct output recipe for the three aged unwaxed forms.
- Each variant has its own block-drop entry and inherits pickaxe/stone-tier requirements. Ordinary recovery requires a qualifying unbroken Stone, Copper, Iron, Diamond or Netherite Pickaxe; Wooden and Golden Pickaxes do not qualify. No elapsed oxidation time or exhaustive structure-acquisition coverage is promised.
- Frozen independent review verifies all eight item bodies, 58 source files, existing headings and configured source/local links. Final integrated documentation/index check and strict build apply. No game crafting, weathering, lightning, mining or browser test was run; source-derived behavior is not a runtime claim. Existing monthly entries and historical checkpoint links remain intact, and broad wiki catch-up remains incomplete.

## Batch 260: TaCZ muzzle brakes and compensator

- Expanded Cthulhu K7, Cyclone D2, Pioneer A3 and T-Rex Heavy Brake plus Tempest Trident Compensator item guides. Added a Content Guide route; the existing firearm/workbench owners retain controls and shared transaction behavior. All existing property tables, compatibility links and headings are preserved.
- Source evidence is pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Exact custom workbench recipes produce one attachment in Muzzle, and both accepted type and exact item ID agree for the same 31-gun fitting set. One Muzzle slot prevents stacking these devices; modifiers from other installed slots can combine.
- Camera pitch/yaw multipliers are Cthulhu 0.85/0.8, Cyclone 0.8/0.7, Pioneer 0.33/1.33, T-Rex 0.66/0.95 and Trident 1/0.6. Pioneer increases yaw; Trident leaves pitch unchanged. These are modifier-stage values, not measured final camera-motion ratios.
- The active path was traced through profile loading, installed-slot collection, modifier evaluation, curve sampling, shot feedback and player-view rotation. Imported ADS/inaccuracy fields are not promoted to aim-speed/accuracy effects, and camera recoil is not described as a direct damage or server-dispersion bonus.
- Frozen independent content review checks recipes, all 63 gun definitions and the 31-gun fit sets, pinned references and existing links/headings. Final integrated documentation/index check and strict build apply. No game crafting, refitting, recoil measurement or browser test was run. Monthly history is append-only; the wider TaCZ item set and broad wiki catch-up remain incomplete.

## Batch 261: Saplings and Flowering Azalea items

- Expanded Birch, Cherry, Dark Oak, Jungle, Pale Oak and Spruce Sapling plus Flowering Azalea item entries, with a Content Guide route. Canonical Saplings and Azaleas, Leaves and Logs pages retain shared behavior ownership; original item headings remain available.
- Source evidence is pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Acquisition distinguishes leaf drops and their Shears/Silk Touch/Fortune/decay conditions, six sapling Wandering Trader offers, Spruce's taiga-house loot, and Flowering Azalea's flowering-leaf/green-Moss routes. The bundled recipe scan found no exact references to these seven IDs.
- Growth distinguishes single-tree Birch/Cherry, single or matching 2-by-2 Jungle/Spruce, and required matching 2-by-2 Dark Oak/Pale Oak. Tree growth is a placed-block process with space/ground and applicable tick or Bone Meal conditions; no fixed elapsed-time guarantee is made.
- Pale Oak saplings select the no-decorator pale_oak_bonemeal feature for both natural ticks and Bone Meal, so this selected feature supplies neither a Creaking Heart nor Pale Moss decorators. This is not a claim about all nearby terrain or naturally generated Pale Oak trees. Flowering Azalea has no natural tree-growth tick and its shared tree feature produces Oak Logs.
- Frozen source/draft hashes, independent content/integration review and final configured links, documentation/index check and strict build apply. No live leaf-breaking, trade, planting, growth, Bone Meal or browser experiment was run. Monthly history and existing guide links remain intact; broader wiki catch-up remains incomplete.

## Batch 262: Desert Pyramid pottery sherds

- Expanded Archer, Miner, Prize and Skull Pottery Sherd item entries and added a Content Guide route. The existing Desert Pyramid, Brush, suspicious-block and Decorated Pot owners retain their detailed behavior scope.
- Evidence is pinned to `78e8e0423084f010bb47e36132550619b37644c2`. The active pyramid archaeology placement assigns an eight-equal-outcome table: each of these sherds has a 12.5% chance per assigned table roll, not a guaranteed find per pyramid or a measured excavation yield. The separate pyramid chest table is not substituted for archaeology loot.
- Guidance preserves suspicious-block support and distinguishes Brush completion from mining, Silk Touch and falling. Loot resolves once on the first successful pulse; stopping and resuming does not reroll the stored find.
- All four are accepted ingredients with actual mapped pot patterns. Four-ingredient crafting, storage insertion versus repainting, tagged-tool shattering versus intact recovery, and separate stored-content drops link back to the shared owners. No artwork comparison or runtime recipe/recovery result is claimed.
- Frozen source/draft hashes, independent content/integration review, final configured references/local anchors, documentation/index check and strict build apply. Existing headings/links and all monthly history are preserved. No world generation, excavation, crafting, rendering, breaking or browser test was run, and broader wiki catch-up remains incomplete.

## Batch 263: TaCZ silencers and suppressors

- Expanded Knight QD, Mirage, Phantom S1, Ptilopsis, 12 Gauge, Ursus, Vulture and Wraith item guides, with a Content Guide route. Exact custom-table recipes, one-item outputs, Muzzle grouping and type-plus-ID compatibility are distinguished from the item names and imported profile data. Existing Properties tables, gun links and heading IDs are retained.
- Source is pinned to `78e8e0423084f010bb47e36132550619b37644c2`. All eight trigger the inspected first-person muzzle-flash suppression predicate; that result is not generalized to remote-player/world rendering or stealth. Actual client/server shot paths still select ordinary gun shooting sounds, and imported silence settings are not used by those consumers. No hearing-distance or measured-audibility claim is made.
- Knight QD, 12 Gauge, Ursus and Vulture also supply active camera-recoil modifiers; Mirage, Phantom S1, Ptilopsis and Wraith add none. Recoil effects concern the camera curve, not server projectile damage or dispersion. Other imported timing, accuracy, range and related fields are not promoted to working effects.
- Exact per-item fitting sets cover 48 unique compatible guns across the family. Wraith's wool ingredient uses the bundled wool-block tag, including mixed qualifying stacks, rather than carpets. Refit controls and shared crafting transactions link to existing owners.
- Frozen draft/source hashes, independent content/integration review and final configured links, documentation/index check and strict build apply. No game crafting, refitting, recoil, audio, remote-player rendering or browser test was run. Monthly history remains append-only; broader TaCZ and wiki catch-up remain incomplete.

## Batch 264: Six classic music discs

- Expanded Music Disc 13, Cat, Blocks, Chirp, Far and Mall entries and added a Content Guide route. The canonical Jukebox guide retains playback, insertion/ejection, transfers, redstone and nearby-mob behavior ownership.
- Source is pinned to `78e8e0423084f010bb47e36132550619b37644c2`. The checked active arrow/damage/death chain preserves fatal-source attacker attribution. The Creeper table's eligible-attacker condition and expanded twelve-disc tag give one equal-weight disc roll with no player-kill requirement or Looting multiplier in that pool. Each selected track has a 1/12 chance per qualifying death, with mob loot enabled; tag membership alone does not establish every listed entity's ordinary attack route.
- 13 and Cat also occur in assigned Monster Room, Woodland Mansion marker and ordinary Ancient City chest tables. Their weights/total are 15/144 with 1-3 pool rolls, 15/127 with 1-3, and 2/86 with 5-10 respectively. These are per-selection fractions, not per-chest or per-structure guarantees. The bundled trade-rebalance city replacement keeps those disc values; fixed/empty/ice-box inventories remain distinct.
- Song IDs, configured lengths, sound events and Comparator strengths are connected to their registry and Jukebox consumers. Configured duration is not measured audio length. No audio, lyrics or artwork were copied, and no crafting recipe for these six discs was found in the inspected resources.
- Frozen source/base/draft hashes, independent content/integration review and final configured links, documentation/index check and strict build apply. Existing headings and history remain intact. No game farm, chest-looting, listening, timed playback or browser experiment was run; broader music-disc and wiki coverage remains incomplete.

## Batch 265: Acacia, Azalea and custom seedlings

- Expanded Acacia Sapling and Azalea entries and refreshed Pewen/Ancient Sapling guides, with a Content Guide route. Existing sapling/tree/leaf/dimension owners and all original item headings remain in place.
- Evidence is pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Acacia acquisition distinguishes leaf loot, trader stock and assigned chest loot; Azalea has leaf/green-Moss routes and Bone Meal-only tree growth. Pewen Branch loot provides an additional sapling route distinct from Pewen Pines loot.
- Pewen and Ancient saplings have active ordinary configured-feature growth, but the first natural starter remains unestablished in the inspected bundled biome-feature closure and structure palettes. The check includes the actual three-biome Primordial Caves override, all 68 biome definitions and 1,202 structure palettes. It is not a claim of absence in every world or data pack.
- Ancient's registered grower selects its ordinary Jungle Log/Ancient Leaves tree. A separate giant grower and 3-by-3 helper do not make the registered sapling select a giant tree; its secondary chance is not a giant-growth probability. Pewen/Ancient lack the inspected ordinary sapling fuel and compost registrations, so vanilla resource-use assumptions are not imported.
- Frozen source/base/draft hashes, independent content/integration review and current combined-tree documentation/index check and strict build apply. The author's isolated source-snapshot build is supplementary evidence and does not replace current integration checks. No harvesting, trading, world search, growth, fuel, composting or browser runtime test was run. Monthly history is preserved and broader wiki catch-up remains incomplete.

## Batch 266: Trail Ruins pottery sherds

- Expanded Burn, Danger, Friend, Heart, Heartbreak, Howl and Sheaf Pottery Sherd item guides and added a Content Guide route. The Trail Ruins, Brush, suspicious-block and Decorated Pot owners retain shared exploration and mechanism details.
- Source evidence is pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Each selected sherd is one of twelve equal outcomes in the assigned Trail Ruins rare archaeology table; none occurs in the common table. The 1/12 chance is conditional on that rare-table roll, not per Suspicious Gravel block encountered or per ruin.
- Active template/pool/processor assignments distinguish house-family rare opportunities from roads and tower-top common processing. Per-template caps and replacement attempts do not establish a universal rare/common ratio or a guaranteed item yield. The source audit includes all 84 Trail Ruins templates.
- Item guidance preserves support, completes Brush extraction and avoids loot-reroll claims. All seven ingredients have actual mapped pot patterns; crafting and recovery distinguish pot faces from the storage slot and intact-item recovery from shattering. No artwork comparison is claimed.
- Frozen source/base/draft hashes, independent content/integration review and final configured links, documentation/index check and strict build apply. Original headings/links and all prior monthly history remain intact. No world search, excavation, crafting, rendering, breaking or browser runtime test was run, and broader wiki catch-up remains incomplete.

## Batch 267: Ocean Ruin pottery sherds

- Expanded Angler, Shelter, Snort, Blade, Explorer, Mourner and Plenty Pottery Sherd entries and added a Content Guide route. Ocean Ruins, Brush, suspicious-block and Decorated Pot owners retain shared behavior and expedition guidance.
- Source evidence is pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Warm-ruin Suspicious Sand uses the Angler/Shelter/Snort table; cold-ruin Suspicious Gravel uses the Blade/Explorer/Mourner/Plenty table. Each selected sherd has weight 1 of 15 in its matching table's single roll. This is conditional on the assigned block/table, not a per-ruin or chest guarantee.
- The replacement cap is per template-processing pass; cold ruins overlay several templates. Placement conditions do not promise a fixed number of surviving suspicious blocks or a sherd yield. The audit includes all 48 ocean-ruin templates.
- Guidance distinguishes fluid targeting from support, maintains an air/surface route, and preserves the one-time stored archaeology reward. All seven have actual mapped pot faces; item storage use does not repaint a pot, and recovery returns existing ingredients rather than multiplying them.
- Frozen source/base/draft hashes, independent content/integration review and final configured links, documentation/index check and strict build apply. Original heading/link compatibility and all prior monthly history are preserved. No generated-world, underwater-brushing, crafting, recovery, visual-pattern or browser gameplay test was run. Broad wiki catch-up remains incomplete.

## Batch 268: Moose Headgear and Sombrero

- Expanded both existing custom headwear item guides and added a Content Guide route. Source evidence is pinned to `78e8e0423084f010bb47e36132550619b37644c2`; shared equipment/armor mechanics retain their existing owners.
- Moose Headgear's final maximum damage is 55: the later Leather helmet property setter overwrites the earlier durability(300) call. Leather repair, enchantability versus actual enchantment-tag eligibility, equipment routes and broken-stack distinctions follow active components and consumers.
- Sombrero's old generic Creative acquisition claim is removed: the registered item is absent from the inspected category entries and bundled recipe/loot sources. Its permission-level-2 command route is distinguished from ordinary Survival acquisition. The swappable=false component skips held-item use; it does not forbid inventory head-slot placement, empty-slot shift-click or the supported dispenser route.
- No item-specific runtime effect is invented from either name. Current armor/broken-equipment behavior is separated from unresolved passive-effect policy; the existing tracked policy discussion is not described as fixed.
- Frozen exact source/base/draft hashes, independent content/integration review and current combined-tree configured links, documentation/index check and strict build apply. The author's older source-docs snapshot check remains supplementary. Original headings/links and previous monthly history are preserved. No gameplay equipment, repair, enchantment, dispenser, command or browser test was run, and broader wiki catch-up remains incomplete.

## Batch 269: Core TaCZ rifle acquisition and behavior

- Expanded AKM, M4A1, SCAR-L, AUG and HK-416A5 item guides, added a Content Guide route and a narrowly scoped shared burst-timing caveat. Existing tables, falloff data, attachment links and heading IDs are preserved; shared firearm/workbench guides retain general controls and transaction ownership.
- Evidence is pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Exact Rifle-group recipes/custom-loader behavior, fresh plain-stack 30-round/AUTO defaults, actual cartridge matching, reload/chamber handling and gun-specific attachment acceptance are separated from imported or unused values. Recipe stock presets and AUG's imported built-in-scope declaration do not establish installed attachments or scope behavior.
- Fixed reload counters, ammo-stack transaction limits and source-driven stat interpretation are documented without measured firing-rate or accuracy claims. Existing numeric tables are interpreted through their active consumers rather than treated as sufficient evidence alone.
- Independent review caught an unpublished SCAR-L two-tick spacing assumption. The corrected page distinguishes configured 800 burst RPM/two-tick task offsets from actual server admission: queued future-stamped rounds can run together while the server has time. The nine-game-tick trigger cooldown is separate. The shared guide adds only that bounded caveat with its own current source pin; no game code was repaired or runtime rate reproduced.
- Frozen corrected source/base/draft hashes, independent content/integration review and final configured links, documentation/index check and strict build apply. The five-page review checks 250 attachment entries and all existing tables/links; new scheduler references are included in the corrected evidence. Prior monthly history is preserved. No live crafting, firing, reload, scope, timing, sound or browser test was run; the remaining rifle families and broad wiki catch-up remain incomplete.

## Batch 270: Remaining classic Creeper-drop discs

- Expanded Music Disc 11, Mellohi, Stal, Strad, Wait and Ward entries and added a Content Guide route. The Jukebox and Creeper owners retain their shared mechanisms; original item headings and related links remain available.
- Evidence is pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Each has a 1/12 chance in the bundled qualifying Creeper-death disc pool, conditioned on the fatal damage source's eligible attacker and ordinary mob-loot path. Membership in that tag does not grant the separate chest routes of other music discs.
- The source/resource scan found no direct recipe output, chest-table item entry or fixed-structure inventory reference for these six. The scope includes optional bundled packs, 7,392 JSON resources and 1,202 structure NBTs searched after decompression where needed; it does not certify arbitrary user data packs or every previously generated world.
- Actual playable components and loaded song consumers connect configured seconds/Comparator values as follows: 11 71/11, Mellohi 96/7, Stal 150/8, Strad 188/9, Wait 238/12 and Ward 251/10. Configured duration is not a measured listening result; no lyrics, audio or artwork were copied.
- Frozen source/base/draft hashes, independent content/integration review and current configured links, documentation/index check and strict build apply. Previous monthly history is preserved. No gameplay farm, loot distribution, listening, timed playback or browser test was run; other disc families and broad wiki catch-up remain incomplete.

## Batch 271: Expedition music discs and fragments

- Expanded Music Disc 5, Disc Fragment, Otherside, Pigstep and Relic item guides and added a Content Guide route. Jukebox and expedition/archaeology owners retain their shared mechanics; Disc 5 owns the exact nine-fragment crafting route.
- Evidence is pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Ordinary Ancient City first-pool selections give fragments weight 4/86 with 1-3 fragments per selection, and Otherside weight 1/86, within 5-10 pool rolls. Otherside additionally uses 1/101 in the stronghold-corridor table and 2/144 in the Monster Room pool. Pigstep is 5/89 in the relevant Bastion-other pool; Relic is 1/12 only on the assigned Trail Ruins rare table. These are conditional table/selection scopes, not per-structure guarantees.
- Active saved-table/template assignment and optional trade-rebalance behavior are distinguished from ordinary tables, fixed inventories and ice-box sources. The city replacement preserves these disc/fragment first-pool probabilities. Existing Ancient City missing-stair issue scope remains with its owner; no world-generation repair is claimed.
- Nine fragments craft one Disc 5, with no implied reverse recycling recipe. These four discs are absent from the classic Creeper-drop tag. Playable components and loaded song records support configured durations and Comparator values, not measured audio or a tested farm. No lyrics, audio or artwork were copied.
- Frozen source/base/draft hashes, independent content/integration review and final configured links, documentation/index check and strict build apply. Original headings/links and monthly history are preserved. No chest-looting, excavation, crafting, listening, timed playback or browser gameplay test was run; other special-disc and broader wiki work remains incomplete.

## Batch 272: Battle and historical Rifle-group firearms

- Expanded FN FAL, HK G3, Gewehr 43, M1 Carbine and StG44 item guides and added a Content Guide route. Existing headings, property/accuracy tables, falloff entries and attachment links remain available, with explicit interpretation where declared values are not attainable through normal Refit.
- Source evidence is pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Exact Rifle-group recipes, fresh-stack defaults, real cartridge matching, chamber/reload state and first-matching-stack transactions are traced through active consumers. Shared firearm/workbench owners retain general mechanisms.
- FAL and G3 active AUTO adjustments are distinguished from base table inputs. StG44's active RPM branch and empty/partial reload counters remain separate from its commented-out ballistic mode block; disabled code is not described as a working damage, speed or spread change. Timing inputs are not measured sustained firing rates.
- Gewehr 43 and StG44 declared extended-capacity rows do not establish accepted Extended Mag installation. M1 Carbine's accepted Heavy III capacity of one remains linked to the existing [tracked limitation](https://github.com/HungLo2020/MattMC/issues/809); this documentation does not repair the gun or broaden its accepted upgrades.
- Frozen source/base/draft hashes, independent content/integration review and final configured links, documentation/index check and strict build apply. The five-page content review checks 57 source identities and all 170 attachment entries. Original links and all prior monthly history remain intact. No live crafting, firing, reload, magazine, accuracy or browser test was run; the remaining weapon families and broader wiki catch-up remain incomplete.

## Batch 273: Coordinated review, 2026-10-04 08:45 UTC

- Reviewed gameplay source remains `78e8e0423084f010bb47e36132550619b37644c2`, unchanged through the published documentation cutoff `aaf735dc5264a913aaa468a955a5ccca8706b433` (Batch 272). The default/wiki refs and source comparison required no new gameplay-source integration. No migration goal, implementation fix or runtime parity result is inferred from documentation deployment.
- Linked the new [shared TaCZ burst scheduling issue #810](https://github.com/HungLo2020/MattMC/issues/810) from the existing firearm and SCAR-L caveats. Future task stamps do not enforce earliest execution in the inspected queue; SCAR-L's 0/2/4 stamps remain distinct from its independently enforced nine-tick trigger cooldown. The finding is source-qualified, not a measurement or a claim that every burst fires simultaneously. The issue records one shared handler concern, not separate gun defects.
- The completed documentation window, Batches 257-272, contains 87 substantive existing item expansions, one scoped biome addition and one scoped mechanics update. Its 16 published commits change 137 paths cumulatively across 92 distinct Markdown files, with no new page or gameplay-source change. These are work-accounting facts, not a declaration that the wiki is complete. The full check remains 2,413 pages and 41 directory indexes.
- A replacement checkout was verified against the exact published Git tree, and its full documentation check and strict build passed. Earlier local supplemental QA logs are unavailable in the replacement workspace; historical per-batch results remain historical. Recovered unpublished drafts must match their approved hashes, or receive a new review, and pass current integration checks before promotion. No runtime or fresh browser verification was added by recovery.
- Continue with the remaining special music-disc and rifle entries, specialist magazine families, ammunition modifiers, adjustable scopes, grips and stocks. Catalog entries and long property tables alone do not establish complete acquisition or active-effect guidance. Use the actual registration/recipe/consumer path and preserve source-qualified limitations.
- This checkpoint adds crosslinks to two player guides and appends monthly/coverage history. Required current checks and exact candidate review apply; the wider catch-up continues after the bounded coordinated report.

## Batch 274: Special music-disc rewards and mob drops

- Expanded Creator, Creator Music Box, Precipice, Lava Chicken and Tears item guides and added a Content Guide route. Existing Trial Chamber, mob and Jukebox owners retain shared exploration and playback behavior.
- Source evidence is pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Full reward-table layers give Creator 3/40 per ominous reward generation, Precipice 1/24 per normal reward generation, and Creator Music Box 5/351 per assigned corridor-pot roll. These conditional reward scopes do not guarantee a find from any particular chamber or arbitrary pot.
- The template audit covers all 191 bundled Trial Chamber templates, including three normal-reward chest assignments and four corridor-pot templates with connected pool references. Vault behavior and the relevant entrance/rubble alternatives remain distinguished from a guaranteed generated layout.
- Lava Chicken requires the qualifying baby Zombie to still ride a Chicken when death loot is evaluated, with player attribution; the Chicken itself is not the disc source. Tears follows returned direct-fireball ownership, collision and fatal-damage attribution. The eligible mob-disc pools add no further random gate, while normal loot conditions still apply.
- Actual playable components, loaded songs, configured durations and Comparator consumers are checked. No audio, lyrics or artwork were copied, and no measured listening or gameplay outcome is claimed.
- The recovered page bodies match their originally approved hashes exactly. Independent content/integration review, current configured references/local links, documentation/index check and strict build apply before promotion. Original headings/links and all previous monthly history are preserved. No generated-world, combat-drop, reward-distribution, listening, timed playback or browser test was run; wider wiki catch-up continues.

## Batch 275: Service rifles and supported fire modes

- Expanded G36K, M16A1, M16A4, QBZ-191 and QBZ-95 item guides and added a Content Guide route. Existing property/accuracy tables, falloff entries, headings and attachment links remain available; shared firearm/workbench owners retain general controls and transaction behavior.
- Source evidence is pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Exact Rifle-group recipes, fresh item state, actual ammunition, active mode/ballistic/ADS/recoil consumers, reload/chamber handling and registered attachment acceptance were checked. All declared extended-magazine tiers are reachable through accepted Heavy I/II/III items.
- G36K and QBZ large magazines can exceed one 60-round ammunition stack. From empty, the first matching-stack transaction can leave a partial magazine despite other reserve stacks; additional completed reloads are needed to fill the remainder. These limits are distinguished from maximum capacity and Creative supply behavior.
- M16A1 cannot select its stored burst definition through its actual AUTO/SEMI mode list. M16A4 and QBZ-95 use non-continuous bursts with separate eight-/nine-tick trigger cooldowns. Future task offsets do not promise inter-round spacing; the existing [shared issue #810](https://github.com/HungLo2020/MattMC/issues/810) remains a limitation, not an implemented fix or measured firing-rate result.
- The recovered five page bodies match their originally approved hashes exactly. Independent content/integration review and current configured links, documentation/index check and strict build apply. Content verification checks all 239 attachment entries; the source review includes 70 pinned files with supporting consumers. Previous monthly history is preserved. No live crafting, firing, reload, accuracy, recoil or browser test was run; broader weapon and wiki catch-up continues.

## Batch 276: Precision rifles and active mode differences

- Expanded MK14 EBR, SCAR-H, SKS, SPR-15 HB and Type 81-1 guides with exact Rifle-group recipes, plain fresh-stack defaults, ammunition, reloads, mode-specific active inputs and accepted attachments. Added a Content Guide route while preserving existing headings, property/accuracy tables and falloff/attachment entries.
- Evidence is pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Independent review checks 65 source files and all 245 attachment entries. All 15 advertised Heavy magazine capacities are reachable. The SKS recipe's stored stock preset is not consumed by the active recipe loader; it must be acquired and installed separately.
- MK14 AUTO, SPR SEMI and Type 81 SEMI adjustments are distinguished from unchanged modes and imported fields without active consumers. Type 81's burst definition is unreachable through its supported AUTO/SEMI mode gate. SPR supports a two-round burst and a separate ten-tick trigger cooldown; scheduled offsets do not guarantee spacing under [issue #810](https://github.com/HungLo2020/MattMC/issues/810).
- Recovered page bodies match their original reviewed hashes. Final integration is checked against the current documentation baseline with configured links, the full documentation/index check and strict build. Source-derived RPM, cooldown, spread and recoil inputs are not measured firing rate, accuracy or performance. No game launch, live crafting, reload, damage or browser test is claimed. Broader wiki catch-up remains open.

## Batch 277: Specialist shotgun and sniper magazines

- Expanded Shotgun Ammo Extended Mag I/II/III and Sniper Ammo Extended Mag I/II/III with exact Attachment Table recipes, the Extended Mag craft group, registered fitting rules, absolute gun-specific capacities and reload/refit behavior. Added a Content Guide route; original compatibility rows and heading targets remain available.
- Source is pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Independent review checks all 63 gun definitions and 21 reachable capacity rows: three Shotgun-compatible guns and four Sniper-compatible guns at each tier. Every listed specialist capacity increases; the accepted one-round Thompson/M1 defect in [issue #809](https://github.com/HungLo2020/MattMC/issues/809) is not attributed to these seven guns.
- AA12 and MK14 accept Heavy magazines instead. DB-2, DB-4, Winchester Model 1897, Kar98k and Springfield exclusions are explicit where relevant; raw arrays do not grant fitting support. Fixed generic tier labels of 21/24/33 rounds do not compute these actual capacities.
- Installation changes attachment storage without adding or immediately clamping ammunition. Downsizing can discard excess on the next successful shot. Reloads consume the first matching stack only; the shotgun timing functions use missing capacity and state rather than available reserve count, and ammunition arrives at completion. Imported weight/ADS fields are not advertised as active penalties.
- Final frozen content and integration review, configured links/anchors, documentation/index check and strict build apply. The source review checks 33 cited files and every compatibility/capacity entry. October history remains preserved. No in-game crafting, refit, reload, combat, performance or fresh browser test is claimed; remaining item and broader wiki work continues.

## Batch 278: Ammo Modifier recipes and current effect limits

- Expanded Full Metal Jacket, Hollow-Point, High Explosive, Incendiary and Shotgun Slug attachment guides, with exact recipes, single-item outputs, registered fitting rules and cartridge distinctions. Added a Content Guide route while preserving every original compatibility link and heading ID.
- At source `78e8e0423084f010bb47e36132550619b37644c2`, all five craft in the Attachment Table's Extended Mag group but install in the Ammo Modifier refit slot. Independent checks compare all registered gun definitions and preserve the exact accepted counts: 50 FMJ, 50 Hollow-Point, 14 High Explosive, 50 Incendiary and six Slug guns.
- The inspected firing and hit consumers do not read these attachments to change damage, pierce, speed, spread, explosion or ignition. The attachment profile collector includes Ammo Modifiers for recoil, but these five bundled profiles contain no recoil/recoil_modifier data; zoom uses the Scope slot. This is a bounded statement about current bundled profiles and inspected active consumers, not a guarantee about every future resource pack or consumer.
- Shotgun Slug retains each accepted gun's current projectile count: AA12 10, DB-2 16, DB-4 10, M1014 eight, M870 nine and SPAS-12 eight. The guides do not convert the imported slug name or flags into a working single-projectile effect. Ordinary bullet reactions and reactive block callbacks remain distinct from attachment-driven explosions or ignition.
- Independent source/content review covers 43 source identities, all 170 fit links, preserved heading targets and valid pinned citations. Final frozen integration, configured local/source checks, full documentation/index check and strict build apply before promotion. No gameplay, live refit, damage, explosion, ignition or fresh browser test is claimed. Broader item and wiki catch-up continues.

## Batch 279: Named variable scopes and active optics behavior

- Expanded LPVO 1-6x, Vudu 1-6x, Mark 5 HD 5-25x and Scout 4-10x guides with exact Attachment Table Scope-group recipes, fitting rules and active optic behavior. Added a Content Guide route while preserving original property tables, compatibility links and heading targets.
- Source is pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Active first zoom values are LPVO 6.25x, Vudu 6.5x, Mark 5 HD 5x and Scout 5x, with separate model FOV values 30/30/27/20 degrees. Loaders select the first entries; the registered Zoom key has no active cycling action. Product names and alternate array entries do not establish selectable zoom ranges.
- Exact fitting sets contain 23 guns for LPVO/Scout and 22 for Vudu/Mark 5 HD, with P90 the difference. Imported weights and ADS fields are distinguished from checked active consumers. Scout's active loaders derive scope_standard_8x resources rather than following the index's scope_scout redirects.
- With the bundled LPVO display and geometry, view 2 selects a missing scope_view_2 node. The active Rust whole-frame first-person path falls back to the gun's iron_view positioning, while its zoom/FOV consumer remains independent. Exact resources can be replaced by resource packs; this source finding is not an observed visual failure or a claim about all pack overrides and hot-reload caches.
- Independent source/content review and final frozen integration checks cover the four item pages and navigation/history changes. Configured links, pinned source paths/ranges, full documentation/index check and strict build apply before promotion. No live zoom, refit, crafting, optic-rendering or fresh browser test is claimed.

## Refreshed non-firearm continuation priorities

- Continue source-reviewed equipment enchantments and curses, followed by missing melee utility enchantments; link specialist Crossbow, Trident and Mace owners without duplicating them.
- Expand the Dragon's Breath, Heart of the Sea, Echo Shard, Breeze Rod and Bottle o' Enchanting entry points, connecting exact acquisition and use to existing expedition, brewing and device guides.
- Add practical ore-finding guidance joining actual biome features, generator-height contexts and separate noise veins; distinguish configured sampling from measured yield or guaranteed block locations.
- Follow with creature capture/spawn-egg workflows, custom interaction/resource items, core crafting inputs, Trowel/Armor Stand and day/weather/rest guidance. These are scoped gaps from a sampled audit, not a certification of other pages. Grips and stocks remain useful later item work.
- Coordinate Building Wand's existing documentation correction with unmerged [PR #794](https://github.com/HungLo2020/MattMC/pull/794) for [issue #720](https://github.com/HungLo2020/MattMC/issues/720) rather than producing a competing branch rewrite. Preserve the established distinction between proposed fixes and current master behavior. Broad wiki catch-up remains incomplete.

## Batch 280: Underwater equipment, Thorns and curses

- Added canonical Aqua Affinity/Respiration/Thorns and Binding/Vanishing guides in the existing Enchanting category. Updated its index, Content Guide and the Armor, Grindstone, Enchanted Book and Death/Respawn discovery routes without replacing their established mechanics.
- Evidence is pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Active definitions, complete nested equipment tags, attributes, post-attack dispatch, table/Anvil and selected book acquisition paths, menu/refit restrictions and the real ServerPlayer death path were independently checked across 68 source identities.
- Underwater attributes are removed on break and skipped on re-equipping a broken item, while victim-side Thorns processing has no equivalent broken-stack guard. These are effect-specific current behaviors; [issue #800](https://github.com/HungLo2020/MattMC/issues/800) retains its unresolved passive-policy question rather than establishing a blanket rule for every enchantment.
- Ordinary bundled Thorns primary items resolve to seven chestplates, with Gold's table-selection power reaching at most 49; a plain Book reaches at most 36. Thorns III requires 50, so the guide explains combining/selected trading rather than promising it from a level-30 table offer. Data/component changes remain outside that bundled bound.
- Binding's ordinary removal and swap restrictions persist on retained broken equipment, with explicit Creative and death/keepInventory distinctions. Vanishing's player death filter checks applied enchantments; a book carrying the stored enchantment component is distinguished from equipped/applied curse behavior. Grindstone and repair do not become invented curse-removal routes.
- Final frozen integration review, configured local/source links, full documentation/index check and strict build apply before promotion. New pages and category links are counted separately from expanded item entries. No enchanting-roll, retaliation, death, item-loss, repair or fresh browser test was run. Remaining melee utility enchantments and the wider continuation queue remain open.

## Batch 281: Expedition rewards and ingredient workflows

- Expanded Dragon's Breath, Heart of the Sea, Echo Shard, Breeze Rod and Bottle o' Enchanting entry points with practical acquisition, exact quantities and current uses. Added a Content Guide route and focused Brewing, Ancient City and Experience links; existing encounter/device owners retain their full rules.
- Evidence is pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Independent review checks live dragon-cloud collection and bottle handling, brewing registration/completion, decoded chest/spawner assignments, ordinary and optional-pack loot, recipe outputs, Breeze player-credit/Looting conditions, repair and Flow-template copying, villager trades and XP-bottle delivery.
- Echo Shards use the ordinary city's 4/86 entry with 1-3 shards when selected; Bottle o' Enchanting uses city 3/86, outpost 7/22 and shipwreck-treasure 5/150 selections under their stated pools. These are per-selection weights, not promises for every chest or structure. Ice-box and optional-pack differences remain explicit.
- XP bottles award 3 plus two independent integer 0-4 draws: a nonuniform 3-11-point distribution with mean seven, not a uniform random range or fixed level gain. The Skyblock dragon-reward resource is not advertised as ordinary End acquisition because its handler checks the player's current generator.
- Independent review corrected the unpublished Dragon's Breath remainder claim before integration: doBrew shrinks the ingredient before resolving its item remainder, so the last Breath leaves no Glass Bottle, while a remaining nonempty stack produces a dropped bottle. Existing Brewing/Brewing Stand owners contained no conflicting promise. This documents a source-qualified limitation, not a gameplay fix or runtime reproduction.
- All five original heading/link sets are preserved. Frozen content/integration review, configured local/source checks, full documentation/index check and strict build apply before promotion. The independent review verifies 102 source files and 97 source definitions. No live dragon collection, brewing, loot, trade, XP, crafting or browser test was run; broader catch-up continues.

## Batch 282: Finding ores across biomes and dimensions

- Added a canonical Finding Ores guide and eleven incoming discovery links, including the Mechanics index, Content Guide, Mining, Ore Resources, biome families and Primordial Caves. Existing tool/drop, expedition and geode owners remain intact.
- Source is pinned to `78e8e0423084f010bb47e36132550619b37644c2`. The joined chart covers 48 ordinary ore/debris placements, with eight separate geode references. Independent verification checks memberships across 68 biome files, generator-height anchor contexts and the actual Primordial feature graph, rather than assuming familiar vanilla or imported-mod distributions.
- Placement origins and configured attempts are distinguished from successful block positions and measured yield. Four ordinary Diamond placements, overlapping Iron distributions, Dripstone Copper and the three Badlands extra-Gold memberships are included. Nether anchors resolve through the 128-block generator; Primordial anchors use minimum -64 and generator height 384. Dimension-type bounds alone do not select those endpoints.
- The separate Overworld noise-vein route and its Copper/Granite and Iron/Tuff material ranges are traced through current density/native callers and Java material/range gates. Ordinary ore generation includes the active Rust sphere/rasterization path while Java retains host/air/write checks. Primordial's disabled noise-vein setting, ordinary custom ore targets and separate geode/missing-host limitations are not collapsed into one route.
- Evidence accounting is explicit: 159 citation URLs across 155 directly cited files, 169 explicit source hashes and 1,042 resource inventory entries; their deduplicated path union is 1,089. These counts describe different checks, not 1,089 individually cited implementation files or a runtime registry dump.
- Final frozen content/integration review, configured local/source references, full documentation/index check and strict build apply before promotion. No world-generation run, ore-yield measurement, best-height benchmark, retroactive terrain change or fresh browser verification is claimed. Source-derived trip choices remain qualified, and broad wiki catch-up continues.

## Batch 283: Melee utility enchantments and specialist discovery

- Added a canonical Looting/Fire Aspect/Knockback guide and incoming Enchanting, Content Guide, Swords and Combat routes. The Enchanting index also exposes existing Crossbow, Trident, Mace, Spear Lunge and fishing-enchantment owners. Their specialist behavior remains on those established pages; this navigation does not imply an exhaustive re-audit of every enchantment detail.
- Source is pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Independent review checks 76 source files, active definitions, complete supported/primary tags, ordinary levels and selected acquisition, attack/post-attack dispatch, loot contexts, rare/equipment chances and broken-stack conditions.
- Looting's ordinary count functions, rare-drop chance and equipment-drop effects are distinct paths; not every entity table uses them. Fire Aspect's post-hit ignition is distinguished from the separate burning-or-direct-attacker smelts_loot predicate. The latter is verified in 17 bundled entity tables at 20 function sites and can apply on a lethal hit before ignition, subject to the table and smelting recipe.
- Knockback's numeric input is not a promised travel distance and is kept separate from sweep strength. Retained broken weapons stop the checked attacker ignition and knockback modifiers, while the inspected Looting readers have no equivalent guard. These effect-specific observations do not settle the broader passive-effect policy question or establish all custom-weapon behavior.
- Final frozen integration, configured heading/local/source checks, full documentation/index check and strict build apply before promotion. The content review verifies 93 pinned references rendering to 117 source links, 29 local/heading links and three tables. No combat, cooked-loot, loot-rate, push-distance, enchanting-roll or fresh browser test was run. Wider wiki catch-up continues.

## Batch 284: Terrapin transport and species-specific spawn eggs

- Expanded Bucket of Terrapin and Anaconda, Anteater, Atlatitan and Axolotl Spawn Egg item pages, with a Content Guide route. Existing mob, generic egg/spawner and inventory-browser owners remain canonical for broader behavior and common restrictions; no natural-spawn or normal breeding guarantee is inferred from egg use.
- Evidence is pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Active entity/block/item packet admission, player interaction, Mob offspring dispatch, runtime species factories, bucket capture/release ordering, category construction and the real browser Creative gate were checked. Ordinary Survival catalog visibility is distinguished from using an egg already supplied to the player.
- Terrapin capture preserves the documented default/special fields and species appearance/HasEgg fields, but omits age, forced-growth state, age-based breeding cooldown and partner appearance data. A captured baby's ordinary release initializes an adult. Ultra-warm water evaporation still reaches animal release without supplying water. Existing [Terrapin lifecycle limitations](https://github.com/HungLo2020/MattMC/issues/798) remain separate from a successful bucket interaction.
- All four selected eggs are Peaceful-allowed and reach matching-species offspring creation through the live dispatcher. The clicked creature need not be breeding-ready; this is a separate baby-creation path without normal mating rewards/cooldown. Anaconda copies its yellow flag; Axolotl uses self-parent variant inheritance with a separate 1/1200 rare-blue roll. Fresh single Anteater placements initialize a size-zero spawn group and are adult despite the species' group baby probability.
- Solid-block and source-liquid placement, shared spawner enabled gates and surrounding use restrictions are explicit. Atlatitan egg-produced babies do not prove that its laid-egg lifecycle works. The pages preserve earlier heading IDs and links rather than replacing accurate canonical limitations.
- Frozen independent content/integration review, configured local/source links, full documentation/index check and strict build apply before promotion. Draft evidence checks 44 source hashes, 98 local links and 178 pinned source-link occurrences. No capture/release, offspring, multiplayer, natural-spawn, rendering or fresh browser experiment was performed; wider catch-up continues.

## Batch 285: Custom resources and connected item interactions

- Expanded Amber Curiosity, Heavy Bone, Tough Hide, Emu Feather, Lost Tentacle, Maraca and Ominous Catalyst entries and added two Content Guide routes. Generic food, progression, drop and ominous-event assumptions were replaced with verified properties, demonstrated interactions and bounded acquisition/use limits.
- Source is pinned to `78e8e0423084f010bb47e36132550619b37644c2`. All seven are plain registered Items; Heavy Bone has stack limit 16, while Ominous Catalyst has uncommon rarity and fire resistance. Creative category membership is checked per item: Maraca and Lost Tentacle lack category entries and are not advertised as catalog-supplied items. Permission-level-2 give access and ordinary Survival browser restrictions are distinguished.
- Maraca equip/recovery is an active Cockroach interaction, but its state is a boolean rather than a saved copy of the supplied stack. Spawn/group, offspring/ootheca, goal, saved-data and dropped-item paths were traced; recovery from an already-equipped creature remains a real conditional route without inventing a naturally equipped population or claiming commands exhaust every customized-world possibility.
- The Giant Squid's Lost Tentacle drop sits behind capture behavior whose whale start and tick callers are commented out. Emu's active periodic output is an Emu Egg, with no established feather death route in the checked bundle. Ominous Catalyst's properties do not establish a working event consumer or the Ominous Bottle/Trial Omen chain.
- Independent closure parsed 8,594 bundled JSON/NBT resources, including 1,202 decoded structures; all 54 nested loot references and 155 structure loot links resolve, and 91 item-tag edges were inspected for target membership. These item-specific checks are not a claim about arbitrary changed data, operator-created state or every future acquisition source.
- Exact frozen content/integration review, configured links/source ranges, full documentation/index check and strict build apply before promotion. All earlier heading/link targets are preserved. No gameplay, natural-drop, Cockroach recovery, whale-capture, event or fresh browser test was run; broader catch-up continues.

## Batch 286: Metal, Leather and gilded crop ingredients

- Expanded Gold Ingot, Gold Nugget, Iron Nugget, Leather, Golden Carrot and Glistering Melon Slice pages and added two Content Guide routes. Mining, storage, food, brewing, animal and Piglin owners retain their detailed common rules.
- Evidence is pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Exact recipe quantities/shapes/tags, cooking devices, selected loot/trades and live item/animal/Piglin consumers were checked. Nine-nugget/one-ingot conversions, four-Hide Leather crafting and eight-nugget crop recipes are explicit.
- Raw Gold processing gives one ingot with 1.0 recipe XP; supported equipment recycling gives one nugget with 0.1 recipe XP. Cooker experience storage/award remains separate from these recipe values. Independent review narrowed the unpublished Gold Ingot recycling sentence to supported equipment; Golden Spear and Golden Nautilus Armor are not silently added to the actual recycling lists.
- Golden Carrot has a food component; Glistering Melon Slice does not. Their brewing roles and actual animal uses are checked separately. Master Farmer base offers provide three Golden Carrots for three Emeralds or three Glistering Melon Slices for four Emeralds, before ordinary price adjustments; those prices are not guarantees for every state or modified trade setup.
- Frozen content/integration review, configured local/source links, full documentation/index check and strict build apply before promotion. The independently reviewed bundle checks 66 source files, 87 local links and 140 pinned source-link occurrences, with all original heading/link sets preserved. No live barter, trade, cooking, food, breeding, XP or fresh browser test was run; wider catch-up continues.

## Batch 287: Building palettes and equipment displays

- Expanded Trowel and Armor Stand item guides and added a Content Guide route. Existing heading/link targets remain intact; Building Wand's separate proposed fix and guide remain untouched.
- Source is pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Exact recipes, stack/item properties, active interaction dispatch, placement contexts and equipment/removal consumers were independently checked. Trowel samples nonempty BlockItem hotbar slots uniformly, rather than weighting by block count or prefiltering successful placements; one failed chosen placement does not retry the palette.
- Armor Stand guidance distinguishes ordinary placement/facing/space and armless interaction from command-customized flags. Survival recovery is qualified by its actual drop gate and damage path; Creative attacks skip stand/equipment recovery. The checked five-tick breaking window is not a guaranteed wall-clock interval.
- The existing Armor Stand Creative-category claim was corrected to Functional Blocks and Redstone Blocks. The exact English label and pinned localization were checked; inventory-browser mode restrictions remain explicit.
- Final frozen integration review, configured local/source checks, full documentation/index check and strict build apply before promotion. Source review covers 33 cited and 20 supplemental entries, with original headings/links preserved. No live placement, random-palette trial, equipment exchange, breaking, recovery or fresh browser test was run; broader catch-up continues.

## Batch 288: Shelf item recipes and ordinary collection limits

- Expanded Acacia, Birch, Cherry, Dark Oak, Jungle, Mangrove, Pale Oak, Spruce and Warped Shelf item pages, added reciprocal links from all twelve rows of the canonical Shelf table and clarified the Content Guide route. Existing Oak, Bamboo and Crimson item bodies remain unchanged.
- Eight older item entries described valid container-component support without explaining ordinary collection; they did not explicitly promise that mining preserves contents. Warped Shelf had a separate generic-content/owner-route gap. The new wording resolves those omissions without misreporting them as a demonstrated runtime data-loss bug or a false explicit mining claim.
- Source is pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Each exact recipe uses six named stripped inputs to make six matching Shelves. Ordinary drops are empty items and stored stacks spill separately, including Silk Touch, under the documented drop/removal conditions. Special data-bearing stacks and Creative data-copy remain separate supported routes; explosion survival of every loose item is not promised.
- Independent review verifies all nine registrations/recipes/loot tables, live removal-to-container-spill ordering, later block loot, component defaults/application and current admission rules. It also closes the axe tag across 32 tags/286 registered blocks and both fuel tags (12 plus 32 required entries, 42 distinct registered items). Default Overworld/Bamboo Shelf fuel is 300 ticks; Crimson/Warped are excluded without asserting universal fire/lava immunity.
- Frozen content/integration review, configured local/source references, full documentation/index check and strict build apply before promotion. All 45 original item heading IDs and prior links remain available. The content review checks 135 local links and 225 pinned source links. No mining, Silk Touch, explosion, inventory-copy, fuel, redstone or fresh browser test was run; wider catch-up remains open.

## Batch 289: Shared clocks, local weather and multiplayer rest

- Added a canonical Time, Weather, and Sleep guide with twelve source-reviewed index/owner links and a Content Guide route. Bed's broad wake/skip paragraph and Commands' time-setting description receive narrow scope clarifications; other owner changes are discovery links, not blanket rewrites.
- Evidence is pinned to `78e8e0423084f010bb47e36132550619b37644c2`. The Overworld advances the shared stored game/day time; other levels read the wrapped values and their setters are ineffective. Fixed-time dimension environments retain their own rules. Day-time advancement and weather advancement have separate game-rule gates.
- Shared weather flags/timers, per-level rain/thunder intensities and predicates, actual precipitation at a position, packet routing and active client rendering are distinguished. The bundled End type has skylight/no ceiling, while its five ordinary biomes forbid precipitation. These facts do not establish identical visible weather or identical weather-dependent interactions in every dimension.
- Sleep thresholds count each level's own active players, with Spectators excluded and a separate deep-sleep check. Waking applies to sleepers in that level; successful Overworld sleep can jump to the next day boundary when allowed, with weather reset gated separately. Skipping day time does not simulate every intervening crop, mob or other ticking step.
- Independent source/render review verifies 57 pinned references across 37 directly cited files plus 26 supporting trace sources, four tables, 30 local links and all twelve proposed incoming anchors. It also checks the exact owner patch and the corrected weather-provider citation range. No storm-frequency, day-length wall-clock, sleep, precipitation, visual-weather or gameplay test was run.
- Final frozen integration, original heading/link preservation, configured local/source checks, full documentation/index check and strict build apply before promotion. No fresh browser verification or difficulty-system expansion is claimed; broader wiki catch-up continues.

## Batch 290: Coral inventory entries and placed-form routes

- Expanded the 26 audited Coral item candidates with exact collection facts and owner anchors; the four already-routed entries and canonical Coral guide remain unchanged. The Content Guide now points readers to its existing variant table. These are item-entry improvements, not 26 new placed-block articles or a broad completion claim.
- Source is pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Independent checks cover 30 actual item identities and 40 placed-block bindings. Ten shared fan items place floor/wall forms; no separate technical wall-fan inventory items are invented.
- Living full blocks require a correct pickaxe and Silk Touch to retain the living form; a correct pickaxe without Silk Touch gives the matching dead block. Dead full blocks need the correct pickaxe without a Silk requirement. Living plants/fans have a Silk loot predicate without a correct-tool gate, while dead plants/fans require both. Retained-broken-tool restrictions are tied to the actual gate rather than applied to every loot reader.
- Placement, support, water/death transitions, exact drop identities and canonical acquisition routes are checked without inventing rehydration resurrection or unverified natural dead-coral sources. Loaded tool-tier and growth tag closure is included in the matrix.
- Recipe accounting distinguishes 1,501 ordinary definitions, 191 active dedicated TaCZ workbench definitions and 1,459 advancement recipe-unlock files. Independent review checks 1,471 explicit ordinary outputs plus special-recipe paths and all dedicated outputs, finding no Coral recipe result; it does not call all 3,151 resource files loaded recipes.
- Frozen content/integration review, configured local/source references, full documentation/index check and strict build apply before promotion. Independent content checks include 182 pinned source-file hashes, 72 resolved tags, 310 local links, 257 source-link occurrences and 130 retained item heading IDs. No gameplay tests of harvesting, drying, growth or trading were run, and no fresh browser verification is claimed; wider catch-up continues.

## Batch 291: Leaf inventory entries and shared harvest routes

- Expanded ten audited leaf item entries and added reciprocal item links to their existing Tree Leaves table rows. Mangrove's already-routed entry and the owner's shared behavior prose remain unchanged. These are ten existing item improvements, not ten new block articles or a completion claim.
- Source pin remains `78e8e0423084f010bb47e36132550619b37644c2`. Independent review checks the ten complete loot-table structures, actual block/item bindings, active drop consumers, Fortune lookup and the valid log/leaf/hoe tag closure.
- Shears or Silk Touch collect one matching leaf and exclude the material pools. Ordinary material drops use separate planting-material, stick and optional apple rolls; only Oak and Dark Oak in this set have apples, Jungle has its distinct sapling chances, and Azalea forms yield their matching shrubs. Explosion conditions and tool-free decay remain distinct from ordinary harvesting.
- Ordinary placement makes leaves persistent without changing their harvest table. No blanket retained-broken-stack rejection is invented: the actual leaf gate and loot readers differ from correct-tool-gated blocks. Shared growth, support and decay details stay with the canonical owner.
- Content review covers 47 frozen source files plus 18 independently traced consumer files, ten exact loot tables, 122 local links, 100 pinned source links and 50 preserved item heading IDs. Final integration references, full documentation/index checks and strict build apply before promotion. No gameplay, tag-reload or fresh browser test is claimed; broad coverage work continues.

## Batch 292: Amethyst Bud item routes

- Expanded the three existing Small, Medium and Large Amethyst Bud entries, added reciprocal item links to the canonical Amethyst table and improved Content Guide discovery. The owner's shared behavior prose remains unchanged; these are not new block articles or a broad completion claim.
- Source pin remains `78e8e0423084f010bb47e36132550619b37644c2`. Buds have Silk Touch-only self drops and no correct-tool drop gate; pickaxe efficiency is a separate speed rule. Unlike mature clusters, these bud loot tables do not produce Shards.
- Each entry distinguishes its light level and stage, support and waterlogging, and growth through an adjacent intact Budding Amethyst block with the matching outward-facing state. Replanting on ordinary support does not create a new growth source; correctly oriented placement beside Budding Amethyst can resume progression.
- Recipe evidence distinguishes 1,501 ordinary definitions, 191 active TaCZ definitions and 1,459 advancement recipe-unlock files; the last group is not a set of recipes. Checked outputs and special paths supply no bud recipe, without claiming that overrides cannot add one.
- Independent content review covers 36 pinned source files, 57 local links, 60 pinned source links and 15 retained item heading IDs. Exact final integration, full documentation/index checks and strict build apply before promotion. No live harvesting/growth, runtime recipe reload or fresh browser check is claimed; broader catch-up continues.

## Batch 293: Deepslate construction item routes

- Expanded the nineteen audited Deepslate item candidates with exact acquisition and variant facts and routes into their existing placed-block owner. The canonical Deepslate guide already links those item pages and remains unchanged. These are existing item improvements, not nineteen new placed-block articles.
- Source remains pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Independent review checks nineteen identities, fifty-five producing recipes and nineteen complete loot tables. Exact recipe inputs replace ambiguous any-Deepslate assumptions; crafting, stonecutting and Furnace yields remain distinct.
- Ordinary full blocks, stairs and walls return their matching item through the correct-pickaxe gate. The four single slab forms return one and their double state returns two; Silk Touch does not bypass the ordinary correct-tool gate. Cracked forms use the checked Furnace routes, and Chiseled/Cracked forms are not accepted as inputs by the inspected stonecutting recipes.
- Infested Deepslate is a separate collection case: Silk Touch yields its ordinary Deepslate host, not an Infested item. Its registered Creative entry is not operator-gated. Ordinary Survival inventory-browser requests remain blocked by the actual packet codec even though later send/handler logic is permissive; no successful Survival insertion route is inferred.
- Independent negative-recipe review includes 1,501 ordinary definitions, 191 active TaCZ definitions and dynamic special outputs. Advancement unlock files are not recipes. Original item headings and links survive; individual validation checks 254 local links and 201 pinned source links before aggregate integration checks and strict build. No gameplay or fresh browser test is claimed, and broader coverage work continues.

## Batch 294: Banner color item routes

- Expanded fifteen audited Banner color item entries and added reciprocal links for all sixteen colors in the canonical Banner table, including the already-routed White entry. Shared owner behavior prose remains unchanged. These are existing item improvements, not fifteen new block articles or proof of broad completion.
- Source remains pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Each color uses six exact-color Wool in two rows and one centered Stick below for one blank Banner. One inventory item places its corresponding standing or wall form; a separate wall-banner item is not invented.
- Exact loot and active block-entity component transfer confirm ordinary recovery retains the supported design/naming components. Duplication, Loom patterns, Shield decoration, washing and map-marker routes are linked to canonical owners and checked through their actual consumers rather than inferred from color names.
- Independent review verifies the fifteen recipes and shared-loot bindings, 150 local links, 195 pinned source links, 105 source-hash records and all original headings/links, including Yellow's prior BOM normalization. Orange grammar was corrected before freezing; no other color body changed during that final correction.
- Final exact integration, documentation/index checks and strict build apply before promotion. No gameplay, runtime resource reload or fresh browser test is claimed; wider catch-up remains open.

## Batch 295: October 4 11:45 UTC coordinated review

- Reconciled the completed article cutoff through batch 294 at `a88c9204b61ea8e7f27e68b78bb76745e32c8705`; gameplay source remains `78e8e0423084f010bb47e36132550619b37644c2`. The reviewed changes are documentation only, with no new source migration or gameplay repair inferred.
- The tracker pass created [issue #811](https://github.com/HungLo2020/MattMC/issues/811) for LPVO's selected `scope_view_2` versus bundled geometry mismatch and [issue #812](https://github.com/HungLo2020/MattMC/issues/812) for the generic final brewing ingredient remainder transaction. This checkpoint adds links to the already source-qualified LPVO and Dragon's Breath entries; neither issue is a completed fix or runtime reproduction.
- LPVO's missing selected node causes the inspected iron-sight positioning fallback while independent zoom/model FOV remains active. Its actual rendered alignment and resource override/reload behavior still need runtime validation. Scout's bypassed index redirects remain a separate intended-resource/port-scope question; the review does not require enabling alternate assets or unsupported raw modifiers.
- The brewing issue concerns ingredient-stack ordering, with one-versus-two Dragon's Breath as the active case. Compatible potions still convert; one ingredient is consumed for the batch, and the missing remainder is not a bottle-per-output-potion rule. Existing source behavior remains documented until a verified implementation changes it.
- Batches 274-294 contain 137 substantive existing item expansions and five new specialist guides, plus navigation and append-only history. These counts describe that bounded work and do not certify complete registry or gameplay coverage. Within the separate eight-family item-route audit sample, 82 of 179 candidates are addressed and 97 remain; isolated later drafts are not counted as published.
- Final exact integration review, documentation/index validation and strict build apply before this checkpoint is promoted. Published article deployments are checked individually for terminal Build/Deploy success. No gameplay test or fresh browser check is claimed by this review, and ongoing catch-up continues after the report cutoff.

## Batch 296: Overworld plank item routes

- Expanded eight audited ordinary Overworld non-Oak plank entries: Spruce, Birch, Jungle, Acacia, Cherry, Dark Oak, Pale Oak and Mangrove. The canonical Wood Construction owner already links these items; its behavior prose remains unchanged. Oak and already-routed Bamboo, plus separately handled Nether/Pewen materials, are not counted as new work in this batch.
- Source remains pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Each checked family accepts its matching Log, Wood or either stripped form for four planks; Mangrove Roots are not substituted for that log-family ingredient. The eight conversions and forty-eight corresponding construction recipes are checked against exact inputs and outputs.
- Ordinary collection, full-block placement, material-specific versus generic plank recipes, fuel and fire behavior are traced through active consumers. No natural-generation guarantee, global absence claim or successful ordinary Survival inventory-browser insertion is inferred from the item names.
- Independent review of the frozen eight-page set passes 685 static assertions, 121 content local links and 144 pinned source references; the author also records 105 matching source-file hashes and all forty original headings preserved. Final aggregate integration, documentation/index checks and strict build apply before promotion. No in-game or fresh browser test is claimed; broader catch-up continues.

## Batch 297: Nether stem and hyphae item routes

- Expanded the eight Crimson/Warped stem/hyphae item entries, including all four stripped forms, with reciprocal links in the existing Tree Logs and Roots table. Shared owner behavior prose remains unchanged; these are existing item improvements, not eight new block guides.
- Source remains pinned to `78e8e0423084f010bb47e36132550619b37644c2`. The four ordinary-to-stripped Axe pairs are exact, with no additional stripping step for already-stripped forms. Pillar placement chooses its axis from the clicked face; ordinary harvest returns the matching item without requiring Silk Touch.
- Four matching stems in a two-by-two recipe make three matching hyphae, separately for ordinary and stripped forms. Each family's four forms can instead make four planks per input. Converting to hyphae first changes total material yield; the recipes are not interchangeable one-for-one conversions.
- Furnace exclusion, ordinary block fire-consumption/ignition rules and dropped-item damage are distinct checked paths. The eight items are excluded through the non-flammable-wood fuel tag but their ordinary item registrations do not supply blanket fire resistance. Conditional planted-fungus growth is traced without a natural-biome guarantee.
- Independent review verifies eight exact baseline/output pairs, 115 pinned source files including recursive tag descendants and active loaders, and 242 rendered links. Private provenance labels were clarified as artifact-regeneration observations without changing any page or patch bytes. Final exact integration, full documentation/index checks and strict build apply before promotion. No gameplay or fresh browser test is claimed; broader catch-up continues.

## Batch 298: Overworld Stripped Wood item routes

- Expanded nine audited Overworld Stripped Wood entries and linked their existing canonical timber-table cells to the item pages. Shared owner behavior remains unchanged; this batch concerns nine existing item entries rather than new placed-block guides.
- Source remains pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Each matching ordinary Wood block has an explicit Axe conversion, and four matching stripped logs craft into three stripped Wood blocks. Already-stripped forms have no further stripping entry; Wood and Log are separate materials with separate recipe identities.
- Pinned model resources confirm the stripped-log side texture is used on all six faces through cube-column inheritance; the blockstate models support all three axes. The active resource reload, baked model cache and native model-registry connection are verified without treating absent sparse-checkout files as absent repository resources.
- Ordinary harvest/loot, exact plank/charcoal/fuel tag closure, fire behavior and placement axis are checked through their active consumers. No natural-generation guarantee or broad item-fire immunity is inferred from the name or finish.
- Independent review verifies nine exact baseline/output pairs, 136 pinned source/data files, 126 rendered local links and 45 pinned source links. Final integration preserves owner row anchors, existing headings/links and monthly history; full documentation/index checks and strict build apply before promotion. No gameplay or fresh browser test is claimed, and broad catch-up continues.

## Batch 299: Overworld Stripped Log item routes

- Expanded nine audited Overworld Stripped Log entries and added reciprocal links in their existing timber-table cells. The ordinary logs, Wood and Nether forms belong to distinct route batches; these are nine existing item improvements, not new placed-block guides.
- Source remains pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Acquisition uses the exact matching ordinary-log Axe conversion and subsequent harvest. The pages no longer imply that an already-stripped log can be stripped again; ordinary loot returns its matching item without a Silk Touch requirement.
- Each variant's end and side textures and three placement axes are checked in pinned model/blockstate resources, including Cherry's distinct x/y/z UV-locked models. Active resource reload, baked model storage and native model-cache submission connect those resources to rendering; no live appearance capture is claimed.
- Exact stripped-log-to-stripped-Wood and plank conversions, charcoal and furnace-fuel tag closure, and fire behavior are verified. Shared details remain in Tree Logs and Roots; no natural-generation guarantee or broad fire-immunity conclusion is inferred from the stripped item names.
- Frozen author checks cover 177 pinned source/data files, 125 semantic assertions and 1,581 resource/Markdown assertions, including all twenty-seven axis states, 99 local file links and 198 rendered source links. Separate independent content/integration review and full documentation/index validation with strict build apply before promotion. No gameplay or fresh browser test is claimed; broad catch-up continues.

## Batch 300: Ordinary Overworld Wood item routes

- Expanded nine audited ordinary Overworld Wood entries, including Oak, and linked their canonical timber-table cells to the item guides. Shared owner behavior remains unchanged. Stripped Wood, logs and Nether materials are separate route batches; the batch number is not a milestone of overall completeness.
- Source remains pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Four matching ordinary logs in a two-by-two recipe make three Wood blocks, each of which can make four matching planks. Each ordinary Wood block has its own exact Axe conversion to matching stripped Wood; mixed-family and already-stripped recipe substitutions are not invented.
- Exact models use matching log-side texture on all six faces, with axis states checked separately. Active Java resource loading and baking, native model registration and Rust model consumption connect that visual data to the renderer. No live appearance capture is claimed.
- Harvest/loot, placement, charcoal and fuel tags, and fire behavior are checked through active consumers. Acacia Wood's previous Natural Blocks tab note is corrected to its actual Building Blocks display entry. This tab correction does not imply ordinary Survival inventory-browser acquisition.
- Frozen author validation checks nine data/Markdown rows and 118 pinned source-file hashes, preserves all forty-five original heading IDs, and rejects four negative recipe/model controls. Separate independent content/integration review plus full documentation/index validation and strict build apply before promotion. Monthly history remains append-only; no gameplay or fresh browser test is claimed, and wider catch-up continues.

## Batch 301: Ordinary Overworld Log item routes

- Expanded eight audited ordinary Overworld Log entries and added their reciprocal timber-table links. Oak Log was an already-routed control and remains unchanged; stripped forms, Wood and Nether timber have separate item-route batches. These are existing item improvements, not new placed-block guides or a statement of complete coverage.
- Source remains pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Renewable acquisition traces the actual sapling/propagule consumer through TreeGrower and configured-feature trunk providers. Dark Oak and Pale Oak require the checked two-by-two route, Spruce and Jungle support both single and two-by-two routes, and Mangrove uses a planted Propagule; biome locations are not inferred from species names.
- Exact harvest and ordinary-log-to-stripped-log Axe mappings, four-log-to-three-Wood and four-plank conversions, charcoal/fuel tags and fire consumers are checked. All twenty-four model-axis variants are verified, including Cherry's separate axis models, with the active Java-to-native model-loading connection traced.
- Frozen author checks record 166 pinned source files, 121 local links and 134 source links, preserve the original headings, and distinguish source-backed growth from runtime success guarantees. Separate independent content/integration review and full documentation/index validation with strict build apply before promotion. History remains append-only; no gameplay or fresh browser test is claimed, and broader catch-up continues.

## Batch 302: Wooden Slab inventory routes

- Expanded eleven audited wooden Slab item entries and added thirteen reciprocal item routes in the shared Slabs section, including the already-routed Bamboo and Bamboo Mosaic entries. Petrified Oak Slab and Pewen remain outside this checked family. These are eleven existing item improvements rather than new block articles.
- Source remains pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Three exact matching planks in one row yield six matching slabs. Ordinary single slabs drop one item and doubles drop two; no separate double-slab inventory item is invented, and the loot count does not require Silk Touch.
- Hand collection is distinct from axe mining speed. Actual placement and replacement rules require the same slab item to form a double, distinguish top/bottom placement and single-slab waterlogging, and reject waterlogging the double state. Shared details stay with Wood Construction.
- Nine Overworld materials receive 150 default furnace burn ticks per slab and have ordinary fire consumption when not waterlogged. Crimson/Warped are explicitly excluded from fuel and have no ordinary fire-consumption entry. These are placed-block and furnace facts, not blanket dropped-item fire immunity.
- Independent content review passes 665 checks including complete per-variant recipes and loot, active harvest/placement/fire/fuel consumers, 58 pinned source hashes and 154 rendered links. Final exact integration, full documentation/index checks and strict build apply before promotion. Prior headings and monthly history are preserved; no gameplay or fresh browser test is claimed, and broad catch-up continues.

## Batch 303: Wooden Stair inventory routes

- Expanded eleven audited wooden Stair entries and added thirteen alphabetical reciprocal item routes under the existing Stairs heading, including the already-routed Bamboo and Bamboo Mosaic entries. Pewen remains outside this checked family. Existing Slab links and shared owner behavior remain unchanged.
- Source remains pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Six exact matching planks in the stair layout make four matching stairs. Registration/base-property inheritance and ordinary self loot distinguish hand collection from axe mining speed; a particular tool tier or Silk Touch is not required for these ordinary drops.
- Actual placement and neighbor updates determine facing, upper/lower placement, inner/outer joining and waterlogging. The item entries route complete shared placement details to Wood Construction rather than assuming every neighboring stair joins or inventing additional inventory forms.
- Nine Overworld materials receive 300 default furnace burn ticks per stair and have active ordinary fire consumption when not waterlogged. Crimson/Warped are removed from fuel and have no ordinary fire-consumption entry. These are separate placed-block/furnace facts, not broad dropped-item fire immunity.
- Recipe evidence is explicitly scoped to 1,501 ordinary RecipeManager definitions and their explicit outputs. The 191 active TaCZ workbench definitions and 1,459 advancement unlock files are separate; the latter are not recipes. No global absence claim or data-pack restriction is inferred from that scan.
- Frozen author checks preserve every original heading ID and validate 132 local links and 125 pinned source-link occurrences. Separate independent content/integration review and full documentation/index checks with strict build apply before promotion. Monthly history stays append-only; no gameplay or fresh browser test is claimed, and broader catch-up continues.

## Batch 304: Wooden Fence inventory routes

- Expanded eleven audited wooden Fence item entries and added twelve alphabetical reciprocal routes under the existing Fences heading, including the already-routed Bamboo Fence. Nether Brick Fence and Pewen remain outside this checked family. Prior Slab/Stair navigation and shared owner behavior stay intact.
- Source remains pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Four exact matching planks and two Sticks make three matching fences. Complete ordinary loot tables return one matching item, and hand collection is distinct from axe speed; a correct-tool tier or Silk Touch is not required for those ordinary drops.
- Actual connection tags, 1.5-block collision, waterlogging and the empty-hand transfer of entities already leashed to the player are traced through active consumers. The entries do not promise universal mob containment or retain unsupported Acacia carpet/trapdoor, spider or broad lighting claims.
- Nine Overworld materials have ordinary fire entries and 300 default furnace fuel ticks per fence; Crimson/Warped have no ordinary fire-consumption entry and are removed from fuel. These placed-block/furnace facts are not blanket dropped-item fire immunity.
- Frozen author validation passes 1,187 static source/data/Markdown checks plus an isolated documentation/index check, preserving original headings and link destinations. Separate independent content/integration review and full aggregate documentation checks with strict build apply before promotion. Monthly history remains append-only; no gameplay or fresh browser test is claimed, and broader catch-up continues.

## Batch 305: Wooden Fence Gate inventory routes

- Expanded eleven audited wooden Fence Gate entries and added twelve alphabetical reciprocal item routes under the existing Fence Gates heading, including the already-routed Bamboo gate. Pewen remains separate. Prior Slab/Stair/Fence routes and shared owner behavior remain unchanged.
- Source remains pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Four Sticks and two exact matching planks make one gate. Full ordinary loot tables return the matching item, with separate explosion-survival conditions; hand collection is distinct from axe speed.
- The actual gate state includes facing, open, powered and in-wall properties, with no waterlogged property. Active interaction and redstone updates distinguish manual toggling under steady power from later power changes. Closed collision remains 1.5 blocks high and open collision is empty; the lowered in-wall appearance does not lower that closed collision shape. No universal mob/pathfinding guarantee is inferred.
- Nine Overworld materials have ordinary fire entries and 300 default furnace burn ticks per item; Crimson/Warped lack those fire entries and are removed from fuel. These source-backed block/furnace rules are separate from dropped-item fire immunity.
- Frozen author checks verify 58 pinned source files, exact variant recipes/loot/state dispatch, 110 local links, 101 source links and 54 retained heading IDs. Separate independent content/integration review and full documentation/index checks with strict build apply before promotion. Monthly history remains append-only; no gameplay or fresh browser test is claimed, and broad catch-up continues.

## Batch 306: Nether Plank routes and bounded coverage checkpoint

- Expanded the two existing Crimson/Warped Plank item entries. Wood Construction already links both pages, and their outgoing owner routes now supply the exact conversion, harvest and material distinctions; no duplicate placed-block guide is added.
- Source remains pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Each family accepts its four Stem/Hyphae/stripped forms for four planks. Six matching construction recipes and selected generic plank recipes are checked, alongside ordinary BlockItem identity and hand collection versus axe speed.
- Placed-block fire consumption and lava-ignition properties, default furnace exclusion and dropped-item damage are separate active paths. Lack of block burning or fuel value does not make the ordinary dropped plank item fire-resistant.
- Independent content review passes 625 assertions across 86 pinned source/data objects, plus exact hashes, preserved headings/links and reproducible individual renders. Final integration, full documentation/index checks and strict build apply before promotion; no gameplay or fresh browser test is claimed.
- These two entries close the specifically selected 179-candidate, eight-family item-route queue. Before integration, all other 177 entries matched their published reviewed output hashes and had rendered direct links to their canonical owners. This is a bounded entry-point checkpoint, not complete item-registry coverage or a claim that the wiki is finished.
- A separate source/documentation audit identified 84 further exact item candidates across ten families with no overlap with that queue. Next priorities are risky foods and cooking, bowl meals and imported-soup limits, Infested Stone and masonry, and Nether cultivation/harvesting. Later groups include colored Beds, selected Heads, worn Anvils, ordinary flowers, mushroom caps and Moss forms. Existing strong controls are excluded, and no heuristic page-length or missing-link screen alone establishes a content defect.
- Ongoing work must continue to improve actual player decisions and accurate source-backed guidance, retain known implementation limits and distinguish completed item expansions from new canonical guides. Monthly entries remain concise and append-only; pending drafts and sampled counts must never be presented as broad completion.

## Batch 307: Risky foods and everyday cooking

- Expanded eight existing entries: Raw/Cooked Chicken, Raw Beef/Steak, Raw/Cooked Salmon, Raw Mutton and Poisonous Potato. The Food Reference and species owners already supply reciprocal routes; shared cooking guidance remains unchanged, as do the stronger Raw Cod, Cooked Cod and Cooked Mutton controls. These are item-entry improvements, not new food-system guides.
- Source remains pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Actual food/consumable components and finish-use paths establish nutrition, saturation before caps, ordinary fullness/Creative gates, and separate harmful-effect rolls. Raw Chicken has a 30% Hunger I effect for 600 game ticks; Poisonous Potato has a 60% Poison I effect for 100 game ticks. Hunger points are not food-icon counts, and effect ticks are not measured real-time durations.
- Exact ordinary loot routes and cooked-meat conditions are checked. Mature potato harvesting has a separate 2% poisonous-potato pool with no Fortune modifier, including the active harvest-and-regrow controls. Existing species and crop guides retain the full surrounding mechanics.
- Twelve raw-to-cooked recipes distinguish Furnace, Smoker and Campfire inputs and configured times. Furnace/Smoker collection uses accumulated recipe experience with its rounding behavior; Campfire completion drops the cooked food without awarding recipe experience. Cooking device outcomes are not inferred solely from the JSON experience field.
- Frozen author checks pass 957 source/data/render assertions over 53 pinned source files and preserve all forty original item heading IDs. Separate independent content/integration review and full documentation/index checks with strict build apply before promotion. Monthly history remains append-only; no gameplay or fresh browser test is claimed, and broad catch-up continues.

## Batch 308: Bowl meals and imported-soup limits

- Expanded Rabbit Stew, Beetroot Soup, Seething Stew and Primordial Soup. Food Reference already links all four, and the existing Relicheirus warning links its soup entry. Canonical owners and the stronger Mushroom Stew/Suspicious Stew controls remain unchanged; these are four existing item improvements rather than new shared food guides.
- Source remains pinned to `78e8e0423084f010bb47e36132550619b37644c2`. All four servings stack to one. Rabbit Stew supplies ten hunger points and twelve saturation before caps; the other three supply six hunger and 7.2 saturation before caps. Ordinary fullness and Creative behavior follow the active consumable path.
- Rabbit Stew and Beetroot Soup have explicit Bowl remainders; the two imported soups do not. Ordinary Survival consumption therefore differs from Creative's retained serving. The imported item names do not establish upstream potion effects: their actual default-food component is checked.
- Exact Rabbit Stew and Beetroot Soup crafting routes are documented without claiming those are their only acquisition routes. For the imported soups, the review found no bundled recipe, loot reward or trade supply and explicitly leaves ordinary Survival availability unestablished; external server packs, commands and existing supplied items are outside that absence claim. Ordinary Survival inventory-browser requests still fail the actual protocol gate.
- Primordial Soup routes to the already-established Relicheirus limitation: ordinary interaction can consume the soup without invoking the separate tree-pushing setter. This is a source-confirmed behavior distinction, not a runtime reproduction or completed fix.
- Independent review matches 65 frozen source snapshots and 8,598 bundled data fingerprints, including 1,202 NBT resources and resolved loot references, plus rendered links/headings and unchanged controls. The data inventory and absence checks are not a claim that every optional or external acquisition path was tested in-game. Final integration, full documentation/index checks and strict build apply before promotion; monthly history remains append-only, and broad catch-up continues.

## Batch 309: Infested Stone routes and catalog terminology

- Expanded six audited Infested Stone-family item entries and added reciprocal links to their existing Stone owner table. Infested Deepslate remains a separate existing guide. Ordinary host collection and obtaining an Infested inventory item are different routes; Silk Touch returns the exact ordinary host rather than the Infested item.
- Source remains pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Exact identities, no-correct-tool harvest eligibility, complete loot branches, active Silverfish callbacks, gamerules and spawn-suppressing enchantment tags are checked. Ordinary Survival mining, Creative breaking, destructive explosions and Silverfish state changes are not conflated.
- These items are registered in the ungated Functional Blocks category. The actual client opens InventoryScreen in Creative too, and its inventory browser combines registered category entries; a category name is not an instruction to open the old separate Creative Menu/tab screen. Ordinary Survival item requests still fail the established server protocol gate.
- Corrected that obsolete screen wording in the published Infested Deepslate opening and qualified Acacia Wood's Building Blocks label as registered category data. Inventory Browser now explicitly explains category labels. No access permissions, gameplay behavior or recipes are changed, and the wider wording audit is not treated as complete by these two targeted corrections.
- Recipe accounting separates 1,501 ordinary RecipeManager definitions, 191 active TaCZ definitions and 1,459 advancement unlock files. Item-page absence wording covers declared JSON outputs only; it does not infer all dynamic recipe results or every possible Survival supply from a literal-ID scan.
- Frozen six-page source/data and Markdown checks preserve the original headings and routes; independent review verifies the corrected access wording and all other item bytes. Final exact integration, documentation/index checks and strict build apply before promotion. Monthly history stays append-only; no gameplay or fresh browser test is claimed, and broad catch-up continues.

## Batch 310: Nether vegetation and cultivation entry routes

- Expanded Crimson/Warped Fungus, Warped Nylium, Crimson/Warped Roots, Warped Wart Block, Weeping Vines and Twisting Vines. Their canonical Nether Fungi, Nether Ground and Vegetation, and Vines owners already provide reciprocal item routes and remain unchanged, as do the four stronger item controls. These are eight existing item improvements, not new placed-block guides.
- Source remains pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Decorative survival substrates differ from matching-Nylium huge-growth targets. Actual Bone Meal validity, chance, consumption and configured-feature callbacks are checked; roots do not acquire a direct Bone Meal target merely because the surrounding Nylium can grow vegetation.
- Warped Nylium recovery uses the correct-pickaxe gate and a Silk Touch-versus-Netherrack loot branch. Root and Wart Block drops have their own predicates. The two Nether-vine inventory items cover their registered tips and bodies; no separate body inventory items are invented. Full Shears/Silk Touch and Fortune branches, directional growth and age/Bone Meal behavior are checked.
- Warped Fungus's Strider food/temptation and placed Hoglin-repellent uses follow actual tags and consumers, separately from the Warped Fungus on a Stick steering check. Shared mob, cultivation and obstruction details stay with their owners; no unverified upstream behavior or broad generation guarantee is imported.
- Independent review verifies 29 frozen baselines, 188 pinned source files, eight output hashes, 169 rendered source destinations/ranges and 99 local links, with no lost headings or links. Author loot checks cover 144 vine cases and 21 other cases. Final integration, full documentation/index checks and strict build apply before promotion. Monthly history stays append-only; no gameplay or fresh browser test is claimed, and broader catch-up continues.

## Batch 311: Bounded inventory-browser wording corrections

- Corrected 65 existing UI clauses across four spawner pages, five redstone pages, seven pottery-sherd pages, twelve music-disc pages and thirty-seven expanded-item pages. Registered category labels describe source grouping; players use the combined inventory item browser rather than a separate category-tab screen.
- Source remains pinned to `78e8e0423084f010bb47e36132550619b37644c2`. All 62 distinct item IDs and 66 per-page category memberships are verified against active registration, category construction and the shared browser path. Creative requests retain the existing feature, permission, capacity and server-admission limits described by the shared guide.
- This is wording-only maintenance, with no substantive item-coverage credit. Existing headings, links, valid acquisition routes and unrelated prose remain unchanged; surrounding recipe, loot and behavior claims are not revalidated merely because their page contains a corrected UI sentence. The broader catalog audit and operator-specific guides remain separate work.
- After execution-workspace replacement, the exact retained 65-path selection was reconstructed against the published baseline and all output hashes and source checks were regenerated. Independent review checks the complete selected drafts, 17 frozen source files, 1,256 local links/anchors and 1,339 source-link occurrences. Final integration, full documentation/index checks and strict build apply before promotion. Monthly history remains append-only; no gameplay or fresh browser test is claimed, and broad catch-up continues.

## Batch 312: Operator tools and active WorldEdit selection

- Expanded the existing Debug Stick and WorldEdit Wand entries with separately verified acquisition and interaction permissions. Both catalog entries come from the operator category and require instant-build plus numeric permission level two; ordinary Survival browser requests still fail the active protocol gate. Possession does not grant tool-use permission.
- Source remains pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Debug Stick property selection, value cycling, Sneak reversal, per-block-type stored selection and no-property feedback follow the active item callbacks. Its give-command permission and state-editing permission remain separate checks.
- WorldEdit command acquisition instead requires initialized WorldEdit and the server operator check, without a separate Creative or universal numeric-level-two requirement. The active registered WandItem sets position one on right-click and position two at the block-breaking callback; Survival mining progress can delay the latter. Sneak does not select the other corner, and an earlier active brush can intercept right-click. Opposite-order legacy helper descriptions are not treated as active dispatch.
- The full-inventory Creative success-message edge, per-world two-corner selection and inclusive cuboid example are explicit. Existing Commands and Building Wand routes remain intact. These are documentation corrections and useful expansions, not changed game behavior or implemented issue fixes.
- Fresh independent review checks the reconstructed exact two-page hashes, active Java/protocol paths, a search of 791 tracked native-source files, 45 source snapshots and rendered references. All twelve existing heading IDs survive. Final integration, documentation/index checks and strict build apply before promotion. Monthly history remains append-only; no gameplay or fresh browser test is claimed, and broad catch-up continues.

## Batch 313: Stone masonry item recipes and recovery

- Expanded twenty existing ordinary Stone-family item entries: six Slabs, five Stairs, four Walls and five full-block forms. Their existing Stone owner already provides reciprocal routes and remains unchanged. Infested variants, the stronger ordinary Stone/Cobblestone controls and the Deepslate family are separate. These are item-entry improvements, not new placed-block guides.
- Source remains pinned to `78e8e0423084f010bb47e36132550619b37644c2`. A fresh complete scan of 1,501 ordinary recipe definitions identifies 42 exact-output recipes for these twenty items: seventeen shaped, two shapeless, twenty-one stonecutting and two smelting. Supporting ingredient-use recipes are separate from that output count. Exact ingredient identity and quantities distinguish Stone from Smooth Stone, moss inputs from their carpet forms, six-input wall layouts and the Furnace conversions.
- All twenty complete loot tables and active correct-tool consumers are checked. An unbroken Wooden Pickaxe is sufficient for this selected family; hand collection does not bypass the tool gate. Single/double Slabs return one/two matching slab items. Silk Touch and Fortune do not add an alternate output branch or multiplier to these ordinary tables. Explosion conditions remain separate.
- Item pages route shared placement, joining and waterlogging details to the Stone owner. Current combined-browser wording preserves the ordinary Survival admission limit. Existing heading IDs and local links survive, including the focused Smooth Stone page.
- Fresh independent review matches 184 frozen artifacts and 110 pinned source snapshots, plus 269 local links and 281 source-link occurrences. The official checker passes the isolated overlay; final aggregate integration, documentation/index checks and strict build apply before promotion. Monthly history remains append-only; no gameplay or fresh browser test is claimed, and broad catch-up continues.

## Batch 314: Colored Bed crafting and recovery routes

- Expanded fifteen existing colored Bed items while preserving the stronger White Bed control. Added sixteen alphabetical color-item routes to the existing Bed owner; its established sleeping, respawn, weather and dimension guidance remains intact. These are item-entry improvements and family navigation, not fifteen new placed-block guides.
- Source remains pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Exact same-color Wool plus the planks tag makes one matching bed. Recoloring recipes accept each of the other fifteen Bed colors with the target dye, with no same-color input. The independent review checks all 225 accepted pairs and negative ingredient cases against the complete definitions.
- Registration and the active placement/loot/removal chain distinguish one inventory item from the two placed halves. Each item stacks to one. Ordinary recovery of an intact pair yields one matching head-derived item rather than two beds; no tool-tier or Silk Touch requirement is invented. Dimension danger follows the actual bed-support property: the bundled Nether and End use the power-five, fire-enabled explosion branch. Color does not establish safety in another dimension.
- Fresh independent source and Markdown review preserves all 75 original item heading IDs, including the Yellow Bed baseline encoding case, and verifies the recipes, loot and canonical owner routes. Separate isolated documentation checks and strict build pass; final combined integration and aggregate checks apply before promotion. Monthly history remains append-only; no gameplay or fresh browser test is claimed, and broad catch-up continues.

## Batch 315: Coordinated 14:45 UTC source and tracker checkpoint

- Rechecked the documentation/source boundary after the completed item batches: gameplay source remains `78e8e0423084f010bb47e36132550619b37644c2`. Subsequent published work in this interval is documentation only; no incoming runtime change or migration completion is inferred.
- Crosslinked the existing Relicheirus and Primordial Soup warnings to [issue #813](https://github.com/HungLo2020/MattMC/issues/813). The independently reviewed active interaction consumes soup after parent interactions pass without enabling the retained tree-push activation hook. The ticket is source-qualified and open; no in-game feeding or AI test and no implemented repair are claimed.
- The issue preserves the deliberately simplified single-block tree effect. Soup acquisition, Bowl policy and other dinosaur interactions remain separate decisions; the checkpoint does not request or imply full upstream tree-felling behavior. Existing guide details and source pins are retained.
- The article cutoff includes the completed bounded family work through batch 314 and the sixty-five wording-only corrections in batch 311. Those UI corrections receive no substantive coverage credit. The earlier 179-candidate route sample is addressed, and the separate 84-candidate sample has 61 addressed entries with 23 remaining candidates independently re-audited. These sampled queues and file counts are not a whole-wiki completeness measure; broader source-grounded catch-up continues.
- Final exact integration, documentation/index checks and strict build apply before promotion. Monthly history remains append-only. Workspace recovery restored published content and required fresh reconstruction/review of unpublished drafts; lost historical local logs are not represented as still available. No gameplay or fresh browser test is claimed.

## Batch 316: Mushroom caps and Moss-form recovery

- Expanded Brown/Red Mushroom Block, Moss Carpet, Pale Moss Block and Pale Moss Carpet. The Mushroom owner already links both caps; the Moss owner table now links all five registered item forms, including unchanged strong Moss Block and Pale Hanging Moss controls. Existing shared mechanics and registry IDs remain intact.
- Source remains pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Complete cap loot and active harvesting distinguish intact Silk Touch recovery from zero-to-two small mushrooms. Neighbor placement and face-state updates explain the retained interior appearance without promising it resets after a neighbor is removed.
- Exact two-block-to-three-carpet recipes keep green and pale ingredients separate. Moss starters and propagation routes retain their actual feature/trade eligibility and placement limits. Pale carpet distinguishes the recoverable bottom piece from the non-dropping upper decoration; Bone Meal extends eligible wall covering rather than duplicating carpet inventory.
- Fresh independent source and Markdown review checks five complete loot tables, two exact carpet recipes, 65 source hashes, active consumers, patch reproduction, original headings/links and canonical controls. These are five existing item expansions and family navigation, not new placed-block articles. Final integrated documentation/index checks and strict build apply before promotion. Monthly history remains append-only; no gameplay or fresh browser test is claimed, and broad catch-up continues.

## Batch 317: Worn Anvil and head-trophy item routes

- Expanded Chipped Anvil, Damaged Anvil, Zombie Head and Creeper Head. Existing Anvil and Heads/Skulls owners already link these items; their shared mechanics and stronger controls remain unchanged. These are four existing item improvements, not new placed-block guides.
- Source remains pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Worn Anvil recovery preserves the exact stage and requires an unbroken pickaxe. Completed-use wear has its own twelve-percent roll and infinite-materials exclusion; falling uses a separate path. Final Damaged-stage destruction is distinct from collecting or replacing a worn anvil, and no repair-back-to-new recipe is invented.
- Zombie and Creeper Head acquisition follows the active charged-Creeper killer callback, selected victim types and shared special-output allowance. A Creeper's own explosion is not the other-Creeper victim reward. Equipment/disguise limits, standing/wall placement and note-block uses follow active consumers; ordinary Survival browser visibility remains separate from admission.
- Independent review verifies frozen artifacts/source identities, exact patches and all four outputs, plus 86 source-link occurrences and 66 local links/anchors. A separate source-approved wording delta makes the two worn-Anvil pickaxe phrases explicitly require an unbroken stack. Existing headings and routes survive. Final exact integration, documentation/index checks and strict build apply before promotion. Monthly history remains append-only; no gameplay or fresh browser test is claimed, and broad catch-up continues.

## Batch 318: Ordinary Flower uses and bounded sample checkpoint

- Expanded eleven small-flower and three tall-flower item entries. Their existing Flowers owner already links every selected variant; sixteen stronger controls and three canonical owners remain unchanged. These are fourteen existing item expansions, not new placed-block articles.
- Source remains pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Complete variant dye, stew and loot definitions are traced through active crafting, consumption, harvesting and placement. White Tulip produces Light Gray Dye. Stew effects belong to the consumed stew rather than the flower itself; configured ticks are not measured gameplay durations.
- Selected natural and Grass Block Bone Meal routes retain biome, position and placement conditions. The Forest lily/tall-patch distinction is explicit, and no selected example is presented as an exhaustive biome/trade/structure inventory. Small flowers do not acquire planted Bone Meal duplication merely because the grass beneath them can generate flowers. Tall flowers have their own two-half placement, single-item harvest and planted Bone Meal behavior.
- Independent review verifies 89 frozen source identities, complete selected recipe/loot evidence, 372 source-link occurrences, 200 local links, preserved headings, exact patch reproduction and active callers. Current combined-browser admission remains distinct from catalog visibility. Final exact integration, documentation/index checks and strict build apply before promotion; no gameplay or fresh browser test is claimed.
- Before integration, the other seventy entries in the separately selected 84-item sample matched their latest approved published hashes, including the later Primordial Soup issue crosslink. The fourteen frozen Flower outputs complete that bounded sample, and all eighty-four integrated hashes reconcile. This does not certify full item-registry coverage or whole-wiki completion.
- A new bounded masonry audit read 124 selected item pages and identified 85 useful shape/finish candidates under eight existing owners, while preserving 39 substantive controls including all fourteen Tuff items. Next groups are Decorative Stone shapes/polished finishes, Blackstone/Basalt and Sandstone, followed by Quartz, Brick/Nether Brick, End Stone/Purpur and Prismarine. Their audited gaps and isolated drafts are not completed work. Andesite Wall has an ordinary-fence connection overclaim to correct; the Sandstone table needs omitted ingredient alternatives added, while its existing representative routes remain valid. Monthly history remains append-only, and broad catch-up continues.

## Batch 319: Masonry material and shape item routes

- Expanded eighteen Granite/Diorite/Andesite shape or polished-finish entries, fifteen Blackstone/Basalt entries and seventeen Sandstone entries. The existing family owners already provide item routes. Strong base-item and redstone controls remain unchanged; these are fifty existing item improvements, not fifty new placed-block articles.
- Source remains pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Exact material identities, per-slot ingredients and crafting/cutting yields are checked. Decorative polished-shape crafting differs from its raw/polished cutting shortcuts; Blackstone finish progression, chiseled inputs, cracked-brick smelting and Polished-versus-Smooth Basalt orientation stay distinct.
- The Sandstone owner table now separates base, Cut and Smooth outputs and lists all reviewed same-color crafting alternatives. Base stairs accept base/Cut/Chiseled ingredients independently per occupied slot, while base slabs accept base/Chiseled. Stonecutting has its own narrower input sets. Existing representative table routes were valid; this adds omitted alternatives rather than declaring those routes broken.
- All fifty full loot tables and active correct-tool consumers are reviewed. An unbroken Wooden Pickaxe suffices for this selected set. Same-item Slab doubling preserves slab identity and returns two slabs; Silk Touch or Fortune does not invent a different output. Placement, waterlogging, axis and joining details route to their shared owners.
- Corrected Andesite Wall's ordinary-fence connection overclaim. The active wall rule accepts wall-tag members, suitable sturdy faces, Iron Bars-class blocks and aligned Fence Gates; panes remain valid. Six new wall drafts also name Copper Bars as a supported example, with the four unwaxed and four waxed forms traced through their factories and inheritance. This is an added supported example, not a claim that the earlier Iron Bars/pane examples were false.
- Independent family reviews verify 42 Decorative Stone, 39 Blackstone/Basalt and 33 Sandstone production recipes, with 18, 15 and 17 loot tables respectively. Exact source identities, original headings/links, controls and patch reproduction are checked, followed by the narrow Copper example review and final combined integration. Full documentation/index checks and strict build apply before promotion. Monthly history remains append-only; no gameplay or fresh browser test is claimed, and broader masonry/wiki catch-up continues.

## Batch 320: Quartz, Brick, End and Prismarine item routes

- Expanded eight Quartz, seven End Stone/Purpur, eight Prismarine, three ordinary Brick-shape and nine Nether Brick residual-form entries. Existing family owners already link the selected items, and their stronger controls remain unchanged. These are thirty-five existing item improvements rather than new placed-block articles.
- Source remains pinned to `78e8e0423084f010bb47e36132550619b37644c2`. Exact crafting alternatives and narrower stonecutting inputs are kept separate, including Quartz base/chiseled/pillar inputs, Purpur base/pillar crafting and direct End Stone cuts. Pillar axes, Smooth Quartz smelting, full Bricks versus loose Brick ingredients, and regular/red Nether finish identities follow their actual registrations and recipes.
- Nether Brick Fence uses four full Nether Bricks and two small Nether Brick items for six fences; its FenceBlock behavior stays separate from WallBlock connections. Prismarine Bricks uses nine Shards, and exact recipe JSON takes precedence over misleading cutting filenames. The active Conduit server consumer accepts its specified full-block IDs; a double slab does not become a valid frame material merely by occupying a full height.
- All thirty-five complete loot tables and active unbroken-pickaxe gates are reviewed, with Wooden Pickaxes sufficient for this selected set. Slab identity, one/two drops, waterlogging, stair/wall behavior and Copper Bars/pane connection examples follow their source-backed rules and shared owner guidance.
- Independent family reviews verify fifteen Quartz, thirty-two End/Purpur/Prismarine and twenty-two Brick/Nether production recipes, together with exact source identities, active consumers, frozen hashes, headings, links and patch reproduction. Final combined integration, documentation/index checks and strict build apply before promotion. Monthly history remains append-only; no gameplay or fresh browser test is claimed.
- The prior fifty masonry candidates match their approved published hashes, and these thirty-five exact outputs reconcile with the remaining audited paths. All eighty-five entries in that bounded selection are now addressed, with the thirty-nine strong controls preserved. Effective inventory routing, detailed article coverage and whole-wiki completeness remain separate checks; broader source-grounded catch-up continues.

## Batch 321: Effective item inventory and name-search distinctions

- Reconstructed the effective built-in item registrations at `78e8e0423084f010bb47e36132550619b37644c2`: 1,682 direct registrations, 24 Copper helper items and 191 TaCZ definitions produce 1,897 distinct IDs, including Air. Five static Item fields are aliases rather than extra registrations; additional placed-block mappings and component variants are separate. Every inspected ID already has an exact Items-index route, including the 24 shared CopperFixtures anchors. This is a source inventory and route reconciliation, not a runtime registry dump or article-completeness certification.
- Added focused search notes to Pewen Boat, Pewen Chest Boat, Ominous Catalyst, Dinosaur Pottery Sherd and Footprint Pottery Sherd. Default item names fall back to untranslated keys under the reviewed bundled-English conditions, and the browser searches the resulting hover-name string. Readable wiki aliases are retained; custom names, other languages and resource packs can change the search text. No live client/UI test or translation/gameplay fix is claimed.
- Added the explicit registered IDs to the two existing Trilocaris Tail entries without rewriting their reviewed food/use guidance. Other identity aliases, legacy URLs and current item routes remain intact. These seven item edits are identity/search maintenance with no substantive item-expansion credit.
- Resource-profile filenames without separate item registrations are not treated as missing item pages. Their absence from the registry does not prove that every other consumer ignores them. Creative-generated component variants likewise do not become new registry IDs, and registry presence does not establish browser availability or ordinary Survival acquisition.
- The next bounded content queue covers five TaCZ lasers, six fixed-name optics and nine banner-template entries. Their recipe, compatibility, active-consumer and reusable-template facts require separate source-grounded expansion; those drafts are not completed coverage. Independent inventory/name review, exact integration, documentation/index checks and strict build apply before promotion. Monthly history remains append-only, and broad catch-up continues.

## Batch 322: Laser, fixed-optic and banner-pattern item behavior

- Expanded the twenty existing entries selected after the effective-item inventory: five TaCZ lasers, six fixed-name optics and nine banner templates. These are substantive existing-item improvements, not new registry IDs or articles. Source remains `78e8e0423084f010bb47e36132550619b37644c2`; the reviewed registration inventory and all current index routes remain unchanged.
- Laser pages give exact Attachment Table costs, registered compatibility and installed-attachment handling. Imported laser color, beam, accuracy and handling fields are distinguished from the actual integrated consumers; full-bright model details do not establish a rendered beam. FN EVOLYS accepts Lopro/PEQ15 but lacks the bundled first-person laser mount, so the inspected submission skips those attachment meshes. This is a conditional source finding, not a running-game visual result or a claim that installation has no other visual effect.
- Optic pages separate product labels from selected zoom/FOV values, explain the inactive view-switch path and preserve complete exact gun fit lists. Elcan, QMK and Contender lack the selected scope_view_2 node and therefore use the inspected gun iron_view positioning fallback while FOV remains independent. Geometry and stencil admission are traced through active consumers; successful GPU output and runtime alignment are not claimed. Related source leads are supplied for independent tracker assessment without inventing issue associations or completed fixes.
- Banner templates distinguish exact paper recipes, Cartographer trade, Bastion loot and normal/ominous Vault reward chains. The Loom consumes the banner and dye while retaining the reusable template. Generic Banner Pattern hover names, the specifically named Bordure entry, pattern tags and costs follow the active source rather than unused description labels. Existing Field Masoned and shared Loom/Banners owner guidance remain intact.
- Independent reviews verify all twenty frozen outputs, exact source blobs and consumers, recipe/allowlist or loot chains, Markdown headings/links and isolated patch reproduction. The optics audit snapshot predates the corrected additional block-to-item alias count of 67; its selected queue and article claims are unchanged. Final combined integration, documentation/index checks and strict build apply before promotion. Monthly history remains append-only; broader wiki catch-up is still incomplete, and no gameplay or fresh browser test is claimed.

## Batch 323: Food, crop, Kangaroo materials and ammunition

- Expanded fifteen existing item guides: six food/crop supplies, four Kangaroo materials/foods and five materials/ammunition entries. Source remains `78e8e0423084f010bb47e36132550619b37644c2`; canonical mob, crop, food, brewing and equipment owners and all current routes are preserved. These are substantive existing-item improvements, with no new articles or whole-wiki completion claim.
- Food/crop entries now distinguish exact nutrition and effects from animal uses, precise crafting and remainder rules, selected loot/trades and active crop harvest behavior. Sugar is not player food; Cookies have the source-backed Parrot hazard; Beetroot Seeds lure Potoos without establishing a working breeding route. Apple/Toucan feeding-order caveats and the existing Sugar Glider link remain intact.
- The four Kangaroo entries separate registered Creative/category access and actual eating values from absent built-in supply or crafting routes. Inherited/default loot resolution, custom hooks, pouch returns and complete bounded resource searches were reviewed: returning previously supplied pouch contents is not new hide/meat production. Missing built-in recipes do not establish upstream cooking or burger behavior. Data packs, assigned loot and custom components can change the scoped conclusions.
- Material/ammunition entries cover conditional Phantom drops and cat gifts, Elytra repair and brewing, Raw Gold mining/processing, Feather recipes and trading, Flint Fortune/conversion rules, and Spectral Arrow crafting/bartering, accepted-hit Glowing and pickup. The non-piercing discard statement explicitly excludes the accepted Enderman early-return path; no universal damage or recovery guarantee is made.
- Independent reviews checked frozen source/baseline/output hashes, full relevant data branches and active consumers, configured Markdown, preserved anchors/links and exact isolated patch reproduction. Before integration, four orphan food-source references were repaired and the Spectral Arrow exception was qualified; the publisher separately inspected their exact source and final deltas. Final combined documentation/index checks, strict build and supplemental QA apply before promotion. No gameplay or fresh browser test is claimed; monthly history remains append-only.

## Batch 324: Remaining Pewen, Resin, storage and garden item details

- Expanded twenty-two existing entries selected by a content-based residual audit: seven Pewen forms, five Resin masonry forms, five resource-storage blocks, three garden items and two utilities. These are substantive item improvements, not new registry entries or placed-block articles. Nine canonical block owners and ten stronger item controls remain intact. Source remains `78e8e0423084f010bb47e36132550619b37644c2`.
- Pewen guides now distinguish exact valid recipes from unavailable built-in stripping routes. Four Stripped Pewen Logs crafting three Stripped Pewen Wood does not establish how ordinary Survival obtains those logs. Hand recovery, absent default axe-speed/fuel support, log-axis orientation, slab/door counts and actual placement rules follow the inspected registrations, tags and consumers; the existing sign issue remains limited to sign forms.
- Resin and storage pages separate small ingredients from full blocks, shaped batches from narrower stonecutting inputs, actual packing/unpacking, broken-tool and tier gates, complete loot and conditional placed-block behavior. Beacon base membership is distinct from payment-slot acceptance, and Raw Gold-block Piglin interest does not make the block barter currency.
- Garden/utility guides cover exact recipe or acquisition limits, planting/support/growth, applicable Bee/Piglin interactions, pressure signals and fuel. Farmland mining returns Dirt even with Silk Touch; creating farmland in the world is distinct from obtaining its inventory item. Soul Lantern has no correct-tool drop gate in the reviewed MattMC source. Shared owner guides retain deeper placed-block rules.
- Independent reviews verify exact source/baseline/output hashes, recipes and tag sets, active consumers, configured Markdown and isolated patch reproduction. Before integration, thirty-five published anchor IDs were restored in the ten Resin/storage drafts and eighteen undefined source-reference occurrences across seven of those pages were repaired; the publisher inspected the eight supporting source destinations. Final combined checks and strict build apply before promotion. Monthly history remains append-only, and no gameplay, fresh browser or broad completion claim is made.

## Batch 325: Sidearm acquisition, active stats and nominal speed

- Expanded nine existing sidearm entries: B93R, CZ75, MK23, M9A4, Deagle, Golden Deagle, M1911, P320 and Timeless .50. Exact crafting inputs, ammunition, supported attachment sets, active firing/reload consumers and source-only limits supplement the retained item routes and published anchors. These are substantive existing-item improvements, not new articles. Item and projectile citations remain pinned to `78e8e0423084f010bb47e36132550619b37644c2`; all sixty-two cited source paths were verified byte-identical after the FROM-master sync to `cc140840a21e5c6c932c23abf34124418d6506b0`.
- Guides separate registered fallback shot values from active distance bands and code-defined spread/headshot behavior. A declared Pierce value of zero is clamped to a one-successful-entity-hit budget. B93R SEMI adjustments and MK23's one-shot BURST path are explicit; count-one firing does not inherit a blanket multi-round scheduling warning. Definition reload durations and tick-rounded cooldowns are kept distinct from imported animation timing and measured performance.
- Independently audited every registered gun's explicit speed rows: all sixty-three already equal the Java base launch setting multiplied by twenty. Ninety-four of ninety-five inspected speed claims are correct in nominal/configuration context, while the Minigun numeric row is already disclosed as declared-only metadata. No wrong-unit number was found, and no bulk numerical correction is made. The new item wording clarifies configured blocks-per-tick and nominal twenty-TPS conversion; one shared Firearms paragraph explains mode adjustments, spread, inherited shooter movement and subsequent flight/tick effects.
- All existing compatible attachment rows remain exact. The shared owner's original source review and all unrelated guidance remain intact; its new source note is limited to speed interpretation. No runtime firing, reload, projectile-speed or broad renderer-parity test is claimed.
- Independent article reviews verify exact frozen bytes, recipes/data/active consumers, full and shortcut references, intended destinations, preserved headings/links and isolated patch reproduction. Five reviewed drafts gained explicit nominal-speed wording and tick citations plus three corrected above/below references before integration. The publisher inspected the new shared-owner source chain and review deltas. Final combined checks and strict build apply before promotion; monthly history remains append-only and broad catch-up continues.

- Publication guards detected the incoming `37817e128b99b07456c0ee22a5d34eaf05c72150` rendering checkpoint, `96659c1193c2ff0344d198ab4f458c752a9796b9` prompt update and `cc140840a21e5c6c932c23abf34124418d6506b0` player-distance migration. The branch was fast-forwarded from master while preserving these twelve pending documentation paths exactly, then the checkpoint wording and full documentation validation were refreshed. Incoming source-authored documentation is retained; detailed rendering/chunk-distance reconciliation belongs to the coordinated source review.

## Batch 326: Coordinated source and issue review, October 4 at 17:45 UTC

- Reconciled the incoming renderer follow-up `37817e128b99b07456c0ee22a5d34eaf05c72150`, prompt update `96659c1193c2ff0344d198ab4f458c752a9796b9` and player-distance migration `cc140840a21e5c6c932c23abf34124418d6506b0` after the safe FROM-master sync. The refreshed Goal 5 owner preserves the older failed comparisons and records later scoped coast/lighting/termination evidence without broad parity, stability or overall performance acceptance. Source-authored rendering procedures and historical evidence remain intact.
- The player-distance guide now distinguishes exhaustive action checks from sampled walk checkpoints, GC stress from measured native release, and a production-adapter benchmark driver from a complete DistanceManager dispatch workload. The author's measurements remain credited and bounded. Natural Spawning gains only an implementation follow-up link; no gameplay distance/cap change or new live-server test is asserted.
- The tracker review verified six open-issue progress comments: [performance #709](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-5982769674), [DH #745](https://github.com/HungLo2020/MattMC/issues/745#issuecomment-5982770594), [terrain/shadow ownership #520](https://github.com/HungLo2020/MattMC/issues/520#issuecomment-5982771278), [resource/capture scope #758](https://github.com/HungLo2020/MattMC/issues/758#issuecomment-5982771898), [canonical renderer #747](https://github.com/HungLo2020/MattMC/issues/747#issuecomment-5982772544) and [player-distance #776](https://github.com/HungLo2020/MattMC/issues/776#issuecomment-5982773396). Its independent 79 Python rendering-tool tests at `37817e1` passed; Java/Rust/native runs, benchmarks and gameplay captures were inspected as author-recorded evidence rather than independently rerun.
- Existing [#811](https://github.com/HungLo2020/MattMC/issues/811) now covers LPVO, Elcan, QMK and Contender selected-view mismatches; the three newly linked guides preserve separate FOV and source-only qualifications. New [#814](https://github.com/HungLo2020/MattMC/issues/814) tracks FN EVOLYS accepting Lopro/PEQ15 despite its missing bundled first-person laser mount. Both laser guides and the gun's narrow warning link it. No issue is presented as a completed gameplay fix; the inspected TaCZ paths remain unchanged through `cc14084`.
- Eight tracker mutations comprise that review: one existing issue scope expansion, one new bug and six progress comments. No closures or milestone changes occurred. This documentation batch adds no new articles or substantive item-expansion credit; its six item changes are bounded issue crosslinks/warnings. All previous monthly entries, source history and batch 325 content are preserved. Full docs/index validation, strict build, source/link checks and guarded nonforce promotion apply; no fresh live-browser or gameplay test is claimed.

## Batch 327: Shotgun recipes, projectile values and reload rules

- Expanded seven existing item guides: AA12, DB-4, DB-2, Winchester 1897, M1014, M870 and SPAS-12. Their current recipes, 12 Gauge supply, active modes, cooldowns, damage bands and reload behavior replace generic acquisition and unqualified stat-table interpretations. All published routes, headings and correct attachment lists remain intact. These are seven substantive existing-item improvements, with no new articles.
- Reviewed sources remain pinned to `78e8e0423084f010bb47e36132550619b37644c2`, with relevant source identities verified unchanged through current source `cc140840a21e5c6c932c23abf34124418d6506b0` and the latest published documentation. The newer Firearms speed explanation and source/issue checkpoint remain preserved. Correct nominal speed numbers are retained; no conversion-error claim is made.
- The guides distinguish total shot values from per-projectile damage, exclusive distance bands, qualifying headshots and successful-hit limits. DB-2 and SPAS count-one BURST paths fire directly, so they do not receive a blanket multi-round scheduling warning. SPAS mode-specific adjustments and Winchester's distinct gravity are explicit source values, not measured flight or combat results.
- Fixed-duration and shell-dependent reload paths are traced through their active timing and completion consumers. Shells are granted at completion rather than incrementally during imported animation segments, and first-matching-stack supply can limit the refill. Winchester's declared extended capacities are separated from its lack of compatible attachments, so normal refitting does not promise those capacities.
- Independent review narrowed three unpublished reload-interruption sentences to switching to a different item type, matching the active same-item check; the nine published sidearm guides and shared owner already used valid keep-holding advice and needed no correction. Exact source/baseline/output hashes, configured full/shortcut references, preserved links/anchors, recipe/consumer data and isolated patch reproduction pass. Final combined checks and strict build apply before promotion. No runtime or fresh live-browser test is claimed; monthly history remains append-only and broad wiki catch-up continues.

## Batch 328: Submachine gun recipes, fire modes and capacity limits

- Expanded seven existing item guides: HK-MP5A5, P90, UMP45, Vector, MP40, M1A1 Thompson and UZI. Exact Gun Smith Table and ammunition recipes, initial loaded state, active controls, cooldowns, reload rules, damage bands and supported attachments replace generic acquisition and incomplete use guidance. The seven guides are substantive existing-item improvements, not new articles; all original headings and 258 exact attachment links remain preserved. Source review is pinned to `cc140840a21e5c6c932c23abf34124418d6506b0`.
- HK-MP5A5, P90, UMP45 and Vector have active multi-round BURST queues; P90 alone repeats BURST while Shoot is held. Their nominal task offsets remain distinct from observed round spacing under the unresolved shared [#810 scheduling limitation](https://github.com/HungLo2020/MattMC/issues/810). P90's BURST damage, headshot and spread adjustments are explicit. Thompson's imported burst settings do not make BURST selectable, and UZI supports AUTO only.
- MP40 and P90 declare extended-capacity arrays without an accepted Extended Mag installation route. Thompson retains its actual Light I/II/III capacities of 30/30/1 and the delayed ammunition-clamp warning linked to [#809](https://github.com/HungLo2020/MattMC/issues/809); no intended fix or corrected capacity is invented. Current nominal projectile-speed values and shared owner explanations remain intact.
- Independent source and article reviews verify recipes, active consumers, numeric values, exact baseline/output identities, preserved anchors and attachment sets, configured full/shortcut references, source ranges, local destinations and isolated patch reproduction. Three unpublished Refit citations were extended to include the removal/storage methods without changing prose. Combined documentation checks and strict build apply before promotion. No runtime firing, reload, combat or fresh live-browser test is claimed; monthly history remains append-only and broad catch-up continues.

## Batch 329: Grip crafting, compatibility and active camera effects

- Expanded twelve existing grip entries: SI, Hera CQR, Talon AFG1, SE-5, P-2, RK-0, RK-1 B25U, RK-6, TD, Nagoma, Koch Ranger and Talon SG2. Each now gives its exact Attachment Table recipe, one-item output, stack limit, installation route and accepted gun set. All 236 compatible-gun links and 72 original headings remain preserved. These are substantive existing-item improvements, not twelve new articles. Source review is pinned to `cc140840a21e5c6c932c23abf34124418d6506b0`.
- The guides distinguish camera pitch and yaw contributions from the separate weapon-model animation and from direct projectile spread/damage. SI, RK-6 and TD have no loaded recoil modifier; identity factors do not remove recoil. Imported weight, ADS and accuracy fields are not promoted into working bonuses. AFG1's level 2 and SE-5's level 5 remain explicitly stored metadata, without an active grip-tier effect. Camera rotation can still affect subsequent aiming; no measured final reduction or runtime recoil test is asserted.
- Every accepted bundled gun geometry has the grip mount required by the reviewed first-person path. FN EVOLYS's existing laser-mount limitation remains specific to its Laser slot. Koch Ranger's imported red-laser metadata does not establish a working beam or toggle. Conditional default-grip/handguard visibility and resource behavior remain bounded to the inspected consumers; node presence is not a visual acceptance test. Shared Firearms, workbench and source/issue owners remain unchanged.
- Independent reviews verify exact source/baseline/output bytes, active consumer and recipe facts, original rows and destinations, configured full/shortcut references, source ranges and isolated patch reproduction. Combined documentation checks and strict build apply before promotion; no fresh live-browser, combat, crafting, refitting or multiplayer test is claimed. The monthly entry is append-only, and broader wiki catch-up continues.

## Batch 330: Remaining masonry entries and Building Wand accuracy

- Expanded the existing Mossy Cobblestone, Mud Brick Stairs and Mud Brick Wall item entries with exact crafting/stonecutting inputs and yields, correct-tool recovery, ordinary drops and links to canonical placed-block behavior. Original headings, exact IDs and valid stair guidance remain preserved. These are three substantive existing-item improvements, not missing registry routes or new articles. Mossy Cobblestone acquisition is bounded to verified recipes and collection; no complete natural-generation inventory is claimed.
- Corrected the existing Building Wand article's false no-recipe and 256-block statements. Current source already provides one Diamond above one Stick for one wand and a cap of 128 successful actions per use. This is documentation accuracy work, not a new recipe or gameplay-limit change. Existing Notes/Trivia anchors and the historical recipe-request link remain intact.
- The current wand still synthesizes face-center hits; its source-reviewed slab-placement limitation remains linked to open [#720](https://github.com/HungLo2020/MattMC/issues/720). [PR #794](https://github.com/HungLo2020/MattMC/pull/794) was reviewed as unmerged. Its proposed preserved click offsets and resolved-target permission behavior were not copied into current-master guidance. The recipe/limit documentation overlaps that draft PR and requires deliberate reconciliation during a later PR rebase or review; this batch makes no PR or code mutation.
- All four items are reviewed against `cc140840a21e5c6c932c23abf34124418d6506b0`, preserving the current grip and source-review work. The wand change is tracked separately as a bounded accuracy correction. Independent source/article review, exact patch replay, reference/anchor checks and final combined documentation validation apply. An unpublished wand recipe citation was narrowed to the file's actual sixteen-line range. No runtime crafting, placement, breaking, combat or fresh live-browser test is claimed; monthly history remains append-only and broader catch-up continues.

## Batch 331: Rifle crafting, reloads and reachable capacities

- Expanded seven existing rifle entries: Accuracy International AWM, M107, M700, M95, Mauser Kar98k, M1 Garand and Springfield 1873. Exact Gun Smith Table recipes and ammunition, initial loaded state, supported controls, cooldowns and actual reload consumers replace incomplete Creative-only acquisition and generic usage. All seven craft in the Sniper group and support SEMI only. The seven substantive existing-item improvements add no new articles; all 56 original headings and 136 exact attachment links remain preserved.
- AWM, M107, M700 and M95 recipe scope presets are ignored by the active workbench output parser, so fresh crafted outputs are loaded but unscoped. Accepted magazine levels select actual replacement capacities; downsizing can discard excess ammunition when a later shot applies the clamp. Kar98 and Springfield's declared capacity arrays do not establish a supported Extended Mag installation route; Garand retains its muzzle-only fit list.
- Kar98 has scope-dependent empty reloads and missing-round-dependent partial reloads. Springfield uses the active 59-tick override rather than its fallback definition timing, while Garand and the four modern sniper rifles use their documented fixed durations. All rounds arrive through completion, with the existing first-matching-stack supply restriction. M107's fallback Damage 55 and Garand's fallback 38 remain distinct from their active near-distance bands of 50 and 42. Existing nominal speed numbers and all 142 original value/falloff rows remain represented; no numerical conversion correction or multi-round BURST warning is invented.
- Sources remain pinned to `cc140840a21e5c6c932c23abf34124418d6506b0` and verified against the latest documentation baseline. Independent article reviews validate active recipe/consumer facts, exact identities, accepted attachment IDs, full/shortcut references, source ranges, local anchors and isolated replay. Two unpublished copy edits fixed the wording “a 8-tick” and “muzzles below” without changing facts. Final combined documentation checks and strict build apply before promotion; no runtime crafting, firing, reload, combat or fresh live-browser test is claimed. Monthly history remains append-only and broad catch-up continues.

## Batch 332: Everyday controls, recipe display and Librarian planning

- Added four missing player owners: Recipe Viewer, Inventory Controls, on-foot Movement and Librarian Trades. Links added in seven existing navigation or related-page files connect them through existing Mechanics, Crafting, Trading and item/block owners while preserving all previous content. The existing Armadillo Spawn Egg entry now links the shared egg/browser/care owners and explains its supported baby interaction before the species food refusal, separately from adult-only scute production. That is one bounded existing-entry improvement, not a new mob or item registration.
- Recipe Viewer documents the active R-key opening route, focus-sensitive navigation, original-container return, six supported recipe families, fixed Coal indicator and output-only lookup. Campfire Cooking and non-recipe acquisition remain outside its displayed coverage. The integrated-manager versus remote-client data path and static-cache refresh limits are described as source-derived conditions, not a universal multiplayer failure, live reproduction or proof that an empty lookup makes an item unobtainable.
- Inventory Controls separates hardcoded GUI modifiers from configurable gameplay bindings, ordinary clicks/dragging/quick transfers from special slots and Bundle behavior, and browser-panel deletion requests from ordinary outside dropping. The existing Survival protocol gate remains explicit. Movement distinguishes sprint initiation from continuation, crouch intent from collision pose, input factors from measured speed, and conditional edge protection from immunity to falls. Shared effects, food, water, flight and transport owners retain their scopes.
- Librarian Trades covers all fourteen ordinary possible listings, book selection/pricing and finite stock, then separately documents the seven stored-type pools under optional Trade Rebalance. The Book-and-Quill listing's declared count of two is distinguished from its actual stack-limit-clamped payment of one through both satisfaction and consumption. Ordinary Mending offers are not restricted to Swamp type, and possible pools are not guaranteed stock. Existing offers are not claimed to be rewritten by changing feature settings.
- Independent reviews verify active source and consumer chains at `cc140840a21e5c6c932c23abf34124418d6506b0`, exact candidate identities, recursive tags, configured reference forms, source ranges, local anchors and isolated patch reproduction. Before publication, Inventory Controls gained a precise restricted-slot capacity sentence and a source-range extension for the double-click condition. Final combined hierarchy checks and strict build apply before promotion. No live UI, movement, trading, crafting, spawning or fresh browser test is claimed. These four new articles and one entry improvement close the bounded six-subject audit queue together with batch 330's wand correction; they do not certify whole-wiki completion. Monthly history remains append-only and firearm/attachment coverage work continues.

## Batch 333: Handcannons, machine guns and special-weapon behavior

- Expanded eleven existing item owners: Raging Bull, .357 Rhino, .22 Modle 943, Taurus Raging Hunter, .30-06 Lonetrail, FN EVOLYS, M249, RPK, Ray Gun, M320 and RPG-7. Exact recipes and groups, loaded initial state, ammunition, active firing/reload paths and attachment limits replace incomplete acquisition and use guidance. All 89 original headings, 219 value/falloff rows and 227 inline links remain represented. The 199 distinct accepted gun/attachment pairs are preserved, including the two additional repeated laser links inside FN EVOLYS's byte-identical [#814 warning](https://github.com/HungLo2020/MattMC/issues/814). These are eleven substantive existing-item improvements, not new articles or registrations.
- Raging Bull's loaded-count-dependent reload lookup is documented without assuming a monotonic progression or using the fallback definition duration. The active M320/RPG overrides and the other fixed timings are distinct from imported animations. Empty FN EVOLYS and M249 magazines cannot be filled to even base capacity from one normal ammunition stack through the first-matching-stack reload path. Supported Heavy magazine capacities and next-shot downsizing loss remain explicit. Lonetrail's recipe scope and RPK's recipe stock presets are ignored by the current output parser.
- The three special weapons create the same ordinary direct-hit projectile type as the other audited guns. Imported explosion, ignition and armor-ignore fields are not promoted into server effects, while the separate client particle path remains documented. Ray Gun and other guns' active distance bands remain distinct from their definition fallback Damage values. M320/RPG's declared Pierce zero is separated from the effective one-successful-hit budget. Correct nominal speed numbers remain unchanged, and none of these supported modes receives an inapplicable multi-round scheduling warning.
- Independent source and article reviews verify the current consumers at `cc140840a21e5c6c932c23abf34124418d6506b0`, exact frozen source/baseline/output identities, original rows and links, configured full/shortcut references, local anchors, source ranges and isolated patch reproduction. Shared Firearms/ammunition/workbench owners, prior player-workflow guides and FN's bounded laser warning remain intact. Final combined documentation validation and strict build apply before promotion. No runtime crafting, firing, timing, combat, particle or fresh live-browser test is claimed; monthly history remains append-only and stock/other coverage work continues.

- Batch 333 publication guards caught this incoming default-branch commit before any tree upload. The direct fast-forward needed no stash and had no conflicts; change-only recovery patches and the original thirteen-file manifest were retained. The eleven approved article bodies and monthly entry remain byte-identical, while this current checkpoint records the new default snapshot. Source-authored migration results are incoming work, not code authored or benchmarks rerun by this wiki batch.

## Batch 334: Reconciled source review and publication recovery

- Reconciled incoming default snapshots `9bafc14d2e2943dcfe8a37e9e81dc001386b88bb` and `5218ac875eda9f2c4151ff1a97795f20f5f356cf` while preserving their source changes and author-recorded results. Rust owns the natural-spawn/player-ticket, simulation, loading and POI distance graphs; Java retains player sets, ticket storage/persistence, POI records, holder policy and published views. Four developer owners qualify minimum-relevant notifications, copied loading/predicate-backed POI fixtures, GC survivor assertions and subsystem benchmark scope. The current driver selects sixteen Java methods and eight chunk-distance Rust tests; four queue tests are separate. These were inspected, not rerun. Author benchmark tables remain intact. [The latest #776 review](https://github.com/HungLo2020/MattMC/issues/776#issuecomment-5985702839) preserves outstanding acceptance.
- The Recipe Viewer owner distinguishes its six RecipeManager families from TaCZ's separately loaded recipe lists; an ordinary supported recipe for the same item remains possible. Its source-derived session/cache warning links [#816](https://github.com/HungLo2020/MattMC/issues/816), whose remote support needs an explicit display-data contract. [#815](https://github.com/HungLo2020/MattMC/issues/815) separately tracks the OEM stock display contract across seventeen accepted pairs/eight guns; installation, recoil and default-stock exceptions remain separate. Neither issue is a gameplay fix or runtime reproduction.
- The original five-file review payload and append-only history proof were preserved after publication was blocked. A newly authorized checkpoint was prepared against the newer default head, with exactly two concise monthly entries appended and no historical bytes removed. The current authored delta is documentation only; source sync and guarded promotion preserve incoming history. Full documentation checks, strict build, source/reference/anchor review and remote readbacks apply before completion is reported.
- Broader catch-up candidates remain separate from this bounded checkpoint: attachment and block expansions, player workflow guides and their navigation have isolated source reviews and builds, but are not credited as published by this batch. No Java/Rust/gameplay/benchmark or fresh live-browser run is claimed. Broad wiki coverage is still being reconciled; page counts are not a completion measure.

## Batch 335: Attachment depth and canonical block behavior

- Expanded thirty-six existing attachment item owners: fourteen stocks, six bayonet/barrel/brake/choke items and sixteen optics. Original source-grounded prose covers exact recipes and accepted fits, active recoil/aim controls, model-selection limits and unused bundled properties. The three OEM stock limitations remain linked to [#815](https://github.com/HungLo2020/MattMC/issues/815), with installation/recoil distinct from adapter display and no gameplay fix claimed. Dedicated built-in optic metadata is not treated as automatic installation; unused zoom/melee/spread fields are not presented as working abilities.
- Brewing Stand and Redstone Dust receive substantive placed-block behavior coverage. Chest receives a canonical joining/access clarification; Chest, Glass Bottle and Furnace item pages receive bounded owner bridges. These four narrower repairs are recorded separately from substantive expansions. Chest's first interaction can detach the player's Lead before the chest callback; this precedence is preserved in the corrected bridge.
- All forty-two exact article candidates passed independent source review and isolated integration, including source hashes/ranges, original headings/links, configured references and patch replay. The newer `5218ac875eda9f2c4151ff1a97795f20f5f356cf` source was checked separately: the historical pickaxe-tag difference adds Ambersol only and does not change Brewing Stand membership. Final combined documentation check and strict build apply on the published review checkpoint before promotion.
- This closes the thirty-six queued attachment candidates from the bounded 182-page depth audit; it does not certify every game mechanic or whole-wiki completion. Ten player-workflow owner candidates remain a separate reviewed batch. No crafting, firing, aim, rendering, redstone or brewing runtime test is claimed. One concise monthly entry is appended with all prior history preserved.

## Batch 336: Player workflow owners and navigation

- Added ten substantive owners: LocalWorlds, MultiplayerAndLAN, GraphicsAndPacks, ChatAndCaptions, AdvancementsAndStatistics, MinimapAndWaypoints, DedicatedServers, WorldDataPacks, GameRules and WorldEdit. Six existing navigation owners provide actual entry routes; related links connect the new guides without duplicating their detailed responsibilities. Minimap belongs under MattMC tools rather than shared vanilla-derived gameplay.
- The guides follow active source routes for creation and closed-world backups, LAN/session permissions, current dedicated launch/save paths, graphics/resource/shader selection, chat/caption timing, progress/reward/statistic gates, local map data, world-pack staging/reload failure boundaries, rule editing/shared persistence and block-state editing history. English labels account for bundled namespaces and deprecation transforms. Source-only limits remain explicit: no tested hosting, restoration, pack compatibility, visual parity, complete server-policy enforcement or online identity-authentication assurance is inferred from a menu or setting.
- Original source review at `9bafc14d2e2943dcfe8a37e9e81dc001386b88bb` was reconciled against `5218ac875eda9f2c4151ff1a97795f20f5f356cf`. Seventeen affected source excerpts remained identical; the world-pack reload owner now includes the new village-centre membership refresh without promising immediate distance propagation. Current Java launch packaging remains distinguished from the final one-Rust-executable client/server target, with at most one separately loaded Rust library and no Java.
- Independent source and combined integration reviews verify exact candidate bytes, immutable source ranges, configured references, original headings/links and actual generated HTML. Full documentation checks and strict build apply at the current published default before promotion; one concise monthly entry preserves every prior historical byte. No game, server command, save/restore, pack, map, network or fresh live-browser test is claimed.
- Broad completeness is not inferred from registry rows or guide counts. The final-gap audit found a separate Region Editor workflow under Tools that needs a source-grounded owner covering saved-world selection, immediate writes and undo limits. That concrete gap remains separate from this batch; ongoing source reconciliation continues.

## Batch 337: Region Editor and final bounded coverage audit

- Added the separately implemented Tools → Region Editor owner and four navigation links. It documents the saved Overworld terrain scope, world/selection loading, separate region/chunk selections, private clipboard, preview versus immediate writes and temporary undo eligibility. One selected 32×32 region counts as 1,024 positions and exceeds the 256-position deletion undo threshold. Lock observation is not described as editor lock enforcement; populated-map status rendering is not promised to show successful writes or undo-skip feedback. Closed-world backup and read/write limitations remain explicit, with no actual editor, save, deletion, copy, restoration or runtime test performed.
- A fresh structural audit independently reconstructs 1,235 block IDs (1,211 direct plus 24 helper-created forms), verifies both directory/category ownership routes, and resolves 1,897 item IDs through current item navigation. All 239 entity registrations/class lineages were classified: 160 player-relevant mob owners, three internal body parts and 76 vehicle/projectile/display/other non-mob registrations. These are identity and navigation findings, not evidence that every variant behavior is complete.
- The bounded final prose review inspected family exceptions, shorter mob owners, twenty representative item/shared-item pages and six other owner scopes. It established no additional high-value block/mob/item rewrite gap in that sample. It did identify Region Editor as a separate missing player workflow, now covered here. The former four misleading generic acquisition/effect templates were absent from current item entries; the remaining repeated TaCZ footer is a family identifier rather than an effect claim. No all-paragraph or runtime-completion certificate is inferred from these results.
- Source and candidate identities are pinned to `5218ac875eda9f2c4151ff1a97795f20f5f356cf` and the verified documentation snapshots. Independent owner review checks the active menu/input/disk paths, immutable references, actual sibling links and exact candidate bytes. Full combined documentation check, strict build and reference/anchor review apply before promotion; one concise monthly entry preserves prior history. Continuing three-hour source/tracker/changelog review remains necessary as the implementation changes, and known implementation limitations remain documented rather than replaced by upstream assumptions.

## Batch 338: Remaining world, enchantment and HUD owners

- Added World Types for the six normal generation presets, Alt-accessible extended Debug entry, two customization editors and nine flat presets. It preserves the initial Superflat/Classic Flat distinction, export-string and shared-settings Cancel limits, and loaded-dimension precedence. Skyblock and Debug details are source-bounded; biome-size multipliers, successful tree/player placement, complete progression and runtime world-generation parity are not inferred from preset names or definitions. Existing creation, dimension, biome and conditional Skyblock-reward owners retain their responsibilities.
- Added Feather Falling under the existing enchanting taxonomy. The guide covers seven tagged Boots, levels I–IV, the three eligible fall-damage IDs, three protection points per level, the combined twenty-point cap, Protection compatibility and verified acquisition gates. Percentages describe the enchantment stage rather than every final damage outcome. Retained broken-equipment behavior remains an unresolved source-qualified [#800](https://github.com/HungLo2020/MattMC/issues/800) limit, not a fix. Existing Ender Pearl and protection references now reach this owner.
- Added the built-in Locator Bar owner, with the active server packet/client/native-GUI chain, received-experience and jump-display priorities, hiding conditions and operator listing/icon controls. The 332-block boundary concerns precision rather than a universal detection cap; chunk view distance is distinguished from terrain obstruction. Spectator, coarse-height, texture/admission and runtime-image limits remain explicit. VoxelMap's saved local waypoints remain a separate owner.
- The remaining core-topic audits reconstructed sixty-eight biome resources, thirty-four structure definitions/twenty structure sets, seven world presets and four destination keys; no additional ownership gap was established in the bounded world sample beyond World Types. All forty-three active enchantment definitions have meaningful owner scopes after this Feather Falling addition. Forty-five registered effects and forty-six potion types already have routes; expanded brewing registration yields sixty-three content transformations and two container conversions, with Luck's lack of a Water-rooted route already documented. These are source/navigation and bounded consumer findings, not all-paragraph or runtime certification.
- Exact owner bytes, source ranges, language labels, rendered tables and current sibling links were independently reviewed. The three owners and navigation integrate with full documentation checks, strict build and configured reference/anchor QA before promotion. One concise issue-linked monthly entry preserves prior history. No gameplay, generation, command, potion, combat, packet-delivery or fresh live-browser test is claimed; continuing source/tracker/changelog maintenance remains necessary.

## Batch 339: Shared systems and practical completeness gaps

- Added shared Loot and Drops, Ticks and Chunk Activity, and Light owners after a bounded cross-system audit. Loot connects active reward contexts, player credit, conditions, pending storage and pickup/age behavior; merging can retain the younger item age. Ticking distinguishes random, scheduled, entity and device paths, loaded/view/simulation ranges, saved queues and server pause. Light distinguishes emission, block/skylight, raw versus sky-darkened queries, propagation and the optional local debug readout. None promises runtime farm output, universal spawnproofing or wall-clock completion.
- Added a bounded Farmer-tending section to Villager, preserving existing content and anchors: six planting items differ from the four mature food crops harvested by that behavior, and Bone Meal uses separate eligibility and rule gates. Crafting clarifies offset/mirrored patterns, occupied ingredient slots and close-grid return/overflow; special recipes retain their existing detailed owners, with a small Recipe Viewer signpost preserving the tracked cache/data limitations.
- The breadth audit found meaningful distributed ownership for general farming/automation, explosions and redstone. A separate recipe audit enumerated 1,501 bundled declarations across twenty-two serializer families and verified usable shared/special owners; resource declarations are not a runtime-loaded recipe dump. These audits evaluate useful topic ownership and bounded consumer claims, not every possible farm blueprint, circuit or paragraph. Their identified additions are recorded here rather than equating registry routes or an empty draft queue with universal completeness. Historical monthly backfill remains non-exhaustive.
- Independent reviews preserve exact candidate hashes, prior headings/links and immutable source ranges, and compare supporting objects with incoming `858476969d6500b2a9e26d10c5d8c5967222ccfe`. The corrected scheduling wording restores saved delays when a chunk starts ticking again. Full integrated documentation checks, strict build and reference/anchor QA apply before promotion; one concise monthly entry appends without removing history. No game, native, world-generation, command, inventory, lighting or fresh public-browser test is claimed. Ongoing source/tracker/changelog maintenance remains necessary.

## Batch 340: Native fill review and VoxelMap tracking

- Reconciled the source-owned eligible NOISE-stage migration at `858476969d6500b2a9e26d10c5d8c5967222ccfe` with [the bounded #775 review](https://github.com/HungLo2020/MattMC/issues/775#issuecomment-5987249254). Rust owns the eligible per-block fill, ordered section replay/counters, worldgen heightmaps, marks and built-in ore/aquifer positional randomness; Java retains cell/cache preparation, object lifetime/installation and stage orchestration. Ineligible chunks retain the Java fill loop, and native-selected failure is not a silent fallback. This does not close #775 or migrate every worldgen random owner.
- Corrected the source document's ore-test wording: every candidate must take the native route, while ore/raw-ore coverage is asserted across the aggregate fixture, not each individual chunk. Three Java methods and eight added native noise-fill tests were inspected; the driver runs a broader Rust filter. Independent static reconstruction matched eight production Java files from twenty-one rewrites and three distance-driver files from fourteen rewrites. No suites, mutation tests, benchmarks or live worlds were rerun in maintenance. Author-reported 20/33/40 percent reductions remain explicitly elapsed medians per eight-fill sample, excluding construction and later generation stages; benchmark checksums are narrower than detailed parity assertions. Historical measurements and raw-result limitations remain preserved.
- Linked the existing Minimap/Waypoints limitation to [#817](https://github.com/HungLo2020/MattMC/issues/817): active settings/world-identity delivery is disconnected, and conditional ready-image submission needs a separate permission-consumer check. Working local mapping, legacy cave chat and manual identity paths remain distinguished. The issue is a source-qualified finding, not a gameplay fix, live restriction-bypass test or blanket multiplayer failure.
- This four-document checkpoint appends two concise issue-linked monthly entries without removing history. Documentation checks, strict build and immutable reference/anchor review apply before guarded promotion and terminal deployment verification. The completed batch 339 article cutoff remains separate from this source/tracker checkpoint; broad ownership audits do not certify every paragraph or runtime behavior, and historical changelog backfill remains non-exhaustive.

## Batch 341: Native router source review

- Reconciled the native interpolation-slice router at `da1109de6fe84592bf75e32cffef0cb5506d2651` with [the #775 progress review](https://github.com/HungLo2020/MattMC/issues/775#issuecomment-5988872723). Router rejection retains Java slice evaluation inside eligible native fill; out-of-grid FlatCache status 1 falls back for that slice, while other failures throw. Java still compiles the graph, copies output, traverses cells, prepares caches/aquifer materials and installs chunk results. This is not complete world-generation ownership or an issue closure.
- Current developer owners now distinguish the router driver from the historical fill driver's `858476969` checkout requirements. Bundled settings include Primordial Caves; the current fixture is not described as vanilla-only. Four router Java methods, the three existing fill methods and seven added Rust router tests were inspected, not rerun. Static reconstruction matched ten modified production Java/Rust files from twenty-four exact rewrites. Selected rejection and fallback cases do not exhaust all ownership, size, lifecycle, failure, concurrency or memory gates.
- Preserved the implementation author's measurements and table values while explicitly labeling eight-fill samples. Both benchmark modes retain the native block fill; checksums and nonzero route counters are narrower than detailed parity assertions. Process-wide affinity and caller-thread CPU timing do not establish separate worker pinning or total worldgen CPU cost. Overworld/Amplified author-reported improvements remain workload-specific; Nether's cross-JVM result remains inconclusive. No full-generation or whole-game performance claim follows.
- This four-document checkpoint appends one concise issue-linked monthly entry while preserving historical bytes and links. Fresh full documentation checks, strict build and independent source/render/anchor review apply before guarded promotion and terminal deployment verification. Earlier temporary validation artifacts are not treated as retained evidence in the recovered workspace. No runtime suite, mutation test, benchmark, live world or fresh public-browser check was performed in this maintenance pass.

## Batch 342: Worldgen stages and retained-rendering reconciliation

- Reconciled the six worldgen slices introduced by `365de0289bfb03fa828889de1bcab315b7a19c6c` and preserved through `54611cfc25dbdf60ae4b11dc17557d2bec77469d`. Thirteen existing developer owners distinguish native template/ordinary NOISE routes, preliminary/fluid programs, shared chunk storage, BIOMES and CARVERS integration from remaining Java conditions, lifecycle, neighbour preparation and stage orchestration. The Java SURFACE X/Z loop and extensions remain explicit; native column scans do not imply the complete surface stage migrated. [The #775 review](https://github.com/HungLo2020/MattMC/issues/775#issuecomment-6010282862) remains open.
- Static reconstruction matched 41 existing production files (25 Java, 16 Rust) from 144 exact rewrites. This checks the edit boundary, not runtime parity. Current `javacells` timing leaves templates enabled and is expected from source to fail its route assertion; parity-only is separate. Preliminary-surface timing changes template/fluid eligibility too. BIOMES and CARVERS fixtures, unchanged native helpers in Java comparison routes, and Java-only biome-oracle hooks retain their documented acceptance limits. Historical measurements, commands and failures are preserved with current/historical labels rather than treated as fresh end-to-end evidence.
- Four rendering owners now distinguish retained shader terrain, ABI 69/70 compact records, Rust camera-graph selection and the shadow-query prefilter from remaining Java semantic extraction/build scheduling and ordinary fallback routes. The small present request is copied at pipeline acceptance; the whole-frame request remains borrowed until join. Context-registry joins and standalone query selection have separate contracts. Current capacity limits, unresolved scene phases, the conditional Citadel geometry issue and broad Goal 5 acceptance remain explicit. [Rendering](https://github.com/HungLo2020/MattMC/issues/747#issuecomment-6010252617), [performance](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-6010243435), [visibility](https://github.com/HungLo2020/MattMC/issues/520#issuecomment-6010247010) and [DH integration](https://github.com/HungLo2020/MattMC/issues/745#issuecomment-6010229884) progress comments do not close those issues.
- Author-recorded moving results retain both the section-graph regression from 187.8 to 175.7 FPS and later shadow-query improvement from 175.7 to 195.7 FPS result, with changing admitted work and separate image/test records. Capture success does not prove asynchronous lifetime/overlap correctness. Atomic library staging is not established closure of the independent SIGSEGV; long-run resources, transitions, flicker and broad parity remain open. Six focused Python tooling tests passed in the coordinated tracker review; no Java/Rust suite, benchmark, live world or unbundled capture artifact was independently rerun/verified by this documentation pass.
- This source-driven checkpoint changes existing owners and appends two concise issue-linked monthly entries, preserving history and navigation. Independent source reviews, fresh full documentation validation, strict build, rendered links/anchors and exact docs-only tree checks apply before guarded promotion and terminal deployment verification. This is maintenance of affected documentation, not a restart of broad content expansion or implementation of research proposals.

## Batch 343: Native light and worldgen conditions

- Reconciled seven existing developer owners with source `5c02fd8215f4c1dde624dbe3d21a476d38b16708`. Native lighting owns propagation queue processing and sky seeding while Java retains section/layer storage, other engine work and publishing. Section layer and block snapshots use separate callback modes. Recoverable unsupported/callback statuses restore propagation queues before Java replay; invalid ABI, downcall/result and seeding failures do not share that replay path. No blanket rollback or universal exception-equivalence claim is made. [The #776 review](https://github.com/HungLo2020/MattMC/issues/776#issuecomment-6025144528) remains open.
- Light acceptance remains scoped: saved FULL terrain supplies block states reconstructed into ProtoChunk fixtures and relit, not a saved-light/region lifecycle roundtrip. Notification membership is checked, not ordering. Eight Java and eleven Rust lighting passes, thirteen mutation outcomes and workload timings are author records; four Rust tests are newly added propagation tests. A successful driver exit does not establish its performance gate because per-case `passes` values are recorded separately. Warmup timeout, native-handle cleanup, concurrency/reentrancy and full loaded-chunk lifecycle remain distinct limits. No new player-facing light rule was identified, so the player guide was not rewritten.
- Eligible native NOISE traversal now prepares its own bound aquifer materials and copies resulting cache memos back; compatibility paths retain Java preparation. SURFACE answers eligible gradients/noise/minimum-level requests natively and reuses cached programs, with possible duplicate concurrent first compilation. Java keeps the outer column loop, temperature/custom requests and extensions. [The #775 review](https://github.com/HungLo2020/MattMC/issues/775#issuecomment-6025163587) preserves the current javacells assertion issue, composite preliminary-level timing and native-BIOMES oracle-hook gap. Native aquifer ownership has no established speedup; surface timing combines condition execution and caching, and the reported Nether upper interval 0.953 misses the 0.95 gate.
- The two new worldgen drivers share the same fourteen-file/seventy-one-rewrite reconstruction; matching both is not twenty-eight files or 142 distinct rewrites. The broader historical-driver audit was not completed during this pass and is not claimed as verified. Runtime suites, mutation tests, benchmarks, live worlds and unbundled measurement artifacts were not rerun or independently validated by this documentation review. Incoming historical tables and previously reported outcomes retain their dates and scope.
- This source-driven nine-document checkpoint appends one concise issue-linked monthly entry without removing history. Independent source review, fresh full documentation checks, strict build, rendered links/anchors and exact docs-only tree validation apply before nonforced promotion and terminal deployment verification. No gameplay/code changes, issue closure, broad wiki expansion or research implementation is included.

## Batch 344: Queued rendering and chunk-section storage

- Reconciled the twenty-commit source series through `121ad13c84e45555c34814d54a8199194b37f39c`. Ordinary queued frames now decode on the caller before Java releases the request arena; FIFO jobs acquire, execute and present, ordered with copied mesh updates and atlas ticks. The queue-disabled single in-flight path still borrows request memory until join. Java backpressure is distinct from the unbounded native channel, and reported job errors do not cancel all later work. Current owners preserve both contracts and the separate synchronous capture route.
- Rendering guidance now qualifies release GAL validation, command-list-local host-write hoisting, idle-only staging bounds, parked fullscreen plans, source-role/voxel reuse, 64-byte source vertices and valid color/mipmap history. Java raw biome color memos remain semantic producers. The current author speed summary is separated from earlier profiles and from its own final-600-frame slice; it does not establish full-run parity, long-run stability or Goal 5 completion. Source tests and author captures remain separate from independently rerun evidence.
- Added the storage subsystem to the architecture map and reconciled the incoming chunk-section owner. Rust emits eligible sections as NBT tape while Java retains live snapshots, vocabulary, the remaining root and scheduling; pending tags are lazy. Existing POI native tape ownership is preserved rather than incorrectly classified as generic Java serialization. Compatibility declines, throwing failures, exact-tape fixtures and region tag comparisons retain their different scopes. The published author's subsystem timing is not a whole-server or concurrent-save result.
- Linked the verified [renderer ownership](https://github.com/HungLo2020/MattMC/issues/747#issuecomment-6027569797), [performance](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-6027581285) and [chunk-storage](https://github.com/HungLo2020/MattMC/issues/774#issuecomment-6027620399) reviews. Static reconstruction matched eight existing Java files from nineteen declared rewrites, not runtime acceptance. The I/O fixture does not force its pending branch or reopen storage; direct region timing excludes live snapshots, worker scheduling and final flush. [#818](https://github.com/HungLo2020/MattMC/issues/818) tracks malformed-input/same-thread scratch recovery and exceptional save-future completion as source-qualified findings, without runtime reproduction or observed world damage. No fixes or issue closures are implied.
- This checkpoint changes affected developer owners and appends concise monthly outcomes without deleting historical entries. Fresh documentation checks, strict build, independent source/render/anchor review and exact documentation-only tree validation apply before nonforced wiki-to-master promotion and terminal deployment verification. No gameplay code, code PR merge, runtime suite, benchmark or fresh public-browser test is claimed; this is source-driven maintenance rather than broad content expansion.

## Batch 345: Terrain selection, chunk loading and biome searches

- Reconciled the four-commit source series through `313e7a8a82a34dc915c4924a78da77c720af2f7e`. Camera-pass entity culling uses the current graph visits, while shadow selection retains its separate admission. Rust now selects ordinary compact terrain/caster records from mirrored section meshes; Java still owns publication, graph inputs, build scheduling and compatibility producers. The direct DH particle-ordering change is distinct from the earlier selected-shader alpha-cutout review. Current ownership and author measurements are separated from historical checkpoints and do not close broad rendering acceptance.
- Updated the storage owner and architecture for eligible current-version section loading. Java retains version/upgrade policy, container unpacking, section/light construction, remaining root parsing and installation. Tag-only/non-current/noncanonical inputs keep compatibility paths; native errors are not universal fallback. Load timing includes `parseLoaded`, unlike initial corpus preparation, and direct region measurements exclude scheduler and durability work. Edited-input route checks are aggregate, the older-version fixture uses an identity callback, and section fingerprints compare selected state rather than every hidden field.
- Updated the incoming biome-search owner for identity-cached compiled sampling, ordered native search and retained Java acceptance/random-result work. Concurrent first compilation, horizontal-only debug guards, eligibility limits and exceptional statuses are explicit. Closest-search parity uses the protected bridge and nonempty acceptance; it does not establish public predicate/Y preparation or empty closest-set coverage. The implementation author's workload timing and mutation records remain scoped and were not rerun by this maintenance pass.
- Static reconstruction matched eight existing Java files from twenty-eight declared storage-driver rewrites and three files from nine biome-driver rewrites. These checks establish declared edit correspondence, not native/runtime acceptance. The source-qualified save-recovery paths in [#818](https://github.com/HungLo2020/MattMC/issues/818) remain unchanged; new throwing-task load handling does not fix the save supplier or rejected-compaction cleanup. No new recovery test, runtime reproduction, observed world damage or repaired behavior is implied.
- Linked the verified [visibility](https://github.com/HungLo2020/MattMC/issues/520#issuecomment-6029873623), [performance](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-6029874156), [DH composition](https://github.com/HungLo2020/MattMC/issues/745#issuecomment-6029874814), [loading](https://github.com/HungLo2020/MattMC/issues/774#issuecomment-6029878781) and [biome-search](https://github.com/HungLo2020/MattMC/issues/775#issuecomment-6029879854) checkpoints. All issues remain open. Locate timing uses the protected test accessor, ring timing includes structure-state construction, and nonzero route counters/process affinity are weaker than per-operation coverage or individual worker pinning.
- This source-driven checkpoint preserves historical entries, headings and measurements while updating affected owners and concise monthly outcomes. Full documentation checks, strict build, independent exact-source/render/link review and documentation-only tree validation apply before nonforced promotion and terminal deployment verification. No gameplay code, code PR merge, Java/Rust suite, benchmark, live world or fresh public-browser check is claimed.

## Batch 346: Bounded chunk-save recovery repair

- Integrated source `8db0fd82c52acd01cd6b787e5775d64fca8c3d25` and reconciled the storage owner with [#818](https://github.com/HungLo2020/MattMC/issues/818). Modeled rejected compaction now clears touched lookup slots before returning, and tape-writer `IOException`/`RuntimeException` is carried to `runStore` for exceptional completion without writing. The caller also falls back for native encoder `RuntimeException`. This does not catch every `Error` or repair the existing `write()`/throwing-supplier future path.
- Qualified the three new regression definitions: the Rust test checks same-label lookup reuse, the Java rejected-input test follows failure with tape comparisons for six ordinary chunks on the same thread, and the store test injects an already-constructed failure record. The latter does not trigger an actual tape-writer exception or exercise a supplier that throws before returning. The commit author's failing-before claims were not independently rerun; earlier October 6 parity/timing tables retain their historical scope.
- [The verified #818 progress comment](https://github.com/HungLo2020/MattMC/issues/818#issuecomment-6034310140) credits the bounded repairs while keeping the issue open. Source/test inspection does not establish the broader save-accounting, supplier-failure, coalescing or lifecycle requirements.
- This three-document checkpoint appends one concise issue-linked monthly entry and preserves all earlier history. Full documentation checks, strict build, independent source/render/link review and exact documentation-only tree validation apply before nonforced publication and terminal deployment. No gameplay code, code PR merge, runtime/native test, benchmark, live world or fresh public-browser verification is claimed; broader #818 completion remains separate from the landed repair.

## Batch 347: Renderer reuse, ordering and benchmark scope

- Reconciled thirty-seven source commits through `20e157cab7962140b30b83f40374cdeb1e6a8b19`. GUI source identities, decoded/prepared caches, foil transforms and content residency reduce repeated work within existing Java/native ownership. Residency aging counts mesh transactions rather than every displayed frame; caller-store generation/address lifetime and bridge recreation retain separate acceptance requirements. Modeled tests do not certify every raster-state transition, cleanup or idle lifecycle.
- Current opaque terrain preserves the graph's visit order, not a strict Euclidean distance sort. Vanilla block entities come from visited built sections plus built global lists, while shader extraction retains its scan for shadow needs. DH ordered translucency, packed generic boxes and role-specific release preserve their narrower source/lifetime boundaries. Three-slot Java buffers rely on the existing consume/copy/queue lifetime; source correspondence and numerical tests are not repeated live overlap or resource-retirement proof.
- Latest author comparisons distinguish short 1,800-frame runs, equally warmed 1,800-frame samples and long 30,000-frame runs. Earlier unequal-warmup parity inference is superseded without deleting history. Shader/DH FPS, percentile and subsystem timings retain their actual workloads, so no broad Goal 5, visual, tail-latency or long-run acceptance follows. This maintenance pass did not rerun Java/Rust suites, captures or benchmarks.
- Documented the new Frozen provisioner from its active implementation. It tracks the latest branch in a separate full clone; `--check` still fetches, including with `--dry-run`, while plain dry-run inspects cached refs and suppresses changing commands. Default build preparation is separate from test execution, and optional run-input copies preserve existing destinations. The tool was inspected, not used to create/update a baseline or run its build.
- Linked verified [GUI](https://github.com/HungLo2020/MattMC/issues/772#issuecomment-6043636919), [selection](https://github.com/HungLo2020/MattMC/issues/520#issuecomment-6043638924), [DH](https://github.com/HungLo2020/MattMC/issues/745#issuecomment-6043640406) and [performance](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-6043642312) checkpoints, with all issues remaining open. The concise monthly additions preserve earlier entries. Full documentation checks, strict build, independent source/render/reference review and documentation-only tree validation apply before nonforced promotion and terminal deployment; no gameplay code, code PR merge or fresh public-browser verification is included.

## Batch 348: Shared registry and current rendering boundaries

- Recovered execution and reconciled the previously held game-model corrections against implemented source through `4740f8fabffd878286850083e2d86ff733c9121e`. The source inventory is 1,235 blocks and 1,897 items, distinguishing direct fields, aliases and helper registrations. The current registry reserves `0xffff`, allowing at most 65,535 states; the dated proposal's earlier 65,536 wording remains identifiable as historical intent. Existing TaCZ native helpers are distinguished from the proposed general gameplay model. No entity/component architecture proposal is silently treated as implemented.
- Rust now installs a block/property/state snapshot exported lazily from Java and derives the migrated consumer views, including format-2 meshing facts. Java still defines content and owns relevant world, callback, publication and compatibility policy. Items, entity types, general components and full shape registries remain future work. Historical 65-Java/48-Rust evidence does not include the final meshing fixture; its two-mode record comparison and startup/hot-path measurement limits are explicit. No runtime suite or benchmark was rerun in this documentation pass.
- Current rendering guidance covers native section bookkeeping and assembly, ABI 71 hierarchy composition, shared GUI decoded buffers, lifecycle-correlated DH receipts and deferred resource retirement. The late source commits supersede earlier Java water/index assembly descriptions while retaining them as historical evidence where needed. Java still drives meshing workers, semantic animation/input and publication. Delayed rig release is not a completion fence, native publication is not end-to-end zero-copy, and the lifecycle gate's specific log/audit conditions do not establish universal visual or lifetime acceptance.
- The verified [registry](https://github.com/HungLo2020/MattMC/issues/771#issuecomment-6049215231), [graph](https://github.com/HungLo2020/MattMC/issues/520#issuecomment-6049216234), [GUI sharing](https://github.com/HungLo2020/MattMC/issues/772#issuecomment-6049217250), [rendering](https://github.com/HungLo2020/MattMC/issues/747#issuecomment-6049236504), [DH](https://github.com/HungLo2020/MattMC/issues/745#issuecomment-6049237485) and [performance](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-6049238541) checkpoints remain open. [#803](https://github.com/HungLo2020/MattMC/issues/803) is not repaired by vanilla ModelPart rigs. [#819](https://github.com/HungLo2020/MattMC/issues/819) records the source-predicted missing remap from raw orb boundaries to expanded rig meshes; no observed crash, visual defect or implemented fix is claimed.
- Author-reported scoped captures and test totals are separated from inspected definitions, unretained dual-path comparisons and differing warm-up/session/profile windows. The earlier 545-to-676 FPS attribution was withdrawn as noise; no broad Goal 5 completion follows from later mode results. Required full documentation checks, strict build, independent exact-source/render/reference review and documentation-only tree validation apply before nonforced publication and terminal deployment. Monthly entries append without deleting history; no source code, code PR merge, live game or fresh public-browser verification is authored or performed here.

## Batch 349: Native DH ownership and staged terrain transport

- Integrated source `f13239e10d0f66d244c4311c091d0d60819fb391` and reconciled affected rendering owners. Ordinary terrain assembly keeps staged vertices in Rust by asset key and generation; Java retains counts and publication/acknowledgment duties, while diagnostic paths can request vertex copies. A staged upload copies into frontend storage and can retry before acknowledgment. This is reduced transport, not end-to-end zero-copy or complete native meshing ownership.
- Rust now owns the DH semantic column ledger, copied packed payloads, publication selection/difference, frame admission and route receipts. Java retains traversal, world/data/scheduler ownership, render inputs and acknowledgment; material-provenance diagnostics retain the packed Java route. Ordinary visibility uses one batched call, while exact-atlas admission remains per column. Column/payload budgets are soft, and the publication slice can admit a first oversized column.
- ABI 72 retains DH generic groups in native storage and sends group instances per frame; expansion still creates camera-relative frame boxes. Generation/resend and release contracts are distinct from universal lifecycle acceptance. Earlier ABI 71 rigs remain applicable and do not resolve the source-qualified Citadel geometry or raw orb-boundary limitations in [#803](https://github.com/HungLo2020/MattMC/issues/803) and [#819](https://github.com/HungLo2020/MattMC/issues/819).
- Crosslinked source-qualified [#820](https://github.com/HungLo2020/MattMC/issues/820), the first allocated generic-group ID zero rejected by active retained admission, and [#821](https://github.com/HungLo2020/MattMC/issues/821), fully omitted translucent staging left without a prior asset cleanup owner. Both are conditional active-path findings; no client failure, measured leak, exhausted budget or implemented repair is claimed.
- Linked the verified [terrain staging](https://github.com/HungLo2020/MattMC/issues/747#issuecomment-6051134159), [DH ledger](https://github.com/HungLo2020/MattMC/issues/777#issuecomment-6051203728), [retained-group](https://github.com/HungLo2020/MattMC/issues/745#issuecomment-6051205144) and [measurement](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-6051135359) progress comments. These remain open acceptance threads; creating the two source-qualified issues is not an implemented repair.
- Preserved earlier performance history while separating the new author-recorded same-session mode comparisons, unequal sampling windows and vanilla-plus-DH gap. New source fixtures and compressed-log lifecycle scanning were inspected; no Java/Rust suite, capture, benchmark or unbundled artifact was independently rerun or examined. Full documentation checks, strict build, independent source/render/reference review and documentation-only tree validation apply before nonforced publication and terminal deployment. Monthly additions preserve all previous entries; no source code or code PR merge is authored here.

## Batch 350: Native publication, shared DH pages and validation scope

- Reconciled source through `697b0a3c6200151830a565c73aaee88d323eb484`. Rust owns terrain section/layer publication rows, collision checks and dirty graph-row synchronization; Java still drives registration/removal/reload transactions and world/worker input. Rows change at registration rather than upload acknowledgement. The reload commit now drops acknowledged staged layers' Java CPU payloads; this is separate from [#821](https://github.com/HungLo2020/MattMC/issues/821), the fully omitted native staging path with no prior asset owner.
- DH candidate collection batches generation/lifecycle checks with publication and visibility work. Lease-only container retirement and closing late worker results repair bounded ownership cases; Java retains the quadtree and scheduling. Shared device pages and page-keyed geometry bindings are implemented, with range reuse and page retirement tied to their specific transaction/submission rules. They prepare a later DH multi-draw path rather than establishing that it exists already.
- Reconciled the new validation driver's prerequisites, selected test scope, serial-retry reporting, skipped-step semantics, aggregate limits and broad matching-client cleanup. The driver was inspected rather than used to launch clients. The latest ABAB measurements are separated from earlier single-run and mixed-duration tables; author-recorded two-repeat results and noisy controls do not establish universal rendering/performance acceptance.
- Linked verified [terrain publication](https://github.com/HungLo2020/MattMC/issues/747#issuecomment-6053377956), [DH lifecycle](https://github.com/HungLo2020/MattMC/issues/777#issuecomment-6053379365), [shared pages](https://github.com/HungLo2020/MattMC/issues/745#issuecomment-6053380774) and [validation/performance](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-6053382027) comments. The coordinated tracker review independently passed seven isolated Python helper tests and reproduced [#822](https://github.com/HungLo2020/MattMC/issues/822) with fully mocked orchestration: a background exception omitted requested Rust/wiki results while the remaining summary passed. No client, external build or cleanup command ran in that reproduction. Existing #818–821 remain open; these comments and the new ticket do not implement repairs.
- Preserved historical headings, measurements and monthly entries. Required documentation check, strict build, independent source/render/reference review and exact documentation-only tree validation apply before guarded nonforced publication and terminal deployment. No game code, code PR merge, runtime suite, benchmark, live-world or fresh public-browser verification is authored or performed by this documentation pass.

## Batch 351: Acceptance repair and evidence-retention boundaries

- Reconciled source `97e3092269ed29854c8175a480a819fb1896c311`. Validation now records requested steps, catches ordinary background exceptions as failed results and rejects missing results. The coordinated tracker review independently passed eleven supplied Python tests and nine additional fully mocked main-flow cases, including errors, interruptions and explicit skips. These tooling checks did not launch clients, Java/Rust builds, captures or benchmarks; they establish the bounded repair separately from live rendering acceptance.
- Updated practical validation and feature-parity guidance for stricter receipts, expected frames/scenarios, paired average-FPS/p99 checks and scoped process cleanup. Source-defined checks do not certify every effective input, platform or workload. Feature fixtures are a selected subset; passing their comparisons is not full rendering coverage. The complete orchestration and actual client transitions were not rerun by the documentation pass.
- Qualified artifact-retention behavior from the active implementation, including invocation pins, disposable-workspace treatment, process-visibility requirements and permanent deletion of retired evidence. Bulk cleanup sizes and protected-input claims are attributed to their author-recorded receipts; this review did not inspect or delete the user's original artifact tree. Retained historical source citations remain available even where raw capture receipts are not.
- Preserved the source-owned migration roadmap and distinguished its planned ownership stages from implemented runtime systems. The latest performance table records candidate `d7ee0335d` from a separate checkout, with missing raw benchmark receipts; it is not a fresh measurement of this tooling-only master commit. Earlier measured windows remain historical and no new runtime speed or migration-completion claim is made.
- Linked the verified [#822 repair evidence](https://github.com/HungLo2020/MattMC/issues/822#issuecomment-6065782881) and completed closure, [performance evidence](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-6065805242), and [rendering acceptance/roadmap checkpoint](https://github.com/HungLo2020/MattMC/issues/747#issuecomment-6065826232). Across the coordinated review, twenty-one supplied Python tests passed (eleven validation, six feature and four retention), plus nine additional mocked validation cases. The validation fixtures required a dedicated temporary root after a default-root permission failure; no production-retention guarantee follows. Separate malformed-feature-evidence checks fail with an exception and can omit cleanup/summary, while pathological helper-retention inputs establish an operator/revalidation caveat rather than observed production data loss. Existing #818–821 were not repaired or closed by this tooling commit.
- Required documentation check, strict build, independent source/render/reference review and exact documentation-only tree validation apply before guarded nonforced publication and terminal deployment. Monthly additions preserve existing bytes. No game code, code PR merge, artifact cleanup or new runtime experiment is performed here.

## Batch 352: Native content ownership and restored maintenance validation

- Recovered the execution environment and reconciled the previously held source review through `d0141162d81eee184fa99f0b7a9411d401c306b4`. Rust now owns ordered state construction, 134 property declarations, five built-in fluids and their 37 intrinsic states, registered block names/layouts/defaults, 181 intrinsic physical profiles and 205 shared intrinsic rule sets. The 1,235 blocks retain 31,809 block states; the combined block/fluid graph count is 31,846. These counts describe different scopes and do not replace one another.
- Current packet format 7 carries remaining Java-supplied facts rather than the registered names/layouts/defaults or native property schemas. Rust now derives emitted light, semantic map-color IDs and canonical fluid associations. Java retains compatibility state objects/codecs, behavior and world-dependent callbacks, shapes/predicates, blocked light, light-face truth tables, remaining flags and offset inputs. Registered native definitions and shared graphs/profiles remain distinct from synthetic/unregistered compatibility construction. Historical Phase 1 measurements and proposal-only APIs are preserved with current-owner pointers.
- Qualified the content observer and author-recorded test/bootstrap/capture results. Semantic digests and codec checks do not establish all gameplay/save lifecycles; bootstrap timing has no pass threshold and JVM main-thread allocation excludes native and whole-process allocation. Latest combined intrinsic-state runtime results remain author reports. All sixteen reported FPS receipts were clean, but the full performance gate failed: vanilla/DH average FPS and p99 remain below target, while both shader modes pass that run. No isolated intrinsic-state speedup or full migration acceptance is claimed.
- Updated current rendering/profiling and capture-storage guidance while preserving historical tables. Parent-marker cleanup and missing-source retention repairs are distinguished from broad storage-safety guarantees; author cleanup quantities and rejected mesh-cache measurements were not independently reproduced. The rejected cache experiment is not a shipped optimization. No production artifact cleanup, runtime/client suite, benchmark or public-browser verification is performed by this documentation pass.
- Linked [#823](https://github.com/HungLo2020/MattMC/issues/823), the two-phase retention gap: an individual missing-source fixture can be retained before its old parent invocation is deleted. Two exact-source synthetic controls reproduced that sequence and confirmed the explicit root `.keep` control, without deleting real user artifacts or observing production loss. The fixture-level source-existence repair is not a guarantee against later parent retirement.
- Crosslinked the verified [native content ownership review](https://github.com/HungLo2020/MattMC/issues/771#issuecomment-6070919773) and [performance/evidence review](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-6070924916), updated against the late intrinsic-state commit. Both remain open; the static observer samples bounded world-independent contexts and does not establish broad gameplay, resource loading, save lifecycle or application migration acceptance.
- Monthly additions preserve all existing history. Required documentation checks, strict build, independent source/render/reference review and exact documentation-only tree validation apply before nonforced publication and terminal deployment; no gameplay code or code PR merge is authored here.

## Batch 353: Native sound/family definitions and retention repair

- Reconciled source through `87046367cdf0a4a427f10066a9010dd6d39fd422`. Native content now owns sound-event/profile/instrument metadata, block sound/offset configuration and typed block-set/wood/family bindings. This metadata slice does not migrate Java sound resolution/scheduling or pack-resource lifecycle; existing native decoding/OpenAL resources remain a separate owner. Family configuration does not migrate entity queries, block mutation or scheduling. Format 8 removes offset metadata from the remaining Java fact export; compatibility objects and contextual behavior remain separate.
- Updated current content owners and observer scope while preserving historical results. Source/test inspection and author-recorded semantic digests do not establish every world context, resource reload, save lifecycle or native application requirement. New sound/family runtime suites and captures were not rerun by this documentation pass.
- Qualified the latest author-recorded family workflow: reported tests, lifecycle cases and reviewed coast comparisons passed, and all sixteen FPS receipts were clean, but the overall performance gate failed. Vanilla and vanilla+DH median average FPS and p99 remain below Frozen; the larger DH gap needs investigation without attributing it to this isolated ownership change. Older sound, intrinsic and physical runs keep their own evidence windows.
- Reconciled the parent-invocation retention repair and Frozen launch workflow against their active code. The coordinated tracker owns the focused temporary-fixture acceptance evidence; this documentation task did not launch a client or clean production artifacts. RunDev's selected-checkout behavior is separate from source-integrity and benchmark-equivalence verification.
- [#823 was closed after focused verification](https://github.com/HungLo2020/MattMC/issues/823#issuecomment-6073221178): eight independently executed temporary-fixture cases covered the actual finish/retirement sequence, repeated passes, restoration, explicit pins, baselines and protection controls. Process discovery was mocked; no real client, production artifact tree, game build or runtime suite was involved. The repair preserves a missing-source workspace against parent deletion but does not verify source-byte identity or every live-filesystem condition.
- Linked the verified [content/sound/family ownership checkpoint](https://github.com/HungLo2020/MattMC/issues/771#issuecomment-6073238616) and [performance/launcher evidence checkpoint](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-6073232463). These goals remain open; native metadata does not complete audio-resource/playback or general gameplay ownership.
- Monthly entries retain their exact prior prefix. Required full documentation checks, strict build, independent source/render/reference review and exact documentation-only tree validation apply before guarded nonforced promotion and terminal deployment; no game code or code PR merge is authored here.

## Batch 354: Recovered map, frame and section-ownership reconciliation

- Reconciled source through `a908f78cd909200f5f4f4424b124072cef0a17f6` after the required execution environment recovered. The held reviews covered map palette/image processing and registered state policy, native DH frame references, shared section-color fields, immutable rebuild snapshots, canonical live palette storage and native generation-stage capture/adoption. Earlier source-only wiki fast-forwards authored no corrections or master commits; this batch supplies the reviewed documentation reconciliation.
- Distinguished current contracts: registry remaining-fact packet 9, block-definition schema 5, content observer v10 with ten digests, whole-frame ABI 74 and compact meshing header 4. Preserved historical sound/family v7/v8 results and older acceptance receipts. Native palette arithmetic is separate from Java world-dependent shade selection; state eligibility does not migrate world callbacks, scheduling or block-entity storage.
- Reconciled retained DH ownership and map rendering boundaries. The three-entry reference ring does not bound all decoded queued owners; ledger/Arc fixtures do not execute the actual render queue. Map GUI staging retirement does not establish retirement of every world PNG cache. Source fixtures and author-recorded image results were inspected, not independently rerun or viewed. Linked the verified [content policy](https://github.com/HungLo2020/MattMC/issues/771#issuecomment-6077636801), [DH handoff](https://github.com/HungLo2020/MattMC/issues/777#issuecomment-6077638088), [map rendering](https://github.com/HungLo2020/MattMC/issues/747#issuecomment-6077639437) and [map/DH measurement](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-6077592661) reviews.
- Shared color requests are per resolver and section, excluding each active block's origin tint call and explicit diagnostic resampling. The 128-lease registry bound excludes already decoded owners. Java still evaluates biome resolvers and contextual inputs. Preserved the distinct color-field runtime/hardening windows and hardware labels in the [ownership](https://github.com/HungLo2020/MattMC/issues/747#issuecomment-6080180242) and [evidence](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-6080155637) checkpoints.
- Canonical live palettes, packed words and mutation now have a native owner; immutable snapshots and bulk halo consumers are separate. Zero-width copies deliberately retain their shared single-palette semantics. Compatibility tests do not establish every arbitrary Configuration callback, and snapshot admission may call storage size/bits before excluding custom classes. Save/network and generic enumeration still use temporary compatibility projections. The [live storage review](https://github.com/HungLo2020/MattMC/issues/776#issuecomment-6083290733) and [final-alias evidence](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-6083252633) keep wider migration and performance acceptance open; #818 remains unchanged.
- The verified [generation-stage ownership review](https://github.com/HungLo2020/MattMC/issues/775#issuecomment-6086170999) confirms eligible stages capture canonical inputs and adopt independent outputs within Rust, preserving modified-only installs and compatibility paths. This is native copying/adoption, not a unified live/stage representation or zero-copy contract. Java retains orchestration, section counters, heightmap/mark publication and callbacks; whole-chunk failure atomicity is not established. Latest [performance evidence](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-6086142300) still fails vanilla/DH FPS and p99 floors. Sparse allocation/CPU samples, differing workloads and historical sessions do not establish an isolated migration speedup.
- Monthly additions retain the exact prior bytes. Required documentation checker, strict build, independent source/history/rendered-reference review and exact documentation-only tree validation apply before guarded nonforced promotion and terminal deployment. No gameplay code, code PR merge, Java/Rust runtime suite, client, benchmark, production cleanup or public-browser verification is authored or performed by this documentation pass.

## Batch 355: Native section counters and frame-input reconciliation

- Reconciled source through `642943247003d7d8d756a65180f0872b088c13f0`. Canonical live mutations now update native signed-short section counters under the storage lock; native recount and generation input capture consume these owners directly. This per-section boundary does not establish whole-chunk failure atomicity or migrate Java chunk orchestration, callbacks and lifecycle. Compatibility failure prefixes, alias counts and incremental/recount fluid differences retain their original semantics.
- Reconciled built-in DH cloud preparation and GUI/world/hand item-pose owners. Current ABI 77 follows historical cloud ABI 75 and GUI-pose ABI 76. CPU allocation pins, bounded pose histories and queue lifetimes are distinct; expiry can still reject a retained cloud epoch. Java model/topology/parent animation, custom API behavior and DH world orchestration remain. No GPU ownership or broad application migration is inferred from CPU handoff progress.
- Recorded the bounded column-sidecar cleanup, camera-space debug-axis transform and terrain/readiness changes. Offscreen edits retain rebuild marks while visible edits invalidate readiness; full capture metadata is serialized on stage changes but live DH execution checks continue each frame. Passing source regression definitions do not establish every live camera/world condition.
- Updated the current Goal 5 workload while preserving all prior tables. The author's measured `0d54a098` window passes all median average-FPS floors but fails vanilla p99; large Current vanilla/Frozen DH repeat variance prevents robust or isolated speedup claims. Later reload/memory observers have focused checks and strict held-clock foil evidence, not a repeated full performance matrix. Ground fixed-probe acceptance and broad visual/temporal/resource limits remain open.
- The coordinated review independently ran eight exact-source Python memory-observer tests using synthetic process and temporary-directory fixtures. It did not run Java/Rust suites, clients, captures or benchmarks. All such runtime totals and image/profile outcomes remain attributed to their source authors; no real artifact cleanup was performed.
- Monthly additions preserve existing bytes. Required documentation checker, strict build, independent source/history/rendered-reference review and exact documentation-only tree checks apply before guarded nonforced publication and terminal deployment. No game code or code PR merge is authored here.
- Linked the [current performance and observer review](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-6088997781); its eight Python fixture passes do not establish runtime acceptance.
- Crosslinked the verified [section-counter](https://github.com/HungLo2020/MattMC/issues/776#issuecomment-6089009016), [DH cloud](https://github.com/HungLo2020/MattMC/issues/777#issuecomment-6089017758) and [item-input](https://github.com/HungLo2020/MattMC/issues/772#issuecomment-6089031512) reviews. These are bounded progress comments; no issue closure or new implemented repair is implied. Existing #819/#820 remain unresolved.

## Batch 356: Farming article depth and native light/GUI reconciliation

- Resumed substantive documentation expansion with actual player-information gaps in existing owners, rather than treating registry routes as completed articles. Farmland and Nether Wart now receive current-source review of collection, placement, protection/growth, native properties and exact harvest rules; companion item pages link to the canonical placed-behavior owners. Existing alphabetical block IDs, category organization and public article URLs remain unchanged.
- Added a compact article-quality overview and made it the maintenance index's current starting point. The long checkpoint retains all historical batch records and anchors; the initial October 1 inventory is explicitly historical. The Blocks introduction now accurately says every listed ID has a related guide while retaining the limits of family-link coverage. No comprehensive content or runtime completeness is claimed.
- Reconciled source through `4246f4e7bfc1f3ab7862272ebba5f1f38aa16953`: native live light generations and handoff ownership, incremental GUI residency at ABI 78, and ordinary-gameplay measurement. Current source and author records remain separate from older measured releases and unverified broad lifecycle/performance claims.
- The [performance review](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-6091091526) keeps settled benchmark failure separate from ordinary travel/manual observations. The coordinated review independently ran four Python harness tests; one JDK bytecode-agent fixture was skipped because the complete Java tooling was unavailable. No game client, Java/Rust suite, capture, production cleanup or benchmark was rerun by that review.
- Linked the verified [GUI/DH residency review](https://github.com/HungLo2020/MattMC/issues/772#issuecomment-6091093270), preserving incremental-update and clean-reuse limits. Source tests and committed measurements do not establish broad ordinary-gameplay parity, and no code repair or issue closure is implied by this documentation work.
- Monthly entries preserve the previous bytes. Required source/history/anchor review, full documentation checker and strict build apply before one guarded nonforced documentation promotion and terminal deployment. Gameplay facts are source-reviewed, not in-game tested.
- Simplified the wiki homepage into direct content, world setup and crafting/system routes; removed unrelated generic Minecraft-history prose while preserving all existing index links and heading URLs. The content guide now distinguishes curated topics from the exhaustive directories and no longer implies current missing-ID rows.
- Filled the Smithing Table's explicit Toolsmith job-site omission with current POI/profession evidence and links to the shared employment/restocking owners. This focused addition does not claim a fresh audit of all older recipe/physical-property citations or natural generation.
- Linked the verified [live-light ownership review](https://github.com/HungLo2020/MattMC/issues/776#issuecomment-6091109625), distinguishing recoverable queue replay from throwing ABI/export/install failures and retaining Java orchestration, mutable-array compatibility and scalar terrain-light reads.

## Batch 357: Lever controls, shared power and terrain-light reconciliation

- Expanded the existing Lever owner around current native definitions and active interaction, support, signal, loot, fluid, piston and wind-charge dispatch. Its item page remains a concise acquisition/use route to the canonical placed-behavior guide.
- Added practical source-choice and support-conduction explanations to the shared Redstone guide, preserving component-specific timing and family matrices in their existing owners. The detailed Button and Pressure Plate guides were checked without duplicative rewrites; the Torch addition is limited to verified environmental removal.
- Source evidence remains pinned to `4246f4e7bfc1f3ab7862272ebba5f1f38aa16953`. Physical signal directions are explained from the receiver's query semantics; no circuit simulation, gameplay test or universal machine compatibility is claimed. All previous headings, catalog rows, monthly entries and batch history are preserved.
- Required source/rendered-reference review, documentation checker and strict build precede the guarded documentation-only promotion and exact-head terminal deployment.
- Preserved the incoming native terrain-light preparation and reconciled current ownership/evidence through `ee34f2ad99921848d8fc5d63da93eb6c583786c4`. The verified [terrain-light review](https://github.com/HungLo2020/MattMC/issues/776#issuecomment-6091341618) distinguishes bulk preparation, retained views, Java callbacks and scalar diagnostics; [current performance evidence](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-6091342527) keeps settled p99 failures and limited ordinary travel separate. No later-source runtime suite was rerun by this review; the preceding four-pass/one-skip Python result belongs only to its earlier source pin.
- Integrated the later test/tooling follow-up without treating changed test scheduling, profiles, fixture coverage or author timing records as a new gameplay/performance pass. The final documentation build uses the current checker, and current commands distinguish ordinary Java tests from tagged parity workloads.

## Batch 358: Grindstone decisions and enchanted-book recycling

- Expanded the existing Grindstone owner with practical curse-only, fully repaired, no-output and two-book cases; normal result-taking consumption; private-menu automation and comparator limits; native blocked piston movement; and the Weaponsmith job site. Shared repair, curse, employment and restocking owners retain their detailed explanations.
- Added Enchanted Book application/recycling guidance: a successful Anvil operation consumes the whole donor book, including effects not transferred; a Grindstone removes non-curses together and returns a plain Book only when no curses remain. Companion Grindstone and Book pages link to the canonical decisions instead of duplicating formulas.
- Preserved all prior prose, headings and public routes. Source evidence is pinned to `4246f4e7bfc1f3ab7862272ebba5f1f38aa16953`; relevant implementations remain unchanged through the integrated later terrain-light/test-tooling source. Independent review narrowed Creative consumption to normal result-taking because the generic Creative clone action is a separate path.
- Required source/hash/history/rendered-reference review, documentation checker and strict build apply before guarded documentation-only promotion and exact-head terminal deployment. No inventory interaction, crafting, repair, village or gameplay test was executed by this documentation batch.

## Batch 359: Soil acquisition and replenishment

- Filled acquisition gaps in the existing Soil, Sand and Gravel owner and the Podzol/Rooted Dirt item routes. The source-grounded additions distinguish conditional natural generation, large-Spruce ground decoration, planted-Azalea ground conversion, ordinary versus Silk Touch recovery and randomly selected Wandering Trader offers.
- Kept existing soil spread, loot, tool conversions, Mud production and tree-growth controls in their established owners. Rooted Dirt Bone Meal produces Hanging Roots rather than additional soil; tree conversion and item purchases are separate routes. Natural patches, growth success and trade availability are not guaranteed yields.
- Preserved existing article lines, headings, links, alphabetical catalog rows and historical entries. Current acquisition evidence is pinned to `f86206767dadde696adfed4e04c5ee97cd0d0885`; no generation, growth, trading or mining gameplay test was performed. Full documentation, source/rendered-link, history and exact-tree checks precede guarded publication and terminal deployment.

## Batch 360: Building-material acquisition choices

- Expanded the Terracotta family owner with a practical choice between Clay smelting, the seven-material Badlands band palette and randomly selected Mason offers. The 33-entry level-4 pool selects two entries; no desired color or finish is guaranteed, and colored blocks do not replace uncolored recipe inputs.
- Added concise acquisition routes for Terracotta, Librarian Glass and Wandering Trader Packed/Blue Ice. Existing trading owners retain stock, price and restocking details; established mining, recipe, placement and piston guidance remains in its canonical pages. No duplicate variant articles were created.
- Source evidence is pinned to `f86206767dadde696adfed4e04c5ee97cd0d0885`. Active native surface evaluation, offer dispatch, family recipes and loot were checked. All previous headings and link destinations, the alphabetical registry directory and historical entries are preserved. No terrain search, trading or other gameplay test was performed; source and rendered-reference review, strict documentation build and exact-tree checks precede guarded publication and terminal deployment.

## Batch 361: Bed recovery and respawn setup

- Expanded the existing Bed owner with crafting and recoloring across all 16 colors, one-item stack limits, paired recovery and practical spawn-point selection. Daylight and monster refusals follow selection; occupied, distance and suffocation failures prevent that step.
- Distinguished sleep-entry obstruction from the actual respawn search for floor, collision clearance and world-border validity, including its relaxed danger filter. The shared Death and Respawn owner retains generic fallback and failed-point replacement guidance. White Bed now supplies missing acquisition/recovery answers and precise links to the shared owner.
- Checked all 32 crafting/recoloring recipes, 16 loot definitions and native/Java bed registrations at `f86206767dadde696adfed4e04c5ee97cd0d0885`. Preserved previous headings, links and historical source records. No gameplay tests were run; full strict documentation validation and independent source/history/rendered-reference/exact-tree checks precede guarded promotion and terminal deployment.

## Batch 362: Wheat seed yields and harvest choices

- Expanded the Wheat owner with exact immature/mature drops, Fortune ranges and the binomial seed calculation. Breaking and replanting reserves one seed; MattMC's mature reset retains the planted crop and spends none. Hoe loot uses the actual tool separately for each selected crop, with the established broken-tool and interaction-routing limits retained.
- Clarified Short Grass starter-seed acquisition in Wheat Seeds: Fortune changes the uniformly selected successful count, not the 12.5% entry chance; shears select grass instead. The item page points to the crop owner for yield and replant decisions.
- Traced active loot, tool context, drop gates, planting cost and native Wheat state policy at `f86206767dadde696adfed4e04c5ee97cd0d0885`. Counts assume bundled loot, enabled block drops and no explosion losses. Previous headings, links and historical evidence are preserved; no gameplay sampling was performed. Full strict documentation build and independent source/history/rendered-reference/exact-tree checks precede guarded publication and terminal deployment.
