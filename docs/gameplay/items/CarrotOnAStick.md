# Carrot on a Stick

Carrot on a Stick (`minecraft:carrot_on_a_stick`) attracts and controls saddled [Pigs](../mobs/Pig.md). It is a durability-based tool, not pig breeding food.

## Crafting

Place a **Fishing Rod diagonally above-left of a Carrot** in the crafting grid to make one. The bundled item has **25 durability**.

## Riding and boosting

Hold it while you are the first passenger of a saddled pig to gain control. The pig follows your facing with a forward riding input. Merely holding the tool nearby attracts the pig but does not put you on it or provide a Saddle.

Use the item while controlling the pig to request a boost. A successful boost costs **7 durability** before applicable durability modifiers; attempting another while already boosting does not start a second boost. A fresh ordinary tool therefore reaches its broken state on its fourth successful boost if no durability-saving effect applies. **Do not expect a Fishing Rod back in this snapshot:** the conversion helper only replaces an empty stack, while the current durability path keeps a nonempty broken stack. Further use is rejected by the broken-item guard. This is a source-identified behavior difference, not a tested wear-out cycle.

The steering implementation selects a duration parameter from **140–980 ticks** and varies the speed multiplier over the boost, rather than applying one constant speed increase. Boost progression is updated by the ridden handler, so this is not a guarantee of a wall-clock duration when the pig is no longer being controlled.

This item only boosts the Pig entity type in its checked registration. A Warped Fungus on a Stick is a separate tool with a different target and durability cost.

## Related pages

- [Pig](../mobs/Pig.md)
- [Saddle](Saddle.md)
- [Carrot](Carrot.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game breeding, riding, equipment, loot, or food test was run. Data packs, components, and game rules can change the described behavior.

- [Recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/carrot_on_a_stick.json)
- [Durability, target, and boost cost](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java)
- [Boost use and conversion call](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/FoodOnAStickItem.java)
- [Actual boost duration and speed curve](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ItemBasedSteering.java)
- [Pig control and attraction](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Pig.java)

- [Retained broken stacks, use guard, and empty-stack conversion condition](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/ItemStack.java#L382-L526)
