# Ender Chest

The `minecraft:ender_chest` item places an access block for your personal 27-slot Ender inventory. Other players using the same block see their own stored items.

## Obtaining

Craft one using the [Eye of Ender and eight Obsidian recipe](EyeOfEnder.md#other-verified-recipes). In this registration, ordinary mining needs no correct tool tier: without Silk Touch it drops eight Obsidian, while the Silk Touch loot branch returns one Ender Chest. A pickaxe is the tagged faster tool.

## Usage

Place it horizontally facing the player, and keep the block above from obstructing its opening with redstone conduction. Its menu accesses the same personal contents at other Ender Chests and across dimensions in the same world/server.

## Behavior

Breaking the access block does not spill or delete your Ender contents. The inventory is saved with the player and retained through ordinary respawn. Hoppers cannot access it, and the block has no inventory-fullness comparator output.

## Notes

See [Ender Chest](../blocks/EnderChest.md) for the exact mining exception, waterlogging, obstruction, lifecycle, and Piglin interactions. [Recipe][recipe] · [Loot][loot] · [Registration][reg]

## Sources and verification

Source-reviewed at `60699a119c4728a7bcaf15196f3c839cfcfd69dc` on 2026-10-02. The linked block guide contains the complete storage and interaction evidence. No gameplay test of storage, relocation, obstruction, dimension travel, respawn, automation, or villager employment was run.

[recipe]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/recipe/crafting/ender_chest.json
[loot]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/loot_table/blocks/ender_chest.json
[reg]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/Blocks.java#L2612-L2616
