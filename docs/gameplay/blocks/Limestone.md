# Limestone family

Limestone is a building family with **ten registered forms**, including smooth blocks, shaped construction pieces, a pillar and Chiseled Limestone. MattMC's [inventory browser](../mechanics/InventoryBrowser.md) can supply these ordinary listed items in Survival as well as Creative. **Plan placements before building:** normal tools cannot recover them with the bundled mining tags, both slab types have a one-item loot entry even when doubled, and the walls have unusual connection limits. [Block registrations][registry] · [Item registrations][items] · [Category entries][list] · [Tool check](#standard-tool-mining) · [Wall warning](#walls-and-the-missing-wall-tag)

## Obtaining and mining

### Inventory access and recipe limits

Search the item browser for the English name shown in the [forms table](#building-forms), then request the form you need. All ten are ordinary category entries. The browser's client and server insertion paths allow them in Survival; this is separate from earning materials through mining, crafting or world generation. Follow the [browser guide](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item) for cursor, capacity and feature checks. [Listing][list] · [Category assembly][browser] · [Client request][client] · [Server handling][server]

**No crafting, smelting or stonecutting conversion for these forms was found in the checked bundled recipe data.** In particular, do not spend time trying ordinary stone recipes to make Smooth Limestone or shaped Limestone. Request each form separately if using the browser. No naturally generated supply was found in the checked active source, world-generation data and decompressed structure templates; a cave's appearance is not evidence of a Limestone deposit. These are bounded source findings, not a survey of every possible custom data pack. [Recipe-loading path][recipes] · [Verification scope](#sources-and-verification)

### Standard-tool mining

All ten forms inherit **requires-correct-tool-for-drops** from the base Limestone registration. They are absent from the bundled pickaxe, axe, shovel and hoe mining tags. The standard tools use those tags to identify blocks they can harvest; an unmatched block returns false from that tool check. **A normal Diamond or Netherite Pickaxe therefore does not solve this family's missing-tag drop gate.** Breaking the block by hand or with those standard tools can destroy it without returning its item. [Base and variants][registry] · [Copied tool gate][copy] · [Pickaxe tag][pickaxe] · [Axe tag][axe] · [Shovel tag][shovel] · [Hoe tag][hoe] · [Tool-family assignment][item-tools] · [Tool rules][material] · [Unmatched result][drop-tool] · [Item dispatch][item-drop-tool] · [Player gate][player-gate] · [Server mining][harvest]

Silk Touch and Fortune do not bypass the player's correct-tool check. Nor do these loot tables contain a special Silk Touch or Fortune result. Added data packs or custom tool components can change the gate; the table below describes the bundled loot **when a drop-producing path actually reaches it**, not a promise that ordinary mining yields the listed item. [Harvest ordering][harvest] · [Per-form loot](#building-forms)

### Other removal and double-slab loss

A destructive explosion uses a different path: it can call the block's loot with an empty tool without the player's mining-tool test. Each Limestone table has an explosion-survival condition, so an explosion can still lose the item; block drops must also be enabled. This does not make a blast a safe or lossless way to move a build. No explosion arrangement was tested. [Explosion dispatch][explosion-call] · [Drop eligibility][explosion-default] · [Explosion loot context][explosion-loot] · [Survival condition][explosion-condition] · [Item-spawn rule][block-drops]

Both **Limestone Slab** and **Smooth Limestone Slab** have a single matching-item loot entry with no double-slab count rule. If their loot is reached and its condition passes, a double slab gives **one slab item, not two**. Joining two slabs does not create a recipe for recovering a full Limestone block. [Limestone Slab loot][loot-limestone_slab] · [Smooth Limestone Slab loot][loot-smooth_limestone_slab]

## Building forms

These are the exact registered block/item identities, with a separately checked loot table for each. The **loot entry** column must be read with the [mining gate](#standard-tool-mining) and [explosion condition](#other-removal-and-double-slab-loss) above. Block states such as facing, slab type and waterlogging are not additional IDs. [Block registrations][registry] · [Items][items]

| Name and exact route | Registry ID | Form | Loot entry |
| --- | --- | --- | --- |
| <span id="limestone"></span>[Limestone](../items/Limestone.md) | `minecraft:limestone` | Full block | [1 matching item][loot-limestone] |
| <span id="limestone-stairs"></span>[Limestone Stairs](../items/LimestoneStairs.md) | `minecraft:limestone_stairs` | Stairs; see [stairs](#stairs-and-corners) | [1 matching item][loot-limestone_stairs] |
| <span id="limestone-slab"></span>[Limestone Slab](../items/LimestoneSlab.md) | `minecraft:limestone_slab` | Top/bottom/double slab; see [slabs](#slabs-and-double-slabs) | [1 matching item][loot-limestone_slab] |
| <span id="limestone-wall"></span>[Limestone Wall](../items/LimestoneWall.md) | `minecraft:limestone_wall` | Wall; see [connection warning](#walls-and-the-missing-wall-tag) | [1 matching item][loot-limestone_wall] |
| <span id="limestone-pillar"></span>[Limestone Pillar](../items/LimestonePillar.md) | `minecraft:limestone_pillar` | Axis-oriented pillar | [1 matching item][loot-limestone_pillar] |
| <span id="limestone-chiseled"></span>[Chiseled Limestone](../items/ChiseledLimestone.md) | `minecraft:limestone_chiseled` | Six-direction facing | [1 matching item][loot-limestone_chiseled] |
| <span id="smooth-limestone"></span>[Smooth Limestone](../items/SmoothLimestone.md) | `minecraft:smooth_limestone` | Full decorative block; no cave-painting use | [1 matching item][loot-smooth_limestone] |
| <span id="smooth-limestone-stairs"></span>[Smooth Limestone Stairs](../items/SmoothLimestoneStairs.md) | `minecraft:smooth_limestone_stairs` | Stairs; see [stairs](#stairs-and-corners) | [1 matching item][loot-smooth_limestone_stairs] |
| <span id="smooth-limestone-slab"></span>[Smooth Limestone Slab](../items/SmoothLimestoneSlab.md) | `minecraft:smooth_limestone_slab` | Top/bottom/double slab; see [slabs](#slabs-and-double-slabs) | [1 matching item][loot-smooth_limestone_slab] |
| <span id="smooth-limestone-wall"></span>[Smooth Limestone Wall](../items/SmoothLimestoneWall.md) | `minecraft:smooth_limestone_wall` | Wall; see [connection warning](#walls-and-the-missing-wall-tag) | [1 matching item][loot-smooth_limestone_wall] |

### Slabs and double slabs

The ordinary and smooth slab forms use the same placement rules. Click the top face or lower half of a side for a bottom slab; use the underside or upper half of a side for a top slab. Place a second slab of the **same item** into the empty half to make a double slab. Ordinary and Smooth Limestone Slabs cannot combine with each other. [Slab placement and replacement][slabs]

A single slab can hold source Water. Making a double slab clears its waterlogged state, and a double slab cannot accept Water through this waterlogging interface. Keep the [one-slab recovery limit](#other-removal-and-double-slab-loss) in mind before combining them. [Water and double-state rules][slabs]

### Stairs and corners

The two stair forms face the player's horizontal direction. The clicked face and height select bottom or upside-down placement. Nearby stairs with compatible facing and the same top/bottom half can form inner or outer corners, including stairs of another material. Their corner test checks the **StairBlock class**, so missing Limestone mining or wall tags do not disable this particular behavior. Both forms can be waterlogged. [Registered stair classes][stair-registration] · [Placement, updates and corner checks][stairs]

### Walls and the missing wall tag

**Limestone Wall and Smooth Limestone Wall are absent from the bundled walls tag.** The wall connection test uses that tag to recognize another wall; simply sharing the WallBlock class is insufficient. With the checked tags and shapes:

- Two Limestone Walls, two Smooth Limestone Walls, or one of each do not create joining arms toward each other
- Beside an ordinary tagged wall such as Cobblestone Wall, the Limestone wall accepts the neighbor but the ordinary wall rejects the Limestone side, producing an asymmetric connection
- A suitable full sturdy side face can still accept a Limestone wall arm. The wall code also accepts Iron Bars-class blocks and suitably aligned Fence Gates

These are **source-predicted connection results**, not a tested appearance or animal-containment design. Check a small section before using these walls as a pen. [Actual wall tag][wall-tag] · [Connection, placement and update callbacks][walls] · [Connection exceptions][connection-exceptions] · [Gate alignment][gate]

The full-face test uses the neighbor's collision/support shape. The wall's narrow post and arms do not fill a horizontal block face, and `forceSolidOn` in the Limestone registration does not replace this test with a full-cube face. A block above can change whether the center post is present and which connected arms use the low or tall state; it does not add either Limestone wall to the missing tag. [Wall shapes][walls] · [Support shape][support-shape] · [Full-face rule][support] · [Support dispatch][support-call] · [Cached support computation][support-cache] · [Post and side-height updates][wall-post]

Wall posts and arms use collision extending **1.5 blocks high**. Both wall forms support waterlogging. This collision height is separate from the connection problem. [Collision shapes and water state][walls] · [Water operations][water]

### Pillar, chiseled and smooth blocks

The **Limestone Pillar** follows the clicked face's axis: top/bottom placement makes a vertical pillar, while a side face chooses its horizontal axis. [Pillar placement][pillar]

**Chiseled Limestone** has the exact ID **`minecraft:limestone_chiseled`**. It faces opposite the player's nearest looking direction when placed, including vertical directions. Use your viewing angle to choose its orientation; this is a different rule from the pillar's clicked-face axis. [Registration][registry] · [Directional placement][directional]

**Smooth Limestone** is an ordinary decorative full block. Its current implementation has no cave-painting interaction. There is no checked recipe converting ordinary Limestone to it. [Smooth block implementation][smooth] · [Recipe scope](#inventory-access-and-recipe-limits)

## Registered properties

All ten inherit **hardness 1.2**, **blast resistance 4.5**, Dripstone-block sounds and a bass-drum Note Block instrument from base Limestone. These are source properties, not mining times or a guaranteed blast radius. The pillar, chiseled and full-block forms have no waterlogged state; slabs, stairs and walls have the qualified Water behavior above. [Base registration and forms][registry] · [Property copying][copy] · [Pillar state][pillar] · [Chiseled state][directional] · [Default fluid state][fluid-default]

The family does not use falling-block behavior or an attachment-survival requirement. Removing a support beneath these placed blocks does not make them fall or pop off through a support-loss callback. Waterlogging allows a Bucket to insert or recover Water in the supported shapes; it does not turn full blocks or double slabs into water containers. [Registered classes][registry] · [Inherited survival rule][support-shape] · [Water operations][water] · [Double-slab exception][slabs]

## Related pages

- [Stone](Stone.md)
- [Pewen family](Pewen.md)
- [Inventory browser](../mechanics/InventoryBrowser.md)
- [Stone category](catalog/stone.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `bc6fd6aca1414d7a0140541217f3b22e45b158dc`. Checked all ten registrations, item/category/localized names, complete loot tables, tool tags and active mining callers, placement/corner/connection/support/water callbacks, and browser insertion. Negative acquisition review covered the native recipe and loot-loader scopes, active source and world-generation JSON, and 1,202 decompressed bundled structure templates; the separate TaCZ recipe catalog and optional-pack JSON were checked separately. A spare cave-painting loot definition is not a verified generated or player-placed source. [Unused-source distinction][cave-paint-loot] · [Current registrations][registry]. No in-game browser, mining, crafting, explosion, wall-layout, placement, waterlogging or world-generation test was run. Added data packs can change recipes, tags and loot.

[registry]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/java/net/minecraft/world/level/block/Blocks.java#L100-L125
[items]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/java/net/minecraft/world/item/Items.java#L76-L85
[list]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L296-L305
[browser]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[client]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[server]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1927
[recipes]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L60-L83
[cave-paint-loot]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/resources/data/minecraft/loot_table/blocks/cave_painting_pewen.json
[copy]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L1061-L1089
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[axe]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[shovel]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/resources/data/minecraft/tags/block/mineable/shovel.json
[hoe]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/resources/data/minecraft/tags/block/mineable/hoe.json
[item-tools]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/java/net/minecraft/world/item/Item.java#L435-L448
[material]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/java/net/minecraft/world/item/ToolMaterial.java#L35-L48
[drop-tool]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/java/net/minecraft/world/item/component/Tool.java#L49-L56
[item-drop-tool]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/java/net/minecraft/world/item/Item.java#L249-L252
[player-gate]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[harvest]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L297
[explosion-call]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/java/net/minecraft/world/level/ServerExplosion.java#L207-L217
[explosion-loot]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L176-L200
[explosion-default]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/java/net/minecraft/world/level/block/Block.java#L493-L495
[explosion-condition]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/java/net/minecraft/world/level/storage/loot/predicates/ExplosionCondition.java#L27-L35
[block-drops]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/java/net/minecraft/world/level/block/Block.java#L410-L415
[slabs]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/java/net/minecraft/world/level/block/SlabBlock.java#L58-L133
[stair-registration]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/java/net/minecraft/world/level/block/Blocks.java#L7255-L7258
[stairs]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/java/net/minecraft/world/level/block/StairBlock.java#L94-L164
[walls]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/java/net/minecraft/world/level/block/WallBlock.java#L54-L192
[wall-tag]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/resources/data/minecraft/tags/block/walls.json
[support]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/java/net/minecraft/world/level/block/SupportType.java#L11-L16
[support-shape]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L288-L310
[support-call]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L909-L915
[support-cache]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L947-L967
[connection-exceptions]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/java/net/minecraft/world/level/block/Block.java#L243-L250
[wall-post]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/java/net/minecraft/world/level/block/WallBlock.java#L195-L238
[gate]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/java/net/minecraft/world/level/block/FenceGateBlock.java#L207-L209
[pillar]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/java/net/minecraft/world/level/block/RotatedPillarBlock.java#L49-L55
[directional]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/java/net/minecraft/world/level/block/custom/DirectionalFacingBlock.java
[smooth]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/java/net/minecraft/world/level/block/custom/SmoothLimestoneBlock.java
[water]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java#L18-L49
[fluid-default]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L228-L230
[loot-limestone]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/resources/data/minecraft/loot_table/blocks/limestone.json
[loot-limestone_stairs]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/resources/data/minecraft/loot_table/blocks/limestone_stairs.json
[loot-limestone_slab]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/resources/data/minecraft/loot_table/blocks/limestone_slab.json
[loot-limestone_wall]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/resources/data/minecraft/loot_table/blocks/limestone_wall.json
[loot-limestone_pillar]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/resources/data/minecraft/loot_table/blocks/limestone_pillar.json
[loot-limestone_chiseled]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/resources/data/minecraft/loot_table/blocks/limestone_chiseled.json
[loot-smooth_limestone]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/resources/data/minecraft/loot_table/blocks/smooth_limestone.json
[loot-smooth_limestone_stairs]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/resources/data/minecraft/loot_table/blocks/smooth_limestone_stairs.json
[loot-smooth_limestone_slab]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/resources/data/minecraft/loot_table/blocks/smooth_limestone_slab.json
[loot-smooth_limestone_wall]: https://github.com/HungLo2020/MattMC/blob/bc6fd6aca1414d7a0140541217f3b22e45b158dc/src/main/resources/data/minecraft/loot_table/blocks/smooth_limestone_wall.json
