# Arrow

Arrow is standard projectile ammunition registered as `minecraft:arrow`. The weapon system recognizes the arrows item tag; this page focuses on the ordinary arrow rather than every tipped or spectral variant.

## Crafting and drops

Place **Flint above a Stick above an ordinary Feather** in a vertical three-cell recipe to craft **four Arrows**. A 3 × 3 Crafting Table grid can fit this recipe.

The recipe explicitly names the ordinary Feather item. [Roadrunner Feather](RoadrunnerFeather.md) is a separate registered material and is not an established substitute.

The [Skeleton](../mobs/Skeleton.md) loot table also defines **0–2 Arrows** before its Looting count bonus. This is distinct from rules governing pickup of arrows already fired into the world.

## Use and limits

Ordinary arrows belong to the ammunition group used by bows and crossbows. Damage, enchantment effects, ammunition consumption, and fired-arrow pickup have additional weapon/projectile rules; this article does not infer a universal damage number from the item alone.

For a redstone use, an arrow can hold an [Oak Button](../blocks/Buttons.md#arrow-detail) active while it remains inside the button's checked shape. Stone Buttons do not enable that arrow test.

## Related pages

- [Skeleton](../mobs/Skeleton.md)
- [Bow](Bow.md), [Crossbow](Crossbow.md)
- [Bow enchantments](../enchanting/BowEnchantments.md): Power, Punch, Flame and Infinity
- [Items](Items.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No in-game combat, crafting, brewing, or taming test was run.

- [Arrow recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/arrow.json)
- [Skeleton loot](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/entities/skeleton.json)
- [Ammunition predicate](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/ProjectileWeaponItem.java)
- [Arrow item tag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/item/arrows.json)
- [Button arrow check](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/ButtonBlock.java)
