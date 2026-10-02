# Snowball

A **Snowball** (`minecraft:snowball`) is a throwable item and Snow Block ingredient. It stacks to **16** and does not place a Snow layer. [Registration][items] · [Use behavior][snowball-item]

## Obtaining

Harvest [Snow layers or Snow Blocks with an unbroken shovel](../blocks/Snow.md#collecting-and-crafting-snow) without Silk Touch. A layer stack gives its layer count in Snowballs; a full Snow Block gives four. A [Snow Golem](../mobs/SnowGolem.md#snow-trail) can supply renewable layers under its documented conditions. [Harvest loot][loot-snow] [loot-snow-block]

## Usage

Use a Snowball to throw it, consuming one in ordinary Survival. The registered projectile applies **3 health points of base thrown damage to a Blaze** and **0 to other entity types**; ordinary target damage rules still apply. The projectile is discarded on impact rather than dropping a recoverable Snowball item. [Item use][snowball-item] · [Projectile registration and impact][entity-types] [snowball-entity]

For building, follow the [Snow Block recipe](../blocks/Snow.md#collecting-and-crafting-snow). To place shallow snow directly, use the separate [Snow item](Snow.md).

## Related pages

- [Snow and Powder Snow](../blocks/Snow.md), [Snow Block](SnowBlock.md), and [Snow Golem](../mobs/SnowGolem.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `e87cde38c872d30ae86139bbee181937603af769` on 2026-10-02. Registration, source loot, consumption and projectile impact were checked. No gameplay throwing or harvesting test was run.

[items]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/Items.java
[snowball-item]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/SnowballItem.java
[loot-snow]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/snow.json
[loot-snow-block]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/snow_block.json
[entity-types]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/entity/EntityType.java
[snowball-entity]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/entity/projectile/Snowball.java
