# Serene Salad

Serene Salad is food registered as `minecraft:serene_salad`. It also enables temporary mounting when used on an adult [Atlatitan](../mobs/Atlatitan.md). It is not a general dinosaur-taming item in the current MattMC implementation.

## Obtaining

Serene Salad is explicitly listed in Creative food items and can be given by its ID with command permission. It **stacks to one**.

**A Survival supply is not established.** No Serene Salad recipe or loot entry was found in the checked bundled data, and the reviewed Java uses do not establish a production route. Its Creative listing does not establish a craftable recipe.

## Eating

Eating one restores **5 hunger points** (2.5 drumstick icons) and adds **3.5 saturation**, subject to the normal cap at current hunger. It is ordinary food, usable when hungry, with no special status effect attached in its checked registration.

The current item registration also does not configure a returned Bowl. See [Hunger and healing](../mechanics/Hunger.md) for the difference between hunger and saturation.

## Using with dinosaurs

- **Atlatitan:** using salad on an adult directly sets a **12,000-game-tick mounting window** and consumes the salad outside Creative. This active interaction does not require the separate feeding hook to work. It grants no ownership, and the timer is not saved. See [Atlatitan riding](../mobs/Atlatitan.md#feeding-and-temporary-riding) for mounting, expiry, and control limitations.
- **Vallumraptor:** a salad-taming method exists for a relaxed wild animal, but the checked normal interaction and item registration do not call it. The preceding stolen-nugget relaxation sequence also conflicts with the bundled theft tags. See [Vallumraptor](../mobs/Vallumraptor.md) rather than assuming an upstream taming recipe works here.

Using salad on an Atlatitan is separate from eating it yourself. It does not serve as that animal's normal breeding or baby-growth food; those checks use [Pine Nuts](PineNuts.md).

## Related pages

- [Atlatitan](../mobs/Atlatitan.md)
- [Vallumraptor](../mobs/Vallumraptor.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game eating, feeding, mounting, taming, or acquisition test was run.

- [Item registration and stack limit](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L1686) and [Creative listing](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1749)
- [Food values](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/food/Foods.java#L55), [saturation calculation](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32), and [food application](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/food/FoodData.java)
- [Default food component and explicit remainder option](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Item.java#L366-L377) and [default consumable](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/component/Consumables.java)
- [Active Atlatitan salad interaction](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/living/AtlatitanEntity.java#L234-L249), [timer and food handling](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/living/AtlatitanEntity.java#L183-L217), and [Vallumraptor hook and item selection](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/living/VallumraptorEntity.java)
- [Shared dinosaur interaction](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/living/DinosaurEntity.java), [bundled recipes](https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe), and [loot tables](https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table)
