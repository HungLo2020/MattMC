# Bone

Bone is a material registered as `minecraft:bone`. It can be converted to Bone Meal or used in the current wolf-taming interaction.

## Obtaining

The [Skeleton](../mobs/Skeleton.md) loot table defines **0–2 Bones** before its Looting bonus. This is one verified acquisition route, not an exhaustive list of all fishing, structure, or mob loot.

## Uses

One Bone in a shapeless crafting recipe produces **three Bone Meal**. Bone Meal has plant-specific behavior; on [Wheat](../blocks/Wheat.md), it advances crop age rather than guaranteeing that every application finishes growth.

An untamed, non-angry Wolf accepts Bone for a taming attempt. The attempt consumes one through the normal item-consumption path and uses a **one-in-three** success check. Success assigns ownership, clears navigation/target, and orders the wolf to sit. A failed attempt does not establish a fixed number of remaining bones needed.

The Bone item is not interchangeable with the integrated [Thin Bone](ThinBone.md) merely because both names contain “bone.” The checked crafting recipe and taming condition name ordinary Bone specifically.

## Related pages

- [Skeleton](../mobs/Skeleton.md)
- [Bone Meal](BoneMeal.md)
- [Wolf](../mobs/Wolf.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No in-game combat, crafting, brewing, or taming test was run.

- [Skeleton loot](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/entities/skeleton.json)
- [Bone Meal recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/bone_meal.json)
- [Wolf interaction](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/animal/wolf/Wolf.java#L505-L523)
- [Item registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java)
