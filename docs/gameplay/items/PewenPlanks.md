# Pewen Planks

Pewen Planks is a building block and explicit ingredient in several Pewen-family recipes. Its ID is `minecraft:pewen_planks`.

## Obtaining

The item is listed in Creative inventory. The bundled recipe intends to convert Pewen logs into four planks, but its ingredient uses an older JSON object format incompatible with the active ingredient codec. This review does **not** establish a working log-to-plank Survival recipe.

## Building and crafting

Place planks directly or use the source-reviewed shaped recipes listed in the [Pewen family guide](../blocks/Pewen.md#construction-and-recipes), including stairs, slabs, fences, doors, signs, and a boat.

Pewen Planks are absent from the checked generic plank item tag. A recipe explicitly naming Pewen Planks and one accepting the generic planks tag are different: do not expect universal substitution in wooden tools, chests, or other generic recipes without checking the current data.

## Related pages

- [Pewen Log](PewenLog.md)
- [Pewen family](../blocks/Pewen.md)
- [Items](Items.md)

## Sources and verification

Reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. Source/data review only; no in-game placement, growth, harvesting, or crafting test.

- [Planks registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/Blocks.java#L7010-L7017)
- [Planks recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/pewen_planks.json)
- [Ingredient codec](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/crafting/Ingredient.java)
- [Generic planks tag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/item/planks.json)
