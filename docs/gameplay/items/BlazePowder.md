# Blaze Powder

Blaze Powder is a brewing fuel and ingredient registered as `minecraft:blaze_powder`. Its role depends on where it is used in a brewing stand.

## Crafting

One Blaze Rod in a shapeless crafting recipe produces **two Blaze Powder**. The Blaze loot table supplies a possible rod only when its player-kill condition is met, with a base 0–1 count and Looting bonus.

## Brewing roles

- In the stand's **fuel slot**, a Blaze Powder loads **20 brew-operation charges** under the bundled brewing-fuel tag. One charge is spent when a brewing operation starts, not once per bottle.
- As the **ingredient**, Blaze Powder transforms Awkward Potion into **Strength** through the current mix registry.

Do not confuse fuel with the top ingredient slot. A powder spent as an ingredient is separate from the stand's fuel supply. See [Brewing Stand](../blocks/BrewingStand.md) for timing, bottle capacity, and interrupted-brew behavior.

## Other verified recipe

One Ender Pearl plus one Blaze Powder in a shapeless recipe crafts **one Eye of Ender**. This is a selected crafting use, not an exhaustive recipe list.

## Related pages

- [Brewing guide](../brewing/Brewing.md)
- [Blaze Rod](BlazeRod.md)
- [Eye of Ender](EyeOfEnder.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No in-game brewing, growth, or collection test was run.

- [Powder crafting](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/blaze_powder.json)
- [Blaze rod loot](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/entities/blaze.json)
- [Brewing fuel tag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/item/brewing_fuel.json)
- [Fuel charges](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/entity/BrewingStandBlockEntity.java#L95-L121)
- [Strength mix](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java)
- [Eye recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/ender_eye.json)
