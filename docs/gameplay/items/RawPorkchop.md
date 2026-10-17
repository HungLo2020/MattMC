# Raw Porkchop

Raw Porkchop (`minecraft:porkchop`) is food. Eating one restores **3 hunger points and 1.8 saturation**, subject to normal hunger/saturation limits. Two hunger points equal one drumstick icon.

## Obtaining

Adult [Pigs](../mobs/Pig.md) have a base drop of 1–3 Raw Porkchops, with a Looting count increase. Their bundled loot converts the meat to cooked form when the burning or Fire Aspect smelting condition applies. Babies do not pass the ordinary loot gate, and the mob-loot game rule must allow the drops.

[Hoglins](../mobs/Hoglin.md#drops) provide another source, with their own drop counts and adult-only ordinary item-loot gate.

## Cooking

One Raw Porkchop becomes one Cooked Porkchop using any of these bundled recipes:

| Method | Recipe time |
| --- | --- |
| Furnace | 200 ticks, about 10 seconds at normal tick speed |
| Smoker | 100 ticks, about 5 seconds |
| Campfire | 600 ticks, about 30 seconds |

The recipes use raw meat as input; cooked meat is not a replacement ingredient. Cooking improves the food value from 3 hunger/1.8 saturation to 8 hunger/12.8 saturation. Neither checked food registration attaches a special effect.

## Related pages

- [Pig](../mobs/Pig.md)
- [Raw Porkchop](RawPorkchop.md)
- [Cooked Porkchop](CookedPorkchop.md)
- [Hunger and healing](../mechanics/Hunger.md)
- [Smelting](../smelting/Smelting.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game breeding, riding, equipment, loot, or food test was run. Data packs, components, and game rules can change the described behavior.

- [Food values](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/food/Foods.java)
- [Item registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java)
- [Pig loot and cooking conditions](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities/pig.json)
- [Fire Aspect smelting tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/enchantment/smelts_loot.json)
- [Furnace recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/smelting/cooked_porkchop.json)
- [Smoker recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/smoking/cooked_porkchop_from_smoking.json)
- [Campfire recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/campfire_cooking/cooked_porkchop_from_campfire_cooking.json)
- [Baby and mob-loot gate](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/LivingEntity.java)
