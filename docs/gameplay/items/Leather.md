# Leather

**Leather** is a crafting material for [Bundles](Bundle.md), [Books](Book.md), [Item Frames](ItemFrame.md), [Saddles](Saddle.md) and leather equipment. It is **not food**. A Cow farm or conversion from Rabbit Hides provides a repeatable supply route. [Registration][leather-item] · [Cow drop][cow-loot] · [Hide recipe][leather-craft]

## Obtaining

- **Adult [Cows](../mobs/Cow.md)** drop **0–2 Leather before Looting**, with mob loot enabled. A kill can therefore give none; calves do not use the normal death-loot route. Looting can add to the Leather count. Follow the Cow guide for finding, feeding and breeding them · [Leather pool][cow-loot] · [Adult and gamerule gate][loot-gate] · [Death dispatch][loot-dispatch]
- Place **four [Rabbit Hides](RabbitHide.md) in a filled 2 × 2 square** to craft **one Leather**. This fits the personal crafting grid. Adult [Rabbits](../mobs/Rabbit.md#drops-and-looting) have a base **0–1 Hide** drop before Looting, so four Hides does not mean exactly four Rabbits · [Recipe][leather-craft] · [Personal grid][personal-grid] · [Hide pool][rabbit-loot]
- **[Piglin bartering](../mobs/Piglin.md#bartering)** can return **2–4 Leather** for a completed Gold Ingot exchange. Leather is one possible reward; it cannot be selected or guaranteed from a single payment · [Reward entry][barter-leather] · [Adult response][barter-response] · [Reward lookup][barter-loot-call]

These are selected practical sources, not a complete list of every Leather-bearing creature or chest.

## Usage

| Make | Ingredients and layout | Output |
| --- | --- | --- |
| [Bundle](Bundle.md) | 1 String directly above 1 Leather | 1 empty Bundle · [Recipe][bundle] |
| [Book](Book.md) | 3 Paper + 1 Leather, in any arrangement | 1 Book · [Recipe][book] |
| [Item Frame](ItemFrame.md) | 8 Sticks surrounding 1 Leather | 1 Item Frame · [Recipe][frame] |
| [Saddle](Saddle.md) | 1 Leather above an Iron Ingot, with 1 Leather on each side of that ingot | 1 Saddle · [Recipe][saddle] |

A first Leather is enough for a Bundle when you have String, or a Book when you have three Paper. Save **three Leather and one Iron Ingot** for the Saddle recipe. The linked finished-item guides explain storage controls, enchanting uses, displays and riding; holding Leather does not perform those actions.

## Behavior

Ordinary Leather stacks to **64**. Its plain item registration has no food or consumable component, so it cannot be eaten to restore hunger. [Registration][leather-item] · [Plain-item helper][item-helper] · [Default components][stack-default] · [Use dispatch][item-use]

## Notes

- This item is registered as `minecraft:leather`
- Rabbit Hide is a separate item; use the four-Hide recipe to convert it before a recipe requiring Leather

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked item registration, ordinary crafting recipes, Cow/Rabbit death-loot gating and Piglin reward dispatch. No in-game farm, drop, crafting or barter test was run. The normal entity loot route resolves the type's table through the server's loaded loot registry; data packs and server rules can change results. [Default table naming][default-loot] · [Entity lookup][entity-loot] · [Runtime loot lookup][loot-load]

[leather-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1530-L1530
[cow-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/entities/cow.json#L1-L33
[leather-craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/leather.json#L1-L15
[loot-gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L567
[loot-dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1476
[personal-grid]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/InventoryMenu.java#L48-L53
[rabbit-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/entities/rabbit.json#L1-L33
[barter-leather]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/gameplay/piglin_bartering.json#L163-L179
[barter-response]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L373-L399
[barter-loot-call]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L440-L446
[bundle]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/bundle.json#L1-L16
[book]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/book.json#L1-L14
[frame]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/item_frame.json#L1-L17
[saddle]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/saddle.json#L1-L16
[item-helper]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2792-L2798
[stack-default]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
[item-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L173-L197
[default-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[entity-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/Entity.java#L3922-L3924
[loot-load]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1502-L1526
