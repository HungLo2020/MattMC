# Respawn Anchor

**Respawn Anchor** is the item form of `minecraft:respawn_anchor`, a placed block for selecting a charged Nether respawn point. [Item registration][items]

## Obtaining

Use the canonical [anchor recipe](../blocks/RespawnAnchor.md#crafting-and-recovering-an-anchor): **6 Crying Obsidian and 3 Glowstone blocks produce 1 anchor**. Mining a placed anchor with an **unbroken Diamond or Netherite Pickaxe** returns one anchor item, without its stored charges or spent Glowstone. [Recipe][recipe] · [Loot][loot]

## Uses

Place the item, charge the block with Glowstone, then select it as your respawn point. Follow the [charging and selection steps](../blocks/RespawnAnchor.md#charging-and-selecting-your-respawn-point), [charge-consumption rules](../blocks/RespawnAnchor.md#successful-respawns-and-fallback), and [dimension restrictions](../blocks/RespawnAnchor.md#dimension-restrictions-and-explosions). Using a charged anchor where anchors cannot set spawn causes an explosion; simply carrying the item does not provide a respawn point.

## Verification

Source-reviewed on **2026-10-02** at `60699a119c4728a7bcaf15196f3c839cfcfd69dc`; the block guide records active interaction and respawn evidence. No in-game test was run.

[Items](Items.md) · [Respawn Anchor block guide](../blocks/RespawnAnchor.md) · [Crying Obsidian](CryingObsidian.md)

[items]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/item/Items.java
[recipe]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/recipe/crafting/respawn_anchor.json
[loot]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/loot_table/blocks/respawn_anchor.json
