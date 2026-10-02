# Respawn Anchor

A **Respawn Anchor** (`minecraft:respawn_anchor`) stores up to four Glowstone charges and lets you select a respawn point in dimensions that allow anchors. In the bundled dimensions, that means the **Nether**. Trying to use a charged anchor for respawning elsewhere causes an explosion. [Registration][blocks] · [Anchor behavior][anchor] · [Dimension settings][nether-type] [overworld-type] [end-type] [primordial-type]

## Crafting and recovering an anchor

Craft **6 Crying Obsidian + 3 Glowstone blocks → 1 Respawn Anchor**. Fill the top and bottom crafting rows with Crying Obsidian and the middle row with Glowstone. Ordinary Obsidian and Glowstone Dust are not substitutes. See [Obsidian and Crying Obsidian](Obsidian.md#finding-both-forms) for a checked supply route. [Exact recipe][recipe] · [Recipe loading][recipes]

Mine a placed anchor with an **unbroken Diamond or Netherite Pickaxe** to recover **one anchor**. It requires a correct tool and the diamond-tier pickaxe requirement. Silk Touch is unnecessary; Fortune adds nothing. Its loot does not preserve the charge or return the spent Glowstone, so a recovered anchor placed again starts at **0 charges**. [Registry and tool rules][blocks] [pickaxe] [diamond-tier] [iron-denials] [diamond-denials] [netherite-denials] [tool-material] · [Harvest dispatch][stack] [player-tool] [break-dispatch] · [Loot][loot] · [Default placement][block] [block-item] [anchor]

## Charging and selecting your respawn point

1. Place the anchor in the Nether and leave a solid floor and clear standing space around it
2. Use a **Glowstone block** on it to add **one charge**, up to **four**
3. Use the charged anchor again with both hands empty to select it as your respawn point

Each successful charge consumes **one Glowstone block** in Survival; players with infinite materials do not lose the item. Charging and selecting are different interactions: adding fuel alone does not save the respawn point, and selecting it does not consume a charge. [Fuel check and spawn selection][anchor] · [Item consumption][stack] · [Active interaction dispatch][use-dispatch]

An off-hand Glowstone block gets a chance to charge a non-full anchor before the ordinary main-hand spawn interaction. At **four charges**, Glowstone cannot add another charge and the interaction can proceed to setting spawn or exploding, depending on the dimension. An empty anchor does neither. Avoid repeatedly clicking a full anchor outside the Nether, even while holding Glowstone. [Hand priority, full-charge behavior and empty check][anchor] · [Dispatch][use-dispatch]

Selecting another anchor or a working [Bed](Bed.md) replaces the saved respawn point. Multiple players can select the same anchor, but the charges belong to that placed block and are shared. Moving an anchor item does not move anyone's saved position: charge and select the newly placed block. [Stored position and selection][anchor] [server-player] · [Bed selection][bed]

## Charges, light and comparator output

| Stored charges | Emitted light | Comparator signal |
| ---: | ---: | ---: |
| 0 | 0 | 0 |
| 1 | 3 | 3 |
| 2 | 7 | 7 |
| 3 | 11 | 11 |
| 4 | 15 | 15 |

Both outputs use the stored charge scaled to 15 and rounded down. A comparator can monitor the remaining fuel; an anchor is not charged by a redstone signal. The registered hardness is **50** and blast resistance is **1,200**. Pistons cannot push or pull it. [Charge, comparator and light calculation][anchor] [blocks] · [Piston exclusion][pistons]

## Successful respawns and fallback

For an ordinary saved anchor point, the server requires the block to still be an anchor, **at least one charge**, an anchor-enabled dimension, and a usable nearby standing position. **A successful death respawn spends one charge.** Simply selecting the anchor spends none, and returning from the End through the checked End-return path does not spend a charge. Command-forced respawn settings have separate rules; they are not the ordinary interaction described here. [Respawn validity and consumption][server-player] · [Death versus End-return dispatch][respawn-packet] [player-list]

The position search checks surrounding cells at the anchor's level, one block below, and one block above, then the cell directly above the anchor. It checks floor height, player collision space, the world border and invalid spawn blocks. It first avoids dangerous blocks, then retries with that danger filter relaxed: a usable result is **not a promise that the surroundings are harmless**. Provide your own protected floor and headroom. [Search order][anchor] · [Position checks][dismount]

If a saved ordinary point is empty, missing, in a disallowed dimension, or has no usable standing position, it cannot provide that anchor respawn. The checked failed-point path falls back to the server's **default spawn** and does not carry the unusable saved point into the replacement player. Finding no usable position does not itself consume a charge. There is no stored sequence of older beds or anchors to try. Re-select a working point after repairing or replacing it. [Saved-point lookup and failure][server-player] · [Default-spawn transition][transition] · [Replacement-player handling][player-list]

## Dimension restrictions and explosions

The rule is the dimension type's **`respawn_anchor_works`** setting, not merely its name. The bundled Nether type enables it; Overworld, Overworld Caves, End and Primordial Caves types disable it. The normal world preset uses the Nether, Overworld, End and Primordial Caves types. Custom data can change these settings. [Dimension predicate][anchor] · [Bundled types][nether-type] [overworld-type] [overworld-caves-type] [end-type] [primordial-type] · [Normal preset][normal]

Charging is possible even where anchors cannot set spawn. Using a **charged** anchor there through its spawn interaction removes the anchor and requests a **power-5, fire-producing block explosion**. Placing it or adding a charge is not that detonation interaction. Actual block damage and fire depend on the explosion rules and surroundings. Its high normal blast resistance does not prevent this deliberate self-removal. [Interaction and explosion][anchor] · [Explosion processing][explosion]

The [Nether guide](../dimensions/Nether.md#respawning-safely) covers dimension preparation; [Beds](Bed.md) remain the canonical guide to sleeping and bed-specific restrictions.

## Sources and verification

Source-reviewed on **2026-10-02** at `60699a119c4728a7bcaf15196f3c839cfcfd69dc`, including the active server interaction and death/End-return respawn paths, standing-position search, fuel transaction, loot, recipe, comparator/light calculation and all bundled dimension-type flags. No in-game crafting, charging, mining, respawn, comparator or explosion test was run. [Anchor explosion source][anchor]

Related: [Anchor item](../items/RespawnAnchor.md) · [Crying Obsidian](Obsidian.md#crying-obsidian) · [Glowstone](../items/Glowstone.md) · [Nether](../dimensions/Nether.md) · [End](../dimensions/End.md) · [Workstations and utilities](catalog/workstations.md) · [Blocks](Blocks.md)

[blocks]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/Blocks.java
[items]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/item/Items.java
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[diamond-tier]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/tags/block/needs_diamond_tool.json
[iron-denials]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/tags/block/incorrect_for_iron_tool.json
[diamond-denials]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/tags/block/incorrect_for_diamond_tool.json
[netherite-denials]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/tags/block/incorrect_for_netherite_tool.json
[tool-material]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/item/ToolMaterial.java
[stack]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/item/ItemStack.java
[player-tool]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[break-dispatch]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L256-L297
[recipes]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L72-L88
[pistons]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java#L226-L259
[anchor]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/RespawnAnchorBlock.java
[recipe]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/recipe/crafting/respawn_anchor.json
[loot]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/loot_table/blocks/respawn_anchor.json
[block]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/Block.java#L435-L437
[block-item]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/item/BlockItem.java
[use-dispatch]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L343-L378
[server-player]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/server/level/ServerPlayer.java
[bed]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/BedBlock.java
[respawn-packet]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1766-L1792
[player-list]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/server/players/PlayerList.java#L430-L493
[dismount]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/entity/vehicle/DismountHelper.java#L77-L109
[transition]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/portal/TeleportTransition.java#L48-L85
[nether-type]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/dimension_type/the_nether.json
[overworld-type]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/dimension_type/overworld.json
[overworld-caves-type]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/dimension_type/overworld_caves.json
[end-type]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/dimension_type/the_end.json
[primordial-type]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/dimension_type/primordial_caves.json
[normal]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[explosion]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/ServerExplosion.java
