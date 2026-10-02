# Signs and Hanging Signs

Signs display **four lines on each of two independently editable faces**. Ordinary signs stand on a block or lie against a wall; hanging signs attach beneath a support or extend sideways from one. The twelve standard wood types have both inventory items and all four placed forms. Pewen adds four registered forms, but has important [integration gaps](#pewen-signs-incomplete-integration). [Sign text][text] · [Registration][blocks] · [Nether wood registration][nether-signs] · [Pewen registration][pewen-blocks]

## Ordinary sign variants

All IDs below use the `minecraft:` namespace. One Sign item places either its standing or wall block; there is no separate wall-sign inventory item. Each item stacks to **16**. [Item registration][items] · [Paired block placement and item mapping][paired-item]

| Wood | Inventory / standing ID | Wall ID | Recipe and ordinary drop |
| --- | --- | --- | --- |
| Oak | `oak_sign` | `oak_wall_sign` | [Recipe][recipe-oak-sign] · [Loot][loot-oak-sign] |
| Spruce | `spruce_sign` | `spruce_wall_sign` | [Recipe][recipe-spruce-sign] · [Loot][loot-spruce-sign] |
| Birch | `birch_sign` | `birch_wall_sign` | [Recipe][recipe-birch-sign] · [Loot][loot-birch-sign] |
| Jungle | `jungle_sign` | `jungle_wall_sign` | [Recipe][recipe-jungle-sign] · [Loot][loot-jungle-sign] |
| Acacia | `acacia_sign` | `acacia_wall_sign` | [Recipe][recipe-acacia-sign] · [Loot][loot-acacia-sign] |
| Dark Oak | `dark_oak_sign` | `dark_oak_wall_sign` | [Recipe][recipe-dark-oak-sign] · [Loot][loot-dark-oak-sign] |
| Pale Oak | `pale_oak_sign` | `pale_oak_wall_sign` | [Recipe][recipe-pale-oak-sign] · [Loot][loot-pale-oak-sign] |
| Mangrove | `mangrove_sign` | `mangrove_wall_sign` | [Recipe][recipe-mangrove-sign] · [Loot][loot-mangrove-sign] |
| Cherry | `cherry_sign` | `cherry_wall_sign` | [Recipe][recipe-cherry-sign] · [Loot][loot-cherry-sign] |
| Bamboo | `bamboo_sign` | `bamboo_wall_sign` | [Recipe][recipe-bamboo-sign] · [Loot][loot-bamboo-sign] |
| Crimson | `crimson_sign` | `crimson_wall_sign` | [Recipe][recipe-crimson-sign] · [Loot][loot-crimson-sign] |
| Warped | `warped_sign` | `warped_wall_sign` | [Recipe][recipe-warped-sign] · [Loot][loot-warped-sign] |

At a crafting table, put **six matching planks in two full rows**, with **one Stick centered underneath**, to make **three Signs** of that wood. These recipes name the individual plank item: mixed species and Bamboo Mosaic are not substitutes. The output is the same item whichever of its two placed forms you later choose. The table links each exact recipe; see [Wood construction](WoodConstruction.md#planks-and-materials) for the plank families.

## Hanging sign variants

One Hanging Sign item places either its ceiling or wall-hanging block. These items also stack to **16**. They are separate from ordinary Signs; there is no recipe here that upgrades a completed Sign. [Item registration][items] · [Hanging Sign item][hanging-item]

| Wood | Inventory / ceiling ID | Wall ID | Six stripped inputs | Recipe and ordinary drop |
| --- | --- | --- | --- | --- |
| Oak | `oak_hanging_sign` | `oak_wall_hanging_sign` | Stripped Oak Logs | [Recipe][recipe-oak-hanging-sign] · [Loot][loot-oak-hanging-sign] |
| Spruce | `spruce_hanging_sign` | `spruce_wall_hanging_sign` | Stripped Spruce Logs | [Recipe][recipe-spruce-hanging-sign] · [Loot][loot-spruce-hanging-sign] |
| Birch | `birch_hanging_sign` | `birch_wall_hanging_sign` | Stripped Birch Logs | [Recipe][recipe-birch-hanging-sign] · [Loot][loot-birch-hanging-sign] |
| Jungle | `jungle_hanging_sign` | `jungle_wall_hanging_sign` | Stripped Jungle Logs | [Recipe][recipe-jungle-hanging-sign] · [Loot][loot-jungle-hanging-sign] |
| Acacia | `acacia_hanging_sign` | `acacia_wall_hanging_sign` | Stripped Acacia Logs | [Recipe][recipe-acacia-hanging-sign] · [Loot][loot-acacia-hanging-sign] |
| Dark Oak | `dark_oak_hanging_sign` | `dark_oak_wall_hanging_sign` | Stripped Dark Oak Logs | [Recipe][recipe-dark-oak-hanging-sign] · [Loot][loot-dark-oak-hanging-sign] |
| Pale Oak | `pale_oak_hanging_sign` | `pale_oak_wall_hanging_sign` | Stripped Pale Oak Logs | [Recipe][recipe-pale-oak-hanging-sign] · [Loot][loot-pale-oak-hanging-sign] |
| Mangrove | `mangrove_hanging_sign` | `mangrove_wall_hanging_sign` | Stripped Mangrove Logs | [Recipe][recipe-mangrove-hanging-sign] · [Loot][loot-mangrove-hanging-sign] |
| Cherry | `cherry_hanging_sign` | `cherry_wall_hanging_sign` | Stripped Cherry Logs | [Recipe][recipe-cherry-hanging-sign] · [Loot][loot-cherry-hanging-sign] |
| Bamboo | `bamboo_hanging_sign` | `bamboo_wall_hanging_sign` | Stripped Bamboo Blocks | [Recipe][recipe-bamboo-hanging-sign] · [Loot][loot-bamboo-hanging-sign] |
| Crimson | `crimson_hanging_sign` | `crimson_wall_hanging_sign` | Stripped Crimson Stems | [Recipe][recipe-crimson-hanging-sign] · [Loot][loot-crimson-hanging-sign] |
| Warped | `warped_hanging_sign` | `warped_wall_hanging_sign` | Stripped Warped Stems | [Recipe][recipe-warped-hanging-sign] · [Loot][loot-warped-hanging-sign] |

At a crafting table, put **two Iron Chains in the top-left and top-right slots**, then **six matching stripped inputs in the two rows below**. Each standard recipe makes **six Hanging Signs**. Use the exact input in the table: ordinary stripped logs, stripped Crimson/Warped **stems**, or Stripped Bamboo **Blocks**. All-bark stripped wood/hyphae, unstripped logs, and planks are not listed alternatives. The active axe stripping map supplies these standard stripped forms. [Axe conversions][axe] · [Iron Chain](../items/IronChain.md)

## Placement, orientation, and support

### Standing and wall signs

For a standing sign, use the Sign item on top of a solid block. It has **16 horizontal rotations**, selected from the placing player's view direction. Its support test is the block below's `isSolid()` value; this is not a requirement that every point on the upper face be a full cube. Removing that valid support makes the sign break on the corresponding shape update. [Standing Sign][standing]

For a wall sign, use the same item against a block's side. It has four horizontal facings, with the supporting block directly behind the board. That support also uses `isSolid()`, and losing it breaks the sign on an update from that side. The placement code tries suitable directions from the player's view, so aim at the intended face with clear space for the sign. [Wall Sign][wall] · [Placement selection][paired-item]

Ordinary signs have no entity collision. They are labels, not barriers that stop a player walking through their block space. [Registration][blocks] · [Default collision behavior][block-behaviour]

### Ceiling hanging signs

Use a Hanging Sign beneath a support with a **sturdy center on its underside**. It can also hang below another supported hanging sign. Removing the valid overhead support makes the lower sign break on its upward-neighbor update. [Ceiling Hanging Sign][ceiling]

The placement code chooses the chain arrangement and rotation. Beneath a full lower collision face, normal placement uses four cardinal rotations. A narrow support or secondary-use placement, usually sneaking, selects the attached-chain arrangement with 16 possible rotations. Stacking under another hanging sign has an extra alignment rule that can keep the cardinal arrangement when the signs line up. Aim at the bottom of the existing sign while holding another Hanging Sign to extend it downward without opening the text editor. [Chain arrangement and placement interaction][ceiling]

Ceiling hanging signs have no entity collision. Their support shape still permits the hanging-sign attachment checks. [Registration][blocks] · [Support shape][ceiling]

### Wall hanging signs

A wall hanging sign extends from a side support, with its board **perpendicular to the attaching wall**. Its top bar runs left-to-right across the board. Placement needs support at either end of that bar: a full sturdy block face, or another wall hanging sign from the wall-hanging-sign tag with the same facing axis. This permits aligned horizontal rows. Use an edge or the underside while holding another Hanging Sign to extend the arrangement; clicking a broad text face normally interacts with the text. [Placement and chaining][wall-hanging] · [Supported sign tag][wall-hanging-tag]

**Current support-removal difference:** the item checks those side supports when placing, but the later neighbor-update code calls inherited `canSurvive()`, which returns true. Consequently the checked source does **not** automatically break a wall hanging sign just because its side support is removed. Do not assume the standing/ceiling support-removal behavior also applies here. This is a source finding, not a reproduced construction test. [Update and placement checks][wall-hanging] · [Inherited survival rule][default-survival] · [Item placement gate][hanging-item]

The hanging board itself does not form a full obstacle, but this form overrides collision to keep a **thin top bar**. Its pathfinding override also differs from the other sign forms. [Wall hanging collision][wall-hanging] · [Active collision dispatch][block-behaviour]

## Writing and editing both faces

Normal placement opens the front-face editor. After placing a standard sign, stand on the face you want to change and use it with an empty hand. Walk around to edit the back separately. The selected face comes from the player's position relative to the sign's rotation, rather than a permanent owner assignment. Both sides start empty with black, non-glowing text. [Placement editor][sign-item] · [Interaction][sign] · [Front/back selection][sign-entity] · [Defaults][text]

Each face has four lines. The normal editor limits each line by **rendered width**, not a fixed character count: 90 font pixels for ordinary signs and 60 for hanging signs. A hanging sign therefore fits less text per line. Closing the editor sends its four lines and chosen face to the server, which applies text filtering and checks the permitted editor and wax state. [Editor][sign-editor] · [Ordinary text width][sign-entity] · [Hanging text width][hanging-entity] · [Update packet][sign-packet] · [Server update][server-text]

Any player allowed to build may edit ordinary plain text while the sign is unwaxed and another player is not already editing it. The active editor reservation is temporary; the sign ticker clears it when the editor is unavailable or too far away. This is not a permanent ownership lock. Command-created signs with special text or click actions can follow additional interaction rules, so the instructions here describe normal player-written signs. [Editing conditions and ticker][sign] · [Editor reservation and save data][sign-entity]

## Dye, glow, ink, and wax

Face the side to change and use the appropriate item normally, without secondary-use bypassing the sign interaction. Successful applications consume **one item in Survival**; Creative's infinite-materials behavior preserves it. An already-matching color or glow state does not consume another applicator. [Server interaction dispatch][server-use] · [Sign applications][sign] · [Consumption][consume]

| Item | Effect |
| --- | --- |
| Any Dye | Changes the text color on the selected face; all four lines share that face's color |
| [Glow Ink Sac](../items/GlowInkSac.md) | Makes the selected face's text glow |
| [Ink Sac](../items/InkSac.md) | Removes the selected face's glow, retaining its text and dye color |
| [Honeycomb](../items/Honeycomb.md) | Waxes the whole sign, protecting **both faces** from editing, dyes, glow ink, and ink |

Dye and either ink require at least one nonempty line on the selected face. Honeycomb can also wax a blank sign. Wax is one shared sign setting, while text, color, and glow are saved separately for front and back. [Applicator condition][applicator] · [Dye][dye] · [Glow Ink][glow] · [Ink][ink] · [Honeycomb][wax] · [Sign state][sign-entity]

Glowing text is drawn brightly; it does **not** turn the sign into a block-light source. The sign registrations have no light emission, and glow is a text-rendering setting. [Rendering][render] · [Block registrations][blocks] · [Light defaults][block-behaviour]

**An axe does not remove sign wax in this source.** The axe's use action handles its stripping map and copper scraping/wax removal, with no sign-wax branch. To rewrite a waxed standard sign in ordinary play, break and replace it, then enter its text again. Breaking loses the saved text and decoration; finish both sides before waxing. [Axe actions][axe] · [Sign interaction gate][sign] · [Drops](#mining-drops-and-saved-text)

## Waterlogging

All four sign classes have a waterlogged state. Placement detects source Water at the destination; flowing Water alone is not the placement condition. A Water Bucket can fill an unwaterlogged sign, and an empty Bucket can remove its stored water without collecting the sign itself. Stored water is exposed as a source fluid and receives scheduled water updates. These rules come from the shared waterlogging interface; a bucket interaction may need secondary use to avoid opening the sign editor. [Standing][standing] · [Wall][wall] · [Ceiling][ceiling] · [Wall hanging][wall-hanging] · [Waterlogging and bucket callbacks][waterlogged] · [Sign interaction][sign]

Waterlogging changes the fluid state, not the sign's text, dye, glow, or wax. It does not replace the support rules above. Pewen still has the independent placement integration problem described below. [Sign data][sign-entity] · [Fluid state][sign]

## Mining, drops, and saved text

The **48 standard placed forms** have hardness and blast resistance **1**. They do not require a particular tool or tier to drop an item, so ordinary Survival hand mining can collect them. An unbroken axe has the normal speed bonus through the sign and hanging-sign block tags. See [Mining](../mechanics/Mining.md) for general tool behavior. [Registrations][blocks] · [Nether registrations][nether-signs] · [Axe tag][axe-tag] · [Tool rules][tool] · [Harvest gate][player-harvest] · [Active harvest call][harvest-call]

Each standard form drops **one matching Sign or Hanging Sign item**, including a wall form sharing its counterpart's loot table. Neither Silk Touch nor Fortune is needed or improves these drops. Support loss for standing, wall, or ceiling signs uses the normal drop path; explosion drops have a survives-explosion condition, and item spawning is subject to `doTileDrops`. The variant tables above link all 24 shared loot tables. [Wall loot mapping][wall-loot] · [Drop dispatch][block-drops]

Front/back text, color, glow, and wax persist **while the block remains placed** because its block entity saves them. The ordinary loot tables contain only a plain item entry: they do not copy those settings onto a mined item. Replacing the dropped sign starts with blank, unwaxed text. This distinction is why mining is also a destructive reset of a waxed sign's writing. [Saved sign data][sign-entity] · [Text defaults][text] · [Oak loot example][loot-oak-sign] · [Hanging loot example][loot-oak-hanging-sign]

## Pewen signs: incomplete integration

The following four additional IDs use the `minecraft:` namespace. Their two inventory items stack to 16 and appear in Creative. [Pewen block registration][pewen-blocks] · [Pewen item registration][pewen-items] · [Creative listing][creative]

| Inventory item | Placed IDs |
| --- | --- |
| [Pewen Sign](../items/PewenSign.md) | `pewen_sign`, `pewen_wall_sign` |
| [Pewen Hanging Sign](../items/PewenHangingSign.md) | `pewen_hanging_sign`, `pewen_wall_hanging_sign` |

**Creative availability does not establish working placement.** These blocks instantiate the ordinary Sign or Hanging Sign block entity, but neither valid-block list includes Pewen. The active placement path constructs that entity, whose constructor rejects an invalid block state with an exception. Source review therefore identifies a placement-time failure path; no in-game crash or its wider effects were reproduced. Do not rely on Pewen signs for functioning editable labels in this revision. [Placement dispatch][block-item] · [Level placement][level-placement] · [Chunk entity creation][chunk-placement] · [Ordinary sign factory][sign] · [Hanging factories][ceiling] · [Wall hanging factory][wall-hanging] · [Entity types][entity-type] · [Valid-set check][entity-valid-set] · [Constructor validation][entity-validation]

See the [Pewen family guide](Pewen.md#construction-and-recipes) for wood acquisition and the broader crafting caveats. Its sign-specific distinctions remain:

- The ordinary Sign recipe names six Pewen Planks and one Stick in the normal arrangement and defines **three Pewen Signs**. Its direct string ingredients use the current recipe form; obtaining the planks remains subject to the family guide's separate caveats. [Recipe][recipe-pewen-sign] · [Ingredient codec][ingredient] · [Recipe codec][recipe-codec]
- The Hanging Sign recipe defines six Stripped Pewen Logs and two `minecraft:chain`, yielding **three**, not six. The active registry names the chain `minecraft:iron_chain`; the requested ingredient is unresolved. This is not presented as a working crafting recipe. The axe map also lacks a Pewen stripping conversion. [Recipe][recipe-pewen-hanging-sign] · [Registered chain][iron-chain] · [Axe map][axe]
- All four sign subclasses use **Oak's wood type**. They are absent from the standard sign/hanging-sign block tags and therefore from the usual axe speed grouping; do not infer complete standard-wood behavior from their names. [Registration][pewen-blocks] · [Ordinary tags][standing-tag] · [Wall tags][wall-tag] · [Ceiling tags][ceiling-tag] · [Wall hanging tags][wall-hanging-tag] · [Axe tag][axe-tag]
- Standing Pewen Sign, Pewen Wall Sign, and ceiling Pewen Hanging Sign have plain one-item loot definitions. **Pewen Wall Hanging Sign has no bundled loot table and no shared-loot override**; the missing lookup resolves to empty loot. These are data findings, not verified recovery instructions for blocks affected by the placement issue. [Standing loot][loot-pewen-sign] · [Wall loot][loot-pewen-wall-sign] · [Ceiling loot][loot-pewen-hanging-sign] · [Registration][pewen-blocks] · [Default loot path][block-behaviour] · [Missing lookup][missing-loot]

## A small label setup

For a source-based, **untested** example, craft three Oak Signs from six Oak Planks and a Stick. Place one on a Stone block beside storage, enter a short front label, then walk behind it and add a direction note. Apply a Dye and Glow Ink Sac to each face you want decorated. Check both sides before using one Honeycomb to lock the sign. Keep the supporting block, and remember that moving the sign by mining requires writing it again.

For a hanging version, first craft six Oak Hanging Signs from six Stripped Oak Logs and two Iron Chains. Place one beneath a full block with enough access to reach both text faces. Its narrower text field favors short labels. The same dye, glow, and wax steps apply. See [Oak Sign](../items/OakSign.md), [Oak Hanging Sign](../items/OakHangingSign.md), [Wood construction](WoodConstruction.md), and [Blocks](Blocks.md).

## Sources and verification

Reviewed at `7af3a1594956f41ed57c3bf67d11ce006e61530f` on 2026-10-02. Source and bundled-data review only; no in-game placement, editing, crafting, support-removal, or harvesting test.

[text]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/entity/SignText.java
[blocks]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/Blocks.java#L1302-L1750
[nether-signs]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/Blocks.java#L5702-L5721
[pewen-blocks]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/Blocks.java#L7062-L7100
[items]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/Items.java#L1419-L1511
[paired-item]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/StandingAndWallBlockItem.java
[recipe-oak-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/recipe/crafting/oak_sign.json
[loot-oak-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/oak_sign.json
[recipe-spruce-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/recipe/crafting/spruce_sign.json
[loot-spruce-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/spruce_sign.json
[recipe-birch-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/recipe/crafting/birch_sign.json
[loot-birch-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/birch_sign.json
[recipe-jungle-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/recipe/crafting/jungle_sign.json
[loot-jungle-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/jungle_sign.json
[recipe-acacia-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/recipe/crafting/acacia_sign.json
[loot-acacia-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/acacia_sign.json
[recipe-dark-oak-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/recipe/crafting/dark_oak_sign.json
[loot-dark-oak-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/dark_oak_sign.json
[recipe-pale-oak-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/recipe/crafting/pale_oak_sign.json
[loot-pale-oak-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/pale_oak_sign.json
[recipe-mangrove-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/recipe/crafting/mangrove_sign.json
[loot-mangrove-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/mangrove_sign.json
[recipe-cherry-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/recipe/crafting/cherry_sign.json
[loot-cherry-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/cherry_sign.json
[recipe-bamboo-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/recipe/crafting/bamboo_sign.json
[loot-bamboo-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/bamboo_sign.json
[recipe-crimson-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/recipe/crafting/crimson_sign.json
[loot-crimson-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/crimson_sign.json
[recipe-warped-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/recipe/crafting/warped_sign.json
[loot-warped-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/warped_sign.json
[hanging-item]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/HangingSignItem.java
[recipe-oak-hanging-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/recipe/crafting/oak_hanging_sign.json
[loot-oak-hanging-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/oak_hanging_sign.json
[recipe-spruce-hanging-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/recipe/crafting/spruce_hanging_sign.json
[loot-spruce-hanging-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/spruce_hanging_sign.json
[recipe-birch-hanging-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/recipe/crafting/birch_hanging_sign.json
[loot-birch-hanging-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/birch_hanging_sign.json
[recipe-jungle-hanging-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/recipe/crafting/jungle_hanging_sign.json
[loot-jungle-hanging-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/jungle_hanging_sign.json
[recipe-acacia-hanging-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/recipe/crafting/acacia_hanging_sign.json
[loot-acacia-hanging-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/acacia_hanging_sign.json
[recipe-dark-oak-hanging-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/recipe/crafting/dark_oak_hanging_sign.json
[loot-dark-oak-hanging-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/dark_oak_hanging_sign.json
[recipe-pale-oak-hanging-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/recipe/crafting/pale_oak_hanging_sign.json
[loot-pale-oak-hanging-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/pale_oak_hanging_sign.json
[recipe-mangrove-hanging-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/recipe/crafting/mangrove_hanging_sign.json
[loot-mangrove-hanging-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/mangrove_hanging_sign.json
[recipe-cherry-hanging-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/recipe/crafting/cherry_hanging_sign.json
[loot-cherry-hanging-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/cherry_hanging_sign.json
[recipe-bamboo-hanging-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/recipe/crafting/bamboo_hanging_sign.json
[loot-bamboo-hanging-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/bamboo_hanging_sign.json
[recipe-crimson-hanging-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/recipe/crafting/crimson_hanging_sign.json
[loot-crimson-hanging-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/crimson_hanging_sign.json
[recipe-warped-hanging-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/recipe/crafting/warped_hanging_sign.json
[loot-warped-hanging-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/warped_hanging_sign.json
[axe]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/AxeItem.java
[standing]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/StandingSignBlock.java
[wall]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/WallSignBlock.java
[block-behaviour]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java
[ceiling]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/CeilingHangingSignBlock.java
[wall-hanging]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/WallHangingSignBlock.java
[wall-hanging-tag]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/tags/block/wall_hanging_signs.json
[default-survival]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L311
[sign-item]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/SignItem.java
[sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/SignBlock.java
[sign-entity]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/entity/SignBlockEntity.java
[sign-editor]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/client/gui/screens/inventory/AbstractSignEditScreen.java
[hanging-entity]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/entity/HangingSignBlockEntity.java
[sign-packet]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/network/protocol/game/ServerboundSignUpdatePacket.java
[server-text]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2032-L2047
[server-use]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L340-L398
[consume]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1080
[applicator]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/SignApplicator.java
[dye]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/DyeItem.java
[glow]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/GlowInkSacItem.java
[ink]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/InkSacItem.java
[wax]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/HoneycombItem.java#L116-L130
[render]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/client/renderer/blockentity/AbstractSignRenderer.java#L105-L139
[waterlogged]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java
[axe-tag]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[tool]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/ToolMaterial.java
[player-harvest]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[harvest-call]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L282-L297
[wall-loot]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/Blocks.java#L7264-L7272
[block-drops]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/Block.java
[pewen-items]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/Items.java#L314-L319
[creative]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[block-item]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/BlockItem.java
[level-placement]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/Level.java#L202-L215
[chunk-placement]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java#L336-L355
[entity-type]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/entity/BlockEntityType.java#L45-L100
[entity-valid-set]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/entity/BlockEntityType.java#L288-L309
[entity-validation]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/entity/BlockEntity.java#L51-L65
[recipe-pewen-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/recipe/pewen_sign.json
[ingredient]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/crafting/Ingredient.java
[recipe-codec]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/crafting/ShapedRecipe.java
[recipe-pewen-hanging-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/recipe/pewen_hanging_sign.json
[iron-chain]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/Blocks.java#L2317-L2322
[standing-tag]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/tags/block/standing_signs.json
[wall-tag]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/tags/block/wall_signs.json
[ceiling-tag]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/tags/block/ceiling_hanging_signs.json
[loot-pewen-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/pewen_sign.json
[loot-pewen-wall-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/pewen_wall_sign.json
[loot-pewen-hanging-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/pewen_hanging_sign.json
[missing-loot]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
