# Honey Block

**Honey Block** (`minecraft:honey_block`) stores bottled honey and provides a slowing, sliding surface for builds. The canonical [Slime and Honey Blocks guide](../blocks/SlimeAndHoneyBlocks.md#honey-block) covers its collision, landing, and movement behavior.

## Obtaining

Craft **four [Honey Bottles](HoneyBottle.md) in a 2 × 2 square** into one Honey Block, retaining four empty Glass Bottle remainders. Ordinary Survival breaking returns the block by hand; Silk Touch is not required. Honey Bottles come from [full Bee housing](../blocks/BeeHousing.md#harvesting). [Crafting recipe][recipe] · [Bottle remainders and collection](../blocks/SlimeAndHoneyBlocks.md#crafting-and-collecting)

## Usage

Combine one Honey Block with **four Glass Bottles in four separate slots** in a Crafting Table to make four Honey Bottles. Use placed Honey Blocks for slowing floors, side-slide walls, or eligible piston assemblies. [Re-bottling recipe][unpack] · [Placed uses](../blocks/SlimeAndHoneyBlocks.md#honey-block)

## Behavior

Honey slows horizontal motion and reduces the base jump impulse. Landing damage uses a 0.2 multiplier; it is not complete fall protection. Side sliding has contact and descent conditions. It does not adhere to Slime Blocks, and a piston assembly still shares the 12-moved-block limit. See [sticky-block rules](../blocks/SlimeAndHoneyBlocks.md#pistons-and-neighboring-blocks) and [moving entity limits](../blocks/SlimeAndHoneyBlocks.md#moving-entities-and-projectile-limits).

## Notes

* This item is the item form of the `minecraft:honey_block` block; [Honeycomb Block](HoneycombBlock.md) is a different block
* Source-reviewed at `beb4335362d5983b867ef84d66a74ce668b6ef7d` on 2026-10-02; no in-game crafting, sliding, damage, or piston test was run

[recipe]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/recipe/crafting/honey_block.json#L1-L15
[unpack]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/recipe/crafting/honey_bottle.json#L1-L15
