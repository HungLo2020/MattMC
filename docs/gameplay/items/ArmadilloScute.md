# Armadillo Scute

**Armadillo Scute** is a renewable material from living adult [Armadillos](../mobs/Armadillo.md). Use six to craft [Wolf Armor](WolfArmor.md), and keep extras for repairing armor on a sitting wolf. Brushing and natural shedding are the confirmed acquisition routes; the Armadillo death-loot table has no item pools. [Brush loot][brush-loot] · [Shed loot][shed-loot] · [Death loot][death-loot] · [Armor recipe][recipe]

## At a glance

- **Item ID:** `minecraft:armadillo_scute`
- **Maximum stack:** 64
- **Creative category:** Ingredients
- **Main uses:** crafting and repairing Wolf Armor

The item is registered without a special stack-size override and uses the default 64-item stack. [Registration][item] · [Default stack size][stack] · [Creative entry][creative]

## Brushing adults

Interact with an adult Armadillo using a [Brush](Brush.md). A successful interaction calls the bundled brush loot table, which produces **one scute**. Babies fail this brushing check. The method has no animal-side brushing cooldown, and it is evaluated before the refusal for a scared animal, so rolling up does not itself prevent an adult from being brushed. [Adult check and interaction][brushing] · [Loot][brush-loot]

Each success attempts to use **16 Brush durability**. The Brush has 64 maximum durability, but MattMC retains broken item stacks and the Armadillo interaction does not reject them. Do not present four scutes as a hard per-Brush limit; see the [fully worn Brush warning](Brush.md#fully-worn-brushes) for the checked interaction paths. [Brush registration][brush-item] · [Brushing][brushing] · [Broken-stack handling][durability]

A Dispenser containing a Brush can also call this adult-brushing behavior for an Armadillo in the block directly in front. It attempts the same 16-durability cost and leaves the scute drop at the animal; it is not an automatic insertion into the Dispenser inventory. Keep the collection area accessible. [Dispenser behavior][dispenser]

## Natural shedding

A living adult sheds one scute when its timer expires, then resets the timer to **6,000–11,999 ticks**. At normal 20 TPS, that is about **five to ten minutes while the animal is ticking**. The timer is saved with the animal, and babies do not advance this adult shedding branch. Unloaded time does not count down. [Timer][shedding] · [Shed loot][shed-loot] · [Saved timer][saving]

The brushing and shedding routes use their own interaction/gift loot helpers. They are separate from the empty death-loot table. Custom data packs can change those reward tables, so the one-scute amount describes the bundled data. [Loot helpers][helpers] · [Death loot][death-loot]

## Crafting and repair

**Crafting:** six scutes produce one Wolf Armor in a Crafting Table. Place one scute in the top-left slot, three across the middle row, and one in each bottom corner. [Recipe][recipe]

**Repair on a wolf:** the owner can use one scute on their sitting, armored wolf to restore **8 armor durability**, capped at the armor's maximum of 64. The armor must be damaged. The bundled repair-ingredient tag contains only Armadillo Scute, so Turtle Scute and Crocodile Scute are not substitutes for this interaction. [Repair tag][repair-tag] · [Repair conditions][repair]

This repair restores the equipment, not the wolf's health. Use an accepted wolf food for injuries; [Rotten Flesh](RottenFlesh.md), for example, heals eight health points through the Wolf feeding interaction. See [Wolf Armor](WolfArmor.md) for equipping, removal, damage exceptions, and its fully damaged-item warning. [Wolf healing][healing]

## Related pages

- [Armadillo](../mobs/Armadillo.md)
- [Brush](Brush.md)
- [Wolf Armor](WolfArmor.md)
- [Wolf](../mobs/Wolf.md)
- [Items](Items.md)

## Verification scope

Source-reviewed at MattMC commit `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. Item registration, adult checks, both reward tables, timer persistence, dispenser brushing, recipe, and repair tag were inspected. No in-game brushing, shedding, dispenser, crafting, or repair test was run.

[brush-loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/brush/armadillo.json
[shed-loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/gameplay/armadillo_shed.json
[death-loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities/armadillo.json
[recipe]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/wolf_armor.json
[item]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L1283
[stack]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/core/component/DataComponents.java#L384
[creative]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1834
[brushing]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/armadillo/Armadillo.java#L299-L322
[brush-item]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L2527
[durability]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/ItemStack.java#L449-L485
[dispenser]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L379-L400
[shedding]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/armadillo/Armadillo.java#L139-L153
[saving]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/armadillo/Armadillo.java#L244-L255
[helpers]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1530-L1580
[repair-tag]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/repairs_wolf_armor.json
[repair]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/wolf/Wolf.java#L474-L485
[healing]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/wolf/Wolf.java#L458-L464
