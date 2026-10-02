# Suspicious Stew

**Suspicious Stew** (`minecraft:suspicious_stew`) is an unstackable food whose stored flower effect depends on how that serving was made. The normal item registration starts with an empty effect list; the checked recipes attach the actual effect. [Stew item registration] · [Stew dandelion]

## Obtaining

For the flower recipes in this guide, combine **one Bowl, one Brown Mushroom, one Red Mushroom, and one eligible small flower** in any arrangement. The result is **one Suspicious Stew**. All four ingredients fit the inventory’s two-by-two crafting grid. The [flower variant table](../blocks/Flowers.md#small-flower-variants) links all thirteen ordinary-flower recipes and lists their exact effects and durations. Each recipe names its particular flower; Sunflower, Lilac, Rose Bush, and Peony do not substitute for it. [Stew dandelion] · [Shapeless recipe execution] · [Ingredient consumption]

A **[brown Mooshroom](../mobs/Mooshroom.md#bowls-milk-and-flower-servings)** provides another checked route. Feed it a flower with suspicious-stew effects when it has none stored, then use a Bowl on the adult animal. That next bowl receives its stored effect and clears the stored serving. A second flower while an effect is already stored does not replace it. Without a stored effect, the Bowl interaction produces ordinary Mushroom Stew. This describes the interaction, not a natural spawning or trading route. [Brown Mooshroom flower feeding] · [Mooshroom bowl interaction] · [Mooshroom effect lookup] · [Flower effect lookup]

## Usage

Eating a serving supplies **6 hunger points and 7.2 saturation points before caps**, plus its stored effect. It can be eaten at full hunger, takes the default **32 use ticks** (about 1.6 seconds at 20 TPS), and normally leaves **one Bowl** in Survival. [Hunger, saturation, and healing](../mechanics/Hunger.md) explains the shared food limits and healing system. [Stew item registration] · [Stew food value] · [Stew saturation modifier] · [Food saturation calculation] · [Food consumption] · [Food caps] · [Eating and effect listeners] · [Default food component] · [Default eating duration] · [Use remainder dispatch] · [Bowl replacement]

## Behavior

The [flower effect table](../blocks/Flowers.md#small-flower-variants) is the practical way to choose a serving: Dandelion and Blue Orchid use Saturation, Poppy uses Night Vision, and other flowers include both beneficial and harmful choices. **Wither Rose stew applies Wither; Lily of the Valley stew applies Poison.** Eating applies the stored effect at level I, subject to ordinary effect handling. The listed duration is not a promise of a fixed amount of healing, hunger gain from the effect, or damage. [Stew effect application] · [Stew wither_rose] · [Stew lily_of_the_valley]

The component’s effect tooltip is shown through its **Creative tooltip path**. Keep track of which flower you used instead of expecting the ordinary food tooltip to identify an unfamiliar serving. [Stew effect application]

## Notes

This page checks the thirteen ordinary small-flower recipes and the brown-Mooshroom interaction. Torchflower/Eyeblossom recipes, structure loot, and trades are outside this bounded recipe comparison. The complete planted-flower mechanics belong to [Small and tall flowers](../blocks/Flowers.md).

## Sources and verification

Source-reviewed on **2026-10-02** at `3c39e8cc456e3c76eac84ae45b12020c4b08a7a7`. Checked the item and food components, thirteen exact flower recipes and their component outputs, active consumption/effect/remainder handling, and the brown-Mooshroom flower and Bowl callbacks. No shared hunger or mob guide is replaced. No in-game placement, harvesting, crafting, propagation, feeding, or world-generation test was run. Data packs can change tags, recipes, loot, and biome features.

[Shapeless recipe execution]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java#L58-L91
[Ingredient consumption]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L81-L98
[Stew effect application]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/component/SuspiciousStewEffects.java#L38-L71
[Flower effect lookup]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/SuspiciousEffectHolder.java#L23-L29
[Stew dandelion]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_dandelion.json
[Stew lily_of_the_valley]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_lily_of_the_valley.json
[Stew wither_rose]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_wither_rose.json
[Stew item registration]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/Items.java#L2378-L2385
[Stew food value]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/food/Foods.java#L41
[Stew saturation modifier]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/food/Foods.java#L59-L61
[Food saturation calculation]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/food/FoodProperties.java#L60-L84
[Food consumption]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/food/FoodProperties.java#L39-L56
[Food caps]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/food/FoodData.java#L14-L29
[Eating and effect listeners]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/component/Consumable.java#L77-L101
[Default food component]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/Item.java#L366-L375
[Default eating duration]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/component/Consumable.java#L132-L137
[Use remainder dispatch]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/ItemStack.java#L399-L412
[Bowl replacement]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/component/UseRemainder.java#L11-L27
[Brown Mooshroom flower feeding]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/entity/animal/MushroomCow.java#L127-L169
[Mooshroom bowl interaction]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/entity/animal/MushroomCow.java#L93-L110
[Mooshroom effect lookup]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/entity/animal/MushroomCow.java#L205-L207
