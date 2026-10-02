# Pewen Fence

## Obtaining

Craft **three Pewen Fences** from two rows of **Pewen Plank–Stick–Pewen Plank**, using four planks and two sticks. Its ordinary category entry also supports the separate [inventory-browser](../mechanics/InventoryBrowser.md) route in Survival and Creative. [Recipe](https://github.com/HungLo2020/MattMC/blob/b153e7232bbb43920a8694afbdb0053c2e219d77/src/main/resources/data/minecraft/recipe/pewen_fence.json) · [Category entry](https://github.com/HungLo2020/MattMC/blob/b153e7232bbb43920a8694afbdb0053c2e219d77/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L244)

## Usage

Pewen Fence is a building and decorative fence. Read the [Pewen connection caveat](../blocks/Pewen.md#fence-connections) before using it for an animal pen.

## Behavior

The current bundled tags omit Pewen Fence from both fence groups. Source review therefore finds no joining rail between two Pewen Fences or between Pewen and an ordinary wooden fence. Suitable full sturdy faces and correctly oriented Fence Gates use separate connection rules; Nether Brick Fence has a one-sided connection exception. See the [source-reviewed family guide](../blocks/Pewen.md#fence-connections) for those limits. No in-game placement or containment test was run.

## Notes

* This item is registered as `minecraft:pewen_fence`.
