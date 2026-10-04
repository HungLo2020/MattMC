# Painting

A **Painting** decorates a wall with one of the world's registered pictures. An ordinary Painting item chooses a picture when placed, using the largest eligible size that fits the available wall space. Use an [Item Frame](ItemFrame.md) instead to display an inventory item or Map. [Item registration][registration] · [Picture selection][selection]

## Obtaining

Surround **one Wool with eight Sticks** in a 3 × 3 crafting grid to make **one Painting**. Any of the sixteen bundled Wool colors works; the color does not select the picture. [Recipe][recipe] · [Wool ingredients][wool] · [Picture selection][selection]

| Left | Center | Right |
| --- | --- | --- |
| Stick | Stick | Stick |
| Stick | Wool | Stick |
| Stick | Stick | Stick |

A **Master, level-5 Shepherd** has a normal trade listing of **three Paintings for a base price of two Emeralds**. Check the Villager's actual offers and current price. Breaking an existing Painting can also recover one item under the [drop rules](#removing-and-reusing-paintings). [Shepherd listing][trade] · [Offer quantities][trade-amount] · [Trade selection][trade-selection]

The Creative catalog includes an ordinary Painting and preset entries for the normally placeable pictures. Presets carry a particular variant rather than leaving the choice random. Follow the [inventory browser's mode and permission limits](../mechanics/InventoryBrowser.md#mode-and-permission-limits): catalog visibility in Survival does not establish server-accepted item insertion. [Catalog entries][catalog] · [Preset contents][presets]

## Usage

### Placing a painting

Use the Painting on the **side of a supporting block**. Ordinary item placement accepts horizontal facing directions; it does not hang Paintings on floors or ceilings. Keep the intended picture area backed by valid support and clear of obstructing collision shapes. Every block behind its footprint must qualify; a full solid wall is a straightforward starting point. Repeaters and Comparators are also accepted by the support check. [Placement][placement] · [Support and overlap][support]

Placement needs the player's item-use permission and the server's normal reach and interaction checks. Spectator mode does not place it. On an interactive support such as a Chest, hold your configured **Sneak/Crouch** control while using the Painting to bypass opening the block. Successful placement consumes one item in ordinary Survival; the server restores the held count for players with infinite materials, normally Creative. [Player permission][permission] · [Server admission][server-placement] · [Block-use dispatch][block-use]

### Choosing the size and picture

For an ordinary Painting item, placement:

1. Starts with variants in the world's `minecraft:placeable` painting-variant tag
2. Removes candidates that fail support, collision, or hanging-entity overlap checks at the clicked location
3. Keeps only those with the **largest area**, measured as width × height
4. Chooses randomly among those remaining candidates

It does not randomly choose among every size that could fit. Repeatedly replacing a Painting on the same open wall can change its picture, but it will continue to favor the largest eligible area. [Selection algorithm][selection]

To aim for a smaller size, leave the desired wall rectangle clear and put temporary blocks **in front of the wall around that opening**, obstructing larger pictures. Adjust the clicked block if the picture extends to an unwanted side: even-width and even-height variants are offset from the clicked anchor. After placement, remove only the temporary surrounding blocks, keeping the support behind the picture. This is a way to narrow the eligible sizes, not a guarantee of a particular picture. [Footprint alignment][footprint] · [Fit checks][support]

A preset Painting item first needs a successful ordinary placement search, then applies its named variant and checks support and clearance again. Its tooltip can show the title, author when supplied, and dimensions. Presets are useful when you have Creative access and want a specific picture. [Preset items][presets] · [Component application][components] · [Chosen variant][painting-components] · [Tooltip][placement]

## Behavior

### Bundled sizes and variants

The reviewed bundled data contains **51 registered variants**, of which **47** are in the normal placement tag. These counts describe the supplied data, not a limit on custom data packs. Width and height below are in **blocks**. [Variant data][variant-data] · [Normal placement tag][placeable] · [Registry loading][registry-load]

| Width × height | Variants in normal random placement |
| --- | ---: |
| 1 × 1 | 8 |
| 2 × 1 | 5 |
| 1 × 2 | 3 |
| 2 × 2 | 8 |
| 4 × 2 | 5 |
| 3 × 3 | 9 |
| 4 × 3 | 2 |
| 3 × 4 | 2 |
| 4 × 4 | 5 |

`minecraft:earth`, `minecraft:wind`, `minecraft:water`, and `minecraft:fire` are additional **2 × 2** variants outside that tag. They are not random outcomes of an ordinary Painting with the bundled data. The catalog generates their presets in the operator category, which requires the relevant Creative ability and permission level. [Placement tag][placeable] · [Operator catalog][operator-catalog] · [Permission rule][operator-permission]

Data packs can change the variant registry and placement tag. Variant data defines dimensions and an artwork asset, with optional title and author; its data codec accepts widths and heights from **1 to 16**. That format range is separate from the bundled sizes above. [Variant format][variant-format] · [World registry loading][world-load]

### Removing and reusing paintings

Attack a normally placed Painting to break it, subject to the server's interaction and damage checks. With **entity drops enabled**, it releases **one ordinary Painting item**, unless the breaking entity is a player with infinite materials, normally Creative. The recovered item does **not preserve the picture**: placing it again performs the ordinary selection process. [Attack dispatch][server-attack] · [Hanging-entity damage][attached] · [Painting drop][drops]

Removing valid support or adding an obstruction can also break it when the periodic server survival check runs. Overlapping Paintings cannot share the checked space, even if they face different directions; other hanging entities also obstruct it when they face the same direction. Keep the backing wall intact and leave room around neighboring displays. [Periodic checks][attached] · [Support and overlap][support]

Disabling entity drops suppresses the Painting item. The inherited damage handling also rejects mob-caused damage when mob griefing is disabled. Modified invulnerability or entity data can change removal behavior. [Drop rule][drops] · [Damage gates][attached]

## Notes

- The inventory item and placed entity are registered as `minecraft:painting`
- Unmodified inventory Paintings stack to **64**; preset variants have different item components, so different pictures are distinct stacks
- The chosen artwork comes from the variant's resource asset. The active Rust route submits that artwork and backing geometry, but this review does not verify the appearance of every picture, resource pack, or shader setting in-game

[Registrations][registration] · [Entity registration][entity] · [Default item properties][item-defaults] · [Stack limit][stack] · [Preset components][presets] · [Stack matching][stack-matching] · [Painting renderer][render] · [Active render traversal][render-route] · [Material submission][render-submit]

Related: [Item Frame](ItemFrame.md) · [Glow Item Frame](GlowItemFrame.md) · [Stick](Stick.md) · [Villager](../mobs/Villager.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked the recipe and Wool tag, trade construction and selection, item/entity registration, client/server placement and attack dispatch, all 51 bundled variant files and the placement tag, component application, support/drop rules, catalog permissions, and the selected render submission route. No in-game crafting, trading, placement, reroll, drop, or visual test was run. Data packs, resource packs, permissions, entity data, and game rules can change the applicable conditions.

[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1410
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/painting.json
[wool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/wool.json
[trade]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L164-L270
[trade-amount]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1422-L1482
[trade-selection]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/Villager.java#L818-L842
[catalog]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1118-L1130
[presets]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2271-L2284
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/HangingEntityItem.java#L34-L97
[selection]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/decoration/Painting.java#L93-L127
[support]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/decoration/HangingEntity.java#L80-L109
[footprint]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/decoration/Painting.java#L152-L171
[permission]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L1524-L1532
[server-placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1255-L1280
[block-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L340-L399
[components]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/EntityType.java#L1710-L1722
[variant-data]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/painting_variant
[placeable]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/painting_variant/placeable.json
[variant-format]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/decoration/PaintingVariant.java#L19-L30
[registry-load]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L309-L335
[world-load]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/WorldLoader.java#L35-L44
[operator-catalog]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2131-L2170
[operator-permission]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L1816-L1818
[attached]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/decoration/BlockAttachedEntity.java#L39-L90
[drops]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/decoration/Painting.java#L173-L181
[server-attack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1703-L1759
[entity]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/EntityType.java#L975-L978
[item-defaults]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L357-L366
[stack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L390
[render]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/renderer/entity/PaintingRenderer.java#L37-L58
[render-route]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/renderer/LevelRenderer.java#L674-L687
[render-submit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java#L1527-L1543
[painting-components]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/decoration/Painting.java#L78-L91
[stack-matching]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L666-L674
