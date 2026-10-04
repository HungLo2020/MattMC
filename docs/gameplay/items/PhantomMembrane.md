# Phantom Membrane

A **Phantom Membrane** repairs damaged [Elytra](Elytra.md) and brews Slow Falling potions. Keep some for flight equipment before spending the rest on brewing. [Item and repair material][registration] · [Brewing recipe][brewing]

## Obtaining

- With mob loot enabled and a player credited for the kill, a [Phantom](../mobs/Phantom.md#drops) can drop **0–1 membrane**, rising to a possible **0–4 with Looting III**. Looting adds a randomized amount; it does not guarantee a membrane. **MattMC disables insomnia spawning by default**, so consult the Phantom guide before planning a hunt. [Loot][phantom-loot] · [Player-credit dispatch][death-loot] · [Looting calculation][looting] · [Default insomnia rule][insomnia]
- A tame [Cat's morning gift](../mobs/Cat.md#sleep-and-morning-gifts) can be **one membrane**. The Cat must be free to relax beside its sleeping owner; the qualifying morning check then has a 70% gift roll. Membrane has weight 2 among 62 total gift weight, or **1 in 31 after a gift is selected**. It is not a guaranteed daily supply. The Cat guide explains the sleep and space checks. [Gift conditions][cat-gift] · [Gift table][gift-table]

The item is listed in the **Ingredients** Creative catalog. Use the [Inventory Browser's mode and permission rules](../mechanics/InventoryBrowser.md#mode-and-permission-limits): seeing it in Survival does not permit server-accepted insertion. [Catalog entry][catalog]

## Usage

### Repairing Elytra

Put damaged Elytra in the Anvil's first input and membranes in the second. Each membrane repairs **up to 108 durability**, one quarter of the ordinary Elytra's 432 maximum, stopping when fully repaired or when the supplied membranes run out. The Anvil consumes the used membranes and charges its displayed level cost; prior repair work can affect that cost. [Elytra properties][registration] · [Repair calculation][repair] · [Payment and consumption][payment]

### Brewing Slow Falling

Use a membrane with **Awkward Potions** in a fueled Brewing Stand. It makes the ordinary **Slow Falling I potion lasting 1,800 ticks**, or **1 minute 30 seconds at 20 ticks per second**. Redstone Dust can extend that potion to **4,800 ticks / 4 minutes**. [Mixes][brewing] · [Potion durations][durations]

One completed brewing operation consumes **one membrane** and processes the three bottle slots, so load up to three matching Awkward Potions for the same ingredient cost. The ingredient must remain valid during brewing. [Brewing checks][brew-checks] · [Bottle processing][brew-action]

## Behavior

An ordinary membrane stacks to **64**. Repairing and brewing consume it as an ingredient; merely holding it does not repair equipped Elytra or grant a potion effect. [Default item registration][item-defaults] · [Item properties][item-properties] · [Stack size][stack] · [Repair operation][repair] · [Brewing operation][brew-action]

## Notes

- Item ID: `minecraft:phantom_membrane`
- These are checked acquisition routes and uses, not an exhaustive audit of all possible loot or data-pack content
- Related: [Phantom](../mobs/Phantom.md#drops), [Cat](../mobs/Cat.md#sleep-and-morning-gifts), [Elytra](Elytra.md), [Items](Items.md)

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`, including active death-loot and Cat-gift dispatch, brewing and Anvil handling. No in-game hunt, overnight gift, repair or brewing test was run. Server rules, item components and data packs can change these defaults.

[item-defaults]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2784-L2797
[item-properties]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L354-L371
[stack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
[death-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1527
[looting]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java#L65-L80
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1196-L1208
[brewing]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L189-L190
[phantom-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/entities/phantom.json#L1-L41
[insomnia]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/GameRules.java#L133-L138
[cat-gift]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/animal/Cat.java#L500-L598
[gift-table]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/gameplay/cat_morning_gift.json#L1-L47
[catalog]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1890-L1897
[repair]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L125-L151
[payment]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L71-L95
[durations]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/alchemy/Potions.java#L73-L76
[brew-checks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/BrewingStandBlockEntity.java#L96-L122
[brew-action]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/BrewingStandBlockEntity.java#L170-L188
