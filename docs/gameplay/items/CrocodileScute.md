# Crocodile Scute

**Crocodile Scute** (`minecraft:crocodile_scute`) is a registered **plain item**, not a placeable block. Its default registration has no food, equipment, special-use, or block-placement behavior. The Ingredients Creative category explicitly lists it. [Registration][item] · [Creative entry][creative]

## Availability

Creative access and command-given items are confirmed. No crafting recipe, entity-loot entry, or other ordinary Survival award for this item was found in the reviewed bundled source and data.

The [Crocodile](../mobs/Crocodile.md) currently drops **Turtle Scute** when an existing baby matures with mob loot enabled. That active placeholder does not award Crocodile Scute despite the nearby source comment naming the intended item. Do not breed or raise Crocodiles expecting this separate resource. [Growth callback][croc] · [Age-boundary dispatch][age]

## Uses

No active crafting, brewing, equipment-repair, or special interaction use for Crocodile Scute was established in the checked recipes and Java references. Registration and a Creative entry alone do not prove an armor recipe or a substitute for another scute type. In particular, [Wolf Armor](WolfArmor.md) uses its own Armadillo Scute repair tag and recipe.

## Sources and verification

Source-reviewed on 2026-10-02 at `3e85592c4c78ebb420302360667a6c230dc0318d`. No gameplay test of acquisition or use was run. Data packs can add recipes or rewards, so these limits describe bundled data.

Related: [Crocodile](../mobs/Crocodile.md) · [Turtle Scute](TurtleScute.md) · [Armadillo Scute](ArmadilloScute.md) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L1284
[creative]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1835
[croc]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityCrocodile.java#L123-L130
[age]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/AgeableMob.java#L97-L104
