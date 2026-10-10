# Raw Cod

Raw Cod is a food item registered as `minecraft:cod`. It can be eaten, cooked, or saved for [animal care](#animal-uses-raw-or-cooked), including [Grizzly Bears](../mobs/GrizzlyBear.md).

## Obtaining

The [Cod](../mobs/Cod.md) loot table provides one cod item. It is converted to cooked cod when the fish is on fire or its direct attacker's held item meets the smelts-loot enchantment condition. This page covers that verified source, rather than claiming a complete list of fishing, trading, and chest-loot routes.

Raw Cod is also listed in the Creative food inventory.

## Eating and cooking

Eating Raw Cod supplies **2 hunger points** (one hunger icon) and **0.4 saturation** before the player's food caps. Cooking it improves both values; see [Cooked Cod](CookedCod.md) for furnace, smoker, and campfire recipes.

## Animal uses: raw or cooked

**Keep some Raw Cod or [Raw Salmon](RawSalmon.md) for Cats and Ocelots before cooking the rest.** Both animals accept only those two raw fish in the bundled food tags. Cooking improves player food and tame-Wolf healing; Dolphins and Grizzly Bears accept either form. [Cat food][care-cat-food] · [Ocelot food][care-ocelot-food] · [Wolf food][care-wolf-food] · [Fish tag][care-fishes]

| Animal | Raw Cod or Raw Salmon | Cooked Cod or Cooked Salmon |
| --- | --- | --- |
| [Cat](../mobs/Cat.md#taming-and-care) | Taming, owner healing, and breeding eligible tame adults | Not accepted |
| [Ocelot](../mobs/Ocelot.md#building-trust) | Building trust; breeding and baby growth through ordinary feeding | Not accepted |
| [Wolf](../mobs/Wolf.md#healing-and-food) | Healing tame Wolves and feeding for breeding | Same uses, with more healing per fish |
| [Dolphin](../mobs/Dolphin.md#feeding-and-finding-structures) | Structure guidance for adults; faster growth for babies | Same uses |

For an injured Cat, its owner's healing feed takes priority over breeding. **Ocelot trust gives no ownership and does not turn it into a Cat**; follow its guide for the trust-first feeding sequence. [Cat interaction][care-cat] · [Tame-Cat mating][care-cat-mating] · [Ocelot interaction][care-ocelot] · [Shared animal feeding][care-animal]

A tame Wolf heals **4 health points** from either raw fish, **10 from Cooked Cod**, or **12 from Cooked Salmon**, capped at its maximum health. Healing takes priority over breeding, and **fish do not tame wild Wolves**. See [Wolf breeding](../mobs/Wolf.md#breeding-and-puppies) for the adult and sitting requirements. Feeding a Dolphin is **neither taming nor breeding**; its guide explains the limits on structure guidance. [Wolf interaction][care-wolf] · [Food values][care-foods] · [Fish item bindings][care-items] · [Dolphin feeding][care-dolphin] · [Dolphin goals][care-dolphin-goals]

## Grizzly Bear use

Raw and cooked cod and salmon belong to MattMC's fish tag. Grizzlies are attracted by this tag, and **dropped fish** are used in their taming sequence. Follow the [Grizzly Bear guide](../mobs/GrizzlyBear.md#taming-and-care) for the pickup timing and care instructions. [Fish tag][care-fishes] · [Grizzly goals][care-grizzly-goals] · [Eating][care-grizzly-eating] · [Pickup][care-grizzly-pickup]

Tamed bears also accept these fish as breeding food; breeding produces a **Grizzly Bear cub**. See [Grizzly breeding and drops](../mobs/GrizzlyBear.md#breeding-and-drops) for the cub's care. [Breeding food][care-grizzly-food] · [Offspring][care-grizzly-offspring]

These are checked animal uses, not a complete catalogue of every interaction with cod or salmon.

## Related pages

- [Cooked Cod](CookedCod.md) and [Cooked Salmon](CookedSalmon.md)
- [Cod mob](../mobs/Cod.md)
- [Items](Items.md)

## Sources and verification

Reviewed against source snapshot `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. These are source-defined rules, not an in-game test.

Animal-use comparison and Grizzly offspring behavior rechecked on **2026-10-10** at `f86206767dadde696adfed4e04c5ee97cd0d0885`. The earlier warning about incorrect Grizzly offspring belongs to the 2026-10-01 review and is superseded by this source check. Acquisition and player-food prose retain their original review above. No in-game feeding, taming, healing, guidance, or breeding test was run; data packs can change the food tags and item components.

- [Cod drops](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/entities/cod.json)
- [Food registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/food/Foods.java#L14-L17)
- [Saturation formula](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/food/FoodConstants.java)
- [Fish tag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/item/fishes.json)
- [Grizzly interactions](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/alexsmobs/entity/EntityGrizzlyBear.java)
- [Creative foods](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java)

[care-cat-food]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/resources/data/minecraft/tags/item/cat_food.json
[care-ocelot-food]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/resources/data/minecraft/tags/item/ocelot_food.json
[care-wolf-food]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/resources/data/minecraft/tags/item/wolf_food.json
[care-fishes]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/resources/data/minecraft/tags/item/fishes.json
[care-cat]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/entity/animal/Cat.java#L372-L430
[care-cat-mating]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/entity/animal/Cat.java#L353-L359
[care-ocelot]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/entity/animal/Ocelot.java#L158-L178
[care-animal]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/entity/animal/Animal.java#L134-L157
[care-wolf]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/entity/animal/wolf/Wolf.java#L454-L512
[care-foods]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/food/Foods.java#L14-L39
[care-items]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/item/Items.java#L1668-L1675
[care-dolphin]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/entity/animal/Dolphin.java#L281-L301
[care-dolphin-goals]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/entity/animal/Dolphin.java#L148-L162
[care-grizzly-goals]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/alexsmobs/entity/EntityGrizzlyBear.java#L185-L209
[care-grizzly-eating]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/alexsmobs/entity/EntityGrizzlyBear.java#L403-L446
[care-grizzly-pickup]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/alexsmobs/entity/EntityGrizzlyBear.java#L697-L714
[care-grizzly-food]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/alexsmobs/entity/EntityGrizzlyBear.java#L236-L239
[care-grizzly-offspring]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/alexsmobs/entity/EntityGrizzlyBear.java#L646-L650
