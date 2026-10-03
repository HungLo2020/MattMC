# Soul Heart

A **Soul Heart** (`minecraft:soul_heart`) is an inventory lure for Spectres. It is registered as a plain Item, **not a placeable block**, and does not have a food component. [Item registration][item]

## Obtaining

Soul Heart is an ordinary category-listed item, so MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) can request it in **Creative** through the checked insertion route. No bundled recipe or loot-table source was found; natural/crafting acquisition remains separate and unverified here. Do not assume an upstream mod's drop or recipe exists. [Category entry][creative]

## Usage

Hold a Soul Heart in your **main hand or offhand** to qualify for the [Spectre](../mobs/Spectre.md)'s lure goal. The goal checks its nearest player within **64 blocks**; a closer player without a Heart can prevent a farther holder from being selected. It does not start when that selected player is already the Spectre's leash holder. [Active goal installation][goals] · [Player and held-item checks][lure]

This is a held-item attraction action, not feeding, placement or owner-taming. The goal does not consume the Heart, and the Spectre's breeding-food check returns false. [Lure callbacks][lure] · [Food check][goals]

## Behavior

While the lure goal runs, it looks toward the selected player and requests movement toward that player's eye height. At a distance below 2.5 blocks, it stops navigation. These are source-defined movement requests, not a tested following distance or a guarantee through obstacles. Stopping the goal clears its lure target and sets a counter that delays retesting the player for 100 subsequent start-condition checks; this is not a measured wall-clock interval. [Goal lifecycle][lure]

## Notes

- The actual Spectre entity registration points to the implementation containing this goal. [Entity registration][entity]
- Source-reviewed at `d1bff20a6235d5a7052eff0e7e2191da8fbb00c4` on 2026-10-02. The recipe/loot absence statement covers the checked bundle, not arbitrary data packs. No in-game acquisition, attraction, movement, leash or inventory-use test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/d1bff20a6235d5a7052eff0e7e2191da8fbb00c4/src/main/java/net/minecraft/world/item/Items.java#L518
[creative]: https://github.com/HungLo2020/MattMC/blob/d1bff20a6235d5a7052eff0e7e2191da8fbb00c4/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1842
[goals]: https://github.com/HungLo2020/MattMC/blob/d1bff20a6235d5a7052eff0e7e2191da8fbb00c4/src/main/java/net/alexsmobs/entity/EntitySpectre.java#L104-L112
[lure]: https://github.com/HungLo2020/MattMC/blob/d1bff20a6235d5a7052eff0e7e2191da8fbb00c4/src/main/java/net/alexsmobs/entity/EntitySpectre.java#L344-L403
[entity]: https://github.com/HungLo2020/MattMC/blob/d1bff20a6235d5a7052eff0e7e2191da8fbb00c4/src/main/java/net/minecraft/world/entity/EntityType.java#L1185-L1187
