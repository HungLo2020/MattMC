# Crying Obsidian

**Crying Obsidian** is the item form of `minecraft:crying_obsidian`. Place it as a light-emitting building block or use it to craft a [Respawn Anchor](../blocks/RespawnAnchor.md#crafting-and-recovering-an-anchor). [Item registration][items]

## Obtaining

Use the checked [adult Piglin bartering or ruined-portal routes](../blocks/Obsidian.md#finding-both-forms). Mining a placed block requires an **unbroken Diamond or Netherite Pickaxe** and returns **one Crying Obsidian**; see the canonical [mining rules](../blocks/Obsidian.md#mining-and-block-properties). There is no bundled recipe producing it or converting ordinary Obsidian into it. [Loot][loot] · [Recipe loading][recipes]

## Uses

Crying Obsidian emits **light level 10**, but **cannot replace ordinary Obsidian in a Nether portal frame**. It does not select a respawn point by itself. Its [block guide](../blocks/Obsidian.md#crying-obsidian) covers these distinctions, particles and block properties. Six blocks form the Crying Obsidian portion of the [anchor recipe](../blocks/RespawnAnchor.md#crafting-and-recovering-an-anchor).

## Verification

Source-reviewed on **2026-10-02** at `60699a119c4728a7bcaf15196f3c839cfcfd69dc`; the block guide links the interaction, portal and recipe evidence. No in-game test was run.

[Items](Items.md) · [Obsidian item](Obsidian.md) · [Obsidian and Crying Obsidian](../blocks/Obsidian.md)

[items]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/item/Items.java
[loot]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/loot_table/blocks/crying_obsidian.json
[recipes]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L72-L88
