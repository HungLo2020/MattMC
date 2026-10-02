# Stonecutter

The **Stonecutter** (`minecraft:stonecutter`) converts one accepted building block into the selected recipe's output. It is useful for small batches of stairs, slabs, and other stone forms, with no fuel or cooking timer. The available choices come from actual stonecutting recipes, not merely an item's appearance or name. [Menu and input selection][menu]

## Crafting and collecting

In a Crafting Table, put **three ordinary Stone across one row**, with **one Iron Ingot directly above the middle Stone**. This makes **one Stonecutter**. The recipe names Stone specifically: Cobblestone and Smooth Stone are not substitutes. [Recipe][recipe]

Use a correct, **unbroken pickaxe** to recover the placed workstation. It requires the correct tool for drops, and its ordinary loot returns one Stonecutter without a Silk Touch requirement. Explosion recovery is conditional. [Registration][blocks] · [Pickaxe tag][pickaxe] · [Loot][loot] · [Mining rules](../mechanics/Mining.md)

## Using the menu

1. Place the Stonecutter and interact with it.
2. Put an accepted ingredient in its input slot.
3. Select a recipe from the available choices.
4. Take the result; each completed operation consumes **one input block** and gives the selected recipe's output count.

Changing to a different input item resets the selected recipe and preview. An empty result can mean no selection or no matching recipe, not missing fuel. Stonecutting uses a single input slot; additional ingredients belong in other crafting systems. [Menu selection and result removal][menu]

The menu's input is temporary player-session storage, not a persistent inventory inside the placed block. Closing it clears the preview and returns remaining input to the player, dropping it when the usual return path cannot retain it. A Hopper does not get access to this private menu container through the workstation block. [Menu cleanup][menu] · [Container return handling][return] · [Full-inventory return][inventory-return] · [Hopper lookup][hopper] · [Block/menu implementation][block]

## Checked recipe examples

| One input block | Selected result | Count |
| --- | --- | ---: |
| Stone | Stone Stairs | 1 |
| Stone | Stone Slab | 2 |
| Stone | Stone Bricks | 1 |
| Cobblestone | Cobblestone Stairs | 1 |
| Bricks | Brick Slab | 2 |

These are examples from the bundled recipes, not a complete input or output list. [Stone Stairs][stone-stairs] · [Stone Slab][stone-slab] · [Stone Bricks][stone-bricks] · [Cobblestone Stairs][cobble-stairs] · [Brick Slab][brick-slab]

For Stone Stairs, the normal Crafting Table recipe spends **six Stone for four stairs**, while this checked stonecutting recipe gives **one stair per Stone**. Stonecutting also lets you make a single stair instead of the four-item crafting batch. Do not assume every material or shape has the same savings: check its actual output choice and count. [Crafted stairs][crafted-stairs] · [Cut stairs][stone-stairs]

An imported block is not automatically accepted. For example, see the Limestone family’s [acquisition and integration limits](Limestone.md); the bundled stonecutting recipe scan found no Limestone ingredient. Server data packs can add or change recipes.

## Placement and village use

Placement sets the front opposite the player's horizontal facing. The collision shape is a full-width base **9/16 block high**; the blade artwork does not add a damaging contact callback. There is no saw-damage routine in the active block class. [Shape and placement][block]

The Stonecutter is registered as the **Mason job-site block**. An eligible Villager can use that workstation through the village profession system, but placing one is not a promise that an already-employed or locked-in Villager changes job. Follow [Villager professions](../mobs/Villager.md) and [Trading](../trading/Trading.md) for the shared employment and trade rules. [Job-site registration][poi] · [Profession binding][profession]

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Checked registration, exact workstation/example recipes, all bundled stonecutting inputs for the Limestone claim, menu selection/consumption/cleanup, block shape and callbacks, mining loot, and Mason registration. No crafting, stonecutting, collection, Hopper, collision, or villager gameplay test was run.

Related: [Stonecutter item](../items/Stonecutter.md) · [Stone](Stone.md) · [Crafting](../crafting/Crafting.md) · [Blocks](Blocks.md)

[menu]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/inventory/StonecutterMenu.java
[recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/stonecutter.json
[blocks]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L5375-L5379
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/stonecutter.json
[return]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java#L589-L613
[block]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/StonecutterBlock.java
[stone-stairs]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/stonecutting/stone_stairs_from_stone_stonecutting.json
[stone-slab]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/stonecutting/stone_slab_from_stone_stonecutting.json
[stone-bricks]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/stonecutting/stone_bricks_from_stone_stonecutting.json
[cobble-stairs]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/stonecutting/cobblestone_stairs_from_cobblestone_stonecutting.json
[brick-slab]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/stonecutting/brick_slab_from_bricks_stonecutting.json
[crafted-stairs]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/stone_stairs.json
[poi]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiTypes.java#L130
[profession]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/npc/VillagerProfession.java#L119

[inventory-return]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/player/Inventory.java#L302-L323
[hopper]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L360-L397
