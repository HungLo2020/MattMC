# Slime Block

**Slime Block** (`minecraft:slime_block`) packs Slimeballs into a placed block for bouncing surfaces and sticky piston assemblies. The canonical [Slime and Honey Blocks guide](../blocks/SlimeAndHoneyBlocks.md#slime-block) covers collision, crouching, and movement limits.

## Obtaining

Fill a 3 × 3 crafting grid with **nine [Slimeballs](Slimeball.md)** to make one Slime Block. One Slime Block unpacks shapelessly into nine Slimeballs. Ordinary Survival breaking returns the block by hand; Silk Touch is not required. [Packing recipe][recipe] · [Unpacking recipe][unpack] · [Collection rules](../blocks/SlimeAndHoneyBlocks.md#crafting-and-collecting)

## Usage

Use it for a bouncing landing surface or to carry eligible neighboring blocks when moved by a piston. It also turns **Awkward Potion into Potion of Oozing** through brewing. See [placed Slime behavior](../blocks/SlimeAndHoneyBlocks.md#slime-block) and [piston attachments](../blocks/SlimeAndHoneyBlocks.md#pistons-and-neighboring-blocks).

## Behavior

Sneak/Crouch suppresses the normal landing bounce. Slime and Honey Blocks do not adhere to each other, and every attached moving group shares the piston limit of 12 moved blocks. Projectile paths and moving player launchers have separate limitations; use the guide's [entity and projectile section](../blocks/SlimeAndHoneyBlocks.md#moving-entities-and-projectile-limits).

## Notes

* This item is the item form of the `minecraft:slime_block` block
* Source-reviewed at `beb4335362d5983b867ef84d66a74ce668b6ef7d` on 2026-10-02; no in-game crafting, bounce, damage, or piston test was run

[recipe]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/recipe/crafting/slime_block.json#L1-L16
[unpack]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/recipe/crafting/slime_ball.json#L1-L11
