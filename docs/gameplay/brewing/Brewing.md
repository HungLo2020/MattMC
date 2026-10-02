# Brewing

Brewing combines a bottled potion with an ingredient to make a new potion. Start with a [Brewing Stand](../blocks/BrewingStand.md), **Blaze Powder for fuel**, and Water Bottles. The stand page covers crafting, slots, fuel capacity, and batch timing.

## A first batch

1. Use empty [Glass Bottles](../items/GlassBottle.md) on a water source to fill them
2. Put one to three Water Bottles in the stand's lower slots and Blaze Powder in its fuel slot
3. Add **Nether Wart** to the ingredient slot and let the operation finish; this makes **Awkward Potions**
4. Replace the ingredient with one from the table below and let that operation finish

Each row starts with **Awkward Potion**, not Water Bottle. These are source-verified examples, not a complete recipe catalog.

| Add to Awkward Potion | Result | Base drinkable effect |
| --- | --- | --- |
| Sugar | Potion of Swiftness | Speed for 3 minutes |
| Blaze Powder | Potion of Strength | Strength for 3 minutes |
| Magma Cream | Potion of Fire Resistance | Fire Resistance for 3 minutes |
| Glistering Melon Slice | Potion of Healing | Instant Health |

**Weakness is an exception:** brew a Water Bottle directly with **Fermented Spider Eye** to make a Potion of Weakness, with a base drinkable duration of 1 minute 30 seconds. Nether Wart is not part of that chain.

The durations above assume normal 20-tick-per-second speed. Splash delivery and lingering clouds have their own effect-application rules.

## Extending, strengthening, and changing potions

Modifiers work only on registered combinations. For example:

- Swiftness + **Redstone Dust** → extended Swiftness, lasting 8 minutes
- Swiftness + **Glowstone Dust** → Swiftness II, lasting 1 minute 30 seconds
- Healing + **Glowstone Dust** → Healing II
- Healing + **Fermented Spider Eye** → Harming

The checked recipe list has no Redstone-to-extended-Swiftness-II combination. Do not plan on combining longer duration and increased strength just because both modifiers work on the base potion.

Order matters. Sugar, Blaze Powder, Magma Cream, or Glistering Melon Slice added directly to Water makes **Mundane Potion**, not the effects in the table. Water + Redstone also makes Mundane; Water + Glowstone makes **Thick Potion**. Mundane and Thick have no status effects and no further potion-content recipes in the checked brewing list. Empty Glass Bottles cannot substitute for Water Bottles.

## Splash and lingering forms

These transformations change the bottle form while retaining the registered potion type:

| Current form | Ingredient | Result |
| --- | --- | --- |
| Drinkable potion | Gunpowder | Splash potion of the same type |
| Splash potion | Dragon's Breath | Lingering potion of the same type |

Splash potions are thrown. For effect potions, the splash applies effects to nearby susceptible entities, with distance reducing duration or instant-effect strength. Throwing an effect-bearing lingering potion creates an area-effect cloud. Water and effect-free bases have different impact handling, so a Lingering Water Bottle should not be treated as an effect cloud.

The same potion-content recipes also accept the splash and lingering containers. For example, Splash Water + Nether Wart makes Splash Awkward. There is no registered reverse conversion from lingering to splash or from splash to drinkable in the checked list.

## Common problems and limits

- **The ingredient fits but nothing happens:** check the potion's current contents, not just the ingredient name
- **Only part of a batch changes:** only bottles with a matching transformation change; use matching starting bottles for predictable batches
- **Blaze Powder went to the wrong place:** the fuel slot powers the stand; the ingredient slot uses it in a Strength recipe
- **An integrated item sounds like a brewing ingredient:** its name or upstream mod behavior does not prove a recipe exists in MattMC

## Related pages

- [Brewing Stand](../blocks/BrewingStand.md)
- [Potion](../items/Potion.md), [Splash Potion](../items/SplashPotion.md), and [Lingering Potion](../items/LingeringPotion.md)
- [Effects](../effects/Effects.md)
- [Movement effects](../effects/MovementEffects.md): Speed, Slowness, Jump Boost, Slow Falling, Levitation, and Dolphin’s Grace
- [Gameplay](../Gameplay.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01, using active `src/main` code and data. This is not an in-game test or a complete survey of ingredient acquisition. The server initializes the reviewed brewing list; enabled features and later builds can affect availability.

- [Server brewing initialization](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/server/MinecraftServer.java#L338-L340)
- [Registered transformations, container rules, and feature checks](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java)
- [Potion effects and durations](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/alchemy/Potions.java)
- [Filling bottles from water sources](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/BottleItem.java#L48-L63)
- [Batch matching and processing](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/entity/BrewingStandBlockEntity.java#L152-L190)
- [Drinkable potion registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java#L1770-L1778)
- [Water versus effect-bearing impact handling](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/projectile/AbstractThrownPotion.java)
- [Splash effect application](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/projectile/ThrownSplashPotion.java)
- [Lingering cloud creation](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/projectile/ThrownLingeringPotion.java)
