# TNT

TNT (`minecraft:tnt`) is the inventory form of the [TNT block](../blocks/TNT.md#tnt). Place it for a timed explosion, load it into a Dispenser, or use it to craft a Minecart with TNT. The item is registered directly from the active TNT block. [Registration][item-registration].

## Obtaining

The [TNT block guide](../blocks/TNT.md#obtaining) owns the exact crafting layout, recovery conditions and exploration sources. The recipe makes **one TNT** from five Gunpowder and four Sand or Red Sand. TNT also appears in Creative's Redstone Blocks selection. [Recipe][crafting]; [Creative entry][creative].

## Usage

- Place TNT, then follow the [priming routes](../blocks/TNT.md#priming-routes). Placing it beside an already-powered circuit can start its fuse immediately.
- Load a Dispenser to emit already-primed TNT. Read [Dispenser and Dropper](../blocks/DispenserAndDropper.md#facing-loading-and-activation) and the [TNT priming guide](../blocks/TNT.md#priming-routes) before building the delivery circuit.
- Combine **one TNT and one Minecart** in any arrangement to craft **one [Minecart with TNT](MinecartWithTNT.md)**. This is a separate item and entity; the placed-block guide does not establish its rail or collision behavior. [Shapeless recipe][minecart-recipe].

## Behavior

Placed TNT, ordinary versus explosion-shortened fuses, fluid movement, terrain damage and permission gates are covered in the [canonical block guide](../blocks/TNT.md#fuse-movement-and-water). Water does not cancel a running fuse. Check [rules and permissions](../blocks/TNT.md#rules-and-permissions) if ignition fails.

## Notes

This item page covers `minecraft:tnt`, not `minecraft:tnt_minecart`. Shared block acquisition and explosive behavior belong on the reciprocal [TNT block page](../blocks/TNT.md#tnt).

## Sources and verification

Source-reviewed on **2026-10-02** at `cf1c134b3f9ff634490e448fe26c335b90f82227`. No placement, dispensing, crafting or explosion test was run in-game.

[item-registration]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/item/Items.java#L1031-L1037
[crafting]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/recipe/crafting/tnt.json#L1-L20
[creative]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1342-L1357
[minecart-recipe]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/recipe/crafting/tnt_minecart.json#L1-L12
