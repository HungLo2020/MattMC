# Brush

The **Brush** extracts the contents of suspicious blocks and collects [Armadillo Scutes](ArmadilloScute.md) from adult [Armadillos](../mobs/Armadillo.md). Those uses have different durability costs and different broken-item checks in MattMC. Carry a serviceable Brush for archaeology, even if a fully worn one still appears to work on an animal. [Archaeology use][brush] · [Adult interaction][armadillo] · [Broken block-use guard][block-guard]

## Crafting and durability

Craft one Brush in a vertical column in a [Crafting Table](../blocks/CraftingTable.md):

1. [Feather](Feather.md) at the top
2. [Copper Ingot](CopperIngot.md) in the middle
3. [Stick](Stick.md) at the bottom

This is an active shaped recipe. The Brush is registered as `minecraft:brush`, has **64 maximum durability**, and appears in Creative. [Recipe][recipe] · [Registration][registration] · [Creative entry][creative]

Normal unmodified durability costs are:

- **1 durability** when a suspicious block finishes brushing, rather than one per animation stroke
- **16 durability** for a successful adult Armadillo brushing, whether used by a player or a Dispenser

Four adult interactions reach a fresh Brush's nominal durability limit, but that is **not a verified four-use cap in MattMC**. See the fully worn Brush section below. Creative use and durability-changing effects can also change wear. [Archaeology cost][brush] · [Animal cost][armadillo] · [Dispenser cost][dispenser] · [Durability handling][durability]

## Using it on an Armadillo

Interact with a living **adult** Armadillo while holding the Brush. Each successful call uses the bundled loot table to drop **one Armadillo Scute**. Babies are rejected. There is no animal-side cooldown, and the Brush check happens before the animal refuses ordinary interactions while scared, so a curled adult can still be brushed. [Adult interaction][armadillo] · [Brush loot][brush-loot]

A Dispenser with a Brush searches the **single block directly in front** for Armadillos and brushes the first one that passes the adult check. The brush remains in the Dispenser; the scute drops at the animal. If no adult qualifies, the behavior reports failure rather than firing the Brush out as an item. [Dispenser behavior][dispenser]

Scutes craft and repair [Wolf Armor](WolfArmor.md). Natural adult shedding is another way to obtain them without using Brush durability; see [Armadillo Scute](ArmadilloScute.md).

## Archaeology basics

Aim at **[Suspicious Sand](SuspiciousSand.md)** or **[Suspicious Gravel](SuspiciousGravel.md)** and hold the use control. These are registered brushable blocks. Ordinary Sand and Gravel can produce brushing effects, but lack the brushable block entity that reveals stored contents. [Block registration][sand] · [Gravel registration][gravel] · [Use behavior][brush]

The block requires **ten successful brush pulses**, with a ten-tick cooldown between them. The Brush attempts a pulse every ten use ticks, making uninterrupted completion roughly five seconds at normal tick speed. Keep aiming at the block: losing the block target stops use, and pausing for about **40 ticks (two seconds)** allows stored brushing progress to start decaying. These are source timings, not measured gameplay results. [Brush pulses][brush] · [Progress and cooldown][progress] · [Progress decay][decay]

On completion, the stored content is released and the suspicious block becomes its ordinary counterpart: Sand or Gravel. The item's identity depends on the block's assigned loot table or stored item. A Creative-placed suspicious block with no assigned contents is not a guaranteed treasure source. [Loot selection][loot] · [Completion and drops][completion] · [Block registration][sand] · [Gravel registration][gravel]

Keep suspicious blocks supported while excavating. Their falling behavior deliberately cancels normal landing/drop behavior, so removing the support can destroy the archaeology opportunity. Their normal block-loot tables also contain no item pools; breaking one is not a verified way to recover it or its hidden contents. Brush it in place instead. [Falling behavior][falling] · [Falling cancellation][falling-cancel] · [Sand block loot][sand-loot] · [Gravel block loot][gravel-loot]

## Fully worn Brushes

**MattMC retains a fully damaged Brush as a broken stack instead of removing it.** Once broken, its durability handler ignores further wear. The practical result depends on the interaction route:

- **Starting a new archaeology block-use is blocked:** the item-stack block-use method checks whether the Brush is broken before calling its use behavior
- **Adult Armadillo interaction remains reachable in source:** player interaction asks the entity to handle the action first. The Armadillo checks for the Brush item and adulthood, but not the Brush's broken state, before producing scute loot
- **Dispenser brushing also lacks a broken-Brush check** in both dispatch and its registered Armadillo-brushing behavior

These are source-identified inconsistencies, not a runtime-tested promise of unlimited brushing. They also explain why the generic item-use guard cannot be applied to every Brush interaction. Replace or repair a fully worn Brush before relying on it for regular work. [Broken-stack handling][durability] · [Block-use guard][block-guard] · [Player interaction order][player] · [Armadillo handler][armadillo] · [Dispenser dispatch][dispenser-dispatch] · [Dispenser behavior][dispenser]

## Repairing a Brush

The active generic crafting-repair recipe accepts **two Brushes** with durability data. It combines their remaining durability and adds **5% of maximum durability, rounded down**: three bonus durability for default 64-durability Brushes, capped at full durability. The two inputs become one repaired Brush. [Repair recipe][repair-recipe] · [Repair calculation][repair]

Crafting repair retains curses but does not preserve ordinary enchantments. Check your enchanted equipment before combining it in a crafting grid; use the [anvil guide](../mechanics/AnvilMechanics.md) for that separate workflow. Copper is a Brush crafting ingredient, but the registered Brush has no copper repair-ingredient component. [Repair enchantment handling][repair] · [Brush registration][registration]

## Related pages

- [Armadillo](../mobs/Armadillo.md)
- [Armadillo Scute](ArmadilloScute.md)
- [Wolf Armor](WolfArmor.md)
- [Suspicious Sand](SuspiciousSand.md)
- [Suspicious Gravel](SuspiciousGravel.md)
- [Items](Items.md)

## Verification scope

Source-reviewed at MattMC commit `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. Recipe, durability, player/mob/dispenser interaction paths, broken-item checks, brushable-block progress, loot handling, and crafting repair were inspected. No in-game crafting, archaeology, dispenser, repair, or broken-Brush test was run.

[brush]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/BrushItem.java#L37-L98
[armadillo]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/armadillo/Armadillo.java#L299-L322
[block-guard]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L370
[recipe]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/brush.json
[registration]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L2527
[creative]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1461
[dispenser]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L379-L400
[durability]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/ItemStack.java#L449-L485
[brush-loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/brush/armadillo.json
[sand]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/Blocks.java#L318-L327
[gravel]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/Blocks.java#L338-L347
[progress]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/BrushableBlockEntity.java#L60-L87
[decay]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/BrushableBlockEntity.java#L149-L169
[loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/BrushableBlockEntity.java#L89-L115
[completion]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/BrushableBlockEntity.java#L117-L147
[falling]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/BrushableBlock.java#L82-L99
[falling-cancel]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/item/FallingBlockEntity.java#L311-L313
[sand-loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/suspicious_sand.json
[gravel-loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/suspicious_gravel.json
[player]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/player/Player.java#L854-L888
[dispenser-dispatch]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/DispenserBlock.java#L82-L112
[repair-recipe]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/repair_item.json
[repair]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/crafting/RepairItemRecipe.java#L41-L80
