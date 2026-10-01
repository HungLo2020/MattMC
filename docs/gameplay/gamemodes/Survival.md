# Survival

Survival is MattMC's default game mode. It grants ordinary building ability but does not grant Creative instant-build, flight, or invulnerable abilities. Resources, tools, food, and safe movement matter.

## A practical start

1. Collect materials that match current recipe tags. Familiar integrated materials can have missing tag wiring; ordinary oak planks are in the bundled planks tag.
2. Make a [Crafting Table](../blocks/CraftingTable.md), then a [Furnace](../blocks/Furnace.md) and [Chest](../blocks/Chest.md).
3. Establish a food source. [Cows](../mobs/Cow.md) and the [cooking guides](../smelting/Smelting.md) are useful source-reviewed examples.
4. Keep food available before exploring. [Hunger and saturation](../mechanics/Hunger.md) affect sprinting and natural healing.

This is an orientation, not a promise that every vanilla or imported progression route has been verified in MattMC.

## Difficulty and rules still matter

Survival is not a difficulty setting. Starvation behavior depends on difficulty, and natural regeneration depends on its game rule. Server permissions, protected areas, and enabled content can restrict otherwise ordinary building or interaction.

Creative availability also is not proof of Survival availability. Several integrated mobs and blocks have explicit acquisition or wiring limitations documented in the [content guide](../ContentGuide.md).

## Related pages

- [All game modes](Gamemodes.md)
- [Crafting](../crafting/Crafting.md)
- [Hunger and food](../mechanics/Hunger.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. Rules below describe checked code, not a running-world test or every server override.

- [Mode abilities](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/GameType.java)
- [Server interactions](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java)
- [Player restrictions](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/player/Player.java)
- [Mode command](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/server/commands/GameModeCommand.java)
- [Food/difficulty rules](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/food/FoodData.java)
