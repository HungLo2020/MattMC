# Barrel

The `minecraft:barrel` item places a 27-slot shared storage block. Each placed Barrel owns its contents; adjacent Barrels keep separate inventories.

## Obtaining

Craft one from six accepted planks and two wooden slabs using the [Barrel recipe](../blocks/Barrel.md#crafting-and-collecting). Ordinary mining drops one Barrel even by hand; an axe is the tagged faster tool.

## Usage

Place it facing any of six directions and use it to open storage. Its menu has no lid-clearance or sitting-cat check, so it can open beneath a solid block when you can reach it. Hoppers can transfer items, and a comparator reads inventory fullness.

## Behavior

Breaking a filled Barrel spills its stored items separately. The Barrel drop preserves a custom name but does not carry the inventory, even with Silk Touch. It is also a Fisherman job site, and opening or breaking it can anger nearby eligible Piglins.

## Notes

The [Barrel guide](../blocks/Barrel.md) covers exact ingredients, facing, persistence, automation, job-site use, and Piglin checks. [Recipe][recipe] · [Loot][loot] · [Registration][reg]

## Sources and verification

Source-reviewed at `60699a119c4728a7bcaf15196f3c839cfcfd69dc` on 2026-10-02. The linked block guide contains the complete storage and interaction evidence. No gameplay test of storage, relocation, obstruction, dimension travel, respawn, automation, or villager employment was run.

[recipe]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/recipe/crafting/barrel.json
[loot]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/loot_table/blocks/barrel.json
[reg]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/Blocks.java#L5311-L5315
